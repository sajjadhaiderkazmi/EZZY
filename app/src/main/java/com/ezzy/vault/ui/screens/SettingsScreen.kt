package com.ezzy.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.ui.LocalSettings
import com.ezzy.vault.ui.components.NavigationRow
import com.ezzy.vault.ui.components.SettingsPage
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.ui.icons.EzzyMark
import com.ezzy.vault.util.AutoHide
import com.ezzy.vault.util.ThemeMode
import com.ezzy.vault.util.TriggerMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val store = container.settings

    /** The sections themselves, for the page that picks which of them the bar carries. */
    val categories: StateFlow<List<CategoryEntity>> = container.repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDisplayName(value: String) = launch { store.setDisplayName(value) }
    fun setOverlayEnabled(value: Boolean) = launch { store.setOverlayEnabled(value) }
    fun setTriggerMode(value: TriggerMode) = launch { store.setTriggerMode(value) }
    fun setAutoHide(value: AutoHide) = launch { store.setAutoHide(value) }
    fun setBiometric(value: Boolean) = launch { store.setBiometricLock(value) }
    fun setAutoLock(minutes: Int) = launch { store.setAutoLockMinutes(minutes) }
    fun setClipboardClear(seconds: Int) = launch { store.setClipboardClearSeconds(seconds) }
    fun setMaskSecrets(value: Boolean) = launch { store.setMaskSecrets(value) }
    fun setBlockScreenshots(value: Boolean) = launch { store.setBlockScreenshots(value) }
    fun setThemeMode(value: ThemeMode) = launch { store.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = launch { store.setDynamicColor(value) }
    fun setBubbleSweep(value: Boolean) = launch { store.setBubbleSweep(value) }

    fun setBarSectionVisible(categoryId: String, visible: Boolean) =
        launch { store.setBarSectionVisible(categoryId, visible) }


    fun eraseEverything(onDone: () -> Unit) = launch {
        container.eraseEverything()
        AppLock.lock()
        onDone()
    }

    // Returns the Job so a caller that needs the write to land before doing anything else
    // (restarting the overlay service, in particular) can .join() it.
    private fun launch(block: suspend () -> Unit): Job = viewModelScope.launch { block() }
}

/**
 * The settings hub. Each area is one row that opens its own page, so no single screen is a wall
 * of switches — the detail (and the explaining) lives on the page it belongs to.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenFloatingBar: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenData: () -> Unit,
) {
    val settings = LocalSettings.current
    var editName by remember { mutableStateOf(false) }

    SettingsPage(title = "Settings", onBack = onBack) {
        item {
            ProfileCard(
                name = settings.displayName,
                onEdit = { editName = true },
            )
        }

        item { Spacer(Modifier.height(4.dp)) }

        item {
            NavigationRow(
                title = "Floating bar",
                subtitle = if (settings.overlayEnabled) {
                    "On · ${settings.triggerMode.label}"
                } else {
                    "Off"
                },
                icon = Icons.Rounded.Bolt,
                onClick = onOpenFloatingBar,
            )
        }

        item {
            NavigationRow(
                title = "Security",
                subtitle = if (settings.biometricLock) "Fingerprint lock on" else "Fingerprint lock off",
                icon = Icons.Rounded.Shield,
                onClick = onOpenSecurity,
            )
        }

        item {
            NavigationRow(
                title = "Appearance",
                subtitle = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> "Follow system"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                icon = Icons.Rounded.Palette,
                onClick = onOpenAppearance,
            )
        }

        item {
            NavigationRow(
                title = "Data",
                subtitle = "Entry types and erasing",
                icon = Icons.Rounded.Storage,
                onClick = onOpenData,
            )
        }

        item {
            Text(
                text = "Everything stays on this phone. No account, no cloud.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp),
            )
        }
    }

    if (editName) {
        NameDialog(
            initial = settings.displayName,
            onDismiss = { editName = false },
            onSave = { editName = false },
        )
    }
}

@Composable
private fun ProfileCard(name: String, onEdit: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val initial = name.trim().firstOrNull()
                    if (initial == null) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    } else {
                        Text(
                            text = initial.uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // The app's own mark on the corner of the avatar, so the card reads as you, in
                // EZZY. The ring is the card's own colour, which is what lifts the badge off
                // the circle behind it.
                Box(
                    modifier = Modifier
                        .size(23.dp)
                        .clip(CircleShape)
                        .background(EzzyMark.Brand)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = EzzyMark.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { "Add your name" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Shown in your home greeting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit name",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Edits the name in a draft, so a half-typed name never reaches the greeting. */
@Composable
private fun NameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val viewModel: SettingsViewModel = ezzyViewModel { SettingsViewModel(it) }
    var draft by remember { mutableStateOf(initial) }

    fun commit() {
        viewModel.setDisplayName(draft)
        onSave()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(24) },
                placeholder = { Text("Your name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
            )
        },
        confirmButton = {
            TextButton(onClick = { commit() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
