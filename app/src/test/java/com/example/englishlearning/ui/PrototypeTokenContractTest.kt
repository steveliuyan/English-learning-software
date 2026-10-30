package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 2026-10-29 原型对齐批 0：设计令牌与冻结 HTML 原型的逐值契约。
 *
 * 权威源：原型 `assets/app.css`（只读）→ `docs/decisions/2026-10-29-ui-prototype-token-alignment.md`
 * 的映射表 → `ui/theme/AppColors.kt` / `AppPalette.kt` / `AppType.kt` / `AppShape.kt` / `AppMotion.kt`。
 *
 * 口径沿用仓库既有契约测试：读剥注释后的源码文本、目录缺失大声失败、每条正则配合成正/负片段、
 * 对比度按 WCAG 相对亮度当场复算（不写「合格」结论，只写实测值）。
 *
 * 本文件**不**覆盖 M3 角色装配（那仍由 `MaterialThemeAssemblyContractTest` 负责），
 * 只钉原型 token 本身、排印度量、形状、动效、字体与贴纸资源。
 */
class PrototypeTokenContractTest {

    private val themeText: String by lazy { stripComments(File("$THEME_PATH/AppColors.kt").readText()) }
    private val paletteText: String by lazy { stripComments(File("$THEME_PATH/AppPalette.kt").readText()) }
    private val typeText: String by lazy { stripComments(File("$THEME_PATH/AppType.kt").readText()) }
    private val shapeText: String by lazy { stripComments(File("$THEME_PATH/AppShape.kt").readText()) }
    private val motionText: String by lazy { stripComments(File("$THEME_PATH/AppMotion.kt").readText()) }

    // ---------- 读取工具 ----------

    private fun anchors(text: String): Map<String, String> {
        val marks = listOf("val AppColorLight", "val AppColorDark", "val DomainLight", "val DomainDark")
        return marks.associateWith { anchor ->
            val start = text.indexOf(anchor)
            assertTrue(start >= 0, "AppColors.kt 缺锚点 $anchor：令牌契约失去对象")
            val end = marks.drop(marks.indexOf(anchor) + 1)
                .map { text.indexOf(it, start + anchor.length) }
                .filter { it >= 0 }
                .minOrNull() ?: text.length
            text.substring(start, end)
        }
    }

    private fun hexOf(block: String, token: String): Long {
        val m = Regex("""(?<![\w.])$token\s*=\s*Color\(\s*(0x[0-9A-Fa-f]{8})\s*\)""")
            .find(block) ?: error("token 未以 `$token = Color(0x…)` 形状声明（注释不算）")
        return m.groupValues[1].removePrefix("0x").toLong(16)
    }

