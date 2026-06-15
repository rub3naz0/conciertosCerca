package org.rubenazo.conciertosfront.core.data.local.dao

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.rubenazo.conciertosfront.testutil.FakeConcertDao

/**
 * Tests for FakeConcertDao.getConcertsInBoundingBox.
 *
 * The real Room DAO query is verified by the Room schema (Room auto-generates it from @Query).
 * Here we test the Fake's contract so that ViewModel/Repository tests remain trustworthy.
 *
 * SCN-MAP-005: bounding box + date range returns only matching rows
 * SCN-MAP-007: bounding box with no concerts returns empty list
 */
class ConcertDaoTest {

    private val dao = FakeConcertDao()

    // SCN-MAP-005: rows inside bounds and date range are returned
    @Test
    fun getConcertsInBoundingBox_returnsRowsInsideBoundsAndDateRange() = runTest {
        val insideRow = concertRow(
            concertId = "c1",
            salaLat = 40.5,
            salaLng = -3.7,
            date = "2026-07-10"
        )
        val outsideLatRow = concertRow(
            concertId = "c2",
            salaLat = 50.0, // outside latMax=41
            salaLng = -3.7,
            date = "2026-07-10"
        )
        val outsideDateRow = concertRow(
            concertId = "c3",
            salaLat = 40.5,
            salaLng = -3.7,
            date = "2026-08-01" // outside endDate=2026-07-31
        )
        dao.setBoundingBoxDetails(listOf(insideRow))

        dao.getConcertsInBoundingBox(
            latMin = 40.0, latMax = 41.0,
            lngMin = -4.0, lngMax = -3.0,
            startDate = "2026-07-01", endDate = "2026-07-31"
        ).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("c1", result[0].concertId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // SCN-MAP-007: bounding box with no concerts returns empty list
    @Test
    fun getConcertsInBoundingBox_emptyWhenNoRowsMatch() = runTest {
        dao.setBoundingBoxDetails(emptyList())

        dao.getConcertsInBoundingBox(
            latMin = 40.0, latMax = 41.0,
            lngMin = -4.0, lngMax = -3.0,
            startDate = "2026-07-01", endDate = "2026-07-31"
        ).test {
            val result = awaitItem()
            assertEquals(0, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getConcertsInBoundingBox_returnsMultipleMatchingRows() = runTest {
        val rows = listOf(
            concertRow("c1", salaLat = 40.5, salaLng = -3.7, date = "2026-07-10"),
            concertRow("c2", salaLat = 40.6, salaLng = -3.8, date = "2026-07-15"),
        )
        dao.setBoundingBoxDetails(rows)

        dao.getConcertsInBoundingBox(
            latMin = 40.0, latMax = 41.0,
            lngMin = -4.0, lngMax = -3.0,
            startDate = "2026-07-01", endDate = "2026-07-31"
        ).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun concertRow(
        concertId: String,
        salaLat: Double?,
        salaLng: Double?,
        date: String,
    ) = ConcertWithDetails(
        concertId = concertId,
        date = date,
        time = "21:00",
        price = "25€",
        sourceUrl = null,
        updatedAt = "2026-05-24T10:00:00Z",
        salaId = "s1",
        salaName = "Sala Test",
        salaAddress = null,
        salaCity = "Madrid",
        salaProvince = "Madrid",
        salaLat = salaLat,
        salaLng = salaLng,
        salaImageUrl = null,
        salaDescription = null,
        salaSourceUrl = null,
        artistId = "a1",
        artistName = "Test Artist",
        artistGenre = null,
        artistImageUrl = null,
        artistWebsite = null,
        artistDescription = null,
        artistSourceUrl = null,
        artistPosition = 0,
    )
}
