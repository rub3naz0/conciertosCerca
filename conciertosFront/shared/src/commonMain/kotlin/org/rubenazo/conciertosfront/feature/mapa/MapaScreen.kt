package org.rubenazo.conciertosfront.feature.mapa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.location.LocationAuthorizationStatus
import org.rubenazo.conciertosfront.core.location.rememberLocationPermissionState
import org.rubenazo.conciertosfront.core.map.LatLng
import org.rubenazo.conciertosfront.core.map.MapMarker
import org.rubenazo.conciertosfront.core.map.MapProvider

@Composable
fun MapaScreen(
    startDate: String,
    endDate: String,
    onConcertClick: (Concert) -> Unit,
    targetLocation: LatLng? = null,
    onTargetConsumed: () -> Unit = {},
    onVisibleProvinceChanged: (String?) -> Unit = {},
) {
    val viewModel = koinViewModel<MapaViewModel>()
    val mapProvider = koinInject<MapProvider>()
    val uiState by viewModel.uiState.collectAsState()
    val permissionState = rememberLocationPermissionState()

    LaunchedEffect(Unit) {
        viewModel.checkPermissionState(permissionState.status)
    }

    LaunchedEffect(permissionState.status) {
        snapshotFlow { permissionState.status }.collect { status ->
            if (status == LocationAuthorizationStatus.GRANTED) {
                viewModel.onSystemPermissionResult(true)
            }
        }
    }

    LaunchedEffect(startDate, endDate) {
        viewModel.setDateFilter(startDate, endDate)
    }

    LaunchedEffect(targetLocation) {
        if (targetLocation != null) {
            viewModel.navigateTo(targetLocation)
            onTargetConsumed()
        }
    }

    LaunchedEffect(uiState.concerts) {
        onVisibleProvinceChanged(firstVisibleProvince(uiState.concerts))
    }

    data class IndexedConcert(val concert: Concert, val index: Int)

    val concertsWithCoords = uiState.concerts.mapIndexedNotNull { i, concert ->
        val lat = concert.salaConcierto.lat ?: return@mapIndexedNotNull null
        val lng = concert.salaConcierto.lng ?: return@mapIndexedNotNull null
        IndexedConcert(concert, i + 1)
    }

    val markers = concertsWithCoords.map { (concert, index) ->
        val label = concert.artists.joinToString(" · ") { it.name }
        MapMarker(id = concert.id, lat = concert.salaConcierto.lat!!, lng = concert.salaConcierto.lng!!, label = label, index = index)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            mapProvider.MapView(
                cameraPosition = uiState.cameraPosition,
                zoom = uiState.zoom,
                markers = markers,
                userLocation = uiState.userLocation,
                onBoundsChanged = { bounds -> viewModel.onBoundsChanged(bounds) },
                onMarkerClick = { markerId ->
                    concertsWithCoords.firstOrNull { it.concert.id == markerId }
                        ?.let { onConcertClick(it.concert) }
                },
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallFloatingActionButton(onClick = { viewModel.zoomIn() }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Acercar")
                }
                SmallFloatingActionButton(onClick = { viewModel.zoomOut() }) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Alejar")
                }
            }

            if (uiState.showPermissionDialog) {
                // Informational pre-prompt: it only explains WHY we ask for location.
                // It must never offer an exit that bypasses the system permission dialog
                // (App Store Guideline 5.1.1(iv)). Any way of leaving this message — the
                // button or an outside tap — proceeds to the OS request, where the user
                // makes the actual allow/deny decision.
                val proceedToSystemRequest = {
                    viewModel.onPermissionDialogAccepted()
                    permissionState.launchPermissionRequest()
                }
                AlertDialog(
                    onDismissRequest = proceedToSystemRequest,
                    title = { Text("Ubicación") },
                    text = {
                        Text(
                            "Usamos tu ubicación para mostrarte los conciertos más cercanos a ti. " +
                                "A continuación te pediremos permiso; si prefieres no compartirla, " +
                                "te situaremos en la Puerta del Sol de Madrid."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = proceedToSystemRequest) {
                            Text("Continuar")
                        }
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Text(
                text = "CONCIERTOS CERCANOS (${uiState.concerts.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(concertsWithCoords) { (concert, index) ->
                    MapConcertCard(concert = concert, index = index, onClick = { onConcertClick(concert) })
                }
            }
        }
    }
}

@Composable
private fun MapConcertCard(concert: Concert, index: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFFEA4335), CircleShape)
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
            Text(
                text = concert.artists.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = buildString {
                    append(concert.date)
                    if (concert.time != null) append(" · ${concert.time}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = concert.salaConcierto.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            }
        }
    }
}
