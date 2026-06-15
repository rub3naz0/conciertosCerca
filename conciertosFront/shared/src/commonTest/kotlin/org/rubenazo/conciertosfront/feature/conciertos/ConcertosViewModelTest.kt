package org.rubenazo.conciertosfront.feature.conciertos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.rubenazo.conciertosfront.core.domain.model.Artist
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto
import org.rubenazo.conciertosfront.testutil.FakeConcertRepository
import org.rubenazo.conciertosfront.testutil.FakeDatabaseProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ConcertosViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsConcertsFromRepository() {
        val repo = FakeConcertRepository()
        repo.concerts = listOf(sampleConcert("c1"))

        val viewModel = ConcertosViewModel(repo, FakeDatabaseProvider())

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.concerts.size)
        assertEquals("c1", viewModel.uiState.value.concerts[0].id)
    }

    @Test
    fun setDateFilter_updatesConcerts() {
        val repo = FakeConcertRepository()
        repo.concerts = listOf(sampleConcert("c1"))
        val viewModel = ConcertosViewModel(repo, FakeDatabaseProvider())

        repo.concerts = listOf(sampleConcert("c1"), sampleConcert("c2"))
        viewModel.setDateFilter("2026-06-01", "2026-06-30")

        assertEquals(2, viewModel.uiState.value.concerts.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun setDateFilter_emptyResult() {
        val repo = FakeConcertRepository()
        repo.concerts = listOf(sampleConcert("c1"))
        val viewModel = ConcertosViewModel(repo, FakeDatabaseProvider())

        repo.concerts = emptyList()
        viewModel.setDateFilter("2026-01-01", "2026-01-31")

        assertEquals(0, viewModel.uiState.value.concerts.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun setGenreFilterActive_listsUpcomingIgnoringDate() {
        val repo = FakeConcertRepository()
        repo.concerts = listOf(sampleConcert("c1"))
        // Upcoming (date-ignoring) source returns a wider set.
        repo.upcomingConcerts = listOf(sampleConcert("c1"), sampleConcert("c2"))
        val viewModel = ConcertosViewModel(repo, FakeDatabaseProvider())

        viewModel.setGenreFilterActive(true)

        assertEquals(2, viewModel.uiState.value.concerts.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun setGenreFilterActive_false_returnsToDateFilteredResults() {
        val repo = FakeConcertRepository()
        repo.concerts = listOf(sampleConcert("c1"))
        repo.upcomingConcerts = listOf(sampleConcert("c1"), sampleConcert("c2"))
        val viewModel = ConcertosViewModel(repo, FakeDatabaseProvider())

        viewModel.setGenreFilterActive(true)
        viewModel.setGenreFilterActive(false)

        assertEquals(1, viewModel.uiState.value.concerts.size)
        assertEquals("c1", viewModel.uiState.value.concerts[0].id)
    }

    // Task 6.4: reset signal triggers Flow restart
    @Test
    fun resetSignal_restartsFlowCollection() = runTest(testDispatcher) {
        val repo = FakeConcertRepository()
        repo.concerts = listOf(sampleConcert("c1"))
        val provider = FakeDatabaseProvider()
        val viewModel = ConcertosViewModel(repo, provider)

        assertEquals(1, viewModel.uiState.value.concerts.size)

        // Update the repo data, then emit reset signal — ViewModel should restart
        repo.concerts = listOf(sampleConcert("c1"), sampleConcert("c2"))
        provider.emitReset()

        assertEquals(2, viewModel.uiState.value.concerts.size)
    }

    private fun sampleConcert(id: String) = Concert(
        id = id,
        salaConcierto = SalaConcierto("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null),
        artists = listOf(Artist("a1", "Rosalía", "Pop", null, null, null, null)),
        date = "2026-06-15", time = "21:00", price = "25€",
        sourceUrl = null, updatedAt = "2026-05-24T10:00:00Z"
    )
}
