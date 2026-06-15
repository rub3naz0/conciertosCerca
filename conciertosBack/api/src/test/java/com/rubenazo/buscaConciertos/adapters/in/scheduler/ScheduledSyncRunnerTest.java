package com.rubenazo.buscaConciertos.adapters.in.scheduler;

import com.rubenazo.buscaConciertos.application.ports.in.AlcalaSyncInputPort;
import com.rubenazo.buscaConciertos.application.ports.in.SyncInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncHeartbeatPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import com.rubenazo.buscaConciertos.domain.SyncRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledSyncRunnerTest {

    private static final long POLL_INTERVAL_MS = 30_000L;
    private static final long POLL_TIMEOUT_MS = 7_200_000L;

    @Mock private SyncRunPort syncRunPort;
    @Mock private SyncInputPort syncInputPort;
    @Mock private AlcalaSyncInputPort alcalaSyncInputPort;
    @Mock private SyncHeartbeatPort syncHeartbeatPort;
    @Mock private ScheduledSyncRunner.Sleeper sleeper;

    private ScheduledSyncRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ScheduledSyncRunner(
            syncRunPort, syncInputPort, alcalaSyncInputPort, syncHeartbeatPort,
            POLL_INTERVAL_MS, POLL_TIMEOUT_MS, sleeper
        );
    }

    private SyncRun runWithStatus(String runId, String status) {
        return new SyncRun(runId, status, Instant.parse("2026-06-10T05:00:00Z"),
            status.equals("running") ? null : Instant.parse("2026-06-10T05:01:00Z"),
            0, 0, 0, 0, 0, null, Instant.parse("2026-06-10T05:00:00Z"));
    }

    @Test
    void runsMainSyncThenAlcalaInOrderWhenBothCanStart() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.of("alcala-run"));
        when(syncRunPort.findById("main-run")).thenReturn(Optional.of(runWithStatus("main-run", "completed")));
        when(syncRunPort.findById("alcala-run")).thenReturn(Optional.of(runWithStatus("alcala-run", "completed")));

        runner.runWeeklySync();

        InOrder order = inOrder(syncInputPort, syncRunPort, alcalaSyncInputPort);
        order.verify(syncRunPort).tryStart();
        order.verify(syncInputPort).execute("main-run");
        order.verify(syncRunPort).findById("main-run");
        order.verify(syncRunPort).tryStart();
        order.verify(alcalaSyncInputPort).execute("alcala-run");
    }

    @Test
    void alwaysPingsHeartbeatStartWhenScheduleFires() {
        when(syncRunPort.tryStart()).thenReturn(Optional.empty(), Optional.empty());

        runner.runWeeklySync();

        verify(syncHeartbeatPort).pingStart();
    }

    @Test
    void pingsHeartbeatSuccessOnlyWhenBothSourcesCompleted() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.of("alcala-run"));
        when(syncRunPort.findById("main-run")).thenReturn(Optional.of(runWithStatus("main-run", "completed")));
        when(syncRunPort.findById("alcala-run")).thenReturn(Optional.of(runWithStatus("alcala-run", "completed")));

        runner.runWeeklySync();

        verify(syncHeartbeatPort).pingStart();
        verify(syncHeartbeatPort).pingSuccess("principal=completed alcala=completed");
        verify(syncHeartbeatPort, never()).pingFail(anyString());
    }

    @Test
    void waitsForMainSyncCompletionBeforeStartingAlcala() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.of("alcala-run"));
        when(syncRunPort.findById("main-run")).thenReturn(
            Optional.of(runWithStatus("main-run", "running")),
            Optional.of(runWithStatus("main-run", "running")),
            Optional.of(runWithStatus("main-run", "completed"))
        );

        runner.runWeeklySync();

        verify(syncRunPort, times(3)).findById("main-run");
        verify(sleeper, times(2)).sleep(POLL_INTERVAL_MS);
        verify(alcalaSyncInputPort).execute("alcala-run");
    }

    @Test
    void alcalaStillRunsWhenMainFailed() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.of("alcala-run"));
        when(syncRunPort.findById("main-run")).thenReturn(Optional.of(runWithStatus("main-run", "failed")));
        when(syncRunPort.findById("alcala-run")).thenReturn(Optional.of(runWithStatus("alcala-run", "completed")));

        runner.runWeeklySync();

        verify(alcalaSyncInputPort).execute("alcala-run");
    }

    @Test
    void pingsHeartbeatFailWithSummaryWhenMainFailed() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.of("alcala-run"));
        when(syncRunPort.findById("main-run")).thenReturn(Optional.of(runWithStatus("main-run", "failed")));
        when(syncRunPort.findById("alcala-run")).thenReturn(Optional.of(runWithStatus("alcala-run", "completed")));

        runner.runWeeklySync();

        verify(syncHeartbeatPort).pingFail("principal=failed alcala=completed");
        verify(syncHeartbeatPort, never()).pingSuccess(anyString());
    }

    @Test
    void skipsMainSyncWithWarningWhenAlreadyRunning() {
        when(syncRunPort.tryStart()).thenReturn(Optional.empty(), Optional.empty());

        runner.runWeeklySync();

        verify(syncInputPort, never()).execute(anyString());
        verify(syncRunPort, never()).findById(anyString());
        verify(alcalaSyncInputPort, never()).execute(anyString());
        verifyNoInteractions(sleeper);
    }

    @Test
    void skipsAlcalaWithWarningWhenItCannotStartAfterMainCompletes() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.empty());
        when(syncRunPort.findById("main-run")).thenReturn(Optional.of(runWithStatus("main-run", "completed")));

        runner.runWeeklySync();

        verify(syncInputPort).execute("main-run");
        verify(alcalaSyncInputPort, never()).execute(anyString());
    }

    @Test
    void pingsHeartbeatFailWithSummaryWhenAlcalaSkipped() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.empty());
        when(syncRunPort.findById("main-run")).thenReturn(Optional.of(runWithStatus("main-run", "completed")));

        runner.runWeeklySync();

        verify(syncHeartbeatPort).pingFail("principal=completed alcala=skipped");
        verify(syncHeartbeatPort, never()).pingSuccess(anyString());
    }

    @Test
    void givesUpWaitingAfterTimeoutAndStillAttemptsAlcala() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.of("alcala-run"));
        // Always "running" -> polling exhausts the timeout budget.
        when(syncRunPort.findById("main-run")).thenReturn(Optional.of(runWithStatus("main-run", "running")));
        when(syncRunPort.findById("alcala-run")).thenReturn(Optional.of(runWithStatus("alcala-run", "completed")));

        runner.runWeeklySync();

        // timeout / pollInterval polls before giving up
        long expectedPolls = POLL_TIMEOUT_MS / POLL_INTERVAL_MS;
        verify(syncRunPort, times((int) expectedPolls)).findById("main-run");
        verify(alcalaSyncInputPort).execute("alcala-run");
    }

    @Test
    void pingsHeartbeatFailWithSummaryWhenMainTimesOut() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.of("alcala-run"));
        when(syncRunPort.findById("main-run")).thenReturn(Optional.of(runWithStatus("main-run", "running")));
        when(syncRunPort.findById("alcala-run")).thenReturn(Optional.of(runWithStatus("alcala-run", "completed")));

        runner.runWeeklySync();

        verify(syncHeartbeatPort).pingFail("principal=timeout alcala=completed");
        verify(syncHeartbeatPort, never()).pingSuccess(anyString());
    }

    @Test
    void doesNotThrowWhenSyncRunDisappearsWhilePolling() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.of("alcala-run"));
        when(syncRunPort.findById("main-run")).thenReturn(Optional.empty());
        when(syncRunPort.findById("alcala-run")).thenReturn(Optional.of(runWithStatus("alcala-run", "completed")));

        runner.runWeeklySync();

        verify(alcalaSyncInputPort).execute("alcala-run");
    }

    @Test
    void pingsHeartbeatFailWithSummaryWhenMainRunDisappears() {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("main-run"), Optional.of("alcala-run"));
        when(syncRunPort.findById("main-run")).thenReturn(Optional.empty());
        when(syncRunPort.findById("alcala-run")).thenReturn(Optional.of(runWithStatus("alcala-run", "completed")));

        runner.runWeeklySync();

        verify(syncHeartbeatPort).pingFail("principal=disappeared alcala=completed");
        verify(syncHeartbeatPort, never()).pingSuccess(anyString());
    }
}
