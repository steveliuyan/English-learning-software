package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.AiPreferenceEntity

@Dao
internal interface InternalAiPreferenceDao {
    @Query("SELECT * FROM ai_preferences WHERE preferenceId = 'device' LIMIT 1")
    suspend fun find(): AiPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: AiPreferenceEntity)
}
