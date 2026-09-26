package com.example.englishlearning.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class AppMotionTest {
    @Test fun zeroScaleMeansZeroDuration() {
        assertEquals(0, AppMotion.durationMs(base = 250, scale = 0f))
    }
    @Test fun fullScaleKeepsBase() {
        assertEquals(250, AppMotion.durationMs(base = 250, scale = 1f))
    }
    @Test fun partialScaleScalesLinearly() {
        assertEquals(125, AppMotion.durationMs(base = 250, scale = 0.5f))
    }
}
