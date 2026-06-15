package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ConcertSqliteAdapterTest {

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

        insertSala("sala1");
        adapter = new ConcertSqliteAdapter(jdbcTemplate, FIXED_CLOCK);
    }

    private void insertSala(String id) {
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, updated_at) VALUES (?,?,?,?,?)",
            id, "Sala Test", "Madrid", "Madrid", "2026-05-01T00:00:00Z"
        );
    }

    private void insertArtist(String id) {
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, updated_at) VALUES (?,?,?)",
            id, "Artist " + id, "2026-05-01T00:00:00Z"
        );
    }

    private void insertConcert(String id, String date, String updatedAt, int deleted) {
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?)",
            id, "sala1", date, "21:00", "25€", "http://source", updatedAt, deleted
        );
    }

    private void insertConcertWithSala(String id, String salaId, String date) {
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, time, price, source_url, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?)",
            id, salaId, date, "21:00", "25€", "http://source", "2026-05-01T00:00:00Z", 0
        );
    }

    private void insertSevereQuality(String entityType, String entityId, String field) {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            entityType, entityId, field, "missing", "severe", "2026-05-01T00:00:00Z"
        );
    }

    private void insertConcertArtist(String concertId, String artistId) {
        jdbcTemplate.update(
            "INSERT INTO concert_artists(concert_id, artist_id, position) VALUES (?,?,?)",
            concertId, artistId, 0
        );
    }

    // --- Existing tests ---

    @Test
    void findAll_returnsOnlyFutureConcerts() {
        insertConcert("c-past", "2026-05-30", "2026-05-01T00:00:00Z", 0);
        insertConcert("c-future", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        // future concert needs an artist link to pass eligibility
        insertArtist("a1");
        insertConcertArtist("c-future", "a1");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("c-future");
    }

    @Test
    void findAll_excludesDeletedConcerts() {
        insertArtist("a1");
        insertConcert("c-active", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        insertConcert("c-deleted", "2026-06-20", "2026-05-01T00:00:00Z", 1);
        insertConcertArtist("c-active", "a1");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("c-active");
    }

    @Test
    void findAll_returnsEmptyWhenNoData() {
        assertThat(adapter.findAll()).isEmpty();
    }

    @Test
    void findAll_includesConcertsOnToday() {
        insertArtist("a1");
        insertConcert("c-today", "2026-06-01", "2026-05-01T00:00:00Z", 0);
        insertConcertArtist("c-today", "a1");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("c-today");
    }

    @Test
    void findModifiedAfter_filtersByDateAndUpdatedAt() {
        insertArtist("a1");
        insertConcert("c-old-future", "2026-06-15", "2026-05-10T00:00:00Z", 0);
        insertConcert("c-new-future", "2026-06-20", "2026-05-25T00:00:00Z", 0);
        insertConcert("c-new-past", "2026-05-30", "2026-05-25T00:00:00Z", 0);
        insertConcertArtist("c-new-future", "a1");

        Instant since = Instant.parse("2026-05-15T00:00:00Z");
        var result = adapter.findModifiedAfter(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("c-new-future");
    }

    @Test
    void findModifiedAfter_excludesDeletedConcerts() {
        insertArtist("a1");
        insertConcert("c-active", "2026-06-15", "2026-05-25T00:00:00Z", 0);
        insertConcert("c-deleted", "2026-06-20", "2026-05-25T00:00:00Z", 1);
        insertConcertArtist("c-active", "a1");

        Instant since = Instant.parse("2026-05-15T00:00:00Z");
        var result = adapter.findModifiedAfter(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("c-active");
    }

    @Test
    void getDeletedIds_nullSince_returnsAllDeletedIds() {
        insertConcert("c-active1", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        insertConcert("c-active2", "2026-06-20", "2026-05-01T00:00:00Z", 0);
        insertConcert("c-deleted", "2026-06-25", "2026-05-01T00:00:00Z", 1);

        var result = adapter.getDeletedIds(null);

        assertThat(result).containsExactly("c-deleted");
    }

    @Test
    void getDeletedIds_nullSince_returnsEmptyWhenNoneDeleted() {
        insertConcert("c-active", "2026-06-15", "2026-05-01T00:00:00Z", 0);

        assertThat(adapter.getDeletedIds(null)).isEmpty();
    }

    @Test
    void getDeletedIds_withSince_returnsOnlyDeletedAfterSince() {
        insertConcert("c-deleted-old", "2026-06-25", "2026-05-10T00:00:00Z", 1);
        insertConcert("c-deleted-new", "2026-06-26", "2026-05-25T00:00:00Z", 1);

        Instant since = Instant.parse("2026-05-15T00:00:00Z");
        var result = adapter.getDeletedIds(since);

        assertThat(result).containsExactly("c-deleted-new");
    }

    @Test
    void getDeletedIds_withSince_excludesActiveEvenIfUpdatedAfterSince() {
        insertConcert("c-active-new", "2026-06-15", "2026-05-25T00:00:00Z", 0);
        insertConcert("c-deleted-new", "2026-06-20", "2026-05-25T00:00:00Z", 1);

        Instant since = Instant.parse("2026-05-15T00:00:00Z");
        var result = adapter.getDeletedIds(since);

        assertThat(result).containsExactly("c-deleted-new");
    }

    @Test
    void existsActiveById_trueForActiveConcert() {
        insertConcert("c-active", "2026-06-15", "2026-05-01T00:00:00Z", 0);

        assertThat(adapter.existsActiveById("c-active")).isTrue();
    }

    @Test
    void existsActiveById_falseForDeletedConcert() {
        insertConcert("c-deleted", "2026-06-15", "2026-05-01T00:00:00Z", 1);

        assertThat(adapter.existsActiveById("c-deleted")).isFalse();
    }

    @Test
    void existsActiveById_falseForMissingConcert() {
        assertThat(adapter.existsActiveById("c-missing")).isFalse();
    }

    @Test
    void findAll_returnsArtistIdsFromConcertArtistsTable() {
        insertArtist("a1");
        insertArtist("a2");
        insertConcert("c1", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        insertConcertArtist("c1", "a1");
        insertConcertArtist("c1", "a2");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        var concert = result.get(0);
        assertThat(concert.artistIds()).containsExactly("a1", "a2");
    }

    // --- Phase 4.1 [RED]: eligibility filtering ---

    @Test
    void findAll_excludesConcertWithSevereDataQuality() {
        insertArtist("a1");
        insertConcert("c1", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        insertConcertArtist("c1", "a1");
        // Concert itself has a severe quality issue
        insertSevereQuality("concert", "c1", "some_field");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_excludesConcertWhenSalaHasSevereDataQuality() {
        insertArtist("a1");
        insertConcert("c1", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        insertConcertArtist("c1", "a1");
        // Parent sala has severe quality issue
        insertSevereQuality("sala", "sala1", "address");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_excludesConcertWithNoArtistLink() {
        insertConcert("c1", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        // No concert_artists rows for c1

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_includesConcertWhenAtLeastOneArtistIsClean() {
        insertArtist("a-dirty");
        insertArtist("a-clean");
        insertConcert("c1", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        insertConcertArtist("c1", "a-dirty");
        insertConcertArtist("c1", "a-clean");
        // Only a-dirty has severe issue
        insertSevereQuality("artist", "a-dirty", "genre");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("c1");
    }

    @Test
    void findAll_excludesConcertWhenAllArtistsHaveSevereIssues() {
        insertArtist("a1");
        insertArtist("a2");
        insertConcert("c1", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        insertConcertArtist("c1", "a1");
        insertConcertArtist("c1", "a2");
        insertSevereQuality("artist", "a1", "genre");
        insertSevereQuality("artist", "a2", "genre");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findModifiedAfter_appliesSameEligibilityFilters() {
        insertArtist("a1");
        insertConcert("c-eligible", "2026-06-15", "2026-05-25T00:00:00Z", 0);
        insertConcert("c-ineligible", "2026-06-20", "2026-05-25T00:00:00Z", 0);
        insertConcertArtist("c-eligible", "a1");
        // c-ineligible has no artist link
        insertSevereQuality("concert", "c-ineligible", "some_field");

        Instant since = Instant.parse("2026-05-15T00:00:00Z");
        var result = adapter.findModifiedAfter(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("c-eligible");
    }

    // --- Phase 2.7 [RED]: findByIdIncludingDeleted (admin edit) ---

    @Test
    void findByIdIncludingDeleted_returnsDeletedConcert() {
        insertConcert("c-deleted", "2026-06-15", "2026-05-01T00:00:00Z", 1);

        var result = adapter.findByIdIncludingDeleted("c-deleted");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("c-deleted");
    }

    @Test
    void findByIdIncludingDeleted_returnsActiveConcertWithArtistIds() {
        insertArtist("a1");
        insertConcert("c-active", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        insertConcertArtist("c-active", "a1");

        var result = adapter.findByIdIncludingDeleted("c-active");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("c-active");
        assertThat(result.get().artistIds()).containsExactly("a1");
    }

    @Test
    void findByIdIncludingDeleted_returnsEmptyForMissingId() {
        var result = adapter.findByIdIncludingDeleted("c-missing");

        assertThat(result).isEmpty();
    }

    // --- Phase 3.4 [RED]: findAllIncludingBlocked excludes deleted, includes severe-blocked ---

    @Test
    void findAllIncludingBlocked_excludesDeletedConcerts() {
        insertArtist("a1");
        insertConcert("c-active", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        insertConcert("c-deleted", "2026-06-20", "2026-05-01T00:00:00Z", 1);
        insertConcertArtist("c-active", "a1");

        var result = adapter.findAllIncludingBlocked();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("c-active");
    }

    @Test
    void findAllIncludingBlocked_includesSevereBlockedConcert() {
        insertArtist("a1");
        insertConcert("c1", "2026-06-15", "2026-05-01T00:00:00Z", 0);
        insertConcertArtist("c1", "a1");
        insertSevereQuality("concert", "c1", "some_field");

        var result = adapter.findAllIncludingBlocked();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("c1");
    }
}
