package com.ezzy.vault.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ezzy.vault.AppContainer
import com.ezzy.vault.appContainer
import com.ezzy.vault.security.SecureClipboard
import com.ezzy.vault.util.EzzySettings
import kotlinx.coroutines.launch

val LocalSettings = compositionLocalOf { EzzySettings() }

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

/**
 * View models here take the [AppContainer] directly rather than going through a DI graph, so
 * this is the one place that knows how to hand it over.
 */
@Composable
inline fun <reified VM : ViewModel> ezzyViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.appContainer
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container) }
        },
    )
}

/** What copying a value looks like from any screen: clipboard write plus confirmation. */
fun interface Copier {
    operator fun invoke(label: String, value: String, sensitive: Boolean)
}

@Composable
fun rememberCopier(): Copier {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    return remember(settings.clipboardClearSeconds, snackbar) {
        Copier { label, value, sensitive ->
            if (value.isBlank()) return@Copier
            val ok = SecureClipboard.copy(
                context = context,
                label = label,
                value = value,
                sensitive = sensitive,
                clearAfterSeconds = settings.clipboardClearSeconds,
            )
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(
                    message = when {
                        !ok -> "Could not copy $label"
                        settings.clipboardClearSeconds > 0 ->
                            "$label copied · clears in ${settings.clipboardClearSeconds}s"

                        else -> "$label copied"
                    },
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
}
