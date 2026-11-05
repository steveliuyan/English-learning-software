package com.example.englishlearning.language.domain

import com.example.englishlearning.language.SpeechPreferenceRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * 关联实体在发音链上的转发（决策 2026-11-05 D-7/D-10）。
 *
 * 这里测的是**中段**：屏层把主键交给 Router，Router 必须原样交给真正会写台账的 provider；
 * 而只覆盖旧重载的 provider（系统 TTS 这类不产音频的）必须照常出声——
 * 默认实现丢掉 entity 是刻意的取舍，不能变成崩溃。
 */
class PronunciationRouterEntityTest {

    @Test
    fun `the word entity reaches the on-device provider`() = runTest {
        val kokoro = EntityRecordingProvider()
        val router = router(kokoro, EntityRecordingProvider(), RecordingFactory(EntityRecordingProvider()))

        router.speakWord("schedule", PronunciationAccent.American, entity = PronunciationEntityRef.wordCard("card-42"))

        assertEquals(
            listOf<Pair<PronunciationEntityRef?, String>>(PronunciationEntityRef.wordCard("card-42") to "schedule"),
            kokoro.seen,
            "Router 把词卡主键半路丢了：端侧台账将答不出「哪张卡」",
        )
    }

    @Test
    fun `the sentence entity reaches mimo and the system fallback`() = runTest {
        val system = EntityRecordingProvider(result = PronunciationResult.Played)
        val mimo = EntityRecordingProvider(result = PronunciationResult.Unavailable("offline"))
        val router = PronunciationRouter(
            kokoroProvider = EntityRecordingProvider(),
            systemProvider = system,
            miMoFactory = RecordingFactory(mimo),
            preferences = FixedPreferences(SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "p")),
        )

        router.speakSentence("Read this.", PronunciationAccent.British, entity = PronunciationEntityRef.article("art-7"))

        val article = PronunciationEntityRef.article("art-7")
        val expected = listOf<Pair<PronunciationEntityRef?, String>>(article to "Read this.")
        assertEquals(expected, mimo.seen, "云端出句时实体必须跟着进 MiMo 的台账")
        assertEquals(expected, system.seen, "远程没出声、降级到系统 TTS 也要带上实体：否则回退路径写出的台账是无主的")
    }

    @Test
    fun `a provider that ignores entities still plays`() = runTest {
        // 兼容面：只覆盖旧重载的 provider（系统 TTS、既有测试假件）不能因为多了这一枚参数就不出声。
        val legacy = LegacyOnlyProvider()
        val router = router(legacy, legacy, RecordingFactory(legacy))

        assertEquals(PronunciationResult.Played, router.speakWord("hello", entity = PronunciationEntityRef.wordCard("card-1")))
        assertEquals(1, legacy.calls)
    }

    private class LegacyOnlyProvider : PronunciationProvider {
        var calls = 0
        override fun capabilities() = emptySet<PronunciationCapability>()
        override suspend fun speak(text: String): PronunciationResult {
            calls++
            return PronunciationResult.Played
        }
        override suspend fun speak(text: String, accent: PronunciationAccent): PronunciationResult {
            calls++
            return PronunciationResult.Played
        }
        override suspend fun speak(
            text: String,
            accent: PronunciationAccent,
            forceRegenerate: Boolean,
        ): PronunciationResult {
            calls++
            return PronunciationResult.Played
        }
    }

    private class EntityRecordingProvider(private val result: PronunciationResult = PronunciationResult.Played) : PronunciationProvider {
        val seen = mutableListOf<Pair<PronunciationEntityRef?, String>>()

        override fun capabilities() = emptySet<PronunciationCapability>()
        override suspend fun speak(text: String): PronunciationResult {
            seen += null to text
            return result
        }
        override suspend fun speak(
            text: String,
            accent: PronunciationAccent,
            forceRegenerate: Boolean,
            rate: Float,
            entity: PronunciationEntityRef?,
        ): PronunciationResult {
            seen += entity to text
            return result
        }
    }

    private class RecordingFactory(private val provider: PronunciationProvider) : MiMoPronunciationProviderFactory {
        override fun create(profileId: String): PronunciationProvider = provider
    }

    private class FixedPreferences(private val preference: SpeechPreference) : SpeechPreferenceRepository {
        override suspend fun get(): Result<SpeechPreference> = Result.success(preference)
        override suspend fun save(preference: SpeechPreference): Result<Unit> = Result.success(Unit)
    }

    private fun router(
        kokoro: PronunciationProvider,
        system: PronunciationProvider,
        mimo: MiMoPronunciationProviderFactory,
    ) = PronunciationRouter(kokoro, system, mimo, FixedPreferences(SpeechPreference()))
}
