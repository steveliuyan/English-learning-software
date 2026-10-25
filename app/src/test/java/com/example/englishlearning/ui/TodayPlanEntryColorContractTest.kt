package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 27（裁决 22-2）的今日计划入口组契约：**同组同色 + 行形互不相同**。
 *
 * 背景是真机取证的两条：一屏九块彩色容器让用户读成「颜色太杂」；`Gear`/`List` 相邻两行在
 * `StrokeOnly` 档下都是三条横线（`entry-group.png`）。裁决把入口行收成三组颜色
 * （学习 `Learn` / 记录 `Review` / 系统 `Settings`），并要求同组内字形仍然互不相同
 * ——颜色收敛不能顺手把「两行长得一样」留下来。
 *
 * 2026-10-25 简化批删掉了「学习工具与设置」行：它落的设置 tab 与紧随其后的「调整词书与目标」
 * 同属系统组，而用户从底部导航一步就能到设置页，组内这一行是同一目的地的第二入口。
 * 行数从六降到五，判据本身（组内同域、跨组换色、组内字形互异）一字未松。
 * 裁决见 `docs/decisions/2026-10-25-ui-simplification-deletions.md`。
 *
 * 读法是源码文本（先过 `stripComments`），不渲染 Compose：本仓 JVM 单测没有渲染运行时，
 * 同组同色是「具名实参相同」这件事，文本即可判定。像素侧由 connected 用例承担。
 */
class TodayPlanEntryColorContractTest {

    /**
     * 逐行钉值：标题 → (字形, 域)。顺序即屏幕上的顺序。
     *
     * `学习记录` 取 `List`（事件流水），`调整词书与目标` 取 `Book`（词书实体）——裁决 22-1 只点名
     * 「`Star`→`Chart`」与「`Clock` 归提醒行」，没给 `学习记录` 指定替代形；若两行都用 `Chart`
     * 会在同组内重新造出一对相邻同形，与本裁决的立法目的相反。故 `Chart` 只给打卡页，
     * `学习记录` 走 `List`、`调整词书与目标` 从 `List` 让位给 `Book`，组内字形互异。
     */
    private val expectedRows = listOf(
        Row("词书单词", "BookOpen", "Learn"),
        Row("我的词", "Bookmark", "Review"),
        Row("学习记录", "List", "Review"),
        Row("打卡与成就", "Chart", "Review"),
        Row("调整词书与目标", "Book", "Settings"),
    )

    /** 分组期望：三组、组内同域。 */
    private val expectedGroups = mapOf(
        "Learn" to listOf("词书单词"),
        "Review" to listOf("我的词", "学习记录", "打卡与成就"),
        "Settings" to listOf("调整词书与目标"),
    )

    @Test
    fun entry_rows_keep_one_domain_per_group_and_distinct_glyphs() {
        val source = source()
        val rows = entryRows(entryGroupSource(source))
        assertEquals(
            expectedRows.size,
            rows.size,
            "今日计划入口行应为 ${expectedRows.size} 行（查词行在组外单独断言），实际 ${rows.size}：$rows",
        )
        expectedRows.forEach { want ->
            val got = rows.firstOrNull { it.title == want.title }
                ?: throw AssertionError("入口组缺「${want.title}」行：$rows")
            assertEquals(want.icon, got.icon, "「${want.title}」的字形应为 ${want.icon}，实为 ${got.icon}")
            assertEquals(want.domain, got.domain, "「${want.title}」的域色应为 ${want.domain}，实为 ${got.domain}")
        }

        // 同组同色：一组的行必须恰好一个域名，跨组必须换色（否则「分组」只是口号）。
        val groups = rows.groupBy { it.domain }.mapValues { (_, group) -> group.map { it.title } }
        assertEquals(expectedGroups, groups, "今日计划入口组的分组与裁决 22-2 不符")
        assertEquals(3, groups.size, "入口组应恰好 3 个域色，实际 ${groups.size}：${groups.keys}")

        // 组内字形互异：颜色收敛不能留下相邻同形。
        val icons = rows.map { it.icon }
        assertEquals(icons.size, icons.distinct().size, "入口组出现同形字形：$icons")

        // Star 已撤出今日计划（裁决 22-1 第二步）；Chart 归打卡行。
        assertTrue("Star" !in icons, "「Star」仍在今日计划入口组：裁决 22-1 已把它撤下 —— $icons")
        assertEquals("Chart", rows.first { it.title == "打卡与成就" }.icon, "打卡与成就必须回稿定稿形 Chart")

        // 入口行不得再拿 Reading 当域色（该域留给阅读内容本身，不参与入口编码）。
        assertEquals(
            0,
            countMatches(source, Regex("""domain\s*=\s*AppDomain\.Reading""")),
            "入口组仍在用阅读域色",
        )
    }

