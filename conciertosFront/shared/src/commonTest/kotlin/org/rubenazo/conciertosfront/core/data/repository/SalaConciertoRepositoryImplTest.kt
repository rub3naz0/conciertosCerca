package org.rubenazo.conciertosfront.core.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.rubenazo.conciertosfront.core.data.local.dao.SalaWithConcertRow
import org.rubenazo.conciertosfront.core.data.local.entity.SalaConciertoEntity
import org.rubenazo.conciertosfront.testutil.FakeDatabaseProvider
import org.rubenazo.conciertosfront.testutil.FakeSalaConciertoDao

class SalaConciertoRepositoryImplTest {

    private val fakeSalaConciertoDao = FakeSalaConciertoDao()
    private val repository = SalaConciertoRepositoryImpl(FakeDatabaseProvider(salaConciertoDao = fakeSalaConciertoDao))

    @Test
    fun getAllFlow_mapsEntitiesToDomain() = runTest {
        fakeSalaConciertoDao.setEntities(listOf(
            SalaConciertoEntity("s1", "Sala Apolo", "C/Nou 113", "Barcelona", "Barcelona", 41.3, 2.1, null, null, null),
            SalaConciertoEntity("s2", "Razzmatazz", null, "Barcelona", "Barcelona", null, null, null, null, null),
        ))

        repository.getAllFlow().test {
            val salas = awaitItem()
            assertEquals(2, salas.size)
            assertEquals("Sala Apolo", salas[0].name)
            assertEquals("C/Nou 113", salas[0].address)
            assertEquals("Razzmatazz", salas[1].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAll_suspend_mapsEntitiesToDomain() = runTest {
        fakeSalaConciertoDao.setEntities(listOf(
            SalaConciertoEntity("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null),
        ))
        val salas = repository.getAll()
        assertEquals(1, salas.size)
        assertEquals("Sala Apolo", salas[0].name)
    }

    @Test
    fun getCount_delegatesToDao() = runTest {
        assertEquals(0, repository.getCount())
    }

    @Test
    fun getByDateRangeWithConcertsFlow_groupsBySala() = runTest {
        fakeSalaConciertoDao.setWithConcerts(listOf(
            SalaWithConcertRow("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null, "c1", "2026-06-15", "21:00", "Rosalía"),
            SalaWithConcertRow("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null, "c2", "2026-07-01", "22:00", "Bad Gyal"),
            SalaWithConcertRow("s2", "Razzmatazz", null, "Barcelona", "Barcelona", null, null, null, null, null, "c3", "2026-06-20", "23:00", "C. Tangana"),
        ))

        repository.getByDateRangeWithConcertsFlow("2026-06-01", "2026-07-31").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("Sala Apolo", result[0].sala.name)
            assertEquals(2, result[0].upcomingConcerts.size)
            assertEquals("Razzmatazz", result[1].sala.name)
            assertEquals(1, result[1].upcomingConcerts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getByDateRangeWithConcertsFlow_filtersNullConcerts() = runTest {
        fakeSalaConciertoDao.setWithConcerts(listOf(
            SalaWithConcertRow("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null, null, null, null, null),
        ))

        repository.getByDateRangeWithConcertsFlow("2026-06-01", "2026-06-30").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(0, result[0].upcomingConcerts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getByDateRangeWithConcertsFlow_groupsConcertsWithMultipleArtists() = runTest {
        fakeSalaConciertoDao.setWithConcerts(listOf(
            SalaWithConcertRow("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null, "c1", "2026-06-15", "21:00", "Rosalía"),
            SalaWithConcertRow("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null, "c1", "2026-06-15", "21:00", "Bad Gyal"),
        ))

        repository.getByDateRangeWithConcertsFlow("2026-06-01", "2026-06-30").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(1, result[0].upcomingConcerts.size)
            assertEquals(2, result[0].upcomingConcerts[0].artistNames.size)
            assertEquals("Rosalía", result[0].upcomingConcerts[0].artistNames[0])
            assertEquals("Bad Gyal", result[0].upcomingConcerts[0].artistNames[1])
            cancelAndIgnoreRemainingEvents()
        }
    }
}
