package com.example.englishlearning.language.domain

/** Boundary for system, on-device, or cloud pronunciation sources. */
interface PronunciationProvider {
    fun capabilities(): Set<PronunciationCapability>

    /** 暂停当前发声会话；不支持或无会话时 false（决策 §2）。 */
    fun pausePlayback(): Boolean = false

    /** 继续已暂停的会话；不支持或无会话时 false。 */
    fun resumePlayback(): Boolean = false

    /** 结束当前会话；默认实现为空操作（未播放时安全无副作用）。 */
    fun stopPlayback() { }

    suspend fun speak(text: String): PronunciationResult

    /**
     * 单词发音（F3-02 四控之速：`rate` 为播放侧变速倍率，决策 §2）。
     * 三参版与四参版在 Kotlin 里默认值签名冲突，按任务书裁决合并为单一声明，调用点零改动。
     */
    suspend fun speakWord(
        text: String,
        accent: PronunciationAccent = PronunciationAccent.American,
        forceRegenerate: Boolean = false,
        rate: Float = 1.0f,
    ): PronunciationResult = speak(text, accent, forceRegenerate, rate)

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

    /** 带播放倍率的发声；默认实现忽略 rate 委托三参版（决策 §2：变速在播放侧，生成参数不变）。 */
    suspend fun speak(
        text: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
        rate: Float,
    ): PronunciationResult = speak(text, accent, forceRegenerate)
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
