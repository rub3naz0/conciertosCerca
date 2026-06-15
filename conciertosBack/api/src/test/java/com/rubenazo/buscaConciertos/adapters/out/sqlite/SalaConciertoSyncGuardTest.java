package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the sync-guard status filter (Option B) on SalaConciertoSqliteAdapter.
 * A severe row with status 'approved' or 'auto_approved' MUST NOT block sync.
 * A severe row with status 'missing' or 'auto_found' MUST still block sync.
 */
class SalaConciertoSyncGuardTest {

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
                suggested TEXT, source TEXT, score REAL, updated_at TEXT NOT NULL,
                UNIQUE(entity_type, entity_id, field)
            )
            """);

        adapter = new SalaConciertoSqliteAdapter(jdbcTemplate);
    }

    private void insertSala(String id) {
        jdbcTemplate.update(
            "INSERT INTO salas_concierto(id, name, city, province, updated_at) VALUES (?,?,?,?,?)",
            id, "Sala Test", "Madrid", "Madrid", "2026-06-01T00:00:00Z"
        );
    }

    private void insertDqRow(String entityId, String field, String status, String severity) {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "sala", entityId, field, status, severity, "2026-06-01T00:00:00Z"
        );
    }

    // --- Sala with severe+approved IS returned (resolved row no longer blocks) ---

    @Test
    void findAll_includesSalaWhenSevereRowIsApproved() {
        insertSala("sala-filled");
        insertDqRow("sala-filled", "address", "approved", "severe");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("sala-filled");
    }

    // --- Sala with severe+auto_approved IS returned ---

    @Test
    void findAll_includesSalaWhenSevereRowIsAutoApproved() {
        insertSala("sala-auto");
        insertDqRow("sala-auto", "address", "auto_approved", "severe");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("sala-auto");
    }

    // --- Sala with severe+missing is NOT returned ---

    @Test
    void findAll_excludesSalaWhenSevereRowIsMissing() {
        insertSala("sala-partial");
        insertDqRow("sala-partial", "address", "missing", "severe");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    // --- Sala with severe+auto_found is NOT returned ---

    @Test
    void findAll_excludesSalaWhenSevereRowIsAutoFound() {
        insertSala("sala-autoFound");
        insertDqRow("sala-autoFound", "address", "auto_found", "severe");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    // --- Sala with mixed severe rows (one approved, one missing) stays hidden ---

    @Test
    void findAll_excludesSalaWithMixedSevereRowsOneStillMissing() {
        insertSala("sala-mixed");
        insertDqRow("sala-mixed", "address", "approved", "severe");
        insertDqRow("sala-mixed", "lat", "missing", "severe");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    // --- Sala with only non-severe+missing IS returned ---

    @Test
    void findAll_includesSalaWithOnlyNonSevereUnresolvedRow() {
        insertSala("sala-ok");
        insertDqRow("sala-ok", "description", "missing", "non_severe");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("sala-ok");
    }

    // --- findModifiedAfter: sala with severe+approved IS returned ---

    @Test
    void findModifiedAfter_includesSalaWhenSevereRowIsApproved() {
        insertSala("sala-since");
        insertDqRow("sala-since", "address", "approved", "severe");

        var result = adapter.findModifiedAfter(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result).hasSize(1);
    }
}
