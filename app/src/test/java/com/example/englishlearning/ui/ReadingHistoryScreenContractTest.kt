package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 21-B：阅读历史（原型 `sReadHistory`，`assets/app.js:796-813`）的源码侧契约。
 *
 * 原型骨架：`barTop('阅读历史')` → 每个日期一枚 `section(h.date, …)`，组内逐版一行
 * （`st(read ? 'newspaper' : 'document', 44)` + 「type · length · 第 v 版」+「words 词 · at 生成」+
 * 右尾 `已读 / 未读`）→ 尾部 `.mt5` 提示「同一天可以重新生成文章；旧版本不会被覆盖，都在这份列表里。」
 *
 * 本屏已有的、原样保留：共享页头、懒列表、**同组多版本折叠**（F4-01 决策 §8 的「看得见」：每组
 * 单版直开、多版展开逐版「重读 / 删除此版」）——原型画的是平铺，本仓的分组是产品决策（删除粒度按版本），
 * 不是漏实现，所以折叠形状不动。本轮补两处原型画到、本屏还缺的：
 *  1. **日期分节**：原型按 `h.date` 分节；本屏此前把日期塞在每行的副标题里。
 *  2. **尾部那句提示**：本仓**成立**（旧版本确实不被覆盖、都在这份列表里），逐字搬。
 *
 * 一处不搬：右尾「已读 / 未读」——`Article` 没有阅读态字段（`reading/domain/Article.kt` 只有
 * `localDate / articleType / lengthTier / version / title / englishText / chineseText /
 * generatedAtEpochMillis / coveredLemmas / source`），拿「有 chineseText 就算读过」这类推断印上去就是编造。
 */
class ReadingHistoryScreenContractTest {

    private val screen: String by lazy { uiScreenSource("ReadingHistoryScreen.kt") }

    @Test
    fun `history is sectioned by date like the prototype`() {
        assertTrue(
            "groupBy { it.localDate }" in screen,
            "列表必须按 `localDate` 分节（原型 `D.history.map(h => section(h.date, …))`）",
        )
        assertEquals(
            1,
            countMatches(screen, Regex("""SectionHeading\(text = date\)""")),
            "日期节标题恰一处，且取真实 `localDate`（不是写死的日期）",
        )
        // 行副标题仍是 `article.localDate`：`ReadingHistoryLayoutContractTest` 有既建立法
        // 「单版本条目的副标题是日期（这一行唯一的定位信息）」，分节标题只做**额外的**分组，
        // 不改行内定位——两处都有日期不构成信息冲突（节标题是导航，行副标题是条目自身）。
        assertTrue("subtitle = article.localDate" in screen, "单版本行的副标题仍是日期（既有立法不变）")
    }

    @Test
    fun `the version grouping stays as the product decided`() {
        assertTrue("it.versionGroupKey" in screen, "同组多版本折叠（F4-01 决策 §8）保留：分组键仍是 `versionGroupKey`")
        assertTrue("reading_history_group_\$groupKey" in screen, "组行锚保留")
        assertTrue("reading_version_delete_" in screen, "逐版「删除此版」入口保留（原型没有的分组能力，属产品决策）")
    }

    @Test
    fun `the tail note is carried because it is true here`() {
        assertTrue(
            "同一天可以重新生成文章；旧版本不会被覆盖，都在这份列表里。" in screen,
            "尾部提示逐字（`app.js:811`）：本仓旧版本确实不被覆盖、都在这份列表里，这句成立",
        )
        assertEquals(1, countMatches(screen, Regex("""reading_history_notice""")), "尾部提示有自己的锚，恰一处")
    }

    @Test
    fun `the read state stays off because nothing records it`() {
        listOf("已读", "未读", "readAt", "isRead").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "「$token」本仓读不到（`Article` 没有阅读态字段），不印假状态",
            )
        }
    }
}
