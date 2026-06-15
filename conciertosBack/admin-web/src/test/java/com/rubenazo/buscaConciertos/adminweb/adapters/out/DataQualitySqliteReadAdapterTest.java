package com.rubenazo.buscaConciertos.adminweb.adapters.out;

import com.rubenazo.buscaConciertos.adminweb.application.SevereIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataQualitySqliteReadAdapterTest {

    private JdbcTemplate jdbcTemplate;
    private DataQualitySqliteReadAdapter adapter;

    @BeforeEach
    void setUp() {
        var ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(ds);

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
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS concerts (
                id TEXT PRIMARY KEY,
                sala_concierto_id TEXT NOT NULL,
                date TEXT NOT NULL,
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

        adapter = new DataQualitySqliteReadAdapter(jdbcTemplate);
    }

    private void insertDq(String entityType, String entityId, String field, String status, String severity) {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            entityType, entityId, field, status, severity, "2026-06-01T00:00:00Z"
        );
    }

    private void insertConcert(String id, String salaId, String date) {
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, deleted) VALUES (?,?,?,0)",
            id, salaId, date
        );
    }

    private void linkArtist(String concertId, String artistId) {
        jdbcTemplate.update(
            "INSERT INTO concert_artists(concert_id, artist_id, position) VALUES (?,?,0)",
            concertId, artistId
        );
    }

    // --- severe+missing with future concerts must appear ---

    @Test
    void listUnresolvedSevere_includesSevereMissing() {
        insertDq("sala", "s1", "address", "missing", "severe");
        insertConcert("c1", "s1", "2099-01-01");

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).entityId()).isEqualTo("s1");
    }

    // --- severe+auto_found with future concerts must appear ---

    @Test
    void listUnresolvedSevere_includesSevereAutoFound() {
        insertDq("artist", "a1", "genre", "auto_found", "severe");
        insertConcert("c1", "some-sala", "2099-01-01");
        linkArtist("c1", "a1");

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).entityId()).isEqualTo("a1");
    }

    // --- severe+approved must NOT appear (even with future concerts) ---

    @Test
    void listUnresolvedSevere_excludesSevereApproved() {
        insertDq("sala", "s2", "address", "approved", "severe");
        insertConcert("c1", "s2", "2099-01-01");

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result).isEmpty();
    }

    // --- severe+auto_approved must NOT appear (even with future concerts) ---

    @Test
    void listUnresolvedSevere_excludesSevereAutoApproved() {
        insertDq("artist", "a2", "genre", "auto_approved", "severe");
        insertConcert("c1", "some-sala", "2099-01-01");
        linkArtist("c1", "a2");

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result).isEmpty();
    }

    // --- non_severe+missing must NOT appear (even with future concerts) ---

    @Test
    void listUnresolvedSevere_excludesNonSevereMissing() {
        insertDq("sala", "s3", "description", "missing", "non_severe");
        insertConcert("c1", "s3", "2099-01-01");

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result).isEmpty();
    }

    // --- zero impact: no future concerts depend on the entity, row stays hidden ---

    @Test
    void listUnresolvedSevere_excludesSalaWithOnlyPastConcerts() {
        insertDq("sala", "s1", "address", "missing", "severe");
        insertConcert("past", "s1", "2000-01-01");

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result).isEmpty();
    }

    @Test
    void listUnresolvedSevere_excludesArtistWithoutFutureConcerts() {
        insertDq("artist", "a1", "genre", "missing", "severe");

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result).isEmpty();
    }

    @Test
    void listUnresolvedSevere_excludesDeletedConcertFromImpact() {
        insertDq("sala", "s1", "address", "missing", "severe");
        jdbcTemplate.update(
            "INSERT INTO concerts(id, sala_concierto_id, date, deleted) VALUES (?,?,?,1)",
            "c-del", "s1", "2099-01-01"
        );

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result).isEmpty();
    }

    // --- mixed: only severe+unresolved rows with impact returned ---

    @Test
    void listUnresolvedSevere_returnOnlySevereUnresolvedRows() {
        insertDq("sala", "s1", "address", "missing", "severe");           // appears
        insertDq("sala", "s2", "address", "approved", "severe");          // excluded: status
        insertDq("artist", "a1", "genre", "missing", "non_severe");       // excluded: severity
        insertDq("artist", "a2", "genre", "auto_found", "severe");        // appears
        insertDq("sala", "s4", "address", "missing", "severe");           // excluded: zero impact
        insertConcert("c1", "s1", "2099-01-01");
        insertConcert("c2", "other-sala", "2099-01-02");
        linkArtist("c2", "a2");

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SevereIssue::entityId)
            .containsExactlyInAnyOrder("s1", "a2");
    }

    // --- severity field is correctly mapped ---

    @Test
    void listUnresolvedSevere_mapsSeverityField() {
        insertDq("sala", "s1", "address", "missing", "severe");
        insertConcert("c1", "s1", "2099-01-01");

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result.get(0).severity()).isEqualTo("severe");
    }

    @Test
    void listUnresolvedSevere_ordersByBlockedConcertCountDescending() {
        insertDq("sala", "low-impact", "address", "missing", "severe");
        insertDq("sala", "high-impact", "address", "missing", "severe");
        insertDq("artist", "medium-impact", "genre", "missing", "severe");
        insertConcert("c1", "high-impact", "2099-01-01");
        insertConcert("c2", "high-impact", "2099-01-02");
        insertConcert("c3", "low-impact", "2099-01-03");
        insertConcert("c4", "other-sala", "2099-01-04");
        linkArtist("c4", "medium-impact");
        insertConcert("past", "high-impact", "2000-01-01");

        List<SevereIssue> result = adapter.listUnresolvedSevere();

        assertThat(result).extracting(SevereIssue::entityId)
            .containsExactly("high-impact", "low-impact", "medium-impact");
        assertThat(result).extracting(SevereIssue::blockedConcertCount)
            .containsExactly(2, 1, 1);
    }
}
