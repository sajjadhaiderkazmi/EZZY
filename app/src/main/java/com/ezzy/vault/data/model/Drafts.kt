package com.ezzy.vault.data.model

import java.util.UUID

/** A field as it is being edited, before it becomes a row. */
data class FieldDraft(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val value: String = "",
    val type: FieldType = FieldType.TEXT,
    /**
     * True when the name came from the entry's type. Those names are shown rather than edited —
     * only the data below them is typed in — while a field the user adds is named up front.
     */
    val fromTemplate: Boolean = false,
) {
    val isBlank: Boolean get() = label.isBlank() && value.isBlank()
}

/** An attachment already sealed on disk, waiting to be linked to an item. */
data class AttachmentDraft(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val caption: String = "",
    val mimeType: String,
    val storedName: String,
    val sizeBytes: Long,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
}

/** Everything the editor collects across its steps. */
data class ItemDraft(
    val id: String = UUID.randomUUID().toString(),
    val isNew: Boolean = true,
    val categoryId: String = "",
    val templateId: String? = null,
    val title: String = "",
    val subtitle: String = "",
    val note: String = "",
    val isPinned: Boolean = false,
    val fields: List<FieldDraft> = emptyList(),
    val attachments: List<AttachmentDraft> = emptyList(),
) {
    val filledFields: List<FieldDraft> get() = fields.filterNot { it.isBlank }
}
