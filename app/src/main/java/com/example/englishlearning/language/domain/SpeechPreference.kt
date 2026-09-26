package com.example.englishlearning.language.domain

data class SpeechPreference(
    val preferenceId: String = "device",
    val selectedEngine: PronunciationEngine = PronunciationEngine.SystemTts,
    val openAiProfileId: String? = null,
    val miMoProfileId: String? = null,
)
