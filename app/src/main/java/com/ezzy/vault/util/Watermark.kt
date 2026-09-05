package com.ezzy.vault.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Everything the user can change about the stamp, kept per file so one picture can carry a
 * faint grey mark and the next a bold red one.
 */
data class WatermarkStyle(
    /** Percent. Low enough to read through, high enough to be seen. */
    val opacity: Int = DEFAULT_OPACITY,
    /** Percent of the picture's width one line of text spans. */
    val scale: Int = DEFAULT_SCALE,
    /** Percent of the width the whole pattern slides left (negative) or right. */
    val offsetX: Int = 0,
    /** Percent of the height the whole pattern slides up (negative) or down. */
    val offsetY: Int = 0,
    val colorKey: String = DEFAULT_COLOR,
) {
    companion object {
        const val DEFAULT_OPACITY = 40
        const val DEFAULT_SCALE = 100
        const val DEFAULT_COLOR = "blue"

        const val MIN_OPACITY = 5
        const val MAX_OPACITY = 100
        const val MIN_SCALE = 40
        const val MAX_SCALE = 220
        const val MAX_OFFSET = 40

        val Default = WatermarkStyle()
    }
}

/**
 * Stamps a picture with a repeating "FOR VERIFICATION PURPOSE ONLY" pattern. [draw] is the one
 * place the pattern is actually painted, shared by the live on-screen preview (drawn straight
 * onto the composable, nothing on disk touched) and [apply] (the baked-in copy staged for Copy
 * or Share) — so what you see before you copy or share is exactly what goes out.
 */
object Watermark {

    private const val LABEL = "FOR VERIFICATION PURPOSE ONLY"
    private const val ROWS = 7

    /** Sized against this, then scaled to the real thing — [Paint] needs some size to measure. */
    private const val MEASURE_SIZE = 100f

    /** The colours the stamp can be, in the order the picker offers them. */
    val colors: List<Pair<String, Int>> = listOf(
        "blue" to Color.rgb(33, 130, 255),
        "red" to Color.rgb(229, 57, 53),
        "green" to Color.rgb(46, 160, 67),
        "orange" to Color.rgb(245, 145, 32),
        "black" to Color.rgb(20, 20, 20),
        "white" to Color.rgb(255, 255, 255),
    )

    fun colorOf(key: String): Int =
        colors.firstOrNull { it.first == key }?.second ?: colors.first().second

    /**
     * Paints the pattern over the [width]x[height] box starting at [left], [top] — the box is
     * the picture itself, not whatever it is sitting in, so a letterboxed preview gets the
     * stamp on the photo rather than across the empty bars beside it.
     */
    fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        style: WatermarkStyle = WatermarkStyle.Default,
    ) {
        if (width <= 0f || height <= 0f) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            color = colorOf(style.colorKey)
            // Set after the colour: colour carries its own alpha and would overwrite this.
            alpha = style.opacity.coerceIn(0, 100) * 255 / 100
            textSize = MEASURE_SIZE
        }

        // The line is sized to span a share of the width whatever the picture's shape, rather
        // than a fixed fraction of it that ran off both edges of anything narrow.
        val measured = paint.measureText(LABEL)
        if (measured <= 0f) return
        val span = width * 0.82f *
            (style.scale.coerceIn(WatermarkStyle.MIN_SCALE, WatermarkStyle.MAX_SCALE) / 100f)
        paint.textSize = MEASURE_SIZE * (span / measured)

        val lineWidth = paint.measureText(LABEL)
        val centerX = left + width / 2f
        val centerY = top + height / 2f
        val shiftX = width * (style.offsetX.coerceIn(-WatermarkStyle.MAX_OFFSET, WatermarkStyle.MAX_OFFSET) / 100f)
        val shiftY = height * (style.offsetY.coerceIn(-WatermarkStyle.MAX_OFFSET, WatermarkStyle.MAX_OFFSET) / 100f)

        canvas.save()
        canvas.rotate(-30f, centerX, centerY)
        // Rows sit inside the box rather than starting off its top corner, so the pattern reads
        // as centred on the picture at every size.
        val step = height / (ROWS + 1)
        repeat(ROWS) { index ->
            canvas.drawText(
                LABEL,
                centerX - lineWidth / 2f + shiftX,
                top + step * (index + 1) + shiftY,
                paint,
            )
        }
        canvas.restore()
    }

    /** Returns watermarked bytes, or the original bytes unchanged if the image can't be read. */
    fun apply(
        bytes: ByteArray,
        mimeType: String,
        style: WatermarkStyle = WatermarkStyle.Default,
    ): ByteArray {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val marked = source.copy(Bitmap.Config.ARGB_8888, true) ?: return bytes
        draw(
            canvas = Canvas(marked),
            left = 0f,
            top = 0f,
            width = marked.width.toFloat(),
            height = marked.height.toFloat(),
            style = style,
        )

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
