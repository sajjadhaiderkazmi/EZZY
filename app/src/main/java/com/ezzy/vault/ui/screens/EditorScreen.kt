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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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

    val stepIndex = EditorStep.ordered.indexOf(state.step)

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
                                text = "Step ${stepIndex + 1} of ${EditorStep.ordered.size} · ${state.step.title}",
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
                    progress = { (stepIndex + 1f) / EditorStep.ordered.size },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            EditorBottomBar(
                state = state,
                isLastStep = state.step == EditorStep.FILES,
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

                EditorStep.TYPE -> TypeStep(
                    templates = templates,
                    selectedId = state.draft.templateId,
                    onSelect = {
                        viewModel.applyTemplate(it)
                        viewModel.next()
                    },
                )

                EditorStep.DETAILS -> DetailsStep(viewModel = viewModel, state = state)

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
            if (state.step != EditorStep.ordered.first()) {
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

// ---- Step 1: section --------------------------------------------------------

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

// ---- Step 2: type -----------------------------------------------------------

@Composable
private fun TypeStep(
    templates: List<TemplateEntity>,
    selectedId: String?,
    onSelect: (TemplateEntity?) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "Pick a type and EZZY pre-fills the right fields. You can rename, add or " +
                    "remove any of them on the next step.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        items(templates, key = { it.id }) { template ->
            val selected = template.id == selectedId
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onSelect(template) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = IconCatalog.image(template.iconKey),
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

// ---- Step 3: details --------------------------------------------------------

@Composable
private fun DetailsStep(viewModel: EditorViewModel, state: EditorUiState) {
    val draft = state.draft
    val context = LocalContext.current

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        readPickedContact(context, uri)?.let { viewModel.applyContact(it.name, it.phone) }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Name this entry") },
                placeholder = { Text("e.g. HBL Current Account") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }
        item {
            OutlinedTextField(
                value = draft.subtitle,
                onValueChange = viewModel::setSubtitle,
                label = { Text("Short description (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
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
                modifier = Modifier.padding(top = 8.dp),
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
                    text = "No fields yet. Add one for every value you want to copy later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(draft.fields, key = { it.id }) { field ->
            FieldEditorCard(
                field = field,
                onChange = { updated -> viewModel.updateField(field.id) { updated } },
                onRemove = { viewModel.removeField(field.id) },
                onMoveUp = { viewModel.moveField(field.id, -1) },
                onMoveDown = { viewModel.moveField(field.id, 1) },
            )
        }

        item {
            FilledTonalButton(
                onClick = { viewModel.addField() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add a field")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditorCard(
    field: FieldDraft,
    onChange: (FieldDraft) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var typeMenuOpen by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = field.label,
                    onValueChange = { onChange(field.copy(label = it)) },
                    label = { Text("Field name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Move up",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = "Move down",
                            modifier = Modifier.size(16.dp),
                        )
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

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = field.value,
                onValueChange = { onChange(field.copy(value = it)) },
                label = { Text("Data") },
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

            Spacer(Modifier.height(8.dp))

            Box {
                TextButton(onClick = { typeMenuOpen = true }) {
                    Text(field.type.displayName())
                }
                DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                    FieldType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName()) },
                            onClick = {
                                onChange(field.copy(type = type))
                                typeMenuOpen = false
                            },
                            trailingIcon = {
                                if (type == field.type) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (datePickerOpen) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onChange(field.copy(value = DATE_FORMAT.format(Date(it))))
                        }
                        datePickerOpen = false
                    }
                ) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// ---- Step 4: files ----------------------------------------------------------

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

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> viewModel.addAttachments(uris, resolveName) }

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
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
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
