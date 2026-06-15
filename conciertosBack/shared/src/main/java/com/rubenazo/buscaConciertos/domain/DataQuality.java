package com.rubenazo.buscaConciertos.domain;

import java.time.Instant;

public record DataQuality(
    Long id,
    String entityType,
    String entityId,
    String field,
    String status,
    String severity,
    String suggested,
    String source,
    Double score,
    Instant updatedAt
) {}
