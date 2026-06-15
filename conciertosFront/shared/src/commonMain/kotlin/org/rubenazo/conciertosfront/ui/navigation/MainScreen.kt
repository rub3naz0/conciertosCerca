package org.rubenazo.conciertosfront.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import conciertosfront.shared.generated.resources.Res
import conciertosfront.shared.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.domain.model.SyncResult
import org.rubenazo.conciertosfront.core.domain.repository.ConcertRepository
import org.rubenazo.conciertosfront.feature.artistas.ArtistasScreen
import org.rubenazo.conciertosfront.feature.mapa.MapaScreen
import org.rubenazo.conciertosfront.core.util.SpanishLocale
import org.rubenazo.conciertosfront.core.util.epochMillisToIsoDate
import org.rubenazo.conciertosfront.core.util.todayIsoDate
import org.rubenazo.conciertosfront.core.config.AppConfig
import org.rubenazo.conciertosfront.core.map.LatLng
import org.rubenazo.conciertosfront.feature.conciertos.ConcertDetailScreen
import org.rubenazo.conciertosfront.feature.conciertos.ConcertosScreen
import org.rubenazo.conciertosfront.feature.salas.SalasScreen
import org.rubenazo.conciertosfront.feature.sync.DebugScreen

enum class MainTab(val label: String, val icon: ImageVector) {
    Mapa("MAPA", Icons.Default.Place),
    Conciertos("CONCIERTOS", Icons.AutoMirrored.Filled.List),
    Artistas("ARTISTAS", Icons.Default.Person),
    Salas("SALAS", Icons.Default.Home)
}

private val MONTH_ABBREVS = listOf(
    "ENE", "FEB", "MAR", "ABR", "MAY", "JUN",
    "JUL", "AGO", "SEP", "OCT", "NOV", "DIC"
)

private fun formatShortDate(isoDate: String): String {
    val parts = isoDate.split("-")
    if (parts.size < 3) return isoDate
    val month = parts[1].toIntOrNull()?.minus(1)?.let { MONTH_ABBREVS.getOrNull(it) } ?: parts[1]
    val day = parts[2].trimStart('0').ifEmpty { "0" }
    return "$day $month"
}

private fun chipLabel(startDate: String, endDate: String, today: String): String {
    if (startDate == today && endDate == today) return "HOY"
    if (startDate == endDate) return formatShortDate(startDate)
    return "${formatShortDate(startDate)} - ${formatShortDate(endDate)}"
}

