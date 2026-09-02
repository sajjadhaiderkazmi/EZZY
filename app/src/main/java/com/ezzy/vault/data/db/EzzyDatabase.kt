package com.ezzy.vault.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EzzyDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun templateDao(): TemplateDao
    abstract fun itemDao(): ItemDao
    abstract fun fieldDao(): FieldDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        const val NAME = "ezzy.db"

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
                .build()
        }
    }
}
