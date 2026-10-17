package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.DomainLight
import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 4 契约：全站唯一的 `MaterialTheme(...)` 装配点。
 *
 * 口径沿用 Task 2/3（`DesignTokenContractTest` / `MintTokenIsComposableContractTest`）：
 *  - 「源码文本即契约」：装配点存在性、角色映射表、裸 hex 禁令都读 `src/main/java` 文本；
 *  - 注释不计入契约：每个入口先过 `SourceRatchet.stripComments`；
 *  - 目录守卫：`walkTopDown()` 对缺失路径只给空序列，静默空扫会让契约无声通过；
 *  - 每条正则都用合成正片段与合成负片段当场证明「抓得住且不越界」，永久红的模式不可能过关；
 *  - 唯一例外是配对断言：装配点引入的字形/容器对由 `AppColors.kt` 的运行时 `Color` 求值后按
 *    WCAG 相对亮度当场复算（全局约束 7「禁止估」）。`ColorScheme` / `Typography` / `Shapes`
 *    本身不在 JVM 侧实例化（其取值入口是 `@Composable`，仓库 JVM 单测没有 Compose 运行时口径），
 *    所以形状用源码正则钉、配对用 token 复算。
 *
 * 扫描范围是整个 `src/main/java`（不是 `ui/`）：装配点唯一性必须同时看见
 * `MainActivity` 与 `launch/BrandLaunchActivity` 这类 `ui/` 之外的内容根。
 */
class MaterialThemeAssemblyContractTest {

    // ---------- 钉值表（逐字来自 spec §2.2 / §2.3 与 Task 4 的角色映射裁决） ----------

    /** 六个语义档在 `AppType.kt` 里的完整声明行（本任务只准新增装配函数，六档逐字不动）。 */
    private val appTypeTierDeclarations: Map<String, String> = linkedMapOf(
        "Display" to "val Display = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, " +
            "letterSpacing = 0.4.sp, color = AppPalette.TextPrimary)",
        "Headline" to "val Headline = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, " +
            "letterSpacing = 0.2.sp, color = AppPalette.TextPrimary)",
        "Title" to "val Title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, " +
            "color = AppPalette.TextPrimary)",
        "Body" to "val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, " +
            "color = AppPalette.TextPrimary)",
        "Footnote" to "val Footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, " +
            "color = AppPalette.TextSecondary)",
        "Label" to "val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, " +
            "letterSpacing = 0.3.sp, color = AppPalette.TextSecondary)",
    )

    /**
     * 十五个 M3 排印角色 → 六档语义源：六档钉值优先，其余按最近字号就近派生，
     * 不引新字号、不混档改字重。`titleLarge`/`titleMedium`/`bodySmall`/`labelLarge`/`titleSmall`
     * 因此产生可见字号变化，属预期改动，核验走真机清单（批 1 收口）。
     */
    private val typographyRoleTiers: Map<String, String> = linkedMapOf(
        "displayLarge" to "Display",
        "displayMedium" to "Display",
        "displaySmall" to "Display",
        "headlineLarge" to "Headline",
        "headlineMedium" to "Headline",
        "headlineSmall" to "Headline",
        "titleLarge" to "Headline",
        "titleMedium" to "Title",
        "titleSmall" to "Body",
        "bodyLarge" to "Body",
        "bodyMedium" to "Body",
        "bodySmall" to "Footnote",
        "labelLarge" to "Label",
        "labelMedium" to "Label",
        "labelSmall" to "Label",
    )

    /**
     * 交给 M3 之前必须先给样式去色：`AppType` 六档自带 `AppPalette.TextPrimary` /
     * `TextSecondary`（浅套静态墨），原样装进 `Typography` 会让全部 `MaterialTheme.typography.*`
     * 读点带着硬编码近黑色，深色模式就成了黑压黑；去色后前景由 `LocalContentColor` 决定。
     */
    private fun neutralTierStyle(tier: String): String = "AppType.$tier.neutralForTheme()"

