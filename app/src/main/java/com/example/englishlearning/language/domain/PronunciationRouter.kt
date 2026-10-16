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

/**
 * Routes pronunciation by content semantics, never by a caller-selected remote word provider.
 *
 * 线程约定（决策 §2）：[activeProvider] 是普通可变字段，**假设主线程串行访问**——
 * speak 与控制按钮都由 Compose 主线程派发，故不引入锁或并发原语；
 * 若后续把它接到别的协程调度器，必须先改为单线程 confined 或补同步设计。
 */
class PronunciationRouter(
    private val kokoroProvider: PronunciationProvider,
    private val systemProvider: PronunciationProvider,
    private val miMoFactory: MiMoPronunciationProviderFactory,
    private val preferences: SpeechPreferenceRepository,
) : PronunciationProvider {
    /** 最近一次发起 speak 的 provider；stop 后清空，控制只作用于它。 */
    private var activeProvider: PronunciationProvider? = null

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
    ): PronunciationResult {
        activeProvider = kokoroProvider
        return kokoroProvider.speak(word, accent, forceRegenerate)
    }

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
            PronunciationEngine.SystemTts -> {
                activeProvider = systemProvider
                systemProvider.speak(sentence, accent)
            }
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
                // 工厂实例必须先落到局部 val：播控要知道真正出声的是哪一个 provider。
                val remoteProvider = miMoFactory.create(profileId)
                activeProvider = remoteProvider
                remoteProvider.speak(sentence, accent, forceRegenerate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                PronunciationResult.Failed(error)
            }
        }
        if (remote == PronunciationResult.Played) return remote
        // 远程没出声就一定由系统 TTS 出声：控制权必须跟着改，否则暂停/停止会打到悬空的 MiMo 实例。
        activeProvider = systemProvider
        return systemProvider.speak(sentence, accent)
    }

    override fun pausePlayback(): Boolean = activeProvider?.pausePlayback() ?: false
    override fun resumePlayback(): Boolean = activeProvider?.resumePlayback() ?: false
    override fun stopPlayback() {
        activeProvider?.stopPlayback()
        activeProvider = null
    }
}
