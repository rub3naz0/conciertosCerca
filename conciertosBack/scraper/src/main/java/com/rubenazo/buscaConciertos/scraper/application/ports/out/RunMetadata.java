package com.rubenazo.buscaConciertos.scraper.application.ports.out;

import java.time.Instant;

public record RunMetadata(
    String runMode,
    Instant startedAt,
    Instant completedAt,
    int totalProcessed
) {}
