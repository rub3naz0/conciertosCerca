package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistSqliteAdapterTest {

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

        adapter = new ArtistSqliteAdapter(jdbcTemplate);
    }

    private void insertArtist(String id, String updatedAt) {
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, genre, image_url, website, updated_at) VALUES (?,?,?,?,?,?)",
            id, "Artist " + id, "Rock", null, null, updatedAt
        );
    }

    private void insertSevereQuality(String artistId, String field) {
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", artistId, field, "missing", "severe", "2026-05-01T00:00:00Z"
        );
    }

    // --- Existing tests ---

    @Test
    void findAll_returnsAllArtists() {
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, genre, image_url, website, updated_at) VALUES (?,?,?,?,?,?)",
            "a1", "Vetusta Morla", "Indie Rock", "http://img1.jpg", "http://web1.com",
            "2026-05-20T15:30:00Z"
        );
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, genre, image_url, website, updated_at) VALUES (?,?,?,?,?,?)",
            "a2", "Supersubmarina", "Rock", "http://img2.jpg", "http://web2.com",
            "2026-05-21T10:00:00Z"
        );

        var result = adapter.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting("id").containsExactlyInAnyOrder("a1", "a2");
    }

    @Test
    void findAll_returnsEmptyWhenNoData() {
        assertThat(adapter.findAll()).isEmpty();
    }

    @Test
    void findModifiedAfter_returnsOnlyModifiedSince() {
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, genre, image_url, website, updated_at) VALUES (?,?,?,?,?,?)",
            "a1", "Old Artist", "Pop", null, null, "2026-05-17T00:00:00Z"
        );
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, genre, image_url, website, updated_at) VALUES (?,?,?,?,?,?)",
            "a2", "New Artist", "Rock", null, null, "2026-05-19T00:00:00Z"
        );

        Instant since = Instant.parse("2026-05-18T00:00:00Z");
        var result = adapter.findModifiedAfter(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("a2");
    }

    @Test
    void findModifiedAfter_includesExactTimestamp() {
        Instant since = Instant.parse("2026-05-18T00:00:00Z");
        jdbcTemplate.update(
            "INSERT INTO artists(id, name, genre, image_url, website, updated_at) VALUES (?,?,?,?,?,?)",
            "a1", "Exact Artist", "Jazz", null, null, since.toString()
        );

        var result = adapter.findModifiedAfter(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("a1");
    }

    // --- Phase 4.5 [RED]: eligibility filtering for artists ---

    @Test
    void findAll_excludesArtistWithSevereDataQuality() {
        insertArtist("a1", "2026-05-20T15:30:00Z");
        insertSevereQuality("a1", "genre");

        var result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_includesArtistWithNoSevereDataQuality() {
        insertArtist("a1", "2026-05-20T15:30:00Z");
        // Non-severe quality row — should not filter out
        jdbcTemplate.update(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, severity, updated_at) VALUES (?,?,?,?,?,?)",
            "artist", "a1", "image_url", "missing", "non_severe", "2026-05-01T00:00:00Z"
        );

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("a1");
    }

    @Test
    void findModifiedAfter_excludesSevere() {
        insertArtist("a1", "2026-05-20T15:30:00Z");
        insertArtist("a2", "2026-05-20T15:30:00Z");
        insertSevereQuality("a1", "genre");

        Instant since = Instant.parse("2026-05-18T00:00:00Z");
        var result = adapter.findModifiedAfter(since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("a2");
    }

    @Test
    void findAll_includesArtistWithNoDataQuality() {
        insertArtist("a1", "2026-05-20T15:30:00Z");

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
    }

    @Test
    void existsById_returnsTrueForPresentArtist() {
        insertArtist("a1", "2026-05-20T15:30:00Z");

        assertThat(adapter.existsById("a1")).isTrue();
    }

    @Test
    void existsById_returnsFalseForAbsentArtist() {
        assertThat(adapter.existsById("does-not-exist")).isFalse();
    }

    // --- Phase 2.3 [RED]: updateAll (admin edit) ---

    @Test
    void updateAll_updatesNameGenreDescriptionImageUrlAndUpdatedAt() {
        insertArtist("a1", "2026-05-20T15:30:00Z");
        Instant updatedAt = Instant.parse("2026-06-12T10:00:00Z");
        var edited = new com.rubenazo.buscaConciertos.domain.Artist(
            "a1", "New Name", "New Genre", "https://example.com/img.jpg",
            "http://web.com", "manual", "New description", updatedAt
        );

        adapter.updateAll(edited);

        var result = adapter.findByIdIncludingBlocked("a1").orElseThrow();
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.genre()).isEqualTo("New Genre");
        assertThat(result.description()).isEqualTo("New description");
        assertThat(result.imageUrl()).isEqualTo("https://example.com/img.jpg");
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    // --- Phase 2.5 [RED]: findByIdIncludingBlocked (admin edit) ---

    @Test
    void findByIdIncludingBlocked_returnsSevereBlockedArtist() {
        insertArtist("a1", "2026-05-20T15:30:00Z");
        insertSevereQuality("a1", "genre");

        var result = adapter.findByIdIncludingBlocked("a1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("a1");
    }

    @Test
    void findByIdIncludingBlocked_returnsEmptyForMissingId() {
        var result = adapter.findByIdIncludingBlocked("does-not-exist");

        assertThat(result).isEmpty();
    }

    // --- Phase 3.2 [RED]: findAllIncludingBlocked (admin list) ---

    @Test
    void findAllIncludingBlocked_returnsSevereBlockedArtists() {
        insertArtist("a1", "2026-05-20T15:30:00Z");
        insertSevereQuality("a1", "genre");

        var result = adapter.findAllIncludingBlocked();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("a1");
    }

    @Test
    void findAllIncludingBlocked_returnsAllArtistsRegardlessOfQuality() {
        insertArtist("a1", "2026-05-20T15:30:00Z");
        insertArtist("a2", "2026-05-21T10:00:00Z");
        insertSevereQuality("a2", "genre");

        var result = adapter.findAllIncludingBlocked();

        assertThat(result).hasSize(2);
        assertThat(result).extracting("id").containsExactlyInAnyOrder("a1", "a2");
    }
}
