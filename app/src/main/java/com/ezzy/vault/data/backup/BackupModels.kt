package com.ezzy.vault.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The JSON shape of a `.ezzy` export, before it is encrypted. */
@Serializable
data class BackupFile(
    val version: Int = 1,
    val exportedAt: Long,
    val categories: List<BackupCategory>,
    val templates: List<BackupTemplate>,
    val items: List<BackupItem>,
)

@Serializable
data class BackupCategory(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val sortOrder: Int,
    val createdAt: Long,
)

@Serializable
data class BackupTemplate(
    val id: String,
    val name: String,
    val iconKey: String,
    /** Carried through unchanged — the app already treats this as an opaque blob elsewhere. */
    val specJson: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
)

@Serializable
data class BackupField(
    val id: String,
    val label: String,
    val value: String,
    /** [com.ezzy.vault.data.model.FieldType] by name, so an unrecognised future type still
     *  round-trips as text rather than failing the whole import. */
    val type: String,
    val sortOrder: Int,
)

@Serializable
data class BackupAttachment(
    val id: String,
    val displayName: String,
    val caption: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sortOrder: Int,
    val createdAt: Long,
    /** Base64 of the file's decrypted bytes — the whole reason the export needs a password. */
    val data: String,
)

@Serializable
data class BackupItem(
    val id: String,
    val categoryId: String,
    val templateId: String?,
    val title: String,
    val subtitle: String,
    val note: String,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long,
    val fields: List<BackupField>,
    val attachments: List<BackupAttachment>,
)

internal val BackupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
