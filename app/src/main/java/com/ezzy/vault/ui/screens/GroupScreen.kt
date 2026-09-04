package com.ezzy.vault.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.CategoryGroupEntity
import com.ezzy.vault.data.db.CategoryWithCount
import com.ezzy.vault.ui.components.EmptyState
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.ezzyViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupViewModel(
    private val container: AppContainer,
    private val groupId: String,
) : ViewModel() {

    val group: StateFlow<CategoryGroupEntity?> = container.repository.observeGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<CategoryWithCount>> =
        container.repository.observeGroupCategories(groupId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything still at the home grid's top level — what "Add sections" can pick from. */
    val ungroupedCategories: StateFlow<List<CategoryWithCount>> =
        container.repository.observeUngroupedCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addSections(categoryIds: Set<String>) {
        viewModelScope.launch {
            categoryIds.forEach { container.repository.setCategoryGroup(it, groupId) }
        }
    }

    fun removeFromGroup(categoryId: String) {
        viewModelScope.launch { container.repository.setCategoryGroup(categoryId, null) }
    }

    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { container.repository.reorderCategories(orderedIds) }
    }
}

/**
 * What tapping a folder card on the home screen opens: the sections filed inside it. The Edit
 * icon turns the grid into a manage mode — a cross to take a section back out, a long press to
 * reorder — everything else about opening or working inside a section is unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    val viewModel: GroupViewModel = ezzyViewModel(key = "group-$groupId") {
        GroupViewModel(it, groupId)
    }
    val group by viewModel.group.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val ungroupedCategories by viewModel.ungroupedCategories.collectAsStateWithLifecycle()

    var editMode by remember { mutableStateOf(false) }
    var addingSections by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    var order by remember { mutableStateOf(categories) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(categories) {
        if (draggingId == null) order = categories
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (categories.isNotEmpty()) {
                        IconButton(onClick = { editMode = !editMode }) {
                            Icon(
                                imageVector = if (editMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                                contentDescription = if (editMode) "Done editing" else "Edit group",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (categories.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Folder,
                title = "Nothing here yet",
                message = "Drag a section onto this folder from the home screen, or add one below.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                action = {
                    Button(onClick = { addingSections = true }) { Text("Add sections") }
                },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (editMode) {
                    Text(
                        text = "Hold a section to reorder it, or tap the cross to take it out",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(order, key = { it.category.id }) { row ->
                        val id = row.category.id
                        GroupMemberCard(
                            row = row,
                            editMode = editMode,
                            dragging = draggingId == id,
                            onClick = { if (!editMode) onOpenCategory(id) },
                            onRemove = { viewModel.removeFromGroup(id) },
                            modifier = if (!editMode) {
                                Modifier
                            } else {
                                Modifier.pointerInput(id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { insideCard ->
                                            draggingId = id
                                            pointer = cardOrigin(gridState, id) + insideCard
                                        },
                                        onDragEnd = {
                                            if (draggingId != null) {
                                                viewModel.reorder(order.map { it.category.id })
                                            }
                                            draggingId = null
                                        },
                                        onDragCancel = { draggingId = null },
                                        onDrag = { _, amount ->
                                            pointer += amount
                                            val from = order.indexOfFirst { it.category.id == draggingId }
                                            val to = cardIndexUnder(gridState, order, pointer)
                                            if (from >= 0 && to >= 0 && from != to) {
                                                order = order.toMutableList()
                                                    .apply { add(to, removeAt(from)) }
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    }
                }

                if (editMode) {
                    Button(
                        onClick = { addingSections = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add sections")
                    }
                }
            }
        }
    }

    if (addingSections) {
        AddSectionsDialog(
            options = ungroupedCategories,
            onDismiss = { addingSections = false },
            onConfirm = { ids ->
                addingSections = false
                viewModel.addSections(ids)
            },
        )
    }
}

@Composable
private fun GroupMemberCard(
    row: CategoryWithCount,
    editMode: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = if (dragging) 10.dp else 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .clickable(enabled = !editMode, onClick = onClick)
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
            if (editMode) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Take \"${row.category.name}\" out of the group",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Picks which ungrouped sections join this group — the reliable path alongside dragging one in. */
@Composable
private fun AddSectionsDialog(
    options: List<CategoryWithCount>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            Column {
                Text(
                    text = "Add sections",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
                )
                if (options.isEmpty()) {
                    Text(
                        text = "Every section is already in a group.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = 12.dp),
                    ) {
                        items(options, key = { it.category.id }) { row ->
                            val id = row.category.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (id in selected) selected - id else selected + id
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = id in selected, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                IconAvatar(
                                    iconKey = row.category.iconKey,
                                    colorKey = row.category.colorKey,
                                    size = 32.dp,
                                    iconSize = 16.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = row.category.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onConfirm(selected) },
                        enabled = selected.isNotEmpty(),
                    ) { Text("Add") }
                }
            }
        }
    }
}

private fun cardOrigin(state: LazyGridState, key: String): Offset {
    val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        ?: return Offset.Zero
    return Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
}

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
