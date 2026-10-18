package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 10 修复轮 1（R-10.6①）：共享组件的**稿面几何取值**在 JVM 源码侧的契约面。
 *
 * 为什么需要这一份（评审轮 C2/C3 的几何面）：connected 只能钉**下限**（`≥48dp`、`≥40dp`），
 * 钉不住稿面**取值**——徽标容器 40dp、入口行 56dp/间隙 12dp/padding 8dp、提示条 14×12dp padding、
 * 间隙 10dp、18dp 字形、1dp 描边（稿 `mockups.html:150-160 / :178-186`，实现 = `af07b34`）。
 * 取值漂移（40→48、12→10）connected 全绿，只有源码契约能挡。读法复用共享夹具
 * [uiFiles]/[stripComments]/[countMatches]（`SourceRatchet.kt`），与 `IconBadgeContrastContractTest`
 * 同一族「源码文本即契约」口径：**先剥注释再匹配**（实现文件 KDoc 会合法复述同一批字面量），
 * 且本文件不写裸十六进制/`Color(0x…)` 字面量。
 *
 * 每条用例都配合成正片段 + 合成负片段自证正则承重（先例：本仓
 * `entry_glyph_shapes_are_pinned_tone_by_tone`）；**按顺序承重的锚点**（clickable 紧跟 fillMaxWidth、
 * drawBehind 在 padding 外侧、size 在 layer 之前）在断言消息里写明顺序即契约。
 * 变异自证（brief Step 4 ⑥⑦）：生产 `spacedBy(12.dp)`→`spacedBy(10.dp)` 与本文件 12dp 锚红；
 * 生产提示条 `vertical = 12.dp`→`vertical = 14.dp` 与本文件 padding 锚红。
 *
 * **修复轮 2 追加一条「配方腿」（R2-4 / 二轮评审 I-1，R-10.7③）**：`IconBadgeContrastContractTest`
 * 那张 28 组合弱档对比表在测试端自己 `mixHex(surface, deep, 0.16)` 现算底色，全文没有一条正则读生产
 * 那枚 `lerp` → 表只对**理想配方**成立。`icon_badge_weak_tone_backdrop_recipe_is_pinned_to_source`
 * 补的就是这条生产绑定腿（变异自证 ⑪：生产 `0.16f`→`0.30f` → 本用例红）。几何值与配色配方从此同属
 * 一份契约：**connected 钉下限、本类钉取值与取色来源**。
 */
class SharedComponentGeometryContractTest {

    private companion object {
        const val ENTRY_ROW = "EntryRow.kt"
        const val INLINE_NOTICE = "InlineNotice.kt"
        const val ICON_BADGE = "IconBadge.kt"
        const val COMPONENTS_DIR = "/ui/components/"
    }

    /**
     * 按文件名在 `ui/components/` 取剥注释源码（私有版，照 `IconBadgeContrastContractTest.uiSource`
     * 的写法：文件缺失/多份/目录挪动都点名失败，不留空扫；不去改那个类，本任务自带一份）。
     */
    private fun uiComponentSource(name: String): String {
        val files = uiFiles().filter { it.name == name }
        assertTrue(
            files.size == 1,
            "$name 应恰好一个，实际 ${files.size} 个（实现文件未创建 / 被改名 / 扫描错位？" +
                "期望目录 $UI_MAIN_SOURCE_PATH，JVM 工作目录 ${File(".").absolutePath}）"
        )
        val file = files.single()
        assertTrue(
            file.path.replace('\\', '/').contains(COMPONENTS_DIR),
            "$name 不在 $COMPONENTS_DIR 下（目录挪动会让本契约读错文件）：${file.path}"
        )
        return stripComments(file.readText())
    }

    // ---------- EntryRow.kt ----------

    /** 行最小内容高（稿 `.entry { min-height:56px }`）。connected 只钉 ≥48 下限，56 这个取值归本锚。 */
    private val rowMinHeight56 = Regex("""heightIn\(min = 56\.dp\)""")

    /** 行上下 padding（稿面 8dp）。 */
    private val rowVerticalPadding8 = Regex("""padding\(vertical = 8\.dp\)""")

