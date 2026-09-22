package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_profiles")
data class AiProfileEntity(
    @PrimaryKey val profileId: String,
    val displayName: String,
    val websiteUrl: String,
    val endpoint: String,
    val model: String,
    val capabilities: String,
    val secretAlias: String,
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int,
    val timeoutSeconds: Int,
    val systemPromptTemplateId: String,
)
