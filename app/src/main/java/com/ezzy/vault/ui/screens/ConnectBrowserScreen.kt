package com.ezzy.vault.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.security.SecureShare
import com.ezzy.vault.sync.BrowserLink
import com.ezzy.vault.sync.LinkState
import com.ezzy.vault.sync.SyncService
import com.ezzy.vault.ui.LocalSnackbar
import com.ezzy.vault.ui.components.LimeButton
import com.ezzy.vault.ui.components.SettingsGroup
import com.ezzy.vault.ui.components.SettingsPage
import com.ezzy.vault.ui.components.SoftPillButton
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.ui.icons.EzzyMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.zip.ZipInputStream

/** The extension as it ships inside this APK. */
data class ExtensionPackage(val version: String, val sizeBytes: Long)

class ConnectBrowserViewModel(private val container: AppContainer) : ViewModel() {

    private val link: BrowserLink = container.browserLink
    val state: StateFlow<LinkState> = link.state

    private val _extension = MutableStateFlow<ExtensionPackage?>(null)
    val extension: StateFlow<ExtensionPackage?> = _extension.asStateFlow()

    /** Set when a pairing could not even start — no free port, say. */
    private val _problem = MutableStateFlow<String?>(null)
    val problem: StateFlow<String?> = _problem.asStateFlow()

    fun loadExtension(context: Context) = viewModelScope.launch {
        _extension.value = withContext(Dispatchers.IO) { readPackage(context) }
    }

    fun startPairing() = viewModelScope.launch {
        _problem.value = null
        if (link.startPairing() == null) {
            _problem.value = "Could not open the connection on this phone. Restart EZZY and try again."
        }
    }

    fun cancelPairing() = viewModelScope.launch { link.cancelPairing() }
    fun setPin(pin: String) = viewModelScope.launch { link.setPin(pin) }
    fun disconnect() = viewModelScope.launch { link.disconnect() }
    /** "Sync now": makes sure the phone side is listening. Chrome pulls changes every few seconds. */
    fun keepAlive(context: Context) {
        link.ensureServerRunning()
        SyncService.start(context)
    }

    /** The zip's bytes, for Download and Share. */
    suspend fun packageBytes(context: Context): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { context.assets.open(EXTENSION_ASSET).use { it.readBytes() } }.getOrNull()
    }

    private fun readPackage(context: Context): ExtensionPackage? = runCatching {
        val bytes = context.assets.open(EXTENSION_ASSET).use { it.readBytes() }
        var version = "1.0.0"
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith("manifest.json")) {
                    val text = zip.readBytes().decodeToString()
                    Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(text)?.let { version = it.groupValues[1] }
                    break
                }
            }
        }
        ExtensionPackage(version, bytes.size.toLong())
    }.getOrNull()

    companion object {
        const val EXTENSION_ASSET = "ezzy-chrome-extension.zip"
    }
}

/**
 * Settings › Connect Browser. One page that walks through the whole thing in order: get the
 * extension onto the computer, confirm it is installed, scan the code, choose the PIN Chrome
 * will ask for — and, once linked, how the link is doing and how to end it.
 */
