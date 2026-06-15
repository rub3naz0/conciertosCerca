package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SalaConciertoSqliteAdapterTest {

    private JdbcTemplate jdbcTemplate;
    private SalaConciertoSqliteAdapter adapter;

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
            CREATE TABLE IF NOT EXISTS data_quality (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL, entity_id TEXT NOT NULL,
                field TEXT NOT NULL, status TEXT NOT NULL,
                severity TEXT NOT NULL DEFAULT 'non_severe',
                suggested TEXT, source TEXT, updated_at TEXT NOT NULL,
                UNIQUE(entity_type, entity_id, field)
            )
            """);

        adapter = new SalaConciertoSqliteAdapter(jdbcTemplate);
    }

    private void insertSala(String id, String updatedAt) {
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, updated_at) VALUES (?,?,?,?,?)",
            id, "Sala Test", "Madrid", "Madrid", updatedAt
        );
    }

    private void insertSevereQuality(String entityId, String field) {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "sala", entityId, field, "missing", "severe", "2026-05-01T00:00:00Z"
        );
    }

    // --- Existing tests ---

    @Test
    void findAll_returnsAllSalas() {
        insertSala("s1", "2026-05-20T15:30:00Z");
        insertSala("s2", "2026-05-21T10:00:00Z");

        var result = adapter.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting("id").containsExactlyInAnyOrder("s1", "s2");
    }

    @Test
    void findAll_returnsEmptyWhenNoData() {
        assertThat(adapter.findAll()).isEmpty();
    }

    @Test
    void findModifiedAfter_returnsOnlyModifiedSince() {
        insertSala("s1", "2026-05-17T00:00:00Z");
        insertSala("s2", "2026-05-19T00:00:00Z");

        Instant since = Instant.parse("2026-05-18T00:00:00Z");
        var result = adapter.findModifiedAfter(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("s2");
    }

    @Test
    void findModifiedAfter_includesExactTimestamp() {
        Instant since = Instant.parse("2026-05-18T00:00:00Z");
        insertSala("s1", since.toString());

        var result = adapter.findModifiedAfter(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("s1");
    }

    @Test
    void findAll_returnsDescriptionField() {
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, description, updated_at) VALUES (?,?,?,?,?,?)",
            "s1", "Sala Apolo", "Barcelona", "Barcelona", "A historic venue", "2026-05-20T15:30:00Z"
        );

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description()).isEqualTo("A historic venue");
    }

    // --- Phase 4.3 [RED]: eligibility filtering for salas ---

    @Test
    void findAll_excludesSalaWithSevereDataQuality() {
        insertSala("s1", "2026-05-20T15:30:00Z");
        insertSevereQuality("s1", "address");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_includesSalaWithoutSevereDataQuality() {
        insertSala("s1", "2026-05-20T15:30:00Z");
        // Non-severe data quality — should not filter out
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "sala", "s1", "image_url", "missing", "non_severe", "2026-05-01T00:00:00Z"
        );

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("s1");
    }

    @Test
    void findModifiedAfter_excludesSevere() {
        insertSala("s1", "2026-05-20T15:30:00Z");
        insertSala("s2", "2026-05-20T15:30:00Z");
        insertSevereQuality("s1", "address");

        Instant since = Instant.parse("2026-05-18T00:00:00Z");
        var result = adapter.findModifiedAfter(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("s2");
    }

    @Test
    void findAll_includesSalaWithNoDataQuality() {
        insertSala("s1", "2026-05-20T15:30:00Z");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
    }

    // --- Phase 2.1 [RED]: updateAll (admin edit) ---

    @Test
    void updateAll_updatesAllEditableColumnsAndUpdatedAt() {
        insertSala("s1", "2026-05-20T15:30:00Z");
        Instant updatedAt = Instant.parse("2026-06-12T10:00:00Z");
        var edited = new com.rubenazo.buscaConciertos.domain.SalaConcierto(
            "s1", "New Name", "New Address", "New City", "New Province",
            41.5, -2.5, "https://example.com/img.jpg", "New description",
            "manual", updatedAt
        );

        adapter.updateAll(edited);

        var result = adapter.findByIdIncludingBlocked("s1").orElseThrow();
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.address()).isEqualTo("New Address");
        assertThat(result.city()).isEqualTo("New City");
        assertThat(result.province()).isEqualTo("New Province");
        assertThat(result.lat()).isEqualTo(41.5);
        assertThat(result.lng()).isEqualTo(-2.5);
        assertThat(result.description()).isEqualTo("New description");
        assertThat(result.imageUrl()).isEqualTo("https://example.com/img.jpg");
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void updateAll_mapsImageUrlToImageUrlColumn() {
        insertSala("s1", "2026-05-20T15:30:00Z");
        Instant updatedAt = Instant.parse("2026-06-12T10:00:00Z");
        var edited = new com.rubenazo.buscaConciertos.domain.SalaConcierto(
            "s1", "Sala Test", "Calle 1", "Madrid", "Madrid",
            null, null, "https://example.com/img.jpg", null,
            "manual", updatedAt
        );

        adapter.updateAll(edited);

        String imageUrl = jdbcTemplate.queryForObject(
            "SELECT image_url FROM salas_concierto WHERE id = ?", String.class, "s1");
        assertThat(imageUrl).isEqualTo("https://example.com/img.jpg");
    }
}
