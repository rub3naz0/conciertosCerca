package org.rubenazo.conciertosfront.core.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Absolute path to the SQLite file inside the app's Documents directory.
 * Documents is the correct location for user data that should persist and
 * be backed up, mirroring Android's `getDatabasePath`.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun databaseFilePath(): String {
    val documentsUrl = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val documentsPath = requireNotNull(documentsUrl?.path) {
        "Unable to resolve iOS Documents directory for the database"
    }
    return "$documentsPath/$DB_NAME"
}

fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(
        name = databaseFilePath(),
    )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*allMigrations())
}
