package com.ezzy.vault.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Stamps a picture with a repeating "FOR VERIFICATION PURPOSE ONLY" pattern before it ever
 * leaves the vault. The file sealed on disk is never touched — this only ever runs on a copy
 * already staged for Copy or Share, so switching the watermark off again is always just that.
 */
object Watermark {

    private const val LABEL = "FOR VERIFICATION PURPOSE ONLY"
    private const val ROWS = 7

    /** Returns watermarked bytes, or the original bytes unchanged if the image can't be read. */
    fun apply(bytes: ByteArray, mimeType: String): ByteArray {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val marked = source.copy(Bitmap.Config.ARGB_8888, true) ?: return bytes
        val canvas = Canvas(marked)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 128, 128, 128)
            textSize = marked.width / 11f
            typeface = Typeface.DEFAULT_BOLD
        }

        canvas.save()
        canvas.rotate(-30f, marked.width / 2f, marked.height / 2f)
        val stepY = marked.height.toFloat() / (ROWS - 1).coerceAtLeast(1)
        // Starts well left of the canvas and well above it — once rotated, a line this wide
        // still crosses every corner rather than leaving the edges of a portrait photo bare.
        val startX = -marked.width * 0.6f
        var y = -marked.height * 0.3f
        repeat(ROWS) {
            canvas.drawText(LABEL, startX, y, paint)
            y += stepY
        }
        canvas.restore()

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
