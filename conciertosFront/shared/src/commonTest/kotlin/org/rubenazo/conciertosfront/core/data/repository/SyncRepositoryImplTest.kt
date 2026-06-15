package org.rubenazo.conciertosfront.core.data.repository

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rubenazo.conciertosfront.core.data.local.entity.SyncMetaEntity
import org.rubenazo.conciertosfront.core.data.remote.dto.ApiResponse
import org.rubenazo.conciertosfront.core.data.remote.dto.ArtistDto
import org.rubenazo.conciertosfront.core.data.remote.dto.ConcertApiResponse
import org.rubenazo.conciertosfront.core.data.remote.dto.ConcertDto
import org.rubenazo.conciertosfront.core.data.remote.dto.SalaConciertoDto
import org.rubenazo.conciertosfront.core.util.DateProvider
import org.rubenazo.conciertosfront.testutil.FakeConcertApiClient
import org.rubenazo.conciertosfront.testutil.FakeDatabaseProvider

class SyncRepositoryImplTest {

    private val api = FakeConcertApiClient()
    private val fakeProvider = FakeDatabaseProvider()
    private val fixedDate = "2026-06-01"
    private val fakeDateProvider = DateProvider { fixedDate }

    private val repo = SyncRepositoryImpl(
        concertApi = api,
        provider = fakeProvider,
        dateProvider = fakeDateProvider,
    )

    // Convenience accessors for the DAOs inside fakeProvider
    private val salaDao get() = fakeProvider.salaConciertoDao() as org.rubenazo.conciertosfront.testutil.FakeSalaConciertoDao
    private val artistDao get() = fakeProvider.artistDao() as org.rubenazo.conciertosfront.testutil.FakeArtistDao
    private val concertDao get() = fakeProvider.concertDao() as org.rubenazo.conciertosfront.testutil.FakeConcertDao
    private val syncMetaDao get() = fakeProvider.syncMetaDao() as org.rubenazo.conciertosfront.testutil.FakeSyncMetaDao

    @Test
    fun sync_alwaysPurgesPastAndDeletesOrphans() = runTest {
        val result = repo.sync()

        assertEquals("2026-06-01", concertDao.purgePastCalledWith)
        assertTrue(artistDao.deleteOrphansCalled)
        assertTrue(result.hadNetwork)
    }

    @Test
    fun sync_noUpdates_returnsZeroCounts() = runTest {
        api.salasHasUpdates = false
        api.artistsHasUpdates = false
        api.concertsHasUpdates = false

        val result = repo.sync()

        assertEquals(0, result.salasCount)
        assertEquals(0, result.artistsCount)
        assertEquals(0, result.concertsCount)
        assertEquals(0, result.deletedConcertsCount)
        assertTrue(result.hadNetwork)
        assertTrue(result.errors.isEmpty())
        assertTrue(salaDao.upsertedSalas.isEmpty())
        assertTrue(artistDao.upsertedArtists.isEmpty())
        assertTrue(concertDao.upsertedConcerts.isEmpty())
    }

    @Test
    fun sync_salasUpdated_upsertsSalasAndSavesMeta() = runTest {
        api.salasHasUpdates = true
        api.salasResponse = ApiResponse(
            timestamp = "2026-06-01T12:00:00Z",
            data = listOf(
                SalaConciertoDto("s1", "Sala Apolo", "C/ Nou 113", "Barcelona", "Barcelona", 41.37, 2.17, null, null, null),
            ),
        )

        val result = repo.sync()

        assertEquals(1, result.salasCount)
        assertEquals(1, salaDao.upsertedSalas.size)
        assertEquals("s1", salaDao.upsertedSalas[0].id)
        assertEquals("Sala Apolo", salaDao.upsertedSalas[0].name)
        assertEquals("2026-06-01T12:00:00Z", syncMetaDao.store["salas_last_sync"]?.value)
    }

    @Test
    fun sync_artistsUpdated_upsertsArtistsAndSavesMeta() = runTest {
        api.artistsHasUpdates = true
        api.artistsResponse = ApiResponse(
            timestamp = "2026-06-01T12:00:00Z",
            data = listOf(
                ArtistDto("a1", "Rosalía", "Pop", null, null, null, null),
                ArtistDto("a2", "C. Tangana", "Urban", null, null, null, null),
            ),
        )

        val result = repo.sync()

        assertEquals(2, result.artistsCount)
        assertEquals(2, artistDao.upsertedArtists.size)
        assertEquals("2026-06-01T12:00:00Z", syncMetaDao.store["artists_last_sync"]?.value)
    }

