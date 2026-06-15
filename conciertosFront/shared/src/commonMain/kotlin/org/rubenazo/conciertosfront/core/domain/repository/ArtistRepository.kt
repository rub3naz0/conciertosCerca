package org.rubenazo.conciertosfront.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.rubenazo.conciertosfront.core.domain.model.Artist
import org.rubenazo.conciertosfront.core.domain.model.ArtistWithConcerts

/**
 * Read port for artists, backed by the local Room cache. `...Flow` methods are reactive and
 * re-emit on data changes; `suspend` methods are one-shot reads. The `WithConcerts` variants join
 * each artist with their upcoming concerts for the artists screen.
 */
interface ArtistRepository {
    fun getAllFlow(): Flow<List<Artist>>
    fun getAllWithUpcomingConcertsFlow(today: String): Flow<List<ArtistWithConcerts>>
    fun getByDateRangeWithConcertsFlow(startDate: String, endDate: String): Flow<List<ArtistWithConcerts>>
    fun searchByNameWithConcertsFlow(query: String, today: String): Flow<List<ArtistWithConcerts>>
    suspend fun getAll(): List<Artist>
    suspend fun getCount(): Int
}
