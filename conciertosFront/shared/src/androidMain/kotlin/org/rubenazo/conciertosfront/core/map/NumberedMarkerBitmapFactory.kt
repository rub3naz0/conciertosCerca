package org.rubenazo.conciertosfront.core.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual object NumberedMarkerBitmapFactory {

    private val cache = mutableMapOf<Int, ImageBitmap>()
    private const val SIZE = 48

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEA4335.toInt()
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // Synchronized to mirror the iOS factory: pins are pre-warmed off the main thread,
    // so cache reads and writes can come from different threads.
    @Synchronized
    actual fun get(index: Int): ImageBitmap = cache.getOrPut(index) {
        val bitmap = android.graphics.Bitmap.createBitmap(SIZE, SIZE, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = SIZE / 2f
        val radius = SIZE / 2f - 2f

        canvas.drawCircle(center, center, radius, circlePaint)
        canvas.drawCircle(center, center, radius, strokePaint)

        val text = index.toString()
        val textY = center - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, center, textY, textPaint)

        bitmap.asImageBitmap()
    }
}
