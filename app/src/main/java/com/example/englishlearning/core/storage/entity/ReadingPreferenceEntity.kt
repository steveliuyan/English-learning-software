package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_preferences")
data class ReadingPreferenceEntity(
    @PrimaryKey val profileId: String,
    val defaultArticleType: String,
    val explicitLengthTier: String?,
)
