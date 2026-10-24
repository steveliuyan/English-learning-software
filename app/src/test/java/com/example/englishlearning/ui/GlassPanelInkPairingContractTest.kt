package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.DomainDark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 批 2 全分支终审修复席 A（task-18fix）：**玻璃对话框的容器与面板内前景必须同源**。
 *
 * 立论（批 1 终审 Seat A I-1 复算 + 批 2 seatB 评审 C-1 的 2.18:1 深色回归，同一枚债的两面）：
 *  - `GlassDialog` 的容器底读静态浅套 `AppPalette.GlassFill`（`0xEFFFFFFF`），而 `AppColorLight.surfaceGlass`
 *    与它**逐字相同**（`ui/theme/AppColors.kt`）——目标 token 早已备好，浅色侧迁移像素零变化；
 *  - 面板内文字全是 `style = AppType.Headline/Body` 且**不带 `color=`**，墨色由 `ui/theme/AppType.kt`
 *    六档自带（批 1–批 2 时期是浅套静态 `AppPalette.TextPrimary`；批 3 Task 26 起改成组合期读
 *    `appColors().text1/text2`）钉在主题墨上；
 *  - 于是「只迁容器」会把深色面板从「白玻璃 + 深墨（意外可读）」翻成「深玻璃 + 深墨（当场不可读）」
 *    ——容器与前景**必须同批迁**，这正是 `GlassFillBackdropDebtLedger` 里那条理由的落地。
 *
 * 本契约只做四件事（全部读 main 源码文本，口径与 `SourceRatchet.kt` 一致：先剥注释再匹配）：
 *  ① `GlassDialog.kt` 容器：`AppPalette.GlassFill` 读点归零、`color = appColors().surfaceGlass` **体内恰一处
 *     / 体外零处**，且锚点带 `(?![.\w])` 尾巴——`appColors().surfaceGlass.copy(…)` 这类「不动字面量的间接路径」
 *     不算合规（那既是变异 m1 的形状，也是评审点名的绕过写法）；主题无关蒙版描边 `AppPalette.GlassHighlight`
 *     保持恰一处（迁它会让 `ThemeInvariantMaskReadLedger` 漂，且两套主题抄同一个 alpha 没有意义）；
 *     公开签名 `GlassDialog(onDismiss, modifier, content)`、`AppShape.Dialog`、`shadowElevation = 24.dp`、
 *     内层 24.dp 留白逐字不动。
 *  ② 容器可滚：内容 Column 的 modifier 链必须是 `heightIn(max = N.dp) → verticalScroll(rememberScrollState())
 *     → padding(24.dp)` 这一形状（N 取 200..700 之间的实际高度上限，太小=版面坏掉、太大=等于没有上限，
 *     设备字号 2.0 下导出选择器的「取消」因此始终可滚到）。
 *  ③ 花名册 [expectedPanels] 登记的消费面板（枚数以该表现数为准，修复轮 1 F-6① 不留绝对数）：
 *     窗口内每一枚带 `style = AppType.X` 的 `Text(`
 *     必须显式带 `color = appColors().<X 档当前墨>`；期望墨**由现场解析 `AppType.kt` 的六档墨表得出**
 *     （`TextPrimary → text1`、`TextSecondary → text2`），不是本文件拍脑袋写的常量——判据跟着真值走，
 *     六档一旦漂移本契约当场要求复核而不是悄悄错位。
 *  ④ 取样窗口绑死真实调用点：按 `GlassDialog(` 出现位置配平取「实参括号 + 尾随 lambda」整段，
 *     **未闭合即响亮报错**（批 2 seatC1 评审 I-1 的教训：窗口切歪时「窗外零处」这类断言会自己骗自己）；
 *     份数恰等于 [expectedPanels] 现数、每枚按标题字面认出，多一枚/少一枚/换身份都判红。
 *
 * 防空转（与 `AppScreenOverlayContainerContractTest`、`ContainerInkPairingContractTest` 同族写法）：
 * 每条主判据都有合成对拍走**同一条**解析链——合规样片放行、违规样片恰被点名、
 * 「字面量还在但换了间接路径」（`.copy`、默认参数提读、把墨存进局部 val）三类诱饵必须判红。
 *
 * 读法只走共享夹具 [uiScreenSource] / [uiComponentSource]（内置「文件恰一个 + 目录守卫 + 剥注释」），
 * 本文件不自写第二份剥注释实现（批 1 终审 F6）。配平取窗三个小夹具与 17A/17B 的同名私有实现同源：
 * 它们既不是 `stripComments` 也没进共享夹具，批 3 做测试夹具归并时应与那两处一起搬进 `SourceRatchet.kt`
 * （席 B 扩展沿用同一批夹具，不再抄第四份）。
 *
 * **批 2 全分支终审修复席 B（task-18fix-b）的扩面**：判据 ⑤–⑦ 把同一条「容器与面板内前景同源」契约
 * 推到余下三块玻璃面板——`components/glass/GlassOverlay.kt` 的浮层面板、`AiProfileSettingsScreen.kt` 的
 * `SectionCard`、`SpeechSettingsScreen.kt` 的 `PreviewCard`。三块各自钉：容器 `appColors().surfaceGlass`
 * 体内恰一处（体外零处、`.copy` 间接换形不算）、`AppPalette.GlassFill` 归零、主题无关蒙版描边恰一处、
 * 公开签名与几何逐字不动；面板内容（消费者尾随 lambda 窗口 + 面板专属私有排印点函数体，如
 * `VoiceOverlayOption` / `CapabilityToggle` / `EditorField`）逐枚按套取墨，禁止静态墨、裸 hex、
 * 命名色常量与「把墨提读成变量再点用」。曾被单独回退成静态浅套墨的那几行，另用
 * [MigratedPanel.pinnedInkLiterals] 逐字钉死取值形状。取样窗口同样绑生效调用点、未闭合即响亮 fail。
 */
class GlassPanelInkPairingContractTest {

    // ---------- 锚点与台账 ----------

    private val glassDialogFile = "GlassDialog.kt"
    private val glassDialogComponentDir = "/components/glass/"
    private val glassDialogSignature = "fun GlassDialog("
    private val glassCall = "GlassDialog("

    /** 容器锚：字面 `color = appColors().surfaceGlass`，尾部 `(?![.\w])` 灭掉 `.copy` / `let` 之类的间接换形。 */
    private val containerInkAnchor = Regex("""color\s*=\s*appColors\(\)\.surfaceGlass(?![.\w])""")

    /** 静态玻璃填充（待迁债务）与主题不变蒙版描边。 */
    private val glassFillRead = Regex("""AppPalette\.GlassFill\b""")
    private val maskBorderRead = Regex("""AppPalette\.GlassHighlight\b""")
    private val anyStaticRead = Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")

    /** 内容列的滚动兜底形状：上限 → 竖向滚动 → 24.dp 内边距（顺序即语义，边距留在滚动内容里才不吞掉首尾行）。 */
    private val scrollFallbackAnchor =
        Regex("""heightIn\(max = (\d+(?:\.\d+)?)\.dp\)\s*\.verticalScroll\(rememberScrollState\(\)\)\s*\.padding\(24\.dp\)""")

    /** 公开签名本体冻结（修复席只换墨的来源，不换谁来画）。 */
    private val frozenGlassDialogParameters = listOf(
        "onDismiss: () -> Unit",
        "modifier: Modifier = Modifier",
        "content: @Composable () -> Unit",
    )

    /** 消费面板花名册：`file` + 该枚面板的身份字面（标题文案，逐枚唯一）；枚数以 [expectedPanels] 现数为准。 */
    private data class Panel(val file: String, val identity: String)

