package org.rubenazo.conciertosfront.feature.conciertos

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.rubenazo.conciertosfront.core.domain.model.Artist
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto
import org.rubenazo.conciertosfront.ui.components.BrandedAsyncImage
import org.rubenazo.conciertosfront.ui.components.ScrimBackButton

private val NeonMagenta = Color(0xFFFF00FF)

private fun formatDate(isoDate: String): String {
    val parts = isoDate.split("-")
    if (parts.size < 3) return isoDate.uppercase()
    val monthAbbrevs =
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
    val monthIndex = parts[1].trimStart('0').toIntOrNull()?.minus(1) ?: return isoDate.uppercase()
    val monthStr = monthAbbrevs.getOrElse(monthIndex) { parts[1] }
    val day = parts[2].trimStart('0').ifEmpty { "0" }
    val year = parts[0]
    return "$day $monthStr $year"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConcertDetailScreen(
    concert: Concert,
    onBack: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    onSalaClick: (SalaConcierto) -> Unit = {},
) {
    val heroImageUrl = concert.artists.firstOrNull()?.imageUrl
    val genres = concert.artists.mapNotNull { it.genre }.distinct()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState()),
        ) {
            // Hero image with genre chips
            Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                BrandedAsyncImage(
                    url = heroImageUrl,
                    contentDescription = concert.artists.firstOrNull()?.name,
                    modifier = Modifier.fillMaxSize(),
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Black.copy(alpha = 0.4f),
                                            Color.Transparent,
                                            Color.Black,
                                        ),
                                    startY = 0f,
                                ),
                            ),
                )

                // Genre chips at bottom-left
                if (genres.isNotEmpty()) {
                    FlowRow(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
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

            // Content
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Concert title (artist names — clickable)
                Column {
                    concert.artists.forEach { artist ->
                        Text(
                            text = artist.name.uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { onArtistClick(artist) },
                        )
                    }
                }

                // Venue section (clickable)
                DetailSection(label = "SALA") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onSalaClick(concert.salaConcierto) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = concert.salaConcierto.name.uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    val location =
                        buildString {
                            concert.salaConcierto.address?.let { append(it) }
                            if (isNotEmpty()) append(", ")
                            append(concert.salaConcierto.city)
                        }
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Date & time section
                DetailSection(label = "FECHA Y HORA") {
                    Text(
                        text = formatDate(concert.date),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    concert.time?.let { time ->
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Price section
                concert.price?.let { price ->
                    DetailSection(label = "PRECIO") {
                        Text(
                            text = price,
                            style = MaterialTheme.typography.headlineMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Lineup section
                if (concert.artists.isNotEmpty()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = "LINEUP",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${concert.artists.size} ARTISTAS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            concert.artists.forEach { artist ->
                                ArtistTile(
                                    artist = artist,
                                    onClick = { onArtistClick(artist) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Back button floating on top, outside scroll. Over the hero photo, so it uses a black
        // scrim + white icon for contrast against arbitrary imagery.
        ScrimBackButton(
            onClick = onBack,
            contentDescription = "Volver",
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 4.dp),
            scrimColor = Color.Black.copy(alpha = 0.5f),
            tint = Color.White,
        )
    }
}

@Composable
private fun DetailSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NeonMagenta,
        )
        content()
    }
}

@Composable
private fun ArtistTile(
    artist: Artist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(110.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(110.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            BrandedAsyncImage(
                url = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = artist.name.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
    }
}
