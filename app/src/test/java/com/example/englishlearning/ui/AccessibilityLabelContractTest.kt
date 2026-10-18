package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * F5-03 源码契约（决策 §3 禁令1）：ui 包内 contentDescription 必须给 TalkBack 可朗读的中文文本——
 * 英文 testTag 字符串会被原样念成内容描述（设备 QA 审计 blocker）。两条禁令口径：
 *  禁令1a contentDescription 字面量（含插值模板）必须含中文（0x4E00–0x9FFF），
 *         "wordbook_words_back" 与 "${filter.label()} $count" 这类无中文模板同样违规，
 *         修复方式是删除该 semantics 行（子节点合并朗读已足够）或改为中文文案。
 *  禁令1b 禁止把任何名字含 tag（忽略大小写）的变量直赋给 contentDescription（.testTag(...) 的值
 *         当描述会被 TalkBack 原样念出）；不限于字面叫 tag 的变量，换名（switchTag / rowTag /
 *         tabTag / testTag / a.tag）同样拦截，而 tab.contentDescription、label 这类合法中文标签代码放行。
 *
 * 本类的三段结构是 Task 2「AccessibilityContrastContractTest」与 Task 3
 * 「AccessibilityColorTokenContractTest」契约测试的样板（同骨架，只替换规则纯函数）：
 *  1. 剥注释（口径见共享夹具 `SourceRatchet.kt` 的字符串感知 `stripComments`，批 1 终审 F6 起本类
 *     不再有私有正则实现）与规则判定收进纯函数（入参源码文本、返回 offender 列表），规则形状由合成片段的
 *     断言证明，而不是只观察真实文件；
 *  2. 真实文件扫描统一走 scanUiSources(dir)：目录缺失或零 .kt 文件立即失败，禁止 walkTopDown()
 *     在路径失效时返回空序列造成的静默空扫（包名重构 / 工作目录变化防线）；缺失目录用例现场
 *     同时断言「空序列危险真实存在」与「守卫把它拦下」；
 *  3. 每条禁令同时具备「合成用例证明规则会抓」与「真实文件用例证明当前零违规」两层断言。
 */
class AccessibilityLabelContractTest {
    private val uiDir = File(UI_SOURCE_PATH)

