package com.ahamai.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import ru.noties.jlatexmath.JLatexMathDrawable

/** A LaTeX formula rendered to a bitmap, with its pixel dimensions. */
data class RenderedLatex(val bitmap: ImageBitmap, val widthPx: Int, val heightPx: Int)

/**
 * Renders a LaTeX formula to a tinted [ImageBitmap] using JLatexMath (native, no WebView).
 * Returns null if the formula can't be parsed/rendered, so callers can fall back to text.
 */
fun renderLatexToBitmap(latex: String, textSizePx: Float, color: Color): RenderedLatex? {
    return try {
        val drawable = JLatexMathDrawable.builder(latex)
            .textSize(textSizePx)
            .padding(2)
            .background(0x00000000)
            .color(color.toArgb())
            .align(JLatexMathDrawable.ALIGN_LEFT)
            .build()

        val w = drawable.intrinsicWidth.coerceIn(1, 4000)
        val h = drawable.intrinsicHeight.coerceIn(1, 4000)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        RenderedLatex(bmp.asImageBitmap(), w, h)
    } catch (e: Throwable) {
        null
    }
}
