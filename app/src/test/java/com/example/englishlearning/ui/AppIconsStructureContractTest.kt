package com.example.englishlearning.ui

import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppIcons
import com.example.englishlearning.ui.components.spec
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 批 1 Task 6 契约：`AppIcons.kt` 里 B 案图标的两层结构与「零 AndroidX material-icons」。
 *
 * 口径沿用 Task 2/3/4（`DesignTokenContractTest` / `MintTokenIsComposableContractTest` /
 * `MaterialThemeAssemblyContractTest`）：
 *  - 「源码文本即契约」：枚数与顺序、两层结构、豁免表、路径字符集都读 `src/main/java` 文本，
 *    本测试不在 JVM 侧渲染 Compose；
 *  - 唯一的例外是有意的缝（修复轮 I-1）：`every enum entry resolves to a spec…` 直接引用
 *    `AppIcon` / `AppIcons.all` 符号，钉的是「路径表键集 == 枚举全集」这条运行时真相——
 *    文本契约只要三处自洽就抓不住少一枚 spec（评审变异 M6 实证），只有读符号能抓；
 *  - 注释不计入代码契约：结构断言先过 `SourceRatchet.stripComments`，
 *    而「逐枚清单」与「逐枚来历注释」钉的都是注释本体，所以它们单独读未剥离的原文；
 *  - 来历注释口径（修复轮 I-2）：每枚必须紧邻一行来历注释，声明只能是
 *    「原创自绘（B 案度量）」或「转录 §<小节号>」两种之一，且同一子句内不得混用——
 *    分写两子句（外框转录 + 内幅自绘）是决策文档允许的正当形状，不在违规之列；
 *  - 目录守卫内置在共享夹具 `uiFiles()`（`walkTopDown()` 对缺失路径只给空序列 = 静默空扫），
 *    位置本身（`ui/components/`）由 `appIconsFile()` 的路径断言钉（修复轮 M-2）；
 *  - 每条规则都用合成正片段与合成负片段当场证明「抓得住且不越界」，不存在永久绿的空转断言。
 *
 * 规则的来源是决策文档，不是本文件：枚数、顺序、用途与豁免表逐枚钉在
 * `docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md` 的清单表里。
 * 增删字形或改动豁免表属范围变化：先改那份文档的清单，再改 `AppIcon` 枚举，最后才动这里——
 * 两处不一致时本测试判红，这是有意的。
 */
class AppIconsStructureContractTest {

    // ---------- 钉值表（逐字来自决策文档的清单表与裁决 4） ----------

    private val expectedIcons = listOf(
        "Back", "ChevronRight", "ChevronDown", "Close", "Check", "Search", "Book", "BookOpen",
        "List", "Bookmark", "Star", "Clock", "Chart", "Spark", "Speaker", "Play", "Pause", "Stop",
        "Mic", "Translate", "Camera", "Image", "Document", "Newspaper", "Gear", "Person", "Info",
        "Bell", "Shield", "Warning", "Trash", "Upload", "Download",
    )

    /** 两层豁免表：这五枚只有描边骨架，不得建填充层，也不得进入 ≥40dp 入口容器。 */
    private val expectedStrokeOnly = setOf("Back", "ChevronRight", "ChevronDown", "Close", "Check")

    // ---------- 规则形状 ----------

    /** 描边层与路径型填充层的合法形状：`M`/`m` 起锚，之后只允许 SVG 命令字母、数字、小数点、逗号、负号与空白。 */
    private val svgPath = Regex("""^[Mm][MmLlHhVvCcSsQqTtAaZz0-9eE.,\s-]*$""")

    /** 圆形填充的自定标记串（不走 SVG path 语法，由 Task 7 的 `AppGlyph` 渲染器解释）。 */
    private val circleFillMarker = Regex("""^CircleFill\(-?\d+(?:\.\d+)?, -?\d+(?:\.\d+)?, -?\d+(?:\.\d+)?\)$""")

    /** 禁止 AndroidX material-icons：它会牵动 `material-icons-extended` 与 lockfile（本轮零新增依赖）。 */
    private val androidxIconRef = Regex("""\bIcons\.""")

    /** 来历标记之一：本项目自绘。只认这个全称，「自绘」「原创」等简写不算（口径要能被 grep 复核）。 */
    private val selfDrawnMarker = Regex("""原创自绘（B 案度量）""")

    /** 来历标记之一的每一次「转录」提及，与它的合规范式：必须紧跟 `§` 与小节号（可带子节）。 */
    private val transcriptionMention = Regex("""转录""")
    private val numberedTranscription = Regex("""转录\s*§[0-9]+(?:\.[0-9]+)*""")

