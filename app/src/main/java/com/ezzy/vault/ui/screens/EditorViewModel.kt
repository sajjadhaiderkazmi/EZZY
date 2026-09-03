package com.ezzy.vault.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.TemplateEntity
import com.ezzy.vault.data.model.AttachmentDraft
import com.ezzy.vault.data.model.FieldDraft
import com.ezzy.vault.data.model.FieldType
import com.ezzy.vault.data.model.ItemDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The wizard's steps, in order. */
enum class EditorStep(val title: String, val caption: String) {
    SECTION("Section", "Where does this belong?"),
    DETAILS("Details", "Fill in what you want to copy later"),
    FILES("Files & note", "Attach photos, scans or receipts"),
    ;

    companion object {
        val ordered: List<EditorStep> = entries
    }
}

data class EditorUiState(
    val draft: ItemDraft = ItemDraft(),
    /**
     * Only the steps this particular entry needs. Opening "Add" from inside a section already
     * answers the section question, so that step is dropped rather than asked again.
     */
    val steps: List<EditorStep> = EditorStep.ordered,
    val step: EditorStep = EditorStep.SECTION,
    /** Example text under the Title box, taken from whichever type is selected. */
    val titleHint: String = DEFAULT_TITLE_HINT,
    val loading: Boolean = true,
    val importing: Boolean = false,
    val message: String? = null,
) {
    val canContinue: Boolean
        get() = when (step) {
            EditorStep.SECTION -> draft.categoryId.isNotBlank()
            EditorStep.DETAILS -> draft.title.isNotBlank()
            EditorStep.FILES -> draft.title.isNotBlank()
        }

    val canSave: Boolean get() = draft.categoryId.isNotBlank() && draft.title.isNotBlank()
}

