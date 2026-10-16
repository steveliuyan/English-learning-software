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

    private class ControlsAudioPlayer(
        private val pauseResult: Boolean,
        private val resumeResult: Boolean,
    ) : AudioPlayer {
        val controlCalls = mutableListOf<String>()

        override suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult = AudioPlaybackResult.Played
        override fun pauseCurrentPlayback(): Boolean { controlCalls += "pause"; return pauseResult }
        override fun resumeCurrentPlayback(): Boolean { controlCalls += "resume"; return resumeResult }
        override fun stopCurrentPlayback() { controlCalls += "stop" }
    }

    private class FakeKokoroEngine : KokoroEngine {
        override suspend fun prepare(): Result<Unit> = Result.success(Unit)
        override fun isReady(): Boolean = true
        override suspend fun synthesize(
            text: String,
            accent: com.example.englishlearning.language.domain.PronunciationAccent,
        ): Result<ByteArray> = Result.success(byteArrayOf(1, 2))
        override suspend fun close() = Unit
    }
}