    /**
     * `appColorScheme(dark)` 两个分支共享的角色取值表（除 `onErrorContainer`）：全部取
     * `AppColorSet` 字段，无裸 hex。分支各自与这张表逐字比对，再加「两分支只差一项」的对位断言，
     * 浅/深两套装配不可能漂移成两份不同的真值。
     */
    private val colorSchemeSharedExpressions: Map<String, String> = linkedMapOf(
        "primary" to "set.onMint",
        "onPrimary" to "set.background",
        "primaryContainer" to "set.tint",
        "onPrimaryContainer" to "set.onMint",
        "secondary" to "set.onMint",
        "onSecondary" to "set.background",
        "secondaryContainer" to "set.tint3",
        "onSecondaryContainer" to "set.text1",
        "tertiary" to "set.text2",
        "onTertiary" to "set.surface",
        "tertiaryContainer" to "set.tint2",
        "onTertiaryContainer" to "set.text1",
        "error" to "set.error",
        "onError" to "set.surface",
        "errorContainer" to "set.errorFill",
        "background" to "set.background",
        "onBackground" to "set.text1",
        "surface" to "set.surface",
        "onSurface" to "set.text1",
        "surfaceVariant" to "set.tint2",
        "onSurfaceVariant" to "set.text1",
        "surfaceTint" to "set.onMint",
        "inverseSurface" to "set.text1",
        "inverseOnSurface" to "set.surface",
        "inversePrimary" to "set.tint",
        "outline" to "set.outline",
        "outlineVariant" to "set.outlineSoft",
        "surfaceContainerLowest" to "set.surface",
        "surfaceContainerLow" to "set.surface",
        "surfaceContainer" to "set.surface",
        "surfaceContainerHigh" to "set.tint2",
        "surfaceContainerHighest" to "set.tint2",
        "surfaceBright" to "set.surface",
        "surfaceDim" to "set.tint2",
    )

    /**
     * 唯一按模式分叉的取值：`errorFill` 浅套是深红、深套是浅红，字形极性相反，
     * 所以浅套取 `text1`、深套取 `background`（顺序即 light to dark）。
     */
    private val onErrorContainerInk: Pair<String, String> = "set.text1" to "set.background"

    /** 某一主题下「角色 → 取色表达式」的完整表（35 项，与 material3 1.4.0 的 builder 必传子集对齐）。 */
    private fun colorSchemeExpressions(dark: Boolean): Map<String, String> = colorSchemeSharedExpressions +
        ("onErrorContainer" to if (dark) onErrorContainerInk.second else onErrorContainerInk.first)

    /**
     * 装配点引入的字形对容器配对，全部按正文族下限 4.5:1 复算（左列字形压右列容器；
     * 后几组是同一批容器色在「字形直接压底」用法下的对偶，如 TextButton 取 `primary` 作字色）。
     * `outline` / `outlineVariant` 故意不在表内：它们是 spec §2.1 的装饰性细线（卡片描边、
     * 分隔线），不承担非文本信息；非文本 3:1 下限走域色 `deep` 容器那条路径（spec §2.1
     * 「域色容器上的字形对比」段，由组件任务落地）。M3 组件内部的轨道/拇指配对不在 JVM 侧
     * 可验证范围，留给真机核验。
     */
    private val textPairRoles: List<Pair<String, String>> = listOf(
        "onPrimary" to "primary",
        "onSecondary" to "secondary",
        "onTertiary" to "tertiary",
        "onPrimaryContainer" to "primaryContainer",
        "onSecondaryContainer" to "secondaryContainer",
        "onTertiaryContainer" to "tertiaryContainer",
        "onError" to "error",
        "onErrorContainer" to "errorContainer",
        "onBackground" to "background",
        "onSurface" to "surface",
        "onSurfaceVariant" to "surfaceVariant",
        "inverseOnSurface" to "inverseSurface",
        "primary" to "surface",
        "primary" to "background",
        "secondary" to "surface",
        "tertiary" to "surface",
        "error" to "surface",
        "error" to "background",
        "onSurface" to "surfaceVariant",
        "onSurface" to "surfaceContainer",
        "onSurface" to "surfaceContainerLowest",
        "onSurface" to "surfaceContainerLow",
        "onSurface" to "surfaceContainerHigh",
        "onSurface" to "surfaceContainerHighest",
        "onSurface" to "surfaceBright",
        "onSurface" to "surfaceDim",
    )

