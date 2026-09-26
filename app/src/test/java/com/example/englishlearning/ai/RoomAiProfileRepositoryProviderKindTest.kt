package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.dao.InternalAiProfileDao
import com.example.englishlearning.core.storage.entity.AiProfileEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RoomAiProfileRepositoryProviderKindTest {
    private val dao = mockk<InternalAiProfileDao>()
    private val database = mockk<AppDatabase> { every { internalAiProfileDao() } returns dao }
    private val repository = RoomAiProfileRepository(database, Dispatchers.Unconfined)

    @Test
    fun `mimo kind round-trips through storage`() = runTest {
        val profile = AiProfile(
            profileId = "mimo-1",
            displayName = "小米 MiMo TTS",
            websiteUrl = "https://mimo.mi.com",
            endpoint = "https://api.xiaomimimo.com/v1/chat/completions",
            model = "mimo-v2.5-tts",
            capabilities = setOf(AiCapability.Speech),
            secretReference = SecretReference("ai-profile-mimo-1"),
            providerKind = AiProviderKind.XIAOMI_MIMO,
        )
        coEvery { dao.upsert(any()) } returns Unit

        repository.save(profile).getOrThrow()

        coVerify {
            dao.upsert(
                AiProfileEntity(
                    profileId = "mimo-1",
                    displayName = "小米 MiMo TTS",
                    websiteUrl = "https://mimo.mi.com",
                    endpoint = "https://api.xiaomimimo.com/v1/chat/completions",
                    model = "mimo-v2.5-tts",
                    capabilities = "Speech",
                    secretAlias = "ai-profile-mimo-1",
                    temperature = 0.7,
                    topP = 1.0,
                    maxTokens = 1024,
                    timeoutSeconds = 30,
                    systemPromptTemplateId = "default-reading-v1",
                    providerKind = "XIAOMI_MIMO",
                    voice = "",
                ),
            )
        }

        coEvery { dao.find("mimo-1") } returns
            AiProfileEntity(
                profileId = "mimo-1",
                displayName = "小米 MiMo TTS",
                websiteUrl = "https://mimo.mi.com",
                endpoint = "https://api.xiaomimimo.com/v1/chat/completions",
                model = "mimo-v2.5-tts",
                capabilities = "Speech",
                secretAlias = "ai-profile-mimo-1",
                temperature = 0.7,
                topP = 1.0,
                maxTokens = 1024,
                timeoutSeconds = 30,
                systemPromptTemplateId = "default-reading-v1",
                providerKind = "XIAOMI_MIMO",
                voice = "",
            )

        assertEquals(AiProviderKind.XIAOMI_MIMO, repository.find("mimo-1").getOrThrow()?.providerKind)
    }

    @Test
    fun `voice round-trips through storage including auto empty value`() = runTest {
        val profile = AiProfile(
            profileId = "mimo-voice",
            displayName = "小米 MiMo TTS",
            websiteUrl = "https://mimo.mi.com",
            endpoint = "https://api.xiaomimimo.com/v1/chat/completions",
            model = "mimo-v2.5-tts",
            capabilities = setOf(AiCapability.Speech),
            secretReference = SecretReference("ai-profile-mimo-voice"),
            providerKind = AiProviderKind.XIAOMI_MIMO,
            voice = "茉莉",
        )
        coEvery { dao.upsert(any()) } returns Unit

        repository.save(profile).getOrThrow()

        coVerify {
            dao.upsert(match { it.voice == "茉莉" })
        }

        coEvery { dao.find("mimo-voice") } returns
            AiProfileEntity(
                profileId = "mimo-voice",
                displayName = "小米 MiMo TTS",
                websiteUrl = "https://mimo.mi.com",
                endpoint = "https://api.xiaomimimo.com/v1/chat/completions",
                model = "mimo-v2.5-tts",
                capabilities = "Speech",
                secretAlias = "ai-profile-mimo-voice",
                temperature = 0.7,
                topP = 1.0,
                maxTokens = 1024,
                timeoutSeconds = 30,
                systemPromptTemplateId = "default-reading-v1",
                providerKind = "XIAOMI_MIMO",
                voice = "",
            )

        // 空串 = 自动音色，必须原样回来。
        assertEquals("", repository.find("mimo-voice").getOrThrow()?.voice)
    }

    @Test
    fun `legacy openai kind round-trips through storage`() = runTest {
        val profile = AiProfile(
            profileId = "openai-1",
            displayName = "中转",
            websiteUrl = "https://example.com",
            endpoint = "https://api.example.com/v1",
            model = "tts-1",
            capabilities = setOf(AiCapability.Speech),
            secretReference = SecretReference("ai-profile-openai-1"),
        )
        coEvery { dao.upsert(any()) } returns Unit

        repository.save(profile).getOrThrow()

        coVerify {
            dao.upsert(match { it.providerKind == "OPENAI_COMPATIBLE" })
        }

        coEvery { dao.find("openai-1") } returns
            AiProfileEntity(
                profileId = "openai-1",
                displayName = "中转",
                websiteUrl = "https://example.com",
                endpoint = "https://api.example.com/v1",
                model = "tts-1",
                capabilities = "Speech",
                secretAlias = "ai-profile-openai-1",
                temperature = 0.7,
                topP = 1.0,
                maxTokens = 1024,
                timeoutSeconds = 30,
                systemPromptTemplateId = "default-reading-v1",
                providerKind = "OPENAI_COMPATIBLE",
                voice = "",
            )

        assertEquals(AiProviderKind.OPENAI_COMPATIBLE, repository.find("openai-1").getOrThrow()?.providerKind)
    }
}
