package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 21-A：外刊选读（原型 `sReadFeed`，`assets/app.js:779-795`）的源码侧契约。
 *
 * 原型骨架：`barTop('外刊选读')` → 一行 `.meta`「来自已登记的公开学习类来源，正文不含广告。」
 * → `.rows` 里每条 `row`（`st('newspaper',44)` + 标题 + `section · length · words 词` + 右尾 `when`）
 * → `section('来源说明', .card-quiet)`（VOA 公共领域 + 「我的 · 开源许可与来源」）。
 *
 * 本屏早前已换过骨架（页头共享、条目是裸行、失败态可重试），本轮只把**原型画到、本屏还没有**的那一段
 * 补上，并把三处读不到的东西登记成偏离：
 *  1. 条目的 `section · length · words 词` 副案**不搬**：`FeedItem`（`reading/ArticleFeedParser.kt`）
 *     只有 `title / articleUrl / publishedAtEpochMillis / summary`，没有栏目、篇幅、词数三枚字段
 *     （改数据形状撞「数据层零改动」，按 `summary.length` 糊字数就是编造）。
 *  2. 原型那句 `.meta`「正文不含广告」不搬：这是对**来源页面**的断言，本仓只读到条目元数据，
 *     无法证明；换成本屏真有的那枚来源署名（`sourceDisplayName`，`FeedUiState.Ready` 带）。
 *  3. 「VOA Learning English 的内容属公共领域」这句**不写死**：来源是可配置的，写死 VOA 会在换成
 *     别的来源时变成假话；来源说明只保留「按来源许可仅供学习使用」+ 指向既有的「开源许可与来源」页。
 *
 * 读法是源码文本（[uiScreenSource] 已剥注释）；渲染侧文案与可点集合由 connected
 * `ReadingFeedScreenTest` 承担。
 */
class ReadingFeedScreenContractTest {

    private val screen: String by lazy { uiScreenSource("ReadingFeedScreen.kt") }

    @Test
    fun `the feed keeps the shared header and one quiet source notice`() {
        assertEquals(1, countMatches(screen, Regex("""AppScreenHeader\(""")), "页头仍恰一处共享 `AppScreenHeader`（原型 `barTop`）")
        assertTrue("title = \"外刊选读\"" in screen, "页名逐字（`app.js:781`）")
        assertEquals(1, countMatches(screen, Regex("""SectionHeading\(text = "来源说明"\)""")), "节标题逐字（`app.js:792`）且恰一处")
        assertEquals(1, countMatches(screen, Regex("""\bQuietCard\(""")), "来源说明走 `card-quiet` 同义的共享 `QuietCard`")
    }

    @Test
    fun `the source notice only states what the feed actually knows`() {
        assertTrue(
            Regex("""\$\{feed\.sourceDisplayName\}""").containsMatchIn(screen),
            "来源说明必须取真实来源名（`FeedUiState.Ready.sourceDisplayName`），不是写死的 VOA",
        )
        listOf("VOA", "公共领域", "正文不含广告", "4 篇").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "「$token」读不到或无法证实：不写死进本屏（文件头第 2、3 条）",
            )
        }
        listOf("section", ".length", "words 词").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "条目副案里的「$token」字段本仓没有（文件头第 1 条），不补假字段",
            )
        }
    }

    @Test
    fun `the feed rows stay bare rows with their own anchors`() {
        assertEquals(1, countMatches(screen, Regex("""(?<![A-Za-z0-9_])BareRow\(""")), "列表条目仍是一条 `BareRow`（单一循环调用点）")
        assertTrue("feed_item_\$index" in screen, "条目锚 `feed_item_\$index` 必须保留（connected 逐条按它取节点）")
        assertTrue("feed_source" in screen, "来源署名那一格仍带自己的锚")
    }
}
