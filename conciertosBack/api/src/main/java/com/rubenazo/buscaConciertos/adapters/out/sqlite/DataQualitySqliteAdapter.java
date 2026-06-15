package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.application.ports.out.DataQualityRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityWritePort;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.FieldSeverity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class DataQualitySqliteAdapter implements DataQualityRepositoryPort, DataQualityWritePort {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<DataQuality> ROW_MAPPER = (rs, rowNum) -> {
        double scoreRaw = rs.getDouble("score");
        Double score = rs.wasNull() ? null : scoreRaw;
        return new DataQuality(
            rs.getLong("id"),
            rs.getString("entity_type"),
            rs.getString("entity_id"),
            rs.getString("field"),
            rs.getString("status"),
            rs.getString("severity"),
            rs.getString("suggested"),
            rs.getString("source"),
            score,
            Instant.parse(rs.getString("updated_at"))
        );
    };

    public DataQualitySqliteAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DataQuality> findByStatus(String status) {
        return jdbcTemplate.query(
            "SELECT id, entity_type, entity_id, field, status, severity, suggested, source, score, updated_at FROM data_quality WHERE status = ?",
            ROW_MAPPER,
            status
        );
    }

    @Override
    public List<DataQuality> findByEntityTypeAndStatus(String entityType, String status) {
        return jdbcTemplate.query(
            "SELECT id, entity_type, entity_id, field, status, severity, suggested, source, score, updated_at FROM data_quality WHERE entity_type = ? AND status = ?",
            ROW_MAPPER,
            entityType, status
        );
    }

    @Override
    public Optional<DataQuality> findById(Long id) {
        List<DataQuality> results = jdbcTemplate.query(
            "SELECT id, entity_type, entity_id, field, status, severity, suggested, source, score, updated_at FROM data_quality WHERE id = ?",
            ROW_MAPPER,
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<DataQuality> findByStatusAndScore(String status, double minScore, String severityFilter) {
        if (severityFilter != null) {
            return jdbcTemplate.query(
                "SELECT id, entity_type, entity_id, field, status, severity, suggested, source, score, updated_at FROM data_quality WHERE status = ? AND score >= ? AND severity = ?",
                ROW_MAPPER,
                status, minScore, severityFilter
            );
        }
        return jdbcTemplate.query(
            "SELECT id, entity_type, entity_id, field, status, severity, suggested, source, score, updated_at FROM data_quality WHERE status = ? AND score >= ?",
            ROW_MAPPER,
            status, minScore
        );
    }

    @Override
    public void saveAll(List<DataQuality> issues) {
        for (DataQuality issue : issues) {
            jdbcTemplate.update("""
                INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(entity_type, entity_id, field) DO UPDATE SET
                    status = CASE
                        WHEN excluded.status IN ('missing') AND data_quality.status IN ('auto_found', 'approved', 'rejected', 'auto_approved') THEN data_quality.status
                        ELSE excluded.status
                    END,
                    severity = CASE
                        WHEN data_quality.status IN ('auto_found', 'approved', 'rejected', 'auto_approved') THEN data_quality.severity
                        ELSE excluded.severity
                    END,
                    suggested = CASE
                        WHEN data_quality.status IN ('auto_found', 'approved', 'rejected', 'auto_approved') THEN data_quality.suggested
                        ELSE excluded.suggested
                    END,
                    source = CASE
                        WHEN data_quality.status IN ('auto_found', 'approved', 'rejected', 'auto_approved') THEN data_quality.source
                        ELSE excluded.source
                    END,
                    updated_at = CASE
                        WHEN data_quality.status IN ('auto_found', 'approved', 'rejected', 'auto_approved') THEN data_quality.updated_at
                        ELSE excluded.updated_at
                    END
                """,
                issue.entityType(), issue.entityId(), issue.field(), issue.status(),
                issue.severity(), issue.suggested(), issue.source(), issue.updatedAt().toString()
            );
        }
    }

    @Override
    public void updateSuggestion(Long id, String status, String suggested, String source, Double score, Instant updatedAt) {
        jdbcTemplate.update(
            "UPDATE data_quality SET status = ?, suggested = ?, source = ?, score = ?, updated_at = ? WHERE id = ?",
            status, suggested, source, score, updatedAt.toString(), id
        );
    }

    @Override
    public void updateStatus(Long id, String status, Instant updatedAt) {
        jdbcTemplate.update(
            "UPDATE data_quality SET status = ?, updated_at = ? WHERE id = ?",
            status, updatedAt.toString(), id
        );
    }

    @Override
    public List<String> findEntityIdsBySourceAndField(String entityType, String source, List<String> fields) {
        if (fields.isEmpty()) {
            return List.of();
        }
        String placeholders = fields.stream().map(f -> "?").collect(java.util.stream.Collectors.joining(","));
        Object[] params = new Object[2 + fields.size()];
        params[0] = entityType;
        params[1] = source;
        for (int i = 0; i < fields.size(); i++) {
            params[2 + i] = fields.get(i);
        }
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT entity_id FROM data_quality WHERE entity_type = ? AND source = ? AND field IN (" + placeholders + ")",
            String.class,
            params
        );
    }

    @Override
    public void resolvePendingForField(String entityType, String entityId, String field, String value, Instant updatedAt) {
        jdbcTemplate.update("""
            UPDATE data_quality SET
                status = 'approved', suggested = ?, updated_at = ?
            WHERE entity_type = ? AND entity_id = ? AND field = ?
              AND status IN ('missing', 'auto_found')
            """,
            value, updatedAt.toString(), entityType, entityId, field
        );
    }

    @Override
    public void upsertResolution(String entityType, String entityId, String field, String status,
                                 String suggested, String source, Double score, Instant updatedAt) {
        String severity = FieldSeverity.of(entityType, field).toDbValue();
        jdbcTemplate.update("""
            INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, score, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON CONFLICT(entity_type, entity_id, field) DO UPDATE SET
                status = excluded.status,
                severity = excluded.severity,
                suggested = excluded.suggested,
                source = excluded.source,
                score = excluded.score,
                updated_at = excluded.updated_at
            """,
            entityType, entityId, field, status, severity, suggested, source, score, updatedAt.toString()
        );
    }
}
