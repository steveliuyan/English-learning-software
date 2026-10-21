package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 20-A（裁决①「我的词」一页三段）的**源码契约**：把屏的形状与入口挂载点钉在 JVM 侧，
 * connected 侧（`MyWordsScreenTest`）只补「同一次组合里三段都渲染」这一件实测。
 *
 * 分工（口径沿用批 1/批 2 的先例）：
 *  - 本文件管**形状**：枚举三枚、三段容器标签各恰一处、必须复用四枚共享组件、必须挂进词汇域入口面、
 *    墨色/排印/图标一律走 token（三张棘轮的口径在这里复述一遍，新增屏不得自带裸 hex 与静态深色读点）；
 *  - `MyWordsScreenTest` 管**行为**：一次 `setContent` 三段齐现、各段数据到位、出口回调通、空态与不可用态诚实降级、
 *    `section` 参把视口落到指定段。
 *
 * 为什么「三段齐现」不能只靠本文件：静态源码读得出「标签写了三次」，读不出「渲染时没有被 `section` 门控成单段 tab 页」；
 * 反过来 connected 读不出墨色（Task 9 实测口径）。两条各守一端。
 */
class MyWordsScreenContractTest {

    private val screen = uiScreenSource("MyWordsScreen.kt")

    /** 裁决①逐字：三段 = 收藏 · 生词本 · 错词；容器标签按 R-20A.2 的三条 testTag。 */
    private val sectionTags = listOf(
        "my_words_section_favorites",
        "my_words_section_wordbook",
        "my_words_section_wrong",
    )

    @Test
    fun declares_the_three_section_enum_exactly_once() {
        val enumRead = Regex("""(?m)^enum class MyWordsSection\b""")
        assertEquals(
            1,
            countMatches(screen, enumRead),
            "MyWordsScreen.kt 应恰一处顶层 `enum class MyWordsSection`（0 = 没声明或在别的文件里，" +
                "2 = 长出第二套段名口径，两边都让入口与屏失去共同语言）",
        )
        val body = Regex("""(?m)^enum class MyWordsSection[^{]*\{([^}]*)\}""").find(screen)
        val cases = body?.groupValues?.get(1).orEmpty()
        listOf("Favorites", "Wordbook", "Wrong").forEach { name ->
            assertTrue(
                name in cases,
                "枚举必须含 $name（R-20A.2 逐字口径 Favorites / Wordbook / Wrong），实际声明体：$cases",
            )
        }
    }

    /** 三段容器标签各恰一处：多一处 = 同一段落长出了第二个容器（滚动定位与用例都会指错那枚）。 */
    @Test
    fun every_section_has_exactly_one_labeled_container() {
        sectionTags.forEach { tag ->
            assertEquals(
                1,
                countMatches(screen, Regex(Regex.escape("\"$tag\""))),
                "$tag 应在本屏恰一处（0 = 该段没有可定位容器，2 = 同段两枚容器让 connected 的定位判据失效）",
            )
        }
    }

    /**
     * 「三段始终全部渲染」的静态一半：`section` 只许用于落位，不得当渲染开关。
     *
     * 行为那一半由 connected 的 `three_sections_render_in_one_composition` 承重（一次 `setContent` 三段齐现）。
     */
    @Test
    fun section_param_lands_instead_of_filtering() {
        assertEquals(
            0,
            countMatches(screen, Regex("""when\s*\(\s*section""")),
            "不得出现 `when (section)` 之类的段门控：R-20A.2 明写本屏不是「只显示一段的 tab 页」，" +
                "section 的语义是初始落位/聚焦，三段始终全部渲染",
        )
        assertTrue(
            screen.contains("scrollTo") || screen.contains("scrollToItem") || screen.contains("animateScrollTo"),
            "section 落位必须真的滚动（scrollTo/scrollToItem 一族），否则形参是一个不起作用的摆设",
        )
    }

