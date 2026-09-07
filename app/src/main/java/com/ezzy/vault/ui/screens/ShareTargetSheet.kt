package com.ezzy.vault.ui.screens

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.data.model.AttachmentDraft
import com.ezzy.vault.ui.components.EmptyState
import com.ezzy.vault.ui.components.EncryptedImage
import com.ezzy.vault.ui.components.ItemRow
import com.ezzy.vault.ui.ezzyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What lands on screen the moment a picture is shared into EZZY from Gallery or anywhere else.
 * Every shared picture is decrypted into the vault as soon as this sheet opens — the one copy
 * both paths below go on to use — so [ShareTargetViewModel] holds the only reference to them
 * until one of the two choices claims it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetSheet(
    uris: List<Uri>,
    // Unique per share, supplied by the caller — MainActivity never recreates between two
    // separate shares, so leaving this to default to the call site alone would hand a second
    // share the first one's already-imported (or already-discarded) ViewModel instead of a
    // fresh one.
    viewModelKey: String,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onAddedToExisting: (itemId: String, title: String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ShareTargetViewModel = ezzyViewModel(key = viewModelKey) {
        ShareTargetViewModel(it)
    }
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()

    LaunchedEffect(uris) { viewModel.import(context, uris) }

    var pickingExisting by remember { mutableStateOf(false) }

    fun close() {
        viewModel.discardIfUnclaimed()
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pickingExisting) {
                        IconButton(onClick = { pickingExisting = false }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Text(
                        text = if (pickingExisting) "Add to an entry" else "Save to EZZY",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (pickingExisting) 0.dp else 16.dp),
                    )
                    IconButton(onClick = ::close) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                }

                when {
                    attachments == null -> Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    attachments.orEmpty().isEmpty() -> EmptyState(
                        icon = Icons.Rounded.BrokenImage,
                        title = "Could not read that picture",
                        message = "The app it came from may have already let go of it — try sharing it again.",
                        modifier = Modifier.weight(1f),
                    )

                    else -> {
                        SharedThumbnails(attachments.orEmpty())

                        if (!pickingExisting) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                ShareChoiceCard(
                                    icon = Icons.Rounded.Add,
                                    title = "New Entry",
                                    subtitle = "Saved as Screenshot / Image, ready to review",
                                    onClick = {
                                        viewModel.createNew()
                                        onCreateNew()
                                    },
                                )
                                Spacer(Modifier.height(10.dp))
                                ShareChoiceCard(
                                    icon = Icons.Rounded.SwapHoriz,
                                    title = "Existing Entry",
                                    subtitle = "Add it to something already saved",
                                    onClick = { pickingExisting = true },
                                )
                            }
                        } else {
                            ExistingEntryPicker(
                                query = query,
                                onQueryChange = viewModel::setQuery,
                                results = results,
                                categories = categories,
                                saving = saving,
                                onPick = { item ->
                                    viewModel.addToExisting(item.item.id) { ok ->
                                        if (ok) onAddedToExisting(item.item.id, item.item.title)
                                    }
                                },
                                // Claims exactly what is left below the header and the
                                // thumbnail strip — an unweighted fillMaxSize() here would
                                // instead ask for the whole dialog's height on top of that,
                                // overflowing past the bottom of the screen by the same amount.
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedThumbnails(attachments: List<AttachmentDraft>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            EncryptedImage(
                storedName = attachment.storedName,
                contentDescription = attachment.displayName,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun ShareChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExistingEntryPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<ItemWithDetails>,
    categories: Map<String, CategoryEntity>,
    saving: Boolean,
    onPick: (ItemWithDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search your entries") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (results.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No entries yet",
                    message = "Save one as a new entry instead, or search for another name.",
                )
            }
        }

        items(results, key = { it.item.id }) { result ->
            val category = categories[result.item.categoryId]
            ItemRow(
                item = result,
                iconKey = category?.iconKey,
                colorKey = category?.colorKey,
                onClick = { if (!saving) onPick(result) },
            )
        }
    }
}

/**
 * Everything one shared picture needs before it can become — or join — an entry: decrypted
 * into the vault the moment the sheet opens, and handed to whichever of the two choices claims
 * it. Neither choice re-imports anything; they only ever place this same copy.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ShareTargetViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository

    /** Null while still importing, empty if every picture failed to read. */
    private val _attachments = MutableStateFlow<List<AttachmentDraft>?>(null)
    val attachments: StateFlow<List<AttachmentDraft>?> = _attachments.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<ItemWithDetails>> = _query
        .debounce(140)
        .flatMapLatest { q -> if (q.isBlank()) repository.observeAllItems() else repository.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<Map<String, CategoryEntity>> = repository.observeCategories()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** True once one of the two choices below has taken the imported files — past that point
     *  they belong to a draft or a saved entry, and must never be swept as unused. */
    private var claimed = false

    private var imported = false

    fun setQuery(value: String) {
        _query.value = value
    }

    fun import(context: Context, uris: List<Uri>) {
        if (imported) return
        imported = true
        viewModelScope.launch {
            _attachments.value = uris.mapNotNull { uri ->
                val stored = container.attachmentStore.import(uri) ?: return@mapNotNull null
                val (name, mime) = withContext(Dispatchers.IO) { resolveAttachmentName(context, uri) }
                AttachmentDraft(
                    displayName = name,
                    mimeType = mime,
                    storedName = stored.storedName,
                    sizeBytes = stored.sizeBytes,
                )
            }
        }
    }

    /** Hands the imported pictures to the next fresh editor that opens — the "New Entry" path. */
    fun createNew() {
        claimed = true
        PendingShareAttachments.set(_attachments.value.orEmpty())
    }

    /** Attaches the imported pictures straight onto an already-saved entry's own Files. */
    fun addToExisting(itemId: String, onDone: (Boolean) -> Unit) {
        val toAdd = _attachments.value.orEmpty()
        if (toAdd.isEmpty()) {
            onDone(false)
            return
        }
        claimed = true
        viewModelScope.launch {
            _saving.value = true
            val draft = repository.draftFor(itemId, "")
            repository.saveItem(draft.copy(attachments = draft.attachments + toAdd))
            _saving.value = false
            onDone(true)
        }
    }

    /** Deletes whatever was decrypted in if neither choice above ever claimed it — cancelling
     *  out of this sheet must not leave a picture's bytes sitting on disk with nothing pointing
     *  at them. [com.ezzy.vault.data.repo.VaultRepository]'s own orphan sweep would eventually
     *  catch the same file on the next unrelated save, but there is no reason to wait for that. */
    fun discardIfUnclaimed() {
        if (claimed) return
        val toDelete = _attachments.value.orEmpty()
        if (toDelete.isEmpty()) return
        viewModelScope.launch {
            toDelete.forEach { container.attachmentStore.delete(it.storedName) }
        }
    }
}
