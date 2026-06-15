package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.domain.Concert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 7.2 [RED] Integration test for ConcertSqliteAdapter:
 * - upsert concert, query back, assert no ticket_url column.
 * Task 7.4 [GREEN] verifies the adapter compiles and works with new schema.
 */
class ConcertSqliteAdapterFieldAlignmentTest {

    private static final Clock FIXED_CLOCK =
        Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("UTC"));

    private JdbcTemplate jdbcTemplate;
    private ConcertSqliteAdapter adapter;

    @BeforeEach
    void setUp() {
        var ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(ds);

        // New schema: ticket_url removed from concerts
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS salas_concierto (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, address TEXT,
                city TEXT NOT NULL, province TEXT NOT NULL,
                lat REAL, lng REAL, description TEXT, image_url TEXT,
                source_url TEXT, updated_at TEXT NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS concerts (
                id TEXT PRIMARY KEY, sala_concierto_id TEXT NOT NULL REFERENCES salas_concierto(id),
                date TEXT NOT NULL, time TEXT, price TEXT,
                source_url TEXT NOT NULL, updated_at TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS concert_artists (
                concert_id TEXT NOT NULL REFERENCES concerts(id),
                artist_id TEXT NOT NULL REFERENCES artists(id),
                position INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (concert_id, artist_id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS artists (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, genre TEXT,
                image_url TEXT, website TEXT, description TEXT, source_url TEXT, updated_at TEXT NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS data_quality (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL, entity_id TEXT NOT NULL,
                field TEXT NOT NULL, status TEXT NOT NULL,
                severity TEXT NOT NULL DEFAULT 'non_severe',
                suggested TEXT, source TEXT, updated_at TEXT NOT NULL,
                UNIQUE(entity_type, entity_id, field)
            )
            """);

        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, updated_at) VALUES (?,?,?,?,?)",
            "sala1", "Sala Test", "Madrid", "Madrid", "2026-05-01T00:00:00Z"
        );

        adapter = new ConcertSqliteAdapter(jdbcTemplate, FIXED_CLOCK);
    }

    @Test
    void upsert_insertsConcertWithoutTicketUrl() {
        Concert concert = new Concert(
            "c1", "sala1", List.of(),
            LocalDate.parse("2026-07-15"), "21:00", "25€",
            "https://source.test/c1",
            Instant.parse("2026-05-20T10:00:00Z")
        );

        adapter.upsert(concert);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM concerts WHERE id = 'c1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("id", "c1");
        assertThat(rows.get(0)).containsEntry("price", "25€");
        // ticket_url must not exist as a column in the new schema
        assertThat(rows.get(0)).doesNotContainKey("ticket_url");
    }

    @Test
    void schema_hasNoTicketUrlColumn() {
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?)",
            "c1", "sala1", "2026-07-15", "https://source", "2026-05-01T00:00:00Z", 0
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM concerts WHERE id = 'c1'");
        assertThat(rows).hasSize(1);
        // The new schema has no ticket_url column
        assertThat(rows.get(0)).doesNotContainKey("ticket_url");
    }

    @Test
    void findAll_returnsConcertWithoutTicketUrl() {
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, updated_at) VALUES (?,?,?)",
            "a1", "Artist One", "2026-05-01T00:00:00Z"
        );
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?)",
            "c-future", "sala1", "2026-06-15", "21:00", "20€", "https://source", "2026-05-01T00:00:00Z", 0
        );
        jdbcTemplate.update(
            "INSERT INTO concert_artists(concert_id, artist_id, position) VALUES (?,?,?)",
            "c-future", "a1", 0
        );

        List<Concert> result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("c-future");
        assertThat(result.get(0).price()).isEqualTo("20€");
    }
}
