package com.ezzy.vault.data.crypto

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Stores photos, scans and PDFs inside the app's private directory, each file sealed with a
 * Keystore key. Nothing readable ever touches shared storage.
 */
class AttachmentStore(context: Context) {

    private val appContext = context.applicationContext
    private val crypto = KeystoreCrypto(KEY_ALIAS)
    private val dir: File
        get() = File(appContext.filesDir, "attachments").apply { if (!exists()) mkdirs() }

    /** Copies the content behind [uri] into the vault. Returns the stored name and byte size. */
    suspend fun import(uri: Uri): StoredFile? = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        if (bytes.size > MAX_BYTES) return@withContext null

        val storedName = "${UUID.randomUUID()}.bin"
        runCatching { File(dir, storedName).writeBytes(crypto.encrypt(bytes)) }
            .getOrElse { return@withContext null }
        StoredFile(storedName, bytes.size.toLong())
    }

    suspend fun read(storedName: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(dir, storedName)
        if (!file.exists()) return@withContext null
        runCatching { crypto.decrypt(file.readBytes()) }.getOrNull()
    }

    suspend fun write(storedName: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(dir, storedName).writeBytes(crypto.encrypt(bytes)) }.isSuccess
    }

    suspend fun delete(storedName: String) = withContext(Dispatchers.IO) {
        runCatching { File(dir, storedName).delete() }
        Unit
    }

    /** Removes sealed files that no row points at any more. */
    suspend fun pruneOrphans(keep: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
        Unit
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    data class StoredFile(val storedName: String, val sizeBytes: Long)

    private companion object {
        const val KEY_ALIAS = "ezzy_file_key_v1"
        const val MAX_BYTES = 25 * 1024 * 1024
    }
}
