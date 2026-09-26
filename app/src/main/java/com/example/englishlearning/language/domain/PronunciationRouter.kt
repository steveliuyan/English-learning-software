package com.example.englishlearning.language.domain

import com.example.englishlearning.language.SpeechPreferenceRepository
import kotlinx.coroutines.CancellationException

enum class PronunciationEngine {
    SystemTts,
    MiMo,
    OpenAi,
}

fun interface MiMoPronunciationProviderFactory {
    fun create(profileId: String): PronunciationProvider
}

fun interface OpenAiPronunciationProviderFactory {
    fun create(profileId: String): PronunciationProvider
}

class PronunciationRouter(
    private val systemProvider: PronunciationProvider,
    private val miMoFactory: MiMoPronunciationProviderFactory,
    private val openAiFactory: OpenAiPronunciationProviderFactory,
    private val preferences: SpeechPreferenceRepository? = null,
) : PronunciationProvider {
    override fun capabilities(): Set<PronunciationCapability> = systemProvider.capabilities()

    override suspend fun speak(text: String): PronunciationResult = speak(text, savedPreference())

    private suspend fun savedPreference(): SpeechPreference = try {
        preferences?.get()?.getOrElse { error ->
            if (error is CancellationException) throw error
            SpeechPreference()
        } ?: SpeechPreference()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        SpeechPreference()
    }

    suspend fun speak(text: String, preference: SpeechPreference): PronunciationResult = when (preference.selectedEngine) {
        PronunciationEngine.SystemTts -> systemProvider.speak(text)
        PronunciationEngine.MiMo -> speakRemoteOrSystem(text, preference.miMoProfileId, miMoFactory::create)
        PronunciationEngine.OpenAi -> {
            val openAiResult = speakRemote(text, preference.openAiProfileId, openAiFactory::create)
            if (openAiResult == PronunciationResult.Played) openAiResult
            else speakRemoteOrSystem(text, preference.miMoProfileId, miMoFactory::create)
        }
    }

    private suspend fun speakRemoteOrSystem(
        text: String,
        profileId: String?,
        factory: (String) -> PronunciationProvider,
    ): PronunciationResult = speakRemote(text, profileId, factory).let { result ->
        if (result == PronunciationResult.Played) result else systemProvider.speak(text)
    }

    private suspend fun speakRemote(
        text: String,
        profileId: String?,
        factory: (String) -> PronunciationProvider,
    ): PronunciationResult {
        if (profileId == null) return PronunciationResult.Unavailable("Missing speech profile")
        return try {
            factory(profileId).speak(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PronunciationResult.Failed()
        }
    }
}
