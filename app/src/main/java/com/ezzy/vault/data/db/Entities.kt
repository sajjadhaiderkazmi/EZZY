package com.ezzy.vault.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ezzy.vault.data.model.FieldType

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            // Deleting a group must never take its sections down with it by accident — that is
            // what the separate, explicitly-confirmed "Delete group" action is for.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("groupId")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val sortOrder: Int,
    val createdAt: Long,
    /** The folder this section has been dragged into, or null if it sits at the top level. */
    @ColumnInfo(defaultValue = "NULL") val groupId: String? = null,
)

/**
 * A folder for sections on the home screen — purely organisational. Nothing about how a
 * section works changes by being inside one: it is the same icon in the floating bar, the same
 * entries, the same lock setting, whether grouped or not.
 */
@Entity(tableName = "category_groups")
data class CategoryGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    /** Serialized [com.ezzy.vault.data.model.TemplateSpec]. */
    val specJson: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId"), Index("isPinned"), Index("lastUsedAt")],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val templateId: String?,
    val title: String,
    val subtitle: String,
    val note: String,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long,
)

@Entity(
    tableName = "fields",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class FieldEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val label: String,
    val value: String,
    @ColumnInfo(defaultValue = "TEXT") val type: FieldType,
    val sortOrder: Int,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val displayName: String,
    /** The user's own note about this file — what the picture actually shows. */
    @ColumnInfo(defaultValue = "") val caption: String,
    val mimeType: String,
    /** File name inside the app's private, encrypted attachment directory. */
    val storedName: String,
    val sizeBytes: Long,
    val sortOrder: Int,
    val createdAt: Long,
)
