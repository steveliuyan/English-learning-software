package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 21-C：今日阅读（原型 `sReadHome`，`assets/app.js:702-737`）的源码侧契约。
 *
 * 本轮只补原型画到、本屏读得到的**那一小块**：今日文章卡上的两枚 chip——「覆盖 N 词」与版本号，
 * 都取 `Article` 自己的真字段（`coveredLemmas.size` / `version`）。
 *
 * 三处仍然不搬（逐条记在这里，后续同屏对齐时按这份清单继续）：
 *  1. 第三枚 chip「未读 / 已读」：`Article` 没有阅读态字段（阅读历史屏同口径，Task 21-B 第 3 行）；
 *  2. `section('继续')` 里的「双语对照」入口：本仓的对照态由 `ArticleReadingScreen` 内部切换，
 *     没有独立入口路由，另开一条属功能改动；
 *  3. `section('本周阅读')` 的「目标是每周 5 篇，还差 2 篇」：目标是原型 mock，本仓没有「每周几篇」的配置。
 */
class ReadingHomeContractTest {

    private val screen: String by lazy { uiScreenSource("ReadingAccessScreen.kt") }

    @Test
    fun `the today card carries the covered word count and the version from the article itself`() {
        assertTrue(
            Regex("""覆盖 \$\{state\.todayArticle\.coveredLemmas\.size\} 词""").containsMatchIn(screen),
            "「覆盖 N 词」取 `coveredLemmas.size`（真字段），不是原型 mock 的 4",
        )
        assertTrue(
            Regex("""第 \$\{state\.todayArticle\.version\} 版""").containsMatchIn(screen),
            "版本号取 `Article.version`（真字段）",
        )
        assertEquals(1, countMatches(screen, Regex("""reading_today_meta""")), "这行有自己的锚，恰一处")
    }

    @Test
    fun `the read state chip stays off because nothing records it`() {
        assertEquals(0, countMatches(screen, Regex("""已读""")), "「已读」无字段，不印假状态")
        assertEquals(0, countMatches(screen, Regex("""未读""")), "「未读」无字段，不印假状态")
    }

    @Test
    fun `the weekly section only reports derived facts and carries no mock target`() {
        assertTrue("SectionHeading(text = \"本周阅读\")" in screen, "节标题逐字（`app.js:730`）")
        assertEquals(1, countMatches(screen, Regex("""\bQuietCard\(""")), "统计卡走 `.card-quiet` 同义的共享 `QuietCard`")
        assertTrue("todayLocalDate.minusDays(6)" in screen, "窗口是 [今天-6, 今天]：基准日由宿主注入，屏内不读钟")
        assertTrue(
            "近 7 天：生成 ${'$'}{weekArticles.size} 篇" in screen,
            "只报派生事实：近 7 天的篇数",
        )
        assertTrue(
            "共 ${'$'}weekWords 词" in screen,
            "只报派生事实：近 7 天的总词数",
        )
        listOf("目标是每周", "还差").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "「$token」是原型 mock（本仓没有每周篇数配置），不写死",
            )
        }
    }
}
