package com.rubenazo.buscaConciertos.adminweb.application;

import java.time.Instant;

public record SevereIssue(
    Long id,
    String entityType,
    String entityId,
    String field,
    String status,
    String severity,
    String suggested,
    String source,
    Double score,
    Instant updatedAt,
    int blockedConcertCount
) {}
