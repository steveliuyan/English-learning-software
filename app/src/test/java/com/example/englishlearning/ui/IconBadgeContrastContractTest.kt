package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.DuotoneFillAlpha
import com.example.englishlearning.ui.components.StrokeOnlyIcons
import com.example.englishlearning.ui.components.allowsInEntryContainer
import com.example.englishlearning.ui.components.buildAppImageVector
import com.example.englishlearning.ui.components.circleFillPathData
import com.example.englishlearning.ui.components.gradientStartForDomain
import com.example.englishlearning.ui.components.spec
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.DomainLight
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 批 1 Task 7 契约：双色图标的两种呈现（≥40dp 入口容器 / 30dp 行内徽标）按实测对比与几何钉死。
 *
 * 口径沿用 Task 2/4/6（`DesignTokenContractTest` / `MaterialThemeAssemblyContractTest` /
 * `AppIconsStructureContractTest`）：
 *  - 对比数字一律由 WCAG 相对亮度当场复算（全局约束 7「禁止估」）：`luminance`/`ratio` 取共享夹具
 *    `SourceRatchet` 里 Task 2 那份 int 域实现，本任务起两个契约类共用同一份公式；
 *  - 域色读 `DomainLight` / `DomainDark` 的活符号而不是抄 hex 字面量：token 漂移必须让本测试跟着动，
 *    抄一份数字只会让契约与实现各说各话。计划原文写的是 int 域（`p.deep and 0xFFFFFF`），而
 *    `DomainPalette` 存的是 `Color`，故统一先经 [argbOf]/[rgbOf] 折算成字节，其余算式逐字照原文；
 *  - 渲染类断言不在这里：JVM 单测没有 Compose 渲染运行时，容器渐变端点、层开关、几何尺寸与
 *    豁免表强制都读 `src/main/java` 文本（先过 `stripComments`，注释里的示例代码不计入契约），
 *    真像素核验走 androidTest 与批 1 收口清单；
 *  - 每条源码正则都用合成正片段与合成负片段当场证明「抓得住且不越界」；文本正则只守「写法」，
 *    写法背后能不能真画出东西由组装用例判定——[buildAppImageVector] 是纯函数，JVM 侧直接读
 *    返回的 `ImageVector` 节点表与画笔参数（修复轮 I-1：只钉「文本里出现过 CircleFill」曾恒真，
 *    删掉标记解释也不会有任何一条红）。
 *
 * [mixArgb] 是本测试自带的第二份混色实现（int 域），与生产的 `gradientStartForDomain` 不共享一行代码：
 * 这是**有意的重复**——两端点必须算出同一个颜色才算契约成立（逐通道比对见
 * [gradient_start_endpoints_match_the_independent_mix_channel_by_channel]）。共用同一份实现的话，
 * 这条契约就退化成「函数返回它自己」，分数写错、参数写反都测不出来。
 *
 * 深色入口的字形极性偏离了计划原文的「白色两层字形」，理由钉在
 * [dark_entry_ink_paired_with_domain_colors_clears_3_to_1]：深色下域色即浅底，白字形压它不到 3:1，
 * 视觉稿因此把深套描边改成墨色；本工程 `AppColorSet` 的字段集由 Task 2 契约钉死、不容为本任务加一枚
 * 墨色 hex，故生产取同族最深中性墨 `AppColorDark.onBrand`，本用例复算的就是这一对真实配对。
 */
class IconBadgeContrastContractTest {

    // ---------- 测试侧独立实现（int 域，与生产零共享） ----------

    /** `Color` → `0xAARRGGBB` 的 int 位形：字节取值入口，算式其余部分逐字照计划原文。 */
    private fun argbOf(color: Color): Int = color.toArgb()

    /** 计划原文的 `p.deep and 0xFFFFFF`：丢掉 alpha 只看 RGB 三字节。 */
    private fun rgbOf(color: Color): Int = argbOf(color) and 0xFFFFFF

