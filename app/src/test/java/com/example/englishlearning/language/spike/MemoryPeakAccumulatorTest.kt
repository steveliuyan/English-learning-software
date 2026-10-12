package com.example.englishlearning.language.spike

import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryPeakAccumulatorTest {
    @Test
    fun `tracks independent peaks and the phase of each peak`() {
        val accumulator = MemoryPeakAccumulator()

        accumulator.record("prepare", pssKb = 120_000, nativePssKb = 80_000, nativeHeapBytes = 70_000_000, javaHeapBytes = 20_000_000)
        accumulator.record("synthesis:apple:US", pssKb = 140_000, nativePssKb = 75_000, nativeHeapBytes = 90_000_000, javaHeapBytes = 35_000_000)
        accumulator.record("playback:apple:US", pssKb = 130_000, nativePssKb = 95_000, nativeHeapBytes = 85_000_000, javaHeapBytes = 30_000_000)

        assertEquals(
            MemoryPeakSnapshot(
                samples = 3,
                peakPssKb = 140_000,
                peakPssPhase = "synthesis:apple:US",
                peakNativePssKb = 95_000,
                peakNativePssPhase = "playback:apple:US",
                peakNativeHeapBytes = 90_000_000,
                peakNativeHeapPhase = "synthesis:apple:US",
                peakJavaHeapBytes = 35_000_000,
                peakJavaHeapPhase = "synthesis:apple:US",
            ),
            accumulator.snapshot(),
        )
    }

    @Test
    fun `empty sampler has no fabricated memory measurement`() {
        assertEquals(MemoryPeakSnapshot(), MemoryPeakAccumulator().snapshot())
    }
}
