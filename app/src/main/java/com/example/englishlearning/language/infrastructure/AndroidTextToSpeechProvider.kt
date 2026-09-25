package com.example.englishlearning.language.infrastructure

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult
import java.util.Locale

internal interface TextToSpeechEngine {
    fun isInitialized(): Boolean
    fun setLanguage(locale: Locale): Boolean
    fun speak(text: String): Boolean
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
    override fun speak(text: String): Boolean = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "english-learning") == TextToSpeech.SUCCESS
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

    fun stop() = engine.stop()
    fun release() = engine.release()
}
