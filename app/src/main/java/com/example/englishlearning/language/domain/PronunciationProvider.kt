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
     *
     * [entity] 是「这段音频从哪张卡来」，只为写台账而存在（决策 2026-11-05 D-7）；
     * 它**不进缓存键**，所以命中与否、复用与否都跟以前一样。
     */
    suspend fun speakWord(
        text: String,
        accent: PronunciationAccent = PronunciationAccent.American,
        forceRegenerate: Boolean = false,
        rate: Float = 1.0f,
        entity: PronunciationEntityRef? = null,
    ): PronunciationResult = speak(text, accent, forceRegenerate, rate, entity)

    suspend fun speakSentence(
        text: String,
        accent: PronunciationAccent = PronunciationAccent.American,
        forceRegenerate: Boolean = false,
        entity: PronunciationEntityRef? = null,
    ): PronunciationResult = speak(text, accent, forceRegenerate, 1.0f, entity)

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

    /**
     * 带关联实体的发声：只有**会写音频缓存**的 provider（端侧 Kokoro、云端 MiMo）需要覆盖它。
     * 默认实现丢掉 entity 委托四参版（决策 D-10）——系统 TTS 按 AGENTS.md 不产出可缓存音频，
     * 让它陪跑一次签名改动毫无收益。代价是「provider 忘了覆盖 ⇒ 实体静默丢失」，
     * 由 `AudioCacheLedgerContractTest` 的调用点判据兜住。
     */
    suspend fun speak(
        text: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
        rate: Float,
        entity: PronunciationEntityRef?,
    ): PronunciationResult = speak(text, accent, forceRegenerate, rate)
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
