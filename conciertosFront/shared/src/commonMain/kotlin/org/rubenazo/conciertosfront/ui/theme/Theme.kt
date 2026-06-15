package org.rubenazo.conciertosfront.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VoltageDarkColorScheme = darkColorScheme(
    primary = ElectricLime,
    onPrimary = PureBlack,
    primaryContainer = ElectricLime,
    onPrimaryContainer = DarkLime,
    secondary = NeonMagentaLight,
    onSecondary = DarkMagenta,
    secondaryContainer = NeonMagenta,
    onSecondaryContainer = DarkMagenta,
    background = PureBlack,
    onBackground = OnSurface,
    surface = DarkSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceVariant = SurfaceContainerHighest,
    surfaceContainerLowest = DarkSurfaceDim,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    surfaceBright = SurfaceBright,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun VoltageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VoltageDarkColorScheme,
        typography = VoltageTypography(),
        shapes = VoltageShapes,
        content = content
    )
}
