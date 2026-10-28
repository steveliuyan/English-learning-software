package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 3：重新学习屏的新骨架契约（spec §5 逐屏表「重新学习」行）。
 *
 * 这屏在批 2 里唯一一个**零契约零 tag** 的屏（重排前 56 行、只有纯文本与 M3 默认按钮），
 * 所以这份契约同时把三件事钉住：页头归共享组件、三档评分吸底、正文可滚。
 *
 * 「行号不锚、注释不计」由 [uiScreenSource] 与 [countMatches] 保证。
 */
class RelearnLayoutContractTest {

    private val source: String by lazy { uiScreenSource("VocabularyRelearnScreen.kt") }

    /** M3 `Button` 调用点：前缀组防 `TextButton(` 冒充（吸底条里只有三档评分该是真实底按钮）。 */
    private val buttonCall = Regex("""(^|[^A-Za-z0-9_.])Button\(""")

    // ---------- tag ----------

    /**
     * 六枚版式 tag 各恰一处：屏根、进度、词头，加三档评分。
     *
     * 吸底条本身单独数（`relearn_feedback_bar`）——它是「三档评分固定在底部」这条要求的几何载体，
     * 与三枚按钮是两种角色：条可以只有一枚，按钮必须三枚。
     */
    @Test
    fun `the screen carries its layout tags exactly once`() {
        listOf(
            "relearn_screen",
            "relearn_progress",
            "relearn_lemma",
            "relearn_feedback_unknown",
            "relearn_feedback_fuzzy",
            "relearn_feedback_known",
            "relearn_feedback_bar",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("""testTag("$tag")"""))),
                "版式锚 `$tag` 必须恰一处：多一处就分不清哪个节点是被测对象",
            )
        }
    }

    // ---------- 页头 ----------

    /**
     * 返回位归共享页头（spec §3 页头族）。
     *
     * 旧版把「返回生词本」做成页头左边一枚 `Button`、标题挤在右边，是「两个并排的东西」；
     * 页头族的口径是「一枚返回位 + 一枚标题」，可点区与「返回」朗读文案由组件一处定义。
     * 因此本屏不得再自带 `contentDescription = "返回"`——出现即说明又造了一枚私有返回键。
     */
    @Test
    fun `the back affordance comes from the shared screen header`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("AppScreenHeader("))),
            "页头必须走共享 `AppScreenHeader`，且只调用一次",
        )
        val body = bodyOf("fun VocabularyRelearnScreen(")
        assertTrue(body.isNotEmpty(), "取不到 VocabularyRelearnScreen 本体，判据会空转")
        assertTrue(Regex(Regex.escape("""title = "重新学习"""")).containsMatchIn(body), "页头标题是「重新学习」")
        val headerAt = body.indexOf("AppScreenHeader(")
        val onBackAt = body.indexOf("onBack = onBack")
        assertTrue(headerAt >= 0 && onBackAt > headerAt, "`onBack = onBack` 必须落在页头调用里（返回位接宿主回调）")
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("contentDescription = \"返回\""))),
            "本屏不得自带返回键语义：`返回` 的朗读文案归共享页头一处定义",
        )
    }

    // ---------- 吸底：三档评分 ----------

    /**
     * spec §5：「三档评分固定在底部」。
     *
     * 判「恰三枚真实底 `Button`」+「三枚全在吸底条函数体内」+「吸底条是滚动区之后的兄弟节点」。
     * 最后一条是「固定在底部」的几何本质：条若在滚动列里，正文一长它就被推着走。
     */
    @Test
    fun `the three feedback actions live in a bar pinned below the scrolling body`() {
        val bar = bodyOf("private fun RelearnFeedbackBar(")
        assertTrue(bar.isNotEmpty(), "取不到 RelearnFeedbackBar 本体，判据会空转")
        assertEquals(3, countMatches(bar, buttonCall), "三档评分（不认识 / 模糊 / 认识）必须是三枚真实底 Button")
        assertEquals(
            3,
            countMatches(source, buttonCall),
            "全文件只允许这三枚 `Button(`：重试与返回这类次要动作走 `TextButton(`，不再和评分抢实底",
        )
        assertTrue(
            Regex(Regex.escape("""testTag("relearn_feedback_bar")""")).containsMatchIn(bar),
            "三档评分必须被 `testTag(\"relearn_feedback_bar\")` 的吸底条包住",
        )

        val body = bodyOf("fun VocabularyRelearnScreen(")
        val scrollAt = body.indexOf("verticalScroll(")
        val barCallAt = body.indexOf("RelearnFeedbackBar(")
        assertTrue(scrollAt >= 0, "屏幕本体里必须有滚动容器")
        assertTrue(barCallAt > scrollAt, "吸底条必须排在滚动容器之后（兄弟节点），否则正文一长它就跟着走")
    }

    // ---------- 排印 ----------

    /** 六档排印只走 `AppType`，M3 默认 `MaterialTheme.typography` 清零（口径同 Task 1 / Task 2）。 */
    @Test
    fun `the screen types the six tier scale instead of the material default`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "屏侧不得再读 M3 默认排印：六档 `AppType` 是唯一入口（spec §4.2）",
        )
        assertTrue("AppType." in source, "屏侧必须消费 `AppType.*`")
        assertTrue("appColors()" in source, "屏侧必须消费 `appColors()`")
    }

    // ---------- 可滚 ----------

    /**
     * 正文可滚、且只有一个滚动容器。
     *
     * 大字号下词头 + 释义 + 提示一屏放不下时，只有「整块正文可滚 + 评分条不动」这一种解法；
     * 两个滚动容器（比如各态各滚一个）会让同一屏出现两条互相不知道的滚动轴。
     */
    @Test
    fun `the body scrolls in exactly one scroll container`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("verticalScroll("))),
            "根级正文必须恰一个 `verticalScroll(`：大字号不裁字，且只能有一条滚动轴",
        )
    }

    // ---------- 文案保留 ----------

    /**
     * connected 侧 `VocabularyRelearnScreenTest` 的三条用例靠这几句文本定位，一个都不能丢：
     * 标题「重新学习」、进度「第 x / y 个」、完成页「重新学习完成」/「本次完成 N 个生词」、
     * 不可用页「生词本暂时无法加载」。
     */
    @Test
    fun `the texts the connected tests locate are preserved`() {
        assertTrue("\"重新学习\"" in source, "页头标题「重新学习」是本屏的定位文本")
        assertTrue(
            "\"第 \${state.position} / \${state.total} 个\"" in source,
            "进度文案必须保持「第 x / y 个」的形状：connected 侧按「第 1 / 2 个」定位",
        )
        assertTrue("\"重新学习完成\"" in source, "完成页标题文案保留")
        assertTrue("\"本次完成 \${state.total} 个生词\"" in source, "完成页计数文案保留")
        assertTrue("\"生词本暂时无法加载\"" in source, "不可用页文案保留")
    }

    // ---------- 取样助手 ----------

    /**
     * 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。
     *
     * 函数体左花括号必须取**括号深度为 0** 的那一枚：默认参数里可以带 lambda 空体
     * （`onRetry: () -> Unit = {}`），照着「签名之后第一枚 `{`」去配对会取到那个 `{}`，
     * 于是取样面变成空串、判据静默空转。
     */
    private fun bodyOf(signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        var parenDepth = 0
        var open = -1
        for (index in start until source.length) {
            when (source[index]) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '{' -> if (parenDepth == 0) {
                    open = index
                    break
                }
            }
        }
        if (open < 0) return ""
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, index)
                }
            }
        }
        return ""
    }
}
