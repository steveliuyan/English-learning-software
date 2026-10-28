package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 4 Task 1：AI 主页（`AiLearningScreen`）的新骨架契约（spec §3.5 / §5「AI 主页」行）。
 *
 * 这屏原样是批 3 之前的形态：四张 M3 `Card` 壳各自 `border(1.dp, MintOutline, …)`、十枚
 * `RoundedCornerShape` 直写、全部文案走 `MaterialTheme.typography`、35 处静态薄荷桥、
 * 「查看词表」藏在第二层卡里的一枚 `PillButton`，而首屏能看见的第一句副标题是
 * 「用你学过的词，生成专属的阅读与练习」——一句 AI 网关还没接通时兑现不了的承诺。
 *
 * spec §3.5 定的骨架是「品牌渐变 hero（本屏唯一强卡）→ 现在能用裸行组 → 未上线功能弱卡（可展开）」，
 * 所以七条判据：
 *  - 1–6 是**本批要做的六件事**，RED 阶段各自必须红；
 *  - 7 是**按钮收口**（现状本来就无实底按钮可退，红线钉在「不许长回来」）。
 *
 * 取窗一律按符号深度配对（注释先剥），锚点改名即 `fail`，不会静默空转。
 */
class AiLearningLayoutContractTest {

    private val source: String by lazy { stripComments(uiScreenSource("AiLearningScreen.kt")) }

    /**
     * 旧副标题（AI 网关未接通时兑现不了的承诺句）。它压在全站对比度最低的那行位置上，
     * 又给用户一个「这套东西现在就能生成内容」的错误前置，本批撤掉。
     */
    private val oldSubtitle = "用你学过的词，生成专属的阅读与练习"

    /**
     * 新副标题：只描述这一屏是什么（能力目录 + 接通进度），不承诺任何生成能力。
     *
     * 定稿文案待评审席追认（批 4 计划「Blocked」栏已登记）：本判据钉的是「中性、无承诺」这条形状，
     * 换词只改这一处常量与屏侧同一行，不牵动其余六条。
     */
    private val newSubtitle = "这里汇总 AI 能力与各自的接通进度"

    // ---------- 1. 品牌渐变 hero 恰一处、无返回位 ----------

