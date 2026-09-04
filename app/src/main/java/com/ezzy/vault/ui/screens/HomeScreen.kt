package com.ezzy.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.CategoryGroupWithCount
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

    // Every section, grouped or not — this is what a quick-access card looks its icon and
    // colour up in, and grouping a section must never make its entries stop finding one.
    val allCategories: StateFlow<List<CategoryWithCount>> = repository.observeCategoriesWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The home grid's own top level: sections not sitting inside a group.
    val categories: StateFlow<List<CategoryWithCount>> = repository.observeUngroupedCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<CategoryGroupWithCount>> = repository.observeGroups()
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

    fun createGroup(name: String) {
        viewModelScope.launch { repository.createGroup(name) }
    }

    fun renameGroup(id: String, name: String) {
        viewModelScope.launch { repository.renameGroup(id, name) }
    }

    /** Dissolves the group; every section it held returns to the home grid's top level. */
    fun ungroup(id: String) {
        viewModelScope.launch { repository.ungroup(id) }
    }

    /** Deletes the group and everything that was still inside it. */
    fun deleteGroup(id: String) {
        viewModelScope.launch { repository.deleteGroupAndContents(id) }
    }

    fun addToGroup(categoryId: String, groupId: String) {
        viewModelScope.launch { repository.setCategoryGroup(categoryId, groupId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: EzzySettings,
    onOpenCategory: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onAddItem: () -> Unit,
    onAddCategory: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = ezzyViewModel { HomeViewModel(it) }
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val itemCount by viewModel.itemCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canUseBiometrics = remember { AppLock.canAuthenticate(context) }

    val quickAccess = (pinned + recent.filterNot { r -> pinned.any { it.item.id == r.item.id } })
        .take(8)
    // Grouping a section is purely cosmetic on the home grid — its quick-access card still
    // needs its icon and colour, so the lookup covers every section, not just the ungrouped
    // ones the grid itself renders.
    val categoryLookup = allCategories.associateBy { it.category.id }

    // Sections can be dragged into whatever order the user wants, or onto a group's folder
    // card to move into it. While a drag is running the grid follows this local list instead
    // of the database, so the cards move under the finger straight away; the change is written
    // once, on drop.
    val gridState = rememberLazyGridState()
    var order by remember { mutableStateOf(categories) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var hoverGroupId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(categories) {
        if (draggingId == null) order = categories
    }

    var creatingGroup by remember { mutableStateOf(false) }
    var renamingGroup by remember { mutableStateOf<CategoryGroupWithCount?>(null) }
    var confirmDeleteGroup by remember { mutableStateOf<CategoryGroupWithCount?>(null) }

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
                    // The icons on their own never said what they did. The label does.
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { creatingGroup = true },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CreateNewFolder,
                                    contentDescription = "New group",
                                    modifier = Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = onAddCategory,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "Create Section",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                )
            }

            if (order.size > 1 || groups.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Hold a section to drag it — onto a group to file it there, " +
                            "or between others to reorder",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(groups, key = { GROUP_KEY_PREFIX + it.group.id }) { row ->
                GroupCard(
                    row = row,
                    highlighted = hoverGroupId == row.group.id,
                    onClick = { onOpenGroup(row.group.id) },
                    onRename = { renamingGroup = row },
                    onUngroup = { viewModel.ungroup(row.group.id) },
                    onDelete = { confirmDeleteGroup = row },
                )
            }

            if (categories.isEmpty() && groups.isEmpty()) {
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
                                    val group = hoverGroupId
                                    if (group != null) {
                                        // Dropped on a folder: the card leaves the top level
                                        // straight away rather than waiting on the database
                                        // round trip to catch up.
                                        viewModel.addToGroup(id, group)
                                        order = order.filterNot { it.category.id == id }
                                    } else if (draggingId != null) {
                                        viewModel.reorderCategories(order.map { it.category.id })
                                    }
                                    draggingId = null
                                    hoverGroupId = null
                                },
                                onDragCancel = {
                                    draggingId = null
                                    hoverGroupId = null
                                },
                                onDrag = { _, amount ->
                                    // The card is never translated, only reordered, so the
                                    // finger's position is tracked here rather than read back
                                    // out of a layout that keeps moving underneath it.
                                    pointer += amount
                                    val key = keyUnder(gridState, pointer) as? String
                                    hoverGroupId = key
                                        ?.takeIf { it.startsWith(GROUP_KEY_PREFIX) }
                                        ?.removePrefix(GROUP_KEY_PREFIX)
                                    if (hoverGroupId == null) {
                                        val from = order.indexOfFirst { it.category.id == draggingId }
                                        val to = order.indexOfFirst { it.category.id == key }
                                        if (from >= 0 && to >= 0 && from != to) {
                                            order = order.toMutableList()
                                                .apply { add(to, removeAt(from)) }
                                        }
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    if (creatingGroup) {
        GroupNameDialog(
            title = "New group",
            confirmLabel = "Create",
            initial = "",
            onDismiss = { creatingGroup = false },
            onConfirm = { name ->
                creatingGroup = false
                viewModel.createGroup(name)
            },
        )
    }

    renamingGroup?.let { target ->
        GroupNameDialog(
            title = "Rename group",
            confirmLabel = "Save",
            initial = target.group.name,
            onDismiss = { renamingGroup = null },
            onConfirm = { name ->
                renamingGroup = null
                viewModel.renameGroup(target.group.id, name)
            },
        )
    }

    confirmDeleteGroup?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteGroup = null },
            title = { Text("Delete \"${target.group.name}\"?") },
            text = {
                Text(
                    if (target.categoryCount == 0) {
                        "This empty group will be removed."
                    } else {
                        "This group and the ${target.categoryCount} " +
                            "${if (target.categoryCount == 1) "section" else "sections"} inside it — " +
                            "along with everything they hold — will be deleted permanently. " +
                            "This cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteGroup = null
                        viewModel.deleteGroup(target.group.id)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteGroup = null }) { Text("Cancel") }
            },
        )
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
        }
    }
}

