package com.ezzy.vault.security

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import com.ezzy.vault.data.crypto.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copying and sharing a stored picture or PDF.
 *
 * A vault file is encrypted on disk and no other app can read it, which is the point — but it
 * also means a picture cannot simply be handed to WhatsApp. So a file the user explicitly taps
 * Copy or Share on is decrypted into one staging folder inside EZZY's own cache and passed out
 * as a content:// URI. The receiving app gets a one-off read grant for that single file and
 * nothing else; the vault is never opened up.
 *
 * The staged copy is plain bytes for as long as it sits there, so [clear] wipes the folder on
 * every app start and whenever the vault is erased.
 */
object SecureShare {

    private const val DIR = "shared"

    /** Decrypts one stored file into the staging folder and returns a grantable URI for it. */
    suspend fun stage(
        context: Context,
        store: AttachmentStore,
        storedName: String,
        displayName: String,
        mimeType: String,
    ): Uri? = withContext(Dispatchers.IO) {
        val bytes = store.read(storedName) ?: return@withContext null
        val dir = File(context.cacheDir, DIR)
        if (!dir.exists() && !dir.mkdirs()) return@withContext null

        val file = File(dir, fileName(displayName, mimeType))
        runCatching { file.writeBytes(bytes) }.getOrElse { return@withContext null }

        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
    }

    /**
     * Puts the file itself on the clipboard, so a long-press and Paste in another app inserts
     * the picture rather than a path. The clipboard service issues the read grant to whichever
     * app pastes it.
     */
    fun copy(context: Context, uri: Uri, label: String): Boolean {
        val manager = context.applicationContext.getSystemService<ClipboardManager>()
            ?: return false
        return runCatching {
            manager.setPrimaryClip(ClipData.newUri(context.contentResolver, label, uri))
            true
        }.getOrDefault(false)
    }

    /** Opens the system share sheet for one file. */
    fun share(context: Context, uri: Uri, mimeType: String): Boolean =
        launchChooser(
            context = context,
            intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
            },
            title = "Share",
        )

    /** Opens the file in whatever app on the phone handles that type — a PDF reader, usually. */
    fun open(context: Context, uri: Uri, mimeType: String): Boolean =
        launchChooser(
            context = context,
            intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mimeType) },
            title = "Open with",
        )

    private fun launchChooser(context: Context, intent: Intent, title: String): Boolean {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // The floating bar is a service, not an activity, and a service can only start an
            // activity in a task of its own.
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    /** Deletes every staged copy. Safe at any time — the originals are untouched. */
    fun clear(context: Context) {
        runCatching { File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() } }
    }

    /**
     * Receiving apps read the extension, not the MIME type, so the staged copy is named after
     * the caption with a real extension appended.
     */
    private fun fileName(displayName: String, mimeType: String): String {
        val base = displayName
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .trim()
            .take(40)
            .ifBlank { "ezzy" }
        return "$base.${extensionFor(mimeType)}"
    }

    private fun extensionFor(mimeType: String): String = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "application/pdf" -> "pdf"
        else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: mimeType.substringAfterLast('/').takeIf { it.isNotBlank() && it.length <= 5 }
            ?: "bin"
    }
}
