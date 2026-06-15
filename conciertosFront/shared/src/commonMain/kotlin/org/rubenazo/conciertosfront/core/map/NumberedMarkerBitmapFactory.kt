package org.rubenazo.conciertosfront.core.map

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Produces a red circular pin with a white number, used as the icon image for
 * the concert SymbolLayer. Platform-specific because each platform rasterizes
 * to an [ImageBitmap] with its native 2D canvas (android.graphics on Android,
 * Skia on iOS). Implementations cache by index.
 */
expect object NumberedMarkerBitmapFactory {
    fun get(index: Int): ImageBitmap
}
