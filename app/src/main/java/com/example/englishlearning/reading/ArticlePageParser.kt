package com.example.englishlearning.reading

/** 文章页提取结果。[body] 为纯文本，段落以 `\n\n` 分隔，标题与其余段落平级。 */
data class ParsedArticlePage(val title: String, val body: String)

/**
 * 文章页正文提取器。手写**深度计数扫描器**（零新依赖是硬约束，且这里需要的结构非常窄）：
 *
 * 1. 定位 `<div class="wsw">`，用「数 `<div` / `</div>`」定位它的结束位置，切出容器片段；
 * 2. **先**逐块剔除 `class="wsw__embed"` 的整块 div（音频播放器，内含 mp3 链接与多层
 *    嵌套 div，2026-09-24 实测），再提取 `<p>` 与 `<h2>`；
 * 3. 段内剥掉所有 `<…>` 标签，解码六类常用实体（其余原样保留，绝不吞字）；
 * 4. 丢弃只由 `_`、空白组成的分隔段（站点用它们当视觉分隔线）。
 *
 * 脆弱性已知：站点改版会让本解析器失效，所以抓取路径的真实站点验证是例行项。
 */
object ArticlePageParser {
    fun parse(html: String): Result<ParsedArticlePage> = runCatching {
        val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.trim()
            ?: throw IllegalArgumentException("no <title> element")
        val container = extractWswContainer(html) ?: throw IllegalArgumentException("no div.wsw container")
        val cleaned = dropEmbedBlocks(container)
        val body = extractBlocks(cleaned)
            .map { (raw, isHeading) -> stripTags(raw, isHeading) }
            .filter { it.isNotEmpty() }
            .filterNot { line -> line.all { it == '_' || it.isWhitespace() } }
            .joinToString("\n\n")
        ParsedArticlePage(title, body)
    }

    /**
     * 深度计数定位 `div class="wsw"` 的结束；找不到返回 null。
     * **每个候选独立尝试**：第一个命中可能是注释或文本里的字面量（本仓库的测试夹具
     * 头注释就真踩过一次），深度计数走到底没有闭合就继续找下一个候选。
     */
    private fun extractWswContainer(html: String): String? {
        var searchFrom = 0
        while (true) {
            val start = html.indexOf("<div class=\"wsw\">", searchFrom)
            if (start < 0) return null
            val end = containerEnd(html, start)
            if (end != null) return html.substring(start, end)
            searchFrom = start + 1
        }
    }

    private fun containerEnd(html: String, start: Int): Int? {
        var depth = 0
        var index = start
        while (index < html.length) {
            val open = html.indexOf("<div", index)
            val close = html.indexOf("</div>", index)
            if (close < 0) return null
            if (open in 0 until close) {
                depth++
                index = open + 4
            } else {
                depth--
                index = close + 6
                if (depth == 0) return index
            }
        }
        return null
    }

    /**
     * 逐块剔除 `wsw__embed` 块。播放器块内有多层嵌套 div，同样用深度计数找它的结束，
     * 剔除必须发生在提取段落**之前**——否则 mp3 链接与播放器文案会混进正文。
     */
    private fun dropEmbedBlocks(container: String): String {
        val builder = StringBuilder(container.length)
        var cursor = 0
        while (true) {
            val marker = container.indexOf("wsw__embed", cursor)
            if (marker < 0) {
                builder.append(container, cursor, container.length)
                return builder.toString()
            }
            val divStart = container.lastIndexOf("<div", marker).coerceAtLeast(cursor)
            builder.append(container, cursor, divStart)
            var depth = 0
            var index = divStart
            while (index < container.length) {
                val open = container.indexOf("<div", index)
                val close = container.indexOf("</div>", index)
                if (close < 0) return builder.toString() // 截断的坏块：丢掉剩余内容
                if (open in 0 until close) {
                    depth++
                    index = open + 4
                } else {
                    depth--
                    index = close + 6
                    if (depth == 0) break
                }
            }
            cursor = index
        }
    }

    /** 按文档顺序返回 `<p>`（isHeading=false）与 `<h2>`（isHeading=true）的原始内容。 */
    private fun extractBlocks(container: String): List<Pair<String, Boolean>> {
        val blockRegex = Regex("<(p|h2)([^>]*)>((?:(?!</\\1>).)*)</\\1>", RegexOption.DOT_MATCHES_ALL)
        return blockRegex.findAll(container).map { match ->
            match.groupValues[3] to (match.groupValues[1] == "h2")
        }.toList()
    }

    private fun stripTags(raw: String, isHeading: Boolean): String {
        // 标签后先补一个空格，避免 <strong>gliders </strong>at 剥成 glidersat。
        val text = raw.replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return text
    }
}
