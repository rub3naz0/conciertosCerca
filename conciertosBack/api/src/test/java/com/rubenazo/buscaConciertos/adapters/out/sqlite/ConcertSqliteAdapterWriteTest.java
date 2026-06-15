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

class ConcertSqliteAdapterWriteTest {

    private static final Clock FIXED_CLOCK =
        Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("UTC"));

    private JdbcTemplate jdbcTemplate;
    private ConcertSqliteAdapter adapter;

    @BeforeEach
    void setUp() {
        var ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(ds);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS salas_concierto (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, address TEXT,
                city TEXT NOT NULL, province TEXT NOT NULL,
                lat REAL, lng REAL, description TEXT, image_url TEXT,
                source_url TEXT, updated_at TEXT NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS artists (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, genre TEXT,
                image_url TEXT, website TEXT, source_url TEXT, updated_at TEXT NOT NULL
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
            CREATE TABLE IF NOT EXISTS data_quality (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL, entity_id TEXT NOT NULL,
                field TEXT NOT NULL, status TEXT NOT NULL,
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

    private Concert buildConcert(String id, List<String> artistIds) {
        return new Concert(
            id, "sala1", artistIds,
            LocalDate.parse("2026-07-15"), "21:00", "25€",
            "https://source.test/" + id,
            Instant.parse("2026-05-20T10:00:00Z")
        );
    }

    @Test
    void upsert_insertsNewConcert() {
        Concert concert = buildConcert("c1", List.of());

        adapter.upsert(concert);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM concerts WHERE id = 'c1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("id", "c1");
        assertThat(rows.get(0)).containsEntry("sala_concierto_id", "sala1");
        assertThat(rows.get(0)).containsEntry("date", "2026-07-15");
        assertThat(((Number) rows.get(0).get("deleted")).intValue()).isEqualTo(0);
        // ticket_url must not exist
        assertThat(rows.get(0)).doesNotContainKey("ticket_url");
    }

    @Test
    void upsert_updatesExistingConcert() {
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?)",
            "c1", "sala1", "2026-07-10", "20:00", "15€", "https://old.source", "2026-01-01T00:00:00Z", 0
        );

        Concert updated = new Concert(
            "c1", "sala1", List.of(),
            LocalDate.parse("2026-07-15"), "21:00", "30€",
            "https://new.source",
            Instant.parse("2026-05-20T10:00:00Z")
        );

        adapter.upsert(updated);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM concerts WHERE id = 'c1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("date", "2026-07-15");
        assertThat(rows.get(0)).containsEntry("source_url", "https://new.source");
        assertThat(rows.get(0).get("price")).isEqualTo("30€");
    }

    @Test
    void upsert_insertsConcertArtistsWithCorrectPositions() {
        Concert concert = buildConcert("c1", List.of("a1", "a2", "a3"));

        adapter.upsert(concert);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM concert_artists WHERE concert_id = 'c1' ORDER BY position"
        );
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("artist_id", "a1");
        assertThat(((Number) rows.get(0).get("position")).intValue()).isEqualTo(0);
        assertThat(rows.get(1)).containsEntry("artist_id", "a2");
        assertThat(((Number) rows.get(1).get("position")).intValue()).isEqualTo(1);
        assertThat(rows.get(2)).containsEntry("artist_id", "a3");
        assertThat(((Number) rows.get(2).get("position")).intValue()).isEqualTo(2);
    }

    @Test
    void upsert_replacesConcertArtistsOnReUpsert() {
        Concert original = buildConcert("c1", List.of("a1", "a2"));
        adapter.upsert(original);

        Concert updated = buildConcert("c1", List.of("a3"));
        adapter.upsert(updated);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM concert_artists WHERE concert_id = 'c1'"
        );
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("artist_id", "a3");
    }

    @Test
    void upsert_resetsDeletedFlagToZero() {
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?)",
            "c1", "sala1", "2026-07-15", "21:00", "25€", "https://source", "2026-01-01T00:00:00Z", 1
        );

        Concert concert = buildConcert("c1", List.of());
        adapter.upsert(concert);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT deleted FROM concerts WHERE id = 'c1'");
        assertThat(((Number) rows.get(0).get("deleted")).intValue()).isEqualTo(0);
    }

    @Test
    void markDeleted_setsDeletedFlag_returnsOne() {
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?)",
            "c1", "sala1", "2026-07-15", "21:00", "25€", "https://source", "2026-05-01T00:00:00Z", 0
        );

        int affected = adapter.markDeleted("c1");

        assertThat(affected).isEqualTo(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT deleted, updated_at FROM concerts WHERE id = ?", "c1"
        );
        assertThat(((Number) rows.get(0).get("deleted")).intValue()).isEqualTo(1);
        assertThat(rows.get(0).get("updated_at")).isEqualTo("2026-06-01T00:00:00Z");
    }

    @Test
    void markDeleted_returnsZeroForNonExistentConcert() {
        int affected = adapter.markDeleted("does-not-exist");

        assertThat(affected).isEqualTo(0);
    }

    @Test
    void markDeleted_returnsOneForAlreadyDeletedRow() {
        // D3 gate: SQLite UPDATE match-count must be 1 for a row that is already deleted=1.
        // This locks the already-deleted=204 (idempotent) contract in ManualCrudUseCase.
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?)",
            "c-already-deleted", "sala1", "2026-07-15", "21:00", "25€", "https://source", "2026-05-01T00:00:00Z", 1
        );

        int affected = adapter.markDeleted("c-already-deleted");

        assertThat(affected).isEqualTo(1);
    }

    // deleteBeforeDate integration tests

    @Test
    void deleteBeforeDate_deletesPastConcertsAcrossThreeTables() {
        LocalDate today = LocalDate.parse("2026-06-01"); // matches FIXED_CLOCK
        String yesterday = "2026-05-31";

        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?)",
            "past-c1", "sala1", yesterday, "21:00", "20€", "https://s", "2026-05-01T00:00:00Z", 0
        );
        jdbcTemplate.update(
            "INSERT INTO concert_artists(concert_id, artist_id, position) VALUES (?,?,?)",
            "past-c1", "a1", 0
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, updated_at) VALUES (?,?,?,?,?)",
            "concert", "past-c1", "price", "missing", "2026-05-01T00:00:00Z"
        );

        int deleted = adapter.deleteBeforeDate(today);

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM concerts", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM concert_artists", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM data_quality WHERE entity_type='concert'", Integer.class)).isZero();
    }

    @Test
    void deleteBeforeDate_doesNotDeleteTodayOrFutureConcerts() {
        LocalDate today = LocalDate.parse("2026-06-01"); // matches FIXED_CLOCK

        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?)",
            "past-c1", "sala1", "2026-05-31", "21:00", "20€", "https://s", "2026-05-01T00:00:00Z", 0
        );
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?)",
            "future-c1", "sala1", "2026-06-15", "21:00", "20€", "https://s", "2026-05-01T00:00:00Z", 0
        );

        adapter.deleteBeforeDate(today);

        List<Map<String, Object>> remaining = jdbcTemplate.queryForList("SELECT id FROM concerts");
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0)).containsEntry("id", "future-c1");
    }

    @Test
    void deleteBeforeDate_returnsZeroWhenNothingToDelete() {
        LocalDate today = LocalDate.parse("2026-06-01");

        int deleted = adapter.deleteBeforeDate(today);

        assertThat(deleted).isZero();
    }
}
