package org.rubenazo.conciertosfront.feature.artistas

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
import org.rubenazo.conciertosfront.core.domain.model.ArtistWithConcerts
import org.rubenazo.conciertosfront.core.domain.repository.ArtistRepository
import org.rubenazo.conciertosfront.core.util.todayIsoDate

data class ArtistasUiState(
    val artists: List<ArtistWithConcerts> = emptyList(),
    val isLoading: Boolean = true,
)

class ArtistasViewModel(
    private val artistRepository: ArtistRepository,
    private val dbProvider: DatabaseProviderPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistasUiState())
    val uiState: StateFlow<ArtistasUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null
    private var currentStartDate: String = todayIsoDate()
    private var currentEndDate: String = todayIsoDate()
    private var currentQuery: String = ""
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

    fun setSearchQuery(query: String) {
        currentQuery = query.trim()
        restartCollection()
    }

    fun setGenreFilterActive(active: Boolean) {
        if (genreFilterActive == active) return
        genreFilterActive = active
        restartCollection()
    }

    // Source precedence: an active name search ignores everything else and matches
    // on name only; an active genre filter ignores the date range and browses all
    // upcoming artists (genre matching itself is applied in the UI); otherwise we
    // fall back to the selected date-range filter.
    private fun restartCollection() {
        collectJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true)
        val source = when {
            currentQuery.isNotEmpty() ->
                artistRepository.searchByNameWithConcertsFlow(currentQuery, todayIsoDate())
            genreFilterActive ->
                artistRepository.getAllWithUpcomingConcertsFlow(todayIsoDate())
            else ->
                artistRepository.getByDateRangeWithConcertsFlow(currentStartDate, currentEndDate)
        }
        collectJob = viewModelScope.launch {
            source
                .catch { e -> if (e !is SQLiteException) throw e }
                .collect { artists ->
                    _uiState.value = ArtistasUiState(artists = artists, isLoading = false)
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