    /** 来历注释的子句切分：注释按行、行内按分号/句号/破折号切，混用判定只在同一子句内生效。 */
    private val provenanceClause = Regex("""[；;。\n]|——""")

    // ---------- 读取与解析 ----------

    private fun appIconsFile(): File {
        val files = uiFiles().filter { it.name == APP_ICONS_NAME }
        assertTrue(
            files.size == 1,
            "$APP_ICONS_NAME 应恰好一个，实际 ${files.size} 个（图标库未创建 / 被改名 / 扫描错位？" +
                "期望目录 $UI_MAIN_SOURCE_PATH，JVM 工作目录 ${File(".").absolutePath}）"
        )
        val file = files.single()
        // 位置本身是 spec §4.1 的决定：只钉文件名时，文件被挪去别的包目录仍然绿（修复轮 M-2）。
        assertContains(
            file.path.replace('\\', '/'),
            ICONS_LOCATION_SEGMENT,
            message = "$APP_ICONS_NAME 不在 ui/components/ 下（图标库的位置是 spec §4.1 钉过的决定，换目录不该继续绿）：${file.path}",
        )
        return file
    }

    /** 剥注释后的实现文本：代码契约的入口。 */
    private fun codeText(): String = stripComments(appIconsFile().readText())

    /** 原文：注释契约（逐枚清单）的入口——被钉的对象本身就是注释，这里绝不能先剥注释。 */
    private fun rawText(): String = appIconsFile().readText()

    /** `enum class AppIcon { … }` 的条目（按声明顺序）；枚举声明缺失时返回 null，由调用方判红。 */
    private fun enumEntries(text: String): List<String>? {
        val body = Regex("""enum\s+class\s+AppIcon\s*\{([^}]*)\}""").find(text) ?: return null
        return body.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** `StrokeOnlyIcons` 的字面集合成员（去掉 `AppIcon.` 前缀）；声明缺失时返回 null。 */
    private fun strokeOnlyLiterals(text: String): Set<String>? {
        val anchor = Regex("""StrokeOnlyIcons\s*(?::\s*[A-Za-z<>,.\s]+)?=\s*setOf\(""").find(text) ?: return null
        val open = anchor.range.last
        val close = matchingParen(text, open) ?: return null
        return text.substring(open + 1, close)
            .split(',')
            .map { it.trim().removePrefix("AppIcon.") }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** `AppIconSpec(name = "…")` 块 → `name` 到块体；重名直接判红（第二份真值会藏在这里）。 */
    private fun specBlocks(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        Regex("""AppIconSpec\(\s*name\s*=\s*"([^"]+)"""").findAll(text).forEach { match ->
            val open = match.range.first + match.groupValues[0].indexOf('(')
            val close = matchingParen(text, open) ?: error("AppIconSpec( 的圆括号未配平：$APP_ICONS_NAME 形状异常")
            val block = text.substring(open + 1, close)
            val name = match.groupValues[1]
            assertTrue(!out.containsKey(name), "AppIconSpec 的 name 重复：$name（一枚字形两份路径数据就是第二套真值）")
            out[name] = block
        }
        return out
    }

    /** 字段声明缺失返回 null；`emptyList()` 返回空表；`listOf("a", "b")` 返回引号内条目。 */
    private fun fieldPaths(block: String, field: String): List<String>? {
        val anchor = Regex("""$field\s*=\s*(emptyList\(\)|listOf\()""").find(block) ?: return null
        if (anchor.groupValues[1] == "emptyList()") return emptyList()
        val open = anchor.range.last
        val close = matchingParen(block, open) ?: error("$field 的圆括号未配平")
        return Regex("\"([^\"]*)\"").findAll(block.substring(open + 1, close)).map { it.groupValues[1] }.toList()
    }

    /** 从 [open] 处（必须是一个左圆括号）做括号配平，返回右圆括号下标。 */
    private fun matchingParen(text: String, open: Int): Int? {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
                else -> Unit
            }
        }
        return null
    }

    /** 等序比对：报出「缺项 / 多项 / 首个错位」，而不是只甩两个列表。 */
    private fun orderDifferences(expected: List<String>, actual: List<String>): List<String> {
        val diffs = mutableListOf<String>()
        expected.filterNot { it in actual }.forEach { diffs += "缺 $it" }
        actual.filterNot { it in expected }.forEach { diffs += "多 $it" }
        expected.zip(actual).forEachIndexed { index, (want, got) ->
            if (want != got && diffs.none { it.contains(want) || it.contains(got) }) {
                diffs += "第 ${index + 1} 位应为 $want，实为 $got"
            }
        }
        return diffs
    }

