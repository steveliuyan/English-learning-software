package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 原型对齐批 1 Task 11：词义详情屏（原型 `sWordDetail`，`assets/app.js:519-559`）。
 *
 * 与既有 [CardDetailScreenContractTest]（发音件归一）与 [CardDetailLayoutContractTest]（批 2 版式）的分工：
 * 本份钉**原型骨架**——词头不再装在英雄卡里、小节标题在卡外、释义按行、搭配按行、近义词是静态芯片、
 * 「词形与派生」合成一节、页末提示 + AI 追问块按钮、收藏位回到页头。
 *
 * 取样面是源码文本（注释先剥），不锚行号；函数体按花括号配对取。
 */
class CardDetailPrototypeContractTest {

    private val source: String by lazy { uiScreenSource("CardDetailScreen.kt") }

    /** 小节标题必须排在卡外面（原型 `.sec-title` 是卡片上方的独立一行）。 */
    @Test
    fun `section titles sit above their cards`() {
        val section = bodyOf("private fun DetailSection(")
        assertTrue(section.isNotEmpty(), "取不到 DetailSection 本体，判据会空转")
        val titleAt = section.indexOf("Text(")
        val cardAt = section.indexOf("SoftCard(")
        assertTrue(titleAt >= 0, "小节必须有标题行")
        assertTrue(cardAt >= 0, "小节必须有卡片本体")
        assertTrue(titleAt < cardAt, "标题必须在卡片之前（原型 .sec-title 在 .card 上方）")
        assertEquals(
            0,
            countMatches(source, Regex("""\bDetailCard\(""")),
            "旧 `DetailCard(` 私壳退役：小节卡一律走共享 `SoftCard(`，不让第二层壳再藏一次标题与内距",
        )
    }

