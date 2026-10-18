package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * F5-03 源码契约（决策 §3 禁令3）：main 源码禁裸 `Color.Red`——Compose 命名红（0xFFFF0000）
 * 压在白底/浅薄荷底上约 3.7:1，达不到 AA 正文 4.5:1，且它是命名常量而非 token，主题化（深色轮）
 * 时无处可改。错误状态文字统一 `AppPalette.ErrorText`（0xFFB30000，白底 7.2:1，取
 * DomainColors.Danger.deep 同值），成功/提示状态文字统一 `AppPalette.SuccessText`（0xFF1B5E20）。
 * 扫描范围按决策原文取「main 源码」全量（比禁令1/禁令2 的 ui 包更宽），故 offender 判定
 * 覆盖 ui 之外的包；`src/main/java` 就是本模块 main 源集的唯一根。
 *
 * 口径边界（有意为之，勿“顺手加严”）：
 *  - 只禁 brief 点名的裸 `Color.Red` 形态。Material 语义色（`MaterialTheme.colorScheme.error` /
 *    `errorContainer`，全仓错误文字的主流写法，见 AppScreen.kt:1724/1873、ArticleImportScreen、
 *    BackupRestoreScreen 等）与「error 红底 + 白字」的滑动删除（AppScreen.kt:1934-1938，真机像素
 *    取证边缘 #B3261E，属 WCAG 非文本对比合规族）都不是本禁令的对象，必须不误伤——
 *    误伤会让后期每个错误态组件都被迫改 token，禁令变成噪声源。
 *  - `\b` 让 `Color.Red` 是完整成员名：`Color.ReddishOrange` 这类近名写法不判违规，
 *    而 `Color.Red.copy(...)` 仍判违规（派生不改「裸红字」这个事实）。
 *  - 注释里的示例不计入契约：剥注释口径已统一到共享夹具（批 1 终审 F6），本文件不再有私有正则实现，
 *    直接调 `SourceRatchet.kt` 里那份**字符串感知**的 `stripComments`。
 *
 * 已知漏判面（无法用源码正则可靠闭合，故记录口径而非做脆弱重写）：
 *  - 「等价的裸红」写法看不见：`Colors.Red`（Material 2 命名，当前 main 源集零出现）、
 *    `Color(0xFFFF0000)`、`Color.argb(255, 255, 0, 0)`、经函数/参数传入的红。
 *    新增状态色一律走 AppPalette token，别依赖本契约替你识别同色异名；
 *  - 字符串字面量里恰好出现 "Color.Red" 会被**误判为违规**（过判面）：main 现无此站点，
 *    若日后文案需要提及该写法，改措辞而不是放宽正则；
 *  - 块注释剥离会把其后的行折叠进同一行文本，故 offender 只报「文件名 + 行内容」不报行号；
 *  - 本契约管文字色命名，不管容器/渐变配比：携带修复的 `AiFeatureScreen.kt:87`（单字徽标白字压
 *    `AiFeatureGradient`，≈1.29–2.40:1）不属于禁令2 的 `buttonColors` 形态，禁令2 与本禁令都看不见它，
 *    连同 Task 2 记录的渐变/变量站点一起归 Task 12 的 connected 清单做设备侧取色取证。
 *
 * 结构沿用 AccessibilityLabelContractTest / AccessibilityContrastContractTest 样板：
 *  1. 规则收进纯函数（入参源码文本，返回 offender 列表），规则形状由合成片段断言证明；
 *  2. 真实文件扫描走 scanMainSources(dir)：目录缺失或零 .kt 立即失败，禁止 walkTopDown()
 *     在路径失效时返回空序列造成的静默空扫（包名重构 / 工作目录变化防线）；
 *  3. 每条禁令同时具备「合成用例证明规则会抓」与「真实文件用例证明当前零违规」两层断言，
 *     外加 token 登记值断言（修复目标色确实存在于 AppPalette 且等于决策 §3 的值）。
 */
class AccessibilityColorTokenContractTest {
    private val mainDir = File(MAIN_COLOR_TOKEN_SOURCE_PATH)

    /**
     * 禁令3 的匹配面：裸 `Color.Red`（含 `androidx.compose.ui.graphics.Color.Red` 全限定写法，
     * 后者是前者的后缀，同一条正则即可抓住），`\b` 排除 `Color.ReddishOrange` 这类近名成员。
     */
    private val rawRed = Regex("""Color\.Red\b""")

    /** 禁令3 纯函数：源码文本 → 含裸 Color.Red 的源码行（折叠空白，去重，不报行号）。 */
    private fun rawRedOffenders(source: String): List<String> {
        val stripped = stripComments(source)
        return rawRed.findAll(stripped)
            .map { m ->
                val lineStart = stripped.lastIndexOf('\n', m.range.first) + 1
                val lineEnd = stripped.indexOf('\n', m.range.last).let { i -> if (i == -1) stripped.length else i }
                stripped.substring(lineStart, lineEnd).trim().replace(Regex("\\s+"), " ")
            }
            .distinct()
            .toList()
    }

