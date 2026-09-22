package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.AiProfileEntity

@Dao
internal interface InternalAiProfileDao {
    @Query("SELECT * FROM ai_profiles ORDER BY profileId ASC")
    suspend fun list(): List<AiProfileEntity>

    @Query("SELECT * FROM ai_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun find(profileId: String): AiProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: AiProfileEntity)

    @Query("DELETE FROM ai_profiles WHERE profileId = :profileId")
    suspend fun delete(profileId: String)
}
