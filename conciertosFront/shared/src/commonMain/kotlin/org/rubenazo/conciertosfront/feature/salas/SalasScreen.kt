package org.rubenazo.conciertosfront.feature.salas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.rubenazo.conciertosfront.core.domain.model.SalaWithConcerts
import org.rubenazo.conciertosfront.core.domain.model.VenueConcert
import org.rubenazo.conciertosfront.ui.components.BrandedAsyncImage
import org.rubenazo.conciertosfront.ui.components.FilterChipRow
import org.rubenazo.conciertosfront.ui.components.ScrimBackButton

private val NeonMagenta = Color(0xFFFF00FF)

private val MONTH_ABBREVS =
    listOf(
        "ENE",
        "FEB",
        "MAR",
        "ABR",
        "MAY",
        "JUN",
        "JUL",
        "AGO",
        "SEP",
        "OCT",
        "NOV",
        "DIC",
    )

private fun formatShortDate(isoDate: String): String {
    val parts = isoDate.split("-")
    if (parts.size < 3) return isoDate.uppercase()
    val month = parts[1].toIntOrNull()?.minus(1)?.let { MONTH_ABBREVS.getOrNull(it) } ?: parts[1]
    val day = parts[2].trimStart('0').ifEmpty { "0" }
    return "$month $day"
}

@Composable
fun SalasScreen(
    startDate: String,
    endDate: String,
    focusedSalaId: String? = null,
    onConcertClick: (String) -> Unit = {},
    onClearFocus: () -> Unit = {},
    onSalaMapClick: (Double, Double) -> Unit = { _, _ -> },
) {
    val viewModel = koinViewModel<SalasViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    var selectedProvince by remember { mutableStateOf<String?>(null) }
    var autoProvinceApplied by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(startDate, endDate) {
        viewModel.setDateFilter(startDate, endDate)
        selectedProvince = null
    }

    androidx.compose.runtime.LaunchedEffect(focusedSalaId, uiState.salas) {
        if (focusedSalaId != null && autoProvinceApplied != focusedSalaId && uiState.salas.isNotEmpty()) {
            selectedProvince =
                uiState.salas
                    .find { it.sala.id == focusedSalaId }
                    ?.sala
                    ?.province
            autoProvinceApplied = focusedSalaId
        } else if (focusedSalaId == null && autoProvinceApplied != null) {
            autoProvinceApplied = null
        }
    }

    val provinces =
        remember(uiState.salas) {
            uiState.salas
                .map { it.sala.province }
                .distinct()
                .sorted()
        }

    val displaySalas =
        uiState.salas
            .let { salas -> if (focusedSalaId != null) salas.filter { it.sala.id == focusedSalaId } else salas }
            .let { salas -> if (selectedProvince != null) salas.filter { it.sala.province == selectedProvince } else salas }

    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        uiState.salas.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NO HAY SALAS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                if (focusedSalaId == null) {
                    FilterChipRow(
                        options = provinces,
                        selected = selectedProvince,
                        onSelectedChange = { selectedProvince = it },
                        label = "Province",
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(displaySalas, key = { it.sala.id }) { salaWithConcerts ->
                            SalaCard(
                                salaWithConcerts = salaWithConcerts,
                                onConcertClick = onConcertClick,
                                onSalaMapClick = onSalaMapClick,
                            )
                        }
                    }

                    if (focusedSalaId != null) {
                        ScrimBackButton(
                            onClick = onClearFocus,
                            contentDescription = "Ver todas las salas",
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 4.dp, start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SalaCard(
    salaWithConcerts: SalaWithConcerts,
    onConcertClick: (String) -> Unit,
    onSalaMapClick: (Double, Double) -> Unit,
) {
    val sala = salaWithConcerts.sala

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            BrandedAsyncImage(
                url = sala.imageUrl,
                contentDescription = sala.name,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 40f,
                            ),
                        ),
            )

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = sala.name.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        if (sala.lat != null && sala.lng != null) {
                            Modifier.clickable { onSalaMapClick(sala.lat, sala.lng) }
                        } else {
                            Modifier
                        },
                )
                val location =
                    buildString {
                        sala.address?.let { append(it) }
                        if (isNotEmpty()) append(", ")
                        append(sala.city)
                    }
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!sala.description.isNullOrBlank()) {
            Text(
                text = sala.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (salaWithConcerts.upcomingConcerts.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "PROXIMOS CONCIERTOS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                salaWithConcerts.upcomingConcerts.forEach { concert ->
                    VenueConcertItem(
                        concert = concert,
                        onClick = { onConcertClick(concert.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VenueConcertItem(
    concert: VenueConcert,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
                .clickable(onClick = onClick)
                .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = formatShortDate(concert.date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            concert.time?.let { time ->
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.weight(2f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = NeonMagenta,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = concert.artistNames.joinToString(", ") { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
