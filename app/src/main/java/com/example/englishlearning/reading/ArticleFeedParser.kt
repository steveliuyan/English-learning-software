package com.example.englishlearning.reading

/**
 * RSS 2.0 索引里的一条候选文章。正文不在这里——[summary] 只有站点给的一句话摘要，
 * 真正文要再请求 [articleUrl] 指向的文章页（两次请求，见计划「已实测的来源事实」）。
 */
data class FeedItem(
    val title: String,
    val articleUrl: String,
    val publishedAtEpochMillis: Long,
    val summary: String,
)

/**
 * RSS 索引解析器。手写标签扫描（零新依赖是硬约束）：逐个切 `<item>…</item>` 块，
 * 块内取**首个** `title`/`description`/`link`/`pubDate`。
 *
 * 宽严尺度：缺 `link` 的 item 跳过（没有链接就没有正文来源，留标题无意义）；
 * `pubDate` 解析失败不致命（记 0）；空 channel 是**空列表而不是错误**（站点真有
 * 空分区，zoneid=965 实测如此）；无 `<channel>` 或非 XML 输入才是失败。
 */
object ArticleFeedParser {
    private val rfc1123 = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

    fun parse(xml: String): Result<List<FeedItem>> = runCatching {
        val channelStart = xml.indexOf("<channel>")
        if (channelStart < 0) throw IllegalArgumentException("not an RSS document")
        val items = mutableListOf<FeedItem>()
        var cursor = channelStart
        while (true) {
            val itemStart = xml.indexOf("<item>", cursor)
            if (itemStart < 0) break
            val itemEnd = xml.indexOf("</item>", itemStart)
            if (itemEnd < 0) break
            val block = xml.substring(itemStart + "<item>".length, itemEnd)
            cursor = itemEnd + "</item>".length

            val link = firstTag(block, "link") ?: continue
            val title = decodeEntities(firstTag(block, "title").orEmpty())
            // 真实站点的 description 带尾随空格（2026-09-24 实测），trim 后进领域。
            val summary = decodeEntities(firstTag(block, "description").orEmpty()).trim()
            val publishedAt = firstTag(block, "pubDate")
                ?.let { runCatching { java.time.ZonedDateTime.parse(it.trim(), rfc1123).toInstant().toEpochMilli() }.getOrDefault(0L) }
                ?: 0L
            items += FeedItem(title = title, articleUrl = link.trim(), publishedAtEpochMillis = publishedAt, summary = summary)
        }
        items
    }

    private fun firstTag(block: String, tag: String): String? {
        val open = "<$tag>"
        val start = block.indexOf(open)
        if (start < 0) return null
        val end = block.indexOf("</$tag>", start)
        if (end < 0) return null
        return block.substring(start + open.length, end)
    }

    /** 只解码 XML 规范要求与站点实际使用的实体；其余原样保留，绝不吞字。 */
    internal fun decodeEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
}
