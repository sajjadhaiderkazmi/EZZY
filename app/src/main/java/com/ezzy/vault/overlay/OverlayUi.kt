package com.ezzy.vault.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezzy.vault.appContainer
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.security.SecureClipboard
import com.ezzy.vault.data.db.AttachmentEntity
import com.ezzy.vault.data.model.Seed
import com.ezzy.vault.ui.components.EncryptedImage
import com.ezzy.vault.ui.components.FieldValueRow
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.icons.IconCatalog
import com.ezzy.vault.ui.nav.Routes
import com.ezzy.vault.ui.icons.EzzyMark
import com.ezzy.vault.ui.rememberAttachmentActions
import com.ezzy.vault.ui.theme.Accents
import com.ezzy.vault.ui.theme.EzzyTheme
import com.ezzy.vault.ui.theme.LocalIsDarkTheme
import com.ezzy.vault.util.ThemeMode
import kotlinx.coroutines.delay

/** What the white ring around the floating button is doing. */
sealed interface BubbleRing {

    /** A white arc travelling round the ring, for the button that stays up permanently. */
    data object Sweep : BubbleRing

    /**
     * The ring emptying as the button's own time runs out, so how long is left is something
     * you can see rather than a surprise. [token] changes whenever the countdown restarts —
     * on a fresh tile tap, or when the panel closes and the button starts counting again.
     */
    data class Countdown(val token: Int, val millis: Long) : BubbleRing
}

/**
 * The draggable launcher that sits on top of other apps.
 *
 * No drop shadow. Each of these lives in its own window sized to exactly this circle, and an
 * elevation shadow is drawn outside the shape it belongs to — with no window left to spill
 * into, it came out as a hard grey square under the button. The ring separates the circle from
 * the wallpaper instead, and stays inside the bounds where nothing can clip it.
 */
