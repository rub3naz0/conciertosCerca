package com.rubenazo.buscaConciertos.adminweb.adapters.out;

import com.rubenazo.buscaConciertos.adminweb.application.SevereIssue;
import com.rubenazo.buscaConciertos.adminweb.application.ports.in.DataQualityReadPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DataQualitySqliteReadAdapter implements DataQualityReadPort {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<SevereIssue> ROW_MAPPER = (rs, rowNum) -> new SevereIssue(
        rs.getLong("id"),
        rs.getString("entity_type"),
        rs.getString("entity_id"),
        rs.getString("field"),
        rs.getString("status"),
        rs.getString("severity"),
        rs.getString("suggested"),
        rs.getString("source"),
        rs.getObject("score") != null ? rs.getDouble("score") : null,
        Instant.parse(rs.getString("updated_at")),
        rs.getInt("blocked_concert_count")
    );

    public DataQualitySqliteReadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SevereIssue> listUnresolvedSevere() {
        // Zero impact = no upcoming concert depends on the entity. The row stays
        // in data_quality (a future sync can make it relevant again) but is
        // hidden from the panel.
        return jdbcTemplate.query(
            """
            SELECT * FROM (
                SELECT dq.id, dq.entity_type, dq.entity_id, dq.field, dq.status, dq.severity,
                       dq.suggested, dq.source, dq.score, dq.updated_at,
                       CASE
                           WHEN dq.entity_type = 'sala' THEN (
                               SELECT COUNT(*)
                               FROM concerts c
                               WHERE c.deleted = 0
                                 AND c.date >= date('now')
                                 AND c.sala_concierto_id = dq.entity_id
                           )
                           WHEN dq.entity_type = 'artist' THEN (
                               SELECT COUNT(DISTINCT c.id)
                               FROM concerts c
                               JOIN concert_artists ca ON ca.concert_id = c.id
                               WHERE c.deleted = 0
                                 AND c.date >= date('now')
                                 AND ca.artist_id = dq.entity_id
                           )
                           WHEN dq.entity_type = 'concert' THEN (
                               SELECT COUNT(*)
                               FROM concerts c
                               WHERE c.deleted = 0
                                 AND c.date >= date('now')
                                 AND c.id = dq.entity_id
                           )
                           ELSE 0
                       END AS blocked_concert_count
                FROM data_quality dq
                WHERE dq.severity = 'severe'
                  AND dq.status IN ('missing', 'auto_found')
            )
            WHERE blocked_concert_count > 0
            ORDER BY blocked_concert_count DESC, updated_at DESC, id ASC
            """,
            ROW_MAPPER
        );
    }
}
