package com.example.englishlearning.ui

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
 *    六档自带的 `AppPalette.TextPrimary`（= `AppColorLight.text1`，同样逐字相同）钉死在浅套深墨上；
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
 *  ③ 八处消费面板（`AppScreen.kt` 两枚 + 六屏各一枚）：窗口内每一枚带 `style = AppType.X` 的 `Text(`
 *     必须显式带 `color = appColors().<X 档当前墨>`；期望墨**由现场解析 `AppType.kt` 的六档墨表得出**
 *     （`TextPrimary → text1`、`TextSecondary → text2`），不是本文件拍脑袋写的常量——判据跟着真值走，
 *     六档一旦漂移本契约当场要求复核而不是悄悄错位。
 *  ④ 取样窗口绑死真实调用点：按 `GlassDialog(` 出现位置配平取「实参括号 + 尾随 lambda」整段，
 *     **未闭合即响亮报错**（批 2 seatC1 评审 I-1 的教训：窗口切歪时「窗外零处」这类断言会自己骗自己）；
 *     份数恰 8、每枚按标题字面认出，多一枚/少一枚/换身份都判红。
 *
 * 防空转（与 `AppScreenOverlayContainerContractTest`、`ContainerInkPairingContractTest` 同族写法）：
 * 每条主判据都有合成对拍走**同一条**解析链——合规样片放行、违规样片恰被点名、
 * 「字面量还在但换了间接路径」（`.copy`、默认参数提读、把墨存进局部 val）三类诱饵必须判红。
 *
 * 读法只走共享夹具 [uiScreenSource] / [uiComponentSource]（内置「文件恰一个 + 目录守卫 + 剥注释」），
 * 本文件不自写第二份剥注释实现（批 1 终审 F6）。配平取窗三个小夹具与 17A/17B 的同名私有实现同源：
 * 它们既不是 `stripComments` 也没进共享夹具，批 3 做测试夹具归并时应与那两处一起搬进 `SourceRatchet.kt`。
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

    /** 八处消费面板：`file` + 该枚面板的身份字面（标题文案，逐枚唯一）。 */
    private data class Panel(val file: String, val identity: String)

    private val expectedPanels = listOf(
        Panel("AppScreen.kt", "选择要导出的词书"),
        Panel("AppScreen.kt", "发现已学过的单词"),
        Panel("ArticleReadingScreen.kt", "本文覆盖你已背的"),
        Panel("ImageStudioScreen.kt", "确认发送请求"),
        Panel("PhotoTranslationScreen.kt", "确认发送请求"),
        Panel("ReadingAccessScreen.kt", "确认发送请求"),
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
     * 解析 `ui/theme/AppType.kt` 六档自带的静态墨，折算成它们应迁往的 `AppColors` 成员名：
     * `TextPrimary → text1`、`TextSecondary → text2`（`ui/theme/AppColors.kt` 两套的对应成员）。
     * 六档缺任何一档即判红：解析链坏掉（改名/换写法）时必须响亮报错，而不是让下面那条配对判据
     * 对着空表「无一枚需要补色」地假绿。
     */
    private fun appTypeInkTable(): Map<String, String> {
        val source = uiComponentSource("AppType.kt", "/ui/theme/")
        // 六档各占一行、行内无嵌套括号，故 `[^)]*` 即可取到整段实参；行尾按 CRLF 存盘（本仓源码统一 CRLF），
        // 所以句尾不能再按 `\n` 收尾判，否则解析链对真文件一枪不发、下面的墨表就是空表假绿。
        val styleDefinition = Regex("""\bval (Display|Headline|Title|Body|Footnote|Label) = TextStyle\(([^)]*)\)""")
        val found = styleDefinition.findAll(source).associate { match ->
            val ink = when {
                "AppPalette.TextPrimary" in match.groupValues[2] -> "text1"
                "AppPalette.TextSecondary" in match.groupValues[2] -> "text2"
                else -> error("AppType.${match.groupValues[1]} 的墨不再是那两枚浅套硬编码：本契约的取样前提已失效，需复核")
            }
            match.groupValues[1] to ink
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

    // ---------- 判据 ③④：八处面板前景同源 ----------

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
}
