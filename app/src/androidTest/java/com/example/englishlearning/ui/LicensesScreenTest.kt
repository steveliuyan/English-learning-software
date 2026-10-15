package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.englishlearning.notices.thirdPartyPrivacyStatements
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LicensesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sampleLedger = """
        # 说明前言
        ## lib-a
        - 名称: Library A
        - 版本: 1.2.3
        - 许可证: Apache-2.0
        - 用途: 测试用途
        - 数据流: 仅在设备内
        - NOTICE 位置: 本文件
        - 替代方案: 无
        - 商业分发结论: 可商业分发
        ## lib-b
        - 名称: Library B
        - 版本: 4.5.6
        - 许可证: MIT
        - 用途: 测试用途
        - 数据流: 仅在设备内
        - NOTICE 位置: 本文件
        - 替代方案: 无
        - 商业分发结论: 可商业分发
    """.trimIndent()

    @Test
    fun rendersEntriesCountPrivacyStatementsAndBack() {
        var backCalls = 0
        composeRule.setContent { LicensesScreen(markdown = sampleLedger, onBack = { backCalls++ }) }
        composeRule.onNodeWithText("关于与开源许可").assertIsDisplayed()
        composeRule.onNodeWithTag("licenses_entry_count").assertIsDisplayed()
        composeRule.onNodeWithText("共收录 2 项第三方资源").assertIsDisplayed()
        composeRule.onNodeWithTag("license_entry-lib-a").assertExists()
        composeRule.onNodeWithTag("license_entry-lib-b").performScrollTo().performClick()
        composeRule.onNodeWithText("用途：测试用途").assertExists()
        thirdPartyPrivacyStatements().forEach { statement ->
            composeRule.onNodeWithText(statement, substring = true).assertExists()
        }
        composeRule.onNodeWithTag("licenses_back").performClick()
        assertEquals(1, backCalls)
    }

    @Test
    fun showsHonestUnavailableStateWhenLedgerMissing() {
        composeRule.setContent { LicensesScreen(markdown = "", onBack = {}) }
        composeRule.onNodeWithTag("licenses_unavailable").assertIsDisplayed()
    }
}
