package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.IllustratedIconTag
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
        listOf("学习", "阅读", "AI 助学", "我的").forEach { label ->
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
     * 四个页签的图形腿（2026-10-31 用户评审 ①：选中/未选中必须是同一枚插画；判据随形改，不放宽）。
     *
     * 旧判据是「选中槽恰一枚插画位图、另外三槽一枚都没有」——那正是用户看到的那版行为：未选中还是旧线框，
     * 选中才变成插画。新判据反过来：**四槽在两种状态下都恰一枚插画**（同一枚位图），选取只改两件事——
     * 有没有药片底、压不压 [TabGlyphIdleAlpha] 半透明。结构（都在）走语义树，视觉（淡出）走像素亮度，
     * 两半各管一件事，见下一条用例。墨色对比度归 JVM 契约 `AppBottomBarContrastTest`，本类不抄色值。
     */
    @Test fun every_slot_draws_the_same_illustration_in_both_states() {
        var selectedTab by mutableStateOf(AppTab.LEARNING)
        composeRule.setContent { AppBottomBar(selected = selectedTab, onSelect = {}) }
        AppTab.entries.forEach { tab ->
            composeRule.runOnIdle { selectedTab = tab }
            composeRule.waitForIdle()
            AppTab.entries.forEach { slot ->
                assertEquals(
                    "${slot.name} 槽丢了插画位图（当前选中 ${tab.name}）：两档必须画同一枚插画，" +
                        "未选中档不许退回 24×24 线框（用户评审 ①）",
                    1,
                    illustratedCountInSlot(slot),
                )
            }
        }
    }

    /**
     * 未选中档必须是选中档的「淡一档孪生」：同一枚位图、压 `TabGlyphIdleAlpha`（0.5）半透明。
     *
     * 为什么用像素亮度而不是语义：alpha 不进语义树，只有合成结果说得清。取同一格（学习）在两种状态下的
     * 位图均值亮度，浅底上压半透明必然更亮——这条同时证明「两档画的是同一格图形」（否则亮度差无从谈起）
     * 与「淡出真的生效」（不然两档完全一样，选取就只剩标签粗细可辨认）。
     */
    @Test fun the_unselected_glyph_is_the_dimmed_twin_of_the_selected_one() {
        var selectedTab by mutableStateOf(AppTab.LEARNING)
        composeRule.setContent { AppBottomBar(selected = selectedTab, onSelect = {}) }
        composeRule.waitForIdle()
        val selectedLuma = meanGlyphLuminance(AppTab.LEARNING)
        composeRule.runOnIdle { selectedTab = AppTab.AI }
        composeRule.waitForIdle()
        val idleLuma = meanGlyphLuminance(AppTab.LEARNING)
        assertTrue(
            "未选中档必须比选中档更淡（压 0.5 半透明）：选中 %.1f / 未选中 %.1f".format(selectedLuma, idleLuma),
            idleLuma > selectedLuma + 8.0,
        )
    }

    /** 该槽里那枚插画位图的平均亮度（0–255，只统计不透明像素）。 */
    private fun meanGlyphLuminance(tab: AppTab): Double {
        val pixels = composeRule.onAllNodes(
            hasTestTag(IllustratedIconTag) and hasAnyAncestor(hasTestTag("app_tab_${tab.name.lowercase()}")),
            useUnmergedTree = true,
        )[0].captureToImage().toPixelMap()
        var sum = 0.0
        var counted = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val pixel = pixels[x, y]
                if (pixel.alpha > 0.5f) {
                    sum += 0.2126 * pixel.red + 0.7152 * pixel.green + 0.0722 * pixel.blue
                    counted++
                }
            }
        }
        assertTrue("${tab.name} 槽的插画位图取不到任何不透明像素（取样面为空，判据会空转）", counted > 0)
        return sum / counted * 255.0
    }

    private fun illustratedCountInSlot(tab: AppTab): Int =
        composeRule.onAllNodes(
            hasTestTag(IllustratedIconTag) and hasAnyAncestor(hasTestTag("app_tab_${tab.name.lowercase()}")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size

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
