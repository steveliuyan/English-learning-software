package com.example.englishlearning.language.domain

import com.example.englishlearning.language.SpeechPreferenceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PronunciationRouterTest {
    @Test
    fun `router is a pronunciation provider and defaults to system`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val router: PronunciationProvider = PronunciationRouter(
            system,
            CountingFactory(FakeProvider(PronunciationResult.Played)),
            CountingFactory(FakeProvider(PronunciationResult.Played)),
        )

        router.speak("hello")

        assertEquals(1, system.calls)
    }

    @Test
    fun `no saved preference uses system`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val miMoFactory = CountingFactory(FakeProvider(PronunciationResult.Played))
        val openAiFactory = CountingFactory(FakeProvider(PronunciationResult.Played))

        PronunciationRouter(
            system,
            miMoFactory,
            openAiFactory,
            FixedSpeechPreferenceRepository(SpeechPreference()),
        ).speak("hello")

        assertEquals(1, system.calls)
        assertEquals(emptyList<String>(), miMoFactory.profileIds)
        assertEquals(emptyList<String>(), openAiFactory.profileIds)
    }

    @Test
    fun `saved mimo preference uses saved profile`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val miMo = FakeProvider(PronunciationResult.Played)
        val miMoFactory = CountingFactory(miMo)
        val openAiFactory = CountingFactory(FakeProvider(PronunciationResult.Played))

        PronunciationRouter(
            system,
            miMoFactory,
            openAiFactory,
            FixedSpeechPreferenceRepository(
                SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "mimo-profile"),
            ),
        ).speak("hello")

        assertEquals(listOf("mimo-profile"), miMoFactory.profileIds)
        assertEquals(1, miMo.calls)
        assertEquals(0, system.calls)
    }

    @Test
    fun `saved openai preference uses openai then fallback`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val openAi = FakeProvider(PronunciationResult.Failed())
        val miMo = FakeProvider(PronunciationResult.Played)
        val openAiFactory = CountingFactory(openAi)
        val miMoFactory = CountingFactory(miMo)

        PronunciationRouter(
            system,
            miMoFactory,
            openAiFactory,
            FixedSpeechPreferenceRepository(
                SpeechPreference(
                    selectedEngine = PronunciationEngine.OpenAi,
                    openAiProfileId = "openai-profile",
                    miMoProfileId = "mimo-profile",
                ),
            ),
        ).speak("hello")

        assertEquals(listOf("openai-profile"), openAiFactory.profileIds)
        assertEquals(listOf("mimo-profile"), miMoFactory.profileIds)
        assertEquals(0, system.calls)
    }

    @Test
    fun `preference repository failure uses system`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val miMoFactory = CountingFactory(FakeProvider(PronunciationResult.Played))
        val openAiFactory = CountingFactory(FakeProvider(PronunciationResult.Played))

        PronunciationRouter(
            system,
            miMoFactory,
            openAiFactory,
            FixedSpeechPreferenceRepository(error = IllegalStateException("storage failed")),
        ).speak("hello")

        assertEquals(1, system.calls)
        assertEquals(emptyList<String>(), miMoFactory.profileIds)
        assertEquals(emptyList<String>(), openAiFactory.profileIds)
    }

    @Test
    fun `preference repository cancellation is rethrown`() = runTest {
        val router = PronunciationRouter(
            FakeProvider(PronunciationResult.Played),
            CountingFactory(FakeProvider(PronunciationResult.Played)),
            CountingFactory(FakeProvider(PronunciationResult.Played)),
            FixedSpeechPreferenceRepository(error = CancellationException("cancel")),
        )

        val error = assertThrows(CancellationException::class.java) {
            runBlocking { router.speak("hello") }
        }

        assertEquals("cancel", error.message)
    }

    @Test
    fun `openai success does not call fallback providers`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val openAi = FakeProvider(PronunciationResult.Played)
        val miMo = FakeProvider(PronunciationResult.Played)
        val openAiFactory = CountingFactory(openAi)
        val miMoFactory = CountingFactory(miMo)
        val router = PronunciationRouter(system, miMoFactory, openAiFactory)

        router.speak(
            "hello",
            SpeechPreference(
                selectedEngine = PronunciationEngine.OpenAi,
                openAiProfileId = "openai-profile",
                miMoProfileId = "mimo-profile",
            ),
        )

        assertEquals(listOf("openai-profile"), openAiFactory.profileIds)
        assertEquals(emptyList<String>(), miMoFactory.profileIds)
        assertEquals(0, system.calls)
    }

    @Test
    fun `openai unavailable or failed falls back to mimo`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val openAi = FakeProvider(PronunciationResult.Unavailable("offline"))
        val miMo = FakeProvider(PronunciationResult.Played)
        val openAiFactory = CountingFactory(openAi)
        val miMoFactory = CountingFactory(miMo)
        val router = PronunciationRouter(system, miMoFactory, openAiFactory)
        val preference = SpeechPreference(
            selectedEngine = PronunciationEngine.OpenAi,
            openAiProfileId = "openai-profile",
            miMoProfileId = "mimo-profile",
        )

        router.speak("hello", preference)
        openAi.result = PronunciationResult.Failed()
        router.speak("hello", preference)

        assertEquals(listOf("openai-profile", "openai-profile"), openAiFactory.profileIds)
        assertEquals(listOf("mimo-profile", "mimo-profile"), miMoFactory.profileIds)
        assertEquals(0, system.calls)
    }

    @Test
    fun `openai failure runs remote fallback and system in order`() = runTest {
        val events = mutableListOf<String>()
        val system = LoggingProvider(events, "system", PronunciationResult.Played)
        val openAi = LoggingProvider(events, "openai", PronunciationResult.Failed())
        val miMo = LoggingProvider(events, "mimo", PronunciationResult.Unavailable("offline"))
        val openAiFactory = LoggingFactory(events, "openai", openAi)
        val miMoFactory = LoggingFactory(events, "mimo", miMo)

        PronunciationRouter(system, miMoFactory, openAiFactory).speak(
            "hello",
            SpeechPreference(
                selectedEngine = PronunciationEngine.OpenAi,
                openAiProfileId = "openai-profile",
                miMoProfileId = "mimo-profile",
            ),
        )

        assertEquals(
            listOf(
                "openai factory:create(openai-profile)",
                "openai:speak(hello)",
                "mimo factory:create(mimo-profile)",
                "mimo:speak(hello)",
                "system:speak(hello)",
            ),
            events,
        )
    }

    @Test
    fun `remote factory exception uses next fallback`() = runTest {
        val events = mutableListOf<String>()
        val system = LoggingProvider(events, "system", PronunciationResult.Played)
        val miMo = LoggingProvider(events, "mimo", PronunciationResult.Played)
        val openAiFactory = ThrowingFactory(events, "openai", IllegalStateException("factory failed"))
        val miMoFactory = LoggingFactory(events, "mimo", miMo)

        PronunciationRouter(system, miMoFactory, openAiFactory).speak(
            "hello",
            SpeechPreference(
                selectedEngine = PronunciationEngine.OpenAi,
                openAiProfileId = "openai-profile",
                miMoProfileId = "mimo-profile",
            ),
        )

        assertEquals(
            listOf(
                "openai factory:create(openai-profile)",
                "mimo factory:create(mimo-profile)",
                "mimo:speak(hello)",
            ),
            events,
        )
    }

    @Test
    fun `factory cancellation is rethrown without fallback`() = runTest {
        val events = mutableListOf<String>()
        val system = LoggingProvider(events, "system", PronunciationResult.Played)
        val miMoFactory = ThrowingFactory(events, "mimo", CancellationException("cancel"))
        val unusedOpenAiFactory = LoggingFactory(
            events,
            "openai",
            LoggingProvider(events, "openai", PronunciationResult.Played),
        )

        val error = assertThrows(CancellationException::class.java) {
            runBlocking {
                PronunciationRouter(system, miMoFactory, unusedOpenAiFactory).speak(
                    "hello",
                    SpeechPreference(
                        selectedEngine = PronunciationEngine.MiMo,
                        miMoProfileId = "mimo-profile",
                    ),
                )
            }
        }

        assertEquals("cancel", error.message)
        assertEquals(listOf("mimo factory:create(mimo-profile)"), events)
    }

    @Test
    fun `openai and mimo failures fall back to system`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val openAi = FakeProvider(PronunciationResult.Failed())
        val miMo = FakeProvider(PronunciationResult.Unavailable("offline"))
        val openAiFactory = CountingFactory(openAi)
        val miMoFactory = CountingFactory(miMo)

        PronunciationRouter(system, miMoFactory, openAiFactory).speak(
            "hello",
            SpeechPreference(
                selectedEngine = PronunciationEngine.OpenAi,
                openAiProfileId = "openai-profile",
                miMoProfileId = "mimo-profile",
            ),
        )

        assertEquals(1, openAi.calls)
        assertEquals(1, miMo.calls)
        assertEquals(1, system.calls)
    }

    @Test
    fun `missing profile skips that remote provider`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val openAi = FakeProvider(PronunciationResult.Played)
        val miMo = FakeProvider(PronunciationResult.Played)
        val openAiFactory = CountingFactory(openAi)
        val miMoFactory = CountingFactory(miMo)
        val router = PronunciationRouter(system, miMoFactory, openAiFactory)

        router.speak(
            "hello",
            SpeechPreference(
                selectedEngine = PronunciationEngine.OpenAi,
                openAiProfileId = null,
                miMoProfileId = "mimo-profile",
            ),
        )
        router.speak(
            "hello",
            SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = null),
        )

        assertEquals(emptyList<String>(), openAiFactory.profileIds)
        assertEquals(listOf("mimo-profile"), miMoFactory.profileIds)
        assertEquals(1, miMo.calls)
        assertEquals(1, system.calls)
    }

    @Test
    fun `remote cancellation is rethrown without fallback`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val miMo = ThrowingProvider()
        val miMoFactory = CountingFactory(miMo)
        val unusedOpenAiFactory = CountingFactory(FakeProvider(PronunciationResult.Played))
        val miMoRouter = PronunciationRouter(system, miMoFactory, unusedOpenAiFactory)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                miMoRouter.speak(
                    "hello",
                    SpeechPreference(
                        selectedEngine = PronunciationEngine.MiMo,
                        miMoProfileId = "mimo-profile",
                    ),
                )
            }
        }
        assertEquals(0, system.calls)
        assertEquals(emptyList<String>(), unusedOpenAiFactory.profileIds)

        val openAi = ThrowingProvider()
        val unusedMiMoFactory = CountingFactory(FakeProvider(PronunciationResult.Played))
        val openAiRouter = PronunciationRouter(system, unusedMiMoFactory, CountingFactory(openAi))
        assertThrows(CancellationException::class.java) {
            runBlocking {
                openAiRouter.speak(
                    "hello",
                    SpeechPreference(
                        selectedEngine = PronunciationEngine.OpenAi,
                        openAiProfileId = "openai-profile",
                        miMoProfileId = "mimo-profile",
                    ),
                )
            }
        }
        assertEquals(0, system.calls)
        assertEquals(emptyList<String>(), unusedMiMoFactory.profileIds)
    }

    @Test
    fun `explicit system does not create remote providers`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val miMoFactory = CountingFactory(FakeProvider(PronunciationResult.Played))
        val openAiFactory = CountingFactory(FakeProvider(PronunciationResult.Played))

        PronunciationRouter(system, miMoFactory, openAiFactory).speak(
            "hello",
            SpeechPreference(
                selectedEngine = PronunciationEngine.SystemTts,
                openAiProfileId = "openai-profile",
                miMoProfileId = "mimo-profile",
            ),
        )

        assertEquals(emptyList<String>(), openAiFactory.profileIds)
        assertEquals(emptyList<String>(), miMoFactory.profileIds)
        assertEquals(1, system.calls)
    }

    private class FixedSpeechPreferenceRepository(
        private val preference: SpeechPreference = SpeechPreference(),
        private val error: Throwable? = null,
    ) : SpeechPreferenceRepository {
        override suspend fun get(): Result<SpeechPreference> = error?.let(Result.Companion::failure)
            ?: Result.success(preference)

        override suspend fun save(preference: SpeechPreference): Result<Unit> = Result.success(Unit)
    }

    private class LoggingFactory(
        private val events: MutableList<String>,
        private val name: String,
        private val provider: PronunciationProvider,
    ) : MiMoPronunciationProviderFactory, OpenAiPronunciationProviderFactory {
        override fun create(profileId: String): PronunciationProvider {
            events += "$name factory:create($profileId)"
            return provider
        }
    }

    private class ThrowingFactory(
        private val events: MutableList<String>,
        private val name: String,
        private val error: Exception,
    ) : MiMoPronunciationProviderFactory, OpenAiPronunciationProviderFactory {
        override fun create(profileId: String): PronunciationProvider {
            events += "$name factory:create($profileId)"
            throw error
        }
    }

    private class LoggingProvider(
        private val events: MutableList<String>,
        private val name: String,
        private val result: PronunciationResult,
    ) : PronunciationProvider {
        override fun capabilities() = emptySet<PronunciationCapability>()
        override suspend fun speak(text: String): PronunciationResult {
            events += "$name:speak($text)"
            return result
        }
    }

    private class CountingFactory(private val provider: PronunciationProvider) :
        MiMoPronunciationProviderFactory,
        OpenAiPronunciationProviderFactory {
        val profileIds = mutableListOf<String>()

        override fun create(profileId: String): PronunciationProvider {
            profileIds += profileId
            return provider
        }
    }

    private open class FakeProvider(var result: PronunciationResult) : PronunciationProvider {
        var calls = 0
        override fun capabilities() = emptySet<PronunciationCapability>()
        override suspend fun speak(text: String): PronunciationResult {
            calls++
            return result
        }
    }

    private class ThrowingProvider : FakeProvider(PronunciationResult.Played) {
        override suspend fun speak(text: String): PronunciationResult {
            calls++
            throw CancellationException("cancel")
        }
    }
}
