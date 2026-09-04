package com.ezzy.vault.data.repo

import androidx.room.withTransaction
import com.ezzy.vault.data.backup.BackupAttachment
import com.ezzy.vault.data.backup.BackupCategory
import com.ezzy.vault.data.backup.BackupField
import com.ezzy.vault.data.backup.BackupFile
import com.ezzy.vault.data.backup.BackupItem
import com.ezzy.vault.data.backup.BackupTemplate
import com.ezzy.vault.data.crypto.AttachmentStore
import com.ezzy.vault.data.db.AttachmentEntity
import com.ezzy.vault.data.db.CategoryEntity
import com.ezzy.vault.data.db.CategoryWithCount
import com.ezzy.vault.data.db.EzzyDatabase
import com.ezzy.vault.data.db.FieldEntity
import com.ezzy.vault.data.db.ItemEntity
import com.ezzy.vault.data.db.ItemWithDetails
import com.ezzy.vault.data.db.TemplateEntity
import com.ezzy.vault.data.model.AttachmentDraft
import com.ezzy.vault.data.model.FieldDraft
import com.ezzy.vault.data.model.FieldType
import com.ezzy.vault.data.model.ItemDraft
import com.ezzy.vault.data.model.Seed
import com.ezzy.vault.data.model.TemplateSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID

