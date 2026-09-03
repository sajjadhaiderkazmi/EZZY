package com.ezzy.vault.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.withResumed
import com.ezzy.vault.AppContainer
import com.ezzy.vault.overlay.OverlayService
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.ui.LocalSettings
import com.ezzy.vault.ui.LocalSnackbar
import com.ezzy.vault.ui.components.SectionHeader
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.util.EdgeSide
import com.ezzy.vault.util.ThemeMode
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val store = container.settings

    fun setOverlayEnabled(value: Boolean) = launch { store.setOverlayEnabled(value) }
    fun setBubble(value: Boolean) = launch { store.setBubbleTrigger(value) }
    fun setEdge(value: Boolean) = launch { store.setEdgeTrigger(value) }
    fun setEdgeSide(value: EdgeSide) = launch { store.setEdgeSide(value) }
    fun setTwoFinger(value: Boolean) = launch { store.setTwoFingerOnly(value) }
    fun setBiometric(value: Boolean) = launch { store.setBiometricLock(value) }
    fun setAutoLock(minutes: Int) = launch { store.setAutoLockMinutes(minutes) }
    fun setClipboardClear(seconds: Int) = launch { store.setClipboardClearSeconds(seconds) }
    fun setMaskSecrets(value: Boolean) = launch { store.setMaskSecrets(value) }
    fun setBlockScreenshots(value: Boolean) = launch { store.setBlockScreenshots(value) }
    fun setThemeMode(value: ThemeMode) = launch { store.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = launch { store.setDynamicColor(value) }

    fun eraseEverything(onDone: () -> Unit) = launch {
        container.eraseEverything()
        AppLock.lock()
        onDone()
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
) {
    val viewModel: SettingsViewModel = ezzyViewModel { SettingsViewModel(it) }
    val settings = LocalSettings.current
    val context = LocalContext.current

    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var confirmErase by remember { mutableStateOf(false) }
    // Set once the user has been sent to the permission screen. Some OEM skins keep reporting
    // false afterwards even though the permission was granted, so the next tap goes ahead and
    // lets the service find out for real.
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    // The result callback runs before the activity is resumed, and starting a foreground
    // service in that window is refused, so the start is deferred to the next resume.
    var startWhenResumed by remember { mutableStateOf(false) }

    fun turnOn() {
        viewModel.setOverlayEnabled(true)
        val failure = OverlayService.start(context)
        if (failure != null) {
            scope.launch { snackbar.showSnackbar("Could not start the bar: $failure") }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(startWhenResumed) {
        if (!startWhenResumed) return@LaunchedEffect
        lifecycleOwner.withResumed { }
        startWhenResumed = false
        canDrawOverlays = Settings.canDrawOverlays(context)
        turnOn()
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        if (canDrawOverlays) {
            startWhenResumed = true
        } else {
            scope.launch {
                snackbar.showSnackbar(
                    "If you already allowed \"Display over other apps\", tap the switch once more."
                )
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The bar still works without it; only the ongoing notice is hidden. */ }

    fun enableOverlay() {
        if (!Settings.canDrawOverlays(context) && !permissionAsked) {
            permissionAsked = true
            // MIUI and a few other skins do not handle this intent with a package URI, and an
            // unhandled launch would take the whole app down, so every step falls back.
            val opened = listOf(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            ).any { intent -> runCatching { overlayPermissionLauncher.launch(intent) }.isSuccess }

            if (!opened) {
                scope.launch {
                    snackbar.showSnackbar(
                        "Could not open the permission screen. Allow \"Display over other apps\" " +
                            "for EZZY in your phone's settings, then tap the switch again."
                    )
                }
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Only decides whether the ongoing notice is visible — never block the bar on it.
            runCatching { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        turnOn()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { SettingsGroup("Floating bar") }

            item {
                SwitchRow(
                    title = "Floating bar",
                    subtitle = if (canDrawOverlays) {
                        "Reach your vault from inside any app"
                    } else {
                        "Needs the \"display over other apps\" permission"
                    },
                    checked = settings.overlayEnabled,
                    onCheckedChange = { wanted ->
                        if (wanted) {
                            enableOverlay()
                        } else {
                            viewModel.setOverlayEnabled(false)
                            OverlayService.stop(context)
                        }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Floating button",
                    subtitle = "A small draggable button, always on screen. The most reliable way in.",
                    checked = settings.bubbleTrigger,
                    enabled = settings.overlayEnabled,
                    onCheckedChange = {
                        viewModel.setBubble(it)
                        if (settings.overlayEnabled) OverlayService.start(context)
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Edge swipe",
                    subtitle = "Swipe up from an invisible strip on the chosen edge.",
                    checked = settings.edgeTrigger,
                    enabled = settings.overlayEnabled,
                    onCheckedChange = {
                        viewModel.setEdge(it)
                        if (settings.overlayEnabled) OverlayService.start(context)
                    },
                )
            }

            item {
                ChoiceRow(
                    title = "Swipe from",
                    current = when (settings.edgeSide) {
                        EdgeSide.LEFT -> "Left edge"
                        EdgeSide.RIGHT -> "Right edge"
                        EdgeSide.BOTTOM -> "Bottom (above the nav bar)"
                    },
                    enabled = settings.overlayEnabled && settings.edgeTrigger,
                    options = listOf(
                        "Bottom (above the nav bar)" to EdgeSide.BOTTOM,
                        "Right edge" to EdgeSide.RIGHT,
                        "Left edge" to EdgeSide.LEFT,
                    ),
                    onSelect = {
                        viewModel.setEdgeSide(it)
                        if (settings.overlayEnabled) OverlayService.start(context)
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Two fingers only",
                    subtitle = "Requires a two-finger swipe, so a stray one-finger swipe never opens the bar.",
                    checked = settings.twoFingerOnly,
                    enabled = settings.overlayEnabled && settings.edgeTrigger,
                    onCheckedChange = {
                        viewModel.setTwoFinger(it)
                        if (settings.overlayEnabled) OverlayService.start(context)
                    },
                )
            }

            item {
                InfoNote(
                    "Some phones (Xiaomi, Oppo, Vivo, Realme) stop background services on their own. " +
                        "If the bar disappears, allow EZZY to autostart and turn off battery optimisation for it."
                )
            }

            item { SettingsGroup("Security") }

            item {
                SwitchRow(
                    title = "Lock with fingerprint",
                    subtitle = "Ask for your fingerprint, face or PIN before opening the vault.",
                    checked = settings.biometricLock,
                    onCheckedChange = viewModel::setBiometric,
                )
            }

            item {
                ChoiceRow(
                    title = "Lock again after",
                    current = autoLockLabel(settings.autoLockMinutes),
                    enabled = settings.biometricLock,
                    options = listOf(
                        "Immediately" to 0,
                        "1 minute" to 1,
                        "5 minutes" to 5,
                        "15 minutes" to 15,
                        "Never" to -1,
                    ),
                    onSelect = viewModel::setAutoLock,
                )
            }

            item {
                ChoiceRow(
                    title = "Clear clipboard after",
                    current = clipboardLabel(settings.clipboardClearSeconds),
                    options = listOf(
                        "15 seconds" to 15,
                        "30 seconds" to 30,
                        "45 seconds" to 45,
                        "1 minute" to 60,
                        "2 minutes" to 120,
                        "Never" to 0,
                    ),
                    onSelect = viewModel::setClipboardClear,
                )
            }

            item {
                SwitchRow(
                    title = "Hide secret values",
                    subtitle = "Account numbers and passwords stay masked until you tap the eye.",
                    checked = settings.maskSecrets,
                    onCheckedChange = viewModel::setMaskSecrets,
                )
            }

            item {
                SwitchRow(
                    title = "Block screenshots",
                    subtitle = "Stops screenshots and screen recording while EZZY is on screen.",
                    checked = settings.blockScreenshots,
                    onCheckedChange = viewModel::setBlockScreenshots,
                )
            }

            item { SettingsGroup("Appearance") }

            item {
                ChoiceRow(
                    title = "Theme",
                    current = when (settings.themeMode) {
                        ThemeMode.SYSTEM -> "Follow system"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    },
                    options = listOf(
                        "Follow system" to ThemeMode.SYSTEM,
                        "Light" to ThemeMode.LIGHT,
                        "Dark" to ThemeMode.DARK,
                    ),
                    onSelect = viewModel::setThemeMode,
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(
                        title = "Use wallpaper colours",
                        subtitle = "Match Android's Material You palette instead of EZZY's own.",
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }

            item { SettingsGroup("Data") }

            item {
                NavigationRow(
                    title = "Entry types",
                    subtitle = "Edit which fields each kind of entry asks for",
                    onClick = onOpenTemplates,
                )
            }

            item {
                NavigationRow(
                    title = "Erase everything",
                    subtitle = "Delete all entries, files and the encryption key",
                    destructive = true,
                    onClick = { confirmErase = true },
                )
            }

            item {
                InfoNote(
                    "Everything stays on this phone. EZZY has no account, no cloud and no internet " +
                        "permission — your entries are encrypted with a key that never leaves this device."
                )
            }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase everything?") },
            text = {
                Text(
                    "Every entry, file and section will be deleted and the encryption key destroyed. " +
                        "There is no backup and no way to undo this."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmErase = false
                        viewModel.eraseEverything {
                            OverlayService.stop(context)
                            onBack()
                        }
                    }
                ) {
                    Text("Erase", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) { Text("Cancel") }
            },
        )
    }
}

// ---- Rows -------------------------------------------------------------------

@Composable
private fun SettingsGroup(title: String) {
    SectionHeader(text = title, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    current: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .clickable(enabled = enabled) { open = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelect(value)
                            open = false
                        },
                        trailingIcon = {
                            if (label == current) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InfoNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

private fun autoLockLabel(minutes: Int): String = when (minutes) {
    0 -> "Immediately"
    1 -> "1 minute"
    -1 -> "Never"
    else -> "$minutes minutes"
}

private fun clipboardLabel(seconds: Int): String = when (seconds) {
    0 -> "Never"
    60 -> "1 minute"
    120 -> "2 minutes"
    else -> "$seconds seconds"
}
