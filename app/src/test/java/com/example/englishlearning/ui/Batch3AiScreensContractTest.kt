package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 3 · AI 助学七屏（原型 `sAiHome` `app.js:814`、`sAiSentence` `:841`、`sAiPhoto` `:865`、
 * `sAiWordQa` `:894`、`sAiImage` `:929`、`sAiProfiles` `:952`、`sAiProfileEdit` `:1194`）的对齐契约。
 *
 * 这七屏在批 4（版式重做）已各自换过骨架，所以本批**不是**重画，而是逐屏拿原型再核一遍：
 *  - 原型的**版面骨架**（AI 主页两列卡片格、问词页自由输入的气泡流、生图页的三段模板）与本仓
 *    既有裁决冲突的地方，按计划「冲突裁决」与 `docs/decisions/2026-10-29-ui-prototype-token-alignment.md`
 *    逐条留账，不搬；本文件把它们钉成**显式的不存在**，避免下一轮有人「照原型补回来」；
 *  - 原型的**一句话提示 / 分组标题 / 禁用态**是能真兑现的部分，本批补上并逐字钉住。
 *
 * 七条判据一一对应计划对账表 #17–#23，每条只在**本批真的落了东西**的那一屏上判红：
 * #17 页末「图片不上传」提示、#18 切分结果的分组与单卡、#19 翻译前的校对提示、
 * #20 问词页不长出自由输入框（反向钉）、#21 生图页两段分组标题与描述提示、
 * #22/#23 配置页页末离线承诺 + 「未接入控件禁用并标暂未接入」（裁决 B）。
 *
 * 读法是注释剥除后的源文本（[uiScreenSource]），按符号与花括号配对取窗口，不锚行号。
 */
class Batch3AiScreensContractTest {

    private val home: String by lazy { uiScreenSource("AiLearningScreen.kt") }
    private val sentence: String by lazy { uiScreenSource("SentenceAnalysisScreen.kt") }
    private val photo: String by lazy { uiScreenSource("PhotoTranslationScreen.kt") }
    private val wordQa: String by lazy { uiScreenSource("WordAiQaScreen.kt") }
    private val studio: String by lazy { uiScreenSource("ImageStudioScreen.kt") }
    private val profiles: String by lazy { uiScreenSource("AiProfileSettingsScreen.kt") }

    // ---------- #17 AI 主页：页末那一句「什么不出本机」 ----------

    /**
     * 原型 `sAiHome` 的页末 `notice(…, 'info', 'shield')`：拍照翻译的图片不上传、
     * AI 生图先转英文提示词。这两件事分别是 AC3-05 与生图两段式的真实形状，不是宣传句。
     */
    @Test
    fun `the ai home ends with the prototype local only notice`() {
        val expected = "拍照翻译的图片只在本机识别，不上传；AI 生图会先把中文描述转成英文提示词。"
        assertEquals(
            1,
            countMatches(home, Regex(Regex.escape("\"ai_learning_privacy_notice\""))),
            "页末提示必须恰一处（`ai_learning_privacy_notice`）：它是本屏唯一一处「什么不出本机」的汇总句",
        )
        assertTrue(expected in home, "页末提示必须逐字照原型那句话（现值里找不到）")
        assertTrue(
            "InlineNoticeKind.Info" in home,
            "原型给的是 `notice(…, 'info', 'shield')`：必须走共享 `InlineNotice` 的 Info 档，不手搓提示条",
        )
        val screen = bodyOf(home, "fun AiLearningScreen(")
        assertTrue(screen.isNotEmpty(), "取不到 `AiLearningScreen(` 本体，判据会空转")
        val noticeAt = screen.indexOf("ai_learning_privacy_notice")
        val explainerAt = screen.indexOf("\"ai_learning_explainer_toggle\"")
        assertTrue(noticeAt > 0, "页末提示不在 `AiLearningScreen(` 体内：它必须长在主页上")
        assertTrue(
            noticeAt > explainerAt,
            "页末提示必须排在说明折叠行之后（原型把 notice 放在 `.scr-body` 末尾）",
        )
    }

    // ---------- #18 长难句：切分结果收成「一节 + 一张卡」 ----------

