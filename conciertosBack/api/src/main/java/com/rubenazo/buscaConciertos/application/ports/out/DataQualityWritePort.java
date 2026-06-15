package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.DataQuality;

import java.time.Instant;
import java.util.List;

public interface DataQualityWritePort {
    void saveAll(List<DataQuality> issues);
    void updateSuggestion(Long id, String status, String suggested, String source, Double score, Instant updatedAt);
    void updateStatus(Long id, String status, Instant updatedAt);
    void upsertResolution(String entityType, String entityId, String field, String status,
                          String suggested, String source, Double score, Instant updatedAt);

    /**
     * Resolves an open data-quality gap for (entityType, entityId, field) when the admin
     * edit flow supplies a value for that field. Transitions a NON-TERMINAL row
     * (status IN ('missing','auto_found')) to 'approved', storing the supplied value as
     * 'suggested'. No-op (0 rows affected) when no matching row exists or the row is
     * terminal (approved/auto_approved/rejected). Never inserts — unlike upsertResolution.
     */
    void resolvePendingForField(String entityType, String entityId, String field, String value, Instant updatedAt);
}
