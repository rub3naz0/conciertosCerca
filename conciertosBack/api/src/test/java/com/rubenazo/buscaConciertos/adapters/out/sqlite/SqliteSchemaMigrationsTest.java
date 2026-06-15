package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Strict TDD: SqliteSchemaMigrations ensures the severity column is added
 * idempotently to pre-existing data_quality tables that lack it.
 */
class SqliteSchemaMigrationsTest {

    private File tempDb;
    private Connection connection;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        tempDb = Files.createTempFile("schema-migrations-test-", ".db").toFile();
        connection = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        if (tempDb != null) {
            tempDb.delete();
        }
    }

    // --- RED case: old schema missing severity column ---

    @Test
    void ensureSeverityColumn_addsColumnWhenTableExistsWithoutIt() throws SQLException {
        // Create data_quality WITHOUT severity (old schema)
        connection.createStatement().execute("""
            CREATE TABLE data_quality (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL, entity_id TEXT NOT NULL,
                field TEXT NOT NULL, status TEXT NOT NULL,
                suggested TEXT, source TEXT, updated_at TEXT NOT NULL,
                UNIQUE(entity_type, entity_id, field)
            )
            """);

        // Run migration
        SqliteSchemaMigrations.ensureSeverityColumn(connection);

        // Assert severity now exists with default 'non_severe'
        List<String> columnNames = getColumnNames(connection, "data_quality");
        assertThat(columnNames).contains("severity");

        // Verify the default value is correct by inserting a row without explicit severity
        connection.createStatement().execute(
            "INSERT INTO data_quality(entity_type, entity_id, field, status, updated_at) " +
            "VALUES ('sala', 's1', 'address', 'missing', '2026-01-01T00:00:00Z')"
        );
        ResultSet rs = connection.createStatement().executeQuery(
            "SELECT severity FROM data_quality WHERE entity_id='s1'"
        );
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("severity")).isEqualTo("non_severe");
    }

    // --- Idempotency: running twice must not throw ---

    @Test
    void ensureSeverityColumn_isIdempotentWhenSeverityAlreadyExists() throws SQLException {
        // Create data_quality WITH severity (new schema)
        connection.createStatement().execute("""
            CREATE TABLE data_quality (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL, entity_id TEXT NOT NULL,
                field TEXT NOT NULL, status TEXT NOT NULL,
                severity TEXT NOT NULL DEFAULT 'non_severe',
                suggested TEXT, source TEXT, updated_at TEXT NOT NULL,
                UNIQUE(entity_type, entity_id, field)
            )
            """);

        // Run migration twice — must not throw
        assertThatNoException().isThrownBy(() -> {
            SqliteSchemaMigrations.ensureSeverityColumn(connection);
            SqliteSchemaMigrations.ensureSeverityColumn(connection);
        });

        // Column still present
        List<String> columnNames = getColumnNames(connection, "data_quality");
        assertThat(columnNames).contains("severity");
    }

    // --- No-op when table is absent ---

    @Test
    void ensureSeverityColumn_doesNothingWhenTableAbsent() {
        // data_quality table does not exist at all
        assertThatNoException().isThrownBy(() ->
            SqliteSchemaMigrations.ensureSeverityColumn(connection)
        );
    }

    // --- score column: adds when absent, idempotent on re-run ---

    @Test
    void ensureScoreColumn_addsColumnWhenTableExistsWithoutIt() throws SQLException {
        // Create data_quality WITHOUT score column (legacy schema)
        connection.createStatement().execute("""
            CREATE TABLE data_quality (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL, entity_id TEXT NOT NULL,
                field TEXT NOT NULL, status TEXT NOT NULL,
                severity TEXT NOT NULL DEFAULT 'non_severe',
                suggested TEXT, source TEXT, updated_at TEXT NOT NULL,
                UNIQUE(entity_type, entity_id, field)
            )
            """);

        SqliteSchemaMigrations.ensureScoreColumn(connection);

        List<String> cols = getColumnNames(connection, "data_quality");
        assertThat(cols).contains("score");
    }

    @Test
    void ensureScoreColumn_isIdempotentOnLegacyTable() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE data_quality (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL, entity_id TEXT NOT NULL,
                field TEXT NOT NULL, status TEXT NOT NULL,
                severity TEXT NOT NULL DEFAULT 'non_severe',
                suggested TEXT, source TEXT, updated_at TEXT NOT NULL,
                UNIQUE(entity_type, entity_id, field)
            )
            """);

        assertThatNoException().isThrownBy(() -> {
            SqliteSchemaMigrations.ensureScoreColumn(connection);
            SqliteSchemaMigrations.ensureScoreColumn(connection);
        });

        List<String> cols = getColumnNames(connection, "data_quality");
        assertThat(cols).contains("score");
    }

    @Test
    void ensureScoreColumn_doesNothingWhenTableAbsent() {
        assertThatNoException().isThrownBy(() ->
            SqliteSchemaMigrations.ensureScoreColumn(connection)
        );
    }

    // --- Helper ---

    private List<String> getColumnNames(Connection conn, String tableName) throws SQLException {
        List<String> names = new ArrayList<>();
        ResultSet rs = conn.createStatement().executeQuery(
            "PRAGMA table_info(" + tableName + ")"
        );
        while (rs.next()) {
            names.add(rs.getString("name"));
        }
        return names;
    }
}
