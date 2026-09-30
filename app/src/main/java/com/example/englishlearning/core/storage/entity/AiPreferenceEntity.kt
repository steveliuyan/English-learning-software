package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_preferences")
data class AiPreferenceEntity(
    @PrimaryKey val preferenceId: String,
    val defaultTextProfileId: String?,
    val defaultImageProfileId: String? = null,
)