/** Single entry point to the vault. All screens and the overlay talk to this. */
class VaultRepository(
    private val db: EzzyDatabase,
    private val attachments: AttachmentStore,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---- Categories -------------------------------------------------------

    fun observeCategories(): Flow<List<CategoryEntity>> = db.categoryDao().observeAll()

    fun observeCategoriesWithCounts(): Flow<List<CategoryWithCount>> =
        db.categoryDao().observeAllWithCounts()

    fun observeCategory(id: String): Flow<CategoryEntity?> = db.categoryDao().observeById(id)

    suspend fun saveCategory(
        id: String?,
        name: String,
        iconKey: String,
        colorKey: String,
    ): String {
        val dao = db.categoryDao()
        val existing = id?.let { dao.getById(it) }
        val entity = CategoryEntity(
            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            iconKey = iconKey,
            colorKey = colorKey,
            sortOrder = existing?.sortOrder ?: dao.nextSortOrder(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity.id
    }

    /**
     * Writes the order the user dragged the sections into. One transaction, so the home grid
     * never observes a half-applied order and shuffles itself while the rows are being written.
     */
    suspend fun reorderCategories(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        db.withTransaction {
            val dao = db.categoryDao()
            orderedIds.forEachIndexed { index, id -> dao.updateSortOrder(id, index) }
        }
    }

    /** Deleting a category cascades to its items; their sealed files are swept up after. */
    suspend fun deleteCategory(id: String) {
        db.categoryDao().deleteById(id)
        sweepOrphanFiles()
    }

    // ---- Templates --------------------------------------------------------

    fun observeTemplates(): Flow<List<TemplateEntity>> = db.templateDao().observeAll()

    suspend fun templateSpec(templateId: String?): TemplateSpec? {
        val raw = templateId?.let { db.templateDao().getById(it) } ?: return null
        return decodeSpec(raw.specJson)
    }

    suspend fun template(templateId: String): TemplateEntity? = db.templateDao().getById(templateId)

    suspend fun saveTemplate(
        id: String?,
        name: String,
        iconKey: String,
        spec: TemplateSpec,
    ): String {
        val dao = db.templateDao()
        val existing = id?.let { dao.getById(it) }
        val entity = TemplateEntity(
            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            iconKey = iconKey,
            specJson = json.encodeToString(TemplateSpec.serializer(), spec),
            isBuiltIn = existing?.isBuiltIn ?: false,
            sortOrder = existing?.sortOrder ?: dao.nextSortOrder(),
        )
        dao.upsert(entity)
        return entity.id
    }

    suspend fun deleteTemplate(id: String) = db.templateDao().deleteCustomById(id)

    fun decodeSpec(specJson: String): TemplateSpec =
        runCatching { json.decodeFromString(TemplateSpec.serializer(), specJson) }
            .getOrDefault(TemplateSpec())

    // ---- Items ------------------------------------------------------------

    fun observeItems(categoryId: String): Flow<List<ItemWithDetails>> =
        db.itemDao().observeByCategory(categoryId)

    fun observeItem(id: String): Flow<ItemWithDetails?> = db.itemDao().observeById(id)

    fun observePinned(): Flow<List<ItemWithDetails>> = db.itemDao().observePinned()

    fun observeRecent(limit: Int = 8): Flow<List<ItemWithDetails>> = db.itemDao().observeRecent(limit)

    fun observeItemCount(): Flow<Int> = db.itemDao().observeCount()

    /** Every stored item, newest edit first. Used by the value picker's default listing. */
    fun observeAllItems(): Flow<List<ItemWithDetails>> = db.itemDao().observeAll()

    fun search(query: String): Flow<List<ItemWithDetails>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return flowOf(emptyList())
        return db.itemDao().search("%$trimmed%")
    }

    suspend fun item(id: String): ItemWithDetails? = db.itemDao().getById(id)

    suspend fun saveItem(draft: ItemDraft): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = db.itemDao().getById(draft.id)?.item

        db.withTransaction {
            db.itemDao().upsert(
                ItemEntity(
                    id = draft.id,
                    categoryId = draft.categoryId,
                    templateId = draft.templateId,
                    title = draft.title.trim().ifEmpty { "Untitled" },
                    subtitle = draft.subtitle.trim(),
                    note = draft.note.trim(),
                    isPinned = draft.isPinned,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    lastUsedAt = existing?.lastUsedAt ?: 0L,
                )
            )

            db.fieldDao().deleteForItem(draft.id)
            db.fieldDao().insertAll(
                draft.filledFields.mapIndexed { index, field ->
                    FieldEntity(
                        id = field.id,
                        itemId = draft.id,
                        label = field.label.trim().ifEmpty { "Field ${index + 1}" },
                        value = field.value.trim(),
                        type = field.type,
                        sortOrder = index,
                    )
                }
            )

            db.attachmentDao().deleteForItem(draft.id)
            db.attachmentDao().insertAll(
                draft.attachments.mapIndexed { index, attachment ->
                    AttachmentEntity(
                        id = attachment.id,
                        itemId = draft.id,
                        displayName = attachment.displayName,
                        caption = attachment.caption.trim(),
                        mimeType = attachment.mimeType,
                        storedName = attachment.storedName,
                        sizeBytes = attachment.sizeBytes,
                        sortOrder = index,
                        createdAt = now,
                    )
                }
            )
        }

        sweepOrphanFiles()
        draft.id
    }

    suspend fun deleteItem(id: String) {
        db.itemDao().deleteById(id)
        sweepOrphanFiles()
    }

    suspend fun setPinned(id: String, pinned: Boolean) =
        db.itemDao().setPinned(id, pinned, System.currentTimeMillis())

    /** Called whenever an item is opened or copied, so "Recent" reflects real usage. */
    suspend fun markUsed(id: String) = db.itemDao().touch(id, System.currentTimeMillis())

    /** Builds an editable draft from a stored item, or a blank one for a new entry. */
    suspend fun draftFor(itemId: String?, categoryId: String): ItemDraft {
        val stored = itemId?.let { db.itemDao().getById(it) } ?: return ItemDraft(categoryId = categoryId)
        // Which names came from the entry's type is not stored, so it is recovered by matching
        // the saved labels against the type's own — those stay read-only in the editor.
        val templateLabels = stored.item.templateId
            ?.let { templateSpec(it) }
            ?.fields
            ?.mapTo(mutableSetOf()) { it.label.lowercase() }
            .orEmpty()

        return ItemDraft(
            id = stored.item.id,
            isNew = false,
            categoryId = stored.item.categoryId,
            templateId = stored.item.templateId,
            title = stored.item.title,
            subtitle = stored.item.subtitle,
            note = stored.item.note,
            isPinned = stored.item.isPinned,
            fields = stored.sortedFields.map {
                FieldDraft(
                    id = it.id,
                    label = it.label,
                    value = it.value,
                    type = it.type,
                    fromTemplate = it.label.lowercase() in templateLabels,
                )
            },
            attachments = stored.sortedAttachments.map {
                AttachmentDraft(
                    id = it.id,
                    displayName = it.displayName,
                    caption = it.caption,
                    mimeType = it.mimeType,
                    storedName = it.storedName,
                    sizeBytes = it.sizeBytes,
                )
            },
        )
    }

    // ---- Housekeeping -----------------------------------------------------

    /** Creates the starter categories and built-in templates exactly once. */
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (db.templateDao().count() == 0) {
            Seed.templates.forEachIndexed { index, template ->
                db.templateDao().upsert(
                    TemplateEntity(
                        id = template.id,
                        name = template.name,
                        iconKey = template.iconKey,
                        specJson = json.encodeToString(TemplateSpec.serializer(), template.spec),
                        isBuiltIn = true,
                        sortOrder = index,
                    )
                )
            }
        }
        if (db.categoryDao().count() == 0) {
            val now = System.currentTimeMillis()
            Seed.categories.forEachIndexed { index, category ->
                db.categoryDao().upsert(
                    CategoryEntity(
                        id = category.id,
                        name = category.name,
                        iconKey = category.iconKey,
                        colorKey = category.colorKey,
                        sortOrder = index,
                        createdAt = now,
                    )
                )
            }
        }
    }

    suspend fun attachmentBytes(storedName: String): ByteArray? = attachments.read(storedName)

    // ---- Backup -------------------------------------------------------------

    /** How many rows [importSnapshot] actually applied versus had to skip. */
    data class ImportResult(val imported: Int, val skipped: Int)

    /**
     * Everything in the vault, with every attachment decrypted back to plain bytes so it can be
     * resealed into the portable export format. [onProgress] runs once per item, from 0f to 1f
     * — attachments are the slow part, and there is one of these calls per item regardless of
     * how many files it carries.
     */
    suspend fun exportSnapshot(onProgress: suspend (Float) -> Unit): BackupFile = withContext(Dispatchers.IO) {
        val categories = db.categoryDao().getAll()
        val templates = db.templateDao().getAll()
        val allItems = db.itemDao().getAll()
        val total = allItems.size.coerceAtLeast(1)

        val backupItems = allItems.mapIndexed { index, entry ->
            val backupAttachments = entry.sortedAttachments.mapNotNull { attachment ->
                val bytes = attachments.read(attachment.storedName) ?: return@mapNotNull null
                BackupAttachment(
                    id = attachment.id,
                    displayName = attachment.displayName,
                    caption = attachment.caption,
                    mimeType = attachment.mimeType,
                    sizeBytes = attachment.sizeBytes,
                    sortOrder = attachment.sortOrder,
                    createdAt = attachment.createdAt,
                    data = Base64.getEncoder().encodeToString(bytes),
                )
            }
            onProgress((index + 1).toFloat() / total)
            BackupItem(
                id = entry.item.id,
                categoryId = entry.item.categoryId,
                templateId = entry.item.templateId,
                title = entry.item.title,
                subtitle = entry.item.subtitle,
                note = entry.item.note,
                isPinned = entry.item.isPinned,
                createdAt = entry.item.createdAt,
                updatedAt = entry.item.updatedAt,
                lastUsedAt = entry.item.lastUsedAt,
                fields = entry.sortedFields.map {
                    BackupField(it.id, it.label, it.value, it.type.name, it.sortOrder)
                },
                attachments = backupAttachments,
            )
        }

        BackupFile(
            exportedAt = System.currentTimeMillis(),
            categories = categories.map {
                BackupCategory(it.id, it.name, it.iconKey, it.colorKey, it.sortOrder, it.createdAt)
            },
            templates = templates.map {
                BackupTemplate(it.id, it.name, it.iconKey, it.specJson, it.isBuiltIn, it.sortOrder)
            },
            items = backupItems,
        )
    }

    /**
     * Applies an imported snapshot on top of whatever is already in the vault. Every row
     * upserts by its original id, so re-importing the same backup twice lands on the same
     * state rather than duplicating it, while importing into a different vault simply adds
     * alongside what is already there. Attachments get a fresh stored name on this device —
     * the name recorded in the backup only ever existed on the device that made it.
     *
     * One item failing (a category the backup did not include, say) is skipped rather than
     * failing the whole import, so a partly damaged file still recovers everything it can.
     */
    suspend fun importSnapshot(
        backup: BackupFile,
        onProgress: suspend (Float) -> Unit,
    ): ImportResult = withContext(Dispatchers.IO) {
        db.withTransaction {
            backup.categories.forEach { c ->
                db.categoryDao().upsert(
                    CategoryEntity(c.id, c.name, c.iconKey, c.colorKey, c.sortOrder, c.createdAt)
                )
            }
            backup.templates.forEach { t ->
                db.templateDao().upsert(
                    TemplateEntity(t.id, t.name, t.iconKey, t.specJson, t.isBuiltIn, t.sortOrder)
                )
            }
        }

        val total = backup.items.size.coerceAtLeast(1)
        var imported = 0
        var skipped = 0

        backup.items.forEachIndexed { index, item ->
            val ok = runCatching {
                val storedAttachments = item.attachments.mapNotNull { backed ->
                    val bytes = runCatching { Base64.getDecoder().decode(backed.data) }.getOrNull()
                        ?: return@mapNotNull null
                    val stored = attachments.save(bytes) ?: return@mapNotNull null
                    AttachmentEntity(
                        id = backed.id,
                        itemId = item.id,
                        displayName = backed.displayName,
                        caption = backed.caption,
                        mimeType = backed.mimeType,
                        storedName = stored.storedName,
                        sizeBytes = stored.sizeBytes,
                        sortOrder = backed.sortOrder,
                        createdAt = backed.createdAt,
                    )
                }

                db.withTransaction {
                    db.itemDao().upsert(
                        ItemEntity(
                            id = item.id,
                            categoryId = item.categoryId,
                            templateId = item.templateId,
                            title = item.title,
                            subtitle = item.subtitle,
                            note = item.note,
                            isPinned = item.isPinned,
                            createdAt = item.createdAt,
                            updatedAt = item.updatedAt,
                            lastUsedAt = item.lastUsedAt,
                        )
                    )
                    db.fieldDao().deleteForItem(item.id)
                    db.fieldDao().insertAll(
                        item.fields.map {
                            FieldEntity(it.id, item.id, it.label, it.value, FieldType.from(it.type), it.sortOrder)
                        }
                    )
                    db.attachmentDao().deleteForItem(item.id)
                    db.attachmentDao().insertAll(storedAttachments)
                }
            }.isSuccess

            if (ok) imported++ else skipped++
            onProgress((index + 1).toFloat() / total)
        }

        sweepOrphanFiles()
        ImportResult(imported, skipped)
    }

    private suspend fun sweepOrphanFiles() {
        val referenced = db.attachmentDao().getAll().map { it.storedName }.toSet()
        attachments.pruneOrphans(referenced)
    }

    suspend fun allItems(): List<ItemWithDetails> = db.itemDao().getAll()
}
