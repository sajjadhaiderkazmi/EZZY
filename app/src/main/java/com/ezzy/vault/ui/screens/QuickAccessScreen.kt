package com.ezzy.vault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.CategoryWithCount
import com.ezzy.vault.data.db.ItemGroupWithCount
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.ui.components.EmptyState
import com.ezzy.vault.ui.components.GROUP_ICON_KEY
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.components.QuickKind
import com.ezzy.vault.ui.components.QuickTarget
import com.ezzy.vault.ui.components.SectionHeader
import com.ezzy.vault.ui.ezzyViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuickAccessViewModel(container: AppContainer) : ViewModel() {

    private val repository = container.repository

    // Everything pinnable, pinned or not: the same three lists answer both what is in Quick
    // access right now and what the edit mode offers to put there, since each row carries its
    // own pinned flag.
    val categories: StateFlow<List<CategoryWithCount>> = repository.observeCategoriesWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<ItemGroupWithCount>> = repository.observeAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<ItemWithDetails>> = repository.observeAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSectionPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { repository.setCategoryPinned(id, pinned) }
    }

    fun setGroupPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { repository.setGroupPinned(id, pinned) }
    }

    fun setEntryPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }
}

/**
 * The whole of Quick access, which "See all" on the home screen opens. Normally it lists what
 * is in there — sections, groups and entries alike — and opens whatever is tapped. The Edit
 * icon turns the same list into every pinnable thing in the vault with a switch each, so
 * adding and removing are the same gesture rather than two separate screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessScreen(
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val viewModel: QuickAccessViewModel = ezzyViewModel { QuickAccessViewModel(it) }
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

    var editMode by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val sectionNames = categories.associate { it.category.id to it.category.name }
    val sectionColors = categories.associate { it.category.id to it.category.colorKey }
    val sectionIcons = categories.associate { it.category.id to it.category.iconKey }

    fun sectionTarget(row: CategoryWithCount) = QuickTarget(
        id = row.category.id,
        kind = QuickKind.SECTION,
        title = row.category.name,
        subtitle = if (row.itemCount == 1) "1 entry" else "${row.itemCount} entries",
        iconKey = row.category.iconKey,
        colorKey = row.category.colorKey,
    )

    fun groupTarget(row: ItemGroupWithCount) = QuickTarget(
        id = row.group.id,
        kind = QuickKind.GROUP,
        title = row.group.name,
        subtitle = listOfNotNull(
            sectionNames[row.group.categoryId],
            if (row.itemCount == 1) "1 entry" else "${row.itemCount} entries",
        ).joinToString(" · "),
        iconKey = GROUP_ICON_KEY,
        // A group borrows its section's colour, so it still reads as part of it.
        colorKey = sectionColors[row.group.categoryId],
    )

    fun entryTarget(row: ItemWithDetails) = QuickTarget(
        id = row.item.id,
        kind = QuickKind.ENTRY,
        title = row.item.title,
        subtitle = sectionNames[row.item.categoryId].orEmpty(),
        iconKey = sectionIcons[row.item.categoryId],
        colorKey = sectionColors[row.item.categoryId],
        photoStoredName = row.item.iconPhoto,
    )

    fun matches(text: String) = query.isBlank() || text.contains(query.trim(), ignoreCase = true)
    val editSections = categories.filter { matches(it.category.name) }
    val editGroups = groups.filter { matches(it.group.name) }
    // Sorted by name rather than pinned-first: a row that jumps up the list the moment its own
    // switch is flipped makes flipping several of them in a row a guessing game.
    val editEntries = items
        .filter { matches(it.item.title) }
        .sortedBy { it.item.title.lowercase() }

    val pinnedSections = categories.filter { it.category.isPinned }
    val pinnedGroups = groups.filter { it.group.isPinned }
    val pinnedEntries = items.filter { it.item.isPinned }
    val nothingPinned = pinnedSections.isEmpty() && pinnedGroups.isEmpty() && pinnedEntries.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick access") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(
                            imageVector = if (editMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                            contentDescription = if (editMode) "Done editing" else "Edit Quick access",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (editMode) {
                item(key = "edit-hint") {
                    Text(
                        text = "Switch anything on to put it in Quick access, or off to take " +
                            "it out. Sections, groups and entries all sit on the same shelf.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item(key = "edit-search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Find a section, group or entry") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }

                if (editSections.isNotEmpty()) {
                    item(key = "edit-sections-header") { SectionHeader(text = "Sections") }
                    items(editSections, key = { "edit-section-" + it.category.id }) { row ->
                        QuickRow(
                            target = sectionTarget(row),
                            trailing = {
                                Switch(
                                    checked = row.category.isPinned,
                                    onCheckedChange = {
                                        viewModel.setSectionPinned(row.category.id, it)
                                    },
                                )
                            },
                        )
                    }
                }

                if (editGroups.isNotEmpty()) {
                    item(key = "edit-groups-header") { SectionHeader(text = "Groups") }
                    items(editGroups, key = { "edit-group-" + it.group.id }) { row ->
                        QuickRow(
                            target = groupTarget(row),
                            trailing = {
                                Switch(
                                    checked = row.group.isPinned,
                                    onCheckedChange = { viewModel.setGroupPinned(row.group.id, it) },
                                )
                            },
                        )
                    }
                }

                if (editEntries.isNotEmpty()) {
                    item(key = "edit-entries-header") { SectionHeader(text = "Entries") }
                    items(editEntries, key = { "edit-entry-" + it.item.id }) { row ->
                        QuickRow(
                            target = entryTarget(row),
                            trailing = {
                                Switch(
                                    checked = row.item.isPinned,
                                    onCheckedChange = { viewModel.setEntryPinned(row.item.id, it) },
                                )
                            },
                        )
                    }
                }

                if (editSections.isEmpty() && editGroups.isEmpty() && editEntries.isEmpty()) {
                    item(key = "edit-empty") {
                        EmptyState(
                            icon = if (query.isBlank()) Icons.Rounded.PushPin else Icons.Rounded.SearchOff,
                            title = if (query.isBlank()) "Nothing to add yet" else "Nothing found",
                            message = if (query.isBlank()) {
                                "Create a section or save an entry first, then it can go in Quick access."
                            } else {
                                "Nothing here matches \"$query\"."
                            },
                        )
                    }
                }
            } else if (nothingPinned) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.PushPin,
                        title = "Quick access is empty",
                        message = "Put the sections, groups and entries you reach for most in here, " +
                            "and they will wait on the home screen.",
                        action = {
                            Button(onClick = { editMode = true }) { Text("Choose what goes here") }
                        },
                    )
                }
            } else {
                if (pinnedSections.isNotEmpty()) {
                    item(key = "sections-header") { SectionHeader(text = "Sections") }
                    items(pinnedSections, key = { "section-" + it.category.id }) { row ->
                        QuickRow(
                            target = sectionTarget(row),
                            onClick = { onOpenCategory(row.category.id) },
                        )
                    }
                }

                if (pinnedGroups.isNotEmpty()) {
                    item(key = "groups-header") { SectionHeader(text = "Groups") }
                    items(pinnedGroups, key = { "group-" + it.group.id }) { row ->
                        QuickRow(
                            target = groupTarget(row),
                            onClick = { onOpenGroup(row.group.id) },
                        )
                    }
                }

                if (pinnedEntries.isNotEmpty()) {
                    item(key = "entries-header") { SectionHeader(text = "Entries") }
                    items(pinnedEntries, key = { "entry-" + it.item.id }) { row ->
                        QuickRow(
                            target = entryTarget(row),
                            onClick = { onOpenItem(row.item.id) },
                        )
                    }
                }
            }
        }
    }
}

/** One line of Quick access — the same shape whether it opens something or toggles a switch. */
@Composable
private fun QuickRow(
    target: QuickTarget,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAvatar(
                iconKey = target.iconKey,
                colorKey = target.colorKey,
                size = 42.dp,
                iconSize = 20.dp,
                photoStoredName = target.photoStoredName,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (target.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = target.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}
