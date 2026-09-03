package com.ezzy.vault.ui.components

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ezzy.vault.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val VOICE_NOTE_MIME = "audio/mp4"

private fun formatSeconds(total: Int): String = "%d:%02d".format(total / 60, total % 60)

/**
 * Records a voice note straight into the vault. The recorder has to write to a real file, so it
 * uses a scratch file in the cache and hands the bytes over sealed — the plaintext copy is
 * deleted before the dialog closes.
 */
@Composable
fun VoiceNoteDialog(
    onCancel: () -> Unit,
    onRecorded: (ByteArray, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var permissionDenied by remember { mutableStateOf(false) }
    val scratch = remember { File(context.cacheDir, "voice_scratch.m4a") }

    fun stopRecorder() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        recording = false
    }

    fun startRecorder() {
        runCatching {
            if (scratch.exists()) scratch.delete()
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(scratch.absolutePath)
                prepare()
                start()
            }
            recorder = created
            seconds = 0
            recording = true
        }.onFailure {
            stopRecorder()
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecorder() else permissionDenied = true
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            seconds++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.release() }
            scratch.delete()
        }
    }

    Dialog(onDismissRequest = { stopRecorder(); onCancel() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Voice note", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        permissionDenied -> "Microphone access is needed to record."
                        recording -> "Recording… ${formatSeconds(seconds)}"
                        seconds > 0 -> "Recorded ${formatSeconds(seconds)}"
                        else -> "Tap the microphone to start."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(22.dp))

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(
                            if (recording) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = {
                            if (recording) {
                                stopRecorder()
                            } else {
                                permissionDenied = false
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(84.dp),
                    ) {
                        Icon(
                            imageVector = if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (recording) "Stop" else "Record",
                            modifier = Modifier.size(36.dp),
                            tint = if (recording) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { stopRecorder(); onCancel() }) { Text("Cancel") }
                    Button(
                        enabled = !recording && seconds > 0 && scratch.exists(),
                        onClick = {
                            val length = seconds
                            scope.launch {
                                val bytes = withContext(Dispatchers.IO) {
                                    runCatching { scratch.readBytes() }.getOrNull()
                                }
                                scratch.delete()
                                if (bytes != null) onRecorded(bytes, length) else onCancel()
                            }
                        },
                    ) {
                        Text("Save note")
                    }
                }
            }
        }
    }
}

/**
 * Plays a sealed voice note. The clip is decrypted into the cache only while it is playing and
 * removed as soon as playback stops, so no plaintext audio outlives the tap.
 */
@Composable
fun VoiceNoteRow(
    storedName: String,
    displayName: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var preparing by remember { mutableStateOf(false) }

    fun stopPlayback() {
        runCatching { player?.release() }
        player = null
        playing = false
        progress = 0f
        File(context.cacheDir, "play_$storedName.m4a").delete()
    }

    DisposableEffect(storedName) { onDispose { stopPlayback() } }

    LaunchedEffect(playing) {
        while (playing) {
            val active = player
            if (active == null || active.duration <= 0) break
            progress = active.currentPosition.toFloat() / active.duration
            delay(200)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                enabled = !preparing,
                onClick = {
                    val active = player
                    when {
                        active != null && active.isPlaying -> {
                            active.pause()
                            playing = false
                        }

                        active != null -> {
                            active.start()
                            playing = true
                        }

                        else -> {
                            preparing = true
                            scope.launch {
                                val file = prepareForPlayback(context, storedName)
                                preparing = false
                                if (file == null) return@launch
                                runCatching {
                                    MediaPlayer().apply {
                                        setDataSource(file.absolutePath)
                                        setOnCompletionListener { stopPlayback() }
                                        prepare()
                                        start()
                                    }
                                }.onSuccess {
                                    player = it
                                    playing = true
                                }
                            }
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play $displayName",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
}

private suspend fun prepareForPlayback(context: Context, storedName: String): File? =
    withContext(Dispatchers.IO) {
        val bytes = context.appContainer.repository.attachmentBytes(storedName)
            ?: return@withContext null
        runCatching {
            File(context.cacheDir, "play_$storedName.m4a").apply { writeBytes(bytes) }
        }.getOrNull()
    }

/** Small delete affordance reused by the editor's attachment list. */
@Composable
fun DeleteAttachmentButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "Remove",
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