/**
 * A group's folder card — a tap opens it, a long press offers Delete, Ungroup and Edit. Tap and
 * long-press share no gesture with the drag a section uses to file itself in here, so a folder
 * never has to guess which one the user meant.
 */
@Composable
private fun GroupCard(
    row: CategoryGroupWithCount,
    highlighted: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onUngroup: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.large,
        // Switches to the accent tint while a dragged section hovers over it — the same cue a
        // chat head's dismiss target gives, so "this is about to accept it" reads clearly.
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                    .padding(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            (if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = row.group.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when (row.categoryCount) {
                        0 -> "Empty"
                        1 -> "1 section"
                        else -> "${row.categoryCount} sections"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = (if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.8f),
                )
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Ungroup") },
                    leadingIcon = { Icon(Icons.Rounded.FolderOff, contentDescription = null) },
                    onClick = { menuOpen = false; onUngroup() },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/** Names a group, for both creating a new one and renaming an existing one. */
@Composable
private fun GroupNameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (name.isNotBlank()) onConfirm(name) }
                    ),
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onConfirm(name) },
                        enabled = name.isNotBlank(),
                    ) { Text(confirmLabel) }
                }
            }
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
            // Always two lines: a row of cards with nothing under the title would otherwise
            // come out ragged, one card short wherever a name happened to fit on one line.
            Text(
                text = item.item.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

/**
 * The key of whatever card the finger is over right now — a category id, a `"group:"`-prefixed
 * group id, or null when it is over the header, a stat card or nothing at all. Section drag
 * reads this once to decide between "reorder" and "file into this group".
 */
private fun keyUnder(state: LazyGridState, point: Offset): Any? =
    state.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        point.x >= info.offset.x &&
            point.x <= info.offset.x + info.size.width &&
            point.y >= info.offset.y &&
            point.y <= info.offset.y + info.size.height
    }?.key

/** Prefixes a group's grid key so it can never collide with a category id (a plain UUID). */
private const val GROUP_KEY_PREFIX = "group:"