    private fun assertSameOrder(label: String, expected: List<String>, actual: List<String>) {
        val diffs = orderDifferences(expected, actual)
        assertTrue(diffs.isEmpty(), "$label 与决策清单不符：$diffs（期望 ${expected.size} 项，实到 ${actual.size} 项）")
    }

    /** 两层口径的纯函数：豁免枚必须零填充、其余必须至少一条填充；返回违规描述（空表 = 合规）。 */
    private fun layerViolations(specs: Map<String, String>, exempt: Set<String>): List<String> {
        val offenders = mutableListOf<String>()
        specs.forEach { (name, block) ->
            val stroke = fieldPaths(block, "strokePaths")
            val fill = fieldPaths(block, "fillPaths")
            if (stroke == null || stroke.isEmpty()) offenders += "$name 缺 strokePaths 或为空：描边骨架是 B 案的必要层"
            if (fill == null) offenders += "$name 缺 fillPaths 字段：两层口径不能靠省略字段表达"
            else if (name in exempt && fill.isNotEmpty()) {
                offenders += "$name 在豁免表里却带 ${fill.size} 条填充层：「两层」口径变成含糊"
            } else if (name !in exempt && fill.isEmpty()) {
                offenders += "$name 不在豁免表里却没有填充层：B 案要求除豁免表外每枚两层"
            }
        }
        return offenders
    }

    /** 路径形状纯函数：描边层必须是 SVG path；填充层允许 SVG path 或 `CircleFill` 标记。 */
    private fun shapeViolations(name: String, field: String, paths: List<String>): List<String> = paths.mapNotNull { path ->
        val legal = if (field == "fillPaths") svgPath.matches(path) || circleFillMarker.matches(path) else svgPath.matches(path)
        if (legal) null else "$name 的 $field 形状不合法（$path）"
    }

    /** 「逐枚清单」到「两层豁免表」之间的小节文本；任一锚点缺失返回 null。 */
    private fun iconListSection(raw: String): String? {
        if (!raw.contains(LIST_HEADING) || !raw.contains(EXEMPT_HEADING)) return null
        return raw.substringAfter(LIST_HEADING).substringBefore(EXEMPT_HEADING)
    }

    /** 小节内的编号行 `序号. \`名字\` — 用途｜两层列`，按序号排序。 */
    private fun iconListEntries(section: String): List<ListedGlyph> =
        iconListLine.findAll(section)
            .map { ListedGlyph(it.groupValues[1].toIntOrNull() ?: 0, it.groupValues[2], it.groupValues[3]) }
            .sortedBy { it.index }
            .toList()

    /**
     * 枚名 → 它前面紧邻的来历注释（连续 `//` 行合并；没有紧邻注释 = null）。
     * 读原文而不是剥注释文本：这里被钉的对象就是注释本身。定位靠 spec 块的起始行往上走，
     * 块与块之间的代码行会终止上溯，所以「整行删掉来历注释」得到 null 而不是捡到上一枚的注释。
     */
    private fun provenanceComments(raw: String): Map<String, String?> {
        val lines = raw.split('\n')
        val out = LinkedHashMap<String, String?>()
        Regex("""AppIconSpec\(\s*name\s*=\s*"([^"]+)"""").findAll(raw).forEach { match ->
            val specLineIndex = raw.substring(0, match.range.first).count { it == '\n' }
            val block = mutableListOf<String>()
            var index = specLineIndex - 1
            while (index >= 0) {
                val trimmed = lines[index].trim()
                if (!trimmed.startsWith("//")) break
                block.add(0, trimmed.removePrefix("//").trim())
                index--
            }
            out[match.groupValues[1]] = if (block.isEmpty()) null else block.joinToString("\n")
        }
        return out
    }

    /** 来历注释纯函数：枚名到紧邻注释 → 违规描述（空表 = 合规）。 */
    private fun provenanceViolations(provenance: Map<String, String?>): List<String> {
        val offenders = mutableListOf<String>()
        provenance.forEach { (name, comment) ->
            if (comment == null) {
                offenders += "$name 缺紧邻来历注释：许可台账 app-authored-ui-icons 的措辞依赖每枚来历声明（AC-R-05）"
                return@forEach
            }
            val mentions = transcriptionMention.findAll(comment).count()
            val selfDrawn = selfDrawnMarker.findAll(comment).count()
            if (mentions == 0 && selfDrawn == 0) {
                offenders += "$name 的来历注释两种来历标记都没有：既没标「原创自绘（B 案度量）」也没标「转录 §<小节号>」"
            }
            if (mentions != numberedTranscription.findAll(comment).count()) {
                offenders += "$name 写了「转录」却没带小节号：出处标签查不实就等于没写（范式：转录 §5 / 转录 §6.1 / 转录 §7）"
            }
            provenanceClause.split(comment).forEach { clause ->
                val text = clause.trim()
                if (selfDrawnMarker.containsMatchIn(text) && transcriptionMention.containsMatchIn(text)) {
                    offenders += "$name 的同一子句「$text」既称原创自绘又称转录：来历口径含糊 = 第二套真值"
                }
            }
        }
        return offenders
    }

