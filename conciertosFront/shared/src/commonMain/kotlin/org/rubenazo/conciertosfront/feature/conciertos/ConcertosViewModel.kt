package org.rubenazo.conciertosfront.feature.conciertos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.SQLiteException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.rubenazo.conciertosfront.core.data.local.DatabaseProviderPort
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.domain.repository.ConcertRepository
import org.rubenazo.conciertosfront.core.util.todayIsoDate

data class ConcertosUiState(
    val concerts: List<Concert> = emptyList(),
    val isLoading: Boolean = true
)

class ConcertosViewModel(
    private val concertRepository: ConcertRepository,
    private val dbProvider: DatabaseProviderPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConcertosUiState())
    val uiState: StateFlow<ConcertosUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null
    private var currentStartDate: String = todayIsoDate()
    private var currentEndDate: String = todayIsoDate()
    private var genreFilterActive: Boolean = false

    init {
        restartCollection()
        observeResetSignal()
    }

    fun setDateFilter(startDate: String, endDate: String) {
        currentStartDate = startDate
        currentEndDate = endDate
        restartCollection()
    }

    fun setGenreFilterActive(active: Boolean) {
        if (genreFilterActive == active) return
        genreFilterActive = active
        restartCollection()
    }

    // An active genre filter ignores the date range and lists all upcoming concerts
    // (genre matching itself is applied in the UI); otherwise we use the selected
    // date-range filter. Both sources are already ordered by date ascending.
    private fun restartCollection() {
        collectJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true)
        val source = if (genreFilterActive) {
            concertRepository.getUpcomingFlow(todayIsoDate())
        } else {
            concertRepository.getByDateRangeFlow(currentStartDate, currentEndDate)
        }
        collectJob = viewModelScope.launch {
            source
                .catch { e -> if (e !is SQLiteException) throw e }
                .collect { concerts ->
                    _uiState.value = ConcertosUiState(concerts = concerts, isLoading = false)
                }
        }
    }

    private fun observeResetSignal() {
        viewModelScope.launch {
            dbProvider.resetSignal.collect {
                restartCollection()
            }
        }
    }
}
