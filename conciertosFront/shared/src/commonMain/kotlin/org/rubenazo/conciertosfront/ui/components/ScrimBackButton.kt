package org.rubenazo.conciertosfront.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Floating circular back button with a scrim behind it. Single source of truth for the three
 * back/clear-focus buttons in the app.
 *
 * Visual defaults suit a solid dark background (Artistas/Salas focus mode): a subtle surface
 * scrim with the brand accent tint. Over a photo (concert detail) pass [scrimColor] = black and
 * [tint] = white so the icon stays legible against arbitrary imagery.
 *
 * The caller supplies [modifier] for alignment/padding (e.g. `align(TopStart).statusBarsPadding()`).
 */
@Composable
fun ScrimBackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    scrimColor: Color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.background(scrimColor, CircleShape),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