/**
 * Main shell shown once past the sync gate: a bottom bar of [MainTab]s (Mapa / Conciertos /
 * Artistas / Salas) plus the shared top bar with the date-range filter.
 *
 * Selected tab and cross-screen selections (focused artist/venue, map target, date range) are held
 * as local composable state and threaded into each feature screen, so tab switches preserve
 * context without a navigation library. The debug screen is reachable only when
 * [AppConfig.SHOW_DEBUG] is enabled — disabled in production builds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(syncResult: SyncResult?, isSyncing: Boolean = false) {
    var selectedTab by remember { mutableStateOf(MainTab.Mapa) }
    val today = remember { todayIsoDate() }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedConcert by remember { mutableStateOf<Concert?>(null) }
    var showDebug by remember { mutableStateOf(false) }
    var focusedArtistId by remember { mutableStateOf<String?>(null) }
    var focusedSalaId by remember { mutableStateOf<String?>(null) }
    var mapTargetLocation by remember { mutableStateOf<LatLng?>(null) }
    var mapContextProvince by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val concertRepository: ConcertRepository = koinInject()

    if (showDatePicker) {
        val dateRangeState = remember { DateRangePickerState(locale = SpanishLocale) }
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                // Material3 lays dismiss/confirm into an internal flow-row whose
                // order follows the platform LayoutDirection, which renders reversed
                // on iOS. Own both buttons in a single LTR-pinned Row so the order
                // (CANCELAR | ACEPTAR) is identical on every platform.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("CANCELAR")
                        }
                        TextButton(
                            onClick = {
                                val selectedStart = dateRangeState.selectedStartDateMillis
                                val selectedEnd = dateRangeState.selectedEndDateMillis
                                if (selectedStart != null) {
                                    startDate = epochMillisToIsoDate(selectedStart)
                                    endDate = if (selectedEnd != null) {
                                        epochMillisToIsoDate(selectedEnd)
                                    } else {
                                        startDate
                                    }
                                }
                                showDatePicker = false
                            },
                            enabled = dateRangeState.selectedStartDateMillis != null,
                        ) {
                            Text("ACEPTAR")
                        }
                    }
                }
            },
        ) {
            DateRangePicker(
                state = dateRangeState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                title = {
                    Text(
                        text = "Selecciona fechas",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                headline = {
                    Row(
                        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val startText = dateRangeState.selectedStartDateMillis?.let {
                            formatShortDate(epochMillisToIsoDate(it))
                        } ?: "Fecha inicio"
                        val endText = dateRangeState.selectedEndDateMillis?.let {
                            formatShortDate(epochMillisToIsoDate(it))
                        } ?: "Fecha fin"
                        Text(startText, style = MaterialTheme.typography.titleLarge)
                        Text(" – ", style = MaterialTheme.typography.titleLarge)
                        Text(endText, style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(Res.drawable.app_logo),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ConciertosCerca",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    actions = {
                        Row(
                            modifier = Modifier.padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Subtle background-refresh indicator (cache-first sync in progress)
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (AppConfig.SHOW_DEBUG) {
                                IconButton(onClick = { showDebug = !showDebug }) { Icon(Icons.Default.Build, contentDescription = "Debug") }
                            }
                            AssistChip(
                                onClick = { showDatePicker = true },
                                label = {
                                    Text(
                                        text = chipLabel(startDate, endDate, today),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Filtrar por fecha",
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    labelColor = MaterialTheme.colorScheme.primary,
                                    leadingIconContentColor = MaterialTheme.colorScheme.primary,
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                if (tab != MainTab.Artistas) focusedArtistId = null
                                if (tab != MainTab.Salas) focusedSalaId = null
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when (selectedTab) {
                    MainTab.Mapa -> MapaScreen(
                        startDate = startDate,
                        endDate = endDate,
                        onConcertClick = { selectedConcert = it },
                        targetLocation = mapTargetLocation,
                        onTargetConsumed = { mapTargetLocation = null },
                        onVisibleProvinceChanged = { mapContextProvince = it },
                    )
                    MainTab.Conciertos -> ConcertosScreen(
                        startDate = startDate,
                        endDate = endDate,
                        onConcertClick = { selectedConcert = it },
                        defaultProvince = mapContextProvince,
                    )
                    MainTab.Artistas -> ArtistasScreen(
                        startDate = startDate,
                        endDate = endDate,
                        focusedArtistId = focusedArtistId,
                        onConcertClick = { concertId ->
                            coroutineScope.launch {
                                concertRepository.getById(concertId)?.let { concert ->
                                    selectedConcert = concert
                                }
                            }
                        },
                        onClearFocus = { focusedArtistId = null },
                    )
                    MainTab.Salas -> SalasScreen(
                        startDate = startDate,
                        endDate = endDate,
                        focusedSalaId = focusedSalaId,
                        onConcertClick = { concertId ->
                            coroutineScope.launch {
                                concertRepository.getById(concertId)?.let { concert ->
                                    selectedConcert = concert
                                }
                            }
                        },
                        onClearFocus = { focusedSalaId = null },
                        onSalaMapClick = { lat, lng ->
                            mapTargetLocation = LatLng(lat, lng)
                            selectedTab = MainTab.Mapa
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedConcert != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            selectedConcert?.let { concert ->
                ConcertDetailScreen(
                    concert = concert,
                    onBack = { selectedConcert = null },
                    onArtistClick = { artist ->
                        selectedConcert = null
                        focusedArtistId = artist.id
                        selectedTab = MainTab.Artistas
                    },
                    onSalaClick = { sala ->
                        selectedConcert = null
                        focusedSalaId = sala.id
                        selectedTab = MainTab.Salas
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = showDebug,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
        ) {
            DebugScreen(
                syncResult = syncResult,
                onBack = { showDebug = false },
            )
        }
    }
}
