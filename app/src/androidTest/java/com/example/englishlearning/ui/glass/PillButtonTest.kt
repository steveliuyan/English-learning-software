package com.example.englishlearning.ui.glass

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import org.junit.Assert.assertTrue
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
}
