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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.TemplateEntity
import com.ezzy.vault.data.model.FieldType
import com.ezzy.vault.data.model.TemplateField
import com.ezzy.vault.data.model.TemplateSpec
import com.ezzy.vault.ui.components.EzzyChip
import com.ezzy.vault.ui.components.IconPickerGrid
import com.ezzy.vault.ui.components.SectionHeader
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.ui.icons.IconCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplatesViewModel(private val container: AppContainer) : ViewModel() {

    val templates: StateFlow<List<TemplateEntity>> = container.repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun specOf(template: TemplateEntity): TemplateSpec =
        container.repository.decodeSpec(template.specJson)

    fun save(id: String?, name: String, iconKey: String, fields: List<TemplateField>) {
        viewModelScope.launch {
            container.repository.saveTemplate(
                id = id,
                name = name,
                iconKey = iconKey,
                spec = TemplateSpec(fields = fields),
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.repository.deleteTemplate(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(onBack: () -> Unit) {
    val viewModel: TemplatesViewModel = ezzyViewModel { TemplatesViewModel(it) }
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TemplateEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry types") },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New type") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "A type decides which fields you are asked for when saving a new entry. " +
                        "Edit one to match how you actually write things down.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(templates, key = { it.id }) { template ->
                val spec = viewModel.specOf(template)
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { editing = template }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = IconCatalog.image(template.iconKey),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(template.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${spec.fields.size} fields",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (template.isBuiltIn) EzzyChip(text = "Built-in")
                    }
                }
            }
        }
    }

    val target = editing
    if (creating || target != null) {
        TemplateEditorDialog(
            template = target,
            initialFields = target?.let { viewModel.specOf(it).fields } ?: emptyList(),
            onDismiss = {
                editing = null
                creating = false
            },
            onSave = { name, iconKey, fields ->
                viewModel.save(target?.id, name, iconKey, fields)
                editing = null
                creating = false
            },
            onDelete = target?.takeIf { !it.isBuiltIn }?.let { template ->
                {
                    viewModel.delete(template.id)
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun TemplateEditorDialog(
    template: TemplateEntity?,
    initialFields: List<TemplateField>,
    onDismiss: () -> Unit,
    onSave: (String, String, List<TemplateField>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(template?.name.orEmpty()) }
    var iconKey by remember { mutableStateOf(template?.iconKey ?: IconCatalog.defaultKey) }
    var fields by remember { mutableStateOf(initialFields) }
    var showIcons by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (template == null) "New type" else "Edit type",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete type",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Type name") },
                            placeholder = { Text("e.g. Property Papers") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = IconCatalog.image(iconKey),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = { showIcons = !showIcons }) {
                                Text(if (showIcons) "Hide icons" else "Change icon")
                            }
                        }
                    }
                    if (showIcons) {
                        item {
                            IconPickerGrid(
                                selectedKey = iconKey,
                                accentKey = "indigo",
                                onSelect = { iconKey = it },
                                modifier = Modifier.height(220.dp),
                            )
                        }
                    }
                    item { SectionHeader("Fields asked for") }
                    items(fields.size) { index ->
                        TemplateFieldRow(
                            field = fields[index],
                            onChange = { updated ->
                                fields = fields.toMutableList().also { it[index] = updated }
                            },
                            onRemove = {
                                fields = fields.toMutableList().also { it.removeAt(index) }
                            },
                        )
                    }
                    item {
                        FilledTonalButton(
                            onClick = { fields = fields + TemplateField(label = "") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Add field")
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = {
                                onSave(
                                    name,
                                    iconKey,
                                    fields.filter { it.label.isNotBlank() },
                                )
                            },
                            enabled = name.isNotBlank(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Save type")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateFieldRow(
    field: TemplateField,
    onChange: (TemplateField) -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = field.label,
                    onValueChange = { onChange(field.copy(label = it)) },
                    label = { Text("Field name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    TextButton(onClick = { menuOpen = true }) {
                        Text(field.type.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        FieldType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    onChange(field.copy(type = type))
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Remove field",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
