package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the sync-guard status filter (Option B) on ConcertSqliteAdapter.
 * ALL THREE severe NOT EXISTS clauses must be guarded:
 *   (a) concert-level severe row blocks
 *   (b) sala-level severe row blocks
 *   (c) all-artists severe row blocks (no clean artist)
 *   (d) each becomes unblocked when severe row becomes approved
 *   (e) concert with no artist link is excluded
 *   (f) concert with one clean artist is included
 */
class ConcertSyncGuardTest {

    // Use a date in the future so concerts are not filtered by date
    private static final String FUTURE_DATE = "2099-12-31";
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
                image_url TEXT, website TEXT, description TEXT, source_url TEXT, updated_at TEXT NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS concerts (
                id TEXT PRIMARY KEY, sala_concierto_id TEXT NOT NULL,
                date TEXT NOT NULL, time TEXT, price TEXT,
                source_url TEXT NOT NULL, updated_at TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS concert_artists (
                concert_id TEXT NOT NULL,
                artist_id TEXT NOT NULL,
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
                suggested TEXT, source TEXT, score REAL, updated_at TEXT NOT NULL,
                UNIQUE(entity_type, entity_id, field)
            )
            """);

        adapter = new ConcertSqliteAdapter(jdbcTemplate, FIXED_CLOCK);
    }

    private void insertSala(String id) {
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, updated_at) VALUES (?,?,?,?,?)",
            id, "Test Sala", "Madrid", "Madrid", "2026-06-01T00:00:00Z"
        );
    }

    private void insertArtist(String id) {
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, updated_at) VALUES (?,?,?)",
            id, "Test Artist", "2026-06-01T00:00:00Z"
        );
    }

    private void insertConcert(String id, String salaId) {
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, source_url, updated_at) VALUES (?,?,?,?,?)",
            id, salaId, FUTURE_DATE, "https://source.com", "2026-06-01T00:00:00Z"
        );
    }

    private void linkArtist(String concertId, String artistId) {
        jdbcTemplate.update(
            "INSERT INTO concert_artists(concert_id, artist_id, position) VALUES (?,?,0)",
            concertId, artistId
        );
    }

    private void insertDq(String entityType, String entityId, String field, String status) {
        insertDq(entityType, entityId, field, status, "2026-06-01T00:00:00Z");
    }

    private void insertDq(String entityType, String entityId, String field, String status, String updatedAt) {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            entityType, entityId, field, status, "severe", updatedAt
        );
    }

    private void updateDqStatus(String entityType, String entityId, String field, String newStatus) {
        jdbcTemplate.update(
            "UPDATE data_quality SET status=? WHERE entity_type=? AND entity_id=? AND field=?",
            newStatus, entityType, entityId, field
        );
    }

    // ------------------------------------------------------------------
    // (a) Concert blocked by concert-level severe+missing
    // ------------------------------------------------------------------

    @Test
    void findAll_excludesConcertWhenConcertLevelSevereIsMissing() {
        insertSala("sala1");
        insertArtist("artist1");
        insertConcert("concert-a", "sala1");
        linkArtist("concert-a", "artist1");
        insertDq("concert", "concert-a", "sala_concierto_id", "missing");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_includesConcertWhenConcertLevelSevereBecomesApproved() {
        insertSala("sala1");
        insertArtist("artist1");
        insertConcert("concert-a", "sala1");
        linkArtist("concert-a", "artist1");
        insertDq("concert", "concert-a", "sala_concierto_id", "missing");

        // Simulate fill: mark as approved
        updateDqStatus("concert", "concert-a", "sala_concierto_id", "approved");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("concert-a");
    }

    // ------------------------------------------------------------------
    // (b) Concert blocked by sala-level severe+missing
    // ------------------------------------------------------------------

    @Test
    void findAll_excludesConcertWhenSalaLevelSevereIsMissing() {
        insertSala("sala-partial");
        insertArtist("artist2");
        insertConcert("concert-b", "sala-partial");
        linkArtist("concert-b", "artist2");
        insertDq("sala", "sala-partial", "address", "missing");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAllIncludingBlocked_includesConcertWhenSalaLevelSevereIsMissing() {
        insertSala("sala-partial-impact");
        insertArtist("artist-impact");
        insertConcert("concert-impact", "sala-partial-impact");
        linkArtist("concert-impact", "artist-impact");
        insertDq("sala", "sala-partial-impact", "address", "missing");

        var visible = adapter.findAll();
        var includingBlocked = adapter.findAllIncludingBlocked();

        assertThat(visible).isEmpty();
        assertThat(includingBlocked).hasSize(1);
        assertThat(includingBlocked.get(0).id()).isEqualTo("concert-impact");
    }

    @Test
    void findAll_includesConcertWhenSalaLevelSevereBecomesApproved() {
        insertSala("sala-partial");
        insertArtist("artist2");
        insertConcert("concert-b", "sala-partial");
        linkArtist("concert-b", "artist2");
        insertDq("sala", "sala-partial", "address", "missing");

        updateDqStatus("sala", "sala-partial", "address", "approved");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("concert-b");
    }

    // ------------------------------------------------------------------
    // (c) Concert blocked by all-artists severe+missing (none is clean)
    // ------------------------------------------------------------------

    @Test
    void findAll_excludesConcertWhenAllLinkedArtistsHaveUnresolvedSevere() {
        insertSala("sala2");
        insertArtist("artist-x");
        insertConcert("concert-c", "sala2");
        linkArtist("concert-c", "artist-x");
        insertDq("artist", "artist-x", "genre", "auto_found");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_includesConcertWhenArtistSevereBecomesApproved() {
        insertSala("sala2");
        insertArtist("artist-x");
        insertConcert("concert-c", "sala2");
        linkArtist("concert-c", "artist-x");
        insertDq("artist", "artist-x", "genre", "auto_found");

        updateDqStatus("artist", "artist-x", "genre", "approved");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("concert-c");
    }

    // ------------------------------------------------------------------
    // (d) Concert with one clean artist is included even if another has severe
    // ------------------------------------------------------------------

    @Test
    void findAll_includesConcertWhenAtLeastOneArtistIsClean() {
        insertSala("sala3");
        insertArtist("artist-unresolved");
        insertArtist("artist-clean");
        insertConcert("concert-d", "sala3");
        linkArtist("concert-d", "artist-unresolved");
        linkArtist("concert-d", "artist-clean");
        insertDq("artist", "artist-unresolved", "genre", "missing");
        // artist-clean has no severe rows

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("concert-d");
    }

    // ------------------------------------------------------------------
    // (e) Concert with no artist link is excluded
    // ------------------------------------------------------------------

    @Test
    void findAll_excludesConcertWithNoArtistLink() {
        insertSala("sala4");
        insertConcert("concert-e", "sala4");
        // No concert_artists row

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------
    // (f) findModifiedAfter: sala-level severe — approved → included, missing → excluded
    // ------------------------------------------------------------------

    @Test
    void findModifiedAfter_includesConcertWhenSalaSevereIsApproved() {
        insertSala("sala-ok");
        insertArtist("artist-ok");
        insertConcert("concert-f", "sala-ok");
        linkArtist("concert-f", "artist-ok");
        insertDq("sala", "sala-ok", "address", "approved");

        var result = adapter.findModifiedAfter(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result).hasSize(1);
    }

    @Test
    void findModifiedAfter_includesConcertUnblockedByRecentSalaSevereResolutionEvenWhenConcertIsOlder() {
        insertSala("sala-recently-unblocked");
        insertArtist("artist-recently-unblocked");
        insertConcert("concert-recently-unblocked", "sala-recently-unblocked");
        linkArtist("concert-recently-unblocked", "artist-recently-unblocked");
        insertDq(
            "sala",
            "sala-recently-unblocked",
            "address",
            "approved",
            "2026-06-03T00:00:00Z"
        );

        var result = adapter.findModifiedAfter(Instant.parse("2026-06-02T00:00:00Z"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("concert-recently-unblocked");
    }

    @Test
    void findModifiedAfter_excludesConcertWhenSalaLevelSevereIsMissing() {
        insertSala("sala-blocked");
        insertArtist("artist-b");
        insertConcert("concert-g", "sala-blocked");
        linkArtist("concert-g", "artist-b");
        insertDq("sala", "sala-blocked", "address", "missing");

        var result = adapter.findModifiedAfter(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result).isEmpty();
    }

    @Test
    void getDeletedIds_includesConcertBlockedByRecentSalaSevereRegression() {
        insertSala("sala-regressed");
        insertArtist("artist-regressed");
        insertConcert("concert-regressed", "sala-regressed");
        linkArtist("concert-regressed", "artist-regressed");
        insertDq("sala", "sala-regressed", "lng", "missing", "2026-06-03T00:00:00Z");

        var result = adapter.getDeletedIds(Instant.parse("2026-06-02T00:00:00Z"));

        assertThat(result).containsExactly("concert-regressed");
    }

    @Test
    void getDeletedIds_includesCurrentlyBlockedConcertEvenWhenSevereRowPredatesSince() {
        insertSala("sala-stale-blocked");
        insertArtist("artist-stale-blocked");
        insertConcert("concert-stale-blocked", "sala-stale-blocked");
        linkArtist("concert-stale-blocked", "artist-stale-blocked");
        insertDq("sala", "sala-stale-blocked", "lat", "missing", "2026-06-01T00:00:00Z");

        var result = adapter.getDeletedIds(Instant.parse("2026-06-02T00:00:00Z"));

        assertThat(result).containsExactly("concert-stale-blocked");
    }

    // ------------------------------------------------------------------
    // (g) findModifiedAfter: concert-level severe — missing → excluded, approved → included
    // ------------------------------------------------------------------

    @Test
    void findModifiedAfter_excludesConcertWhenConcertLevelSevereIsMissing() {
        insertSala("sala5");
        insertArtist("artist-c");
        insertConcert("concert-h", "sala5");
        linkArtist("concert-h", "artist-c");
        insertDq("concert", "concert-h", "sala_concierto_id", "missing");

        var result = adapter.findModifiedAfter(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result).isEmpty();
    }

    @Test
    void findModifiedAfter_includesConcertWhenConcertLevelSevereBecomesApproved() {
        insertSala("sala5");
        insertArtist("artist-c");
        insertConcert("concert-h", "sala5");
        linkArtist("concert-h", "artist-c");
        insertDq("concert", "concert-h", "sala_concierto_id", "missing");

        updateDqStatus("concert", "concert-h", "sala_concierto_id", "approved");

        var result = adapter.findModifiedAfter(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("concert-h");
    }

    // ------------------------------------------------------------------
    // (h) findModifiedAfter: all-artists-blocked — missing → excluded, approved → included
    // ------------------------------------------------------------------

    @Test
    void findModifiedAfter_excludesConcertWhenAllLinkedArtistsHaveUnresolvedSevere() {
        insertSala("sala6");
        insertArtist("artist-y");
        insertConcert("concert-i", "sala6");
        linkArtist("concert-i", "artist-y");
        insertDq("artist", "artist-y", "genre", "auto_found");

        var result = adapter.findModifiedAfter(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result).isEmpty();
    }

    @Test
    void findModifiedAfter_includesConcertWhenArtistSevereBecomesApproved() {
        insertSala("sala6");
        insertArtist("artist-y");
        insertConcert("concert-i", "sala6");
        linkArtist("concert-i", "artist-y");
        insertDq("artist", "artist-y", "genre", "auto_found");

        updateDqStatus("artist", "artist-y", "genre", "approved");

        var result = adapter.findModifiedAfter(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("concert-i");
    }
}
