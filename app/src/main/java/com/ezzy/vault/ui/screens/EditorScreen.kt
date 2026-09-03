package com.ezzy.vault.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.TemplateEntity
import com.ezzy.vault.data.model.FieldDraft
import com.ezzy.vault.data.model.FieldType
import com.ezzy.vault.ui.LocalSnackbar
import com.ezzy.vault.ui.components.DeleteAttachmentButton
import com.ezzy.vault.ui.components.EncryptedImage
import com.ezzy.vault.ui.components.ImageCropDialog
import com.ezzy.vault.ui.components.VOICE_NOTE_MIME
import com.ezzy.vault.ui.components.VoiceNoteDialog
import com.ezzy.vault.ui.components.VoiceNoteRow
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.components.SectionHeader
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.ui.icons.IconCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    itemId: String?,
    categoryId: String?,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val viewModel: EditorViewModel = ezzyViewModel(key = "editor-${itemId.orEmpty()}-${categoryId.orEmpty()}") {
        EditorViewModel(it, itemId, categoryId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val stepIndex = state.steps.indexOf(state.step).coerceAtLeast(0)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (state.draft.isNew) "New entry" else "Edit entry",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Step ${stepIndex + 1} of ${state.steps.size} · ${state.step.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (!viewModel.back()) onClose() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.Close, contentDescription = "Discard")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                LinearProgressIndicator(
                    progress = { (stepIndex + 1f) / state.steps.size },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            EditorBottomBar(
                state = state,
                isLastStep = state.step == state.steps.last(),
                onBack = { viewModel.back() },
                onNext = { viewModel.next() },
                onSave = { viewModel.save(onSaved) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        AnimatedContent(
            targetState = state.step,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "editor-step",
        ) { step ->
            when (step) {
                EditorStep.SECTION -> SectionStep(
                    categories = categories,
                    selectedId = state.draft.categoryId,
                    onSelect = {
                        viewModel.setCategory(it)
                        viewModel.next()
                    },
                )

                EditorStep.DETAILS -> DetailsStep(
                    viewModel = viewModel,
                    state = state,
                    categories = categories,
                    templates = templates,
                )

                EditorStep.FILES -> FilesStep(viewModel = viewModel, state = state)
            }
        }
    }
}

@Composable
private fun EditorBottomBar(
    state: EditorUiState,
    isLastStep: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step != state.steps.first()) {
                TextButton(onClick = onBack) { Text("Back") }
            }
            Spacer(Modifier.weight(1f))

            // Once the entry has a name it can be saved from any step — no need to walk
            // through files just to store a phone number.
            if (!isLastStep && state.canSave) {
                TextButton(onClick = onSave) { Text("Save now") }
            }

            if (isLastStep) {
                Button(onClick = onSave, enabled = state.canSave) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save entry")
                }
            } else {
                Button(onClick = onNext, enabled = state.canContinue) { Text("Continue") }
            }
        }
    }
}

// ---- Section step -----------------------------------------------------------

