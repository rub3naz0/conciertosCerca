package org.rubenazo.conciertosfront.feature.artistas

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
import org.rubenazo.conciertosfront.core.domain.model.ArtistWithConcerts
import org.rubenazo.conciertosfront.core.domain.model.UpcomingConcert
import org.rubenazo.conciertosfront.testutil.FakeArtistRepository
import org.rubenazo.conciertosfront.testutil.FakeDatabaseProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistasViewModelTest {

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
    fun initialState_loadsArtistsFromRepository() {
        val repo = FakeArtistRepository()
        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(
                artist = Artist("a1", "Rosalía", "Pop", null, null, null, null),
                upcomingConcerts = listOf(
                    UpcomingConcert("c1", "2026-06-15", "21:00", "Sala Apolo", "Barcelona")
                )
            )
        )

        val viewModel = ArtistasViewModel(repo, FakeDatabaseProvider())

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.artists.size)
        assertEquals("Rosalía", viewModel.uiState.value.artists[0].artist.name)
        assertEquals(1, viewModel.uiState.value.artists[0].upcomingConcerts.size)
    }

    @Test
    fun setDateFilter_updatesArtists() {
        val repo = FakeArtistRepository()
        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList())
        )
        val viewModel = ArtistasViewModel(repo, FakeDatabaseProvider())

        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList()),
            ArtistWithConcerts(Artist("a2", "Bad Gyal", "Reggaeton", null, null, null, null), emptyList()),
        )
        viewModel.setDateFilter("2026-06-01", "2026-06-30")

        assertEquals(2, viewModel.uiState.value.artists.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun setDateFilter_emptyResult() {
        val repo = FakeArtistRepository()
        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList())
        )
        val viewModel = ArtistasViewModel(repo, FakeDatabaseProvider())

        repo.artistsWithConcerts = emptyList()
        viewModel.setDateFilter("2026-01-01", "2026-01-31")

        assertEquals(0, viewModel.uiState.value.artists.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun setSearchQuery_loadsSearchResultsIgnoringDate() {
        val repo = FakeArtistRepository()
        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList())
        )
        repo.searchResults = listOf(
            ArtistWithConcerts(Artist("a2", "Bad Gyal", "Reggaeton", null, null, null, null), emptyList())
        )
        val viewModel = ArtistasViewModel(repo, FakeDatabaseProvider())

        viewModel.setSearchQuery("bad")

        assertEquals(1, viewModel.uiState.value.artists.size)
        assertEquals("Bad Gyal", viewModel.uiState.value.artists[0].artist.name)
        assertEquals("bad", repo.lastSearchQuery)
    }

    @Test
    fun setSearchQuery_blank_fallsBackToDateFilteredResults() {
        val repo = FakeArtistRepository()
        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList())
        )
        repo.searchResults = listOf(
            ArtistWithConcerts(Artist("a2", "Bad Gyal", "Reggaeton", null, null, null, null), emptyList())
        )
        val viewModel = ArtistasViewModel(repo, FakeDatabaseProvider())

        viewModel.setSearchQuery("bad")
        viewModel.setSearchQuery("   ")

        assertEquals(1, viewModel.uiState.value.artists.size)
        assertEquals("Rosalía", viewModel.uiState.value.artists[0].artist.name)
    }

    @Test
    fun setDateFilter_whileSearchActive_keepsSearchResults() {
        val repo = FakeArtistRepository()
        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList())
        )
        repo.searchResults = listOf(
            ArtistWithConcerts(Artist("a2", "Bad Gyal", "Reggaeton", null, null, null, null), emptyList())
        )
        val viewModel = ArtistasViewModel(repo, FakeDatabaseProvider())

        viewModel.setSearchQuery("bad")
        viewModel.setDateFilter("2026-01-01", "2026-01-31")

        // Search ignores the date filter, so results stay on the name match.
        assertEquals(1, viewModel.uiState.value.artists.size)
        assertEquals("Bad Gyal", viewModel.uiState.value.artists[0].artist.name)
    }

    @Test
    fun setGenreFilterActive_browsesAllUpcomingIgnoringDate() {
        val repo = FakeArtistRepository()
        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList())
        )
        // All-upcoming (date-ignoring) source returns a different, wider set.
        repo.allUpcomingResults = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList()),
            ArtistWithConcerts(Artist("a2", "Bad Gyal", "Reggaeton", null, null, null, null), emptyList()),
        )
        val viewModel = ArtistasViewModel(repo, FakeDatabaseProvider())

        viewModel.setGenreFilterActive(true)

        assertEquals(2, viewModel.uiState.value.artists.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun setGenreFilterActive_false_returnsToDateFilteredResults() {
        val repo = FakeArtistRepository()
        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList())
        )
        repo.allUpcomingResults = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList()),
            ArtistWithConcerts(Artist("a2", "Bad Gyal", "Reggaeton", null, null, null, null), emptyList()),
        )
        val viewModel = ArtistasViewModel(repo, FakeDatabaseProvider())

        viewModel.setGenreFilterActive(true)
        viewModel.setGenreFilterActive(false)

        assertEquals(1, viewModel.uiState.value.artists.size)
        assertEquals("Rosalía", viewModel.uiState.value.artists[0].artist.name)
    }

    @Test
    fun searchQuery_takesPrecedenceOverGenreFilter() {
        val repo = FakeArtistRepository()
        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList())
        )
        repo.allUpcomingResults = listOf(
            ArtistWithConcerts(Artist("a2", "Bad Gyal", "Reggaeton", null, null, null, null), emptyList())
        )
        repo.searchResults = listOf(
            ArtistWithConcerts(Artist("a3", "C. Tangana", "Urban", null, null, null, null), emptyList())
        )
        val viewModel = ArtistasViewModel(repo, FakeDatabaseProvider())

        viewModel.setGenreFilterActive(true)
        viewModel.setSearchQuery("tangana")

        assertEquals(1, viewModel.uiState.value.artists.size)
        assertEquals("C. Tangana", viewModel.uiState.value.artists[0].artist.name)
    }

    // Task 6.4 analogue: reset signal restarts Flow collection
    @Test
    fun resetSignal_restartsFlowCollection() = runTest(testDispatcher) {
        val repo = FakeArtistRepository()
        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList())
        )
        val provider = FakeDatabaseProvider()
        val viewModel = ArtistasViewModel(repo, provider)

        assertEquals(1, viewModel.uiState.value.artists.size)

        repo.artistsWithConcerts = listOf(
            ArtistWithConcerts(Artist("a1", "Rosalía", "Pop", null, null, null, null), emptyList()),
            ArtistWithConcerts(Artist("a2", "Bad Gyal", "Reggaeton", null, null, null, null), emptyList()),
        )
        provider.emitReset()

        assertEquals(2, viewModel.uiState.value.artists.size)
    }
}
