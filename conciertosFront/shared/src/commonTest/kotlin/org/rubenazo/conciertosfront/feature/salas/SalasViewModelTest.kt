package org.rubenazo.conciertosfront.feature.salas

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
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto
import org.rubenazo.conciertosfront.core.domain.model.SalaWithConcerts
import org.rubenazo.conciertosfront.core.domain.model.VenueConcert
import org.rubenazo.conciertosfront.testutil.FakeDatabaseProvider
import org.rubenazo.conciertosfront.testutil.FakeSalaConciertoRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SalasViewModelTest {

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
    fun initialState_loadsSalasFromRepository() {
        val repo = FakeSalaConciertoRepository()
        repo.salasWithConcerts = listOf(
            SalaWithConcerts(
                sala = SalaConcierto("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null),
                upcomingConcerts = listOf(
                    VenueConcert("c1", "2026-06-15", "21:00", listOf("Rosalía"))
                )
            )
        )

        val viewModel = SalasViewModel(repo, FakeDatabaseProvider())

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.salas.size)
        assertEquals("Sala Apolo", viewModel.uiState.value.salas[0].sala.name)
        assertEquals(1, viewModel.uiState.value.salas[0].upcomingConcerts.size)
    }

    @Test
    fun setDateFilter_updatesSalas() {
        val repo = FakeSalaConciertoRepository()
        repo.salasWithConcerts = listOf(
            SalaWithConcerts(SalaConcierto("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null), emptyList())
        )
        val viewModel = SalasViewModel(repo, FakeDatabaseProvider())

        repo.salasWithConcerts = listOf(
            SalaWithConcerts(SalaConcierto("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null), emptyList()),
            SalaWithConcerts(SalaConcierto("s2", "Razzmatazz", null, "Barcelona", "Barcelona", null, null, null, null, null), emptyList()),
        )
        viewModel.setDateFilter("2026-06-01", "2026-06-30")

        assertEquals(2, viewModel.uiState.value.salas.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun setDateFilter_emptyResult() {
        val repo = FakeSalaConciertoRepository()
        repo.salasWithConcerts = listOf(
            SalaWithConcerts(SalaConcierto("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null), emptyList())
        )
        val viewModel = SalasViewModel(repo, FakeDatabaseProvider())

        repo.salasWithConcerts = emptyList()
        viewModel.setDateFilter("2026-01-01", "2026-01-31")

        assertEquals(0, viewModel.uiState.value.salas.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // Task 6.4 analogue: reset signal restarts Flow collection
    @Test
    fun resetSignal_restartsFlowCollection() = runTest(testDispatcher) {
        val repo = FakeSalaConciertoRepository()
        repo.salasWithConcerts = listOf(
            SalaWithConcerts(SalaConcierto("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null), emptyList())
        )
        val provider = FakeDatabaseProvider()
        val viewModel = SalasViewModel(repo, provider)

        assertEquals(1, viewModel.uiState.value.salas.size)

        repo.salasWithConcerts = listOf(
            SalaWithConcerts(SalaConcierto("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null), emptyList()),
            SalaWithConcerts(SalaConcierto("s2", "Razzmatazz", null, "Barcelona", "Barcelona", null, null, null, null, null), emptyList()),
        )
        provider.emitReset()

        assertEquals(2, viewModel.uiState.value.salas.size)
    }
}
