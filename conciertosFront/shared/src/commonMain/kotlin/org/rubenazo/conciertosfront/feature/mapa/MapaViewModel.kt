package org.rubenazo.conciertosfront.feature.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.SQLiteException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.rubenazo.conciertosfront.core.data.local.DatabaseProviderPort
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.domain.repository.ConcertRepository
import org.rubenazo.conciertosfront.core.location.LocationAuthorizationStatus
import org.rubenazo.conciertosfront.core.location.LocationPort
import org.rubenazo.conciertosfront.core.map.CameraBounds
import org.rubenazo.conciertosfront.core.map.LatLng
import org.rubenazo.conciertosfront.core.util.todayIsoDate

val DEFAULT_LOCATION = LatLng(40.4168, -3.7038) // Puerta del Sol, Madrid

data class MapaUiState(
    val cameraPosition: LatLng = DEFAULT_LOCATION,
    val zoom: Float = 14f,
    val showPermissionDialog: Boolean = false,
    val userLocationResolved: Boolean = false,
    val userLocation: LatLng? = null,
    val concerts: List<Concert> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MapaViewModel(
    private val locationProvider: LocationPort,
    private val concertRepository: ConcertRepository,
    private val dbProvider: DatabaseProviderPort,
) : ViewModel() {

    private val _baseState = MutableStateFlow(MapaUiState())

    private val _bounds = MutableStateFlow<CameraBounds?>(null)

    private val _dateRange = MutableStateFlow(todayIsoDate() to todayIsoDate())

    private val _dbVersion = MutableStateFlow(0)

    private val _concerts = combine(
        _bounds.filterNotNull().debounce(300),
        _dateRange,
        _dbVersion,
    ) { bounds, dates, _ ->
        bounds to dates
    }.flatMapLatest { (bounds, dates) ->
        concertRepository.getInBoundingBoxFlow(
            latMin = bounds.latMin,
            latMax = bounds.latMax,
            lngMin = bounds.lngMin,
            lngMax = bounds.lngMax,
            startDate = dates.first,
            endDate = dates.second,
        ).catch { e -> if (e !is SQLiteException) throw e }
    }

    val uiState: StateFlow<MapaUiState> = combine(_baseState, _concerts) { base, concerts ->
        base.copy(concerts = concerts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapaUiState(),
    )

    init {
        observeResetSignal()
    }

    fun onBoundsChanged(bounds: CameraBounds) {
        _bounds.value = bounds
    }

    fun setDateFilter(startDate: String, endDate: String) {
        _dateRange.value = startDate to endDate
    }

    fun checkPermissionState(status: LocationAuthorizationStatus) {
        if (_baseState.value.userLocationResolved) return
        when (status) {
            LocationAuthorizationStatus.GRANTED -> fetchUserLocation()
            LocationAuthorizationStatus.NOT_DETERMINED -> {
                _baseState.value = _baseState.value.copy(showPermissionDialog = true)
            }
            LocationAuthorizationStatus.DENIED -> {
                _baseState.value = _baseState.value.copy(userLocationResolved = true)
            }
        }
    }

    fun onPermissionDialogAccepted() {
        _baseState.value = _baseState.value.copy(showPermissionDialog = false)
    }

    fun onSystemPermissionResult(granted: Boolean) {
        if (granted) {
            fetchUserLocation()
        } else {
            _baseState.value = _baseState.value.copy(userLocationResolved = true)
        }
    }

    fun navigateTo(target: LatLng) {
        _baseState.value = _baseState.value.copy(cameraPosition = target, zoom = 16f)
    }

    fun zoomIn() {
        _baseState.value = _baseState.value.copy(
            zoom = (_baseState.value.zoom + 1f).coerceAtMost(20f)
        )
    }

    fun zoomOut() {
        _baseState.value = _baseState.value.copy(
            zoom = (_baseState.value.zoom - 1f).coerceAtLeast(2f)
        )
    }

    private fun observeResetSignal() {
        viewModelScope.launch {
            dbProvider.resetSignal.collect {
                _dbVersion.update { it + 1 }
            }
        }
    }

    private fun fetchUserLocation() {
        if (_baseState.value.userLocationResolved) return
        viewModelScope.launch {
            val location = try {
                locationProvider.getLastLocation()
            } catch (e: Exception) {
                null
            }
            _baseState.value = _baseState.value.copy(
                cameraPosition = location ?: DEFAULT_LOCATION,
                userLocation = location,
                userLocationResolved = true,
            )
        }
    }
}
