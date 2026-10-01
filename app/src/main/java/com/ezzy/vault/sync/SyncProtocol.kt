package com.ezzy.vault.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * Everything that crosses the wire between the phone and the Chrome extension. Field names are
 * kept short and stable: the extension in /browser-extension reads exactly these shapes, so a
 * rename here is a protocol change and needs [PROTOCOL_VERSION] bumped on both sides.
 */

const val PROTOCOL_VERSION = 1

// ---- Pairing (the only plaintext exchange; both sides prove they know the code) ------------

@Serializable
data class PairRequest(
    val v: Int,
    /** The extension's P-256 public key, SPKI DER in base64. */
    val pub: String,
    /** What the extension calls itself, e.g. "Chrome on Windows". Shown on the phone. */
    val name: String,
    /** HMAC-SHA256(codeKey, pub) — proof the extension was shown this phone's code. */
    val mac: String,
)

@Serializable
data class PairResponse(
    val v: Int,
    val pub: String,
    val deviceId: String,
    val phoneId: String,
    val phoneName: String,
    /** HMAC-SHA256(codeKey, phonePub || extensionPub) — proof this really is that phone. */
    val mac: String,
)

// ---- Sealed API envelopes ---------------------------------------------------------------------

@Serializable
data class SealedRequest(val d: String, val iv: String, val c: String)

@Serializable
data class SealedResponse(val iv: String, val c: String)

/** What a [SealedRequest] opens to. [ts] and [rid] are the replay guard. */
@Serializable
data class ApiRequest(
    val ts: Long,
    val rid: String,
    val op: String,
    val a: JsonElement? = null,
)

@Serializable
data class ApiResponse(
    val rid: String,
    val ok: Boolean,
    val r: JsonElement? = null,
    val err: String? = null,
)

// ---- Vault snapshot ---------------------------------------------------------------------------

@Serializable
data class SnapCategory(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val sortOrder: Int,
    /** A section the phone asks to unlock again on its own — the extension does the same. */
    val locked: Boolean,
)

@Serializable
data class SnapGroup(val id: String, val categoryId: String, val name: String, val sortOrder: Int)

@Serializable
data class SnapTemplateField(val label: String, val type: String, val hint: String, val required: Boolean)

@Serializable
data class SnapTemplate(
    val id: String,
    val name: String,
    val iconKey: String,
    val titleHint: String,
    val fields: List<SnapTemplateField>,
)

@Serializable
data class SnapField(val id: String, val label: String, val value: String, val type: String)

@Serializable
data class SnapAttachment(
    val id: String,
    val name: String,
    val caption: String,
    val mime: String,
    val size: Long,
)

@Serializable
data class SnapItem(
    val id: String,
    val categoryId: String,
    val templateId: String?,
    val groupId: String?,
    val title: String,
    val subtitle: String,
    val note: String,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long,
    val fields: List<SnapField>,
    val attachments: List<SnapAttachment>,
)

@Serializable
data class Snapshot(
    /** Content hash of everything below; the extension refetches only when it moves. */
    val revision: String,
    val phoneName: String,
    val ownerName: String,
    val clipboardClearSeconds: Int,
    val maskSecrets: Boolean,
    val categories: List<SnapCategory>,
    val groups: List<SnapGroup>,
    val templates: List<SnapTemplate>,
    val items: List<SnapItem>,
)

// ---- Writes from the extension ----------------------------------------------------------------

@Serializable
data class SaveItemField(
    val id: String? = null,
    val label: String,
    val value: String,
    val type: String = "TEXT",
)

@Serializable
data class SaveItemArgs(
    /** Null for a new entry. */
    val id: String? = null,
    val categoryId: String,
    val templateId: String? = null,
    val title: String,
    val note: String = "",
    val isPinned: Boolean = false,
    val fields: List<SaveItemField> = emptyList(),
)

@Serializable
data class IdArgs(val id: String)

@Serializable
data class PinArgs(val id: String, val pinned: Boolean)

@Serializable
data class SectionLockArgs(val categoryId: String, val locked: Boolean)

@Serializable
data class StatusResult(
    val revision: String,
    /** Handed over exactly once, right after the user sets it on the phone during pairing. */
    val pin: String? = null,
    val pinPending: Boolean = false,
)

@Serializable
data class AttachmentResult(val name: String, val mime: String, val data: String)

@Serializable
data class SavedResult(val id: String, val revision: String)
