package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.domain.DataQuality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DataQualitySqliteAdapterTest {

    private JdbcTemplate jdbcTemplate;
    private DataQualitySqliteAdapter adapter;

    private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-05-28T11:00:00Z");

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

        adapter = new DataQualitySqliteAdapter(jdbcTemplate);
    }

    // --- saveAll (upsert) ---

    @Test
    void saveAll_insertsNewRows() {
        DataQuality dq = new DataQuality(null, "artist", "a1", "genre", "missing", "severe", null, null, null, NOW);

        adapter.saveAll(List.of(dq));

        List<DataQuality> found = adapter.findByStatus("missing");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).entityType()).isEqualTo("artist");
        assertThat(found.get(0).entityId()).isEqualTo("a1");
        assertThat(found.get(0).field()).isEqualTo("genre");
        assertThat(found.get(0).status()).isEqualTo("missing");
        assertThat(found.get(0).severity()).isEqualTo("severe");
        assertThat(found.get(0).id()).isNotNull();
    }

    @Test
    void saveAll_writesSeverityCorrectly() {
        DataQuality severeRow = new DataQuality(null, "sala", "s1", "address", "missing", "severe", null, null, null, NOW);
        DataQuality nonSevereRow = new DataQuality(null, "sala", "s1", "image_url", "missing", "non_severe", null, null, null, NOW);

        adapter.saveAll(List.of(severeRow, nonSevereRow));

        String severityForAddress = jdbcTemplate.queryForObject(
            "SELECT severity FROM data_quality WHERE entity_id='s1' AND field='address'", String.class);
        String severityForImageUrl = jdbcTemplate.queryForObject(
            "SELECT severity FROM data_quality WHERE entity_id='s1' AND field='image_url'", String.class);

        assertThat(severityForAddress).isEqualTo("severe");
        assertThat(severityForImageUrl).isEqualTo("non_severe");
    }

    @Test
    void saveAll_doesNotOverwriteAutoFoundRow() {
        // Insert a row that Tavily already enriched (status = auto_found)
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, updated_at) VALUES (?,?,?,?,?,?,?,?)",
            "artist", "a1", "genre", "auto_found", "severe", "Indie Rock", "https://tavily.com/result", NOW.toString()
        );

        // Re-sync tries to write missing for the same entity/field
        DataQuality dq = new DataQuality(null, "artist", "a1", "genre", "missing", "severe", null, null, null, LATER);
        adapter.saveAll(List.of(dq));

        // auto_found must be preserved — not overwritten back to missing
        List<DataQuality> autoFound = adapter.findByStatus("auto_found");
        assertThat(autoFound).hasSize(1);
        assertThat(autoFound.get(0).status()).isEqualTo("auto_found");

        List<DataQuality> missing = adapter.findByStatus("missing");
        assertThat(missing).isEmpty();
    }

    @Test
    void saveAll_doesNotOverwriteApprovedRow() {
        // Insert an approved row
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a1", "genre", "approved", "non_severe", NOW.toString()
        );

        // Try to saveAll with missing status for same unique key
        DataQuality dq = new DataQuality(null, "artist", "a1", "genre", "missing", "severe", null, null, null, LATER);
        adapter.saveAll(List.of(dq));

        // Should still be approved
        List<DataQuality> results = adapter.findByStatus("approved");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("approved");

        List<DataQuality> missing = adapter.findByStatus("missing");
        assertThat(missing).isEmpty();
    }

    @Test
    void saveAll_doesNotOverwriteRejectedRow() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "sala", "s1", "phone", "rejected", "non_severe", NOW.toString()
        );

        DataQuality dq = new DataQuality(null, "sala", "s1", "phone", "missing", "non_severe", null, null, null, LATER);
        adapter.saveAll(List.of(dq));

        List<DataQuality> results = adapter.findByStatus("rejected");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("rejected");
    }

    @Test
    void saveAll_updatesExistingMissingRow() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a1", "genre", "missing", "severe", NOW.toString()
        );

        DataQuality dq = new DataQuality(null, "artist", "a1", "genre", "missing", "severe", null, null, null, LATER);
        adapter.saveAll(List.of(dq));

        List<DataQuality> results = adapter.findByStatus("missing");
        assertThat(results).hasSize(1);
    }

    @Test
    void saveAll_handlesMultipleRows() {
        List<DataQuality> issues = List.of(
            new DataQuality(null, "artist", "a1", "genre", "missing", "severe", null, null, null, NOW),
            new DataQuality(null, "artist", "a1", "imageUrl", "missing", "non_severe", null, null, null, NOW),
            new DataQuality(null, "sala", "s1", "phone", "missing", "non_severe", null, null, null, NOW)
        );

        adapter.saveAll(issues);

        assertThat(adapter.findByStatus("missing")).hasSize(3);
    }

    @Test
    void saveAll_preservesAutoApprovedOnReImport() {
        // W-01: auto_approved rows must survive re-import
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, score, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "sala", "s1", "description", "auto_approved", "non_severe", "Great venue", "https://tavily.com", 0.92, NOW.toString()
        );

        DataQuality dq = new DataQuality(null, "sala", "s1", "description", "missing", "non_severe", null, null, null, LATER);
        adapter.saveAll(List.of(dq));

        List<DataQuality> autoApproved = adapter.findByStatus("auto_approved");
        assertThat(autoApproved).hasSize(1);
        assertThat(autoApproved.get(0).status()).isEqualTo("auto_approved");

        List<DataQuality> missing = adapter.findByStatus("missing");
        assertThat(missing).isEmpty();
    }

    // --- findByStatus ---

    @Test
    void findByStatus_returnsOnlyMatchingStatus() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a1", "genre", "missing", "severe", NOW.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a2", "genre", "approved", "non_severe", NOW.toString()
        );

        List<DataQuality> missing = adapter.findByStatus("missing");
        assertThat(missing).hasSize(1);
        assertThat(missing.get(0).entityId()).isEqualTo("a1");
        assertThat(missing.get(0).severity()).isEqualTo("severe");
    }

    @Test
    void findByStatus_returnsEmptyWhenNoneMatch() {
        assertThat(adapter.findByStatus("auto_found")).isEmpty();
    }

    // --- findByEntityTypeAndStatus ---

    @Test
    void findByEntityTypeAndStatus_filtersCorrectly() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a1", "genre", "missing", "severe", NOW.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "sala", "s1", "phone", "missing", "non_severe", NOW.toString()
        );

        List<DataQuality> artistMissing = adapter.findByEntityTypeAndStatus("artist", "missing");
        assertThat(artistMissing).hasSize(1);
        assertThat(artistMissing.get(0).entityType()).isEqualTo("artist");
        assertThat(artistMissing.get(0).severity()).isEqualTo("severe");
    }

    // --- findById ---

    @Test
    void findById_returnsExistingRow() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a1", "genre", "missing", "severe", NOW.toString()
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM data_quality", Long.class);

        Optional<DataQuality> result = adapter.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().entityId()).isEqualTo("a1");
        assertThat(result.get().severity()).isEqualTo("severe");
    }

    @Test
    void findById_returnsEmptyForMissingId() {
        Optional<DataQuality> result = adapter.findById(999L);
        assertThat(result).isEmpty();
    }

    // --- updateSuggestion ---

    @Test
    void updateSuggestion_updatesStatusAndFields() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a1", "genre", "missing", "severe", NOW.toString()
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM data_quality", Long.class);

        adapter.updateSuggestion(id, "auto_found", "Indie Rock", "https://tavily.com/result", 0.85, LATER);

        Optional<DataQuality> updated = adapter.findById(id);
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo("auto_found");
        assertThat(updated.get().suggested()).isEqualTo("Indie Rock");
        assertThat(updated.get().source()).isEqualTo("https://tavily.com/result");
        assertThat(updated.get().updatedAt()).isEqualTo(LATER);
        assertThat(updated.get().severity()).isEqualTo("severe");
    }

    @Test
    void updateSuggestion_persistsScore() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "sala", "s1", "description", "missing", "non_severe", NOW.toString()
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM data_quality", Long.class);

        adapter.updateSuggestion(id, "auto_found", "Great venue", "https://tavily.com", 0.91, LATER);

        Optional<DataQuality> updated = adapter.findById(id);
        assertThat(updated).isPresent();
        assertThat(updated.get().score()).isEqualTo(0.91);
    }

    @Test
    void rowMapper_readsScore_nonNull() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, score, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "artist", "a1", "genre", "auto_found", "severe", "Rock", "https://src.com", 0.88, NOW.toString()
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM data_quality", Long.class);

        Optional<DataQuality> result = adapter.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(0.88);
    }

    @Test
    void rowMapper_readsScore_null() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a1", "genre", "missing", "severe", NOW.toString()
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM data_quality", Long.class);

        Optional<DataQuality> result = adapter.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().score()).isNull();
    }

    // --- updateStatus ---

    @Test
    void updateStatus_updatesOnlyStatusAndTimestamp() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, updated_at) VALUES (?,?,?,?,?,?,?)",
            "artist", "a1", "genre", "auto_found", "severe", "Indie Rock", NOW.toString()
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM data_quality", Long.class);

        adapter.updateStatus(id, "approved", LATER);

        Optional<DataQuality> updated = adapter.findById(id);
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo("approved");
        assertThat(updated.get().suggested()).isEqualTo("Indie Rock");
        assertThat(updated.get().updatedAt()).isEqualTo(LATER);
        assertThat(updated.get().severity()).isEqualTo("severe");
    }

    // --- findByStatusAndScore ---

    @Test
    void findByStatusAndScore_returnsMatchingRows() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, score, updated_at) VALUES (?,?,?,?,?,?,?)",
            "artist", "a1", "genre", "auto_found", "severe", 0.92, NOW.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, score, updated_at) VALUES (?,?,?,?,?,?,?)",
            "artist", "a2", "website", "auto_found", "non_severe", 0.75, NOW.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, score, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s1", "description", "auto_found", "non_severe", 0.85, NOW.toString()
        );

        List<DataQuality> result = adapter.findByStatusAndScore("auto_found", 0.8, null);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(DataQuality::score).containsExactlyInAnyOrder(0.92, 0.85);
    }

    @Test
    void findByStatusAndScore_filtersBySeverity() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, score, updated_at) VALUES (?,?,?,?,?,?,?)",
            "artist", "a1", "genre", "auto_found", "severe", 0.92, NOW.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, score, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s1", "description", "auto_found", "non_severe", 0.85, NOW.toString()
        );

        List<DataQuality> result = adapter.findByStatusAndScore("auto_found", 0.8, "non_severe");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).severity()).isEqualTo("non_severe");
    }

    // --- findEntityIdsBySourceAndField ---

    @Test
    void findEntityIdsBySourceAndField_returnsDistinctSalaIdsForLocationIqLatLng() {
        // Insert lat and lng rows for s1 with source=locationiq (two rows for same id)
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, source, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s1", "lat", "auto_approved", "severe", "locationiq", NOW.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, source, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s1", "lng", "auto_approved", "severe", "locationiq", NOW.toString()
        );
        // Insert for s2 with same source
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, source, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s2", "lat", "auto_approved", "severe", "locationiq", NOW.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, source, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s2", "lng", "auto_approved", "severe", "locationiq", NOW.toString()
        );
        // Insert for s3 with source=locationiq
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, source, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s3", "lat", "auto_approved", "severe", "locationiq", NOW.toString()
        );

        List<String> ids = adapter.findEntityIdsBySourceAndField("sala", "locationiq", List.of("lat", "lng"));

        // Must return exactly 3 distinct ids — not 5 (no duplicates from lat+lng rows for same id)
        assertThat(ids).hasSize(3);
        assertThat(ids).containsExactlyInAnyOrder("s1", "s2", "s3");
    }

    @Test
    void findEntityIdsBySourceAndField_excludesOtherSource() {
        // locationiq sala
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, source, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s1", "lat", "auto_approved", "severe", "locationiq", NOW.toString()
        );
        // foursquare sala — must NOT appear
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, source, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s99", "lat", "auto_approved", "severe", "foursquare", NOW.toString()
        );

        List<String> ids = adapter.findEntityIdsBySourceAndField("sala", "locationiq", List.of("lat", "lng"));

        assertThat(ids).containsExactly("s1");
        assertThat(ids).doesNotContain("s99");
    }

    @Test
    void findEntityIdsBySourceAndField_emptyFieldsList_returnsEmpty() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, source, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s1", "lat", "auto_approved", "severe", "locationiq", NOW.toString()
        );

        List<String> ids = adapter.findEntityIdsBySourceAndField("sala", "locationiq", List.of());

        assertThat(ids).isEmpty();
    }

    @Test
    void findEntityIdsBySourceAndField_dedupesBySalaId_twoRowsSameIdCountsOnce() {
        // Two rows for the SAME sala with lat AND lng — must produce only 1 id
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, source, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "dup-id", "lat", "auto_approved", "severe", "locationiq", NOW.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, source, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "dup-id", "lng", "auto_approved", "severe", "locationiq", NOW.toString()
        );

        List<String> ids = adapter.findEntityIdsBySourceAndField("sala", "locationiq", List.of("lat", "lng"));

        assertThat(ids).hasSize(1);
        assertThat(ids).containsExactly("dup-id");
    }

    // --- Phase 2.9 [RED]: resolvePendingForField (admin edit dq reconciliation) ---

    @Test
    void resolvePendingForField_transitionsMissingRowToApprovedWithSuggestedValue() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "sala", "s1", "description", "missing", "non_severe", NOW.toString()
        );

        adapter.resolvePendingForField("sala", "s1", "description", "New description", LATER);

        Optional<DataQuality> result = adapter.findByEntityTypeAndStatus("sala", "approved").stream().findFirst();
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("approved");
        assertThat(result.get().suggested()).isEqualTo("New description");
        assertThat(result.get().updatedAt()).isEqualTo(LATER);
    }

    @Test
    void resolvePendingForField_transitionsAutoFoundRowToApproved() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, source, updated_at) VALUES (?,?,?,?,?,?,?,?)",
            "sala", "s1", "address", "auto_found", "severe", "Old suggestion", "tavily", NOW.toString()
        );

        adapter.resolvePendingForField("sala", "s1", "address", "New address", LATER);

        Optional<DataQuality> result = adapter.findByEntityTypeAndStatus("sala", "approved").stream().findFirst();
        assertThat(result).isPresent();
        assertThat(result.get().suggested()).isEqualTo("New address");
        assertThat(result.get().status()).isEqualTo("approved");
    }

    @Test
    void resolvePendingForField_noOpForTerminalApprovedRow() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s1", "address", "approved", "severe", "Original value", NOW.toString()
        );

        adapter.resolvePendingForField("sala", "s1", "address", "New address", LATER);

        Optional<DataQuality> result = adapter.findByEntityTypeAndStatus("sala", "approved").stream().findFirst();
        assertThat(result).isPresent();
        assertThat(result.get().suggested()).isEqualTo("Original value");
        assertThat(result.get().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void resolvePendingForField_noOpForTerminalRejectedRow() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s1", "address", "rejected", "severe", "Old", NOW.toString()
        );

        adapter.resolvePendingForField("sala", "s1", "address", "New address", LATER);

        Optional<DataQuality> result = adapter.findByEntityTypeAndStatus("sala", "rejected").stream().findFirst();
        assertThat(result).isPresent();
        assertThat(result.get().suggested()).isEqualTo("Old");
        assertThat(result.get().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void resolvePendingForField_noOpForTerminalAutoApprovedRow() {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, suggested, updated_at) VALUES (?,?,?,?,?,?,?)",
            "sala", "s1", "description", "auto_approved", "non_severe", "Auto desc", NOW.toString()
        );

        adapter.resolvePendingForField("sala", "s1", "description", "New description", LATER);

        Optional<DataQuality> result = adapter.findByEntityTypeAndStatus("sala", "auto_approved").stream().findFirst();
        assertThat(result).isPresent();
        assertThat(result.get().suggested()).isEqualTo("Auto desc");
        assertThat(result.get().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void resolvePendingForField_noOpWhenNoMatchingRow() {
        // No row at all for (sala, s1, description)
        adapter.resolvePendingForField("sala", "s1", "description", "New description", LATER);

        List<DataQuality> all = adapter.findByStatus("approved");
        assertThat(all).isEmpty();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM data_quality WHERE entity_type='sala' AND entity_id='s1' AND field='description'",
            Integer.class);
        assertThat(count).isZero();
    }
}
