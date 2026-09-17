package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.englishlearning.core.storage.entity.AssetRecordEntity

@Dao
internal interface InternalAssetDao {
    @Query("SELECT * FROM asset_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AssetRecordEntity?

    @Upsert
    suspend fun upsert(asset: AssetRecordEntity)
}
