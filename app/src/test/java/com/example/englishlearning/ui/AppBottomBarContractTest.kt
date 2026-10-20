package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Task 17A 底导契约（批 2 UI 重设计）：底部四 tab 的字形必须由共享 duotone 组件渲染，
 * 自绘 Canvas 一族（`Canvas(` / `drawTabGlyph` / `drawSparkle` 及其 foundation 导入）在本文件剥注释后归零；
 * 无障碍三件（testTag / Role.Tab / stateDescription）与两条版式承重（64dp 下限、系统栏内边距）
 * 都钉在 `TabSlot` **活体函数**的花括号配对区间内，并额外要求同一形状在函数体外计数为零——
 * 「死分支/幽灵函数里塞一份假调用」骗不过「体内恰一处 + 体外零处」这一对（Task 15/16 decoy 教训）。
 * 整函数体内的假调用残留（`.takeIf{false}?.let` 族）与批 3 AC-R-12 整族结转同口径，本席不另造。
 *
 * 修复轮 1（评审 I-2）：两枚字形锚从「只钉调用字面」升格为**实参级**逐字形状（字形来源、墨、度量、图层一起钉），
 * 并加「体内字形调用恰一处 + 显式 StrokeOnly 层恰一处」这一对，把评审 M2 的两条偷换路径
 * （`StrokeOnly`→`Duotone`、省略 `layer` 塌回默认 `Duotone`）都逼到当场见红；反空转探针逐形状钉住。
 *
 * 修复轮 2（复评 A）：标签墨的「定义 + 消费」配对（`val labelInk = if (selected && !LocalIsDark.current)` /
 * `color = labelInk`）进同一具「体内恰一处 + 体外零处」计数器——复评席的击穿形状
 * 「生效定义换另一枚活 token + 体内不可达分支留原取色副本」喂不饱定义锚，也喂不进体外零处；
 * 同名单影副本属纯计数机制天花板，与 `.takeIf{false}` 族同口径归批 3 AC-R-12 整族结转（见下）。
 *
 * 选中态墨色必须走 `appDomain(...)` / `appColors()` 主题路径且文件内静态读点归零，
 * 与 `DarkTokenConsumptionRatchetTest` 删 seed 行互为两侧闸（seed 残留由棘轮红，回退静态由本契约红）。
 *
 * 书写形状与 `AccessibilityStateSemanticsContractTest` 规则2 钉的字面量逐字同源，那份契约先于本席存在、
 * 本席重写必须保持这些形状在场；两份各钉一面（那份管语义声明存在，这份管它长在活路径上）。
 */
class AppBottomBarContractTest {

    // ---------- 读取 ----------

    private fun appBottomBarSource(): String {
        val files = uiFiles().filter { it.name == "AppBottomBar.kt" }
        assertTrue(
            files.size == 1,
            "AppBottomBar.kt 应恰好一个，实际 ${files.size} 个（期望目录 $UI_MAIN_SOURCE_PATH，" +
                "JVM 工作目录 ${File(".").absolutePath}）",
        )
        val file = files.single()
        assertTrue(
            file.parentFile.relativeTo(File(UI_MAIN_SOURCE_PATH)).path.isEmpty(),
            "AppBottomBar.kt 不在 ui 根下（搬家会让本契约读错文件）：${file.path}",
        )
        return stripComments(file.readText())
    }

    // ---------- 函数体提取（花括号配对，认字符串） ----------