    /**
     * 填充不透明度纯函数（修复轮 M-1）：透明度是主题属性，只在 `DuotoneFillAlpha` 里成对出现，
     * 既不许逐枚写回 `AppIconSpec(...)`，也不许只留一档。
     */
    private fun alphaViolations(text: String): List<String> {
        val offenders = mutableListOf<String>()
        if (!text.contains("0.42")) offenders += "缺浅色档 0.42（B 案浅色实心层 = DuotoneFillAlpha.Light）"
        if (!text.contains("0.30")) offenders += "缺深色档 0.30（B 案深色实心层 = DuotoneFillAlpha.Dark）"
        specBlocks(text).forEach { (name, block) ->
            if (block.contains("fillAlpha")) {
                offenders += "$name 把填充不透明度写回了 AppIconSpec(...) 调用：33 枚各写一份就是第二套真值"
            }
        }
        val declaration = Regex("""data\s+class\s+AppIconSpec\s*\(([^)]*)\)""").find(text)
        if (declaration != null && declaration.groupValues[1].contains("fillAlpha")) {
            offenders += "AppIconSpec 又带回了 fillAlpha 字段：透明度按主题取（DuotoneFillAlpha.forTheme），不是字形属性"
        }
        return offenders
    }

    // ---------- 用例：枚数与顺序（brief Step 1 断言 1） ----------

    @Test
    fun `AppIcon enum lists every glyph in the ruling order`() {
        val entries = enumEntries(codeText())
        assertTrue(entries != null, "缺 enum class AppIcon { … }：图标库的枚数没有载体")
        assertSameOrder("AppIcon 枚举", expectedIcons, entries!!)

        // 反空转 1：枚举少一项必须被同一条规则抓住（合成片段只测解析与比对本身）。
        val shortFragment = """
            enum class AppIcon { Back, ChevronRight, ChevronDown, Close, Check, Search, Book, BookOpen,
            List, Bookmark, Star, Clock, Chart, Spark, Speaker, Play, Pause, Stop, Mic, Translate,
            Camera, Image, Document, Newspaper, Gear, Person, Info, Bell, Shield, Warning, Trash, Download }
        """.trimIndent()
        val shortEntries = enumEntries(shortFragment) ?: error("合成片段解析失败：多行枚举体没读出来")
        assertNotEquals(expectedIcons, shortEntries, "合成负例被放行：枚举少一项仍与钉定清单相等")
        assertTrue(
            orderDifferences(expectedIcons, shortEntries).contains("缺 Upload"),
            "等序比对应点名缺失项，实际差异：${orderDifferences(expectedIcons, shortEntries)}"
        )

        // 反空转 2：枚数对但顺序错位（头两枚互换）同样判红——顺序也是决策文档的钉值面。
        val swapped = expectedIcons.toMutableList().also { it[0] = it[1]; it[1] = expectedIcons[0] }
        assertEquals(expectedIcons.size, swapped.size, "合成片段自身失控")
        assertNotEquals(expectedIcons, swapped, "合成负例被放行：同集合换序仍算等序")
        assertEquals(
            listOf("第 1 位应为 Back，实为 ChevronRight"),
            orderDifferences(expectedIcons, swapped),
            "等序比对没点名首个错位",
        )

        // 反空转 3：枚举声明缺失时解析给 null，而不是空列表冒充「零枚也算通过」。
        assertEquals(null, enumEntries("val somethingElse = 1"), "没有枚举声明时解析必须给 null（否则断言空转）")
    }

    // ---------- 用例：两层结构与豁免双向（brief Step 1 断言 2） ----------

    @Test
    fun `every glyph outside the exemption table carries a fill layer and every exemption stays bare`() {
        val text = codeText()
        val specs = specBlocks(text)
        assertSameOrder("AppIconSpec 的 name 清单", expectedIcons, specs.keys.toList())
        val exempt = strokeOnlyLiterals(text)
        assertEquals(
            expectedStrokeOnly,
            exempt,
            "StrokeOnlyIcons 与决策文档的豁免表不一致（双向比对：多一枚与少一枚都判红）",
        )
        assertEquals(
            emptyList(),
            layerViolations(specs, exempt ?: emptySet()),
            "两层结构与豁免表对不上（逐条违规见上）",
        )
    }

