package com.example.englishlearning.ui

import com.example.englishlearning.ui.theme.AppDomain
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

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
 * 断言的形状刻意复用 `ContainerInkPairingContractTest` 的「按 Text 实参窗口取样」手法，但取样器本身
 * 在修复轮 1 里从「向前找最近一处 `Text(`」改成**枚举 + 归属**（[textWindows] + [textCallContaining]，
 * 评审 C-1 / 本席 F1）：先剥注释（走共享夹具），再枚举本文件每一枚 `Text(` 调用的闭区间，
 * 要求目标标签**恰落在一枚窗口之内**——命中 0 处（标签被上提到包裹容器）与命中 >1 处（歧义）都当场红。
 * 窗口内要求排印档与墨色同时成立——只看整文件计数会放过「Display 写在别的槽位、进度数字仍是 Title」
 * 这种换形；而「屏内再摆一枚新的大字号数字」这一形由 [display_is_used_exactly_once_and_anchors_the_total_progress_slot]
 * 的全文件大字号读数钉住（评审 C-2 / 本席 F2），两条合起来才罩住 AC-R-20 的「一处权威呈现」。
 * 窗口的可判红性由 [the_number_slot_window_extractor_is_not_vacuous] **真调用取样器**自证（本席 F6）。
 *
 * **对交接草案的四条修订**（都属于「判据自身判不了 / 编译不过」这一族，**没有一条放宽判据**，逐条理由见对应位置）：
 *  #1 [textCallContaining] 改为按**带引号**的标签字面量定位（草案按裸标签 `indexOf`，被
 *    `today_plan_progress` ⊂ `today_plan_progress_summary` 这组前缀关系命中，窗口取到了别的站点）；
 *  #2 [display_is_used_exactly_once_and_anchors_the_total_progress_slot] 的两个顺序锚点同样加引号，同一枚缺陷；
 *  #3 `the_screen_keeps_one_primary_cta_and_entry_rows_for_every_other_action` 里 Ready 分支切片的防空转守卫，
 *    草案要求「切片里再出现 `TodayPlanUiState`」——Ready 是 `when` 的最后一支，其后半文只剩私有辅助函数，
 *    这条在任何合法实现下恒假（在未改动的源码上亦红），换成「锚点存在 + 切片读到本分支自己的进度卡标签」；
 *  #4 草案引用 `AppDomain` 未 import，`compileDebugUnitTestKotlin` 直接失败，补 import。
 *
 * **修复轮 1（评审 C-1 / C-2 / ②）追加的三条**：
 *  #5 [textCallContaining] 改为 [textWindows] 枚举 + 归属判定（判据在「tag 上提到包裹容器」这一形下失守，
 *    评审用 `/tmp/mut.py` 的 M1 变异证得 JVM 六条窗口判据 + Display 计数全绿）；
 *  #6 [display_is_used_exactly_once_and_anchors_the_total_progress_slot] 补全文件大字号读数
 *    （`Display` + `Headline` 合计恰 1、`Title` 恰 1），封掉「新增一枚自带新 tag 的大字号数字」这一**本文件字面量**形（M2）；
 *  #7 [the_number_slot_window_extractor_is_not_vacuous] 兑现 KDoc 早先的声明：**真调用**取样器打三枚合成源
 *    （命中 1 / 命中 0 / 命中 2），不再只对正则谓词做合成断言。
 *
 * **批 2 终审修复席 C（评审 C1 的 I-3 / I-4 / I-5；只动本契约文件，生产码零改动）**：
 *  #8 [bodyOf]：取样**先钉锚在位、再按花括号配平取体**，缺锚与未闭合一律响亮红
 *    （评审 I-3：旧 `substringAfter(...).substringBefore("\n}")` 缺锚时返回整份原文，
 *    本屏 `TodayPlanScreen.kt` 第一枚顶层闭括号在 `fun TodayPlanScreen` 收尾处，回退取样面里
 *    同时含 Review/Learn 两枚域色 ⇒ `WorkloadKind.color()` 被改名/删除后那条配色同源判据照样绿）；
 *  #9 [the_screen_keeps_one_primary_cta_and_entry_rows_for_every_other_action]：Ready 分支改成真区间
 *    （评审 I-4：旧 `readyBranch` 自称分支、实为无界尾切片，主 CTA 从未绑到分支体），
 *    药片计数、主 CTA 的 `today_plan_start_learning` tag 与本分支的进度卡标签都在体内判；
 *  #10 [the_screen_reads_only_dynamic_tokens_for_color_and_typography] 末尾补 `style =` 实参**白名单负判据**
 *    （评审 I-5：三道计数门只认本文件字面量，跨文件 style 符号可整体绕过 AC-R-20）——
 *    #6 的口径同时收窄为「封掉本文件字面量形，跨文件 style 符号由 #10 这条白名单封」；
 *  #11 [samplers_fail_loudly_when_the_anchor_is_missing_or_the_region_cannot_be_closed]：
 *    缺锚 / 未闭合 / 形参表未闭合三条合成负控，钉住 #8/#9 的「响亮红而不是静默降级」。
 */
