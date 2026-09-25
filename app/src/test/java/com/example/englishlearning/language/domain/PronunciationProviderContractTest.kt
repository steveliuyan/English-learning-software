package com.example.englishlearning.language.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PronunciationProviderContractTest {
    @Test
    fun `speak records text and returns played`() = runTest {
        val provider = RecordingProvider()

        val result = provider.speak("ability")

        assertEquals(listOf("ability"), provider.spokenTexts)
        assertIs<PronunciationResult.Played>(result)
    }

    @Test
    fun `blank text returns unavailable`() = runTest {
        val provider = RecordingProvider()

        val result = provider.speak("  ")

        assertIs<PronunciationResult.Unavailable>(result)
        assertEquals("blank text", result.reason)
    }

    private class RecordingProvider : PronunciationProvider {
        val spokenTexts = mutableListOf<String>()

        override fun capabilities(): Set<PronunciationCapability> = emptySet()

        override suspend fun speak(text: String): PronunciationResult {
            if (text.isBlank()) return PronunciationResult.Unavailable("blank text")
            spokenTexts += text
            return PronunciationResult.Played
        }
    }
}