    @Test
    fun `the two-layer rules are not vacuous`() {
        // 合成正片段：豁免枚裸描边 + 非豁免枚两层，必须判合规。
        val good = """
            AppIconSpec(
                name = "Back",
                strokePaths = listOf("M15 5l-7 7 7 7"),
                fillPaths = emptyList(),
            )
            AppIconSpec(
                name = "Search",
                strokePaths = listOf("M11 5a6 6 0 010 12 6 6 0 010-12"),
                fillPaths = listOf("CircleFill(11, 11, 6)"),
            )
        """.trimIndent()
        val goodSpecs = specBlocks(good)
        assertEquals(listOf("Back", "Search"), goodSpecs.keys.toList(), "合成片段解析失败：逐枚块没切出来")
        assertEquals(
            emptyList(),
            layerViolations(goodSpecs, setOf("Back")),
            "合成正片段被误判：合规的「裸描边 + 两层」组合被判违规",
        )

        // 合成负例 1：豁免枚（Back）偷偷带上填充层。
        val exemptWithFill = """
            AppIconSpec(
                name = "Back",
                strokePaths = listOf("M15 5l-7 7 7 7"),
                fillPaths = listOf("M5 5h4v4H5z"),
            )
        """.trimIndent()
        assertEquals(
            listOf("Back 在豁免表里却带 1 条填充层：「两层」口径变成含糊"),
            layerViolations(specBlocks(exemptWithFill), setOf("Back")),
            "合成负例被放行：豁免表内出现填充层没被抓住",
        )

        // 合成负例 2：非豁免枚退化成单层（emptyList）。
        val singleLayer = """
            AppIconSpec(
                name = "Search",
                strokePaths = listOf("M11 5a6 6 0 010 12 6 6 0 010-12"),
                fillPaths = emptyList(),
            )
        """.trimIndent()
        assertEquals(
            listOf("Search 不在豁免表里却没有填充层：B 案要求除豁免表外每枚两层"),
            layerViolations(specBlocks(singleLayer), setOf("Back")),
            "合成负例被放行：非豁免枚单层没被抓住",
        )

        // 合成负例 3：描边层缺失（只剩填充层）也要被抓——否则「每枚至少一条 stroke」是空话。
        val strokeless = """
            AppIconSpec(name = "Star", strokePaths = emptyList(), fillPaths = listOf("M6.4 4h11v15H6.4z"))
        """.trimIndent()
        assertEquals(
            listOf("Star 缺 strokePaths 或为空：描边骨架是 B 案的必要层"),
            layerViolations(specBlocks(strokeless), setOf("Back")),
            "合成负例被放行：裸填充无描边也算合格",
        )

        // 合成负例 4：豁免表字面量自己漂移。
        assertEquals(
            setOf("Back", "Check"),
            strokeOnlyLiterals("val StrokeOnlyIcons: Set<AppIcon> = setOf(AppIcon.Back, AppIcon.Check)"),
            "豁免表解析失效：字面量集合没读出来",
        )
        assertEquals(null, strokeOnlyLiterals("val unrelated = 1"), "豁免表缺失时必须给 null，不能冒充空集")
    }

    // ---------- 用例：路径形状（brief Step 1 断言 3 + 控制器追加的填充层形状） ----------

    @Test
    fun `stroke layers are svg paths and fill layers are svg paths or circle markers`() {
        specBlocks(codeText()).forEach { (name, block) ->
            val strokes = fieldPaths(block, "strokePaths").orEmpty()
            assertTrue(strokes.isNotEmpty(), "$name 缺 strokePaths：形状用例的前提是每枚至少一条描边")
            assertTrue(
                shapeViolations(name, "strokePaths", strokes).isEmpty(),
                "$name 的描边层形状违规：${shapeViolations(name, "strokePaths", strokes)}",
            )
            val fills = fieldPaths(block, "fillPaths").orEmpty()
            assertTrue(
                shapeViolations(name, "fillPaths", fills).isEmpty(),
                "$name 的填充层形状违规：${shapeViolations(name, "fillPaths", fills)}",
            )
        }
    }

