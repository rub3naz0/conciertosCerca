package org.rubenazo.conciertosfront.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.rubenazo.conciertosfront.core.domain.model.SyncResult
import org.rubenazo.conciertosfront.ui.components.ErrorCard
import org.rubenazo.conciertosfront.ui.components.SplashScreen

@Composable
fun SyncScreen(
    onSyncComplete: (SyncResult) -> Unit,
    onContinueOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<SyncViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SyncUiState.Success -> onSyncComplete(state.result)
            is SyncUiState.DbRecovered -> {
                snackbarHostState.showSnackbar("Base de datos local regenerada. Resincronizando...")
                onSyncComplete(state.result)
            }
            else -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        SplashScreen {
            when (val state = uiState) {
                is SyncUiState.Loading, is SyncUiState.Syncing, is SyncUiState.DbRecovered ->
                    SyncStatus(label = "Sincronizando datos...")

                is SyncUiState.Success, is SyncUiState.CacheReady ->
                    SyncStatus(label = "Cargando...")

                is SyncUiState.Error ->
                    ErrorCard(
                        message = state.message,
                        onContinueOffline = onContinueOffline,
                        onRetry = { viewModel.startSync() }
                    )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }
}

@Composable
private fun SyncStatus(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
