package com.ezzy.vault.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ezzy.vault.ui.components.bleedHorizontally
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.TemplateEntity
import com.ezzy.vault.data.model.AttachmentDraft
import com.ezzy.vault.data.model.FieldDraft
import com.ezzy.vault.data.model.FieldType
import com.ezzy.vault.data.model.Seed
import com.ezzy.vault.ui.LocalSnackbar
import com.ezzy.vault.ui.components.CircleIconButton
import com.ezzy.vault.ui.components.DeleteAttachmentButton
import com.ezzy.vault.ui.components.EncryptedImage
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.components.ImageCropDialog
import com.ezzy.vault.ui.components.LimeButton
import com.ezzy.vault.ui.components.SoftPillButton
import com.ezzy.vault.ui.components.VOICE_NOTE_MIME
import com.ezzy.vault.ui.components.VoiceNoteDialog
import com.ezzy.vault.ui.components.VoiceNoteRow
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.ui.icons.IconCatalog
import com.ezzy.vault.ui.theme.EzzyLime
import com.ezzy.vault.ui.theme.EzzyOnLime
import com.ezzy.vault.ui.theme.ValueMonoStyle
import com.ezzy.vault.ui.theme.accentChip
import com.ezzy.vault.ui.theme.accentOnCard
import com.ezzy.vault.ui.theme.accentCard
import com.ezzy.vault.ui.theme.accentSheen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EditorScreen(
    itemId: String?,
    categoryId: String?,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    // Set only when something outside the normal wizard already knows which type this entry
    // should be — the share target's "New entry" path, so far the one caller of this.
    templateId: String? = null,
) {
    val key = "editor-${itemId.orEmpty()}-${categoryId.orEmpty()}-${templateId.orEmpty()}"
    val viewModel: EditorViewModel = ezzyViewModel(key = key) {
        EditorViewModel(it, itemId, categoryId, templateId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // One way out, wherever it is pressed from: a step back first, then — only if something
    // was actually typed — a quick "discard?" instead of silently throwing the entry away.
    val leave: () -> Unit = {
        if (!viewModel.back()) {
            if (state.isDirty) confirmDiscard = true else onClose()
        }
    }
    BackHandler(onBack = leave)

    val stepIndex = state.steps.indexOf(state.step).coerceAtLeast(0)

    Scaffold(
        topBar = {
            EditorHeader(
                isNew = state.draft.isNew,
                stepIndex = stepIndex,
                stepCount = state.steps.size,
                stepTitle = state.step.title,
                stepCaption = state.step.caption,
                onBack = leave,
            )
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
            transitionSpec = {
                // Forward slides in from the right, back from the left — like turning the
                // page of a form rather than swapping one screen for another.
                val forward = state.steps.indexOf(targetState) > state.steps.indexOf(initialState)
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(260)) { it / 3 * dir } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(220)) { -it / 3 * dir } + fadeOut(tween(160)))
            },
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

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text(if (state.draft.isNew) "Discard this entry?" else "Discard your changes?") },
            text = { Text("What you typed here has not been saved yet.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }
}

/**
 * The editor's own header: a round back button, what is being made and where in the form the
 * user is, and a segmented progress bar that fills as they move through the steps.
 */
@Composable
private fun EditorHeader(
    isNew: Boolean,
    stepIndex: Int,
    stepCount: Int,
    stepTitle: String,
    stepCaption: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isNew) "New entry" else "Edit entry",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = if (stepCount > 1) "Step ${stepIndex + 1} of $stepCount · $stepTitle"
                    else stepCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (stepCount > 1) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(stepCount) { index ->
                    val fill by animateFloatAsState(
                        targetValue = if (index <= stepIndex) 1f else 0f,
                        animationSpec = tween(320),
                        label = "step-$index",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fill)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(EzzyLime),
                        )
                    }
                }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // A greyed-out button on its own doesn't say why — this line does.
        AnimatedVisibility(visible = state.step == EditorStep.DETAILS && state.draft.title.isBlank()) {
            Text(
                text = "Give the entry a name to continue",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step != state.steps.first()) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Previous step",
                    onClick = onBack,
                    size = 56.dp,
                )
            }

            // Once the entry has a name it can be saved from any step — no need to walk
            // through files just to store a phone number.
            if (!isLastStep && state.canSave) {
                SoftPillButton(
                    text = "Save",
                    icon = Icons.Rounded.Check,
                    onClick = onSave,
                )
            }

            if (isLastStep) {
                LimeButton(
                    text = if (state.draft.isNew) "Save entry" else "Save changes",
                    icon = Icons.Rounded.Check,
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LimeButton(
                    text = "Continue",
                    trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = onNext,
                    enabled = state.canContinue,
                    modifier = Modifier.weight(1f),
                )
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
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Pick a section",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(categories, key = { it.id }) { category ->
            val selected = category.id == selectedId
            Surface(
                onClick = { onSelect(category.id) },
                shape = MaterialTheme.shapes.large,
                color = accentCard(category.colorKey),
                contentColor = accentOnCard(),
                border = if (selected) {
                    androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .background(accentSheen())
                        .padding(16.dp),
                ) {
                    IconAvatar(
                        iconKey = category.iconKey,
                        colorKey = category.colorKey,
                        size = 48.dp,
                        iconSize = 24.dp,
                        onCard = true,
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = accentOnCard(),
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---- Details step -----------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(
    viewModel: EditorViewModel,
    state: EditorUiState,
    categories: List<CategoryEntity>,
    templates: List<TemplateEntity>,
) {
    val draft = state.draft
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var newField by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FieldDraft?>(null) }
    var actionsFor by remember { mutableStateOf<FieldDraft?>(null) }
    var cropping by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf<Chooser?>(null) }

    // The photo just picked for the entry's own icon, waiting on its crop — separate from
    // [cropping] above, which keys off an attachment already sitting in the draft.
    var croppingIcon by remember { mutableStateOf<String?>(null) }
    val iconPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.importIconPhoto(it) { stored -> croppingIcon = stored } } }

    val resolveName = rememberAttachmentNamer()
    var recording by remember { mutableStateOf(false) }
    val pickers = rememberAttachPickers(viewModel, resolveName)

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        readPickedContact(context, uri)?.let { viewModel.applyContact(it.name, it.phone) }
    }

    val selectedCategory = categories.firstOrNull { it.id == draft.categoryId }
    val selectedTemplate = templates.firstOrNull { it.id == draft.templateId }

    val removeWithUndo: (FieldDraft) -> Unit = { field ->
        val index = draft.fields.indexOfFirst { it.id == field.id }
        viewModel.removeField(field.id)
        scope.launch {
            val result = snackbar.showSnackbar(
                message = "\"${field.label.ifBlank { "Field" }}\" removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreField(field, index)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title-card") {
            TitleCard(
                title = draft.title,
                titleHint = state.titleHint,
                onTitleChange = viewModel::setTitle,
                photoStoredName = draft.iconPhoto,
                category = selectedCategory,
                template = selectedTemplate,
                onPickPhoto = {
                    runCatching {
                        iconPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                },
                onRemovePhoto = viewModel::removeIconPhoto,
                onChooseSection = { choosing = Chooser.SECTION },
                onChooseType = { choosing = Chooser.TYPE },
            )
        }

        // A brand-new entry with no type yet starts like a survey: "what are you saving?",
        // answered with one tap on a tile, and the right questions appear underneath.
        if (draft.templateId == null && templates.isNotEmpty()) {
            item(key = "type-strip") {
                Column {
                    Text(
                        text = "What are you saving?",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.bleedHorizontally(16.dp),
                    ) {
                        items(templates, key = { it.id }) { template ->
                            TypeTile(
                                name = template.name,
                                iconKey = template.iconKey,
                                onClick = { viewModel.applyTemplate(template) },
                            )
                        }
                    }
                }
            }
        }

        // Only the Contact type offers this — pulling a name and number off the phone means
        // nothing for a bank account or a receipt.
        if (draft.templateId == Seed.CONTACT_TEMPLATE_ID) {
            item(key = "contact-import") {
                Surface(
                    onClick = {
                        runCatching {
                            contactPicker.launch(
                                Intent(
                                    Intent.ACTION_PICK,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                )
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Contacts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Import from your contacts",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = "Fills in the name and phone number",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }

        if (state.needsPhoto) {
            item(key = "files-header") { FormHeader(title = "Files", trailing = null) }

            // A document's proof is as often a video, a voice note or a plain file as it is a
            // photo or a PDF, so every kind is one tap away right under the title.
            item(key = "attach-tiles") {
                AttachTiles(
                    enabled = !state.importing,
                    pickers = pickers,
                    onRecord = { recording = true },
                )
            }

            items(draft.attachments, key = { "scan-" + it.id }) { scan ->
                Box(Modifier.animateItem()) {
                    if (scan.isAudio) {
                        VoiceNoteRow(
                            storedName = scan.storedName,
                            displayName = scan.displayName,
                            trailing = {
                                DeleteAttachmentButton { viewModel.removeAttachment(scan.id) }
                            },
                        )
                    } else {
                        AttachmentEditorRow(
                            attachment = scan,
                            onCaptionChange = { viewModel.setAttachmentCaption(scan.id, it) },
                            canCrop = scan.isImage,
                            onCrop = { cropping = scan.id },
                            onRemove = { viewModel.removeAttachment(scan.id) },
                            onToggleWatermark = { viewModel.setAttachmentWatermark(scan.id, it) },
                        )
                    }
                }
            }
        }

        if (draft.fields.isNotEmpty()) {
            item(key = "fields-header") {
                val filled = draft.fields.count { it.value.isNotBlank() }
                FormHeader(
                    title = "Details",
                    trailing = "$filled of ${draft.fields.size} filled",
                    progress = filled.toFloat() / draft.fields.size,
                )
            }
            item(key = "fields-tip") {
                Text(
                    text = "Tip: long-press a field to duplicate, move or delete it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        itemsIndexed(draft.fields, key = { _, field -> field.id }) { index, field ->
            FieldCard(
                number = index + 1,
                field = field,
                isLast = index == draft.fields.lastIndex,
                onValueChange = { text -> viewModel.updateField(field.id) { it.copy(value = text) } },
                onLongPress = { actionsFor = field },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "add-field") {
            AddFieldPanel(
                onCustom = { newField = true },
                onQuickAdd = { label, type -> viewModel.addFieldWithValue(label, type) },
            )
        }

        // These types have no second step to hold it, so the note comes along here.
        if (state.needsPhoto) {
            item(key = "note") {
                NoteField(value = draft.note, onValueChange = viewModel::setNote)
            }
        }
    }

    choosing?.let { which ->
        ChooserSheet(
            title = if (which == Chooser.SECTION) "Move to section" else "Entry type",
            options = if (which == Chooser.SECTION) {
                categories.map { ChooserOption(it.id, it.name, it.iconKey, it.colorKey) }
            } else {
                templates.map { ChooserOption(it.id, it.name, it.iconKey, null) }
            },
            selectedId = if (which == Chooser.SECTION) draft.categoryId else draft.templateId.orEmpty(),
            onDismiss = { choosing = null },
            onSelect = { id ->
                choosing = null
                if (which == Chooser.SECTION) {
                    viewModel.setCategory(id)
                } else {
                    viewModel.applyTemplate(templates.firstOrNull { it.id == id })
                }
            },
        )
    }

    actionsFor?.let { target ->
        val index = draft.fields.indexOfFirst { it.id == target.id }
        FieldActionsSheet(
            field = draft.fields.getOrNull(index) ?: target,
            canMoveUp = index > 0,
            canMoveDown = index in 0 until draft.fields.lastIndex,
            onDismiss = { actionsFor = null },
            onDuplicate = {
                actionsFor = null
                viewModel.duplicateField(target.id)
            },
            onMoveUp = { viewModel.moveField(target.id, -1) },
            onMoveDown = { viewModel.moveField(target.id, 1) },
            onRename = {
                actionsFor = null
                renaming = draft.fields.getOrNull(index) ?: target
            },
            onClear = {
                actionsFor = null
                viewModel.updateField(target.id) { it.copy(value = "") }
            },
            onDelete = {
                actionsFor = null
                removeWithUndo(draft.fields.getOrNull(index) ?: target)
            },
        )
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
            title = "Edit field",
            initialLabel = field.label,
            initialType = field.type,
            onDismiss = { renaming = null },
            onConfirm = { label, type ->
                renaming = null
                viewModel.renameField(field.id, label, type)
            },
        )
    }

    if (recording) {
        VoiceNoteDialog(
            onCancel = { recording = false },
            onRecorded = { bytes, seconds ->
                recording = false
                val index = draft.attachments.count { it.isAudio } + 1
                viewModel.addBytesAttachment(
                    bytes = bytes,
                    displayName = "Voice note $index (${seconds}s)",
                    mimeType = VOICE_NOTE_MIME,
                )
            },
        )
    }

    CropHost(
        attachmentId = cropping,
        attachments = draft.attachments,
        onDismiss = { cropping = null },
        onCropped = { id, bytes -> viewModel.replaceAttachmentBytes(id, bytes) },
    )

    croppingIcon?.let { stored ->
        ImageCropDialog(
            storedName = stored,
            circular = true,
            onCancel = {
                viewModel.discardUnusedIconPhoto(stored)
                croppingIcon = null
            },
            onCropped = { bytes ->
                viewModel.setIconPhoto(stored, bytes)
                croppingIcon = null
            },
        )
    }
}

private enum class Chooser { SECTION, TYPE }

private data class ChooserOption(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String?,
)

/**
 * The top of the form: the entry's picture, its name typed big, and where it is filed — the
 * section and the type as two tappable pills — all on one card washed in the section's colour.
 */
@Composable
private fun TitleCard(
    title: String,
    titleHint: String,
    onTitleChange: (String) -> Unit,
    photoStoredName: String?,
    category: CategoryEntity?,
    template: TemplateEntity?,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onChooseSection: () -> Unit,
    onChooseType: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = accentCard(category?.colorKey),
        contentColor = accentOnCard(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .background(accentSheen())
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Surface(onClick = onPickPhoto, shape = CircleShape, color = Color.Transparent) {
                        IconAvatar(
                            iconKey = category?.iconKey,
                            colorKey = category?.colorKey,
                            photoStoredName = photoStoredName,
                            size = 64.dp,
                            iconSize = 30.dp,
                            onCard = true,
                        )
                    }
                    Surface(
                        onClick = if (photoStoredName != null) onRemovePhoto else onPickPhoto,
                        shape = CircleShape,
                        color = if (photoStoredName != null) MaterialTheme.colorScheme.inverseSurface
                        else EzzyLime,
                        contentColor = if (photoStoredName != null) MaterialTheme.colorScheme.inverseOnSurface
                        else EzzyOnLime,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (photoStoredName != null) Icons.Rounded.Close
                                else Icons.Rounded.PhotoCamera,
                                contentDescription = if (photoStoredName != null) "Remove entry picture"
                                else "Add entry picture",
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Name",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentOnCard().copy(alpha = 0.65f),
                        modifier = Modifier.padding(start = 2.dp),
                    )
                    TextField(
                        value = title,
                        onValueChange = onTitleChange,
                        placeholder = {
                            Text(
                                text = titleHint,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        textStyle = MaterialTheme.typography.titleLarge,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next,
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = accentOnCard(),
                            unfocusedTextColor = accentOnCard(),
                            cursorColor = accentOnCard(),
                            focusedPlaceholderColor = accentOnCard().copy(alpha = 0.45f),
                            unfocusedPlaceholderColor = accentOnCard().copy(alpha = 0.45f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 0.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChooser(
                    caption = "Section",
                    value = category?.name ?: "Choose",
                    iconKey = category?.iconKey,
                    onClick = onChooseSection,
                    modifier = Modifier.weight(1f),
                )
                PillChooser(
                    caption = "Type",
                    value = template?.name ?: "Choose",
                    iconKey = template?.iconKey,
                    onClick = onChooseType,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PillChooser(
    caption: String,
    value: String,
    iconKey: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        // Set outright: a see-through surface has no matching content colour, and the values
        // came out as dark grey on the dark pill.
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = IconCatalog.image(iconKey),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = "Change $caption",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** One answer in the "What are you saving?" strip. */
@Composable
private fun TypeTile(name: String, iconKey: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(104.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = IconCatalog.image(iconKey),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A section title in the form, with an optional count and a thin lime progress line. */
@Composable
private fun FormHeader(title: String, trailing: String?, progress: Float? = null) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (progress != null) {
            val animated by animateFloatAsState(progress, tween(300), label = "form-progress")
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}

/**
 * One question of the form. The number badge turns into a lime tick once it is answered, the
 * card lights up while it is being typed in, and a long-press anywhere on its header opens the
 * field's actions (duplicate, move, rename, delete).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldCard(
    number: Int,
    field: FieldDraft,
    isLast: Boolean,
    onValueChange: (String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val answered = field.value.isNotBlank()

    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "field-border",
    )

    // Tapping anywhere on a date field opens the calendar — the box itself is read-only.
    val dateTaps = remember { MutableInteractionSource() }
    if (field.type == FieldType.DATE) {
        LaunchedEffect(dateTaps) {
            dateTaps.interactions.collect { if (it is PressInteraction.Release) datePickerOpen = true }
        }
    }

    val openActions = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongPress()
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // The header is the long-press target: a long-press inside the text box itself is
            // left alone, so selecting or pasting text keeps working the normal way.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                        onLongClick = openActions,
                    )
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(targetState = answered, label = "badge") { done ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (done) EzzyLime else MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = EzzyOnLime,
                                modifier = Modifier.size(15.dp),
                            )
                        } else {
                            Text(
                                text = "$number",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = field.label.ifBlank { "Untitled field" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (field.type.isMasked) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Hidden field",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(onClick = openActions, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "${field.label} options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            TextField(
                value = field.value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                // Kept faint so an example like "0000 0000 0000" never passes for a real value.
                placeholder = {
                    Text(
                        text = field.type.placeholder(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                textStyle = if (field.type.isNumeric()) {
                    ValueMonoStyle.copy(color = MaterialTheme.colorScheme.onSurface)
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                singleLine = field.type != FieldType.MULTILINE,
                minLines = if (field.type == FieldType.MULTILINE) 3 else 1,
                readOnly = field.type == FieldType.DATE,
                interactionSource = dateTaps,
                visualTransformation = if (field.type.isMasked && !revealed) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = field.type.keyboardType(),
                    capitalization = field.type.capitalization(),
                    autoCorrectEnabled = field.type == FieldType.TEXT || field.type == FieldType.MULTILINE,
                    imeAction = when {
                        field.type == FieldType.MULTILINE -> ImeAction.Default
                        isLast -> ImeAction.Done
                        else -> ImeAction.Next
                    },
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = when {
                    field.type.isMasked -> {
                        {
                            IconButton(onClick = { revealed = !revealed }) {
                                Icon(
                                    imageVector = if (revealed) Icons.Rounded.VisibilityOff
                                    else Icons.Rounded.Visibility,
                                    contentDescription = if (revealed) "Hide value" else "Show value",
                                )
                            }
                        }
                    }

                    field.type == FieldType.DATE -> {
                        {
                            IconButton(onClick = { datePickerOpen = true }) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = "Pick a date")
                            }
                        }
                    }

                    else -> null
                },
            )
        }
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

/** Everything a long-press on a field can do, as a bottom sheet of big rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldActionsSheet(
    field: FieldDraft,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(
                text = field.label.ifBlank { "Field" },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = field.type.displayName(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            // Move up / down stay in the sheet so a field can be nudged several places in a row
            // while watching the form shift behind it.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    label = "Move up",
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                    modifier = Modifier.weight(1f),
                )
                ActionTile(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    label = "Move down",
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                    modifier = Modifier.weight(1f),
                )
                ActionTile(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Duplicate",
                    onClick = onDuplicate,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            ActionRow(icon = Icons.Rounded.Edit, label = "Rename or change kind", onClick = onRename)
            if (field.value.isNotBlank()) {
                ActionRow(icon = Icons.Rounded.Close, label = "Clear what's typed", onClick = onClear)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ActionRow(
                icon = Icons.Rounded.DeleteOutline,
                label = "Delete field",
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/**
 * The end of the form: a big dashed-feeling "add a field" button for anything custom, and a row
 * of one-tap chips for the fields people add most, so the common case never opens a dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFieldPanel(
    onCustom: () -> Unit,
    onQuickAdd: (String, FieldType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onCustom,
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Add a field",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QUICK_FIELDS.forEach { (label, type) ->
                Surface(
                    onClick = { onQuickAdd(label, type) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = type.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Add a note (optional)") },
        minLines = 3,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
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

/**
 * Naming comes first: a field is created with a name and a kind, then it holds data. The kind
 * is picked from chips with icons rather than a drop-down list, so all eight are visible at once.
 */
@OptIn(ExperimentalLayoutApi::class)
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
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Kind",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldType.entries.forEach { option ->
                        val selected = option == type
                        Surface(
                            onClick = { type = option },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(option.icon(), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(option.displayName(), style = MaterialTheme.typography.labelLarge)
                            }
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

/** Picking a section or a type: a sheet of big icon tiles instead of a long drop-down list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooserSheet(
    title: String,
    options: List<ChooserOption>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(options, key = { it.id }) { option ->
                val selected = option.id == selectedId
                Surface(
                    onClick = { onSelect(option.id) },
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (option.colorKey != null) {
                            IconAvatar(
                                iconKey = option.iconKey,
                                colorKey = option.colorKey,
                                size = 40.dp,
                                iconSize = 20.dp,
                            )
                        } else {
                            Icon(
                                imageVector = IconCatalog.image(option.iconKey),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp).padding(top = 2.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---- Files step -------------------------------------------------------------

@Composable
private fun FilesStep(viewModel: EditorViewModel, state: EditorUiState) {
    var recording by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<String?>(null) }

    val resolveName = rememberAttachmentNamer()
    val pickers = rememberAttachPickers(viewModel, resolveName)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "files-title") {
            Text(
                text = "Add proof or pictures",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item(key = "attach-tiles") {
            AttachTiles(
                enabled = !state.importing,
                pickers = pickers,
                onRecord = { recording = true },
            )
        }

        item(key = "files-safety") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Encrypted on this phone. Never added to your gallery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.draft.attachments.isNotEmpty()) {
            item(key = "attached-header") {
                FormHeader(title = "Attached", trailing = "${state.draft.attachments.size}")
            }
            items(state.draft.attachments, key = { it.id }) { attachment ->
                Box(Modifier.animateItem()) {
                    if (attachment.isAudio) {
                        VoiceNoteRow(
                            storedName = attachment.storedName,
                            displayName = attachment.displayName,
                            trailing = {
                                DeleteAttachmentButton { viewModel.removeAttachment(attachment.id) }
                            },
                        )
                    } else {
                        AttachmentEditorRow(
                            attachment = attachment,
                            onCaptionChange = { viewModel.setAttachmentCaption(attachment.id, it) },
                            canCrop = attachment.isImage,
                            onCrop = { cropping = attachment.id },
                            onRemove = { viewModel.removeAttachment(attachment.id) },
                            onToggleWatermark = {
                                viewModel.setAttachmentWatermark(attachment.id, it)
                            },
                        )
                    }
                }
            }
        }

        item(key = "note") {
            NoteField(value = state.draft.note, onValueChange = viewModel::setNote)
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

    CropHost(
        attachmentId = cropping,
        attachments = state.draft.attachments,
        onDismiss = { cropping = null },
        onCropped = { id, bytes -> viewModel.replaceAttachmentBytes(id, bytes) },
    )
}

// ---- Attachment pieces shared by both steps ---------------------------------

/** The four system pickers both steps attach through, created once per screen. */
private class AttachPickers(
    val photo: () -> Unit,
    val file: () -> Unit,
    val video: () -> Unit,
    val audio: () -> Unit,
)

@Composable
private fun rememberAttachPickers(
    viewModel: EditorViewModel,
    resolveName: (Uri) -> Pair<String, String>,
): AttachPickers {
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.addAttachments(listOfNotNull(uri), resolveName) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.addAttachments(listOfNotNull(uri), resolveName) }

    // A clip or a track already sitting on the phone each get their own picker instead of
    // getting lost in a document chooser that wasn't built for them.
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.addAttachments(listOfNotNull(uri), resolveName) }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.addAttachments(listOfNotNull(uri), resolveName) }

    return AttachPickers(
        photo = {
            runCatching {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        },
        file = { runCatching { filePicker.launch(DOCUMENT_MIME_TYPES) } },
        video = {
            runCatching {
                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }
        },
        audio = { runCatching { audioPicker.launch(arrayOf("audio/*")) } },
    )
}

/** Five square tiles in one row — every kind of attachment, one tap each. */
@Composable
private fun AttachTiles(enabled: Boolean, pickers: AttachPickers, onRecord: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AttachTile(Icons.Rounded.AddPhotoAlternate, "Photo", enabled, pickers.photo, Modifier.weight(1f))
        AttachTile(Icons.Rounded.AttachFile, "File", enabled, pickers.file, Modifier.weight(1f))
        AttachTile(Icons.Rounded.Mic, "Voice", enabled, onRecord, Modifier.weight(1f))
        AttachTile(Icons.Rounded.Videocam, "Video", enabled, pickers.video, Modifier.weight(1f))
        AttachTile(Icons.Rounded.AudioFile, "Audio", enabled, pickers.audio, Modifier.weight(1f))
    }
}

@Composable
private fun AttachTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (highlight) EzzyLime else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlight) EzzyOnLime else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(text = label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * A stored file. A picture is shown big, edge to edge, with round Crop and Delete buttons
 * floating on it; any other file gets a tidy row with its kind's icon. Under it, the remark is
 * a soft one-line box, and a picture's watermark is a single tappable chip rather than a whole
 * switch row with two lines of explanation.
 */
@Composable
private fun AttachmentEditorRow(
    attachment: AttachmentDraft,
    onCaptionChange: (String) -> Unit,
    canCrop: Boolean,
    onCrop: () -> Unit,
    onRemove: () -> Unit,
    onToggleWatermark: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (attachment.isImage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    EncryptedImage(
                        storedName = attachment.storedName,
                        contentDescription = attachment.displayName,
                        modifier = Modifier.fillMaxSize(),
                        watermark = attachment.watermark,
                        watermarkStyle = attachment.watermarkStyle,
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (canCrop) {
                            CircleIconButton(
                                icon = Icons.Rounded.Crop,
                                contentDescription = "Crop ${attachment.displayName}",
                                onClick = onCrop,
                                size = 40.dp,
                                container = Color.Black.copy(alpha = 0.55f),
                                content = Color.White,
                            )
                        }
                        CircleIconButton(
                            icon = Icons.Rounded.DeleteOutline,
                            contentDescription = "Remove ${attachment.displayName}",
                            onClick = onRemove,
                            size = 40.dp,
                            container = Color.Black.copy(alpha = 0.55f),
                            content = Color.White,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                    ) {
                        Text(
                            text = "${attachment.displayName} · ${attachment.sizeLabel()}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (attachment.isPdf) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = when {
                                attachment.isPdf -> Icons.Rounded.PictureAsPdf
                                attachment.isVideo -> Icons.Rounded.Videocam
                                else -> Icons.Rounded.Description
                            },
                            contentDescription = null,
                            tint = if (attachment.isPdf) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = attachment.sizeLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DeleteAttachmentButton(onRemove)
                }
            }

            Spacer(Modifier.height(8.dp))

            TextField(
                value = attachment.caption,
                onValueChange = onCaptionChange,
                placeholder = { Text("Add a remark — what this shows") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // Only a picture can carry a visible stamp — the chip never sits beside a PDF,
            // video or audio row where it would silently do nothing.
            if (attachment.isImage) {
                Spacer(Modifier.height(8.dp))
                val on = attachment.watermark
                Surface(
                    onClick = { onToggleWatermark(!on) },
                    shape = CircleShape,
                    color = if (on) EzzyLime else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (on) EzzyOnLime else MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (on) Icons.Rounded.Check else Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (on) "Watermark on when shared" else "Add watermark when shared",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

private fun AttachmentDraft.sizeLabel(): String = when {
    sizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes / 1024f / 1024f)
    else -> "${(sizeBytes / 1024).coerceAtLeast(1)} KB"
}

/** Opens the cropper for whichever attachment is currently selected, if any. */
@Composable
private fun CropHost(
    attachmentId: String?,
    attachments: List<AttachmentDraft>,
    onDismiss: () -> Unit,
    onCropped: (String, ByteArray) -> Unit,
) {
    val target = attachmentId?.let { id -> attachments.firstOrNull { it.id == id } }
    if (attachmentId != null && target == null) {
        onDismiss()
        return
    }
    if (target == null) return

    ImageCropDialog(
        storedName = target.storedName,
        onCancel = onDismiss,
        onCropped = { bytes ->
            onDismiss()
            onCropped(target.id, bytes)
        },
    )
}

/** Resolves a picked file's display name and type, off the main thread when it is used. */
@Composable
private fun rememberAttachmentNamer(): (Uri) -> Pair<String, String> {
    val context = LocalContext.current
    return remember(context) { { uri -> resolveAttachmentName(context, uri) } }
}

/**
 * The plain, non-Compose half of [rememberAttachmentNamer] — pulled out so the share-target
 * screen can name an incoming picture the same way without needing a composable to do it.
 */
internal fun resolveAttachmentName(context: Context, uri: Uri): Pair<String, String> {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "File"
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index) ?: name
        }
    }
    return name to (context.contentResolver.getType(uri) ?: "application/octet-stream")
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

private fun FieldType.icon(): ImageVector = when (this) {
    FieldType.TEXT -> Icons.Rounded.TextFields
    FieldType.MULTILINE -> Icons.AutoMirrored.Rounded.Notes
    FieldType.SECRET -> Icons.Rounded.Lock
    FieldType.NUMBER -> Icons.Rounded.Numbers
    FieldType.PHONE -> Icons.Rounded.Phone
    FieldType.EMAIL -> Icons.Rounded.AlternateEmail
    FieldType.URL -> Icons.Rounded.Language
    FieldType.DATE -> Icons.Rounded.CalendarMonth
}

private fun FieldType.placeholder(): String = when (this) {
    FieldType.TEXT -> "Type here"
    FieldType.MULTILINE -> "Write as much as you like"
    FieldType.SECRET -> "Hidden as you type"
    FieldType.NUMBER -> "0000 0000 0000"
    FieldType.PHONE -> "03xx xxxxxxx"
    FieldType.EMAIL -> "name@example.com"
    FieldType.URL -> "example.com"
    FieldType.DATE -> "Tap to pick a date"
}

/** Digits read better in the monospaced style, where they line up like on the card itself. */
private fun FieldType.isNumeric(): Boolean =
    this == FieldType.NUMBER || this == FieldType.PHONE || this == FieldType.SECRET

private fun FieldType.capitalization(): KeyboardCapitalization = when (this) {
    FieldType.TEXT -> KeyboardCapitalization.Words
    FieldType.MULTILINE -> KeyboardCapitalization.Sentences
    else -> KeyboardCapitalization.None
}

/** The fields people add by hand most often, one tap each under the form. */
private val QUICK_FIELDS: List<Pair<String, FieldType>> = listOf(
    "Phone" to FieldType.PHONE,
    "Email" to FieldType.EMAIL,
    "Number" to FieldType.NUMBER,
    "PIN" to FieldType.SECRET,
    "Date" to FieldType.DATE,
    "Website" to FieldType.URL,
    "Note" to FieldType.MULTILINE,
)

private fun FieldType.keyboardType(): KeyboardType = when (this) {
    FieldType.NUMBER -> KeyboardType.Number
    FieldType.PHONE -> KeyboardType.Phone
    FieldType.EMAIL -> KeyboardType.Email
    FieldType.URL -> KeyboardType.Uri
    FieldType.SECRET -> KeyboardType.Password
    else -> KeyboardType.Text
}
