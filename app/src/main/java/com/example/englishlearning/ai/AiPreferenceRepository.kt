package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiPreference

interface AiPreferenceRepository {
    suspend fun get(): Result<AiPreference>

    suspend fun save(preference: AiPreference): Result<Unit>
}
