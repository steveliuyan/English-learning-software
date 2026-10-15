package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * F5-03 源码契约（决策 §3 禁令2）：禁「浅薄荷 MintPrimary 容器 + 白字」主 CTA 组合
 * （白字压浅薄荷实测 ≈2.1:1，远低于 AA 4.5:1，TalkBack 用户同样受害于低对比）。
 * 同一次 buttonColors 调用内 containerColor = MintPrimary 与 contentColor = Color.White
 * 共存即违规；修复色统一 AppPalette.OnMintPrimary（≈4.6:1，与 PillButton 品牌渐变深字同源）。
 *
 * 口径边界（有意为之，勿“顺手加严”）：
 *  - 只锁决策点名的这一形状（MintPrimary 容器 + Color.White 文字）；
 *    MintSurface+MintPrimaryDark、DomainColors.*.deep+白字、渐变上 Color.Transparent 容器
 *    与只写 containerColor 的调用都是现存的合法组合，必须不误伤。
 *  - 沿用禁令1 判定面：认 buttonColors 调用内的字面量形状；变量容器（如 WordCardScreen 的
 *    container/content 局部变量）不在契约匹配面内，由修复任务逐点保证。
 *
 * 结构沿用 AccessibilityLabelContractTest 样板（scanUiSources 守卫 + 规则纯函数 +
 * 合成用例证明规则形状 + 真实文件用例证明当前零违规）；本类是 Task 3
 * 「禁裸 Color.Red」契约的骨架参照。
 */
class AccessibilityContrastContractTest {
    private val uiDir = File(UI_CONTRAST_SOURCE_PATH)

    /** 剥注释样板（沿用 RestoreNoBypassContractTest）：注释里的示例代码不计入契约。 */
    private fun stripComments(text: String): String = text
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//.*"), "")

    /**
     * 禁令2 的匹配面：同一次 buttonColors 调用（参数内不含 `)`）里
     * containerColor = MintPrimary 与 contentColor = Color.White 共存。
     * Kotlin Regex 的 `[^)]` 默认跨行，多行参数写法同样被抓住。
     */
    private val lowContrastCombo =
        Regex("""buttonColors\([^)]*containerColor = MintPrimary[^)]*contentColor = Color\.White[^)]*\)""")

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
            // AppScreen:1645/1853 —— 渐变底 + 透明容器承载白字
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
