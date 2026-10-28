package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 3 Task 2：阅读历史屏的新骨架契约（spec §6 逐屏表「阅读历史」行）。
 *
 * 这屏原样是批 1 之前的形态：M3 `Button("返回")` + `headlineSmall` 自写页头、三张 `Card` 壳、
 * 三处 `RoundedCornerShape`、十处静态薄荷桥，且**整屏零滚动**——历史一多就整页往下长。
 *
 * 七条判据分两档颜色：
 *  - 1–6 是**本批要做的六件事**，RED 阶段各自必须红；
 *  - 7 是**防回潮**（现状本来就绿）：八枚 tag 与展开态播报口径逐字保留，
 *    换骨架时最可能的破坏不是删锚而是复制一份（两处各写一次会让 connected 侧撞多节点）。
 *
 * 取窗一律按符号深度配对（注释先剥），锚点改名即 `fail`，不会静默空转。
 */
class ReadingHistoryLayoutContractTest {

    private val source: String by lazy { stripComments(uiScreenSource("ReadingHistoryScreen.kt")) }

    /**
     * 七枚静态薄荷桥。本文件 RED 前有 10 处读点（`MintPrimaryDark` 5 / `MintSurface` 3 /
     * `MintTextMuted` 1 / `MintBackground` 1），换装后必须全归零：只许组合期读 `appColors()`。
     */
    private val mintBridges = listOf(
        "MintPrimaryDark",
        "MintPrimary",
        "MintTextMuted",
        "MintSurface",
        "MintOutline",
        "MintTint",
        "MintBackground",
    )

    // ---------- 1. 页头 ----------

