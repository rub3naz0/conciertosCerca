package org.rubenazo.conciertosfront.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.rubenazo.conciertosfront.core.data.local.entity.ConcertArtistEntity
import org.rubenazo.conciertosfront.core.data.local.entity.ConcertEntity

data class ConcertWithDetails(
    val concertId: String,
    val date: String,
    val time: String?,
    val price: String?,
    val sourceUrl: String?,
    val updatedAt: String,
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
    val artistId: String?,
    val artistName: String?,
    val artistGenre: String?,
    val artistImageUrl: String?,
    val artistWebsite: String?,
    val artistDescription: String?,
    val artistSourceUrl: String?,
    val artistPosition: Int?
)

@Dao
interface ConcertDao {
    @Upsert
    suspend fun upsert(concerts: List<ConcertEntity>)

    // Raw query — deletes only from concerts; use deleteByIds() for transactional deletion
    @Query("DELETE FROM concerts WHERE id IN (:ids)")
    suspend fun deleteConcertsByIds(ids: List<String>)

    // Raw query — deletes only from concerts; use purgePast() for transactional deletion
    @Query("DELETE FROM concerts WHERE date < :today")
    suspend fun purgePastConcerts(today: String)

    // Deletes orphaned junction rows for the given concert ids before deleting the concerts
    // (BundledSQLiteDriver runs with FK enforcement off, so CASCADE does not fire)
    @Query("DELETE FROM concert_artists WHERE concert_id IN (:ids)")
    suspend fun deleteConcertArtistsByIds(ids: List<String>)

    // Deletes orphaned junction rows for past concerts before deleting the concerts
    @Query("DELETE FROM concert_artists WHERE concert_id IN (SELECT id FROM concerts WHERE date < :today)")
    suspend fun deletePastConcertArtists(today: String)

    @Transaction
    suspend fun deleteByIds(ids: List<String>) {
        deleteConcertArtistsByIds(ids)
        deleteConcertsByIds(ids)
    }

    @Transaction
    suspend fun purgePast(today: String) {
        deletePastConcertArtists(today)
        purgePastConcerts(today)
    }

    @Query("SELECT COUNT(*) FROM concerts")
    suspend fun getCount(): Int

    @Query(
        """
        SELECT
            c.id AS concertId,
            c.date AS date,
            c.time AS time,
            c.price AS price,
            c.source_url AS sourceUrl,
            c.updated_at AS updatedAt,
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
            a.id AS artistId,
            a.name AS artistName,
            a.genre AS artistGenre,
            a.image_url AS artistImageUrl,
            a.website AS artistWebsite,
            a.description AS artistDescription,
            a.source_url AS artistSourceUrl,
            ca.position AS artistPosition
        FROM concerts c
        INNER JOIN salas_concierto s ON s.id = c.sala_concierto_id
        LEFT JOIN concert_artists ca ON ca.concert_id = c.id
        LEFT JOIN artists a ON a.id = ca.artist_id
        ORDER BY c.date ASC, ca.position ASC
        """
    )
    fun getAllWithDetails(): Flow<List<ConcertWithDetails>>

    @Query(
        """
        SELECT
            c.id AS concertId,
            c.date AS date,
            c.time AS time,
            c.price AS price,
            c.source_url AS sourceUrl,
            c.updated_at AS updatedAt,
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
            a.id AS artistId,
            a.name AS artistName,
            a.genre AS artistGenre,
            a.image_url AS artistImageUrl,
            a.website AS artistWebsite,
            a.description AS artistDescription,
            a.source_url AS artistSourceUrl,
            ca.position AS artistPosition
        FROM concerts c
        INNER JOIN salas_concierto s ON s.id = c.sala_concierto_id
        LEFT JOIN concert_artists ca ON ca.concert_id = c.id
        LEFT JOIN artists a ON a.id = ca.artist_id
        ORDER BY c.date ASC, ca.position ASC
        """
    )
    suspend fun getAllWithDetailsOnce(): List<ConcertWithDetails>

