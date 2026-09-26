package com.example.englishlearning.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.glass.GlassOverlay
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassOverlayTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun hiddenByDefaultAndShowsOnVisible() {
        // 同一 Activity 只能 setContent 一次，用状态驱动 visible 切换
        val visible = mutableStateOf(false)
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                GlassOverlay(visible = visible.value, onDismiss = {}) {
                    Text("选项 A", modifier = Modifier.testTag("overlay_item_a"))
                }
            }
        }
        composeRule.onNodeWithText("选项 A").assertDoesNotExist()
        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithTag("overlay_item_a", useUnmergedTree = true).assertExists()
    }

    @Test fun tappingScrimDismisses() {
        var dismissed = false
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                GlassOverlay(visible = true, onDismiss = { dismissed = true }, modifier = Modifier.testTag("overlay_scrim")) {
                    Text("选项 A")
                }
            }
        }
        // 点击左上角远角，避开中央面板
        composeRule.onNodeWithTag("overlay_scrim", useUnmergedTree = true)
            .performTouchInput { click(Offset(10f, 10f)) }
        composeRule.waitForIdle()
        assertTrue(dismissed)
    }
}
