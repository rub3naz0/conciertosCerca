package org.rubenazo.conciertosfront.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.rubenazo.conciertosfront.core.domain.model.Artist
import org.rubenazo.conciertosfront.core.domain.model.ArtistWithConcerts
import org.rubenazo.conciertosfront.core.domain.repository.ArtistRepository

class FakeArtistRepository : ArtistRepository {
    var artists: List<Artist> = emptyList()
    var artistsWithConcerts: List<ArtistWithConcerts> = emptyList()
    var allUpcomingResults: List<ArtistWithConcerts>? = null
    var searchResults: List<ArtistWithConcerts> = emptyList()
    var lastSearchQuery: String? = null

    override fun getAllFlow(): Flow<List<Artist>> = flowOf(artists)
    override fun getAllWithUpcomingConcertsFlow(today: String): Flow<List<ArtistWithConcerts>> = flowOf(allUpcomingResults ?: artistsWithConcerts)
    override fun getByDateRangeWithConcertsFlow(startDate: String, endDate: String): Flow<List<ArtistWithConcerts>> = flowOf(artistsWithConcerts)
    override fun searchByNameWithConcertsFlow(query: String, today: String): Flow<List<ArtistWithConcerts>> {
        lastSearchQuery = query
        return flowOf(searchResults)
    }
    override suspend fun getAll(): List<Artist> = artists
    override suspend fun getCount(): Int = artists.size
}
