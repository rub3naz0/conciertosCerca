package org.rubenazo.conciertosfront.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.rubenazo.conciertosfront.core.data.local.entity.SalaConciertoEntity

data class SalaWithConcertRow(
    val salaId: String,
    val salaName: String,
    val salaAddress: String?,
    val salaCity: String,
    val salaProvince: String,
    val salaLat: Double?,
    val salaLng: Double?,
    val salaImageUrl: String?,
    val salaDescription: String?,
    val salaSourceUrl: String?,
    val concertId: String?,
    val concertDate: String?,
    val concertTime: String?,
    val artistName: String?,
)

@Dao
interface SalaConciertoDao {
    @Upsert
    suspend fun upsert(salas: List<SalaConciertoEntity>)

    @Query("SELECT * FROM salas_concierto ORDER BY name ASC")
    fun getAll(): Flow<List<SalaConciertoEntity>>

    @Query("SELECT * FROM salas_concierto WHERE id = :id")
    suspend fun getById(id: String): SalaConciertoEntity?

    @Query("SELECT COUNT(*) FROM salas_concierto")
    suspend fun getCount(): Int

    @Query(
        """
        SELECT
            s.id AS salaId,
            s.name AS salaName,
            s.address AS salaAddress,
            s.city AS salaCity,
            s.province AS salaProvince,
            s.lat AS salaLat,
            s.lng AS salaLng,
            s.image_url AS salaImageUrl,
            s.description AS salaDescription,
            s.source_url AS salaSourceUrl,
            c.id AS concertId,
            c.date AS concertDate,
            c.time AS concertTime,
            a.name AS artistName
        FROM salas_concierto s
        INNER JOIN concerts c ON c.sala_concierto_id = s.id
        INNER JOIN concert_artists ca ON ca.concert_id = c.id
        INNER JOIN artists a ON a.id = ca.artist_id
        WHERE c.date BETWEEN :startDate AND :endDate
        ORDER BY s.name ASC, c.date ASC, ca.position ASC
        """
    )
    fun getByDateRangeWithConcerts(startDate: String, endDate: String): Flow<List<SalaWithConcertRow>>
}
