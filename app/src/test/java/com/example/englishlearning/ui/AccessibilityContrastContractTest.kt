package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * F5-03 源码契约（决策 §3 禁令2）：禁「浅薄荷 MintPrimary 容器 + 白字」主 CTA 组合
 * （白字压浅薄荷实测 ≈2.1:1，远低于 AA 4.5:1，TalkBack 用户同样受害于低对比）。
 * 同一次 buttonColors 调用内 containerColor = MintPrimary 与 contentColor = Color.White
 * 共存即违规（决策原文是「同调用内共存」，与两参数的书写顺序无关）；
 * 修复色统一 AppPalette.OnMintPrimary（≈4.6:1，与 PillButton 品牌渐变深字同源）。
 *
 * 口径边界（有意为之，勿“顺手加严”）：
 *  - 只锁决策点名的这一形状（MintPrimary 容器 + Color.White 文字）；MintSurface+MintPrimaryDark、
 *    DomainColors.*.deep+白字、MintPrimaryDark+白字（≈15:1）与只写 containerColor 的调用都是
 *    现存的正确配比，必须不误伤——容器 token 名因此要求**完整匹配**（后面不得紧跟字母），
 *    否则 `containerColor = MintPrimary` 会前缀匹配到 MintPrimaryDark 造成误判。
 *  - Color.Transparent 容器 + 白字「不被判违规」只是匹配面结论，**不是对渐变的安全背书**：
 *    透明容器的实际底色来自 Modifier.background(brush/…) 或父级容器，源码正则无从计算对比度，
 *    因此「浅渐变 + 白字」（最坏 ≈1.29:1）在这里抓不住。仍走这条路子的站点
 *    （GlassButton 品牌渐变白字、AiLearningScreen 头卡白字标题/副标题）归 Task 12 的 connected 清单，
 *    用设备侧截图/取色取证；AiFeatureScreen 头部徽标已按裁决 #3 改取 on-brand 前景墨，
 *    亮度腿由 `AiBadgeContrastFixTest` 单独登记，不再属于本清单。
 *    本契约只保证「不因看不准而误伤」，不背书任何渐变组合。
 *  - 沿用禁令1 判定面：认 buttonColors 调用内的字面量形状。
 *  - 剥注释口径：批 1 终审 F6 起本类直接调共享夹具 `SourceRatchet.kt` 里那份**字符串感知**的
 *    `stripComments`，私有正则实现已删除（正则版不认字符串字面量，会吞掉 MIME 字面量之后的真代码）。
 *
 * 已知漏判面（无法用源码正则可靠闭合，故记录口径而非做脆弱重写）：
 *  - 变量容器 / 变量文字色（如 WordCardScreen FeedbackButton 的 container、content 局部变量）
 *    不在判定面内，由修复任务逐点保证；
 *  - `[^)]` 会在任意**嵌套右括号**处停住，所以
 *    `containerColor = MintPrimary.copy(alpha = .5f), contentColor = Color.White`
 *    或参数里内联 `if (implemented) MintPrimary else MintTint` 这类写法会静默漏判。
 *    新增此类站点时优先改成本契约可判的字面量形状，否则依赖设备侧证据兜底。
 *
 * 结构沿用 AccessibilityLabelContractTest 样板（scanUiSources 守卫 + 规则纯函数 +
 * 合成用例证明规则形状 + 真实文件用例证明当前零违规）；本类是 Task 3
 * 「禁裸 Color.Red」契约的骨架参照。
 */
class AccessibilityContrastContractTest {
    private val uiDir = File(UI_CONTRAST_SOURCE_PATH)

    /**
     * 禁令2 的匹配面：同一次 buttonColors 调用（参数内不含 `)`）里
     * containerColor = MintPrimary 与 contentColor = Color.White 共存——两个方向各一条交替式，
     * 反参数序写法同样判违规，不接受「换个书写顺序就绕过契约」。
     * `(?![A-Za-z])` 让 MintPrimary 必须是完整 token 名，不前缀匹配 MintPrimaryDark（深底白字 ≈15:1 合法）。
     * Kotlin Regex 的 `[^)]` 默认跨行，多行参数写法同样被抓住。
     */
    private val lowContrastCombo = Regex(
        """buttonColors\([^)]*containerColor = MintPrimary(?![A-Za-z])[^)]*contentColor = Color\.White[^)]*\)""" +
            """|buttonColors\([^)]*contentColor = Color\.White[^)]*containerColor = MintPrimary(?![A-Za-z])[^)]*\)"""
    )

    /** 禁令2 纯函数：源码文本 → 低对比 buttonColors 调用 offender 列表（折叠空白便于阅读）。 */
    private fun lowContrastOffenders(source: String): List<String> =
        lowContrastCombo.findAll(stripComments(source))
            .map { m -> m.value.replace(Regex("\\s+"), " ") }
            .toList()

