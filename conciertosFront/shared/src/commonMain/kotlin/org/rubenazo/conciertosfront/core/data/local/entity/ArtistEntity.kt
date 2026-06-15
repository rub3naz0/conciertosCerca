package org.rubenazo.conciertosfront.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "genre")
    val genre: String?,
    @ColumnInfo(name = "image_url")
    val imageUrl: String?,
    @ColumnInfo(name = "website")
    val website: String?,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String?
)
