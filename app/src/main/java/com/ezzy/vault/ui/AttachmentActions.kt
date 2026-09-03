package com.ezzy.vault.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.ezzy.vault.appContainer
import com.ezzy.vault.data.crypto.AttachmentStore
import com.ezzy.vault.security.SecureShare
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
        onResult: (Boolean) -> Unit = {},
    ) = perform(storedName, displayName, mimeType, onResult) { uri ->
        SecureShare.copy(context, uri, displayName)
    }

    fun share(
        storedName: String,
        displayName: String,
        mimeType: String,
        onResult: (Boolean) -> Unit = {},
    ) = perform(storedName, displayName, mimeType, onResult) { uri ->
        SecureShare.share(context, uri, mimeType)
    }

    fun open(
        storedName: String,
        displayName: String,
        mimeType: String,
        onResult: (Boolean) -> Unit = {},
    ) = perform(storedName, displayName, mimeType, onResult) { uri ->
        SecureShare.open(context, uri, mimeType)
    }

    private fun perform(
        storedName: String,
        displayName: String,
        mimeType: String,
        onResult: (Boolean) -> Unit,
        action: (android.net.Uri) -> Boolean,
    ) {
        scope.launch {
            val uri = SecureShare.stage(context, store, storedName, displayName, mimeType)
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
