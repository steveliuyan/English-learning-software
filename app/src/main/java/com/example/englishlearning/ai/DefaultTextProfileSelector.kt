package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiPreference
import com.example.englishlearning.ai.domain.AiProfile

sealed interface DefaultTextProfileResult {
    data class Selected(val profile: AiProfile) : DefaultTextProfileResult
    data object NoSelection : DefaultTextProfileResult
    data object Unavailable : DefaultTextProfileResult
    data object StorageUnavailable : DefaultTextProfileResult
}

fun interface DefaultTextProfileResolver {
    suspend fun select(): DefaultTextProfileResult
}

/** Resolves the explicitly selected device-level text profile without reading secret contents. */
class DefaultTextProfileSelector(
    private val preferences: AiPreferenceRepository,
    private val profiles: AiProfileRepository,
    private val secrets: AiProfileSecretUseCase,
) : DefaultTextProfileResolver {
    override suspend fun select(): DefaultTextProfileResult {
        val preference = preferences.get().getOrElse { return DefaultTextProfileResult.StorageUnavailable }
        val profileId = preference.defaultTextProfileId ?: return DefaultTextProfileResult.NoSelection
        val profile = profiles.find(profileId).getOrElse {
            return DefaultTextProfileResult.StorageUnavailable
        } ?: return clearAndUnavailable(preference)
        if (AiCapability.Text !in profile.capabilities) return clearAndUnavailable(preference)
        val hasKey = secrets.hasKey(profile).getOrElse {
            return DefaultTextProfileResult.StorageUnavailable
        }
        if (!hasKey) return clearAndUnavailable(preference)
        return DefaultTextProfileResult.Selected(profile)
    }

    /** 清除失效选择时只清文本默认这一个字段——生图默认是另一个独立字段，不能陪葬。 */
    private suspend fun clearAndUnavailable(current: AiPreference): DefaultTextProfileResult {
        preferences.save(current.copy(defaultTextProfileId = null)).getOrElse {
            return DefaultTextProfileResult.StorageUnavailable
        }
        return DefaultTextProfileResult.Unavailable
    }
}
