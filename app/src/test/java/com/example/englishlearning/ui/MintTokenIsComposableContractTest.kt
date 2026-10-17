package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 计划 Task 3 契约（批 1 深色生效的主切换）：`MintPalette.kt` 的**七个中性令牌**必须以
 * `@Composable @ReadOnlyComposable get() = Local…` 形状组合期读取 Task 2 的 CompositionLocal，
 * **四个 `AppleMint*` 品牌令牌**必须保持 `= Color(0x…)` 静态。
 *
 * 为什么形状本身就是契约：全站中性令牌的读点全在组合期内（column-0 非 import 读点实测 0 处，
 * 见 task-3 报告），所以把令牌改成组合期 getter 不需要改任何调用文件；但反过来，只要有一个令牌
 * 退回静态 `= Color(…)`，它在深色模式下会静默保持浅值——编译照样通过、单测照样绿，深色能力凭空
 * 少一个 token。故此处逐成员钉「读哪个字段」，不只钉「读某个字段」——尤其
 * `MintOutline → outlineSoft`（命名陷阱：名字像 spec 的 `outline #ADE7D2`，值却是分隔线；
 * 映射到 `outline` 会让全站细分隔线变粗薄荷线）。
 *
 * 口径沿用 Task 2（形状同 `DesignTokenContractTest`）：
 *  - 「源码文本即契约」：本仓库 JVM 单测零 `androidx.compose` 导入，不渲染，只读 `src/main/java`；
 *  - 注释不计入契约：每个入口先过 `SourceRatchet.stripComments`（MintPalette 的 KDoc 会合法复述
 *    同一批形状与 hex，不剥注释的正则会被注释满足，契约就成空转）；唯一例外是最后一个用例，
 *    它的断言对象就是 KDoc 文本本身，已在用例内写明；
 *  - 目录守卫走共享夹具 `uiFiles()`（`walkTopDown()` 对缺失路径返回空序列 = 静默空扫）；
 *  - 正则防「永久红 / 永久绿」：合成正片段 + 合成负片段当场证明规则抓得住也不越界。
 *
 * 注解可放置目标已用字节码核过（compose runtime 1.10.0 的 `Composable` `@Target` =
 * FUNCTION, TYPE, TYPE_PARAMETER, PROPERTY_GETTER；`ReadOnlyComposable` = FUNCTION,
 * PROPERTY_GETTER；material3 1.4.0 的 `MaterialTheme.getColorScheme` 即同一形状的库内先例）：
 * 属性本体不是合法目标，所以「注解写在 `val` 之前」这种看着等价的形状必须判红——它编译不过。
 */
class MintTokenIsComposableContractTest {

    // ---------- 钉值表（逐字来自 Task 3 brief 的已现场核实映射表） ----------

    /** 六个读 `LocalAppColors` 的中性令牌 → `AppColorSet` 字段名。 */
    private val neutralAppColorFields: Map<String, String> = linkedMapOf(
        "MintBackground" to "background",
        "MintSurface" to "surface",
        "MintTint" to "tint",
        "MintPrimaryDark" to "text1",
        "MintOutline" to "outlineSoft",
        "MintTextMuted" to "text2",
    )

    /** 七个中性令牌全集：`MintPrimary` 是唯一例外（读域色调色板的 AiSpeech.base，不是中性集字段）。 */
    private val neutralTokens: List<String> get() = neutralAppColorFields.keys.toList() + MINT_PRIMARY

    /** 四个品牌渐变令牌：保持静态 + 既有钉值（spec §2.1「品牌渐变保持现状不动」）。 */
    private val brandStaticHexes: Map<String, String> = linkedMapOf(
        "AppleMintStart" to "0xFFA8F3C8",
        "AppleMintMiddle" to "0xFF5DDFB4",
        "AppleMintEnd" to "0xFF35B9B5",
        "AppleMintLight" to "0xFFD9FFF0",
    )

    // ---------- 形状规则 ----------

