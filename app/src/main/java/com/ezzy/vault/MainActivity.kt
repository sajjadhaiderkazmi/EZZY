package com.ezzy.vault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.ezzy.vault.data.model.Seed
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.ui.LocalSettings
import com.ezzy.vault.ui.LocalSnackbar
import com.ezzy.vault.ui.nav.EzzyNavHost
import com.ezzy.vault.ui.nav.Routes
import com.ezzy.vault.ui.screens.LockScreen
import com.ezzy.vault.ui.screens.ShareTargetSheet
import com.ezzy.vault.ui.screens.WelcomeScreen
import com.ezzy.vault.ui.theme.EzzyTheme
import com.ezzy.vault.util.EzzySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)

    // Set only when the intent that (re)started this activity was Android's "Share" sheet
    // handing over a picture — checked once here rather than read from `intent` directly in
    // the composable below, since that would re-run on every unrelated recomposition. The
    // token is what makes each share its own: MainActivity is launchMode="singleTask" and
    // never recreates between two shares, and sharing the very same picture twice in a row
    // (share, cancel, share again) would otherwise hand the second attempt the first one's
    // already-imported-then-discarded ViewModel and its now-deleted files.
    private var pendingShare by mutableStateOf<PendingShare?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)
        pendingShare = sharedImageUris(intent)?.let { PendingShare(it) }

        setContent {
            // Null until the first DataStore read lands. Without that distinction a fresh read
            // of "not onboarded yet" is indistinguishable from the defaults, and the welcome
            // screen would flash on every cold start.
            // Flow is covariant, so the upcast is all it takes to make `null` a legal initial
            // value here.
            val settingsFlow: Flow<EzzySettings?> = appContainer.settings.settings
            val stored by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val settings = stored ?: EzzySettings()
            val unlocked by AppLock.unlocked.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            var authError by remember { mutableStateOf<String?>(null) }

            // Screenshot blocking is a window flag, so it has to follow the setting live.
            LaunchedEffect(settings.blockScreenshots) {
                if (settings.blockScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            EzzyTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(
                    LocalSettings provides settings,
                    LocalSnackbar provides snackbarHostState,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            // Settings not read back yet: hold on a plain themed frame rather
                            // than showing a screen that the next frame would replace.
                            stored == null -> Unit

                            !settings.onboarded -> WelcomeScreen(
                                onContinue = { name ->
                                    lifecycleScope.launch {
                                        appContainer.settings.completeOnboarding(name)
                                    }
                                },
                            )

                            settings.biometricLock && !unlocked -> LockScreen(
                                error = authError,
                                onUnlock = {
                                    authError = null
                                    AppLock.prompt(
                                        activity = this@MainActivity,
                                        title = "Unlock EZZY",
                                        subtitle = "Your vault is encrypted on this device",
                                        onSuccess = { AppLock.unlock() },
                                        onFailure = { authError = it },
                                    )
                                },
                            )

                            else -> {
                                val navController = rememberNavController()

                                LaunchedEffect(pendingRoute) {
                                    pendingRoute?.let {
                                        navController.navigate(it)
                                        pendingRoute = null
                                    }
                                }

                                EzzyNavHost(navController = navController, settings = settings)

                                pendingShare?.let { share ->
                                    ShareTargetSheet(
                                        uris = share.uris,
                                        viewModelKey = share.token.toString(),
                                        onDismiss = { pendingShare = null },
                                        onCreateNew = {
                                            pendingShare = null
                                            navController.navigate(
                                                Routes.editor(
                                                    categoryId = Seed.SHARE_TARGET_CATEGORY_ID,
                                                    templateId = Seed.SHARE_TARGET_TEMPLATE_ID,
                                                )
                                            )
                                        },
                                        onAddedToExisting = { itemId, title ->
                                            pendingShare = null
                                            navController.navigate(Routes.item(itemId))
                                            lifecycleScope.launch {
                                                snackbarHostState.showSnackbar("Added to $title")
                                            }
                                        },
                                    )
                                }
                            }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(EXTRA_ROUTE)
        pendingShare = sharedImageUris(intent)?.let { PendingShare(it) }
    }

    override fun onStart() {
        super.onStart()
        // Re-lock if the app was away longer than the grace period the user chose.
        lifecycleScope.launch {
            val minutes = appContainer.settings.settings.first().autoLockMinutes
            val grace = if (minutes < 0) Long.MAX_VALUE else minutes * 60_000L
            AppLock.onForegrounded(grace)
        }
    }

    /** [uris] plus a value unique to this one share, even if the exact same picture is shared
     *  again a moment later. */
    private data class PendingShare(val uris: List<Uri>, val token: Long = System.nanoTime())

    companion object {
        const val EXTRA_ROUTE = "com.ezzy.vault.extra.ROUTE"

        /** Pulls the picture(s) out of an incoming "Share" intent, or null if this isn't one. */
        private fun sharedImageUris(intent: Intent?): List<Uri>? = when (intent?.action) {
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { listOf(it) }

            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.takeIf { it.isNotEmpty() }

            else -> null
        }
    }
}
