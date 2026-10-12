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
        return requestMutex.withLock { speakPrepared(text, accent) }
    }

    private suspend fun speakPrepared(text: String, accent: PronunciationAccent): PronunciationResult {
        return try {
            if (!engine.isReady() && engine.prepare().isFailure) {
                return PronunciationResult.Unavailable("Kokoro engine unavailable")
            }
            if (!engine.isReady()) return PronunciationResult.Unavailable("Kokoro engine unavailable")
            val audio = engine.synthesize(text, accent).getOrElse { throw it }
            try {
                if (audio.isEmpty()) {
                    PronunciationResult.Failed()
                } else if (player.play(audio, "wav") is AudioPlaybackResult.Played) {
                    PronunciationResult.Played
                } else {
                    PronunciationResult.Failed()
                }
            } finally {
                audio.fill(0)
            }
        } catch (cancelled: CancellationException) {
            // 引擎由应用级 DI 共享；单次请求取消不能永久关闭它。
            // 合成阶段由引擎释放当前 runtime，播放阶段由 player 释放当前播放器。
            throw cancelled
        } catch (error: Exception) {
            PronunciationResult.Failed(error)
        }
    }
}