    private fun localRead(field: String): String = """LocalAppColors\.current\.$field(?!\w)"""

    private val domainAispeechBaseRead =
        """LocalDomainColors\.current\.getValue\(AppDomain\.AiSpeech\)\.base(?!\w)"""

    /**
     * 组合期 getter 形状：`internal val <token>: Color` + `@Composable` + `@ReadOnlyComposable`
     * + `get() = <读取表达式>`，对 [declarationBlock] 整块 `matches`（不是 containsMatchIn），因此
     * 退回 `= Color(…)` 静态、缺任一注解、读错字段、`get()` 后面还接了别的（`.copy(...)`）都判红。
     * 两注解之间用 `[\s\S]*?` 容忍换行与缩进；作用域已被单声明分块限定，不会把下一个令牌的注解
     * 借来凑形状。
     */
    private fun getterShape(token: String, readExpr: String): Regex = Regex(
        """internal\s+val\s+$token\s*:\s*Color\s+@Composable(?!\w)[\s\S]*?""" +
            """@ReadOnlyComposable(?!\w)[\s\S]*?get\(\)\s*=\s*(?:$readExpr)\s*$"""
    )

    /** 静态形状：`internal val <token>: Color = Color(0xRRGGBBAA)`，整块匹配。 */
    private fun staticShape(token: String, hex: String): Regex = Regex(
        """internal\s+val\s+$token\s*:\s*Color\s*=\s*Color\(\s*$hex\s*\)\s*$"""
    )

    // ---------- 读取与分块 ----------

    /** 共享夹具 `uiFiles()`（含目录守卫 + 空扫守卫）里按文件名定位 MintPalette.kt，并剥注释。 */
    private fun mintPaletteText(): String {
        val files = uiFiles().filter { it.name == "MintPalette.kt" }
        assertTrue(
            files.size == 1,
            "MintPalette.kt 应恰好一个，实际 ${files.size} 个（扫描错位？期望 " +
                "$UI_MAIN_SOURCE_PATH/theme，JVM 工作目录 ${File(".").absolutePath}）"
        )
        return stripComments(files.single().readText())
    }

    /**
     * 单声明块：从 `internal val $token` 起到下一个顶层 `internal val`（或文本尾），trim 后整块匹配。
     * 缺失声明直接点名失败——「令牌被改名/删掉」不能伪装成「形状不符」。
     */
    private fun declarationBlock(text: String, token: String): String {
        val head = Regex("""internal\s+val\s+$token\b""").find(text)
            ?: error("MintPalette.kt 剥注释后找不到声明 internal val $token（令牌被改名或删除？）")
        val rest = text.substring(head.range.first)
        val next = Regex("""(?m)^internal\s+val\s""").find(rest, 1)
        return rest.substring(0, next?.range?.first ?: rest.length).trim()
    }

    // ---------- 用例：扫描范围与守卫 ----------

