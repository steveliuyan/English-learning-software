package com.example.englishlearning.screentranslation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F3-05 / AC3-12：同一屏指纹在节流窗口内只发一次请求；手动动作不被节流拦住。 */
class ScreenCaptureGateTest {
    private val gate = ScreenCaptureGate(minIntervalMillis = 3_000L)

    private fun decideAutomatic(
        mode: ScreenTranslationMode,
        fingerprint: Long,
        nowMillis: Long,
    ): CaptureDecision = gate.decide(mode, fingerprint, nowMillis, CaptureTrigger.Automatic)

    @Test
    fun offModeNeverSends() {
        val decision = decideAutomatic(ScreenTranslationMode.Off, fingerprint = 7L, nowMillis = 0L)

        assertFalse(decision.send)
        assertEquals(CaptureSkipReason.Stopped, decision.skipReason)
    }

    @Test
    fun manualTriggerDuringOffModeStillDoesNotSend() {
        val decision = gate.decide(ScreenTranslationMode.Off, 7L, 0L, CaptureTrigger.Manual)

        assertFalse(decision.send)
        assertEquals(CaptureSkipReason.Stopped, decision.skipReason)
    }

    @Test
    fun continuousSendsFirstFrameAndThenSkipsTheVerySameScreen() {
        assertTrue(decideAutomatic(ScreenTranslationMode.Continuous, 7L, 0L).send)
        gate.markSent(7L, 0L)

        val second = decideAutomatic(ScreenTranslationMode.Continuous, 7L, 60_000L)

        assertFalse(second.send)
        assertEquals(CaptureSkipReason.DuplicateFrame, second.skipReason)
    }

    @Test
    fun continuousThrottlesANewScreenInsideTheWindow() {
        gate.markSent(fingerprint = 7L, nowMillis = 1_000L)

        val decision = decideAutomatic(ScreenTranslationMode.Continuous, fingerprint = 8L, nowMillis = 2_500L)

        assertFalse(decision.send)
        assertEquals(CaptureSkipReason.Throttled, decision.skipReason)
    }

    @Test
    fun continuousSendsANewScreenOnceTheWindowElapsed() {
        gate.markSent(fingerprint = 7L, nowMillis = 1_000L)

        val decision = decideAutomatic(ScreenTranslationMode.Continuous, fingerprint = 8L, nowMillis = 4_000L)

        assertTrue(decision.send)
    }

    @Test
    fun onceModeIgnoresAutomaticPolling() {
        val decision = decideAutomatic(ScreenTranslationMode.Once, fingerprint = 7L, nowMillis = 0L)

        assertFalse(decision.send)
        assertEquals(CaptureSkipReason.SingleShotMode, decision.skipReason)
    }

    @Test
    fun manualTriggerAlwaysSendsInOnceMode() {
        gate.markSent(fingerprint = 7L, nowMillis = 1_000L)

        val decision = gate.decide(ScreenTranslationMode.Once, 7L, 1_100L, CaptureTrigger.Manual)

        assertTrue(decision.send)
    }

    @Test
    fun manualTriggerBypassesBothThrottleAndDedupeInContinuousMode() {
        gate.markSent(fingerprint = 7L, nowMillis = 1_000L)

        assertTrue(gate.decide(ScreenTranslationMode.Continuous, 7L, 1_100L, CaptureTrigger.Manual).send)
        assertTrue(gate.decide(ScreenTranslationMode.Continuous, 9L, 1_100L, CaptureTrigger.Manual).send)
    }

    @Test
    fun skippedFramesDoNotMoveTheBaseline() {
        gate.markSent(fingerprint = 7L, nowMillis = 1_000L)
        // 被节流掉的一帧不调 markSent：基准必须还是 (7, 1000)。
        assertFalse(decideAutomatic(ScreenTranslationMode.Continuous, 8L, 2_000L).send)

        assertFalse(decideAutomatic(ScreenTranslationMode.Continuous, 7L, 2_000L).send)
        assertTrue(decideAutomatic(ScreenTranslationMode.Continuous, 8L, 4_000L).send)
    }

    @Test
    fun resetForgetsTheBaselineSoANewSessionMaySendTheSameScreen() {
        gate.markSent(fingerprint = 7L, nowMillis = 1_000L)
        gate.reset()

        assertTrue(decideAutomatic(ScreenTranslationMode.Continuous, 7L, 1_100L).send)
    }
}
