package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 2026-11-02 用户评审（图标去重）：「AI 学」页七枚功能入口从出现那天起共用同一枚 `AppIcon.Spark`。
 *
 * 旧形状的来历是善意的——批 3 为了消灭「单字中文当图标」（spec §4.2）而把 `文/填/听/讲/析/拍/画`
 * 统统一刀换成 AI 域锚点字形，`AiBadgeContrastFixTest` 至今还钉着「共享字形出口里必须有 Spark」。
 * 但代价是七行读成了同一行：用户在列表里扫不到「哪一行是拍照翻译、哪一行是生图」，只剩标题必须逐字读。
 * 域锚点（这一屏属于 AI，配色是 AiSpeech 绿）已经由 `AppDomain.AiSpeech` 承担，字形不该再重复一遍。
 *
 * 本契约钉三件事，缺一件就会退回「一枚图标铺满整页」：
 *  1. 目录侧：七枚功能各声明一枚 `icon = AppIcon.X`，且**两两不同**；
 *  2. 目录侧：七枚字形逐字钉在本文件里（改成另一枚必须是有意为之，而不是顺手）；
 *  3. 站点侧：列表行与详情页头徽标都读 `feature.icon`，不得再写死 `AppIcon.Spark`。
 *
 * 判据面按源文本取样（`stripComments` + 括号配对），与 `AiLearningLayoutContractTest` /
 * `AiBadgeContrastFixTest` 同一套夹具：锚点改名即 `fail`，不会静默空转。
 */
class AiFeatureIconContractTest {

    private val catalogue: String by lazy {
        stripComments(File("$UI_MAIN_SOURCE_PATH/AiFeature.kt").readText())
    }

    private val learning: String by lazy { stripComments(uiScreenSource("AiLearningScreen.kt")) }

    private val featureScreen: String by lazy { stripComments(uiScreenSource("AiFeatureScreen.kt")) }

    /**
     * 七枚功能各自的字形（2026-11-02 裁决）。
     *
     * 取值不是「凑够七个不同的」：每一枚都对上该功能**动作或对象**的那一件东西，
     * 让用户在扫列表时不必读标题——这一条是用户报的原始诉求（「全部共用同一枚 Spark」）。
     */
    private val expectedIcons = listOf(
        // 词文串学：根据今日词匹配一篇外刊短文 → 报纸（外刊/文章，与「阅读」tab 同一语义族）。
        "WORD_PASSAGE" to "Newspaper",
        // AI 短文填词：短文里挖空、从候选里选 → 清单（题面与选项是一列一列的）。
        "CLOZE" to "List",
        // 单词随身听：放音、听不懂再听 → 喇叭。
        "LISTENING" to "Speaker",
        // 单词串讲：逐个讲、可以追问 → 话筒（讲解＝有人在说）。
        "COACH" to "Mic",
        // 长难句分析：把一句英文拆开逐段解释 → 文档（被分析的那段文本）。
        "SENTENCE_ANALYSIS" to "Document",
        // 拍照翻译：相机是主路径，相册是退路 → 相机。
        "PHOTO_TRANSLATION" to "Camera",
        // AI 生图：主题词换一张画面 → 画面。
        "IMAGE_STUDIO" to "Image",
    )

    // ---------- 1. 目录侧：七枚各有一枚字形，且互不重复 ----------

