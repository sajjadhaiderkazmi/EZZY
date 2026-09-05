package com.ezzy.vault.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ezzy.vault.appContainer
import com.ezzy.vault.util.Watermark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_DIMENSION = 1600

/**
 * Attachments are AES-sealed on disk, so they cannot be handed to a normal image loader by
 * path. This decrypts in memory and downsamples before decoding, which keeps a 12 MP phone
 * photo from blowing the bitmap budget.
 */
@Composable
fun rememberDecryptedBitmap(storedName: String?): State<Bitmap?> {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, storedName) {
        val name = storedName
        if (name == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val bytes = context.appContainer.repository.attachmentBytes(name)
                ?: return@withContext null
            runCatching { decodeSampled(bytes) }.getOrNull()
        }
    }
}

private fun decodeSampled(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (largest > 0 && largest / sample > MAX_DIMENSION) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/**
 * Renders a sealed attachment, with a spinner while it decrypts and a fallback if it cannot.
 * [watermark] draws the same "FOR VERIFICATION PURPOSE ONLY" pattern [Watermark] stamps onto a
 * Copy or Share, straight onto this composable — so the picture already shows it wherever it's
 * looked at, not only in the file that leaves the vault. Nothing is written to the decoded
 * bitmap itself, so this costs nothing beyond one extra draw pass.
 */
@Composable
fun EncryptedImage(
    storedName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    watermark: Boolean = false,
) {
    val bitmap by rememberDecryptedBitmap(storedName)
    val current = bitmap
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            current != null -> Image(
                bitmap = current.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (watermark) {
                            Modifier.drawWithContent {
                                drawContent()
                                drawIntoCanvas { canvas ->
                                    Watermark.draw(
                                        canvas.nativeCanvas,
                                        size.width.toInt(),
                                        size.height.toInt(),
                                    )
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentScale = contentScale,
            )

            storedName == null -> Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}