    @Test
    fun sync_concertsUpdated_upsertsConcertsAndArtistLinks() = runTest {
        api.concertsHasUpdates = true
        api.concertsResponse = ConcertApiResponse(
            timestamp = "2026-06-01T12:00:00Z",
            data = listOf(
                ConcertDto("c1", "s1", listOf("a1", "a2"), "2026-06-15", "21:00", "25€", null, "2026-05-24T10:00:00Z"),
            ),
            deletedIds = emptyList(),
        )

        val result = repo.sync()

        assertEquals(1, result.concertsCount)
        assertEquals(0, result.deletedConcertsCount)
        assertEquals(1, concertDao.upsertedConcerts.size)
        assertEquals("c1", concertDao.upsertedConcerts[0].id)
        assertEquals("s1", concertDao.upsertedConcerts[0].salaConciertoId)
        assertEquals(listOf("c1"), concertDao.deletedConcertArtistsConcertIds)
        assertEquals(2, concertDao.upsertedConcertArtists.size)
        assertEquals("a1", concertDao.upsertedConcertArtists[0].artistId)
        assertEquals(0, concertDao.upsertedConcertArtists[0].position)
        assertEquals("a2", concertDao.upsertedConcertArtists[1].artistId)
        assertEquals(1, concertDao.upsertedConcertArtists[1].position)
        assertEquals("2026-06-01T12:00:00Z", syncMetaDao.store["concerts_last_sync"]?.value)
    }

    @Test
    fun sync_concertsWithDeletedIds_deletesThenUpserts() = runTest {
        api.concertsHasUpdates = true
        api.concertsResponse = ConcertApiResponse(
            timestamp = "2026-06-01T12:00:00Z",
            data = listOf(
                ConcertDto("c2", "s1", listOf("a1"), "2026-06-20", null, null, null, "2026-05-24T10:00:00Z"),
            ),
            deletedIds = listOf("c_old1", "c_old2"),
        )

        val result = repo.sync()

        assertEquals(1, result.concertsCount)
        assertEquals(2, result.deletedConcertsCount)
        assertEquals(listOf("c_old1", "c_old2"), concertDao.deletedIds)
    }

    // --- Fix #1: deletion paths must remove junction rows BEFORE concerts (FK enforcement is off) ---
    @Test
    fun sync_deletedConcerts_removeJunctionRowsBeforeConcerts() = runTest {
        // Future dates so the always-on purgePast does not touch these rows; isolates the deleteByIds path.
        concertDao.seedConcertArtist("c_del", "a1", "2026-07-01")
        concertDao.seedConcertArtist("c_live", "a2", "2026-07-02")
        api.concertsHasUpdates = true
        api.concertsResponse = ConcertApiResponse(
            timestamp = "2026-06-01T12:00:00Z",
            data = emptyList(),
            deletedIds = listOf("c_del"),
        )

        repo.sync()

        assertTrue(concertDao.concertArtistsStore.none { it.concertId == "c_del" })
        assertTrue(concertDao.concertArtistsStore.any { it.concertId == "c_live" })
        assertTrue(concertDao.deletedConcertArtistsByIds.contains("c_del"))
        val junction = concertDao.deletionOrder.indexOf("junctionByIds")
        val concerts = concertDao.deletionOrder.indexOf("concertsByIds")
        assertTrue(junction in 0 until concerts, "junction rows must be deleted before concerts")
    }

    @Test
    fun sync_purgePast_removesJunctionRowsForPastConcertsBeforeConcerts() = runTest {
        concertDao.seedConcertArtist("c_past", "a1", "2026-05-01") // before fixedDate 2026-06-01
        concertDao.seedConcertArtist("c_future", "a2", "2026-07-01")

        repo.sync()

        assertTrue(concertDao.concertArtistsStore.none { it.concertId == "c_past" })
        assertTrue(concertDao.concertArtistsStore.any { it.concertId == "c_future" })
        val junction = concertDao.deletionOrder.indexOf("junctionPast")
        val concerts = concertDao.deletionOrder.indexOf("concertsPast")
        assertTrue(junction in 0 until concerts, "past junction rows must be deleted before concerts")
    }

    @Test
    fun sync_allUpdated_fullFlow() = runTest {
        api.salasHasUpdates = true
        api.artistsHasUpdates = true
        api.concertsHasUpdates = true
        api.salasResponse = ApiResponse("ts-salas", listOf(SalaConciertoDto("s1", "Razzmatazz", null, "Barcelona", "Barcelona", null, null, null, null, null)))
        api.artistsResponse = ApiResponse("ts-artists", listOf(ArtistDto("a1", "Amaia", "Pop", null, null, null, null)))
        api.concertsResponse = ConcertApiResponse("ts-concerts", listOf(ConcertDto("c1", "s1", listOf("a1"), "2026-07-01", null, null, null, "2026-06-01T00:00:00Z")), emptyList())

        val result = repo.sync()

        assertEquals(1, result.salasCount)
        assertEquals(1, result.artistsCount)
        assertEquals(1, result.concertsCount)
        assertTrue(result.hadNetwork)
        assertEquals("ts-salas", syncMetaDao.store["salas_last_sync"]?.value)
        assertEquals("ts-artists", syncMetaDao.store["artists_last_sync"]?.value)
        assertEquals("ts-concerts", syncMetaDao.store["concerts_last_sync"]?.value)
    }

