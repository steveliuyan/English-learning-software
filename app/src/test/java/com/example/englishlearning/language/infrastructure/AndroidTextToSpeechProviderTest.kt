package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.PronunciationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidTextToSpeechProviderTest {
    @Test
    fun `initialization failure returns unavailable`() {
        val engine = FakeEngine(initialized = false)
        val provider = AndroidTextToSpeechProvider(engine)

        kotlinx.coroutines.test.runTest {
            val result = provider.speak("ability")

            assertIs<PronunciationResult.Unavailable>(result)
            assertEquals("text to speech unavailable", result.reason)
        }
    }

    @Test
    fun `blank text does not call engine`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)

        kotlinx.coroutines.test.runTest {
            val result = provider.speak("  ")

            assertIs<PronunciationResult.Unavailable>(result)
            assertEquals("blank text", result.reason)
            assertEquals(emptyList(), engine.spoken)
        }
    }

    @Test
    fun `normal text uses english locale and passes original text`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)

        kotlinx.coroutines.test.runTest {
            val result = provider.speak(" ability ")

            assertIs<PronunciationResult.Played>(result)
            assertEquals(listOf(" ability "), engine.spoken)
            assertEquals(java.util.Locale.ENGLISH, engine.locale)
        }
    }

    @Test
    fun `stop and release delegate to engine`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)

        provider.stop()
        provider.release()

        assertEquals(1, engine.stopCount)
        assertEquals(1, engine.releaseCount)
    }

    @Test
    fun `pause resume and stop delegate to engine`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)

        assertTrue(provider.pausePlayback()); assertEquals(1, engine.pauseCount)
        assertTrue(provider.resumePlayback()); assertEquals(1, engine.resumeCount)
        provider.stopPlayback(); assertEquals(1, engine.stopCount)
    }

    @Test
    fun `rate overrides applied before speak`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)
        kotlinx.coroutines.test.runTest { provider.speak("hello", com.example.englishlearning.language.domain.PronunciationAccent.American, false, 1.5f) }
        assertEquals(1.5f, engine.lastRate)
    }

    /**
     * I1 反向闸（系统 TTS 变速的对称面）：本 provider 由 Hilt 提供为 @Singleton，
     * 而 `TextToSpeech.setSpeechRate` 是对共享引擎的**进程粘性**设置，不是逐句参数，
     * 所以「1.5x 调回 1.0x」必须显式把引擎速度复位，否则系统朗读会停在最后一次的非 1 倍速，
     * 直到进程重启。
     * 这条用例钉住实现里的 `if (rate != 1.0f)` 守卫：守卫存在时第二次 speak 跳过 setSpeechRate，
     * `rates` 只有一条、`lastRate` 停在 1.5f → 红；守卫写成反向（`rate == 1.0f`）同样红。
     */
    @Test
    fun `returning to normal rate still resets the engine speed`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)

        kotlinx.coroutines.test.runTest {
            provider.speak("first", com.example.englishlearning.language.domain.PronunciationAccent.American, false, 1.5f)
            provider.speak("second", com.example.englishlearning.language.domain.PronunciationAccent.American, false, 1.0f)
        }

        assertEquals(listOf(1.5f, 1.0f), engine.rates)
        assertEquals(1.0f, engine.lastRate)
    }

    /**
     * 时序闸：必须先落引擎语速再出声——`setSpeechRate` 挪到 `speak()` 之后，
     * 本句会以上一句的速率播出（静默错速），这条用例即红。
     */
    @Test
    fun `rate is applied before the utterance reaches the engine`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)

        kotlinx.coroutines.test.runTest {
            provider.speak("hello", com.example.englishlearning.language.domain.PronunciationAccent.American, false, 1.25f)
        }

        assertEquals(listOf("rate=1.25", "speak"), engine.calls)
    }

    private class FakeEngine(private val initialized: Boolean) : TextToSpeechEngine {
        val spoken = mutableListOf<String>()
        var locale: java.util.Locale? = null
        var stopCount = 0
        var releaseCount = 0
        var pauseCount = 0
        var resumeCount = 0
        var lastRate: Float? = null

        /** 设速调用按顺序留痕（覆盖式 `lastRate` 证伪不了「跳过复位」与「乱序」）。 */
        val rates = mutableListOf<Float>()

        /** 引擎调用序列：只记 `setSpeechRate` 与 `speak`，用于钉住「先设速再发声」。 */
        val calls = mutableListOf<String>()

        override fun isInitialized(): Boolean = initialized
        override fun setLanguage(locale: java.util.Locale): Boolean { this.locale = locale; return initialized }
        override fun speak(text: String): Boolean { spoken += text; calls += "speak"; return initialized }
        override fun setSpeechRate(rate: Float): Boolean { lastRate = rate; rates += rate; calls += "rate=$rate"; return true }
        override fun pause(): Boolean { pauseCount++; return true }
        override fun resume(): Boolean { resumeCount++; return true }
        override fun stop() { stopCount++ }
        override fun release() { releaseCount++ }
    }
}