class TodayPlanNumberAuthorityTest {

    private val source: String = uiScreenSource("TodayPlanScreen.kt")

    /**
     * 取包住 [needle] 的那一枚 `Text(` 调用窗口（含首尾括号）。归属判定走 [textWindows] 枚举，
     * **不再**「向前找最近一处 `Text(`」（修订 #5 / 评审 C-1）。
     *
     * 三条出口，只有「恰一处命中」算过：
     *  - [needle] 一次都不出现 ⇒ 红：锚点被改名或删除；
     *  - 命中 0 枚窗口 ⇒ 红，消息点名「tag 被上提到包裹容器即绕过本判据」——这正是评审 M1 变形的形状，
     *    旧实现会回退去捞前一枚不相干 `Text(`（那里恰好是 Footnote + 中性墨）从而全绿；
     *  - 命中 >1 枚窗口 ⇒ 红（歧义取样，禁止静默取第一个）。反向的「合法实现被误判」也在这条里响亮报出，
     *    而不是像旧实现那样无解释地红。
     *
     * [src] 默认为本屏剥注释源码（[source]）；防空转用例传入合成源码复用同一套扫描器（修订 #7）。
     */
    private fun textCallContaining(needle: String, src: String = source): String {
        val occurrences = mutableListOf<Int>()
        var from = 0
        while (true) {
            val at = src.indexOf(needle, from)
            if (at < 0) break
            occurrences += at
            from = at + 1
        }
        assertTrue(
            occurrences.isNotEmpty(),
            "今日计划屏源码里找不到 $needle——该呈现位或其标签被改名/删除（AC-R-20 的锚点）",
        )
        val windows = textWindows(src)
        val hits = windows.filter { w -> occurrences.any { it in w } }
        assertTrue(
            hits.size == 1,
            if (hits.isEmpty()) {
                "$needle 不在任何 `Text(` 调用的实参之内——tag 被上提到包裹容器即绕过本判据" +
                    "（本文件共枚举到 ${windows.size} 枚 `Text(` 调用，无一包含该标签）"
            } else {
                "$needle 同时命中 ${hits.size} 枚 `Text(` 调用——歧义取样，禁止静默取第一个；" +
                    "要么两处呈现位共用同一标签（connected 侧会取到不确定的一枚），要么窗口扫描器把嵌套调用算重了"
            },
        )
        val hit = hits.single()
        return src.substring(hit.first, hit.last + 1)
    }

