package org.rubenazo.conciertosfront.feature.artistas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import org.rubenazo.conciertosfront.core.domain.model.ArtistWithConcerts
import org.rubenazo.conciertosfront.core.domain.model.UpcomingConcert
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
fun ArtistasScreen(
    startDate: String,
    endDate: String,
    focusedArtistId: String? = null,
    onConcertClick: (String) -> Unit = {},
    onClearFocus: () -> Unit = {},
) {
    val viewModel = koinViewModel<ArtistasViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var autoGenreApplied by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(startDate, endDate) {
        viewModel.setDateFilter(startDate, endDate)
        selectedGenre = null
    }

    // A name search ignores the date filter and matches on name only; focus mode
    // (deep-linked single artist) never searches.
    androidx.compose.runtime.LaunchedEffect(searchQuery, focusedArtistId) {
        viewModel.setSearchQuery(if (focusedArtistId != null) "" else searchQuery)
    }

    // A genre filter also ignores the date range: when one is active the VM browses
    // all upcoming artists and the genre match itself is applied below.
    androidx.compose.runtime.LaunchedEffect(selectedGenre) {
        viewModel.setGenreFilterActive(selectedGenre != null)
    }

    androidx.compose.runtime.LaunchedEffect(focusedArtistId, uiState.artists) {
        if (focusedArtistId != null && autoGenreApplied != focusedArtistId && uiState.artists.isNotEmpty()) {
            selectedGenre =
                uiState.artists
                    .find { it.artist.id == focusedArtistId }
                    ?.artist
                    ?.genre
                    ?.split(",", "/")
                    ?.firstOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            autoGenreApplied = focusedArtistId
        } else if (focusedArtistId == null && autoGenreApplied != null) {
            autoGenreApplied = null
        }
    }

    val genres =
        remember(uiState.artists) {
            uiState.artists
                .mapNotNull { it.artist.genre }
                .flatMap { it.split(",", "/").map { g -> g.trim() } }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }

    val displayArtists =
        uiState.artists
            .let { artists -> if (focusedArtistId != null) artists.filter { it.artist.id == focusedArtistId } else artists }
            .let { artists ->
                if (selectedGenre != null && searchQuery.isBlank()) {
                    artists.filter { awc ->
                        awc.artist.genre
                            ?.split(",", "/")
                            ?.map { it.trim() }
                            ?.any { it.equals(selectedGenre, ignoreCase = true) }
                            ?: false
                    }
                } else {
                    artists
                }
            }
            .let { artists ->
                // When browsing by genre, order artists by their next concert date
                // (upcomingConcerts is already date-ascending, so the first is the
                // soonest). Artists with no upcoming concert sort last.
                if (selectedGenre != null && searchQuery.isBlank()) {
                    artists.sortedWith(
                        compareBy(nullsLast()) { awc ->
                            awc.upcomingConcerts.minOfOrNull { it.date }
                        }
                    )
                } else {
                    artists
                }
            }

    Column(modifier = Modifier.fillMaxSize()) {
        if (focusedArtistId == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar artista por nombre") },
                singleLine = true,
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Limpiar búsqueda",
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // Genre filter only makes sense when browsing, not while searching by name.
            if (searchQuery.isBlank()) {
                FilterChipRow(
                    options = genres,
                    selected = selectedGenre,
                    onSelectedChange = { selectedGenre = it },
                    label = "Genre",
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                displayArtists.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "SIN RESULTADOS" else "NO HAY ARTISTAS",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(displayArtists, key = { it.artist.id }) { artistWithConcerts ->
                            ArtistCard(
                                artistWithConcerts = artistWithConcerts,
                                onConcertClick = onConcertClick,
                            )
                        }
                    }
                }
            }

            if (focusedArtistId != null) {
                ScrimBackButton(
                    onClick = onClearFocus,
                    contentDescription = "Ver todos los artistas",
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 4.dp, start = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistCard(
    artistWithConcerts: ArtistWithConcerts,
    onConcertClick: (String) -> Unit,
) {
    val artist = artistWithConcerts.artist
    val genres =
        artist.genre
            ?.split(",", "/")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            BrandedAsyncImage(
                url = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 60f,
                            ),
                        ),
            )

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = artist.name.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (genres.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        genres.forEach { genre ->
                            Box(
                                modifier =
                                    Modifier
                                        .border(BorderStroke(1.dp, NeonMagenta), RectangleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = genre.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonMagenta,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!artist.description.isNullOrBlank()) {
            Text(
                text = artist.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (artistWithConcerts.upcomingConcerts.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "PROXIMOS CONCIERTOS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                artistWithConcerts.upcomingConcerts.forEach { concert ->
                    ConcertItem(
                        concert = concert,
                        onClick = { onConcertClick(concert.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConcertItem(
    concert: UpcomingConcert,
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
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = concert.salaName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = concert.salaCity.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
