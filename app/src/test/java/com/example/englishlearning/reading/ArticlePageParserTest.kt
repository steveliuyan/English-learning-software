package com.example.englishlearning.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticlePageParserTest {
    /** 真实抓取的缩减样本（2026-09-24，文章 7998765），见资源文件头注释。 */
    private val page = javaClass.getResourceAsStream("/voa/article-7998765-reduced.html")!!
        .readBytes().decodeToString()

    private fun parse() = ArticlePageParser.parse(page).getOrThrow()

    @Test
    fun extractsTheTitleAndTheParagraphsFromTheArticleBody() {
        val parsed = parse()
        assertEquals("Wilbur and Orville Wright: The First Airplane", parsed.title)
        val paragraphs = parsed.body.split("\n\n")
        assertTrue(
            paragraphs.contains(
                "Wilbur and Orville Wright are the American inventors who made a small engine-powered flying machine. " +
                    "They proved that flight without the aid of gas-filled balloons was possible.",
            ),
        )
        assertEquals(9, paragraphs.size) // 7 个有效 p + 2 个 h2 文本（分隔线段已丢弃）
    }

    @Test
    fun dropsTheAudioPlayerEmbed() {
        val body = parse().body
        // 嵌入块里的任何痕迹都不得进入正文。
        assertTrue(!body.contains("mp3"), "body leaks mp3 links")
        assertTrue(!body.contains("data-player_id"), "body leaks player attributes")
        assertTrue(!body.contains("c-player"), "body leaks player classes")
    }

    @Test
    fun dropsTheUnderscoreSeparatorParagraph() {
        val paragraphs = parse().body.split("\n\n")
        assertTrue(paragraphs.none { it.all { ch -> ch == '_' || ch.isWhitespace() } })
    }

    @Test
    fun decodesHtmlEntities() {
        val body = parse().body
        assertTrue(body.contains("the \"flying machine\" would change travel & trade forever"))
        assertTrue(body.contains("world's first flight"))
        // 未收录的实体原样保留，绝不吞字。
        assertTrue(body.contains("&ndash;"))
    }

    @Test
    fun stripsInnerTagsFromParagraphs() {
        val body = parse().body
        assertTrue(body.contains("The Wright brothers did many tests with gliders at Kitty Hawk."))
        assertTrue(!body.contains("<strong>"))
        assertTrue(!body.contains("<em>"))
        assertTrue(!body.contains("<a href"))
        assertTrue(body.contains("Art & Culture lessons from learningenglish.voanews.com: engine"))
    }

    @Test
    fun keepsHeadingsAsPlainText() {
        val paragraphs = parse().body.split("\n\n")
        assertTrue(paragraphs.contains("Quiz - Wilbur and Orville Wright: The First Airplane"))
        assertTrue(paragraphs.contains("Words in This Story"))
    }

    @Test
    fun failsWhenThereIsNoBodyContainer() {
        assertTrue(ArticlePageParser.parse("<html><body><p>no wsw here</p></body></html>").isFailure)
    }

    @Test
    fun isDeterministic() {
        assertEquals(parse(), parse())
    }
}