@Composable
fun ConnectBrowserScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ConnectBrowserViewModel = ezzyViewModel { ConnectBrowserViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val extension by viewModel.extension.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    var askInstalled by remember { mutableStateOf(false) }
    var askLogout by remember { mutableStateOf(false) }

    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.loadExtension(context) }
    // The "connected" and "Syncing… / Done" notices need this on Android 13+; sync works without it.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val midPairing = state is LinkState.Pairing || state is LinkState.SetPin
    val leave: () -> Unit = {
        if (midPairing) viewModel.cancelPairing()
        onBack()
    }
    BackHandler(onBack = leave)

    SettingsPage(title = "Connect Browser", onBack = leave) {
        when (val current = state) {
            LinkState.Off -> {
                item { ExtensionCard(extension, viewModel) }
                item { SettingsGroup("How to add it to Chrome") }
                item { InstructionsCard() }
                if (problem != null) item { ProblemCard(problem!!) }
                item {
                    LimeButton(
                        text = "Proceed",
                        trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                        onClick = { askInstalled = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            is LinkState.Pairing -> item {
                PairingPanel(
                    pairing = current,
                    onNewCode = { viewModel.startPairing() },
                    onCancel = { viewModel.cancelPairing() },
                )
            }

            is LinkState.SetPin -> item {
                PinPanel(
                    browserName = current.browserName,
                    onPinChosen = { viewModel.setPin(it) },
                    onCancel = { viewModel.cancelPairing() },
                )
            }

            is LinkState.WaitingForBrowser -> item { WaitingPanel(current.browserName) }

            is LinkState.Linked -> {
                item { LinkedHero(current) }
                item { SettingsGroup("Details") }
                item { LinkDetails(current) }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        SoftPillButton(
                            text = "Sync now",
                            icon = Icons.Rounded.Sync,
                            onClick = {
                                viewModel.keepAlive(context)
                                scope.launch { snackbar.showSnackbar("Chrome picks up changes within a few seconds") }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        LogoutButton(onClick = { askLogout = true }, modifier = Modifier.weight(1f))
                    }
                }
                item { SettingsGroup("The extension") }
                item { ExtensionCard(extension, viewModel) }
            }
        }
    }

    if (askInstalled) {
        AlertDialog(
            onDismissRequest = { askInstalled = false },
            icon = { Icon(Icons.Rounded.Language, contentDescription = null) },
            title = { Text("Installed in Chrome?") },
            text = {
                Text(
                    "Have you installed the EZZY extension in your Chrome browser? " +
                        "Keep your phone and computer on the same Wi-Fi for the next step.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askInstalled = false
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        runCatching { notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                    }
                    viewModel.startPairing()
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { askInstalled = false }) { Text("Back & Install") }
            },
        )
    }

    if (askLogout) {
        AlertDialog(
            onDismissRequest = { askLogout = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
            title = { Text("Log out of Chrome?") },
            text = {
                Text(
                    "Chrome loses access straight away and wipes its copy of your vault the next " +
                        "time it tries to sync. You can pair again whenever you like.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askLogout = false
                    viewModel.disconnect()
                }) { Text("Log out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { askLogout = false }) { Text("Cancel") }
            },
        )
    }
}

// ---- Step 1: get the extension -----------------------------------------------------------------

@Composable
private fun ExtensionCard(extension: ExtensionPackage?, viewModel: ConnectBrowserViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val fileName = "EZZY-Chrome-Extension-v${extension?.version ?: "1"}.zip"

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val bytes = viewModel.packageBytes(context) ?: return@withContext false
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                }.getOrDefault(false)
            }
            snackbar.showSnackbar(if (ok) "Saved $fileName" else "Could not save the file")
        }
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(EzzyMark.Brand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(EzzyMark.Bolt, contentDescription = null, tint = EzzyMark.Spark, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EZZY - Chrome Extension",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (extension == null) "Preparing…"
                        else "Version ${extension.version} · ${formatSize(extension.sizeBytes)} · Google Chrome",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Open your vault in Chrome on your computer: search, view, copy, add and edit " +
                    "entries — locked with your own PIN.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LimeButton(
                    text = "Download",
                    icon = Icons.Rounded.Download,
                    onClick = { saver.launch(fileName) },
                    enabled = extension != null,
                    height = 50.dp,
                    modifier = Modifier.weight(1f),
                )
                SoftPillButton(
                    text = "Share",
                    icon = Icons.Rounded.Share,
                    enabled = extension != null,
                    height = 50.dp,
                    onClick = {
                        scope.launch {
                            val uri = withContext(Dispatchers.IO) { stageForShare(context, viewModel, fileName) }
                            if (uri == null || !SecureShare.share(context, uri, "application/zip")) {
                                snackbar.showSnackbar("Could not share the file")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private suspend fun stageForShare(context: Context, viewModel: ConnectBrowserViewModel, name: String): Uri? {
    val bytes = viewModel.packageBytes(context) ?: return null
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, name)
    return runCatching {
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }.getOrNull()
}

@Composable
private fun InstructionsCard() {
    val steps = listOf(
        "Download the extension, or Share it to your computer (WhatsApp, email or Drive).",
        "On the computer, unzip (extract) the file. You get a folder named \"ezzy-chrome-extension\".",
        "Open Google Chrome and go to chrome://extensions",
        "Turn on \"Developer mode\" at the top right.",
        "Click \"Load unpacked\" and choose the \"ezzy-chrome-extension\" folder.",
        "Click the puzzle icon in Chrome's toolbar and pin EZZY so it is always one click away.",
        "Connect the phone and the computer to the same Wi-Fi, then tap Proceed below.",
    )
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            steps.forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(vertical = 7.dp)) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProblemCard(message: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

// ---- Step 2: the code ----------------------------------------------------------------------------

@Composable
private fun PairingPanel(
    pairing: LinkState.Pairing,
    onNewCode: () -> Unit,
    onCancel: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pairing.code) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val secondsLeft = ((pairing.expiresAt - now) / 1000).coerceAtLeast(0)
    val expired = secondsLeft == 0L

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        StepLabel("Step 2 of 3 · Type these in Chrome")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "On your computer, click the EZZY icon in Chrome, choose Connect phone, " +
                "and type the address and code below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))

        if (pairing.host == null) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WifiOff, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "This phone is not on Wi-Fi. Join the same Wi-Fi as your computer, then tap New code.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        BigValueCard(
            label = "Phone address",
            value = "${pairing.host ?: "—"}:${pairing.port}",
            dimmed = pairing.host == null,
        )
        Spacer(Modifier.height(12.dp))
        BigValueCard(
            label = "Pairing code",
            value = if (expired) "Expired" else BrowserLink.displayCode(pairing.code),
            dimmed = expired,
            highlight = true,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (expired) "This code has expired — tap New code"
            else "Code expires in %d:%02d · works once".format(secondsLeft / 60, secondsLeft % 60),
            style = MaterialTheme.typography.labelLarge,
            color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Keep this screen open until Chrome connects.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SoftPillButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            LimeButton(
                text = "New code",
                icon = Icons.Rounded.Refresh,
                onClick = onNewCode,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BigValueCard(label: String, value: String, dimmed: Boolean, highlight: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (highlight) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 18.dp, horizontal = 16.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = if (highlight) 34.sp else 26.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (highlight) 4.sp else 1.sp,
                color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---- Step 3: the PIN -----------------------------------------------------------------------------

@Composable
private fun PinPanel(browserName: String, onPinChosen: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(confirming) { runCatching { focus.requestFocus() } }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        StepLabel("Step 3 of 3 · Set a PIN")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$browserName is connecting.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (confirming) "Type the same 6 digits again to confirm."
            else "Choose 6 digits. Chrome will ask for this PIN every time EZZY opens there.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))

        AnimatedContent(targetState = confirming, label = "pin-step") { isConfirm ->
            PinBoxes(
                value = if (isConfirm) confirm else first,
                error = mismatch && isConfirm,
                focus = focus,
                onValueChange = { digits ->
                    mismatch = false
                    if (isConfirm) confirm = digits else first = digits
                    if (!isConfirm && digits.length == PIN_LENGTH) {
                        confirming = true
                    } else if (isConfirm && digits.length == PIN_LENGTH) {
                        if (digits == first) onPinChosen(digits) else {
                            mismatch = true
                            confirm = ""
                        }
                    }
                },
            )
        }

        if (mismatch) {
            Spacer(Modifier.height(10.dp))
            Text("Those PINs don't match. Try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SoftPillButton(
                text = if (confirming) "Start over" else "Cancel",
                onClick = {
                    if (confirming) {
                        confirming = false
                        first = ""
                        confirm = ""
                        mismatch = false
                    } else onCancel()
                },
                modifier = Modifier.weight(1f),
            )
            LimeButton(
                text = if (confirming) "Set PIN" else "Next",
                enabled = (if (confirming) confirm else first).length == PIN_LENGTH,
                onClick = {
                    if (!confirming) confirming = true
                    else if (confirm == first) onPinChosen(confirm)
                    else {
                        mismatch = true
                        confirm = ""
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PinBoxes(value: String, error: Boolean, focus: FocusRequester, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(PIN_LENGTH)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.focusRequester(focus),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(PIN_LENGTH) { index ->
                    val filled = index < value.length
                    val active = index == value.length
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(
                                width = if (active || error) 2.dp else 1.dp,
                                color = when {
                                    error -> MaterialTheme.colorScheme.error
                                    active -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filled) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun WaitingPanel(browserName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        Text("Finishing in Chrome…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Keep the EZZY page open in $browserName. This takes a few seconds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---- Linked ----------------------------------------------------------------------------------------

@Composable
private fun LinkedHero(state: LinkState.Linked) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val online = state.browser.lastSyncAt > 0 && now - state.browser.lastSyncAt < ONLINE_WINDOW_MS

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("Connected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(state.browser.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (online) Color(0xFF34C759) else MaterialTheme.colorScheme.outline),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (online) "Syncing now" else "Chrome is closed or on another network",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun LinkDetails(state: LinkState.Linked) {
    val dateTime = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            DetailRow("Linked on", dateTime.format(Date(state.browser.pairedAt)))
            DetailRow(
                "Last sync",
                if (state.browser.lastSyncAt == 0L) "Not yet" else dateTime.format(Date(state.browser.lastSyncAt)),
            )
            DetailRow("Phone address", if (state.host == null) "Not on Wi-Fi" else "${state.host}:${state.port}")
            DetailRow("Security", "End-to-end encrypted · PIN in Chrome")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Composable
private fun LogoutButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.height(56.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Log out", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "${(bytes + 1023) / 1024} KB"
}

private const val PIN_LENGTH = 6
private const val ONLINE_WINDOW_MS = 90_000L
