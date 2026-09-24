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

    /**
     * 读回该 Profile 的密钥，供出站授权使用。
     *
     * **原样透传结果，不记录日志、不包装错误。** 失败一律是 `AppError.KeyStoreUnavailable`，
     * 界面据此提示「密钥不可读，请重新录入」；任何把原始失败细节带上去的做法都可能顺带泄露密钥。
     *
     * 返回的数组由调用方负责清零。
     */
    fun loadKey(profile: AiProfile): Result<CharArray> = secretStore.read(referenceFor(profile.profileId))

    companion object {
        fun referenceFor(profileId: String): SecretReference = SecretReference("ai-profile-$profileId")
    }
}
