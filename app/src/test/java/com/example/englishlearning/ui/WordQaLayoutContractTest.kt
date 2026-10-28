package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 4 Task 7：AI 问词重排的源码契约（spec §5「AI 问词」行 + 计划对账 3）。
 *
 * 该屏旧形态是「实底 `Button` 形态返回 + `headlineSmall` 自绘标题 + 三枚同权实底问法药片 +
 * 笔记区压在问法之上 + 回答沉在页底 + 已保存笔记用描边 `Card` 逐条」。
 * 重做口径（均为 spec §5 原话「4 枚按钮分主次：首选『拆词义』实底，其余文字按钮；回答上移」）：
 *  - 页头换共享 [com.example.englishlearning.ui.components.AppScreenHeader]，旧「← 返回上一层」
 *    与自绘标题一起下线；
 *  - **回答上移**：状态/回答区排在三枚问法**之上**——点完问法立刻在眼前，不必先滚过一排按钮；
 *  - **4 枚按钮分主次**：三枚问法里 `Breakdown`（拆词义）是唯一实底药片，另两枚文字药片；
 *    第 4 枚「保存为笔记」随回答一起出现，取次要点药片；
 *  - 笔记区下沉：「我的笔记」输入与保存排在问法与回答之后；
 *  - 已保存笔记改裸行（`BareRow`），本文件 `Card(` 归零；
 *  - 令牌换装：`Mint*` 读点清零，排印走六档 `AppType`。
 *
 * 读法是注释剥除后的源文本（[uiScreenSource]），按符号与花括号配对取窗口，不锚行号。
 */
class WordQaLayoutContractTest {

    private val source: String by lazy { uiScreenSource("WordAiQaScreen.kt") }

    // ---------- ① 页头 ----------

