package com.example.englishlearning.ui

import com.example.englishlearning.ui.theme.AppDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 14（AC-R-20）：今日计划屏的「一处权威数字」与「1 主 CTA + 入口行组」的**源码侧**契约。
 *
 * 为什么这族断言放在 JVM 侧（与 `SourceRatchet` 文件头同一口径）：取值与形状都是「源码文本即契约」
 * 能问出口的问题——`AppType.Display` 用了几次、还剩几枚 M3 `Button`、入口行有没有真的换成共享组件、
 * 裸 hex 与静态深色读点是否清零，都能在读实现文件时当场判定；把同一件事在真机再数一遍只会得到
 * 一套更脆的判据。真机侧只留 JVM 问不到的那一半（节点实际渲染字号/颜色、整宽与饱和底），
 * 见 `app/src/androidTest/.../TodayPlanScreenTest.kt` 的 `ready_presents_todays_numbers_with_one_display_authority`
 * 与 `ready_groups_entry_actions_and_keeps_one_primary_cta`。
 *
 * 断言的形状刻意复用 `ContainerInkPairingContractTest` 的「按 Text 实参窗口取样」手法：
 * 先 [stripComments]（走共享夹具），再取「包住那枚 testTag 字面量的最近一处 `Text(` 调用」，
 * 窗口内要求排印档与墨色同时成立——只看整文件计数会放过「Display 写在别的槽位、进度数字仍是 Title」
 * 这种换形。窗口的可判红性由 [the_number_slot_window_extractor_is_not_vacuous] 自证。
 *
 * **对交接草案的四条修订**（都属于「判据自身判不了 / 编译不过」这一族，**没有一条放宽判据**，逐条理由见对应位置）：
 *  #1 [textCallContaining] 改为按**带引号**的标签字面量定位（草案按裸标签 `indexOf`，被
 *    `today_plan_progress` ⊂ `today_plan_progress_summary` 这组前缀关系命中，窗口取到了别的站点）；
 *  #2 [display_is_used_exactly_once_and_anchors_the_total_progress_slot] 的两个顺序锚点同样加引号，同一枚缺陷；
 *  #3 `the_screen_keeps_one_primary_cta_and_entry_rows_for_every_other_action` 里 Ready 分支切片的防空转守卫，
 *    草案要求「切片里再出现 `TodayPlanUiState`」——Ready 是 `when` 的最后一支，其后半文只剩私有辅助函数，
 *    这条在任何合法实现下恒假（在未改动的源码上亦红），换成「锚点存在 + 切片读到本分支自己的进度卡标签」；
 *  #4 草案引用 `AppDomain` 未 import，`compileDebugUnitTestKotlin` 直接失败，补 import。
 */
class TodayPlanNumberAuthorityTest {

    private val source: String = uiScreenSource("TodayPlanScreen.kt")