    /**
     * 逐通道（含 alpha）线性插值：[from] 权重 `1 - fraction`、[to] 权重 `fraction`，四舍五入取字节。
     * 等价于视觉稿的 `color-mix(in srgb, var(--d) 65%, var(--db))`（稿里 `--d` 取 deep、`--db` 取 base）。
     */
    private fun mixArgb(from: Int, to: Int, fraction: Double): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * fraction).roundToInt()
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** 计划原文的混色入口：只要 RGB 三字节（白字形比值不关心 alpha）。 */
    private fun mixHex(from: Int, to: Int, fraction: Double): Int = mixArgb(from, to, fraction) and 0xFFFFFF

    // ---------- 读取与守卫 ----------

    /** 按文件名在 `ui/components/` 取剥注释源码（本任务两个实现文件与 Task 6 的 AppIcons.kt 同目录）。 */
    private fun uiComponentSource(name: String): String = uiSource(name, "/ui/components/")

    /** 按文件名在 `ui/theme/` 取剥注释源码（`AppShape.kt` 的两档圆角数值归这里）。 */
    private fun uiThemeSource(name: String): String = uiSource(name, "/ui/theme/")

    /** 按文件名在共享夹具扫描面里取剥注释源码；文件缺失 = 契约没有对象，必须点名而不是空扫过关。 */
    private fun uiSource(name: String, dirFragment: String): String {
        val files = uiFiles().filter { it.name == name }
        assertTrue(
            files.size == 1,
            "$name 应恰好一个，实际 ${files.size} 个（实现文件未创建 / 被改名 / 扫描错位？" +
                "期望目录 $UI_MAIN_SOURCE_PATH，JVM 工作目录 ${File(".").absolutePath}）"
        )
        val file = files.single()
        assertTrue(
            file.path.replace('\\', '/').contains(dirFragment),
            "$name 不在 $dirFragment 下（目录挪动会让本契约读错文件）：${file.path}"
        )
        return stripComments(file.readText())
    }

    // ---------- 规则形状 ----------

    /** 入口容器渐变：亮端必须由域名规则现算，且整份文件只此一处渐变（第二处就是第二套容器口径）。 */
    private val gradientFromRule = Regex("""linearGradient\(\s*listOf\(\s*gradientStartForDomain\(""")

    /** 禁止形状：把域 `base` 直接当渐变端点（spec §2.1 点名的失败形状，白字形只有 2.11 / 2.06）。 */
    private val rawBaseEndpoint = Regex("""listOf\(\s*[A-Za-z_][\w.]*\.base\s*,""")

    /** 行内徽标：17dp 字形必须显式只要描边层——填充层在 30dp 浅底上没有信息量，只会把字形糊成一坨。 */
    private val strokeOnlyGlyphCall = Regex("""size\s*=\s*17\.dp\s*,\s*layer\s*=\s*AppGlyphLayer\.StrokeOnly""")

    // 首轮那条 `size = 24.dp)` 把「不带 layer 实参」与「size 是最后一个实参」混为一谈（评审 M-2），
    // 已换成下面这条把整段调用写死的精确形状，行为面另由组装用例
    // [assembled_vector_keeps_both_layers_for_duotone_and_only_the_skeleton_for_stroke_only] 真断言。

    /** 禁止形状：把描边-only 开关用到入口字形上（两种呈现的差异就坍缩成一种）。 */
    private val strokeOnlyOnEntryGlyph = Regex("""size\s*=\s*24\.dp\s*,\s*layer\s*=\s*AppGlyphLayer\.StrokeOnly""")

    /** 入口字形的两层口径 = 不传 `layer` 实参（走默认值）；写死实参或换行排版都算改口径，本形状当场不匹配。 */
    private val entryGlyphDefaultCall = Regex("""AppGlyph\(icon = icon, tint = ink, size = 24\.dp\)""")

    /** 豁免表强制：入口容器开头必须由纯函数判定挡住豁免枚。 */
    private val entryRequire = Regex("""require\(allowsInEntryContainer\(icon\)\)""")

    private val entryRequireMessage = Regex("""不得进入 ≥40dp 入口容器""")

    private val entryContainerSize = Regex("""size\(40\.dp\)""")

    private val inlineContainerSize = Regex("""size\(30\.dp\)""")

    /** 行内容器底色必须取主题 tint（深浅两套随主题翻，写死浅绿就是第二套真值）。 */
    private val inlineTintBackdrop = Regex("""background\(appColors\(\)\.tint\)""")

    /** 入口容器圆角：`IconBadge.kt` 里 `clip(AppShape.Icon)` 恰 1 处（12dp 档只归 ≥40dp 入口）。 */
    private val entryShapeClip = Regex("""clip\(AppShape\.Icon\)""")

    /** 行内徽标圆角：`clip(AppShape.Inline)` 恰 1 处（视觉稿行内 chip 的 9px 档，不与入口共用）。 */
    private val inlineShapeClip = Regex("""clip\(AppShape\.Inline\)""")

    /** 两档圆角的数值本身：各恰一处，数值漂移（12→9 或 9→12）与合并成同档都在这里红。 */
    private val iconCornerValue = Regex("""RoundedCornerShape\(12\.dp\)""")

    private val inlineCornerValue = Regex("""RoundedCornerShape\(9\.dp\)""")

    /** 数值还得落在正确的槽位上：`Icon` 拿 12dp、`Inline` 拿 9dp，写反了就不是「两档」而是换了个名。 */
    private val iconCornerTier = Regex("""val Icon: Shape = RoundedCornerShape\(12\.dp\)""")

    private val inlineCornerTier = Regex("""val Inline: Shape = RoundedCornerShape\(9\.dp\)""")

    /**
     * 玻璃高光的**近似**落地（按 token 落地，不是视觉稿的同一实现）：1dp `AppPalette.GlassHighlight` 描边。
     *
     * 实测差（登记用，非放行依据）：token `0x59FFFFFF` → α≈0.349，视觉稿浅为 `rgba(255,255,255,.26)`、
     * 深为 `.20`（稿分深浅两档，实现只有单档通用），即本实现比稿亮约 0.089 / 0.149；
     * 像素分布也不同——稿是 CSS `box-shadow: inset 0 0 0 1px`，这里是 Compose 按 shape 描边。
     * 是否为此新建第二档 token 由控制者裁决 4 认定：不新建，本处只把差异写清。
     */
    private val glassHighlightBorder = Regex("""border\(1\.dp,\s*AppPalette\.GlassHighlight,\s*AppShape\.Icon\)""")

    /** 填充层不透明度是主题取值，只能问 `DuotoneFillAlpha.forTheme`，不得把档位抄进渲染码。 */
    private val themedFillAlphaRead = Regex("""DuotoneFillAlpha\.forTheme\(""")

    /** 组装结果必须按输入缓存：图标、色、描边倍率任一变化都要重建，否则换色不换形。 */
    private val cacheKeyedOnInputs = Regex("""remember\(\s*icon\s*,\s*tint\s*,\s*strokeScale""")

    /** 视觉描边重量 2 随 `strokeScale` 缩放（Task 6 的度量基准在渲染端的落点）。 */
    private val scaledStrokeWidth = Regex("""2f\s*\*\s*strokeScale""")

    /** `CircleFill(` 标记串必须进解释函数：定义 1 处 + 调用 1 处 = 恰 2 处。 */
    private val circleFillInterpretation = Regex("""circleFillPathData\(""")

    /** 禁止形状：解释不出就回落给 SVG 解析器（回落分支必须整条消失，不是改成另一种写法）。 */
    private val rawPathFallback = Regex("""\?:\s*data""")

    /** 禁止形状：解析器直接拿到未经解释的 `data`（`C` 会被当成立方贝塞尔命令）。 */
    private val parserReadsRawData = Regex("""parsePathString\(\s*data\s*\)""")

    /** 装饰性字形：内容描述留给调用方语义，渲染层不给 TalkBack 念路径名。 */
    private val decorativeGlyph = Regex("""contentDescription = null,""")

    /** 引号内的字符串字面量（用来找「渲染码自己内联了几何」）。 */
    private val quotedLiteral = Regex("\"([^\"]*)\"")

    /** 几何串形状与 Task 6 的 svgPath 规则同源：以 `M`/`m` 起锚，后续只允许命令字母、数字、标点与空白。 */
    private val svgPathShape = Regex("""^[Mm][MmLlHhVvCcSsQqTtAaZz0-9eE.,\s-]{3,}$""")

    /** 源码文本 → 其中内联的 SVG path 字面量（空表 = 渲染码没有第二套几何）。 */
    private fun inlinedGeometryLiterals(text: String): List<String> =
        quotedLiteral.findAll(text).map { it.groupValues[1] }.filter { svgPathShape.matches(it) }.toList()

    // ---------- 用例：对比契约（计划 Step 1 的四组数，阈值与注释数值逐字保留） ----------

    /** B 案容器规则：渐变亮端 = deep 混 35% base；白字形在非文本口径下必须 ≥3:1。 */
    @Test
    fun white_glyph_on_every_domain_gradient_start_clears_3_to_1() {
        val white = 0xFFFFFF
        DomainLight.forEach { (domain, p) ->
            val start = mixHex(argbOf(p.deep), argbOf(p.base), 0.35)
            val r = ratio(white, start)
            assertTrue(r >= 3.0, "$domain 容器亮端 #%06X 上白字形 %.2f:1 不足 3:1".format(start and 0xFFFFFF, r))
        }
        // 反空转：同一条比值口径必须判得出「直接用 base 作底」不合格，否则 3.0 这条线是摆设。
        assertTrue(
            DomainLight.any { (_, p) -> ratio(white, rgbOf(p.base)) < 3.0 },
            "公式或阈值失效：没有任何一枚 base 作白字底是不合格的"
        )
    }

    @Test
    fun base_as_white_glyph_backdrop_is_rejected() {
        // 反证：直接用 base 作底正是 spec §2.1 点名的失败形状（AI 2.11 / 复习 2.06）
        listOf("AiSpeech" to 0xFF2EC99C.toInt(), "Review" to 0xFFFF9F0A.toInt()).forEach { (name, base) ->
            assertTrue(ratio(0xFFFFFF, base) < 3.0, "$name 的 base 作白字底本应不合格；规则需重议")
        }
    }

    @Test
    fun inline_deep_glyph_on_tint_clears_3_to_1() {
        // 底色写的是视觉稿 §4 行内 chip 的参考底（该值与浅套真实 tint `AppColorLight.tint` 相同）；
        // 读活符号的那条配对由 inline_deep_glyph_on_live_light_tint_token_clears_3_to_1 复算，两者口径不同。
        DomainLight.forEach { (_, p) ->
            assertTrue(ratio(rgbOf(p.deep), 0xFFDDF7E8.toInt()) >= 3.0)   // 实测 3.55–8.06
        }
        // 反空转：换成同族的装饰性细线（浅套 outline #ADE7D2）必须掉到线下，否则本用例抓不住「取错字段」。
        assertTrue(
            ratio(0xADE7D2.toInt(), 0xFFDDF7E8.toInt()) < 3.0,
            "合成负例被放行：装饰性细线压 tint 竟也过 3:1（亮度口径失控）"
        )
    }

    @Test
    fun dark_container_with_dark_glyph_clears_3_to_1() {
        // 底色是视觉稿的深底容器色（`#06231A`），不是生产深套的 tint token（`AppColorDark.tint` 另有其值）；
        // 活 token 的那条配对见 dark_deep_glyph_on_live_dark_tint_token_clears_3_to_1。
        DomainDark.forEach { (_, p) ->
            assertTrue(ratio(rgbOf(p.deep), 0xFF06231A.toInt()) >= 3.0)   // 实测 5.96–9.82，最差危险域
        }
    }

    /**
     * 修复轮 M-1：浅套行内配对必须按**活 token** 复算——底色读 `AppColorLight.tint`，
     * 上面那条写死的 `0xDDF7E8` 只是稿面参考底（今天与 token 同值，token 一改就分家）。
     */
    @Test
    fun inline_deep_glyph_on_live_light_tint_token_clears_3_to_1() {
        val tint = rgbOf(AppColorLight.tint)
        DomainLight.forEach { (domain, p) ->
            val glyph = rgbOf(p.deep)
            val r = ratio(glyph, tint)
            assertTrue(
                r >= 3.0,
                "浅套 $domain 字形 #%06X 压活 token tint #%06X 只有 %.2f:1，不足非文本 3:1".format(glyph, tint, r)
            )
        }
        // 反空转：同一条公式判浅套装饰性细线（活 outline token）必须掉到线下，
        // 否则「过 3:1」是公式失效的假象，与本用例选的 deep 字段无关。
        assertTrue(
            ratio(rgbOf(AppColorLight.outline), tint) < 3.0,
            "合成负例被放行：浅套装饰线压活 tint 竟也过 3:1（亮度口径失控）"
        )
    }

    /** 修复轮 M-1：深套行内配对同样按活 token 复算（底色 `AppColorDark.tint`，不是稿面 `#06231A`）。 */
    @Test
    fun dark_deep_glyph_on_live_dark_tint_token_clears_3_to_1() {
        val tint = rgbOf(AppColorDark.tint)
        DomainDark.forEach { (domain, p) ->
            val glyph = rgbOf(p.deep)
            val r = ratio(glyph, tint)
            assertTrue(
                r >= 3.0,
                "深套 $domain 字形 #%06X 压活 token tint #%06X 只有 %.2f:1，不足非文本 3:1".format(glyph, tint, r)
            )
        }
        // 反空转：深套装饰线（活 outline token）压同一枚 tint 必须不合格——两枚都深，公式要判得出「没差别」。
        assertTrue(
            ratio(rgbOf(AppColorDark.outline), tint) < 3.0,
            "合成负例被放行：深套装饰线压活 tint 竟也过 3:1（亮度口径失控）"
        )
        // 反空转（口径核对）：稿面深底与活 token 不是同一枚值，本用例与上面那条字面量用例才不算重复。
        assertNotEquals(0xFF06231A.toInt() and 0xFFFFFF, tint, "深套 tint 已改成稿面值：两条用例合流，活 token 覆盖失去意义")
    }

    /**
     * 深色入口容器真正落地的配对：视觉稿在深色下把字形从白换成墨（`#06231A`），本工程
     * `AppColorSet` 无该字段（字段集是 Task 2 契约面，不得为渲染码扩容），生产取同族最深中性墨
     * `AppColorDark.onBrand`。本用例复算这一对，并反证白色在深套不合格（偏离因此有据）。
     */
    @Test
    fun dark_entry_ink_paired_with_domain_colors_clears_3_to_1() {
        val ink = rgbOf(AppColorDark.onBrand)
        DomainDark.forEach { (domain, p) ->
            val glyph = rgbOf(p.deep)
            val r = ratio(glyph, ink)
            assertTrue(
                r >= 3.0,
                "深套 $domain 字形 #%06X 压入口容器墨 #%06X 只有 %.2f:1，不足非文本 3:1".format(glyph, ink, r)
            )
            assertTrue(
                ratio(0xFFFFFF, glyph) < 3.0,
                "深套 $domain 上白字形竟已过 3:1：计划原文的「白色两层字形」在深色下无需偏离，裁决需重议"
            )
        }
    }

    // ---------- 用例：两版混色实现必须落在同一点 ----------

    @Test
    fun gradient_start_endpoints_match_the_independent_mix_channel_by_channel() {
        DomainLight.forEach { (domain, p) ->
            val expected = mixArgb(argbOf(p.deep), argbOf(p.base), 0.35)
            val actual = argbOf(gradientStartForDomain(p.deep, p.base))
            assertEquals((expected shr 16) and 0xFF, (actual shr 16) and 0xFF, "$domain 红通道偏离测试端独立复算")
            assertEquals((expected shr 8) and 0xFF, (actual shr 8) and 0xFF, "$domain 绿通道偏离测试端独立复算")
            assertEquals(expected and 0xFF, actual and 0xFF, "$domain 蓝通道偏离测试端独立复算")
            assertEquals((expected shr 24) and 0xFF, (actual shr 24) and 0xFF, "$domain alpha 通道偏离测试端独立复算")
            // 反空转：分数换成 0.5 的端点必须与 0.35 的端点不同，否则逐通道比对没有把关对象。
            assertNotEquals(
                expected,
                mixArgb(argbOf(p.deep), argbOf(p.base), 0.5),
                "合成负例失效：$domain 在 0.35 与 0.5 两档混出同一点（fraction 写错也测不出）"
            )
        }
        // 反空转：参数写反（拿 base 当主色）必须被同一条口径抓住，且生产函数必须辨得出顺序。
        val ai = DomainLight.getValue(AppDomain.AiSpeech)
        assertNotEquals(
            mixArgb(argbOf(ai.deep), argbOf(ai.base), 0.35),
            mixArgb(argbOf(ai.base), argbOf(ai.deep), 0.35),
            "合成负例失效：deep/base 顺序颠倒仍算同一点",
        )
        assertNotEquals(
            argbOf(gradientStartForDomain(ai.deep, ai.base)),
            argbOf(gradientStartForDomain(ai.base, ai.deep)),
            "生产函数不辨参数顺序：deep/base 写反不会红",
        )
    }

    @Test
    fun gradient_start_mixes_alpha_and_keeps_opaque_endpoints_opaque() {
        val deep = Color(0x80188F76)
        val base = Color(0xFF2EC99C)
        assertEquals(
            mixArgb(argbOf(deep), argbOf(base), 0.35),
            argbOf(gradientStartForDomain(deep, base)),
            "半透明端点的混色必须连 alpha 一起插值（alpha 被丢掉或被写死都会在这里爆）",
        )
        val opaque = argbOf(gradientStartForDomain(Color(0xFF188F76), Color(0xFF2EC99C)))
        assertEquals(0xFF, (opaque shr 24) and 0xFF, "两套域色都是不透明端点，混出的亮端也必须完全不透明")
    }

    // ---------- 用例：组装契约（修复轮 I-1：`CircleFill` 解释与两种层必须被真断言守住） ----------

    /** 路径串 → 解析节点：与生产同一个解析器，用来把「未解释的标记串进解析器」的真实后果变成可断言的事实。 */
    private fun nodesOf(pathData: String): List<PathNode> = PathParser().parsePathString(pathData).toNodes()

    /** 组装结果里的路径节点（`ImageVector` 是纯数据，JVM 侧直接读，不需要渲染运行时）。 */
    private fun pathsOf(vector: ImageVector): List<VectorPath> = collectPaths(vector.root)

    private fun collectPaths(group: VectorGroup): List<VectorPath> = buildList {
        group.forEach { node ->
            when (node) {
                is VectorPath -> add(node)
                is VectorGroup -> addAll(collectPaths(node))
            }
        }
    }

    /** 填充层：组装时只给 `fill`，不带 stroke；骨架层反之。层身份由画笔存在性区分，与书写顺序无关。 */
    private fun fillLayers(vector: ImageVector): List<VectorPath> = pathsOf(vector).filter { it.stroke == null }

    private fun strokeLayers(vector: ImageVector): List<VectorPath> = pathsOf(vector).filter { it.stroke != null }

    /**
     * 填充不透明度落在 `SolidColor` 的颜色 alpha 上（Compose 的 `Color` 是 8-bit 量化的），
     * 所以主题档比对按字节做，容得下 1/255 的量化差；档位抄错（0.42 写成 0.30 一类）差 30 个字节，照样判红。
     */
    private fun alphaMatchesTheme(path: VectorPath, isDark: Boolean): Boolean {
        val brush = path.fill as? SolidColor
            ?: fail("填充层画笔不是 SolidColor，主题档无从复算：${path.fill}")
        val actual = (brush.value.toArgb() shr 24) and 0xFF
        val expected = (DuotoneFillAlpha.forTheme(isDark) * 255f).roundToInt()
        return abs(actual - expected) <= 1
    }

    /** 圆形标记的展开产物必须是「两段圆弧闭合」，且极点坐标原样落在串里（不解释就画不出圆）。 */
    @Test
    fun circle_fill_marker_expands_to_a_closed_arc_through_the_vertical_poles() {
        val arc = circleFillPathData("CircleFill(11, 11, 6)")
        assertTrue(arc.startsWith("M "), "标记展开产物必须以 moveTo 起锚，实际：$arc")
        assertEquals(2, Regex("""[Aa]\s""").findAll(arc).count(), "两段首尾相接的圆弧才闭合成一个圆，实际：$arc")
        assertTrue(
            arc.contains("11") && arc.contains("5") && arc.contains("17"),
            "圆心与上下极点（11 / 5 / 17）必须原样出现在产物里，实际：$arc",
        )
        assertFalse(arc.contains("CircleFill"), "产物里仍留着标记串：说明解释分支根本没跑")
        assertTrue(nodesOf(arc).isNotEmpty(), "标记展开产物解析后没有节点：图标静默不画，$arc")

        // 反空转（合成负例）：同一个圆写成左右极点的相对弧时 17 不出现，本用例的极点判据必须判红。
        val leftRightRelative = "M 5 11 a 6 6 0 0 1 12 0 a 6 6 0 0 1 -12 0"
        assertFalse(leftRightRelative.contains("17"), "合成负例失控：左右极点写法竟也满足极点判据")
        // 反证（解释被整段删掉的形状）：标记串直接进 SVG 解析器时当场抛错——实测消息
        // `Unknown command for: i`（开头的 `C` 被当成立方贝塞尔命令），所以「回落」把失败推迟到运行时，
        // 而且消息里没有字形名与坐标，认不出坏在哪一枚。
        assertFailsWith<IllegalArgumentException> { nodesOf("CircleFill(11, 11, 6)") }
    }

    /** 小数半径必须按极点值落地（`Camera` 用的就是小数），整数截断在这里当场露出。 */
    @Test
    fun circle_fill_marker_keeps_the_decimal_radius_at_both_poles() {
        val arc = circleFillPathData("CircleFill(12, 13, 3.2)")
        assertTrue(
            arc.contains("9.8") && arc.contains("16.2"),
            "小数半径 3.2 的上下极点应写成 9.8 / 16.2（整数截断会在这里露出），实际：$arc",
        )
        assertTrue(nodesOf(arc).isNotEmpty(), "小数半径的圆弧解析后没有节点，$arc")

        val truncated = "M 12 9 A 3 3 0 0 1 12 16 A 3 3 0 0 1 12 9"
        assertFalse(
            truncated.contains("9.8") && truncated.contains("16.2"),
            "合成负例失控：截断成整数的极点也满足本用例判据",
        )
    }

    /** 坏标记必须响亮失败：`?: data` 那类回落等于把坏形状交给只会报「命令不认识」的解析器。 */
    @Test
    fun malformed_circle_fill_marker_fails_loudly_instead_of_being_handed_to_the_svg_parser() {
        listOf(
            "CircleFill(11, 11)",          // 少一段
            "CircleFill(11, 11, 6",        // 缺右括号
            "CircleFill(11, 11, x)",       // 非数字
            "CircleFill(1, 2, 3, 4)",      // 多一段
        ).forEach { bad ->
            val thrown = assertFailsWith<IllegalArgumentException> { circleFillPathData(bad) }
            // 失败必须可归因：消息里带上原始串，否则运行时只知道自己炸了、不知道哪一枚字形炸。
            assertTrue(
                thrown.message?.contains(bad) == true,
                "抛错消息没有带上坏标记原文，无法归因到具体圆形：message=${thrown.message}，输入=$bad",
            )
        }
        // 反空转：合法串不抛（否则上面四条红是「恒抛」而非「抓住坏串」）。
        val good = circleFillPathData("CircleFill(9, 9, 2)")
        assertTrue(good.contains("7") && good.contains("11"), "合法标记未被展开，实际：$good")
        // 反证：坏串在解析器那边炸的是解析器的消息（`Unknown command for: i`），不带原文与坐标——
        // 这正是回落分支的危害：形状错了却只能看到一条与几何无关的错。
        val parserThrown = assertFailsWith<IllegalArgumentException> { nodesOf("CircleFill(11, 11)") }
        assertTrue(
            parserThrown.message?.contains("CircleFill(11, 11)") == false,
            "反证失控：解析器错误消息已能指明原始串，回落分支就不至于无法归因",
        )
    }

    /** 层开关是组装期的真分支：Duotone 两层都在、StrokeOnly 只剩骨架。 */
    @Test
    fun assembled_vector_keeps_both_layers_for_duotone_and_only_the_skeleton_for_stroke_only() {
        val duotone = buildAppImageVector(AppIcon.Search, Color.White, 1f, AppGlyphLayer.Duotone, false)
        assertTrue(
            fillLayers(duotone).isNotEmpty() && fillLayers(duotone).all { it.pathData.isNotEmpty() },
            "Duotone 的填充层没有可画节点：标记解释被删掉就是这一条红",
        )
        assertTrue(
            strokeLayers(duotone).isNotEmpty() && strokeLayers(duotone).all { it.pathData.isNotEmpty() },
            "Duotone 的骨架层没有可画节点：字形不会画出来",
        )

        val strokeOnly = buildAppImageVector(AppIcon.Search, Color.White, 1f, AppGlyphLayer.StrokeOnly, false)
        assertTrue(fillLayers(strokeOnly).isEmpty(), "StrokeOnly 仍长出填充层：30dp 行内的单层口径失效")
        assertTrue(strokeLayers(strokeOnly).all { it.pathData.isNotEmpty() }, "StrokeOnly 的骨架为空：行内字形不会画出来")

        // 反空转（合成反例）：一条 pathData 为空的填充路径必须让同一个 all{} 判据变红，否则 all{} 是空转。
        val emptyFill = ImageVector.Builder(
            name = "SyntheticEmptyFill",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = emptyList(), fill = SolidColor(Color.White)).build()
        assertEquals(1, fillLayers(emptyFill).size, "合成反例失控：空 pathData 的填充路径没被识别成填充层")
        assertFalse(fillLayers(emptyFill).all { it.pathData.isNotEmpty() }, "合成反例失控：零节点填充被判为合格")
        // 反空转：把 layer 实参写错成 Duotone 的调用形状（行内误用两层）必须由填充层断言抓住。
        assertEquals(1, fillLayers(duotone).size, "Search 的填充层数量与冻结数据不符（两条填充层就是两套口径）")
    }

    /** 33 枚全遍历：删掉标记解释、改描边重量、改主题档，都会在这里变红。 */
    @Test
    fun every_glyph_builds_the_skeleton_and_fill_with_pinned_stroke_weight_and_theme_alpha() {
        listOf(1f, 1.5f).forEach { strokeScale ->
            listOf(false, true).forEach { isDark ->
                AppIcon.entries.forEach { icon ->
                    val duotone = buildAppImageVector(icon, Color.White, strokeScale, AppGlyphLayer.Duotone, isDark)
                    val skeleton = strokeLayers(duotone)
                    assertTrue(
                        skeleton.isNotEmpty() && skeleton.all { it.pathData.isNotEmpty() },
                        "$icon 在 strokeScale=$strokeScale / isDark=$isDark 下骨架层没有可画节点",
                    )
                    skeleton.forEach { path ->
                        assertEquals(
                            2f * strokeScale,
                            path.strokeLineWidth,
                            "$icon 的描边重量不等于 2f * strokeScale（视觉稿的度量基准）",
                        )
                    }
                    val fills = fillLayers(duotone)
                    if (icon in StrokeOnlyIcons) {
                        assertTrue(fills.isEmpty(), "$icon 属两层豁免表，却在 Duotone 档长出填充层")
                    } else {
                        assertTrue(
                            fills.isNotEmpty() && fills.all { it.pathData.isNotEmpty() },
                            "$icon 的填充层没有可画节点：CircleFill 解释被删掉就是这一条红",
                        )
                        fills.forEach { path ->
                            assertTrue(
                                alphaMatchesTheme(path, isDark),
                                "$icon 的填充不透明度不等于 DuotoneFillAlpha.forTheme($isDark)",
                            )
                        }
                    }

                    val strokeOnly = buildAppImageVector(icon, Color.White, strokeScale, AppGlyphLayer.StrokeOnly, isDark)
                    assertTrue(
                        strokeLayers(strokeOnly).all { it.pathData.isNotEmpty() },
                        "$icon 的 StrokeOnly 档骨架为空（strokeScale=$strokeScale / isDark=$isDark）",
                    )
                    assertTrue(fillLayers(strokeOnly).isEmpty(), "$icon 的 StrokeOnly 档仍带填充层")
                }
            }
        }
        // 反空转（合成反例）：浅色矢量拿到深色档、或主题档整体抄错，都必须被同一个判据抓住。
        val search = buildAppImageVector(AppIcon.Search, Color.White, 1f, AppGlyphLayer.Duotone, false)
        val searchFill = fillLayers(search).first()
        assertFalse(alphaMatchesTheme(searchFill, true), "合成反例失控：浅色矢量被判为深色档（forTheme 不辨主题）")
        // 反证：未解释的标记串进不了画布（解析器当场抛 `Unknown command for: i`），
        // 所以「33 枚遍历 + 节点非空」正是删除解释的回归锁：解释一没，遍历立刻炸。
        assertFailsWith<IllegalArgumentException> { nodesOf("CircleFill(12, 13, 3.2)") }
    }

    // ---------- 用例：豁免表强制（计划 Step 4 要求的纯函数判定） ----------

    @Test
    fun entry_badge_rejects_stroke_only_glyphs() {
        StrokeOnlyIcons.forEach { icon ->
            assertFalse(allowsInEntryContainer(icon), "$icon 属两层豁免表，只作行内/尾部指示，不得进入 ≥40dp 入口容器")
        }
        AppIcon.entries.filterNot { it in StrokeOnlyIcons }.forEach { icon ->
            assertTrue(allowsInEntryContainer(icon), "$icon 不在豁免表里，入口容器没有理由拒它")
        }
        // 上面两条是双向循环：判定恒真被第一条抓住、恒假被第二条抓住；前提本身也要在场，
        // 否则第一条循环空转（豁免表被清空时「入口拒绝豁免枚」这条契约就失去对象）。
        assertTrue(StrokeOnlyIcons.isNotEmpty(), "豁免表为空：入口容器的拒绝契约没有对象，循环空转")
        assertTrue(
            StrokeOnlyIcons.size < AppIcon.entries.size,
            "豁免表覆盖了全部枚举：非豁免循环空转，判定恒假也能过",
        )
    }

    /** 豁免枚的路径数据本身必须是裸描边：入口判定函数与两层口径不能各说各话。 */
    @Test
    fun exempt_glyphs_really_have_no_fill_layer_to_hide() {
        StrokeOnlyIcons.forEach { icon ->
            assertEquals(
                emptyList<String>(),
                icon.spec.fillPaths,
                "$icon 在豁免表里却带填充层：行内降级成单层描边这条呈现规则失去依据",
            )
        }
        // 反空转：非豁免枚必须至少一条填充层，否则「填充层不显示」是句空话。
        val duotone = AppIcon.entries.first { it !in StrokeOnlyIcons }
        assertTrue(duotone.spec.fillPaths.isNotEmpty(), "$duotone 没有填充层：行内的单层降级没有可关的对象")
    }

    // ---------- 用例：源码正则契约（计划 Step 1 追加的两条 + 几何与渲染码口径） ----------

    @Test
    fun entry_gradient_start_is_produced_by_the_domain_rule_not_a_raw_base_endpoint() {
        val badge = uiComponentSource(BADGE_SOURCE_NAME)
        assertEquals(1, countMatches(badge, gradientFromRule), "入口容器渐变必须恰好一处、且亮端来自 gradientStartForDomain")
        assertEquals(0, countMatches(badge, rawBaseEndpoint), "渐变端点里出现裸 .base：白字形会掉到 2.11 / 2.06（spec §2.1）")

        val good = """
            val brush = Brush.linearGradient(
                listOf(gradientStartForDomain(palette.deep, palette.base), palette.deep)
            )
        """.trimIndent()
        assertEquals(1, countMatches(good, gradientFromRule), "合成正片段漏判：规则渐变形状抓不住")
        assertEquals(0, countMatches(good, rawBaseEndpoint), "合成正片段越界：合法渐变被判成裸 base 端点")

        val bad = "val brush = Brush.linearGradient(listOf(palette.base, palette.deep))"
        assertEquals(0, countMatches(bad, gradientFromRule), "合成负片段被放行：绕过域名规则的渐变没被抓")
        assertEquals(1, countMatches(bad, rawBaseEndpoint), "合成负片段被放行：裸 base 端点没被数出来")
    }

    @Test
    fun inline_badge_glyph_shows_the_stroke_layer_only() {
        val badge = uiComponentSource(BADGE_SOURCE_NAME)
        assertEquals(1, countMatches(badge, strokeOnlyGlyphCall), "行内 17dp 字形必须显式取描边-only 层")
        assertEquals(0, countMatches(badge, strokeOnlyOnEntryGlyph), "入口 24dp 字形被降级成单层：两种呈现坍缩成一种")
        assertEquals(
            1,
            countMatches(badge, Regex("""AppGlyph\(icon = icon, tint = ink, size = 24\.dp\)""")),
            "入口字形必须保持「不传 layer 实参」的两层默认（写死 layer = Duotone 也算改口径）",
        )

        val good = "AppGlyph(icon = icon, tint = palette.deep, size = 17.dp, layer = AppGlyphLayer.StrokeOnly)"
        assertEquals(1, countMatches(good, strokeOnlyGlyphCall), "合成正片段漏判：行内单层形状抓不住")
        val unlayered = "AppGlyph(icon = icon, tint = palette.deep, size = 17.dp)"
        assertEquals(0, countMatches(unlayered, strokeOnlyGlyphCall), "合成负片段被放行：行内没关填充层也算合格")
        val wrongBadge = "AppGlyph(icon = icon, tint = ink, size = 24.dp, layer = AppGlyphLayer.StrokeOnly)"
        assertEquals(1, countMatches(wrongBadge, strokeOnlyOnEntryGlyph), "合成负片段被放行：入口字形被降级没被抓")
        val explicitDuotone = "AppGlyph(icon = icon, tint = ink, size = 24.dp, layer = AppGlyphLayer.Duotone)"
        assertEquals(
            0,
            countMatches(explicitDuotone, entryGlyphDefaultCall),
            "合成负片段被放行：入口显式写 layer 也当成「保持默认两层」",
        )
    }

    @Test
    fun badge_containers_use_the_ruled_geometry_and_glass_border() {
        val badge = uiComponentSource(BADGE_SOURCE_NAME)
        assertEquals(1, countMatches(badge, entryContainerSize), "入口容器必须是 40dp（≥40dp 入口档位由本尺寸承载）")
        assertEquals(1, countMatches(badge, inlineContainerSize), "行内容器必须是 30dp")
        assertEquals(1, countMatches(badge, inlineTintBackdrop), "行内底色必须取主题 tint，不得抄浅绿字面量")
        assertEquals(1, countMatches(badge, entryShapeClip), "入口容器裁角必须恰好一处 clip(AppShape.Icon)（12dp 档）")
        assertEquals(1, countMatches(badge, inlineShapeClip), "行内徽标必须裁在 clip(AppShape.Inline)（9dp 档）")
        assertEquals(1, countMatches(badge, glassHighlightBorder), "入口容器的 inset 高光必须用 1dp AppPalette.GlassHighlight 描边")
        assertEquals(1, countMatches(badge, entryRequire), "入口容器缺豁免表强制判定")
        assertEquals(1, countMatches(badge, entryRequireMessage), "入口容器的拒绝理由没按计划原文写全")

        val good = """
            Box(
                modifier = Modifier.size(40.dp).clip(AppShape.Icon).background(brush)
                    .border(1.dp, AppPalette.GlassHighlight, AppShape.Icon),
            )
            Box(modifier = Modifier.size(30.dp).clip(AppShape.Inline).background(appColors().tint))
            require(allowsInEntryContainer(icon)) { "X 属两层豁免表，只作行内/尾部指示，不得进入 ≥40dp 入口容器" }
        """.trimIndent()
        assertEquals(1, countMatches(good, entryContainerSize), "合成正片段漏判：40dp 容器")
        assertEquals(1, countMatches(good, inlineContainerSize), "合成正片段漏判：30dp 容器")
        assertEquals(1, countMatches(good, entryShapeClip), "合成正片段漏判：入口一处 AppShape.Icon 裁角")
        assertEquals(1, countMatches(good, inlineShapeClip), "合成正片段漏判：行内一处 AppShape.Inline 裁角")
        assertEquals(1, countMatches(good, glassHighlightBorder), "合成正片段漏判：玻璃高光描边")
        assertEquals(1, countMatches(good, entryRequire), "合成正片段漏判：require 判定")

        val bad = "Box(modifier = Modifier.size(36.dp).clip(AppShape.Card).background(AppPalette.Tint))"
        assertEquals(0, countMatches(bad, entryContainerSize), "合成负片段被放行：非 40dp 容器没被抓")
        assertEquals(0, countMatches(bad, inlineTintBackdrop), "合成负片段被放行：底色取自静态 AppPalette 没被抓")
        assertEquals(0, countMatches(bad, entryShapeClip), "合成负片段被放行：圆角用了别的档位没被抓")
        // 两档合并（行内也裁 Icon 档）必须让 inlineShapeClip 归零：这正是「共用一档」的错误表述落成代码的形状。
        val mergedTier = "Box(modifier = Modifier.size(30.dp).clip(AppShape.Icon).background(appColors().tint))"
        assertEquals(0, countMatches(mergedTier, inlineShapeClip), "合成负片段被放行：行内复用入口档没被抓")
        assertEquals(1, countMatches(mergedTier, entryShapeClip), "合成负片段失控：档位在文本里都不存在")
    }

    /** 两档圆角的数值本身（源码文本即契约，不渲染）：12dp 与 9dp 各恰一处，谁被改掉都红。 */
    @Test
    fun app_shape_pins_the_entry_and_inline_corner_tiers_as_separate_values() {
        val shape = uiThemeSource(APP_SHAPE_SOURCE_NAME)
        assertEquals(1, countMatches(shape, iconCornerValue), "入口档数值 12dp 必须恰一处（视觉稿 ≥40px 档）")
        assertEquals(1, countMatches(shape, inlineCornerValue), "行内档数值 9dp 必须恰一处（视觉稿行内 chip 档）")
        assertEquals(1, countMatches(shape, iconCornerTier), "12dp 必须仍挂在 `AppShape.Icon` 槽位上")
        assertEquals(1, countMatches(shape, inlineCornerTier), "9dp 必须仍挂在 `AppShape.Inline` 槽位上")

        val good = """
            val Icon: Shape = RoundedCornerShape(12.dp)
            val Inline: Shape = RoundedCornerShape(9.dp)
        """.trimIndent()
        assertEquals(1, countMatches(good, iconCornerValue), "合成正片段漏判：12dp 数值")
        assertEquals(1, countMatches(good, inlineCornerValue), "合成正片段漏判：9dp 数值")
        assertEquals(1, countMatches(good, iconCornerTier), "合成正片段漏判：12dp 挂在 Icon 槽")
        assertEquals(1, countMatches(good, inlineCornerTier), "合成正片段漏判：9dp 挂在 Inline 槽")

        // 「值被改掉就红」：行内档被并进 12dp（评审 I-4 复算的失败形状：两档合一）。
        val inlined = "val Icon: Shape = RoundedCornerShape(12.dp)\nval Inline: Shape = RoundedCornerShape(12.dp)"
        assertEquals(0, countMatches(inlined, inlineCornerValue), "合成负片段被放行：9dp 被改成 12dp 还算合格")
        assertEquals(0, countMatches(inlined, inlineCornerTier), "合成负片段被放行：合并后的行内档仍被判成独立档")
        assertEquals(2, countMatches(inlined, iconCornerValue), "合成负片段失控：同值两处都数不出来")
        // 「值被改掉就红」：入口档被写歪成别的数值。
        val entryMoved = "val Icon: Shape = RoundedCornerShape(10.dp)\nval Inline: Shape = RoundedCornerShape(9.dp)"
        assertEquals(0, countMatches(entryMoved, iconCornerValue), "合成负片段被放行：12dp 被改成别的值还算合格")
        assertEquals(0, countMatches(entryMoved, iconCornerTier), "合成负片段被放行：Icon 槽位数值漂移没被抓")
        assertEquals(1, countMatches(entryMoved, inlineCornerValue), "合成负片段失控：这段里行内档都不在场")
        // 「槽位写反就红」：两档数值都在场，但挂错了名字。
        val swapped = "val Icon: Shape = RoundedCornerShape(9.dp)\nval Inline: Shape = RoundedCornerShape(12.dp)"
        assertEquals(1, countMatches(swapped, iconCornerValue), "合成负片段失控：数值计数在写反片段里失灵")
        assertEquals(0, countMatches(swapped, iconCornerTier), "合成负片段被放行：两档写反槽位还算合格")
        assertEquals(0, countMatches(swapped, inlineCornerTier), "合成负片段被放行：Inline 拿了 12dp 还算合格")
    }

    @Test
    fun glyph_assembly_reads_theme_alpha_caches_by_inputs_and_scales_the_stroke() {
        val glyph = uiComponentSource(GLYPH_SOURCE_NAME)
        assertEquals(
            1,
            countMatches(glyph, themedFillAlphaRead),
            "填充层不透明度必须问 DuotoneFillAlpha.forTheme：浅 0.42 / 深 0.30 是主题档，不是渲染码里的字面量",
        )
        assertTrue(
            countMatches(glyph, cacheKeyedOnInputs) >= 1,
            "组装结果未按 icon/tint/strokeScale 缓存：每次组合都要重建整枚路径节点表",
        )
        assertEquals(1, countMatches(glyph, scaledStrokeWidth), "描边重量必须是 2f 随 strokeScale 缩放")
        assertEquals(
            2,
            countMatches(glyph, circleFillInterpretation),
            "圆形标记必须经过解释函数（定义 1 处 + 调用 1 处）：恒真的「文本里出现过 CircleFill」不算契约",
        )
        assertEquals(0, countMatches(glyph, rawPathFallback), "解释失败仍在回落给 SVG 解析器：坏标记会被静默吞掉")
        assertEquals(0, countMatches(glyph, parserReadsRawData), "解析器直接拿到未解释的 data：圆形填充不再经过解释分支")
        assertTrue(countMatches(glyph, decorativeGlyph) >= 1, "字形 Image 必须声明装饰性（contentDescription = null,）")

        val good = """
            val alpha = DuotoneFillAlpha.forTheme(isDark)
            return remember(icon, tint, strokeScale, layer, isDark) {
                addPath(
                    pathData = nodes(if (isCircleFillMarker(data)) circleFillPathData(data) else data),
                    strokeLineWidth = 2f * strokeScale,
                )
            }
            Image(imageVector = vector, contentDescription = null, modifier = modifier)
        """.trimIndent()
        assertEquals(1, countMatches(good, themedFillAlphaRead), "合成正片段漏判：主题化不透明度读点")
        assertEquals(1, countMatches(good, cacheKeyedOnInputs), "合成正片段漏判：按输入缓存")
        assertEquals(1, countMatches(good, scaledStrokeWidth), "合成正片段漏判：缩放描边")
        assertEquals(1, countMatches(good, circleFillInterpretation), "合成正片段漏判：标记解释调用")
        assertEquals(0, countMatches(good, rawPathFallback), "合成正片段越界：合法分支被判成回落")
        assertEquals(0, countMatches(good, parserReadsRawData), "合成正片段越界：经过解释的串被判成未解释")
        assertEquals(1, countMatches(good, decorativeGlyph), "合成正片段漏判：装饰性声明")

        val bad = """
            val alpha = 0.42f
            return remember(spec) { build(strokeLineWidth = 2f) }
            fun pathNodesOf(data: String) = parsePathString(circleFillPathData(data) ?: data).toNodes()
            Image(imageVector = vector, contentDescription = "学习", modifier = modifier)
        """.trimIndent()
        assertEquals(0, countMatches(bad, themedFillAlphaRead), "合成负片段被放行：把主题档抄成字面量没被抓")
        assertEquals(0, countMatches(bad, cacheKeyedOnInputs), "合成负片段被放行：缓存键不含输入参数没被抓")
        assertEquals(0, countMatches(bad, scaledStrokeWidth), "合成负片段被放行：描边不随 strokeScale 缩放没被抓")
        assertEquals(0, countMatches(bad, decorativeGlyph), "合成负片段被放行：装饰性没声明没被抓")
        assertEquals(1, countMatches(bad, rawPathFallback), "合成负片段被放行：`?: data` 回落没被数出来")
        assertEquals(1, countMatches(bad, circleFillInterpretation), "合成负片段失控：回落片段里连解释调用都不在场")

        val parserBad = "fun pathNodesOf(data: String) = parsePathString(data).toNodes()"
        assertEquals(1, countMatches(parserBad, parserReadsRawData), "合成负片段被放行：未解释的 data 直接进解析器没被抓")
        assertEquals(0, countMatches(parserBad, circleFillInterpretation), "合成负片段失控：这条片段里没有解释函数")
    }

    /** 渲染码不得反过来长出几何：Task 6 的路径数据是唯一字形源（本用例钉「渲染端零枚内联路径」）。 */
    @Test
    fun glyph_assembly_adds_no_geometry_of_its_own() {
        assertEquals(
            emptyList(),
            inlinedGeometryLiterals(uiComponentSource(GLYPH_SOURCE_NAME)),
            "AppIcon.kt 内联了 SVG path 字面量：几何必须只来自 AppIcons.kt（Task 6 冻结源，渲染端无权改坐标）",
        )
        // 反空转 + 不越界：真实几何串必须被抓，标记串前缀与消息文本必须不被误抓。
        assertEquals(
            listOf("M11 5a6 6 0 010 12"),
            inlinedGeometryLiterals("""val strokePaths = listOf("M11 5a6 6 0 010 12", "CircleFill(11, 11, 6)")"""),
            "合成片段失控：内联几何没被抓，或 CircleFill 标记被误抓",
        )
        assertEquals(
            emptyList(),
            inlinedGeometryLiterals("val prefix = \"CircleFill\""),
            "合成负片段被误判：标记串前缀被当成内联几何",
        )
    }

    private companion object {
        const val BADGE_SOURCE_NAME = "IconBadge.kt"
        const val GLYPH_SOURCE_NAME = "AppIcon.kt"
        const val APP_SHAPE_SOURCE_NAME = "AppShape.kt"
    }
}
