package com.example.englishlearning.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 批 2 Task 22-H（#36 `sUnavailable`）：新建屏的三条出口与「这一页不是失败态」。
 *
 * 两枚动作都必须真接到回调——原型这页的两枚键是导航，不是装饰；漏接一枚就是零假入口原则的反例。
 * 「重试」的缺席由第二条用例从渲染面钉住（源码侧的计数在 `Batch2EntryScreensContractTest`）。
 */
class UnavailableScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersThePrototypeCopyAndEveryExitCallsBack() {
        var backCalls = 0
        var learningCalls = 0
        var browseCalls = 0
        composeRule.setContent {
            UnavailableScreen(
                onBack = { backCalls++ },
                onGoLearning = { learningCalls++ },
                onBrowseFeatures = { browseCalls++ },
            )
        }
        composeRule.onNodeWithTag("unavailable_screen").assertExists()
        composeRule.onNodeWithText("页面不可用").assertExists()
        composeRule.onNodeWithText("这个页面在当前版本没有开放").assertExists()
        composeRule.onNodeWithContentDescription("回到今日学习").assertExists().performClick()
        composeRule.onNodeWithContentDescription("看看别的功能").assertExists().performClick()
        composeRule.onNodeWithContentDescription("返回").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, learningCalls)
        assertEquals(1, browseCalls)
        assertEquals(1, backCalls)
    }

    @Test
    fun theRouteFallbackIsNotAFailureStateSoItOffersNoRetry() {
        composeRule.setContent {
            UnavailableScreen(onBack = {}, onGoLearning = {}, onBrowseFeatures = {})
        }
        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }
}
