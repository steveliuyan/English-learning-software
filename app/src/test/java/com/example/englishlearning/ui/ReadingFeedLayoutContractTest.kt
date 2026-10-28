package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 3 Task 1：外刊选读屏的新骨架契约（spec §6 逐屏表「外刊选读」行）。
 *
 * 这屏是批 1 已经换过装（`appColors()` + `AppType` + 共享页头）的样板，所以本契约不重证令牌，
 * 只钉「索引页该是懒列表」「条目该是裸行而不是一列实底按钮」「条目带日期」「失败态得能重试」
 * 四件版式事，外加三条防回潮锚（排印 / 令牌 / 既有锚点）。
 *
 * 七条判据分三档颜色：
 *  - 1/2/3/4 是**本批新做的四件**，RED 阶段必须各自红；
 *  - 5/6/7 是**防回潮**（现状本来就绿），用于挡住换骨架时顺手把排印、令牌或既有锚点改坏；
 *    它们若在 RED 阶段就红，说明基线已被前一批改动破坏，要单独查，不能当成本 Task 的进度。
 *
 * 「行号不锚、注释不计」由 [uiScreenSource] 与 [countMatches] 保证：源先剥注释，
 * 判据全部按符号取窗，锚点改名即 `fail`，不会静默空转。
 */
class ReadingFeedLayoutContractTest {

    private val source: String by lazy { stripComments(uiScreenSource("ReadingFeedScreen.kt")) }
    private val host: String by lazy { stripComments(uiScreenSource("AppScreen.kt")) }

    /**
     * M3 实底 `Button` 调用点。前缀组排除 `TextButton(`：后者的 `B` 前面是字母 `t`，
     * 不该被算成「条目又变回实底按钮」。
     */
    private val buttonCall = Regex("""(^|[^A-Za-z0-9_.])Button\(""")

    // ---------- 1. 懒列表 ----------