    @Test
    fun `the path shape rules are not vacuous`() {
        assertTrue(svgPath.matches("M15 5l-7 7 7 7"), "合成正片段失效：合法描边被判不合格")
        assertTrue(svgPath.matches("m4 6h16"), "合成正片段失效：小写起锚与相对命令被判不合格")
        assertTrue(circleFillMarker.matches("CircleFill(11, 11, 6)"), "合成正片段失效：圆形标记被判不合格")
        assertTrue(circleFillMarker.matches("CircleFill(17, 17, 2.5)"), "合成正片段失效：小数半径被判不合格")

        assertFalse(svgPath.matches("CircleFill(11, 11, 6)"), "合成负例被放行：CircleFill 混进描边层也算合法")
        assertFalse(svgPath.matches("M20 20l-4.6-4.6)"), "合成负例被放行：多余括号混进路径也算合法")
        assertFalse(svgPath.matches("L11 5a6 6 0 010 12"), "合成负例被放行：不起锚于 M/m 也算合法")
        assertFalse(svgPath.matches("drawPath(icon)"), "合成负例被放行：代码片段混进路径也算合法")
        assertFalse(circleFillMarker.matches("CircleFill(11, 11)"), "合成负例被放行：缺半径的圆形标记也算合法")
        assertFalse(svgPath.matches("CircleFill"), "合成负例被放行：裸标记名也算合法路径")

        assertEquals(
            listOf("Star 的 fillPaths 形状不合法（whatever）"),
            shapeViolations("Star", "fillPaths", listOf("whatever")),
            "合成负例被放行：填充层塞任意字符串仍算合格",
        )
        assertEquals(
            listOf("Star 的 strokePaths 形状不合法（CircleFill(12, 15, 4)）"),
            shapeViolations("Star", "strokePaths", listOf("CircleFill(12, 15, 4)")),
            "合成负例被放行：描边层塞圆形标记仍算合格",
        )
        assertEquals(
            emptyList(),
            shapeViolations("Search", "fillPaths", listOf("CircleFill(11, 11, 6)", "M5 6h5v5H5z")),
            "合成正片段被误判：填充层两种合法形状被拒",
        )
    }

    // ---------- 用例：全站零 material-icons（brief Step 1 断言 4） ----------

    @Test
    fun `no androidx material icon reference anywhere in the ui sources`() {
        val hits = uiFiles().flatMap { file ->
            androidxIconRef.findAll(stripComments(file.readText())).map { file.name }.toList()
        }
        assertTrue(hits.isEmpty(), "禁止 AndroidX material-icons（会牵动 lockfile）：${hits.distinct()}")

        // 反空转：规则必须抓得住真实违规形状，同时不被本项目自己的 `AppIcons` 命名满足。
        assertEquals(1, countMatches("val mark = Icons.Filled.Home", androidxIconRef), "违规形状没被数出来：规则空转")
        assertEquals(1, countMatches("import androidx.compose.material.icons.Icons.", androidxIconRef), "import 形状漏判")
        assertEquals(0, countMatches("val all = AppIcons.all.getValue(icon)", androidxIconRef), "本项目 AppIcons 被误判成 material-icons")
        assertEquals(0, countMatches("val only = StrokeOnlyIcons.size", androidxIconRef), "本项目 StrokeOnlyIcons 被误判")
        assertEquals(0, countMatches("// Icons.Filled 的旧写法\nval unrelated = 1", androidxIconRef), "注释里的旧写法被算进违规")
    }

    // ---------- 用例：逐枚清单钉进注释（brief Step 1 断言 5，spec §10.5） ----------

    @Test
    fun `file header pins the per-glyph list in the ruling order`() {
        val raw = rawText()
        val section = iconListSection(raw)
        assertTrue(
            section != null,
            "$APP_ICONS_NAME 缺「逐枚清单」小节：spec §10.5 要求逐枚清单钉进本文件顶部注释"
        )

        val listed = iconListEntries(section!!)
        assertSameOrder("文件头逐枚清单", expectedIcons, listed.map { it.name })
        listed.forEach { glyph -> assertContains(raw, "`${glyph.name}`") }

        // 「两层」列必须与代码里的豁免表同源：注释说豁免、代码带填充（或反之）都判红。
        val claimedExempt = listed.filter { it.layerColumn.contains("豁免") }.map { it.name }.toSet()
        assertEquals(
            expectedStrokeOnly,
            claimedExempt,
            "逐枚清单的「两层」列与决策文档豁免表不一致：注释自称的豁免枚是 $claimedExempt",
        )
        assertEquals(
            claimedExempt,
            strokeOnlyLiterals(codeText()),
            "逐枚清单的「两层」列与 StrokeOnlyIcons 不一致：豁免口径成了两处真值",
        )
    }

