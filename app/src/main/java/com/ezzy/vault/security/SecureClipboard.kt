package com.ezzy.vault.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copying a bank number is the whole point of this app, but leaving it on the clipboard is the
 * one thing that leaks it to every other app. So every copy is marked sensitive and wiped again
 * after a short window.
 */
object SecureClipboard {

    private val scope = CoroutineScope(SupervisorJob())
    private var clearJob: Job? = null
    private var lastCopied: String? = null

    /** @return true when the value actually reached the clipboard. */
    fun copy(
        context: Context,
        label: String,
        value: String,
        sensitive: Boolean,
        clearAfterSeconds: Int,
    ): Boolean {
        val manager = context.applicationContext.getSystemService<ClipboardManager>() ?: return false
        val clip = ClipData.newPlainText(label, value).apply {
            if (sensitive) {
                // Hides the value from the Android 13+ copy confirmation popup. The literal is
                // used instead of ClipDescription.EXTRA_IS_SENSITIVE so this also compiles and
                // behaves on the older platforms EZZY still supports.
                description.extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }
        return runCatching {
            manager.setPrimaryClip(clip)
            lastCopied = value
            scheduleClear(manager, clearAfterSeconds)
            true
        }.getOrDefault(false)
    }

    private fun scheduleClear(manager: ClipboardManager, seconds: Int) {
        clearJob?.cancel()
        if (seconds <= 0) return
        clearJob = scope.launch {
            delay(seconds * 1000L)
            clearIfUnchanged(manager)
        }
    }

    /** Only wipes what we put there — never someone else's clipboard content. */
    private fun clearIfUnchanged(manager: ClipboardManager) {
        val current = runCatching {
            manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        }.getOrNull()
        if (current != null && current != lastCopied) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        lastCopied = null
    }
}
