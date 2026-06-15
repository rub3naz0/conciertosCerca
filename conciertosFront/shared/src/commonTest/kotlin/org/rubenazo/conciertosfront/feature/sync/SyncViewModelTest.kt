package org.rubenazo.conciertosfront.feature.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.rubenazo.conciertosfront.core.domain.model.SyncResult
import org.rubenazo.conciertosfront.testutil.FakeSyncRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

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
    fun initialSync_success() {
        val repo = FakeSyncRepository()
        repo.syncResult = SyncResult(10, 20, 30, 5, true, emptyList())

        val viewModel = SyncViewModel(repo)

        val state = viewModel.uiState.value
        assertIs<SyncUiState.Success>(state)
        assertEquals(10, state.result.salasCount)
        assertEquals(20, state.result.artistsCount)
        assertEquals(30, state.result.concertsCount)
        assertEquals(5, state.result.deletedConcertsCount)
    }

    @Test
    fun initialSync_noNetwork_showsErrorWithMessage() {
        val repo = FakeSyncRepository()
        repo.syncResult = SyncResult(0, 0, 0, 0, false, listOf("Connection refused"))

        val viewModel = SyncViewModel(repo)

        val state = viewModel.uiState.value
        assertIs<SyncUiState.Error>(state)
        assertEquals("Connection refused", state.message)
    }

    @Test
    fun initialSync_noNetwork_defaultMessage() {
        val repo = FakeSyncRepository()
        repo.syncResult = SyncResult(0, 0, 0, 0, false, emptyList())

        val viewModel = SyncViewModel(repo)

        val state = viewModel.uiState.value
        assertIs<SyncUiState.Error>(state)
        assertEquals("Sin conexión a internet", state.message)
    }

    @Test
    fun initialSync_exception_showsError() {
        val repo = FakeSyncRepository()
        repo.shouldThrow = RuntimeException("Network timeout")

        val viewModel = SyncViewModel(repo)

        val state = viewModel.uiState.value
        assertIs<SyncUiState.Error>(state)
        assertEquals("Network timeout", state.message)
    }

    @Test
    fun startSync_retriesSync() {
        val repo = FakeSyncRepository()
        repo.syncResult = SyncResult(10, 20, 30, 0, true, emptyList())

        val viewModel = SyncViewModel(repo)
        assertEquals(1, repo.syncCalledCount)

        viewModel.startSync()
        assertEquals(2, repo.syncCalledCount)
    }

    // Task 6.4 for SyncViewModel: dbRecovered = true emits DbRecovered state
    @Test
    fun initialSync_dbRecovered_emitsDbRecoveredState() {
        val repo = FakeSyncRepository()
        repo.syncResult = SyncResult(5, 10, 15, 0, true, emptyList(), dbRecovered = true)

        val viewModel = SyncViewModel(repo)

        val state = viewModel.uiState.value
        assertIs<SyncUiState.DbRecovered>(state)
        assertEquals(5, state.result.salasCount)
        assertEquals(true, state.result.dbRecovered)
    }

    // Cache-first: with local data, enter Main immediately and refresh in background (no gate).
    @Test
    fun initialSync_withLocalData_entersCacheReadyAndRefreshes() {
        val repo = FakeSyncRepository()
        repo.localDataPresent = true
        repo.syncResult = SyncResult(1, 2, 3, 0, true, emptyList())

        val viewModel = SyncViewModel(repo)

        val state = viewModel.uiState.value
        assertIs<SyncUiState.CacheReady>(state)
        assertEquals(false, state.refreshing)
        assertEquals(3, state.result?.concertsCount)
        assertEquals(1, repo.syncCalledCount)
    }

    // Cache-first: a failed background sync must NOT gate the app — it stays in CacheReady.
    @Test
    fun initialSync_withLocalData_backgroundSyncFails_staysCacheReady() {
        val repo = FakeSyncRepository()
        repo.localDataPresent = true
        repo.shouldThrow = RuntimeException("Network timeout")

        val viewModel = SyncViewModel(repo)

        val state = viewModel.uiState.value
        assertIs<SyncUiState.CacheReady>(state)
        assertEquals(false, state.refreshing)
    }

    // Cache-first: a no-network background result keeps cached data without surfacing an error gate.
    @Test
    fun initialSync_withLocalData_backgroundNoNetwork_staysCacheReady() {
        val repo = FakeSyncRepository()
        repo.localDataPresent = true
        repo.syncResult = SyncResult(0, 0, 0, 0, false, listOf("Connection refused"))

        val viewModel = SyncViewModel(repo)

        val state = viewModel.uiState.value
        assertIs<SyncUiState.CacheReady>(state)
        assertEquals(false, state.refreshing)
    }
}