    // ---------- 读取与守卫 ----------

    /** 扫描整个 `src/main/java`：装配点守卫必须覆盖 `ui/` 之外的内容根与 Activity。 */
    private fun scanMainSources(dir: File = File(MAIN_SOURCE_PATH)): List<File> {
        assertTrue(
            dir.isDirectory,
            "装配点契约扫描目录缺失（期望 $MAIN_SOURCE_PATH）：${dir.path}，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}；" +
                "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——包名重构 / 工作目录变化必须先修此处路径。"
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "装配点契约扫描空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
        return files
    }

    /** 剥注释源码；「应恰好一个」的失败信息点名缺失，改名或删除不许伪装成「形状不符」。 */
    private fun sourceText(name: String): String {
        val files = scanMainSources().filter { it.name == name }
        assertTrue(
            files.size == 1,
            "$name 应恰好一个，实际 ${files.size} 个（装配点未创建 / 被改名 / 扫描错位？" +
                "期望目录 $MAIN_SOURCE_PATH，JVM 工作目录 ${File(".").absolutePath}）"
        )
        return stripComments(files.single().readText())
    }

    private fun mintGlassThemeText(): String = sourceText(MINT_GLASS_THEME_NAME)

    private fun appTypeText(): String = sourceText(APP_TYPE_NAME)

    private fun mainActivityText(): String {
        val file = File(MAIN_ACTIVITY_PATH)
        assertTrue(
            file.isFile,
            "MainActivity 源码缺失：${file.path}（相对 JVM 工作目录 ${File(".").absolutePath}）——" +
                "内容根守卫的扫描对象都没了，装配点接线无从校验"
        )
        return stripComments(file.readText())
    }

    // ---------- 解析工具 ----------

    /** 从 [anchor] 命中的 `(` 起做括号配平，返回括号内文本（不含外层括号）。 */
    private fun parenBlock(text: String, anchor: Regex): String {
        val m = anchor.find(text) ?: error("找不到锚点：$anchor（装配形状被改名或整体缺失）")
        val open = m.range.last
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(open + 1, i)
                }
                else -> Unit
            }
        }
        error("锚点 $anchor 的圆括号未配平")
    }

    /** `fun <name>(` 起的花括号配平函数体（含外层大括号；签名不在返回文本内）。 */
    private fun functionText(text: String, name: String): String {
        val head = Regex("""fun\s+$name\(""").find(text)
            ?: error("找不到函数声明 fun $name(...)：$name 未落地或被改名")
        val open = text.indexOf('{', head.range.last)
        assertTrue(open >= 0, "fun $name 没有函数体")
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(open, i + 1)
                }
                else -> Unit
            }
        }
        error("fun $name 函数体花括号未配平")
    }

    /** 逐行命名字段：`role = <expr>,` → `role` 到 `<expr>`（实现约定一条实参一行，值不跨行）。 */
    private fun namedArgs(block: String): Map<String, String> =
        Regex("""^\s*([a-zA-Z]\w*)\s*=\s*(.+?)\s*,?\s*$""", RegexOption.MULTILINE)
            .findAll(block)
            .associate { it.groupValues[1] to it.groupValues[2] }

    /** 取色表达式里出现的 `AppColorSet` 字段名。 */
    private fun fieldsOf(expr: String): List<String> =
        Regex("""set\.([a-zA-Z]\w*)""").findAll(expr).map { it.groupValues[1] }.toList()

    private fun fieldOf(set: AppColorSet, name: String): Color = when (name) {
        "background" -> set.background
        "surface" -> set.surface
        "surfaceGlass" -> set.surfaceGlass
        "tint" -> set.tint
        "tint2" -> set.tint2
        "tint3" -> set.tint3
        "outline" -> set.outline
        "outlineSoft" -> set.outlineSoft
        "text1" -> set.text1
        "text2" -> set.text2
        "text3" -> set.text3
        "onMint" -> set.onMint
        "onBrand" -> set.onBrand
        "error" -> set.error
        "errorFill" -> set.errorFill
        "success" -> set.success
        else -> error("AppColorSet 没有字段：$name")
    }

    // ---------- WCAG 计算（全局约束 7：按相对亮度当场复算，禁止估） ----------

    /** sRGB 分量线性化后加权，口径与 `DesignTokenContractTest.luminance` 一致（同一公式两处实现）。 */
    private fun luminance(color: Color): Double {
        fun lin(component: Float): Double {
            val s = component.toDouble()
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(color.red) + 0.7152 * lin(color.green) + 0.0722 * lin(color.blue)
    }

    private fun ratio(fg: Color, bg: Color): Double {
        val hi = maxOf(luminance(fg), luminance(bg))
        val lo = minOf(luminance(fg), luminance(bg))
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertAtLeast(label: String, fg: Color, bg: Color, floor: Double) {
        val r = ratio(fg, bg)
        assertTrue(r >= floor, "$label 实测 %.2f:1 低于 %.1f:1".format(r, floor))
    }

    // ---------- 用例：扫描范围与守卫 ----------

    @Test
    fun `main source scan resolves to the assembly inputs and a missing directory fails loudly`() {
        val names = scanMainSources().map { it.name }
        listOf(APP_TYPE_NAME, APP_COLORS_NAME, MINT_PALETTE_NAME, MAIN_ACTIVITY_NAME).forEach {
            assertTrue(it in names, "扫描到 ${names.size} 个 .kt 却缺 $it，扫描范围疑似错位：$MAIN_SOURCE_PATH")
        }
        // 装配点本体：RED 轮就是在这里点名「MintGlassTheme.kt 一份都没有」。
        assertEquals(
            1,
            names.count { it == MINT_GLASS_THEME_NAME },
            "唯一装配点 $MINT_GLASS_THEME_NAME 必须存在且只有一份，实际 ${names.count { it == MINT_GLASS_THEME_NAME }} 份",
        )

        val missing = File("$MAIN_SOURCE_PATH/__not_a_real_package__")
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）"
        )
        val error = assertFailsWith<AssertionError> { scanMainSources(missing) }
        assertTrue(
            error.message?.contains(MAIN_SOURCE_PATH) == true,
            "目录守卫失败信息必须点名期望路径，实际：${error.message}"
        )
    }

    // ---------- 用例：装配点唯一 + 内容根（brief Step 1 断言 1 / 2） ----------

    @Test
    fun `MaterialTheme call site is unique to the assembly file`() {
        val sites = scanMainSources().flatMap { file ->
            List(countMatches(file.readText(), materialThemeCall)) { file.name }
        }
        assertEquals(
            listOf(MINT_GLASS_THEME_NAME),
            sites,
            "MaterialTheme( 装配点应唯一，实际：$sites（多一处就有第二套主题，深浅两套互相覆盖）",
        )

        // 反空转：只数「调用」形状，`.typography` / `.colorScheme` 读点不得被算进来。
        val reads = """
            val line = MaterialTheme.typography.bodySmall
            val ink = MaterialTheme.colorScheme.error
            val size = MaterialTheme.typography.headlineSmall.fontSize
            MintGlassTheme(themeMode = ThemeMode.Dark) { }
        """.trimIndent()
        assertEquals(0, countMatches(reads, materialThemeCall), "读点被误判成调用：计数正则越界")

        val calls = """
            MaterialTheme(colorScheme = scheme) { content() }
            MaterialTheme(
                colorScheme = appColorScheme(dark),
                typography = appTypography(),
                shapes = AppShapes,
                content = content,
            )
        """.trimIndent()
        assertEquals(2, countMatches(calls, materialThemeCall), "同行与换行两种调用形状都必须被抓住")

        // 注释里的调用形状不计入契约（证明 stripComments 是本规则的入口而不是摆设）。
        assertEquals(
            0,
            countMatches("// MaterialTheme(colorScheme = scheme) 的旧写法\nval unrelated = 1", materialThemeCall),
            "注释里的 MaterialTheme( 被算成了装配点",
        )
    }

    @Test
    fun `MainActivity content root is the assembly wrapper and AppScreen stays inside it`() {
        val body = mainActivityText()
        assertTrue(
            setContentRoot.containsMatchIn(body),
            "MainActivity 内容根必须是 MintGlassTheme，否则 CompositionLocal 无提供者，Mint* 会取到默认浅套"
        )
        assertTrue(
            Regex("""MintGlassTheme\(\s*themeMode\s*=""").containsMatchIn(body),
            "MintGlassTheme 必须显式接 themeMode 实参（Task 5 把它换成 rememberAppThemeMode()）"
        )
        listOf(
            "import com.example.englishlearning.ui.theme.MintGlassTheme",
            "import com.example.englishlearning.ui.theme.ThemeMode",
        ).forEach { fqcn ->
            assertTrue(
                Regex("""^${Regex.escape(fqcn)}$""", RegexOption.MULTILINE).containsMatchIn(body),
                "MainActivity 缺 import $fqcn：内容根接线编译不过"
            )
        }

        val setContentIndex = Regex("""setContent\s*\{""").find(body)?.range?.first
            ?: error("MainActivity 没有 setContent 调用：内容根守卫失去对象")
        val wrapperIndex = Regex("""MintGlassTheme\(""").find(body)?.range?.first
            ?: error("MainActivity 的内容根不是 MintGlassTheme")
        val appScreenIndex = Regex("""AppScreen\(""").find(body)?.range?.first
            ?: error("MainActivity 不再装配 AppScreen：AppScreenProductionWiringTest 的实参装配失去对象")
        assertTrue(
            setContentIndex < wrapperIndex && wrapperIndex < appScreenIndex,
            "内容根顺序必须是 setContent { MintGlassTheme { AppScreen(...) } }，实际 " +
                "setContent=$setContentIndex wrapper=$wrapperIndex AppScreen=$appScreenIndex"
        )

        // 合成负例：装配点被埋进别的容器底下时，根守卫必须判红。
        val wrongRoot =
            "setContent {\n    AppGlassScaffold { MintGlassTheme(themeMode = ThemeMode.System) { AppScreen() } }\n}"
        assertTrue(
            !setContentRoot.containsMatchIn(wrongRoot),
            "合成负例被放行：MintGlassTheme 不是 setContent 后的第一个组合调用仍被判通过"
        )
    }

    // ---------- 用例：六档钉值（brief Step 1 断言 3，spec §2.2） ----------

    @Test
    fun `AppType six tiers match spec 2_2 verbatim`() {
        val text = appTypeText()
        appTypeTierDeclarations.forEach { (tier, declaration) ->
            assertTrue(
                text.contains(declaration),
                "AppType.$tier 偏离 spec §2.2 钉值（本任务只准新增装配函数，六档声明逐字不动）：期望 $declaration"
            )
        }
        assertEquals(
            6,
            Regex("""val (Display|Headline|Title|Body|Footnote|Label) = TextStyle\(""").findAll(text).count(),
            "AppType 必须恰好六个语义档：多出任何一档就是引入新字号（R3 禁止）",
        )
    }

    // ---------- 用例：Typography（去色 + 角色表） ----------

    @Test
    fun `appTypography assembles all fifteen M3 roles from the six tiers`() {
        val body = functionText(appTypeText(), "appTypography")
        val args = namedArgs(parenBlock(body, typographyCall))
        val expected = typographyRoleTiers.mapValues { neutralTierStyle(it.value) }
        assertEquals(expected, args, "appTypography 的角色映射偏离钉值表（缺角色 / 错档 / 未去色都会在这里爆）")
        assertEquals(15, args.size, "M3 Typography 的核心角色恰好十五个")
    }

    @Test
    fun `appTypography carries no palette colour because every role is neutralised`() {
        val assembly = appTypeText()
        val body = functionText(assembly, "appTypography")
        assertTrue(
            !Regex("""AppPalette\.""").containsMatchIn(body),
            "appTypography 直接引用 AppPalette：装配出的排印样式带浅套静态墨，深色模式变成黑压黑"
        )
        assertTrue(
            !Regex("""Color\(\s*0x""").containsMatchIn(body),
            "appTypography 里出现裸 Color(0x…)：装配点只能取 token（全局约束 6）"
        )
        assertTrue(
            neutralHelperShape.containsMatchIn(assembly),
            "缺 private fun TextStyle.neutralForTheme(): TextStyle = copy(color = Color.Unspecified)：" +
                "排印样式的静态浅墨会跟着进 Typography"
        )
        assertTrue(
            Regex("""^import\s+androidx\.compose\.ui\.graphics\.Color$""", RegexOption.MULTILINE)
                .containsMatchIn(assembly),
            "AppType.kt 缺 androidx.compose.ui.graphics.Color 导入：neutralForTheme 编译不过"
        )

        // 合成正/负片段：角色规则要抓得住去色形状，同时拒绝带色形状与错档形状。
        assertTrue(roleRule("titleLarge", "Headline").matches("titleLarge = AppType.Headline.neutralForTheme(),"))
        assertTrue(
            !roleRule("titleLarge", "Headline").matches("titleLarge = AppType.Headline,"),
            "合成负例被放行：未去色的 AppType 样式直接进 Typography 也算合格"
        )
        assertTrue(
            !roleRule("titleLarge", "Headline").matches("titleLarge = AppType.Title.neutralForTheme(),"),
            "合成负例被放行：错档（titleLarge 取了 Title）也算合格"
        )
        assertTrue(
            !roleRule("bodySmall", "Footnote")
                .matches("bodySmall = AppType.Footnote.copy(color = AppPalette.TextPrimary),"),
            "合成负例被放行：带 AppPalette 前景的角色也算合格"
        )
    }

    // ---------- 用例：ColorScheme（取色映射 + 配对复算） ----------

    @Test
    fun `appColorScheme takes every named role from the AppColorSet`() {
        val body = functionText(mintGlassThemeText(), "appColorScheme")
        val light = namedArgs(parenBlock(body, lightSchemeCall))
        val dark = namedArgs(parenBlock(body, darkSchemeCall))
        assertEquals(colorSchemeExpressions(false), light, "lightColorScheme 分支的角色取值偏离钉值表")
        assertEquals(colorSchemeExpressions(true), dark, "darkColorScheme 分支的角色取值偏离钉值表")
        // 分支对位：两分支只允许在 onErrorContainer 上分叉，其余取值必须逐字相同。
        assertEquals(
            light - "onErrorContainer",
            dark - "onErrorContainer",
            "深浅两分支除 onErrorContainer 外出现取值分叉：装配点长出了第二套真值",
        )
        listOf("light 分支" to light, "dark 分支" to dark).forEach { (branchName, args) ->
            args.forEach { (role, expr) ->
                assertEquals(
                    1,
                    fieldsOf(expr).size,
                    "$branchName 的 $role 取色表达式应恰好引用一个 AppColorSet 字段：$expr",
                )
            }
        }
        assertTrue(
            Regex("""val set = if \(dark\) AppColorDark else AppColorLight""").containsMatchIn(body),
            "appColorScheme 必须由 dark 在两套 AppColorSet 之间选择（深套不是第二套 hex）"
        )
        assertTrue(
            !Regex("""AppPalette\.""").containsMatchIn(body),
            "appColorScheme 引用了 AppPalette 静态浅套：深色下会把浅墨带回组件"
        )
        // 两个 builder 形状的正/负片段：`[\s{]lightColorScheme(` 不得被 `darkColorScheme(` 满足，
        // 反之亦然——否则「两分支各自比对」会退化成「同一分支比两次」。
        assertTrue(lightSchemeCall.containsMatchIn("    return if (dark) x else lightColorScheme(\n        primary = set.onMint,\n    )"))
        assertTrue(!lightSchemeCall.containsMatchIn("darkColorScheme(\n    primary = set.onMint,\n)"))
        assertTrue(darkSchemeCall.containsMatchIn("if (dark) {\n        darkColorScheme(\n            primary = set.onMint,\n        )"))
        assertTrue(!darkSchemeCall.containsMatchIn("lightColorScheme(\n    primary = set.onMint,\n)"))
        // 装配点只准这两个 builder，各一次：第三处调用能让子树里的角色表偏离钉值表而不被发现。
        assertEquals(1, countMatches(body, lightSchemeCall), "lightColorScheme 调用应恰好一处")
        assertEquals(1, countMatches(body, darkSchemeCall), "darkColorScheme 调用应恰好一处")
    }

    @Test
    fun `scheme ink pairs introduced by the assembly meet WCAG floors computed at runtime`() {
        listOf(false to AppColorLight, true to AppColorDark).forEach { (dark, set) ->
            val schemeName = if (dark) "深套" else "浅套"
            val expressions = colorSchemeExpressions(dark)
            fun colorOf(role: String): Color {
                val expr = expressions[role] ?: error("钉值表缺角色 $role")
                return fieldOf(set, fieldsOf(expr).single())
            }
            textPairRoles.forEach { (inkRole, containerRole) ->
                assertAtLeast(
                    "$schemeName $inkRole(${expressions.getValue(inkRole)}) 压 " +
                        "$containerRole(${expressions.getValue(containerRole)})",
                    colorOf(inkRole),
                    colorOf(containerRole),
                    4.5,
                )
            }
        }
        // 反空转：故意让 onErrorContainer 取 text1（深套的 text-1 就是 onSurface，浅墨压浅红填充），
        // 必须判它不合格——否则说明亮度公式或 4.5 阈值本身失效。
        val r = ratio(AppColorDark.text1, AppColorDark.errorFill)
        assertTrue(r < 4.5, "合成负例失效：深套浅墨压 error-fill 实测 %.2f:1 竟已过正文阈值（公式或阈值错了）".format(r))
    }

    // ---------- 用例：CompositionLocal 提供者（翻色链路 + 域色完整性） ----------

    @Test
    fun `MintGlassTheme is the sole provider of the three theme locals`() {
        val text = mintGlassThemeText()
        val body = functionText(text, "MintGlassTheme")
        listOf(
            Regex("""LocalAppColors provides set""") to "LocalAppColors provides set",
            Regex("""LocalDomainColors provides domains""") to "LocalDomainColors provides domains",
            Regex("""LocalIsDark provides dark""") to "LocalIsDark provides dark",
        ).forEach { (regex, label) ->
            assertTrue(regex.containsMatchIn(body), "CompositionLocalProvider 缺 $label：Mint* 会取到默认浅套")
        }
        assertEquals(
            1,
            countMatches(text, Regex("""CompositionLocalProvider\(""")),
            "提供者必须只有一处：第二处能在子树里换掉 token 集，装配点就失去唯一性",
        )
        assertTrue(
            Regex("""val domains = if \(dark\) DomainDark else DomainLight""").containsMatchIn(body),
            "域色必须整张 DomainLight/DomainDark 提供：给子集会让 appDomain() 与 MintPrimary 的 " +
                "getValue 在首次切换深色时于组合期抛异常（整机白屏）"
        )
        // 翻色链路的起点：dark 必须是组合期状态读取的结果，否则 readers 永不失效、深色静默不生效。
        assertTrue(
            Regex("""shouldUseDarkTheme\(themeMode, isSystemInDarkTheme\(\)\)""").containsMatchIn(body),
            "MintGlassTheme 必须由 shouldUseDarkTheme(themeMode, isSystemInDarkTheme()) 求 dark：" +
                "非组合期取值求出的主题不会让 compositionLocalOf 的读取失效"
        )
        val args = namedArgs(parenBlock(body, materialThemeCallInAssembly))
        assertEquals(
            mapOf(
                "colorScheme" to "appColorScheme(dark)",
                "typography" to "appTypography()",
                "shapes" to "AppShapes",
                "content" to "content",
            ),
            args,
            "MaterialTheme( 的实参必须是 appColorScheme(dark) / appTypography() / AppShapes / content",
        )
    }

    @Test
    fun `both domain maps cover every app domain entry`() {
        val all = AppDomain.entries.toSet()
        assertEquals(
            all,
            DomainLight.keys,
            "DomainLight 覆盖不全：装配点整张提供后，缺失域会让首次切深色的 getValue 抛异常",
        )
        assertEquals(all, DomainDark.keys, "DomainDark 覆盖不全：同 DomainLight 口径")
        // 反空转：少一个键的合成分组必须被同一条规则拒绝。
        val partial = DomainLight - AppDomain.Settings
        assertNotEquals(all, partial.keys, "合成负例被放行：缺键映射也能通过覆盖断言")
    }

    // ---------- 用例：AppShapes（槽位映射 + 卡片圆角校准） ----------

    @Test
    fun `AppShapes maps the five M3 shape slots to the corner tiers`() {
        val text = mintGlassThemeText()
        val expected = mapOf(
            "extraSmall" to "RoundedCornerShape(12.dp)",
            "small" to "AppShape.Button",
            "medium" to "MediumCorner",
            "large" to "AppShape.Dialog",
            "extraLarge" to "AppShape.Dialog",
        )
        assertEquals(expected, namedArgs(parenBlock(text, appShapesCall)), "AppShapes 槽位偏离 spec §2.3")
        assertTrue(
            Regex("""private val MediumCorner = RoundedCornerShape\(18\.dp\)""").containsMatchIn(text),
            "卡片圆角校准值必须以 private val MediumCorner = RoundedCornerShape(18.dp) 落地（spec §2.3 卡 18dp）"
        )
        assertTrue(
            !Regex("""AppShape\.Card""").containsMatchIn(text),
            "AppShapes 不得复用 AppShape.Card：那是 20dp 旧值，spec §2.3 要卡 18dp"
        )
        // 合成负例：medium 退回 20dp 旧值必须判红——证明上面的表真的在校准值上把关。
        val reverted = "val AppShapes: Shapes = Shapes(\n" +
            "    extraSmall = RoundedCornerShape(12.dp),\n" +
            "    small = AppShape.Button,\n" +
            "    medium = AppShape.Card,\n" +
            "    large = AppShape.Dialog,\n" +
            "    extraLarge = AppShape.Dialog,\n)"
        assertFailsWith<AssertionError> { assertEquals(expected, namedArgs(parenBlock(reverted, appShapesCall))) }
    }

    // ---------- 用例：装配文件零裸 hex（全局约束 6） ----------

    @Test
    fun `assembly file declares no bare colour literal`() {
        val text = mintGlassThemeText()
        assertEquals(
            0,
            countMatches(text, bareHex),
            "$MINT_GLASS_THEME_NAME 出现裸 Color(0x…)：颜色只能取 AppColorSet / 域色映射（全局约束 6）",
        )
        // 反空转：同一条规则必须抓得住一个裸 hex。
        assertEquals(1, countMatches("val scheme = Color(0xFF2EC99C)", bareHex), "裸 hex 守卫失效：Color(0x…) 没被数出来")
    }

    // ---------- 规则常量 ----------

    /** `MaterialTheme(` 的**调用**形状：紧跟标识符（同行或换行后），排除 `.typography` / `.colorScheme` 读点。 */
    private val materialThemeCall = Regex("""MaterialTheme\(\s*[a-zA-Z_]""")

    /** 装配点内部那一次调用：前面必须是空白或左大括号，排除 `fun MintGlassTheme(` 之类同后缀名字。 */
    private val materialThemeCallInAssembly = Regex("""[\s{]MaterialTheme\(""")

    private val typographyCall = Regex("""[\s{]Typography\(""")

    /** 两个带库内默认值的 builder：前置 `[\s{]` 让 `light` 与 `dark` 分支互不救活。 */
    private val lightSchemeCall = Regex("""[\s{]lightColorScheme\(""")

    private val darkSchemeCall = Regex("""[\s{]darkColorScheme\(""")

    private val appShapesCall = Regex("""Shapes\(""")

    private val setContentRoot = Regex("""setContent\s*\{\s*MintGlassTheme\(""")

    private val neutralHelperShape = Regex(
        """private fun TextStyle\.neutralForTheme\(\): TextStyle = copy\(color = Color\.Unspecified\)"""
    )

    private val bareHex = Regex("""Color\(\s*0x""")

    private fun roleRule(role: String, tier: String): Regex =
        Regex("""^$role = ${Regex.escape(neutralTierStyle(tier))},?$""")
}

private const val MAIN_SOURCE_PATH = "src/main/java"
private const val MAIN_ACTIVITY_PATH = "src/main/java/com/example/englishlearning/MainActivity.kt"
private const val MAIN_ACTIVITY_NAME = "MainActivity.kt"
private const val MINT_GLASS_THEME_NAME = "MintGlassTheme.kt"
private const val APP_TYPE_NAME = "AppType.kt"
private const val APP_COLORS_NAME = "AppColors.kt"
private const val MINT_PALETTE_NAME = "MintPalette.kt"
