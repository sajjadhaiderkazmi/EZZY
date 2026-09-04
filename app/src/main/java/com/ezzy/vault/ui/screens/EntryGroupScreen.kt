package com.ezzy.vault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.ItemGroupEntity
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.ui.components.EmptyState
import com.ezzy.vault.ui.components.ItemRow
import com.ezzy.vault.ui.ezzyViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class EntryGroupViewModel(
    private val container: AppContainer,
    private val groupId: String,
) : ViewModel() {

    val group: StateFlow<ItemGroupEntity?> = container.repository.observeItemGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<ItemWithDetails>> = container.repository.observeGroupItems(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The section this group lives in — an entry row needs its icon and colour. */
    val category: StateFlow<CategoryEntity?> = group
        .filterNotNull()
        .flatMapLatest { container.repository.observeCategory(it.categoryId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The rest of this group's own section — what "Add entries" can pick from. */
    val ungroupedItems: StateFlow<List<ItemWithDetails>> = group
        .filterNotNull()
        .flatMapLatest { container.repository.observeUngroupedItems(it.categoryId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addItems(itemIds: Set<String>) {
        viewModelScope.launch {
            itemIds.forEach { container.repository.setItemGroup(it, groupId) }
        }
    }

    fun removeFromGroup(itemId: String) {
        viewModelScope.launch { container.repository.setItemGroup(itemId, null) }
    }
}

/**
 * What tapping a folder row inside a section opens: the entries filed inside it. The Edit icon
 * turns the list into a manage mode — a cross to take an entry back out — everything else about
 * opening or working with an entry is unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryGroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val viewModel: EntryGroupViewModel = ezzyViewModel(key = "entryGroup-$groupId") {
        EntryGroupViewModel(it, groupId)
    }
    val group by viewModel.group.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val ungroupedItems by viewModel.ungroupedItems.collectAsStateWithLifecycle()

    var editMode by remember { mutableStateOf(false) }
    var addingItems by remember { mutableStateOf(false) }

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
                    if (items.isNotEmpty()) {
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
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Folder,
                title = "Nothing here yet",
                message = "Drag an entry onto this folder from the section, or add one below.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                action = {
                    Button(onClick = { addingItems = true }) { Text("Add entries") }
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
                        text = "Tap the cross to take an entry out of this group",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.item.id }) { entry ->
                        val id = entry.item.id
                        Box {
                            ItemRow(
                                item = entry,
                                iconKey = category?.iconKey,
                                colorKey = category?.colorKey,
                                onClick = { if (!editMode) onOpenItem(id) },
                            )
                            if (editMode) {
                                IconButton(
                                    onClick = { viewModel.removeFromGroup(id) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(30.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Take \"${entry.item.title}\" out of the group",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (editMode) {
                    Button(
                        onClick = { addingItems = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add entries")
                    }
                }
            }
        }
    }

    if (addingItems) {
        AddEntriesDialog(
            options = ungroupedItems,
            onDismiss = { addingItems = false },
            onConfirm = { ids ->
                addingItems = false
                viewModel.addItems(ids)
            },
        )
    }
}

/** Picks which of the section's other entries join this group — the reliable path alongside
 *  dragging one in. */
@Composable
private fun AddEntriesDialog(
    options: List<ItemWithDetails>,
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
                    text = "Add entries",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
                )
                if (options.isEmpty()) {
                    Text(
                        text = "Every entry here is already in a group.",
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
                        items(options, key = { it.item.id }) { entry ->
                            val id = entry.item.id
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
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = entry.item.title,
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
