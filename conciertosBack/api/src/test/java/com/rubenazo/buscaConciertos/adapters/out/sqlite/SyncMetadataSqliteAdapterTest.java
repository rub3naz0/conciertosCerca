package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncMetadataSqliteAdapterTest {

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
    void shouldSync_returnsTrueWhenResourceExists() {
        jdbcTemplate.update(
            "INSERT INTO sync_meta(resource, last_modified) VALUES (?,?)",
            "concerts", "2026-05-20T15:30:00Z"
        );

        assertThat(adapter.shouldSync("concerts")).isTrue();
    }

    @Test
    void shouldSync_returnsFalseWhenResourceNotExists() {
        assertThat(adapter.shouldSync("concerts")).isFalse();
    }

    @Test
    void getLastModified_returnsTimestamp() {
        Instant expected = Instant.parse("2026-05-20T15:30:00Z");
        jdbcTemplate.update(
            "INSERT INTO sync_meta(resource, last_modified) VALUES (?,?)",
            "artists", expected.toString()
        );

        Instant result = adapter.getLastModified("artists");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getLastModified_throwsWhenResourceNotExists() {
        assertThatThrownBy(() -> adapter.getLastModified("nonexistent"))
            .isInstanceOf(Exception.class);
    }
}