    @Test
    fun `the per-glyph list parser is not vacuous`() {
        val good = """
              逐枚清单
              ---------
              1. `Back` — 返回键｜豁免（描边only）
              2. `Search` — 查词入口｜两层
              两层豁免表
        """.trimIndent()
        val section = iconListSection(good) ?: error("合成片段解析失败：小节定位不到")
        val parsed = iconListEntries(section)
        assertEquals(listOf("Back", "Search"), parsed.map { it.name }, "合成正片段失效：编号行没被解析")
        assertEquals(
            setOf("Back"),
            parsed.filter { it.layerColumn.contains("豁免") }.map { it.name }.toSet(),
            "合成正片段失效：「两层」列里的豁免标记没被读出来",
        )

        // 合成负例：清单少一枚（漏掉 Search）——同一条规则必须给出可比的差异。
        val incomplete = iconListEntries(
            iconListSection("  逐枚清单\n  1. `Back` — 返回键｜豁免（描边only）\n  两层豁免表")
                ?: error("合成片段解析失败：缺枚小节定位不到")
        )
        assertEquals(
            listOf("缺 Search"),
            orderDifferences(listOf("Back", "Search"), incomplete.map { it.name }),
            "合成负例被放行：清单缺枚没造成可比差异",
        )
        assertEquals(null, iconListSection("只有代码没有注释小节"), "小节锚点缺失时必须给 null，不能当成空清单")
    }

    // ---------- 用例：键集完整性读符号（修复轮 I-1，文本契约的有意补缝） ----------

    @Test
    fun `every enum entry resolves to a spec and no spec is orphaned`() {
        val icons = AppIcon.entries.toSet()
        assertEquals(icons, AppIcons.all.keys, "枚举与路径表不同源：缺枚或多余枚")
        assertEquals(33, AppIcons.all.size, "`AppIcons.all` 枚数不是 33：清单/枚举/spec 三处之一漂移")
        expectedIcons.forEach { name -> assertSame(name, AppIcon.valueOf(name).spec.name, "$name 的 spec.name 与枚举名不一致") }

        // 反空转：少一个键就必须让上面第一条判红（评审变异 M6 的形状，文本契约抓不到它）。
        assertNotEquals(icons, (AppIcons.all - AppIcon.Download).keys, "键集比对空转：抽掉一枚仍与枚举全集相等")
    }

    // ---------- 用例：逐枚来历注释钉住许可台账的证据链（修复轮 I-2） ----------

    @Test
    fun `every glyph declares its provenance in the comment above its paths`() {
        val provenance = provenanceComments(rawText())
        assertEquals(
            expectedIcons,
            provenance.keys.toList(),
            "来历注释解析面与钉值清单不同源（枚序或枚数漂移，本条没有可比对象）",
        )
        assertEquals(
            emptyList(),
            provenanceViolations(provenance),
            "逐枚来历注释违规（缺失 / 混用两种标记 / 转录没带小节号，逐条见上）",
        )
    }

    @Test
    fun `the provenance rules are not vacuous`() {
        // 合成正片段：外框与内幅分写两子句，各自一种标记 = 决策文档允许的正当形状，不得越界判红。
        val splitProvenance = """
            // Search：骨架层转录 §6.1；内幅原创自绘（B 案度量）
            AppIconSpec(name = "Search", strokePaths = listOf("M11 5a6 6 0 010 12"), fillPaths = listOf("CircleFill(11, 11, 6)"))
        """.trimIndent()
        assertEquals(
            emptyList(),
            provenanceViolations(provenanceComments(splitProvenance)),
            "合成正片段被误判：分写两子句（转录 + 自绘）被判成混用",
        )

        // 合成负例 1（评审变异 M8 的真实形状）：来历注释整行删掉 → 点名该枚。
        val noComment = "AppIconSpec(name = \"Book\", strokePaths = listOf(\"M6.5 5h11\"), fillPaths = listOf(\"M5 6h5v10H5z\"))"
        assertEquals(
            listOf("Book 缺紧邻来历注释：许可台账 app-authored-ui-icons 的措辞依赖每枚来历声明（AC-R-05）"),
            provenanceViolations(provenanceComments(noComment)),
            "合成负例被放行：删掉来历注释仍算合规",
        )

        // 合成负例 2：同一子句里同时塞「原创自绘（B 案度量）」与「转录 §6.2」→ 点名该枚。
        val mixedClause = """
            // Star：骨架原创自绘（B 案度量）转录 §6.2 同串
            AppIconSpec(name = "Star", strokePaths = listOf("M6.4 4h11z"), fillPaths = listOf("M6.4 4h11z"))
        """.trimIndent()
        assertTrue(
            provenanceViolations(provenanceComments(mixedClause))
                .any { it.startsWith("Star") && it.contains("既称原创自绘又称转录") },
            "合成负例被放行：一枚里两种标记混写仍算合规（口径含糊 = 第二套真值）",
        )

        // 合成负例 3：写了「转录」却没有小节号 → 出处标签查不实。
        val sectionless = """
            // Info：圆环是稿中 <circle> 的标记串转录
            AppIconSpec(name = "Info", strokePaths = listOf("M12 4a8 8 0 010 16"), fillPaths = listOf("CircleFill(12, 12, 8)"))
        """.trimIndent()
        assertTrue(
            provenanceViolations(provenanceComments(sectionless)).any { it.startsWith("Info") && it.contains("没带小节号") },
            "合成负例被放行：不带小节号的转录也算合规",
        )

        // 合成负例 4：注释在场但两种标记都没有 → 缺来历声明。
        val markerless = """
            // List：按 B 案度量补齐三条文字线与圆点
            AppIconSpec(name = "List", strokePaths = listOf("M8 6h12"), fillPaths = listOf("M4 4.8h2.4v2.4H4z"))
        """.trimIndent()
        assertTrue(
            provenanceViolations(provenanceComments(markerless)).any { it.startsWith("List") && it.contains("两种来历标记都没有") },
            "合成负例被放行：只写度量不写来历也算合规",
        )

        // 解析面守卫：没有 spec 块时给空表，不冒充「零枚也合规」。
        assertEquals(emptyMap(), provenanceComments("val unrelated = 1"), "没有 AppIconSpec 块时必须解析为空")
    }