    /** 扫描范围守卫：目录与 .kt 文件必须真实存在，否则契约测试静默空扫。 */
    private fun scanUiSources(dir: File = uiDir): List<File> {
        assertTrue(
            dir.isDirectory,
            "低对比契约扫描目录缺失（期望 $UI_CONTRAST_SOURCE_PATH）：${dir.path}，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}；" +
                "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——包名重构或工作目录变化必须先修此处路径。"
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "低对比契约扫描空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
        return files
    }

    private fun offenderLines(): List<String> =
        scanUiSources().flatMap { file ->
            lowContrastOffenders(file.readText()).map { offender -> "${file.name}: $offender" }
        }

    @Test
    fun `ui scan scope resolves to real kotlin sources`() {
        val files = scanUiSources()
        val screens = files.filter { it.name.endsWith("Screen.kt") }
        assertTrue(
            screens.isNotEmpty(),
            "扫描到 ${files.size} 个 .kt 文件却不含任何 *Screen.kt，扫描范围疑似错位：$UI_CONTRAST_SOURCE_PATH"
        )
    }

    @Test
    fun `missing scan directory fails loudly instead of passing on an empty scan`() {
        val missing = File("$UI_CONTRAST_SOURCE_PATH/__not_a_real_package__")
        // 危险本体：无守卫时 walkTopDown() 只给空序列，真实文件契约用例会当成零违规通过。
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）"
        )
        // 守卫本体：同一缺失路径必须让扫描立即失败而不是返回空列表。
        val error = assertFailsWith<AssertionError> { scanUiSources(missing) }
        assertTrue(
            error.message?.contains(UI_CONTRAST_SOURCE_PATH) == true,
            "守卫失败信息必须点名期望路径，实际：${error.message}"
        )
    }

    @Test
    fun `no mint-primary container with white content in same buttonColors call`() {
        val offenders = offenderLines()
        assertTrue(
            offenders.isEmpty(),
            "MintPrimary+白字低对比主 CTA（应改 AppPalette.OnMintPrimary）：\n${offenders.joinToString("\n")}"
        )
    }

    @Test
    fun `low contrast rule synthetic snippets flag the named combination in one and multiline form`() {
        val cases = listOf(
            // 单行形状（修复前 AiFeatureScreen/AppScreen/ArticleImportScreen/WordAiQaScreen:110 等）
            "colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),",
            // 多行形状（修复前 ReadingAccessScreen:403-405、WordAiQaScreen:163-168 等）
            "colors = ButtonDefaults.buttonColors(\n    containerColor = MintPrimary,\n    contentColor = Color.White,\n),",
            // 多行 + 附带 disabled 参数（修复前 ImageStudioScreen:114-119 等）
            "colors = ButtonDefaults.buttonColors(\n    containerColor = MintPrimary,\n    contentColor = Color.White,\n    disabledContainerColor = MintTint,\n),",
            // 反参数序（决策 §3 禁令2 的口径是「同调用内共存」，与书写顺序无关）：
            // contentColor 在前的写法必须同样判违规，否则改个顺序就绕过契约。
            "colors = ButtonDefaults.buttonColors(contentColor = Color.White, containerColor = MintPrimary),",
            // 反参数序 + 多行形状
            "colors = ButtonDefaults.buttonColors(\n    contentColor = Color.White,\n    containerColor = MintPrimary,\n),",
        )
        cases.forEach { snippet ->
            val offenders = lowContrastOffenders(snippet)
            assertTrue(offenders.isNotEmpty(), "禁令2 漏判低对比组合：$snippet")
            assertTrue(
                offenders.single().contains("containerColor = MintPrimary") &&
                    offenders.single().contains("contentColor = Color.White"),
                "禁令2 offender 应同时点名两个参数，实际：$offenders"
            )
        }
    }

    @Test
    fun `low contrast rule synthetic snippets keep legitimate color combinations clean`() {
        val snippets = listOf(
            // 修复后的目标形状：深字 token 压浅薄荷底
            "colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = AppPalette.OnMintPrimary),",
            // GlobalVocabularySearchScreen:217 —— 只给容器色，不写 contentColor
            "colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),",
            // TodayPlanScreen:156 等 —— 浅底深字族
            "colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),",
            // 深字容器族：MintPrimaryDark（0xFF1C1C1E）+ 白字 ≈15:1 是正确配比，
            // 规则必须把 `MintPrimary` 当作完整 token 名，不得前缀匹配到 MintPrimaryDark。
            "colors = ButtonDefaults.buttonColors(containerColor = MintPrimaryDark, contentColor = Color.White),",
            "colors = ButtonDefaults.buttonColors(contentColor = Color.White, containerColor = MintPrimaryDark),",
            // 透明容器承载白字（GlassButton 品牌渐变等自绘底实况）：
            // 底色来自 Modifier.background(brush/…)，源码正则无从计算对比度——
            // 「不误伤」只说明匹配面判不了，**不代表渐变 + 白字是安全配比**；
            // 这类站点的对比度归 Task 12 connected 清单做设备侧取证。
            "colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),",
            // CheckInScreen:77 / TodayPlanScreen:90 —— 域色 deep 底白字（≥4.1:1）
            "colors = ButtonDefaults.buttonColors(containerColor = DomainColors.Review.deep, contentColor = Color.White),",
            // 两次独立调用各自只命中一半：`[^)]*` 不得越过右括号把两条调用拼成违规
            "a = ButtonDefaults.buttonColors(containerColor = MintPrimary), b = ButtonDefaults.buttonColors(contentColor = Color.White),",
            // 注释里的示例代码不计入契约
            "// colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),\nval x = 1",
            "/* colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White) */",
        )
        snippets.forEach { snippet ->
            assertTrue(lowContrastOffenders(snippet).isEmpty(), "禁令2 误判合法组合：$snippet")
        }
    }
}

private const val UI_CONTRAST_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui"