    private val expectedPanels = listOf(
        Panel("AppScreen.kt", "选择要导出的词书"),
        Panel("AppScreen.kt", "发现已学过的单词"),
        Panel("ArticleReadingScreen.kt", "本文覆盖你已背的"),
        Panel("ImageStudioScreen.kt", "确认发送请求"),
        Panel("PhotoTranslationScreen.kt", "确认发送请求"),
        // Task 24（裁决⑤ 第二件）：`ReadingAccessScreen.kt` 的私有玻璃对话框退役、改调共享
        // `OutboundConfirmDialog`，本屏随玻璃面板族摘一行——共享件的面板由 `DialogPanel` 承重，
        // 不属本契约的 `GlassDialog(` 取样族。重算：
        // `grep -rn 'GlassDialog(' app/src/main/java/com/example/englishlearning/ui/ReadingAccessScreen.kt`（应为零命中）。
        Panel("SentenceAnalysisScreen.kt", "确认发送请求"),
        Panel("WordAiQaScreen.kt", "确认发送请求"),
    )

    private data class Window(val file: String, val region: String)

    // ---------- 配平取窗夹具（与 17A/17B 同名私有实现同源；字符串感知、未闭合即 fail） ----------

    private fun occurrencesOf(hay: String, needle: String): List<Int> {
        val out = mutableListOf<Int>()
        var index = hay.indexOf(needle)
        while (index >= 0) {
            out += index
            index = hay.indexOf(needle, index + needle.length)
        }
        return out
    }

