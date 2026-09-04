package com.ezzy.vault.data.backup

import android.content.Context
import android.net.Uri
import com.ezzy.vault.data.repo.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ties the vault's own data together with [BackupCrypto] and the file the user picked, so the
 * UI only ever deals in a Uri, a password and a progress callback.
 */
class BackupManager(private val repository: VaultRepository) {

    /**
     * Builds the export, encrypts it, and writes it to wherever the user chose to save it.
     * [onProgress] runs from 0f (nothing read yet) to 1f (the file is written).
     */
    suspend fun export(
        context: Context,
        uri: Uri,
        password: String,
        onProgress: suspend (Float) -> Unit,
    ) {
        // Reading every attachment back to plain bytes is the slow part, so it gets the first
        // 90% of the bar; encoding and encrypting a few hundred KB of JSON is fast enough that
        // the last 10% is really just "writing the file" from the user's point of view.
        val snapshot = repository.exportSnapshot { onProgress(it * 0.9f) }
        val json = BackupJson.encodeToString(BackupFile.serializer(), snapshot)
        val sealed = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), password)

        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("Could not open the chosen location")
            stream.use { it.write(sealed) }
        }
        onProgress(1f)
    }

    /** Reads the picked file's raw bytes, so the header can be checked before asking for a password. */
    suspend fun readFile(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Could not read the chosen file")
        stream.use { it.readBytes() }
    }

    /**
     * Decrypts and applies a backup already read into memory.
     *
     * @throws BackupCrypto.WrongPasswordException the password does not open the file
     * @throws BackupCrypto.CorruptDataException the password worked but the contents were not
     *   a genuine backup (a truncated or hand-edited file, most likely)
     */
    suspend fun import(
        fileBytes: ByteArray,
        password: String,
        onProgress: suspend (Float) -> Unit,
    ): VaultRepository.ImportResult {
        val plain = BackupCrypto.decrypt(fileBytes, password)
        val backup = try {
            BackupJson.decodeFromString(BackupFile.serializer(), String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupCrypto.CorruptDataException()
        }
        return repository.importSnapshot(backup, onProgress)
    }
}
