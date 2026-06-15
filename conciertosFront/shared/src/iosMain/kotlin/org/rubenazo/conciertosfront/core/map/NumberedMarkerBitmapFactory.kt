package org.rubenazo.conciertosfront.core.map

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine

/**
 * iOS counterpart of the Android factory, rasterizing the numbered pin with
 * Skia (Skiko, bundled with Compose Multiplatform on iOS). Mirrors the Android
 * geometry: 48px circle, red fill (#EA4335), white stroke and centered number.
 */
actual object NumberedMarkerBitmapFactory {

    private const val SIZE = 48
    private val cache = mutableMapOf<Int, ImageBitmap>()
    private val lock = SynchronizedObject()

    private val font = Font(
        FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD),
        24f,
    )

    private val fillPaint = Paint().apply {
        color = Color.makeARGB(255, 0xEA, 0x43, 0x35)
        mode = PaintMode.FILL
        isAntiAlias = true
    }

    private val strokePaint = Paint().apply {
        color = Color.makeARGB(255, 255, 255, 255)
        mode = PaintMode.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.makeARGB(255, 255, 255, 255)
        isAntiAlias = true
    }

    // Locked: pins are pre-warmed off the main thread (see MapLibreMapProvider),
    // so reads from the compose thread and writes from the warm-up coroutine can race.
    actual fun get(index: Int): ImageBitmap = synchronized(lock) {
        cache.getOrPut(index) {
            val surface = Surface.makeRasterN32Premul(SIZE, SIZE)
            val canvas = surface.canvas
            val center = SIZE / 2f
            val radius = SIZE / 2f - 2f

            canvas.drawCircle(center, center, radius, fillPaint)
            canvas.drawCircle(center, center, radius, strokePaint)

            val textLine = TextLine.make(index.toString(), font)
            val textX = center - textLine.width / 2f
            val textY = center - (font.metrics.ascent + font.metrics.descent) / 2f
            canvas.drawTextLine(textLine, textX, textY, textPaint)

            surface.makeImageSnapshot().toComposeImageBitmap()
        }
    }
}
