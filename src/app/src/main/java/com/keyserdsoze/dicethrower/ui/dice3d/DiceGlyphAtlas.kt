package com.keyserdsoze.dicethrower.ui.dice3d

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.min

/** Paints the numeral atlas described by [DiceGlyphAtlasLayout] with a real typeface. */
internal object DiceGlyphAtlas {
    const val SIZE = 1024

    fun render(typeface: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
        }
        val cellWidth = SIZE.toFloat() / DiceGlyphAtlasLayout.COLUMNS
        val cellHeight = SIZE.toFloat() / DiceGlyphAtlasLayout.ROWS
        val bounds = Rect()
        DiceGlyphAtlasLayout.labels.forEachIndexed { index, label ->
            val column = index % DiceGlyphAtlasLayout.COLUMNS
            val row = index / DiceGlyphAtlasLayout.COLUMNS
            val centerX = (column + 0.5f) * cellWidth
            val centerY = (row + 0.5f) * cellHeight
            val underline = DiceGlyphAtlasLayout.needsUnderline(label)

            paint.textSize = cellHeight
            paint.getTextBounds(label, 0, label.length, bounds)
            val maxWidth = cellWidth * if (label.length > 1) 0.84f else 0.70f
            val maxHeight = cellHeight * when {
                underline -> 0.60f
                label.length > 1 -> 0.58f
                else -> 0.70f
            }
            paint.textSize *= min(maxWidth / bounds.width(), maxHeight / bounds.height())
            paint.getTextBounds(label, 0, label.length, bounds)

            val glyphShift = if (underline) -cellHeight * 0.045f else 0f
            val x = centerX - bounds.exactCenterX()
            val baseline = centerY - bounds.exactCenterY() + glyphShift
            canvas.drawText(label, x, baseline, paint)
            if (underline) {
                val width = bounds.width() * 0.78f
                val top = baseline + cellHeight * 0.055f
                canvas.drawRoundRect(
                    centerX - width / 2f, top, centerX + width / 2f, top + cellHeight * 0.06f,
                    cellHeight * 0.03f, cellHeight * 0.03f, paint,
                )
            }
        }
        return bitmap
    }
}
