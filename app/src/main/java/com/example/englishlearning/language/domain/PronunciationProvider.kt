package com.example.englishlearning.language.domain

/** Boundary for system, on-device, or cloud pronunciation sources. */
interface PronunciationProvider {
    fun capabilities(): Set<PronunciationCapability>
}

enum class PronunciationCapability {
    SystemTextToSpeech,
    OnDeviceAudio,
    RemoteAudio,
}
