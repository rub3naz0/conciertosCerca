package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.domain.SyncRun;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SyncRunDtoTest {

    @Test
    void from_mapsAllFieldsFromCompletedRun() {
        Instant startedAt = Instant.parse("2026-05-30T10:00:00Z");
        Instant completedAt = Instant.parse("2026-05-30T11:00:00Z");
        SyncRun run = new SyncRun(
            "run-1", "completed", startedAt, completedAt,
            5, 10, 20, 2, 3, null, startedAt
        );

        SyncRunDto dto = SyncRunDto.from(run);

        assertThat(dto.runId()).isEqualTo("run-1");
        assertThat(dto.status()).isEqualTo("completed");
        assertThat(dto.startedAt()).isEqualTo(startedAt);
        assertThat(dto.completedAt()).isEqualTo(completedAt);
        assertThat(dto.stats().salas()).isEqualTo(5);
        assertThat(dto.stats().artists()).isEqualTo(10);
        assertThat(dto.stats().concerts()).isEqualTo(20);
        assertThat(dto.stats().errors()).isEqualTo(2);
        assertThat(dto.stats().discrepancies()).isEqualTo(3);
        assertThat(dto.errorMessage()).isNull();
    }

    @Test
    void from_nullableCompletedAt_doesNotThrow() {
        SyncRun run = new SyncRun(
            "run-2", "running", Instant.now(), null,
            0, 0, 0, 0, 0, null, Instant.now()
        );

        SyncRunDto dto = SyncRunDto.from(run);

        assertThat(dto.completedAt()).isNull();
        assertThat(dto.errorMessage()).isNull();
    }

    @Test
    void from_failedRun_mapsErrorMessage() {
        SyncRun run = new SyncRun(
            "run-3", "failed", Instant.now(), Instant.now(),
            0, 0, 0, 1, 0, "Scraper timeout", Instant.now()
        );

        SyncRunDto dto = SyncRunDto.from(run);

        assertThat(dto.status()).isEqualTo("failed");
        assertThat(dto.errorMessage()).isEqualTo("Scraper timeout");
    }
}
