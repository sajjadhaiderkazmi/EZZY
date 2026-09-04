package com.ezzy.vault.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ezzy.vault.data.model.FieldType
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class Converters {
    @TypeConverter
    fun fieldTypeToString(type: FieldType): String = type.name

    @TypeConverter
    fun stringToFieldType(raw: String?): FieldType = FieldType.from(raw)
}

@Database(
    entities = [
        CategoryEntity::class,
        TemplateEntity::class,
        ItemEntity::class,
        FieldEntity::class,
        AttachmentEntity::class,
        ItemGroupEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EzzyDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun templateDao(): TemplateDao
    abstract fun itemDao(): ItemDao
    abstract fun fieldDao(): FieldDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun itemGroupDao(): ItemGroupDao

    companion object {
        const val NAME = "ezzy.db"

        /** Captions arrived in v2; existing rows simply start without one. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE attachments ADD COLUMN caption TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * Entry groups — folders for the data inside one section — arrived in v3, along with
         * which group an entry sits in.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS item_groups (
                        id TEXT NOT NULL PRIMARY KEY,
                        categoryId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_item_groups_categoryId ON item_groups(categoryId)"
                )

                // items.groupId carries a real foreign key to item_groups, and SQLite cannot add
                // a foreign key to an existing table with ALTER TABLE — only a fresh CREATE
                // TABLE can declare one. So the table is rebuilt: a new one with the column and
                // its constraint in place from the start, the existing rows copied across, the
                // old table dropped, and the new one renamed into its place. DROP TABLE never
                // runs SQLite's foreign-key checks — those only fire on row-level INSERT/UPDATE/
                // DELETE, never on schema statements — so this is safe with foreign keys
                // enforced throughout, and once the rename lands, fields.itemId and
                // attachments.itemId (which reference "items" by name) resolve to the rebuilt
                // table exactly as before.
                db.execSQL(
                    """
                    CREATE TABLE items_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        categoryId TEXT NOT NULL,
                        templateId TEXT,
                        title TEXT NOT NULL,
                        subtitle TEXT NOT NULL,
                        note TEXT NOT NULL,
                        isPinned INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastUsedAt INTEGER NOT NULL,
                        groupId TEXT DEFAULT NULL,
                        FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE,
                        FOREIGN KEY(groupId) REFERENCES item_groups(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO items_new (
                        id, categoryId, templateId, title, subtitle, note,
                        isPinned, createdAt, updatedAt, lastUsedAt, groupId
                    )
                    SELECT id, categoryId, templateId, title, subtitle, note,
                           isPinned, createdAt, updatedAt, lastUsedAt, NULL
                    FROM items
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE items")
                db.execSQL("ALTER TABLE items_new RENAME TO items")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_categoryId ON items(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_isPinned ON items(isPinned)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_lastUsedAt ON items(lastUsedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_groupId ON items(groupId)")
            }
        }

        /**
         * Opens the encrypted database. [passphrase] is consumed (and zeroed) by SQLCipher,
         * so callers must hand over a copy they no longer need.
         */
        fun open(context: Context, passphrase: ByteArray): EzzyDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(
                context.applicationContext,
                EzzyDatabase::class.java,
                NAME,
            )
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