@Composable
private fun SectionStep(
    categories: List<CategoryEntity>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            val selected = category.id == selectedId
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .clickable { onSelect(category.id) }
                        .padding(14.dp),
                ) {
                    IconAvatar(
                        iconKey = category.iconKey,
                        colorKey = category.colorKey,
                        size = 44.dp,
                        iconSize = 22.dp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---- Details step -----------------------------------------------------------

@Composable
private fun DetailsStep(
    viewModel: EditorViewModel,
    state: EditorUiState,
    categories: List<CategoryEntity>,
    templates: List<TemplateEntity>,
) {
    val draft = state.draft
    val context = LocalContext.current
    var newField by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FieldDraft?>(null) }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        readPickedContact(context, uri)?.let { viewModel.applyContact(it.name, it.phone) }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ChooserRow(
                caption = "Section",
                value = categories.firstOrNull { it.id == draft.categoryId }?.name
                    ?: "Choose a section",
                iconKey = categories.firstOrNull { it.id == draft.categoryId }?.iconKey,
                colorKey = categories.firstOrNull { it.id == draft.categoryId }?.colorKey,
                options = categories.map { Triple(it.id, it.name, it.iconKey) },
                selectedId = draft.categoryId,
                onSelect = viewModel::setCategory,
            )
        }

        item {
            ChooserRow(
                caption = "Type",
                value = templates.firstOrNull { it.id == draft.templateId }?.name
                    ?: "Choose a type",
                iconKey = templates.firstOrNull { it.id == draft.templateId }?.iconKey,
                colorKey = null,
                options = templates.map { Triple(it.id, it.name, it.iconKey) },
                selectedId = draft.templateId.orEmpty(),
                onSelect = { id -> viewModel.applyTemplate(templates.firstOrNull { it.id == id }) },
            )
        }

        item {
            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Title") },
                placeholder = { Text(state.titleHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pin to quick access", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Shows first in the floating bar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = draft.isPinned, onCheckedChange = viewModel::setPinned)
            }
        }

        item {
            SectionHeader(
                text = "Fields",
                modifier = Modifier.padding(top = 6.dp),
                trailing = {
                    TextButton(
                        onClick = {
                            runCatching {
                                contactPicker.launch(
                                    Intent(
                                        Intent.ACTION_PICK,
                                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Contacts,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("From contacts", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        }

        if (draft.fields.isEmpty()) {
            item {
                Text(
                    text = "Pick a type above to get its usual fields, or add your own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(draft.fields, key = { it.id }) { field ->
            FieldInput(
                field = field,
                onValueChange = { text -> viewModel.updateField(field.id) { it.copy(value = text) } },
                canRename = !field.fromTemplate,
                onRename = { renaming = field },
                onRemove = { viewModel.removeField(field.id) },
            )
        }

        item {
            FilledTonalButton(
                onClick = { newField = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add a field")
            }
        }
    }

    if (newField) {
        FieldNameDialog(
            title = "New field",
            initialLabel = "",
            initialType = FieldType.TEXT,
            onDismiss = { newField = false },
            onConfirm = { label, type ->
                newField = false
                viewModel.addField(label, type)
            },
        )
    }

    renaming?.let { field ->
        FieldNameDialog(
            title = "Rename field",
            initialLabel = field.label,
            initialType = field.type,
            onDismiss = { renaming = null },
            onConfirm = { label, type ->
                renaming = null
                viewModel.renameField(field.id, label, type)
            },
        )
    }
}

/**
 * One stored value. The name sits above as a caption and only the data below it is typed in —
 * a field that came from the entry's type has a fixed name, and one the user added was named
 * when it was created, so neither needs an editable name box sitting in the form.
 */
@Composable
private fun FieldInput(
    field: FieldDraft,
    onValueChange: (String) -> Unit,
    canRename: Boolean,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.label.ifBlank { "Untitled field" }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (canRename) {
                IconButton(onClick = onRename, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Rename ${field.label}",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove ${field.label}",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = field.value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = field.type != FieldType.MULTILINE,
            minLines = if (field.type == FieldType.MULTILINE) 3 else 1,
            readOnly = field.type == FieldType.DATE,
            visualTransformation = if (field.type.isMasked && !revealed) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(keyboardType = field.type.keyboardType()),
            trailingIcon = {
                when {
                    field.type.isMasked -> IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) Icons.Rounded.VisibilityOff
                            else Icons.Rounded.Visibility,
                            contentDescription = if (revealed) "Hide value" else "Show value",
                        )
                    }

                    field.type == FieldType.DATE -> IconButton(onClick = { datePickerOpen = true }) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = "Pick a date")
                    }

                    else -> Unit
                }
            },
        )
    }

    if (datePickerOpen) {
        DatePickerSheet(
            onDismiss = { datePickerOpen = false },
            onPicked = { millis ->
                datePickerOpen = false
                onValueChange(DATE_FORMAT.format(Date(millis)))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let(onPicked) ?: onDismiss() }
            ) {
                Text("Set")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

/** Naming comes first: a field is created with a name and a kind, then it holds data. */
@Composable
private fun FieldNameDialog(
    title: String,
    initialLabel: String,
    initialType: FieldType,
    onDismiss: () -> Unit,
    onConfirm: (String, FieldType) -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }
    var type by remember { mutableStateOf(initialType) }
    var typeMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Field name") },
                    placeholder = { Text("e.g. Branch code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedButton(
                        onClick = { typeMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(type.displayName(), modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = typeMenuOpen,
                        onDismissRequest = { typeMenuOpen = false },
                    ) {
                        FieldType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName()) },
                                onClick = {
                                    type = option
                                    typeMenuOpen = false
                                },
                                trailingIcon = {
                                    if (option == type) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank(),
                onClick = { onConfirm(label, type) },
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shared row for the two things an entry is filed under: its section and its type. */
@Composable
private fun ChooserRow(
    caption: String,
    value: String,
    iconKey: String?,
    colorKey: String?,
    options: List<Triple<String, String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAvatar(
                    iconKey = iconKey,
                    colorKey = colorKey,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = value, style = MaterialTheme.typography.bodyLarge)
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = "Change $caption",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (id, name, icon) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSelect(id)
                            open = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = IconCatalog.image(icon),
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (id == selectedId) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

// ---- Files step -------------------------------------------------------------

@Composable
private fun FilesStep(viewModel: EditorViewModel, state: EditorUiState) {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<String?>(null) }
    val resolveName: (Uri) -> Pair<String, String> = { uri ->
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "File"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index) ?: name
            }
        }
        name to (context.contentResolver.getType(uri) ?: "application/octet-stream")
    }

    // Single select on purpose: on several OEM gallery pickers a tap in multi-select mode only
    // opens a preview and Done comes back empty. Picking one photo returns immediately, and the
    // button can simply be tapped again for the next one.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.addAttachments(listOfNotNull(uri), resolveName) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.addAttachments(listOfNotNull(uri), resolveName) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = {
                        runCatching {
                            photoPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.importing,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Photos")
                }
                FilledTonalButton(
                    onClick = { runCatching { filePicker.launch(DOCUMENT_MIME_TYPES) } },
                    modifier = Modifier.weight(1f),
                    enabled = !state.importing,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("File")
                }
                FilledTonalButton(
                    onClick = { recording = true },
                    modifier = Modifier.weight(1f),
                    enabled = !state.importing,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Voice")
                }
            }
        }

        item {
            Text(
                text = "Files are encrypted with a key held in this phone's secure hardware and " +
                    "stored inside the app — never in your gallery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.draft.attachments.isNotEmpty()) {
            item { SectionHeader("Attached (${state.draft.attachments.size})") }
            items(state.draft.attachments, key = { it.id }) { attachment ->
                if (attachment.isAudio) {
                    VoiceNoteRow(
                        storedName = attachment.storedName,
                        displayName = attachment.displayName,
                        trailing = {
                            DeleteAttachmentButton { viewModel.removeAttachment(attachment.id) }
                        },
                    )
                    return@items
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (attachment.isImage) {
                            EncryptedImage(
                                storedName = attachment.storedName,
                                contentDescription = attachment.displayName,
                                modifier = Modifier
                                    .size(52.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(52.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${attachment.sizeBytes / 1024} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (attachment.isImage) {
                            IconButton(onClick = { cropping = attachment.id }) {
                                Icon(
                                    imageVector = Icons.Rounded.Crop,
                                    contentDescription = "Crop ${attachment.displayName}",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        DeleteAttachmentButton { viewModel.removeAttachment(attachment.id) }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = state.draft.note,
                onValueChange = viewModel::setNote,
                label = { Text("Note (optional)") },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }

    if (recording) {
        VoiceNoteDialog(
            onCancel = { recording = false },
            onRecorded = { bytes, seconds ->
                recording = false
                val index = state.draft.attachments.count { it.isAudio } + 1
                viewModel.addBytesAttachment(
                    bytes = bytes,
                    displayName = "Voice note $index (${seconds}s)",
                    mimeType = VOICE_NOTE_MIME,
                )
            },
        )
    }

    cropping?.let { id ->
        val target = state.draft.attachments.firstOrNull { it.id == id }
        if (target == null) {
            cropping = null
        } else {
            ImageCropDialog(
                storedName = target.storedName,
                onCancel = { cropping = null },
                onCropped = { bytes ->
                    cropping = null
                    viewModel.replaceAttachmentBytes(id, bytes)
                },
            )
        }
    }
}

/**
 * Reads the single phone row the contacts picker handed back. Going through ACTION_PICK means
 * the picker grants read access to just that row, so EZZY never asks for READ_CONTACTS.
 */
private fun readPickedContact(context: Context, uri: Uri): PickedContact? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        PickedContact(
            name = cursor.getString(0).orEmpty(),
            phone = cursor.getString(1).orEmpty(),
        )
    }
}.getOrNull()

private data class PickedContact(val name: String, val phone: String)

// ---- Helpers ----------------------------------------------------------------

private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

/** What the "File" button will accept: scans, office documents and plain text. */
private val DOCUMENT_MIME_TYPES = arrayOf(
    "application/pdf",
    "image/*",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "text/plain",
    "text/csv",
)

private fun FieldType.displayName(): String = when (this) {
    FieldType.TEXT -> "Text"
    FieldType.MULTILINE -> "Long text"
    FieldType.SECRET -> "Secret (hidden)"
    FieldType.NUMBER -> "Number"
    FieldType.PHONE -> "Phone"
    FieldType.EMAIL -> "Email"
    FieldType.URL -> "Website"
    FieldType.DATE -> "Date"
}

private fun FieldType.keyboardType(): KeyboardType = when (this) {
    FieldType.NUMBER -> KeyboardType.Number
    FieldType.PHONE -> KeyboardType.Phone
    FieldType.EMAIL -> KeyboardType.Email
    FieldType.URL -> KeyboardType.Uri
    FieldType.SECRET -> KeyboardType.Password
    else -> KeyboardType.Text
}
