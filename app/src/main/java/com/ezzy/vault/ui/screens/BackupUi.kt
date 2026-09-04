package com.ezzy.vault.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.backup.BackupCrypto
import com.ezzy.vault.data.repo.VaultRepository
import kotlinx.coroutines.launch

/** Everything the Export/Import UI can be doing, one state at a time. */
sealed interface BackupUiState {
    data object Idle : BackupUiState

    /** A file has been picked and its header checked; now the password that opens it is needed. */
    data class AskImportPassword(val error: String? = null) : BackupUiState

    data class Working(val progress: Float, val exporting: Boolean) : BackupUiState
    data object ExportDone : BackupUiState
    data class ImportDone(val result: VaultRepository.ImportResult) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

class BackupViewModel(container: AppContainer) : ViewModel() {

    private val manager = container.backupManager

    var state by mutableStateOf<BackupUiState>(BackupUiState.Idle)
        private set

    // Held only in memory, between the file being picked and the password being confirmed —
    // never written to disk unencrypted, and dropped the moment import finishes or fails.
    private var pendingBytes: ByteArray? = null

    fun export(context: Context, uri: Uri, password: String) {
        state = BackupUiState.Working(0f, exporting = true)
        viewModelScope.launch {
            runCatching {
                manager.export(context, uri, password) { progress ->
                    state = BackupUiState.Working(progress, exporting = true)
                }
            }.onSuccess {
                state = BackupUiState.ExportDone
            }.onFailure { error ->
                state = BackupUiState.Error(error.message ?: "Could not save the backup")
            }
        }
    }

    /** Reads the picked file and checks its header before asking for anything from the user. */
    fun beginImport(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching { manager.readFile(context, uri) }
                .onSuccess { bytes ->
                    if (BackupCrypto.looksLikeBackup(bytes)) {
                        pendingBytes = bytes
                        state = BackupUiState.AskImportPassword()
                    } else {
                        state = BackupUiState.Error("This is not an EZZY backup file")
                    }
                }
                .onFailure { error ->
                    state = BackupUiState.Error(error.message ?: "Could not read that file")
                }
        }
    }

    fun confirmImportPassword(password: String) {
        val bytes = pendingBytes ?: return
        state = BackupUiState.Working(0f, exporting = false)
        viewModelScope.launch {
            runCatching {
                manager.import(bytes, password) { progress ->
                    state = BackupUiState.Working(progress, exporting = false)
                }
            }.onSuccess { result ->
                pendingBytes = null
                state = BackupUiState.ImportDone(result)
            }.onFailure { error ->
                state = if (error is BackupCrypto.WrongPasswordException) {
                    // The file is still held — this is a retry, not a restart.
                    BackupUiState.AskImportPassword(error.message)
                } else {
                    pendingBytes = null
                    BackupUiState.Error(error.message ?: "Could not import that backup")
                }
            }
        }
    }

    fun dismiss() {
        pendingBytes = null
        state = BackupUiState.Idle
    }
}

/**
 * Sets a new password before an export starts. Two fields rather than one, since a typo here
 * is only discoverable much later — when a real restore needs the password that was actually
 * typed, not the one the user meant to type.
 */
@Composable
fun SetExportPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }

    val tooShort = password.length < 4
    val mismatch = touched && password != confirm
    val canConfirm = !tooShort && password == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a backup password") },
        text = {
            Column {
                Text(
                    "This unlocks the file — write it down somewhere. EZZY does not store it, " +
                        "so a lost password means a lost backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; touched = true },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (canConfirm) onConfirm(password) }
                    ),
                )
                if (mismatch) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Passwords don't match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (touched && tooShort) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "At least 4 characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = canConfirm,
            ) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Asks for the password an already-picked file was exported with. */
@Composable
fun AskImportPasswordDialog(
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup password") },
        text = {
            Column {
                Text(
                    "This file was exported with a password. Enter it to restore its entries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (password.isNotEmpty()) onConfirm(password) }
                    ),
                )
                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty(),
            ) { Text("Unlock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * The progress screen itself: a bar plus a percentage, with nothing to dismiss it — an export
 * or import is not a state to walk away from partway through.
 */
@Composable
fun BackupProgressDialog(progress: Float, exporting: Boolean) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    text = if (exporting) "Exporting…" else "Importing…",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
