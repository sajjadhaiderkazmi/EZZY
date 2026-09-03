package com.ezzy.vault.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.AttachmentEntity
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.ui.LocalSettings
import com.ezzy.vault.ui.components.EncryptedImage
import com.ezzy.vault.ui.components.EzzyChip
import com.ezzy.vault.ui.components.FieldValueRow
import com.ezzy.vault.ui.components.IconAvatar
import com.ezzy.vault.ui.components.SectionHeader
import com.ezzy.vault.ui.components.VoiceNoteRow
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.ui.rememberCopier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    var confirmDelete by remember { mutableStateOf(false) }
    var previewAttachment by remember { mutableStateOf<AttachmentEntity?>(null) }

    val details = item

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
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconAvatar(
                            iconKey = category?.iconKey,
                            colorKey = category?.colorKey,
                            size = 52.dp,
                            iconSize = 26.dp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = details.item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (details.item.subtitle.isNotBlank()) {
                                Text(
                                    text = details.item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                category?.let { EzzyChip(text = it.name) }
                                templateName?.let { EzzyChip(text = it) }
                            }
                        }
                    }
                }
            }

            if (details.fields.isNotEmpty()) {
                item {
                    SectionHeader(
                        text = "Details",
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        trailing = {
                            OutlinedButton(
                                onClick = {
                                    val all = details.sortedFields.joinToString("\n") {
                                        "${it.label}: ${it.value}"
                                    }
                                    copy("All details", all, sensitive = true)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Copy all", style = MaterialTheme.typography.labelMedium)
                            }
                        },
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
                    SectionHeader(
                        text = "Files (${files.size})",
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(files, key = { it.id }) { attachment ->
                            AttachmentThumb(
                                attachment = attachment,
                                onClick = { previewAttachment = attachment },
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
        AttachmentPreviewDialog(attachment = attachment, onDismiss = { previewAttachment = null })
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
}

@Composable
private fun AttachmentThumb(attachment: AttachmentEntity, onClick: () -> Unit) {
    val isImage = attachment.mimeType.startsWith("image/")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(124.dp),
    ) {
        Column(modifier = Modifier.clickable(onClick = onClick)) {
            if (isImage) {
                EncryptedImage(
                    storedName = attachment.storedName,
                    contentDescription = attachment.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
}

@Composable
private fun AttachmentPreviewDialog(attachment: AttachmentEntity, onDismiss: () -> Unit) {
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
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            modifier = Modifier.size(46.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Stored securely · ${attachment.sizeBytes / 1024} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
