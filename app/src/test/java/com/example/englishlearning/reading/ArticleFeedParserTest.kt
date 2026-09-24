package com.example.englishlearning.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleFeedParserTest {
    /** 真实抓取的缩减样本（2026-09-24，zoneid=1579），见资源文件头注释。 */
    private val feed = javaClass.getResourceAsStream("/voa/feed-zone1579-reduced.xml")!!
        .readBytes().decodeToString()

    @Test
    fun extractsEveryItemWithItsFields() {
        val items = ArticleFeedParser.parse(feed).getOrThrow()
        assertEquals(3, items.size) // 4 个 item，缺 link 的一个被跳过
        val first = items[0]
        assertEquals("Wilbur and Orville Wright: The First Airplane", first.title)
        assertEquals(
            "https://learningenglish.voanews.com/a/wilbur-and-orville-wright-the-first-airplane/7998765.html",
            first.articleUrl,
        )
        assertEquals(
            "Wilbur and Orville Wright quietly developed a powered flying machine that proved heavier-than-air flight was possible more than 120 years ago.",
            first.summary,
        )
        // Mon, 17 Mar 2025 22:05:00 +0000
        assertEquals(1742249100000L, first.publishedAtEpochMillis)
    }

    @Test
    fun decodesXmlEntitiesInTitles() {
        val items = ArticleFeedParser.parse(feed).getOrThrow()
        assertEquals("Quizzes & Lessons: Study Skills", items[2].title)
        assertTrue(items[2].summary.contains("to \"Quizzes & Lessons\""))
    }

    @Test
    fun skipsItemsWithoutALink() {
        val titles = ArticleFeedParser.parse(feed).getOrThrow().map { it.title }
        assertTrue(titles.none { it.contains("without a link") })
    }

    @Test
    fun treatsAnEmptyChannelAsEmptyListRatherThanAnError() {
        val empty = "<rss version=\"2.0\"><channel><title>zoneid=965 返回的空 channel</title></channel></rss>"
        assertEquals(emptyList<FeedItem>(), ArticleFeedParser.parse(empty).getOrThrow())
    }

    @Test
    fun failsOnNonRssContent() {
        assertTrue(ArticleFeedParser.parse("<html><body>not a feed</body></html>").isFailure)
        assertTrue(ArticleFeedParser.parse("").isFailure)
    }
}
