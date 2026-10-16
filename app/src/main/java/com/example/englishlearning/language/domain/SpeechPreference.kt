package com.example.englishlearning.language.domain

data class SpeechPreference(
    val preferenceId: String = "device",
    val selectedEngine: PronunciationEngine = PronunciationEngine.SystemTts,
    val openAiProfileId: String? = null,
    val miMoProfileId: String? = null,
    /** 学习流词卡就绪时自动朗读一次（D1，docs/decisions/2026-10-06-auto-pronunciation-and-wordbook-words.md）。 */
    val autoSpeakOnCardReady: Boolean = true,
    /** 播放侧变速倍率（F3-02 四控之速，决策 2026-10-15 §2）；读写两侧 clamp 到 0.5–2.0。 */
    val speechRate: Float = 1.0f,
)
