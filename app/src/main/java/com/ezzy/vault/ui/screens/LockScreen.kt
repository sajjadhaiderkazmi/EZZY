package com.ezzy.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezzy.vault.ui.icons.EzzyMark

/**
 * The app's mark, drawn from the same bolt and the same flat indigo as the launcher icon, at
 * the same proportions — the glyph covers a little over half the tile's height, so the margin
 * around it stays even and nothing runs into an edge.
 */
@Composable
fun EzzyLogo(size: androidx.compose.ui.unit.Dp = 88.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.6f))
            .background(EzzyMark.Brand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = EzzyMark.Bolt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.74f),
        )
    }
}

@Composable
fun LockScreen(
    error: String?,
    onUnlock: () -> Unit,
) {
    // Ask straight away — one tap should be enough on the common path. The prompt is a
    // fragment transaction, so it must wait until the activity is genuinely resumed.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.withResumed { onUnlock() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EzzyLogo()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "EZZY",
            style = MaterialTheme.typography.headlineMedium,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your vault is locked",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onUnlock) {
            Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("Unlock")
        }
    }
}
