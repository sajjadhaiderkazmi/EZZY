package com.ezzy.vault.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Which handle of the crop frame the finger grabbed. */
private enum class Grip { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY, NONE }

private const val HANDLE_TOUCH_DP = 34f
private const val MIN_CROP_DP = 56f

/**
 * A self-contained cropper. Everything happens on bitmaps EZZY already holds in memory, so a
 * photo never has to be handed to another app to be trimmed — which for a vault matters more
 * than the few lines a third-party cropper would have saved.
 */
@Composable
fun ImageCropDialog(
    storedName: String,
    onCancel: () -> Unit,
    onCropped: (ByteArray) -> Unit,
) {
    val source by rememberDecryptedBitmap(storedName)
    var working by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(source) { working = source }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            val bitmap = working
            if (bitmap == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                return@Surface
            }

            var containerSize by remember { mutableStateOf(Size.Zero) }
            // Reset the frame whenever the picture itself changes (a rotation makes a new one).
            var crop by remember(bitmap) { mutableStateOf<Rect?>(null) }
            var grip by remember { mutableStateOf(Grip.NONE) }
            var squareLock by remember { mutableStateOf(false) }

            val density = LocalDensity.current
            val handleTouch = with(density) { HANDLE_TOUCH_DP.dp.toPx() }
            val minSide = with(density) { MIN_CROP_DP.dp.toPx() }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                    Text(
                        text = "Crop",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            squareLock = !squareLock
                            crop = crop?.let { if (squareLock) it.toSquare(minSide) else it }
                        }
                    ) {
                        Icon(
                            imageVector = if (squareLock) Icons.Rounded.CropSquare else Icons.Rounded.CropFree,
                            contentDescription = if (squareLock) "Free size" else "Square",
                            tint = if (squareLock) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    IconButton(
                        onClick = {
                            working = bitmap.rotated(90f)
                            crop = null
                        }
                    ) {
                        Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged {
                            containerSize = Size(it.width.toFloat(), it.height.toFloat())
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Photo being cropped",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    val displayed = remember(bitmap, containerSize) {
                        fittedRect(bitmap.width, bitmap.height, containerSize)
                    }

                    LaunchedEffect(displayed) {
                        if (crop == null && displayed.width > 0f) crop = displayed.inset(0.08f)
                    }

                    val frame = crop
                    if (frame != null && displayed.width > 0f) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(displayed, squareLock) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            grip = crop?.gripAt(start, handleTouch) ?: Grip.NONE
                                        },
                                        onDragEnd = { grip = Grip.NONE },
                                        onDragCancel = { grip = Grip.NONE },
                                    ) { change, drag ->
                                        change.consume()
                                        val current = crop ?: return@detectDragGestures
                                        crop = current
                                            .resized(grip, drag, minSide, squareLock)
                                            .clampedInside(displayed)
                                    }
                                }
                        ) {
                            drawCropOverlay(frame)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { crop = null }) {
                        Text("Reset", color = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = !saving && crop != null,
                        onClick = {
                            val frame = crop ?: return@Button
                            val displayed = fittedRect(bitmap.width, bitmap.height, containerSize)
                            saving = true
                            scope.launch {
                                val bytes = cropToJpeg(bitmap, frame, displayed)
                                saving = false
                                if (bytes != null) onCropped(bytes) else onCancel()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (saving) "Saving…" else "Save crop")
                    }
                }
            }
        }
    }
}

// ---- Geometry ---------------------------------------------------------------

/** Where a Fit-scaled bitmap actually lands inside its container. */
private fun fittedRect(bitmapWidth: Int, bitmapHeight: Int, container: Size): Rect {
    if (container.width <= 0f || container.height <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) {
        return Rect(Offset.Zero, Size.Zero)
    }
    val scale = min(container.width / bitmapWidth, container.height / bitmapHeight)
    val width = bitmapWidth * scale
    val height = bitmapHeight * scale
    val left = (container.width - width) / 2f
    val top = (container.height - height) / 2f
    return Rect(Offset(left, top), Size(width, height))
}

private fun Rect.inset(fraction: Float): Rect {
    val dx = width * fraction
    val dy = height * fraction
    return Rect(left + dx, top + dy, right - dx, bottom - dy)
}