    @Test
    fun `mint palette source resolves and missing directory fails loudly`() {
        val text = mintPaletteText()
        (neutralTokens + brandStaticHexes.keys).forEach { token ->
            declarationBlock(text, token) // 缺失即 error() 点名，不允许无声跳过
        }

        // 危险本体：无守卫时 walkTopDown() 只给空序列，按名查找会静默拿到「没有文件」。
        val missing = File("$UI_MAIN_SOURCE_PATH/__not_a_real_package__")
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）"
        )
        val error = assertFailsWith<AssertionError> { uiFiles(missing) }
        assertTrue(
            error.message?.contains(UI_MAIN_SOURCE_PATH) == true,
            "uiFiles 目录守卫的失败信息必须点名期望路径，实际：${error.message}"
        )
    }

    // ---------- 用例：七个中性令牌 = 组合期 getter ----------

    @Test
    fun `six neutral mint tokens read their pinned LocalAppColors field`() {
        val text = mintPaletteText()
        neutralAppColorFields.forEach { (token, field) ->
            val block = declarationBlock(text, token)
            assertTrue(
                getterShape(token, localRead(field)).matches(block),
                "$token 必须是 @Composable @ReadOnlyComposable get() = LocalAppColors.current.$field" +
                    "（brief 映射表逐成员钉字段，$token → $field）；实际声明块：\n$block"
            )
        }
    }

    @Test
    fun `mint outline reads outlineSoft not outline because its value is the separator`() {
        val text = mintPaletteText()
        val block = declarationBlock(text, "MintOutline")
        // 正向：分隔线族 outlineSoft（= 改值前 AppPalette.Separator 的同族值）。
        assertTrue(
            getterShape("MintOutline", localRead("outlineSoft")).matches(block),
            "MintOutline 命名陷阱：它承载的是分隔线，必须读 outlineSoft；实际声明块：\n$block"
        )
        // 反向独立钉一遍：名字相近的 outline 是粗描边族（spec #ADE7D2），映射过去会让全站细线变粗。
        assertTrue(
            !getterShape("MintOutline", localRead("outline")).matches(block),
            "MintOutline 不得读 LocalAppColors.current.outline（那是新组件描边用的 #ADE7D2 族）"
        )
        // 前缀守卫本体当场证明：`outline` 模式不得顺带命中 `outlineSoft`。
        assertTrue(
            Regex("""LocalAppColors\.current\.outline(?!\w)""")
                .find("get() = LocalAppColors.current.outlineSoft") == null,
            "前缀守卫失效：outline 模式命中了 outlineSoft"
        )
    }

    @Test
    fun `mint primary is the one neutral token that reads the domain palette`() {
        val text = mintPaletteText()
        val block = declarationBlock(text, MINT_PRIMARY)
        assertTrue(
            getterShape(MINT_PRIMARY, domainAispeechBaseRead).matches(block),
            "$MINT_PRIMARY 必须读 LocalDomainColors.current.getValue(AppDomain.AiSpeech).base；实际声明块：\n$block"
        )
        // 中性族取值救不了它：MintPrimary 是 AiSpeech 域色，不是任何 AppColorSet 字段。
        neutralAppColorFields.values.forEach { field ->
            assertTrue(
                !getterShape(MINT_PRIMARY, localRead(field)).matches(block),
                "$MINT_PRIMARY 不得读 LocalAppColors.current.$field（域色与中性集不是同一张表）"
            )
        }
    }

    @Test
    fun `composable getter and static token counts are exactly seven and four`() {
        val text = mintPaletteText()
        val getters = Regex(
            """internal\s+val\s+Mint\w+\s*:\s*Color\s+@Composable(?!\w)[\s\S]*?""" +
                """@ReadOnlyComposable(?!\w)[\s\S]*?get\(\)\s*="""
        ).findAll(text).count()
        assertEquals(7, getters, "七个中性 Mint* 令牌应是组合期 getter，多/少都说明令牌表被动过")

        // 中性令牌里不得残留裸 hex（全局约束 6：ui/ 禁止新增裸 Color(0x…) 字面量）。
        neutralTokens.forEach { token ->
            val block = declarationBlock(text, token)
            assertTrue(
                !Regex("""Color\(\s*0x""").containsMatchIn(block),
                "$token 改读 CompositionLocal 后不应再带裸 hex 字面量：\n$block"
            )
        }

        // getter 需要这两个 import，缺了就是编译期问题，这里先于构建爆。
        listOf("androidx.compose.runtime.Composable", "androidx.compose.runtime.ReadOnlyComposable")
            .forEach { fqcn ->
                assertTrue(
                    Regex("""^import\s+$fqcn$""", RegexOption.MULTILINE).containsMatchIn(text),
                    "MintPalette.kt 缺 import $fqcn：组合期 getter 编译不过"
                )
            }

        val brandStatics = brandStaticHexes.keys.count {
            staticShape(it, brandStaticHexes.getValue(it)).matches(declarationBlock(text, it))
        }
        assertEquals(4, brandStatics, "四个 AppleMint* 品牌令牌必须保持静态且取值不动")
    }

    // ---------- 用例：四个品牌令牌保持静态 ----------

    @Test
    fun `four brand gradient tokens stay static with pinned hexes`() {
        val text = mintPaletteText()
        brandStaticHexes.forEach { (token, hex) ->
            val block = declarationBlock(text, token)
            assertTrue(
                staticShape(token, hex).matches(block),
                "$token 必须保持静态 Color($hex)（三处顶层 Brush.linearGradient 需组合期外读取 + " +
                    "品牌渐变模式不变）；实际声明块：\n$block"
            )
            assertTrue(
                !Regex("""@Composable""").containsMatchIn(block),
                "$token 一旦被改成组合期 getter，三处顶层 Brush.linearGradient 会当场编译不过：\n$block"
            )
        }
    }

    // ---------- 用例：正则不空转（合成正/负片段） ----------

    @Test
    fun `shape rule works on synthetic positives and refuses plausible wrong declarations`() {
        val positives: List<Pair<String, Pair<String, String>?>> = listOf(
            // 实现采用的规范形状（注解各占一行）
            "internal val MintTint: Color\n    @Composable\n    @ReadOnlyComposable\n    get() = LocalAppColors.current.tint" to
                ("MintTint" to localRead("tint")),
            // 注解同行 + get() 前后多余空白：\s+ 必须容下，否则真实文件换个缩进就永久红
            "internal val MintSurface : Color\n\n  @Composable @ReadOnlyComposable\n\n  get()\n      =   LocalAppColors.current.surface" to
                ("MintSurface" to localRead("surface")),
            // 域色读取表达式含括号与点，必须原样匹配
            "internal val MintPrimary: Color\n    @Composable\n    @ReadOnlyComposable\n    get() = LocalDomainColors.current.getValue(AppDomain.AiSpeech).base" to
                (MINT_PRIMARY to domainAispeechBaseRead),
            // 静态形状的正片段：品牌令牌的真实形状（用 null 标记走 staticShape 分支）
            "internal val AppleMintEnd: Color = Color(0xFF35B9B5)" to null,
        )
        positives.forEach { (snippet, expect) ->
            val text = stripComments(snippet)
            if (expect == null) {
                assertTrue(
                    staticShape("AppleMintEnd", "0xFF35B9B5").matches(declarationBlock(text, "AppleMintEnd")),
                    "合成正片段漏判（静态形状）：$snippet"
                )
            } else {
                val (token, readExpr) = expect
                assertTrue(
                    getterShape(token, readExpr).matches(declarationBlock(text, token)),
                    "合成正片段漏判：$snippet"
                )
            }
        }

        val negatives: List<Pair<String, Pair<String, String>?>> = listOf(
            // 退回静态：深色失效的静默回归，必须红
            "internal val MintTint: Color = Color(0xFFDDF7E8)" to ("MintTint" to localRead("tint")),
            // 缺 @ReadOnlyComposable
            "internal val MintTextMuted: Color\n    @Composable\n    get() = LocalAppColors.current.text2" to
                ("MintTextMuted" to localRead("text2")),
            // 缺 @Composable：普通 getter 读 current，形状不符 brief
            "internal val MintBackground: Color\n    @ReadOnlyComposable\n    get() = LocalAppColors.current.background" to
                ("MintBackground" to localRead("background")),
            // 注解放错位置（属性不是 @Composable 的合法目标）：看着等价，实为编译错误
            "@Composable\n@ReadOnlyComposable\ninternal val MintSurface: Color\n    get() = LocalAppColors.current.surface" to
                ("MintSurface" to localRead("surface")),
            // 读错字段的「像样」形状：MintSurface 取了 surfaceGlass（前缀守卫必须拦下）
            "internal val MintSurface: Color\n    @Composable\n    @ReadOnlyComposable\n    get() = LocalAppColors.current.surfaceGlass" to
                ("MintSurface" to localRead("surface")),
            // 读错字段的「像样」形状：MintTint 取了 tint2
            "internal val MintTint: Color\n    @Composable\n    @ReadOnlyComposable\n    get() = LocalAppColors.current.tint2" to
                ("MintTint" to localRead("tint")),
            // 命名陷阱本体：MintOutline 读 outline（本用例族存在的首要理由）
            "internal val MintOutline: Color\n    @Composable\n    @ReadOnlyComposable\n    get() = LocalAppColors.current.outline" to
                ("MintOutline" to localRead("outlineSoft")),
            // text1 / text2 互换：MintPrimaryDark 应读 text1
            "internal val MintPrimaryDark: Color\n    @Composable\n    @ReadOnlyComposable\n    get() = LocalAppColors.current.text2" to
                ("MintPrimaryDark" to localRead("text1")),
            // 域写成 Learn：MintPrimary 只认 AiSpeech
            "internal val MintPrimary: Color\n    @Composable\n    @ReadOnlyComposable\n    get() = LocalDomainColors.current.getValue(AppDomain.Learn).base" to
                (MINT_PRIMARY to domainAispeechBaseRead),
            // getter 后面再接东西（.copy）：不是「直接读该字段」
            "internal val MintPrimary: Color\n    @Composable\n    @ReadOnlyComposable\n    get() = LocalDomainColors.current.getValue(AppDomain.AiSpeech).base.copy(alpha = 0.5f)" to
                (MINT_PRIMARY to domainAispeechBaseRead),
            // 品牌令牌被改成组合期 getter：静态形状必须红（三处顶层 Brush 会炸）
            "internal val AppleMintStart: Color\n    @Composable\n    @ReadOnlyComposable\n    get() = LocalAppColors.current.tint" to null,
        )
        negatives.forEach { (snippet, expect) ->
            val text = stripComments(snippet)
            if (expect == null) {
                val block = declarationBlock(text, "AppleMintStart")
                assertTrue(
                    !staticShape("AppleMintStart", "0xFFA8F3C8").matches(block),
                    "合成负例被放行（静态形状规则越界）：$snippet"
                )
            } else {
                val (token, readExpr) = expect
                val block = declarationBlock(text, token)
                assertTrue(
                    !getterShape(token, readExpr).matches(block),
                    "合成负例被放行（正则越界或锚点失效）：$snippet"
                )
            }
        }

        // 合规形状只写在注释里时必须「找不到声明」：证明 stripComments 是本规则的入口而不是摆设。
        assertFailsWith<IllegalStateException> {
            declarationBlock(
                stripComments("// internal val MintTint: Color\n//     get() = LocalAppColors.current.tint\nval unrelated = 1"),
                "MintTint"
            )
        }
    }

    // ---------- 用例：Step 3 要求的 KDoc 说明本体 ----------

    /**
     * 唯一直接断言注释文本的用例（Step 3 的交付物就是注释）：用未剥注释的原文，
     * 其余用例一律先过 stripComments。钉住必须留下的事实：中性令牌组合期读取后调用文件为零、
     * AppleMint* 保持静态的三处顶层 Brush 站点、MintOutline → outlineSoft 命名陷阱与
     * `outline` 不建 Mint 别名的处置。
     */
    @Test
    fun `kdoc records the zero call site rewrite and why brand tokens stay static`() {
        val raw = uiFiles().single { it.name == "MintPalette.kt" }.readText()
        listOf(
            "Brush.linearGradient",
            "ui/AiFeatureScreen.kt:47",
            "ui/AiLearningScreen.kt:56",
            "ui/AppScreen.kt:100",
            "outlineSoft",
            "appColors().outline",
        ).forEach { fact ->
            assertTrue(raw.contains(fact), "MintPalette.kt 的 KDoc 必须写明这条事实（Step 3）：$fact")
        }
    }
}

/** 顶层 private const：`const val` 在 Kotlin 里只能放顶层具名/伴生/对象，不能放实例成员。 */
private const val MINT_PRIMARY = "MintPrimary"
