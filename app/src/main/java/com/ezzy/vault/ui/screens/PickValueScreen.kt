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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.ui.components.EmptyState
import com.ezzy.vault.ui.components.ItemRow
import com.ezzy.vault.ui.ezzyViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PickValueViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Pinned and recently used first, then everything, narrowed by the search box. */
    val items: StateFlow<List<ItemWithDetails>> = _query
        .debounce(120)
        .flatMapLatest { text ->
            if (text.isBlank()) {
                combine(
                    repository.observePinned(),
                    repository.observeRecent(12),
                    repository.observeAllItems(),
                ) { pinned, recent, all ->
                    val head = pinned + recent
                    val seen = head.mapTo(mutableSetOf()) { it.item.id }
                    head + all.filterNot { it.item.id in seen }
                }
            } else {
                repository.search(text)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<Map<String, CategoryEntity>> = repository.observeCategories()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun markUsed(itemId: String) {
        viewModelScope.launch { repository.markUsed(itemId) }
    }
}

/**
 * The picker behind the "EZZY" item in Android's text selection menu, and a plain way to grab
 * one value in two taps. Picking a field hands its text straight back to whatever field the
 * user was typing in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickValueScreen(
    onPick: (label: String, value: String, sensitive: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val viewModel: PickValueViewModel = ezzyViewModel { PickValueViewModel(it) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var openItem by remember { mutableStateOf<ItemWithDetails?>(null) }
    val selected = openItem?.let { current -> items.firstOrNull { it.item.id == current.item.id } }
        ?: openItem

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selected?.item?.title ?: "Insert from EZZY",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (selected != null) {
                        IconButton(onClick = { openItem = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (selected != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(selected.sortedFields, key = { it.id }) { field ->
                    ValueRow(
                        label = field.label,
                        preview = if (field.type.isMasked) {
                            "•".repeat(field.value.length.coerceIn(6, 14))
                        } else {
                            field.value
                        },
                        onClick = {
                            viewModel.markUsed(selected.item.id)
                            onPick(field.label, field.value, field.type.isMasked)
                        },
                    )
                }
            }
            return@Scaffold
        }

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
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search your vault") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Inbox,
                        title = if (query.isBlank()) "Nothing saved yet" else "Nothing found",
                        message = if (query.isBlank()) {
                            "Add an entry in EZZY and it will show up here."
                        } else {
                            "No entry matches \"$query\"."
                        },
                    )
                }
            } else {
                items(items, key = { it.item.id }) { entry ->
                    val category = categories[entry.item.categoryId]
                    ItemRow(
                        item = entry,
                        iconKey = category?.iconKey,
                        colorKey = category?.colorKey,
                        onClick = { openItem = entry },
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, preview: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preview.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Rounded.ContentPaste,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
