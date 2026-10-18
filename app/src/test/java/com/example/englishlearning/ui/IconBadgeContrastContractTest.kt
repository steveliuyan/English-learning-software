package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.StrokeOnlyIcons
import com.example.englishlearning.ui.components.allowsInEntryContainer
import com.example.englishlearning.ui.components.gradientStartForDomain
import com.example.englishlearning.ui.components.spec
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.DomainLight
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
 *  - 每条源码正则都用合成正片段与合成负片段当场证明「抓得住且不越界」，不存在永久绿的空转断言。
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

    /** 按文件名在共享夹具扫描面里取剥注释源码；文件缺失 = 契约没有对象，必须点名而不是空扫过关。 */
    private fun uiComponentSource(name: String): String {
        val files = uiFiles().filter { it.name == name }
        assertTrue(
            files.size == 1,
            "$name 应恰好一个，实际 ${files.size} 个（实现文件未创建 / 被改名 / 扫描错位？" +
                "期望目录 $UI_MAIN_SOURCE_PATH，JVM 工作目录 ${File(".").absolutePath}）"
        )
        val file = files.single()
        assertTrue(
            file.path.replace('\\', '/').contains("/ui/components/"),
            "$name 不在 ui/components/ 下（本任务两个实现文件与 Task 6 的 AppIcons.kt 同目录）：${file.path}"
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

    /** 入口徽标：24dp 字形保持两层（默认 Duotone），不得顺手也降级成单层。 */
    private val duotoneGlyphCall = Regex("""size\s*=\s*24\.dp\s*\)""")

    /** 禁止形状：把描边-only 开关用到入口字形上（两种呈现的差异就坍缩成一种）。 */
    private val strokeOnlyOnEntryGlyph = Regex("""size\s*=\s*24\.dp\s*,\s*layer\s*=\s*AppGlyphLayer\.StrokeOnly""")

    /** 豁免表强制：入口容器开头必须由纯函数判定挡住豁免枚。 */
    private val entryRequire = Regex("""require\(allowsInEntryContainer\(icon\)\)""")

    private val entryRequireMessage = Regex("""不得进入 ≥40dp 入口容器""")

    private val entryContainerSize = Regex("""size\(40\.dp\)""")

    private val inlineContainerSize = Regex("""size\(30\.dp\)""")

    /** 行内容器底色必须取主题 tint（深浅两套随主题翻，写死浅绿就是第二套真值）。 */
    private val inlineTintBackdrop = Regex("""background\(appColors\(\)\.tint\)""")

    /** 容器圆角只认控制者裁决补的那一枚 `AppShape.Icon`（图标容器档，不复用 AppShapes 槽位）。 */
    private val iconShapeClip = Regex("""clip\(AppShape\.Icon\)""")

    /** inset 高光的等价实现：1dp 玻璃高光描边。 */
    private val glassHighlightBorder = Regex("""border\(1\.dp,\s*AppPalette\.GlassHighlight,\s*AppShape\.Icon\)""")

    /** 填充层不透明度是主题取值，只能问 `DuotoneFillAlpha.forTheme`，不得把档位抄进渲染码。 */
    private val themedFillAlphaRead = Regex("""DuotoneFillAlpha\.forTheme\(""")

    /** 组装结果必须按输入缓存：图标、色、描边倍率任一变化都要重建，否则换色不换形。 */
    private val cacheKeyedOnInputs = Regex("""remember\(\s*icon\s*,\s*tint\s*,\s*strokeScale""")

    /** 视觉描边重量 2 随 `strokeScale` 缩放（Task 6 的度量基准在渲染端的落点）。 */
    private val scaledStrokeWidth = Regex("""2f\s*\*\s*strokeScale""")

    /** `CircleFill(cx, cy, r)` 标记串必须由渲染端解释（路径数据里它是唯一非 SVG 形状）。 */
    private val circleFillMarkerRead = Regex("""CircleFill""")

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
        DomainDark.forEach { (_, p) ->
            assertTrue(ratio(rgbOf(p.deep), 0xFF06231A.toInt()) >= 3.0)   // 实测 5.96–9.82，最差危险域
        }
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
        assertEquals(1, countMatches(badge, duotoneGlyphCall), "入口字形必须保持两层默认（不带 layer 实参）")

        val good = "AppGlyph(icon = icon, tint = palette.deep, size = 17.dp, layer = AppGlyphLayer.StrokeOnly)"
        assertEquals(1, countMatches(good, strokeOnlyGlyphCall), "合成正片段漏判：行内单层形状抓不住")
        val unlayered = "AppGlyph(icon = icon, tint = palette.deep, size = 17.dp)"
        assertEquals(0, countMatches(unlayered, strokeOnlyGlyphCall), "合成负片段被放行：行内没关填充层也算合格")
        val wrongBadge = "AppGlyph(icon = icon, tint = ink, size = 24.dp, layer = AppGlyphLayer.StrokeOnly)"
        assertEquals(1, countMatches(wrongBadge, strokeOnlyOnEntryGlyph), "合成负片段被放行：入口字形被降级没被抓")
        assertEquals(0, countMatches(good, duotoneGlyphCall), "合成负片段被放行：行内单层被当成了两层入口字形")
    }

    @Test
    fun badge_containers_use_the_ruled_geometry_and_glass_border() {
        val badge = uiComponentSource(BADGE_SOURCE_NAME)
        assertEquals(1, countMatches(badge, entryContainerSize), "入口容器必须是 40dp（≥40dp 入口档位由本尺寸承载）")
        assertEquals(1, countMatches(badge, inlineContainerSize), "行内容器必须是 30dp")
        assertEquals(1, countMatches(badge, inlineTintBackdrop), "行内底色必须取主题 tint，不得抄浅绿字面量")
        assertEquals(2, countMatches(badge, iconShapeClip), "两种容器都要裁在同一枚图标容器圆角 AppShape.Icon 上")
        assertEquals(1, countMatches(badge, glassHighlightBorder), "入口容器的 inset 高光必须用 1dp AppPalette.GlassHighlight 描边")
        assertEquals(1, countMatches(badge, entryRequire), "入口容器缺豁免表强制判定")
        assertEquals(1, countMatches(badge, entryRequireMessage), "入口容器的拒绝理由没按计划原文写全")

        val good = """
            Box(
                modifier = Modifier.size(40.dp).clip(AppShape.Icon).background(brush)
                    .border(1.dp, AppPalette.GlassHighlight, AppShape.Icon),
            )
            Box(modifier = Modifier.size(30.dp).clip(AppShape.Icon).background(appColors().tint))
            require(allowsInEntryContainer(icon)) { "X 属两层豁免表，只作行内/尾部指示，不得进入 ≥40dp 入口容器" }
        """.trimIndent()
        assertEquals(1, countMatches(good, entryContainerSize), "合成正片段漏判：40dp 容器")
        assertEquals(1, countMatches(good, inlineContainerSize), "合成正片段漏判：30dp 容器")
        assertEquals(2, countMatches(good, iconShapeClip), "合成正片段漏判：两处 AppShape.Icon 裁角")
        assertEquals(1, countMatches(good, glassHighlightBorder), "合成正片段漏判：玻璃高光描边")
        assertEquals(1, countMatches(good, entryRequire), "合成正片段漏判：require 判定")

        val bad = "Box(modifier = Modifier.size(36.dp).clip(AppShape.Card).background(AppPalette.Tint))"
        assertEquals(0, countMatches(bad, entryContainerSize), "合成负片段被放行：非 40dp 容器没被抓")
        assertEquals(0, countMatches(bad, inlineTintBackdrop), "合成负片段被放行：底色取自静态 AppPalette 没被抓")
        assertEquals(0, countMatches(bad, iconShapeClip), "合成负片段被放行：圆角用了别的档位没被抓")
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
        assertTrue(countMatches(glyph, circleFillMarkerRead) >= 1, "渲染端没解释 CircleFill 标记串：圆形填充会当普通路径解析")
        assertTrue(countMatches(glyph, decorativeGlyph) >= 1, "字形 Image 必须声明装饰性（contentDescription = null,）")

        val good = """
            val alpha = DuotoneFillAlpha.forTheme(isDark)
            return remember(icon, tint, strokeScale, layer, isDark) {
                build(path.startsWith("CircleFill("), strokeLineWidth = 2f * strokeScale)
            }
            Image(imageVector = vector, contentDescription = null, modifier = modifier)
        """.trimIndent()
        assertEquals(1, countMatches(good, themedFillAlphaRead), "合成正片段漏判：主题化不透明度读点")
        assertEquals(1, countMatches(good, cacheKeyedOnInputs), "合成正片段漏判：按输入缓存")
        assertEquals(1, countMatches(good, scaledStrokeWidth), "合成正片段漏判：缩放描边")
        assertEquals(1, countMatches(good, decorativeGlyph), "合成正片段漏判：装饰性声明")

        val bad = """
            val alpha = 0.42f
            return remember(spec) { build(strokeLineWidth = 2f) }
            Image(imageVector = vector, contentDescription = "学习", modifier = modifier)
        """.trimIndent()
        assertEquals(0, countMatches(bad, themedFillAlphaRead), "合成负片段被放行：把主题档抄成字面量没被抓")
        assertEquals(0, countMatches(bad, cacheKeyedOnInputs), "合成负片段被放行：缓存键不含输入参数没被抓")
        assertEquals(0, countMatches(bad, scaledStrokeWidth), "合成负片段被放行：描边不随 strokeScale 缩放没被抓")
        assertEquals(0, countMatches(bad, decorativeGlyph), "合成负片段被放行：装饰性没声明没被抓")
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
    }
}