    /**
     * 去重腿。用户原话是「各功能入口全部共用同一枚 Spark」——这一条把「同一枚铺满七行」钉死。
     *
     * 判据同时钉总数与去重：总数少了说明有功能漏声明（会静默退回屏侧写死的字面量），
     * 去重失败说明又长回了「一枚图标铺满整页」。
     */
    @Test
    fun `every feature declares its own icon and no two entries share one`() {
        val declared = Regex("""\bicon\s*=\s*AppIcon\.([A-Z][A-Za-z]*)""")
            .findAll(catalogue)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            AiFeature.entries.size,
            declared.size,
            "七枚功能必须各声明一枚 `icon = AppIcon.X`，实际 ${declared.size} 枚：$declared。" +
                "少了 = 有功能漏声明，屏侧会退回写死字面量；多了 = 目录里长出第二枚没人读的字形",
        )
        assertEquals(
            declared.size,
            declared.toSet().size,
            "功能入口字形重复：$declared —— 同一枚字形铺满多行正是用户报的「图标全都一样」",
        )
    }

    /**
     * 逐枚钉（换字形必须是有意为之）。这条不是复述代码，而是把「这一行凭什么用这枚」的裁决
     * 留在测试里：想换任何一枚，必须同时改本表并写下理由，而不是顺手换一枚看着好看的。
     */
    @Test
    fun `the seven icons are pinned feature by feature`() {
        expectedIcons.forEach { (entry, icon) ->
            val body = entryBody(entry)
            assertTrue(body.isNotEmpty(), "取不到 `$entry(` 的枚举条目（改名/搬家：本条失去对象）")
            assertTrue(
                """icon = AppIcon.$icon""" in body,
                "$entry 的入口字形必须是 AppIcon.$icon（2026-11-02 裁决：每枚功能对上一件具体的物），实际条目为：\n$body",
            )
        }
    }

    // ---------- 2. 站点侧：列表行读功能自己的字形 ----------

    /**
     * 「AI 学」主页那七行（`AiFeatureRow`）必须读 `feature.icon`。
     *
     * 反向那半（0 处 `AppIcon.Spark`）才是用户报的那枚缺陷本体：屏侧写死字面量时，
     * 目录里就算声明了七枚不同的字形也一行都不会变——这正是「图标全一样」能被改回去的缝。
     */
    @Test
    fun `the feature row draws the feature's own icon instead of the shared spark`() {
        val row = bodyOf(learning, "private fun AiFeatureRow(")
        assertTrue(row.isNotEmpty(), "取不到 `AiFeatureRow(` 本体（改名/搬家：本条失去对象）")
        assertTrue(
            "InlineIconBadge(icon = feature.icon" in row,
            "功能行的徽标必须读 `feature.icon`（目录是字形的唯一出处）：\n$row",
        )
        assertEquals(
            0,
            countMatches(row, Regex("""AppIcon\.Spark""")),
            "功能行仍在写死 `AppIcon.Spark`：目录里的七枚字形一行也不会生效——用户报的「图标全都一样」就是这么长回来的",
        )
    }

    /**
     * 详情页头徽标同一条口径：用户从「拍照翻译」那一行点进去，页头却亮着一枚 Spark，
     * 等于把刚建立的「相机＝拍照翻译」的对应当场剪断。批 3 之前这枚徽标本就是功能自己的单字
     * （析/拍/画），换成统一 Spark 是为了止「文字当图标」，不是要抹掉功能身份。
     */
    @Test
    fun `the feature page header badge follows the feature instead of the shared spark`() {
        assertTrue(
            "AiFeatureBadge(feature)" in featureScreen,
            "详情页头必须把当前功能交给徽标（`AiFeatureBadge(feature)`）：徽标要知道自己画的是哪一枚功能",
        )
        val badge = bodyOf(featureScreen, "private fun AiFeatureBadge(")
        assertTrue(badge.isNotEmpty(), "取不到 `AiFeatureBadge(` 本体（改名/搬家：本条失去对象）")
        assertTrue(
            "AppGlyph(icon = feature.icon" in badge,
            "详情页头徽标必须读 `feature.icon`：\n$badge",
        )
        assertEquals(
            0,
            countMatches(badge, Regex("""AppIcon\.Spark""")),
            "详情页头徽标仍在写死 `AppIcon.Spark`：七枚功能点进去都是同一枚，功能身份在第二跳就丢了",
        )
    }

    // ---------- 取样助手 ----------

    /**
     * 取一枚枚举条目的源文本：从 `    ENTRY(` 到下一处 4 空格缩进的 `),`。
     *
     * `AiFeature.kt` 的条目恰好是「4 空格缩进的名字 + 圆括号」，条目的收尾 `),` 也是 4 空格缩进，
     * 而 `dependencies = listOf(` 的收尾是 8 空格缩进——所以按 4 空格 `),` 取样不会截进下一条，
     * 也不会提前断在依赖表里。锚点改名时返回空串，调用处各自响亮判红。
     */
    private fun entryBody(entry: String): String {
        val start = catalogue.indexOf("\n    $entry(")
        if (start < 0) return ""
        val end = catalogue.indexOf("\n    ),", start)
        return if (end < 0) "" else catalogue.substring(start, end)
    }

    /**
     * 按花括号配对取 `fun X(` 的函数体；取不到返回空串。
     *
     * 函数体左花括号取**括号深度为 0** 的那一枚：默认参数里可以带 lambda 空体，照「签名之后第一枚 `{`」
     * 配对会取到那个 `{}`（`AiLearningLayoutContractTest` 同一条踩坑注记）。
     */
    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        var parenDepth = 0
        for (index in start until source.length) {
            when (source[index]) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '{' -> if (parenDepth == 0) return balancedFrom(source, index)
            }
        }
        return ""
    }

    /** 从左花括号起按深度配对，返回去掉外层花括号的段。 */
    private fun balancedFrom(source: String, open: Int): String {
        if (source.getOrNull(open) != '{') return ""
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
