package org.rubenazo.conciertosfront.core.data.repository

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.rubenazo.conciertosfront.core.data.local.DatabaseProviderPort
import org.rubenazo.conciertosfront.core.data.local.entity.ConcertArtistEntity
import org.rubenazo.conciertosfront.core.data.local.entity.SyncMetaEntity
import org.rubenazo.conciertosfront.core.data.remote.api.ConcertApiClient
import org.rubenazo.conciertosfront.core.domain.model.Artist
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto
import org.rubenazo.conciertosfront.core.domain.model.SyncResult
import org.rubenazo.conciertosfront.core.domain.model.SyncedConcert
import org.rubenazo.conciertosfront.core.domain.repository.SyncRepository
import org.rubenazo.conciertosfront.core.util.DateProvider

class SyncRepositoryImpl(
    private val concertApi: ConcertApiClient,
    private val provider: DatabaseProviderPort,
    private val dateProvider: DateProvider = DateProvider { error("DateProvider must be injected") },
    private val isRetry: Boolean = false,
) : SyncRepository {

    companion object {
        private const val KEY_SALAS_LAST_SYNC = "salas_last_sync"
        private const val KEY_ARTISTS_LAST_SYNC = "artists_last_sync"
        private const val KEY_CONCERTS_LAST_SYNC = "concerts_last_sync"
    }

    override suspend fun sync(): SyncResult = syncInternal(dbRecovered = false)

    override suspend fun hasLocalData(): Boolean =
        try {
            provider.syncMetaDao().getByKey(KEY_CONCERTS_LAST_SYNC) != null
        } catch (e: SQLiteException) {
            // A broken DB means there's nothing usable to show — fall back to the blocking
            // sync path, which will reset and recover the database.
            false
        }

    private suspend fun syncInternal(dbRecovered: Boolean): SyncResult {
        val today = dateProvider.today()

        return try {
            // purgePast and deleteOrphans are first DB writes; keep them inside the try so a
            // corrupt-DB SQLiteException is caught by the reset+retry recovery path below.
            provider.concertDao().purgePast(today)
            provider.artistDao().deleteOrphans()

            val salasLastSync = provider.syncMetaDao().getByKey(KEY_SALAS_LAST_SYNC)?.value
            val artistsLastSync = provider.syncMetaDao().getByKey(KEY_ARTISTS_LAST_SYNC)?.value
            val concertsLastSync = provider.syncMetaDao().getByKey(KEY_CONCERTS_LAST_SYNC)?.value

            val (salasHasUpdates, artistsHasUpdates, concertsHasUpdates) = coroutineScope {
                val salasDeferred = async { concertApi.checkSalasUpdates(salasLastSync) }
                val artistsDeferred = async { concertApi.checkArtistsUpdates(artistsLastSync) }
                val concertsDeferred = async { concertApi.checkConcertsUpdates(concertsLastSync) }
                Triple(salasDeferred.await(), artistsDeferred.await(), concertsDeferred.await())
            }

            var salasCount = 0
            var artistsCount = 0
            var concertsCount = 0
            var deletedCount = 0
            var newSalas = emptyList<SalaConcierto>()
            var newArtists = emptyList<Artist>()
            var newConcerts = emptyList<SyncedConcert>()

            if (salasHasUpdates) {
                val response = concertApi.getSalas(salasLastSync)
                provider.salaConciertoDao().upsert(response.data.map { it.toEntity() })
                newSalas = response.data.map { it.toDomain() }
                salasCount = response.data.size
                provider.syncMetaDao().upsert(SyncMetaEntity(KEY_SALAS_LAST_SYNC, response.timestamp))
            }

            if (artistsHasUpdates) {
                val response = concertApi.getArtists(artistsLastSync)
                provider.artistDao().upsert(response.data.map { it.toEntity() })
                newArtists = response.data.map { it.toDomain() }
                artistsCount = response.data.size
                provider.syncMetaDao().upsert(SyncMetaEntity(KEY_ARTISTS_LAST_SYNC, response.timestamp))
            }

            if (concertsHasUpdates) {
                val response = concertApi.getConcerts(concertsLastSync)

                provider.withTransaction {
                    if (response.deletedIds.isNotEmpty()) {
                        provider.concertDao().deleteByIds(response.deletedIds)
                        deletedCount = response.deletedIds.size
                    }

                    provider.concertDao().upsert(response.data.map { it.toEntity() })

                    response.data.forEach { concertDto ->
                        provider.concertDao().deleteConcertArtistsByConcertId(concertDto.id)
                        val concertArtists = concertDto.artistIds.mapIndexed { index, artistId ->
                            ConcertArtistEntity(
                                concertId = concertDto.id,
                                artistId = artistId,
                                position = index
                            )
                        }
                        provider.concertDao().upsertConcertArtists(concertArtists)
                    }

                    provider.syncMetaDao().upsert(SyncMetaEntity(KEY_CONCERTS_LAST_SYNC, response.timestamp))
                }

                newConcerts = response.data.map { it.toSynced() }
                concertsCount = response.data.size
            }

            SyncResult(
                salasCount = salasCount,
                artistsCount = artistsCount,
                concertsCount = concertsCount,
                deletedConcertsCount = deletedCount,
                hadNetwork = true,
                errors = emptyList(),
                newSalas = newSalas,
                newArtists = newArtists,
                newConcerts = newConcerts,
                dbRecovered = dbRecovered,
            )
        } catch (e: SQLiteException) {
            if (isRetry) {
                // Already retried once — report failure without another reset
                SyncResult(
                    salasCount = 0,
                    artistsCount = 0,
                    concertsCount = 0,
                    deletedConcertsCount = 0,
                    hadNetwork = false,
                    errors = listOf(e.message ?: "SQLite error after recovery"),
                )
            } else {
                provider.reset()
                SyncRepositoryImpl(
                    concertApi = concertApi,
                    provider = provider,
                    dateProvider = dateProvider,
                    isRetry = true,
                ).syncInternal(dbRecovered = true)
            }
        } catch (e: Exception) {
            SyncResult(
                salasCount = 0,
                artistsCount = 0,
                concertsCount = 0,
                deletedConcertsCount = 0,
                hadNetwork = false,
                errors = listOf(e.message ?: "Unknown error"),
            )
        }
    }
}