    private fun parenBlock(text: String, anchor: Regex): String {
        val m = anchor.find(text) ?: error("找不到锚点：$anchor")
        val open = m.range.last
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(open + 1, i)
                }
            }
        }
        error("锚点 $anchor 圆括号未配平")
    }

    private fun specBlock(tier: String): String =
        parenBlock(typeText, Regex("""private val ${tier}Spec\s*=\s*TextStyle\("""))

    private fun assertSpec(tier: String, vararg fragments: String) {
        val block = specBlock(tier)
        fragments.forEach { fragment ->
            assertTrue(
                block.contains(fragment),
                "$tier 档缺少原型度量 `$fragment`；实际块：${block.replace("\n", " ")}",
            )
        }
    }

    // ---------- 钉值表（原型 :root / html[data-theme='dark'] 逐值） ----------

    private val lightHex: Map<String, Long> = linkedMapOf(
        "background" to 0xFFF1F4EFL,
        "surface" to 0xFFFFFFFFL,
        "surface2" to 0xFFF7F9F5L,
        "surfaceGlass" to 0xE0FFFFFFL,
        "tint" to 0xFFE8F2EAL,
        "tint2" to 0xFFDCEBE1L,
        "tint3" to 0xFFE4F4ECL,
        "outline" to 0xFFDDE5DDL,
        "outlineSoft" to 0xFFEEF2EDL,
        "text1" to 0xFF15211CL,
        "text2" to 0xFF55665DL,
        "text3" to 0xFF65756CL,
        "brand" to 0xFF6CCBABL,
        "brandDeep" to 0xFF1A7A63L,
        "brandTint" to 0xFFE4F4ECL,
        "onMint" to 0xFF1A7A63L,
        "onBrand" to 0xFF04241CL,
        "error" to 0xFFA5291FL,
        "errorFill" to 0xFFFF3B30L,
        "dangerTint" to 0xFFFDEEECL,
        "success" to 0xFF1B5E20L,
        "feedbackAgain" to 0xFFB3573FL,
        "feedbackFuzzy" to 0xFFA8792AL,
        "feedbackKnown" to 0xFF1A7A63L,
        "strongSurface" to 0xFF1F4739L,
        "strongSurfaceHigh" to 0xFF2A5A49L,
        "onStrong" to 0xFFE9F2ECL,
        "onStrongMuted" to 0xBDE9F2ECL,
    )

    private val darkHex: Map<String, Long> = linkedMapOf(
        "background" to 0xFF101814L,
        "surface" to 0xFF161F1AL,
        "surface2" to 0xFF1A2420L,
        "surfaceGlass" to 0xE0161F1AL,
        "tint" to 0xFF1C2A24L,
        "tint2" to 0xFF22322BL,
        "tint3" to 0xFF1D322BL,
        "outline" to 0xFF2A3931L,
        "outlineSoft" to 0xFF202D27L,
        "text1" to 0xFFE9F2ECL,
        "text2" to 0xFFA5B8AEL,
        "text3" to 0xFF93A69CL,
        "brand" to 0xFF6FD9B6L,
        "brandDeep" to 0xFF7FE3C1L,
        "brandTint" to 0xFF1D322BL,
        "onMint" to 0xFF7FE3C1L,
        "onBrand" to 0xFF04241CL,
        "error" to 0xFFFF8177L,
        "errorFill" to 0xFFFF6B61L,
        "dangerTint" to 0xFF34211FL,
        "success" to 0xFF6BD98AL,
        "feedbackAgain" to 0xFFE39086L,
        "feedbackFuzzy" to 0xFFEFCB92L,
        "feedbackKnown" to 0xFF7FE3C1L,
        "strongSurface" to 0xFF22332CL,
        "strongSurfaceHigh" to 0xFF2B4038L,
        "onStrong" to 0xFFE9F2ECL,
        "onStrongMuted" to 0xB8E9F2ECL,
    )

    /** 迁移期兼容字段：批 0 保留，批 1–3 逐屏替换，批 4 删除。名单只减不增。 */
    private val legacyCompatFields = setOf("surfaceGlass", "tint3", "onMint", "onBrand", "errorFill")

    private fun productionFields(): Set<String> {
        val anchor = "data class AppColorSet("
        val start = themeText.indexOf(anchor)
        assertTrue(start >= 0, "AppColors.kt 缺 `$anchor`：字段表解析失去对象")
        val end = themeText.indexOf("\n)", start)
        assertTrue(end > start, "AppColorSet 参数表未落在「行首右括号」形状上")
        return Regex("""val\s+(\w+)\s*:\s*Color""")
            .findAll(themeText.substring(start + anchor.length, end))
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun `color set fields cover the prototype token set exactly and keep only named legacy fields`() {
        assertEquals(
            lightHex.keys,
            productionFields(),
            "AppColorSet 字段与原型 token 表不相等（多一格少一格都红）；兼容字段只允许 $legacyCompatFields",
        )
        assertEquals(28, productionFields().size, "原型 token 表应为 28 枚（23 原型 + 5 迁移期兼容）")
        assertTrue(
            legacyCompatFields.all { it in productionFields() },
            "兼容字段被提前删除会让未迁移屏编译失败：$legacyCompatFields",
        )
    }

    @Test
    fun `light and dark token values match the prototype css verbatim`() {
        val blocks = anchors(themeText)
        lightHex.forEach { (token, expected) ->
            assertEquals(expected, hexOf(blocks.getValue("val AppColorLight"), token), "浅套 $token 偏离原型")
        }
        darkHex.forEach { (token, expected) ->
            assertEquals(expected, hexOf(blocks.getValue("val AppColorDark"), token), "深套 $token 偏离原型")
        }
    }

    @Test
    fun `light and dark sets differ except brand invariant inks`() {
        val blocks = anchors(themeText)
        val light = blocks.getValue("val AppColorLight")
        val dark = blocks.getValue("val AppColorDark")
        val identical = lightHex.keys.filter { hexOf(light, it) == hexOf(dark, it) }.toSet()
        assertEquals(
            setOf("onBrand", "onStrong"),
            identical,
            "浅深同值只允许品牌渐变墨 onBrand 与强卡墨 onStrong；其余同值 = 深色下永不翻的死 token",
        )
    }

    @Test
    fun `domain colors stay on the frozen prototype domain table`() {
        val blocks = anchors(themeText)
        val light = blocks.getValue("val DomainLight")
        val dark = blocks.getValue("val DomainDark")
        val lightExpected = linkedMapOf(
            "AiSpeech" to (0xFF6CCBABL to 0xFF1A7A63L),
            "Learn" to (0xFF7BB4E6L to 0xFF175A93L),
            "Review" to (0xFFEBC68BL to 0xFF8A5712L),
            "Library" to (0xFFB7A0D9L to 0xFF65478AL),
            "Reading" to (0xFFE297A4L to 0xFF9C3B4FL),
            "Danger" to (0xFFDD8B81L to 0xFF9E2B24L),
            "Settings" to (0xFFA6B0ACL to 0xFF4A5551L),
        )
        lightExpected.forEach { (domain, pair) ->
            val m = Regex("""AppDomain\.$domain\b[\s\S]*?DomainPalette\(base = Color\((0x[0-9A-Fa-f]{8})\), deep = Color\((0x[0-9A-Fa-f]{8})\)\)""")
                .find(light)
                ?: error("DomainLight.$domain 丢失或形状偏离 DomainPalette(base = …, deep = …)")
            val (base, deep) = m.destructured
            assertEquals(pair.first, base.removePrefix("0x").toLong(16), "DomainLight.$domain.base 偏离原型域色")
            assertEquals(pair.second, deep.removePrefix("0x").toLong(16), "DomainLight.$domain.deep 偏离原型域色")
        }
        val darkExpected = linkedMapOf(
            "AiSpeech" to 0xFF6FD9B6L,
            "Learn" to 0xFF84C4EEL,
            "Review" to 0xFFEFCB92L,
            "Library" to 0xFFC6A9E4L,
            "Reading" to 0xFFEDA8B4L,
            "Danger" to 0xFFE39086L,
            "Settings" to 0xFFB4BEBAL,
        )
        darkExpected.forEach { (domain, hex) ->
            val m = Regex("""AppDomain\.$domain\b[\s\S]*?DomainPalette\(base = Color\((0x[0-9A-Fa-f]{8})\), deep = Color\((0x[0-9A-Fa-f]{8})\)\)""")
                .find(dark)
                ?: error("DomainDark.$domain 丢失或形状偏离")
            val (base, deep) = m.destructured
            assertEquals(hex, base.removePrefix("0x").toLong(16), "DomainDark.$domain.base 偏离原型域色")
            assertEquals(hex, deep.removePrefix("0x").toLong(16), "DomainDark.$domain 必须 deep = base")
        }
    }

    @Test
    fun `static light palette mirrors the runtime light set`() {
        val mirrored = mapOf(
            "Background" to 0xFFF1F4EFL,
            "Surface" to 0xFFFFFFFFL,
            "TextPrimary" to 0xFF15211CL,
            "TextSecondary" to 0xFF55665DL,
            "TextTertiary" to 0xFF65756CL,
            "Separator" to 0xFFEEF2EDL,
            "GlassFill" to 0xE0FFFFFFL,
            "Tint" to 0xFFE8F2EAL,
            "Tint2" to 0xFFDCEBE1L,
            "Tint3" to 0xFFE4F4ECL,
            "Outline" to 0xFFDDE5DDL,
            "OutlineSoft" to 0xFFEEF2EDL,
            "OnBrand" to 0xFF04241CL,
            "ErrorFill" to 0xFFFF3B30L,
            "ErrorText" to 0xFFA5291FL,
            "SuccessText" to 0xFF1B5E20L,
            "OnMintPrimary" to 0xFF1A7A63L,
        )
        mirrored.forEach { (name, expected) ->
            val m = Regex("""val\s+$name\s*=\s*Color\(\s*(0x[0-9A-Fa-f]{8})\s*\)""").find(paletteText)
                ?: error("AppPalette.$name 未以 `val $name = Color(0x…)` 声明")
            assertEquals(expected, m.groupValues[1].removePrefix("0x").toLong(16), "AppPalette.$name 与运行时浅套不同步")
        }
    }

    // ---------- 排印 ----------

    @Test
    fun `type tiers carry prototype size weight spacing and line height`() {
        assertSpec("Display", "fontSize = 46.sp", "fontWeight = FontWeight(500)", "fontFamily = AppFont.Serif", "letterSpacing = (-1.15).sp", "lineHeight = 48.sp", "fontFeatureSettings = \"tnum\"")
        assertSpec("Headline", "fontSize = 24.sp", "fontWeight = FontWeight(650)", "letterSpacing = (-0.24).sp", "lineHeight = 30.sp")
        assertSpec("Title", "fontSize = 17.sp", "fontWeight = FontWeight(650)", "letterSpacing = (-0.17).sp", "lineHeight = 27.sp")
        assertSpec("Body", "fontSize = 15.sp", "fontWeight = FontWeight.Normal", "lineHeight = 24.sp")
        assertSpec("Footnote", "fontSize = 13.sp", "fontWeight = FontWeight.Normal", "lineHeight = 21.sp")
        assertSpec("Label", "fontSize = 12.sp", "fontWeight = FontWeight(600)", "letterSpacing = 0.24.sp", "lineHeight = 16.sp")
        assertSpec("Eyebrow", "fontSize = 11.sp", "fontWeight = FontWeight(600)", "letterSpacing = 1.54.sp", "lineHeight = 14.sp")
        assertSpec("Caption", "fontSize = 12.sp", "fontWeight = FontWeight.Normal", "lineHeight = 19.sp")
        assertSpec("Meta", "fontSize = 13.sp", "fontWeight = FontWeight.Normal", "lineHeight = 20.sp")
        assertSpec("BodyLg", "fontSize = 17.sp", "fontWeight = FontWeight.Normal", "lineHeight = 26.sp")
        assertSpec("StatNum", "fontSize = 26.sp", "fontWeight = FontWeight(500)", "fontFamily = AppFont.Serif", "letterSpacing = (-0.52).sp", "lineHeight = 29.sp")
        assertSpec("Prose", "fontSize = 17.5.sp", "fontWeight = FontWeight.Normal", "fontFamily = AppFont.Serif", "lineHeight = 30.sp")
        assertSpec("ProseZh", "fontSize = 13.sp", "fontWeight = FontWeight.Normal", "lineHeight = 23.sp")
        assertSpec("NavLabel", "fontSize = 11.sp", "fontWeight = FontWeight(600)", "letterSpacing = 0.11.sp", "lineHeight = 11.sp")
        assertSpec("StrongTitle", "fontSize = 27.sp", "fontWeight = FontWeight(500)", "fontFamily = AppFont.Serif", "letterSpacing = (-0.27).sp", "lineHeight = 32.sp")
        assertSpec("ArticleTitle", "fontSize = 30.sp", "fontWeight = FontWeight(500)", "fontFamily = AppFont.Serif", "letterSpacing = (-0.6).sp", "lineHeight = 35.sp")
        assertSpec("PlanNum", "fontSize = 32.sp", "fontWeight = FontWeight(500)", "fontFamily = AppFont.Serif", "letterSpacing = (-0.64).sp", "lineHeight = 32.sp")
        // 按钮档（批 1 补）：原型 `.btn` = `--fs-body`（15）+ `font-weight:600` + `letter-spacing:-.005em`。
        // 它不是新字号，是 Body 的字号 + 按钮的字重/字距——`.btn-sm` 与 `.feedback .btn` 同样是「Meta 字号 + 600」。
        assertSpec("ButtonLabel", "fontSize = 15.sp", "fontWeight = FontWeight(600)", "letterSpacing = (-0.075).sp", "lineHeight = 24.sp")
        assertSpec("Lemma", "fontSize = 46.sp", "fontFamily = AppFont.Serif")
    }

    @Test
    fun `sans tiers do not opt into the serif family`() {
        listOf("Headline", "Title", "Body", "Footnote", "Label", "Eyebrow", "Caption", "Meta", "BodyLg", "ProseZh", "NavLabel", "ButtonLabel")
            .forEach { tier ->
                assertTrue(
                    !specBlock(tier).contains("fontFamily"),
                    "$tier 是 sans 档，原型没有给它 Newsreader；混入字族会改掉整屏观感",
                )
            }
    }

    @Test
    fun `tier inks read the current theme set per prototype hierarchy`() {
        val textInk = setOf("Display", "Headline", "Title", "Body", "BodyLg", "StrongTitle", "ArticleTitle", "Lemma", "StatNum", "PlanNum", "Prose", "ButtonLabel")
        val secondaryInk = setOf("Meta", "Footnote", "Label", "ProseZh")
        val tertiaryInk = setOf("Caption", "Eyebrow", "NavLabel")
        (textInk + secondaryInk + tertiaryInk).forEach { tier ->
            val expected = when (tier) {
                in textInk -> "text1"
                in secondaryInk -> "text2"
                else -> "text3"
            }
            val getter = Regex("""val\s+$tier\s*:\s*TextStyle[\s\S]*?get\(\)\s*=\s*${tier}Spec\.copy\(color = appColors\(\)\.$expected\)""")
            assertTrue(
                getter.containsMatchIn(typeText),
                "$tier 的墨应取 appColors().$expected（原型层级），当前 getter 缺失或取错 token",
            )
        }
    }

    // ---------- M3 装配之外的形状与动效 ----------

    @Test
    fun `shape tokens match prototype radii`() {
        listOf(
            "val Dialog = RoundedCornerShape(26.dp)",
            "val Card = RoundedCornerShape(20.dp)",
            "val Button = RoundedCornerShape(14.dp)",
            "val Small = RoundedCornerShape(10.dp)",
            "val Badge = RoundedCornerShape(14.dp)",
            "val BadgeSmall = RoundedCornerShape(10.dp)",
            "val BadgeLarge = RoundedCornerShape(20.dp)",
            "val Pill = RoundedCornerShape(percent = 50)",
        ).forEach { shape ->
            assertTrue(shapeText.contains(shape), "AppShape 缺原型圆角档：$shape")
        }
    }

    @Test
    fun `motion tokens match prototype durations and curves`() {
        listOf(
            "const val Fast = 160",
            "const val Normal = 220",
            "const val Slow = 300",
            "val EasingOut = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)",
            "val EasingIn = CubicBezierEasing(0.4f, 0f, 0.8f, 0.3f)",
        ).forEach { line ->
            assertTrue(motionText.contains(line), "AppMotion 缺原型动效钉值：$line")
        }
        // 旧调用点仍写 AppMotion.Easing：保留别名，且必须指向入场缓动，不得另立第三条曲线。
        assertTrue(
            Regex("""val\s+Easing\s*=\s*EasingOut""").containsMatchIn(motionText),
            "AppMotion.Easing 必须保留为 EasingOut 的别名（旧读点还有引用；另立曲线就是第三条缓动）",
        )
    }

    // ---------- 字体与贴纸 ----------

    @Test
    fun `newsreader static weights are bundled for android`() {
        val expected = listOf("regular", "medium", "book", "semibold", "bold")
        expected.forEach { weight ->
            val file = File("$FONT_PATH/newsreader_$weight.ttf")
            assertTrue(file.isFile && file.length() > 10_000, "缺静态字体 ${file.path}（Newsreader $weight）")
        }
        val notice = File("../docs/third-party-notices.md")
        assertTrue(notice.isFile, "异地方案要求许可台账存在：${notice.absolutePath}")
        val text = notice.readText()
        assertTrue(text.contains("Newsreader"), "许可台账未登记 Newsreader")
        assertTrue(
            text.contains("SIL Open Font License") || text.contains("OFL"),
            "许可台账未登记 SIL Open Font License 1.1",
        )
    }

    @Test
    fun `all thirty two prototype stickers land as local drawables`() {
        val names = listOf(
            "bell", "book", "bookmark", "books", "camera", "chart", "clock", "cloud",
            "document", "download", "edit", "gear", "image", "info", "list", "microphone",
            "newspaper", "pause", "person", "play", "qr", "search", "shield", "spark",
            "speaker", "star", "stop", "target", "translate", "trash", "upload", "warning",
        )
        assertEquals(32, names.toSet().size, "贴纸名单自身必须去重后仍是 32 枚")
        names.forEach { name ->
            val file = File("$DRAWABLE_PATH/sticker_$name.png")
            assertTrue(file.isFile && file.length() > 0, "缺贴纸 ${file.path}（从原型 assets/icons 拷入，禁止联网取图）")
            assertEquals(0x89, file.readBytes()[0].toInt() and 0xFF, "sticker_$name.png 不是 PNG")
        }
    }

    // ---------- 对比度（运行时复算，不写估数） ----------

    private fun ratioOf(fg: Long, bg: Long): Double = ratio(fg.toInt(), bg.toInt())

    private fun assertAtLeast(label: String, fg: Long, bg: Long, floor: Double) {
        val r = ratioOf(fg, bg)
        assertTrue(r >= floor, "$label 实测 %.2f:1 低于 %.1f:1".format(r, floor))
    }

    @Test
    fun `prototype text pairs meet wcag floors where the css does`() {
        // 浅套
        assertAtLeast("text1/bg", lightHex.getValue("text1"), lightHex.getValue("background"), 4.5)
        assertAtLeast("text1/surface", lightHex.getValue("text1"), lightHex.getValue("surface"), 4.5)
        assertAtLeast("text1/tint", lightHex.getValue("text1"), lightHex.getValue("tint"), 4.5)
        assertAtLeast("text2/surface", lightHex.getValue("text2"), lightHex.getValue("surface"), 4.5)
        assertAtLeast("text2/bg", lightHex.getValue("text2"), lightHex.getValue("background"), 4.5)
        assertAtLeast("text2/tint", lightHex.getValue("text2"), lightHex.getValue("tint"), 4.5)
        assertAtLeast("text2/tint2", lightHex.getValue("text2"), lightHex.getValue("tint2"), 4.5)
        assertAtLeast("brandDeep/brandTint", lightHex.getValue("brandDeep"), lightHex.getValue("brandTint"), 4.5)
        assertAtLeast("brandDeep/surface", lightHex.getValue("brandDeep"), lightHex.getValue("surface"), 4.5)
        assertAtLeast("error/surface", lightHex.getValue("error"), lightHex.getValue("surface"), 4.5)
        assertAtLeast("error/dangerTint", lightHex.getValue("error"), lightHex.getValue("dangerTint"), 4.5)
        assertAtLeast("success/surface", lightHex.getValue("success"), lightHex.getValue("surface"), 4.5)
        assertAtLeast("onStrong/strong", lightHex.getValue("onStrong"), lightHex.getValue("strongSurface"), 4.5)
        assertAtLeast("onStrong/strongHi", lightHex.getValue("onStrong"), lightHex.getValue("strongSurfaceHigh"), 4.5)
        assertAtLeast("onBrand/gradientEnd", lightHex.getValue("onBrand"), 0xFF35B9B5L, 4.5)
        // 深套
        assertAtLeast("dark text1/bg", darkHex.getValue("text1"), darkHex.getValue("background"), 4.5)
        assertAtLeast("dark text2/surface", darkHex.getValue("text2"), darkHex.getValue("surface"), 4.5)
        assertAtLeast("dark text2/tint2", darkHex.getValue("text2"), darkHex.getValue("tint2"), 4.5)
        assertAtLeast("dark brandDeep/brandTint", darkHex.getValue("brandDeep"), darkHex.getValue("brandTint"), 4.5)
        assertAtLeast("dark error/surface", darkHex.getValue("error"), darkHex.getValue("surface"), 4.5)
        assertAtLeast("dark onStrong/strong", darkHex.getValue("onStrong"), darkHex.getValue("strongSurface"), 4.5)
    }

    /**
     * 原型自带的两个淡色缺陷：**照原值落地**（原型是权威），但把实测钉在这里，
     * 任何人调深/调浅都会让本用例红，必须带着实测值重议。
     */
    @Test
    fun `prototype low contrast inks are pinned as measured instead of silently improved`() {
        val fuzzyOnSurface = ratioOf(lightHex.getValue("feedbackFuzzy"), lightHex.getValue("surface"))
        assertTrue(
            fuzzyOnSurface in 3.8..3.95,
            "原型 --fuzzy 压白实测应在 3.87:1 附近，当前 %.2f；调深即偏离原型，需先裁决".format(fuzzyOnSurface),
        )
        val ink3OnBg = ratioOf(lightHex.getValue("text3"), lightHex.getValue("background"))
        assertTrue(
            ink3OnBg in 4.35..4.45,
            "原型 --ink-3 压 --bg 实测应在 4.39:1 附近，当前 %.2f；只用于辅助小字，不得提为正文".format(ink3OnBg),
        )
    }
}

private const val THEME_PATH = "src/main/java/com/example/englishlearning/ui/theme"
private const val FONT_PATH = "src/main/res/font"
private const val DRAWABLE_PATH = "src/main/res/drawable-nodpi"