    /**
     * 从 `stripped` 中取 [signature] 锚定函数的花括号体（不含最外层括号）。
     * 字符串字面量整体跳过（`stripComments` 保留字符串本体，串里的花括号不能参与配对）；
     * 未闭合直接 fail——配对器失准时「体外计数」会把体内锚算成体外，必须响亮报错而不是静默切歪。
     */
    private fun extractFunctionBody(stripped: String, signature: String): String {
        val start = stripped.indexOf(signature)
        assertTrue(start >= 0, "找不到函数签名 \"$signature\"：锚点搬家或改名，契约不许空转")
        val open = stripped.indexOf('{', start)
        assertTrue(open >= 0, "$signature 之后找不到函数体起始花括号")
        var depth = 0
        var i = open
        var inString = false
        var escaped = false
        while (i < stripped.length) {
            val c = stripped[i]
            when {
                inString -> when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }

                c == '"' -> inString = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return stripped.substring(open + 1, i)
                }
            }
            i++
        }
        fail("$signature 的花括号未闭合：配对器无法界定函数体，本契约拒绝猜测")
    }

    private fun count(hay: String, needle: String): Int = Regex(needle, RegexOption.LITERAL).findAll(hay).count()

    // ---------- 锚点形状 ----------

    private val tabSlotSignature = "private fun RowScope.TabSlot("

    private val testTagAnchor = "testTag(\"app_tab_\${tab.name.lowercase()}\")"
    private val contentDescriptionAnchor = "contentDescription = tab.contentDescription"
    private val roleAnchor = "role = Role.Tab"
    private val stateAnchor = "stateDescription = if (selected)"
    private val slotMinHeightAnchor = "heightIn(min = 64.dp)"
    private val themedInkAnchor =
        "if (selected) appDomain(AppDomain.AiSpeech).deep else appColors().text2"

    /**
     * 实参级锚（修复轮 1 / 评审 I-2）：旧形状只钉 `InlineIconBadge(` / `AppGlyph(` 两枚字面，
     * 于是把未选中档的 `layer = AppGlyphLayer.StrokeOnly` 偷换成 `Duotone`（spec §4.3 的行内层口径反过来改）
     * 46 例全绿存活——与 14A I-1「只钉转调声明、不钉实参消费点」同族。故这里钉**整条实参形状**：
     * 字形来自 `tab.glyph`、墨来自那枚主题化 `tint`、度量 22dp、层必须显式 `StrokeOnly`。
     */
    private val selectedBadgeAnchor = "InlineIconBadge(icon = tab.glyph, domain = AppDomain.AiSpeech)"
    private val unselectedGlyphAnchor =
        "AppGlyph(icon = tab.glyph, tint = tint, size = 22.dp, layer = AppGlyphLayer.StrokeOnly)"
    private val strokeOnlyLayerAnchor = "AppGlyphLayer.StrokeOnly"
    private val glyphCallAnchor = "AppGlyph("

    /**
     * 标签墨夹击锚（修复轮 2 / 复评 A）：`AppBottomBarContrastTest` 的文件级 `contains` 只问「形状在不在文本里」，
     * 于是「生效定义换成另一枚活 token（如 `val labelInk = appColors().text3`）+ 同一函数体的不可达分支原样保留
     * `if (selected && !LocalIsDark.current) appColors().onMint else tint`」这发击穿能把文字腿 ≥4.5 变成对一枚
     * 从未渲染的墨复算而全绿存活。这里把该配对的**定义形状**与**消费形状**钉进与三件套同一具
     * 「体内恰一处 + 体外零处」计数器：死副本改名喂不到定义锚（近 miss 见探针），幽灵函数消费喂不进体外零处。
     * 同名单影副本（`if (false) { val labelInk = <原定义> }`）是纯计数机制的已知天花板，
     * 与文件头 KDoc 的 `.takeIf{false}` 族同口径归批 3 AC-R-12 整族结转，本席不另造作用域分析。
     */
    private val labelInkDefinitionAnchor = "val labelInk = if (selected && !LocalIsDark.current)"
    private val labelInkConsumptionAnchor = "color = labelInk"

    /** 三件齐 + 墨线 + 两枚实参级字形锚 + 标签墨定义/消费配对：这些形状钉在 TabSlot 体内「恰一处」，且同一形状在体外「零处」。 */
    private val inBodyExactlyOne = listOf(
        testTagAnchor,
        contentDescriptionAnchor,
        roleAnchor,
        stateAnchor,
        slotMinHeightAnchor,
        themedInkAnchor,
        selectedBadgeAnchor,
        unselectedGlyphAnchor,
        labelInkDefinitionAnchor,
        labelInkConsumptionAnchor,
    )

    /** 体外必须归零的那一组（假 tab/幽灵函数塞这些形状即被体外计数抓获）。 */
    private val outsideBodyMustBeZero = listOf(
        testTagAnchor,
        contentDescriptionAnchor,
        roleAnchor,
        stateAnchor,
        selectedBadgeAnchor,
        unselectedGlyphAnchor,
        labelInkConsumptionAnchor,
    )

    // ---------- 用例：自绘字形族归零 ----------

    @Test
    fun `hand drawn glyph family is fully gone from the bottom bar`() {
        val bar = appBottomBarSource()
        listOf(
            "Canvas(" to "Canvas 自绘调用",
            "drawTabGlyph" to "自绘字形分发函数名",
            "drawSparkle" to "自绘星芒函数名",
            "androidx.compose.foundation.Canvas" to "Canvas 的 foundation 导入",
        ).forEach { (needle, label) ->
            assertEquals(0, count(bar, needle), "底导仍残留$label（「$needle」出现 ${count(bar, needle)} 次）：字形必须走共享组件")
        }
        // 反空转：同一具计数器必须数得出合成片段里的命中，否则上面的 0 是恒真。
        val probe = "Canvas(Modifier.size(22.dp)) { drawTabGlyph(tab, tint) }\ndrawSparkle(tint, unit)\n" +
            "import androidx.compose.foundation.Canvas\n"
        probe.split("\n").filter { it.isNotBlank() }.forEach { line ->
            assertTrue(count(line, "Canvas(") + count(line, "drawTabGlyph") + count(line, "drawSparkle") +
                count(line, "androidx.compose.foundation.Canvas") > 0, "反空转失控：探针行没被数出任何命中：$line")
        }
    }

    // ---------- 用例：三件齐钉在 TabSlot 活体 ----------

    @Test
    fun `tab slot live body carries the accessibility trio, themed ink and shared glyph calls`() {
        val bar = appBottomBarSource()
        val body = extractFunctionBody(bar, tabSlotSignature)
        inBodyExactlyOne.forEach { anchor ->
            assertEquals(1, count(body, anchor), "TabSlot 体内「$anchor」必须恰一处，实际 ${count(body, anchor)} 次")
        }
        outsideBodyMustBeZero.forEach { anchor ->
            val outside = count(bar, anchor) - count(body, anchor)
            assertEquals(0, outside, "TabSlot 体之外出现「$anchor」（$outside 次）：语义三件只许长在生效路径上，死分支/幽灵函数不算")
        }
        // 反空转：把同一形状塞进函数体之外的幽灵函数，体外计数必须非零（否则上面那条断言恒真）。
        val ghost = "$tabSlotSignature tab: AppTab) {\n    val tint = if (selected) a else b\n}\n" +
            "private fun GhostTabSlot() {\n    $roleAnchor\n}\n"
        val ghostBody = extractFunctionBody(ghost, tabSlotSignature)
        assertEquals(0, count(ghostBody, roleAnchor), "反空转失控：幽灵形状被算进了 TabSlot 体内")
        assertEquals(1, count(ghost, roleAnchor) - count(ghostBody, roleAnchor), "反空转失控：体外的假 Role.Tab 没被数出来")
        // 反空转（近miss）：常量播报必须不满足双值形状。
        val constantState = """stateDescription = "已选中""""
        assertEquals(0, count(constantState, stateAnchor), "反空转失控：常量 stateDescription 竟满足双值锚点")

        // ---------- 字形层的实参级牙齿（修复轮 1 / 评审 I-2）----------
        // 一枚共享字形调用 + 一枚显式 StrokeOnly 层，两条同时恰一处：
        // 「省略 layer 实参」会塌回 AppGlyph 的默认 Duotone，调用计数照旧为 1，但层计数掉到 0 ⇒ 红。
        assertEquals(1, count(body, glyphCallAnchor), "TabSlot 体内的共享字形调用必须恰一处（多一处即长出第二份渲染路径）")
        assertEquals(
            1, count(body, strokeOnlyLayerAnchor),
            "未选中档必须显式只要描边层（AppGlyphLayer.StrokeOnly）：省略 layer 会静默取默认的 Duotone",
        )
        assertEquals(
            0, count(bar, "AppGlyphLayer.Duotone"),
            "底导不得显式请求 Duotone 层：选中档走 InlineIconBadge（内部即单层描边），未选档按 spec §4.3 只显描边",
        )
        // 反空转（评审 M2 那发变异的两条形状）：换成 Duotone、或整段省略 layer，都必须让实参级锚归零，
        // 否则上面那批断言只是「字面在不在文件里」的摆设。
        val duotoneSwap = unselectedGlyphAnchor.replace("AppGlyphLayer.StrokeOnly", "AppGlyphLayer.Duotone")
        assertEquals(0, count(duotoneSwap, unselectedGlyphAnchor), "反空转失控：StrokeOnly→Duotone 的偷换形状仍满足实参级锚")
        assertEquals(0, count(duotoneSwap, strokeOnlyLayerAnchor), "反空转失控：偷换成 Duotone 后层锚竟还有命中")
        val layerOmitted = "AppGlyph(icon = tab.glyph, tint = tint, size = 22.dp)"
        assertEquals(0, count(layerOmitted, unselectedGlyphAnchor), "反空转失控：省略 layer（默认 Duotone）竟满足实参级锚")
        assertEquals(0, count(layerOmitted, strokeOnlyLayerAnchor), "反空转失控：省略 layer 后层锚竟还有命中")
        val reordered = "AppGlyph(icon = tab.glyph, tint = tint, layer = AppGlyphLayer.StrokeOnly, size = 22.dp)"
        assertEquals(0, count(reordered, unselectedGlyphAnchor), "反空转失控：实参换序的形状竟满足逐字锚（锚点没钉到实参段）")
        assertEquals(1, count(unselectedGlyphAnchor, unselectedGlyphAnchor), "反空转失控：正向形状不满足自身锚点，锚点写歪了")

        // ---------- 标签墨夹击的近 miss 探针（修复轮 2 / 复评 A）----------
        // 复评席给的击穿形状本身：生效定义换成另一枚活 token、体内死分支只留原取色表达式的改名副本。
        // 定义锚必须判 0（副本没钉成 labelInk 的生效定义），否则「体内恰一处」对这类喂锚无效。
        val breachProbe = "val labelInk = appColors().text3\n" +
            "if (false) {\n    val deadInk = if (selected && !LocalIsDark.current) appColors().onMint else tint\n}\n" +
            "Text(color = labelInk)\n"
        assertEquals(0, count(breachProbe, labelInkDefinitionAnchor), "反空转失控：死分支的改名取色副本竟满足标签墨定义锚")
        assertEquals(1, count(breachProbe, labelInkConsumptionAnchor), "反空转失控：击穿探针里的消费点计数异常，消费锚写歪了")
        // 消费点被换成别的墨（color = tint）必须不满足消费锚；正向形状自命中。
        val rebindingProbe = "val labelInk = if (selected && !LocalIsDark.current) appColors().onMint else tint\nText(color = tint)"
        assertEquals(0, count(rebindingProbe, labelInkConsumptionAnchor), "反空转失控：标签没接 labelInk 竟满足消费锚")
        assertEquals(1, count(rebindingProbe, labelInkDefinitionAnchor), "反空转失控：生效定义形状不满足自身定义锚，锚点写歪了")
        assertEquals(1, count(labelInkConsumptionAnchor, labelInkConsumptionAnchor), "反空转失控：正向消费形状不满足自身锚点")
    }

    // ---------- 用例：版式承重（F5-03 Task 11 成果） ----------

    @Test
    fun `bottom bar keeps the 64dp floor and navigation bar padding`() {
        val bar = appBottomBarSource()
        assertEquals(1, count(bar, "navigationBarsPadding()"), "系统栏内边距必须恰一处（Row 容器上，F5-03 Task 11 成果）")
        assertEquals(2, count(bar, slotMinHeightAnchor), "64dp 下限必须恰两处（Row 容器 + TabSlot 各一处，被吞任何一处即红）")
        val body = extractFunctionBody(bar, tabSlotSignature)
        assertEquals(1, count(body, slotMinHeightAnchor), "TabSlot 体内必须保留 64dp 下限一处")
    }

    // ---------- 用例：选中态主题化（dark-token 债在此清偿） ----------

    @Test
    fun `selected ink reads the themed domain palette and no static palette read survives`() {
        val bar = appBottomBarSource()
        assertEquals(0, count(bar, "DomainColors."), "底导不得残留 AppPalette.kt 静态域色读点：改 appDomain(AppDomain.X).deep")
        assertEquals(0, count(bar, "AppPalette."), "底导不得残留 AppPalette 静态读点：改 appColors()/appDomain()")
        val body = extractFunctionBody(bar, tabSlotSignature)
        assertEquals(1, count(body, themedInkAnchor), "TabSlot 选中/未选中墨色必须按主题取：选中 deep、未选中 text2，恰一处")
    }

    // ---------- 用例：四枚 duotone 字形映射 ----------

    @Test
    fun `the four tabs map to the pinned duotone glyphs exactly once each`() {
        val bar = appBottomBarSource()
        listOf("AppIcon.Book", "AppIcon.Newspaper", "AppIcon.Spark", "AppIcon.Gear").forEach { glyph ->
            assertEquals(1, count(bar, glyph), "底导字形映射里「$glyph」必须恰一处（学习 Book / 阅读 Newspaper / AI Spark / 设置 Gear）")
        }
        assertTrue(
            bar.contains("tab.glyph"),
            "TabSlot 必须经 AppTab 的字形映射取 glyph，而不是散落四枚 when 分支",
        )
    }
}
