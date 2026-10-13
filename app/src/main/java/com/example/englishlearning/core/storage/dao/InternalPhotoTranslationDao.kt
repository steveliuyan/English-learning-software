package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity

@Dao
internal interface InternalPhotoTranslationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PhotoTranslationRecordEntity)

    @Query(
        "SELECT * FROM photo_translation_records WHERE profileId = :profileId " +
            "ORDER BY createdAtEpochMillis DESC LIMIT :limit",
    )
    suspend fun listRecent(profileId: String, limit: Int): List<PhotoTranslationRecordEntity>

    // 按 profileId 收窄：删除请求只能带走自己的记录，跨 profile 一律 0 行。
    @Query("DELETE FROM photo_translation_records WHERE recordId = :recordId AND profileId = :profileId")
    suspend fun deleteOne(profileId: String, recordId: String)

    @Query("SELECT imageAssetId FROM photo_translation_records WHERE recordId = :recordId AND profileId = :profileId")
    suspend fun imageAssetIdFor(profileId: String, recordId: String): String?
}