    @Test
    fun `the screen wears the shared header and the hand drawn title is gone`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bAppScreenHeader\(""")),
            "全文件 `AppScreenHeader(` 必须恰 1 处（这一屏只有一页页头）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""←\s*返回""")),
            "屏内不得再出现「← 返回…」药片文案：返回位归 AppScreenHeader（contentDescription 固定「返回」）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("\"word_qa_back\""))),
            "旧的 `word_qa_back` tag 随返回药片一起下线：返回位只有页头那一枚",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography""")),
            "排印必须走六档 `AppType`，不得回退 `MaterialTheme.typography`",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""headlineSmall""")),
            "自绘大标题退役：标题进页头",
        )
        val body = bodyOf(source, "fun WordAiQaScreen(")
        assertTrue(body.isNotEmpty(), "取不到 `WordAiQaScreen(` 本体，判据会空转")
        assertTrue("onBack = onBack" in body, "页头必须把 `onBack` 接进返回位")
    }

    // ---------- ② 回答上移 ----------

    @Test
    fun `the answer is rendered above the question chips`() {
        listOf("word_qa_answer", "word_qa_answer_kind").forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "「$tag」必须恰 1 处：回答只有一个落点",
            )
        }
        val answerAt = source.indexOf("\"word_qa_answer\"")
        val chipAt = source.indexOf("\"word_qa_chip_")
        assertTrue(answerAt > 0, "源码里找不到 `word_qa_answer` 锚点")
        assertTrue(chipAt > 0, "源码里找不到 `word_qa_chip_` 锚点")
        assertTrue(
            answerAt < chipAt,
            "回答的渲染点必须排在第一枚问法 chip 之前（实际 $answerAt vs $chipAt）：" +
                "spec §5 要「回答上移」，点完问法不该先滚过一排按钮才看到答案",
        )

        // 渲染顺序的真判据在屏体内：状态区（`when (state)`）在三枚问法之前。
        val body = bodyOf(source, "fun WordAiQaScreen(")
        assertTrue(body.isNotEmpty(), "取不到 `WordAiQaScreen(` 本体，判据会空转")
        val stateIdx = body.indexOf("when (state)")
        val chipsIdx = body.indexOf("askKinds.forEach")
        assertTrue(stateIdx >= 0, "屏体内找不到 `when (state)`：状态/回答区结构被改坏")
        assertTrue(chipsIdx >= 0, "屏体内找不到 `askKinds.forEach`：问法列表被改坏")
        assertTrue(
            stateIdx < chipsIdx,
            "状态/回答区必须排在问法之前（实际 $stateIdx vs $chipsIdx）",
        )
    }

    // ---------- ③ 4 枚按钮分主次 ----------

    @Test
    fun `the four buttons split into one solid primary and text tiers`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bButton\(""")),
            "M3 `Button(` 必须清零：问法与保存一律走 `PillButton` 三档（旧三枚问法都是同权实底）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"word_qa_chip_\${kind.name}\""))),
            "问法 tag 必须由 `kind.name` 插值而成：tag 与 `WordQaKind` 枚举一一对应（写死三枚字面量就会与枚举脱钩）",
        )

        val chipBody = bodyOf(source, "private fun QuestionChip(")
        assertTrue(chipBody.isNotEmpty(), "取不到 `QuestionChip(` 本体，判据会空转")
        assertEquals(
            1,
            countMatches(chipBody, Regex("""PillStyle\.Primary""")),
            "问法里只有一枚实底药片（首选「拆词义」）",
        )
        assertEquals(
            1,
            countMatches(chipBody, Regex("""PillStyle\.Text""")),
            "其余问法必须是文字药片：同权实底正是 spec §5 点名要改的那个毛病",
        )
        assertTrue(
            "WordQaKind.Breakdown" in chipBody,
            "实底档必须挂在 `WordQaKind.Breakdown`（拆词义）上：spec §5 指定「首选『拆词义』实底」",
        )
        assertTrue(
            "heightIn(min = 54.dp)" in chipBody,
            "问法药片的 54dp 最小点按高度必须保留（大字号下的可点性）",
        )

        val orderDecl = declarationSpan(source, "private val askKinds")
        assertTrue(orderDecl.isNotEmpty(), "找不到 `private val askKinds` 声明：问法顺序失去唯一真值")
        assertTrue(
            "WordQaKind.Personal" in orderDecl,
            "`Personal`（本地笔记）必须仍被 filter 掉：它不进 AI 问法，走同屏独立输入区",
        )
        val firstKind = Regex("""WordQaKind\.\w+""").find(orderDecl)?.value
        assertEquals(
            "WordQaKind.Breakdown",
            firstKind,
            "问法顺序的首位必须是 `WordQaKind.Breakdown`（实际 $firstKind）：首选项要排在用户最先看到的位置",
        )
    }

    // ---------- ④ 笔记区下沉 ----------

    @Test
    fun `the personal note editor sinks below the questions and the answer`() {
        listOf("word_qa_personal_note", "word_qa_save_personal_note").forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "「$tag」必须恰 1 处：我的笔记只有一个输入口与一个保存键",
            )
        }
        val body = bodyOf(source, "fun WordAiQaScreen(")
        assertTrue(body.isNotEmpty(), "取不到 `WordAiQaScreen(` 本体，判据会空转")
        val chipsIdx = body.indexOf("askKinds.forEach")
        val noteIdx = body.indexOf("word_qa_personal_note")
        val savedListIdx = body.indexOf("SavedNoteList(")
        assertTrue(chipsIdx >= 0, "屏体内找不到 `askKinds.forEach`：问法列表被改坏")
        assertTrue(noteIdx >= 0, "屏体内找不到 `word_qa_personal_note`：笔记输入口不在主列里")
        assertTrue(savedListIdx >= 0, "屏体内找不到 `SavedNoteList(`：已保存笔记块被改坏")
        assertTrue(
            chipsIdx < noteIdx,
            "笔记输入必须排在问法之后（实际 $chipsIdx vs $noteIdx）：先问 AI，再记自己的心得",
        )
        assertTrue(
            noteIdx < savedListIdx,
            "已保存笔记必须排在笔记输入之后（实际 $noteIdx vs $savedListIdx）：先写再翻",
        )
    }

    // ---------- ⑤ 已保存笔记改裸行 ----------

    @Test
    fun `the saved notes list is bare rows instead of bordered cards`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "M3 `Card(` 必须清零：已保存笔记与失败卡都改共享容器",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""CardDefaults""")),
            "`CardDefaults` 随 `Card(` 一起下线（残留即说明还有地方在手工拼卡片色）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""\bBareRow\(""")),
            "已保存笔记必须是 `BareRow(` 逐条（恰 1 处调用点，在循环里）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("testTag(\"word_qa_saved_note_\${note.noteId}\")"))),
            "已保存笔记的 tag 形状 `word_qa_saved_note_\${note.noteId}` 必须保留",
        )
    }

    // ---------- ⑥ 出站确认面 + 失败提示 ----------

    @Test
    fun `the outbound confirmation surface and the save failure notice survive`() {
        listOf(
            "word_qa_outbound_confirmation_text",
            "word_qa_decline_outbound",
            "word_qa_confirm_outbound",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "出站确认三要素「$tag」必须恰 1 处：确认前零字节出网这件事实只由它证",
            )
        }
        assertEquals(
            1,
            countMatches(source, Regex("""GlassDialog\(""")),
            "出站确认框必须仍是唯一一枚 `GlassDialog(`",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"word_qa_save_failed\""))),
            "笔记保存失败锚 `word_qa_save_failed` 必须恰 1 处",
        )
        val at = source.indexOf("\"word_qa_save_failed\"")
        assertTrue(at > 0, "源码里找不到 `word_qa_save_failed` 锚点")
        val window = source.substring(at, minOf(source.length, at + 260))
        assertTrue(
            "InlineNoticeKind.Error" in window,
            "保存失败仍必须包在 `InlineNotice(..., InlineNoticeKind.Error)` 里（窗口：${window.trim()}）",
        )
    }

    // ---------- ⑦ token 闸 + 回答可及名 ----------

    @Test
    fun `the screen reads no mint globals and keeps the answer access label`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bMint[A-Za-z0-9_]*""")),
            "`Mint*` 全局读点必须清零：配色一律走 `appColors()` / `appDomain()`",
        )
        assertTrue(
            countMatches(source, Regex("""AppType\.""")) >= 8,
            "六档排印至少要落到 8 处（现 ${countMatches(source, Regex("""AppType\."""))}）：标题/说明/正文/注脚/标签各有所属",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("contentDescription = \"AI 回答\""))),
            "回答正文的可及名必须逐字保留 `contentDescription = \"AI 回答\"`：TalkBack 靠它区分「AI 说的」与「我说的」",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"word_qa_lemma\""))),
            "单词锚 `word_qa_lemma` 必须恰 1 处（页头只带标题，单词由紧随其后的那一行说明）",
        )
    }

    // ---------- 夹具 ----------

    /**
     * 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。
     *
     * 先走完**形参表**再找函数体的 `{`：本屏的 `onSavePersonalNote: (String) -> Unit = {}` 这类
     * 默认值里自带花括号，直接找签名后的第一个 `{` 会把它当成函数体，取出来的「本体」当场是空串
     * （首跑时就踩过这个坑：判据没红在版式上，红在夹具上）。
     */
    private fun bodyOf(text: String, signature: String): String {
        val start = text.indexOf(signature)
        if (start < 0) return ""
        val openParen = text.indexOf('(', start)
        if (openParen < 0) return ""
        var depth = 0
        var index = openParen
        while (index < text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            index++
        }
        if (index >= text.length) return ""
        val open = text.indexOf('{', index)
        if (open < 0) return ""
        val close = braceEnd(text, open)
        return if (close < 0) "" else text.substring(open + 1, close)
    }

    /** 取一条属性声明的区间：从签名处到其后第一个空行为止（声明的尾巴由缩进结构自己收口）。 */
    private fun declarationSpan(text: String, signature: String): String {
        val start = text.indexOf(signature)
        if (start < 0) return ""
        val nextBlank = text.indexOf("\n\n", start)
        val end = if (nextBlank < 0) minOf(text.length, start + 400) else nextBlank
        return text.substring(start, end)
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
