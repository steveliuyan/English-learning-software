package com.example.englishlearning.language.domain

/** Boundary for system, on-device, or cloud pronunciation sources. */
interface PronunciationProvider {
    fun capabilities(): Set<PronunciationCapability>

    suspend fun speak(text: String): PronunciationResult
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