    /**
     * 原型把切分结果画成 `section('切分结果', <一张 card 的 rows>, '3 段')`：一个分组标题 + 一张卡里
     * 逐段一行。旧实现是「每段一张软卡」，三段就是三张卡（`spec §4.3` 的卡片汤）。
     */
    @Test
    fun `sentence analysis groups the segments into one card under a section heading`() {
        val segmentList = bodyOf(sentence, "private fun SegmentList(")
        assertTrue(segmentList.isNotEmpty(), "取不到 `SegmentList(` 本体，判据会空转")

        assertEquals(
            1,
            countMatches(segmentList, Regex("""\bSoftCard\(""")),
            "逐段结果必须收进同一张软卡（原型是一张 `card` 的 rows）：每段一张卡就是卡片汤回潮",
        )
        assertEquals(
            1,
            countMatches(segmentList, Regex("""\bBareRow\(""")),
            "逐段行必须走共享 `BareRow`（行内是英文 + 中文 + 角色三档）：不许在屏侧手搓第二份结果行",
        )
        assertTrue(
            "topDivider = index > 0" in segmentList,
            "段间靠发丝线分隔（原型 `.row + .row` 的顶线）：屏侧不手搓 `HorizontalDivider`",
        )
        assertTrue(
            "showChevron = false" in segmentList,
            "切分段是内容不是入口：裸行不许带 chevron（那是「点进去」的暗示）",
        )
        assertTrue(
            "titleMaxLines = 3" in segmentList,
            "英文原句最多三行：切分段本身是长句，一行省略号会把要分析的对象裁掉",
        )
        assertTrue(
            "切分结果" in sentence,
            "分组标题必须逐字照原型「切分结果」",
        )
        val heading = "trailing = \"${'$'}{segments.size} 段\""
        assertTrue(
            heading in sentence,
            "分组标题的 trailing 必须是真实段数（`trailing = \"\${segments.size} 段\"`）：原型的「3 段」是样例，照抄就是编造",
        )
        assertEquals(
            0,
            countMatches(sentence, Regex(Regex.escape("继续追问"))),
            "原型的「继续追问」三枚 chip 不搬：本仓分析是一次性请求，没有多轮会话后端，" +
                "画出来就是点了没反应的死控件",
        )
        assertTrue(
            "英文句子" in sentence,
            "输入框标签照原型「英文句子」（旧句「输入一句英文（最多 600 字符）」把硬上限塞进了标签）",
        )
        assertTrue(
            "可以直接粘贴外刊里的长句" in sentence,
            "输入提示照原型「可以直接粘贴外刊里的长句」：这是这一栏唯一的作用说明",
        )
    }

    // ---------- #19 拍照翻译：翻译前先校对 ----------

    @Test
    fun `photo translation tells the user to proofread before translating`() {
        val expected = "先校对文字再翻译，识别错一个词，译文会差很远。"
        assertEquals(
            1,
            countMatches(photo, Regex(Regex.escape("\"photo_translation_proofread_notice\""))),
            "校对提示必须恰一处（`photo_translation_proofread_notice`）",
        )
        assertTrue(expected in photo, "校对提示必须逐字照原型那句话")
        assertTrue("InlineNoticeKind.Info" in photo, "校对提示走共享 `InlineNotice` 的 Info 档")
        val noticeAt = photo.indexOf("photo_translation_proofread_notice")
        val editorAt = photo.indexOf("photo_translation_text_editor")
        val translateAt = photo.indexOf("photo_translation_translate_button")
        assertTrue(editorAt in 0 until noticeAt, "校对提示必须排在识别文本编辑框之后：先看到字，再被提醒校对")
        assertTrue(noticeAt < translateAt, "校对提示必须排在「翻译成中文」之前：翻完再提醒就晚了")
    }

    // ---------- #20 AI 问词：反向钉住「不长出自由输入框」 ----------

    /**
     * 原型 `sAiWordQa` 是一屏自由输入的对话：底部一枚 `<input placeholder="围绕这个词提问">`。
     * 本仓的防线是「只收 [com.example.englishlearning.wordqa.WordQaKind] 三种固定问法，拒绝任意提示词」，
     * 所以那枚输入框**不能**补——补上就是把提示词注入面开到界面上。这里把原型那两处形状钉成 0。
     */
    @Test
    fun `word qa keeps its fixed ask list and never grows a free text composer`() {
        assertEquals(
            0,
            countMatches(wordQa, Regex(Regex.escape("围绕这个词提问"))),
            "问词页不得长出自由输入框：原型的占位符逐字出现在屏上就说明提示词注入面被开到界面里",
        )
        assertEquals(
            0,
            countMatches(wordQa, Regex(Regex.escape("\"已发送\""))),
            "原型的「已发送」回执属于对话流；本仓没有对话后端，不许留一枚只弹 toast 的假发送",
        )
        assertTrue(
            "word_qa_chip_${'$'}{kind.name}" in wordQa,
            "三枚固定问法必须仍在（tag 仍是 `word_qa_chip_<Kind>`）：这是屏上唯一的提问入口",
        )
        assertTrue(
            "word_qa_lemma" in wordQa,
            "词头那行必须保留（`word_qa_lemma`）：原型头卡里的词义本仓拿不到，至少要说清问的是哪个词",
        )
    }

    // ---------- #21 AI 生图：两段分组 + 描述提示 ----------

