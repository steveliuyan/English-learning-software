package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_settings")
data class LearningSettingsEntity(
    @PrimaryKey val profileId: String,
    val openDetailOnKnown: Boolean,
    val openDetailOnFuzzy: Boolean,
    val openDetailOnForgotten: Boolean,
)
