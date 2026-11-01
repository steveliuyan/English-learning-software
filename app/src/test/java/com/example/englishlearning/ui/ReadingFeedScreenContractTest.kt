package com.example.englishlearning.ui

import java.io.File
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
 *     别的来源时变成假话；来源说明只保留「按来源许可仅供学习使用」+ 指向许可页。原型那一句里的页名是
 *     许可页**旧名**（本仓已改名），本屏按 `LicensesScreen` 页头的现名引用——见
 *     `the source notice names the licenses page by its live title`。
 *
 * 读法是源码文本（[uiScreenSource] 已剥注释）；渲染侧文案与可点集合由 connected
 * `ReadingFeedScreenTest` 承担。
 */
class ReadingFeedScreenContractTest {

    private companion object {
        /** 许可页在 U-5/U-12 已逐字改名的旧名；本仓不存在这一页，屏上引用即死链。 */
        const val STALE_LICENSES_PAGE_NAME = "开源许可与来源"

        fun occurrences(text: String, phrase: String): Int = Regex(Regex.escape(phrase)).findAll(text).count()

        /** 许可页的**现名**从它自己的页头现读：判据里再抄一份字面量就多一枚会漂的副本。 */
        fun licensesPageTitle(): String {
            val file = File(UI_MAIN_SOURCE_PATH).resolve("LicensesScreen.kt")
            assertTrue(file.isFile, "LicensesScreen.kt 读不到（${file.path}，工作目录 ${File(".").absolutePath}）")
            return Regex("""AppScreenHeader\(\s*title = "([^"]+)"""").find(file.readText())?.groupValues?.get(1)
                ?: error("许可页页头标题抽不到：页头形状变了就同步改这条判据，不许放宽断言")
        }
    }

    private val screen: String by lazy { uiScreenSource("ReadingFeedScreen.kt") }

    /** 整份源码（**含注释**）：死页名留在注释里就是下一批的抄写模板，口径同 [AboutRowNameIsJustAboutTest]。 */
    private val rawScreen: String by lazy {
        val file = File(UI_MAIN_SOURCE_PATH).resolve("ReadingFeedScreen.kt")
        assertTrue(file.isFile, "ReadingFeedScreen.kt 读不到（${file.path}，工作目录 ${File(".").absolutePath}）")
        file.readText()
    }

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

    /**
     * U-6 收口盘点发现、2026-11-01 用户拍板「现在修」：本屏那句「来源说明」指向的页面早就不叫那个名字了。
     *
     * 判据不写死「应当叫什么」，而是从许可页自己的页头现读现比——下次谁再改名，这条会红着提醒两处以
     * 一致的方式改（写死期望值只会让第二处副本跟着漂）。
     */
    @Test
    fun `the source notice names the licenses page by its live title`() {
        assertEquals(
            0,
            occurrences(rawScreen, STALE_LICENSES_PAGE_NAME),
            "死页名「$STALE_LICENSES_PAGE_NAME」必须在本屏整份源码（含注释）归零：" +
                "许可页已逐字改名，那句现在把用户指向一间没有的房间；注释里的旧名还会被下一批照抄",
        )
        assertEquals(
            1,
            occurrences(rawScreen, "「我的 → 关于 → ${licensesPageTitle()}」"),
            "来源说明必须逐字引用许可页现名（现读自 `LicensesScreen` 页头）且恰一处：" +
                "改名不等于删指引，用户仍要从这屏跳得回来源台账；路径要写到实际落地的那一跳（关于是中间层）",
        )
    }

    /** 反空转：抽名正则与归零计数都要在合成串上真命中，否则上面那条对任何文件都恒绿。 */
    @Test
    fun `the live-title gate reads the name from the licenses screen and is not vacuous`() {
        val licensesSource = File(UI_MAIN_SOURCE_PATH).resolve("LicensesScreen.kt").readText()
        assertEquals(
            1,
            countMatches(licensesSource, Regex("""AppScreenHeader\(""")),
            "许可页页头恰一处：有多枚 `AppScreenHeader` 时「取第一枚」会静默抽到别的名字",
        )
        assertEquals(
            "开源许可与隐私说明",
            Regex("""AppScreenHeader\(\s*title = "([^"]+)"""")
                .find("AppScreenHeader(\n            title = \"开源许可与隐私说明\",\n")?.groupValues?.get(1),
            "反空转：抽名正则必须真能从页头形状里取出标题",
        )
        assertEquals(
            1,
            occurrences("App 内的来源台账见「我的 · $STALE_LICENSES_PAGE_NAME」。", STALE_LICENSES_PAGE_NAME),
            "反空转：合成 stale 句必须被计到一次（计数口径本身不能是空转的）",
        )
    }
}
