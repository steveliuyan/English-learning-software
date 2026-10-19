package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 12.5 修复轮的容器—墨配对判据（评审 I-5 的自然延伸，不扩范围：它只判本任务自己造出的那一类缺陷）。
 *
 * 立论（与批 1 终审 F4 / AC-R-11 同一条口径）：`AppType` 六档自带**静态浅套墨**
 * （`ui/theme/AppType.kt`：Display/Headline/Title/Body = `AppPalette.TextPrimary`，Footnote/Label =
 * `AppPalette.TextSecondary`），`appTypography()` 只在装配 M3 `Typography` 的路径上摘色，
 * `style = AppType.*` 的**直读**仍然带着那两枚硬编码深墨。于是「容器迁深、墨留浅套」会当场造出
 * 深色档 1.02:1 的不可读前景——本任务要消灭的缺陷的反向形态。批 1 新组件的既定写法正是补显式前景
 * （`ui/components/ScreenUnavailable.kt`、`ui/components/EntryRow.kt`：`style = AppType.Body, color = …`）。
 *
 * 判据形状（有意按**顶层函数段**取样，不做 AST/缩进窗口）：
 *  ① 段落 = 一条 `fun` 声明起、到下一条 `fun` 声明前（本仓 Composable 一律是 `@Composable` 标注的顶层函数，
 *     段内即该组件自己画容器 + 画前景的范围）；
 *  ② 段落含中性容器迁移读点 `appColors().surface` → 该段容器已随主题换套；
 *  ③ 该段内任一 `Text(…)` 实参含 `style = AppType.*` 而不含 `color =` → 违规（浅套静态墨压换套深底）。
 *  段落级足够：本轮 5 处违规（`AiProfileRow` 三处、`CapabilityToggle` 两处）全在「同一段既画迁移容器、
 *  又画静态墨前景」这一形状上；而同一文件里压在**未迁**玻璃面板（`AppPalette.GlassFill`）与页底上的
 *  排印点不在迁移段内，不误判——那些站点属批 3（`GlassFillBackdropDebtLedger` / `AppType` 摘色）。
 *
 * 三条防空转守卫：合成正/反片段各一枚（命中形状必报、补色后必不报、无迁移容器段必不报），
 * 加目录守卫复用 [uiFiles]（缺路径/零 .kt 当场红，不静默空扫）；真文件锚不拼假段，直接在生产源码里
 * 要求「AiProfileRow 那一段同时含迁移容器读点与 AppType 排印点」——形状不在了就当场判红并说明锚点要更新。
 * 本判据的取值不依赖任何具体 token 数值，也不声明「这条规则覆盖了所有配对缺陷」：
 * 它只挡「已迁容器 + 未补色直读排印档」这一形，其余配对（域色 `deep` 墨、玻璃面板前景）由
 * [DarkTokenConsumptionRatchetTest] 的同批理由台账与批 2/批 3 随屏裁决负责。
 */
private val TopLevelFunDecl = Regex("""^(?:private |internal |public )?fun [A-Za-z_]""")

/** 中性容器迁移读点。`\b` 同时排除 `appColors().surfaceGlass`（玻璃填充族，批 3 专项，形状不同）。 */
private val MigratedNeutralContainer = Regex("""appColors\(\)\.surface\b""")

/** 排印档直读：只有六档本体，`MaterialTheme.typography.*` 已被摘色，不属本判据。 */
private val AppTypeStyleRead = Regex("""style\s*=\s*AppType\.(?:Display|Headline|Title|Body|Footnote|Label)\b""")

/** Text 实参里的显式前景。左侧负向断言排除 `contentColor =` 这类同名后缀。 */
private val ExplicitInkArgument = Regex("""(?<![A-Za-z])color\s*=""")

/** 剥注释后按顶层 `fun` 切段；首条 `fun` 之前的 package/imports/KDoc 成段 0（不含 Text 调用，天然无违规）。 */
private fun topLevelSegments(text: String): List<String> {
    val out = mutableListOf<StringBuilder>()
    var current = StringBuilder()
    for (line in text.lines()) {
        // 用 containsMatchIn 而不是 matches：后者是**整行**匹配，会把 `private fun Foo(` 判成不匹配，
        // 于是整份文件合成一段、容器谓词在文件级命中，段落判据退化成文件判据（第一版实跑踩中的坑）。
        if (TopLevelFunDecl.containsMatchIn(line) && current.isNotBlank()) {
            out.add(current)
            current = StringBuilder()
        }
        current.appendLine(line)
    }
    out.add(current)
    return out.map { it.toString() }.filter { it.isNotBlank() }
}

/**
 * 取一段源码里所有 `Text(` 调用的实参文本：从 `(` 起按括号配对走到闭合。
 * 只数括号、不数引号里的括号是已知取舍——本仓 `Text(` 实参里的括号成对出现在表达式内，
 * 且字符串字面量已由 [stripComments] 原样保留，含未闭合括号的文案会让配对提前收尾，
 * 最坏是**漏判一段**（失明方向），故另由合成用例钉住「成对括号内确实读到实参」这一前提。
 */
private fun textCallArguments(segment: String): List<String> {
    val out = mutableListOf<String>()
    var index = 0
    while (true) {
        val hit = Regex("""\bText\(""").find(segment, index) ?: break
        var depth = 0
        var cursor = hit.range.last
        var end = -1
        while (cursor < segment.length) {
            when (segment[cursor]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) { end = cursor; break }
                }
            }
            cursor++
        }
        if (end == -1) break
        out.add(segment.substring(hit.range.last + 1, end))
        index = end
    }
    return out
}

