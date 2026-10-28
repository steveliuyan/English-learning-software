package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 4 Task 2：未上线骨架页（`AiFeatureScreen`）的新骨架契约（spec §5「骨架页」行：
 * 「两按钮上移，下半屏补一句轻量说明」）。
 *
 * 改前这屏是批 3 之前的形态：自写 `TextButton("← 返回 AI 学")` 当返回位、44dp 渐变徽标 + 标题 Row
 * 与共享页头并存、`SectionCard`（`Card` + 1dp 描边）四张、36 处静态薄荷桥、12 处 `MaterialTheme.typography`；
 * 更要紧的是**顺序**：唯一的两个真动作压在「现在可以做的」卡片里、排在两句解释文字之后，
 * 用户在 411dp 屏上要滑过大半屏才看到「去配置 AI 服务」。
 *
 * 七条判据：
 *  - 1–5、7 是本批要做的六件事，RED 阶段各自必须红；
 *  - 6 是**许可链不许顺手改坏**（`StatusPill` 的两档墨与徽章单一出处），RED 阶段应设计上先绿。
 *
 * 取窗一律按括号深度配对（注释先剥），锚点改名即 `fail`，不会静默空转。
 */
class AiFeatureLayoutContractTest {

    private val source: String by lazy { stripComments(uiScreenSource("AiFeatureScreen.kt")) }

    /**
     * 页末那句轻量说明。spec §5 只给了「补一句轻量说明」的方位，没给文案；本句是把它落成一句话：
     * 先说**现在真能用的两件事**，再说其余能力的现状——不含任何「即将上线 / 马上支持」的承诺。
     */
    private val lightNote = "这个功能上线前，上面两件事随时可用；其余能力点开只会看到进度。"

    // ---------- 1. 标题与返回位归共享页头 ----------

