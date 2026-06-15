package org.rubenazo.conciertosfront.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

/** File name of the local SQLite database, shared by all platform builders. */
const val DB_NAME = "conciertos.db"

/**
 * Platform-agnostic migration specification.
 * Holds the SQL and version range so that:
 * - commonTest can assert the SQL contract (TDD)
 * - Platform builders (android/ios) wrap this into the real Room Migration
 */
data class MigrationSpec(
    val startVersion: Int,
    val endVersion: Int,
    val sql: String,
)

/**
 * Wraps a [MigrationSpec] into a Room [Migration], splitting multi-statement
 * SQL on `;` and executing each statement in order. Single-statement specs
 * (no trailing `;`) run as one statement.
 */
fun MigrationSpec.toMigration(): Migration = object : Migration(startVersion, endVersion) {
    override fun migrate(connection: SQLiteConnection) {
        sql.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { statement ->
                connection.prepare(statement).use { it.step() }
            }
    }
}

/** All migrations in version order, shared by every platform database builder. */
fun allMigrations(): Array<Migration> = arrayOf(
    MIGRATION_1_2.toMigration(),
    MIGRATION_2_3.toMigration(),
    MIGRATION_3_4.toMigration(),
    MIGRATION_4_5.toMigration(),
    MIGRATION_5_6.toMigration(),
)

val MIGRATION_1_2 = MigrationSpec(
    startVersion = 1,
    endVersion = 2,
    sql = "CREATE INDEX IF NOT EXISTS index_salas_concierto_lat_lng" +
        " ON salas_concierto(lat, lng)",
)

val MIGRATION_2_3 = MigrationSpec(
    startVersion = 2,
    endVersion = 3,
    sql = """
        CREATE TABLE IF NOT EXISTS concerts_new (
            id TEXT NOT NULL PRIMARY KEY,
            sala_concierto_id TEXT NOT NULL,
            date TEXT NOT NULL,
            time TEXT,
            price TEXT,
            ticket_url TEXT,
            source_url TEXT,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (sala_concierto_id) REFERENCES salas_concierto(id) ON DELETE NO ACTION
        );
        INSERT INTO concerts_new (id, sala_concierto_id, date, time, price, ticket_url, source_url, updated_at)
            SELECT id, sala_concierto_id, date, time, CAST(price AS TEXT), ticket_url, source_url, updated_at FROM concerts;
        DROP TABLE concerts;
        ALTER TABLE concerts_new RENAME TO concerts;
        CREATE INDEX IF NOT EXISTS index_concerts_date ON concerts(date);
        CREATE INDEX IF NOT EXISTS index_concerts_sala_concierto_id ON concerts(sala_concierto_id);
    """.trimIndent(),
)

/**
 * Migration 3 → 4: Field alignment with backend domain changes.
 * - concerts: remove ticket_url column
 * - salas_concierto: remove phone/website columns; add description TEXT column
 *
 * SQLite (pre-3.35) cannot DROP COLUMN, so both tables are recreated
 * using the CREATE new → INSERT old → DROP old → RENAME new pattern.
 */
val MIGRATION_3_4 = MigrationSpec(
    startVersion = 3,
    endVersion = 4,
    sql = """
        CREATE TABLE IF NOT EXISTS concerts_v4 (
            id TEXT NOT NULL PRIMARY KEY,
            sala_concierto_id TEXT NOT NULL,
            date TEXT NOT NULL,
            time TEXT,
            price TEXT,
            source_url TEXT,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (sala_concierto_id) REFERENCES salas_concierto(id) ON DELETE NO ACTION
        );
        INSERT INTO concerts_v4 (id, sala_concierto_id, date, time, price, source_url, updated_at)
            SELECT id, sala_concierto_id, date, time, price, source_url, updated_at FROM concerts;
        DROP TABLE concerts;
        ALTER TABLE concerts_v4 RENAME TO concerts;
        CREATE INDEX IF NOT EXISTS index_concerts_date ON concerts(date);
        CREATE INDEX IF NOT EXISTS index_concerts_sala_concierto_id ON concerts(sala_concierto_id);
        CREATE TABLE IF NOT EXISTS salas_concierto_v4 (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            address TEXT,
            city TEXT NOT NULL,
            province TEXT NOT NULL,
            lat REAL,
            lng REAL,
            image_url TEXT,
            description TEXT
        );
        INSERT INTO salas_concierto_v4 (id, name, address, city, province, lat, lng, image_url)
            SELECT id, name, address, city, province, lat, lng, image_url FROM salas_concierto;
        DROP TABLE salas_concierto;
        ALTER TABLE salas_concierto_v4 RENAME TO salas_concierto;
        CREATE INDEX IF NOT EXISTS index_salas_concierto_lat_lng ON salas_concierto(lat, lng);
    """.trimIndent(),
)

val MIGRATION_4_5 = MigrationSpec(
    startVersion = 4,
    endVersion = 5,
    sql = """
        ALTER TABLE artists ADD COLUMN description TEXT;
        ALTER TABLE artists ADD COLUMN source_url TEXT;
        ALTER TABLE salas_concierto ADD COLUMN source_url TEXT;
    """.trimIndent(),
)

/**
 * Migration 5 → 6: Remove the discarded "Favorites" feature.
 * - Drops the favorites table (FavoriteEntity removed, no DAO/UI ever shipped)
 *
 * IF NOT EXISTS / IF EXISTS guards keep the migration safe both for installs
 * that have the table and for fresh databases.
 */
val MIGRATION_5_6 = MigrationSpec(
    startVersion = 5,
    endVersion = 6,
    sql = "DROP TABLE IF EXISTS favorites",
)
