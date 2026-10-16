package com.example.englishlearning.language.domain

import com.example.englishlearning.language.SpeechPreferenceRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PronunciationRouterTest {
    @Test
    fun `american word uses kokoro only`() = runTest {
        val kokoro = RecordingProvider(PronunciationResult.Played)
        val mimo = RecordingFactory(RecordingProvider(PronunciationResult.Played))
        val system = RecordingProvider(PronunciationResult.Played)
        val router = router(kokoro, system, mimo)

        router.speakWord("schedule", PronunciationAccent.American)

        assertEquals(listOf("schedule:American"), kokoro.accentCalls)
        assertEquals(emptyList<String>(), mimo.profileIds)
        assertEquals(0, system.calls)
    }

    @Test
    fun `british word uses kokoro only`() = runTest {
        val kokoro = RecordingProvider(PronunciationResult.Played)
        val mimo = RecordingFactory(RecordingProvider(PronunciationResult.Played))
        val system = RecordingProvider(PronunciationResult.Played)
        val router = router(kokoro, system, mimo)

        router.speakWord("schedule", PronunciationAccent.British)

        assertEquals(listOf("schedule:British"), kokoro.accentCalls)
        assertEquals(emptyList<String>(), mimo.profileIds)
        assertEquals(0, system.calls)
    }

    @Test
    fun `sentence uses mimo and system only as final fallback`() = runTest {
        val events = mutableListOf<String>()
        val mimoProvider = EventProvider(events, "mimo", PronunciationResult.Unavailable("offline"))
        val system = EventProvider(events, "system", PronunciationResult.Played)
        val router = PronunciationRouter(
            kokoroProvider = EventProvider(events, "kokoro", PronunciationResult.Played),
            systemProvider = system,
            miMoFactory = EventFactory(events, mimoProvider),
            preferences = FixedPreferences(
                SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "mimo-profile"),
            ),
        )

        router.speakSentence("This is a sentence.", PronunciationAccent.British, forceRegenerate = true)

        assertEquals(
            listOf(
                "factory:mimo-profile",
                "mimo:This is a sentence.:British:true",
                "system:This is a sentence.:British:false",
            ),
            events,
        )
    }

    @Test
    fun `kokoro word failure never falls back to mimo or system`() = runTest {
        val kokoro = RecordingProvider(PronunciationResult.Failed())
        val mimo = RecordingFactory(RecordingProvider(PronunciationResult.Played))
        val system = RecordingProvider(PronunciationResult.Played)
        val router = router(kokoro, system, mimo)

        val result = router.speakWord("schedule", PronunciationAccent.American)

        assertEquals(PronunciationResult.Failed(), result)
        assertEquals(emptyList<String>(), mimo.profileIds)
        assertEquals(0, system.calls)
    }

    @Test
    fun `word regeneration forwards both accents and force flag only to kokoro`() = runTest {
        val kokoro = RecordingProvider(PronunciationResult.Played)
        val mimo = RecordingFactory(RecordingProvider(PronunciationResult.Played))
        val system = RecordingProvider(PronunciationResult.Played)
        val provider: PronunciationProvider = router(kokoro, system, mimo)

        assertEquals(PronunciationResult.Played, provider.speakWord("schedule", PronunciationAccent.American, true))
        assertEquals(PronunciationResult.Played, provider.speakWord("schedule", PronunciationAccent.British, true))

        assertEquals(listOf("schedule:American", "schedule:British"), kokoro.accentCalls)
        assertEquals(listOf(true, true), kokoro.forceRegenerateCalls)
        assertEquals(emptyList<String>(), mimo.profileIds)
        assertEquals(0, system.calls)
    }

    @Test
    fun `system sentence preference does not create mimo provider`() = runTest {
        val mimo = RecordingFactory(RecordingProvider(PronunciationResult.Played))
        val system = RecordingProvider(PronunciationResult.Played)
        val router = router(
            kokoro = RecordingProvider(PronunciationResult.Played),
            system = system,
            mimo = mimo,
            preference = SpeechPreference(selectedEngine = PronunciationEngine.SystemTts, miMoProfileId = "mimo"),
        )

        router.speakSentence("hello")

        assertEquals(1, system.calls)
        assertEquals(emptyList<String>(), mimo.profileIds)
    }

    @Test
    fun `provider compatibility speak routes to sentence`() = runTest {
        val system = RecordingProvider(PronunciationResult.Played)
        val router = router(RecordingProvider(PronunciationResult.Played), system, RecordingFactory(RecordingProvider(PronunciationResult.Played)))

        val result = router.speak("hello", PronunciationAccent.British, true)

        assertEquals(PronunciationResult.Played, result)
        assertEquals(listOf("hello:British"), system.accentCalls)
        // v32 变速接线后 SystemTts 出声点改走 4 参 speak（rate 透传），记录落点从 2 参上移到 3 参重写；
        // 语义不变：调用方的 forceRegenerate=true 依旧被丢弃，落引擎的仍是 false。
        assertEquals(listOf(false), system.forceRegenerateCalls)
    }

    @Test
    fun `router requires speech preference repository`() {
        val parameter = requireNotNull(PronunciationRouter::class.constructors.single().parameters)
            .single { it.name == "preferences" }
        assertEquals(SpeechPreferenceRepository::class, parameter.type.classifier)
    }

    private fun router(
        kokoro: PronunciationProvider,
        system: PronunciationProvider,
        mimo: MiMoPronunciationProviderFactory,
        preference: SpeechPreference = SpeechPreference(),
    ) = PronunciationRouter(kokoro, system, mimo, FixedPreferences(preference))

    private class FixedPreferences(private val preference: SpeechPreference) : SpeechPreferenceRepository {
        override suspend fun get(): Result<SpeechPreference> = Result.success(preference)
        override suspend fun save(preference: SpeechPreference): Result<Unit> = Result.success(Unit)
    }

    private class RecordingFactory(private val provider: PronunciationProvider) : MiMoPronunciationProviderFactory {
        val profileIds = mutableListOf<String>()
        override fun create(profileId: String): PronunciationProvider {
            profileIds += profileId
            return provider
        }
    }

    private class EventFactory(
        private val events: MutableList<String>,
        private val provider: PronunciationProvider,
    ) : MiMoPronunciationProviderFactory {
        override fun create(profileId: String): PronunciationProvider {
            events += "factory:$profileId"
            return provider
        }
    }

    private open class RecordingProvider(private val result: PronunciationResult) : PronunciationProvider {
        var calls = 0
        val accentCalls = mutableListOf<String>()
        val forceRegenerateCalls = mutableListOf<Boolean>()
        override fun capabilities() = emptySet<PronunciationCapability>()
        override suspend fun speak(text: String): PronunciationResult {
            calls++
            return result
        }
        override suspend fun speak(text: String, accent: PronunciationAccent): PronunciationResult {
            calls++
            accentCalls += "$text:$accent"
            return result
        }
        override suspend fun speak(text: String, accent: PronunciationAccent, forceRegenerate: Boolean): PronunciationResult {
            calls++
            accentCalls += "$text:$accent"
            forceRegenerateCalls += forceRegenerate
            return result
        }
    }

    private class EventProvider(
        private val events: MutableList<String>,
        private val name: String,
        private val result: PronunciationResult,
    ) : PronunciationProvider {
        override fun capabilities() = emptySet<PronunciationCapability>()
        override suspend fun speak(text: String): PronunciationResult {
            events += "$name:$text:American:false"
            return result
        }
        override suspend fun speak(text: String, accent: PronunciationAccent): PronunciationResult {
            events += "$name:$text:$accent:false"
            return result
        }
        override suspend fun speak(text: String, accent: PronunciationAccent, forceRegenerate: Boolean): PronunciationResult {
            events += "$name:$text:$accent:$forceRegenerate"
            return result
        }
    }
}
