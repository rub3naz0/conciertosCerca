package org.rubenazo.conciertosfront

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import org.rubenazo.conciertosfront.core.domain.model.SyncResult
import org.rubenazo.conciertosfront.feature.sync.SyncScreen
import org.rubenazo.conciertosfront.feature.sync.SyncUiState
import org.rubenazo.conciertosfront.feature.sync.SyncViewModel
import org.rubenazo.conciertosfront.ui.navigation.MainScreen
import org.rubenazo.conciertosfront.ui.theme.VoltageTheme

/**
 * Shared root composable for both Android and iOS.
 *
 * Acts as a one-way gate: while there is no usable data the user sees [SyncScreen]; once data is
 * ready (or the user chooses to continue offline) the app switches to [MainScreen] and never goes
 * back to the gate. The [SyncViewModel] is created here, not inside [SyncScreen], so its background
 * refresh keeps running across the gate → Main transition.
 */
@Composable
fun App() {
    KoinContext {
        VoltageTheme {
            // Hoisted to App so the background sync's viewModelScope survives the gate → Main
            // transition (the ViewModel is owned by the root store, not the SyncScreen).
            val syncViewModel = koinViewModel<SyncViewModel>()
            val syncState by syncViewModel.uiState.collectAsState()

            // Once we're in Main we never return to the gate, regardless of later sync states.
            var enteredMain by remember { mutableStateOf(false) }
            var lastSyncResult by remember { mutableStateOf<SyncResult?>(null) }

            val cacheReady = syncState as? SyncUiState.CacheReady

            if (enteredMain || cacheReady != null) {
                MainScreen(
                    syncResult = cacheReady?.result ?: lastSyncResult,
                    isSyncing = cacheReady?.refreshing ?: false,
                )
            } else {
                SyncScreen(
                    onSyncComplete = { result ->
                        lastSyncResult = result
                        enteredMain = true
                    },
                    onContinueOffline = {
                        lastSyncResult = SyncResult(
                            salasCount = 0,
                            artistsCount = 0,
                            concertsCount = 0,
                            deletedConcertsCount = 0,
                            hadNetwork = false,
                            errors = emptyList()
                        )
                        enteredMain = true
                    }
                )
            }
        }
    }
}
