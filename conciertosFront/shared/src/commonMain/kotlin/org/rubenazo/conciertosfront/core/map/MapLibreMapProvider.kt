package org.rubenazo.conciertosfront.core.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.Feature as ExprFeature
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

const val OPEN_FREE_MAP_DARK_STYLE_URL =
    "https://tiles.openfreemap.org/styles/liberty"

class MapLibreMapProvider : MapProvider {

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
        val cameraState = rememberCameraState(
            firstPosition = CameraPosition(
                target = Position(
                    longitude = cameraPosition.lng,
                    latitude = cameraPosition.lat,
                ),
                zoom = zoom.toDouble(),
            )
        )

        LaunchedEffect(zoom) {
            cameraState.animateTo(
                CameraPosition(
                    target = cameraState.position.target,
                    zoom = zoom.toDouble(),
                )
            )
        }

        LaunchedEffect(cameraPosition) {
            cameraState.animateTo(
                CameraPosition(
                    target = Position(
                        longitude = cameraPosition.lng,
                        latitude = cameraPosition.lat,
                    ),
                    zoom = cameraState.position.zoom,
                )
            )
        }

        LaunchedEffect(cameraState.position) {
            // On the first composition the map's GL surface isn't ready yet, so
            // projection is null. Keyed only on position, this effect wouldn't
            // re-run until the user pans — leaving the initial bounds (and thus
            // every marker) unemitted. Wait for the projection to come up instead
            // of bailing out, so concerts appear without needing to move the map.
            var projection = cameraState.projection
            var attempts = 0
            while (projection == null && attempts < 40) {
                delay(50)
                projection = cameraState.projection
                attempts++
            }
            val bbox = (projection ?: return@LaunchedEffect).queryVisibleBoundingBox()
            onBoundsChanged(
                CameraBounds(
                    latMin = bbox.south,
                    latMax = bbox.north,
                    lngMin = bbox.west,
                    lngMax = bbox.east,
                )
            )
        }

        val currentMarkers = rememberUpdatedState(markers)
        val currentUserLocation = rememberUpdatedState(userLocation)
        val currentOnMarkerClick = rememberUpdatedState(onMarkerClick)

        MaplibreMap(
            modifier = modifier,
            baseStyle = BaseStyle.Uri(OPEN_FREE_MAP_DARK_STYLE_URL),
            cameraState = cameraState,
        ) {
            val location = currentUserLocation.value
            val locationFeatures = if (location != null) {
                listOf(
                    Feature(
                        geometry = Point(Position(location.lng, location.lat)),
                        properties = JsonObject(emptyMap()),
                    )
                )
            } else {
                emptyList()
            }
            val locationSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(FeatureCollection(locationFeatures)),
                options = GeoJsonOptions(synchronousUpdate = true),
            )
            CircleLayer(
                id = "user-location",
                source = locationSource,
                color = const(Color(0xFF4285F4)),
                radius = const(8.dp),
                strokeColor = const(Color.White),
                strokeWidth = const(2.dp),
            )

            val activeMarkers = currentMarkers.value
            val markerFeatures = remember(activeMarkers) {
                activeMarkers.map { marker ->
                    Feature(
                        geometry = Point(Position(marker.lng, marker.lat)),
                        properties = JsonObject(mapOf(
                            "id" to JsonPrimitive(marker.id),
                            "label" to JsonPrimitive(marker.label),
                            "marker_index" to JsonPrimitive(marker.index.toString()),
                        )),
                    )
                }
            }
            val markerSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(FeatureCollection(markerFeatures)),
                options = GeoJsonOptions(synchronousUpdate = true),
            )

            // Rasterizing the numbered pins runs text shaping through Skia, which on iOS
            // stalls the compose thread when many markers appear at once. Warm the bitmap
            // cache off the main thread first, then attach the symbol layer once it's ready.
            val highestIndex = activeMarkers.maxOfOrNull { it.index } ?: 0
            var warmedIndex by remember { mutableStateOf(-1) }
            LaunchedEffect(highestIndex) {
                if (highestIndex > warmedIndex) {
                    withContext(Dispatchers.Default) {
                        for (i in 0..highestIndex) NumberedMarkerBitmapFactory.get(i)
                    }
                    warmedIndex = highestIndex
                }
            }

            if (activeMarkers.isNotEmpty() && warmedIndex >= highestIndex) {
                val iconImage = remember(activeMarkers) {
                    val iconCases = activeMarkers.map { marker ->
                        case(marker.index.toString(), image(NumberedMarkerBitmapFactory.get(marker.index)))
                    }.toTypedArray()
                    switch(
                        input = ExprFeature["marker_index"].asString(),
                        *iconCases,
                        fallback = image(NumberedMarkerBitmapFactory.get(0)),
                    )
                }
                SymbolLayer(
                    id = "concert-markers",
                    source = markerSource,
                    iconImage = iconImage,
                    iconAllowOverlap = const(true),
                    iconIgnorePlacement = const(true),
                    onClick = { features ->
                        val markerId = features.firstOrNull()
                            ?.properties
                            ?.get("id")
                            ?.let { (it as? JsonPrimitive)?.content }
                        if (markerId != null) {
                            currentOnMarkerClick.value(markerId)
                            ClickResult.Consume
                        } else {
                            ClickResult.Pass
                        }
                    },
                )
            }
        }
    }
}
