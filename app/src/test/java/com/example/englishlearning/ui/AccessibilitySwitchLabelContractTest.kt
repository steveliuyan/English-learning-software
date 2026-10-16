package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * F5-03 源码契约（决策 §4）：带 testTag 的 Switch 必须在修饰链上携带中文 contentDescription——
 * 裸 Switch 在 TalkBack 里只念「开/关」，用户无从知道开关管什么（设备 QA 审计点名四处：
 * 提醒开关 / 文章标记开关 / 备份含音频开关 / 练习单设置行开关）。两条规则口径：
 *  规则1（三个带 testTag 的开关）锚点 testTag("<tag>") 之后 80 字符窗口内必须出现
 *         contentDescription 语义；锚点本身缺失同样判失败（契约空转防线）。
 *  规则2（WorksheetSettingsScreen 的裸 Switch）锚点 Switch( 之后 200 字符窗口内必须出现
 *         contentDescription = title——行标题就是该开关的天然标签，规则按 brief 锁定这一形状，
 *         日后把 title 改名或换字面量要有意识地连带改契约，而不是被误判后放宽正则。
 *         窗口 200 沿用 brief 字面值，但注意 brief Step 3 演示的书写顺序（colors 在前、modifier
 *         收尾）在该文件真实缩进下让标签距锚点约 215 字符，**照抄会过不了 brief Step 1 的同一
 *         契约**（brief 内部自相矛盾，实现时已把 modifier 参数前置于 colors，真实间距约 134 字符）；
 *         后续新增参数别把标签挤出窗口，紧凑书写优先于放宽窗口。
 * 两条规则判的是「标签语义存在且挂在对的位置」，**不校验标签值是否中文**——值的中文合规由
 * AccessibilityLabelContractTest 禁令1a 兜底（禁令1b 同时禁止 tag 命名变量直赋 contentDescription），
 * 两份契约互为双轨：本契约锁位置，禁令1 锁内容。
 *
 * 结构沿用 AccessibilityLabelContractTest / ContrastContractTest / ColorTokenContractTest 样板：
 *  1. 剥注释与规则判定收进纯函数（入参源码文本，返回 offender 列表），规则形状由合成片段的
 *     断言证明，而不是只观察真实文件；
 *  2. 真实文件扫描统一走 scanUiSources(dir)：目录缺失或零 .kt 文件立即失败，禁止 walkTopDown()
 *     在路径失效时返回空序列造成的静默空扫（包名重构 / 工作目录变化防线）；
 *  3. 每条规则同时具备「合成用例证明规则会抓」与「真实文件用例证明当前零违规」两层断言，
 *     外加锚点存在性断言（testTag/Switch( 被删除或改名时契约必须报错而不是无声通过）。
 *
 * 已知漏判面（无法用源码正则可靠闭合，故记录口径而非做脆弱重写）：
 *  - **剥行注释的字符串字面量泄漏**：stripComments 的 `//.*` 会把字符串里出现的 `//` 之后
 *    （同一行）一并抹掉——如 `url = "https://example.com"` 所在行若同时携带 testTag 锚点或
 *    contentDescription，该行后半段对规则不可见，offender 会**静默逃逸**。禁令1（Task 1）与
 *    禁令3（Task 3）的 stripComments 同款缺陷尚未在各自 KDoc 记录，本条按控制者裁决先记于此，
 *    统一口径与处置归口见 `docs/verification/f5-03-accessibility-hardening-2026-10.md`
 *    §1.1「契约测试匹配面局限」（账号=已知债 M-B2，本轮不改判定逻辑）；
 *    新增站点不要把锚点/标签写到含 `//` 字面量的同一行即可规避；
 *  - 窗口判定是「锚点之后文本里出现 contentDescription 字样」而非 AST 级配对：锚点后 80 字符内
 *    恰好出现**别的**组件的 contentDescription 会误放行未加标签的开关（漏判）；反之标签合法但
 *    书写距离超窗（如插入长 lambda）会误判违规（过判）——误伤时优先紧凑书写，勿放宽窗口；
 *  - 规则1 只认 brief 登记的三个 testTag 锚点：新开关若不设 testTag（如规则2 的裸 Switch）不在
 *    判定面内，靠规则2 的形状锁与 Task 5/9 的同模式扩展兜底；
 *  - 修饰链经函数封装（如 Modifier.switchSemantics(desc)）时字样不出现，判违规——这是有意的：
 *    封装会把标签责任推迟到调用点，源码层无法证明 TalkBack 念得到，先报错再讨论口径。
 */
class AccessibilitySwitchLabelContractTest {
    private val uiDir = File(UI_SWITCH_SOURCE_PATH)

    /** brief 登记的三个带 testTag 开关：文件名 → testTag 锚点（Task 5/9 扩展同表追加）。 */
    private val labelledSwitches = listOf(
        "ReminderSettingsScreen.kt" to "reminder_enabled_switch",
        "ArticleReadingScreen.kt" to "article_marks_switch",
        "BackupExportScreen.kt" to "backup_export_audio_switch",
    )

    /** 剥注释样板（沿用禁令1/禁令2/禁令3）：注释里的示例代码不计入契约。 */
    private fun stripComments(text: String): String = text
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//.*"), "")

    /** 规则1 的锚点与窗口：testTag("<tag>") 之后 80 字符内必须出现 contentDescription。 */
    private fun tagAnchor(tag: String) = Regex("""testTag\("${Regex.escape(tag)}"\)""")

    /** 规则1 纯函数：源码文本 + 开关 testTag → 标签缺失 offender 列表（锚点缺失不判，由存在性断言另防）。 */
    private fun unlabelledTagSwitchOffenders(source: String, tag: String): List<String> {
        val stripped = stripComments(source)
        val labelWindow = Regex("""^[\s\S]{0,80}?contentDescription""")
        return tagAnchor(tag).findAll(stripped)
            .filterNot { m -> labelWindow.containsMatchIn(stripped.substring(m.range.last + 1)) }
            .map { m -> "testTag(\"$tag\") 之后 80 字符内无 contentDescription（TalkBack 只念「开/关」）：锚点 @ offset ${m.range.last}" }
            .toList()
    }

    /** 规则2 纯函数：源码文本 → Switch( 之后 200 字符内缺 contentDescription = title 的 offender 列表。 */
    private fun unlabelledRowSwitchOffenders(source: String): List<String> {
        val stripped = stripComments(source)
        val labelWindow = Regex("""^[\s\S]{0,200}?contentDescription = title""")
        return Regex("""Switch\(""").findAll(stripped)
            .filterNot { m -> labelWindow.containsMatchIn(stripped.substring(m.range.last + 1)) }
            .map { m -> "Switch( 之后 200 字符内无 contentDescription = title：锚点 @ offset ${m.range.last}" }
            .toList()
    }

    /** 扫描范围守卫：目录与 .kt 文件必须真实存在，否则契约测试静默空扫。 */
    private fun scanUiSources(dir: File = uiDir): List<File> {
        assertTrue(
            dir.isDirectory,
            "Switch 标签契约扫描目录缺失（期望 $UI_SWITCH_SOURCE_PATH）：${dir.path}，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}；" +
                "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——包名重构或工作目录变化必须先修此处路径。"
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "Switch 标签契约扫描空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
        return files
    }

    /** 按文件名取源码：缺失或重名立即失败，禁止契约对着隐形的文件空转。 */
    private fun sourceOf(fileName: String): String {
        val matches = scanUiSources().filter { it.name == fileName }
        assertTrue(
            matches.size == 1,
            "Switch 标签契约目标文件应恰好一个（$UI_SWITCH_SOURCE_PATH/$fileName），实际 ${matches.size} 个",
        )
        return matches.single().readText()
    }

    @Test
    fun `ui scan scope resolves to real kotlin sources`() {
        val files = scanUiSources()
        val screens = files.filter { it.name.endsWith("Screen.kt") }
        assertTrue(
            screens.isNotEmpty(),
            "扫描到 ${files.size} 个 .kt 文件却不含任何 *Screen.kt，扫描范围疑似错位：$UI_SWITCH_SOURCE_PATH"
        )
    }

    @Test
    fun `missing scan directory fails loudly instead of passing on an empty scan`() {
        val missing = File("$UI_SWITCH_SOURCE_PATH/__not_a_real_package__")
        // 危险本体：无守卫时 walkTopDown() 只给空序列，真实文件契约用例会当成零违规通过。
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）"
        )
        // 守卫本体：同一缺失路径必须让扫描立即失败而不是返回空列表。
        val error = assertFailsWith<AssertionError> { scanUiSources(missing) }
        assertTrue(
            error.message?.contains(UI_SWITCH_SOURCE_PATH) == true,
            "守卫失败信息必须点名期望路径，实际：${error.message}"
        )
    }

    @Test
    fun `every labelled switch in real sources carries a contentDescription in its modifier chain`() {
        labelledSwitches.forEach { (fileName, tag) ->
            val source = sourceOf(fileName)
            assertTrue(
                tagAnchor(tag).containsMatchIn(stripComments(source)),
                "$fileName 找不到锚点 testTag(\"$tag\")：契约空转（锚点被改名或删除必须显式失败，不能无声通过）"
            )
            val offenders = unlabelledTagSwitchOffenders(source, tag)
            assertTrue(offenders.isEmpty(), "$fileName:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `worksheet settings row switch is labelled by its title`() {
        val source = sourceOf("WorksheetSettingsScreen.kt")
        assertTrue(
            Regex("""Switch\(""").containsMatchIn(stripComments(source)),
            "WorksheetSettingsScreen 找不到锚点 Switch(：契约空转（组件被删除或改名必须显式失败）"
        )
        val offenders = unlabelledRowSwitchOffenders(source)
        assertTrue(
            offenders.isEmpty(),
            "WorksheetSettingsScreen 的裸 Switch 必须 contentDescription = title：\n${offenders.joinToString("\n")}"
        )
    }

    @Test
    fun `tag chain rule synthetic snippets flag switches with no label within the window`() {
        val tag = "reminder_enabled_switch"
        val anchor = """Modifier.testTag("$tag")"""
        val cases = listOf(
            // 修复前真实形状：锚点收尾，链上根本没有标签
            "$anchor,",
            // 窗口边界：contentDescription 距锚点闭合恰好 81 字符（brief 锁 80），不得放行
            "$anchor," + " ".repeat(80) + "contentDescription = \"开启学习提醒\"",
            // 标签写在注释里 = 对 TalkBack 不存在，剥注释后必须判违规
            "$anchor.padding(8.dp) // contentDescription",
            // 多行修饰链上的裸开关（修复前 ArticleReadingScreen 形状）
            "Switch(\n    checked = current.showLearnedMarks,\n    onCheckedChange = onSetLearnedMarks,\n    $anchor,\n)",
        )
        cases.forEach { snippet ->
            val offenders = unlabelledTagSwitchOffenders(snippet, tag)
            assertTrue(offenders.isNotEmpty(), "Switch 标签契约漏判（锚点后窗口内无标签）：$snippet")
            assertTrue(
                offenders.single().contains("testTag(\"$tag\")"),
                "offender 应点名锚点 testTag(\"$tag\")，实际：$offenders"
            )
        }
    }

    @Test
    fun `tag chain rule synthetic snippets keep labelled chains clean`() {
        val tag = "reminder_enabled_switch"
        val snippets = listOf(
            // 修复目标形状（Task 4 四处修复的书写样式）
            """modifier = Modifier.testTag("$tag").semantics { contentDescription = "开启学习提醒" },""",
            // 多行形状：窗口跨行仍须放行
            """Modifier\n    .testTag("$tag")\n    .semantics {\n        contentDescription = "包含 AI 音频文件"\n    },""",
            // 标签与锚点贴着写（0 字符间隔）
            """Modifier.testTag("$tag").semantics { contentDescription = "标记已背的词" }""",
            // 锚点根本没出现 = 该文件不含此开关，规则1 不产出 offender（存在性由真实文件用例单独把关）
            """Text("与开关无关的一行")""",
        )
        snippets.forEach { snippet ->
            assertTrue(unlabelledTagSwitchOffenders(snippet, tag).isEmpty(), "Switch 标签契约误判合规链：$snippet")
        }
    }

    @Test
    fun `row switch rule synthetic snippets flag bare switches and keep title labelled clean`() {
        val offenders = unlabelledRowSwitchOffenders(
            "Switch(\n    checked = checked,\n    onCheckedChange = onCheckedChange,\n    colors = SwitchDefaults.colors(checkedTrackColor = MintPrimary),\n)"
        )
        assertTrue(offenders.isNotEmpty(), "Switch 标签契约漏判裸 Switch(（规则2 无标签形状）")
        // 修复目标形状（生产真实书写：modifier 前置于 colors，见类 KDoc brief 缺陷说明）：
        // 行标题即标签（AppScreen.kt:2073 的 label 同款语义，禁令1b 允许：title 名不含 tag）
        val fixed = unlabelledRowSwitchOffenders(
            "Switch(\n    checked = checked,\n    onCheckedChange = onCheckedChange,\n    modifier = Modifier.semantics { contentDescription = title },\n    colors = SwitchDefaults.colors(checkedTrackColor = MintPrimary),\n)"
        )
        assertTrue(fixed.isEmpty(), "Switch 标签契约误判 contentDescription = title 合规形状")
    }
}

private const val UI_SWITCH_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui"
