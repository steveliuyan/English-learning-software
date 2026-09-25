package com.example.englishlearning.language.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PronunciationRouterTest {
    @Test
    fun `default and explicit system use only system provider`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val mimo = FakeProvider(PronunciationResult.Played)
        val router = PronunciationRouter(system, FakeFactory(mimo))

        router.speak("hello")
        router.speak("hello", PronunciationSelection(PronunciationEngine.SystemTts))

        assertEquals(2, system.calls)
        assertEquals(0, mimo.calls)
    }

    @Test
    fun `mimo without profile falls back to system`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val mimo = FakeProvider(PronunciationResult.Played)
        PronunciationRouter(system, FakeFactory(mimo)).speak(
            "hello", PronunciationSelection(PronunciationEngine.MiMo, null)
        )
        assertEquals(1, system.calls)
        assertEquals(0, mimo.calls)
    }

    @Test
    fun `mimo factory receives profile and successful mimo does not call system`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val mimo = FakeProvider(PronunciationResult.Played)
        val factory = FakeFactory(mimo)
        PronunciationRouter(system, factory).speak(
            "hello", PronunciationSelection(PronunciationEngine.MiMo, "profile-7")
        )
        assertEquals("profile-7", factory.profileId)
        assertEquals(0, system.calls)
        assertEquals(1, mimo.calls)
    }

    @Test
    fun `mimo unavailable or failed falls back to system`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val mimo = FakeProvider(PronunciationResult.Unavailable("offline"))
        val router = PronunciationRouter(system, FakeFactory(mimo))
        router.speak("hello", PronunciationSelection(PronunciationEngine.MiMo, "p"))
        assertEquals(1, system.calls)
        mimo.result = PronunciationResult.Failed()
        router.speak("hello", PronunciationSelection(PronunciationEngine.MiMo, "p"))
        assertEquals(2, system.calls)
    }

    @Test
    fun `mimo cancellation is rethrown without fallback`() = runTest {
        val system = FakeProvider(PronunciationResult.Played)
        val mimo = ThrowingProvider()
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                PronunciationRouter(system, FakeFactory(mimo)).speak(
                    "hello", PronunciationSelection(PronunciationEngine.MiMo, "p")
                )
            }
        }
        assertEquals(0, system.calls)
    }

    private class FakeFactory(private val provider: PronunciationProvider) : MiMoPronunciationProviderFactory {
        var profileId: String? = null
        override fun create(profileId: String): PronunciationProvider {
            this.profileId = profileId
            return provider
        }
    }

    private open class FakeProvider(var result: PronunciationResult) : PronunciationProvider {
        var calls = 0
        override fun capabilities() = emptySet<PronunciationCapability>()
        override suspend fun speak(text: String): PronunciationResult { calls++; return result }
    }

    private class ThrowingProvider : FakeProvider(PronunciationResult.Played) {
        override suspend fun speak(text: String): PronunciationResult { calls++; throw CancellationException("cancel") }
    }
}