    @Test
    fun `image studio frames the prompt and the result as prototype sections`() {
        assertEquals(
            1,
            countMatches(studio, Regex(Regex.escape("\"英文提示词\""))),
            "「英文提示词」分组标题必须恰一处（原型 `section('英文提示词', card-quiet)`）",
        )
        assertEquals(
            1,
            countMatches(studio, Regex(Regex.escape("\"生成结果\""))),
            "「生成结果」分组标题必须恰一处（原型 `section('生成结果', 占位框)`）",
        )
        assertEquals(
            2,
            countMatches(studio, Regex("""\bSectionHeading\(""")),
            "两段分组都走共享 `SectionHeading`：屏侧手搓分组标题就是第二套分组排印",
        )
        assertTrue(
            "描述越具体越好：主体、材质、光线、构图" in studio,
            "描述框的提示照原型那句：它是「怎么写才出好图」的唯一指引",
        )
        assertTrue(
            "\"${'$'}{subject.length}/${'$'}{DrawingPromptPolicy.maxSubjectLength}\"" in studio,
            "硬上限仍要露在输入框脚下：提示句不能把「最多 N 字」挤掉（超长会被截断）",
        )
    }

    // ---------- #22 AI 服务列表：页末离线承诺 ----------

    @Test
    fun `the ai profile list ends with the prototype offline guarantee`() {
        val expected = "没有配置密钥时，所有不依赖 AI 的功能照常可用：背词、复习、听写、本机 OCR。"
        assertEquals(
            1,
            countMatches(profiles, Regex(Regex.escape("\"ai_profiles_offline_notice\""))),
            "页末离线承诺必须恰一处（`ai_profiles_offline_notice`）",
        )
        assertTrue(expected in profiles, "离线承诺必须逐字照原型那句话（它同时是本地优先口径的一句人话）")
        assertTrue("InlineNoticeKind.Info" in profiles, "离线承诺走共享 `InlineNotice` 的 Info 档")
        val list = bodyOf(profiles, "private fun AiProfileList(")
        assertTrue(list.isNotEmpty(), "取不到 `AiProfileList(` 本体，判据会空转")
        assertTrue(
            list.indexOf("ai_profiles_offline_notice") > list.indexOf("\"ai_profiles_add\""),
            "离线承诺必须排在「新增配置」之后（原型把 notice 放在 `.scr-body` 末尾）",
        )
    }

    // ---------- #23 AI 服务编辑：未接入控件「画出来、禁用、标暂未接入」 ----------

    /**
     * 裁决 B（`docs/decisions/2026-10-29-ui-prototype-token-alignment.md` §已知冲突）：
     * 原型的流式 / 语音 / 生图参数**超出 V1 请求协议白名单**，既不能照搬成真控件，也不能整块消失——
     * 「画完整版面，未接入控件禁用并标注「暂未接入」」。落地形状：
     *  - 第四枚软卡「未接入的选项」，四枚 `EntryRow(enabled = false)`，尾部值统一「暂未接入」；
     *  - 禁用走共享件的 `enabled = false`（不挂 `clickable` + `disabled()` 语义），不是「能点却不作为」；
     *  - 四枚既有参数（temperature / top_p / max_tokens / timeout）仍是真控件，不并入这张卡。
     */
    @Test
    fun `the ai profile editor draws the unsupported options disabled and labelled`() {
        val editor = bodyOf(profiles, "private fun AiProfileEditor(")
        assertTrue(editor.isNotEmpty(), "取不到 `AiProfileEditor(` 本体，判据会空转")
        assertEquals(
            1,
            countMatches(profiles, Regex(Regex.escape("\"ai_profile_unsupported_card\""))),
            "未接入组必须恰一处（`ai_profile_unsupported_card`）",
        )
        assertTrue("未接入的选项" in profiles, "分组标题必须是「未接入的选项」：不能混进「高级参数」里假装是可调项")
        listOf(
            "ai_profile_unsupported_stream",
            "ai_profile_unsupported_voice",
            "ai_profile_unsupported_image",
            "ai_profile_unsupported_connection_test",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(profiles, Regex(Regex.escape("\"$tag\""))),
                "未接入控件「$tag」必须恰一处：把原型那几组参数逐项点名画出来，少一项就是「整块消失」",
            )
        }
        assertEquals(
            4,
            countMatches(editor, Regex("""\bUnsupportedOption\(""")),
            "四枚未接入控件必须都走同一个禁用行配方（`UnsupportedOption(`）：屏侧不逐枚手搓",
        )
        val helper = bodyOf(profiles, "private fun UnsupportedOption(")
        assertTrue(helper.isNotEmpty(), "取不到 `UnsupportedOption(` 本体：禁用配方失去了唯一出处")
        assertTrue(
            "enabled = false" in helper,
            "未接入行必须真的禁用（共享 `EntryRow(enabled = false)` 不挂 `clickable` 并补 `disabled()` 语义）",
        )
        assertTrue(
            "trailing = \"暂未接入\"" in helper,
            "尾部值必须是「暂未接入」四个字：裁决 B 的标注口径",
        )
        assertTrue(
            "showChevron = false" in helper,
            "禁用行不许带 chevron：那是「点得进去」的暗示",
        )
    }

    // ---------- 夹具 ----------

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
