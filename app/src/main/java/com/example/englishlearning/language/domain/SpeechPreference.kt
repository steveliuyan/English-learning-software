package com.example.englishlearning.language.domain

data class SpeechPreference(
    val preferenceId: String = "device",
    val selectedEngine: PronunciationEngine = PronunciationEngine.SystemTts,
    val openAiProfileId: String? = null,
    val miMoProfileId: String? = null,
    /** 学习流词卡就绪时自动朗读一次（D1，docs/decisions/2026-10-06-auto-pronunciation-and-wordbook-words.md）。 */
    val autoSpeakOnCardReady: Boolean = true,
)
