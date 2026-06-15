package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.domain.DataQuality;

import java.time.Instant;

public record DataQualityIssueDto(
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
) {
    public static DataQualityIssueDto from(DataQuality dq) {
        return new DataQualityIssueDto(
            dq.id(),
            dq.entityType(),
            dq.entityId(),
            dq.field(),
            dq.status(),
            dq.severity(),
            dq.suggested(),
            dq.source(),
            dq.score(),
            dq.updatedAt()
        );
    }
}
