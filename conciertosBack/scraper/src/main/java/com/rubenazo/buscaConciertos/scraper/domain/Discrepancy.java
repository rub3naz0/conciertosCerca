package com.rubenazo.buscaConciertos.scraper.domain;

import java.time.Instant;

public record Discrepancy(
    DiscrepancyType type,
    Severity severity,
    String entityType,
    String entityId,
    String field,
    String expected,
    String actual,
    String rawSnippet,
    Instant timestamp
) {}
