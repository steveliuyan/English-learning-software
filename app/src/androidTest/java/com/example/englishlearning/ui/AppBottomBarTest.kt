package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.IllustratedIconTag
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import kotlin.math.abs
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
        // 设置不住底导：它是「我的」里的子页（2026-11-02 用户裁定）。
        composeRule.onNodeWithText("设置").assertDoesNotExist()
    }

    @Test fun every_slot_is_reachable_by_its_content_description() {
        composeRule.setContent { AppBottomBar(selected = AppTab.LEARNING, onSelect = {}) }
        AppTab.entries.forEach { tab ->
            composeRule.onNodeWithContentDescription(tab.contentDescription).assertExists()
        }
    }

    /**
     * 每个页签的图形腿（2026-10-31 用户评审 ①：选中/未选中必须是同一枚插画；判据随形改，不放宽）。
     *
     * 旧判据是「选中槽恰一枚插画位图、其余槽一枚都没有」——那正是用户看到的那版行为：未选中还是旧线框，
     * 选中才变成插画。新判据反过来：**每槽在两种状态下都恰一枚插画**（同一枚位图），选取只改两件事——
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

    /**
     * QA3 A2.7（用户 2026-11-08 拍板「全部都做」）：字体大小顶档 3.0 下，四枚格子必须**仍装在栏这一条里**，
     * 并且四格同高同沿。
     *
     * 旧形实测（走查册 §A2.7，图证 `fs300-ai-01-hub.png`）：「AI 助学」格 bounds
     * `[540,1442][810,1794]`（352px）而其余三格 `[*,1505][*,1731]`（226px）——同一排四格不同高，
     * 折行那一格的图标顶到其余三格之上、文案第二行落到其余三格之下，读起来像「画到栏外压住页面内容」。
     * `uiautomator` 的 bounds 是与父容器的可视交集，看不见真裁切，所以这条护栏必须在 Compose 侧量**未裁剪**矩形。
     *
     * 三条判据（对「格高怎么来的」中立：格高随字号、标签按档收成一行、内在量取短了，各有一种红样）：
     *  ① 四格**上沿**必须一致——中心对齐的那一格长过其余时，上沿必然不齐（旧形实测 AI 格 1442、其余 1505）；
     *  ② 每格的药片与标签必须整个落在本格矩形内——行高若被取短（`IntrinsicSize.Max` 的内在量小于真实内容高），
     *     图标就从格顶之上浮出、压住页面内容；
     *  ③ 四枚药片必须落在**同一条横线**上——这一条才对应眼睛看到的对齐；只有 ①② 时，格内各自居中仍会让
     *     两行标签那一格的图标顶到别人头上（2026-11-08 第一版整改正是这样过了 ①②，设备取色 AI 药片 1442、其余 1515）。
     * 0.5dp 的容差只用来吃掉亚像素取整，不用来放行真越界（旧形上下沿差 13dp、字形线差 24dp）。
     *
     * 2026-11-08 踩过的坑，写死在这里：**必须套 [MintGlassTheme]**。不套主题时 `labelSmall` 走 M3 默认排印，
     * 套上主题才走 `appTypography()`；同一份顶档栏，不套主题量到格高 130.286dp、套上主题 134.095dp
     * （= 设备 352px÷2.625，与 dump 对上）。差 4dp 看着不大，但这条护栏量的正是「内容高 vs 行高」的余量，
     * 用错的排印量出来的绿不代表设备——第一轮这条判据在设备上仍有观感差却报 OK，根因就在这里。
     */
    @Test
    fun slotsStayInsideTheBarAtTopFontScale() {
        assertSlotsStayInsideTheBar(width = null)
    }

    /**
     * A2.7 的小屏一侧：320dp 是 Spec 05 钉住的最小走查宽度（`docs/verification/visual-walkthrough-2026-11-08.md`
     * 的 320dp 组），这一档每格只有 80dp 宽，「AI 助学」在顶档折成两行时比整机宽度更容易顶出栏沿。
     * 与上一条分成两个方法而不是在同一方法里两次 `setContent`：同一 Compose 测试内二次 `setContent`
     * 在设备侧必崩（QA 手册 §2 记过），拆开发才跑得动。
     */
    @Test
    fun slotsStayInsideTheBarAtTopFontScaleOnNarrowScreen() {
        assertSlotsStayInsideTheBar(width = 320.dp)
    }

    private fun assertSlotsStayInsideTheBar(width: Dp?) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 3f)) {
                MintGlassTheme(themeMode = ThemeMode.Light) {
                    if (width == null) {
                        AppBottomBar(selected = AppTab.AI, onSelect = {})
                    } else {
                        Box(modifier = Modifier.width(width)) {
                            AppBottomBar(selected = AppTab.AI, onSelect = {})
                        }
                    }
                }
            }
        }

        val boxes = AppTab.entries.associateWith { tab ->
            composeRule.onNodeWithTag("app_tab_${tab.name.lowercase()}", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        }
        val reference = boxes.getValue(AppTab.LEARNING)
        AppTab.entries.forEach { tab ->
            val box = boxes.getValue(tab)
            assertTrue(
                "fontScale=3.0（宽度=${width ?: "整幅"}）下「${tab.label}」格与「${AppTab.LEARNING.label}」格上沿不齐：" +
                    "本格=$box 基准=$reference ——上沿不齐说明这一格是被中心对齐顶出栏沿的那一格（A2.2/A2.7 同一根因）",
                abs(box.top.value - reference.top.value) < 0.5f,
            )
        }
        val glyphBoxes = AppTab.entries.associateWith { tab ->
            composeRule.onNodeWithTag("app_tab_glyph_${tab.name.lowercase()}", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        }
        AppTab.entries.forEach { tab ->
            val slotBox = boxes.getValue(tab)
            val glyphBox = glyphBoxes.getValue(tab)
            val labelBox = composeRule.onNodeWithText(tab.label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(
                "fontScale=3.0（宽度=${width ?: "整幅"}）下「${tab.label}」格的内容画出格外：" +
                    "药片越顶 ${slotBox.top.value - glyphBox.top.value}dp、" +
                    "标签越底 ${labelBox.bottom.value - slotBox.bottom.value}dp（格=$slotBox 药片=$glyphBox 标签=$labelBox）" +
                    "——行高取短了，图标会浮到栏沿之上压住页面内容",
                glyphBox.top.value >= slotBox.top.value - 0.5f && labelBox.bottom.value <= slotBox.bottom.value + 0.5f,
            )
        }
        // ③ 四枚药片必须落在同一条横线上——这一条才是用户眼睛看到的「对齐」。
        // ①只保证四格的**矩形**等高：矩形等高、格内各自居中时，两行标签那一格的图标仍会被顶到别人之上
        // （2026-11-08 第一版整改正是只有 ① 没有 ③，四格同为 352px 却各排各的：AI 药片上沿 1442、其余 1515）。
        val glyphReference = glyphBoxes.getValue(AppTab.LEARNING)
        AppTab.entries.forEach { tab ->
            val glyphBox = glyphBoxes.getValue(tab)
            assertTrue(
                "fontScale=3.0（宽度=${width ?: "整幅"}）下「${tab.label}」的字形与「${AppTab.LEARNING.label}」不在同一条横线：" +
                    "本格药片=$glyphBox 基准=$glyphReference（上沿差 ${glyphBox.top.value - glyphReference.top.value}dp）" +
                    "——格高一致但格内居中，长标签那一格照样把图标顶到别人头上",
                abs(glyphBox.top.value - glyphReference.top.value) < 0.5f,
            )
        }
    }
}
