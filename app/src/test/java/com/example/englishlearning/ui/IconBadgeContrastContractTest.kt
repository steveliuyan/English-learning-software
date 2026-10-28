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
import com.example.englishlearning.ui.components.AccentlessIcons
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.DuotoneFillAlpha
import com.example.englishlearning.ui.components.StrokeOnlyIcons
import com.example.englishlearning.ui.components.accentInkForGradient
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
 * Task 10 在同一文件里给入口容器补了第二档强度（`EntryIconTone`，裁决 R-10.2）并把入口字形对齐稿的
 * 22dp（裁决 R-10.3），故本类的入口对比段扩成 7 域 × 2 档 × 2 主题共 28 个组合复算
 * （[both_entry_tones_clear_3_to_1_in_both_theme_sets]），入口形状段按档重写
 * （[entry_glyph_shapes_are_pinned_tone_by_tone]）。
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
 *    删掉标记解释也不会有任何一条红）。入口字形那条文本钉在修复轮 2 改成**否定式**：
 *    [entryGlyphCallSegment] 只负责找出 22dp 调用整段（允许批 2 追加合法实参），
 *    口径按档分开把守（Task 10 R-10.3 重写，见 [entry_glyph_shapes_are_pinned_tone_by_tone]）：
 *    弱档那处**必须**显式单层（[entryStrokeOnlyCall]），实底档那处 `layer =` 出现 0 次
 *    （[explicitLayerArgument]）——旧版「入口一律不得显式 layer」的口径在有了弱档之后正好反了。
 *    Task 27（裁决 22-2）再把弱档底从「`surface` 混 16% deep」改为活 token `appColors().tint`：
 *    28 个组合的弱档那一半改按 `deep` 压 `tint` 复算，另加一条源码钉
 *    （[entry_tint_tone_uses_the_shared_tint_token_not_a_per_domain_mix]）把旧混色写成禁止形状。
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

    /**
     * 逐通道乘 `(1 - fraction)`（强调层压暗规则的测试端独立实现，与生产 `IconBadge.darkenArgb` 零共享）。
     * alpha 恒置满：强调层必须不透明，这条在 [accent_ink_stays_opaque_whatever_the_domain_color_is] 里单独钉。
     */
    private fun darkenArgb(from: Int, fraction: Double): Int {
        fun channel(shift: Int): Int {
            val v = (from shr shift) and 0xFF
            return (v * (1 - fraction)).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * 半透明前景压不透明底：`fg` 权重 `alpha`、`bg` 权重 `1 - alpha`，字节域四舍五入，返回 RGB。
     * 底幅是「不透明墨按主题 α 稀释后压在容器底上」，屏幕看到的正是这一层合成色，
     * 强调层与底幅的比值必须按合成后的颜色算——拿未经稀释的墨去比会高估一倍。
     */
    private fun overArgb(fg: Int, alpha: Double, bg: Int): Int {
        fun channel(shift: Int): Int {
            val f = (fg shr shift) and 0xFF
            val b = (bg shr shift) and 0xFF
            return (f * alpha + b * (1 - alpha)).roundToInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * 强调层的压暗比重，必须与生产 `IconBadge.kt` 的 `ACCENT_DARKEN_FRACTION` 同值。
     * 与 [gradientStartFraction] 同一口径：本类**有意**走第二份实现，一侧漂移由
     * [accent_ink_is_produced_by_the_darken_rule_channel_by_channel] 判红，两侧一起改由
     * [accent_darken_fraction_is_pinned_to_the_three_to_one_solved_value] 按源码字面量判红。
     */
    private val accentDarkenFraction = 0.22

    /**
     * 入口容器亮端的混比，必须与生产 `IconBadge.kt` 的 `GRADIENT_START_FRACTION` 同值：
     * 本类**有意**走第二份混色实现（[mixArgb]），所以这里再抄一个分数不算「两面同源」——
     * 一侧漂移会让 [gradient_start_endpoints_match_the_independent_mix_channel_by_channel] 当场红，
     * 两侧一起改则由 [gradient_start_fraction_is_pinned_to_the_relaxed_palette_value] 按源码字面量钉红。
     * 2026-10-27 轻松档：0.35 → 0.18，容器从「一块彩色渐变」变成「近乎纯色」。
     */
    private val gradientStartFraction = 0.18

    // ---------- 读取与守卫 ----------

    /**
     * 按文件名在 `ui/theme/` 取剥注释源码（`AppShape.kt` 的两档圆角数值归这里）。
     *
     * M-3（Task 11 复评结转、Task 12 兑现）：组件目录那一份读取入口已提升为 `SourceRatchet.kt` 的
     * [uiComponentSource]，「文件恰一个 + 目录守卫」两条断言只在那一份里；本类不再留私有副本，
     * 只按共享夹具开放的 `dirFragment` 形参再开一个主题目录别名。
     */
    private fun uiThemeSource(name: String): String = uiComponentSource(name, "/ui/theme/")

    // ---------- 规则形状 ----------

    /** 入口容器渐变：亮端必须由域名规则现算，且整份文件只此一处渐变（第二处就是第二套容器口径）。 */
    private val gradientFromRule = Regex("""linearGradient\(\s*listOf\(\s*gradientStartForDomain\(""")

    /** 禁止形状：把域 `base` 直接当渐变端点（spec §2.1 点名的失败形状，白字形只有 2.11 / 2.06）。 */
    private val rawBaseEndpoint = Regex("""listOf\(\s*[A-Za-z_][\w.]*\.base\s*,""")

    /** 行内徽标：17dp 字形必须显式只要描边层——填充层在 30dp 浅底上没有信息量，只会把字形糊成一坨。 */
    private val strokeOnlyGlyphCall = Regex("""size\s*=\s*17\.dp\s*,\s*layer\s*=\s*AppGlyphLayer\.StrokeOnly""")

    /**
     * 入口族字形的 `AppGlyph` 调用**整段**（稿 `.ic svg { width:22px; height:22px }`，`mockups.html:153`，
     * R-10.3 把 Task 7 出厂的 24dp 对齐成 22dp）——本文件唯一的入口形状真值，源码断言只走这一条：
     * 只负责「把那段调用找出来」，允许批 2 接线时增补合法实参（`modifier = …`、`strokeScale = …`）与换序，
     * 实参里再嵌一层括号（`Modifier.padding(4.dp)` 一类）也不截断；行内的 17dp 调用不含 `size = 22.dp`，
     * 因此不会被认成入口段。**两档各一枚，共 2 处**（R-10.2：实底渐变档 + 弱档 tint）。
     */
    private val entryGlyphCallSegment =
        Regex("""AppGlyph\((?:[^()]|\([^()]*\))*?size\s*=\s*22\.dp(?:[^()]|\([^()]*\))*?\)""")

    /**
     * 弱档形状真值：22dp 且**显式**只要描边层（稿 `.ic-tint` 的字形就是 `deep` 的单层描边）。
     * 这是 R-10.3 推翻旧禁令后的正向锚点——旧 `strokeOnlyOnEntryGlyph` 把这一形状当禁止写法，
     * 口径已经反了，故连同 `24.dp` 锚点一并删除，不留死正则。
     */
    private val entryStrokeOnlyCall =
        Regex("""size\s*=\s*22\.dp\s*,\s*layer\s*=\s*AppGlyphLayer\.StrokeOnly""")

    /**
     * 禁止形状（换序留痕）：把 `layer` 写在前、`size` 写在后的弱档调用。R-10.3 要求入口形状「两档各恰一处」，
     * 同一处写法换个个儿就不该被 [entryStrokeOnlyCall] 数到——本用例靠这条合成负片段自证锚点真在按顺序承重
     * （变异自证：把生产弱档的 `layer = StrokeOnly` 删掉，计数当场红）。
     */
    private val strokeOnlyBeforeSize =
        Regex("""layer\s*=\s*AppGlyphLayer\.StrokeOnly[^()]*size\s*=\s*22\.dp""")

    /** 禁止形状：入口族之外的旧 24dp 锚点串残留（字形度量必须只有 22dp 一档，Task 10 R-10.3）。 */
    private val legacyEntryGlyphSize = Regex("""size\s*=\s*24\.dp""")

    /** 禁止形状：入口实底档调用段里显式写 `layer =`——三层口径靠默认值承载，写死即改口径。 */
    private val explicitLayerArgument = Regex("""layer\s*=""")

    /** 豁免表强制：入口容器开头必须由纯函数判定挡住豁免枚。 */
    private val entryRequire = Regex("""require\(allowsInEntryContainer\(icon\)\)""")

    private val entryRequireMessage = Regex("""不得进入 ≥40dp 入口容器""")

    private val entryContainerSize = Regex("""size\(40\.dp\)""")

    private val inlineContainerSize = Regex("""size\(30\.dp\)""")

    /** 行内容器底色必须取主题 tint（深浅两套随主题翻，写死浅绿就是第二套真值）。 */
    private val inlineTintBackdrop = Regex("""background\(appColors\(\)\.tint\)""")

    /**
     * 裁决 22-2 退役的弱档底色写法：逐域 `lerp(surface, deep, 0.16f)`。
     * 正则不锚分数（0.16 / 0.08 / 0.10 都是同一套「按域混色」口径），换个数继续混也要抓住。
     */
    private val retiredEntryTintMix = Regex("""lerp\(colors\.surface,\s*palette\.deep,""")

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

    /** 强调层压暗比重（裁决 D1）：0.22 是「压暗后过 3:1」反推出来的最小整数档，按源码字面量钉死。 */
    private val accentDarkenRule = Regex("""private const val ACCENT_DARKEN_FRACTION = 0\.22\b""")

    /** 入口实底档必须把强调墨真正传给字形：参数接进去却没用画笔画，等于第三层不存在。 */
    private val entrySolidAccentArgument = Regex("""accent\s*=\s*accentInk""")

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

    /** 入口族 22dp 调用的整段命中（两档各一枚）：段内再做分档判定，源码断言与本用例片段共用同一条正则。 */
    private fun entryGlyphCallSegments(text: String): List<String> =
        entryGlyphCallSegment.findAll(text).map { it.value }.toList()

    // ---------- 用例：对比契约（计划 Step 1 的四组数，阈值与注释数值逐字保留） ----------

    /** B 案容器规则：渐变亮端 = deep 混 35% base；白字形在非文本口径下必须 ≥3:1。 */
    @Test
    fun white_glyph_on_every_domain_gradient_start_clears_3_to_1() {
        val white = 0xFFFFFF
        DomainLight.forEach { (domain, p) ->
            val start = mixHex(argbOf(p.deep), argbOf(p.base), gradientStartFraction)
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
            val expected = mixArgb(argbOf(p.deep), argbOf(p.base), gradientStartFraction)
            val actual = argbOf(gradientStartForDomain(p.deep, p.base))
            assertEquals((expected shr 16) and 0xFF, (actual shr 16) and 0xFF, "$domain 红通道偏离测试端独立复算")
            assertEquals((expected shr 8) and 0xFF, (actual shr 8) and 0xFF, "$domain 绿通道偏离测试端独立复算")
            assertEquals(expected and 0xFF, actual and 0xFF, "$domain 蓝通道偏离测试端独立复算")
            assertEquals((expected shr 24) and 0xFF, (actual shr 24) and 0xFF, "$domain alpha 通道偏离测试端独立复算")
            // 反空转：分数换成 0.5 的端点必须与生产分数（[gradientStartFraction]）的端点不同，
            // 否则逐通道比对没有把关对象。
            assertNotEquals(
                expected,
                mixArgb(argbOf(p.deep), argbOf(p.base), 0.5),
                "合成负例失效：$domain 在 $gradientStartFraction 与 0.5 两档混出同一点（fraction 写错也测不出）"
            )
        }
        // 反空转：参数写反（拿 base 当主色）必须被同一条口径抓住，且生产函数必须辨得出顺序。
        val ai = DomainLight.getValue(AppDomain.AiSpeech)
        assertNotEquals(
            mixArgb(argbOf(ai.deep), argbOf(ai.base), gradientStartFraction),
            mixArgb(argbOf(ai.base), argbOf(ai.deep), gradientStartFraction),
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
        val deep = Color(0x801A7A63)
        val base = Color(0xFF6CCBAB)
        assertEquals(
            mixArgb(argbOf(deep), argbOf(base), gradientStartFraction),
            argbOf(gradientStartForDomain(deep, base)),
            "半透明端点的混色必须连 alpha 一起插值（alpha 被丢掉或被写死都会在这里爆）",
        )
        val opaque = argbOf(gradientStartForDomain(Color(0xFF1A7A63), Color(0xFF6CCBAB)))
        assertEquals(0xFF, (opaque shr 24) and 0xFF, "两套域色都是不透明端点，混出的亮端也必须完全不透明")
    }

    /**
     * 生产那枚分数按源码字面量钉住。
     *
     * 上面几条「两端算出同一点」的用例只发现得了**一侧**漂移；把本类常量与生产常量同时改成别的值
     * 它会全绿——但那是设计裁决变更，必须让一条字面量钉红一次，逼改动者先改决策文档与设计稿。
     * 断言用「恰一处」而非「出现过」：多出一处同名常量（第二套容器口径）同样要红。
     */
    @Test
    fun gradient_start_fraction_is_pinned_to_the_relaxed_palette_value() {
        val badge = uiComponentSource(BADGE_SOURCE_NAME)
        assertEquals(
            1,
            countMatches(badge, Regex("""private const val GRADIENT_START_FRACTION = 0\.18\b""")),
            "入口容器渐变亮端混比必须恰为 0.18（2026-10-27 轻松档裁决：0.35 的容器读成「一块彩色渐变」，" +
                "与「低压力、近乎纯色」方向相反）；改这枚数等于改设计裁决，先改决策文档与本钉值。",
        )
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

    /**
     * 三层判定（2026-10-28，裁决 D1 把强调层接进组装之后）：填充 / 强调 / 骨架各自的画笔指纹。
     *
     * 三层的区别不只是「有没有 stroke」——底幅与强调层都是纯填充，只有 alpha 能把它们分开：
     * 底幅按主题稀释（`DuotoneFillAlpha.forTheme`），强调层恒不透明。所以：
     *  - [fillLayers] = 只给 `fill`、且 alpha **小于**不透明；
     *  - [accentLayers] = 只给 `fill`、且 alpha **恰为** 255；
     *  - [strokeLayers] = 带 `stroke`。
     * 这三条合起来覆盖全部画笔组合，任何一条路径都躲不进「既不算填充也不算强调」的缝里
     * （[every_path_in_the_vector_is_one_of_the_three_layers] 钉这条）。
     */
    private fun fillLayers(vector: ImageVector): List<VectorPath> =
        pathsOf(vector).filter { it.stroke == null && alphaByteOf(it) < 255 }

    private fun accentLayers(vector: ImageVector): List<VectorPath> =
        pathsOf(vector).filter { it.stroke == null && alphaByteOf(it) == 255 }

    private fun strokeLayers(vector: ImageVector): List<VectorPath> = pathsOf(vector).filter { it.stroke != null }

    /** 纯填充路径的 alpha 字节；拿不到 `SolidColor` 就当场炸（渐变画笔在字形里没有合法用例）。 */
    private fun alphaByteOf(path: VectorPath): Int {
        val brush = path.fill as? SolidColor
            ?: fail("填充层画笔不是 SolidColor，层身份无从判定：${path.fill}")
        return (brush.value.toArgb() shr 24) and 0xFF
    }

    /** 纯填充路径的颜色字节（RGB），强调层取色契约按它比对。 */
    private fun rgbByteOf(path: VectorPath): Int {
        val brush = path.fill as? SolidColor
            ?: fail("填充层画笔不是 SolidColor，取色无从判定：${path.fill}")
        return brush.value.toArgb() and 0xFFFFFF
    }

    /**
     * 填充不透明度落在 `SolidColor` 的颜色 alpha 上（Compose 的 `Color` 是 8-bit 量化的），
     * 所以主题档比对按字节做，容得下 1/255 的量化差；档位抄错（0.42 写成 0.30 一类）差 30 个字节，照样判红。
     */
    private fun alphaMatchesTheme(path: VectorPath, isDark: Boolean): Boolean {
        val actual = alphaByteOf(path)
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

    /**
     * 圆心落在 0 是合法几何（与画布左/上边缘相切，24dp 栅格里的正常造型需求）：
     * 「为正」这条约束只属于半径（标记串的第 3 段），圆心两轴只要求有限。
     */
    @Test
    fun circle_fill_marker_accepts_a_center_coordinate_of_zero() {
        val arc = circleFillPathData("CircleFill(0, 12, 12)")
        assertTrue(arc.startsWith("M 0 0"), "cx = 0、cy = 12、半径 12 的圆应从顶极点 (0, 0) 起锚，实际：$arc")
        assertTrue(arc.contains(" 0 24"), "下极点 (0, 24) 必须原样落在产物里，实际：$arc")
        assertEquals(2, Regex("""[Aa]\s""").findAll(arc).count(), "相切圆同样要两段首尾相接的圆弧才闭合成整圆，实际：$arc")
        assertFalse(arc.contains("CircleFill"), "产物里仍留着标记串：说明解释分支根本没跑")
        assertTrue(nodesOf(arc).isNotEmpty(), "相切圆的产物解析后没有节点：图标静默不画，$arc")

        // 同一条口径的另一侧：cy 为 0 也合法（极点可以为负，视口外的部分由画布裁掉）。
        val topEdge = circleFillPathData("CircleFill(12, 0, 4)")
        assertTrue(
            topEdge.startsWith("M 12 -4") && topEdge.contains("12 4"),
            "cy = 0、半径 4 的上下极点应为 -4 / 4，实际：$topEdge",
        )
        assertTrue(nodesOf(topEdge).isNotEmpty(), "上边缘相切圆的产物解析后没有节点，$topEdge")

        // 合成反例（证明本用例的极点判据抓得住「圆心被写歪」）：把 cx 当成 12 的产物必须不满足上面两条判据。
        val wrongCenter = "M 12 0 A 12 12 0 0 1 12 24 A 12 12 0 0 1 12 0"
        assertFalse(wrongCenter.startsWith("M 0 0"), "合成反例失控：圆心写歪的产物也满足本用例的起锚判据")
        assertFalse(wrongCenter.contains(" 0 24"), "合成反例失控：极点 (0, 24) 不在场也算命中")
    }

    /** 收紧的是圆心的过严约束，不是半径的下界：坏半径与非有限圆心仍必须各自抛错并带上原文。 */
    @Test
    fun circle_fill_marker_still_rejects_a_bad_radius_and_a_non_finite_center() {
        // 每条坏输入配一个消息关键词：半径不合格要指名「半径」，圆心不合格要指名「有限」——
        // 消息只笼统给一个总条件时，读错误的人分不清是三段里的哪一段写歪了。
        listOf(
            "CircleFill(0, 12, 0)" to "半径",        // 半径 0：上下极点重合，画不出圆
            "CircleFill(12, 12, -4)" to "半径",      // 负半径
            "CircleFill(12, 12, NaN)" to "半径",     // 非有限半径（Double 字面量认 "NaN"）
            "CircleFill(12, 12, Infinity)" to "半径", // 非有限半径
            "CircleFill(NaN, 12, 4)" to "有限",      // 非有限圆心：半径合格也定不出位置
        ).forEach { (bad, keyword) ->
            val thrown = assertFailsWith<IllegalArgumentException> { circleFillPathData(bad) }
            assertTrue(
                thrown.message?.contains(bad) == true,
                "抛错消息没有带上原始串，无法归因到具体圆形：message=${thrown.message}，输入=$bad",
            )
            assertTrue(
                thrown.message?.contains(keyword) == true,
                "抛错消息没点明是哪一段、缺什么条件（$keyword）：message=${thrown.message}，输入=$bad",
            )
        }
    }

    /**
     * 层开关是组装期的真分支：Duotone 三层都在（底幅 + 强调 + 骨架），StrokeOnly 只剩骨架。
     *
     * 强调层的存在是 2026-10-28 裁决 D1 的落点：`AppIcons.kt` 里逐枚写好的 `accentPaths` 此前从未进过画布，
     * 字形实际只有「半透明底幅 + 白描边」两层——24dp 画布上所有字形退化成同一档线框，
     * 这正是「单调、没有绘画感」的直接根因。本用例按 Search 的冻结数据复算三层枚数。
     */
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
        assertEquals(
            AppIcon.Search.spec.accentPaths.size,
            accentLayers(duotone).size,
            "Duotone 的强调层枚数不等于冻结数据：`accentPaths` 没进组装（裁决 D1 回归锁）",
        )
        assertTrue(
            accentLayers(duotone).all { it.pathData.isNotEmpty() && alphaByteOf(it) == 255 },
            "强调层必须是可画且**不透明**的路径：稀释后与底幅的明度差会被吃掉，等于白画一层",
        )

        val strokeOnly = buildAppImageVector(AppIcon.Search, Color.White, 1f, AppGlyphLayer.StrokeOnly, false)
        assertTrue(fillLayers(strokeOnly).isEmpty(), "StrokeOnly 仍长出填充层：30dp 行内的单层口径失效")
        assertTrue(accentLayers(strokeOnly).isEmpty(), "StrokeOnly 仍长出强调层：行内只画骨架这一条口径失效")
        assertTrue(strokeLayers(strokeOnly).all { it.pathData.isNotEmpty() }, "StrokeOnly 的骨架为空：行内字形不会画出来")

        // 反空转（合成反例）：一条 pathData 为空的**底幅**填充路径必须让同一个 all{} 判据变红，否则 all{} 是空转。
        val emptyFill = ImageVector.Builder(
            name = "SyntheticEmptyFill",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = emptyList(),
            fill = SolidColor(Color.White.copy(alpha = DuotoneFillAlpha.forTheme(false))),
        ).build()
        assertEquals(1, fillLayers(emptyFill).size, "合成反例失控：空 pathData 的底幅填充没被识别成填充层")
        assertFalse(fillLayers(emptyFill).all { it.pathData.isNotEmpty() }, "合成反例失控：零节点填充被判为合格")
        // 反空转：把 layer 实参写错成 Duotone 的调用形状（行内误用两层）必须由填充层断言抓住。
        assertEquals(1, fillLayers(duotone).size, "Search 的填充层数量与冻结数据不符（两条填充层就是两套口径）")
        // 反空转（层身份的真判据是 alpha 而不是「有没有 stroke」）：不透明填充必须落进强调层、不进底幅层，
        // 否则本用例的 accentLayers 恒空、「强调层到底画没画」根本无从判定。
        val syntheticAccent = ImageVector.Builder(
            name = "SyntheticAccent",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = nodesOf("M4 4h8v8H4z"), fill = SolidColor(Color.Red)).build()
        assertEquals(0, fillLayers(syntheticAccent).size, "合成反例失控：不透明填充被算进底幅层，两层判据混成一档")
        assertEquals(1, accentLayers(syntheticAccent).size, "合成反例失控：不透明填充没被识别成强调层")
    }

    /** 每一条路径都必须落进三层之一：有画笔组合能躲过三个筛选，「三层」就是句空话。 */
    @Test
    fun every_path_in_the_vector_is_one_of_the_three_layers() {
        AppIcon.entries.forEach { icon ->
            listOf(false, true).forEach { isDark ->
                val vector = buildAppImageVector(icon, Color.White, 1f, AppGlyphLayer.Duotone, isDark)
                val layered = fillLayers(vector).size + accentLayers(vector).size + strokeLayers(vector).size
                assertEquals(
                    pathsOf(vector).size,
                    layered,
                    "$icon（isDark=$isDark）有路径既不算底幅、也不算强调、也不算骨架：三层筛选漏了一档画笔组合",
                )
                // 三层顺序也得对：底幅在下、强调居中、骨架在上——强调层被骨架压住才不会糊掉轮廓。
                val rank = pathsOf(vector).map { path ->
                    when {
                        path.stroke != null -> 2
                        alphaByteOf(path) == 255 -> 1
                        else -> 0
                    }
                }
                assertEquals(
                    rank.sorted(),
                    rank,
                    "$icon（isDark=$isDark）三层顺序乱了（应为 底幅 → 强调 → 骨架），实际：$rank",
                )
            }
        }
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
                        assertTrue(fills.isEmpty(), "$icon 属三层结构豁免表，却在 Duotone 档长出填充层")
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

                    // 强调层（裁决 D1）：`AccentlessIcons` 之外必须逐条落进画布且不透明；表内一枚都不许有。
                    val accents = accentLayers(duotone)
                    if (icon in AccentlessIcons) {
                        assertTrue(
                            accents.isEmpty(),
                            "$icon 在强调层豁免表里，却在 Duotone 档长出强调层：实心感已由底幅承担，再叠一层是重复",
                        )
                    } else {
                        assertEquals(
                            icon.spec.accentPaths.size,
                            accents.size,
                            "$icon 的强调层枚数与冻结数据不符（strokeScale=$strokeScale / isDark=$isDark）：" +
                                "`accentPaths` 没进组装就是「单调线框」复发",
                        )
                        assertTrue(
                            accents.all { it.pathData.isNotEmpty() && alphaByteOf(it) == 255 },
                            "$icon 的强调层必须可画且不透明：稀释或空路径都等于没画第三层",
                        )
                    }

                    val strokeOnly = buildAppImageVector(icon, Color.White, strokeScale, AppGlyphLayer.StrokeOnly, isDark)
                    assertTrue(
                        strokeLayers(strokeOnly).all { it.pathData.isNotEmpty() },
                        "$icon 的 StrokeOnly 档骨架为空（strokeScale=$strokeScale / isDark=$isDark）",
                    )
                    assertTrue(fillLayers(strokeOnly).isEmpty(), "$icon 的 StrokeOnly 档仍带填充层")
                    assertTrue(accentLayers(strokeOnly).isEmpty(), "$icon 的 StrokeOnly 档仍带强调层")
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
            assertFalse(allowsInEntryContainer(icon), "$icon 属三层结构豁免表，只作行内/尾部指示，不得进入 ≥40dp 入口容器")
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

    /** 豁免枚的路径数据本身必须是裸描边：入口判定函数与三层口径不能各说各话。 */
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

        // 行内那枚的字形度量必须仍然「不是入口段」：入口族改到 22dp 后，17dp 调用绝不能被算进入口计数，
        // 否则 R-10.2 的两档计数与行内档位会互相顶替（入口段判定见 entry_glyph_shapes_are_pinned_tone_by_tone）。
        val good = "AppGlyph(icon = icon, tint = palette.deep, size = 17.dp, layer = AppGlyphLayer.StrokeOnly)"
        assertEquals(1, countMatches(good, strokeOnlyGlyphCall), "合成正片段漏判：行内单层形状抓不住")
        assertEquals(0, entryGlyphCallSegments(good).size, "合成正片段越界：行内 17dp 调用被当成入口段")
        val unlayered = "AppGlyph(icon = icon, tint = palette.deep, size = 17.dp)"
        assertEquals(0, countMatches(unlayered, strokeOnlyGlyphCall), "合成负片段被放行：行内没关填充层也算合格")
    }

    /**
     * R-10.3 的入口形状段（Task 10 与 `EntryIconTone` 同提交重写）：入口族字形统一 22dp（稿 `.ic svg`），
     * 两档各一枚、共 2 处调用，且**两档各按其形状判**——弱档恰一处显式单层，实底档那一处 `layer =` 出现 0 次。
     *
     * 旧版这里钉的是「入口 24dp、段内不得出现 layer =」，并有一条把 `24.dp + StrokeOnly` 判成禁止写法的
     * `strokeOnlyOnEntryGlyph`。R-10.2 落地后这条口径**反了**：弱档合法地就是单层。所以旧禁令连同
     * `24.dp` 锚点一并删除（不留死正则），换成上面的分档判定。
     *
     * 反向变异自证（本用例的三条承重线，brief Step 4 逐条跑过、贴输出、改回）：
     *  ① 生产实底档字形改回 `24.dp` → [entryGlyphCallSegment] 只数到 1 处、且 [legacyEntryGlyphSize] 不归零，双红；
     *  ② 生产弱档删掉 `layer = StrokeOnly` → [entryStrokeOnlyCall] 的分档计数从 1 掉到 0；
     *  ③ 生产弱档补上渐变（多一处 `linearGradient`）→ `gradientFromRule` 的「恰好一处」红
     *     （该正则本身按 brief 要求不动，见 [entry_gradient_start_is_produced_by_the_domain_rule_not_a_raw_base_endpoint]）。
     */
    @Test
    fun entry_glyph_shapes_are_pinned_tone_by_tone() {
        val badge = uiComponentSource(BADGE_SOURCE_NAME)
        val entryCalls = entryGlyphCallSegments(badge)
        assertEquals(
            2,
            entryCalls.size,
            "入口族必须恰好两枚 22dp `AppGlyph` 调用（实底档 + 弱档）：0/1 处 = 弱档没接上或两档坍缩成一档，3 处 = 第三套入口口径",
        )
        val strokeOnlyCalls = entryCalls.filter { countMatches(it, entryStrokeOnlyCall) == 1 }
        assertEquals(
            1,
            strokeOnlyCalls.size,
            "两档里必须恰一处是弱档形状（22dp 且显式 StrokeOnly），实际 ${strokeOnlyCalls.size} 处：$entryCalls",
        )
        entryCalls.filterNot { it in strokeOnlyCalls }.forEach { call ->
            assertEquals(
                0,
                countMatches(call, explicitLayerArgument),
                "入口实底档把 layer 实参写死了：默认三层口径被改成显式口径（追加 modifier 一类合法实参不在禁止范围）",
            )
        }
        assertEquals(0, countMatches(badge, legacyEntryGlyphSize), "入口族字形度量已统一到 22dp：残留 size = 24.dp 就是第二套度量")
        assertEquals(0, countMatches(badge, strokeOnlyBeforeSize), "入口族不得靠「layer 写在 size 之前」的形状存在（锚点按顺序承重）")

        // 合成正片段：两档各一枚，形状与生产同形。
        val solid = "AppGlyph(icon = icon, tint = ink, size = 22.dp)"
        val tint = "AppGlyph(icon = icon, tint = palette.deep, size = 22.dp, layer = AppGlyphLayer.StrokeOnly)"
        val both = "$solid\n$tint"
        assertEquals(2, entryGlyphCallSegments(both).size, "合成正片段漏判：两档 22dp 调用没被数成两处")
        assertEquals(1, entryGlyphCallSegments(both).count { countMatches(it, entryStrokeOnlyCall) == 1 }, "合成正片段漏判：弱档形状没被认出")
        assertEquals(
            1,
            entryGlyphCallSegments(both).count { countMatches(it, explicitLayerArgument) == 0 },
            "合成正片段漏判：实底档的「无 layer」没被认出",
        )
        assertEquals(0, countMatches(both, legacyEntryGlyphSize), "合成正片段越界：22dp 片段里不该有 24dp 命中")
        // 批 2 追加合法实参后仍然认得（复评 N-3 的口径不变）。
        val augmented = "AppGlyph(icon = icon, tint = ink, size = 22.dp, modifier = Modifier.padding(4.dp))"
        assertEquals(1, entryGlyphCallSegments(augmented).size, "合成正片段漏判：追加 modifier 的入口调用认不出来")
        assertEquals(
            0,
            countMatches(entryGlyphCallSegments(augmented).single(), explicitLayerArgument),
            "合成正片段越界：追加合法实参被算成改口径",
        )

        // 合成负片段：旧 24dp 串不得再被任何入口正则匹配（锚点搬走的证据）。
        val legacy = "AppGlyph(icon = icon, tint = ink, size = 24.dp)"
        assertEquals(0, entryGlyphCallSegments(legacy).size, "合成负片段被放行：旧的 24dp 入口调用仍被当成入口段")
        assertEquals(1, countMatches(legacy, legacyEntryGlyphSize), "合成负片段失控：24dp 锚点串根本没被数出来")
        // 合成负片段：两档坍缩成一档（弱档丢了 StrokeOnly）必须让分档计数归零。
        val collapsed = "$solid\nAppGlyph(icon = icon, tint = palette.deep, size = 22.dp)"
        assertEquals(
            0,
            entryGlyphCallSegments(collapsed).count { countMatches(it, entryStrokeOnlyCall) == 1 },
            "合成负片段被放行：弱档丢了单层开关也算两档齐",
        )
        // 合成负片段：把 layer 写到 size 之前（换个写法就想绕开形状锚点）。
        val reordered = "AppGlyph(icon = icon, tint = palette.deep, layer = AppGlyphLayer.StrokeOnly, size = 22.dp)"
        assertEquals(0, countMatches(reordered, entryStrokeOnlyCall), "合成负片段被放行：换序后的单层写法也算弱档形状")
        assertEquals(1, countMatches(reordered, strokeOnlyBeforeSize), "合成负片段失控：换序片段本身没被 order 探针认出")
        assertEquals(1, entryGlyphCallSegments(reordered).size, "合成负片段失控：换序写法连入口段都不在计数里（分档判定会失去对象）")
    }

    /**
     * R-10.2 的对比面：入口族两档在两套主题下的 28 个组合（7 域 × 2 档 × 2 主题）逐个按 WCAG 相对亮度
     * 复算非文本 3:1，阈值与配比都当场算、**比值只写进断言消息**（注释与消息里的数字都不当判据）。
     *
     * 两档的配对各按生产取：
     *  - 实底档：底 = 渐变亮端 = deep 混 35% base（与 [white_glyph_on_every_domain_gradient_start_clears_3_to_1]
     *    同一条端点算法），字形墨浅套白、深套 `onBrand`（深套 `DomainDark` 里 deep == base，混色不改变端点）；
     *  - 弱档（裁决 22-2 改写）：底 = **活 token** `appColors().tint`（全站一档，不再是 `surface` 混 16% deep），
     *    字形墨 = 同域 `deep`。底色必须读 `AppColorLight.tint` / `AppColorDark.tint` 活符号：
     *    抄一份 hex 就会让 token 漂移后契约与实现各说各话。
     *
     * 两条反空转分别证明「弱档底色不是随手混出来的」与「3:1 阈值抓得住取错字段」——少一条，28 个组合都可能恒真。
     */
    @Test
    fun both_entry_tones_clear_3_to_1_in_both_theme_sets() {
        listOf(false to DomainLight, true to DomainDark).forEach { (isDark, table) ->
            val set = if (isDark) AppColorDark else AppColorLight
            val tintBackdrop = rgbOf(set.tint)
            val solidInk = if (isDark) rgbOf(set.onBrand) else 0xFFFFFF
            table.forEach { (domain, p) ->
                val deep = rgbOf(p.deep)
                val solidBackdrop = mixHex(argbOf(p.deep), argbOf(p.base), gradientStartFraction)
                val solidRatio = ratio(solidInk, solidBackdrop)
                assertTrue(
                    solidRatio >= 3.0,
                    "$domain 实底档：入口墨 #%06X 压渐变亮端 #%06X 只有 %.2f:1，不足非文本 3:1".format(
                        solidInk, solidBackdrop, solidRatio
                    )
                )
                val tintRatio = ratio(deep, tintBackdrop)
                assertTrue(
                    tintRatio >= 3.0,
                    "$domain 弱档：deep 字形 #%06X 压 tint #%06X 只有 %.2f:1，不足非文本 3:1".format(
                        deep, tintBackdrop, tintRatio
                    )
                )
                // 两档必须真的是两档：底色相同就说明弱档坍回了实底口径。
                assertNotEquals(solidBackdrop, tintBackdrop, "$domain 两档混出同一个底：R-10.2 的两档强度名存实亡")
            }
        }
        val ai = DomainLight.getValue(AppDomain.AiSpeech)
        // 反空转（弱档底色不是随手混出来的）：旧的「surface 混 16% deep」必须与活 token tint 不等，
        // 否则 22-2 的改底在数值上不可见，上面 14 条弱档断言仍然恒真。
        val legacyTintBackdrop = mixHex(argbOf(AppColorLight.surface), rgbOf(ai.deep), 0.16)
        assertNotEquals(
            rgbOf(AppColorLight.tint),
            legacyTintBackdrop,
            "旧的 16% 混色底恰等于 tint token：弱档改底在数值上不可见，契约失去判别力",
        )
        // 反空转（阈值抓得住取错字段）：同一枚 tint 底上换用装饰性细线（浅套 outline）必须不合格。
        val decoy = ratio(rgbOf(AppColorLight.outline), rgbOf(AppColorLight.tint))
        assertTrue(
            decoy < 3.0,
            "合成负例被放行：装饰性细线压 tint 竟也过非文本 3:1（%.2f:1），阈值或公式失控".format(decoy)
        )
    }

    // ---------- 用例：强调层（2026-10-28 裁决 D1） ----------

    /**
     * 强调层取色的独立复算：生产 `accentInkForGradient` 必须等于「域 deep 逐通道压暗 22%」。
     * 两侧各写一份实现是本类一贯的口径（同 [mixArgb]）：共用一份就退化成「函数返回它自己」。
     */
    @Test
    fun accent_ink_is_produced_by_the_darken_rule_channel_by_channel() {
        DomainLight.forEach { (domain, p) ->
            val expected = darkenArgb(argbOf(p.deep), accentDarkenFraction)
            val actual = argbOf(accentInkForGradient(p.deep))
            assertEquals((expected shr 16) and 0xFF, (actual shr 16) and 0xFF, "$domain 强调层红通道偏离测试端独立复算")
            assertEquals((expected shr 8) and 0xFF, (actual shr 8) and 0xFF, "$domain 强调层绿通道偏离测试端独立复算")
            assertEquals(expected and 0xFF, actual and 0xFF, "$domain 强调层蓝通道偏离测试端独立复算")
            // 反空转：压暗比重换成 0（= 原样返回 deep）必须与生产结果不同，否则「压暗」这件事测不出来。
            assertNotEquals(
                actual,
                darkenArgb(argbOf(p.deep), 0.0),
                "合成负例失效：$domain 压暗 22% 与不压暗算出了同一个颜色（规则形同虚设）",
            )
            // 反空转：强化必须真的变深——压暗后的亮度不得高于原 deep，否则规则方向反了。
            assertTrue(
                luminance(actual) < luminance(rgbOf(p.deep)),
                "$domain 强调层没有比域 deep 更深：压暗规则方向反了",
            )
        }
    }

    /** 强调层必须恒不透明：alpha 随域色漂移（半透明域色一类输入也不许带出去）会让「深一档」被底幅稀释掉。 */
    @Test
    fun accent_ink_stays_opaque_whatever_the_domain_color_is() {
        (listOf(0x801A7A63, 0xFF1A7A63, 0x00FFFFFF).map { Color(it.toInt()) }).forEach { deep ->
            val argb = argbOf(accentInkForGradient(deep))
            assertEquals(0xFF, (argb shr 24) and 0xFF, "强调层取色没把 alpha 置满：输入 $deep → #%08X".format(argb))
        }
    }

    /**
     * 裁决 D1 的对比面：强调层压在**底幅合成色**上必须 ≥3:1（非文本口径），7 域 × 2 主题共 14 对。
     *
     * 取样的底幅不是一枚：入口容器的底是渐变（亮端 = deep 混 18% base，暗端 = deep），
     * 底幅在两端透出的合成色不同，故两端各算一次、取**最差**的那一端——只按亮端算会漏掉 Deep 端那 0.3 的差。
     * 浅套强调层 = [accentInkForGradient]（域 deep 压暗 22%），深套 = `onBrand`（深套域色本身就是浅底，
     * 再压暗没有意义，最深的中性墨就是这一枚）。
     */
    @Test
    fun accent_layer_clears_3_to_1_against_the_duotone_fill_in_both_theme_sets() {
        listOf(false to DomainLight, true to DomainDark).forEach { (isDark, table) ->
            val set = if (isDark) AppColorDark else AppColorLight
            val ink = rgbOf(if (isDark) set.onBrand else Color.White)
            val fillAlpha = DuotoneFillAlpha.forTheme(isDark).toDouble()
            table.forEach { (domain, p) ->
                val accent = rgbOf(if (isDark) set.onBrand else accentInkForGradient(p.deep))
                // 底幅合成色：亮端与暗端各一枚（深套 deep == base，混色不改变端点，两端同值）。
                val ends = listOf(
                    mixHex(argbOf(p.deep), argbOf(p.base), gradientStartFraction),
                    rgbOf(p.deep),
                ).map { overArgb(ink, fillAlpha, it) }
                val worst = ends.minOf { ratio(accent, it) }
                assertTrue(
                    worst >= 3.0,
                    "$domain（isDark=$isDark）强调层 #%06X 压底幅 %s 最差只有 %.2f:1，不足非文本 3:1——" +
                        "第三层与底幅分不开就等于没画".format(
                            accent,
                            ends.joinToString("/") { "#%06X".format(it) },
                            worst,
                        )
                )
            }
        }
        // 反证（旧裁决被证伪）：强调层按原计划取域 `base` 时，压在底幅合成色上只有 1.0–1.5:1，
        // 与「同一家族的浅色叠浅色」完全一致——这条断言就是决策文档第 4 条必须改钉的证据。
        val fillAlpha = DuotoneFillAlpha.forTheme(false).toDouble()
        DomainLight.forEach { (domain, p) ->
            val fill = overArgb(0xFFFFFF, fillAlpha, rgbOf(p.deep))
            val baseAsAccent = ratio(rgbOf(p.base), fill)
            assertTrue(
                baseAsAccent < 3.0,
                "$domain 用 base 当强调层竟也过 3:1（%.2f:1）：裁决 D1 的改钉理由不再成立，需重议".format(baseAsAccent),
            )
        }
    }

    /**
     * 强调层取色规则按源码字面量钉住，并把入口实底档的接线一起钉住：
     *
     *  - `ACCENT_DARKEN_FRACTION = 0.22` 恰一处——22% 是「域 deep 压暗后过 3:1」反推出来的最小整数档
     *    （18% 时最差的 `AiSpeech` 只有 2.93），不是审美偏好；改这枚数等于改裁决，先改决策文档与本钉值；
     *  - 入口实底档必须把 `accentInk` 传进字形：少了这处接线，`accentPaths` 又会回到「写了但从不渲染」的旧状态。
     */
    @Test
    fun accent_darken_fraction_is_pinned_to_the_three_to_one_solved_value() {
        val badge = uiComponentSource(BADGE_SOURCE_NAME)
        assertEquals(
            1,
            countMatches(badge, accentDarkenRule),
            "强调层压暗比重必须恰为 0.22（18% 时最差域只有 2.93:1，过不了 3:1 线）；改它先改裁决",
        )
        assertEquals(
            1,
            countMatches(badge, entrySolidAccentArgument),
            "入口实底档没有把 accentInk 传给字形：强调层会退化成与 tint 同色（深套尚可，浅套等于没画）",
        )
        // 合成正片段 / 负片段：正则必须认得出接线、也必须认得出「忘了传」。
        assertEquals(1, countMatches("AppGlyph(icon = icon, tint = ink, size = 22.dp, accent = accentInk)", entrySolidAccentArgument))
        assertEquals(0, countMatches("AppGlyph(icon = icon, tint = ink, size = 22.dp)", entrySolidAccentArgument))
        assertEquals(
            1,
            countMatches("private const val ACCENT_DARKEN_FRACTION = 0.22", accentDarkenRule),
            "合成正片段漏判：压暗常量没被认出",
        )
        assertEquals(
            0,
            countMatches("private const val ACCENT_DARKEN_FRACTION = 0.18", accentDarkenRule),
            "合成负片段被放行：改回 18%（2.93:1，过不了 3:1）仍算合格",
        )
    }

    /**
     * 组装层必须真的用调用方给的强调色：传一枚与 tint 明显不同的色，强调层就得是那一枚。
     * 这条守的是「accent 参数接进去了却没被画出来」这类假接线（参数收下、画笔仍用 tint）。
     */
    @Test
    fun accent_layer_paints_the_color_it_was_given() {
        val accent = Color(0xFF123456)
        val vector = buildAppImageVector(AppIcon.Book, Color.White, 1f, AppGlyphLayer.Duotone, false, accent)
        val accents = accentLayers(vector)
        assertEquals(AppIcon.Book.spec.accentPaths.size, accents.size, "Book 的强调层没进画布：accent 参数被吞掉了")
        accents.forEach { path ->
            assertEquals(0x123456, rgbByteOf(path), "强调层画出来的不是调用方给的颜色（画笔仍取 tint）")
        }
        // 默认实参沿用 tint：不传 accent 的调用点（今日计划、提示条一类）拿到的是「同色不透明」，仍是第三档明度。
        val defaultAccent = accentLayers(buildAppImageVector(AppIcon.Book, Color.White, 1f, AppGlyphLayer.Duotone, false))
        defaultAccent.forEach { path ->
            assertEquals(0xFFFFFF, rgbByteOf(path), "accent 默认值不再是 tint：未传强调色的调用点会拿到意外颜色")
        }
        // 反空转：底幅层不得被顺手染成强调色（三层画成一层就是这次的失败形状）。
        fillLayers(vector).forEach { path ->
            assertEquals(0xFFFFFF, rgbByteOf(path), "强调色漏进了底幅层：三层被压成同一层")
        }
    }

    /**
     * 裁决 22-2 的源码面：入口弱档的底必须与行内共用 `appColors().tint`，
     * 旧的 `lerp(colors.surface, palette.deep, 0.16f)` 必须整条消失（不是改个分数继续混）。
     *
     * 为什么不用「比值过 3:1」代替这条：对比契约能证明颜色合格，证明不了**用同一档容器**；
     * 逐域 16% 混色在数值上也能过 3:1，却正是用户说的「一屏九块彩色」的来源。
     */
    @Test
    fun entry_tint_tone_uses_the_shared_tint_token_not_a_per_domain_mix() {
        // **2026-10-31（用户评审 ①）取样范围收窄**：行内徽标加了 `backdrop` 开关后，同一枚配方在文件里
        // 出现两处（入口弱档 + 行内开关支）。本锚守的是**入口弱档**那一处（对比表复算的底就是它），
        // 故先把行内段切掉再数；行内那处由本类
        // `badge_containers_use_the_ruled_geometry_and_glass_border` 的「两档合计 2 处
        // `background(appColors().tint)`」钉住，不是放宽。退役混色的禁形仍按**整份文件**数。
        val file = uiComponentSource(BADGE_SOURCE_NAME)
        val badge = file.substringBefore("fun InlineIconBadge(")
        val tintBranch = Regex("""Modifier\.background\(appColors\(\)\.tint\)""")
        assertEquals(
            1,
            countMatches(badge, tintBranch),
            "入口弱档的底必须是 background(appColors().tint)（与行内同一 token）",
        )
        assertEquals(
            0,
            countMatches(file, retiredEntryTintMix),
            "入口弱档仍在按域混色（lerp(colors.surface, palette.deep, …)）：裁决 22-2 要求域名只染字形",
        )

        // 合成正片段：弱档取 token、没有 lerp。
        val good = "val backdropModifier = if (tintTone) Modifier.background(appColors().tint) else Modifier"
        assertEquals(1, countMatches(good, tintBranch), "合成正片段漏判：token 底没被认出")
        assertEquals(0, countMatches(good, retiredEntryTintMix), "合成正片段越界：没有 lerp 却被判成混色")
        // 合成负片段：改个分数继续混色（16%→8%）也必须红——裁决说的是换底，不是调淡。
        val weakMix = "Modifier.background(lerp(colors.surface, palette.deep, 0.08f))"
        assertEquals(0, countMatches(weakMix, tintBranch), "合成负片段被放行：混色底也算 token 底")
        assertEquals(1, countMatches(weakMix, retiredEntryTintMix), "合成负片段被放行：换分数的混色没被抓住")
    }

    @Test
    fun badge_containers_use_the_ruled_geometry_and_glass_border() {
        val badge = uiComponentSource(BADGE_SOURCE_NAME)
        assertEquals(1, countMatches(badge, entryContainerSize), "入口容器必须是 40dp（≥40dp 入口档位由本尺寸承载）")
        assertEquals(1, countMatches(badge, inlineContainerSize), "行内容器必须是 30dp")
        assertEquals(
            2,
            countMatches(badge, inlineTintBackdrop),
            "行内与入口弱档都必须取主题 tint（裁决 22-2：全站一档，不得再逐域混色；也不得抄浅绿字面量）",
        )
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
            require(allowsInEntryContainer(icon)) { "X 属三层结构豁免表，只作行内/尾部指示，不得进入 ≥40dp 入口容器" }
        """.trimIndent()
        assertEquals(1, countMatches(good, entryContainerSize), "合成正片段漏判：40dp 容器")
        assertEquals(1, countMatches(good, inlineContainerSize), "合成正片段漏判：30dp 容器")
        assertEquals(1, countMatches(good, entryShapeClip), "合成正片段漏判：入口一处 AppShape.Icon 裁角")
        assertEquals(1, countMatches(good, inlineShapeClip), "合成正片段漏判：行内一处 AppShape.Inline 裁角")
        assertEquals(1, countMatches(good, glassHighlightBorder), "合成正片段漏判：玻璃高光描边")
        assertEquals(1, countMatches(good, entryRequire), "合成正片段漏判：require 判定")

        val bad = "Box(modifier = Modifier.size(36.dp).clip(AppShape.Card).background(AppPalette.Tint))"
        assertEquals(0, countMatches(bad, entryContainerSize), "合成负片段被放行：非 40dp 容器没被抓")
        assertEquals(0, countMatches(bad, inlineContainerSize), "合成负片段被放行：非 30dp 容器也算行内")
        assertEquals(0, countMatches(bad, inlineTintBackdrop), "合成负片段被放行：底色取自静态 AppPalette 没被抓")
        assertEquals(0, countMatches(bad, entryShapeClip), "合成负片段被放行：圆角用了别的档位没被抓")
        // 两档合并（行内也裁 Icon 档）必须让 inlineShapeClip 归零：这正是「共用一档」的错误表述落成代码的形状。
        val mergedTier = "Box(modifier = Modifier.size(30.dp).clip(AppShape.Icon).background(appColors().tint))"
        assertEquals(0, countMatches(mergedTier, inlineShapeClip), "合成负片段被放行：行内复用入口档没被抓")
        assertEquals(1, countMatches(mergedTier, entryShapeClip), "合成负片段失控：档位在文本里都不存在")

        // 复评 N-4 点名的单侧规则在这里补齐另一侧：写法被改写一点点就必须归零，否则计数契约形同虚设。
        assertEquals(1, countMatches(good, inlineTintBackdrop), "合成正片段漏判：行内底色取主题 tint")
        val thinBorder = "Modifier.border(2.dp, AppPalette.GlassHighlight, AppShape.Icon)"
        assertEquals(0, countMatches(thinBorder, glassHighlightBorder), "合成负片段被放行：高光描边重量被改成 2dp 还算合格")
        val softRequire = "if (allowsInEntryContainer(icon)) { renderEntryBadge() }"
        assertEquals(0, countMatches(softRequire, entryRequire), "合成负片段被放行：require 换成 if 分支也算准入强制")
        val otherMessage = "require(allowsInEntryContainer(icon)) { \"X 只作行内指示\" }"
        assertEquals(0, countMatches(otherMessage, entryRequireMessage), "合成负片段被放行：拒绝理由换成别的措辞也算按计划原文写全")
        assertEquals(1, countMatches(good, entryRequireMessage), "合成正片段漏判：require 的拒绝理由")
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
