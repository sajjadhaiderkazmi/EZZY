package com.ezzy.vault

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.security.SecureClipboard
import com.ezzy.vault.ui.LocalSettings
import com.ezzy.vault.ui.LocalSnackbar
import com.ezzy.vault.ui.screens.LockScreen
import com.ezzy.vault.ui.screens.PickValueScreen
import com.ezzy.vault.ui.theme.EzzyTheme
import com.ezzy.vault.util.EzzySettings

/**
 * Puts EZZY into Android's own text selection menu.
 *
 * Long-pressing any text field anywhere — WhatsApp, a browser, Messages — shows Cut, Copy,
 * Paste and whatever apps have registered for [Intent.ACTION_PROCESS_TEXT]; EZZY is one of
 * them. Picking a value here hands it straight back to the field the user was typing in, which
 * beats copying and pasting. When the text is read-only there is nothing to hand back, so the
 * value goes to the clipboard instead.
 */
class ProcessTextActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val readOnly = intent?.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false) ?: true

        setContent {
            val settings by appContainer.settings.settings
                .collectAsStateWithLifecycle(initialValue = EzzySettings())
            val unlocked by AppLock.unlocked.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            var authError by remember { mutableStateOf<String?>(null) }

            EzzyTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(
                    LocalSettings provides settings,
                    LocalSnackbar provides snackbarHostState,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (settings.biometricLock && !unlocked) {
                            LockScreen(
                                error = authError,
                                onUnlock = {
                                    authError = null
                                    AppLock.prompt(
                                        activity = this@ProcessTextActivity,
                                        title = "Unlock EZZY",
                                        subtitle = "Confirm it is you to insert a saved value",
                                        onSuccess = { AppLock.unlock() },
                                        onFailure = { authError = it },
                                    )
                                },
                            )
                        } else {
                            PickValueScreen(
                                onPick = { label, value, sensitive ->
                                    deliver(
                                        label = label,
                                        value = value,
                                        sensitive = sensitive,
                                        readOnly = readOnly,
                                        clearAfterSeconds = settings.clipboardClearSeconds,
                                    )
                                },
                                onClose = {
                                    setResult(RESULT_CANCELED)
                                    finish()
                                },
                            )
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    private fun deliver(
        label: String,
        value: String,
        sensitive: Boolean,
        readOnly: Boolean,
        clearAfterSeconds: Int,
    ) {
        if (value.isBlank()) return

        if (readOnly) {
            SecureClipboard.copy(
                context = this,
                label = label,
                value = value,
                sensitive = sensitive,
                clearAfterSeconds = clearAfterSeconds,
            )
            setResult(RESULT_CANCELED)
        } else {
            // Returning the text replaces the selection in the field the user came from.
            setResult(
                RESULT_OK,
                Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, value),
            )
        }
        finish()
    }
}
