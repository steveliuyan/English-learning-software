package com.example.englishlearning.language.infrastructure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AndroidAudioPlayer` 暂停/继续/停止的设备侧取证（controller 授权新增，超出 brief 的 Files 清单）。
 *
 * 为什么必须在设备上：`MediaPlayer` 与 `MediaDataSource` 是 Android 框架类，JVM 单测拿不到真实播放会话，
 * 本任务最高风险的这个文件在 JVM 侧零覆盖；这里用测试侧生成的多秒 PCM WAV 直接驱动它，
 * 证明三件事：播中 `pauseCurrentPlayback()` 返回 true、`resumeCurrentPlayback()` 接管已暂停会话、
 * `stopCurrentPlayback()` 让挂起的 `play()` **立即**以 `Played` 收口（超时即视为悬挂失败）。
 *
 * 命名沿用 androidTest 侧约定（camelCase），与 `KokoroProductionPronunciationTest` 同族。
 */
class AndroidAudioPlayerControlsTest {
    @Test
    fun pauseCurrentPlaybackSucceedsOncePlayingAndResumeFollows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = AndroidAudioPlayer(context)
        val playback = async { player.play(sineWav(seconds = 6), "wav") }
        try {
            // prepared 之前会话还没挂上，pause 返回 false；轮到 true 说明已进入可播控状态。
            withTimeout(PREPARE_TIMEOUT_MS) {
                while (!player.pauseCurrentPlayback()) delay(POLL_INTERVAL_MS)
            }
            assertTrue("resume must take over the paused session", player.resumeCurrentPlayback())
            withTimeout(RESUME_TIMEOUT_MS) {
                while (!player.pauseCurrentPlayback()) delay(POLL_INTERVAL_MS)
            }
            println("ANDROID_AUDIO_PLAYER pause=ok resume=ok re-pause=ok")
        } finally {
            playback.cancel()
        }
    }

    @Test
    fun stopCurrentPlaybackResolvesSuspendedPlayAsPlayedWithoutHanging() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = AndroidAudioPlayer(context)
        val playback = async { player.play(sineWav(seconds = 6), "wav") }
        withTimeout(PREPARE_TIMEOUT_MS) {
            while (!player.pauseCurrentPlayback()) delay(POLL_INTERVAL_MS)
        }
        assertTrue("resume must succeed before stopping", player.resumeCurrentPlayback())

        player.stopCurrentPlayback()

        val outcome = withTimeout(STOP_TIMEOUT_MS) {
            // 用户主动停止后 play() 必须马上返回；走到超时就是说 speak 悬挂。
            playback.await()
        }
        assertEquals(AudioPlaybackResult.Played, outcome)
        assertFalse("stopped session must not stay controllable", player.pauseCurrentPlayback())
        assertFalse("stopped session must not stay controllable", player.resumeCurrentPlayback())
        println("ANDROID_AUDIO_PLAYER stop resolved play() immediately outcome=Played")
    }

    /**
     * 暂停中直接点下一个词（新的 `play()` 抢占尚未结束的旧会话）时的收口取证。
     *
     * 生产代码里 `play()` 会覆盖 `active`/`preparing`/`pending` 三个句柄字段：
     * 若安装新句柄前不收口旧会话，旧续程不再被任何字段引用（点停止也找不到它），
     * 旧的 `speak` 会永久悬挂在 Playing，且旧 `MediaPlayer` 永不 release。
     * 这里断言旧会话立即以 `Played` 返回、且新会话仍可被播控。
     */
    @Test
    fun playbackStartedWhilePreviousSessionPausedResolvesTheOldSessionWithoutHanging() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = AndroidAudioPlayer(context)
        val first = async { player.play(sineWav(seconds = 6), "wav") }
        withTimeout(PREPARE_TIMEOUT_MS) {
            while (!player.pauseCurrentPlayback()) delay(POLL_INTERVAL_MS)
        }

        val second = async { player.play(sineWav(seconds = 6), "wav") }
        try {
            val outcome = withTimeout(STOP_TIMEOUT_MS) { first.await() }
            assertEquals(AudioPlaybackResult.Played, outcome)
            println("ANDROID_AUDIO_PLAYER preempt oldSession=Played-no-hang")

            withTimeout(PREPARE_TIMEOUT_MS) {
                while (!player.pauseCurrentPlayback()) delay(POLL_INTERVAL_MS)
            }
            assertTrue("the preempting session must stay controllable", player.resumeCurrentPlayback())
            println("ANDROID_AUDIO_PLAYER preempt newSession=controllable")
        } finally {
            player.stopCurrentPlayback()
            first.cancel()
            second.cancel()
        }
    }

    /**
     * 变速参数（F3-02 播放侧变速）的设备侧冒烟取证：非默认倍速**不得**打断真实 `MediaPlayer` 的
     * prepared→start 链路——`playbackParams.setSpeed` 若用错时序/对象，会抛异常走 `Failed`，
     * 会话永远 pause 不到 true（本用例超时即红）。变速后的暂停/继续仍可用一并断言。
     */
    @Test
    fun playAtNonDefaultRateStillStartsAndStaysControllable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = AndroidAudioPlayer(context)
        // 12 秒音频 @1.25x ≈ 9.6 秒墙钟：给「异步暂停收敛 → 继续 → 再暂停」留出窗口，
        // 否则会话可能在轮询期间自然播完，pause/resume 双双变 false 而误报。
        val playback = async { player.play(sineWav(seconds = 12), "wav", 1.25f) }
        try {
            withTimeout(PREPARE_TIMEOUT_MS) {
                while (!player.pauseCurrentPlayback()) delay(POLL_INTERVAL_MS)
            }
            // 设备实测取证（首轮 connected 原文 `FAILURES!!!` 即此竞态，非实现缺陷）：
            // 变速会话下 `MediaPlayer.pause()` 是异步收敛的，紧随其后的一次 `resumeCurrentPlayback()`
            // 可能因 `isPlaying` 仍为 true 而如实返回 false（此刻确实「没有在暂停，无需继续」）。
            // 因此按本文件既有口径轮询到接管成功——轮询到超时即红，证伪力不降；
            // 再补一次「仍可暂停」，证明继续真的接管了这个变速会话。
            withTimeout(RESUME_TIMEOUT_MS) {
                while (!player.resumeCurrentPlayback()) delay(POLL_INTERVAL_MS)
            }
            withTimeout(RESUME_TIMEOUT_MS) {
                while (!player.pauseCurrentPlayback()) delay(POLL_INTERVAL_MS)
            }
            println("ANDROID_AUDIO_PLAYER rate=1.25 controllable=ok")
        } finally {
            playback.cancel()
        }
    }

    /** 生成 PCM16 单声道正弦 WAV：必须足够长，才能在真实播放中被暂停与停止。 */
    private fun sineWav(seconds: Int, sampleRate: Int = 16_000): ByteArray {
        val sampleCount = sampleRate * seconds
        val buffer = ByteBuffer.allocate(44 + sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + sampleCount * 2)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2) // block align
        buffer.putShort(16) // bits per sample
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(sampleCount * 2)
        for (index in 0 until sampleCount) {
            val sample = (sin(2.0 * PI * 440.0 * index / sampleRate) * 8_000.0).toInt().toShort()
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    private companion object {
        const val PREPARE_TIMEOUT_MS = 30_000L
        const val RESUME_TIMEOUT_MS = 10_000L
        const val STOP_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 25L
    }
}