@Composable
fun OverlayBubble(ring: BubbleRing? = null) {
    EzzyTheme {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(EzzyMark.Brand)
                    // The track the bright arc runs on, and what is left once it has gone.
                    .border(1.5.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // Same proportion as the launcher icon: the bolt fills a little over half the
                // circle's height, so it never crowds the edge.
                Icon(
                    imageVector = EzzyMark.Bolt,
                    contentDescription = "Open EZZY",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }

            BubbleRingArc(ring = ring, modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
private fun BubbleRingArc(ring: BubbleRing?, modifier: Modifier) {
    when (ring) {
        null -> Unit

        is BubbleRing.Sweep -> {
            val transition = rememberInfiniteTransition(label = "bubble-sweep")
            val start by transition.animateFloat(
                initialValue = -90f,
                targetValue = 270f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1600, easing = LinearEasing),
                ),
                label = "bubble-sweep-angle",
            )
            RingArc(modifier = modifier, startAngle = start, sweepAngle = 96f)
        }

        is BubbleRing.Countdown -> {
            // Driven off the clock rather than a frame-by-frame animation: it has to agree
            // with the service's own timer, and a ten-minute arc redrawn sixty times a second
            // would be burning the battery to show a change no eye can see. One tick per
            // degree of arc, floored so a short countdown still looks smooth.
            var progress by remember(ring.token) { mutableStateOf(1f) }
            LaunchedEffect(ring.token) {
                val startedAt = System.currentTimeMillis()
                val tick = (ring.millis / 360L).coerceIn(33L, 500L)
                while (true) {
                    val elapsed = System.currentTimeMillis() - startedAt
                    progress = (1f - elapsed.toFloat() / ring.millis.toFloat()).coerceIn(0f, 1f)
                    if (progress <= 0f) break
                    delay(tick)
                }
            }
            RingArc(
                modifier = modifier,
                startAngle = -90f,
                sweepAngle = 360f * progress,
            )
        }
    }
}

/** One white arc drawn on the button's edge, inset so the stroke stays inside the circle. */
@Composable
private fun RingArc(modifier: Modifier, startAngle: Float, sweepAngle: Float) {
    val strokePx = with(LocalDensity.current) { 2.5.dp.toPx() }
    Canvas(modifier = modifier) {
        val inset = strokePx / 2f
        drawArc(
            color = Color.White,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokePx, size.height - strokePx),
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}

/**
 * The circle the bubble is dropped onto to switch the floating bar off. Grows and turns red
 * once the bubble is close enough to release, the way a chat head's dismiss target does.
 */
@Composable
fun DismissTarget(armed: Boolean) {
    EzzyTheme {
        val size by animateDpAsState(if (armed) 74.dp else 64.dp, label = "dismiss-size")
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (armed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                // Same reason as the button: a shadow here had no window to fall into.
                .border(1.5.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Turn the floating bar off",
                tint = if (armed) MaterialTheme.colorScheme.onError
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (armed) 34.dp else 28.dp),
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
    /** Section ids the user has taken out of the rail. */
    hiddenSections: Set<String>,
    /** Whether the rail carries its Quick access star at all. */
    showQuickAccess: Boolean,
    requireUnlock: Boolean,
    clipboardClearSeconds: Int,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onDismiss: () -> Unit,
    onOpenApp: (String?) -> Unit,
    onInteraction: () -> Unit,
    // null asks for the vault itself; an item id asks again for that one guarded entry.
    onRequestUnlock: (String?) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { context.appContainer.repository }
    val categories by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Only the rail is filtered. The full list still names an entry's section in the header,
    // so opening a hidden section's entry from Quick access still says where it came from.
    val railCategories = remember(categories, hiddenSections) {
        categories.filterNot { it.id in hiddenSections }
    }
    val unlocked by AppLock.unlocked.collectAsStateWithLifecycle()
    val confirmedItems by AppLock.confirmedItems.collectAsStateWithLifecycle()

    var view by remember { mutableStateOf<PanelView>(PanelView.Quick) }
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    // Raised one beat after the window goes up. Building this whole window, laying the sheet
    // out and taking the first rows back from the database all happen on the first few frames;
    // animating through that is what made the bar arrive in steps. It waits instead, then
    // plays one clean movement.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        shown = true
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown) 0.32f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "overlay-scrim",
    )

    fun navigate(target: PanelView) {
        onInteraction()
        view = target
    }

    // Quick access is the panel's usual root. With the star switched off there is nothing to
    // root on, so the first section takes its place — including when a back arrow aims there.
    LaunchedEffect(showQuickAccess, railCategories, view) {
        if (!showQuickAccess && view is PanelView.Quick) {
            railCategories.firstOrNull()?.let { view = PanelView.Section(it.id) }
        }
    }

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
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    )
            )

            AnimatedVisibility(
                visible = shown,
                enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                ) { it / 4 } + fadeIn(tween(180)),
                exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                ) { it / 4 } + fadeOut(tween(140)),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Row(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .heightIn(max = panelMaxHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.width(sheetWidth),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        // A soft shadow is redrawn every frame of the slide; 16dp on a
                        // translucent overlay window was paying for itself in dropped frames.
                        shadowElevation = 8.dp,
                    ) {
                        if (locked) {
                            LockedSheet(onUnlock = { onRequestUnlock(null) })
                        } else {
                            PanelSheet(
                                view = view,
                                categories = categories,
                                maxListHeight = panelMaxHeight - 60.dp,
                                maskSecrets = maskSecrets,
                                copiedLabel = copiedLabel,
                                confirmedItems = confirmedItems,
                                showQuickAccess = showQuickAccess,
                                onNotify = { copiedLabel = it },
                                onNavigate = ::navigate,
                                onRequestUnlock = onRequestUnlock,
                                onCopy = { label, value, sensitive ->
                                    onInteraction()
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

                    Spacer(Modifier.width(8.dp))

                    SectionRail(
                        categories = railCategories,
                        showQuick = showQuickAccess,
                        activeCategoryId = (view as? PanelView.Section)?.categoryId,
                        quickActive = view is PanelView.Quick,
                        onQuick = { navigate(PanelView.Quick) },
                        onSection = { navigate(PanelView.Section(it)) },
                        onClose = onDismiss,
                    )
                }
            }
        }
    }
}

/** The narrow strip of section icons — this is the "menu" the swipe brings up. */
@Composable
private fun SectionRail(
    categories: List<CategoryEntity>,
    showQuick: Boolean,
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
            if (showQuick) {
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
    confirmedItems: Set<String>,
    showQuickAccess: Boolean,
    onNotify: (String) -> Unit,
    onNavigate: (PanelView) -> Unit,
    onRequestUnlock: (String?) -> Unit,
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
            showBack = when (view) {
                is PanelView.Quick -> false
                // A section is the root once the star is gone; there is nowhere back to.
                is PanelView.Section -> showQuickAccess
                is PanelView.Entry -> true
            },
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

                // Logins and documents ask for the fingerprint again, every time the bar
                // opens them — the panel is drawn over whatever app is in front.
                val guarded = details != null &&
                    Seed.guardedTemplateIds.contains(details.item.templateId.orEmpty()) &&
                    !confirmedItems.contains(details.item.id)

                if (details == null) {
                    Spacer(Modifier.height(120.dp))
                } else if (guarded) {
                    GuardedSheet(
                        title = details.item.title,
                        onConfirm = { onRequestUnlock(details.item.id) },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = maxListHeight),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item {
                            Row(
                                modifier = Modifier.padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = details.item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (details.fields.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            val all = details.sortedFields.joinToString("\n") {
                                                "${it.label}: ${it.value}"
                                            }
                                            onCopy("All details", all, true)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Copy all",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
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
                                PanelImage(
                                    attachment = attachment,
                                    onCopied = { onNotify(attachment.caption.ifBlank { attachment.displayName }) },
                                    onShared = onClose,
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

/**
 * What a login or a document shows in the floating bar until the fingerprint comes back. The
 * bar draws on top of whatever app is open, so the one place a password should never appear
 * unasked is right here.
 */
@Composable
private fun GuardedSheet(title: String, onConfirm: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Confirm it is you to see this one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onConfirm) { Text("Use fingerprint") }
    }
}

/**
 * A stored picture in the bar, with the two things worth doing to it from inside another app:
 * copy it so it can be pasted straight into a chat, or hand it to the share sheet.
 */
@Composable
private fun PanelImage(
    attachment: AttachmentEntity,
    onCopied: () -> Unit,
    onShared: () -> Unit,
) {
    val actions = rememberAttachmentActions()
    val label = attachment.caption.ifBlank { attachment.displayName }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EncryptedImage(
            storedName = attachment.storedName,
            contentDescription = attachment.displayName,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(
                onClick = {
                    actions.copy(attachment.storedName, label, attachment.mimeType) { ok ->
                        if (ok) onCopied()
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text("Copy", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalButton(
                onClick = {
                    actions.share(attachment.storedName, label, attachment.mimeType) { ok ->
                        // The bar sits over everything, including the share sheet.
                        if (ok) onShared()
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text("Share", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
