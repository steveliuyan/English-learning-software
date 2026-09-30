package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.DomainColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppBottomBarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun renders_the_four_slots_the_user_asked_for() {
        composeRule.setContent { AppBottomBar(selected = AppTab.LEARNING, onSelect = {}) }
        AppTab.entries.forEach { tab ->
            composeRule
                .onNodeWithTag("app_tab_${tab.name.lowercase()}")
                .assertExists()
                .assertHasClickAction()
        }
    }

    @Test fun every_slot_carries_its_label_so_the_bar_reads_by_itself() {
        composeRule.setContent { AppBottomBar(selected = AppTab.LEARNING, onSelect = {}) }
        listOf("学习", "阅读", "AI 学", "设置").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
    }

    @Test fun every_slot_is_reachable_by_its_content_description() {
        composeRule.setContent { AppBottomBar(selected = AppTab.LEARNING, onSelect = {}) }
        AppTab.entries.forEach { tab ->
            composeRule.onNodeWithContentDescription(tab.contentDescription).assertExists()
        }
    }

    @Test fun each_selected_slot_uses_the_same_calm_brand_color() {
        var selectedTab by mutableStateOf(AppTab.LEARNING)
        composeRule.setContent { AppBottomBar(selected = selectedTab, onSelect = {}) }
        AppTab.entries.forEach { tab ->
            composeRule.runOnIdle { selectedTab = tab }
            assertSelectedSlotUsesColor(tab, DomainColors.AiSpeech.deep.toArgb())
        }
    }

    private fun assertSelectedSlotUsesColor(selectedTab: AppTab, expectedColor: Int) {
        val selectedNode = composeRule.onNodeWithTag("app_tab_${selectedTab.name.lowercase()}")
        val bitmap = selectedNode.captureToImage().asAndroidBitmap()
        val nearest = (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> colorDistance(bitmap.getPixel(x, y), expectedColor) }
        }.minOrNull() ?: Double.POSITIVE_INFINITY
        assertTrue("${selectedTab.name} should use its calm brand color; nearest distance=$nearest", nearest <= 18.0)
    }

    private fun colorDistance(actual: Int, expected: Int): Double {
        val dr = ((actual shr 16 and 0xFF) - (expected shr 16 and 0xFF)).toDouble()
        val dg = ((actual shr 8 and 0xFF) - (expected shr 8 and 0xFF)).toDouble()
        val db = ((actual and 0xFF) - (expected and 0xFF)).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }

    @Test fun tapping_the_ai_slot_reports_the_ai_tab() {
        var received: AppTab? = null
        composeRule.setContent { AppBottomBar(selected = AppTab.LEARNING, onSelect = { received = it }) }
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()
        assertEquals(AppTab.AI, received)
    }

    @Test fun tapping_each_slot_reports_its_own_tab() {
        val received = mutableListOf<AppTab>()
        composeRule.setContent { AppBottomBar(selected = AppTab.LEARNING, onSelect = { received += it }) }
        AppTab.entries.forEach { tab ->
            composeRule.onNodeWithTag("app_tab_${tab.name.lowercase()}").performClick()
        }
        composeRule.waitForIdle()
        assertEquals(AppTab.entries.toList(), received)
    }
}