    /** 行间隙（稿 `.en`/`.ic` 的 margin 12dp）。变异自证 ⑥ 的红点。 */
    private val rowGap12 = Regex("""Arrangement\.spacedBy\(12\.dp\)""")

    /** 徽标定位标签（connected 侧量 40dp 容器的唯一口，KDoc 登记的存在理由见 `EntryRow.kt`）。 */
    private val iconBadgeTestTag = Regex("""testTag\("entry_row_icon"\)""")

    /** 省略号三档（标题/副标题/尾部值各一枚；brief 的「两处」只点了标题与副标题，trailing 是同口径第三处——本锚钉剥注释后的生产实面 3）。 */
    private val maxLinesOne = Regex("""maxLines = 1""")
    private val overflowEllipsis = Regex("""overflow = TextOverflow\.Ellipsis""")

    /** 禁止形状：把省略号换成裁切（用例名 not_clipping 的源码面镜像）。 */
    private val overflowClip = Regex("""overflow = TextOverflow\.Clip""")

    /** chevron：16dp 且显式单层。锚点**按顺序承重**（size 写在 layer 之前，与 `IconBadgeContrastContractTest.strokeOnlyBeforeSize` 同理）。 */
    private val chevronStroke16 = Regex("""size = 16\.dp, layer = AppGlyphLayer\.StrokeOnly""")

    /** 发丝线宽度取值。 */
    private val hairlineToPx = Regex("""1\.dp\.toPx\(\)""")

    /**
     * 发丝线顺序锚（R-10.5）：`drawBehind { …1.dp.toPx()… }` 之后紧跟 `.padding(top = 1.dp)`——
     * 先画线再让位，线才在内容外侧；写反（padding 后 drawBehind）就把线画进内容顶边。
     * 全文件 `padding(top = 1.dp)` 剥注释后是 2 处（另一处是副标题的 1dp 间隙，稿 `.ed`），
     * 所以「恰 1 处」必须由这条带 `drawBehind` 前件顺序的形状来钉。
     */
    private val hairlineOutsideContent =
        Regex("""drawBehind\s*\{[^{}]*1\.dp\.toPx\(\)[^{}]*\}\s*\.padding\(top = 1\.dp\)""")

    /** 新顺序（实测教训，计划 :1049）：clickable 紧跟 fillMaxWidth，可点面才是整盒。 */
    private val clickableRightAfterFillMaxWidth = Regex("""fillMaxWidth\(\)\s*\.clickable\(""")

    /** 旧形状（禁止）：clickable 排在 heightIn/padding 之后——真机只给出 40dp 可点面，撞 F5-03。 */
    private val clickableAfterPadding = Regex(
        """heightIn\(min = 56\.dp\)\s*\.padding\(vertical = 8\.dp\)\s*\.clickable\(""",
    )