    @Test
    fun sync_usesSavedTimestampsForHeadChecks() = runTest {
        syncMetaDao.store["salas_last_sync"] = SyncMetaEntity("salas_last_sync", "2026-05-20T00:00:00Z")
        syncMetaDao.store["artists_last_sync"] = SyncMetaEntity("artists_last_sync", "2026-05-21T00:00:00Z")
        syncMetaDao.store["concerts_last_sync"] = SyncMetaEntity("concerts_last_sync", "2026-05-22T00:00:00Z")

        repo.sync()

        assertNotNull(syncMetaDao.store["salas_last_sync"])
        assertNotNull(syncMetaDao.store["artists_last_sync"])
        assertNotNull(syncMetaDao.store["concerts_last_sync"])
    }

    @Test
    fun sync_networkError_returnsNoNetworkResult() = runTest {
        api.shouldThrow = RuntimeException("Connection refused")

        val result = repo.sync()

        assertFalse(result.hadNetwork)
        assertEquals(0, result.salasCount)
        assertEquals(0, result.concertsCount)
        assertEquals(listOf("Connection refused"), result.errors)
        assertEquals("2026-06-01", concertDao.purgePastCalledWith)
        assertTrue(artistDao.deleteOrphansCalled)
    }

    @Test
    fun sync_noUpdates_doesNotSaveSyncMeta() = runTest {
        api.salasHasUpdates = false
        api.artistsHasUpdates = false
        api.concertsHasUpdates = false

        repo.sync()

        assertNull(syncMetaDao.store["salas_last_sync"])
        assertNull(syncMetaDao.store["artists_last_sync"])
        assertNull(syncMetaDao.store["concerts_last_sync"])
    }

    // --- Task 6.1: SQLiteException triggers reset + retry succeeds → dbRecovered = true ---
    @Test
    fun sync_sqliteException_triggersResetAndRetryWithDbRecovered() = runTest {
        api.shouldThrow = SQLiteException("disk I/O error")
        // After reset, the retry will not throw — we clear the error for the second call
        // We achieve this by using a call-count-aware fake
        var callCount = 0
        val countingApi = object : org.rubenazo.conciertosfront.core.data.remote.api.ConcertApiClient {
            override suspend fun checkSalasUpdates(since: String?): Boolean {
                callCount++
                if (callCount == 1) throw SQLiteException("disk I/O error")
                return false
            }
            override suspend fun checkArtistsUpdates(since: String?) = false
            override suspend fun checkConcertsUpdates(since: String?) = false
            override suspend fun getSalas(since: String?) = ApiResponse("", emptyList<SalaConciertoDto>())
            override suspend fun getArtists(since: String?) = ApiResponse("", emptyList<ArtistDto>())
            override suspend fun getConcerts(since: String?) = ConcertApiResponse("", emptyList(), emptyList())
        }
        val provider = FakeDatabaseProvider()
        val recoveryRepo = SyncRepositoryImpl(
            concertApi = countingApi,
            provider = provider,
            dateProvider = fakeDateProvider,
        )

        val result = recoveryRepo.sync()

        assertEquals(1, provider.resetCallCount)
        assertTrue(result.dbRecovered)
        assertTrue(result.hadNetwork)
    }

    // --- Task 6.2: Network error (IOException/generic) does NOT trigger reset ---
    @Test
    fun sync_ioException_doesNotTriggerReset() = runTest {
        api.shouldThrow = RuntimeException("Network failure")
        val provider = FakeDatabaseProvider()
        val repoWithProvider = SyncRepositoryImpl(
            concertApi = api,
            provider = provider,
            dateProvider = fakeDateProvider,
        )

        val result = repoWithProvider.sync()

        assertEquals(0, provider.resetCallCount)
        assertFalse(result.hadNetwork)
        assertFalse(result.dbRecovered)
    }

    // --- Task 6.3: SQLiteException on both calls → reset once, no infinite loop ---
    @Test
    fun sync_sqliteExceptionOnBothCalls_resetsOnceAndReturnsFailure() = runTest {
        val alwaysThrowingApi = object : org.rubenazo.conciertosfront.core.data.remote.api.ConcertApiClient {
            override suspend fun checkSalasUpdates(since: String?) = throw SQLiteException("persistent error")
            override suspend fun checkArtistsUpdates(since: String?) = throw SQLiteException("persistent error")
            override suspend fun checkConcertsUpdates(since: String?) = throw SQLiteException("persistent error")
            override suspend fun getSalas(since: String?) = ApiResponse("", emptyList<SalaConciertoDto>())
            override suspend fun getArtists(since: String?) = ApiResponse("", emptyList<ArtistDto>())
            override suspend fun getConcerts(since: String?) = ConcertApiResponse("", emptyList(), emptyList())
        }
        val provider = FakeDatabaseProvider()
        val doubleFailRepo = SyncRepositoryImpl(
            concertApi = alwaysThrowingApi,
            provider = provider,
            dateProvider = fakeDateProvider,
        )

        val result = doubleFailRepo.sync()

        // Reset must have been called exactly once (no infinite loop)
        assertEquals(1, provider.resetCallCount)
        assertFalse(result.hadNetwork)
        assertFalse(result.dbRecovered)
    }
}
