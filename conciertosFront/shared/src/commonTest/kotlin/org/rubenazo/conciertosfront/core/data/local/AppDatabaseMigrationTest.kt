package org.rubenazo.conciertosfront.core.data.local

import kotlin.test.Test
import kotlin.test.assertTrue

class AppDatabaseMigrationTest {

    @Test
    fun migration1To2_sqlCreatesLatLngIndex() {
        val sql = MIGRATION_1_2.sql
        assertTrue(
            actual = sql.contains("index_salas_concierto_lat_lng", ignoreCase = true),
            message = "Migration SQL must create index_salas_concierto_lat_lng"
        )
    }

    @Test
    fun migration1To2_sqlIsCreateIndexIfNotExists() {
        val sql = MIGRATION_1_2.sql
        assertTrue(
            actual = sql.startsWith("CREATE INDEX IF NOT EXISTS", ignoreCase = true),
            message = "Migration SQL must start with CREATE INDEX IF NOT EXISTS"
        )
    }

    @Test
    fun migration1To2_sqlTargetsSalasConcierto() {
        val sql = MIGRATION_1_2.sql
        assertTrue(
            actual = sql.contains("salas_concierto", ignoreCase = true),
            message = "Migration SQL must reference salas_concierto table"
        )
    }

    @Test
    fun migration1To2_sqlIncludesLatAndLng() {
        val sql = MIGRATION_1_2.sql
        assertTrue(
            actual = sql.contains("lat", ignoreCase = true) && sql.contains("lng", ignoreCase = true),
            message = "Migration SQL must include lat and lng columns"
        )
    }

    @Test
    fun migration1To2_startVersionIs1() {
        val migration = MIGRATION_1_2
        assertTrue(
            actual = migration.startVersion == 1,
            message = "Migration start version must be 1"
        )
    }

    @Test
    fun migration1To2_endVersionIs2() {
        val migration = MIGRATION_1_2
        assertTrue(
            actual = migration.endVersion == 2,
            message = "Migration end version must be 2"
        )
    }

    @Test
    fun migration2To3_startVersionIs2() {
        assertTrue(
            actual = MIGRATION_2_3.startVersion == 2,
            message = "Migration start version must be 2"
        )
    }

    @Test
    fun migration2To3_endVersionIs3() {
        assertTrue(
            actual = MIGRATION_2_3.endVersion == 3,
            message = "Migration end version must be 3"
        )
    }

    @Test
    fun migration2To3_sqlCreatesConcertsNewTable() {
        assertTrue(
            actual = MIGRATION_2_3.sql.contains("CREATE TABLE IF NOT EXISTS concerts_new", ignoreCase = true),
            message = "Migration must create concerts_new table"
        )
    }

    @Test
    fun migration2To3_sqlChangesPriceToText() {
        assertTrue(
            actual = MIGRATION_2_3.sql.contains("price TEXT", ignoreCase = true),
            message = "Migration must define price as TEXT"
        )
    }

    @Test
    fun migration2To3_sqlCastsPriceAsText() {
        assertTrue(
            actual = MIGRATION_2_3.sql.contains("CAST(price AS TEXT)", ignoreCase = true),
            message = "Migration must CAST price AS TEXT when copying data"
        )
    }

    @Test
    fun migration2To3_sqlDropsOldAndRenames() {
        val sql = MIGRATION_2_3.sql
        assertTrue(
            actual = sql.contains("DROP TABLE concerts", ignoreCase = true),
            message = "Migration must drop old concerts table"
        )
        assertTrue(
            actual = sql.contains("ALTER TABLE concerts_new RENAME TO concerts", ignoreCase = true),
            message = "Migration must rename concerts_new to concerts"
        )
    }

    @Test
    fun migration2To3_sqlRecreatesIndexes() {
        val sql = MIGRATION_2_3.sql
        assertTrue(
            actual = sql.contains("index_concerts_date", ignoreCase = true),
            message = "Migration must recreate date index"
        )
        assertTrue(
            actual = sql.contains("index_concerts_sala_concierto_id", ignoreCase = true),
            message = "Migration must recreate sala_concierto_id index"
        )
    }

    // --- Migration 3 → 4 ---

    @Test
    fun migration3To4_startVersionIs3() {
        assertTrue(
            actual = MIGRATION_3_4.startVersion == 3,
            message = "Migration start version must be 3"
        )
    }

    @Test
    fun migration3To4_endVersionIs4() {
        assertTrue(
            actual = MIGRATION_3_4.endVersion == 4,
            message = "Migration end version must be 4"
        )
    }

    @Test
    fun migration3To4_sqlDropsTicketUrlFromConcerts() {
        val sql = MIGRATION_3_4.sql
        assertTrue(
            actual = sql.contains("concerts_v4", ignoreCase = true),
            message = "Migration must create concerts_v4 table"
        )
        assertTrue(
            actual = !sql.contains("ticket_url", ignoreCase = true),
            message = "Migration must NOT include ticket_url column in new concerts table"
        )
    }