    /**
     * 一级页（tab 根页）的 hero 就是这枚品牌渐变头卡，屏侧不再另起 `AppScreenHeader(hero = true)`：
     * 两枚 28sp 标题（页头一枚 + 头卡一枚）会当场撞进同一屏，connected 侧
     * `onNodeWithText("AI 助学")` 也会变成多节点。
     */
    @Test
    fun `the gradient hero is the only hero and there is no back slot`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""testTag("ai_learning_header")"""))),
            "品牌渐变头卡必须恰一处（`ai_learning_header`）：它是本屏唯一强卡，多一枚就是卡片汤",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""\.background\(AiHeaderGradient""")),
            "渐变只许铺在头卡底上：第二处 `background(AiHeaderGradient` 就是「渐变描边套卡」那批旧形状",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""Brush\.linearGradient\(""")),
            "品牌渐变的 `Brush.linearGradient(` 全文件只许留顶层静态声明那一处（`AiHeaderGradient`）",
        )
        assertTrue(
            "private val AiHeaderGradient = Brush.linearGradient(" in source,
            "`AiHeaderGradient` 必须是顶层 `private val`：`MintTokenIsComposableContractTest` 按符号名钉着" +
                "「组合期之外求值的品牌渐变持有符号」，挪进函数体内会同时失掉静态性与该判据的对象",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("""contentDescription = "返回""""))),
            "tab 根页没有返回位：屏侧不得自写 `contentDescription = \"返回\"`",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("AppScreenHeader("))),
            "本屏的 hero 是渐变头卡（spec §3.5），不另调共享页头：两枚 28sp 标题会撞屏，且 connected 侧的" +
                "`onNodeWithText(\"AI 助学\")` 会从单节点变多节点",
        )
    }

    /**
     * 屏名与底导同步（2026-11-02 用户定名「AI 助学」）：hero 标题必须用定名，屏侧不留「AI 学」这半截旧名。
     *
     * 这条同时守住批 4 记下的那条不变量：hero 标题是全屏唯一一枚 `"AI 助学"` 文本，多一枚就会让
     * connected 侧 `onNodeWithText("AI 助学")` 从单节点变多节点。
     */
    @Test
    fun `the hero title speaks the destination name the user settled on`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"AI 助学\""))),
            "hero 标题恰一处「AI 助学」：0 = 定名没落地（屏名还停在旧名），2+ = 标题被抄了第二份",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("\"AI 学\""))),
            "「AI 学」已被 2026-11-02 定名替换：屏侧不得再有一处独立成串的旧名",
        )
        assertTrue(
            "AI 助学如何起作用？" in source,
            "说明折叠行的可见标题必须跟到定名（`AiLearningScreenTest` 同批改按新文案点）",
        )
        assertTrue(
            "AI 助学如何起作用说明" in source,
            "折叠行的可及名必须跟到定名：读屏念的还是旧名 = 改了一半",
        )
    }

    // ---------- 2. 副标题换成中性一句 ----------

    @Test
    fun `the subtitle speaks neutral copy instead of promising generated content`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape(oldSubtitle))),
            "旧副标题「$oldSubtitle」必须撤：AI 网关未接通时它是一句兑现不了的承诺（spec §5「AI 主页」行）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape(newSubtitle))),
            "中性副标题必须恰一处：它是本屏「这一页是什么」的唯一说明，0 = 换骨架时把副标题丢了，" +
                "2 = 头卡与页面各写一遍",
        )
    }

    // ---------- 3. 「查看词表」收成裸行、直达词库 ----------

    /**
     * 旧形状是「卡里套一张渐变描边卡，再套一枚 `PillButton("查看详情")`」，点「查看词表」要两跳。
     * 新版：一枚 `BareRow` 整行可点，标题「查看词表」、副标题就是真实词数（`wordListLine`），
     * 一次点击直达词库。
     */
    @Test
    fun `the word list entry is one bare row that goes straight to the library`() {
        assertEquals(
            2,
            countMatches(source, Regex("""\bBareRow\(""")),
            "裸行恰两处：词表入口一枚 + 组内功能行那一枚（`AiFeatureRow` 的实现体）。" +
                "1 = 词表还压在卡片里，3 = 同组行被抄成了第二份私有实现",
        )
        val row = callSpanContaining("BareRow(", "ai_learning_nav_row")
        assertTrue(row.isNotEmpty(), "取不到 `BareRow(` 调用，判据会空转")
        assertTrue(
            """testTag("ai_learning_nav_row")""" in row,
            "词表入口的 tag 必须是 `ai_learning_nav_row`（旧 `ai_learning_nav_card` / `ai_learning_nav_detail` 退役）",
        )
        assertTrue(
            "onClick = onOpenWordList" in row,
            "裸行必须整行接屏的 `onOpenWordList`：再包一层 lambda 会把目的地通道变成第二个真值",
        )
        assertTrue(
            "wordListLine(" in row,
            "副标题必须是真实词数（`wordListLine`）：今日计划不可读时它自己会说不可读，不拿 0 冒充",
        )
        listOf(
            "ai_learning_nav_detail",
            "ai_learning_nav_card",
            "ai_learning_word_list_count",
        ).forEach { retired ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(retired))),
                "旧锚 `$retired` 必须随旧形状一起退役：它承载的是「卡 + 按钮」那套两跳入口",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("""testTag("ai_learning_word_list_count")"""))),
            "词数不再单独挂 tag：它已经是裸行副标题，connected 侧改按文本点（同批已改）",
        )
    }

    // ---------- 4. 分组落三档容器（软卡 + 发丝线），M3 Card 归零 ----------

    /**
     * 旧形状四张 `Card` 各自 `border(1.dp, MintOutline, …)`：一屏九块描边卡 = 卡片汤。
     * 新版全部走共享三档容器：软卡分组（不描边）、组内行靠 `topDivider` 的发丝线分隔。
     */
    @Test
    fun `the groups collapse into soft cards and bare rows with no hand drawn cards`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "屏侧不得再用 M3 `Card(`：三档容器（`StrongCard`/`SoftCard`/`BareRow`）就是替代它的那一层",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("CardDefaults"))),
            "`CardDefaults` 随 `Card(` 一起撤",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\.border\(""")),
            "屏侧不得自画描边：软卡不描边（`ContainerContractTest` 同口径），同组靠发丝线表达",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bStrongCard\(""")),
            "本屏的强卡是品牌渐变头卡，不是 `StrongCard`：再来一枚实底深卡就是两枚强卡",
        )
        assertEquals(
            4,
            countMatches(source, Regex("""\bSoftCard\(""")),
            "软卡恰四枚：现在能用组 / 未上线组 / 词表入口 / 使用说明（每张卡 = 一件事）",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("topDivider = "))) >= 2,
            "组内行必须靠 `topDivider` 的发丝线分隔（`EntryRow`/`BareRow` 的共享画法），" +
                "屏侧不再手搓 `HorizontalDivider`",
        )
    }

    // ---------- 5. 排印与墨色全部走组合期令牌 ----------

    /**
     * 旧文件 35 处静态薄荷桥（`MintPrimaryDark` / `MintSurface` / `MintOutline` / `MintTint` /
     * `MintBackground` / `MintTextMuted`）+ 12 处 `MaterialTheme.typography` + 0 处 `AppType`：
     * 深色档在这屏整页停在浅值。新版只许组合期读 `appColors()` 与六档 `AppType`。
     */
    @Test
    fun `the screen speaks the six step type scale and the themed accessors only`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bMint[A-Za-z]*""")),
            "屏侧不得再读静态薄荷桥：墨色一律 `appColors()`（组合期），深套才会翻色",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "屏侧不得再用 `MaterialTheme.typography`：六档 `AppType` 是唯一排印入口（spec §2.2 档锁）",
        )
        // 一一对应而非「至少 N 处」：本屏自己只画 5 行文本（头卡标题/副标题/胶囊 + 说明条小标题/正文），
        // 每一行都必须显式取六档档位；行内排印归共享组件（BareRow/EntryRow/SectionHeading 内部读 AppType）。
        // 用「Text( 数 == AppType 读点数」这条不变式，比钉死一个数字更抗改动，也不给「漏一行默认排印」留缝。
        val textCalls = countMatches(source, Regex("""(?<![A-Za-z])Text\("""))
        val typedReads = countMatches(source, Regex("""AppType\.[A-Z][A-Za-z]*"""))
        assertTrue(textCalls > 0, "取不到任何 `Text(`：取样面失效，本条会空转")
        assertTrue(
            textCalls == typedReads,
            "本屏每一处 `Text(` 都要显式取一档 `AppType`（$textCalls 处 Text / $typedReads 处读点）：" +
                "差值说明有行是 M3 默认排印长出来的",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("fontWeight ="))),
            "六档 token 自带字重，屏侧不得再 `fontWeight =` 覆写（会绕开档锁）",
        )
    }

    // ---------- 6. 白胶囊与品牌渐变保持登记形状 ----------

    /**
     * 头卡上那两件事**不许顺手改**（`AiBadgeContrastFixTest` 逐行钉着，这里是第二面）：
     *  - 状态胶囊的容器是主题不变的白 `Color.White.copy(alpha = 0.85f)`，前景只能取登记的两枚静态墨；
     *  - 渐变上的两行文案取 `onBrand`，白字压渐变起点只有 1.29:1。
     */
    @Test
    fun `the hero keeps the theme invariant pill and the static brand gradient`() {
        val hero = bodyOf("private fun AiHeaderCard(")
        assertTrue(hero.isNotEmpty(), "取不到 `AiHeaderCard(` 本体（函数改名/搬家：本判据失去对象）")
        assertEquals(
            1,
            countMatches(hero, Regex(Regex.escape("Color.White.copy(alpha = 0.85f)"))),
            "状态胶囊容器必须仍是那枚主题不变白（容器主题化归 Task 26/27，本批未授权）",
        )
        assertEquals(
            2,
            countMatches(hero, Regex(Regex.escape("color = appColors().onBrand"))),
            "头卡两行文案（标题与副标题）必须都走 `onBrand`：少一枚 = 还有白字，多一枚 = 墨挪到了别处",
        )
        assertEquals(
            2,
            countMatches(source, Regex("""(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")),
            "全文件静态令牌读点必须恰是白胶囊前景那一行登记的两枚（R-23.14 回退形状）：" +
                "多出来 = 又长出静态墨（深套停在浅值）",
        )
        assertTrue(
            "color = if (aiConfigured) DomainColors.AiSpeech.deep else AppPalette.TextSecondary," in source,
            "登记的例外必须逐字留在白胶囊前景那一行",
        )
    }

    // ---------- 7. 按钮收口 ----------

    /**
     * 旧形状屏上唯一的实底按钮是词表入口那枚 `PillButton("查看详情")`。新版**零实底按钮**：
     * 两个折叠入口走共享 `EntryRow`（56dp 行 + 展开态语义），词表入口走裸行。
     * 判据按词界取，`TextButton(` / `PillButton(` 不被 `\bButton\(` 误算。
     */
    @Test
    fun `no material button survives on the screen`() {
        listOf("Button(", "PillButton(", "TextButton(").forEach { anchor ->
            assertEquals(
                0,
                countMatches(source, Regex("""(?:^|[^A-Za-z0-9_])${Regex.escape(anchor)}""")),
                "屏侧不得再有 $anchor：实底按钮在本屏没有主次之分要表达，入口一律裸行/入口行",
            )
        }
        assertEquals(
            2,
            countMatches(source, Regex("""\bEntryRow\(""")),
            "两枚折叠入口（未上线功能 / 使用说明）必须都走共享 `EntryRow`：手搓第二份折叠件会撞组件份数棘轮",
        )
        assertTrue(
            """testTag("ai_learning_unreleased_toggle")""" in source,
            "未上线折叠行的 tag 必须保留：`AiLearningScreenTest` 与 `AppScreenTest` 都按它点击",
        )
        assertTrue(
            """testTag("ai_learning_explainer_toggle")""" in source,
            "说明折叠行的 tag 必须保留：`AiLearningScreenTest.explainer_starts_collapsed_and_expands_on_tap` 按它点击",
        )
    }

    // ---------- 取样助手 ----------

    /**
     * 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。
     *
     * 函数体左花括号取**括号深度为 0** 的那一枚：默认参数里可以带 lambda 空体，照「签名之后第一枚 `{`」
     * 配对会取到那个 `{}`（`ReadingHistoryLayoutContractTest` 同一条踩坑注记）。
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
     * 段必须**连尾随 lambda 一起**取：`BareRow(…)` 之后紧跟的行/列 lambda 里才是行的实际内容，
     * 只取括号段会对着「没有内容」的空段判红。
     */
    private fun callSpan(anchor: String): String {
        val start = source.indexOf(anchor)
        if (start < 0) return ""
        return spanAt(start + anchor.length - 1)
    }

    /**
     * 取**含 `needle` 的那一枚**调用段：同一文件里 `BareRow(` 有两处，
     * 只取第一枚会对着组内功能行判红/判绿（取样面错位），所以按要点名的 tag 反查。
     */
    private fun callSpanContaining(anchor: String, needle: String): String {
        var from = 0
        while (true) {
            val start = source.indexOf(anchor, from)
            if (start < 0) return ""
            val span = spanAt(start + anchor.length - 1)
            if (needle in span) return span
            if (span.isEmpty()) return ""
            from = start + 1
        }
    }

    /** 从 `open`（指向左括号）起配对括号；随后若紧跟 `{ … }`，把尾随 lambda 一并纳入段。 */
    private fun spanAt(open: Int): String {
        if (source.getOrNull(open) != '(') return ""
        var depth = 0
        var end = -1
        for (index in open until source.length) {
            when (source[index]) {
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
        while (cursor < source.length && source[cursor].isWhitespace()) cursor++
        if (source.getOrNull(cursor) == '{') {
            val lambdaEnd = braceEnd(cursor)
            if (lambdaEnd > 0) return source.substring(open, lambdaEnd + 1)
        }
        return source.substring(open, end + 1)
    }

    /** 从左花括号起按深度配对，返回右花括号下标；取不到返回 -1。 */
    private fun braceEnd(open: Int): Int {
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
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
        val end = braceEnd(open)
        return if (end < 0) "" else source.substring(open + 1, end)
    }
}
