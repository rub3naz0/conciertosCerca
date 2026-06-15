package org.rubenazo.conciertosfront.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.rubenazo.conciertosfront.core.domain.model.SyncResult
import org.rubenazo.conciertosfront.core.domain.repository.SyncRepository

sealed class SyncUiState {
    data object Loading : SyncUiState()
    data object Syncing : SyncUiState()
    data class Error(val message: String) : SyncUiState()
    data class Success(val result: SyncResult) : SyncUiState()
    data class DbRecovered(val result: SyncResult) : SyncUiState()

    /**
     * Cache-first state: local data already exists, so the app is shown immediately while a
     * refresh runs in the background. [refreshing] drives a subtle in-app indicator; [result]
     * holds the latest background sync outcome (null until the first one returns).
     */
    data class CacheReady(
        val refreshing: Boolean,
        val result: SyncResult?,
    ) : SyncUiState()
}

class SyncViewModel(
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        start()
    }

    private fun start() {
        viewModelScope.launch {
            val cached = try {
                syncRepository.hasLocalData()
            } catch (e: Exception) {
                false
            }
            if (cached) {
                _uiState.value = SyncUiState.CacheReady(refreshing = true, result = null)
                backgroundSync()
            } else {
                blockingSync()
            }
        }
    }

    /** Background refresh while the user already sees cached data. Failures never gate the app. */
    private suspend fun backgroundSync() {
        val previousResult = (_uiState.value as? SyncUiState.CacheReady)?.result
        val result = try {
            syncRepository.sync()
        } catch (e: Exception) {
            null
        }
        _uiState.value = SyncUiState.CacheReady(
            refreshing = false,
            result = if (result != null && result.hadNetwork) result else previousResult,
        )
    }

    /** Blocking sync used on a fresh install (no local data) and as the retry from the error gate. */
    internal fun startSync() {
        viewModelScope.launch { blockingSync() }
    }

    private suspend fun blockingSync() {
        _uiState.value = SyncUiState.Syncing
        try {
            val result = syncRepository.sync()
            if (!result.hadNetwork) {
                _uiState.value = SyncUiState.Error(
                    result.errors.firstOrNull() ?: "Sin conexión a internet"
                )
            } else if (result.dbRecovered) {
                _uiState.value = SyncUiState.DbRecovered(result)
            } else {
                _uiState.value = SyncUiState.Success(result)
            }
        } catch (e: Exception) {
            _uiState.value = SyncUiState.Error(e.message ?: "Error desconocido")
        }
    }
}