    /** 四枚共享组件必须复用，不得再长出私有份（份数棘轮的复述，方向与 `SharedComponentUniquenessRatchetTest` 一致）。 */
    @Test
    fun reuses_the_shared_components_instead_of_reimplementing_them() {
        assertEquals(
            1,
            countMatches(screen, Regex("""AppScreenHeader\(""")),
            "页头必须由共享 AppScreenHeader 承载，且本屏恰一处（页头族）",
        )
        assertTrue(
            countMatches(screen, Regex("""(^|[^A-Za-z0-9_])WordRow\(""")) >= 1,
            "词行必须改调共享 WordRow（词行族）：本屏是新屏，不在 `SharedComponentUniquenessRatchetTest` 的词行 seed 台账里，" +
                "写私有词行会当场判新增债务",
        )
        assertTrue(
            countMatches(screen, Regex("""ScreenUnavailable\(""")) >= 1,
            "不可用态必须复用共享 ScreenUnavailable，不得自搓一套降级卡",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""private fun NotConfiguredCard\(""")),
            "不得定义私有 NotConfiguredCard（未配置卡族没有合规出口）",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""MaterialTheme\.typography\.""")),
            "排印一律走 AppType 六档，不读库内档",
        )
    }

    /** token 纪律（三张棘轮的新屏口径）：零裸 hex、零静态深色读点、零库内图标、零 `Mint*` 快捷名。 */
    @Test
    fun consumes_only_tokens() {
        assertEquals(0, countMatches(screen, Regex("""Color\(0x[0-9A-Fa-f]{8}\)""")), "本屏不得写裸 Color(0xFF…)")
        assertEquals(
            0,
            countMatches(screen, Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")),
            "本屏不得有 AppPalette./DomainColors. 静态读点（深色模式会浮浅套值，且会新增债务台账站点）",
        )
        assertEquals(0, countMatches(screen, Regex("""\bIcons\.""")), "图标一律 AppIcon + AppGlyph，禁 androidx Icons.")
        assertEquals(0, countMatches(screen, Regex("""\bMint[A-Z]\w*""")), "中性色一律 appColors()，不留 Mint* 快捷名")
        assertTrue(
            screen.contains("appColors()"),
            "本屏墨色必须来自 appColors()/appDomain()，否则上面的「零裸 hex」只是把颜色丢给了默认主题",
        )
    }

    /** 入口挂载（R-20A.3「只建不摘」）：新入口挂进词汇域入口面，且旧入口一行不摘。 */
    @Test
    fun the_new_entry_is_mounted_in_the_vocabulary_domain_surface() {
        val todayPlan = uiScreenSource("TodayPlanScreen.kt")
        assertTrue(
            "today_plan_open_my_words" in todayPlan,
            "「我的词」入口必须挂在 TodayPlanScreen 的词汇域入口行组里（标签 today_plan_open_my_words）",
        )
        assertTrue(
            countMatches(todayPlan, Regex(Regex.escape("\"我的词\""))) >= 1,
            "入口行标题必须是「我的词」（与查词 / 词表并列的三入口文案，裁决①）",
        )
        // 只建不摘：旧入口的标签与标题仍在原处，摘除动作归 Task 22 / 批 3 收口。
        listOf(
            "today_plan_open_vocabulary",
            "today_plan_open_wordbook_words",
            "today_plan_search_entry",
        ).forEach { tag ->
            assertTrue(tag in todayPlan, "旧入口标签 $tag 不得被摘（本席只建不摘，摘除归 Task 22）")
        }

        val appScreen = uiScreenSource("AppScreen.kt")
        assertTrue(
            countMatches(appScreen, Regex("""(^|[^A-Za-z0-9_])MyWordsScreen\(""")) == 1,
            "AppScreen 必须挂载 MyWordsScreen 恰一处（0 = 新入口没有宿主，2 = 长出第二份挂载）",
        )
        assertTrue(
            countMatches(appScreen, Regex("""\bshowMyWords\b""")) >= 3,
            "AppScreen 侧的 showMyWords 状态必须同时被「声明 / 返回键 / 渲染分支」读到（少于 3 处说明有一环没接上）",
        )
        // 只建不摘：收藏页、错词页、生词本页三条老路由仍在册。
        listOf("FavoritesScreen(", "WrongWordsScreen(", "VocabularyScreen(").forEach { call ->
            assertTrue(
                call in appScreen,
                "旧屏挂载点 $call 不得被摘（摘除归 Task 22）",
            )
        }
    }
}
