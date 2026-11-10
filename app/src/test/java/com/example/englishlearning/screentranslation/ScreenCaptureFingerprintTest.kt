package com.example.englishlearning.screentranslation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith

/** F3-05 / AC3-12：同屏指纹必须稳定，滚动/切页必须变，抗锯齿噪声必须被吃掉。 */
class ScreenCaptureFingerprintTest {
    @Test
    fun identicalSamplesProduceIdenticalFingerprints() {
        val samples = IntArray(64) { (it * 4) % 256 }

        assertEquals(ScreenCaptureFingerprint.of(samples), ScreenCaptureFingerprint.of(samples.copyOf()))
    }

    @Test
    fun noiseInsideTheSameQuantisationBucketIsIgnored() {
        // 取每档中点再 ±3：仍在同一档内，指纹必须不变（跨档抖动由 3 秒节流兜底，见 ScreenCaptureFingerprint 注释）。
        val base = IntArray(64) { (it % 31) * 8 + 4 }
        val noisy = IntArray(64) { (base[it] + 3).coerceAtMost(255) }

        assertEquals(ScreenCaptureFingerprint.of(base), ScreenCaptureFingerprint.of(noisy))
    }

    @Test
    fun oneStepOfQuantizedChangeAltersTheFingerprint() {
        val base = IntArray(64) { 100 }
        val changed = base.copyOf().also { it[17] = it[17] + (1 shl ScreenCaptureFingerprint.QUANTIZE_SHIFT) }

        assertNotEquals(ScreenCaptureFingerprint.of(base), ScreenCaptureFingerprint.of(changed))
    }

    @Test
    fun sampleOrderMattersSoScrollingIsDetected() {
        val rising = IntArray(30) { it * 8 }
        val falling = IntArray(30) { (29 - it) * 8 }

        assertNotEquals(ScreenCaptureFingerprint.of(rising), ScreenCaptureFingerprint.of(falling))
    }

    @Test
    fun outOfRangeLumaIsClampedInsteadOfThrowing() {
        assertEquals(ScreenCaptureFingerprint.of(intArrayOf(255, 0)), ScreenCaptureFingerprint.of(intArrayOf(900, -40)))
    }

    @Test
    fun emptySamplesAreRejected() {
        assertFailsWith<IllegalArgumentException> { ScreenCaptureFingerprint.of(IntArray(0)) }
    }

    @Test
    fun lumaUsesRec601Weights() {
        assertEquals(0, ScreenCaptureFingerprint.lumaOf(0, 0, 0))
        assertEquals(255, ScreenCaptureFingerprint.lumaOf(255, 255, 255))
        assertEquals(76, ScreenCaptureFingerprint.lumaOf(255, 0, 0))
    }
}