    /**
     * spec §6 C 类骨架：索引页用 `LazyColumn`，不用「整屏 `verticalScroll` + 逐条 `forEach`」。
     *
     * 后者在条目一多时会把每条都组合出来（外刊一次几十条），且滚动轴与页头绑在一起；
     * 懒列表把滚动面限在页头之下，条目按需组合。
     */
    @Test
    fun `the feed renders through a lazy list instead of a scrolling column`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("LazyColumn("))),
            "外刊列表必须是 `LazyColumn` 且只一枚：索引页按需组合条目，不再 forEach 铺满整屏",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("verticalScroll("))),
            "根容器不得再自持 `verticalScroll(`：滚动面归懒列表，页头不跟着滚（两条滚动轴还会互相不知道）",
        )
        val screenBody = bodyOf("fun ReadingFeedScreen(")
        assertTrue(screenBody.isNotEmpty(), "取不到 ReadingFeedScreen 本体，判据会空转")
        val root = callSpan(screenBody, "Column(")
        assertTrue(root.isNotEmpty(), "取不到根 Column 调用，判据会空转")
        assertTrue(
            "background(colors.background)" in root,
            "根节点必须自绘 `appColors().background`：像素腿按 `reading_feed_screen` 取页底众数",
        )
        assertTrue(
            """testTag("reading_feed_screen")""" in root,
            "页底锚 `reading_feed_screen` 必须留在带 background 的根节点上（两条像素腿按它取节点）",
        )
    }

    // ---------- 2. 裸行条目 ----------

    /**
     * spec §6：外刊条目是一列**裸行**，不是一列 M3 实底按钮。
     *
     * 原来每条是一枚 `Button` + `RoundedCornerShape(18.dp)`：几十条实底按钮就是「卡片汤」的极端版，
     * 每一条都在喊「点我」，主次全无。裸行靠 1dp 发丝线表达「同组」，圆角归容器三档、屏侧不再自造。
     */
    @Test
    fun `each feed item is a bare row instead of a filled button`() {
        assertEquals(
            0,
            countMatches(source, buttonCall),
            "条目不得再用 M3 实底 `Button(`：一列实底按钮没有主次，同组关系交给裸行的发丝线",
        )
        assertEquals(
            2,
            countMatches(source, Regex(Regex.escape("TextButton("))),
            "全文件只允许两枚 `TextButton(`（两种失败态各一枚重试）：次要动作不许与主操作抢实底",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("RoundedCornerShape("))),
            "屏侧不得自造圆角：20 / 14 / 12 三档由 `AppShape` 与三档容器承载（spec §4.1）",
        )
        val row = callSpan(source, "BareRow(")
        assertTrue(row.isNotEmpty(), "取不到 `BareRow(` 调用，判据会空转")
        assertTrue(
            "testTag(\"feed_item_\$index\")" in row,
            "条目锚 `feed_item_\$index` 必须挂在裸行调用上：connected 侧按它点名每一行",
        )
        // 可及名逐字保留（转义后的 `${…}` 是源码里的真字面量，不是测试侧的模板求值）。
        val a11y = "contentDescription = \"阅读外刊文章 \${item.title}\""
        assertTrue(
            a11y in row,
            "条目必须带「阅读外刊文章 + 标题」的可及名（原样保留，不许随换容器丢掉）",
        )
        assertTrue(
            "onClick = { onFetchFeedItem(item) }" in row,
            "条目点击必须仍把该条交给宿主：换容器不许顺手换掉回调形状",
        )
        assertTrue(
            "titleMaxLines = 2" in row,
            "外刊标题必须允许两行（`BareRow` 新增的 `titleMaxLines` 槽）：单行会把长标题截成半句",
        )
    }

    // ---------- 3. 日期 ----------

    /**
     * spec §6：条目补日期（**只补日期，不补篇幅**）。
     *
     * `FeedItem`（`reading/ArticleFeedParser.kt:7-12`）只有 `title` / `articleUrl` /
     * `publishedAtEpochMillis` / `summary`，**没有篇幅字段**；补篇幅要么改数据形状、要么按
     * `summary.length` 糊一个假字数，两条都不做（见计划 §对账 1）。日期则复用既有
     * `wordListDateLabel(instant, zoneId)`，时区由屏的 `zoneId` 形参传入，不偷读系统时区。
     */
    @Test
    fun `each feed item carries its publication date`() {
        assertTrue(
            "zoneId: ZoneId = ZoneId.systemDefault()" in source,
            "本屏必须带 `zoneId: ZoneId = ZoneId.systemDefault()` 形参（与 `MyWordsScreen` 同一写法）：" +
                "日期格式化不许在屏内偷读系统时区",
        )
        assertTrue(
            "publishedAtEpochMillis" in source,
            "日期必须取自 `FeedItem.publishedAtEpochMillis`，不许拿 `summary.length` 之类糊一个",
        )
        assertTrue(
            "Instant.ofEpochMilli(" in source,
            "毫秒时间戳要包成 `Instant` 才能进 `wordListDateLabel`",
        )
        val row = callSpan(source, "BareRow(")
        assertTrue(row.isNotEmpty(), "取不到 `BareRow(` 调用，判据会空转")
        assertTrue(
            "wordListDateLabel(" in row,
            "日期必须走既有的 `wordListDateLabel(instant, zoneId)`，不许屏内自拼 `DateTimeFormatter`",
        )
        assertTrue(
            "item.publishedAtEpochMillis" in row,
            "日期必须作为裸行的 `trailing` 槽取自条目本身（而不是来源级或今天）",
        )
    }

    // ---------- 4. 失败态重试 ----------

    /**
     * spec §6 批 3 的收口项「失败态带重试」。
     *
     * 两种取源失败（来源不可达 / 单条抓取失败）此前都只有一句文案，用户只能退回主页再进来一次；
     * 重试接的是宿主现成的 `openFeed()`（`ReadingAccessViewModel.kt:190-207`），
     * **不新增数据通路、不动 ViewModel**——屏只多收一枚 `onRetry` 回调。
     */
    @Test
    fun `both failure states offer a retry action`() {
        val notice = bodyOf("private fun FeedStatusNotice(")
        assertTrue(notice.isNotEmpty(), "取不到 FeedStatusNotice 本体，判据会空转")
        listOf("FeedUiState.Unreachable ->", "is FeedUiState.FetchFailed ->").forEach { anchor ->
            val branch = branchBody(notice, anchor)
            assertTrue(branch.isNotEmpty(), "取不到「$anchor」分支本体（锚点搬家或改名），判据会空转")
            assertTrue(
                """testTag("feed_retry")""" in branch,
                "「$anchor」必须给出重试动作（锚 `feed_retry`）：只有文案的失败态等于让用户退回主页再进一次",
            )
            assertTrue(
                "TextButton(" in branch,
                "「$anchor」的重试走 `TextButton(`：它是失败态的次要动作，不与主操作抢实底",
            )
            assertTrue(
                "onRetry" in branch,
                "「$anchor」的重试必须接屏的 `onRetry` 回调（不许屏内自己再发一次网络请求）",
            )
        }
        assertEquals(
            2,
            countMatches(source, Regex(Regex.escape("""testTag("feed_retry")"""))),
            "两枚失败态各一枚 `feed_retry`（3 = 长了第三处散落重试，1 = 有一态被漏掉）",
        )
        assertTrue(
            "onRetry: () -> Unit" in source,
            "函数签名必须收 `onRetry: () -> Unit`：重试通道由宿主注入，屏不持 ViewModel",
        )
        val mount = callSpan(host, "ReadingFeedScreen(")
        assertTrue(mount.isNotEmpty(), "宿主取不到 `ReadingFeedScreen(` 挂载调用，判据会空转")
        assertTrue(
            "onRetry =" in mount,
            "宿主必须把 `onRetry` 接上（`openFeed()` 是现成入口）：只加形参不接线等于重试按钮点了没反应",
        )
    }

    // ---------- 5. 排印（防回潮） ----------

    /** 排印只走 `AppType` 六档，M3 默认 `MaterialTheme.typography` 保持归零（批 1 已换装，本批不许回潮）。 */
    @Test
    fun `the screen types the six tier scale instead of the material default`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "屏侧不得读 M3 默认排印：六档 `AppType` 是唯一入口（spec §4.2）",
        )
        assertTrue(
            countMatches(source, Regex("""AppType\.""")) >= 3,
            "本屏至少三档排印（页头标题 / 来源署名 / 正文或日期）：换骨架不许把排印退回默认值",
        )
        assertTrue("appColors()" in source, "屏侧必须消费 `appColors()`（深色套才跟着换）")
    }

    // ---------- 6. 令牌（防回潮） ----------

    /** 七枚静态薄荷桥保持归零 + 不得自造描边（两张棘轮当场可核）。 */
    @Test
    fun `the mint bridge stays retired and no border grows back`() {
        listOf(
            "MintPrimaryDark",
            "MintPrimary",
            "MintTextMuted",
            "MintSurface",
            "MintOutline",
            "MintTint",
            "MintBackground",
        ).forEach { token ->
            assertEquals(
                0,
                countMatches(source, Regex(token)),
                "静态薄荷桥 `$token` 必须归零：换装后只许 `appColors()` 组合期读（`DarkTokenConsumptionRatchetTest` 同口径）",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("border("))),
            "屏侧不得自造描边：软卡靠明度差分、裸行靠发丝线（spec §4.1）",
        )
    }

    // ---------- 7. 既有锚点（防回潮） ----------

    /**
     * 七枚既有 tag 与一条来源署名口径、四句状态文案逐字保留。
     *
     * `ReadingHomeLayoutContractTest` 已在文件级钉过一遍（锚在场），这里改成**每枚恰一处字面量**：
     * 换骨架时最可能的破坏不是「删掉锚」而是「复制一份」（比如两处各写一次 `feed_error`），
     * 那种形状在原判据下仍是绿的，但 connected 侧会开始撞「多个节点匹配」。
     */
    @Test
    fun `the seven existing anchors and four status texts stay exactly once`() {
        listOf(
            "feed_idle",
            "feed_loading",
            "feed_empty",
            "feed_error",
            "feed_fetch_failed",
            "feed_source",
            "feed_item_\$index",
        ).forEach { anchor ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$anchor\""))),
                "锚「$anchor」必须恰一处字面量：两处会让 connected 侧按 tag 定位时撞多个节点",
            )
        }
        assertTrue(
            "来源：\${feed.sourceDisplayName}" in source,
            "「来源：\${feed.sourceDisplayName}」是来源署名的权威口径，换骨架时不许改写（connected 按全等断言）",
        )
        listOf(
            "外刊列表正在准备，请稍候。",
            "正在获取外刊列表…",
            "这个来源暂时没有可读的文章，稍后再来看看。",
            "外刊来源暂时不可用，请检查网络后重试。",
        ).forEach { text ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$text\""))),
                "状态文案「$text」必须逐字保留且恰一处：connected 侧按全等文案断言",
            )
        }
        assertTrue(
            "\"抓取失败：\${feed.failure.label}\"" in source,
            "抓取失败文案仍是「抓取失败：」+ 常量表 label：不许把原始响应体塞进界面",
        )
    }

    // ---------- 取样助手 ----------

    /**
     * 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。
     *
     * 函数体左花括号取**括号深度为 0** 的那一枚：默认参数里可以带 lambda 空体
     * （`onRetry: () -> Unit = {}`），照「签名之后第一枚 `{`」配对会取到那个 `{}`，
     * 取样面就成了空串、判据静默空转（批 2 Task 3 踩过这颗雷）。
     */
    private fun bodyOf(signature: String): String {
        val start = source.indexOf(signature)
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
        return balancedFrom(open)
    }

    /**
     * 取 `when` 某一分支的本体（`anchor` 为分支条件原文，如 `"is FeedUiState.FetchFailed ->"`）。
     *
     * 从锚之后的第一枚深度 0 左花括号起配对：「分支体」既可能是块 `{ … }`、也可能是
     * `Column(...) { … }` 这样的尾随 lambda，两种形状都从同一枚括号起算。
     */
    private fun branchBody(text: String, anchor: String): String {
        val start = text.indexOf(anchor)
        if (start < 0) return ""
        var parenDepth = 0
        for (index in start + anchor.length until text.length) {
            when (text[index]) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '{' -> if (parenDepth == 0) return balancedFromIn(text, index)
            }
        }
        return ""
    }

    /** 取一次调用的完整括号段（`anchor` 含左括号，如 `"BareRow("`）；取不到返回空串。 */
    private fun callSpan(text: String, anchor: String): String {
        val start = text.indexOf(anchor)
        if (start < 0) return ""
        val open = start + anchor.length - 1
        if (text.getOrNull(open) != '(') return ""
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(open, index + 1)
                }
            }
        }
        return ""
    }

    private fun balancedFrom(open: Int): String = balancedFromIn(source, open)

    private fun balancedFromIn(text: String, open: Int): String {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(open + 1, index)
                }
            }
        }
        return ""
    }
}
