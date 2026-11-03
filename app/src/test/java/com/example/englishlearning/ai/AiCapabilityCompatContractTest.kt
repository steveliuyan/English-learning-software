package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.dao.InternalAiProfileDao
import com.example.englishlearning.core.storage.entity.AiProfileEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `AiCapability` 枚名的**数据兼容契约**（决策
 * `docs/decisions/2026-11-04-ai-profile-vision-and-connection-test.md` §2）。
 *
 * 背景：能力列以枚举名字符串存进 `ai_profiles.capabilities`（`RoomAiProfileRepository.kt:53`），
 * 读回走 `AiCapability.valueOf`（`:70`），而 `valueOf` 对未知枚名抛 `IllegalArgumentException`
 * 会被 `:43` 的兜底 catch 折叠成 `StorageUnavailable`——**后果是整表读不出来**，
 * 不是丢一枚能力：AI 服务页、默认文本/生图解析、语音候选与所有依赖默认 Profile 的功能一起塌。
 *
 * 所以「摘掉读图开关」的正当处置是**只摘 UI、保留枚名**，并用本类把两件事钉住：
 *  1. 老行里的 `Vision` 还能完整解析（本类两条用例）；
 *  2. 「删掉一枚枚名 = 数据损坏」这条因果必须当场可见（`unknownTokenTakesTheWholeTableDown`，
 *     它断言的是既有实现的失败面，也是本决策的理由本身）。
 *
 * 第 3 条 `enumNamesAreTheFrozenStorageVocabulary` 是**锁**而不是新行为：它现在就该绿，
 * 绿的原因是「谁删常量谁红」。摘开关的判据另有其类（`AiVisionToggleRetirementTest`）。
 */
class AiCapabilityCompatContractTest {
    private val dao = mockk<InternalAiProfileDao>()
    private val database = mockk<AppDatabase> { every { internalAiProfileDao() } returns dao }
    private val repository = RoomAiProfileRepository(database, Dispatchers.Unconfined)

    private fun entity(capabilities: String) = AiProfileEntity(
        profileId = "p1",
        displayName = "旧配置",
        websiteUrl = "https://example.com",
        endpoint = "https://api.test/v1",
        model = "gpt-x",
        capabilities = capabilities,
        secretAlias = "ai-profile-p1",
        temperature = 0.7,
        topP = 1.0,
        maxTokens = 1024,
        timeoutSeconds = 30,
        systemPromptTemplateId = "default-reading-v1",
        providerKind = "OPENAI_COMPATIBLE",
        voice = "",
    )

    @Test
    fun enumNamesAreTheFrozenStorageVocabulary() {
        assertEquals(
            listOf("Text", "Vision", "Speech", "ImageGeneration"),
            AiCapability.entries.map { it.name },
            "`Vision` 是历史行的解析锚：摘 UI 开关可以，删枚名会让已存 `…,Vision` 的整张表读不出来。" +
                "真要清理枚名，先开数据迁移决策",
        )
        assertEquals(AiCapability.Vision, AiCapability.valueOf("Vision"))
    }

    @Test
    fun aLegacyRowThatDeclaredVisionStillReadsBackWhole() = runTest {
        coEvery { dao.list() } returns listOf(entity("Text,Vision"))

        val profiles = repository.list().getOrThrow()

        assertEquals(
            setOf(AiCapability.Text, AiCapability.Vision),
            profiles.single().capabilities,
            "读图开关下线后，老配置仍要能列出、编辑、设为默认——能力集合按原样带回，不静默丢枚",
        )
    }

    @Test
    fun savingKeepsTheLegacyVisionTokenInTheSameColumn() = runTest {
        coEvery { dao.upsert(any()) } returns Unit
        val captured = mutableListOf<AiProfileEntity>()

        repository.save(
            com.example.englishlearning.ai.domain.AiProfile(
                profileId = "p1",
                displayName = "旧配置",
                websiteUrl = "https://example.com",
                endpoint = "https://api.test/v1",
                model = "gpt-x",
                capabilities = setOf(AiCapability.Text, AiCapability.Vision),
                secretReference = com.example.englishlearning.core.security.SecretReference("ai-profile-p1"),
            ),
        ).getOrThrow()

        coVerify { dao.upsert(capture(captured)) }
        assertEquals(
            "Text,Vision",
            captured.single().capabilities,
            "编辑老配置（界面上已无读图开关）必须原样带回 `Vision`：既不许改名，也不许替用户静默丢掉一个他没点过的字段",
        )
    }

    @Test
    fun unknownTokenTakesTheWholeTableDown() = runTest {
        coEvery { dao.list() } returns listOf(entity("Text,NotAModality"))

        val result = repository.list()

        assertTrue(
            result.isFailure,
            "本用例钉的是既有失败面：未知枚名 = 整表读失败。正因为代价这么大，`Vision` 只能留不能删",
        )
    }

}
