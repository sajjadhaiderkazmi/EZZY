package com.ezzy.vault

import android.content.Intent
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.ui.LocalSettings
import com.ezzy.vault.ui.LocalSnackbar
import com.ezzy.vault.ui.nav.EzzyNavHost
import com.ezzy.vault.ui.screens.LockScreen
import com.ezzy.vault.ui.screens.WelcomeScreen
import com.ezzy.vault.ui.theme.EzzyTheme
import com.ezzy.vault.util.EzzySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)

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

    companion object {
        const val EXTRA_ROUTE = "com.ezzy.vault.extra.ROUTE"
    }
}
