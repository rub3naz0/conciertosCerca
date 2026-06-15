package org.rubenazo.conciertosfront.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.rubenazo.conciertosfront.core.data.local.dao.ArtistDao
import org.rubenazo.conciertosfront.core.data.local.dao.ArtistWithConcertRow
import org.rubenazo.conciertosfront.core.data.local.entity.ArtistEntity

class FakeArtistDao : ArtistDao {
    private val _entities = MutableStateFlow<List<ArtistEntity>>(emptyList())
    private val _withConcerts = MutableStateFlow<List<ArtistWithConcertRow>>(emptyList())
    private val _dateRangeWithConcerts = MutableStateFlow<List<ArtistWithConcertRow>>(emptyList())
    private val _searchWithConcerts = MutableStateFlow<List<ArtistWithConcertRow>>(emptyList())

    fun setEntities(entities: List<ArtistEntity>) { _entities.value = entities }
    fun setWithConcerts(rows: List<ArtistWithConcertRow>) { _withConcerts.value = rows }
    fun setDateRangeWithConcerts(rows: List<ArtistWithConcertRow>) { _dateRangeWithConcerts.value = rows }
    fun setSearchWithConcerts(rows: List<ArtistWithConcertRow>) { _searchWithConcerts.value = rows }

    var deleteOrphansCalled = false
    var lastSearchQuery: String? = null
    var upsertedArtists = mutableListOf<ArtistEntity>()

    override suspend fun upsert(artists: List<ArtistEntity>) { upsertedArtists.addAll(artists) }
    override fun getAll(): Flow<List<ArtistEntity>> = _entities
    override suspend fun getById(id: String): ArtistEntity? = _entities.value.find { it.id == id }
    override suspend fun getCount(): Int = _entities.value.size
    override suspend fun deleteOrphans() { deleteOrphansCalled = true }
    override fun getAllWithUpcomingConcerts(today: String): Flow<List<ArtistWithConcertRow>> = _withConcerts
    override fun getByDateRangeWithConcerts(startDate: String, endDate: String): Flow<List<ArtistWithConcertRow>> = _dateRangeWithConcerts
    override fun searchByNameWithUpcomingConcerts(query: String, today: String): Flow<List<ArtistWithConcertRow>> {
        lastSearchQuery = query
        return _searchWithConcerts
    }
}