    @Test
    fun entry_row_geometry_literals_are_pinned_in_source() {
        val source = uiComponentSource(ENTRY_ROW)
        assertEquals(1, countMatches(source, rowMinHeight56), "入口行最小高必须恰一处 56dp（稿 .entry min-height）")
        assertEquals(1, countMatches(source, rowVerticalPadding8), "入口行上下 padding 必须恰一处 8dp")
        assertEquals(1, countMatches(source, rowGap12), "入口行间隙必须恰一处 12dp（变异自证 ⑥：改 10dp 即此条红）")
        assertEquals(1, countMatches(source, iconBadgeTestTag), "entry_row_icon 标签必须恰一处（多一处 = 第二套徽标定位口径）")
        assertEquals(3, countMatches(source, maxLinesOne), "标题/副标题/尾部值各一枚 maxLines = 1，恰 3 处")
        assertEquals(3, countMatches(source, overflowEllipsis), "同一批三处必须都是 Ellipsis，恰 3 处")
        assertEquals(0, countMatches(source, overflowClip), "入口行文本禁 Clip：裁掉字形就是 not_clipping 用例名说谎")
        assertEquals(1, countMatches(source, chevronStroke16), "chevron 必须恰一处 size = 16.dp 且带 StrokeOnly（锚点按 size→layer 顺序承重）")
        assertEquals(1, countMatches(source, hairlineToPx), "发丝线宽度必须恰一处 1.dp.toPx()")
        assertEquals(1, countMatches(source, hairlineOutsideContent), "发丝线必须恰一处「drawBehind 在前、padding(top = 1.dp) 让位在后」（顺序即契约，R-10.5）")
        assertEquals(1, countMatches(source, clickableRightAfterFillMaxWidth), "clickable 必须紧跟 fillMaxWidth()（可点面 = 整盒；计划 :1049 的实测教训）")
        assertEquals(0, countMatches(source, clickableAfterPadding), "禁旧形状 clickable 排在 heightIn/padding 之后（真机只有 40dp 可点面，撞 F5-03）")

        // 合成正片段：新顺序形状必须被「新顺序」探针认出、且不被「旧形状」探针误认。
        val newShape = "Row(modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)" +
            ".heightIn(min = 56.dp).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp))"
        assertEquals(1, countMatches(newShape, clickableRightAfterFillMaxWidth), "合成正片段漏判：新顺序没被认出来")
        assertEquals(0, countMatches(newShape, clickableAfterPadding), "合成正片段越界：新顺序被判成了旧形状")
        assertEquals(1, countMatches(newShape, rowGap12), "合成正片段漏判：12dp 间隙锚认不出自家形状")

        // 合成负片段：旧顺序串必须被「旧形状」探针命中、且被「新顺序」探针判 0 处。
        val legacyShape = "Row(modifier = modifier.heightIn(min = 56.dp).padding(vertical = 8.dp)" +
            ".clickable(role = Role.Button, onClick = onClick))"
        assertEquals(1, countMatches(legacyShape, clickableAfterPadding), "合成负片段失控：旧顺序没被禁形探针认出")
        assertEquals(0, countMatches(legacyShape, clickableRightAfterFillMaxWidth), "合成负片段被放行：旧顺序竟过了「clickable 紧跟 fillMaxWidth」锚")

        // 合成负片段：邻近取值不得互相冒名——48dp 串不得被 56dp 锚命中，10dp 串不得被 12dp 锚命中。
        assertEquals(0, countMatches("Box(Modifier.heightIn(min = 48.dp))", rowMinHeight56), "合成负片段被放行：48dp 下限串被 56dp 取值锚冒名命中")
        assertEquals(0, countMatches("Row(horizontalArrangement = Arrangement.spacedBy(10.dp))", rowGap12), "合成负片段被放行：提示条的 10dp 串被入口行 12dp 锚冒名命中")
        assertEquals(0, countMatches("Text(overflow = TextOverflow.Clip)", overflowEllipsis), "合成负片段被放行：Clip 串被 Ellipsis 锚命中")
        assertEquals(1, countMatches("Text(overflow = TextOverflow.Clip)", overflowClip), "禁形探针失控：Clip 串根本没被数出来")
        assertEquals(0, countMatches("AppGlyph(size = 16.dp)", chevronStroke16), "合成负片段被放行：丢了 StrokeOnly 层的 chevron 也算合规")
        assertEquals(0, countMatches("drawBehind { drawLine() }.padding(top = 2.dp)", hairlineOutsideContent), "合成负片段被放行：让位 2dp 竟也过发丝线顺序锚")
    }

    // ---------- InlineNotice.kt ----------

    /** 提示条 padding（稿 `.banner` 14×12dp）。变异自证 ⑦ 的红点。 */
    private val noticePadding14x12 = Regex("""padding\(horizontal = 14\.dp, vertical = 12\.dp\)""")

    /** 提示条间隙（稿 `.banner` 10dp）。 */
    private val noticeGap10 = Regex("""Arrangement\.spacedBy\(10\.dp\)""")

    /** 1dp 描边（稿 `.banner` border）。 */
    private val noticeBorder1dp = Regex("""border\(1\.dp,""")

    /** 18dp 状态字形（稿 `.banner .ic`）。 */
    private val noticeGlyph18 = Regex("""size = 18\.dp""")

