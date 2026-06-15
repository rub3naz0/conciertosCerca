package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the sync-guard status filter (Option B) on ArtistSqliteAdapter.
 * A severe row with status 'approved' or 'auto_approved' MUST NOT block sync.
 * A severe row with status 'missing' or 'auto_found' MUST still block sync.
 */
class ArtistSyncGuardTest {

    private JdbcTemplate jdbcTemplate;
    private ArtistSqliteAdapter adapter;

    @BeforeEach
    void setUp() {
        var ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(ds);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS artists (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, genre TEXT,
                image_url TEXT, website TEXT, description TEXT,
                source_url TEXT, updated_at TEXT NOT NULL
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

        adapter = new ArtistSqliteAdapter(jdbcTemplate);
    }

    private void insertArtist(String id) {
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, updated_at) VALUES (?,?,?)",
            id, "Test Artist", "2026-06-01T00:00:00Z"
        );
    }

    private void insertDqRow(String entityId, String field, String status, String severity) {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", entityId, field, status, severity, "2026-06-01T00:00:00Z"
        );
    }

    // --- Artist with severe+approved IS returned ---

    @Test
    void findAll_includesArtistWhenSevereRowIsApproved() {
        insertArtist("banda-y");
        insertDqRow("banda-y", "genre", "approved", "severe");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("banda-y");
    }

    // --- Artist with severe+auto_found is NOT returned ---

    @Test
    void findAll_excludesArtistWhenSevereRowIsAutoFound() {
        insertArtist("banda-x");
        insertDqRow("banda-x", "genre", "auto_found", "severe");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    // --- Artist with severe+missing is NOT returned ---

    @Test
    void findAll_excludesArtistWhenSevereRowIsMissing() {
        insertArtist("banda-z");
        insertDqRow("banda-z", "genre", "missing", "severe");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    // --- Artist with severe+auto_approved IS returned ---

    @Test
    void findAll_includesArtistWhenSevereRowIsAutoApproved() {
        insertArtist("banda-w");
        insertDqRow("banda-w", "genre", "auto_approved", "severe");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
    }

    // --- findModifiedAfter respects status filter too ---

    @Test
    void findModifiedAfter_includesArtistWhenSevereRowIsApproved() {
        insertArtist("banda-since");
        insertDqRow("banda-since", "genre", "approved", "severe");

        var result = adapter.findModifiedAfter(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result).hasSize(1);
    }
}
