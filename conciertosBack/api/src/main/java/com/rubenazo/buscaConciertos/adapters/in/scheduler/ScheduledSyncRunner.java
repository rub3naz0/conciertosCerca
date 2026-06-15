package com.rubenazo.buscaConciertos.adapters.in.scheduler;

import com.rubenazo.buscaConciertos.application.ports.in.AlcalaSyncInputPort;
import com.rubenazo.buscaConciertos.application.ports.in.SyncInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncHeartbeatPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import com.rubenazo.buscaConciertos.domain.SyncRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Input adapter that triggers the weekly scrape sync from inside the application,
 * replacing a host-level cron job. Disabled by default — see {@code app.sync.cron}.
 *
 * <p>On each scheduled run it chains both sync sources sequentially: it starts the
 * main sync (same default date window the admin endpoint uses when {@code from}/{@code to}
 * are omitted), waits for it to leave the {@code running} state, then starts the
 * alcalaesmusica.org sync. Both sources share a DB-level single-running-sync guard
 * ({@link SyncRunPort#tryStart()}), so if a sync is already in progress when the
 * schedule fires, that source is skipped with a warning instead of failing.
 *
 * <p>This adapter calls input ports only ({@link SyncInputPort}, {@link AlcalaSyncInputPort})
 * plus the {@link SyncRunPort} output port to start runs and poll their status — the same
 * dependencies {@code SyncAdminApi}/{@code AlcalaSyncAdminApi} use.
 *
 * <p>It also reports a heartbeat to {@link SyncHeartbeatPort}: a start ping when the
 * schedule fires, and a success ping only when both sources finished with status
 * {@code completed}; otherwise a fail ping with a one-line per-source summary. Manual
 * syncs via {@code /api/admin/sync*} do not ping — only this scheduled run does.
 */
@Component
public class ScheduledSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledSyncRunner.class);
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_COMPLETED = "completed";

    private final SyncRunPort syncRunPort;
    private final SyncInputPort syncInputPort;
    private final AlcalaSyncInputPort alcalaSyncInputPort;
    private final SyncHeartbeatPort syncHeartbeatPort;
    private final long pollIntervalMs;
    private final long pollTimeoutMs;
    private final Sleeper sleeper;

    @Autowired
    public ScheduledSyncRunner(
        SyncRunPort syncRunPort,
        SyncInputPort syncInputPort,
        AlcalaSyncInputPort alcalaSyncInputPort,
        SyncHeartbeatPort syncHeartbeatPort,
        @Value("${app.sync.poll-interval-ms:30000}") long pollIntervalMs,
        @Value("${app.sync.poll-timeout-ms:7200000}") long pollTimeoutMs
    ) {
        this(syncRunPort, syncInputPort, alcalaSyncInputPort, syncHeartbeatPort, pollIntervalMs, pollTimeoutMs, ThreadSleeper.INSTANCE);
    }

    // Visible for testing — lets tests inject a no-op Sleeper to avoid real delays.
    ScheduledSyncRunner(
        SyncRunPort syncRunPort,
        SyncInputPort syncInputPort,
        AlcalaSyncInputPort alcalaSyncInputPort,
        SyncHeartbeatPort syncHeartbeatPort,
        long pollIntervalMs,
        long pollTimeoutMs,
        Sleeper sleeper
    ) {
        this.syncRunPort = syncRunPort;
        this.syncInputPort = syncInputPort;
        this.alcalaSyncInputPort = alcalaSyncInputPort;
        this.syncHeartbeatPort = syncHeartbeatPort;
        this.pollIntervalMs = pollIntervalMs;
        this.pollTimeoutMs = pollTimeoutMs;
        this.sleeper = sleeper;
    }

    /**
     * Disabled by default ({@code app.sync.cron} unset -> Spring's CRON_DISABLED marker "-").
     * Set {@code app.sync.cron=0 0 5 * * WED} to run every Wednesday at 05:00 Europe/Madrid.
     */
    @Scheduled(cron = "${app.sync.cron:-}", zone = "Europe/Madrid")
    public void runWeeklySync() {
        log.info("Scheduled weekly sync triggered");
        syncHeartbeatPort.pingStart();

        SyncOutcome mainOutcome = runMainSync();
        SyncOutcome alcalaOutcome = runAlcalaSync();

        reportHeartbeat(mainOutcome, alcalaOutcome);
        log.info("Scheduled weekly sync finished");
    }

    private void reportHeartbeat(SyncOutcome mainOutcome, SyncOutcome alcalaOutcome) {
        String summary = "principal=" + mainOutcome.label() + " alcala=" + alcalaOutcome.label();
        if (mainOutcome == SyncOutcome.COMPLETED && alcalaOutcome == SyncOutcome.COMPLETED) {
            syncHeartbeatPort.pingSuccess(summary);
        } else {
            syncHeartbeatPort.pingFail(summary);
        }
    }

    private SyncOutcome runMainSync() {
        Optional<String> runId = syncRunPort.tryStart();
        if (runId.isEmpty()) {
            log.warn("Scheduled sync: skipping main sync — another sync is already running");
            return SyncOutcome.SKIPPED;
        }
        log.info("Scheduled sync: started main sync run {}", runId.get());
        syncInputPort.execute(runId.get());
        return awaitCompletion(runId.get(), "main");
    }

    private SyncOutcome runAlcalaSync() {
        Optional<String> runId = syncRunPort.tryStart();
        if (runId.isEmpty()) {
            log.warn("Scheduled sync: skipping alcala sync — another sync is already running");
            return SyncOutcome.SKIPPED;
        }
        log.info("Scheduled sync: started alcala sync run {}", runId.get());
        alcalaSyncInputPort.execute(runId.get());
        return awaitCompletion(runId.get(), "alcala");
    }

    /**
     * Polls {@link SyncRunPort#findById(String)} until the run leaves the {@code running}
     * status, or until {@code pollTimeoutMs} is exhausted. Logs the final status (and error
     * count, if any) so the application log doubles as the audit trail for this source.
     */
    private SyncOutcome awaitCompletion(String runId, String sourceLabel) {
        long maxPolls = Math.max(1, pollTimeoutMs / pollIntervalMs);
        for (long attempt = 1; attempt <= maxPolls; attempt++) {
            Optional<SyncRun> run = syncRunPort.findById(runId);
            if (run.isEmpty()) {
                log.warn("Scheduled sync: {} run {} disappeared while polling for completion", sourceLabel, runId);
                return SyncOutcome.DISAPPEARED;
            }
            SyncRun current = run.get();
            if (!STATUS_RUNNING.equals(current.status())) {
                logFinalStatus(sourceLabel, current);
                return STATUS_COMPLETED.equals(current.status()) ? SyncOutcome.COMPLETED : SyncOutcome.FAILED;
            }
            if (attempt == maxPolls) {
                log.warn("Scheduled sync: {} run {} still running after {} ms — giving up waiting, "
                        + "continuing with the next source",
                    sourceLabel, runId, pollTimeoutMs);
                return SyncOutcome.TIMEOUT;
            }
            sleeper.sleep(pollIntervalMs);
        }
        // Unreachable: maxPolls is always >= 1, so the loop above always returns.
        return SyncOutcome.TIMEOUT;
    }

    private void logFinalStatus(String sourceLabel, SyncRun run) {
        if ("failed".equals(run.status())) {
            log.warn("Scheduled sync: {} run {} failed — error: {}",
                sourceLabel, run.id(), run.errorMessage());
        } else {
            log.info("Scheduled sync: {} run {} finished with status {} — salas={} artists={} concerts={} errors={}",
                sourceLabel, run.id(), run.status(), run.salasCount(), run.artistsCount(),
                run.concertsCount(), run.errorsCount());
        }
    }

    /**
     * Outcome of one source's sync attempt, used to decide the heartbeat result and to
     * build the one-line summary sent to {@link SyncHeartbeatPort#pingFail(String)}.
     * Only {@link #COMPLETED} counts as success — a skipped source means the schedule
     * fired but that source's data was not refreshed.
     */
    enum SyncOutcome {
        COMPLETED("completed"),
        FAILED("failed"),
        SKIPPED("skipped"),
        TIMEOUT("timeout"),
        DISAPPEARED("disappeared");

        private final String label;

        SyncOutcome(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /**
     * Abstraction over {@code Thread.sleep} so tests can avoid real delays while exercising
     * the polling loop.
     */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis);
    }

    private enum ThreadSleeper implements Sleeper {
        INSTANCE;

        @Override
        public void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
