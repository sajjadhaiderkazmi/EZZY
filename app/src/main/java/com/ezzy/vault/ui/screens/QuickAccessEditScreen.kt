package com.ezzy.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SearchOff
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
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.ui.components.EmptyState
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.ezzyViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuickAccessEditViewModel(container: AppContainer) : ViewModel() {

    private val repository = container.repository

    val items: StateFlow<List<ItemWithDetails>> = repository.observeAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<Map<String, CategoryEntity>> = repository.observeCategories()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }
}

/**
 * What "Quick access"'s edit icon opens: every entry in the vault, each with a switch that pins
 * or unpins it. Quick access itself is just "pinned, then recent" — this screen only ever
 * touches the pinned flag, so there is nothing separate to keep in sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessEditScreen(onBack: () -> Unit) {
    val viewModel: QuickAccessEditViewModel = ezzyViewModel { QuickAccessEditViewModel(it) }
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }

    // Pinned entries first — so the ones already in Quick access are the first thing the user
    // checks — then everything else alphabetically, so the list has a stable, predictable order
    // that doesn't jump around as switches are flipped.
    val visible = items
        .filter { query.isBlank() || it.item.title.contains(query, ignoreCase = true) }
        .sortedWith(compareByDescending<ItemWithDetails> { it.item.isPinned }.thenBy { it.item.title })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Quick access") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Find an entry") },
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

            item {
                Text(
                    text = "Switch an entry on to pin it to Quick access on the home screen, " +
                        "or off to take it out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }

            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = if (items.isEmpty()) "No entries yet" else "Nothing found",
                        message = if (items.isEmpty()) {
                            "Add an entry first, then pin it here."
                        } else {
                            "No entry matches \"$query\"."
                        },
                    )
                }
            } else {
                items(visible, key = { it.item.id }) { entry ->
                    val category = categories[entry.item.categoryId]
                    QuickAccessPickerRow(
                        item = entry,
                        iconKey = category?.iconKey,
                        colorKey = category?.colorKey,
                        onToggle = { pinned -> viewModel.setPinned(entry.item.id, pinned) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessPickerRow(
    item: ItemWithDetails,
    iconKey: String?,
    colorKey: String?,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAvatar(iconKey = iconKey, colorKey = colorKey, size = 42.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.item.isPinned) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "In Quick access",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = item.item.isPinned,
                onCheckedChange = onToggle,
            )
        }
    }
}