    /** 48dp 触控下限——只派给动作槽（R-10.1 口径：无动作本体 ~42dp 是合规内容高）。 */
    private val actionFloor48 = Regex("""heightIn\(min = 48\.dp\)""")

    /** 圆角取装配档 18dp（`MintGlassTheme.kt:173` = 稿 `--r-card`）。 */
    private val shapesMedium = Regex("""MaterialTheme\.shapes\.medium""")

    /** 禁止形状：新组件读 20dp 旧档。 */
    private val appShapeCard = Regex("""AppShape\.Card""")

    /** 禁止形状：提示文案给 maxLines（R-10.1：裁掉就是丢信息，与入口行相反口径）。 */
    private val anyMaxLines = Regex("""maxLines""")

    @Test
    fun inline_notice_geometry_literals_are_pinned_in_source() {
        val source = uiComponentSource(INLINE_NOTICE)
        assertEquals(1, countMatches(source, noticePadding14x12), "提示条 padding 必须恰一处 14×12dp（变异自证 ⑦：vertical 改 14 即此条红）")
        assertEquals(1, countMatches(source, noticeGap10), "提示条间隙必须恰一处 10dp")
        assertEquals(1, countMatches(source, noticeBorder1dp), "提示条描边必须恰一处 border(1.dp,")
        assertEquals(1, countMatches(source, noticeGlyph18), "提示条状态字形必须恰一处 18dp")
        assertEquals(1, countMatches(source, actionFloor48), "48dp 下限必须恰一处且只落在动作槽（多一处 = 给本体也钉了水线）")
        assertEquals(1, countMatches(source, shapesMedium), "圆角必须恰一处 MaterialTheme.shapes.medium（装配档 = 稿 --r-card 18dp）")
        assertEquals(0, countMatches(source, appShapeCard), "新组件禁读 AppShape.Card（20dp 旧档）")
        assertEquals(0, countMatches(source, anyMaxLines), "提示文案不得出现 maxLines（R-10.1 相反口径：这里裁掉 = 丢信息）")

        // 合成正/负片段：padding 锚必须同时吃下两个分量——14×14（变异 ⑦ 的形状）与 12×12 都不得命中。
        val goodPadding = "Modifier.padding(horizontal = 14.dp, vertical = 12.dp)"
        assertEquals(1, countMatches(goodPadding, noticePadding14x12), "合成正片段漏判：14×12 padding 没被锚认出")
        assertEquals(0, countMatches("Modifier.padding(horizontal = 14.dp, vertical = 14.dp)", noticePadding14x12), "合成负片段被放行：vertical 漂到 14dp 也算合规（变异 ⑦ 形状）")
        assertEquals(0, countMatches("Modifier.padding(horizontal = 12.dp, vertical = 12.dp)", noticePadding14x12), "合成负片段被放行：horizontal 漂到 12dp 也算合规")
        // 跨组件冒名：入口行的 12dp 串不得被提示条 10dp 锚命中；48dp 锚不吃入口行的 56dp 串。
        assertEquals(0, countMatches("Row(horizontalArrangement = Arrangement.spacedBy(12.dp))", noticeGap10), "合成负片段被放行：入口行 12dp 串被提示条 10dp 锚冒名命中")
        assertEquals(0, countMatches("Row(Modifier.heightIn(min = 56.dp))", actionFloor48), "合成负片段被放行：入口行 56dp 串被动作槽 48dp 锚冒名命中")
        assertEquals(1, countMatches("Box(Modifier.heightIn(min = 48.dp))", actionFloor48), "合成正片段漏判：动作槽 48dp 下限没被锚认出")
        assertEquals(1, countMatches("Column { Text(modifier = Modifier.padding(top = 1.dp)) }", Regex("""padding\(top = 1\.dp\)""")), "取样壳失控：1dp padding 串本身数不出来（顺序锚另见入口行用例）")
        assertEquals(1, countMatches("Text(text = text, maxLines = 1)", anyMaxLines), "禁形探针失控：maxLines 串根本没被数出来")
    }

