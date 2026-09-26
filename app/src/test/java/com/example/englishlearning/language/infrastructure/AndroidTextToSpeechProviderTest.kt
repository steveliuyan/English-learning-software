package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.PronunciationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AndroidTextToSpeechProviderTest {
    @Test
    fun `initialization failure returns unavailable`() {
        val engine = FakeEngine(initialized = false)
        val provider = AndroidTextToSpeechProvider(engine)

        kotlinx.coroutines.test.runTest {
            val result = provider.speak("ability")

            assertIs<PronunciationResult.Unavailable>(result)
            assertEquals("text to speech unavailable", result.reason)
        }
    }

    @Test
    fun `blank text does not call engine`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)

        kotlinx.coroutines.test.runTest {
            val result = provider.speak("  ")

            assertIs<PronunciationResult.Unavailable>(result)
            assertEquals("blank text", result.reason)
            assertEquals(emptyList(), engine.spoken)
        }
    }

    @Test
    fun `normal text uses english locale and passes original text`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)

        kotlinx.coroutines.test.runTest {
            val result = provider.speak(" ability ")

            assertIs<PronunciationResult.Played>(result)
            assertEquals(listOf(" ability "), engine.spoken)
            assertEquals(java.util.Locale.ENGLISH, engine.locale)
        }
    }

    @Test
    fun `stop and release delegate to engine`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)

        provider.stop()
        provider.release()

        assertEquals(1, engine.stopCount)
        assertEquals(1, engine.releaseCount)
    }

    private class FakeEngine(private val initialized: Boolean) : TextToSpeechEngine {
        val spoken = mutableListOf<String>()
        var locale: java.util.Locale? = null
        var stopCount = 0
        var releaseCount = 0

        override fun isInitialized(): Boolean = initialized
        override fun setLanguage(locale: java.util.Locale): Boolean { this.locale = locale; return initialized }
        override fun speak(text: String): Boolean { spoken += text; return initialized }
        override fun stop() { stopCount++ }
        override fun release() { releaseCount++ }
    }
}
