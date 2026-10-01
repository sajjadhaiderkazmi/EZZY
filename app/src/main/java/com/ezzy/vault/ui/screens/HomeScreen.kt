package com.ezzy.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import com.ezzy.vault.ui.theme.EzzyGreen
import com.ezzy.vault.ui.theme.EzzyGreenBright
import com.ezzy.vault.ui.components.ItemRow
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.BorderStroke
import com.ezzy.vault.ui.theme.LocalIsDarkTheme
import com.ezzy.vault.ui.components.bleedHorizontally
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.CategoryWithCount
import com.ezzy.vault.data.db.ItemGroupEntity
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.ui.components.EmptyState
import com.ezzy.vault.ui.components.GROUP_ICON_KEY
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.components.QuickKind
import com.ezzy.vault.ui.components.QuickTarget
import com.ezzy.vault.ui.components.SectionHeader
import com.ezzy.vault.ui.icons.EzzyMark
import com.ezzy.vault.ui.theme.EzzyLime
import com.ezzy.vault.ui.theme.EzzyOnLime
import com.ezzy.vault.ui.theme.accentChip
import com.ezzy.vault.ui.theme.accentOnCard
import com.ezzy.vault.ui.theme.accentCard
import com.ezzy.vault.ui.theme.accentSheen
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.util.EzzySettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(container: AppContainer) : ViewModel() {

    private val repository = container.repository

    val categories: StateFlow<List<CategoryWithCount>> = repository.observeCategoriesWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinned: StateFlow<List<ItemWithDetails>> = repository.observePinned()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinnedCategories: StateFlow<List<CategoryEntity>> = repository.observePinnedCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinnedGroups: StateFlow<List<ItemGroupEntity>> = repository.observePinnedGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val itemCount: StateFlow<Int> = repository.observeItemCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The last few entries touched, for "Recently opened" at the bottom of Home. */
    val recent: StateFlow<List<ItemWithDetails>> = repository.observeRecent(3)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val settings = container.settings

    fun enableBiometricLock() {
        viewModelScope.launch { settings.setBiometricLock(true) }
    }

    fun reorderCategories(orderedIds: List<String>) {
        viewModelScope.launch { repository.reorderCategories(orderedIds) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: EzzySettings,
    onOpenCategory: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenQuickAccess: () -> Unit,
    onAddItem: () -> Unit,
    onAddCategory: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = ezzyViewModel { HomeViewModel(it) }
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val pinnedCategories by viewModel.pinnedCategories.collectAsStateWithLifecycle()
    val pinnedGroups by viewModel.pinnedGroups.collectAsStateWithLifecycle()
    val itemCount by viewModel.itemCount.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canUseBiometrics = remember { AppLock.canAuthenticate(context) }

    val categoryLookup = categories.associateBy { it.category.id }

    // Quick access holds three different things now — sections, groups and entries — so they
    // are flattened to one shelf here. Only what the user actually put there shows up: nothing
    // arrives on its own, so what is on this shelf always matches what is switched on under
    // "See all", and taking something off is always possible.
    val quickAccess = buildList {
        pinnedCategories.forEach { section ->
            add(
                QuickTarget(
                    id = section.id,
                    kind = QuickKind.SECTION,
                    title = section.name,
                    subtitle = "",
                    iconKey = section.iconKey,
                    colorKey = section.colorKey,
                )
            )
        }
        pinnedGroups.forEach { group ->
            add(
                QuickTarget(
                    id = group.id,
                    kind = QuickKind.GROUP,
                    title = group.name,
                    subtitle = "",
                    iconKey = GROUP_ICON_KEY,
                    // A group borrows its section's colour, so it still reads as part of it.
                    colorKey = categoryLookup[group.categoryId]?.category?.colorKey,
                )
            )
        }
        pinned.forEach { entry ->
            add(
                QuickTarget(
                    id = entry.item.id,
                    kind = QuickKind.ENTRY,
                    title = entry.item.title,
                    subtitle = "",
                    iconKey = categoryLookup[entry.item.categoryId]?.category?.iconKey,
                    colorKey = categoryLookup[entry.item.categoryId]?.category?.colorKey,
                    photoStoredName = entry.item.iconPhoto,
                )
            )
        }
    }

    // Sections can be dragged into whatever order the user wants. While a drag is running the
    // grid follows this local list instead of the database, so the cards move under the finger
    // straight away; the change is written once, on drop.
    val gridState = rememberLazyGridState()
    var order by remember { mutableStateOf(categories) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(categories) {
        if (draggingId == null) order = categories
    }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }

    Scaffold(
        bottomBar = {
            HomeBottomBar(
                onAdd = onAddItem,
                onPinned = onOpenQuickAccess,
                onSearch = onOpenSearch,
                onSettings = onOpenSettings,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeHeader(
                    greeting = buildString {
                        append(greeting)
                        if (settings.displayName.isNotBlank()) append(", ${settings.displayName}")
                    },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                BentoTop(
                    itemCount = itemCount,
                    sectionCount = categories.size,
                    pinnedCount = quickAccess.size,
                    onPinned = onOpenQuickAccess,
                    onAddSection = onAddCategory,
                )
            }

            if (!settings.overlayEnabled) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SetupCard(
                        icon = Icons.Rounded.Bolt,
                        title = "Turn on the floating bar",
                        subtitle = "Reach your vault from inside any app",
                        onClick = onOpenSettings,
                    )
                }
            }

            if (!settings.biometricLock) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SetupCard(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Turn on fingerprint vault login",
                        subtitle = if (canUseBiometrics) {
                            "Ask for your fingerprint before opening the vault"
                        } else {
                            "Set a screen lock on this phone first"
                        },
                        enabled = canUseBiometrics,
                        onClick = {
                            viewModel.enableBiometricLock()
                            AppLock.unlock()
                        },
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeLabel(
                    text = "My sections",
                    action = "+ New",
                    onAction = onAddCategory,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (categories.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Rounded.FolderOpen,
                        title = "No sections yet",
                        message = "Sections are the icons you will see in the floating bar. Create one for banks, documents, receipts — whatever you need.",
                    )
                }
            } else {
                items(order, key = { it.category.id }) { row ->
                    val id = row.category.id
                    CategoryCard(
                        row = row,
                        dragging = draggingId == id,
                        onClick = { onOpenCategory(id) },
                        modifier = Modifier.pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { insideCard ->
                                    draggingId = id
                                    pointer = cardOrigin(gridState, id) + insideCard
                                },
                                onDragEnd = {
                                    if (draggingId != null) {
                                        viewModel.reorderCategories(order.map { it.category.id })
                                    }
                                    draggingId = null
                                },
                                onDragCancel = { draggingId = null },
                                onDrag = { _, amount ->
                                    // The card is never translated, only reordered, so the
                                    // finger's position is tracked here rather than read back
                                    // out of a layout that keeps moving underneath it.
                                    pointer += amount
                                    val from = order.indexOfFirst { it.category.id == draggingId }
                                    val to = cardIndexUnder(gridState, order, pointer)
                                    if (from >= 0 && to >= 0 && from != to) {
                                        order = order.toMutableList()
                                            .apply { add(to, removeAt(from)) }
                                    }
                                },
                            )
                        },
                    )
                }
            }

            if (recent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeLabel(
                        text = "Recently opened",
                        action = "See all",
                        onAction = onOpenSearch,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                items(recent, key = { "recent-" + it.item.id }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                    val section = categoryLookup[entry.item.categoryId]?.category
                    ItemRow(
                        item = entry,
                        iconKey = section?.iconKey,
                        colorKey = section?.colorKey,
                        onClick = { onOpenItem(entry.item.id) },
                    )
                }
            }
        }
        // A solid strip behind the clock, so tiles scrolling up pass under it instead of
        // showing through the status bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background),
        )
      }
    }
}

