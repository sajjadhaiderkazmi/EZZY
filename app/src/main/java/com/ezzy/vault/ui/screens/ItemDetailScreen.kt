package com.ezzy.vault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.AttachmentEntity
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.FieldEntity
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.data.model.FieldType
import com.ezzy.vault.ui.LocalSettings
import com.ezzy.vault.ui.LocalSnackbar
import com.ezzy.vault.ui.components.EncryptedImage
import com.ezzy.vault.ui.components.FieldValueRow
import com.ezzy.vault.ui.components.SectionHeader
import com.ezzy.vault.ui.components.VoiceNoteRow
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.ui.icons.IconCatalog
import com.ezzy.vault.ui.rememberAttachmentActions
import com.ezzy.vault.ui.theme.brandBannerColors
import com.ezzy.vault.ui.rememberCopier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModel(
    private val container: AppContainer,
    private val itemId: String,
) : ViewModel() {

    val item: StateFlow<ItemWithDetails?> = container.repository.observeItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Derived from the item itself, so moving an entry to another section updates the header. */
    val category: StateFlow<CategoryEntity?> = item
        .filterNotNull()
        .flatMapLatest { container.repository.observeCategory(it.item.categoryId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val templateName: StateFlow<String?> = item
        .map { it?.item?.templateId?.let { id -> container.repository.template(id)?.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { container.repository.markUsed(itemId) }
    }

    fun togglePin() {
        val current = item.value ?: return
        viewModelScope.launch { container.repository.setPinned(itemId, !current.item.isPinned) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            container.repository.deleteItem(itemId)
            onDone()
        }
    }

    /** The bulk "Delete" a multi-select of this entry's own files offers. */
    fun deleteAttachments(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { container.repository.deleteAttachments(ids) }
    }

    fun setAttachmentWatermark(id: String, enabled: Boolean) {
        viewModelScope.launch { container.repository.setAttachmentWatermark(id, enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val viewModel: ItemDetailViewModel = ezzyViewModel(key = "item-$itemId") {
        ItemDetailViewModel(it, itemId)
    }
    val item by viewModel.item.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val templateName by viewModel.templateName.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val copy = rememberCopier()
    val fileActions = rememberAttachmentActions()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var confirmDelete by remember { mutableStateOf(false) }
    var previewAttachment by remember { mutableStateOf<AttachmentEntity?>(null) }

    // Long-pressing a file turns the row into a multi-select — a plain tap toggles from then
    // on, until the selection is cleared, the same gesture pattern the app already uses for a
    // section's own cards. Cleared whenever the entry itself changes so a stale id can't linger.
    var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDeleteFiles by remember { mutableStateOf(false) }

    val details = item
    LaunchedEffect(details?.item?.id) { selectedFileIds = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(details?.item?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePin() }) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = if (details?.item?.isPinned == true) "Unpin" else "Pin",
                            tint = if (details?.item?.isPinned == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete entry",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onEdit,
                icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                text = { Text("Edit") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (details == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                // A consistent brand banner rather than a per-category tint: ten accent colours
                // as full-bleed backgrounds would fight each other entry to entry, and this is
                // the one screen worth spending the app's identity on.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Brush.linearGradient(brandBannerColors())),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 26.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (details.item.iconPhoto != null) {
                            EncryptedImage(
                                storedName = details.item.iconPhoto,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = IconCatalog.image(category?.iconKey),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = details.item.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (details.item.subtitle.isNotBlank()) {
                            Text(
                                text = details.item.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (category != null || templateName != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                category?.let { HeroChip(it.name) }
                                templateName?.let { HeroChip(it) }
                            }
                        }
                    }
                }
            }

            expiryStatusOf(details.fields)?.let { status ->
                item { ExpiryBanner(status) }
            }

            if (details.fields.isNotEmpty()) {
                item {
                    Button(
                        onClick = {
                            val all = details.sortedFields.joinToString("\n") {
                                "${it.label}: ${it.value}"
                            }
                            copy("All details", all, sensitive = true)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Copy all")
                    }
                }
                item {
                    SectionHeader(
                        text = "Details",
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
                items(details.sortedFields, key = { it.id }) { field ->
                    FieldValueRow(
                        label = field.label,
                        value = field.value,
                        type = field.type,
                        startMasked = settings.maskSecrets,
                        onCopy = { copy(field.label, field.value, field.type.isMasked) },
                    )
                }
            }

            val voiceNotes = details.sortedAttachments.filter { it.mimeType.startsWith("audio/") }
            val files = details.sortedAttachments - voiceNotes.toSet()

            if (voiceNotes.isNotEmpty()) {
                item {
                    SectionHeader(
                        text = "Voice notes (${voiceNotes.size})",
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(voiceNotes, key = { it.id }) { note ->
                    VoiceNoteRow(storedName = note.storedName, displayName = note.displayName)
                }
            }

            if (files.isNotEmpty()) {
                item {
                    if (selectedFileIds.isNotEmpty()) {
                        FileSelectionBar(
                            count = selectedFileIds.size,
                            onCopy = {
                                val selected = files.filter { it.id in selectedFileIds }
                                fileActions.copyMultiple(selected) { ok ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            if (ok) "Copied — paste in any app" else "Could not copy them"
                                        )
                                    }
                                }
                                selectedFileIds = emptySet()
                            },
                            onDelete = { confirmDeleteFiles = true },
                            onCancel = { selectedFileIds = emptySet() },
                        )
                    } else {
                        SectionHeader(
                            text = "Files (${files.size})",
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(files, key = { it.id }) { attachment ->
                            AttachmentThumb(
                                attachment = attachment,
                                selected = attachment.id in selectedFileIds,
                                selectionMode = selectedFileIds.isNotEmpty(),
                                onClick = {
                                    if (selectedFileIds.isNotEmpty()) {
                                        selectedFileIds = if (attachment.id in selectedFileIds) {
                                            selectedFileIds - attachment.id
                                        } else {
                                            selectedFileIds + attachment.id
                                        }
                                    } else {
                                        previewAttachment = attachment
                                    }
                                },
                                onLongClick = { selectedFileIds = selectedFileIds + attachment.id },
                            )
                        }
                    }
                }
            }

            if (details.item.note.isNotBlank()) {
                item {
                    SectionHeader(
                        text = "Note",
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = details.item.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
        }
    }

    previewAttachment?.let { attachment ->
        AttachmentPreviewDialog(
            attachment = attachment,
            onDismiss = { previewAttachment = null },
            onToggleWatermark = { enabled ->
                viewModel.setAttachmentWatermark(attachment.id, enabled)
                // Keeps the open dialog's own switch in sync — it holds a snapshot of the row,
                // not the live one, so it would otherwise sit one tap behind.
                previewAttachment = attachment.copy(watermark = enabled)
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("\"${details?.item?.title.orEmpty()}\" and its files will be removed permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onBack)
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

    if (confirmDeleteFiles) {
        val count = selectedFileIds.size
        AlertDialog(
            onDismissRequest = { confirmDeleteFiles = false },
            title = { Text(if (count == 1) "Delete this file?" else "Delete $count files?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteFiles = false
                        viewModel.deleteAttachments(selectedFileIds)
                        selectedFileIds = emptySet()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFiles = false }) { Text("Cancel") }
            },
        )
    }
}

/** The bar that replaces the "Files" header once one or more of an entry's own files is
 *  long-pressed into a multi-select — the same Copy and Delete a single file already offers,
 *  just for as many as are picked. */
@Composable
private fun FileSelectionBar(
    count: Int,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count selected",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCopy) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy selected files")
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete selected files",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
        }
    }
}

/** Small translucent pill for the category/type labels sitting on the gradient banner. */
@Composable
private fun HeroChip(text: String) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.2f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentThumb(
    attachment: AttachmentEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isImage = attachment.mimeType.startsWith("image/")
    val isPdf = attachment.mimeType == "application/pdf"
    val isVideo = attachment.mimeType.startsWith("video/")
    Box(modifier = Modifier.width(124.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            ) {
                if (isImage) {
                    EncryptedImage(
                        storedName = attachment.storedName,
                        contentDescription = attachment.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        watermark = attachment.watermark,
                    )
                } else {
                    // A PDF or a video gets its own mark and colour — a scanned document or a
                    // clip is the common case here, and a generic page icon made every file
                    // look the same.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = when {
                                isPdf -> Icons.Rounded.PictureAsPdf
                                isVideo -> Icons.Rounded.Videocam
                                else -> Icons.Rounded.Description
                            },
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = if (isPdf) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isPdf) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "PDF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Text(
                    text = attachment.caption.ifBlank { attachment.displayName },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.35f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewDialog(
    attachment: AttachmentEntity,
    onDismiss: () -> Unit,
    onToggleWatermark: (Boolean) -> Unit,
) {
    val isPdf = attachment.mimeType == "application/pdf"
    val isVideo = attachment.mimeType.startsWith("video/")
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = attachment.caption.ifBlank { attachment.displayName },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close preview")
                    }
                }
                if (attachment.mimeType.startsWith("image/")) {
                    EncryptedImage(
                        storedName = attachment.storedName,
                        contentDescription = attachment.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        watermark = attachment.watermark,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = when {
                                isPdf -> Icons.Rounded.PictureAsPdf
                                isVideo -> Icons.Rounded.Videocam
                                else -> Icons.Rounded.Description
                            },
                            contentDescription = null,
                            modifier = Modifier.size(46.dp),
                            tint = if (isPdf) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Stored securely · ${attachment.sizeBytes / 1024} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AttachmentActionRow(attachment = attachment, onToggleWatermark = onToggleWatermark)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/**
 * Copy, Share and — for anything that is not a picture — Open, on the file the user is looking
 * at. Each one decrypts a single copy into EZZY's staging folder and hands the other app a
 * one-off read grant for that file alone; the vault stays sealed.
 */
@Composable
private fun AttachmentActionRow(attachment: AttachmentEntity, onToggleWatermark: (Boolean) -> Unit) {
    val actions = rememberAttachmentActions()
    val isImage = attachment.mimeType.startsWith("image/")
    val label = attachment.caption.ifBlank { attachment.displayName }

    // The app's snackbar host sits behind this dialog's own window, so a message sent there
    // would never be seen. The result is reported in the dialog instead.
    var status by remember(attachment.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(status) {
        if (status != null) {
            delay(2200)
            status = null
        }
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        // Only a picture can carry the pattern — a switch here that did nothing for a PDF or a
        // video would just be confusing.
        if (isImage) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Watermark", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "\"FOR VERIFICATION PURPOSE ONLY\" on Copy and Share",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = attachment.watermark, onCheckedChange = onToggleWatermark)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AttachmentAction(
                icon = Icons.Rounded.ContentCopy,
                label = "Copy",
                modifier = Modifier.weight(1f),
                onClick = {
                    actions.copy(
                        attachment.storedName, label, attachment.mimeType, attachment.watermark,
                    ) { ok ->
                        status = if (ok) "Copied — paste it in any app" else "Could not copy it"
                    }
                },
            )
            AttachmentAction(
                icon = Icons.Rounded.Share,
                label = "Share",
                modifier = Modifier.weight(1f),
                onClick = {
                    actions.share(
                        attachment.storedName, label, attachment.mimeType, attachment.watermark,
                    ) { ok ->
                        if (!ok) status = "No app on this phone can receive it"
                    }
                },
            )
            if (!isImage) {
                AttachmentAction(
                    icon = Icons.Rounded.OpenInNew,
                    label = "Open",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        actions.open(attachment.storedName, label, attachment.mimeType) { ok ->
                            if (!ok) status = "No app on this phone can open it"
                        }
                    },
                )
            }
        }
        status?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AttachmentAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

// ---- Expiry ------------------------------------------------------------------

/**
 * What a document's expiry date means today. A CNIC or a passport is mostly worth storing so
 * you can see, at a glance, whether it is still good — so the date is read back and stated in
 * days rather than left as one more line of text to work out for yourself.
 */
private data class ExpiryStatus(
    val label: String,
    val detail: String,
    val expired: Boolean,
    val soon: Boolean,
)

/** The format the editor's date picker writes, so a stored date always reads back cleanly. */
private val STORED_DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

private val EXPIRY_WORDS = listOf("expir", "ends", "valid", "renew")

private fun expiryStatusOf(fields: List<FieldEntity>): ExpiryStatus? {
    val field = fields.firstOrNull { candidate ->
        val label = candidate.label.lowercase(Locale.ROOT)
        candidate.type == FieldType.DATE &&
            candidate.value.isNotBlank() &&
            EXPIRY_WORDS.any { label.contains(it) } &&
            // "Valid from" and "Issued on" are the opposite of an expiry.
            !label.contains("from") &&
            !label.contains("issue")
    } ?: return null

    val parsed = runCatching { STORED_DATE_FORMAT.parse(field.value) }.getOrNull() ?: return null

    fun midnight(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val days = TimeUnit.MILLISECONDS.toDays(
        midnight(parsed.time) - midnight(System.currentTimeMillis())
    )

    return when {
        days < 0 -> ExpiryStatus(
            label = if (days == -1L) "Expired yesterday" else "Expired ${-days} days ago",
            detail = "${field.label} · ${field.value}",
            expired = true,
            soon = false,
        )

        days == 0L -> ExpiryStatus("Expires today", "${field.label} · ${field.value}", false, true)

        days <= 60 -> ExpiryStatus(
            label = if (days == 1L) "Expires tomorrow" else "Expires in $days days",
            detail = "${field.label} · ${field.value}",
            expired = false,
            soon = true,
        )

        else -> ExpiryStatus(
            label = "Valid for $days more days",
            detail = "${field.label} · ${field.value}",
            expired = false,
            soon = false,
        )
    }
}

@Composable
private fun ExpiryBanner(status: ExpiryStatus) {
    val container = when {
        status.expired -> MaterialTheme.colorScheme.errorContainer
        status.soon -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val foreground = when {
        status.expired -> MaterialTheme.colorScheme.onErrorContainer
        status.soon -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (status.expired) Icons.Rounded.ErrorOutline
                else Icons.Rounded.EventAvailable,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = foreground,
                )
                Text(
                    text = status.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = foreground.copy(alpha = 0.8f),
                )
            }
        }
    }
}