    // ---------- IconBadge.kt ----------

    /** 入口容器 40dp 档：两档共用同一枚外层 Box，`size(40.dp)` 在文件里只许出现一遍（R-10.2 的份数证据）。 */
    private val entryBox40 = Regex("""size\(40\.dp\)""")

    /**
     * 弱档底色配方锚（R-10.7③ / I-1）：稿 `.ic-tint` 的
     * `color-mix(in srgb, var(--d) 16%, var(--surface))` 在生产里就是这一枚 `lerp`；
     * `\s*` 只容忍换行缩进，不容忍参数换位（换位的写法该由 `IconBadgeContrastContractTest` 的复算先红）。
     */
    private val weakToneRecipe = Regex("""lerp\(colors\.surface,\s*palette\.deep,\s*0\.16f\)""")

    /** 禁形一（brief 原文正则）：混色基色被换成 `colors.background`——稿混的是 `--surface`，不是 `--background`。 */
    private val weakToneOnBackground = Regex("""lerp\(colors\.background,""")

    /**
     * 禁形二：同一枚 lerp 的系数漂到 0.16 之外（`0.30f`/`0.40f`/`0.24f` 全判红）。
     * 窄口径靠两点收：前件必须是 `colors.surface` + `palette.deep` 这两枚实参，负向预查 `(?!16f)`
     * 只放行那个已裁决的系数——所以 `GRADIENT_START_FRACTION = 0.35`、玻璃高光 alpha、
     * `InlineNotice` 的 `lerp(set.surface, set.errorFill, 0.12f)` 都不在本锚视野里。
     */
    private val weakToneCoefficientDrift = Regex("""lerp\(colors\.surface,\s*palette\.deep,\s*0\.(?!16f)\d+f\)""")

    @Test
    fun icon_badge_entry_container_is_one_shared_40dp_box() {
        val source = uiComponentSource(ICON_BADGE)
        assertEquals(
            1,
            countMatches(source, entryBox40),
            "入口容器必须只有一枚 size(40.dp)（两档共用；多一处 = 弱档另起容器，connected 严格相等那两条会一起红）",
        )
        // 合成片段：取值漂移（40→48/36）与行内 30dp 档都不得冒名命中 40dp 锚。
        assertEquals(1, countMatches("Box(modifier = Modifier.size(40.dp).clip(shape))", entryBox40), "合成正片段漏判：40dp 容器锚认不出自家形状")
        assertEquals(0, countMatches("Box(modifier = Modifier.size(48.dp))", entryBox40), "合成负片段被放行：容器漂到 48dp 也算 40dp 档（connected 只钉下限抓不住）")
        assertEquals(0, countMatches("Box(modifier = Modifier.size(30.dp))", entryBox40), "合成负片段被放行：行内 30dp 档被入口 40dp 锚冒名命中")
    }

