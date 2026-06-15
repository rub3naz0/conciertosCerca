package org.rubenazo.conciertosfront.feature.salas

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
import org.rubenazo.conciertosfront.core.domain.model.SalaWithConcerts
import org.rubenazo.conciertosfront.core.domain.repository.SalaConciertoRepository
import org.rubenazo.conciertosfront.core.util.todayIsoDate

data class SalasUiState(
    val salas: List<SalaWithConcerts> = emptyList(),
    val isLoading: Boolean = true,
)

class SalasViewModel(
    private val salaRepository: SalaConciertoRepository,
    private val dbProvider: DatabaseProviderPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalasUiState())
    val uiState: StateFlow<SalasUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null
    private var currentStartDate: String = todayIsoDate()
    private var currentEndDate: String = todayIsoDate()

    init {
        setDateFilter(currentStartDate, currentEndDate)
        observeResetSignal()
    }

    fun setDateFilter(startDate: String, endDate: String) {
        currentStartDate = startDate
        currentEndDate = endDate
        collectJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true)
        collectJob = viewModelScope.launch {
            salaRepository.getByDateRangeWithConcertsFlow(startDate, endDate)
                .catch { e -> if (e !is SQLiteException) throw e }
                .collect { salas ->
                    _uiState.value = SalasUiState(salas = salas, isLoading = false)
                }
        }
    }

    private fun observeResetSignal() {
        viewModelScope.launch {
            dbProvider.resetSignal.collect {
                setDateFilter(currentStartDate, currentEndDate)
            }
        }
    }
}
