package com.ezzy.vault.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.ezzy.vault.appContainer
import com.ezzy.vault.data.crypto.AttachmentStore
import com.ezzy.vault.data.db.AttachmentEntity
import com.ezzy.vault.data.db.watermarkStyle
import com.ezzy.vault.security.SecureShare
import com.ezzy.vault.util.WatermarkStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What Copy, Share and Open look like for a stored file, from any screen and from the floating
 * bar alike. Each one decrypts the file into EZZY's staging folder first, so the work is
 * asynchronous and the result comes back through [onResult] rather than a return value.
 */
class AttachmentActions internal constructor(
    private val context: Context,
    private val store: AttachmentStore,
    private val scope: CoroutineScope,
) {

    fun copy(
        storedName: String,
        displayName: String,
        mimeType: String,
        watermark: WatermarkStyle? = null,
        onResult: (Boolean) -> Unit = {},
    ) = perform(storedName, displayName, mimeType, watermark, onResult) { uri ->
        SecureShare.copy(context, uri, displayName)
    }

    fun share(
        storedName: String,
        displayName: String,
        mimeType: String,
        watermark: WatermarkStyle? = null,
        onResult: (Boolean) -> Unit = {},
    ) = perform(storedName, displayName, mimeType, watermark, onResult) { uri ->
        SecureShare.share(context, uri, mimeType)
    }

    fun open(
        storedName: String,
        displayName: String,
        mimeType: String,
        onResult: (Boolean) -> Unit = {},
    ) = perform(storedName, displayName, mimeType, watermark = null, onResult = onResult) { uri ->
        SecureShare.open(context, uri, mimeType)
    }

    /** Stages every file in [files] — each with its own watermark setting — and puts all of
     *  them on the clipboard together, the multi-select "Copy". */
    fun copyMultiple(files: List<AttachmentEntity>, onResult: (Boolean) -> Unit = {}) {
        if (files.isEmpty()) {
            onResult(false)
            return
        }
        scope.launch {
            val uris = files.mapNotNull { file ->
                SecureShare.stage(
                    context = context,
                    store = store,
                    storedName = file.storedName,
                    displayName = file.caption.ifBlank { file.displayName },
                    mimeType = file.mimeType,
                    watermark = file.watermarkStyle.takeIf { file.watermark },
                )
            }
            onResult(uris.isNotEmpty() && SecureShare.copyMultiple(context, uris, "EZZY files"))
        }
    }

    private fun perform(
        storedName: String,
        displayName: String,
        mimeType: String,
        watermark: WatermarkStyle?,
        onResult: (Boolean) -> Unit,
        action: (android.net.Uri) -> Boolean,
    ) {
        scope.launch {
            val uri = SecureShare.stage(context, store, storedName, displayName, mimeType, watermark)
            onResult(uri != null && action(uri))
        }
    }
}

@Composable
fun rememberAttachmentActions(): AttachmentActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { context.appContainer.attachmentStore }
    return remember(context, store, scope) { AttachmentActions(context, store, scope) }
}