    /**
     * 弱档（稿 `.ic-tint`）底色的**配方**在生产源码里必须就是那张对比表所依据的那一枚表达式。
     *
     * 这条是给 `IconBadgeContrastContractTest.both_entry_tones_clear_3_to_1_in_both_theme_sets`
     * 那张「7 域 × 2 档 × 2 主题 = 28 组合」表**补的生产绑定腿**（R-10.7③，修复轮 2 的 I-1）：
     * 那张表在测试端自己按 `mixHex(surface, deep, 0.16)` 现算底色，读的**不是**生产
     * `IconBadge.kt` 的 `lerp(colors.surface, palette.deep, 0.16f)` —— 于是表算的是**理想配方**：
     * 生产把 `colors.surface` 写成 `colors.background`、或把系数改成 `0.30f/0.40f`，那 26 条契约全绿
     * （控制者补算：漂移后深套最低仍在 3:1 以上，所以这是「偏离稿面配方」而非当场破线，故 I-1 不列
     * Critical——但「配方 = 稿面那一个」这件事得有人守，就是本锚）。
     * 与同族几何锚一个口径：先剥注释再匹配、配合成正/负片段自证正则承重。
     * 变异自证 ⑪：生产 `0.16f`→`0.30f` → 本用例正锚红（恰 1 处判成 0 处）。
     */
    @Test
    fun icon_badge_weak_tone_backdrop_recipe_is_pinned_to_source() {
        val source = uiComponentSource(ICON_BADGE)
        assertEquals(
            1,
            countMatches(source, weakToneRecipe),
            "弱档底色必须恰一处 lerp(colors.surface, palette.deep, 0.16f)（稿 .ic-tint 的 color-mix 16% 配方）",
        )
        assertEquals(
            0,
            countMatches(source, weakToneOnBackground),
            "禁形：混色基色不得换成 colors.background（稿 .ic-tint 混的是 --surface，不是 --background）",
        )
        assertEquals(
            0,
            countMatches(source, weakToneCoefficientDrift),
            "禁形：同一枚 lerp 的系数不得漂到 0.16 之外（0.30f/0.40f 都算偏离稿面配方，28 组合表只对理想配方成立）",
        )

        // 合成正片段：生产那枚写法（含 background(...) 外壳）必须被正锚认出、且不被两枚禁形锚误伤。
        val goodRecipe = "Modifier.background(lerp(colors.surface, palette.deep, 0.16f))"
        assertEquals(1, countMatches(goodRecipe, weakToneRecipe), "合成正片段漏判：16% 混 surface 的配方没被锚认出")
        assertEquals(0, countMatches(goodRecipe, weakToneOnBackground), "合成正片段越界：surface 配方被 background 禁形锚误判")
        assertEquals(0, countMatches(goodRecipe, weakToneCoefficientDrift), "合成正片段越界：0.16f 被系数漂移禁形锚误判")

        // 合成负片段：三种漂移形状各必须被对应禁形锚命中、且不被正锚放行。
        val backgroundDrift = "Modifier.background(lerp(colors.background, palette.deep, 0.16f))"
        assertEquals(1, countMatches(backgroundDrift, weakToneOnBackground), "合成负片段失控：background 换色没被禁形锚数出来")
        assertEquals(0, countMatches(backgroundDrift, weakToneRecipe), "合成负片段被放行：colors.background 换色竟也过 16% 配方锚")
        val coefficientDrift = "Modifier.background(lerp(colors.surface, palette.deep, 0.30f))"
        assertEquals(1, countMatches(coefficientDrift, weakToneCoefficientDrift), "合成负片段失控：0.30f 没被系数漂移锚数出来（变异 ⑪ 的形状）")
        assertEquals(0, countMatches(coefficientDrift, weakToneRecipe), "合成负片段被放行：0.30f 竟也过 16% 配方锚")
        assertEquals(
            1,
            countMatches("Modifier.background(lerp(colors.surface, palette.deep, 0.40f))", weakToneCoefficientDrift),
            "合成负片段失控：0.40f 没被系数漂移锚数出来",
        )
        // 跨文件冒名：InlineNotice 的 12% 混底（set.surface / set.errorFill，另一套取色口径）
        // 既不得命中入口弱档配方锚，也不得被系数漂移锚误判成「同一枚 lerp 漂了」。
        val noticeBackdrop = "backdrop = lerp(set.surface, set.errorFill, 0.12f)"
        assertEquals(0, countMatches(noticeBackdrop, weakToneRecipe), "合成负片段被放行：提示条 12% 混底被入口弱档配方锚冒名命中")
        assertEquals(0, countMatches(noticeBackdrop, weakToneCoefficientDrift), "合成负片段被放行：提示条 12% 混底被系数漂移禁形锚冒名命中（本锚只管 palette.deep 那枚）")
        // 邻近字段冒名：GRADIENT_START_FRACTION = 0.35（Double 常量、无 f 后缀、不在 lerp 调用位上）
        // 不得被任何一枚配方锚命中——禁形锚只管同一枚 lerp(colors.surface, palette.deep, …) 里的系数。
        assertEquals(0, countMatches("private const val GRADIENT_START_FRACTION = 0.35", weakToneCoefficientDrift), "合成负片段被放行：禁形锚宽到误伤了渐变亮端的 0.35 常量")
    }
}
