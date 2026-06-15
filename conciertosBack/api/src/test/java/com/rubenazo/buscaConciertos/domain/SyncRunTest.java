package com.rubenazo.buscaConciertos.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SyncRunTest {

    @Test
    void constructor_setsAllRequiredFields() {
        Instant startedAt = Instant.parse("2026-05-30T10:00:00Z");
        Instant createdAt = Instant.parse("2026-05-30T10:00:00Z");

        SyncRun run = new SyncRun(
            "run-id-1", "running", startedAt, null,
            0, 0, 0, 0, 0, null, createdAt
        );

        assertThat(run.id()).isEqualTo("run-id-1");
        assertThat(run.status()).isEqualTo("running");
        assertThat(run.startedAt()).isEqualTo(startedAt);
        assertThat(run.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void completedAt_canBeNull() {
        SyncRun run = new SyncRun(
            "run-1", "running", Instant.now(), null,
            0, 0, 0, 0, 0, null, Instant.now()
        );

        assertThat(run.completedAt()).isNull();
    }

    @Test
    void errorMessage_canBeNull() {
        SyncRun run = new SyncRun(
            "run-1", "running", Instant.now(), null,
            0, 0, 0, 0, 0, null, Instant.now()
        );

        assertThat(run.errorMessage()).isNull();
    }

    @Test
    void completedRun_hasAllCounts() {
        Instant startedAt = Instant.parse("2026-05-30T10:00:00Z");
        Instant completedAt = Instant.parse("2026-05-30T11:00:00Z");

        SyncRun run = new SyncRun(
            "run-2", "completed", startedAt, completedAt,
            5, 10, 15, 2, 3, null, startedAt
        );

        assertThat(run.status()).isEqualTo("completed");
        assertThat(run.completedAt()).isEqualTo(completedAt);
        assertThat(run.salasCount()).isEqualTo(5);
        assertThat(run.artistsCount()).isEqualTo(10);
        assertThat(run.concertsCount()).isEqualTo(15);
        assertThat(run.errorsCount()).isEqualTo(2);
        assertThat(run.discrepanciesCount()).isEqualTo(3);
    }

    @Test
    void failedRun_hasErrorMessage() {
        SyncRun run = new SyncRun(
            "run-3", "failed", Instant.now(), Instant.now(),
            0, 0, 0, 1, 0, "Connection refused", Instant.now()
        );

        assertThat(run.status()).isEqualTo("failed");
        assertThat(run.errorMessage()).isEqualTo("Connection refused");
    }
}
