package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 2：词义详情屏的新骨架契约（spec §5 逐屏表「词义详情」行）。
 *
 * 与 [CardDetailScreenContractTest] 的分工：那一份钉**发音件归一 + 页头 / 词行接共享组件**
 * （三枚药片、三枚词行、停止文案各恰一处），本份钉**版式**：配图缩略图、动作药片两行网格、
 * 「我的笔记」标题只留一处、空 live region 不吃间距、令牌换装。两份都必须绿。
 *
 * 「行号不锚、注释不计」由 [uiScreenSource] 与 [countMatches] 保证。
 */
class CardDetailLayoutContractTest {

    private val source: String by lazy { uiScreenSource("CardDetailScreen.kt") }

    /** 共享动作药片调用点：前缀组防私有同族名冒充。 */
    private val pillCall = Regex("""(^|[^A-Za-z0-9_.])PillButton\(""")

    private val anyBlockCall = Regex(
        """\b(?:Row|Column|SoftCard|Card|SpeakerButton|PillButton|WordActionPillGrid|SenseRow|Image)\(""",
    )

    // ---------- 配图：4:3 大图 → 72dp 缩略图 ----------

    /**
     * spec §5：「撤掉 4:3 配图，改 72dp 缩略图」。
     *
     * 两枚 tag 一并钉住：`card_detail_illustration`（内置 drawable 那条路）与 `card_detail_image_file`
     * （词书导入的真实文件）是 spec §5「有图才显示」的两条渲染路径，缩图改造不许把任一条路径丢掉。
     */
    @Test
    fun `the illustration is a 72dp thumbnail instead of a banner`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("aspectRatio("))),
            "配图不再是 4:3 大图：整块横幅把单词卡挤出首屏（spec §5 要求 72dp 缩略图）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""val illustrationThumbnailSize = 72\.dp""")),
            "缩略图边长必须是一处命名常量 72.dp（散落的 72.dp 字面量在不同分支里会漂）",
        )
        listOf("card_detail_illustration", "card_detail_image_file").forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("""testTag("$tag")"""))),
                "缩略图的两条渲染路径（内置图 / 导入文件）各恰一枚 testTag(\"$tag\")",
            )
        }
    }

    // ---------- 动作药片：单行 5 枚 → 两行网格 3+2 ----------

    /**
     * spec §5：「5 枚动作药片改两行网格（3+2）」。
     *
     * 旧形状是一枚 `Row` 里塞五枚药片：320dp 窄屏 + 1.3 字号下第五枚被压成竖排单字或被裁掉。
     * 取样面是网格本体（私有 [WordActionPillGrid]），判「恰两行、首行 3、次行 2」，
     * 并反向钉住「药片不许再散落在网格之外」——全屏命中只允许比网格内多出 1 处定义。
     */
    @Test
    fun `the action pills form a two row grid of three plus two`() {
        val grid = bodyOf("private fun WordActionPillGrid(")
        assertTrue(grid.isNotEmpty(), "取不到 WordActionPillGrid 本体，判据会空转")
        assertEquals(
            2,
            countMatches(grid, Regex(Regex.escape("Row("))),
            "动作药片必须是两行（3 + 2）：单行 5 枚在窄屏 + 大字号下会压掉最后一枚",
        )

        val secondRowAt = grid.indexOf("Row(", grid.indexOf("Row(") + 1)
        assertTrue(secondRowAt > 0, "取不到第二行起点，判据会空转")
        assertEquals(3, countMatches(grid.substring(0, secondRowAt), pillCall), "首行三枚（收藏 / 生词本 / 重新学习）")
        assertEquals(2, countMatches(grid.substring(secondRowAt), pillCall), "次行两枚（下一个单词 / 问 AI）")
        // 网格之外只允许两处 `PillButton(`：`private fun PillButton(` 定义本身，以及笔记小节
        // 那枚「添加 / 管理」——它挂在「我的笔记」标题下，不属于对词的五枚动作，留在网格外是有意的。
        assertEquals(
            countMatches(grid, pillCall) + 2,
            countMatches(source, pillCall),
            "五枚词动作药片必须全落在网格内：网格之外只允许定义本身与笔记小节的「添加 / 管理」入口",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""testTag("card_detail_notes_manage")"""))),
            "网格外那枚药片必须就是笔记小节的「添加 / 管理」入口——多出任何别的散落药片都算违规",
        )
    }

    // ---------- 「我的笔记」标题只留一处 ----------

    /**
     * spec §5：详情页正文的「我的笔记」小节与弹窗标题同名，读屏与视觉上都像两块同义内容。
     *
     * 弹窗是**管理**入口（列表 + 逐条删除 + 新增），标题改「管理笔记」；正文小节仍是「我的笔记」。
     */
    @Test
    fun `the notes heading is stated once and the dialog is named for managing`() {
        assertEquals(
            1,
            countMatches(source, Regex("\"我的笔记\"")),
            "「我的笔记」只在正文小节标题出现一次：弹窗标题另起「管理笔记」",
        )
        assertEquals(
            1,
            countMatches(source, Regex("\"管理笔记\"")),
            "弹窗标题必须改「管理笔记」（它是增删入口，不是第二块展示区）",
        )
    }

    // ---------- 空 live region 不再吃间距 ----------

    /**
     * 旧版把常驻 live region 容器夹在单词卡中间：容器空态仍占外层 `spacedBy` 一份间距
     * （`CardDetailPronunciationControlsTest` 同期的 N-4 备注已记这笔账）。
     *
     * 修法**不是**按文案门控容器——connected 侧 `liveRegionContainerPersistsWhileNoPronunciationMessageIsShown`
     * 要求 Idle 时容器仍在树里、仍带 Polite、零子节点。真正的解法是把它排到单词卡的**最后一个**
     * 内容块：`spacedBy` 只在相邻子节点之间加缝，末尾的空容器因此不再制造幽灵间距。
     */
    @Test
    fun `the persistent live region sits last so an empty container costs no gap`() {
        val hero = bodyOf("private fun WordHeroCard(")
        assertTrue(hero.isNotEmpty(), "取不到 WordHeroCard 本体，判据会空转")
        val statusTag = """testTag("card_detail_status_live_region")"""
        val statusAt = hero.indexOf(statusTag)
        assertTrue(statusAt >= 0, "单词卡内必须有常驻 live region 容器（N-4：容器不许随文案进出组合）")

        val lastContentAt = listOf("WordActionPillGrid(", """testTag("card_detail_senses")""", "SenseRow(")
            .map { hero.indexOf(it) }
            .max()
        assertTrue(
            statusAt > lastContentAt,
            "live region 容器必须是单词卡列里的最后一块：夹在中间时空容器会吃掉 spacedBy 的一份间距",
        )

        val tail = hero.substring(statusAt)
        assertEquals(1, countMatches(tail, Regex("""\bText\(""")), "容器之后只允许它自己的瞬时文案子树")
        assertEquals(
            0,
            countMatches(tail, anyBlockCall),
            "容器之后不得再有任何内容块（否则它又退回「夹在中间」的形状）",
        )
    }

    // ---------- 令牌换装 ----------

    /** 屏侧只消费 `appColors()` / `appDomain()`；`Mint*` 静态桥在这屏清零（口径同批 2 Task 1）。 */
    @Test
    fun `the screen consumes theme tokens instead of the static mint bridge`() {
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
                countMatches(source, Regex(Regex.escape(token))),
                "$token 是静态薄荷桥，屏侧改用 appColors() / appDomain()（spec §4.4）",
            )
        }
        assertTrue("appColors()" in source, "屏侧必须消费 appColors()")
    }

    /** 弱卡收编：小节卡改共享 `SoftCard`（白面、无描边、圆角 20），不再自造 M3 卡 + 1dp 描边。 */
    @Test
    fun `the detail sections are built on the shared soft card`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("SoftCard("))),
            "小节卡必须是一处共享软档卡（spec §4.1 弱卡）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "不得再自造 M3 Card：描边卡片汤就是它铺出来的",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("border(1.dp"))),
            "弱卡不描边（描边只留给输入框与危险动作）",
        )
        assertEquals(2, countMatches("Card(\nCard(", Regex("""\bCard\(""")), "反空转：`\\bCard(` 必须能数出 2 份")
    }

    // ---------- 取样助手 ----------

    /**
     * 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。
     *
     * 函数体左花括号必须取**括号深度为 0** 的那一枚：默认参数里可以带 lambda（`= {}`），
     * 照「签名之后第一枚 `{`」配对会取到那里，取样面变成空串、判据静默空转。
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
