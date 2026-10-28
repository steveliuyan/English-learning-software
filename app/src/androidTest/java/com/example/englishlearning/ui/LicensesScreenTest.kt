package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
        composeRule.setContent {
            LicensesScreen(markdown = sampleLedger, onBack = { backCalls++ }, onRetry = {})
        }
        // 页名与设置入口同一句（spec §5「入口名与页名统一」），旧页名反向钉住不存在。
        composeRule.onNodeWithText("开源许可与隐私说明").assertIsDisplayed()
        composeRule.onNodeWithText("关于与开源许可").assertDoesNotExist()
        // 2026-10-25 简化批删掉了「共收录 N 项第三方资源」计数行：下面的台账本身就是完整名单，
        // 计数既不帮用户核对（条目会随台账滚动出现）也不帮决策。反向钉住删除结果。
        composeRule.onNodeWithTag("licenses_entry_count").assertDoesNotExist()
        composeRule.onNodeWithText("共收录", substring = true).assertDoesNotExist()
        // 隐私说明是 `LazyColumn` 的首条 item：先取它，再往下滚台账——2× 字号下首条也会被滚出组合，
        // 顺序反了会拿到「节点不存在」的假红。断言本身逐字未改。
        thirdPartyPrivacyStatements().forEach { statement ->
            composeRule.onNodeWithText(statement, substring = true).assertExists()
        }
        composeRule.onNodeWithTag("licenses_ledger")
            .performScrollToNode(hasTestTag("license_entry-lib-a"))
        composeRule.onNodeWithTag("license_entry-lib-a").assertExists()
        composeRule.onNodeWithTag("licenses_ledger")
            .performScrollToNode(hasTestTag("license_entry-lib-b"))
        composeRule.onNodeWithTag("license_entry-lib-b").performClick()
        composeRule.onNodeWithText("用途：测试用途").assertExists()
        // 批 5 Task 3：返回位归共享页头（可及名「返回」），旧锚 `licenses_back` 退役。
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backCalls)
        composeRule.onNodeWithTag("licenses_back").assertDoesNotExist()
    }

    /**
     * 空态如实说不可用，且「重新读取」必须真的回灌一层（spec §5「空态带重试」）。
     *
     * 断言落点是「点一次 = 宿主收到一次 `onRetry`」——屏侧不自己重读（assets 在宿主手里），
     * 所以这里不检查文本变化，只检查回调有没有接上；真正的重读由 `AppScreen` 的
     * `licensesReload` 键让 `remember` 失效（`LicensesLayoutContractTest` 第 3 条钉住形参无默认实现）。
     */
    @Test
    fun showsHonestUnavailableStateAndTheRetryIsWired() {
        var retries = 0
        composeRule.setContent {
            LicensesScreen(markdown = "", onBack = {}, onRetry = { retries++ })
        }
        composeRule.onNodeWithTag("licenses_unavailable").assertIsDisplayed()
        // 空态不该顺带渲染台账与隐私段：没有任何条目却摆一段隐私说明是自相矛盾的信息。
        composeRule.onNodeWithTag("licenses_privacy_statement").assertDoesNotExist()
        composeRule.onNodeWithTag("licenses_retry").assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }

    /**
     * F5-03 的 48dp 触控硬下限：本页返回位在批 5 Task 3 之后由共享页头承担
     * （`AppScreenHeader` 的 48dp 可点 Box + 40dp 视觉容器），改按可及名「返回」定位。
     *
     * 页头在根 `Column` 的首行、**不在滚动容器内**（滚动的是下方台账 `LazyColumn`），
     * 节点常驻视口，所以不需要 `performScrollTo()` 锚点——`assertIsDisplayed()` 钉住矩形未被裁剪后，
     * brief 口径与 `getUnclippedBoundsInRoot` 口径各跑一遍。
     */
    @Test
    fun backLinkMeetsTheMinimumTouchTarget() {
        composeRule.setContent {
            LicensesScreen(markdown = sampleLedger, onBack = {}, onRetry = {})
        }

        val back = composeRule.onNodeWithContentDescription("返回")
        back.assertIsDisplayed()
        back.assertHeightIsAtLeast(48.dp)
        back.assertWidthIsAtLeast(48.dp)

        val bounds = back.getUnclippedBoundsInRoot()
        assertTrue("返回位的触控目标应至少 48dp 高，实际=$bounds", bounds.bottom - bounds.top >= 48.dp)
        assertTrue("返回位的触控目标应至少 48dp 宽，实际=$bounds", bounds.right - bounds.left >= 48.dp)
    }
}
