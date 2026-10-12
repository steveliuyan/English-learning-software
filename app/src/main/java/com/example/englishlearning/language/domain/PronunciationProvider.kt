package com.example.englishlearning.language.domain

/** Boundary for system, on-device, or cloud pronunciation sources. */
interface PronunciationProvider {
    fun capabilities(): Set<PronunciationCapability>

    suspend fun speak(text: String): PronunciationResult

    suspend fun speakWord(
        text: String,
        accent: PronunciationAccent = PronunciationAccent.American,
        forceRegenerate: Boolean = false,
    ): PronunciationResult = speak(text, accent, forceRegenerate)

    suspend fun speakSentence(
        text: String,
        accent: PronunciationAccent = PronunciationAccent.American,
        forceRegenerate: Boolean = false,
    ): PronunciationResult = speak(text, accent, forceRegenerate)

    suspend fun speak(text: String, accent: PronunciationAccent): PronunciationResult = speak(text)

    suspend fun speak(
        text: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
    ): PronunciationResult = speak(text, accent)
}

sealed interface PronunciationResult {
    data object Played : PronunciationResult
    data class Unavailable(val reason: String) : PronunciationResult
    data class Failed(val cause: Throwable? = null) : PronunciationResult
}

enum class PronunciationCapability {
    SystemTextToSpeech,
    OnDeviceAudio,
    RemoteAudio,
}

enum class PronunciationAccent {
    American,
    British,
}
