package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiPreference
import com.example.englishlearning.ai.domain.AiProfile

sealed interface DefaultImageProfileResult {
    data class Selected(val profile: AiProfile) : DefaultImageProfileResult
    data object NoSelection : DefaultImageProfileResult
    data object Unavailable : DefaultImageProfileResult
    data object StorageUnavailable : DefaultImageProfileResult
}

/** Resolves the explicitly selected device-level image-generation profile without reading secret contents. */
fun interface DefaultImageProfileResolver {
    suspend fun select(): DefaultImageProfileResult
}

/**
 * 默认生图服务选择。与 [DefaultTextProfileSelector] 同一套语义（能力 + 密钥 + 存在性），
 * 但能力要求是 [AiCapability.ImageGeneration]，并且失效清除只清自己的字段——
 * 生图默认与文本默认各自独立，互相不能抹掉。
 */
class DefaultImageProfileSelector(
    private val preferences: AiPreferenceRepository,
    private val profiles: AiProfileRepository,
    private val secrets: AiProfileSecretUseCase,
) : DefaultImageProfileResolver {
    override suspend fun select(): DefaultImageProfileResult {
        val preference = preferences.get().getOrElse { return DefaultImageProfileResult.StorageUnavailable }
        val profileId = preference.defaultImageProfileId ?: return DefaultImageProfileResult.NoSelection
        val profile = profiles.find(profileId).getOrElse {
            return DefaultImageProfileResult.StorageUnavailable
        } ?: return clearAndUnavailable(preference)
        if (AiCapability.ImageGeneration !in profile.capabilities) return clearAndUnavailable(preference)
        val hasKey = secrets.hasKey(profile).getOrElse {
            return DefaultImageProfileResult.StorageUnavailable
        }
        if (!hasKey) return clearAndUnavailable(preference)
        return DefaultImageProfileResult.Selected(profile)
    }

    private suspend fun clearAndUnavailable(current: AiPreference): DefaultImageProfileResult {
        preferences.save(current.copy(defaultImageProfileId = null)).getOrElse {
            return DefaultImageProfileResult.StorageUnavailable
        }
        return DefaultImageProfileResult.Unavailable
    }
}
