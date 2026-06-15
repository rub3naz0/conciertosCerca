package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import com.rubenazo.buscaConciertos.domain.Artist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistSqliteAdapterWriteTest {

    private JdbcTemplate jdbcTemplate;
    private ArtistSqliteAdapter adapter;

    @BeforeEach
    void setUp() {
        var ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(ds);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS artists (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, genre TEXT,
                image_url TEXT, website TEXT, description TEXT, source_url TEXT, updated_at TEXT NOT NULL
            )
            """);

        adapter = new ArtistSqliteAdapter(jdbcTemplate);
    }

    @Test
    void upsert_insertsNewArtist() {
        var artist = new Artist(
            "a1", "Vetusta Morla", "Indie Rock",
            "https://img.vetusta.com/logo.jpg", "https://vetusta.com",
            null, null, Instant.parse("2026-05-20T10:00:00Z")
        );

        adapter.upsert(artist);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM artists WHERE id = 'a1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("id", "a1");
        assertThat(rows.get(0)).containsEntry("name", "Vetusta Morla");
        assertThat(rows.get(0)).containsEntry("genre", "Indie Rock");
    }

    @Test
    void upsert_updatesExistingArtist() {
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, genre, updated_at) VALUES (?,?,?,?)",
            "a1", "Old Name", "Old Genre", "2026-01-01T00:00:00Z"
        );

        var artist = new Artist(
            "a1", "New Name", "New Genre",
            "https://img.new.com/logo.jpg", "https://new.com",
            null, null, Instant.parse("2026-05-20T10:00:00Z")
        );

        adapter.upsert(artist);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM artists WHERE id = 'a1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("name", "New Name");
        assertThat(rows.get(0)).containsEntry("genre", "New Genre");
    }

    @Test
    void upsert_handlesNullOptionalFields() {
        var artist = new Artist(
            "a2", "Minimal Artist", null,
            null, null,
            null, null, Instant.parse("2026-05-21T00:00:00Z")
        );

        adapter.upsert(artist);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM artists WHERE id = 'a2'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("genre")).isNull();
        assertThat(rows.get(0).get("image_url")).isNull();
        assertThat(rows.get(0).get("website")).isNull();
        assertThat(rows.get(0).get("source_url")).isNull();
    }

    @Test
    void updateField_updatesSpecifiedColumn() {
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, genre, updated_at) VALUES (?,?,?,?)",
            "a1", "Vetusta Morla", null, "2026-01-01T00:00:00Z"
        );

        adapter.updateField("a1", "genre", "Indie Rock", Instant.parse("2026-05-28T10:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM artists WHERE id = 'a1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("genre", "Indie Rock");
        assertThat(rows.get(0)).containsEntry("updated_at", "2026-05-28T10:00:00Z");
    }

    @Test
    void updateField_updatesWebsiteField() {
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, website, updated_at) VALUES (?,?,?,?)",
            "a1", "Vetusta Morla", null, "2026-01-01T00:00:00Z"
        );

        adapter.updateField("a1", "website", "https://vetusta.com", Instant.parse("2026-05-28T10:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM artists WHERE id = 'a1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("website", "https://vetusta.com");
    }

    @Test
    void updateField_rejectsUnknownColumnName() {
        assertThatThrownBy(() ->
            adapter.updateField("a1", "malicious_column; DROP TABLE artists;--", "value", Instant.now())
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateField_rejectsEmptyFieldName() {
        assertThatThrownBy(() ->
            adapter.updateField("a1", "", "value", Instant.now())
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
