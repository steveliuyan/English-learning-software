package com.example.englishlearning.ui.glass

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintStart
import com.example.englishlearning.ui.theme.DomainColors
import androidx.compose.ui.graphics.toArgb
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PillButtonTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun clickFiresCallbackAndTagsArePresent() {
        var clicked = false
        composeRule.setContent {
            PillButton("试听", onClick = { clicked = true }, testTag = "glass_btn", contentDescription = "试听发音")
        }
        composeRule.onNodeWithTag("glass_btn").assertExists()
        composeRule.onNodeWithContentDescription("试听发音").assertExists()
        composeRule.onNodeWithTag("glass_btn").performClick()
        assertTrue(clicked)
    }

    @Test fun disabledButtonDoesNotFire() {
        var clicked = false
        composeRule.setContent {
            PillButton("试听", onClick = { clicked = true }, enabled = false, testTag = "glass_btn")
        }
        composeRule.onNodeWithTag("glass_btn").performClick()
        composeRule.waitForIdle()
        assertTrue(!clicked)
    }

    @Test fun enabledPrimaryUsesAppleMintGradientAndReadableDarkText() {
        composeRule.setContent { PillButton("开始学习", onClick = {}, testTag = "glass_btn") }
        val bitmap = composeRule.onNodeWithTag("glass_btn").captureToImage().asAndroidBitmap()
        val pixels = (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> bitmap.getPixel(x, y) }
        }
        assertTrue("primary should render mint gradient start", pixels.minOf { colorDistance(it, AppleMintStart.toArgb()) } <= 18.0)
        assertTrue("primary should render softened mint end", pixels.minOf { colorDistance(it, 0xFF65D3B9.toInt()) } <= 18.0)
        assertTrue("primary should render deep teal text", pixels.minOf { colorDistance(it, 0xFF174C44.toInt()) } <= 18.0)
    }

    private fun colorDistance(actual: Int, expected: Int): Double {
        val dr = ((actual shr 16 and 0xFF) - (expected shr 16 and 0xFF)).toDouble()
        val dg = ((actual shr 8 and 0xFF) - (expected shr 8 and 0xFF)).toDouble()
        val db = ((actual and 0xFF) - (expected and 0xFF)).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }

    @Test fun disabledPrimaryDoesNotRenderSaturatedAccent() {
        composeRule.setContent { PillButton("试听", onClick = {}, enabled = false, testTag = "glass_btn") }
        val bitmap = composeRule.onNodeWithTag("glass_btn").captureToImage().asAndroidBitmap()
        val accent = DomainColors.AiSpeech.base.toArgb()
        val saturated = (0 until bitmap.width).any { x ->
            (0 until bitmap.height).any { y -> bitmap.getPixel(x, y) == accent }
        }
        assertFalse("disabled primary must not show saturated mint", saturated)
        assertTrue("disabled primary should have neutral fill", (0 until bitmap.width).any { x ->
            (0 until bitmap.height).any { y -> bitmap.getPixel(x, y) == 0xFFE5E5EA.toInt() }
        })
    }
}
