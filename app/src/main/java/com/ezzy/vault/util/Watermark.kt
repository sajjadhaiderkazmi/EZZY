package com.ezzy.vault.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Stamps a picture with a repeating "FOR VERIFICATION PURPOSE ONLY" pattern. [draw] is the one
 * place the pattern is actually painted, shared by the live on-screen preview (drawn straight
 * onto the composable, nothing on disk touched) and [apply] (the baked-in copy staged for Copy
 * or Share) — so what you see before you copy or share is exactly what goes out.
 */
object Watermark {

    private const val LABEL = "FOR VERIFICATION PURPOSE ONLY"
    private const val ROWS = 7

    // Plain gray all but vanished into a busy photo. Blue reads as a stamp against both a
    // bright white document scan and a dark picture alike, and a low alpha keeps it a
    // watermark rather than a banner sitting over the picture.
    private val TEXT_COLOR = Color.argb(110, 33, 130, 255)

    /** Paints the pattern directly onto [canvas], sized to a [width]x[height] surface. */
    fun draw(canvas: Canvas, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR
            textSize = width / 11f
            typeface = Typeface.DEFAULT_BOLD
        }

        canvas.save()
        canvas.rotate(-30f, width / 2f, height / 2f)
        val stepY = height.toFloat() / (ROWS - 1).coerceAtLeast(1)
        // Starts well left of the canvas and well above it — once rotated, a line this wide
        // still crosses every corner rather than leaving the edges of a portrait photo bare.
        val startX = -width * 0.6f
        var y = -height * 0.3f
        repeat(ROWS) {
            canvas.drawText(LABEL, startX, y, paint)
            y += stepY
        }
        canvas.restore()
    }

    /** Returns watermarked bytes, or the original bytes unchanged if the image can't be read. */
    fun apply(bytes: ByteArray, mimeType: String): ByteArray {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val marked = source.copy(Bitmap.Config.ARGB_8888, true) ?: return bytes
        draw(Canvas(marked), marked.width, marked.height)

        val format = if (mimeType == "image/png") {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
        return ByteArrayOutputStream().use { out ->
            marked.compress(format, 92, out)
            out.toByteArray()
        }
    }
}
