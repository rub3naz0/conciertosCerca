package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Idempotent schema migrations for SQLite.
 *
 * <p>schema.sql uses {@code CREATE TABLE IF NOT EXISTS}, which is a no-op when the
 * table already exists — even if new columns were added in a later version.  This
 * class fills the gap with guarded {@code ALTER TABLE} statements that are safe to
 * run on every startup.
 */
class SqliteSchemaMigrations {

    private SqliteSchemaMigrations() {
        // utility class — no instances
    }

    /**
     * Ensures the {@code severity} column exists on the {@code data_quality} table.
     *
     * <ul>
     *   <li>Table absent → no-op (schema.sql will create it fresh with the column).</li>
     *   <li>Table present, {@code severity} absent → {@code ALTER TABLE … ADD COLUMN}.</li>
     *   <li>Table present, {@code severity} already present → no-op.</li>
     * </ul>
     *
     * Safe to call multiple times; fully idempotent.
     *
     * @param connection an open JDBC connection to the SQLite database
     * @throws SQLException if an unexpected database error occurs
     */
    static void ensureSeverityColumn(Connection connection) throws SQLException {
        if (!tableExists(connection, "data_quality")) {
            return;
        }
        if (columnExists(connection, "data_quality", "severity")) {
            return;
        }
        connection.createStatement().execute(
            "ALTER TABLE data_quality ADD COLUMN severity TEXT NOT NULL DEFAULT 'non_severe'"
        );
    }

    /**
     * Ensures the {@code score} column exists on the {@code data_quality} table.
     *
     * <ul>
     *   <li>Table absent → no-op (schema.sql will create it fresh with the column).</li>
     *   <li>Table present, {@code score} absent → {@code ALTER TABLE … ADD COLUMN}.</li>
     *   <li>Table present, {@code score} already present → no-op.</li>
     * </ul>
     *
     * Safe to call multiple times; fully idempotent.
     *
     * @param connection an open JDBC connection to the SQLite database
     * @throws SQLException if an unexpected database error occurs
     */
    static void ensureScoreColumn(Connection connection) throws SQLException {
        if (!tableExists(connection, "data_quality")) {
            return;
        }
        if (columnExists(connection, "data_quality", "score")) {
            return;
        }
        connection.createStatement().execute(
            "ALTER TABLE data_quality ADD COLUMN score REAL"
        );
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery(
            "PRAGMA table_info(" + tableName + ")"
        );
        return rs.next();
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery(
            "PRAGMA table_info(" + tableName + ")"
        );
        while (rs.next()) {
            if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                return true;
            }
        }
        return false;
    }
}
