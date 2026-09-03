package com.ezzy.vault.data.model

import kotlinx.serialization.Serializable

/**
 * How a single value behaves: which keyboard it asks for, whether it is masked on screen,
 * and how it is presented in the overlay.
 */
enum class FieldType {
    TEXT,
    MULTILINE,
    SECRET,
    NUMBER,
    PHONE,
    EMAIL,
    URL,
    DATE;

    val isMasked: Boolean get() = this == SECRET

    companion object {
        fun from(raw: String?): FieldType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

/** One field position inside a template, before it is filled in. */
@Serializable
data class TemplateField(
    val label: String,
    val type: FieldType = FieldType.TEXT,
    val hint: String = "",
    val required: Boolean = false,
)

/** A template is the "survey" that the editor walks the user through. */
@Serializable
data class TemplateSpec(
    val fields: List<TemplateField> = emptyList(),
    val allowsAttachments: Boolean = true,
    /** Example shown under the Title box, so every type suggests something of its own. */
    val titleHint: String = "",
)
