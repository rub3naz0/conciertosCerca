package org.rubenazo.conciertosfront.core.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import org.rubenazo.conciertosfront.core.data.local.dao.ArtistDao
import org.rubenazo.conciertosfront.core.data.local.dao.ConcertDao
import org.rubenazo.conciertosfront.core.data.local.dao.SalaConciertoDao
import org.rubenazo.conciertosfront.core.data.local.dao.SyncMetaDao
import org.rubenazo.conciertosfront.core.data.local.entity.ArtistEntity
import org.rubenazo.conciertosfront.core.data.local.entity.ConcertArtistEntity
import org.rubenazo.conciertosfront.core.data.local.entity.ConcertEntity
import org.rubenazo.conciertosfront.core.data.local.entity.SalaConciertoEntity
import org.rubenazo.conciertosfront.core.data.local.entity.SyncMetaEntity

@Database(
    entities = [
        SalaConciertoEntity::class,
        ArtistEntity::class,
        ConcertEntity::class,
        ConcertArtistEntity::class,
        SyncMetaEntity::class
    ],
    version = 6
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun salaConciertoDao(): SalaConciertoDao
    abstract fun artistDao(): ArtistDao
    abstract fun concertDao(): ConcertDao
    abstract fun syncMetaDao(): SyncMetaDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
