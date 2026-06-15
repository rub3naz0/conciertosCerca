package org.rubenazo.conciertosfront.feature.conciertos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.ui.components.ConcertCard
import org.rubenazo.conciertosfront.ui.components.FilterChipRow

@Composable
fun ConcertosScreen(
    startDate: String,
    endDate: String,
    onConcertClick: (Concert) -> Unit,
    defaultProvince: String? = null,
) {
    val viewModel = koinViewModel<ConcertosViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    var selectedProvince by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var userTouchedProvince by remember { mutableStateOf(false) }

    LaunchedEffect(startDate, endDate) {
        viewModel.setDateFilter(startDate, endDate)
        selectedProvince = null
        selectedGenre = null
        userTouchedProvince = false
    }

    // A genre filter ignores the date range: when one is active the VM lists all
    // upcoming concerts and the genre match itself is applied below.
    LaunchedEffect(selectedGenre) {
        viewModel.setGenreFilterActive(selectedGenre != null)
    }

    val provinces = remember(uiState.concerts) {
        uiState.concerts.map { it.salaConcierto.province }.distinct().sorted()
    }

    // Pre-select the province of the first concert visible in the Mapa tab as
    // the default filter, but only while the user hasn't interacted with the
    // province chip, and only if that province is actually present among the
    // loaded concerts.
    LaunchedEffect(defaultProvince, provinces, userTouchedProvince) {
        if (!userTouchedProvince && defaultProvince != null && provinces.contains(defaultProvince)) {
            selectedProvince = defaultProvince
        }
    }

    val genres = remember(uiState.concerts) {
        uiState.concerts
            .flatMap { concert -> concert.artists.mapNotNull { it.genre } }
            .flatMap { it.split(",", "/").map { g -> g.trim() } }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    val displayConcerts = uiState.concerts
        .let { concerts ->
            if (selectedProvince != null) {
                concerts.filter { it.salaConcierto.province == selectedProvince }
            } else {
                concerts
            }
        }
        .let { concerts ->
            if (selectedGenre != null) {
                concerts.filter { concert ->
                    concert.artists.any { artist ->
                        artist.genre
                            ?.split(",", "/")
                            ?.map { it.trim() }
                            ?.any { it.equals(selectedGenre, ignoreCase = true) }
                            ?: false
                    }
                }
            } else {
                concerts
            }
        }

    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        uiState.concerts.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NO HAY CONCIERTOS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                FilterChipRow(
                    options = provinces,
                    selected = selectedProvince,
                    onSelectedChange = {
                        userTouchedProvince = true
                        selectedProvince = it
                    },
                    label = "Province",
                )
                FilterChipRow(
                    options = genres,
                    selected = selectedGenre,
                    onSelectedChange = { selectedGenre = it },
                    label = "Genre",
                )
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(displayConcerts, key = { it.id }) { concert ->
                        ConcertCard(concert = concert, onClick = { onConcertClick(concert) })
                    }
                }
            }
        }
    }
}
