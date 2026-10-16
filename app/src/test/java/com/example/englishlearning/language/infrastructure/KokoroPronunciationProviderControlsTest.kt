package com.example.englishlearning.language.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Kokoro 词发音的暂停/继续/停止必须原样委托给播放层并透传结果（决策 §2）。
 * 委托是无状态直传，主线程调用约定由 [AndroidAudioPlayer] 一侧声明。
 */
class KokoroPronunciationProviderControlsTest {
    @Test
    fun `pause resume and stop delegate to the audio player and pass the result through`() {
        val player = ControlsAudioPlayer(pauseResult = true, resumeResult = true)
        val provider = KokoroPronunciationProvider(FakeKokoroEngine(), player)

        assertTrue(provider.pausePlayback())
        assertTrue(provider.resumePlayback())
        provider.stopPlayback()

        assertEquals(listOf("pause", "resume", "stop"), player.controlCalls)
    }

    @Test
    fun `player without an active session makes controls report unsupported`() {
        val player = ControlsAudioPlayer(pauseResult = false, resumeResult = false)
        val provider = KokoroPronunciationProvider(FakeKokoroEngine(), player)

        assertFalse(provider.pausePlayback())
        assertFalse(provider.resumePlayback())
        assertEquals(listOf("pause", "resume"), player.controlCalls)
    }

    @Test
    fun `four argument speak carries the rate down to the audio player`() = kotlinx.coroutines.test.runTest {
        // 决策 §2：语速=播放侧变速。Kokoro 的合成与缓存都不因 rate 改变，只有 play 收 rate。
        val player = ControlsAudioPlayer(pauseResult = false, resumeResult = false)
        val provider = KokoroPronunciationProvider(FakeKokoroEngine(), player)

        assertEquals(
            com.example.englishlearning.language.domain.PronunciationResult.Played,
            provider.speak("hello", com.example.englishlearning.language.domain.PronunciationAccent.American, false, 1.25f),
        )
        assertEquals(1.25f, player.lastRate)
    }

    @Test
    fun `a synthesis cache hit plays at the requested rate too`() = kotlinx.coroutines.test.runTest {
        // 缓存命中不重跑推理，但倍率仍要落到播放器：playCached 里那一处 rate 只有本用例钉住。
        val engine = FakeKokoroEngine()
        val player = ControlsAudioPlayer(pauseResult = false, resumeResult = false)
        val provider = KokoroPronunciationProvider(engine, player, StubSynthCache(byteArrayOf(7, 8)))

        assertEquals(
            com.example.englishlearning.language.domain.PronunciationResult.Played,
            provider.speak("hello", com.example.englishlearning.language.domain.PronunciationAccent.American, false, 0.75f),
        )
        assertEquals(0.75f, player.lastRate)
        assertEquals(0, engine.synthesizeCalls)
    }

    private class ControlsAudioPlayer(
        private val pauseResult: Boolean,
        private val resumeResult: Boolean,
    ) : AudioPlayer {
        val controlCalls = mutableListOf<String>()
        var lastRate: Float? = null

        override suspend fun play(bytes: ByteArray, format: String, rate: Float): AudioPlaybackResult {
            lastRate = rate
            return AudioPlaybackResult.Played
        }
        override fun pauseCurrentPlayback(): Boolean { controlCalls += "pause"; return pauseResult }
        override fun resumeCurrentPlayback(): Boolean { controlCalls += "resume"; return resumeResult }
        override fun stopCurrentPlayback() { controlCalls += "stop" }
    }

    private class FakeKokoroEngine : KokoroEngine {
        var synthesizeCalls = 0
        override suspend fun prepare(): Result<Unit> = Result.success(Unit)
        override fun isReady(): Boolean = true
        override suspend fun synthesize(
            text: String,
            accent: com.example.englishlearning.language.domain.PronunciationAccent,
        ): Result<ByteArray> {
            synthesizeCalls++
            return Result.success(byteArrayOf(1, 2))
        }
        override suspend fun close() = Unit
    }

    /** 永远命中的合成缓存桩：用于证明「缓存路径也带倍率」，与推理路径分开。 */
    private class StubSynthCache(private val audio: ByteArray) : SynthesizedAudioCache {
        override suspend fun read(
            text: String,
            accent: com.example.englishlearning.language.domain.PronunciationAccent,
        ): ByteArray = audio.copyOf()

        override suspend fun write(
            text: String,
            accent: com.example.englishlearning.language.domain.PronunciationAccent,
            wav: ByteArray,
        ) = Unit
    }
}
