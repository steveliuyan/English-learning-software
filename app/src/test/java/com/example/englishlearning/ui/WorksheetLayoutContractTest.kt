package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 4 / Task 5：默写纸两屏的版式契约（spec §5 逐屏表「默写纸设置」「默写纸预览」两行）。
 *
 * 设置段钉四件事：方向控件排在范围之前、范围改单选、纸张样式标题不在方向分支里、圆角归到形状档。
 * 预览段（Task 5）在同文件追加。
 *
 * 「行号不锚、注释不计」由 [uiScreenSource] 与 [countMatches] 保证。
 */
class WorksheetLayoutContractTest {

    private val source: String by lazy { uiScreenSource("WorksheetSettingsScreen.kt") }

    private val screenBody: String by lazy { bodyOf(source, "fun WorksheetSettingsScreen(") }

    /** 前缀组防 `RadioButtonDefaults(` / `CheckboxDefaults(` 冒充控件本体。 */
    private val radioCall = Regex("""(^|[^A-Za-z0-9_.])RadioButton\(""")
    private val checkBoxCall = Regex("""(^|[^A-Za-z0-9_.])Checkbox\(""")

    // ---------- 页头 ----------

    @Test
    fun `the settings header comes from the shared component`() {
        assertTrue(screenBody.isNotEmpty(), "取不到 WorksheetSettingsScreen 本体，判据会空转")
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("AppScreenHeader("))),
            "页头必须走共享 `AppScreenHeader`（旧的 `← 返回设置` 文字按钮 + headline 标题是双份页头）",
        )
        val headerAt = screenBody.indexOf("AppScreenHeader(")
        assertTrue(headerAt >= 0, "屏幕本体里必须有页头调用")
        assertTrue(screenBody.substring(headerAt).contains("""title = "生成默写纸""""), "页头标题是「生成默写纸」")
        assertTrue(screenBody.substring(headerAt).contains("onBack = onBack"), "页头必须接宿主返回回调")
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "屏侧不得再读 M3 默认排印：六档 `AppType` 是唯一入口（spec §4.2）",
        )
    }

    // ---------- 方向控件排在范围之前 ----------

    /**
     * spec §5：「方向控件移到范围之前」。
     *
     * 判的是**渲染顺序**（屏幕本体里的调用先后），不是函数定义先后：方向决定「这张纸怎么默」，
     * 范围决定「拿哪些词」，先定形状再定素材。
     */
    @Test
    fun `the direction controls are rendered before the range choice`() {
        val directionAt = screenBody.indexOf("DirectionSection(")
        val rangeAt = screenBody.indexOf("RangeSection(")
        assertTrue(directionAt >= 0, "屏幕本体里必须调用 `DirectionSection(`")
        assertTrue(rangeAt >= 0, "屏幕本体里必须调用 `RangeSection(`")
        assertTrue(directionAt < rangeAt, "方向控件必须排在范围之前（spec §5 明文要求）")
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"worksheet_direction_zh_to_en\""))),
            "方向行的锚 `worksheet_direction_zh_to_en` 保留（connected 侧按它定位）",
        )
    }

    // ---------- 范围改单选 ----------

    /**
     * spec §5：「范围的单选 Radio」。
     *
     * 模板单选与范围单选各一枚 `RadioButton(`；方向行是**多选**（中译英 / 英译中可同时要），
     * 仍用 `Checkbox(`——两枚复选框必须全落在 `DirectionRow` 体内，范围行不得再出现复选框。
     */
    @Test
    fun `the range is a single choice radio group while directions stay multi select`() {
        assertEquals(2, countMatches(source, radioCall), "恰两组单选：模板单选 + 范围单选（各一枚 `RadioButton(`）")
        val directionRow = bodyOf(source, "private fun DirectionRow(")
        assertTrue(directionRow.isNotEmpty(), "取不到 DirectionRow 本体，判据会空转")
        assertEquals(
            1,
            countMatches(source, checkBoxCall),
            "多选只属于方向行：方向行件里一枚复选框，被两种方向各调用一次；别处再出现复选框即红",
        )
        assertEquals(
            countMatches(source, checkBoxCall),
            countMatches(directionRow, checkBoxCall),
            "范围行不得再用 `Checkbox(`：同一屏里「能选几个」必须靠控件形状说清（单选 Radio / 多选 Checkbox）",
        )
    }

    // ---------- 纸张样式标题不在方向分支里 ----------

    /**
     * spec §5：纸张样式（四线三格 / 答案页）对每种模板都成立，旧版把它的标题写进了
     * `if (settings.requiresDirection())` 分支，于是「我的词表 / 艾宾浩斯」两种模板下
     * 标题消失、下面两个开关却还在——标题与内容分家。
     *
     * 判法：把方向分支的花括号内容整段取出来，其中不许出现「纸张样式」或 `PaperStyleSection(`；
     * 同时该标题必须恰好一处且在自己的段落件体内。
     */
    @Test
    fun `the paper style heading is not hidden behind the direction gate`() {
        val guarded = guardedDirectionBlock()
        assertTrue(guarded.isNotEmpty(), "取不到 `if (settings.requiresDirection()) {` 的分支体，判据会空转")
        assertTrue("纸张样式" !in guarded, "纸张样式标题不得写进方向分支：非拼写测试模板下标题会消失")
        assertTrue("PaperStyleSection(" !in guarded, "纸张样式整段都不得落在方向分支里")
        val paperSection = bodyOf(source, "private fun PaperStyleSection(")
        assertTrue(paperSection.isNotEmpty(), "纸张样式必须是一段独立的段落件 `PaperStyleSection`")
        assertEquals(1, countMatches(source, Regex("纸张样式")), "「纸张样式」标题恰一处")
        val paperCallAt = screenBody.indexOf("PaperStyleSection(")
        assertTrue(paperCallAt >= 0, "屏幕本体必须调用 `PaperStyleSection(`")
        val anchor = "if (settings.requiresDirection()) {"
        val guardAt = screenBody.indexOf(anchor)
        val guardEnd = guardAt + anchor.length + guarded.length
        assertTrue(
            paperCallAt > guardEnd,
            "调用点必须排在方向分支**闭合之后**：落在分支里就等于只在拼写测试模板下渲染",
        )
    }

    // ---------- 圆角归形状档 ----------

    /** 圆角只走 `AppShape`（卡片 20 / 按钮 14），不再出现 22dp / 18dp / 16dp 三套散值。 */
    @Test
    fun `the corner radii come from the shared shape scale`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("RoundedCornerShape("))),
            "不得再手写圆角：`AppShape.Card` / `AppShape.Button` 是唯一入口",
        )
        assertTrue("AppShape.Card" in source, "弱卡/可选行用卡片档圆角")
        assertTrue("AppShape.Button" in source, "按钮与控件用按钮档圆角")
    }

    // ---------- 令牌 ----------

    /** 屏侧只消费 `appColors()` / `appDomain()`；七枚 `Mint*` 静态桥清零（口径同 Task 1–3）。 */
    @Test
    fun `the settings screen consumes theme tokens instead of the static mint bridge`() {
        listOf(
            "MintPrimaryDark",
            "MintPrimary",
            "MintTextMuted",
            "MintSurface",
            "MintOutline",
            "MintTint",
            "MintBackground",
        ).forEach { token ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(token))),
                "$token 是静态薄荷桥，屏侧改用 appColors() / appDomain()（spec §4.4）",
            )
        }
        assertTrue("appColors()" in source, "屏侧必须消费 `appColors()`")
    }

    // ---------- 既有锚 ----------

    /**
     * connected 侧按这些 tag 定位，一枚都不能丢。
     *
     * 判的是**字符串字面量**恰一处，而不是 `testTag("…")` 的形状：范围那组 tag 由
     * `rangeTag(range)` 的 `when` 分支给（枚举 → 字面量），写法与模板那组不同，
     * 判字符串能让两条路都被覆盖，且「同一枚 tag 在别处又被写了一遍」照样会红。
     */
    @Test
    fun `the existing anchors survive the redesign`() {
        listOf(
            "worksheet_settings_screen",
            "worksheet_preview_action",
            "worksheet_direction_zh_to_en",
            "worksheet_direction_en_to_zh",
            "worksheet_template_full_list",
            "worksheet_template_spelling_test",
            "worksheet_template_ebbinghaus",
            "worksheet_range_completed_today",
            "worksheet_range_all_plan_items",
            "worksheet_range_completed_new",
            "worksheet_range_completed_review",
            "worksheet_range_difficult_today",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "锚 `$tag` 必须恰一处字面量",
            )
        }
    }

    // ---------- 主操作在禁用态仍读得出字 ----------

    /**
     * 底部主操作按钮的**整段调用**（本屏恰一枚 `Button(`；`RadioButton(` 由前缀组排除）。
     *
     * 真机现形：没有可导出词条时按钮进入禁用态，而文案行自钉了 `color = colors.onStrong`，
     * 于是近白字压浅薄荷底——整枚按钮只剩一块空底色，「为什么不能点」无从得知。
     * 前景色必须由 `Button` 的 `contentColor` / `disabledContentColor` 下发。
     */
    private val previewActionButton: String by lazy { callSpan(source, "Button(") }

    /** 只认「裸」赋值：`contentColor = colors.onStrong` 是按钮自己的合法声明，不算屏侧重钉。 */
    private val bareInkPin = Regex("""(?<![A-Za-z])color = colors\.onStrong""")

    @Test
    fun `the preview action keeps its label readable when it is disabled`() {
        assertTrue(previewActionButton.isNotEmpty(), "取不到底部主操作的 `Button(` 按钮体，判据会空转")
        assertTrue("\"预览默写纸\"" in previewActionButton, "按钮体里必须有「预览默写纸」这枚文案")
        assertEquals(
            1,
            countMatches(previewActionButton, Regex(Regex.escape("disabledContentColor = colors.text3"))),
            "禁用态要读得出字：按钮声明 `disabledContentColor = colors.text3`",
        )
        assertEquals(
            0,
            countMatches(previewActionButton, bareInkPin),
            "按钮内的文案不得自钉 `color = colors.onStrong`：禁用时近白字压浅底，按钮看上去是空的——" +
                "前景色交给 `contentColor` / `disabledContentColor` 下发",
        )
        assertTrue(
            "LocalContentColor.current" in previewActionButton,
            "文案前景色必须取 `LocalContentColor.current`：`AppType.Label` 自带 `text2` 墨色，" +
                "在深实底上本就偏暗、禁用态更会压到浅底上读不出——两态都得跟着按钮下发的 ink 走",
        )
    }

    // ==================== 预览段（Task 5） ====================

    private val previewSource: String by lazy { uiScreenSource("WorksheetPreviewScreen.kt") }

    private val previewBody: String by lazy { bodyOf(previewSource, "fun WorksheetPreviewScreen(") }

    /** 吸底动作条本体（导出按钮必须在里面）。 */
    private val previewBar: String by lazy { bodyOf(previewSource, "private fun PreviewActionBar(") }

    /**
     * spec §3 页头族：上一页 / 下一页这类二级页一律「返回位 + 标题」。
     *
     * 旧版自己写了 `Text("预览默写纸")` 当标题、顶部根本没有返回路，只有底部一枚
     * 「返回修改」实底按钮——用户想回到设置得先滚到底。
     *
     * 返回位由共享 `AppScreenHeader` 自带（48dp 可点区 + `contentDescription = "返回"`，
     * R-9.1 一处定义），所以这里不另外要求 `worksheet_preview_back` 这枚 tag：给它就得让共享组件
     * 多一个 tag 参数，等于把「返回语义一处定义」拆开（与 Task 3 同一条裁决）。判据换成两条合起来
     * 更严的：页头恰一处且接了 `onBack`；全文件 `contentDescription = "返回"` 必须为 0。
     */
    @Test
    fun `the preview header comes from the shared component`() {
        assertTrue(previewBody.isNotEmpty(), "取不到 WorksheetPreviewScreen 本体，判据会空转")
        assertEquals(
            1,
            countMatches(previewSource, Regex(Regex.escape("AppScreenHeader("))),
            "页头必须走共享 `AppScreenHeader`",
        )
        val headerAt = previewBody.indexOf("AppScreenHeader(")
        assertTrue(headerAt >= 0, "屏幕本体里必须有页头调用")
        assertTrue(previewBody.substring(headerAt).contains("""title = "预览默写纸""""), "页头标题是「预览默写纸」")
        assertTrue(previewBody.substring(headerAt).contains("onBack = onBack"), "页头必须接宿主返回回调")
        assertEquals(
            0,
            countMatches(previewSource, Regex(Regex.escape("""contentDescription = "返回""""))),
            "返回语义只在共享页头里定义一次，屏侧不得自排第二枚返回键",
        )
        assertEquals(
            0,
            countMatches(previewSource, Regex(Regex.escape("MaterialTheme.typography"))),
            "屏侧不得再读 M3 默认排印：六档 `AppType` 是唯一入口（spec §4.2）",
        )
    }

    /**
     * spec §5：「导出 PDF 固定在底部」——底部动作条里只留导出这一枚主操作。
     *
     * 旧版把「返回修改」和「导出 PDF」并排放在同一行，两枚都是实底（还一浅一深），
     * 于是「回上一页」被摆成了与导出同级的动作；返回改由页头承担后，这枚必须消失。
     */
    @Test
    fun `the preview footer keeps only the export action`() {
        assertTrue(previewBar.isNotEmpty(), "取不到 PreviewActionBar 本体，判据会空转")
        assertEquals(
            1,
            countMatches(previewBar, Regex(Regex.escape("Button("))),
            "吸底条里只留导出这一枚按钮（返回已上移到页头）",
        )
        assertTrue(previewBar.contains("worksheet_export_action"), "导出按钮的锚 `worksheet_export_action` 保留")
        assertEquals(
            0,
            countMatches(previewSource, Regex("返回修改")),
            "「返回修改」这枚与导出平级的按钮必须删除：返回由页头承担",
        )
    }

    /**
     * spec §5：「导出非吸底」→ 改成吸底。
     *
     * 判两点：导出按钮落在 `PreviewActionBar` 里；该条在渲染顺序上排在最外层滚动容器的调用**之后**，
     * 即不在滚动区里跟着页面跑。
     */
    @Test
    fun `the export action is pinned to the bottom outside the scroll`() {
        assertTrue(previewBar.contains("worksheet_export_action"), "导出按钮必须落在吸底条体内")
        assertEquals(
            1,
            countMatches(previewSource, Regex(Regex.escape("worksheet_preview_action_bar"))),
            "吸底条必须有且只有一个 `worksheet_preview_action_bar` 锚",
        )
        assertTrue("worksheet_preview_action_bar" in previewBar, "该锚挂在吸底条本身上")
        val listAt = previewBody.indexOf("LazyColumn(")
        val barAt = previewBody.indexOf("PreviewActionBar(")
        assertTrue(listAt >= 0, "预览正文必须是可滚动的页面列表")
        assertTrue(barAt >= 0, "屏幕本体必须调用 `PreviewActionBar(`")
        assertTrue(listAt < barAt, "吸底条必须排在滚动正文之后：排在前面等于跟着内容一起滚走")
    }

    /**
     * 页纸底色是「纸」，必须取中性面 token。
     *
     * 旧版硬编码 `Color.White`：深色主题下预览里飘着一张刺眼的纯白纸，且这张纸的底色
     * 与阅读区、卡片用的是两套语言。
     */
    @Test
    fun `the page paper uses the surface token instead of hard coded white`() {
        assertEquals(
            0,
            countMatches(previewSource, Regex(Regex.escape("Color.White"))),
            "页纸底色不得硬编码 `Color.White`，改 `appColors().surface`（spec §4.4）",
        )
        assertTrue(
            Regex("""\.surface\b""").containsMatchIn(previewSource),
            "页纸 / 吸底条底色取中性面 token（`colors.surface`）",
        )
        assertEquals(
            0,
            countMatches(previewSource, Regex(Regex.escape("RoundedCornerShape("))),
            "不得再手写圆角：`AppShape.Card` / `AppShape.Button` 是唯一入口",
        )
        assertTrue("AppShape.Card" in previewSource, "页纸用卡片档圆角")
    }

    /** 七枚 `Mint*` 静态桥清零（口径同 Task 1–4）。 */
    @Test
    fun `the preview screen consumes theme tokens instead of the static mint bridge`() {
        listOf(
            "MintPrimaryDark",
            "MintPrimary",
            "MintTextMuted",
            "MintSurface",
            "MintOutline",
            "MintTint",
            "MintBackground",
        ).forEach { token ->
            assertEquals(
                0,
                countMatches(previewSource, Regex(Regex.escape(token))),
                "$token 是静态薄荷桥，屏侧改用 appColors()（spec §4.4）",
            )
        }
        assertTrue("appColors()" in previewSource, "屏侧必须消费 `appColors()`")
    }

    /** 预览的既有锚与文案（connected 侧定位 / 读屏文案都靠它们）。 */
    @Test
    fun `the preview anchors survive the redesign`() {
        listOf(
            "worksheet_preview_screen",
            "worksheet_preview_pages",
            "worksheet_export_action",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(previewSource, Regex(Regex.escape("\"$tag\""))),
                "锚 `$tag` 必须恰一处字面量",
            )
        }
        assertEquals(
            1,
            countMatches(previewSource, Regex("页预览")),
            "每页配图的读屏文案（`第 N 页预览`）保留且恰一处",
        )
        assertEquals(
            1,
            countMatches(previewSource, Regex("预览默写纸")),
            "「预览默写纸」只出现在页头标题里（旧版是自写标题再去掉页头）",
        )
    }

    // ---------- 取样助手 ----------
    /**
     * 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。
     *
     * 函数体左花括号必须取**括号深度为 0** 的那一枚：默认参数里可以带 lambda（`= {}`），
     * 照「签名之后第一枚 `{`」配对会取到那里，取样面变成空串、判据静默空转。
     */
    private fun bodyOf(text: String, signature: String): String {
        val start = text.indexOf(signature)
        if (start < 0) return ""
        var parenDepth = 0
        var open = -1
        for (index in start until text.length) {
            when (text[index]) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '{' -> if (parenDepth == 0) {
                    open = index
                    break
                }
            }
        }
        if (open < 0) return ""
        return balancedFrom(text, open)
    }

    /** 从一枚已知的左花括号起按配对取内容（不含两层花括号本身）。 */
    private fun balancedFrom(text: String, open: Int): String {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(open + 1, index)
                }
            }
        }
        return ""
    }

    /** 屏幕本体里 `if (settings.requiresDirection()) {` 那段分支的内容。 */
    private fun guardedDirectionBlock(): String {
        val anchor = "if (settings.requiresDirection()) {"
        val at = screenBody.indexOf(anchor)
        if (at < 0) return ""
        return balancedFrom(screenBody, at + anchor.length - 1)
    }

    /**
     * 取一枚调用的**整段**：从 `marker`（如 `Button(`）起，跨过参数圆括号与尾随 lambda，
     * 到 lambda 的右花括号为止。
     *
     * 只取 lambda 体会丢掉 `colors = ButtonDefaults.buttonColors(…)` 这一整段声明，
     * 「禁用色声明了吗」与「文案自钉前景色了吗」就落在两个不同窗口里、两条判据各自半瞎。
     * 参数里的 lambda（`onClick = { … }`）先出现，所以只认**圆括号深度归零之后**的那枚 `{`。
     */
    private fun callSpan(text: String, marker: String): String {
        val at = text.indexOf(marker)
        if (at < 0) return ""
        var depth = 0
        var inString = false
        var escaped = false
        var index = at
        while (index < text.length) {
            val c = text[index]
            when {
                inString -> when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }

                c == '"' -> inString = true
                c == '(' -> depth++
                c == ')' -> depth--
                c == '{' -> if (depth == 0) {
                    val close = balancedEnd(text, index)
                    if (close < 0) return ""
                    return text.substring(at, close + 1)
                }
            }
            index++
        }
        return ""
    }

    /** 从一枚左花括号起按配对找右花括号下标（字符串感知；取不到 -1）。 */
    private fun balancedEnd(text: String, open: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        var index = open
        while (index < text.length) {
            val c = text[index]
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
                    if (depth == 0) return index
                }
            }
            index++
        }
        return -1
    }
}
