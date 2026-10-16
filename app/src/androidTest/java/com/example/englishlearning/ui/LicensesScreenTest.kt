package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.englishlearning.notices.thirdPartyPrivacyStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /**
     * F5-03 / Task 10（B2 文字链）：许可页「‹」返回的触控目标必须 ≥48dp 见方。
     * 现状只有 `headlineSmall` 的一个字（实测约 20×34dp），是本页唯一的返回路径。
     *
     * 返回链在根 Column 的首行（**不在滚动容器内**，滚动的是下方台账列表），节点常驻视口，
     * 所以这里不需要 `performScrollTo()` 锚点——用 `assertIsDisplayed()` 钉住矩形未被裁剪后，
     * brief 口径与 `getUnclippedBoundsInRoot` 口径各跑一遍。
     */
    @Test
    fun backLinkMeetsTheMinimumTouchTarget() {
        composeRule.setContent { LicensesScreen(markdown = sampleLedger, onBack = {}) }

        composeRule.onNodeWithTag("licenses_back").assertIsDisplayed()
        composeRule.onNodeWithTag("licenses_back").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("licenses_back").assertWidthIsAtLeast(48.dp)

        val bounds = composeRule.onNodeWithTag("licenses_back").getUnclippedBoundsInRoot()
        assertTrue("licenses_back 的触控目标应至少 48dp 高，实际=$bounds", bounds.bottom - bounds.top >= 48.dp)
        assertTrue("licenses_back 的触控目标应至少 48dp 宽，实际=$bounds", bounds.right - bounds.left >= 48.dp)
    }
}
