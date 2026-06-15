package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.application.ports.out.ConcertRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertWritePort;
import com.rubenazo.buscaConciertos.domain.Concert;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ConcertSqliteAdapter implements ConcertRepositoryPort, ConcertWritePort {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    private static final RowMapper<Concert> ROW_MAPPER = (rs, rowNum) -> {
        String artistIdsRaw = rs.getString("artist_ids");
        List<String> artistIds = (artistIdsRaw != null && !artistIdsRaw.isBlank())
            ? Arrays.asList(artistIdsRaw.split(","))
            : List.of();

        return new Concert(
            rs.getString("id"),
            rs.getString("sala_concierto_id"),
            artistIds,
            LocalDate.parse(rs.getString("date")),
            rs.getString("time"),
            rs.getString("price"),
            rs.getString("source_url"),
            Instant.parse(rs.getString("updated_at"))
        );
    };

    public ConcertSqliteAdapter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    private static final String ELIGIBILITY_CONDITIONS = """
            AND NOT EXISTS (
                SELECT 1 FROM data_quality dq
                WHERE dq.entity_type = 'concert' AND dq.entity_id = c.id AND dq.severity = 'severe'
                  AND dq.status NOT IN ('approved','auto_approved')
            )
            AND NOT EXISTS (
                SELECT 1 FROM data_quality dq
                WHERE dq.entity_type = 'sala' AND dq.entity_id = c.sala_concierto_id AND dq.severity = 'severe'
                  AND dq.status NOT IN ('approved','auto_approved')
            )
            AND EXISTS (
                SELECT 1 FROM concert_artists ca2
                WHERE ca2.concert_id = c.id
            )
            AND EXISTS (
                SELECT 1 FROM concert_artists ca3
                WHERE ca3.concert_id = c.id
                AND NOT EXISTS (
                    SELECT 1 FROM data_quality dq2
                    WHERE dq2.entity_type = 'artist' AND dq2.entity_id = ca3.artist_id AND dq2.severity = 'severe'
                      AND dq2.status NOT IN ('approved','auto_approved')
                )
            )
        """;

    private static final String INELIGIBLE_CONDITIONS = """
            (
                EXISTS (
                    SELECT 1 FROM data_quality dq
                    WHERE dq.entity_type = 'concert' AND dq.entity_id = c.id AND dq.severity = 'severe'
                      AND dq.status NOT IN ('approved','auto_approved')
                )
                OR EXISTS (
                    SELECT 1 FROM data_quality dq
                    WHERE dq.entity_type = 'sala' AND dq.entity_id = c.sala_concierto_id AND dq.severity = 'severe'
                      AND dq.status NOT IN ('approved','auto_approved')
                )
                OR (
                    EXISTS (
                        SELECT 1 FROM concert_artists ca2
                        WHERE ca2.concert_id = c.id
                    )
                    AND NOT EXISTS (
                        SELECT 1 FROM concert_artists ca3
                        WHERE ca3.concert_id = c.id
                        AND NOT EXISTS (
                            SELECT 1 FROM data_quality dq2
                            WHERE dq2.entity_type = 'artist' AND dq2.entity_id = ca3.artist_id AND dq2.severity = 'severe'
                              AND dq2.status NOT IN ('approved','auto_approved')
                        )
                    )
                )
            )
        """;

    private static final String RECENT_CONCERT_OR_DQ_CHANGE_CONDITIONS = """
            (
                c.updated_at >= ?
                OR EXISTS (
                    SELECT 1 FROM data_quality dq
                    WHERE dq.entity_type = 'concert' AND dq.entity_id = c.id AND dq.severity = 'severe'
                      AND dq.updated_at >= ?
                )
                OR EXISTS (
                    SELECT 1 FROM data_quality dq
                    WHERE dq.entity_type = 'sala' AND dq.entity_id = c.sala_concierto_id AND dq.severity = 'severe'
                      AND dq.updated_at >= ?
                )
                OR EXISTS (
                    SELECT 1 FROM concert_artists ca_recent
                    JOIN data_quality dq ON dq.entity_type = 'artist'
                        AND dq.entity_id = ca_recent.artist_id
                        AND dq.severity = 'severe'
                        AND dq.updated_at >= ?
                    WHERE ca_recent.concert_id = c.id
                )
            )
        """;

    @Override
    public List<Concert> findAll() {
        String today = LocalDate.now(clock).toString();
        return jdbcTemplate.query(
            """
            SELECT c.id, c.sala_concierto_id, c.date, c.time, c.price, c.source_url, c.updated_at,
                   GROUP_CONCAT(ca.artist_id ORDER BY ca.position) as artist_ids
            FROM concerts c
            LEFT JOIN concert_artists ca ON c.id = ca.concert_id
            WHERE c.date >= ? AND c.deleted = 0
            """ + ELIGIBILITY_CONDITIONS + """
            GROUP BY c.id
            ORDER BY c.date
            """,
            ROW_MAPPER,
            today
        );
    }

    @Override
    public List<Concert> findAllIncludingBlocked() {
        String today = LocalDate.now(clock).toString();
        return jdbcTemplate.query(
            """
            SELECT c.id, c.sala_concierto_id, c.date, c.time, c.price, c.source_url, c.updated_at,
                   GROUP_CONCAT(ca.artist_id ORDER BY ca.position) as artist_ids
            FROM concerts c
            LEFT JOIN concert_artists ca ON c.id = ca.concert_id
            WHERE c.date >= ? AND c.deleted = 0
            GROUP BY c.id
            ORDER BY c.date
            """,
            ROW_MAPPER,
            today
        );
    }

    @Override
    public Optional<Concert> findByIdIncludingDeleted(String id) {
        List<Concert> results = jdbcTemplate.query(
            """
            SELECT c.id, c.sala_concierto_id, c.date, c.time, c.price, c.source_url, c.updated_at,
                   GROUP_CONCAT(ca.artist_id ORDER BY ca.position) as artist_ids
            FROM concerts c
            LEFT JOIN concert_artists ca ON c.id = ca.concert_id
            WHERE c.id = ?
            GROUP BY c.id
            """,
            ROW_MAPPER,
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<Concert> findModifiedAfter(Instant since) {
        String today = LocalDate.now(clock).toString();
        return jdbcTemplate.query(
            """
            SELECT c.id, c.sala_concierto_id, c.date, c.time, c.price, c.source_url, c.updated_at,
                   GROUP_CONCAT(ca.artist_id ORDER BY ca.position) as artist_ids
            FROM concerts c
            LEFT JOIN concert_artists ca ON c.id = ca.concert_id
            WHERE c.date >= ? AND c.deleted = 0 AND
            """ + RECENT_CONCERT_OR_DQ_CHANGE_CONDITIONS + ELIGIBILITY_CONDITIONS + """
            GROUP BY c.id
            ORDER BY c.date
            """,
            ROW_MAPPER,
            today,
            since.toString(),
            since.toString(),
            since.toString(),
            since.toString()
        );
    }

    @Override
    public List<String> getDeletedIds(Instant since) {
        String today = LocalDate.now(clock).toString();
        String sinceValue = since != null ? since.toString() : null;
        List<String> deleted = since == null
            ? jdbcTemplate.queryForList("SELECT id FROM concerts WHERE deleted = 1", String.class)
            : jdbcTemplate.queryForList(
                "SELECT id FROM concerts WHERE deleted = 1 AND updated_at >= ?",
                String.class,
                sinceValue
            );
        List<String> newlyIneligible = jdbcTemplate.queryForList(
            """
            SELECT DISTINCT c.id
            FROM concerts c
            WHERE c.date >= ? AND c.deleted = 0 AND
            """ + INELIGIBLE_CONDITIONS + """
            ORDER BY c.id
            """,
            String.class,
            today
        );
        Set<String> merged = new LinkedHashSet<>(deleted);
        merged.addAll(newlyIneligible);
        return List.copyOf(merged);
    }

    @Override
    public boolean existsActiveById(String id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM concerts WHERE id = ? AND deleted = 0",
            Integer.class, id
        );
        return count != null && count > 0;
    }

    @Override
    public void upsert(Concert concert) {
        jdbcTemplate.update("""
            INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted)
            VALUES (?,?,?,?,?,?,?,0)
            ON CONFLICT(id) DO UPDATE SET
                sala_concierto_id = CASE
                    WHEN (SELECT 1 FROM salas_concierto WHERE id = excluded.sala_concierto_id) IS NOT NULL
                    THEN excluded.sala_concierto_id ELSE concerts.sala_concierto_id END,
                date=excluded.date, time=excluded.time,
                price=excluded.price, source_url=excluded.source_url,
                updated_at=excluded.updated_at, deleted=0
            """,
            concert.id(), concert.salaConciertoId(), concert.date().toString(),
            concert.time(), concert.price(), concert.sourceUrl(),
            concert.updatedAt().toString()
        );
        jdbcTemplate.update("DELETE FROM concert_artists WHERE concert_id = ?", concert.id());
        for (int i = 0; i < concert.artistIds().size(); i++) {
            jdbcTemplate.update(
                "INSERT INTO concert_artists(concert_id, artist_id, position) VALUES (?,?,?)",
                concert.id(), concert.artistIds().get(i), i
            );
        }
    }

    @Override
    public int markDeleted(String concertId) {
        return jdbcTemplate.update(
            "UPDATE concerts SET deleted = 1, updated_at = ? WHERE id = ?",
            Instant.now(clock).toString(), concertId
        );
    }

    @Override
    public int deleteBeforeDate(LocalDate cutoff) {
        String cutoffStr = cutoff.toString();
        jdbcTemplate.update(
            "DELETE FROM concert_artists WHERE concert_id IN (SELECT id FROM concerts WHERE date < ?)",
            cutoffStr
        );
        jdbcTemplate.update(
            "DELETE FROM data_quality WHERE entity_type = 'concert' AND entity_id IN (SELECT id FROM concerts WHERE date < ?)",
            cutoffStr
        );
        return jdbcTemplate.update(
            "DELETE FROM concerts WHERE date < ?",
            cutoffStr
        );
    }
}
