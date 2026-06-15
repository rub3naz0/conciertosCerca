package org.rubenazo.conciertosfront.testutil

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.rubenazo.conciertosfront.core.map.CameraBounds
import org.rubenazo.conciertosfront.core.map.LatLng
import org.rubenazo.conciertosfront.core.map.MapMarker
import org.rubenazo.conciertosfront.core.map.MapProvider

class FakeMapProvider : MapProvider {
    var lastCameraPosition: LatLng? = null
    var lastZoom: Float? = null
    var lastMarkers: List<MapMarker> = emptyList()
    var lastUserLocation: LatLng? = null
    var lastOnMarkerClick: ((String) -> Unit)? = null

    @Composable
    override fun MapView(
        cameraPosition: LatLng,
        zoom: Float,
        markers: List<MapMarker>,
        userLocation: LatLng?,
        onBoundsChanged: (CameraBounds) -> Unit,
        onMarkerClick: (markerId: String) -> Unit,
        modifier: Modifier,
    ) {
        lastCameraPosition = cameraPosition
        lastZoom = zoom
        lastMarkers = markers
        lastUserLocation = userLocation
        lastOnMarkerClick = onMarkerClick
    }
}