private fun Rect.toSquare(minSide: Float): Rect {
    val side = max(min(width, height), minSide)
    val cx = center.x
    val cy = center.y
    return Rect(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
}

private fun Rect.gripAt(point: Offset, touch: Float): Grip = when {
    (point - Offset(left, top)).getDistance() <= touch -> Grip.TOP_LEFT
    (point - Offset(right, top)).getDistance() <= touch -> Grip.TOP_RIGHT
    (point - Offset(left, bottom)).getDistance() <= touch -> Grip.BOTTOM_LEFT
    (point - Offset(right, bottom)).getDistance() <= touch -> Grip.BOTTOM_RIGHT
    contains(point) -> Grip.BODY
    else -> Grip.NONE
}

private fun Rect.resized(grip: Grip, drag: Offset, minSide: Float, square: Boolean): Rect {
    val moved = when (grip) {
        Grip.BODY -> Rect(left + drag.x, top + drag.y, right + drag.x, bottom + drag.y)
        Grip.TOP_LEFT -> Rect(left + drag.x, top + drag.y, right, bottom)
        Grip.TOP_RIGHT -> Rect(left, top + drag.y, right + drag.x, bottom)
        Grip.BOTTOM_LEFT -> Rect(left + drag.x, top, right, bottom + drag.y)
        Grip.BOTTOM_RIGHT -> Rect(left, top, right + drag.x, bottom + drag.y)
        Grip.NONE -> return this
    }
    if (grip == Grip.BODY) return moved

    // Keep the frame usable: never let a corner cross over or shrink below a thumb's width.
    val fixed = Rect(
        left = min(moved.left, moved.right - minSide),
        top = min(moved.top, moved.bottom - minSide),
        right = max(moved.right, moved.left + minSide),
        bottom = max(moved.bottom, moved.top + minSide),
    )
    if (!square) return fixed

    val side = max(fixed.width, fixed.height)
    return when (grip) {
        Grip.TOP_LEFT -> Rect(fixed.right - side, fixed.bottom - side, fixed.right, fixed.bottom)
        Grip.TOP_RIGHT -> Rect(fixed.left, fixed.bottom - side, fixed.left + side, fixed.bottom)
        Grip.BOTTOM_LEFT -> Rect(fixed.right - side, fixed.top, fixed.right, fixed.top + side)
        else -> Rect(fixed.left, fixed.top, fixed.left + side, fixed.top + side)
    }
}

private fun Rect.clampedInside(bounds: Rect): Rect {
    val width = min(width, bounds.width)
    val height = min(height, bounds.height)
    val left = this.left.coerceIn(bounds.left, bounds.right - width)
    val top = this.top.coerceIn(bounds.top, bounds.bottom - height)
    return Rect(left, top, left + width, top + height)
}

// ---- Drawing ----------------------------------------------------------------

private fun DrawScope.drawCropOverlay(frame: Rect) {
    val shade = Color.Black.copy(alpha = 0.55f)
    // Four bands around the frame, rather than a cut-out, which needs no layer save.
    drawRect(shade, size = Size(size.width, frame.top))
    drawRect(shade, topLeft = Offset(0f, frame.bottom), size = Size(size.width, size.height - frame.bottom))
    drawRect(shade, topLeft = Offset(0f, frame.top), size = Size(frame.left, frame.height))
    drawRect(
        shade,
        topLeft = Offset(frame.right, frame.top),
        size = Size(size.width - frame.right, frame.height),
    )

    drawRect(
        color = Color.White,
        topLeft = frame.topLeft,
        size = frame.size,
        style = Stroke(width = 2f),
    )

    // Thirds guides, then the corner grips.
    val guide = Color.White.copy(alpha = 0.35f)
    for (i in 1..2) {
        val x = frame.left + frame.width * i / 3f
        val y = frame.top + frame.height * i / 3f
        drawLine(guide, Offset(x, frame.top), Offset(x, frame.bottom), strokeWidth = 1f)
        drawLine(guide, Offset(frame.left, y), Offset(frame.right, y), strokeWidth = 1f)
    }

    val grip = 18f
    val thickness = 5f
    listOf(
        frame.topLeft to Offset(1f, 1f),
        Offset(frame.right, frame.top) to Offset(-1f, 1f),
        Offset(frame.left, frame.bottom) to Offset(1f, -1f),
        Offset(frame.right, frame.bottom) to Offset(-1f, -1f),
    ).forEach { (corner, direction) ->
        drawLine(Color.White, corner, corner + Offset(grip * direction.x, 0f), strokeWidth = thickness)
        drawLine(Color.White, corner, corner + Offset(0f, grip * direction.y), strokeWidth = thickness)
    }
}

// ---- Producing the cropped bytes --------------------------------------------

private fun Bitmap.rotated(degrees: Float): Bitmap = runCatching {
    Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)
}.getOrDefault(this)

/**
 * Maps the on-screen frame back onto source pixels and encodes the result. Runs off the main
 * thread because a full-resolution crop of a phone photo is not instant.
 */
private suspend fun cropToJpeg(bitmap: Bitmap, frame: Rect, displayed: Rect): ByteArray? {
    if (displayed.width <= 0f || displayed.height <= 0f) return null

    val scale = bitmap.width / displayed.width
    val x = ((frame.left - displayed.left) * scale).roundToInt().coerceIn(0, bitmap.width - 1)
    val y = ((frame.top - displayed.top) * scale).roundToInt().coerceIn(0, bitmap.height - 1)
    val width = (frame.width * scale).roundToInt().coerceIn(1, bitmap.width - x)
    val height = (frame.height * scale).roundToInt().coerceIn(1, bitmap.height - y)

    return withContext(Dispatchers.Default) {
        runCatching {
            val cropped = Bitmap.createBitmap(bitmap, x, y, width, height)
            ByteArrayOutputStream().use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.toByteArray()
            }
        }.getOrNull()
    }
}
