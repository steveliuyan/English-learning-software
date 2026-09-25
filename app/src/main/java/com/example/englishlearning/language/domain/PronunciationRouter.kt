package com.example.englishlearning.language.domain

import kotlinx.coroutines.CancellationException

enum class PronunciationEngine {
    SystemTts,
    MiMo,
}

data class PronunciationSelection(
    val engine: PronunciationEngine = PronunciationEngine.SystemTts,
    val profileId: String? = null,
)

fun interface MiMoPronunciationProviderFactory {
    fun create(profileId: String): PronunciationProvider
}

class PronunciationRouter(
    private val systemProvider: PronunciationProvider,
    private val miMoFactory: MiMoPronunciationProviderFactory,
) {
    suspend fun speak(
        text: String,
        selection: PronunciationSelection = PronunciationSelection(),
    ): PronunciationResult {
        if (selection.engine == PronunciationEngine.SystemTts || selection.profileId == null) {
            return systemProvider.speak(text)
        }
        val result = try {
            miMoFactory.create(selection.profileId).speak(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PronunciationResult.Failed()
        }
        return when (result) {
            PronunciationResult.Played -> result
            is PronunciationResult.Unavailable, is PronunciationResult.Failed -> systemProvider.speak(text)
        }
    }
}
