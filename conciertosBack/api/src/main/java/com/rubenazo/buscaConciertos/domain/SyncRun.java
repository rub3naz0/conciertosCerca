package com.rubenazo.buscaConciertos.domain;

import java.time.Instant;

public record SyncRun(
    String id,
    String status,
    Instant startedAt,
    Instant completedAt,
    int salasCount,
    int artistsCount,
    int concertsCount,
    int errorsCount,
    int discrepanciesCount,
    String errorMessage,
    Instant createdAt
) {}
