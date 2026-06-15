package org.rubenazo.conciertosfront.core.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.rubenazo.conciertosfront.core.data.local.dao.ArtistWithConcertRow
import org.rubenazo.conciertosfront.core.data.local.entity.ArtistEntity
import org.rubenazo.conciertosfront.testutil.FakeArtistDao
import org.rubenazo.conciertosfront.testutil.FakeDatabaseProvider

class ArtistRepositoryImplTest {

    private val fakeArtistDao = FakeArtistDao()
    private val repository = ArtistRepositoryImpl(FakeDatabaseProvider(artistDao = fakeArtistDao))

    @Test
    fun getAllFlow_mapsEntitiesToDomain() = runTest {
        fakeArtistDao.setEntities(listOf(
            ArtistEntity("a1", "Rosalía", "Flamenco Pop", "https://img.com/rosalia.jpg", "https://rosalia.com", null, null),
            ArtistEntity("a2", "Bad Gyal", "Reggaeton", null, null, null, null),
        ))

        repository.getAllFlow().test {
            val artists = awaitItem()
            assertEquals(2, artists.size)
            assertEquals("Rosalía", artists[0].name)
            assertEquals("Flamenco Pop", artists[0].genre)
            assertEquals("Bad Gyal", artists[1].name)
            assertEquals("Reggaeton", artists[1].genre)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAllWithUpcomingConcertsFlow_groupsByArtist() = runTest {
        fakeArtistDao.setWithConcerts(listOf(
            ArtistWithConcertRow("a1", "Rosalía", "Pop", null, null, null, null, "c1", "2026-06-15", "21:00", "Sala Apolo", "Barcelona"),
            ArtistWithConcertRow("a1", "Rosalía", "Pop", null, null, null, null, "c2", "2026-07-01", "22:00", "Razzmatazz", "Barcelona"),
            ArtistWithConcertRow("a2", "Bad Gyal", "Reggaeton", null, null, null, null, "c3", "2026-06-20", "23:00", "Sala Bikini", "Barcelona"),
        ))

        repository.getAllWithUpcomingConcertsFlow("2026-06-01").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("Rosalía", result[0].artist.name)
            assertEquals(2, result[0].upcomingConcerts.size)
            assertEquals("Sala Apolo", result[0].upcomingConcerts[0].salaName)
            assertEquals("Bad Gyal", result[1].artist.name)
            assertEquals(1, result[1].upcomingConcerts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAllWithUpcomingConcertsFlow_filtersNullConcerts() = runTest {
        fakeArtistDao.setWithConcerts(listOf(
            ArtistWithConcertRow("a1", "Rosalía", "Pop", null, null, null, null, null, null, null, null, null),
        ))

        repository.getAllWithUpcomingConcertsFlow("2026-06-01").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Rosalía", result[0].artist.name)
            assertEquals(0, result[0].upcomingConcerts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getByDateRangeWithConcertsFlow_groupsByArtist() = runTest {
        fakeArtistDao.setDateRangeWithConcerts(listOf(
            ArtistWithConcertRow("a1", "Rosalía", "Pop", null, null, null, null, "c1", "2026-06-15", "21:00", "Sala Apolo", "Barcelona"),
        ))

        repository.getByDateRangeWithConcertsFlow("2026-06-01", "2026-06-30").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Rosalía", result[0].artist.name)
            assertEquals(1, result[0].upcomingConcerts.size)
            assertEquals("c1", result[0].upcomingConcerts[0].id)
            assertEquals("Sala Apolo", result[0].upcomingConcerts[0].salaName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun searchByNameWithConcertsFlow_escapesLikeWildcards() = runTest {
        repository.searchByNameWithConcertsFlow("100%", "2026-06-01")
        assertEquals("""100\%""", fakeArtistDao.lastSearchQuery)

        repository.searchByNameWithConcertsFlow("a_b", "2026-06-01")
        assertEquals("""a\_b""", fakeArtistDao.lastSearchQuery)

        repository.searchByNameWithConcertsFlow("""AC\DC""", "2026-06-01")
        assertEquals("""AC\\DC""", fakeArtistDao.lastSearchQuery)
    }

    @Test
    fun searchByNameWithConcertsFlow_passesPlainQueryUnchanged() = runTest {
        repository.searchByNameWithConcertsFlow("Rosalía", "2026-06-01")
        assertEquals("Rosalía", fakeArtistDao.lastSearchQuery)
    }

    @Test
    fun getAll_suspend_mapsEntitiesToDomain() = runTest {
        fakeArtistDao.setEntities(listOf(
            ArtistEntity("a1", "Rosalía", "Pop", null, null, null, null),
        ))
        val artists = repository.getAll()
        assertEquals(1, artists.size)
        assertEquals("Rosalía", artists[0].name)
    }

    @Test
    fun getCount_delegatesToDao() = runTest {
        fakeArtistDao.setEntities(listOf(
            ArtistEntity("a1", "Rosalía", "Pop", null, null, null, null),
            ArtistEntity("a2", "Bad Gyal", "Reggaeton", null, null, null, null),
        ))
        assertEquals(2, repository.getCount())
    }
}
