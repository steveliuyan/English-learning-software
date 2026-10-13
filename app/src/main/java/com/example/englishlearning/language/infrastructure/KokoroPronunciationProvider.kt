package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.PronunciationAccent
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KokoroPronunciationProvider(
    private val engine: KokoroEngine,
    private val player: AudioPlayer,
    // 合成结果旁路缓存（按 文本+口音）：命中直接播放，重复点词不再重跑推理。
    private val audioCache: SynthesizedAudioCache? = null,
) : PronunciationProvider {
    private val requestMutex = Mutex()

    override fun capabilities(): Set<PronunciationCapability> = setOf(PronunciationCapability.OnDeviceAudio)

    override suspend fun speak(text: String): PronunciationResult =
        speak(text, PronunciationAccent.American)

    override suspend fun speak(
        text: String,
        accent: PronunciationAccent,
    ): PronunciationResult {
        if (text.isBlank()) return PronunciationResult.Unavailable("blank text")

        // 等上一请求取消清理完成，再检查共享引擎是否需要重新准备。
        return requestMutex.withLock { speakPrepared(text, accent, forceRegenerate = false) }
    }

    override suspend fun speak(
        text: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
    ): PronunciationResult {
        if (text.isBlank()) return PronunciationResult.Unavailable("blank text")
        return requestMutex.withLock { speakPrepared(text, accent, forceRegenerate) }
    }

    private suspend fun speakPrepared(
        text: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
    ): PronunciationResult {
        return try {
            if (!forceRegenerate) {
                val cached = playCached(text, accent)
                if (cached != null) return cached
            }
            if (!engine.isReady() && engine.prepare().isFailure) {
                return PronunciationResult.Unavailable("Kokoro engine unavailable")
            }
            if (!engine.isReady()) return PronunciationResult.Unavailable("Kokoro engine unavailable")
            val audio = engine.synthesize(text, accent).getOrElse { throw it }
            playAndCache(text, accent, audio)
        } catch (cancelled: CancellationException) {
            // 引擎由应用级 DI 共享；单次请求取消不能永久关闭它。
            // 合成阶段由引擎释放当前 runtime，播放阶段由 player 释放当前播放器。
            throw cancelled
        } catch (error: Exception) {
            PronunciationResult.Failed(error)
        }
    }

    /** 播放合成结果并顺手写缓存；[audio] 所有权在此，播完必清零。 */
    private suspend fun playAndCache(text: String, accent: PronunciationAccent, audio: ByteArray): PronunciationResult {
        try {
            if (audio.isEmpty()) return PronunciationResult.Failed()
            // 缓存是旁路：写失败不打断本次播放，也不改变结果。
            runCatching { audioCache?.write(text, accent, audio) }
            val played = player.play(audio, "wav") is AudioPlaybackResult.Played
            return if (played) PronunciationResult.Played else PronunciationResult.Failed()
        } finally {
            audio.fill(0)
        }
    }

    /** 缓存命中并播放成功返回 Played；未命中或缓存播放失败返回 null，走正常合成路径。 */
    private suspend fun playCached(text: String, accent: PronunciationAccent): PronunciationResult? {
        val cache = audioCache ?: return null
        val bytes = runCatching { cache.read(text, accent) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            if (player.play(bytes, "wav") is AudioPlaybackResult.Played) PronunciationResult.Played else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            bytes.fill(0)
        }
    }
}
