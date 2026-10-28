package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 4 Task 6：AI 生图重排的源码契约（spec §5「AI 生图」行 + 计划对账 6）。
 *
 * 该屏旧形态是「`TextButton` 形态返回 + `MaterialTheme.typography.headlineSmall` 自绘标题 +
 * 步骤一次排开的两枚同权实底 `Button` + 描边 `Card` 装提示词与失败原因」。
 * 重做口径（spec §5 原话「画面描述可编辑后再生成」）：
 *  - 页头换共享 [com.example.englishlearning.ui.components.AppScreenHeader]，旧「← 返回 AI 学」下线；
 *  - **描述可编辑**：模型写出来的画面描述落在 `SoftCard` 里的输入框，用户改完再生成
 *    （旧形态是只读 `Text`，想改只能重跑一次文本调用——那是白花钱）；
 *  - **生成键吸底**：主操作固定在页底 Surface 上，不随长描述滚走；
 *  - **主操作随状态换身份**：主题态是「生成画面描述」，描述就绪后换成「生成图片」，
 *    两枚落在同一个 `when (state)` 分支结构里，不许出现第三枚「生成」按钮；
 *  - 令牌换装：`Mint*` 读点清零，排印走六档 `AppType`。
 *
 * 读法是注释剥除后的源文本（[uiScreenSource]），按符号与花括号配对取窗口，不锚行号。
 *
 * 须先照准的一处（计划对账 6）：描述可编辑要 VM 收一个形参 `generateImage(promptOverride)`，
 * 这是本批唯一越出「只动呈现层」的地方。本契约按**照准后**的形态钉；若评审席不照准，
 * 第 2 条随实现一起降级为「只读 + 提示重新生成」并登记为未兑现项，不得悄悄放宽。
 */
class ImageStudioLayoutContractTest {

