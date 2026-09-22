package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiProfile

interface AiProfileRepository {
    suspend fun list(): Result<List<AiProfile>>

    suspend fun find(profileId: String): Result<AiProfile?>

    suspend fun save(profile: AiProfile): Result<Unit>

    suspend fun delete(profileId: String): Result<Unit>
}
