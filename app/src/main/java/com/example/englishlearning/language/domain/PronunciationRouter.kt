package com.example.englishlearning.language.domain

import com.example.englishlearning.language.SpeechPreferenceRepository
import kotlinx.coroutines.CancellationException

enum class PronunciationEngine {
    SystemTts,
    MiMo,
}

fun interface MiMoPronunciationProviderFactory {
    fun create(profileId: String): PronunciationProvider
}

/** Routes pronunciation by content semantics, never by a caller-selected remote word provider. */
class PronunciationRouter(
    private val kokoroProvider: PronunciationProvider,
    private val systemProvider: PronunciationProvider,
    private val miMoFactory: MiMoPronunciationProviderFactory,
    private val preferences: SpeechPreferenceRepository,
) : PronunciationProvider {
    override fun capabilities(): Set<PronunciationCapability> =
        kokoroProvider.capabilities() + systemProvider.capabilities()

    /** Compatibility entry point for existing sentence/preview callers. */
    override suspend fun speak(text: String): PronunciationResult = speakSentence(text)

    override suspend fun speak(
        text: String,
        accent: PronunciationAccent,
    ): PronunciationResult = speakSentence(text, accent)

    override suspend fun speak(
        text: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
    ): PronunciationResult = speakSentence(text, accent, forceRegenerate)

    override suspend fun speakWord(
        word: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
    ): PronunciationResult = kokoroProvider.speak(word, accent, forceRegenerate)

    suspend fun speakWord(word: String, accent: PronunciationAccent): PronunciationResult =
        speakWord(word, accent, false)

    override suspend fun speakSentence(
        sentence: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
    ): PronunciationResult = routeSentence(sentence, accent, forceRegenerate)

    suspend fun speakSentence(sentence: String): PronunciationResult =
        routeSentence(sentence, PronunciationAccent.American, false)

    suspend fun speakSentence(sentence: String, accent: PronunciationAccent): PronunciationResult =
        routeSentence(sentence, accent, false)

    private suspend fun routeSentence(
        sentence: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
    ): PronunciationResult {
        val preference = savedPreference()
        return when (preference.selectedEngine) {
            PronunciationEngine.SystemTts -> systemProvider.speak(sentence, accent)
            PronunciationEngine.MiMo -> speakMiMoOrSystem(
                sentence = sentence,
                accent = accent,
                forceRegenerate = forceRegenerate,
                profileId = preference.miMoProfileId,
            )
        }
    }

    private suspend fun savedPreference(): SpeechPreference = try {
        preferences.get().getOrElse { error ->
            if (error is CancellationException) throw error
            SpeechPreference()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        SpeechPreference()
    }

    private suspend fun speakMiMoOrSystem(
        sentence: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
        profileId: String?,
    ): PronunciationResult {
        val remote = if (profileId == null) {
            PronunciationResult.Unavailable("Missing speech profile")
        } else {
            try {
                miMoFactory.create(profileId).speak(sentence, accent, forceRegenerate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                PronunciationResult.Failed(error)
            }
        }
        return if (remote == PronunciationResult.Played) remote else systemProvider.speak(sentence, accent)
    }
}