    /** 词头站在页面上：46sp 衬线主词 + 音标行 + 口音行，外面没有英雄卡。 */
    @Test
    fun `the word head stands on the page instead of a hero card`() {
        val head = bodyOf("private fun WordHead(")
        assertTrue(head.isNotEmpty(), "取不到 WordHead 本体，判据会空转")
        assertEquals(
            0,
            countMatches(head, Regex("""\bSoftCard\(|\bStrongCard\(""")),
            "词头不装卡：原型 sWordDetail 的主词直接落在页面上（卡片从「释义」才出现）",
        )
        assertEquals(
            1,
            countMatches(head, Regex("""\bAppType\.Lemma\b""")),
            "主词取 AppType.Lemma（46sp 衬线，原型 .word-lemma）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""AppType\.Display\.copy\(""")),
            "屏侧不再用「Display 缩到 36sp」的临时档：词头档已经由 AppType.Lemma 承载",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bWordHeroCard\(""")),
            "旧英雄卡 `WordHeroCard(` 整块退役",
        )
    }

    /** 收藏是页头尾槽的一枚图标按钮（原型 `barTop` 的 `icon-btn`），不再挤在动作药片里。 */
    @Test
    fun `the favorite toggle lives in the header action slot`() {
        val headerAt = source.indexOf("AppScreenHeader(")
        assertTrue(headerAt >= 0, "详情页必须有共享页头")
        val header = callArgumentsAt(headerAt)
        assertTrue(header.isNotEmpty(), "取不到页头调用窗口，判据会空转")
        assertTrue(
            "action = favoriteHeaderAction(" in header,
            "页头必须挂尾槽 `action = …`，且尾槽就是收藏键（原型 barTop 右上 icon-btn）",
        )
        val favorite = bodyOf("private fun favoriteHeaderAction(")
        assertTrue(favorite.isNotEmpty(), "取不到 favoriteHeaderAction 本体，判据会空转")
        assertTrue(
            "card_detail_favorite_toggle" in favorite,
            "收藏开关的 testTag 必须画在页头尾槽里",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""testTag("card_detail_favorite_toggle")"""))),
            "收藏开关全屏只此一处",
        )
        // 动作药片网格整体退役后，收藏不得又回到卡内的动作行里。
        assertEquals(
            0,
            countMatches(source, Regex("""\bWordActionPillGrid\(""")),
            "五枚动作药片网格退役：收藏进页头，其余动作回到词头下的次键行",
        )
    }

    /** 释义按行：词性取衬线斜体品牌深色、38dp 定宽；释义 15sp。 */
    @Test
    fun `the senses are rows with a fixed serif part of speech column`() {
        val row = bodyOf("private fun SenseRow(")
        assertTrue(row.isNotEmpty(), "取不到 SenseRow 本体，判据会空转")
        assertTrue("width(38.dp)" in row, "词性列宽 38dp（原型 .row-value width:38px）")
        assertTrue("fontStyle = FontStyle.Italic" in row, "词性是衬线斜体（原型 .word-pos / row-value 的 font-style:italic）")
        assertTrue("fontFamily = AppFont.Serif" in row, "词性走 Newsreader 衬线（原型 var(--serif)）")
        assertTrue("colors.brandDeep" in row, "词性墨取 brand-deep（原型 color:var(--brand-deep)）")
        assertEquals(
            0,
            countMatches(row, Regex("""\.clip\(AppShape\.Pill\)""")),
            "词性不再做成浅绿胶囊：原型是「定宽斜体一列」，不是标签块",
        )
        assertEquals(
            0,
            countMatches(row, Regex("""\.background\(colors\.tint\)""")),
            "同上：释义行不铺标签底色",
        )
    }

    /** 近义词是静态芯片（原型 `.chip chip-static`），不是词行。 */
    @Test
    fun `synonyms render as static chips`() {
        val chips = bodyOf("private fun SynonymChips(")
        assertTrue(chips.isNotEmpty(), "取不到 SynonymChips 本体，判据会空转")
        assertTrue("AppShape.Pill" in chips, "芯片药片形（原型 .chip）")
        assertTrue("height(30.dp)" in chips, "芯片本体 30dp 高（原型 .chip height:30px）")
        assertTrue("colors.surface" in chips, "芯片底色是 surface 一档（原型 .chip background:var(--surface)）")
        assertTrue(
            "clickable(" !in chips && "toggleable(" !in chips,
            "静态芯片没有点击语义（原型 .chip-static：cursor:default，非操作项）",
        )
        val synonymsAt = source.indexOf("\"card_detail_synonyms\"")
        assertTrue(synonymsAt >= 0, "近义词小节必须保留既有锚 card_detail_synonyms")
        val section = source.substring(synonymsAt)
        assertTrue(
            "SynonymChips(" in section,
            "近义词小节必须走静态芯片（`SynonymChips(`）",
        )
    }

    /** 「词形与派生」合并成一节（原型 `w.derived.join('；')` + `w.inflections.join(' · ')`）。 */
    @Test
    fun `derived words and inflections share one section`() {
        assertEquals(
            1,
            countMatches(source, Regex("\"词形与派生\"")),
            "小节标题「词形与派生」恰一处（原型 section('词形与派生', …)）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("\"词形变化\"")),
            "旧标题「词形变化」并入「词形与派生」，不得回流",
        )
        assertTrue(
            """joinToString("；")""" in source,
            "派生词用「；」连成一句（原型 derived.join('；')）",
        )
        assertTrue(
            """joinToString(" · ")""" in source,
            "词形变化用「 · 」连成一句（原型 inflections.join(' · ')）",
        )
    }

    /** 页末：一句提示 + 一枚整宽次键「用 AI 追问这个词」（原型 notice + btn-secondary btn-block）。 */
    @Test
    fun `the page ends with the notice and the ai follow up block button`() {
        val notice = "\"长难句分析、AI 问词都从这里进入，围绕这一个词继续追问。\""
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape(notice))),
            "页末提示文案按原型逐字出现一次",
        )
        val askAi = bodyOf("private fun AskAiButton(")
        assertTrue(askAi.isNotEmpty(), "取不到 AskAiButton 本体，判据会空转")
        assertTrue("fillMaxWidth()" in askAi, "AI 追问是整宽块按钮（原型 btn-block）")
        assertTrue(
            "PillStyle.Secondary" in askAi,
            "AI 追问是次键档（原型 btn-secondary），不是品牌实心主操作",
        )
        assertTrue(
            "\"用 AI 追问这个词\"" in askAi,
            "按钮文案逐字取原型「用 AI 追问这个词」",
        )
        assertTrue(
            """testTag("card_detail_ask_ai")""" in askAi || "card_detail_ask_ai" in askAi,
            "AI 追问沿用既有锚 card_detail_ask_ai",
        )
        // 入口顺序：提示在按钮之前（原型 notice 在上、块按钮在下）。
        val noticeAt = source.indexOf(notice)
        val askSectionAt = source.indexOf("AskAiButton(")
        assertTrue(noticeAt in 0 until askSectionAt, "提示必须排在 AI 追问按钮之前")
    }

    // ---------- 取样助手 ----------

    private fun bodyOf(signature: String): String = bodyOfAt(source.indexOf(signature))

    /** 取从 [start] 起那次调用的实参窗口（括号配对）；取不到返回空串。 */
    private fun callArgumentsAt(start: Int): String {
        if (start < 0) return ""
        val open = source.indexOf('(', start)
        if (open < 0) return ""
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, index)
                }
            }
        }
        return ""
    }

    /** 按花括号配对取从 [start] 起的调用/函数体（源已剥注释）；取不到返回空串。 */
    private fun bodyOfAt(start: Int): String {
        if (start < 0) return ""
        var parenDepth = 0
        var open = -1
        for (index in start until source.length) {
            when (source[index]) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '{' -> if (parenDepth == 0) {
                    open = index
                    break
                }
            }
        }
        if (open < 0) return ""
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, index)
                }
            }
        }
        return ""
    }
}