    /** 查词行在组外（四态常驻），但它的域色与字形同样是入口口径的一部分。 */
    @Test
    fun persistent_search_row_stays_outside_the_group_and_keeps_its_own_domain() {
        val source = source()
        val search = entryRows(source).firstOrNull { it.title == "查词" }
            ?: throw AssertionError("缺常驻查词行：${entryRows(source)}")
        assertEquals("Search", search.icon, "查词行字形应为 Search")
        assertEquals("Library", search.domain, "查词行域色应为 Library（词汇域）")
        val groupStart = source.indexOf("private fun TodayPlanEntryGroup(")
        assertTrue(groupStart > 0, "找不到 TodayPlanEntryGroup：入口组契约失去对象")
        val searchAt = source.indexOf("title = \"查词\"")
        assertTrue(searchAt in 0 until groupStart, "查词行被挪进了入口组：它必须四态常驻在组外")
    }

    // ---------- 解析 ----------

    private data class Row(val title: String, val icon: String, val domain: String)

    private fun source(): String = uiScreenSource("TodayPlanScreen.kt")

    /** 入口组函数体：从 `TodayPlanEntryGroup(` 到下一个 `private fun` 之前（查词行不在其中）。 */
    private fun entryGroupSource(source: String): String {
        val start = source.indexOf("private fun TodayPlanEntryGroup(")
        assertTrue(start >= 0, "找不到 TodayPlanEntryGroup：入口组契约失去对象")
        val next = source.indexOf("private fun", start + 1)
        return if (next == -1) source.substring(start) else source.substring(start, next)
    }

    /**
     * 从源码里切出每一枚 `EntryRow(...)` 调用，取 `title` / `icon` / `domain` 三个具名实参。
     * 用括号配平切段（参数里嵌 `Modifier.padding(...)` / `semantics { ... }` 不会截断）。
     */
    private fun entryRows(source: String): List<Row> = buildList {
        var index = source.indexOf("EntryRow(")
        while (index >= 0) {
            val open = source.indexOf('(', index)
            val close = matchingParen(source, open)
                ?: throw AssertionError("EntryRow( 的圆括号未配平：${source.length} 字文本")
            val call = source.substring(open + 1, close)
            val title = Regex("""title\s*=\s*"([^"]+)"""").find(call)?.groupValues?.get(1)
            val icon = Regex("""icon\s*=\s*AppIcon\.(\w+)""").find(call)?.groupValues?.get(1)
            val domain = Regex("""domain\s*=\s*AppDomain\.(\w+)""").find(call)?.groupValues?.get(1)
            if (title != null && icon != null && domain != null) add(Row(title, icon, domain))
            index = source.indexOf("EntryRow(", close)
        }
    }

    private fun matchingParen(text: String, open: Int): Int? {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    // ---------- 反空转 ----------

    @Test
    fun parser_is_not_vacuous() {
        val good = """
            EntryRow(
                icon = AppIcon.Chart,
                domain = AppDomain.Review,
                title = "打卡与成就",
                modifier = Modifier.testTag("x").semantics { contentDescription = "查看打卡与成就" },
                onClick = {},
            )
        """.trimIndent()
        assertEquals(listOf(Row("打卡与成就", "Chart", "Review")), entryRows(good), "合成正片段漏判：合法入口行没被切出来")
        assertEquals(0, entryRows("EntryRow(icon = AppIcon.Star)").size, "合成负片段被放行：缺 title/domain 的调用也算一行")

        // 同组同色判据本身要抓得住「同组里混进第二个域」的老形状。
        val mixed = """
            EntryRow(icon = AppIcon.Clock, domain = AppDomain.Learn, title = "学习记录")
            EntryRow(icon = AppIcon.Chart, domain = AppDomain.Review, title = "打卡与成就")
        """.trimIndent()
        val mixedGroups = entryRows(mixed).groupBy { it.domain }
        assertEquals(2, mixedGroups.size, "合成负例失控：两域两行竟没被读成两组")
        assertTrue(
            mixedGroups.getValue("Learn").map { it.title } != expectedGroups.getValue("Review").take(1),
            "合成负例失控：旧域色与期望分组无法区分",
        )

        // Star 禁令与 Chart 归位同样不是空话。
        assertTrue("Star" !in expectedRows.map { it.icon }, "钉值表自己把 Star 留在入口组")
        assertEquals(1, expectedRows.count { it.icon == "Chart" }, "Chart 必须只归一行为入口字形（相邻同形的教训）")
    }
}
