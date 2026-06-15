package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: partial sala upsert idempotency + concert eligibility filtering.
 *
 * Covers task 4.7: upsert same partial sala twice -> single row; add severe row ->
 * concert excluded; remove severe row -> concert visible.
 */
class PartialSalaEligibilityIntegrationTest {

    private static final Clock FIXED_CLOCK =
        Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("UTC"));
    private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");

    private JdbcTemplate jdbcTemplate;
    private SalaConciertoSqliteAdapter salaAdapter;
    private ConcertSqliteAdapter concertAdapter;
    private DataQualitySqliteAdapter qualityAdapter;

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
                image_url TEXT, website TEXT, description TEXT, source_url TEXT, updated_at TEXT NOT NULL
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
                severity TEXT NOT NULL DEFAULT 'non_severe',
                suggested TEXT, source TEXT, updated_at TEXT NOT NULL,
                UNIQUE(entity_type, entity_id, field)
            )
            """);

        salaAdapter = new SalaConciertoSqliteAdapter(jdbcTemplate);
        concertAdapter = new ConcertSqliteAdapter(jdbcTemplate, FIXED_CLOCK);
        qualityAdapter = new DataQualitySqliteAdapter(jdbcTemplate);
    }

    @Test
    void partialSalaUpsertTwice_producesOnlyOneRow() {
        SalaConcierto partial = new SalaConcierto(
            "sala-partial", "Sala Parcial", null,
            "madrid", "madrid", null, null,
            null, null, null, NOW
        );

        salaAdapter.upsert(partial);
        salaAdapter.upsert(partial);

        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM salas_concierto WHERE id = 'sala-partial'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void concertLinkedToPartialSala_excludedWhenSevereDataQualityExists() {
        // Insert partial sala, artist, and concert with an artist link
        SalaConcierto partial = new SalaConcierto(
            "sala-partial", "Sala Parcial", null,
            "madrid", "madrid", null, null,
            null, null, null, NOW
        );
        salaAdapter.upsert(partial);

        jdbcTemplate.update(
            "INSERT INTO artists(id, name, updated_at) VALUES (?,?,?)",
            "a1", "Artist One", NOW.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?)",
            "c1", "sala-partial", "2026-06-15", "http://src", NOW.toString(), 0
        );
        jdbcTemplate.update(
            "INSERT INTO concert_artists(concert_id, artist_id, position) VALUES (?,?,?)",
            "c1", "a1", 0
        );

        // No severe data quality yet — concert is visible
        var concertsBefore = concertAdapter.findAll();
        assertThat(concertsBefore).hasSize(1).extracting("id").contains("c1");

        // Add severe data quality for the partial sala
        qualityAdapter.saveAll(List.of(
            new DataQuality(null, "sala", "sala-partial", "address", "missing", "severe", null, null, null, NOW)
        ));

        // Concert is now excluded
        var concertsAfter = concertAdapter.findAll();
        assertThat(concertsAfter).isEmpty();

        // Remove severe data quality
        jdbcTemplate.update("DELETE FROM data_quality WHERE entity_id='sala-partial'");

        // Concert is visible again
        var concertsRestored = concertAdapter.findAll();
        assertThat(concertsRestored).hasSize(1).extracting("id").contains("c1");
    }
}
