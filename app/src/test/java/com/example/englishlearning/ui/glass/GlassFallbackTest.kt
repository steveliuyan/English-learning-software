package com.example.englishlearning.ui.glass

import com.example.englishlearning.ui.components.glass.blurRadiusFor
import com.example.englishlearning.ui.components.glass.scrimAlphaFor
import kotlin.test.Test
import kotlin.test.assertEquals

class GlassFallbackTest {
    @Test fun api31PlusGetsWindowBlur() {
        assertEquals(60, blurRadiusFor(31))
        assertEquals(60, blurRadiusFor(33))
    }
    @Test fun api30AndBelowGetScrimFallback() {
        assertEquals(0, blurRadiusFor(30))
        assertEquals(0.45f, scrimAlphaFor(30))
        assertEquals(0.15f, scrimAlphaFor(31))
    }
}