    private val source: String by lazy { uiScreenSource("ImageStudioScreen.kt") }

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
            countMatches(source, Regex(Regex.escape("\"image_studio_back\""))),
            "旧的 `image_studio_back` tag 随返回药片一起下线：返回位只有页头那一枚",
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
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"image_studio_screen\""))),
            "屏根 tag `image_studio_screen` 必须恰 1 处：宿主浮层与 connected 用例都按它取样",
        )
        val body = bodyOf(source, "fun ImageStudioScreen(")
        assertTrue(body.isNotEmpty(), "取不到 `ImageStudioScreen(` 本体，判据会空转")
        assertTrue("onBack = onBack" in body, "页头必须把 `onBack` 接进返回位")
    }

    // ---------- ② 描述可编辑 ----------

    @Test
    fun `the drawing description is editable before the image is generated`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"image_studio_prompt\""))),
            "「image_studio_prompt」必须恰 1 处：画面描述只有一个落点",
        )
        val at = source.indexOf("\"image_studio_prompt\"")
        assertTrue(at > 0, "源码里找不到 `image_studio_prompt` 锚点，判据会空转")

        // 承载该 tag 的那一发输入框调用必须是可编辑输入：窗口内同时有 `value = editedPrompt`
        // 与 `onValueChange`。
        //
        // **夹具勘误（2026-11-01，实施 Task 6 Step 3 时实跑发现）**：计划稿写的是「tag 之后 200 字符内
        // 出现 `onValueChange`」，首版夹具按「anchor 之前最近的那枚 `(`」取窗口（[enclosingCallSpan]）。
        // 那条读法对**任何合法 Compose 写法都判红**，不是实现不合规：输入框的 tag 必然长在
        // `.testTag("…")` 这个**嵌套调用**里，anchor 之前最近的 `(` 是 `testTag(`，窗口只有
        // `("image_studio_prompt")`，永远不含 `onValueChange`/`value = editedPrompt`。
        // 改按**符号**取宿主调用：以输入框的类名为锚找最近一发、且括号窗口真的包住 anchor 的那次调用
        // （[inputFieldCallCarrying]）。窗口内三条要求一字未减，只把「哪一发调用」从字符距离换成括号配对。
        val fieldBody = inputFieldCallCarrying(source, "\"image_studio_prompt\"")
        assertTrue(
            fieldBody.isNotEmpty(),
            "取不到承载 `image_studio_prompt` 的输入框调用窗口：画面描述必须落在 " +
                "`OutlinedTextField(` / `BasicTextField(` 里（spec §5 要「可编辑后再生成」，只读 `Text` 就是旧形态）",
        )
        assertTrue(
            "onValueChange" in fieldBody,
            "描述输入框必须真的可改：调用窗口内找不到 `onValueChange`（窗口：${fieldBody.trim()}）",
        )
        assertTrue(
            "value = editedPrompt" in fieldBody,
            "描述输入框的 `value` 必须来自屏内本地态 `editedPrompt`（窗口：${fieldBody.trim()}）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""var\s+editedPrompt\s+by\s+rememberSaveable""")),
            "本地态 `editedPrompt` 必须由 `rememberSaveable` 承载：转屏/重建不丢用户改过的字",
        )
    }

    // ---------- ③ 生成键吸底 ----------

    @Test
    fun `the generate image button docks to the bottom instead of scrolling away`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"image_studio_generate_image\""))),
            "吸底生成键必须恰 1 处：两枚就是两个真相",
        )
        val at = source.indexOf("\"image_studio_generate_image\"")
        assertTrue(at > 0, "源码里找不到 `image_studio_generate_image` 锚点，判据会空转")
        val window = source.substring(maxOf(0, at - 800), minOf(source.length, at + 800))
        assertTrue(
            "Alignment.BottomCenter" in window,
            "「生成图片」必须落在吸底条上（800 字符窗口内须出现 `Alignment.BottomCenter`）：" +
                "描述一长，主操作就被挤到屏外，这是旧形态的毛病",
        )
        assertTrue(
            "Surface(" in window,
            "吸底条必须是不透明 `Surface(`（滚动内容从它下面穿过，而不是半透明叠字）",
        )
    }

    // ---------- ④ 主操作换身份 ----------

    @Test
    fun `the primary action swaps identity with the state in a single slot`() {
        val promptAt = source.indexOf("\"image_studio_generate_prompt\"")
        val imageAt = source.indexOf("\"image_studio_generate_image\"")
        assertTrue(promptAt > 0, "源码里找不到 `image_studio_generate_prompt` 锚点")
        assertTrue(imageAt > 0, "源码里找不到 `image_studio_generate_image` 锚点")
        assertEquals(
            2,
            countMatches(source, Regex(Regex.escape("\"image_studio_generate_"))),
            "全文件只允许两枚生成键（主题态一枚 + 描述态一枚）：第三枚「生成」按钮就是两个入口抢同一件事",
        )
        assertTrue(
            inSameWhenStateBlock(source, promptAt, imageAt),
            "两枚主操作必须落在**同一个** `when (state)` 分支结构里：同一槽位随状态换身份，" +
                "而不是并排两枚常驻按钮",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bButton\(""")),
            "M3 `Button(` 必须清零：主操作一律走 `PillButton`（旧形态两枚同权实底 Button）",
        )
    }

    // ---------- ⑤ 出站确认面 + 旧卡下线 + 状态锚 ----------

    @Test
    fun `the outbound confirmation surface survives and the legacy cards are gone`() {
        listOf(
            "image_studio_outbound_confirmation_text",
            "image_studio_decline_outbound",
            "image_studio_confirm_outbound",
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
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "M3 `Card(` 必须清零：提示词卡与失败卡都改共享容器",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""CardDefaults""")),
            "`CardDefaults` 随 `Card(` 一起下线（残留即说明还有地方在手工拼卡片色）",
        )
        assertTrue(
            countMatches(source, Regex("""\bSoftCard\(""")) >= 1,
            "提示/失败卡必须落在共享软档容器 `SoftCard(` 上",
        )
        // 其余状态锚：connected 用例逐态取样，重排时不许丢。
        listOf(
            "image_studio_drafting",
            "image_studio_generating",
            "image_studio_result",
            "image_studio_result_unreadable",
            "image_studio_not_configured",
            "image_studio_failure",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "状态锚「$tag」必须恰 1 处",
            )
        }
    }

    // ---------- ⑥ 主题输入与计数 ----------

    @Test
    fun `the subject input and its length counter survive`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"image_studio_subject_input\""))),
            "主题输入锚必须恰 1 处",
        )
        assertEquals(
            3,
            countMatches(source, Regex("""DrawingPromptPolicy\.maxSubjectLength""")),
            "上限常量必须恰 3 处：`take()` 截断保护 + 输入框标签里的上限提示 + 现行的 n/100 计数" +
                "（少一处就是上限失效 / 提示丢失 / 计数消失。计划稿写的「恰 1 处」是笔误——" +
                "本条按三处功能各自必要钉成恰 3，是收紧不是放宽）",
        )
    }

    // ---------- ⑦ token 闸 + 费用句 ----------

    @Test
    fun `the screen reads no mint globals and keeps both cost hints`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bMint[A-Za-z0-9_]*""")),
            "`Mint*` 全局读点必须清零：配色一律走 `appColors()` / `appDomain()`",
        )
        assertTrue(
            countMatches(source, Regex("""AppType\.""")) >= 10,
            "六档排印至少要落到 10 处（现 ${countMatches(source, Regex("""AppType\."""))}）：" +
                "标题/说明/正文/注脚/标签/失败文案各有所属",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"image_studio_cost_hint\""))),
            "文本调用费用句 `image_studio_cost_hint` 必须恰 1 处",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"image_studio_image_cost_hint\""))),
            "按张计费费用句 `image_studio_image_cost_hint` 必须恰 1 处（生成前只讲一次）",
        )
    }

    // ---------- 夹具 ----------

    /**
     * 取**承载** [anchor] 的那一发输入框调用的完整窗口：按类名找最近一发 `OutlinedTextField(` /
     * `BasicTextField(`，再用括号配对确认 [anchor] 真落在这一发的参数里；都落空返回空串，
     * 让调用处响亮判红（不静默裁剪）。
     *
     * 为什么不按「anchor 之前最近的 `(`」取：那条读法（[enclosingCallSpan] 的初版）会把
     * `.testTag("…")` 这个嵌套调用当成宿主，窗口里永远看不到 `value`/`onValueChange`——
     * 对任何合法写法都判红，等于把判据写成了不可满足。这里改按符号配对，窗口取到的是
     * **整发输入框调用**（含 `value` / `onValueChange` / `modifier` 全部实参），要求不减。
     */
    private fun inputFieldCallCarrying(text: String, anchor: String): String {
        val at = text.indexOf(anchor)
        if (at < 0) return ""
        for (callee in listOf("OutlinedTextField", "BasicTextField")) {
            var from = at
            while (true) {
                val nameAt = text.lastIndexOf(callee, from)
                if (nameAt < 0) break
                val open = text.indexOf('(', nameAt + callee.length)
                if (open < 0) break
                // 类名与 `(` 之间只允许空白：`FooOutlinedTextField(` 这类非标准扩展不算本族宿主。
                if (text.substring(nameAt + callee.length, open).isBlank()) {
                    val close = parenEnd(text, open)
                    if (close > 0 && at in (open + 1) until close) return text.substring(nameAt, close + 1)
                }
                from = nameAt - 1
            }
        }
        return ""
    }

    /** 从 [open] 这个 `(` 起配对，返回配对的 `)` 下标；未闭合返回 -1。 */
    private fun parenEnd(text: String, open: Int): Int {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    /** 是否存在某个 `when (state)` 的花括号块同时包含 [first] 与 [second] 两个下标。 */
    private fun inSameWhenStateBlock(text: String, first: Int, second: Int): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf("when (state)", from)
            if (at < 0) return false
            val open = text.indexOf('{', at)
            if (open < 0) return false
            val close = braceEnd(text, open)
            if (close < 0) return false
            if (first in (open + 1) until close && second in (open + 1) until close) return true
            from = open + 1
        }
    }

    /**
     * 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。
     *
     * 先走完**形参表**再找函数体的 `{`：本屏形参里带 `(String) -> Unit` 这类默认值形状，
     * 直接找签名后的第一个 `{` 会把它当成函数体。
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

    /** 从 [open] 这个 `{` 起配对，返回配对的 `}` 下标；未闭合返回 -1（让调用处判红而非静默裁剪）。 */
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
