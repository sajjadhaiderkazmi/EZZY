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
    version = 8,
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
         * A handful of devices reached "version 3" through an earlier build of this migration —
         * one that built section groups on categories (a category_groups table, a groupId
         * column on categories) before that feature was reworked to live inside each section
         * instead, at which point this file's own MIGRATION_2_3 body was rewritten to build
         * item_groups directly. Changing an already-shipped migration's body is never safe: any
         * device that had already run the old one keeps the old shape on disk forever, no matter
         * what the code says version 3 means now. Bumping the version and cleaning up here,
         * rather than editing MIGRATION_2_3 again, is what actually fixes it — for everyone.
         *
         * A device coming from true v2 runs the (correct, current) MIGRATION_2_3 above and
         * arrives here with the right shape already, so this checks what is actually on disk
         * and only acts on the old one; nothing to do is a valid outcome for a migration.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasOldSectionGroups = db.query(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'category_groups'"
                ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
                if (!hasOldSectionGroups) return

                // Undo the old section-groups shape: categories goes back to carrying no
                // groupId or foreign key at all, and category_groups is dropped outright —
                // nothing about it needs preserving, a section was never deleted by having
                // been filed into one.
                db.execSQL(
                    """
                    CREATE TABLE categories_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        iconKey TEXT NOT NULL,
                        colorKey TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO categories_new (id, name, iconKey, colorKey, sortOrder, createdAt)
                    SELECT id, name, iconKey, colorKey, sortOrder, createdAt FROM categories
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_new RENAME TO categories")
                db.execSQL("DROP TABLE category_groups")

                // This device's own v2→v3 run predates entry groups, so its items table never
                // got a groupId column — exactly what the current MIGRATION_2_3 already does
                // for every other device, applied here instead.
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
         * Quick access grew past entries in v5: a section or a group can sit in it too, so both
         * carry the same pinned flag an entry already had. Plain column additions with a
         * default — nothing references them, so no table rebuild is needed here.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE item_groups ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * A custom picture per entry arrived in v6. Plain column addition with a default —
         * nothing references it, so no table rebuild is needed here.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN iconPhoto TEXT DEFAULT NULL")
            }
        }

        /**
         * A per-file watermark switch arrived in v7. Plain column addition with a default —
         * nothing references it, so no table rebuild is needed here.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachments ADD COLUMN watermark INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v8 lets that watermark be tuned: opacity, size, position and colour, per file. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachments ADD COLUMN watermarkOpacity INTEGER NOT NULL DEFAULT 40")
                db.execSQL("ALTER TABLE attachments ADD COLUMN watermarkScale INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE attachments ADD COLUMN watermarkX INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attachments ADD COLUMN watermarkY INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attachments ADD COLUMN watermarkColor TEXT NOT NULL DEFAULT 'blue'")
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
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                .build()
        }
    }
}
