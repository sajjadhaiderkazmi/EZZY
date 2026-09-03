package com.ezzy.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val container: AppContainer,
    private val categoryId: String,
) : ViewModel() {

    val category: StateFlow<CategoryEntity?> = container.repository.observeCategory(categoryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<ItemWithDetails>> = container.repository.observeItems(categoryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteCategory(onDone: () -> Unit) {
        viewModelScope.launch {
            container.repository.deleteCategory(categoryId)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categoryId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onAddItem: () -> Unit,
    onEditCategory: () -> Unit,
) {
    val viewModel: CategoryViewModel = ezzyViewModel(key = "category-$categoryId") {
        CategoryViewModel(it, categoryId)
    }
    val category by viewModel.category.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = category?.name ?: "Section",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (items.size == 1) "1 item" else "${items.size} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Section options")
                        }
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddItem,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (items.isEmpty()) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.item.id }) { entry ->
                    ItemRow(
                        item = entry,
                        iconKey = category?.iconKey,
                        colorKey = category?.colorKey,
                        onClick = { onOpenItem(entry.item.id) },
                    )
                }
            }
        }
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
