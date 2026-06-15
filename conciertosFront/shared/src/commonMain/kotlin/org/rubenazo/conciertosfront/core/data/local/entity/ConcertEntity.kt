package org.rubenazo.conciertosfront.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "concerts",
    foreignKeys = [
        ForeignKey(
            entity = SalaConciertoEntity::class,
            parentColumns = ["id"],
            childColumns = ["sala_concierto_id"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index("date"),
        Index("sala_concierto_id")
    ]
)
data class ConcertEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "sala_concierto_id")
    val salaConciertoId: String,
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "time")
    val time: String?,
    @ColumnInfo(name = "price")
    val price: String?,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
