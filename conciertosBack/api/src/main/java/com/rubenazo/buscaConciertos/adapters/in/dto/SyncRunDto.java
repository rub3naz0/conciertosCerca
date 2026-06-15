package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.domain.SyncRun;

import java.time.Instant;

public record SyncRunDto(
    String runId,
    String status,
    Instant startedAt,
    Instant completedAt,
    Stats stats,
    String errorMessage
) {
    public record Stats(int concerts, int salas, int artists, int errors, int discrepancies) {}

    public static SyncRunDto from(SyncRun run) {
        return new SyncRunDto(
            run.id(),
            run.status(),
            run.startedAt(),
            run.completedAt(),
            new Stats(
                run.concertsCount(),
                run.salasCount(),
                run.artistsCount(),
                run.errorsCount(),
                run.discrepanciesCount()
            ),
            run.errorMessage()
        );
    }
}
