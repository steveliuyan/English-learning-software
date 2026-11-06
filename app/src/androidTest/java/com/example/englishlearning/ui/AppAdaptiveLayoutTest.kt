package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批 B 自适应窗口的设备侧护栏（2026-11-06；口径见 `docs/decisions/2026-11-06-adaptive-window-layout.md`）。
 *
 * 本类钉三件「只能在真机上量」的事：
 *  1. **底栏与侧栏对 TalkBack 是同一句话**——同一份断言表分别喂给两枚组件
 *     （testTag / 朗读标签 / `Role.Tab` / 选中态播报 / 标签文字），任何一枚单独改语义即红。
 *     JVM 侧的 `AppWindowLayoutContractTest` 已在源码字面级比对过同一组锚点，这里补的是
 *     「合成后的语义树真是这样」——源码同形不等于运行时同形（合并语义、`useUnmergedTree` 口径都能差）。
 *  2. **hero 降级真的改了字级**：三档 provider 加一枚非 hero 参照头同屏渲染，
 *     字级从 `TextLayoutResult.layoutInput.style.fontSize` 现读（javap 核验：`TextLayoutInput.getStyle()`
 *     是 public），紧凑＝中等 > 宽，且宽档与参照头逐字相等。
 *  3. **宿主缺席时的回落**：没有 provider 时 `currentAppWindowLayout()` 按 `LocalConfiguration` 现算，
 *     与同一份配置直接分档的结果一致——这条同时证明 `null` 哨兵被处理了（没处理就是崩溃或空档名）。
 *
 * 本类**不**声称能证的事：「≥600dp 窗口里宿主真的换成侧栏、底栏不在场」需要真宽窗口，
 * `createComposeRule()` 的 ComponentActivity 宿主跟着设备走，测试自己改不了窗口。
 * 那一半由批 B-5 的**两档**取证覆盖（决策 §10.4：手机横屏形态随 §10.2 退场，不再当真 Medium 床）：
 * `wm size` 造出紧凑（手机竖屏 411dp）与宽（平板形 `wm size 2560x1600` + `wm density 320` → 1080dp / sw=800dp）
 * 各复跑 `AppScreenTest` 同一批类并留末行原文，外加截图目视；中等档只由本类的合成 provider
 * 与 `AppWindowLayoutContractTest` 的穷举分档钉。源码侧的挂载条件由 `AppWindowLayoutContractTest` 钉。
 *
 * 沿用本包先例：每枚用例只调一次 `setContent`（真机同宿主二次 `setContent` 必崩），
 * 用例之间不复用宿主；组件级宿主没有可滚动祖先，故不写 `performScrollTo()`。
 */
