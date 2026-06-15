package org.rubenazo.conciertosfront.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun getDatabaseBuilder(context: Context): androidx.room.RoomDatabase.Builder<AppDatabase> {
    val dbFile = context.getDatabasePath(DB_NAME)
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = dbFile.absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*allMigrations())
}