    /**
     * 取包住 [tag] 的那一枚 `Text(` 调用（含首尾括号）；找不到即当场红，不做静默空判。
     *
     * **对交接草案的修订 #1（按位取标签必须带引号定界）**：草案原先按裸标签 `indexOf(tag)` 取样，
     * 而本屏有互为前缀的标签——`today_plan_progress`（今日明细行）是 `today_plan_progress_summary`
     * （进度卡容器标签）的前缀，且容器写在明细行之前，`indexOf` 一律先命中容器那一处，
     * 于是 `today_plan_progress` 那条窗口取到的是**容器**那段代码，判据对目标站点失明（并且先在
     * 未改动的实现上假红）。改为查 `"\"$tag\""`：标签在本屏只以带引号字面量出现，闭合引号让
     * 前缀关系不再互相冒名。
     */
    private fun textCallContaining(tag: String): String {
        val quoted = "\"$tag\""
        val at = source.indexOf(quoted)
        assertTrue(at >= 0, "今日计划屏源码里找不到 \"$quoted\"——该呈现位或其标签被改名/删除（AC-R-20 的锚点）")
        val start = source.lastIndexOf("Text(", at)
        assertTrue(start >= 0, "\"$quoted\" 不在任何 Text( 调用之内，本条无法判定它的排印档")
        var depth = 0
        var i = start + "Text".length
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return source.substring(start, i + 1)
                }
            }
            i++
        }
        error("Text( 调用从 $start 起括号未闭合，窗口取样失效")
    }

    /**
     * 今日数字的唯一权威：`AppType.Display` 全屏恰一处，且必须落在 `today_plan_total_progress`
     * 那一格（今日完成数），并且真的在 `today_plan_progress_summary` 容器与 `today_plan_unlock_status`
     * 之间——进度卡才是「今天读了多少」的权威位置，别处再摆一枚同尺寸数字即破坏单一权威。
     */
    @Test
    fun display_is_used_exactly_once_and_anchors_the_total_progress_slot() {
        val displayRule = Regex("""AppType\.Display\b""")
        assertEquals(
            1,
            countMatches(source, displayRule),
            "AC-R-20：`AppType.Display` 在今日计划屏只许一处（今日完成数），其余数字一律降为 Footnote",
        )
        val window = textCallContaining("today_plan_total_progress")
        assertTrue(
            "AppType.Display" in window,
            "唯一那处 Display 必须就是 today_plan_total_progress 那一格，实际窗口：$window",
        )
        val cardAt = source.indexOf("\"today_plan_progress_summary\"")
        val unlockAt = source.indexOf("\"today_plan_unlock_status\"")
        val displayAt = source.indexOf("AppType.Display")
        assertTrue(cardAt in 0 until displayAt, "Display 必须在 today_plan_progress_summary 之后（进度卡里）")
        assertTrue(displayAt < unlockAt, "Display 必须在 today_plan_unlock_status 之前（不得跑到解锁文案下方另起一节）")
        assertTrue(NeutralInk.containsMatchIn(window), "权威数字也得显式给中性墨（AppType 各档自带浅套静态墨）：$window")
    }

    /**
     * 「其余数字一律降到 `Footnote` 且不带强调色」的源码侧落点：
     * 逐个呈现位取窗口，要求 Footnote + 中性墨，且窗口内看不见任何强调色读法
     * （`Mint*` 快捷名、`DomainColors.` 静态域色、`appDomain(...)` 动态域色、`AppType.Display`）。
     *
     * 两张计数卡是同一个私有组合（`CountCard`）的两个实例，故按函数体窗口判一次即可——
     * 卡内那条数值行才是数字，标签行按 `Label` 档走，都在同一段里被这条判据覆盖。
     */
    @Test
    fun every_other_number_on_the_screen_is_footnote_with_neutral_ink() {
        listOf(
            "today_plan_learned_percent",
            "today_plan_book_words",
            "today_plan_book_unlearned",
            "today_plan_progress",
            "today_plan_empty",
        ).forEach { tag ->
            val window = textCallContaining(tag)
            assertTrue(
                "AppType.Footnote" in window,
                "$tag 的数字必须降到 Footnote 档（AC-R-20 只留一处权威尺寸），实际窗口：$window",
            )
            assertTrue(NeutralInk.containsMatchIn(window), "$tag 必须显式取中性墨，实际窗口：$window")
            assertTrue(
                AccentInk.find(window) == null,
                "$tag 不该再带强调色（数字的权威只有一处），实际窗口：$window",
            )
        }
        val countCard = source.substringAfter("private fun RowScope.CountCard(").substringBefore("\n}")
        assertTrue(
            "AppType.Footnote" in countCard && NeutralInk.containsMatchIn(countCard),
            "计数卡里的数值行必须降到 Footnote + 中性墨（色点才是该卡的强调色载体），实际段：$countCard",
        )
        assertTrue(
            Regex("""AppType\.(?:Display|Headline|Title)""").find(countCard) == null,
            "计数卡里不得再出现大字号档——今日数字的权威尺寸只许一处，实际段：$countCard",
        )
    }

    /**
     * 布局收成的源码侧判据（AC-R-20「8 个整宽按钮 → 1 CTA + 入口行组」）：
     *  - M3 `Button(` 只剩阅读文章那一枚受解锁态约束的支撑动作（`PillButton`/`TextButton`/`ButtonDefaults`
     *    都不入计数：左侧词界断言只认独立成名的 `Button(`）；
     *  - 其余动作改调共享 `EntryRow`，且 Ready 分支里 `[PillButton(` 恰一处（主 CTA 唯一），
     *    入口行 ≥3 条，页头走 `AppScreenHeader`。
     */
    @Test
    fun the_screen_keeps_one_primary_cta_and_entry_rows_for_every_other_action() {
        assertEquals(
            1,
            countMatches(source, Regex("""(?<![A-Za-z0-9_])Button\(""")),
            "整宽 M3 Button 只许留下「阅读文章」那一枚支撑动作，其余动作一律换成 EntryRow / PillButton",
        )
        val readyAnchor = "is TodayPlanUiState.Ready"
        assertTrue(
            source.contains(readyAnchor),
            "Ready 分支取样失效（源码里找不到锚点 \"$readyAnchor\"）：分支写法被改，下面的按分支计数会对着整份文件判，" +
                "把「缺配置/不可用态不在本条约束内」这一层判据抹掉",
        )
        val readyBranch = source.substringAfter(readyAnchor)
        assertTrue(
            readyBranch.contains("today_plan_summary"),
            "Ready 分支切片为空或不含本分支自己的呈现位（substringAfter 命中锚点后应当读到进度卡标签），" +
                "实际切片起头：${readyBranch.take(80)}",
        )
        assertEquals(
            1,
            countMatches(readyBranch, Regex("""PillButton\(""")),
            "Ready 态里带强调底的主按钮只许一枚（继续学习 CTA）；缺配置/不可用态在别的分支，不受本条约束",
        )
        val entryRows = countMatches(source, Regex("""(?<![A-Za-z0-9_])EntryRow\("""))
        assertTrue(
            entryRows >= 3,
            "AC-R-20 要求入口行组：本屏至少 3 条共享 EntryRow（查词/词书单词/生词本/学习记录/打卡/工具），实际 $entryRows",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""AppScreenHeader\(""")),
            "页标题必须由共享 AppScreenHeader 承载（页头族 seed 删行的判据是 headline 读点清零，见 R-13.1）",
        )
        listOf(
            "today_plan_open_wordbook_words",
            "today_plan_open_vocabulary",
            "today_plan_open_learning_records",
            "today_plan_open_check_in",
            "today_plan_learning_tools",
            "today_plan_open_setup_entry",
            "today_plan_search_entry",
        ).forEach { tag ->
            assertTrue(tag in source, "入口行标签 $tag 不得改名或删除（既有 connected 用例与 AppScreenTest 依赖它）")
        }
    }

    /**
     * 本屏的取色与排印读法必须全部走动态 token：
     *  - 零裸 hex（Step 2 点名的 `#68BDA6`/`#C7A47D`/`#795B36` 三形五处全部 token 化，seed 同步删行）；
     *  - 零 `AppPalette.` / `DomainColors.` 静态读点（深色档不再浮浅套值）；
     *  - 零 `MaterialTheme.typography.` 读点（六档 token 取代库内档，页头由 AppScreenHeader 承载）；
     *  - 零 `Mint*` 快捷名（那些 getter 虽随主题翻色，但本屏统一按 `appColors()` 取，避免同一屏两套写法）。
     */
    @Test
    fun the_screen_reads_only_dynamic_tokens_for_color_and_typography() {
        assertEquals(
            0,
            countMatches(source, rawColorLiteral),
            "今日计划屏不得留裸 hex：三形五处（计数卡色点、打卡棕、工作量图例色）都要换成域色",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")),
            "今日计划屏的静态深色读点必须清零（DarkTokenConsumptionRatchetTest 的 seed 随之删行）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography\.""")),
            "本屏不再读库内排印档：一律走 AppType 六档 + 显式 color",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bMint[A-Z]\w*""")),
            "本屏中性色一律 appColors()，不留 Mint* 快捷名（同一屏两套取色写法是漂移的入口）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""FontWeight\.""")),
            "字重由六档 token 承载，屏侧不得再 copy 字重（spec §2.2 六档锁）",
        )
    }

    /**
     * Workload 三档与两张计数卡的色点必须同源、且取自域色（brief Step 2「复习 `Review` 系」）：
     * 复习 → `Review`、今日新学/新增 → `Learn`、已学 → 品牌 `AiSpeech`；同一概念在本屏只许一套颜色
     * （`TodayWorkloadBar` 上方那段 KDoc 早已写明这条）。
     */
    @Test
    fun today_markers_and_workload_segments_read_the_same_domain_colors() {
        listOf(AppDomain.Review, AppDomain.Learn, AppDomain.AiSpeech).forEach { domain ->
            assertTrue(
                source.contains("appDomain(AppDomain.${domain.name})"),
                "本屏必须按 appDomain(AppDomain.${domain.name}) 取域色（色点与分段条同源）",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex("""private val Workload\w+Color""")),
            "工作量色表不得再以顶层裸色常量存在（顶层 val 是「非可组合上下文」静态读点那一族）",
        )
        val colorFun = source.substringAfter("private fun WorkloadKind.color()").substringBefore("\n}")
        assertTrue(
            colorFun.contains("AppDomain.Review") && colorFun.contains("AppDomain.Learn"),
            "分段条的 REVIEW/NEW 两档必须读 Review/Learn 域色，实际：$colorFun",
        )
    }

    /**
     * 窗口提取器自身的防空转自证：同一段文本里，缺 `color` 的那一枚必须被中性墨谓词判红、
     * 补齐的那一枚必须放过；否则上面两条「按窗口判排印档与墨色」的用例就是空转。
     */
    @Test
    fun the_number_slot_window_extractor_is_not_vacuous() {
        val uncolored = "Text(\"3/5\", style = AppType.Display, modifier = Modifier.testTag(\"probe_tag\"))"
        val colored = "Text(\"3/5\", style = AppType.Display, color = appColors().text1, modifier = Modifier.testTag(\"probe_tag\"))"
        assertEquals(1, Regex("""\bText\(""").findAll(uncolored).count(), "合成片段自检：窗口取样前提（Text 调用存在）不成立")
        assertTrue(
            !NeutralInk.containsMatchIn(uncolored.substring(3, uncolored.length - 1)),
            "未补色的合成片段被判成已补色 = 本类的窗口谓词空转",
        )
        assertTrue(
            NeutralInk.containsMatchIn(colored.substring(3, colored.length - 1)),
            "补色的合成片段不被认出 = 本类窗口谓词对正确写法也判红，判据过宽",
        )
        assertTrue(
            AccentInk.containsMatchIn("color = MintPrimaryDark"),
            "强调色谓词抓不到 Mint* 快捷名 = 「数字不带强调色」那条是空转",
        )
        assertTrue(
            AccentInk.containsMatchIn("color = appDomain(AppDomain.Review).deep"),
            "强调色谓词抓不到动态域色 = 「数字不带强调色」那条是空转",
        )
    }

    private companion object {
        /** 显式中性墨读法（字面形与 `val colors = appColors()` 别名形都算）。 */
        val NeutralInk = Regex("""\bcolor\s*=\s*(?:appColors\(\)\.|colors\.|set\.)(?:text1|text2)\b""")

        /** 任何强调色读法：`Mint*` 快捷名、静态/动态域色、以及状态色。 */
        val AccentInk = Regex("""\bcolor\s*=\s*(?:Mint\w+|AppPalette\.\w+|DomainColors\.\w+|appDomain\(|appColors\(\)\.(?:success|error|errorFill|onMint|tint\d*)|colors\.(?:success|error|errorFill|onMint))""")
    }
}