/** 扫描入口：返回「文件: 折叠后的 style 行」条目，口径与 [violations] 一致（剥注释、不带行号）。 */
private fun pairingViolationsIn(text: String, fileName: String): List<String> =
    topLevelSegments(stripComments(text))
        .filter { MigratedNeutralContainer.containsMatchIn(it) }
        .flatMap { segment -> textCallArguments(segment) }
        .filter { AppTypeStyleRead.containsMatchIn(it) && !ExplicitInkArgument.containsMatchIn(it) }
        .map { args ->
            val styleLine = args.lines().first { AppTypeStyleRead.containsMatchIn(it) }
            "$fileName: ${styleLine.trim().replace(Regex("""\s+"""), " ")}"
        }

class ContainerInkPairingContractTest {

    @Test
    fun `a container migrated to appColors() surface never renders an uncolored AppType style`() {
        val offenders = uiFiles().flatMap { pairingViolationsIn(it.readText(), it.name) }
        assertTrue(
            offenders.isEmpty(),
            "容器已迁 `appColors().surface`（随主题换套）却仍画 `AppType` 静态浅套墨：深色档近黑压近黑。" +
                "每处补显式 `color = appColors().text1/text2`（写法见 ui/components/ScreenUnavailable.kt），" +
                "或把容器读点回退成静态 `AppPalette.Surface` 并在同批理由台账登记「与所在容器同批迁」。\n" +
                offenders.sorted().joinToString("\n")
        )
    }

    @Test
    fun `the pairing scan catches a planted violation and passes its colored twin`() {
        val migrated = """
            private fun Card(title: String) {
                Surface(color = appColors().surface) {
                    Text(title, style = AppType.Title)
                }
            }
        """.trimIndent()
        assertEquals(
            listOf("Fake.kt: title, style = AppType.Title"),
            pairingViolationsIn(migrated, "Fake.kt"),
            "命中形状不报 = 本判据空转（段落切分或 Text 实参配对失效）",
        )

        val colored = """
            private fun Card(title: String) {
                Surface(color = appColors().surface) {
                    Text(
                        title,
                        style = AppType.Title,
                        color = appColors().text1,
                    )
                }
            }
        """.trimIndent()
        assertEquals(
            emptyList(),
            pairingViolationsIn(colored, "Fake.kt"),
            "补了显式 color 仍报 = 实参里的换行/缩进形状没被读到，判据会误伤正确写法",
        )
    }

    @Test
    fun `the pairing scan stays blind to segments without a migrated container`() {
        val staticBackdrop = """
            private fun Panel(title: String) {
                Surface(color = AppPalette.GlassFill) {
                    Text(title, style = AppType.Title)
                }
            }
        """.trimIndent()
        assertEquals(
            emptyList(),
            pairingViolationsIn(staticBackdrop, "Fake.kt"),
            "未迁容器（玻璃填充等待迁族）里的静态墨是批 3 的账，本判据把它拉进来说明容器谓词写宽了",
        )
        assertEquals(
            emptyList(),
            pairingViolationsIn(
                "private fun Other(t: String) {\n    Text(t, style = AppType.Body, color = appColors().text1)\n}",
                "Fake.kt",
            ),
        )
        assertTrue(
            MigratedNeutralContainer.containsMatchIn("containerColor = appColors().surface,") &&
                !MigratedNeutralContainer.containsMatchIn("containerColor = appColors().surfaceGlass,"),
            "容器谓词必须区分 surface 与 surfaceGlass：后者是批 3 的玻璃填充族",
        )
    }

    @Test
    fun `the pairing scan guards the real AiProfileRow segment, not a fabricated one`() {
        // 真实文件锚（m-6 的处置）：本判据守的形状必须在**生产源码**里真实存在——
        // AiProfileRow 那一段要同时含「已迁的中性容器读点」与「AppType 排印点」。
        // 早先这一例是把真行拼进一段合成的 `Surface(color = appColors().surface)` 里再喂扫描器，
        // 于是「真文件里这两者到底同不同段」从没被证过：版面一旦改动（容器回退静态、或排印点挪出该段），
        // 第一条全量真文件用例照样绿，本例照样绿，判据却已经对着一个不存在的形状空转。
        val source = uiScreenSource("AiProfileSettingsScreen.kt")
        val rowSegment = topLevelSegments(source).firstOrNull {
            it.contains("fun AiProfileRow") && MigratedNeutralContainer.containsMatchIn(it)
        }
        assertTrue(
            rowSegment != null,
            "生产源码里已不存在「AiProfileRow 段内含 appColors().surface 容器」这一形状：" +
                "容器读点回退成静态 AppPalette.Surface、或该段被拆分重排。" +
                "本判据的锚点必须随之更新（连同类合成片段一起复核，见上一条用例）。",
        )
        val segment = checkNotNull(rowSegment)
        assertTrue(
            segment.contains("item.profile.displayName") && AppTypeStyleRead.containsMatchIn(segment),
            "AiProfileRow 段里的 displayName 排印点不见了（版面重排把本判据盯的那处前景挪走了）：$segment",
        )
        assertEquals(
            emptyList(),
            pairingViolationsIn(source, "AiProfileSettingsScreen.kt"),
            "真实文件里 AiProfileRow 仍画未补色的 AppType 前景 = 深色档浅套静态墨压换套深底（C-1 回归）",
        )
    }
}
