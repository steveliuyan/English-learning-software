package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.englishlearning.core.storage.entity.LearningSettingsEntity

@Dao
internal interface InternalLearningSettingsDao {
    @Query("SELECT * FROM learning_settings WHERE profileId = :profileId LIMIT 1")
    suspend fun findByProfileId(profileId: String): LearningSettingsEntity?

    @Upsert
    suspend fun upsert(settings: LearningSettingsEntity)
}
