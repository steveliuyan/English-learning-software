package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
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

    /**
     * 语义订正（修复轮 2 / 复评 C，判据逐字不动）：本用例的像素取样实际只锁得住**字形腿**——
     * 选中槽里 `InlineIconBadge` 药片上的域 `deep` 单层描边必须仍近 `DomainColors.AiSpeech.deep`（nearest ≤ 18.0）。
     * 轮 1 起浅套选中标签墨按 R-14.4 刻意不沿用 brand `deep` 而取 `onMint`，名字里的「同一枚 calm brand 色」
     * 对标签墨而言已不成立；标签墨腿由 JVM 契约 `AppBottomBarContrastTest` 按真实底复算负责。
     * 设备侧「标签墨取样」缺口（整槽截图无法把标签像素从字形像素里分离出来单测）结转 Task 18，
     * 本席不新增取样、不放宽 `nearest ≤ 18.0`，也不删任何断言。
     */
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

    /**
     * brief「TalkBack 朗读四 tab」的验收形态：模拟器上没有 TalkBack 引擎可赌，改为直接读语义树——
     * 每个 slot 节点必须同时给出 Tab 角色、可读的 contentDescription 与双值 stateDescription，
     * 这正是 TalkBack 合成播报所需的全部输入；选中态轮转一遍，四枚 slot 在两种状态下逐一核验。
     */
    @Test fun talk_back_can_read_every_slot_as_a_tab_with_its_selection_state() {
        var selectedTab by mutableStateOf(AppTab.LEARNING)
        composeRule.setContent { AppBottomBar(selected = selectedTab, onSelect = {}) }
        AppTab.entries.forEach { focused ->
            composeRule.runOnIdle { selectedTab = focused }
            AppTab.entries.forEach { tab ->
                val config = composeRule
                    .onNodeWithTag("app_tab_${tab.name.lowercase()}", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .config
                assertTrue("${tab.name} 缺 Tab 角色", config.contains(SemanticsProperties.Role))
                assertEquals(
                    "${tab.name} 的角色必须是 Tab（读屏才会念「标签页」）",
                    Role.Tab,
                    config[SemanticsProperties.Role],
                )
                assertTrue("${tab.name} 缺 contentDescription，读屏无从念出目的地", config.contains(SemanticsProperties.ContentDescription))
                assertEquals(
                    "${tab.name} 的播报文本应恰为枚举里的中文文案",
                    listOf(tab.contentDescription),
                    config[SemanticsProperties.ContentDescription],
                )
                assertTrue("${tab.name} 缺 stateDescription，选中/未选中进不了播报", config.contains(SemanticsProperties.StateDescription))
                assertEquals(
                    "${tab.name} 在选中 tab=${focused.name} 时的状态播报",
                    if (tab == focused) "已选中" else "未选中",
                    config[SemanticsProperties.StateDescription],
                )
            }
        }
    }

    /** 底导走查：逐枚点过去，选中态跟随点击迁移，四枚 slot 与四枚标签始终成齐（duotone 重写后整栏读得通）。 */
    @Test fun walking_the_bar_moves_the_selection_and_keeps_every_slot_and_label() {
        var selectedTab by mutableStateOf(AppTab.LEARNING)
        composeRule.setContent { AppBottomBar(selected = selectedTab, onSelect = { selectedTab = it }) }
        AppTab.entries.forEach { tab ->
            composeRule.onNodeWithTag("app_tab_${tab.name.lowercase()}").performClick()
            composeRule.waitForIdle()
            assertEquals(tab, selectedTab)
            AppTab.entries.forEach { slot ->
                composeRule.onNodeWithTag("app_tab_${slot.name.lowercase()}").assertExists().assertHasClickAction()
            }
            AppTab.entries.forEach { label ->
                composeRule.onNodeWithText(label.label).assertExists()
            }
            val stateOf = composeRule
                .onNodeWithTag("app_tab_${tab.name.lowercase()}", useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            assertEquals("点过 $tab 之后它必须自己念出已选中", "已选中", stateOf)
        }
    }
}
