package com.example.englishlearning.language.infrastructure

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.englishlearning.language.domain.PronunciationAccent
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult
import java.util.Locale

internal interface TextToSpeechEngine {
    fun isInitialized(): Boolean
    fun setLanguage(locale: Locale): Boolean
    fun setSpeechRate(rate: Float): Boolean
    fun speak(text: String): Boolean
    fun pause(): Boolean
    fun resume(): Boolean
    fun stop()
    fun release()
}

private class AndroidTextToSpeechEngine(context: Context) : TextToSpeechEngine {
    private var ready = false
    private val textToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    override fun isInitialized(): Boolean = ready
    override fun setLanguage(locale: Locale): Boolean = textToSpeech.setLanguage(locale) != TextToSpeech.LANG_NOT_SUPPORTED
    // 取证：本 SDK 的 `TextToSpeech.setSpeechRate(float)` 返回 Int 的 SUCCESS/ERROR（编译错误原文：
    // 31:56 Operator '!=' cannot be applied to 'Int' and 'Float'），故与 Int 常量 ERROR 直接比较，不做 float 转换。
    override fun setSpeechRate(rate: Float): Boolean = textToSpeech.setSpeechRate(rate) != TextToSpeech.ERROR
    override fun speak(text: String): Boolean = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "english-learning") == TextToSpeech.SUCCESS

    /**
     * 框架限制（实测 compileSdk 34/35/36 的 `android.jar` 与编译错误共同取证）：
     * `android.speech.tts.TextToSpeech` 只有 `speak/stop/isSpeaking`，**没有公开的 `pause()`/`resume()`**，
     * 所以系统 TTS 只能如实报告「暂停/继续不支持」，停止仍可用。
     * 需要逐句暂停的语境由 `MediaPlayer` 路径（Kokoro 词发音、MiMo/在线 TTS 句子）承担；
     * 将来若改用 `synthesizeToFile()` + `MediaPlayer` 播系统 TTS，播控就落回 [AudioPlayer] 一侧，
     * 这两个方法的签名无需变化。
     */
    override fun pause(): Boolean = false
    override fun resume(): Boolean = false

    override fun stop() { textToSpeech.stop() }
    override fun release() { textToSpeech.shutdown() }
}

class AndroidTextToSpeechProvider internal constructor(
    private val engine: TextToSpeechEngine,
) : PronunciationProvider {
    constructor(context: Context) : this(AndroidTextToSpeechEngine(context))

    override fun capabilities(): Set<PronunciationCapability> = setOf(PronunciationCapability.SystemTextToSpeech)

    override suspend fun speak(text: String): PronunciationResult {
        if (text.isBlank()) return PronunciationResult.Unavailable("blank text")
        if (!engine.isInitialized()) return PronunciationResult.Unavailable("text to speech unavailable")
        if (!engine.setLanguage(Locale.ENGLISH)) return PronunciationResult.Unavailable("english locale unavailable")
        return if (engine.speak(text)) PronunciationResult.Played else PronunciationResult.Failed()
    }

    /**
     * 播放侧变速（决策 §2）：**每次**发声前都无条件落引擎语速，含 `1.0f` 的复位。
     * 本 provider 是 Hilt `@Singleton`，而 `TextToSpeech.setSpeechRate` 是对共享引擎的**进程粘性**设置
     * （不是逐句参数），所以「切回常速」必须显式写回 1.0f；加 `rate != 1.0f` 守卫会让引擎停在
     * 最后一次的非 1 倍速直到进程重启，属失效而非优化（取证见 JVM 用例
     * `returning to normal rate still resets the engine speed`）。
     * `setSpeechRate` 失败不阻断朗读——系统仍会以自身默认速度播出，
     * 变速只是体验增强项，不值得为它把整次朗读判失败。
     */
    override suspend fun speak(
        text: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
        rate: Float,
    ): PronunciationResult {
        engine.setSpeechRate(rate)
        return speak(text)
    }

    /**
     * 播控三件套委托给引擎；系统 TTS 的 `pause/resume` 只有引擎点头才算成功。
     * [stopPlayback] 走引擎 `stop()`，与既有公开 [stop]（供页面主动打断）是同一引擎调用，
     * 保留两个入口是为了不破坏既有调用点。
     */
    override fun pausePlayback(): Boolean = engine.pause()
    override fun resumePlayback(): Boolean = engine.resume()
    override fun stopPlayback() { engine.stop() }

    fun stop() = engine.stop()
    fun release() = engine.release()
}
