package com.ezzy.vault.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.data.model.FieldType

/** One saved entry as it appears in any list. Keeps home, category and search identical. */
@Composable
fun ItemRow(
    item: ItemWithDetails,
    iconKey: String?,
    colorKey: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A login's website is a far more useful thing to see under its title than any part of the
    // password — so a URL field always wins the preview slot before a masked one is considered.
    val urlField = item.sortedFields.firstOrNull { it.type == FieldType.URL && it.value.isNotBlank() }
    // A masked field (account number, card, IBAN…) makes a far more useful preview than the
    // full value would — "•••• 4321" says which one at a glance without showing it in a list.
    // A password is the one masked field this never applies to: unlike a card's last four
    // digits, a password's last four characters are real secret to leak into a list view.
    val maskedField = item.sortedFields.firstOrNull {
        it.type.isMasked && it.value.isNotBlank() && !it.label.contains("password", ignoreCase = true)
    }
    val subtitle = when {
        urlField != null -> urlField.value
        maskedField != null -> "•••• " + maskedField.value.takeLast(4)
        else -> item.item.subtitle.ifBlank {
            item.sortedFields.firstOrNull { !it.type.isMasked && it.value.isNotBlank() }?.value.orEmpty()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAvatar(iconKey = iconKey, colorKey = colorKey, size = 44.dp, iconSize = 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.attachments.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = "${item.attachments.size} files",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "${item.attachments.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.item.isPinned) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = "${item.fields.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
