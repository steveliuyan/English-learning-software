package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 21-D：阅读正文 / 双语对照（原型 `sReadArticle` `app.js:738-761`、
 * `sReadTranslate` `app.js:762-778`）的源码侧契约。
 *
 * 两屏在本仓是**同一支 `ArticleReadingScreen` 的两种呈现**（正文态 ↔ 对照态由
 * `article_translation_toggle` 切换），这一形状与同类开源项目（LWT / LinguaCafe：翻译在阅读器内
 * 切换/逐词注记，没有独立「双语页」）一致，所以不新开页面。
 *
 * 本轮只补原型 `.article-meta` 读得到的那一枚（词数 + 版本）；另两处不搬：
 *  1. 「5 分钟」——本仓没有阅读速度口径，按固定词/分钟换算就是编造；
 *  2. `.article-meta` 里的 `source`——本屏来源块已单独成块（`article_source_name` /
 *     `article_attribution`），再在元信息行重复一遍是同一条信息第二遍。
 */
class ArticleReadingScreenContractTest {

    private val screen: String by lazy { uiScreenSource("ArticleReadingScreen.kt") }

    @Test
    fun `the article meta reports only the word count and the version`() {
        assertEquals(1, countMatches(screen, Regex("""article_meta""")), "元信息行恰一处")
        assertTrue(
            Regex("""article\.englishText\.split\(Regex\("\\\\s\+"\)\)\.count""").containsMatchIn(screen),
            "词数按正文空白切分真算（不是原型 mock 的 214）",
        )
        assertTrue(
            Regex("""第 \$\{article\.version\} 版""").containsMatchIn(screen),
            "版本号取 `Article.version`（真字段）",
        )
    }

    @Test
    fun `the fabricated reading time stays off the screen`() {
        listOf("5 分钟", "214 词").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "「$token」读不到：本仓没有阅读速度口径，不写死",
            )
        }
    }

    @Test
    fun `the two presentations stay inside one reader screen`() {
        assertEquals(1, countMatches(screen, Regex("""(?<![A-Za-z0-9_])fun ArticleReadingScreen\(""")), "正文态与对照态同属一支 `ArticleReadingScreen`")
        assertTrue("article_translation_toggle" in screen, "两态之间的切换仍由阅读器内那枚开关承担（无独立「双语页」）")
    }
}
