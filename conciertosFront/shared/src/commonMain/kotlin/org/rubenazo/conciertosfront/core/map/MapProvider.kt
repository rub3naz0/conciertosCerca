package org.rubenazo.conciertosfront.core.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class LatLng(val lat: Double, val lng: Double)

data class MapMarker(
    val id: String,
    val lat: Double,
    val lng: Double,
    val label: String,
    val index: Int = 0,
)

data class CameraBounds(
    val latMin: Double,
    val latMax: Double,
    val lngMin: Double,
    val lngMax: Double,
)

interface MapProvider {
    @Composable
    fun MapView(
        cameraPosition: LatLng,
        zoom: Float,
        markers: List<MapMarker>,
        userLocation: LatLng?,
        onBoundsChanged: (CameraBounds) -> Unit,
        onMarkerClick: (markerId: String) -> Unit,
        modifier: Modifier,
    )
}
