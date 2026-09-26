package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speech_preferences")
data class SpeechPreferenceEntity(
    @PrimaryKey val preferenceId: String = "device",
    val selectedEngine: String,
    val openAiProfileId: String?,
    val miMoProfileId: String?,
)
