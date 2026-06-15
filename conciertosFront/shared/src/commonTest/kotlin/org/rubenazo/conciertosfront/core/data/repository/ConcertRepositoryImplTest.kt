package org.rubenazo.conciertosfront.core.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rubenazo.conciertosfront.core.data.local.dao.ConcertWithDetails
import org.rubenazo.conciertosfront.testutil.FakeConcertDao
import org.rubenazo.conciertosfront.testutil.FakeDatabaseProvider

class ConcertRepositoryImplTest {

    private val fakeConcertDao = FakeConcertDao()
    private val repository = ConcertRepositoryImpl(FakeDatabaseProvider(concertDao = fakeConcertDao))

    @Test
    fun getAllWithDetailsFlow_mapsToConcertList() = runTest {
        fakeConcertDao.setAllDetails(listOf(sampleRow()))

        repository.getAllWithDetailsFlow().test {
            val concerts = awaitItem()
            assertEquals(1, concerts.size)
            assertEquals("c1", concerts[0].id)
            assertEquals("Sala Apolo", concerts[0].salaConcierto.name)
            assertEquals("Rosalía", concerts[0].artists[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getByDateRangeFlow_mapsToConcertList() = runTest {
        fakeConcertDao.setDateRangeDetails(listOf(sampleRow()))

        repository.getByDateRangeFlow("2026-06-01", "2026-06-30").test {
            val concerts = awaitItem()
            assertEquals(1, concerts.size)
            assertEquals("c1", concerts[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAllWithDetails_suspend() = runTest {
        fakeConcertDao.setAllDetails(listOf(sampleRow()))
        val concerts = repository.getAllWithDetails()
        assertEquals(1, concerts.size)
        assertEquals("c1", concerts[0].id)
    }

    @Test
    fun getCount_delegatesToDao() = runTest {
        fakeConcertDao.setCount(42)
        assertEquals(42, repository.getCount())
    }

    @Test
    fun getById_existingId() = runTest {
        fakeConcertDao.setAllDetails(listOf(sampleRow()))
        val concert = repository.getById("c1")
        assertEquals("c1", concert?.id)
        assertEquals("Rosalía", concert?.artists?.get(0)?.name)
    }

    @Test
    fun getById_nonExistingId() = runTest {
        fakeConcertDao.setAllDetails(listOf(sampleRow()))
        val concert = repository.getById("nonexistent")
        assertNull(concert)
    }

    // SCN-MAP-004: getInBoundingBoxFlow maps rows through the concert mapper
    @Test
    fun getInBoundingBoxFlow_mapsToConcertList() = runTest {
        fakeConcertDao.setBoundingBoxDetails(listOf(sampleRow()))

        repository.getInBoundingBoxFlow(
            latMin = 40.0, latMax = 42.0,
            lngMin = 1.0, lngMax = 3.0,
            startDate = "2026-06-01", endDate = "2026-06-30"
        ).test {
            val concerts = awaitItem()
            assertEquals(1, concerts.size)
            assertEquals("c1", concerts[0].id)
            assertEquals("Sala Apolo", concerts[0].salaConcierto.name)
            assertEquals("Rosalía", concerts[0].artists[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // SCN-MAP-005: empty bounding box returns empty list
    @Test
    fun getInBoundingBoxFlow_emptyListWhenDaoReturnsNothing() = runTest {
        fakeConcertDao.setBoundingBoxDetails(emptyList())

        repository.getInBoundingBoxFlow(
            latMin = 40.0, latMax = 42.0,
            lngMin = 1.0, lngMax = 3.0,
            startDate = "2026-06-01", endDate = "2026-06-30"
        ).test {
            val concerts = awaitItem()
            assertEquals(0, concerts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun sampleRow() = ConcertWithDetails(
        concertId = "c1", date = "2026-06-15", time = "21:00",
        price = "25€", sourceUrl = null, updatedAt = "2026-05-24T10:00:00Z",
        salaId = "s1", salaName = "Sala Apolo", salaAddress = "C/Nou 113",
        salaCity = "Barcelona", salaProvince = "Barcelona",
        salaLat = 41.3, salaLng = 2.1,
        salaImageUrl = null, salaDescription = null, salaSourceUrl = null,
        artistId = "a1", artistName = "Rosalía", artistGenre = "Flamenco Pop",
        artistImageUrl = null, artistWebsite = null,
        artistDescription = null, artistSourceUrl = null, artistPosition = 0
    )
}
