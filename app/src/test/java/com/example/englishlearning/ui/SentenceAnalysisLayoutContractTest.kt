package com.example.englishlearning.ui

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * 批 4 Task 5：长难句重排的源码契约（spec §5「长难句」行 + 计划对账 5）。
 *
 * 该屏旧形态是「文字药片返回 + headlineSmall 自绘标题 + M3 实底按钮 + 描边卡片逐段」。
 * 重做口径：
 *  - 页头换共享 [com.example.englishlearning.ui.components.AppScreenHeader]，标题进页头、
 *    说明句进 `subtitle`，旧「← 返回 AI 学」药片与自绘标题一起下线；
 *  - 逐段结果卡从 M3 `Card(` 换 `SoftCard`（本屏零强卡、零描边）；
 *  - 结果区补两枚**文字药片**操作（复制译文 / 朗读原文）：复制走平台剪贴板，**朗读不引入
 *    语音依赖**——由宿主把 `speakMessage` 与 `onSpeak` 递进来（与拍照翻译同形）；
 *  - 费用只讲一处：常驻成本句保留，出站确认句不再重复「可能产生费用」；
 *  - 主操作唯一：`sentence_analysis_analyze` 是全屏唯一实底药片，其余一律文字/次要药片。
 *
 * 读法是注释剥除后的源文本（[uiScreenSource]），按符号与花括号配对取窗口，不锚行号。
 */
class SentenceAnalysisLayoutContractTest {

    private val source: String by lazy { uiScreenSource("SentenceAnalysisScreen.kt") }

    // ---------- ① 页头 ----------

