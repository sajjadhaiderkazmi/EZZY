package com.ezzy.vault.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ezzy.vault.data.model.FieldType
import com.ezzy.vault.util.WatermarkStyle

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val sortOrder: Int,
    val createdAt: Long,
    /** Whether this section sits in Quick access on the home screen. */
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
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
        ForeignKey(
            entity = ItemGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            // Deleting a group must never take its entries down with it by accident — that is
            // what the separate, explicitly-confirmed "Delete group" action is for.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("isPinned"), Index("lastUsedAt"), Index("groupId")],
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
    /** The folder this entry has been dragged into within its own section, or null at the top level. */
    @ColumnInfo(defaultValue = "NULL") val groupId: String? = null,
    /** A custom picture for this entry, sealed in the attachment store — null uses the
     *  section's own icon instead, everywhere the entry is shown. */
    @ColumnInfo(defaultValue = "NULL") val iconPhoto: String? = null,
)

/**
 * A folder for entries inside one section — purely organisational. Nothing about how an entry
 * works changes by being inside one: same fields, same attachments, same section lock, whether
 * grouped or not. Scoped to [categoryId] since a group only ever makes sense within one section.
 */
@Entity(
    tableName = "item_groups",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class ItemGroupEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    /** Whether this group sits in Quick access on the home screen. */
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
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
    /** Stamps "FOR VERIFICATION PURPOSE ONLY" across a picture the moment it leaves the vault
     *  via Copy or Share — the file sealed here stays untouched either way. */
    @ColumnInfo(defaultValue = "0") val watermark: Boolean = false,
    // How that stamp is drawn, once the user has tuned it. Kept as plain columns rather than a
    // blob so a future screen can sort or search on them without parsing anything.
    @ColumnInfo(defaultValue = "40") val watermarkOpacity: Int = WatermarkStyle.DEFAULT_OPACITY,
    @ColumnInfo(defaultValue = "100") val watermarkScale: Int = WatermarkStyle.DEFAULT_SCALE,
    @ColumnInfo(defaultValue = "0") val watermarkX: Int = 0,
    @ColumnInfo(defaultValue = "0") val watermarkY: Int = 0,
    // Quoted: Room drops this into the CREATE TABLE as written, and it has to match the
    // migration's own DEFAULT 'blue' exactly or opening the database fails the schema check.
    @ColumnInfo(defaultValue = "'blue'") val watermarkColor: String = WatermarkStyle.DEFAULT_COLOR,
)

/** The stamp settings this file carries, in the shape the drawing code asks for. */
val AttachmentEntity.watermarkStyle: WatermarkStyle
    get() = WatermarkStyle(
        opacity = watermarkOpacity,
        scale = watermarkScale,
        offsetX = watermarkX,
        offsetY = watermarkY,
        colorKey = watermarkColor,
    )