    /**
     * 二级页的返回位与标题归共享页头，屏侧不再自写。
     *
     * 原来的「返回」是一枚 M3 实底 `Button` + `semantics { contentDescription = "返回每日阅读" }`，
     * 标题是 `headlineSmall`：既与全站二级页页头（40dp 返回位 + 17sp 标题）不是一套，
     * 也让「返回」的可及名在不同屏各不相同。页头接管后本文件 `contentDescription =` 与
     * `MaterialTheme.typography` 双双归零——这也是 `SharedComponentUniquenessRatchetTest`
     * 页头族能摘掉本文件的前提。
     */
    @Test
    fun `the shared header owns the back slot and the screen title`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("AppScreenHeader("))),
            "页头必须恰好一枚共享 `AppScreenHeader(`：二级页的返回位与标题只允许这一套",
        )
        val header = callSpan(source, "AppScreenHeader(")
        assertTrue(header.isNotEmpty(), "取不到 `AppScreenHeader(` 调用，判据会空转")
        assertTrue(
            """title = "阅读历史"""" in header,
            "页头标题必须是「阅读历史」（旧页头文案「本地阅读历史」随自写标题一起撤）",
        )
        assertTrue(
            "onBack = onBack" in header,
            "页头必须直接接屏的 `onBack`：自己再包一层 lambda 会把返回通道变成第二个真值",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("contentDescription = "))),
            "屏侧不得再自写 `contentDescription =`：返回位的可及名归页头（固定「返回」），" +
                "旧文案「返回每日阅读」在本文件必须为 0",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "屏侧不得读 M3 默认排印：六档 `AppType` 是唯一入口（spec §4.2）",
        )
    }

    // ---------- 2. 滚动 ----------

    /**
     * 本屏原来**零滚动**：根 `Column` 直接铺满，历史一多就顶出屏幕、页头跟着走。
     *
     * 新骨架把滚动面收进页头之下的 `LazyColumn`，条目按需组合；根节点仍自绘页底并留住
     * `reading_history_screen` 锚（connected 侧与像素腿都按它取节点）。
     */
    @Test
    fun `the history renders through a lazy list under a fixed header`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("LazyColumn("))),
            "历史列表必须是 `LazyColumn` 且只一枚：本屏原来零滚动，条目一多就顶出屏幕",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("verticalScroll("))),
            "根容器不得自持 `verticalScroll(`：滚动面归懒列表，页头不跟着滚（两条滚动轴还会互相不知道）",
        )
        val screenBody = bodyOf("fun ReadingHistoryScreen(")
        assertTrue(screenBody.isNotEmpty(), "取不到 ReadingHistoryScreen 本体，判据会空转")
        val root = callSpan(screenBody, "Column(")
        assertTrue(root.isNotEmpty(), "取不到根 Column 调用，判据会空转")
        assertTrue(
            "background(colors.background)" in root,
            "根节点必须自绘 `appColors().background`（旧值 `MintBackground` 归零）",
        )
        assertTrue(
            """testTag("reading_history_screen")""" in root,
            "页底锚 `reading_history_screen` 必须留在带 background 的根节点上（connected 侧按它取节点）",
        )
    }

    // ---------- 3. 容器 ----------

    /**
     * 三张 `Card` 壳拆成裸行：单版本条目、多版本组行、展开后的逐版行。
     *
     * 一层套一层的同色卡片是「卡片汤」：组行与版本行本来就是同一张清单里的主从两级，
     * 主从靠缩进 + 发丝线表达就够，圆角归 `AppShape` 三档、屏侧不再自造。
     */
    @Test
    fun `cards give way to bare rows`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("Card("))),
            "屏侧不得再用 M3 `Card(`：清单主从两级靠裸行 + 缩进表达，不靠一层套一层卡壳",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("CardDefaults"))),
            "`CardDefaults` 随卡片一起撤：留着就是换了个地方继续配卡面",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("border("))),
            "屏侧不得自造描边：软卡靠明度差分、裸行靠发丝线（spec §4.1）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("RoundedCornerShape("))),
            "屏侧不得自造圆角：20 / 14 / 12 三档由 `AppShape` 与三档容器承载（spec §4.1）",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("BareRow("))) >= 2,
            "至少两处 `BareRow(`：单版本条目一处、多版本组行一处（只有一处说明有一个分支还套着卡壳）",
        )
    }

    // ---------- 4. 单版本不写「第 1 版」 ----------

    /**
     * 单版本条目的副标题只留日期。
     *
     * 「· 第 1 版」在单版本条目上没有信息量——组里就这一版，版本号讲不出「还有别的」，
     * 只是把副标题挤长。多版本展开后的逐版行则必须保留「第 N 版 · 日期」的拼法
     * （connected 侧 `onNodeWithText("第 2 版 · 2026-09-22")` 全等断言）。
     */
    @Test
    fun `a single version row does not spell out its only version`() {
        val itemRow = callSpanContaining(source, "BareRow(", """testTag("reading_history_item_""")
        assertTrue(itemRow.isNotEmpty(), "取不到承载 `reading_history_item_` 的裸行调用，判据会空转")
        assertEquals(
            0,
            countMatches(itemRow, Regex(Regex.escape("\" · 第 "))),
            "单版本条目的副标题不得再拼「· 第 N 版」：组里只有这一版，版本号没有信息量",
        )
        assertTrue(
            "article.localDate" in itemRow,
            "单版本条目的副标题必须是 `article.localDate`（日期是这一行唯一的定位信息）",
        )
        val versionRow = callSpanContaining(source, "Row(", """testTag("reading_version_row_""")
        assertTrue(versionRow.isNotEmpty(), "取不到「第 N 版」那行的 `Row(`，判据会空转")
        assertTrue(
            "第 \${article.version} 版 · \${article.localDate}" in versionRow,
            "多版本逐版行必须保留「第 N 版 · 日期」的拼法（connected 侧按全等文案断言）",
        )
    }

    // ---------- 5. 危险墨 ----------

    /**
     * 「删除此版」只换墨色、不做红底。
     *
     * 危险**填充**归确认框（`ConfirmDestructiveInkTest` 那一族红线）：列表行里的删除是入口，
     * 点下去才弹确认；入口就染成红底按钮会让整屏扫一眼全是「危险」。
     */
    @Test
    fun `the delete entry uses danger ink without a danger fill`() {
        val deleteButton = callSpanContaining(source, "TextButton(", """testTag("reading_version_delete_""")
        assertTrue(deleteButton.isNotEmpty(), "取不到「删除此版」的 `TextButton(`，判据会空转")
        assertTrue(
            "colors.error" in deleteButton,
            "「删除此版」的文字色必须是 `colors.error`：危险语义只靠墨色表达，容器保持透明",
        )
        // 计划原文写的是 `dangerFill`；本仓对应的令牌名是 `AppColorSet.errorFill` / `AppPalette.ErrorFill`。
        assertEquals(
            0,
            countMatches(source, Regex("""[Ee]rrorFill""")),
            "全文件不得出现危险填充：`errorFill` 这一档只给确认框，列表入口不做红底按钮",
        )
    }

    // ---------- 6. 令牌 ----------

    /** 七枚静态薄荷桥归零 + 域色只用一处主操作（展开后逐版行的「重读」）。 */
    @Test
    fun `the mint bridge stays retired and the reading domain carries one action`() {
        mintBridges.forEach { token ->
            assertEquals(
                0,
                countMatches(source, Regex(token)),
                "静态薄荷桥 `$token` 必须归零：换装后只许组合期读 `appColors()`（`DarkTokenConsumptionRatchetTest` 同口径）",
            )
        }
        assertTrue(
            countMatches(source, Regex(Regex.escape("appColors()"))) >= 1,
            "屏侧必须消费 `appColors()`（深色套才跟着换）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("appDomain("))),
            "阅读域色只许出现一处，且必须挂在主操作上（展开后逐版行的「重读」）：" +
                "本屏没有第二个主操作，第二处域色只能是纯装饰",
        )
        assertTrue(
            "appDomain(AppDomain.Reading)" in source,
            "域色必须取阅读域（`AppDomain.Reading`），不许借别的功能域",
        )
    }

    // ---------- 7. 既有锚点（防回潮） ----------

    /**
     * 八枚 tag 各恰一处字面量 + 展开态播报逐字保留。
     *
     * `AccessibilityStateSemanticsContractTest:174-182` 连变量名一起钉住
     * `stateDescription = if (expanded) "已展开" else "已收起"`——本屏的展开态播报是它的站点，
     * 换骨架时变量名或文案变一个字，那条契约就会在无语义的空壳上变绿。
     */
    @Test
    fun `the eight existing anchors and the expand state stay exactly once`() {
        listOf(
            "reading_history_screen",
            "reading_version_notice",
            "reading_history_empty",
            "reading_history_item_",
            "reading_history_group_",
            "reading_version_row_",
            "reading_version_reread_",
            "reading_version_delete_",
        ).forEach { anchor ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$anchor"))),
                "锚「$anchor」必须恰一处字面量：两处会让 connected 侧按 tag 定位时撞多个节点",
            )
        }
        assertTrue(
            """stateDescription = if (expanded) "已展开" else "已收起"""" in source,
            "展开态播报必须逐字保留（含变量名 `expanded`）：`AccessibilityStateSemanticsContractTest` 按全等文本钉",
        )
        assertTrue(
            "versionGroupKey" in source,
            "多版本组行的 tag 必须仍由 `versionGroupKey` 生成：组键换了，组与版本行的对应关系就断了",
        )
        assertTrue(
            "暂无本地文章" in source,
            "空态文案前缀「暂无本地文章」是离线空态的口径，不许改写成「没有内容」之类",
        )
    }

    // ---------- 取样助手 ----------

    /**
     * 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。
     *
     * 函数体左花括号取**括号深度为 0** 的那一枚：默认参数里可以带 lambda 空体
     * （`onOpenArticle: (Article) -> Unit = {}`），照「签名之后第一枚 `{`」配对会取到那个 `{}`。
     */
    private fun bodyOf(signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        var parenDepth = 0
        for (index in start until source.length) {
            when (source[index]) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '{' -> if (parenDepth == 0) return balancedFrom(index)
            }
        }
        return ""
    }

    /**
     * 取一次调用的完整段（`anchor` 含左括号，如 `"BareRow("`）；取不到返回空串。
     *
     * 段必须**连尾随 lambda 一起**取：`TextButton(onClick = …) { Text("删除此版", color = colors.error) }`
     * 的括号在 lambda 之前就闭合了，只取括号段会取到「没有内容」的按钮，
     * 对着空段判「文字色是 `colors.error`」永远红（本 Task 实现现场踩过，判据全绿前修的就是这里）。
     */
    private fun callSpan(text: String, anchor: String): String {
        val start = text.indexOf(anchor)
        if (start < 0) return ""
        return spanAt(text, start + anchor.length - 1)
    }

    /**
     * 取**含 `needle` 的那一枚**调用段：同一文件里 `TextButton(` 有多处，
     * 只取第一枚会对着别的按钮判红/判绿（取样面错位），所以按需要点名的 tag 反查。
     */
    private fun callSpanContaining(text: String, anchor: String, needle: String): String {
        var from = 0
        while (true) {
            val start = text.indexOf(anchor, from)
            if (start < 0) return ""
            val span = spanAt(text, start + anchor.length - 1)
            if (needle in span) return span
            if (span.isEmpty()) return ""
            from = start + 1
        }
    }

    /** 从 `open`（指向左括号）起配对括号；随后若紧跟 `{ … }`，把尾随 lambda 一并纳入段。 */
    private fun spanAt(text: String, open: Int): String {
        if (text.getOrNull(open) != '(') return ""
        var depth = 0
        var end = -1
        for (index in open until text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        end = index
                        break
                    }
                }
            }
        }
        if (end < 0) return ""
        var cursor = end + 1
        while (cursor < text.length && text[cursor].isWhitespace()) cursor++
        if (text.getOrNull(cursor) == '{') {
            val lambdaEnd = braceEnd(text, cursor)
            if (lambdaEnd > 0) return text.substring(open, lambdaEnd + 1)
        }
        return text.substring(open, end + 1)
    }

    /** 从左花括号起按深度配对，返回右花括号下标；取不到返回 -1。 */
    private fun braceEnd(text: String, open: Int): Int {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    /** 从左花括号起按深度配对，返回去掉外层花括号的段。 */
    private fun balancedFrom(open: Int): String {
        if (source.getOrNull(open) != '{') return ""
        val end = braceEnd(source, open)
        return if (end < 0) "" else source.substring(open + 1, end)
    }
}
