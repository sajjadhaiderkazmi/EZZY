package com.ezzy.vault.ui.screens

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import com.ezzy.vault.R
import com.ezzy.vault.overlay.EzzyTileService
import com.ezzy.vault.overlay.OverlayService
import com.ezzy.vault.ui.LocalSettings
import com.ezzy.vault.ui.LocalSnackbar
import com.ezzy.vault.ui.components.ChoiceRow
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.components.NavigationRow
import com.ezzy.vault.ui.components.SettingsGroup
import com.ezzy.vault.ui.components.SettingsPage
import com.ezzy.vault.ui.components.SwitchRow
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.util.AutoHide
import com.ezzy.vault.util.ThemeMode
import com.ezzy.vault.util.TriggerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- Floating bar -----------------------------------------------------------

@Composable
fun FloatingBarSettingsScreen(onBack: () -> Unit, onOpenBarSections: () -> Unit) {
    val viewModel: SettingsViewModel = ezzyViewModel { SettingsViewModel(it) }
    val settings = LocalSettings.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    // Set once the user has been sent to the permission screen. Some OEM skins keep reporting
    // false afterwards even though the permission was granted, so the next tap goes ahead and
    // lets the service find out for real.
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    // The result callback runs before the activity is resumed, and starting a foreground
    // service in that window is refused, so the start is deferred to the next resume.
    var startWhenResumed by remember { mutableStateOf(false) }
    var askMode by remember { mutableStateOf(false) }

    fun turnOn() {
        if (!settings.triggerModeChosen) askMode = true
        // The write has to land before the service starts and reads it back — starting it
        // synchronously right after firing the write off could still read the old value.
        scope.launch {
            viewModel.setOverlayEnabled(true).join()
            val failure = OverlayService.start(context)
            if (failure != null) {
                snackbar.showSnackbar("Could not start the bar: $failure")
            }
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

    SettingsPage(title = "Floating bar", onBack = onBack) {
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
            ChoiceRow(
                title = "Mode",
                subtitle = settings.triggerMode.hint,
                current = settings.triggerMode.label,
                enabled = settings.overlayEnabled,
                options = TriggerMode.entries.map { it.label to it },
                onSelect = { mode ->
                    // Same ordering as turnOn(): the write must land before the service
                    // restarts and reads it, or it can still pick up the old mode.
                    scope.launch {
                        viewModel.setTriggerMode(mode).join()
                        if (settings.overlayEnabled) OverlayService.start(context)
                    }
                },
            )
        }

        // Both of these are about a button that comes and goes. In Always active mode it
        // never does — it is simply there — so neither row has anything to say.
        if (settings.triggerMode == TriggerMode.ON_TRIGGER) {
            item {
                ChoiceRow(
                    title = "Hide the bar",
                    current = settings.autoHide.label,
                    enabled = settings.overlayEnabled,
                    options = AutoHide.entries.map { it.label to it },
                    onSelect = viewModel::setAutoHide,
                )
            }

            item {
                NavigationRow(
                    title = "Add Quick Settings tile",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "Puts EZZY next to Wi-Fi and Bluetooth"
                    } else {
                        "Open the shade, tap the pencil, then drag EZZY in"
                    },
                    onClick = { requestQuickSettingsTile(context, scope, snackbar) },
                )
            }
        }

        item {
            NavigationRow(
                title = "Sections in the bar",
                subtitle = if (settings.hiddenBarSections.isEmpty()) {
                    "All of them"
                } else {
                    "${settings.hiddenBarSections.size} hidden"
                },
                onClick = onOpenBarSections,
            )
        }

        item {
            Text(
                text = "Bar keeps disappearing? Allow EZZY to autostart and turn off battery " +
                    "optimisation for it in your phone's settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            )
        }
    }

    if (askMode) {
        AlertDialog(
            onDismissRequest = {
                // Dismissing still settles on a mode, so the question is not asked again.
                scope.launch { viewModel.setTriggerMode(settings.triggerMode).join() }
                askMode = false
            },
            title = { Text("Choose one") },
            text = {
                Column {
                    TriggerMode.entries.forEach { mode ->
                        NavigationRow(
                            title = mode.label,
                            subtitle = mode.hint,
                            onClick = {
                                askMode = false
                                scope.launch {
                                    viewModel.setTriggerMode(mode).join()
                                    OverlayService.start(context)
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {},
        )
    }
}

// ---- Sections in the bar -----------------------------------------------------

/**
 * Which sections the floating bar's rail carries. Everything is in it by default; this is for
 * taking things out, so the icons beside the button are the handful actually reached for from
 * inside another app rather than every section in the vault.
 *
 * Turning a section off here changes nothing about the section itself — it is untouched in the
 * app, and its entries still turn up in the bar's Quick access if they are pinned or recent.
 */
@Composable
fun BarSectionsSettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = ezzyViewModel { SettingsViewModel(it) }
    val settings = LocalSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    SettingsPage(title = "Sections in the bar", onBack = onBack) {
        item {
            Text(
                text = "Turn a section off to keep it out of the floating bar. It stays in the " +
                    "app either way.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }

        item {
            BarToggleRow(
                name = "Quick access",
                subtitle = "Pinned and recent, from every section",
                visible = settings.quickAccessInBar,
                onChange = { wanted ->
                    scope.launch {
                        viewModel.setQuickAccessInBar(wanted).join()
                        if (settings.overlayEnabled) OverlayService.refresh(context)
                    }
                },
                leading = {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                },
            )
        }

        item { SettingsGroup("Sections") }

        if (categories.isEmpty()) {
            item {
                Text(
                    text = "No sections yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                )
            }
        }

        items(categories, key = { it.id }) { category ->
            BarToggleRow(
                name = category.name,
                visible = category.id !in settings.hiddenBarSections,
                onChange = { wanted ->
                    // Same ordering as everywhere else the service reads a setting back: the
                    // write has to land first, or the bar can still be built from the old list.
                    scope.launch {
                        viewModel.setBarSectionVisible(category.id, wanted).join()
                        if (settings.overlayEnabled) OverlayService.refresh(context)
                    }
                },
                leading = {
                    // The same badge the bar itself shows, so the row is recognisable as that
                    // icon rather than just a name.
                    IconAvatar(
                        iconKey = category.iconKey,
                        colorKey = category.colorKey,
                        size = 38.dp,
                        iconSize = 19.dp,
                    )
                },
            )
        }
    }
}

@Composable
private fun BarToggleRow(
    name: String,
    visible: Boolean,
    onChange: (Boolean) -> Unit,
    leading: @Composable () -> Unit,
    subtitle: String? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable { onChange(!visible) }
                .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = visible, onCheckedChange = onChange)
        }
    }
}

// ---- Security ---------------------------------------------------------------

@Composable
fun SecuritySettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = ezzyViewModel { SettingsViewModel(it) }
    val settings = LocalSettings.current

    SettingsPage(title = "Security", onBack = onBack) {
        item { SettingsGroup("Opening the vault") }

        item {
            SwitchRow(
                title = "Lock with fingerprint",
                subtitle = "Ask for your fingerprint, face or PIN first",
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

        item { SettingsGroup("On screen") }

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
                subtitle = "Stay masked until you tap the eye",
                checked = settings.maskSecrets,
                onCheckedChange = viewModel::setMaskSecrets,
            )
        }

        item {
            SwitchRow(
                title = "Block screenshots",
                subtitle = "Blocks screenshots and screen recording in EZZY",
                checked = settings.blockScreenshots,
                onCheckedChange = viewModel::setBlockScreenshots,
            )
        }
    }
}

// ---- Appearance -------------------------------------------------------------

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = ezzyViewModel { SettingsViewModel(it) }
    val settings = LocalSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SettingsPage(title = "Appearance", onBack = onBack) {
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
                    subtitle = "Follow Android's Material You palette",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }
        }

        item { SettingsGroup("Floating button") }

        item {
            SwitchRow(
                title = "Spinning ring",
                subtitle = "The white arc circling the button in Always active mode",
                checked = settings.bubbleSweep,
                onCheckedChange = { wanted ->
                    // The button is already on screen, so the service is told to pick the
                    // change up rather than making the user turn the bar off and on again: it
                    // only repaints the ring, and the button keeps the place it was dragged
                    // to. Same ordering as a mode change — the write has to land before the
                    // service reads it back, or it can still see the old value.
                    scope.launch {
                        viewModel.setBubbleSweep(wanted).join()
                        if (settings.overlayEnabled) OverlayService.refresh(context)
                    }
                },
            )
        }

        item {
            Text(
                text = "In On trigger mode the ring is the countdown to the button " +
                    "disappearing, so it is always shown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            )
        }
    }
}

// ---- Data -------------------------------------------------------------------

@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    onErased: () -> Unit,
) {
    val viewModel: SettingsViewModel = ezzyViewModel { SettingsViewModel(it) }
    val backupViewModel: BackupViewModel = ezzyViewModel { BackupViewModel(it) }
    val context = LocalContext.current
    var confirmErase by remember { mutableStateOf(false) }
    var setExportPassword by remember { mutableStateOf(false) }
    // Held only long enough to reach the file picker's callback — never written anywhere.
    var pendingExportPassword by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && pendingExportPassword.isNotEmpty()) {
            backupViewModel.export(context, uri, pendingExportPassword)
        }
        pendingExportPassword = ""
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) backupViewModel.beginImport(context, uri)
    }

    SettingsPage(title = "Data", onBack = onBack) {
        item {
            NavigationRow(
                title = "Manage entry types",
                subtitle = "Edit which fields each kind of entry asks for",
                onClick = onOpenTemplates,
            )
        }

        item { SettingsGroup("Backup") }

        item {
            NavigationRow(
                title = "Export backup",
                subtitle = "Save an encrypted copy of everything to a file",
                onClick = { setExportPassword = true },
            )
        }

        item {
            NavigationRow(
                title = "Import backup",
                subtitle = "Add entries from a previously exported .ezzy file",
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )
        }

        item {
            Text(
                text = "The backup file is encrypted with the password you set for it. EZZY " +
                    "never stores that password — losing it means losing the backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            )
        }

        item { SettingsGroup("Danger zone") }

        item {
            NavigationRow(
                title = "Erase everything",
                subtitle = "Delete all entries, files and the encryption key",
                destructive = true,
                onClick = { confirmErase = true },
            )
        }
    }

    if (setExportPassword) {
        SetExportPasswordDialog(
            onDismiss = { setExportPassword = false },
            onConfirm = { password ->
                setExportPassword = false
                pendingExportPassword = password
                exportLauncher.launch(exportFileName())
            },
        )
    }

    val backupState = backupViewModel.state
    when (backupState) {
        is BackupUiState.AskImportPassword -> {
            AskImportPasswordDialog(
                error = backupState.error,
                onDismiss = { backupViewModel.dismiss() },
                onConfirm = { password -> backupViewModel.confirmImportPassword(password) },
            )
        }

        is BackupUiState.Working -> {
            BackupProgressDialog(progress = backupState.progress, exporting = backupState.exporting)
        }

        BackupUiState.ExportDone -> {
            AlertDialog(
                onDismissRequest = { backupViewModel.dismiss() },
                title = { Text("Backup saved") },
                text = { Text("Keep the password somewhere safe — it is the only way back in.") },
                confirmButton = {
                    TextButton(onClick = { backupViewModel.dismiss() }) { Text("Done") }
                },
            )
        }

        is BackupUiState.ImportDone -> {
            val result = backupState.result
            AlertDialog(
                onDismissRequest = { backupViewModel.dismiss() },
                title = { Text("Import complete") },
                text = {
                    Text(
                        if (result.skipped == 0) {
                            "${result.imported} ${if (result.imported == 1) "entry" else "entries"} added."
                        } else {
                            "${result.imported} added, ${result.skipped} could not be read."
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { backupViewModel.dismiss() }) { Text("Done") }
                },
            )
        }

        is BackupUiState.Error -> {
            AlertDialog(
                onDismissRequest = { backupViewModel.dismiss() },
                title = { Text("Couldn't do that") },
                text = { Text(backupState.message) },
                confirmButton = {
                    TextButton(onClick = { backupViewModel.dismiss() }) { Text("OK") }
                },
            )
        }

        BackupUiState.Idle -> Unit
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase everything?") },
            text = {
                Text(
                    "Every entry, file and section will be deleted and the encryption key " +
                        "destroyed. There is no backup and no way to undo this."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmErase = false
                        viewModel.eraseEverything {
                            OverlayService.stop(context)
                            onErased()
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

// ---- Helpers ----------------------------------------------------------------

/**
 * Prompts the system to add the EZZY tile to Quick Settings (Android 13+), where the user
 * approves or dismisses it themselves — no app can add its own tile silently. Older Android has
 * no such API at all, so the only path there is the manual one, explained instead.
 */
private fun requestQuickSettingsTile(
    context: Context,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        scope.launch {
            snackbar.showSnackbar(
                "Open the notification shade, tap the pencil (Edit), then drag EZZY into your tiles."
            )
        }
        return
    }

    val manager = context.getSystemService(StatusBarManager::class.java)
    val opened = manager != null && runCatching {
        manager.requestAddTileService(
            ComponentName(context, EzzyTileService::class.java),
            context.getString(R.string.app_name),
            android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_notification),
            { command -> command.run() },
            { /* The system already shows its own result to the user. */ },
        )
    }.isSuccess

    if (!opened) {
        scope.launch {
            snackbar.showSnackbar(
                "Could not open that automatically. Open the notification shade, tap the " +
                    "pencil (Edit), then drag EZZY into your tiles."
            )
        }
    }
}

/** A distinct name per export, so saving a new one never silently overwrites an older one. */
private fun exportFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
    return "ezzy-backup-$stamp.ezzy"
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