    @Query(
        """
        SELECT
            c.id AS concertId,
            c.date AS date,
            c.time AS time,
            c.price AS price,
            c.source_url AS sourceUrl,
            c.updated_at AS updatedAt,
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
            a.id AS artistId,
            a.name AS artistName,
            a.genre AS artistGenre,
            a.image_url AS artistImageUrl,
            a.website AS artistWebsite,
            a.description AS artistDescription,
            a.source_url AS artistSourceUrl,
            ca.position AS artistPosition
        FROM concerts c
        INNER JOIN salas_concierto s ON s.id = c.sala_concierto_id
        LEFT JOIN concert_artists ca ON ca.concert_id = c.id
        LEFT JOIN artists a ON a.id = ca.artist_id
        WHERE c.date BETWEEN :startDate AND :endDate
        ORDER BY c.date ASC, ca.position ASC
        """
    )
    fun getByDateRangeWithDetails(startDate: String, endDate: String): Flow<List<ConcertWithDetails>>

    @Query(
        """
        SELECT
            c.id AS concertId,
            c.date AS date,
            c.time AS time,
            c.price AS price,
            c.source_url AS sourceUrl,
            c.updated_at AS updatedAt,
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
            a.id AS artistId,
            a.name AS artistName,
            a.genre AS artistGenre,
            a.image_url AS artistImageUrl,
            a.website AS artistWebsite,
            a.description AS artistDescription,
            a.source_url AS artistSourceUrl,
            ca.position AS artistPosition
        FROM concerts c
        INNER JOIN salas_concierto s ON s.id = c.sala_concierto_id
        LEFT JOIN concert_artists ca ON ca.concert_id = c.id
        LEFT JOIN artists a ON a.id = ca.artist_id
        WHERE c.date >= :today
        ORDER BY c.date ASC, ca.position ASC
        """
    )
    fun getUpcomingWithDetails(today: String): Flow<List<ConcertWithDetails>>

    @Query(
        """
        SELECT
            c.id AS concertId,
            c.date AS date,
            c.time AS time,
            c.price AS price,
            c.source_url AS sourceUrl,
            c.updated_at AS updatedAt,
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
            a.id AS artistId,
            a.name AS artistName,
            a.genre AS artistGenre,
            a.image_url AS artistImageUrl,
            a.website AS artistWebsite,
            a.description AS artistDescription,
            a.source_url AS artistSourceUrl,
            ca.position AS artistPosition
        FROM concerts c
        INNER JOIN salas_concierto s ON s.id = c.sala_concierto_id
        LEFT JOIN concert_artists ca ON ca.concert_id = c.id
        LEFT JOIN artists a ON a.id = ca.artist_id
        WHERE c.id = :concertId
        ORDER BY ca.position ASC
        """
    )
    suspend fun getByIdWithDetails(concertId: String): List<ConcertWithDetails>

    @Query(
        """
        SELECT
            c.id AS concertId,
            c.date AS date,
            c.time AS time,
            c.price AS price,
            c.source_url AS sourceUrl,
            c.updated_at AS updatedAt,
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
            a.id AS artistId,
            a.name AS artistName,
            a.genre AS artistGenre,
            a.image_url AS artistImageUrl,
            a.website AS artistWebsite,
            a.description AS artistDescription,
            a.source_url AS artistSourceUrl,
            ca.position AS artistPosition
        FROM concerts c
        INNER JOIN salas_concierto s ON s.id = c.sala_concierto_id
        LEFT JOIN concert_artists ca ON ca.concert_id = c.id
        LEFT JOIN artists a ON a.id = ca.artist_id
        WHERE s.lat BETWEEN :latMin AND :latMax
          AND s.lng BETWEEN :lngMin AND :lngMax
          AND c.date BETWEEN :startDate AND :endDate
        ORDER BY c.date ASC, ca.position ASC
        """
    )
    fun getConcertsInBoundingBox(
        latMin: Double,
        latMax: Double,
        lngMin: Double,
        lngMax: Double,
        startDate: String,
        endDate: String,
    ): Flow<List<ConcertWithDetails>>

    @Query("SELECT * FROM concert_artists WHERE concert_id = :concertId ORDER BY position ASC")
    suspend fun getConcertArtistsByConcertId(concertId: String): List<ConcertArtistEntity>

    @Query("SELECT * FROM concert_artists")
    suspend fun getAllConcertArtists(): List<ConcertArtistEntity>

    @Upsert
    suspend fun upsertConcertArtist(concertArtist: ConcertArtistEntity)

    @Upsert
    suspend fun upsertConcertArtists(concertArtists: List<ConcertArtistEntity>)

    @Query("DELETE FROM concert_artists WHERE concert_id = :concertId")
    suspend fun deleteConcertArtistsByConcertId(concertId: String)
}
