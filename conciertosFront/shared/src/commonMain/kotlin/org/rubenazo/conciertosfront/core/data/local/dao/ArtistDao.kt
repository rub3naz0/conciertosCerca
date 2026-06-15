package org.rubenazo.conciertosfront.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.rubenazo.conciertosfront.core.data.local.entity.ArtistEntity

data class ArtistWithConcertRow(
    val artistId: String,
    val artistName: String,
    val artistGenre: String?,
    val artistImageUrl: String?,
    val artistWebsite: String?,
    val artistDescription: String?,
    val artistSourceUrl: String?,
    val concertId: String?,
    val concertDate: String?,
    val concertTime: String?,
    val salaName: String?,
    val salaCity: String?,
)

@Dao
interface ArtistDao {
    @Upsert
    suspend fun upsert(artists: List<ArtistEntity>)

    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: String): ArtistEntity?

    @Query("SELECT COUNT(*) FROM artists")
    suspend fun getCount(): Int

    @Query("DELETE FROM artists WHERE id NOT IN (SELECT DISTINCT artist_id FROM concert_artists)")
    suspend fun deleteOrphans()

    @Query(
        """
        SELECT
            a.id AS artistId,
            a.name AS artistName,
            a.genre AS artistGenre,
            a.image_url AS artistImageUrl,
            a.website AS artistWebsite,
            a.description AS artistDescription,
            a.source_url AS artistSourceUrl,
            c.id AS concertId,
            c.date AS concertDate,
            c.time AS concertTime,
            s.name AS salaName,
            s.city AS salaCity
        FROM artists a
        LEFT JOIN concert_artists ca ON ca.artist_id = a.id
        LEFT JOIN concerts c ON c.id = ca.concert_id AND c.date >= :today
        LEFT JOIN salas_concierto s ON s.id = c.sala_concierto_id
        ORDER BY a.name ASC, c.date ASC
        """
    )
    fun getAllWithUpcomingConcerts(today: String): Flow<List<ArtistWithConcertRow>>

    @Query(
        """
        SELECT
            a.id AS artistId,
            a.name AS artistName,
            a.genre AS artistGenre,
            a.image_url AS artistImageUrl,
            a.website AS artistWebsite,
            a.description AS artistDescription,
            a.source_url AS artistSourceUrl,
            c.id AS concertId,
            c.date AS concertDate,
            c.time AS concertTime,
            s.name AS salaName,
            s.city AS salaCity
        FROM artists a
        INNER JOIN concert_artists ca ON ca.artist_id = a.id
        INNER JOIN concerts c ON c.id = ca.concert_id
        LEFT JOIN salas_concierto s ON s.id = c.sala_concierto_id
        WHERE c.date BETWEEN :startDate AND :endDate
        ORDER BY a.name ASC, c.date ASC
        """
    )
    fun getByDateRangeWithConcerts(startDate: String, endDate: String): Flow<List<ArtistWithConcertRow>>

    @Query(
        """
        SELECT
            a.id AS artistId,
            a.name AS artistName,
            a.genre AS artistGenre,
            a.image_url AS artistImageUrl,
            a.website AS artistWebsite,
            a.description AS artistDescription,
            a.source_url AS artistSourceUrl,
            c.id AS concertId,
            c.date AS concertDate,
            c.time AS concertTime,
            s.name AS salaName,
            s.city AS salaCity
        FROM artists a
        LEFT JOIN concert_artists ca ON ca.artist_id = a.id
        LEFT JOIN concerts c ON c.id = ca.concert_id AND c.date >= :today
        LEFT JOIN salas_concierto s ON s.id = c.sala_concierto_id
        WHERE a.name LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY a.name ASC, c.date ASC
        """
    )
    fun searchByNameWithUpcomingConcerts(query: String, today: String): Flow<List<ArtistWithConcertRow>>
}