    /**
     * 改前是「自写 `TextButton` 返回 + 44dp 徽标 + 标题 Row」两套页头并排：标题 `headlineSmall` 与
     * 各屏共享页头不是一个档位，返回位的触控面也不受 `AppScreenHeader` 的 48dp 保证。
     * 新版标题进 `AppScreenHeader`，返回位由组件的 48dp 可点盒承担（`contentDescription = "返回"`）。
     */
    @Test
    fun `the shared header carries the title and the back slot`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("AppScreenHeader("))),
            "整屏只许一个页头：改前是「自写 TextButton 返回」+「标题 Row」两套并排（`ai_feature_title` 那行）",
        )
        val header = callSpan("AppScreenHeader(")
        assertTrue(header.isNotEmpty(), "取不到 `AppScreenHeader(` 的调用段（页头改名/搬家：本判据失去对象）")
        assertTrue("title = feature.title" in header, "标题必须由页头承载并直取目录原文：$header")
        assertTrue("subtitle = feature.summary" in header, "副标题改用页头的 `subtitle` 槽（改前是标题下面第二行 Text）")
        assertTrue("onBack = onBack" in header, "返回位必须走页头的 `onBack`（48dp 可点盒 + contentDescription \"返回\"）")
        listOf(
            "TextButton(" to "旧自写返回按钮",
            "ai_feature_back" to "旧返回锚（页头自带返回位，connected 侧改按 contentDescription \"返回\" 点）",
            "ai_feature_title" to "旧标题锚（标题进页头后没有独立节点，connected 侧改读 app_screen_header）",
            """contentDescription = "返回"""" to "页头返回位的语义由组件提供，屏侧不得再写一份",
        ).forEach { (retired, why) ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(retired))),
                "`$retired` 必须随旧页头一起退役：$why",
            )
        }
    }

    // ---------- 2. 两按钮上移、说明下移 ----------

    /**
     * spec §5「骨架页」行原文是「两按钮上移，下半屏补一句轻量说明」。改前顺序是
     * 「页头 → 进度卡 → 「现在可以做的」标题 → 解释句 → 主按钮 → 次按钮」，两个真动作在最下面。
     * 新版顺序：页头 → 进度卡 → 主按钮 → 次按钮 → 页末说明。
     */
    @Test
    fun `the two actions move above the copy and the note closes the page`() {
        val order = listOf(
            "共享页头" to "AppScreenHeader(",
            "当前进度卡" to """testTag("ai_feature_status_card")""",
            "主操作（去配置 AI 服务）" to """testTag("ai_feature_open_settings")""",
            "次操作（回到今日学习）" to """testTag("ai_feature_open_learning")""",
            "页末轻量说明" to lightNote,
        ).map { (label, anchor) -> label to source.indexOf(anchor) }
        order.forEach { (label, index) ->
            assertTrue(index >= 0, "取样面上找不到「$label」（本条会空转：锚点被删或文案被改）")
        }
        order.zipWithNext().forEach { (earlier, later) ->
            assertTrue(
                earlier.second < later.second,
                "「${earlier.first}」必须排在「${later.first}」之前（spec §5「骨架页」：两按钮上移、说明下移）",
            )
        }
    }

    // ---------- 3. 主次分明：主操作实底、次操作描边 ----------

    /**
     * 改前两枚都是 `Button`，主操作实底 `MintPrimary`、次操作 `MintSurface` + 1dp 描边——主次已经有形，
     * 但两枚都裹在「现在可以做的」卡片里，视觉上是「卡片里的两行」而不是「一屏一个主操作」。
     * 新版把主次搬到页级：主操作实底域色（全屏唯一一枚实底按钮），次操作面 + 描边。
     *
     * 容器取 `appDomain(AppDomain.AiSpeech).base` 而不是 `MintPrimary`：两者是同一个值
     * （`MintPrimary` 的 getter 就是这句），但前者把「这枚容器随主题」写在调用点上，
     * `AiBadgeContrastFixTest` 的站点腿按容器表达式取前景，两种拼法都认。
     */
    @Test
    fun `the settings action is the only filled button and learning is its outlined twin`() {
        val primary = callSpanContaining("Button(", """testTag("ai_feature_open_settings")""")
        assertTrue(primary.isNotEmpty(), "取不到主操作那枚 `Button(`（本条会空转）")
        assertTrue(
            "containerColor = appDomain(AppDomain.AiSpeech).base" in primary,
            "主操作必须是实底域色容器（全屏唯一的主操作）：$primary",
        )
        assertTrue(
            "contentColor = if (!LocalIsDark.current) colors.onMint else colors.onBrand" in primary,
            "实底域色容器两套主题皆亮，前景必须按 R-23.13 分档（裸 `colors.onMint` 在深套是亮墨压亮底 ≈1.49:1）：$primary",
        )
        val secondary = callSpanContaining("Button(", """testTag("ai_feature_open_learning")""")
        assertTrue(secondary.isNotEmpty(), "取不到次操作那枚 `Button(`（本条会空转）")
        assertTrue(
            "containerColor = colors.surface" in secondary,
            "次操作是「面 + 描边」：容器取中性 `surface`，不再与主操作争实底域色：$secondary",
        )
        assertTrue(
            "border(1.dp, colors.outlineSoft, AppShape.Button)" in secondary,
            "次操作的描边取令牌（`outlineSoft`）+ 共享按钮圆角档，不直写 `RoundedCornerShape(14.dp)`：$secondary",
        )
        assertTrue("appDomain(" !in secondary, "次操作不得再取域色容器：那就成了第二枚主操作：$secondary")
        assertEquals(
            1,
            countMatches(source, Regex("""containerColor = appDomain\(""")),
            "全屏恰一枚实底域色容器（主操作）：两枚起就是「两个主按钮」，用户不知道先点哪个",
        )
        listOf(primary, secondary).forEach { call ->
            assertTrue(
                "heightIn(min = 48.dp)" in call,
                "F5-03 的 48dp 触控下限不许退化（两枚按钮都要）：$call",
            )
        }
    }

    // ---------- 4. 页末一句轻量说明，替掉「现在可以做的」块 ----------

    /**
     * 改前那枚卡片是「标题『现在可以做的』+ 一句『这个功能还没开发完，但下面两件事现在就能用。』
     * + 两枚按钮」：按钮上移后这句话的方位词（「下面」）当场失实，卡片本身也只剩一句铺垫。
     * 新版把它换成页末一句说明：先说能用的两件事，再说其余能力的现状。
     */
    @Test
    fun `the page closes with one light note instead of a promissory block`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape(lightNote))),
            "页末轻量说明必须恰一处：0 = 补句丢了（spec §5「下半屏补一句轻量说明」没兑现），" +
                "2 = 同一句话在两处各写一遍",
        )
        assertTrue(
            """testTag("ai_feature_light_note")""" in source,
            "页末说明挂 `ai_feature_light_note`：adb 走查与 connected 侧按它定位这句",
        )
        listOf(
            "现在可以做的" to "旧块标题",
            "这个功能还没开发完，但下面两件事现在就能用。" to "旧铺垫句（按钮上移后「下面」失实）",
        ).forEach { (retired, why) ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(retired))),
                "`$retired` 必须撤：$why",
            )
        }
        listOf("Card(" to "M3 `Card(`", "SectionCard(" to "屏侧私有 `SectionCard`").forEach { (retired, why) ->
            assertEquals(
                0,
                countMatches(source, Regex("""(?:^|[^A-Za-z0-9_])${Regex.escape(retired)}""")),
                "屏侧不得再有$why：分组靠共享 `SoftCard`，一屏至多一枚强卡",
            )
        }
    }

    // ---------- 5. 排印与墨色全部走组合期令牌 ----------

    /**
     * 改前 36 处静态薄荷桥 + 12 处 `MaterialTheme.typography` + 0 处 `AppType`：深色档整页停在浅值，
     * 而且 `fontWeight = FontWeight.Bold` 直写绕开了六档档锁（spec §2.2）。新版只许组合期读
     * `appColors()` / `appDomain(...)` 与六档 `AppType`。
     */
    @Test
    fun `the screen speaks the six step type scale and the themed accessors only`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bMint[A-Za-z]*""")),
            "屏侧不得再读静态薄荷桥：墨色一律 `appColors()` / `appDomain(...)`（组合期），深套才会翻色",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "屏侧不得再用 `MaterialTheme.typography`：六档 `AppType` 是唯一排印入口（spec §2.2 档锁）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("fontWeight ="))),
            "六档 token 自带字重，屏侧不得再 `fontWeight =` 覆写（会绕开档锁）",
        )
        // 一一对应而非「至少 N 处」：屏侧每一行文本都必须显式取档，行内排印归共享组件。
        val textCalls = countMatches(source, Regex("""(?<![A-Za-z])Text\("""))
        val typedReads = countMatches(source, Regex("""AppType\.[A-Z][A-Za-z]*"""))
        assertTrue(textCalls > 0, "取不到任何 `Text(`：取样面失效，本条会空转")
        assertTrue(
            textCalls == typedReads,
            "本屏每一处 `Text(` 都要显式取一档 `AppType`（$textCalls 处 Text / $typedReads 处读点）：" +
                "差值说明有行是 M3 默认排印长出来的",
        )
    }

    // ---------- 6. 许可链（徽章单一出处 + 分档墨）不许顺手改坏 ----------

    /**
     * `StatusPill` 是**亮底容器**（实底域色，两套主题皆亮）上的一枚墨：前景按 R-23.13 分三档，
     * 浅套 `onMint`、深套 `onBrand`、未上线档 `text1`。改前改后这条链一字不动——它由
     * `AiBadgeContrastFixTest` 按两套活值当场复算，`AiFeatureScreenTest` 在设备侧取色复核。
     * 徽章面文案只有一个出处（R-23.9）：`featureStatusBadge(implemented)`，屏侧不得自带词。
     */
    @Test
    fun `the status pill keeps its single wording source and its split ink`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("featureStatusBadge("))),
            "徽章文案必须仍由 `featureStatusBadge(implemented)` 产出（R-23.9：与目录同文件，不新开组件）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(""""(可用|未实现)"""")),
            "屏侧不得自带徽章文案字面量（与 `AiFeatureTest` 同口径）",
        )
        assertTrue(
            "private fun StatusPill(" in source,
            "`StatusPill` 必须还在（`AiFeatureScreenTest` 按 `ai_feature_status_pill` 取色）",
        )
        assertTrue(
            "color = if (implemented && !LocalIsDark.current) colors.onMint else if (implemented) colors.onBrand else colors.text1," in source,
            "「现在能用」档的分档墨链必须逐字保留：浅套 onMint / 深套 onBrand / 未上线档 text1",
        )
        assertTrue(
            "background(if (implemented) appDomain(AppDomain.AiSpeech).base else colors.tint, AppShape.Pill)" in source,
            "徽章两档容器：能用档实底域色（与主操作同族）、未上线档中性 `tint`；圆角走 `AppShape.Pill`",
        )
    }

    // ---------- 7. 头部渐变徽标与品牌渐变保持静态 ----------

    /**
     * 44dp 渐变徽标是 R-23.15 登记过的「携带修复」：徽标字形的 tint 取 `colors.onBrand`
     * （白压渐变起点只有 1.29:1）。它不随骨架搬走——只是从「标题 Row 的左边」挪进页头的 `badge` 槽。
     *
     * 2026-11-02 换锚（图标去重）：字形实参由 `AppIcon.Spark` 改成 `feature.icon`——七枚功能点进来
     * 曾都是同一枚 AI 域锚点，用户评审判成缺陷。本条钉的仍是这**一枚** 22dp onBrand 调用：数量恒为 1、
     * tint 仍必须 onBrand、size 仍必须 22dp，判据一字未放宽。
     */
    @Test
    fun `the gradient badge keeps its on brand glyph and the gradient stays static`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("AppGlyph(icon = feature.icon, tint = colors.onBrand, size = 22.dp)"))),
            "头部渐变徽标的 `AppGlyph(feature.icon)` 必须恰一枚且 tint 取 onBrand（R-23.15；白字压渐变 ≈1.3:1）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("Color.White"))),
            "品牌渐变上不得长出白字/白容器（F5-03 禁令 2 的最坏形状）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""Brush\.linearGradient\(""")),
            "品牌渐变的 `Brush.linearGradient(` 全文件只许留顶层静态声明那一处（`AiFeatureGradient`）",
        )
        assertTrue(
            "private val AiFeatureGradient = Brush.linearGradient(" in source,
            "`AiFeatureGradient` 必须是顶层 `private val`：`MintTokenIsComposableContractTest` 按符号名钉着" +
                "「组合期之外求值的品牌渐变持有符号」，挪进函数体会同时失掉静态性",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""\.background\(AiFeatureGradient""")),
            "渐变只许铺在徽标容器底上：第二处就是「渐变描边套卡」那批旧形状",
        )
    }

    // ---------- 取样助手 ----------

    /**
     * 取一次调用的完整段（`anchor` 含左括号，如 `"Button("`）；取不到返回空串。
     *
     * 段必须**连尾随 lambda 一起**取：`Button(…) { … }` 的内容写在 lambda 里，
     * 只取括号段会对着「没有内容」的空段判红。
     */
    private fun callSpan(anchor: String): String {
        val start = source.indexOf(anchor)
        if (start < 0) return ""
        return spanAt(start + anchor.length - 1)
    }

    /**
     * 取**含 `needle` 的那一枚**调用段：同一文件里 `Button(` 有两处（主/次操作），
     * 只取第一枚会拿主操作的实参去判次操作（取样面错位），所以按要点名的 tag 反查。
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

}
