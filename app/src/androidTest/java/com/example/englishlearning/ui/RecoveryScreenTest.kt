package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F5-02 / SP-B 决策 §3：RecoveryScreen 呈现固定文案 + 唯一「重试」入口（testTag
 * `recovery_retry`），且页面上不得出现任何「清除/删除数据」按钮——可恢复错误不诱导销毁数据。
 */
@RunWith(AndroidJUnit4::class)
class RecoveryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsDecisionCopyAndRetriesExactlyOnce() {
        var retries = 0
        composeRule.setContent { RecoveryScreen(reason = "数据库结构迁移失败", onRetry = { retries++ }) }

        composeRule.onNodeWithText("数据库升级失败：数据库结构迁移失败。").assertIsDisplayed()
        composeRule
            .onNodeWithText("你的学习数据仍在设备上。可重试；如反复失败，请先用另一台设备确认导出留档。")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("recovery_retry").performClick()
        assertEquals("重试按钮应恰好触发一次回调", 1, retries)
    }

    @Test
    fun offersNoDataWipeEntryAnywhereOnTheScreen() {
        composeRule.setContent { RecoveryScreen(reason = "数据库文件无法打开", onRetry = {}) }

        for (label in listOf("清除数据", "删除数据", "清空数据", "重置")) {
            assertEquals(
                "可恢复页不得提供「$label」类销毁入口",
                0,
                composeRule.onAllNodes(hasText(label, substring = true)).fetchSemanticsNodes().size,
            )
        }
    }
}