    // ---------- 用例：填充不透明度是主题取值而非逐枚字段（修复轮 M-1） ----------

    @Test
    fun `duotone fill opacity is a themed pair of constants and never a per-glyph field`() {
        val text = codeText()
        assertTrue(
            Regex("""data\s+class\s+AppIconSpec\s*\(""").containsMatchIn(text),
            "找不到 AppIconSpec 声明：透明度字段的归属没有面可钉",
        )
        assertEquals(
            emptyList(),
            alphaViolations(text),
            "填充不透明度口径违规（浅 0.42 / 深 0.30 两个主题档必须在场，且不得逐枚写字段）",
        )
    }

    @Test
    fun `the fill opacity rules are not vacuous`() {
        // 合成正片段：主题常量成对 + 逐枚不写字段。
        val themed = """
            object DuotoneFillAlpha {
                const val Light = 0.42f
                const val Dark = 0.30f
            }
            AppIconSpec(name = "Search", strokePaths = listOf("M11 5a6 6 0 010 12"), fillPaths = listOf("CircleFill(11, 11, 6)"))
        """.trimIndent()
        assertEquals(emptyList(), alphaViolations(themed), "合成正片段被误判：主题成对常量被判违规")

        // 合成负例 1：把透明度逐枚写回调用（评审变异 M7 的形状）。
        val perGlyph = "AppIconSpec(name = \"Search\", strokePaths = listOf(\"M11 5\"), fillPaths = listOf(\"CircleFill(11, 11, 6)\"), fillAlpha = 0.9f)"
        assertEquals(
            listOf("Search 把填充不透明度写回了 AppIconSpec(...) 调用：33 枚各写一份就是第二套真值"),
            alphaViolations(perGlyph).filter { it.contains("调用") },
            "合成负例被放行：逐枚 fillAlpha 字段没被抓住",
        )

        // 合成负例 2：只剩一档（把深色档当成两档通用就是这个形状）。
        val oneShade = "AppIconSpec(name = \"Search\", strokePaths = listOf(\"M11 5\"), fillPaths = listOf(\"CircleFill(11, 11, 6)\")) const val Dark = 0.30f"
        assertTrue(
            alphaViolations(oneShade).any { it.contains("0.42") },
            "合成负例被放行：缺浅色档 0.42 没被点名",
        )
    }

    private companion object {
        const val APP_ICONS_NAME = "AppIcons.kt"
        const val ICONS_LOCATION_SEGMENT = "/ui/components/"
        const val LIST_HEADING = "逐枚清单"
        const val EXEMPT_HEADING = "两层豁免表"

        /** 编号行：`序号. \`字形名\` — 用途（落点）｜两层列`。 */
        val iconListLine = Regex("""^\s*\**\s*(\d+)\.\s+`([A-Za-z]+)`(.*)$""", RegexOption.MULTILINE)
    }
}

/** 逐枚清单里的一行：序号、字形名、「两层」列文本。 */
private data class ListedGlyph(val index: Int, val name: String, val layerColumn: String)
