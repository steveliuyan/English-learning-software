package com.example.englishlearning.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.example.englishlearning.core.storage.dao.InternalAssetDao
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.KeyAliasEntity
import com.example.englishlearning.core.storage.entity.SchemaMetaEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity

/**
 * Versioned Room metadata store. Every version transition must be supplied through [MIGRATIONS].
 * Destructive migration fallback is intentionally never configured by callers.
 */
@Database(
    entities = [SchemaMetaEntity::class, AssetRecordEntity::class, KeyAliasEntity::class, LocalProfileEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    internal abstract fun internalAssetDao(): InternalAssetDao
    internal abstract fun internalProfileDao(): com.example.englishlearning.core.storage.dao.InternalProfileDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_profiles` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