@RunWith(AndroidJUnit4::class)
class AppAdaptiveLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    // ---------- 1. 两枚导航 = 同一套语义，只换摆法 ----------

    /**
     * 一份断言表喂两枚组件：逐格查四件（tag 在场且可点、朗读标签取 `AppTab.contentDescription`、
     * `Role.Tab`、选中态播报随 `selected` 移动），并确认标签文字腿也在场。
     */
    private fun assertNavigationSemantics(selected: AppTab, host: @Composable () -> Unit) {
        composeRule.setContent { MintGlassTheme { host() } }
        AppTab.entries.forEach { tab ->
            val tag = "app_tab_${tab.name.lowercase()}"
            val node = composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHasClickAction()
                .fetchSemanticsNode()
            assertEquals(
                "$tag 的朗读标签与 AppTab.contentDescription 脱钩",
                tab.contentDescription,
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString(),
            )
            assertEquals("$tag 的语义 Role 不是 Tab", Role.Tab, if (node.config.contains(SemanticsProperties.Role)) node.config[SemanticsProperties.Role] else null)
            assertEquals(
                "$tag 的选中态播报错了（本宿主选中 ${selected.name}）",
                if (tab == selected) "已选中" else "未选中",
                node.config.getOrElse(SemanticsProperties.StateDescription) { "" },
            )
            composeRule.onNodeWithText(tab.label).assertIsDisplayed()
        }
    }

    @Test
    fun the_bottom_bar_exposes_four_slots_with_tab_semantics() {
        assertNavigationSemantics(selected = AppTab.AI) {
            AppBottomBar(selected = AppTab.AI, onSelect = {})
        }
    }

    @Test
    fun the_side_nav_exposes_the_same_four_slots_with_the_same_semantics() {
        assertNavigationSemantics(selected = AppTab.AI) {
            AppSideNav(selected = AppTab.AI, onSelect = {})
        }
    }

    /**
     * 选中态播报必须跟着 `selected` 移动，而不是写死在第一格：
     * 换一枚选中档再跑同一张表，两格（AI 与 我的）的期望值同时翻转。
     */
    @Test
    fun the_rail_moves_the_selected_announcement_with_the_selection() {
        assertNavigationSemantics(selected = AppTab.PROFILE) {
            AppSideNav(selected = AppTab.PROFILE, onSelect = {})
        }
    }

    /** 侧栏每一格都得是真正的触控目标：F5-03 的 48dp 下限在换摆法之后仍然逐格成立。 */
    @Test
    fun every_rail_slot_keeps_the_48dp_touch_floor() {
        composeRule.setContent { MintGlassTheme { AppSideNav(selected = AppTab.LEARNING, onSelect = {}) } }
        AppTab.entries.forEach { tab ->
            val node = composeRule.onNodeWithTag("app_tab_${tab.name.lowercase()}")
            node.assertHeightIsAtLeast(48.dp)
            node.assertWidthIsAtLeast(48.dp)
        }
    }

    /** 侧栏点下去要真的报那一格：换摆法不换行为，四格各点一次按顺序回报。 */
    @Test
    fun tapping_each_rail_slot_reports_that_tab() {
        val received = mutableListOf<AppTab>()
        composeRule.setContent {
            MintGlassTheme { AppSideNav(selected = AppTab.LEARNING, onSelect = { received += it }) }
        }
        AppTab.entries.forEach { tab ->
            composeRule.onNodeWithTag("app_tab_${tab.name.lowercase()}").performClick()
        }
        assertEquals(AppTab.entries.toList(), received)
    }

    // ---------- 2. hero 降级改变字级 ----------

    /** 从语义树取 `GetTextLayoutResult`，现读该节点实际用的 TextStyle 字号（不抄 AppType 数值）。 */
    private fun titleFontSize(title: String): TextUnit {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        return layout.layoutInput.style.fontSize
    }

    /**
     * 钉的破坏：「宽窗降 hero」只写在 KDoc 里，或降级判定读错了档位。
     *
     * 三档 provider 各渲染一枚 hero 页头，再加一枚**紧凑档非 hero** 参照头：
     *  - 紧凑 == 中等：中等档（平板竖屏 / 折叠内屏，sw ≥ 600dp）不该被降级（决策 §6；
     *    手机横屏那档自 §10.2 起在产品侧关闭，这里钉的是**分档规则**本身，不是那个窗口形态）；
     *  - 宽 < 紧凑：平板/折叠展开必须降；
     *  - 宽 == 参照：降到的那一档就是普通页头本身，不是第三套字级。
     */
    @Test
    fun the_hero_title_shrinks_only_in_the_wide_window() {
        val compactTitle = "自适应取证·紧凑档"
        val mediumTitle = "自适应取证·中等档"
        val wideTitle = "自适应取证·宽档"
        val plainTitle = "自适应取证·参照非hero"
        composeRule.setContent {
            MintGlassTheme {
                Column(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        AppWindowLayout.Compact to compactTitle,
                        AppWindowLayout.Medium to mediumTitle,
                        AppWindowLayout.Wide to wideTitle,
                    ).forEach { (layout, title) ->
                        CompositionLocalProvider(LocalAppWindowLayout provides layout) {
                            AppScreenHeader(title = title, hero = true)
                        }
                    }
                    CompositionLocalProvider(LocalAppWindowLayout provides AppWindowLayout.Compact) {
                        AppScreenHeader(title = plainTitle, hero = false)
                    }
                }
            }
        }
        val compact = titleFontSize(compactTitle)
        val medium = titleFontSize(mediumTitle)
        val wide = titleFontSize(wideTitle)
        val plain = titleFontSize(plainTitle)
        assertEquals("中等档（平板竖屏/折叠内屏）不该降 hero，字级须与紧凑档相等", compact, medium)
        assertTrue(
            "宽档必须把 hero 降成普通页头字级：紧凑=${compact.value}sp / 宽=${wide.value}sp",
            wide.value < compact.value,
        )
        assertEquals("宽档 hero 降级后必须落到普通页头那一档字级", plain, wide)
    }

    /**
     * 反向对照：紧凑档里 hero 与普通页头的字级必须**不等**，
     * 否则上面那条「宽 < 紧凑」可能只是两档都没渲染出 hero（一起塌成 Title 照样绿）。
     */
    @Test
    fun hero_and_plain_headers_differ_in_the_compact_window() {
        val heroTitle = "自适应取证·紧凑hero"
        val plainTitle = "自适应取证·紧凑普通"
        composeRule.setContent {
            MintGlassTheme {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CompositionLocalProvider(LocalAppWindowLayout provides AppWindowLayout.Compact) {
                        AppScreenHeader(title = heroTitle, hero = true)
                        AppScreenHeader(title = plainTitle, hero = false)
                    }
                }
            }
        }
        val hero = titleFontSize(heroTitle)
        val plain = titleFontSize(plainTitle)
        assertTrue("紧凑档里 hero 必须比普通页头大：hero=${hero.value}sp / 普通=${plain.value}sp", hero.value > plain.value)
    }

    // ---------- 3. 宿主缺席时的回落 ----------

    /**
     * 没有 provider 时 `currentAppWindowLayout()` 走 `LocalConfiguration.screenWidthDp` 现算。
     *
     * 钉的破坏：`compositionLocalOf { null }` 的哨兵没被处理（回落分支被删）——那样要么崩，
     * 要么渲染出空档名。判据取「同一份配置自己分档」与「组件拿到的档」相等，
     * 因此**与当前窗口是几档无关**：三档取证复跑时这条照样成立。
     */
    @Test
    fun without_a_host_the_layout_falls_back_to_the_configuration_width() {
        composeRule.setContent {
            MintGlassTheme {
                val fromComposable = currentAppWindowLayout().name
                val widthDp = LocalConfiguration.current.screenWidthDp
                val fromConfig = AppWindowLayoutPolicy.classify(widthDp.toFloat()).name
                Text(
                    text = "$fromComposable|$fromConfig|$widthDp",
                    modifier = Modifier.testTag("window_layout_probe"),
                )
            }
        }
        val node = composeRule.onNodeWithTag("window_layout_probe").fetchSemanticsNode()
        val rendered = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString()
        val parts = rendered.split('|')
        assertEquals("探针应渲染出三段（档名 / 配置分档 / 宽度），实际=$rendered", 3, parts.size)
        assertEquals("宿主缺席时组件拿到的档必须与同一份 screenWidthDp 的分档一致", parts[1], parts[0])
        assertTrue(
            "回落档必须是三档之一，实际=${parts[0]}：null 哨兵没被处理就会落成空串",
            AppWindowLayout.entries.any { it.name == parts[0] },
        )
    }
}
