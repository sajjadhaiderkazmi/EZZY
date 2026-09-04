package com.ezzy.vault.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query(
        """
        SELECT c.*, (SELECT COUNT(*) FROM items i WHERE i.categoryId = c.id) AS itemCount
        FROM categories c
        ORDER BY c.sortOrder ASC, c.name ASC
        """
    )
    fun observeAllWithCounts(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observeById(id: String): Flow<CategoryEntity?>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TemplateDao {

    @Query("SELECT * FROM templates ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: String): TemplateEntity?

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM templates")
    suspend fun nextSortOrder(): Int

    @Upsert
    suspend fun upsert(template: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomById(id: String)
}

@Dao
interface ItemDao {

    @Transaction
    @Query("SELECT * FROM items WHERE categoryId = :categoryId ORDER BY isPinned DESC, title COLLATE NOCASE ASC")
    fun observeByCategory(categoryId: String): Flow<List<ItemWithDetails>>

    /** A section's own top level: entries that have not been dragged into one of its groups. */
    @Transaction
    @Query(
        """
        SELECT * FROM items WHERE categoryId = :categoryId AND groupId IS NULL
        ORDER BY isPinned DESC, title COLLATE NOCASE ASC
        """
    )
    fun observeUngroupedByCategory(categoryId: String): Flow<List<ItemWithDetails>>

    @Transaction
    @Query("SELECT * FROM items WHERE groupId = :groupId ORDER BY isPinned DESC, title COLLATE NOCASE ASC")
    fun observeByGroup(groupId: String): Flow<List<ItemWithDetails>>

    @Query("UPDATE items SET groupId = :groupId WHERE id = :id")
    suspend fun setGroupId(id: String, groupId: String?)

    /** Every entry still in a group that is about to be deleted, so it can be dropped too. */
    @Query("SELECT id FROM items WHERE groupId = :groupId")
    suspend fun idsInGroup(groupId: String): List<String>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun observeById(id: String): Flow<ItemWithDetails?>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): ItemWithDetails?

    @Transaction
    @Query("SELECT * FROM items WHERE isPinned = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observePinned(): Flow<List<ItemWithDetails>>

    @Transaction
    @Query("SELECT * FROM items WHERE lastUsedAt > 0 ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ItemWithDetails>>

    @Transaction
    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ItemWithDetails>>

    @Transaction
    @Query("SELECT * FROM items")
    suspend fun getAll(): List<ItemWithDetails>

    /**
     * Matches the item's own text plus any of its field labels and values.
     * [query] must already be wrapped in `%` by the caller.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM items
        WHERE title LIKE :query COLLATE NOCASE
           OR subtitle LIKE :query COLLATE NOCASE
           OR note LIKE :query COLLATE NOCASE
           OR id IN (
                SELECT itemId FROM fields
                WHERE label LIKE :query COLLATE NOCASE
                   OR (type != 'SECRET' AND value LIKE :query COLLATE NOCASE)
           )
        ORDER BY isPinned DESC, title COLLATE NOCASE ASC
        LIMIT 60
        """
    )
    fun search(query: String): Flow<List<ItemWithDetails>>

    @Query("SELECT COUNT(*) FROM items")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemEntity)

    @Upsert
    suspend fun upsert(item: ItemEntity)

    @Query("UPDATE items SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE items SET lastUsedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FieldDao {

    @Query("SELECT * FROM fields WHERE itemId = :itemId ORDER BY sortOrder ASC")
    suspend fun getForItem(itemId: String): List<FieldEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fields: List<FieldEntity>)

    @Query("DELETE FROM fields WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: String)
}

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE itemId = :itemId ORDER BY sortOrder ASC")
    suspend fun getForItem(itemId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: String)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ItemGroupDao {

    @Query(
        """
        SELECT g.*, (SELECT COUNT(*) FROM items i WHERE i.groupId = g.id) AS itemCount
        FROM item_groups g
        WHERE g.categoryId = :categoryId
        ORDER BY g.sortOrder ASC, g.name ASC
        """
    )
    fun observeAllWithCounts(categoryId: String): Flow<List<ItemGroupWithCount>>

    @Query("SELECT * FROM item_groups WHERE id = :id")
    fun observeById(id: String): Flow<ItemGroupEntity?>

    @Query("SELECT * FROM item_groups WHERE id = :id")
    suspend fun getById(id: String): ItemGroupEntity?

    @Query("SELECT * FROM item_groups ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<ItemGroupEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM item_groups WHERE categoryId = :categoryId")
    suspend fun nextSortOrder(categoryId: String): Int

    @Query("UPDATE item_groups SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Upsert
    suspend fun upsert(group: ItemGroupEntity)

    @Query("DELETE FROM item_groups WHERE id = :id")
    suspend fun deleteById(id: String)
}
