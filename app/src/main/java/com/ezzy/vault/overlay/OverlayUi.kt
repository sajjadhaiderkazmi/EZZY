package com.ezzy.vault.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezzy.vault.appContainer
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.security.SecureClipboard
import com.ezzy.vault.ui.components.EncryptedImage
import com.ezzy.vault.ui.components.FieldValueRow
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.icons.IconCatalog
import com.ezzy.vault.ui.nav.Routes
import com.ezzy.vault.ui.theme.Accents
import com.ezzy.vault.ui.theme.EzzyTheme
import com.ezzy.vault.ui.theme.LocalIsDarkTheme
import com.ezzy.vault.util.ThemeMode

/** The draggable launcher that sits on top of other apps. */
@Composable
fun OverlayBubble() {
    EzzyTheme {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = "Open EZZY",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** One row in the panel's list — flattened so the sheet never re-queries while drawing. */
private data class PanelEntry(
    val id: String,
    val title: String,
    val fieldCount: Int,
    val categoryId: String,
)

/** Where the panel currently is: the rail is always visible, this is what fills the sheet. */
private sealed interface PanelView {
    data object Quick : PanelView
    data class Section(val categoryId: String) : PanelView
    data class Entry(val itemId: String, val fromCategoryId: String?) : PanelView
}

/**
 * The floating bar itself: a rail of section icons down the right edge, and a sheet beside it
 * that drills from sections to entries to the individual values, each with its own copy button.
 */
@Composable
fun OverlayPanel(
    maskSecrets: Boolean,
    requireUnlock: Boolean,
    clipboardClearSeconds: Int,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onDismiss: () -> Unit,
    onOpenApp: (String?) -> Unit,
    onRequestUnlock: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { context.appContainer.repository }
    val categories by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val unlocked by AppLock.unlocked.collectAsStateWithLifecycle()

    var view by remember { mutableStateOf<PanelView>(PanelView.Quick) }
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    val locked = requireUnlock && !unlocked

    // The sheet plus the 60dp rail must fit a 360dp phone, so the sheet takes what is left
    // rather than a fixed width that would push the rail off screen.
    val configuration = LocalConfiguration.current
    val sheetWidth = (configuration.screenWidthDp.dp - 92.dp).coerceIn(200.dp, 320.dp)
    val panelMaxHeight = (configuration.screenHeightDp.dp - 96.dp).coerceIn(280.dp, 560.dp)

    EzzyTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Tapping anywhere outside closes the bar — same as any sheet.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .heightIn(max = panelMaxHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Flipped after the first frame so the sheet actually plays its slide-in
                // rather than appearing already open.
                var sheetShown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { sheetShown = true }

                AnimatedVisibility(
                    visible = sheetShown,
                    enter = slideInHorizontally(tween(180)) { it / 2 } + fadeIn(tween(180)),
                    exit = slideOutHorizontally(tween(140)) { it / 2 } + fadeOut(tween(140)),
                ) {
                    Surface(
                        modifier = Modifier.width(sheetWidth),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shadowElevation = 16.dp,
                    ) {
                        if (locked) {
                            LockedSheet(onUnlock = onRequestUnlock)
                        } else {
                            PanelSheet(
                                view = view,
                                categories = categories,
                                maxListHeight = panelMaxHeight - 60.dp,
                                maskSecrets = maskSecrets,
                                copiedLabel = copiedLabel,
                                onNavigate = { view = it },
                                onCopy = { label, value, sensitive ->
                                    val ok = SecureClipboard.copy(
                                        context = context,
                                        label = label,
                                        value = value,
                                        sensitive = sensitive,
                                        clearAfterSeconds = clipboardClearSeconds,
                                    )
                                    copiedLabel = if (ok) label else null
                                },
                                onOpenApp = onOpenApp,
                                onClose = onDismiss,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                SectionRail(
                    categories = categories,
                    activeCategoryId = (view as? PanelView.Section)?.categoryId,
                    quickActive = view is PanelView.Quick,
                    onQuick = { view = PanelView.Quick },
                    onSection = { view = PanelView.Section(it) },
                    onClose = onDismiss,
                )
            }
        }
    }
}

/** The narrow strip of section icons — this is the "menu" the swipe brings up. */
@Composable
private fun SectionRail(
    categories: List<CategoryEntity>,
    activeCategoryId: String?,
    quickActive: Boolean,
    onQuick: () -> Unit,
    onSection: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .width(60.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RailButton(
                selected = quickActive,
                accent = MaterialTheme.colorScheme.primary,
                onClick = onQuick,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Quick access",
                    tint = if (quickActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            categories.forEach { category ->
                val accent = Accents.color(category.colorKey, LocalIsDarkTheme.current)
                RailButton(
                    selected = category.id == activeCategoryId,
                    accent = accent,
                    onClick = { onSection(category.id) },
                ) {
                    Icon(
                        imageVector = IconCatalog.image(category.iconKey),
                        contentDescription = category.name,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close EZZY bar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RailButton(
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun LockedSheet(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Vault locked", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Unlock to reach your saved details.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}

@Composable
private fun PanelSheet(
    view: PanelView,
    categories: List<CategoryEntity>,
    maxListHeight: Dp,
    maskSecrets: Boolean,
    copiedLabel: String?,
    onNavigate: (PanelView) -> Unit,
    onCopy: (String, String, Boolean) -> Unit,
    onOpenApp: (String?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { context.appContainer.repository }

    Column(modifier = Modifier.fillMaxWidth()) {
        val title = when (view) {
            is PanelView.Quick -> "Quick access"
            is PanelView.Section -> categories.firstOrNull { it.id == view.categoryId }?.name ?: "Section"
            is PanelView.Entry -> "Details"
        }

        PanelHeader(
            title = title,
            showBack = view !is PanelView.Quick,
            onBack = {
                onNavigate(
                    when (view) {
                        is PanelView.Entry -> view.fromCategoryId
                            ?.let { PanelView.Section(it) } ?: PanelView.Quick

                        else -> PanelView.Quick
                    }
                )
            },
            onOpenApp = {
                onOpenApp(
                    when (view) {
                        is PanelView.Section -> Routes.category(view.categoryId)
                        is PanelView.Entry -> Routes.item(view.itemId)
                        else -> null
                    }
                )
            },
            onClose = onClose,
        )

        when (view) {
            is PanelView.Quick -> {
                val pinned by remember { repository.observePinned() }
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val recent by remember { repository.observeRecent(10) }
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                val combined = (pinned + recent.filterNot { r -> pinned.any { it.item.id == r.item.id } })
                    .take(12)

                PanelEntryList(
                    entries = combined.map {
                        PanelEntry(
                            id = it.item.id,
                            title = it.item.title,
                            fieldCount = it.fields.size,
                            categoryId = it.item.categoryId,
                        )
                    },
                    categoriesById = categories.associateBy { it.id },
                    maxListHeight = maxListHeight,
                    emptyMessage = "Pin the entries you use most and they will wait for you right here.",
                    onOpen = { onNavigate(PanelView.Entry(it, null)) },
                )
            }

            is PanelView.Section -> {
                val items by remember(view.categoryId) { repository.observeItems(view.categoryId) }
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                PanelEntryList(
                    entries = items.map {
                        PanelEntry(
                            id = it.item.id,
                            title = it.item.title,
                            fieldCount = it.fields.size,
                            categoryId = it.item.categoryId,
                        )
                    },
                    categoriesById = categories.associateBy { it.id },
                    maxListHeight = maxListHeight,
                    emptyMessage = "Nothing saved in this section yet.",
                    onOpen = { onNavigate(PanelView.Entry(it, view.categoryId)) },
                )
            }

            is PanelView.Entry -> {
                val entry by remember(view.itemId) { repository.observeItem(view.itemId) }
                    .collectAsStateWithLifecycle(initialValue = null)
                val details = entry

                if (details == null) {
                    Spacer(Modifier.height(120.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = maxListHeight),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item {
                            Text(
                                text = details.item.title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        items(details.sortedFields, key = { it.id }) { field ->
                            FieldValueRow(
                                label = field.label,
                                value = field.value,
                                type = field.type,
                                startMasked = maskSecrets,
                                compact = true,
                                onCopy = { onCopy(field.label, field.value, field.type.isMasked) },
                            )
                        }
                        val images = details.sortedAttachments.filter {
                            it.mimeType.startsWith("image/")
                        }
                        if (images.isNotEmpty()) {
                            items(images, key = { it.id }) { attachment ->
                                EncryptedImage(
                                    storedName = attachment.storedName,
                                    contentDescription = attachment.displayName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = copiedLabel != null) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${copiedLabel.orEmpty()} copied",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (showBack) 2.dp else 4.dp),
        )
        IconButton(onClick = onOpenApp, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = "Open in EZZY",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PanelEntryList(
    entries: List<PanelEntry>,
    categoriesById: Map<String, CategoryEntity>,
    maxListHeight: Dp,
    emptyMessage: String,
    onOpen: (String) -> Unit,
) {
    if (entries.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.heightIn(max = maxListHeight),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            val category = categoriesById[entry.categoryId]
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onOpen(entry.id) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAvatar(
                        iconKey = category?.iconKey,
                        colorKey = category?.colorKey,
                        size = 34.dp,
                        iconSize = 17.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (entry.fieldCount == 1) "1 value" else "${entry.fieldCount} values",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