class EditorViewModel(
    private val container: AppContainer,
    private val itemId: String?,
    private val presetCategoryId: String?,
) : ViewModel() {

    private val repository = container.repository

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<TemplateEntity>> = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val draft = repository.draftFor(itemId, presetCategoryId.orEmpty())
            // Once the section is known there is nothing left to ask before the form itself:
            // the type is chosen inside it, next to everything it changes.
            val steps = if (draft.categoryId.isNotBlank()) {
                listOf(EditorStep.DETAILS, EditorStep.FILES)
            } else {
                EditorStep.ordered
            }
            _state.value = EditorUiState(
                draft = draft,
                steps = steps,
                step = steps.first(),
                titleHint = draft.templateId
                    ?.let { repository.templateSpec(it)?.titleHint }
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_TITLE_HINT,
                loading = false,
            )
        }
    }

    // ---- Navigation -------------------------------------------------------

    fun goTo(step: EditorStep) = update { it.copy(step = step) }

    fun next() = update { current ->
        val index = current.steps.indexOf(current.step)
        current.copy(step = current.steps.getOrElse(index + 1) { current.step })
    }

    fun back(): Boolean {
        val current = _state.value
        val index = current.steps.indexOf(current.step)
        if (index <= 0) return false
        update { it.copy(step = current.steps[index - 1]) }
        return true
    }

    fun consumeMessage() = update { it.copy(message = null) }

    // ---- Draft edits ------------------------------------------------------

    fun setCategory(categoryId: String) = updateDraft { it.copy(categoryId = categoryId) }

    fun setTitle(title: String) = updateDraft { it.copy(title = title) }

    fun setSubtitle(subtitle: String) = updateDraft { it.copy(subtitle = subtitle) }

    fun setNote(note: String) = updateDraft { it.copy(note = note) }

    fun setPinned(pinned: Boolean) = updateDraft { it.copy(isPinned = pinned) }

    /**
     * Applies a template's field list. Anything the user already typed is preserved by matching
     * on the label, so switching type by mistake does not wipe the entry.
     */
    fun applyTemplate(template: TemplateEntity?) {
        viewModelScope.launch {
            if (template == null) {
                update { it.copy(titleHint = DEFAULT_TITLE_HINT) }
                updateDraft { it.copy(templateId = null) }
                return@launch
            }
            val spec = repository.decodeSpec(template.specJson)
            val existing = _state.value.draft.fields
            val merged = spec.fields.map { templateField ->
                val carried = existing.firstOrNull { it.label.equals(templateField.label, true) }
                FieldDraft(
                    label = templateField.label,
                    value = carried?.value.orEmpty(),
                    type = templateField.type,
                    fromTemplate = true,
                )
            }
            val extras = existing.filter { field ->
                field.value.isNotBlank() &&
                    spec.fields.none { it.label.equals(field.label, true) }
            }
            update { it.copy(titleHint = spec.titleHint.ifBlank { DEFAULT_TITLE_HINT }) }
            updateDraft { it.copy(templateId = template.id, fields = merged + extras) }
        }
    }

    fun updateField(id: String, transform: (FieldDraft) -> FieldDraft) = updateDraft { draft ->
        draft.copy(fields = draft.fields.map { if (it.id == id) transform(it) else it })
    }

    fun addField(label: String, type: FieldType) = updateDraft { draft ->
        draft.copy(
            fields = draft.fields + FieldDraft(
                label = label.trim(),
                type = type,
                fromTemplate = false,
            )
        )
    }

    fun renameField(id: String, label: String, type: FieldType) = updateField(id) {
        it.copy(label = label.trim(), type = type)
    }

    fun removeField(id: String) = updateDraft { draft ->
        draft.copy(fields = draft.fields.filterNot { it.id == id })
    }

    // ---- Attachments ------------------------------------------------------

    fun addAttachments(uris: List<Uri>, resolveName: (Uri) -> Pair<String, String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            update { it.copy(importing = true) }
            var failures = 0
            val added = mutableListOf<AttachmentDraft>()
            uris.forEach { uri ->
                val stored = container.attachmentStore.import(uri)
                if (stored == null) {
                    failures++
                    return@forEach
                }
                val (name, mime) = withContext(Dispatchers.IO) { resolveName(uri) }
                added += AttachmentDraft(
                    displayName = name,
                    mimeType = mime,
                    storedName = stored.storedName,
                    sizeBytes = stored.sizeBytes,
                )
            }
            _state.value = _state.value.let { current ->
                current.copy(
                    draft = current.draft.copy(attachments = current.draft.attachments + added),
                    importing = false,
                    message = when {
                        failures == 0 -> null
                        added.isEmpty() -> "Could not add that file (over 25 MB or unreadable)"
                        else -> "$failures file(s) could not be added"
                    },
                )
            }
        }
    }

    /** Adds bytes EZZY produced itself: a recorded voice note, or a photo taken in-app. */
    fun addBytesAttachment(bytes: ByteArray, displayName: String, mimeType: String) {
        viewModelScope.launch {
            update { it.copy(importing = true) }
            val stored = container.attachmentStore.save(bytes)
            _state.value = _state.value.let { current ->
                current.copy(
                    importing = false,
                    message = if (stored == null) "Could not save that recording" else null,
                    draft = if (stored == null) current.draft else current.draft.copy(
                        attachments = current.draft.attachments + AttachmentDraft(
                            displayName = displayName,
                            mimeType = mimeType,
                            storedName = stored.storedName,
                            sizeBytes = stored.sizeBytes,
                        ),
                    ),
                )
            }
        }
    }

    /** Overwrites an attachment in place, which is what cropping a photo amounts to. */
    fun replaceAttachmentBytes(id: String, bytes: ByteArray) {
        val target = _state.value.draft.attachments.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val ok = container.attachmentStore.write(target.storedName, bytes)
            if (!ok) {
                update { it.copy(message = "Could not save the cropped photo") }
                return@launch
            }
            updateDraft { draft ->
                draft.copy(
                    attachments = draft.attachments.map {
                        if (it.id == id) {
                            it.copy(mimeType = "image/jpeg", sizeBytes = bytes.size.toLong())
                        } else {
                            it
                        }
                    }
                )
            }
        }
    }

    /** Fills the entry from a contact the user picked, without overwriting anything typed. */
    fun applyContact(name: String, phone: String) {
        updateDraft { draft ->
            val fields = draft.fields.toMutableList()

            fun setOrAdd(label: String, value: String, type: FieldType) {
                if (value.isBlank()) return
                val index = fields.indexOfFirst { it.label.equals(label, ignoreCase = true) }
                if (index >= 0) {
                    fields[index] = fields[index].copy(value = value, type = type)
                } else {
                    fields += FieldDraft(label = label, value = value, type = type)
                }
            }

            setOrAdd("Name", name, FieldType.TEXT)
            setOrAdd("Phone", phone, FieldType.PHONE)
            draft.copy(title = draft.title.ifBlank { name }, fields = fields)
        }
    }

    fun removeAttachment(id: String) {
        val target = _state.value.draft.attachments.firstOrNull { it.id == id } ?: return
        updateDraft { draft -> draft.copy(attachments = draft.attachments.filterNot { it.id == id }) }
        // Only sweep the file once it is no longer referenced by the saved row either.
        viewModelScope.launch {
            val stillReferenced = repository.item(_state.value.draft.id)
                ?.attachments.orEmpty()
                .any { it.storedName == target.storedName }
            if (!stillReferenced) container.attachmentStore.delete(target.storedName)
        }
    }

    // ---- Persistence ------------------------------------------------------

    fun save(onSaved: (String) -> Unit) {
        val draft = _state.value.draft
        if (draft.categoryId.isBlank() || draft.title.isBlank()) return
        viewModelScope.launch {
            val id = repository.saveItem(draft)
            onSaved(id)
        }
    }

    private fun update(transform: (EditorUiState) -> EditorUiState) {
        _state.value = transform(_state.value)
    }

    private fun updateDraft(transform: (ItemDraft) -> ItemDraft) {
        _state.value = _state.value.copy(draft = transform(_state.value.draft))
    }
}

/** Used until a type with its own example is picked. */
const val DEFAULT_TITLE_HINT = "Give this entry a name"
