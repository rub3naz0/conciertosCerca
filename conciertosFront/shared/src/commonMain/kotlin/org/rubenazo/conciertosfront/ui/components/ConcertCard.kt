package org.rubenazo.conciertosfront.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.rubenazo.conciertosfront.core.domain.model.Concert

private val NeonMagenta = Color(0xFFFF00FF)

private fun formatDate(isoDate: String): String {
    val parts = isoDate.split("-")
    if (parts.size < 3) return isoDate.uppercase()
    val monthAbbrevs =
        listOf(
            "JAN",
            "FEB",
            "MAR",
            "APR",
            "MAY",
            "JUN",
            "JUL",
            "AUG",
            "SEP",
            "OCT",
            "NOV",
            "DEC",
        )
    val monthIndex = parts[1].trimStart('0').toIntOrNull()?.minus(1) ?: return isoDate.uppercase()
    val monthStr = monthAbbrevs.getOrElse(monthIndex) { parts[1] }
    val day = parts[2].trimStart('0').ifEmpty { "0" }
    val year = parts[0]
    return "$monthStr $day, $year"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConcertCard(
    concert: Concert,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val heroImageUrl = concert.artists.firstOrNull()?.imageUrl
    val genres = concert.artists.mapNotNull { it.genre }.distinct()

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // Hero image with gradient overlay and genre chips
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            BrandedAsyncImage(
                url = heroImageUrl,
                contentDescription = concert.artists.firstOrNull()?.name,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )

            // Bottom gradient overlay
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 60f,
                            ),
                        ),
            )

            // Genre chips overlaid bottom-left
            if (genres.isNotEmpty()) {
                FlowRow(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    genres.forEach { genre ->
                        GenreChip(genre = genre)
                    }
                }
            }
        }

        // Content below image
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Artist names
            Text(
                text = concert.artists.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Date + time row (with optional price on the right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dateText =
                    buildString {
                        append(formatDate(concert.date))
                        concert.time?.let { append(" · $it") }
                    }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                concert.price?.let { price ->
                    Text(
                        text = price,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Venue row
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "${concert.salaConcierto.name}, ${concert.salaConcierto.city}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GenreChip(genre: String) {
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
