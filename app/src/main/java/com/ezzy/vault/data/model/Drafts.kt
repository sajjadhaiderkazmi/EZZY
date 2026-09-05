package com.ezzy.vault.data.model

import com.ezzy.vault.util.WatermarkStyle
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
    /** Stamps "FOR VERIFICATION PURPOSE ONLY" across this picture on Copy or Share. */
    val watermark: Boolean = false,
    /** Carried through the editor untouched, so re-saving an entry cannot quietly reset a
     *  stamp the user has already tuned on the entry's own screen. */
    val watermarkStyle: WatermarkStyle = WatermarkStyle.Default,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isPdf: Boolean get() = mimeType == "application/pdf"
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
    /** A custom picture for this entry, already sealed on disk — null uses the section's icon. */
    val iconPhoto: String? = null,
) {
    val filledFields: List<FieldDraft> get() = fields.filterNot { it.isBlank }
}
