package com.ezzy.vault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.ui.draw.scale
import com.ezzy.vault.ui.components.CircleIconButton
import com.ezzy.vault.ui.components.LimeButton
import com.ezzy.vault.ui.theme.EzzyLime
import com.ezzy.vault.ui.theme.EzzyOnLime
import com.ezzy.vault.ui.theme.accentChip
import com.ezzy.vault.ui.theme.accentOnCard
import com.ezzy.vault.ui.theme.accentCard
import com.ezzy.vault.ui.theme.accentSheen
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.withResumed
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.ItemGroupWithCount
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.ui.LocalSettings
import com.ezzy.vault.ui.components.EmptyState
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.components.ItemRow
import com.ezzy.vault.ui.ezzyViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.ContentCopy
import com.ezzy.vault.ui.rememberCopier
import kotlinx.coroutines.flow.first

class CategoryViewModel(
    private val container: AppContainer,
    private val categoryId: String,
) : ViewModel() {

    val category: StateFlow<CategoryEntity?> = container.repository.observeCategory(categoryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Everything in the section, grouped or not — used for the header's own count. */
    val items: StateFlow<List<ItemWithDetails>> = container.repository.observeItems(categoryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The section's own top level: entries that have not been dragged into one of its groups. */
    val ungroupedItems: StateFlow<List<ItemWithDetails>> =
        container.repository.observeUngroupedItems(categoryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<ItemGroupWithCount>> = container.repository.observeItemGroups(categoryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteCategory(onDone: () -> Unit) {
        viewModelScope.launch {
            container.repository.deleteCategory(categoryId)
            onDone()
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch { container.repository.createItemGroup(categoryId, name) }
    }

    fun renameGroup(id: String, name: String) {
        viewModelScope.launch { container.repository.renameItemGroup(id, name) }
    }

    /** Dissolves the group; every entry it held returns to the section's top level. */
    fun ungroup(id: String) {
        viewModelScope.launch { container.repository.ungroupItems(id) }
    }

    /** Deletes the group and everything that was still inside it. */
    fun deleteGroup(id: String) {
        viewModelScope.launch { container.repository.deleteItemGroupAndContents(id) }
    }

    /**
     * Builds the text for copying a whole group: each entry's title, then its details one per
     * line, with a blank line between entries.
     */
    fun copyGroup(id: String, onText: (String) -> Unit) {
        viewModelScope.launch {
            val text = container.repository.observeGroupItems(id).first()
                .joinToString("\n\n") { entry ->
                    (listOf(entry.item.title) + entry.sortedFields.map { "${it.label}: ${it.value}" })
                        .joinToString("\n")
                }
            onText(text)
        }
    }

    fun addToGroup(itemId: String, groupId: String) {
        viewModelScope.launch { container.repository.setItemGroup(itemId, groupId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categoryId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onAddItem: () -> Unit,
    onEditCategory: () -> Unit,
) {
    val viewModel: CategoryViewModel = ezzyViewModel(key = "category-$categoryId") {
        CategoryViewModel(it, categoryId)
    }
    val category by viewModel.category.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val ungroupedItems by viewModel.ungroupedItems.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val copy = rememberCopier()
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // Entries can be dragged onto a group's folder row to file them there. While a drag is
    // running the list follows this local copy instead of the database, so the dragged row
    // leaves the top level straight away; the change is written once, on drop.
    val listState = rememberLazyListState()
    var order by remember { mutableStateOf(ungroupedItems) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var hoverGroupId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(ungroupedItems) {
        if (draggingId == null) order = ungroupedItems
    }

    var creatingGroup by remember { mutableStateOf(false) }
    var renamingGroup by remember { mutableStateOf<ItemGroupWithCount?>(null) }
    var confirmDeleteGroup by remember { mutableStateOf<ItemGroupWithCount?>(null) }

    // Not persisted anywhere: this is local composition state, so navigating away and back in
    // — the only way to leave this screen at all — always starts a locked section locked again,
    // regardless of whether the vault itself stayed unlocked the whole time.
    val settings = LocalSettings.current
    val requiresUnlock = categoryId in settings.lockedSections
    var unlocked by remember { mutableStateOf(!requiresUnlock) }

    if (requiresUnlock && !unlocked) {
        SectionLockGate(
            iconKey = category?.iconKey,
            colorKey = category?.colorKey,
            name = category?.name ?: "Section",
            onBack = onBack,
            onUnlocked = { unlocked = true },
        )
        return
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                CircleIconButton(
                    icon = Icons.Rounded.CreateNewFolder,
                    contentDescription = "New group",
                    onClick = { creatingGroup = true },
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    CircleIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "Section options",
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit section") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Edit, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onEditCategory()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete section", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            LimeButton(
                text = "Add entry",
                icon = Icons.Rounded.Add,
                onClick = onAddItem,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (items.isEmpty() && groups.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Inbox,
                title = "Nothing saved here yet",
                message = "Add your first entry and it will show up in the floating bar under this section.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                action = {
                    Button(onClick = onAddItem) { Text("Add") }
                },
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "banner") {
                    SectionBanner(
                        category = category,
                        entryCount = items.size,
                        groupCount = groups.size,
                    )
                }

                if (groups.isNotEmpty()) {
                    item(key = "hint") {
                        Text(
                            text = "Tip: hold an entry and drag it onto a folder to file it there.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }

                items(groups, key = { GROUP_KEY_PREFIX + it.group.id }) { row ->
                    ItemGroupRow(
                        row = row,
                        highlighted = hoverGroupId == row.group.id,
                        onClick = { onOpenGroup(row.group.id) },
                        onRename = { renamingGroup = row },
                        onUngroup = { viewModel.ungroup(row.group.id) },
                        onDelete = { confirmDeleteGroup = row },
                        onCopy = {
                            viewModel.copyGroup(row.group.id) { text ->
                                copy(row.group.name, text, true)
                            }
                        },
                    )
                }

                items(order, key = { it.item.id }) { entry ->
                    val id = entry.item.id
                    ItemRow(
                        item = entry,
                        iconKey = category?.iconKey,
                        colorKey = category?.colorKey,
                        onClick = { onOpenItem(id) },
                        dragging = draggingId == id,
                        modifier = if (groups.isEmpty()) {
                            Modifier
                        } else {
                            Modifier.pointerInput(id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { insideCard ->
                                        draggingId = id
                                        pointer = rowOrigin(listState, id) + insideCard
                                    },
                                    onDragEnd = {
                                        val group = hoverGroupId
                                        if (group != null) {
                                            // Dropped on a folder: the row leaves the top level
                                            // straight away rather than waiting on the database
                                            // round trip to catch up.
                                            viewModel.addToGroup(id, group)
                                            order = order.filterNot { it.item.id == id }
                                        }
                                        draggingId = null
                                        hoverGroupId = null
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        hoverGroupId = null
                                    },
                                    onDrag = { _, amount ->
                                        pointer += amount
                                        val key = keyUnder(listState, pointer) as? String
                                        hoverGroupId = key
                                            ?.takeIf { it.startsWith(GROUP_KEY_PREFIX) }
                                            ?.removePrefix(GROUP_KEY_PREFIX)
                                    },
                                )
                            }
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
                    if (target.itemCount == 0) {
                        "This empty group will be removed."
                    } else {
                        "This group and the ${target.itemCount} " +
                            "${if (target.itemCount == 1) "entry" else "entries"} inside it will be " +
                            "deleted permanently. This cannot be undone."
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this section?") },
            text = {
                Text(
                    "\"${category?.name.orEmpty()}\" and all ${items.size} entries inside it will be " +
                        "removed permanently, along with their files. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteCategory(onBack)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * A group's folder row — a tap opens it, a long press offers Delete, Ungroup and Edit. Tap and
 * long-press share no gesture with the drag an entry uses to file itself in here, so a folder
 * never has to guess which one the user meant.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemGroupRow(
    row: ItemGroupWithCount,
    highlighted: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onUngroup: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    // A real folder: a lime tile with a folder glyph, the count as a pill, and a lime outline
    // that lights up (and the card grows a touch) while a dragged entry hovers over it.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (highlighted) 1.03f else 1f),
        shape = MaterialTheme.shapes.large,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (highlighted) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Box {
            Row(
                modifier = Modifier
                    .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                    .padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EzzyLime),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (highlighted) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = EzzyOnLime,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.group.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (highlighted) "Drop to file it here" else "Folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        text = when (row.itemCount) {
                            0 -> "Empty"
                            1 -> "1 entry"
                            else -> "${row.itemCount} entries"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (row.itemCount > 0) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = { menuOpen = false; onCopy() },
                    )
                }
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

/**
 * What a section marked "locked" shows instead of its contents. Prompts the moment the screen
 * is genuinely on screen — the same beat [LockScreen] waits for, since the biometric prompt is
 * a fragment transaction and needs the activity properly resumed first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionLockGate(
    iconKey: String?,
    colorKey: String?,
    name: String,
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var error by remember { mutableStateOf<String?>(null) }

    fun tryUnlock() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            // Should never happen inside the main nav host, but a silent unlock on a cast
            // failure would defeat the whole point of the lock — fail closed instead.
            error = "Could not open the unlock prompt"
            return
        }
        error = null
        AppLock.prompt(
            activity = activity,
            title = "Unlock \"$name\"",
            subtitle = "This section asks again every time it's opened",
            onSuccess = onUnlocked,
            onFailure = { error = it },
        )
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.withResumed { tryUnlock() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IconAvatar(iconKey = iconKey, colorKey = colorKey, size = 72.dp, iconSize = 34.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "This section is locked",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))
            Button(onClick = { tryUnlock() }) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("Unlock")
            }
        }
    }
}

/** Where the list has laid one entry row out, in the list's own coordinates. */
private fun rowOrigin(state: LazyListState, key: String): Offset {
    val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return Offset.Zero
    return Offset(0f, info.offset.toFloat())
}

/**
 * The key of whatever row the finger is over right now — a `"group:"`-prefixed group id, an
 * entry id, or null when it is over nothing a drag can land on. Only the vertical position
 * matters: every row in this list spans the full width.
 */
private fun keyUnder(state: LazyListState, point: Offset): Any? =
    state.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        point.y >= info.offset && point.y <= info.offset + info.size
    }?.key

/** Prefixes a group's list key so it can never collide with an entry id (a plain UUID). */
private const val GROUP_KEY_PREFIX = "group:"

/** The top of a section: its icon, name and what it holds, on a wash of its own colour. */
@Composable
private fun SectionBanner(category: CategoryEntity?, entryCount: Int, groupCount: Int) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = accentCard(category?.colorKey),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentColor = accentOnCard(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .background(accentSheen())
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAvatar(
                iconKey = category?.iconKey,
                colorKey = category?.colorKey,
                size = 60.dp,
                iconSize = 30.dp,
                onCard = true,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category?.name ?: "Section",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BannerChip(if (entryCount == 1) "1 entry" else "$entryCount entries")
                    if (groupCount > 0) {
                        BannerChip(if (groupCount == 1) "1 folder" else "$groupCount folders")
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerChip(text: String) {
    Surface(shape = CircleShape, color = accentChip(), contentColor = accentOnCard()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