    /**
     * 枚举 [src] 里每一枚 `Text(` 调用的**闭区间**（含首尾括号），供 [textCallContaining] 做归属判定。
     *
     * 口径三条：
     *  - **左词界**：`Text(` 前一个字符不得属于 `[A-Za-z0-9_]`（`TextButton(` 这种不成名，
     *    `foo.Text(` 这种算调用——与 M3 `Button(` 计数用同一族词界判断）；
     *  - **字符串感知**的括号平衡：双引号串认 `\"` 转义、三引号原始串整段跳、单引号字符字面量整段跳，
     *    否则 `"…（${x}）…"` / `"("` 这类字面量里的括号会把闭合点带偏（本屏 `Text` 的实参里到处是
     *    `${}` 插值，插值里的括号也在串内）；
     *  - **每条 `Text(` 恰返回一个区间**：长度为 0 或与其它区间重叠都当场 `error()`。重叠意味着嵌套
     *    （一枚 `Text(` 的实参里又出现一枚 `Text(`）或扫描器自己被带偏，此时任何「窗口内应当含 X」的
     *    判据都不可信，必须响亮失败，不能静默取一个继续判绿。
     */
    private fun textWindows(src: String): List<IntRange> {
        val windows = mutableListOf<IntRange>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (c == '"' || c == '\'') {
                i = skipLiteral(src, i)
                continue
            }
            if (!src.startsWith("Text(", i) || (i > 0 && isWordChar(src[i - 1]))) {
                i++
                continue
            }
            val openParen = i + "Text".length
            val close = matchingParen(src, openParen)
            if (close <= openParen) {
                error(
                    "`Text(` 调用从下标 $i 起括号未闭合或长度为 0（close=$close, open=$openParen），窗口取样失效：" +
                        "本文件的调用形状被改坏或扫描器读偏，任何「窗口内应当含 X」的判据此时都不可信",
                )
            }
            val window = i..close
            windows.firstOrNull { it.first <= window.last && window.first <= it.last }?.let { outer ->
                error(
                    "`Text(` 调用窗口重叠：$window 落在 $outer 之内（嵌套 Text 实参或扫描器带偏），" +
                        "归属判定失去唯一性；片段：${src.substring(window.first, minOf(window.last + 1, src.length))}",
                )
            }
            windows += window
            // 只前进到 `Text(` 之后：嵌套的那一枚因此仍会被扫到，并由上面的重叠检查判红。
            i = openParen + 1
        }
        return windows
    }

    /** 从 [start]（指向 `"` 或 `'`）跳过一枚字面量，返回闭合引号之后的下标；形状不合法时只前进一位，绝不吃掉后面的括号。 */
    private fun skipLiteral(src: String, start: Int): Int {
        if (src[start] == '\'') {
            var j = if (start + 1 < src.length && src[start + 1] == '\\') start + 2 else start + 1
            return if (j < src.length && src[j] == '\'') j + 1 else start + 1
        }
        if (src.startsWith("\"\"\"", start)) {
            val end = src.indexOf("\"\"\"", start + 3)
            return if (end < 0) src.length else end + 3
        }
        var j = start + 1
        while (j < src.length) {
            when (src[j]) {
                '\\' -> j += 2
                '"' -> return j + 1
                '\n' -> return start + 1
                else -> j++
            }
        }
        return src.length
    }

    /** 从 [open]（指向 `(`）做字符串感知的括号平衡扫描，返回配对的 `)` 下标；扫不到返回 -1。 */
    private fun matchingParen(src: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < src.length) {
            val c = src[i]
            if (c == '"' || c == '\'') {
                i = skipLiteral(src, i)
                continue
            }
            when (c) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun isWordChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'

    /**
     * 从 [open]（必为 `{`）做字符串/字符字面量感知的花括号配平，返回配对 `}` 的下标。
     * 未闭合或长度为 0 一律 `fail`（批 2 终审 C1 评审 I-1 的口径：取样面无法闭合必须响亮报错，
     * 不许像旧版 `substringAfter`/`indexOf("\n}")` 那样静默回退成「锚点之后的整份原文」——
     * 那会把分支级/函数体级判据悄悄降级成整文件判据而照常绿）。
     */
    private fun balancedBrace(src: String, open: Int, site: String): Int {
        assertTrue(
            open in 0 until src.length && src[open] == '{',
            "$site：取窗起点不是 `{`（open=$open），本夹具只接受指向花括号的区间",
        )
        var depth = 0
        var i = open
        while (i < src.length) {
            val c = src[i]
            if (c == '"' || c == '\'') {
                val next = skipLiteral(src, i)
                assertTrue(next > i, "$site：字面量扫描器在 $i 处原地不动，配平无法继续（拒绝猜测）")
                i = next
                continue
            }
            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        fail(
            "$site：从下标 $open 起的 `{…}` 未闭合（长度 0 或扫描被带偏）——分支体/函数体区间无法界定，" +
                "本契约拒绝把它降级成「锚点之后到文末」的整份原文取样",
        )
    }

    /**
     * 按**锚点字面**取一段体（函数体 / `when` 分支体），返回不含最外层花括号的体内文本。
     *
     * 三道响亮守卫（评审 I-3 / I-4：旧写法 `source.substringAfter(anchor).substringBefore("\n}")`
     * 在锚缺失时返回整份原文、在未闭合时吞到文末，两条都静默降级成整文件判据）：
     *  - 锚不在位 ⇒ 红（消息点名「锚缺失即回退整份原文」）；
     *  - 锚以 `(` 收尾时先把形参表配平到 `)`，再取体内第一枚 `{`（形参默认值里的 `{` 不算体起始）；
     *  - 花括号未闭合 ⇒ 由 [balancedBrace] 红。
     */
    private fun bodyOf(src: String, anchor: String, site: String): String {
        val at = src.indexOf(anchor)
        assertTrue(
            at >= 0,
            "$site：取样锚 \"$anchor\" 不在位（被改名/删除/换形）——旧写法会静默回退成整份原文，判据当场失明，" +
                "故本夹具先钉锚在位再取样",
        )
        var cursor = at + anchor.length
        if (anchor.endsWith("(")) {
            val closeParen = matchingParen(src, at + anchor.length - 1)
            assertTrue(
                closeParen >= cursor,
                "$site：锚 \"$anchor\" 的形参表括号未闭合（close=$closeParen），无法定位函数体起点",
            )
            cursor = closeParen + 1
        }
        val open = if (src.getOrNull(cursor - 1) == '{') cursor - 1 else src.indexOf('{', cursor)
        assertTrue(
            open >= 0,
            "$site：锚 \"$anchor\" 之后找不到体起始花括号 `{`（本屏的这段形状与本夹具口径不符，拒绝猜测）",
        )
        val close = balancedBrace(src, open, site)
        return src.substring(open + 1, close)
    }

    /**
     * 标签字面量的**带引号**定界写法（沿用修订 #1）：本屏存在互为前缀的标签
     * （`today_plan_progress` ⊂ `today_plan_progress_summary`），只有闭合引号能让枚举不再把两者混为一谈。
     */
    private fun quoted(tag: String): String = "\"$tag\""

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
        // F2 / 评审 C-2：只数 `AppType.Display` 的字面量，防得住「把注脚升上去」，防不住「再摆一枚新的」——
        // 屏内新增一枚自带新 tag 的 `AppType.Headline` 数字时，Display 计数仍为 1、两个顺序锚点仍成立、
        // connected 侧只采样那 8 个固定 tag，两道门都不红（评审 `/tmp/mut.py` 的 M2 变异）。
        // 所以这里按**全文件**判大字号读数：`Display` + `Headline` 合计恰 1，即本屏不允许任何第二处
        // 大字号读点。判据能成立的口径：页头标题在共享组件 `AppScreenHeader` 里，不在本文件，
        // 故「本屏一处权威呈现」在剥注释源码上就是一个可数的数字。
        assertEquals(
            1,
            countMatches(source, Regex("""AppType\.(?:Display|Headline)\b""")),
            "AC-R-20：本屏大字号读点（AppType.Display + AppType.Headline 合计）只许恰一处——今日完成数那一格；" +
                "多出来的任何一枚（哪怕是自带新 tag 的第三格数字）都在复制同一份视觉重量，" +
                "批 3 若确需第二个大字号读点，先改规格再改本判据",
        )
        // `AppType.Title`（17sp）是「比注脚大、比 Display 小」的那一档，评审 C-1 指出 connected 的高度代理
        // 判据对它零余量（34sp/17sp 行高比恰为 2），所以 Title 的回退必须由这条源码侧判据兜住：本屏只许
        // 词书名那一处文字用 Title。
        assertEquals(
            1,
            countMatches(source, Regex("""AppType\.Title\b""")),
            "AC-R-20：`AppType.Title` 在本屏只许用于词书名那一处文字（state.wordBookName）；" +
                "今日数字一律不许升回 Title（connected 的高度代理判据对这一档没有余量）",
        )
        val window = textCallContaining(quoted("today_plan_total_progress"))
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
            val window = textCallContaining(quoted(tag))
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
        val countCard = bodyOf(source, "private fun RowScope.CountCard(", "计数卡排印取样")
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
     *    入口行 ≥3 条，页头走 `AppScreenHeader`；
     *  - **查词条的位置**（修复轮 1 的 F3）：`today_plan_search_entry` 必须出现在 `when (state)` 之前，
     *    即四态常驻，不许跟着 Ready 分支走。
     */
    @Test
    fun the_screen_keeps_one_primary_cta_and_entry_rows_for_every_other_action() {
        assertEquals(
            1,
            countMatches(source, Regex("""(?<![A-Za-z0-9_])Button\(""")),
            "整宽 M3 Button 只许留下「阅读文章」那一枚支撑动作，其余动作一律换成 EntryRow / PillButton",
        )
        // 评审 I-4：旧写法 `source.substringAfter("is TodayPlanUiState.Ready")` 自称「Ready 分支」，
        // 实为**无界尾切片**（吞到文件末，把 `TodayPlanEntryGroup`/`CountCard`/`TodayWorkloadBar` 全算进分支），
        // 于是「把主 CTA 抽进分支锚点之后新加的未被调用的私有组合件」这种写法 tail 计数仍是 1、全绿，
        // 而 Ready 分支实际渲染出空。现按花括号配平取**分支体本身**，药片计数、主 CTA 的 tag 与
        // 本分支自己的进度卡标签都在体内判；缺锚或未闭合由 [bodyOf] 响亮红。
        val readyBranch = bodyOf(source, "is TodayPlanUiState.Ready -> {", "Ready 分支取样")
        assertTrue(
            readyBranch.contains(quoted("today_plan_summary")),
            "Ready 分支体内读不到本分支自己的进度卡标签 ${quoted("today_plan_summary")}——" +
                "分支区间没界定到渲染体（或该呈现位被挪出分支），实际体起头：${readyBranch.take(80)}",
        )
        assertEquals(
            1,
            countMatches(readyBranch, Regex("""PillButton\(""")),
            "Ready 分支体里带强调底的主按钮只许一枚（继续学习 CTA）；缺配置/不可用态在别的分支，不受本条约束",
        )
        assertTrue(
            readyBranch.contains(quoted("today_plan_start_learning")),
            "主 CTA 的 testTag ${quoted("today_plan_start_learning")} 必须长在 Ready 分支体内：把它抽进" +
                "分支之后另写的私有组合件（哪怕同名 tag）会让分支渲染为空、而 tail 计数照旧绿（评审 I-4 的变异形）",
        )
        // F3（修复轮 1 / 评审 I-1）：**查词条必须常驻**。上一版把它收进只在 Ready 分支渲染的
        // `TodayPlanEntryGroup`，于是缺配置 / 不可用 / 加载三态下学习 tab 内没有任何查词出口
        // （四个 tab 里没有查词 tab），与 `AGENTS.md`「无网络或无 AI Key 时……离线查词仍应可用」相抵。
        // 静态位置的口径：`today_plan_search_entry` 的首次出现必须**早于** `when (state)`——
        // 即它由 `TodayPlanScreen` 主体渲染，而不是任何分支内部。运行时那一半由 connected 的
        // `search_entry_is_reachable_in_non_ready_state_missing_setup` / `..._unavailable` 罩住。
        val whenAnchor = "when (state)"
        val whenAt = source.indexOf(whenAnchor)
        assertTrue(whenAt >= 0, "本屏源码里找不到 \"$whenAnchor\" 锚点，无法判定查词条的位置")
        val searchEntryAt = source.indexOf(quoted("today_plan_search_entry"))
        assertTrue(
            searchEntryAt in 0 until whenAt,
            "查词入口标签 today_plan_search_entry 必须写在 `when (state)` **之前**（四态常驻），" +
                "实际位置：标签=$searchEntryAt / `when (state)`=$whenAt——" +
                "把它挪进任何分支（含 TodayPlanEntryGroup）都会让非 Ready 态失去查词出口",
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
            assertTrue(
                tag in source,
                "入口行标签 $tag 不得改名或删除（既有 connected 用例与 AppScreenTest 依赖它；" +
                    "名单是 6 条组内行 + 1 条常驻查词 = 本屏入口的全部，两者都不许改名）",
            )
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
        // N-1（Task 14B 复评席的新洞，控制者当场补门）：上面三道全文件计数门判的是 `AppType.*` 的**字面量**，
        // 对「间接升档」全盲——`AppType.Footnote.copy(fontSize = 22.sp)`、裸 `TextStyle(fontSize = 30.sp)`、
        // `LocalTextStyle.merge(...)` 都能长出第二枚大号数字而一个字面量都不动
        // （复评席 JVM 实测：`.copy(fontSize = 22.sp)` 下 6 tests 全绿，13–15.3sp 区间双门皆盲）。
        // 本屏的排印面只许由六档 token 承载：任何字号/行高覆写即绕过 AC-R-20 的「一处权威尺寸」。
        assertEquals(
            0,
            countMatches(source, Regex("""\b(?:fontSize|lineHeight)\b|\bTextStyle\(|\.merge\(""")),
            "今日计划屏不得覆写字号/行高，也不得自造 TextStyle/merge 排印：六档 token 之外的一切排印改动" +
                "都会让「大字号读点恰一处」那三道计数门失明",
        )
        // 评审 I-5：上面五道门判的都是**本文件字面量**，跨文件的 style 符号能整体绕过 AC-R-20——
        // 在 `ui/` 别处写一枚顶层 `fun bigNumberStyle()`（或 `val`），屏侧只写 `style = bigNumberStyle()`，
        // 五道门一字不动全绿，屏上照样长出第二枚大字号数字。
        // 零误伤白名单口径：本屏每一枚 `style =` 实参必须**逐字**是 AppType 六档 token 之一，
        // 白名单外（符号上提、`.copy` 换形、跨文件 helper、形参默认值）一律红。
        // 判据写成「两侧计数相等」而不是「正则命中为零」：Java 正则里 `\s*` 会回溯，
        // 单个带否定前瞻的正则会把每一枚合规实参都数进来（本席实测假红 18 处），
        // 换成「全部 style 实参数 == 白名单逐字数」后同一处逃逸仍能单独判红。
        // 可重算：`grep -o 'style = [A-Za-z0-9_.()]*' app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt | sort | uniq -c`
        val styleArgs = countMatches(source, Regex("""(?<![A-Za-z0-9_])style\s*=\s*"""))
        val whitelistedStyleArgs = countMatches(
            source,
            Regex("""(?<![A-Za-z0-9_])style\s*=\s*AppType\.(?:Display|Headline|Title|Body|Label|Footnote)\b(?![.\w])"""),
        )
        assertTrue(
            styleArgs > 0,
            "反空转：本屏一处 `style =` 实参也没取到，说明取样器或本屏排印面整体失效，" +
                "白名单门此刻没有任何对象（实测取到 $styleArgs 处）",
        )
        assertEquals(
            styleArgs,
            whitelistedStyleArgs,
            "本屏的 style = 实参只许逐字写 AppType 六档 token（Display/Headline/Title/Body/Label/Footnote）：" +
                "全项 $styleArgs 处、白名单内只有 $whitelistedStyleArgs 处，差额就是白名单外的写法。" +
                "换成别处定义的样式符号（顶层 val/fun、跨文件 helper、`.copy` 换形）就是「大字号读点恰一处」" +
                "那三道字面量门的跨文件逃逸口（评审 I-5）",
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
        // 评审 I-3：旧写法 `source.substringAfter("private fun WorkloadKind.color()").substringBefore("\n}")`
        // 缺锚时 Kotlin 返回**整份原文**，再截到第一枚第 0 列闭括号 ⇒ 取样面换成主屏函数体
        // （`TodayPlanScreen.kt` 的 `:224/:225` 已含 Review/Learn 两枚域色读点），本条照旧绿而分段条失去保护。
        // 现走 [bodyOf]：先钉锚在位、再按花括号配平取 `when` 体本身。
        val colorFun = bodyOf(source, "private fun WorkloadKind.color()", "分段条配色取样")
        assertTrue(
            colorFun.contains("AppDomain.Review") && colorFun.contains("AppDomain.Learn"),
            "分段条的 REVIEW/NEW 两档必须读 Review/Learn 域色，实际：$colorFun",
        )
        assertTrue(
            colorFun.contains("AppDomain.AiSpeech"),
            "分段条的已学档必须与页头色点同源（AiSpeech 域色），不得另调一套静态色，实际：$colorFun",
        )
    }

    /**
     * 窗口提取器自身的防空转自证（修订 #7 / 评审 I-2）：**真调用** [textCallContaining] 与 [textWindows]，
     * 三种合成源各打一次——旧实现只对正则谓词做合成断言，KDoc 声称的「窗口取样自证」落空，
     * 于是 C-1 那条形（tag 上提到包裹容器）能在六条窗口判据下全绿。
     *
     *  ① tag 写在 `Text(` 实参内 ⇒ 取到窗口，且窗口就是那一枚调用（含 `AppType.Display`）；
     *  ② tag 上提到包裹 `Row(...)` ⇒ 当场红，且消息点名「包裹容器」（这就是 M1 变形的形状）；
     *  ③ 同一 tag 出现在两枚 `Text(` 内 ⇒ 红（歧义命中，禁止静默取第一个）；
     *  ④ 对本屏真实源码逐枚取样 ⇒ 每枚窗口都必须回含它自己的标签，且六枚窗口互不相同
     *     （这条才真正兑现「判据罩的是目标站点」，也是评审 `/tmp/win2.py` 手工验过的那六次命中）。
     *
     * 末尾保留正则谓词的合成自检：`NeutralInk` / `AccentInk` 若对正确写法判红、对补色缺失放行，
     * 上面两条按窗口判档位的用例同样是空转。
     */
    @Test
    fun the_number_slot_window_extractor_is_not_vacuous() {
        // ① tag 在 Text( 实参内：能取到窗口，窗口含所要求的档位
        val inlineTag = """Row { Text("3/5", style = AppType.Display, color = appColors().text1, modifier = Modifier.testTag("probe_tag")) }"""
        val hit = textCallContaining(quoted("probe_tag"), inlineTag)
        assertTrue("AppType.Display" in hit, "① 取样器对「tag 写在 Text 实参内」这一形取错了窗口：$hit")
        assertTrue(quoted("probe_tag") in hit, "① 取回的窗口不含自己的标签（归属判据空转）：$hit")

        // ② tag 上提到包裹容器：必须红，且消息要说清绕过方式
        val hoistedTag = """Row(modifier = Modifier.testTag("probe_tag")) { Text("3/5", style = AppType.Headline, color = appColors().text1) }"""
        val hoistedFailure = assertFailsWith<AssertionError> {
            textCallContaining(quoted("probe_tag"), hoistedTag)
        }
        val hoistedMessage = hoistedFailure.message ?: ""
        assertTrue(
            "包裹容器" in hoistedMessage,
            "② tag 被上提到包裹容器时取样器没有响亮判红（或消息没写明绕过方式）：实际消息「$hoistedMessage」",
        )

        // ③ 同一 tag 出现在两枚 Text( 内：歧义命中必须红
        val duplicatedTag = """Column { Text("3/5", style = AppType.Display, color = appColors().text1, modifier = Modifier.testTag("probe_tag")); """ +
            """Text("2/5", style = AppType.Footnote, color = appColors().text2, modifier = Modifier.testTag("probe_tag")) }"""
        val duplicateFailure = assertFailsWith<AssertionError> {
            textCallContaining(quoted("probe_tag"), duplicatedTag)
        }
        assertTrue(
            "歧义" in (duplicateFailure.message ?: ""),
            "③ 同一标签命中两枚 Text( 调用时取样器没有判红（静默取第一个 = 假绿入口）：实际消息「${duplicateFailure.message}」",
        )

        // ④ 真实源码逐枚自证：每枚受判标签都要回含自身，且六枚窗口两两不同
        val sampledTags = listOf(
            "today_plan_total_progress",
            "today_plan_learned_percent",
            "today_plan_book_words",
            "today_plan_book_unlearned",
            "today_plan_progress",
            "today_plan_empty",
        )
        val realWindows = sampledTags.map { tag -> tag to textCallContaining(quoted(tag)) }
        realWindows.forEach { (tag, window) ->
            assertTrue(quoted(tag) in window, "④ $tag 的窗口没有回含自身标签（取样器对本屏失明）：$window")
        }
        assertEquals(
            sampledTags.size,
            realWindows.map { it.second }.distinct().size,
            "④ 六个受判标签取回了重复窗口——取样器把不同站点归到了同一枚 Text( 调用上",
        )

        // 谓词自检（旧有内容，保留）：缺 color 的那枚必须被中性墨谓词判红、补齐的那枚必须放过。
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

    /**
     * 取样器自身的三条响亮负控（批 2 终审 C1 评审 I-1 / I-3 / I-4：旧写法在**缺锚**与**未闭合**时
     * 都不报错，只是静默换取样面——`substringAfter` 回退整份原文、`substringBefore("\n}")` 吞到文末）：
     *  ① 锚缺失 ⇒ [bodyOf] 红并点名「不在位」；
     *  ② 花括号未闭合 ⇒ 红并点名「未闭合」，而不是给出吞到文末的区间；
     *  ③ 锚以 `(` 收尾而形参表未闭合 ⇒ 红并点名「形参表」。
     * 开头另喂一枚合法闭合样片作正控：证明失败来自区间界定，而不是夹具读不了源码。
     */
    @Test
    fun samplers_fail_loudly_when_the_anchor_is_missing_or_the_region_cannot_be_closed() {
        val closed = "private fun WorkloadKind.color(): Color = when (this) {\n" +
            "    WorkloadKind.REVIEW -> appDomain(AppDomain.Review).deep\n}"
        val positive = bodyOf(closed, "private fun WorkloadKind.color()", "合成自检：闭合样片")
        assertTrue(
            "AppDomain.Review" in positive,
            "正控：闭合样片的取体失败——夹具本身坏了，下面三条响亮报错也就无从谈起（实测体：$positive）",
        )

        val missingFailure = assertFailsWith<AssertionError> {
            bodyOf("private fun WorkloadKind.paint(): Color = Color.Red", "private fun WorkloadKind.color()", "合成自检：缺锚")
        }
        assertTrue(
            "不在位" in (missingFailure.message ?: ""),
            "① 锚缺失时取样器没有响亮判红（旧 `substringAfter` 会静默回退整份原文）：实际消息「${missingFailure.message}」",
        )

        val unclosedFailure = assertFailsWith<AssertionError> {
            bodyOf("is TodayPlanUiState.Ready -> {\n    Column { Text(\"x\") }", "is TodayPlanUiState.Ready -> {", "合成自检：未闭合")
        }
        assertTrue(
            "未闭合" in (unclosedFailure.message ?: ""),
            "② 分支体未闭合时取样器没有响亮判红（旧写法会吞到文末并照常判绿）：实际消息「${unclosedFailure.message}」",
        )

        val paramFailure = assertFailsWith<AssertionError> {
            bodyOf("private fun RowScope.CountCard(text: String, tag: String", "private fun RowScope.CountCard(", "合成自检：形参表")
        }
        assertTrue(
            "形参表" in (paramFailure.message ?: ""),
            "③ 形参表括号未闭合时必须响亮判红（否则体起点会猜错位置）：实际消息「${paramFailure.message}」",
        )
    }

    private companion object {
        /** 显式中性墨读法（字面形与 `val colors = appColors()` 别名形都算）。 */
        val NeutralInk = Regex("""\bcolor\s*=\s*(?:appColors\(\)\.|colors\.|set\.)(?:text1|text2)\b""")

        /** 任何强调色读法：`Mint*` 快捷名、静态/动态域色、以及状态色。 */
        val AccentInk = Regex("""\bcolor\s*=\s*(?:Mint\w+|AppPalette\.\w+|DomainColors\.\w+|appDomain\(|appColors\(\)\.(?:success|error|errorFill|onMint|tint\d*)|colors\.(?:success|error|errorFill|onMint))""")
    }
}
