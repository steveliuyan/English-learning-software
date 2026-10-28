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
 * F5-02 / SP-B 决策 §3：RecoveryScreen 呈现固定文案 + 两条出路（`recovery_retry` 重试、
 * `recovery_export_archive` 导出本机留档），且页面上不得出现任何「清除/删除数据」按钮——
 * 可恢复错误不诱导销毁数据。
 *
 * 批 5 Task 8 同步改期望值（**改期望值不放宽判据**）：
 *  - 标题从「数据库升级失败：<原因>」改成「数据暂时打不开」，原始原因默认收起，
 *    由「详情」行展开——技术串不再排在用户第一眼；
 *  - 出路从「只有重试」变成「留档为主、重试为次」：本屏文案自己写着「先确认留档」，
 *    旧形态却没有对应入口（对账 1）。
 */
@RunWith(AndroidJUnit4::class)
class RecoveryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setRecoveryScreen(
        reason: String = "数据库结构迁移失败",
        onRetry: () -> Unit = {},
        onOpenBackupExport: () -> Unit = {},
    ) {
        composeRule.setContent {
            RecoveryScreen(
                reason = reason,
                onRetry = onRetry,
                onOpenBackupExport = onOpenBackupExport,
            )
        }
    }

    @Test
    fun showsPlainLanguageHeadlineAndKeepsTheReassuranceSentence() {
        setRecoveryScreen()

        composeRule.onNodeWithText("数据暂时打不开").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "你的学习数据仍在设备上，只是这次没能读出来。可以先重试；" +
                    "如果反复失败，先导出一份本机留档，再决定下一步。",
            )
            .assertIsDisplayed()
        // 技术串不再上第一眼：标题里不许出现引擎内部词汇。
        for (word in listOf("SQLite", "Exception", "database")) {
            assertEquals(
                "标题区不得出现技术串「$word」",
                0,
                composeRule.onAllNodes(hasText(word, substring = true)).fetchSemanticsNodes().size,
            )
        }
    }

    @Test
    fun retriesExactlyOnce() {
        var retries = 0
        setRecoveryScreen(onRetry = { retries++ })

        composeRule.onNodeWithTag("recovery_retry").performClick()
        assertEquals("重试按钮应恰好触发一次回调", 1, retries)
    }

    @Test
    fun opensTheArchiveExportExactlyOnce() {
        var exports = 0
        setRecoveryScreen(onOpenBackupExport = { exports++ })

        composeRule.onNodeWithTag("recovery_export_archive").assertIsDisplayed().performClick()
        assertEquals("「导出本机留档」应恰好触发一次回调（对账 1）", 1, exports)
    }

    @Test
    fun theRawReasonIsFoldedBehindTheDetailsRow() {
        setRecoveryScreen(reason = "数据库文件无法打开")

        // 默认收起：原始原因不出现在无障碍树里。
        assertEquals(
            "原始原因默认必须收起",
            0,
            composeRule.onAllNodes(hasText("数据库文件无法打开", substring = true)).fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithText("详情").performClick()
        composeRule.onNodeWithText("数据库文件无法打开").assertIsDisplayed()
        composeRule.onNodeWithText("收起").assertIsDisplayed()
    }

    @Test
    fun offersNoDataWipeEntryAnywhereOnTheScreen() {
        setRecoveryScreen(reason = "数据库文件无法打开")

        for (label in listOf("清除数据", "删除数据", "清空数据", "重置")) {
            assertEquals(
                "可恢复页不得提供「$label」类销毁入口",
                0,
                composeRule.onAllNodes(hasText(label, substring = true)).fetchSemanticsNodes().size,
            )
        }
    }
}
