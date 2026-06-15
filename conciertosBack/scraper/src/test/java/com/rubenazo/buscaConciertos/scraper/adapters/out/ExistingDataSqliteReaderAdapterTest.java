package com.rubenazo.buscaConciertos.scraper.adapters.out;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExistingDataSqliteReaderAdapterTest {

    private Connection connection;
    private ExistingDataSqliteReaderAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema(connection);
        seedData(connection);
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        adapter = new ExistingDataSqliteReaderAdapter(jdbcTemplate);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    private void createSchema(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS salas_concierto (
                    id TEXT PRIMARY KEY, name TEXT NOT NULL,
                    city TEXT NOT NULL, province TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS artists (
                    id TEXT PRIMARY KEY, name TEXT NOT NULL,
                    description TEXT,
                    updated_at TEXT NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS concerts (
                    id TEXT PRIMARY KEY,
                    sala_concierto_id TEXT NOT NULL,
                    date TEXT NOT NULL, updated_at TEXT NOT NULL,
                    deleted INTEGER NOT NULL DEFAULT 0
                )
                """);
        }
    }

    private void seedData(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO artists VALUES ('artist-1', 'Artist One', 'A great artist', '2026-01-01T00:00:00Z')");
            stmt.execute("INSERT INTO artists VALUES ('artist-2', 'Artist Two', NULL, '2026-01-01T00:00:00Z')");
            stmt.execute("INSERT INTO artists VALUES ('artist-3', 'Artist Three', '', '2026-01-01T00:00:00Z')");
            stmt.execute("INSERT INTO salas_concierto VALUES ('sala-1', 'Sala One', 'Madrid', 'Madrid', '2026-01-01T00:00:00Z')");
            stmt.execute("INSERT INTO concerts VALUES ('concert-1', 'sala-1', '2026-06-15', '2026-01-01T00:00:00Z', 0)");
            stmt.execute("INSERT INTO concerts VALUES ('concert-2', 'sala-1', '2026-07-01', '2026-01-01T00:00:00Z', 0)");
            stmt.execute("INSERT INTO concerts VALUES ('deleted-concert', 'sala-1', '2026-07-15', '2026-01-01T00:00:00Z', 1)");
        }
    }

    @Test
    void existingConcertIds_returnsAllNonDeletedConcertIds() {
        Set<String> ids = adapter.existingConcertIds();

        assertThat(ids).containsExactlyInAnyOrder("concert-1", "concert-2");
        assertThat(ids).doesNotContain("deleted-concert");
    }

    @Test
    void existingArtistIds_returnsAllArtistIds() {
        Set<String> ids = adapter.existingArtistIds();

        assertThat(ids).containsExactlyInAnyOrder("artist-1", "artist-2", "artist-3");
    }

    @Test
    void enrichedArtistIds_returnsOnlyArtistsWithDescription() {
        Set<String> ids = adapter.enrichedArtistIds();

        assertThat(ids).containsExactly("artist-1");
    }

    @Test
    void existingVenueIds_returnsAllVenueIds() {
        Set<String> ids = adapter.existingVenueIds();

        assertThat(ids).containsExactlyInAnyOrder("sala-1");
    }
}