    /** 扫描范围守卫：目录与 .kt 文件必须真实存在，否则契约测试静默空扫。 */
    private fun scanMainSources(dir: File = mainDir): List<File> {
        assertTrue(
            dir.isDirectory,
            "状态色契约扫描目录缺失（期望 $MAIN_COLOR_TOKEN_SOURCE_PATH）：${dir.path}，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}；" +
                "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——包名重构或工作目录变化必须先修此处路径。"
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "状态色契约扫描空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
        return files
    }

    private fun offenderLines(): List<String> =
        scanMainSources().flatMap { file ->
            rawRedOffenders(file.readText()).map { offender -> "${file.name}: $offender" }
        }

    @Test
    fun `main scan scope resolves to real kotlin sources`() {
        val files = scanMainSources()
        val screens = files.filter { it.name.endsWith("Screen.kt") }
        assertTrue(
            screens.isNotEmpty(),
            "扫描到 ${files.size} 个 .kt 文件却不含任何 *Screen.kt，扫描范围疑似错位：$MAIN_COLOR_TOKEN_SOURCE_PATH"
        )
    }

    @Test
    fun `missing scan directory fails loudly instead of passing on an empty scan`() {
        val missing = File("$MAIN_COLOR_TOKEN_SOURCE_PATH/__not_a_real_package__")
        // 危险本体：无守卫时 walkTopDown() 只给空序列，真实文件契约用例会当成零违规通过。
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）"
        )
        // 守卫本体：同一缺失路径必须让扫描立即失败而不是返回空列表。
        val error = assertFailsWith<AssertionError> { scanMainSources(missing) }
        assertTrue(
            error.message?.contains(MAIN_COLOR_TOKEN_SOURCE_PATH) == true,
            "守卫失败信息必须点名期望路径，实际：${error.message}"
        )
    }

    @Test
    fun `no raw Color-Red in main sources`() {
        val offenders = offenderLines()
        assertTrue(
            offenders.isEmpty(),
            "裸 Color.Red 状态文字（应改 AppPalette.ErrorText / SuccessText）：\n${offenders.joinToString("\n")}"
        )
    }

    @Test
    fun `error and success text tokens exist at the decided palette values`() {
        val paletteFiles = scanMainSources().filter { it.name == "AppPalette.kt" }
        assertTrue(
            paletteFiles.size == 1,
            "禁令3 的修复目标 AppPalette.kt 应恰好一个，实际 ${paletteFiles.size} 个（扫描错位？）"
        )
        val paletteSource = paletteFiles.single().readText()
        listOf("ErrorText" to "0xFFB30000", "SuccessText" to "0xFF1B5E20").forEach { (token, hex) ->
            val declared = Regex("""val\s+$token\s*=\s*Color\(\s*$hex\s*\)""", RegexOption.IGNORE_CASE)
            assertTrue(
                declared.containsMatchIn(paletteSource),
                "AppPalette.$token 缺失或偏离决策 §3 登记值：应声明为 val $token = Color($hex)"
            )
        }
    }

    @Test
    fun `raw red rule synthetic snippets flag the bare named red in every writing form`() {
        val cases = listOf(
            // 修复前真实形状：BackupExportScreen.kt:99（简名）
            """state.error?.let { Text(it, color = Color.Red, modifier = Modifier.testTag("backup_export_error")) }""",
            // 修复前真实形状：ReminderSettingsScreen.kt:62（全限定，无 import 也照样是裸红）
            """state.error?.let { Text(it, color = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.testTag("reminder_settings_error")) }""",
            // 多行参数形状不得因换行逃逸
            "Text(\n    it,\n    color = Color.Red,\n    fontSize = 12.sp,\n)",
            // 派生写法：.copy 之后仍是裸红，`\b` 在 d 与 ( 之间成立
            "Text(it, color = Color.Red.copy(alpha = 0.8f))",
            // 条件表达式里的裸红
            "val statusColor = if (failed) Color.Red else Color.Gray",
            // 形参默认值位置的裸红
            "private fun StatusLine(text: String, color: Color = Color.Red)",
        )
        cases.forEach { snippet ->
            val offenders = rawRedOffenders(snippet)
            assertTrue(offenders.isNotEmpty(), "禁令3 漏判裸 Color.Red：$snippet")
            assertTrue(
                offenders.single().contains("Color.Red"),
                "禁令3 offender 应点名裸红，实际：$offenders"
            )
        }
    }

    @Test
    fun `raw red rule synthetic snippets keep material semantic colors and tokens clean`() {
        val snippets = listOf(
            // 修复后的目标形状：状态文字 token
            """state.error?.let { Text(it, color = AppPalette.ErrorText, modifier = Modifier.testTag("backup_export_error")) }""",
            """state.message?.let { Text(it, color = AppPalette.SuccessText, modifier = Modifier.testTag("backup_export_message")) }""",
            // AppScreen.kt:1724 / 1873 —— 全仓主流的 Material 语义错误色，不在禁令内
            """Text("删除", color = MaterialTheme.colorScheme.error)""",
            "Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 10.dp))",
            // AppScreen.kt:1934-1938 —— error 红底 + 白字的滑动删除（真机像素取证 #B3261E，已判合规）
            ".background(MaterialTheme.colorScheme.error, RoundedCornerShape(18.dp))",
            """Text("删除", color = Color.White, fontWeight = FontWeight.Bold)""",
            // M3 errorContainer + 白字族同属 Material 语义色，不是裸命名红
            "colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = Color.White),",
            // 域色定义本体与 deep 引用：hex 字面量不等于裸 Color.Red，禁令只针对命名常量红字
            "val Danger = DomainAccent(Color(0xFFFF3B30), Color(0xFFB30000))",
            "val ErrorText = Color(0xFFB30000)",
            "Text(text, color = DomainColors.Danger.deep)",
            // `\b` 是完整成员名匹配：近名写法不得被当成违规（否则禁令会随新命名常量误伤）
            "Text(it, color = Color.ReddishOrange)",
            // 注释里的示例代码不计入契约
            "// color = Color.Red 的旧写法已废弃\nval x = 1",
            "/* Text(it, color = Color.Red) */\nval y = 2",
        )
        snippets.forEach { snippet ->
            assertTrue(rawRedOffenders(snippet).isEmpty(), "禁令3 误判合规写法：$snippet")
        }
    }
}

private const val MAIN_COLOR_TOKEN_SOURCE_PATH = "src/main/java"
