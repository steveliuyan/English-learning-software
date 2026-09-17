package com.example.englishlearning.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.example.englishlearning.core.storage.dao.InternalAssetDao
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.KeyAliasEntity
import com.example.englishlearning.core.storage.entity.SchemaMetaEntity

/**
 * Versioned Room metadata store. Every version transition must be supplied through [MIGRATIONS].
 * Destructive migration fallback is intentionally never configured by callers.
 */
@Database(
    entities = [SchemaMetaEntity::class, AssetRecordEntity::class, KeyAliasEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    internal abstract fun internalAssetDao(): InternalAssetDao

    companion object {
        /** Explicit migration extension point. Version 1 has no historical transition. */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