    @Test
    fun `the screen wears the shared header and the hand drawn title is gone`() {
        assertEquals(
            1,
            countMatches(source, headerCall),
            "全文件 `AppScreenHeader(` 必须恰 1 处（这一屏只有一页页头）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""←\s*返回""")),
            "屏内不得再出现「← 返回…」药片文案：返回位归 AppScreenHeader（contentDescription 固定「返回」）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("\"sentence_analysis_back\""))),
            "旧的 `sentence_analysis_back` tag 随返回药片一起下线：返回位只有页头那一枚",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography""")),
            "排印必须走六档 `AppType`，不得回退 `MaterialTheme.typography`",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""headlineSmall""")),
            "自绘大标题退役：标题进页头，说明句进 `subtitle`",
        )
        val body = bodyOf(source, "fun SentenceAnalysisScreen(")
        assertTrue(body.isNotEmpty(), "取不到 `SentenceAnalysisScreen(` 本体，判据会空转")
        assertTrue("onBack = onBack" in body, "页头必须把 `onBack` 接进返回位")
        assertTrue(
            "subtitle = " in body,
            "说明句必须坐进页头 `subtitle`（旧的那行自绘说明随标题一起搬）",
        )
    }

    // ---------- ② 结果区两枚操作 ----------

    @Test
    fun `the result block ends with copy and speak actions`() {
        val resultAt = source.indexOf("testTag(\"sentence_analysis_result\")")
        assertTrue(resultAt > 0, "源码里找不到 `sentence_analysis_result` 锚点")
        val segmentListBody = bodyOf(source, "private fun SegmentList(")
        assertTrue(segmentListBody.isNotEmpty(), "取不到 `SegmentList(` 本体，判据会空转")

        listOf("sentence_analysis_copy", "sentence_analysis_speak").forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "结果区操作「$tag」必须恰 1 处：复制/朗读各一枚，不许复制出第二个入口",
            )
            assertTrue(
                tag in segmentListBody,
                "「$tag」必须长在结果区（`SegmentList(` 体内）：操作紧跟在逐段解释之后才叫「结果区末尾」",
            )
            assertTrue(
                source.indexOf("\"$tag\"") > resultAt,
                "「$tag」必须排在 `sentence_analysis_result` 之后（先看结果，再决定复制或朗读）",
            )
        }

        // 两枚都是文字药片：实底只留给主操作。
        assertEquals(
            2,
            countMatches(source, Regex("""PillStyle\.Text""")),
            "复制/朗读必须是两枚 `PillStyle.Text` 文字药片（多一枚 = 又冒出实底同权操作）",
        )
    }

    // ---------- ③ 复制走平台剪贴板 ----------

    @Test
    fun `copy goes through the platform clipboard without a new dependency`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("LocalClipboardManager.current"))),
            "复制必须走 `LocalClipboardManager.current`（不新增依赖、不落盘）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("clipboard.setText("))),
            "`clipboard.setText(` 必须恰 1 处：只有「复制译文」写剪贴板",
        )
    }

    // ---------- ④ 朗读归宿主 ----------

    @Test
    fun `speech is delegated to the host instead of touching a speech engine`() {
        val body = bodyOf(source, "fun SentenceAnalysisScreen(")
        assertTrue(body.isNotEmpty(), "取不到 `SentenceAnalysisScreen(` 本体，判据会空转")
        assertTrue(
            "speakMessage: String?," in source,
            "屏侧形参必须有 `speakMessage: String?`（朗读反馈由宿主代次校验后递回来）",
        )
        assertTrue(
            "onSpeak: (String) -> Unit," in source,
            "屏侧形参必须有 `onSpeak: (String) -> Unit`（朗读请求只上报，不由屏内直接发声）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""pronunciationProvider""")),
            "屏内不得直调 `pronunciationProvider`：语音是宿主的能力，屏只发请求",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""TextToSpeech""")),
            "屏内不得自建 `TextToSpeech`：这一屏不引入新的语音依赖",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""onSpeak\(""")),
            "`onSpeak(` 必须恰 1 处（「朗读原文」的点击处）",
        )
    }

    // ---------- ⑤ 费用只讲一处 ----------

    @Test
    fun `the cost is stated exactly once across the screen`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"sentence_analysis_cost_hint\""))),
            "常驻成本句 `sentence_analysis_cost_hint` 必须恰 1 处",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("并可能产生费用"))) <= 1,
            "「并可能产生费用」全屏至多讲一次：常驻成本句已经讲清楚，出站确认句不再复读" +
                "（现值 ${countMatches(source, Regex(Regex.escape("并可能产生费用")))}）",
        )
    }

    // ---------- ⑥ 主操作唯一 ----------

    @Test
    fun `analyze stays the only solid action on the screen`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bButton\(""")),
            "M3 `Button(` 必须清零：实底主操作与文字操作都走 `PillButton` 三档",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"sentence_analysis_analyze\""))),
            "主操作 `sentence_analysis_analyze` 必须恰 1 处",
        )
        assertEquals(
            5,
            countMatches(source, Regex("""\bPillButton\(""")),
            "全屏药片应为 5 枚：主操作 1 + 复制/朗读 2 + 出站确认框取消/确认 2",
        )
        assertEquals(
            2,
            countMatches(source, Regex("""PillStyle\.Primary""")),
            "实底药片恰 2 枚：分析句子（主操作）+ 确认发送（出站确认框的确认键）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""PillStyle\.Secondary""")),
            "次要药片恰 1 枚：出站确认框的取消键",
        )
    }

    // ---------- ⑦ token 闸 + 出站确认面守恒 ----------

    @Test
    fun `the screen reads no mint globals and keeps the outbound confirmation surface`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bMint[A-Za-z0-9_]*""")),
            "`Mint*` 全局读点必须清零：配色一律走 `appColors()` / `appDomain()`",
        )
        assertTrue(
            countMatches(source, Regex("""AppType\.""")) >= 8,
            "六档排印至少要落到 8 处（现 ${countMatches(source, Regex("""AppType\."""))}）：标题/说明/标签/正文/注脚各有所属",
        )
        listOf(
            "sentence_analysis_outbound_confirmation_text",
            "sentence_analysis_decline_outbound",
            "sentence_analysis_confirm_outbound",
        ).forEach { tag ->
            assertTrue(
                tag in source,
                "出站确认三要素「$tag」必须保留：确认前零字节出网这件事实只由它证",
            )
        }
    }

    // ---------- 夹具 ----------

    private val headerCall = Regex("""\bAppScreenHeader\(""")

    /** 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。 */
    private fun bodyOf(text: String, signature: String): String {
        val start = text.indexOf(signature)
        if (start < 0) return ""
        val open = text.indexOf('{', start)
        if (open < 0) return ""
        val close = braceEnd(text, open)
        return if (close < 0) "" else text.substring(open + 1, close)
    }

    /** 从 [open] 这个 `{` 起配对，返回配对的 `}` 下标；未闭合返回 -1（让调用处判红而不是静默裁剪）。 */
    private fun braceEnd(text: String, open: Int): Int {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }
}
