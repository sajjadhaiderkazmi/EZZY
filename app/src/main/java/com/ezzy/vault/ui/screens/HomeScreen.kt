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
import androidx.compose.ui.draw.clip
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
import com.ezzy.vault.data.db.CategoryWithCount
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.ui.components.EmptyState
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.components.SectionHeader
import com.ezzy.vault.ui.components.StatCard
import com.ezzy.vault.ui.icons.EzzyMark
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

    val recent: StateFlow<List<ItemWithDetails>> = repository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val itemCount: StateFlow<Int> = repository.observeItemCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
    onAddItem: () -> Unit,
    onAddCategory: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = ezzyViewModel { HomeViewModel(it) }
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val itemCount by viewModel.itemCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canUseBiometrics = remember { AppLock.canAuthenticate(context) }

    val quickAccess = (pinned + recent.filterNot { r -> pinned.any { it.item.id == r.item.id } })
        .take(8)
    val categoryLookup = categories.associateBy { it.category.id }

    // Sections can be dragged into whatever order the user wants. While a drag is running the
    // grid follows this local list instead of the database, so the cards move under the finger
    // straight away; the order is written once, on drop.
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
        topBar = {
            TopAppBar(
                // The bar used to carry nothing but the search icon, leaving the whole left
                // side blank. The name and the promise underneath it fill that space and say
                // what the app is every time it opens.
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(EzzyMark.Brand),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = EzzyMark.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "EZZY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Your Digital Wallet",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            HomeBottomBar(onAdd = onAddItem, onSettings = onOpenSettings)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = buildString {
                        append(greeting)
                        if (settings.displayName.isNotBlank()) append(", ${settings.displayName}")
                        append(" 👋")
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            item(span = { GridItemSpan(1) }) {
                StatCard(value = "$itemCount", label = if (itemCount == 1) "Total item" else "Total items")
            }
            item(span = { GridItemSpan(1) }) {
                StatCard(value = "${pinned.size}", label = "Pinned")
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

            if (quickAccess.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        text = "Quick access",
                        modifier = Modifier.padding(top = 4.dp),
                        trailing = {
                            TextButton(
                                onClick = onOpenSearch,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("See all", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(quickAccess, key = { it.item.id }) { entry ->
                            QuickAccessCard(
                                item = entry,
                                iconKey = categoryLookup[entry.item.categoryId]?.category?.iconKey,
                                colorKey = categoryLookup[entry.item.categoryId]?.category?.colorKey,
                                onClick = { onOpenItem(entry.item.id) },
                            )
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    text = "My Sections",
                    modifier = Modifier.padding(top = 8.dp),
                    trailing = {
                        IconButton(onClick = onAddCategory, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = "New section",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }

            if (order.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Hold a section to drag it into place",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        }
    }
}

/**
 * Home's own bottom bar: Home (already here), a raised Add button for the single most common
 * action, and Settings. Sub-screens keep the back-arrow top bar they already had — this bar is
 * Home-only, not a persistent app-wide shell.
 *
 * There is deliberately no panel behind the actions. A filled bar drew a lighter band with a
 * hard top edge running out either side of the round Add button, which read as a strip stuck
 * across the bottom of the page. The actions now sit straight on the page background; the
 * Scaffold still reserves this whole height, so the grid never scrolls underneath them.
 */
@Composable
private fun HomeBottomBar(onAdd: () -> Unit, onSettings: () -> Unit) {
    val barHeight = 66.dp
    val addSize = 58.dp
    // How far the button stands proud of the action row, and the extra room the Box needs.
    val lift = 24.dp
    // Headroom above the button so its drop shadow has somewhere to land.
    val headroom = 8.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(barHeight + lift + headroom),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(barHeight)
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarAction(
                icon = Icons.Rounded.Home,
                label = "Home",
                selected = true,
                onClick = {},
            )
            // Keeps the two labels clear of the raised button sitting between them.
            Spacer(Modifier.width(addSize))
            BottomBarAction(
                icon = Icons.Rounded.Settings,
                label = "Settings",
                selected = false,
                onClick = onSettings,
            )
        }

        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = headroom)
                .size(addSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add entry",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomBarAction(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

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
        shadowElevation = if (dragging) 10.dp else 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(14.dp),
        ) {
            IconAvatar(
                iconKey = row.category.iconKey,
                colorKey = row.category.colorKey,
                size = 46.dp,
                iconSize = 23.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = row.category.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when (row.itemCount) {
                    0 -> "Empty"
                    1 -> "1 entry"
                    else -> "${row.itemCount} entries"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickAccessCard(
    item: ItemWithDetails,
    iconKey: String?,
    colorKey: String?,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(150.dp),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            IconAvatar(iconKey = iconKey, colorKey = colorKey, size = 36.dp, iconSize = 18.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.item.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = quickAccessSummary(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What a quick-access card says under the title. An entry made only of photos or voice notes has
 * no fields at all, so counting fields alone printed a bare "0 fields" for it.
 */
private fun quickAccessSummary(item: ItemWithDetails): String {
    val fields = item.fields.size
    val files = item.attachments.size
    val parts = buildList {
        if (fields > 0) add(if (fields == 1) "1 field" else "$fields fields")
        if (files > 0) add(if (files == 1) "1 file" else "$files files")
    }
    return if (parts.isEmpty()) "Empty" else parts.joinToString(" · ")
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

/**
 * Which section the finger is over right now, or -1 when it is over the header, a stat card or
 * nothing at all. Only the section cards carry a category id as their key, so hitting one of
 * those is what makes a swap.
 */
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
