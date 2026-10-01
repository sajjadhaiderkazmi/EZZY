package com.ezzy.vault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ezzy.vault.ui.theme.EzzyLime
import com.ezzy.vault.ui.theme.EzzyOnLime

/**
 * The app's main call to action: a tall lime pill with dark writing, the same in both themes.
 * It squashes a little under the finger so a tap feels like it landed.
 */
@Composable
fun LimeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "lime-press",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        // Disabled stays lime, only faded — it still reads as "the button", just not ready yet.
        color = if (enabled) EzzyLime else EzzyLime.copy(alpha = 0.35f),
        contentColor = if (enabled) EzzyOnLime else EzzyOnLime.copy(alpha = 0.45f),
        interactionSource = interaction,
        modifier = modifier
            .height(height)
            .scale(scale),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A soft, pill-shaped secondary action that sits next to [LimeButton]. */
@Composable
fun SoftPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp),
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(height),
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** A round icon button on a tonal disc — the back / close / more buttons of the new screens. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.46f))
        }
    }
}
