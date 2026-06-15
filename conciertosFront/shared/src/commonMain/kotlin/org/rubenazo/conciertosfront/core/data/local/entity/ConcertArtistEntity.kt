package org.rubenazo.conciertosfront.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "concert_artists",
    primaryKeys = ["concert_id", "artist_id"],
    foreignKeys = [
        ForeignKey(
            entity = ConcertEntity::class,
            parentColumns = ["id"],
            childColumns = ["concert_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index("artist_id")
    ]
)
data class ConcertArtistEntity(
    @ColumnInfo(name = "concert_id")
    val concertId: String,
    @ColumnInfo(name = "artist_id")
    val artistId: String,
    @ColumnInfo(name = "position")
    val position: Int = 0
)
