package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SyncMetadataSqliteAdapterWriteTest {

    private JdbcTemplate jdbcTemplate;
    private SyncMetadataSqliteAdapter adapter;

    @BeforeEach
    void setUp() {
        var ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(ds);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS sync_meta (
                resource TEXT PRIMARY KEY, last_modified TEXT NOT NULL
            )
            """);

        adapter = new SyncMetadataSqliteAdapter(jdbcTemplate);
    }

    @Test
    void updateLastModified_insertsNewResource() {
        Instant timestamp = Instant.parse("2026-05-20T10:00:00Z");

        adapter.updateLastModified("concerts", timestamp);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM sync_meta WHERE resource = 'concerts'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("resource", "concerts");
        assertThat(rows.get(0)).containsEntry("last_modified", "2026-05-20T10:00:00Z");
    }

    @Test
    void updateLastModified_updatesExistingResource() {
        jdbcTemplate.update(
            "INSERT INTO sync_meta(resource, last_modified) VALUES (?,?)",
            "artists", "2026-01-01T00:00:00Z"
        );
        Instant newTimestamp = Instant.parse("2026-05-20T10:00:00Z");

        adapter.updateLastModified("artists", newTimestamp);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM sync_meta WHERE resource = 'artists'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("last_modified", "2026-05-20T10:00:00Z");
    }

    @Test
    void updateLastModified_isIdempotent() {
        Instant timestamp = Instant.parse("2026-05-20T10:00:00Z");

        adapter.updateLastModified("salas-concierto", timestamp);
        adapter.updateLastModified("salas-concierto", timestamp);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM sync_meta WHERE resource = 'salas-concierto'");
        assertThat(rows).hasSize(1);
    }
}
