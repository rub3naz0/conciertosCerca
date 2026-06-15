package com.rubenazo.buscaConciertos.adminweb.adapters.in.dto;

import com.rubenazo.buscaConciertos.adminweb.application.SevereIssue;

import java.time.Instant;

public record SevereIssueDto(
    Long id,
    String entityType,
    String entityId,
    String field,
    String status,
    String suggested,
    Double score,
    String severity,
    Instant updatedAt,
    int blockedConcertCount
) {
    public static SevereIssueDto from(SevereIssue issue) {
        return new SevereIssueDto(
            issue.id(),
            issue.entityType(),
            issue.entityId(),
            issue.field(),
            issue.status(),
            issue.suggested(),
            issue.score(),
            issue.severity(),
            issue.updatedAt(),
            issue.blockedConcertCount()
        );
    }
}
