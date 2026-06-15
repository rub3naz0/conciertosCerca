package org.rubenazo.conciertosfront.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "salas_concierto",
    indices = [Index(value = ["lat", "lng"])]
)
data class SalaConciertoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "address")
    val address: String?,
    @ColumnInfo(name = "city")
    val city: String,
    @ColumnInfo(name = "province")
    val province: String,
    @ColumnInfo(name = "lat")
    val lat: Double?,
    @ColumnInfo(name = "lng")
    val lng: Double?,
    @ColumnInfo(name = "image_url")
    val imageUrl: String?,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String?
)
