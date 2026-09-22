package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore

class AiProfileSecretUseCase(
    private val secretStore: SecretStore,
) {
    fun saveKey(profile: AiProfile, key: CharArray): Result<Unit> =
        try {
            secretStore.save(referenceFor(profile.profileId), key)
        } finally {
            key.fill('\u0000')
        }

    fun deleteKey(profile: AiProfile): Result<Unit> = secretStore.delete(referenceFor(profile.profileId))

    fun hasKey(profile: AiProfile): Result<Boolean> = secretStore.has(referenceFor(profile.profileId))

    companion object {
        fun referenceFor(profileId: String): SecretReference = SecretReference("ai-profile-$profileId")
    }
}