    private val cjkLiteral = Regex("""contentDescription = "([^"]*)"""")

    /** 禁令1a 纯函数：源码文本 → 无中文 contentDescription 字面量 offender 列表。 */
    private fun nonCjkLiteralOffenders(source: String): List<String> =
        cjkLiteral.findAll(stripComments(source))
            .filter { m -> !m.groupValues[1].any { ch -> ch.code in 0x4E00..0x9FFF } }
            .map { m -> m.value }
            .toList()

    /**
     * 禁令1b 的匹配面：contentDescription 右侧的整条限定访问（a.b.c 每一段都看）。
     * 只认标识符，不认字符串字面量与 if/when 表达式，故合法的中文标签代码
     * （tab.contentDescription、label、card.lemma）不受影响。
     */
    private val tagAssignment = Regex("""contentDescription\s*=\s*([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)""")

    /**
     * 禁令1b 纯函数：源码文本 → tag 变量直赋 contentDescription 的 offender 列表。
     * 口径是「右侧任一标识符名含 tag（忽略大小写）」而非只认名为 tag 的变量：
     * Task 4/5/9 新增的 Switch / Tab / 发音四控语义若写成 switchTag、rowTag、tabTag、testTag
     * 或 safeTag 都会被抓住，换变量名不能绕过禁令。
     */
    private fun tagVariableOffenders(source: String): List<String> =
        tagAssignment.findAll(stripComments(source))
            .filter { m -> m.groupValues[1].split('.').any { part -> part.contains("tag", ignoreCase = true) } }
            .map { m -> "contentDescription = ${m.groupValues[1]}" }
            .toList()

    /** 扫描范围守卫：目录与 .kt 文件必须真实存在，否则契约测试静默空扫。 */
    private fun scanUiSources(dir: File = uiDir): List<File> {
        assertTrue(
            dir.isDirectory,
            "contentDescription 契约扫描目录缺失（期望 $UI_SOURCE_PATH）：${dir.path}，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}；" +
                "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——包名重构或工作目录变化必须先修此处路径。"
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "contentDescription 契约扫描空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
        return files
    }

    private fun offendersOf(scan: (String) -> List<String>): List<String> =
        scanUiSources().flatMap { file -> scan(file.readText()).map { offender -> "${file.name}: $offender" } }

    @Test
    fun `ui scan scope resolves to real kotlin sources`() {
        val files = scanUiSources()
        val screens = files.filter { it.name.endsWith("Screen.kt") }
        assertTrue(
            screens.isNotEmpty(),
            "扫描到 ${files.size} 个 .kt 文件却不含任何 *Screen.kt，扫描范围疑似错位：$UI_SOURCE_PATH"
        )
    }

    @Test
    fun `missing scan directory fails loudly instead of passing on an empty scan`() {
        val missing = File("$UI_SOURCE_PATH/__not_a_real_package__")
        // 危险本体：无守卫时 walkTopDown() 只给空序列，真实文件契约用例会当成零违规通过。
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）"
        )
        // 守卫本体：同一缺失路径必须让扫描立即失败而不是返回空列表。
        val error = assertFailsWith<AssertionError> { scanUiSources(missing) }
        assertTrue(
            error.message?.contains(UI_SOURCE_PATH) == true,
            "守卫失败信息必须点名期望路径，实际：${error.message}"
        )
    }

    @Test
    fun `contentDescription literals in ui sources always contain cjk text`() {
        val offenders = offendersOf { nonCjkLiteralOffenders(it) }
        assertTrue(offenders.isEmpty(), "英文 contentDescription 字面量：\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `no contentDescription is assigned from a raw tag variable`() {
        val offenders = offendersOf { tagVariableOffenders(it) }
        assertTrue(offenders.isEmpty(), "contentDescription = tag 直赋：\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `tag ban synthetic snippets flag every tag named identifier on the right hand side`() {
        val cases = listOf(
            "tag" to ".semantics { contentDescription = tag },",
            "switchTag" to "Row(modifier = Modifier.testTag(switchTag).semantics { contentDescription = switchTag })",
            "rowTag" to ".semantics { contentDescription = rowTag }",
            "testTag" to "Modifier.clickable { onToggle() }.semantics { contentDescription = testTag }",
            "TAB_TAG" to ".semantics { contentDescription = TAB_TAG }",
            "tabTag" to ".semantics {\n    contentDescription =\n        tabTag\n}",
        )
        cases.forEach { (identifier, snippet) ->
            val offenders = tagVariableOffenders(snippet)
            assertTrue(
                offenders.isNotEmpty(),
                "禁令1b 漏判：contentDescription = $identifier 未被识别（tag 禁令只认字面 tag 变量名，换名即失效）"
            )
            assertTrue(
                offenders.single().contains(identifier),
                "禁令1b offender 应指向 $identifier，实际：$offenders"
            )
        }
    }

    @Test
    fun `tag ban synthetic snippets keep legitimate chinese label identifiers clean`() {
        val snippets = listOf(
            // AppBottomBar.kt:78 —— tab.contentDescription 取的是枚举里的中文文案
            ".semantics { contentDescription = tab.contentDescription },",
            // AppScreen.kt:2073 —— Switch 的语义标签来自中文 label
            "Switch(checked = checked, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = label })",
            ".semantics { contentDescription = card.lemma },",
            "m = m.semantics { this.contentDescription = contentDescription }",
            "contentDescription = null,",
            """.semantics { contentDescription = "返回" }""",
        )
        snippets.forEach { snippet ->
            assertTrue(tagVariableOffenders(snippet).isEmpty(), "禁令1b 误判合法中文标签代码：$snippet")
        }
    }

    @Test
    fun `cjk literal rule synthetic snippets catch english and interpolated templates only`() {
        assertTrue(
            nonCjkLiteralOffenders("""Modifier.testTag("learning_records_back").semantics { contentDescription = "learning_records_back" }""").isNotEmpty(),
            "禁令1a 漏判：英文标识符字面量应违规"
        )
        assertTrue(
            nonCjkLiteralOffenders(""".semantics { contentDescription = "${'$'}safeId" }""").isNotEmpty(),
            "禁令1a 漏判：含插值但无中文的模板串应违规"
        )
        assertTrue(nonCjkLiteralOffenders(""".semantics { contentDescription = "返回" }""").isEmpty(), "禁令1a 误判：中文文案合规")
        assertTrue(
            nonCjkLiteralOffenders(""".semantics { contentDescription = "已保存到本机 ${'$'}count" }""").isEmpty(),
            "禁令1a 误判：含中文的插值合规"
        )
        assertTrue(
            nonCjkLiteralOffenders("// contentDescription = \"wordbook_words_screen\"\nval a = 1\n").isEmpty(),
            "禁令1a 误判：注释里的示例不应计入契约"
        )
    }
}

private const val UI_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui"