    @Test
    fun migration3To4_sqlDropsPhoneAndWebsiteFromSalas() {
        val sql = MIGRATION_3_4.sql
        assertTrue(
            actual = sql.contains("salas_concierto_v4", ignoreCase = true),
            message = "Migration must create salas_concierto_v4 table"
        )
        assertTrue(
            actual = !sql.contains("phone", ignoreCase = true),
            message = "Migration must NOT include phone column in new salas_concierto table"
        )
        assertTrue(
            actual = !sql.contains("website", ignoreCase = true),
            message = "Migration must NOT include website column in new salas_concierto table"
        )
    }

    @Test
    fun migration3To4_sqlAddsDescriptionToSalas() {
        val sql = MIGRATION_3_4.sql
        assertTrue(
            actual = sql.contains("description TEXT", ignoreCase = true),
            message = "Migration must add description TEXT column to salas_concierto"
        )
    }

    @Test
    fun migration3To4_sqlDropsOldTablesAndRenames() {
        val sql = MIGRATION_3_4.sql
        assertTrue(
            actual = sql.contains("DROP TABLE concerts", ignoreCase = true),
            message = "Migration must drop old concerts table"
        )
        assertTrue(
            actual = sql.contains("ALTER TABLE concerts_v4 RENAME TO concerts", ignoreCase = true),
            message = "Migration must rename concerts_v4 to concerts"
        )
        assertTrue(
            actual = sql.contains("DROP TABLE salas_concierto", ignoreCase = true),
            message = "Migration must drop old salas_concierto table"
        )
        assertTrue(
            actual = sql.contains("ALTER TABLE salas_concierto_v4 RENAME TO salas_concierto", ignoreCase = true),
            message = "Migration must rename salas_concierto_v4 to salas_concierto"
        )
    }

    @Test
    fun migration3To4_sqlRecreatesIndexes() {
        val sql = MIGRATION_3_4.sql
        assertTrue(
            actual = sql.contains("index_concerts_date", ignoreCase = true),
            message = "Migration must recreate index_concerts_date"
        )
        assertTrue(
            actual = sql.contains("index_concerts_sala_concierto_id", ignoreCase = true),
            message = "Migration must recreate index_concerts_sala_concierto_id"
        )
        assertTrue(
            actual = sql.contains("index_salas_concierto_lat_lng", ignoreCase = true),
            message = "Migration must recreate index_salas_concierto_lat_lng"
        )
    }

    @Test
    fun migration4To5_startVersionIs4() {
        assertTrue(
            actual = MIGRATION_4_5.startVersion == 4,
            message = "Migration start version must be 4"
        )
    }

    @Test
    fun migration4To5_endVersionIs5() {
        assertTrue(
            actual = MIGRATION_4_5.endVersion == 5,
            message = "Migration end version must be 5"
        )
    }

    @Test
    fun migration4To5_sqlAddsDescriptionAndSourceUrlToArtists() {
        val sql = MIGRATION_4_5.sql
        assertTrue(
            actual = sql.contains("ALTER TABLE artists ADD COLUMN description TEXT", ignoreCase = true),
            message = "Migration must add description column to artists"
        )
        assertTrue(
            actual = sql.contains("ALTER TABLE artists ADD COLUMN source_url TEXT", ignoreCase = true),
            message = "Migration must add source_url column to artists"
        )
    }

    @Test
    fun migration4To5_sqlAddsSourceUrlToSalas() {
        val sql = MIGRATION_4_5.sql
        assertTrue(
            actual = sql.contains("ALTER TABLE salas_concierto ADD COLUMN source_url TEXT", ignoreCase = true),
            message = "Migration must add source_url column to salas_concierto"
        )
    }

    // --- Migration 5 → 6 ---

    @Test
    fun migration5To6_startVersionIs5() {
        assertTrue(
            actual = MIGRATION_5_6.startVersion == 5,
            message = "Migration start version must be 5"
        )
    }

    @Test
    fun migration5To6_endVersionIs6() {
        assertTrue(
            actual = MIGRATION_5_6.endVersion == 6,
            message = "Migration end version must be 6"
        )
    }

    @Test
    fun migration5To6_sqlDropsFavoritesTable() {
        val sql = MIGRATION_5_6.sql
        assertTrue(
            actual = sql.contains("DROP TABLE IF EXISTS favorites", ignoreCase = true),
            message = "Migration must drop the favorites table if it exists"
        )
    }

    @Test
    fun allMigrations_includeMigration5To6() {
        val migrations = allMigrations()
        assertTrue(
            actual = migrations.any { it.startVersion == 5 && it.endVersion == 6 },
            message = "allMigrations() must include the 5 -> 6 migration"
        )
    }
}