    private fun balancedRegion(text: String, open: Int, openChar: Char, closeChar: Char, site: String): Pair<Int, Int> {
        assertTrue(text[open] == openChar, "$site：取窗起点不是 $openChar，配对器不接受非 $openChar 下标")
        var depth = 0
        var i = open
        var inString = false
        var escaped = false
        while (i < text.length) {
            val c = text[i]
            when {
                inString -> when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }

                c == '"' -> inString = true
                c == openChar -> depth++
                c == closeChar -> {
                    depth--
                    if (depth == 0) return Pair(open, i)
                }
            }
            i++
        }
        fail("$site：从下标 $open 起的 $openChar…$closeChar 未闭合——窗口切歪会把窗外文字算进窗内，本契约拒绝猜测")
    }

    /** 一枚调用的完整区间：实参括号配平 + 紧随其后的尾随 lambda 块（`GlassDialog(…) { 面板内容 }`）。 */
    private fun extractCallRegion(stripped: String, callStart: Int, anchor: String, site: String): String {
        val paren = callStart + anchor.length - 1
        assertTrue(
            stripped.getOrNull(paren) == '(',
            "$site：$anchor 之后指位不是左括号，调用形状与本夹具口径不符，拒绝猜测",
        )
        val args = balancedRegion(stripped, paren, '(', ')', "$site 实参括号")
        var end = args.second + 1
        val nextBrace = firstNonWhitespace(stripped, end)
        if (nextBrace != null && stripped[nextBrace] == '{') {
            end = balancedRegion(stripped, nextBrace, '{', '}', "$site 尾随 lambda").second + 1
        }
        return stripped.substring(callStart, end)
    }

    private fun firstNonWhitespace(text: String, from: Int): Int? {
        var i = from
        while (i < text.length) {
            if (!text[i].isWhitespace()) return i
            i++
        }
        return null
    }

    /** 函数体（不含最外层花括号）：用于「体内恰一处 / 体外零处」夹击。 */
    private fun functionBody(stripped: String, signature: String, site: String): String {
        val start = stripped.indexOf(signature)
        assertTrue(start >= 0, "$site：找不到函数签名 \"$signature\"，锚点搬家或改名，契约不许空转")
        val open = stripped.indexOf('{', start)
        assertTrue(open >= 0, "$site：$signature 之后找不到函数体起始花括号")
        val region = balancedRegion(stripped, open, '{', '}', "$site 函数体")
        return stripped.substring(region.first + 1, region.second)
    }

    /** 签名实参括号窗口 → 逐枚形参名单（本仓签名带尾逗号，尾逗号不许成枚）。 */
    private fun splitParameters(signature: String): List<String> =
        signature.removePrefix("(").removeSuffix(")").split(',').map { it.trim() }.filter { it.isNotEmpty() }

    // ---------- 排印档 → 主题墨档（真值来自 AppType.kt 现场解析，不是本文件写死的常量） ----------

    /**
     * 解析 `ui/theme/AppType.kt` 六档的**主题墨对位**，折算成面板内应显式补的 `AppColors` 成员名：
     * `text1 → "text1"`、`text2 → "text2"`。批 3 Task 26 起六档不再是浅套静态墨，
     * 而是逐档组合期读 `appColors().<字段>`；本表因此按**同一形状**（`<Tier>Spec.copy(color = appColors().X)`）
     * 现场解析，仍是「判据跟着真值走」而不是本文件拍脑袋写的常量。
     *
     * 六档缺任何一档即判红：解析链坏掉（改名/换写法）时必须响亮报错，而不是让下面那条配对判据
     * 对着空表「无一枚需要补色」地假绿。玻璃面板内仍要求逐枚显式 `color =`——那是房内写法（R-22.3），
     * 与本契约要挡的「深色面板 + 未显式配墨」是两件事：`AppType` 的主题墨不会跟着玻璃容器走，
     * 面板前景必须自己钉住。
     */
    private fun appTypeInkTable(): Map<String, String> {
        val source = uiComponentSource("AppType.kt", "/ui/theme/")
        val tierWindow = { tier: String ->
            Regex(
                """val $tier: TextStyle\s*\n\s*@Composable\s*\n\s*@ReadOnlyComposable\s*\n""" +
                    """\s*get\(\) = ${tier}Spec\.copy\(color = appColors\(\)\.(text1|text2)\)"""
            )
        }
        val found = listOf("Display", "Headline", "Title", "Body", "Footnote", "Label").associateWith { tier ->
            val hit = tierWindow(tier).find(source)
                ?: error("AppType.$tier 的墨不再是组合期 appColors().text1/text2：本契约的取样前提已失效，需复核")
            hit.groupValues[1]
        }
        assertEquals(
            setOf("Display", "Headline", "Title", "Body", "Footnote", "Label"),
            found.keys,
            "AppType 六档没能解析齐（改名/换写法会让本判据对着不完整的墨表空转）：实测 ${found.keys.sorted()}",
        )
        return found
    }

    // ---------- 判据 ①②：容器与滚动兜底 ----------

    /** 纯函数：对一份 `GlassDialog` 源码返回 offender（空 = 合规）。真文件与合成样片走同一条。 */
    private fun containerOffenders(source: String, label: String): List<String> {
        val body = functionBody(source, glassDialogSignature, label)
        val offenders = mutableListOf<String>()
        if (countMatches(source, glassFillRead) != 0) {
            offenders += "$label：仍有 ${countMatches(source, glassFillRead)} 处 AppPalette.GlassFill 静态玻璃填充读点"
        }
        val inBody = containerInkAnchor.findAll(body).count()
        val inFile = containerInkAnchor.findAll(source).count()
        if (inBody != 1) offenders += "$label：容器锚 color = appColors().surfaceGlass（不带任何间接换形）体内应恰一处，实测 $inBody"
        if (inFile != inBody) offenders += "$label：容器锚有 ${inFile - inBody} 处落在函数体之外（窗外副本 = 死分支/幽灵容器）"
        val staticReads = anyStaticRead.findAll(body).map { it.value }.toList()
        val maskOnly = staticReads.filterNot { it == "AppPalette.GlassHighlight" }
        if (maskOnly.isNotEmpty()) offenders += "$label：体内出现除蒙版描边以外的静态读点 $maskOnly"
        if (countMatches(source, maskBorderRead) != 1) {
            offenders += "$label：主题无关蒙版描边 AppPalette.GlassHighlight 应恰一处（实测 ${countMatches(source, maskBorderRead)}，迁走或多出都会让蒙版台账漂）"
        }
        val signature = splitParameters(
            balancedRegion(source, source.indexOf(glassDialogSignature) + glassDialogSignature.length - 1, '(', ')', "$label 签名")
                .let { (start, end) -> source.substring(start, end + 1) },
        )
        if (signature != frozenGlassDialogParameters) {
            offenders += "$label：公开签名必须逐字保持 $frozenGlassDialogParameters，实测 $signature"
        }
        if (countMatches(body, Regex("""shape = AppShape\.Dialog""")) != 1) offenders += "$label：AppShape.Dialog 形状读点应恰一处"
        if (countMatches(body, Regex("""shadowElevation = 24\.dp""")) != 1) offenders += "$label：24.dp 投影几何应恰一处"
        if (countMatches(body, Regex("""padding\(horizontal = 24\.dp\)""")) != 1) offenders += "$label：外层 Box 的 24.dp 水平留白应恰一处"
        val chain = scrollFallbackAnchor.findAll(body).map { it.groupValues[1] }.toList()
        if (chain.size != 1) {
            offenders += "$label：内容列必须恰一条「heightIn(max = N.dp) → verticalScroll(rememberScrollState()) → padding(24.dp)」兜底链，实测 ${chain.size} 条"
        } else {
            val cap = chain.single().toDoubleOrNull()
            if (cap == null || cap < 200.0 || cap > 700.0) {
                offenders += "$label：高度上限取 ${chain.single()}.dp 不合理（200..700 之外＝版面坏掉或等于没有上限）"
            }
        }
        return offenders
    }

    @Test
    fun `glass dialog container takes its fill from the theme set and its content stays scrollable`() {
        val source = uiComponentSource(glassDialogFile, glassDialogComponentDir)
        assertTrue(source.isNotBlank(), "$glassDialogFile 剥注释后为空：契约不许对空文件判绿")
        val offenders = containerOffenders(source, "GlassDialog.kt")
        assertTrue(offenders.isEmpty(), "玻璃对话框容器未与主题档同源/兜底缺失：\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `the container scan rejects a copy indirected fill and accepts the theme bound twin`() {
        val compliant = """
            fun GlassDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
                Dialog {
                    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Surface(shape = AppShape.Dialog, color = appColors().surfaceGlass, border = BorderStroke(1.dp, AppPalette.GlassHighlight), shadowElevation = 24.dp) {
                            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(24.dp)) { content() }
                        }
                    }
                }
            }
        """.trimIndent()
        assertEquals(emptyList(), containerOffenders(compliant, "合规样片"), "反空转失控：合规样片被真判据误伤（判据恒假，主用例随之全是假红）")

        val copyDecoy = compliant.replace(
            "color = appColors().surfaceGlass",
            "color = appColors().surfaceGlass.copy(alpha = 0.9f)",
        )
        assertTrue(
            containerOffenders(copyDecoy, "变异样片").isNotEmpty(),
            "反空转失控：字面量原封不动、只加一层 .copy 的间接换形没被判出——变异 m1 没有牙齿",
        )
        val maskDecoy = compliant.replace("AppPalette.GlassHighlight", "appColors().outlineSoft")
        assertTrue(
            containerOffenders(maskDecoy, "蒙版样片").isNotEmpty(),
            "反空转失控：蒙版描边被主题化没被判出（那会让 ThemeInvariantMaskReadLedger 漂）",
        )
        val noScrollDecoy = compliant.replace(
            "Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(24.dp)",
            "Modifier.padding(24.dp)",
        )
        assertTrue(
            containerOffenders(noScrollDecoy, "无兜底样片").isNotEmpty(),
            "反空转失控：去掉滚动兜底链没被判出（长内容下的取消键可达性就没有承重的判据）",
        )
        val hoistedDecoy = compliant.replace(
            "color = appColors().surfaceGlass",
            "color = glassFill,",
        ).replace("fun GlassDialog(", "fun GlassDialog(glassFill: Color = appColors().surfaceGlass, ")
        assertTrue(
            containerOffenders(hoistedDecoy, "提读样片").isNotEmpty(),
            "反空转失控：把主题墨提到默认参数、容器只读一个变量名的形状没被判出（评审点名的默认参数提读绕过路径）",
        )
    }

    // ---------- 判据 ③④：花名册内逐枚面板前景同源（枚数见 [expectedPanels]） ----------

    /** 按调用点取全部玻璃面板窗口（未闭合即 fail，见 [balancedRegion]）。 */
    private fun windowsOf(file: String, source: String): List<Window> =
        occurrencesOf(source, glassCall).map { start ->
            Window(file, extractCallRegion(source, start, glassCall, "$file 下标 $start"))
        }

    /**
     * 纯函数：一枚面板窗口的 offender（空 = 合规）。
     *  - 窗口内每一枚 `Text(` 只要写了 `style = AppType.X`，就必须逐字带 `color = appColors().<X 档墨>`
     *    （带 `(?![.\w])` 尾巴：`appColors().text1.copy(…)`、`colors.text1` 别名提读都不算合规）；
     *  - 窗口内不许出现任何静态墨读点（`AppPalette.` / `DomainColors.` / 裸 hex），
     *    也不许把墨存进局部 `val` 再点用（默认参数/局部变量提读正是评审点名的间接路径）。
     */
    private fun panelOffenders(panel: Panel, window: String, inkTable: Map<String, String>): List<String> {
        val offenders = mutableListOf<String>()
        val site = "${panel.file}「${panel.identity}」"
        val staticReads = anyStaticRead.findAll(window).map { it.value }.toList()
        if (staticReads.isNotEmpty()) offenders += "$site：窗口内残留静态墨读点 $staticReads（容器迁了、前景留浅套 = 深色档近黑压近黑）"
        if (Regex("""Color\(0x""").containsMatchIn(window)) offenders += "$site：窗口内出现裸 hex 构造"
        occurrencesOf(window, "AppType.").forEach { hit ->
            val styleName = window.substring(hit + "AppType.".length).takeWhile { it.isLetterOrDigit() || it == '_' }
            if (styleName !in inkTable) return@forEach
            val args = textCallArgumentsOf(window, hit)
                ?: run {
                    offenders += "$site：AppType.$styleName 读点找不到所属 Text( 调用（取样窗口形状不合口径）"
                    return@forEach
                }
            if (!Regex("""style\s*=\s*AppType\.$styleName\b""").containsMatchIn(args)) {
                offenders += "$site：AppType.$styleName 读点不在任何 Text 的实参里（取样窗口切歪）"
                return@forEach
            }
            val expected = inkTable.getValue(styleName)
            val bound = Regex("""(?<![A-Za-z])color\s*=\s*appColors\(\)\.$expected(?![.\w])""")
            if (!bound.containsMatchIn(args)) {
                val hasAnyColor = Regex("""(?<![A-Za-z])color\s*=""").containsMatchIn(args)
                offenders += "$site：Text(style = AppType.$styleName) 的墨没绑到调用点——" +
                    if (hasAnyColor) "有 color= 但取值不是 appColors().$expected（别名/提读/间接换形都不算）" else "缺显式 color=，仍吃 AppType 的浅套静态墨"
            }
        }
        return offenders
    }

    /**
     * 从 `AppType.` 命中点回溯定位其所属 `Text(` 调用，返回实参文本（含两端括号）；
     * 找不到合法调用（或命中点根本不在该调用区间内）返回 null，由调用方判红——
     * 窗口切歪时宁可报错，不许静默给一枚截断窗口。
     */
    private fun textCallArgumentsOf(window: String, appTypeHit: Int): String? {
        val call = occurrencesOf(window, "Text")
            .filter { it < appTypeHit && window.indexOf('(', it) == it + 4 }
            .filter { before ->
                val prefix = if (before == 0) ' ' else window[before - 1]
                !prefix.isLetterOrDigit() && prefix != '_'
            }
            .lastOrNull() ?: return null
        val region = extractCallRegion(window, call, "Text(", "Text 实参回溯")
        return if (appTypeHit <= call + region.length) region else null
    }

    @Test
    fun `every glass panel foreground is bound to the same theme ink as its container`() {
        val inkTable = appTypeInkTable()
        val sources = expectedPanels.map { it.file }.distinct().associateWith { file ->
            uiScreenSource(file).also {
                assertTrue(it.isNotBlank(), "$file 剥注释后为空：契约不许对空文件判绿")
            }
        }
        val windows = sources.flatMap { (file, source) -> windowsOf(file, source) }
        assertEquals(
            expectedPanels.size,
            windows.size,
            "玻璃面板取样窗口必须恰 ${expectedPanels.size} 枚（实测 ${windows.size}）：少一枚 = 有面板没进契约，多一枚 = 新增了未登记的玻璃面板",
        )
        val offenders = mutableListOf<String>()
        expectedPanels.forEach { panel ->
            val matched = windows.filter { it.file == panel.file && it.region.contains(panel.identity) }
            if (matched.size != 1) {
                offenders += "${panel.file}：身份字面「${panel.identity}」必须恰命中一枚窗口（实测 ${matched.size}）——窗口绑不回真实调用点，判据就是空转"
                return@forEach
            }
            offenders += panelOffenders(panel, matched.single().region, inkTable)
        }
        // 无名窗口也算违例：份数相等时若某枚窗口不含任何已登记身份，上面只登记了 8 枚身份而窗口仍有一枚没人认领
        val claimed = expectedPanels.map { it.file to it.identity }
        windows.forEach { window ->
            if (claimed.none { (file, identity) -> file == window.file && window.region.contains(identity) }) {
                offenders += "${window.file}：一枚 GlassDialog 窗口不含任何已登记身份字面（新增面板必须先登记再迁墨）"
            }
        }
        assertTrue(offenders.isEmpty(), "玻璃面板内前景未与容器同批迁主题档：\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `the panel scan rejects an uncolored style and a hoisted ink, and accepts the theme bound twin`() {
        val inkTable = appTypeInkTable()
        val panel = Panel("FakeScreen.kt", "确认发送请求")
        val compliant = """
            GlassDialog(onDismiss = { close() }) {
                Text("确认发送请求", style = AppType.Headline, color = appColors().text1)
                Text(message, style = AppType.Body, color = appColors().text1)
                PillButton(text = "取消", onClick = { close() }, style = PillStyle.Secondary, domain = AppDomain.Learn)
            }
        """.trimIndent()
        assertEquals(emptyList(), panelOffenders(panel, compliant, inkTable), "反空转失控：合规样片被真判据误伤")

        val uncolored = compliant.replace(", color = appColors().text1)", ")")
        assertTrue(
            panelOffenders(panel, uncolored, inkTable).isNotEmpty(),
            "反空转失控：补色被抹掉（回到 AppType 静态浅套墨）没被判出",
        )
        val hoisted = compliant.replace("color = appColors().text1", "color = ink")
        assertTrue(
            panelOffenders(panel, hoisted, inkTable).isNotEmpty(),
            "反空转失控：把主题墨提读成变量再点用（默认参数/局部 val 提读那一族）没被判出",
        )
        val indirected = compliant.replace("appColors().text1", "appColors().text1.copy(alpha = 1f)")
        assertTrue(
            panelOffenders(panel, indirected, inkTable).isNotEmpty(),
            "反空转失控：字面量原封不动只加一层 .copy 的间接换形没被判出（变异 m1 的消费侧同族形状）",
        )
        val staticMoved = compliant.replace("appColors().text1", "AppPalette.TextPrimary")
        assertTrue(
            panelOffenders(panel, staticMoved, inkTable).isNotEmpty(),
            "反空转失控：窗口内残留静态浅套墨没被判出",
        )
        val secondaryTwin = """
            GlassDialog(onDismiss = { close() }) {
                Text("说明", style = AppType.Footnote, color = appColors().text2)
            }
        """.trimIndent()
        assertEquals(
            emptyList(),
            panelOffenders(Panel("FakeScreen.kt", "说明"), secondaryTwin, inkTable),
            "反空转失控：次级档按真值表配 text2 的写法被误伤（判据把 text1 当成唯一答案）",
        )
    }

    @Test
    fun `an unclosed glass dialog region fails loudly instead of scanning a truncated window`() {
        val truncated = "GlassDialog(onDismiss = {}) { Text(\"确认发送请求\", style = AppType.Headline, color = appColors().text1\n"
        assertFailsWith<AssertionError>(
            "未闭合的调用区间必须响亮报错：静默给一枚截断窗口，「窗外零处」那类断言就会自己骗自己（批 2 seatC1 评审 I-1）",
        ) {
            windowsOf("TruncatedScreen.kt", truncated)
        }
        val closed = "GlassDialog(onDismiss = {}) { Text(\"确认发送请求\", style = AppType.Headline, color = appColors().text1) }\n"
        assertEquals(1, windowsOf("ClosedScreen.kt", closed).size, "闭合样片取不到窗口：配平夹具本身坏了，上面的响亮报错也无从谈起")
    }

    // ---------- 判据 ⑤–⑦：批 2 终审修复席 B 的三块玻璃面板 ----------

    /**
     * 席 B 面板契约描述。字段口径：
     *  - [containerSignature] 定位容器函数体（`appColors().surfaceGlass` 必须写在这个体内恰一处）；
     *  - [frozenAnchors] 是本席**只换墨、不改版面**的几何冻结（正则 + 期望处数）；
     *  - [callAnchor] + [consumerFile] + [consumerIdentities] 描述面板内容在消费者侧的调用点与份数
     *    （`null` = 内容内联在容器体内，如 `PreviewCard`）；
     *  - [helperSignatures] 是「内容被拆进同文件私有 Composable」的那几枚（`VoiceOverlayOption` 等），
     *    它们的体必须折进面板窗口，否则契约只看得到调用点、看不见真正画字的行（席 A 的八枚面板没有这一族，
     *    本席三块面板全有）；
     *  - [pinnedInkLiterals] 逐字钉曾被 Task 12.5 单独回退的那几行取墨形状（条件式墨无法用「字面 = appColors().textX」
     *    一条覆盖，故按整行字面钉，改动即红并说明要同步本契约）。
     */
    private data class MigratedPanel(
        val label: String,
        val file: String,
        val dirFragment: String?,
        val containerSignature: String,
        val frozenParameters: List<String>,
        val frozenAnchors: List<Pair<Regex, Int>>,
        val callAnchor: String?,
        val consumerFile: String?,
        val consumerIdentities: List<String>,
        val helperSignatures: List<String>,
        val pinnedInkLiterals: List<String>,
    )

    private val migratedPanels = listOf(
        MigratedPanel(
            label = "GlassOverlay 浮层面板",
            file = "GlassOverlay.kt",
            dirFragment = glassDialogComponentDir,
            containerSignature = "fun BoxScope.GlassOverlay(",
            frozenParameters = listOf(
                "visible: Boolean",
                "onDismiss: () -> Unit",
                "modifier: Modifier = Modifier",
                "content: @Composable ColumnScope.() -> Unit",
            ),
            frozenAnchors = listOf(
                Regex("""shape = AppShape\.Dialog""") to 1,
                Regex("""shadowElevation = 16\.dp""") to 1,
                Regex("""padding\(horizontal = 32\.dp\)""") to 1,
                Regex("""background\(AppPalette\.Scrim\)""") to 1,
                Regex("""Column\(Modifier\.padding\(vertical = 8\.dp\), content = content\)""") to 1,
            ),
            callAnchor = "GlassOverlay",
            consumerFile = "AiProfileSettingsScreen.kt",
            consumerIdentities = listOf("ai_profile_voice_auto"),
            helperSignatures = listOf("private fun VoiceOverlayOption("),
            pinnedInkLiterals = listOf(
                // 批 2 终审修复席 C/C-1（控制者裁决，B 席 concerns ①）：本行 `style = AppType.Body` 属正文档，
                // 阈值就是 4.5，不许按装饰档 3.1 放行；选中档前景按 `AppBottomBar.kt:95` 的形状**按套钉死**：
                // 浅套取 `appColors().onMint`（#174C44 压浅玻璃 #FEFFFE 现算 9.727:1），深套沿用 `deep`
                // （#35E0AC 压深玻璃 #161F1B ≥4.5，由取样类深套腿当场钉），未选中两套同 `text1`。
                // 现算与反空转见 [the_selected_voice_row_ink_clears_the_body_aa_gate_on_both_sets]。
                "color = if (selected && !LocalIsDark.current) appColors().onMint else if (selected) appDomain(AppDomain.AiSpeech).deep else appColors().text1",
            ),
        ),
        MigratedPanel(
            label = "AI 配置卡 SectionCard",
            file = "AiProfileSettingsScreen.kt",
            dirFragment = null,
            containerSignature = "private fun SectionCard(",
            frozenParameters = listOf("content: @Composable ColumnScope.() -> Unit"),
            frozenAnchors = listOf(
                Regex("""shape = AppShape\.Card""") to 1,
                Regex("""Modifier\.fillMaxWidth\(\)""") to 1,
                Regex("""Modifier\.padding\(16\.dp\)""") to 1,
                Regex("""verticalArrangement = Arrangement\.spacedBy\(10\.dp\)""") to 1,
            ),
            callAnchor = "SectionCard",
            consumerFile = "AiProfileSettingsScreen.kt",
            consumerIdentities = listOf("名称", "能力", "语音角色", "高级参数", "密钥"),
            helperSignatures = listOf("private fun CapabilityToggle(", "private fun EditorField("),
            pinnedInkLiterals = listOf(
                "color = if (!LocalIsDark.current) appColors().onMint else appDomain(AppDomain.AiSpeech).deep",
                "val background = if (selected) appDomain(AppDomain.AiSpeech).base.copy(alpha = 0.12f) else appColors().surface",
                "val border = if (selected) appDomain(AppDomain.AiSpeech).base else appColors().outlineSoft",
            ),
        ),
        MigratedPanel(
            label = "语音试听卡 PreviewCard",
            file = "SpeechSettingsScreen.kt",
            dirFragment = null,
            containerSignature = "private fun PreviewCard(",
            frozenParameters = listOf("state: SpeechSettingsUiState", "onPreview: (String) -> Unit"),
            frozenAnchors = listOf(
                Regex("""shape = AppShape\.Card""") to 1,
                Regex("""Modifier\.fillMaxWidth\(\)""") to 2,
                Regex("""Modifier\.padding\(16\.dp\)""") to 1,
                Regex("""verticalArrangement = Arrangement\.spacedBy\(10\.dp\)""") to 1,
                Regex("""Hello! 你好，世界！""") to 1,
            ),
            callAnchor = null,
            consumerFile = null,
            consumerIdentities = emptyList(),
            helperSignatures = emptyList(),
            pinnedInkLiterals = listOf(
                "focusedContainerColor = appColors().surface",
                "unfocusedContainerColor = appColors().surface",
                "unfocusedIndicatorColor = appDomain(AppDomain.AiSpeech).base.copy(alpha = 0.3f)",
            ),
        ),
    )

    /**
     * 批 2 终审修复席 C（控制者裁决，B 席 concerns ①）：`AiProfileSettingsScreen.kt` 的
     * 音色浮层选中行（`VoiceOverlayOption`，`style = AppType.Body`）选中档前景改取 `appColors().onMint`
     * （取值形状照 `AppBottomBar.kt:95` 按套钉死：浅套 `#174C44` 压浅玻璃、深套 `deep` `#35E0AC` 压深玻璃），未选中档两套同 `text1`。
     * 本用例把**正文档 ≥4.5** 这条线当场算给它看：
     *
     *  - 底取**该行的真实落底**：`surfaceGlass` 带 alpha，按 src-over 压在该套 `background` 上
     *    （浅套合成 ≈ `#FEFFFE`、深套合成 ≈ `#161F1B`），不是拿半透明 token 的字节直接算；
     *  - 两套 × 选中/未选中四对墨全部 ≥ 4.5；
     *  - 反空转（评审红线「不许为过判据放宽阈值」）：把**修复前那枚域色墨**（本席现算 4.006:1）与
     *    一枚 3.6 档诱饵墨 `#1E987C`（本席现算 3.589:1）喂进同一条 4.5 闸，两枚都必须不合格——
     *    它们若被判 ≥4.5，说明本条把阈值写宽了；它们若被判 <3.1，说明本行压根该按装饰档走，
     *    两种情况这条用例都会红，逼下一次改动来复评裁决本身。
     */
    @Test
    fun the_selected_voice_row_ink_clears_the_body_aa_gate_on_both_sets() {
        val bodyAa = 4.5
        val profile = uiScreenSource("AiProfileSettingsScreen.kt")
        val option = functionBody(profile, "private fun VoiceOverlayOption(", "音色浮层选中行")
        assertTrue(
            "style = AppType.Body" in option,
            "音色浮层选项行必须仍是 `AppType.Body`（正文档）：本条 4.5 闸的立论就是「这一行是正文」；" +
                "若它被降成装饰档，得先改控制者裁决再改本判据，不许就地换阈值（实测体：$option）",
        )
        assertTrue(
            "color = if (selected && !LocalIsDark.current) appColors().onMint else if (selected) appDomain(AppDomain.AiSpeech).deep else appColors().text1" in option,
            "选中档前景必须按 `AppBottomBar.kt:95` 的形状按套钉死（浅套 `appColors().onMint`、深套沿用 `deep`）：" +
                "修复前的浅套域色墨 `#188F76` 压浅玻璃只有 4.006:1，不达正文 AA 4.5，" +
                "按装饰档 3.1 放行即「为过判据放宽阈值」（实测体：$option）",
        )

        val lightGlass = glassBackdrop(AppColorLight.surfaceGlass, AppColorLight.background)
        val darkGlass = glassBackdrop(AppColorDark.surfaceGlass, AppColorDark.background)
        assertTrue(
            lightGlass != darkGlass,
            "两套玻璃合成底必须真的不同值，否则本用例的换套判据没有对象（浅 #${hexOf(lightGlass)}）",
        )
        listOf(
            "浅套选中行 onMint 压浅玻璃" to rgbOf(AppColorLight.onMint) to lightGlass,
            "浅套未选中行 text1 压浅玻璃" to rgbOf(AppColorLight.text1) to lightGlass,
            "深套选中行沿用域色 deep 压深玻璃" to rgbOf(DomainDark.getValue(AppDomain.AiSpeech).deep) to darkGlass,
            "深套未选中行 text1 压深玻璃" to rgbOf(AppColorDark.text1) to darkGlass,
        ).forEach { (inkPair, backdrop) ->
            val (label, ink) = inkPair
            val measured = ratio(ink, backdrop)
            assertTrue(
                measured >= bodyAa,
                "$label：#${hexOf(ink)} 压 #${hexOf(backdrop)} WCAG 实测 ${fmt(measured)}:1，未达正文 AA $bodyAa" +
                    "（正文档下限，本席不许放宽；达不到就换合格墨或改容器档）",
            )
        }

        listOf(
            "修复前那枚浅套域色墨（AiSpeech.deep）" to rgbOf(DomainColors.AiSpeech.deep),
            "3.6 档诱饵墨 #1E987C" to (0xFF1E987C.toInt() and 0xFFFFFF),
        ).forEach { (label, ink) ->
            val measured = ratio(ink, lightGlass)
            assertTrue(
                measured < bodyAa,
                "$label 压浅玻璃 #${hexOf(lightGlass)} 实测 ${fmt(measured)}:1——本条要求它**低于**正文阈值 $bodyAa" +
                    "（反空转：它若 ≥$bodyAa 说明 4.5 闸对这行没有对象；若连装饰档 3.1 都不到，" +
                    "说明这行的档位归属需要重新裁决）",
            )
            assertTrue(
                measured >= 3.1,
                "$label 实测 ${fmt(measured)}:1 也不得低于装饰档 3.1：诱饵墨应当**正好落在**「装饰过、正文不过」这一档，" +
                    "否则它证明不了本条判据卡的是 4.5 而不是 3.1",
            )
        }
    }

    /** `Color` → 不透明 RGB（丢掉 alpha 字节，与 [ratio] 的入参口径一致）。 */
    private fun rgbOf(color: Color): Int = color.toArgb() and 0xFFFFFF

    /** 半透明玻璃 `glass` src-over 压在 `under` 上的合成实底（逐通道，四舍五入）。 */
    private fun glassBackdrop(glass: Color, under: Color): Int {
        val a = (glass.toArgb() ushr 24) and 0xFF
        val top = rgbOf(glass)
        val bottom = rgbOf(under)
        fun channel(shift: Int): Int =
            (a * ((top shr shift) and 0xFF) + (255 - a) * ((bottom shr shift) and 0xFF) + 127) / 255
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun hexOf(rgb: Int): String = String.format("%06X", rgb)

    private fun fmt(value: Double): String = String.format("%.2f", value)

    /** WCAG 相对亮度（sRGB 逐通道线性化；入参为不透明 RGB，与 [rgbOf] 同口径）。 */
    private fun luminance(rgb: Int): Double {
        fun lin(channel: Int): Double {
            val s = (channel and 0xFF) / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(rgb shr 16) + 0.7152 * lin(rgb shr 8) + 0.0722 * lin(rgb)
    }

    /** WCAG 对比度现算（席 C 起草本用例时引用而未落定义，C-1 席补齐，公式与取样类逐字同档）。 */
    private fun ratio(ink: Int, backdrop: Int): Double {
        val hi = maxOf(luminance(ink), luminance(backdrop))
        val lo = minOf(luminance(ink), luminance(backdrop))
        return (hi + 0.05) / (lo + 0.05)
    }

    /** 命名色常量（`Color.White` 那一族浅套硬编码）与裸 hex 构造：面板窗口内两者都不许出现。 */
    private val namedStaticColorRead = Regex("""\bColor\.(?:White|Black|Gray|Grey|Red|Green|Blue|Yellow)\b""")
    private val bareHexRead = Regex("""Color\(0x""")

    /** 主题取墨的合法起点：`appColors().x` 或 `appDomain(...).base|deep`，且不许紧跟 `.copy` 之类的间接换形。 */
    private val themeInkRead = Regex(
        """^(?:if\s*\([^()]*\)\s*)?(?:appColors\(\)\.[A-Za-z0-9_]+|appDomain\([A-Za-z0-9_. ]*\)\.(?:base|deep))(?![.\w])""",
    )

    private fun migratedPanelSource(panel: MigratedPanel): String =
        if (panel.dirFragment == null) uiScreenSource(panel.file) else uiComponentSource(panel.file, panel.dirFragment)

    private fun skipWhitespaceOrFail(text: String, from: Int, site: String): Int =
        firstNonWhitespace(text, from) ?: fail("$site：调用点之后文本直接结束，取不到面板窗口（未闭合或取样切歪）")

    /**
     * 形参左括号下标：夹具登记的 `containerSignature` 以 `(` 收尾（本席三块面板与席 A 同口径），
     * 故取签名末尾那枚 `(`；签名不含 `(` 时退回到「签名之后第一枚非空白必须是 `(`」。
     * 两种形状都不匹配即响亮失败——配对器不许猜。
     */
    private fun paramParenOf(source: String, signature: String, site: String): Int {
        val start = source.indexOf(signature)
        assertTrue(start >= 0, "$site：找不到容器签名 \"$signature\"，锚点搬家或改名，契约不许空转")
        if (signature.endsWith("(")) return start + signature.length - 1
        val next = skipWhitespaceOrFail(source, start + signature.length, "$site 签名")
        assertTrue(source[next] == '(', "$site：签名之后第一枚字符不是 (（实测 '${source[next]}'），容器形状与本夹具口径不符")
        return next
    }

    /** 调用点取窗：`Foo(…) {…}` 与 `Foo {…}` 两种形状都吃；配平失败由 [balancedRegion] 响亮 fail。 */
    private fun regionOfCall(text: String, start: Int, name: String, site: String): String {
        val after = start + name.length
        val head = text.getOrNull(after)
        assertTrue(
            head == null || head == '(' || head == '{' || head.isWhitespace(),
            "$site：$name 之后不是 ( 也不是 {，调用形状与本夹具口径不符，拒绝猜测",
        )
        var cursor = skipWhitespaceOrFail(text, after, site)
        var end = cursor
        if (text[cursor] == '(') {
            val args = balancedRegion(text, cursor, '(', ')', "$site 实参括号")
            end = args.second + 1
            cursor = firstNonWhitespace(text, end) ?: return text.substring(start, end)
        }
        if (text.getOrNull(cursor) == '{') {
            end = balancedRegion(text, cursor, '{', '}', "$site 尾随 lambda").second + 1
        }
        return text.substring(start, end)
    }

    /**
     * 定义点（`fun SectionCard(`）与限定引用（`import …glass.GlassOverlay`）都不算消费窗口：
     * 前者由 `endsWith("fun")` 排除，后者由前导 `.` 排除——只留真正的调用点。
     */
    private fun callSites(source: String, name: String): List<Int> = occurrencesOf(source, name).filter { index ->
        val before = if (index == 0) ' ' else source[index - 1]
        !before.isLetterOrDigit() && before != '_' && before != '.' &&
            !source.substring(0, index).trimEnd().endsWith("fun")
    }

    private fun migratedWindows(panel: MigratedPanel, containerSource: String, consumerSource: String?): List<String> {
        val regions = mutableListOf(functionBody(containerSource, panel.containerSignature, panel.label))
        val anchor = panel.callAnchor
        if (anchor != null && consumerSource != null) {
            callSites(consumerSource, anchor).forEach { start ->
                regions += regionOfCall(consumerSource, start, anchor, "${panel.label} 消费窗口")
            }
            panel.helperSignatures.forEach { signature ->
                regions += functionBody(consumerSource, signature, "${panel.label} 内 $signature")
            }
        }
        return regions
    }

    /** 容器判据（纯函数，真文件与合成样片走同一条）：填充归零、容器锚体内恰一处、蒙版恰一处、签名与几何冻结。 */
    private fun migratedContainerOffenders(panel: MigratedPanel, source: String, label: String): List<String> {
        val offenders = mutableListOf<String>()
        val body = functionBody(source, panel.containerSignature, label)
        if (countMatches(source, glassFillRead) != 0) {
            offenders += "$label：仍有 ${countMatches(source, glassFillRead)} 处 AppPalette.GlassFill 静态玻璃填充读点"
        }
        val inBody = containerInkAnchor.findAll(body).count()
        val inFile = containerInkAnchor.findAll(source).count()
        if (inBody != 1) offenders += "$label：容器锚 color = appColors().surfaceGlass（不带任何间接换形）体内应恰一处，实测 $inBody"
        if (inFile != inBody) offenders += "$label：容器锚有 ${inFile - inBody} 处落在容器函数体之外（窗外副本 = 死分支/幽灵容器）"
        if (countMatches(source, maskBorderRead) != 1) {
            offenders += "$label：主题无关蒙版描边 AppPalette.GlassHighlight 应恰一处（实测 ${countMatches(source, maskBorderRead)}）"
        }
        val maskMembers = setOf("AppPalette.GlassHighlight", "AppPalette.Scrim")
        val badStaticReads = anyStaticRead.findAll(body).map { it.value }.toSet() - maskMembers
        if (badStaticReads.isNotEmpty()) offenders += "$label：容器体内出现除蒙版（GlassHighlight/Scrim）以外的静态读点 $badStaticReads"
        panel.frozenAnchors.forEach { (anchor, expected) ->
            val actual = countMatches(body, anchor)
            if (actual != expected) offenders += "$label：几何/形状锚 ${anchor.pattern} 应恰 $expected 处，实测 $actual（本席只换墨，不改面板版面）"
        }
        val signatureOpen = paramParenOf(source, panel.containerSignature, label)
        val signature = splitParameters(
            balancedRegion(source, signatureOpen, '(', ')', "$label 签名").let { (start, end) -> source.substring(start, end + 1) },
        )
        if (signature != panel.frozenParameters) {
            offenders += "$label：容器签名必须逐字保持 $frozenMigratedParametersNote panel=${panel.frozenParameters}，实测 $signature"
        }
        return offenders
    }

    /** 面板窗口判据：窗口内零静态墨/零裸 hex/零命名色常量，每一枚 `AppType` 排印点的墨必须当场取自当前套。 */
    private fun migratedWindowOffenders(label: String, window: String, inkTable: Map<String, String>): List<String> {
        val offenders = mutableListOf<String>()
        // 主题无关蒙版（GlassHighlight / Scrim）按台账口径留在静态读点，容器判据那一侧已钉它恰一处；
        // 窗口这一侧只挡「浅套墨」那一族，否则面板体窗口与容器判据互相打架。
        val maskMembers = setOf("AppPalette.GlassHighlight", "AppPalette.Scrim")
        val staticReads = anyStaticRead.findAll(window).map { it.value }.toSet() - maskMembers
        if (staticReads.isNotEmpty()) offenders += "$label：窗口内残留静态墨读点 $staticReads（容器迁了、前景留浅套 = 深色档近黑压近黑）"
        if (bareHexRead.containsMatchIn(window)) offenders += "$label：窗口内出现裸 hex 构造"
        if (namedStaticColorRead.containsMatchIn(window)) {
            offenders += "$label：窗口内出现命名色常量 ${namedStaticColorRead.findAll(window).map { it.value }.toSet()}（浅套硬编码，不随主题换套）"
        }
        occurrencesOf(window, "AppType.").forEach { hit ->
            val styleName = window.substring(hit + "AppType.".length).takeWhile { it.isLetterOrDigit() || it == '_' }
            if (styleName !in inkTable) return@forEach
            val args = textCallArgumentsOf(window, hit)
                ?: run {
                    offenders += "$label：AppType.$styleName 读点找不到所属 Text( 调用（取样窗口形状不合口径）"
                    return@forEach
                }
            if (!Regex("""style\s*=\s*AppType\.$styleName\b""").containsMatchIn(args)) {
                offenders += "$label：AppType.$styleName 读点不在任何 Text 的实参里（取样窗口切歪）"
                return@forEach
            }
            val colorExpression = colorArgumentOf(args)
            if (colorExpression == null) {
                offenders += "$label：Text(style = AppType.$styleName) 缺显式 color=，仍吃 AppType 的浅套静态墨"
                return@forEach
            }
            if (!themeInkRead.containsMatchIn(colorExpression)) {
                offenders += "$label：Text(style = AppType.$styleName) 的墨不是当场取当前套（实测 \"$colorExpression\"；" +
                    "变量提读、.copy 间接换形与另一套常量都不算）"
            }
        }
        return offenders
    }

    /**
     * 取 `Text(...)` 实参里的 `color =` 表达式（跳过嵌套括号里的逗号）；没有 `color =` 返回 null。
     * 只认字面 `color =`（`contentColor =` / `focusedContainerColor =` 由前导边界排除），
     * 于是「把墨提读成局部 val」会在下一步被 [themeInkRead] 的锚点判出。
     */
    private fun colorArgumentOf(args: String): String? {
        val match = Regex("""(?<![A-Za-z])color\s*=\s*""").find(args) ?: return null
        var depth = 0
        var index = match.range.last + 1
        while (index < args.length) {
            val c = args[index]
            when {
                c in "([{" -> depth++
                c in ")]}" -> if (depth == 0) break else depth--
                c == ',' && depth == 0 -> break
            }
            index++
        }
        return args.substring(match.range.last + 1, index).trim()
    }

    /** 逐字钉住「曾被单独回退成静态浅套墨」那几行的取值形状（在容器文件 + 消费者文件的并集里找）。 */
    private fun migratedPinnedLiteralsOffenders(panel: MigratedPanel, sources: List<String>): List<String> =
        panel.pinnedInkLiterals.filterNot { literal -> sources.any { literal in it } }
            .map { "$panel.label：登记在册的取墨行丢失或改写：\"$it\"（本席把它逐字钉死，改动必须同步本契约与理由）" }

    @Test
    fun `the three migrated glass panel containers take their fill from the theme set`() {
        migratedPanels.forEach { panel ->
            val source = migratedPanelSource(panel)
            assertTrue(source.isNotBlank(), "${panel.file} 剥注释后为空：契约不许对空文件判绿")
            val offenders = migratedContainerOffenders(panel, source, panel.label)
            assertTrue(offenders.isEmpty(), "玻璃面板容器未与主题档同源：\n" + offenders.joinToString("\n"))
        }
    }

    @Test
    fun `every foreground inside the three migrated glass panels is bound to a theme ink`() {
        val inkTable = appTypeInkTable()
        val sources = migratedPanels.map { it.file }.distinct().associateWith { file ->
            migratedPanels.first { it.file == file }.let { panel ->
                if (panel.dirFragment == null) uiScreenSource(file) else uiComponentSource(file, panel.dirFragment)
            }.also { assertTrue(it.isNotBlank(), "$file 剥注释后为空：契约不许对空文件判绿") }
        }
        migratedPanels.forEach { panel ->
            val containerSource = sources.getValue(panel.file)
            val consumerSource = panel.consumerFile?.let { sources.getValue(it) }
            val regions = migratedWindows(panel, containerSource, consumerSource)
            val expectedRegions = 1 + panel.consumerIdentities.size + panel.helperSignatures.size
            assertTrue(
                regions.size == expectedRegions,
                "${panel.label}：面板窗口应恰 $expectedRegions 枚（容器体 + ${panel.consumerIdentities.size} 枚消费窗口 + " +
                    "${panel.helperSignatures.size} 枚私有排印点函数体），实测 ${regions.size} —— 少一枚就是有一处内容没进契约",
            )
            panel.consumerIdentities.forEach { identity ->
                // 只认**带引号的字面量**那一枚：中文身份词常以子串混进别的窗口
                // （如 `contentDescription = "${capability.label}能力"` 里的「能力」没有前引号），
                // 放宽到裸子串会让「身份绑回调用点」这条判据失去唯一性。
                val matched = regions.count { it.contains("\"$identity\"") }
                assertTrue(
                    matched == 1,
                    "${panel.label}：身份字面「$identity」必须恰命中一枚窗口（实测 $matched）——窗口绑不回真实调用点，判据就是空转",
                )
            }
            val offenders = mutableListOf<String>()
            regions.forEachIndexed { index, region ->
                offenders += migratedWindowOffenders("${panel.label} 窗口 #$index", region, inkTable)
            }
            offenders += migratedPinnedLiteralsOffenders(panel, listOf(containerSource) + listOfNotNull(consumerSource))
            assertTrue(offenders.isEmpty(), "玻璃面板内前景未与容器同批迁主题档：\n" + offenders.joinToString("\n"))
        }
    }

    @Test
    fun `the migrated panel scan rejects a copy indirected fill, a hoisted ink and a themed mask`() {
        val inkTable = appTypeInkTable()
        val panel = MigratedPanel(
            label = "合成 SectionCard",
            file = "ProbeScreen.kt",
            dirFragment = null,
            containerSignature = "private fun SectionCard(",
            frozenParameters = listOf("content: @Composable ColumnScope.() -> Unit"),
            frozenAnchors = listOf(
                Regex("""shape = AppShape\.Card""") to 1,
                Regex("""Modifier\.fillMaxWidth\(\)""") to 1,
                Regex("""Modifier\.padding\(16\.dp\)""") to 1,
            ),
            callAnchor = "SectionCard",
            consumerFile = "ProbeScreen.kt",
            consumerIdentities = listOf("能力"),
            helperSignatures = emptyList(),
            pinnedInkLiterals = listOf("color = appColors().text1"),
        )
        val compliantContainer = """
            private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
                Surface(
                    shape = AppShape.Card,
                    color = appColors().surfaceGlass,
                    border = BorderStroke(1.dp, AppPalette.GlassHighlight),
                    modifier = Modifier.fillMaxWidth(),
                ) { Column(Modifier.padding(16.dp), content = content) }
            }
        """.trimIndent()
        assertEquals(
            emptyList(),
            migratedContainerOffenders(panel, compliantContainer, "合规容器样片"),
            "反空转失控：合规容器样片被真判据误伤（判据恒假，主用例随之全假红）",
        )

        val copyDecoy = compliantContainer.replace(
            "color = appColors().surfaceGlass",
            "color = appColors().surfaceGlass.copy(alpha = 0.9f)",
        )
        assertTrue(
            migratedContainerOffenders(panel, copyDecoy, "间接换形容器").isNotEmpty(),
            "反空转失控：只加一层 .copy 的间接换形没被判出（席 A 变异 m1 的同族形状）",
        )
        val staticDecoy = compliantContainer.replace("color = appColors().surfaceGlass", "color = AppPalette.GlassFill")
        assertTrue(
            migratedContainerOffenders(panel, staticDecoy, "回退静态填充").isNotEmpty(),
            "反空转失控：容器退回静态 AppPalette.GlassFill 没被判出（那正是 C-1 的根因）",
        )
        val maskDecoy = compliantContainer.replace("AppPalette.GlassHighlight", "appColors().outlineSoft")
        assertTrue(
            migratedContainerOffenders(panel, maskDecoy, "蒙版被主题化").isNotEmpty(),
            "反空转失控：主题无关蒙版被主题化没被判出（会让 ThemeInvariantMaskReadLedger 漂）",
        )
        // 版面守卫的牙齿落在「冻结锚点逐枚恰中」上：改值/改名都会让某一枚锚的计数漂移，即刻判红。
        // （只往链尾追加一段修饰、不动任何已登记锚形状，本机制看不见——这是显式口径，不假装全护。）
        val geometryDecoy = compliantContainer.replace("Modifier.padding(16.dp)", "Modifier.padding(20.dp)")
        assertTrue(
            migratedContainerOffenders(panel, geometryDecoy, "版面被改").isNotEmpty(),
            "反空转失控：面板几何锚被改值没被判出（本席只换墨，不改版面）",
        )
        val shapeDecoy = compliantContainer.replace("shape = AppShape.Card", "shape = AppShape.Dialog")
        assertTrue(
            migratedContainerOffenders(panel, shapeDecoy, "形状被改").isNotEmpty(),
            "反空转失控：容器形状锚被改名没被判出（面板形状不属于本席的改动面）",
        )

        val compliantWindow = """
            SectionCard {
                Text("能力", style = AppType.Title, color = appColors().text1)
                Text("声明这套服务支持的模态。", style = AppType.Footnote, color = appColors().text2)
                CapabilityToggle(capability = capability, selected = true, onToggle = {})
            }
        """.trimIndent()
        assertEquals(
            emptyList(),
            migratedWindowOffenders("合规窗口样片", compliantWindow, inkTable),
            "反空转失控：合规窗口样片被真判据误伤",
        )
        val uncolored = compliantWindow.replace(", color = appColors().text1", "")
        assertTrue(
            migratedWindowOffenders("缺色样片", uncolored, inkTable).isNotEmpty(),
            "反空转失控：补色被抹掉（回到 AppType 静态浅套墨）没被判出",
        )
        val hoisted = compliantWindow.replace("color = appColors().text1", "color = ink")
        assertTrue(
            migratedWindowOffenders("提读样片", hoisted, inkTable).isNotEmpty(),
            "反空转失控：把主题墨提读成变量再点用没被判出",
        )
        val indirected = compliantWindow.replace("color = appColors().text1", "color = appColors().text1.copy(alpha = 1f)")
        assertTrue(
            migratedWindowOffenders("间接墨样片", indirected, inkTable).isNotEmpty(),
            "反空转失控：字面量原封不动只加一层 .copy 的间接换形没被判出",
        )
        val staticWindow = compliantWindow.replace("appColors().text2", "AppPalette.TextSecondary")
        assertTrue(
            migratedWindowOffenders("静态墨样片", staticWindow, inkTable).isNotEmpty(),
            "反空转失控：窗口内残留静态浅套墨没被判出",
        )
        val whiteField = compliantWindow + "\nTextField(focusedContainerColor = Color.White)\n"
        assertTrue(
            migratedWindowOffenders("命名色样片", whiteField, inkTable).isNotEmpty(),
            "反空转失控：卡内写死的 Color.White 容器没被判出（深色档会在深玻璃里留一枚白输入框）",
        )
        val conditional = """
            SectionCard {
                Text(label, style = AppType.Body, color = if (selected && !LocalIsDark.current) appColors().onMint else if (selected) appDomain(AppDomain.AiSpeech).deep else appColors().text1)
            }
        """.trimIndent()
        assertEquals(
            emptyList(),
            migratedWindowOffenders("条件墨样片", conditional, inkTable),
            "反空转失控：VoiceOverlayOption 那行的条件式取墨形状被误伤（契约只认单值，判据就落不到真实面板上）",
        )
        val pinnedLoss = migratedPinnedLiteralsOffenders(panel, listOf(compliantWindow))
        assertTrue(pinnedLoss.isEmpty(), "反空转失控：在册取墨行明明存在却被判丢：$pinnedLoss")
        assertTrue(
            migratedPinnedLiteralsOffenders(panel, listOf(compliantWindow.replace("color = appColors().text1", "color = AppPalette.TextPrimary"))).isNotEmpty(),
            "反空转失控：在册取墨行被改回静态墨却没被点名（Task 12.5 那一族回退就这样悄悄回来）",
        )
    }

    @Test
    fun `an unclosed migrated panel call region fails loudly instead of scanning a truncated window`() {
        val truncated = "SectionCard { Text(\"能力\", style = AppType.Title, color = appColors().text1\n"
        assertFailsWith<AssertionError>(
            "未闭合的尾随 lambda 必须响亮报错：静默截断窗口会把窗外文字算进窗内（批 2 seatC1 评审 I-1 同族教训）",
        ) {
            callSites(truncated, "SectionCard").forEach { regionOfCall(truncated, it, "SectionCard", "截断样片") }
        }
        val closed = "SectionCard { Text(\"能力\", style = AppType.Title, color = appColors().text1) }\n"
        assertEquals(1, callSites(closed, "SectionCard").size, "闭合样片取不到调用点：取窗夹具本身坏了")
        assertEquals(1, regionOfCall(closed, callSites(closed, "SectionCard").single(), "SectionCard", "闭合样片").count { it == '}' }, "取窗没收尾到尾随 lambda 的闭合花括号")
        val definitionForm = "private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {}\n"
        assertEquals(
            emptyList(),
            callSites(definitionForm, "SectionCard"),
            "定义点被当成消费窗口：容器判据会在自己头上取窗，消费者侧反而失明",
        )
    }
}

/** [migratedContainerOffenders] 的报错文案里要的固定前缀（抽出来避免把整段中文塞进正则）。 */
private const val frozenMigratedParametersNote = "的形参清单（实测不符即容器签名被动过）："
