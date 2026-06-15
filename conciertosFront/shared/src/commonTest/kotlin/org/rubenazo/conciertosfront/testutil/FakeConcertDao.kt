package org.rubenazo.conciertosfront.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.rubenazo.conciertosfront.core.data.local.dao.ConcertDao
import org.rubenazo.conciertosfront.core.data.local.dao.ConcertWithDetails
import org.rubenazo.conciertosfront.core.data.local.entity.ConcertArtistEntity
import org.rubenazo.conciertosfront.core.data.local.entity.ConcertEntity

class FakeConcertDao : ConcertDao {
    private val _allDetails = MutableStateFlow<List<ConcertWithDetails>>(emptyList())
    private val _dateRangeDetails = MutableStateFlow<List<ConcertWithDetails>>(emptyList())
    private val _upcomingDetails = MutableStateFlow<List<ConcertWithDetails>>(emptyList())
    private val _boundingBoxDetails = MutableStateFlow<List<ConcertWithDetails>>(emptyList())
    private var _count = 0

    var purgePastCalledWith: String? = null
    var upsertedConcerts = mutableListOf<ConcertEntity>()
    var deletedIds = mutableListOf<String>()
    var deletedConcertArtistsConcertIds = mutableListOf<String>()
    var upsertedConcertArtists = mutableListOf<ConcertArtistEntity>()

    // Junction-row store + observation fields for the transactional delete paths (fix #1).
    // deletionOrder records each raw delete so tests can assert junction rows go before concerts.
    val concertArtistsStore = mutableListOf<ConcertArtistEntity>()
    private val concertDates = mutableMapOf<String, String>()
    var deletionOrder = mutableListOf<String>()
    var deletedConcertArtistsByIds = mutableListOf<String>()

    /** Seed a junction row whose parent concert has [date], so date-based purge can be exercised. */
    fun seedConcertArtist(concertId: String, artistId: String, date: String) {
        concertArtistsStore.add(ConcertArtistEntity(concertId = concertId, artistId = artistId))
        concertDates[concertId] = date
    }

    fun setAllDetails(details: List<ConcertWithDetails>) { _allDetails.value = details }
    fun setDateRangeDetails(details: List<ConcertWithDetails>) { _dateRangeDetails.value = details }
    fun setUpcomingDetails(details: List<ConcertWithDetails>) { _upcomingDetails.value = details }
    fun setBoundingBoxDetails(details: List<ConcertWithDetails>) { _boundingBoxDetails.value = details }
    fun setCount(count: Int) { _count = count }

    override suspend fun upsert(concerts: List<ConcertEntity>) { upsertedConcerts.addAll(concerts) }
    // deleteByIds / purgePast are intentionally NOT overridden: the real @Transaction default
    // methods run, exercising the junction-then-concert deletion order that fix #1 introduced.
    override suspend fun deleteConcertArtistsByIds(ids: List<String>) {
        deletionOrder.add("junctionByIds")
        deletedConcertArtistsByIds.addAll(ids)
        concertArtistsStore.removeAll { it.concertId in ids }
    }
    override suspend fun deleteConcertsByIds(ids: List<String>) {
        deletionOrder.add("concertsByIds")
        deletedIds.addAll(ids)
        ids.forEach { concertDates.remove(it) }
    }
    override suspend fun deletePastConcertArtists(today: String) {
        deletionOrder.add("junctionPast")
        concertArtistsStore.removeAll { (concertDates[it.concertId] ?: today) < today }
    }
    override suspend fun purgePastConcerts(today: String) {
        deletionOrder.add("concertsPast")
        purgePastCalledWith = today
        concertDates.keys.filter { (concertDates[it] ?: today) < today }.forEach { concertDates.remove(it) }
    }
    override suspend fun getCount(): Int = _count
    override fun getAllWithDetails(): Flow<List<ConcertWithDetails>> = _allDetails
    override suspend fun getAllWithDetailsOnce(): List<ConcertWithDetails> = _allDetails.value
    override fun getByDateRangeWithDetails(startDate: String, endDate: String): Flow<List<ConcertWithDetails>> = _dateRangeDetails
    override fun getUpcomingWithDetails(today: String): Flow<List<ConcertWithDetails>> = _upcomingDetails
    override fun getConcertsInBoundingBox(
        latMin: Double,
        latMax: Double,
        lngMin: Double,
        lngMax: Double,
        startDate: String,
        endDate: String,
    ): Flow<List<ConcertWithDetails>> = _boundingBoxDetails
    override suspend fun getByIdWithDetails(concertId: String): List<ConcertWithDetails> =
        _allDetails.value.filter { it.concertId == concertId }
    override suspend fun getConcertArtistsByConcertId(concertId: String): List<ConcertArtistEntity> = emptyList()
    override suspend fun getAllConcertArtists(): List<ConcertArtistEntity> = emptyList()
    override suspend fun upsertConcertArtist(concertArtist: ConcertArtistEntity) {}
    override suspend fun upsertConcertArtists(concertArtists: List<ConcertArtistEntity>) { upsertedConcertArtists.addAll(concertArtists) }
    override suspend fun deleteConcertArtistsByConcertId(concertId: String) { deletedConcertArtistsConcertIds.add(concertId) }
}
