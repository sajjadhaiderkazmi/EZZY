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
        CategoryGroupEntity::class,
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
    abstract fun categoryGroupDao(): CategoryGroupDao

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

        /** Groups (folders for sections) arrived in v3, along with which group a section is in. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_groups (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // categories.groupId carries a real foreign key to category_groups, and SQLite
                // cannot add a foreign key to an existing table with ALTER TABLE — only a fresh
                // CREATE TABLE can declare one. So the table is rebuilt: a new one with the
                // column and its constraint in place from the start, the existing rows copied
                // across, the old table dropped, and the new one renamed into its place.
                // Foreign keys in SQLite resolve by table name, so items.categoryId's own
                // reference to "categories" keeps working once the rename lands.
                db.execSQL(
                    """
                    CREATE TABLE categories_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        iconKey TEXT NOT NULL,
                        colorKey TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        groupId TEXT DEFAULT NULL,
                        FOREIGN KEY(groupId) REFERENCES category_groups(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO categories_new (id, name, iconKey, colorKey, sortOrder, createdAt, groupId)
                    SELECT id, name, iconKey, colorKey, sortOrder, createdAt, NULL FROM categories
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_new RENAME TO categories")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_categories_groupId ON categories(groupId)"
                )
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
