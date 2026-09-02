package com.ezzy.vault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.ui.components.ColorPickerRow
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.components.IconPickerGrid
import com.ezzy.vault.ui.components.SectionHeader
import com.ezzy.vault.ui.ezzyViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CategoryEditorState(
    val name: String = "",
    val iconKey: String = "folder",
    val colorKey: String = "indigo",
    val isNew: Boolean = true,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

class CategoryEditorViewModel(
    private val container: AppContainer,
    private val categoryId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryEditorState())
    val state: StateFlow<CategoryEditorState> = _state.asStateFlow()

    init {
        if (categoryId != null) {
            viewModelScope.launch {
                val category = container.repository.observeCategory(categoryId).first() ?: return@launch
                _state.value = CategoryEditorState(
                    name = category.name,
                    iconKey = category.iconKey,
                    colorKey = category.colorKey,
                    isNew = false,
                )
            }
        }
    }

    fun setName(value: String) {
        _state.value = _state.value.copy(name = value)
    }

    fun setIcon(value: String) {
        _state.value = _state.value.copy(iconKey = value)
    }

    fun setColor(value: String) {
        _state.value = _state.value.copy(colorKey = value)
    }

    fun save(onDone: () -> Unit) {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            container.repository.saveCategory(
                id = categoryId,
                name = current.name,
                iconKey = current.iconKey,
                colorKey = current.colorKey,
            )
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorScreen(
    categoryId: String?,
    onBack: () -> Unit,
) {
    val viewModel: CategoryEditorViewModel = ezzyViewModel(key = "cat-editor-${categoryId.orEmpty()}") {
        CategoryEditorViewModel(it, categoryId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (categoryId == null) "New section" else "Edit section") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAvatar(
                        iconKey = state.iconKey,
                        colorKey = state.colorKey,
                        size = 56.dp,
                        iconSize = 28.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = state.name.ifBlank { "Section name" },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "This is how it looks in the floating bar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Section name") },
                placeholder = { Text("e.g. Bank & Cards") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            SectionHeader("Colour")
            Spacer(Modifier.height(10.dp))
            ColorPickerRow(selectedKey = state.colorKey, onSelect = viewModel::setColor)

            Spacer(Modifier.height(20.dp))
            SectionHeader("Icon")
            Spacer(Modifier.height(10.dp))
            IconPickerGrid(
                selectedKey = state.iconKey,
                accentKey = state.colorKey,
                onSelect = viewModel::setIcon,
                modifier = Modifier.height(280.dp),
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.save(onBack) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (categoryId == null) "Create section" else "Save changes")
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