/**
 * The top of Home: the greeting over "My Vault". Search and Add live in the bottom bar, so
 * they aren't repeated up here.
 */
@Composable
private fun HomeHeader(greeting: String) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "My Vault",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A small uppercase heading with an optional green action on the right. */
@Composable
private fun HomeLabel(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * The bento block: one tall green tile with the vault total on the left, and two smaller
 * tiles stacked beside it — Pinned and a new section.
 */
@Composable
private fun BentoTop(
    itemCount: Int,
    sectionCount: Int,
    pinnedCount: Int,
    onPinned: () -> Unit,
    onAddSection: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Encrypted",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$itemCount",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = if (itemCount == 1) "entry safe" else "entries safe",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (sectionCount == 1) "1 section" else "$sectionCount sections",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            BentoTile(
                icon = Icons.Rounded.PushPin,
                title = "Pinned",
                caption = if (pinnedCount == 1) "1 item" else "$pinnedCount items",
                onClick = onPinned,
            )
            BentoTile(
                icon = Icons.Rounded.CreateNewFolder,
                title = "New section",
                caption = "Banks, IDs, bills…",
                onClick = onAddSection,
            )
        }
    }
}

@Composable
private fun BentoTile(icon: ImageVector, title: String, caption: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One section in the grid: a white tile with its green icon, name and how many entries. */
@Composable
private fun CategoryCard(
    row: CategoryWithCount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (dragging) 10.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .scale(if (dragging) 1.04f else 1f),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(14.dp),
        ) {
            IconAvatar(
                iconKey = row.category.iconKey,
                colorKey = row.category.colorKey,
                size = 40.dp,
                iconSize = 21.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = row.category.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (row.itemCount == 1) "1 entry" else "${row.itemCount} entries",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Home's bottom bar: a plain card-coloured bar running down behind the gesture area, with
 * Home in the middle as a dark pill. Pinned and Search sit to its left, Add and Settings to
 * its right.
 */
@Composable
private fun HomeBottomBar(
    onAdd: () -> Unit,
    onPinned: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomBarAction(Icons.Rounded.PushPin, "Pinned", onPinned, Modifier.weight(1f))
                BottomBarAction(Icons.Rounded.Search, "Search", onSearch, Modifier.weight(1f))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1.3f)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = null,
                                tint = if (LocalIsDarkTheme.current) EzzyGreen else EzzyGreenBright,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Home", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                BottomBarAction(Icons.Rounded.Add, "Add", onAdd, Modifier.weight(1f))
                BottomBarAction(Icons.Rounded.Settings, "Settings", onSettings, Modifier.weight(1f))
            }
            Spacer(Modifier.fillMaxWidth().navigationBarsPadding())
        }
    }
}

@Composable
private fun BottomBarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

/** The one-tap setup prompts on the home screen: the floating bar, then the fingerprint lock. */
@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val foreground = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(foreground.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = foreground,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = foreground.copy(alpha = 0.8f),
                )
            }
            if (enabled) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = foreground,
                )
            }
        }
    }
}

/** Where the grid has laid one section card out, in the grid's own coordinates. */
private fun cardOrigin(state: LazyGridState, key: String): Offset {
    val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        ?: return Offset.Zero
    return Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
}

/** Which section card the finger is over right now, as an index into [order]. */
private fun cardIndexUnder(
    state: LazyGridState,
    order: List<CategoryWithCount>,
    point: Offset,
): Int {
    val hit = state.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        point.x >= info.offset.x &&
            point.x <= info.offset.x + info.size.width &&
            point.y >= info.offset.y &&
            point.y <= info.offset.y + info.size.height
    } ?: return -1
    return order.indexOfFirst { it.category.id == hit.key }
}

/** The hero card's ground: the launcher icon's ink, a touch lifted so it reads as a card. */
