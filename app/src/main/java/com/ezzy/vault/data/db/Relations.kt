package com.ezzy.vault.data.db

import androidx.room.Embedded
import androidx.room.Relation

/** An item with everything needed to render its detail screen or overlay panel. */
data class ItemWithDetails(
    @Embedded val item: ItemEntity,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val fields: List<FieldEntity>,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val attachments: List<AttachmentEntity>,
) {
    val sortedFields: List<FieldEntity> get() = fields.sortedBy { it.sortOrder }
    val sortedAttachments: List<AttachmentEntity> get() = attachments.sortedBy { it.sortOrder }
}

/** Row used by the home grid: a category plus how much is stored inside it. */
data class CategoryWithCount(
    @Embedded val category: CategoryEntity,
    val itemCount: Int,
)

/** Row used for a group's folder card: how many sections it holds. */
data class CategoryGroupWithCount(
    @Embedded val group: CategoryGroupEntity,
    val categoryCount: Int,
)
