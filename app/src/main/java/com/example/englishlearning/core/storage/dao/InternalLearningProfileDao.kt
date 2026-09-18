package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.englishlearning.core.storage.entity.LearningProfileEntity

@Dao
internal interface InternalLearningProfileDao {
    @Query("SELECT * FROM learning_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun findByProfileId(profileId: String): LearningProfileEntity?

    @Upsert
    suspend fun upsert(profile: LearningProfileEntity)
}
