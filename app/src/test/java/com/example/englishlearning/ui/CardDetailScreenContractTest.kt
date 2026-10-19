package com.example.englishlearning.ui

import com.example.englishlearning.ui.components.SpeakerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 16：`CardDetailScreen.kt` 的 JVM 侧源码契约（详情页发音件归一共享药片 + 页头 / 词行接共享组件）。
 *
 * 为什么是「源码文本即契约」（`SourceRatchet.kt` 文件头的同一口径）：本仓 JVM 单测没有 Compose
 * 渲染能力（`ui-test-junit4` 只在 `androidTestImplementation`），48dp 触控、语义合并、live region
 * 播报这些只能在 connected 侧量；本文件因此只钉**形状**与**调用点的归属**，
 * 「真被渲染出来」由 `CardDetailScreenTest` / `CardDetailPronunciationControlsTest` 两类的设备用例负责。
 *
 * 全部探针先经 [stripComments]（[countMatches] 内置）：本屏 KDoc 会合法复述被钉的词，
 * 不剥注释就把契约写成空转。按 14A 的书写坑口径，本文件注释里也**不内嵌**被钉的精确调用形状。
 *
 * 「锚必须绑在生效路径上」（progress.md 记的 14B C-1 / Task 15 修复轮口径）在本屏落成三条判据：
 *  - [callWindows] 的实参窗口钉 **tag / 口音标注归属**（防上提、防把 tag 错挂给另一枚药片）；
 *  - 同一批窗口内钉 **`contentDescription =` 整体缺席**（防拼接 / 模板 / 局部 val 等一切复述形态）；
 *  - 窗口起点必须落在**真正被渲染的那一段组合体**里：药片三枚钉在单词卡函数体内、
 *    词行三枚钉在各自行小节的区间内。只在别处补一枚同形调用（死函数、临时 val）当场判红。
 *
 * 简报里那句 `grep -c "SpeakerButton(" == 1` 在本屏**不可满足**：详情页的现场形状是
 * 美 / 英两枚待播药片 + 播控组一枚暂停-继续药片（三处调用点，各自带自己的 tag），
 * 压成 1 处只有「再造一枚私有包装件」这一条路——那正是本任务要拆的东西。
 * 故按控制者「以现场形状选最小可红写法」的授权钉成 `== 3` + 逐枚窗口归属 + 渲染路径归属，
 * 并配一条「计数被死函数喂饱但渲染路径只剩两枚」的合成负控自证这条判据不是摆设。
 */
class CardDetailScreenContractTest {

    private companion object {
        const val CARD_DETAIL_SCREEN = "CardDetailScreen.kt"

        /** 单词卡（发音件与播控组的实际渲染体）取样起点。 */
        const val HERO_FUNCTION_START = "private fun WordHeroCard("

        const val DERIVED_SECTION = """title = "派生词""""
        const val PHRASES_SECTION = """title = "关联短语""""
        const val SYNONYMS_SECTION = """title = "近义词""""
        const val INFLECTIONS_SECTION = """title = "词形变化""""

        const val SPEAK_TAG = """testTag("card_detail_speak")"""
        const val BRITISH_TAG = """testTag("card_detail_speak_british")"""
        const val TOGGLE_TAG = """testTag("card_detail_pronunciation_toggle")"""
        const val STATUS_MAPPING_CALL = "cardDetailSpeakerState(pronunciationStatus)"

        const val AMERICAN_ACCENT = """accentLabel = "美""""
        const val BRITISH_ACCENT = """accentLabel = "英""""

        /** 旧私有件那枚模板朗读文案的样子（正控用它，本体不进被钉文件的合法形状）。 */
        const val LEGACY_TEMPLATE_READOUT = """semantics { contentDescription = "播放 ${'$'}{card.lemma} 发音" }"""
    }

    // ---------- 共享药片（收 spec §3「两套同构几何」的详情页那一半） ----------

    /** 共享药片调用点：前缀组防私有同族名冒充（私有药片那枚名字末尾就含这串子串）。 */
    private val sharedPillCall = Regex("""(^|[^A-Za-z0-9_.])SpeakerButton\(""")

    /** 药片实参窗口里禁一切定语写法（含拼接/模板/局部 val），三态朗读文案只在组件一处定义。 */
    private val pillContentDescriptionAssignment = Regex("""contentDescription\s*=""")

    private val americanAccent = Regex(AMERICAN_ACCENT)
    private val britishAccent = Regex(BRITISH_ACCENT)

    /** 被废掉的私有播放件：定义 + 调用点各判一次（留着定义就是第二份同构几何）。 */
    private val privatePlaybackDefinitions = Regex("""private fun (?:AccentSpeakerButton|SpeakerGlyph)\(""")
    private val privateSpeakerButtonCall = Regex("""(^|[^A-Za-z0-9_.])AccentSpeakerButton\(""")
    private val privateSpeakerGlyphCall = Regex("""(^|[^A-Za-z0-9_.])SpeakerGlyph\(""")

    /** 自绘字形与触控凑数：字形归 `AppIcons` 清单、48dp 下限归组件的 heightIn。 */
    private val canvasGlyph = Regex("""Canvas\(""")
    private val minimumTouchSizeWorkaround = Regex("""minimumInteractiveComponentSize\(""")

    /** 药片底色凑数：单词卡内不许再给自己铺浅绿底（底色是组件的墨色纯表）。 */
    private val pillBackdrop = Regex("""\.background\(MintTint""")

    /** 屏侧自己念一遍三态文案 = spec §3 点名的第二份真值（含被本任务废掉的旧私有模板文案）。 */
    private val literalReadouts = listOf(
        Regex(""""播放发音"""") to "待播档文案只在组件的 speakerContentDescription 一处定义",
        Regex(""""暂停发音"""") to "暂停档文案同上，屏侧不得另写一份",
        Regex(""""继续发音"""") to "继续档文案同上，屏侧不得另写一份",
        Regex(""""继续播放发音"""") to "Task 15 起由组件的「继续发音」取代，旧私有文案不得回流",
    )

    /** 旧私有件的「播放 + 词头 + 发音」模板朗读文案：字面量计数抓不到模板，本探针专门认这一族。 */
    private val templateReadout = Regex(""""播放 [^"]*发音"""")

    /** 连 `speakerContentDescription(` 都不必调：文案由药片的可见文字承载（clickable 合并子孙）。 */
    private val readoutFunctionCall = Regex("""speakerContentDescription\(""")

    /** 「停止」是播控组的职责、不在药片三态里（组件 KDoc 的同一口径），故文案留本屏、各恰一处。 */
    private val stopReadoutLiteral = Regex("""contentDescription = "停止播放发音"""")
    private val stopLabelLiteral = Regex("""Text\(\s*"停止"\s*\)""")

    // ---------- 页头（详情层是带返回的二级层） ----------

    private val sharedHeaderCall = Regex("""(^|[^A-Za-z0-9_.])AppScreenHeader\(""")
    private val headlineStyleRead = Regex("""AppType\.Headline""")
    private val arrowBackLiteral = Regex(""""←"""")
    private val backReadoutLiteral = Regex(""""返回"""")

    // ---------- 词行（派生词 / 关联短语 / 近义词三节的私有行件） ----------

    private val sharedWordRowCall = Regex("""(^|[^A-Za-z0-9_.])WordRow\(""")
    private val privateRelationLineDefinition = Regex("""private fun RelationLine\(""")
    private val privateRelationLineCall = Regex("""(^|[^A-Za-z0-9_.])RelationLine\(""")
    private val wordRowLemmaArgument = Regex("""lemma =""")
    private val wordRowGlossArgument = Regex("""gloss =""")
    private val wordRowMetaArgument = Regex("""meta =""")

    // ---------- 取样 ----------

    /**
     * 取每一处调用的**实参窗口**（调用点起、到与之配平的第一枚右括号止），返回下标区间。
     *
     * 为什么不能只做全文件计数：`testTag` 一旦从药片自己的 modifier 链上被上提到外层容器，
     * connected 的 tag 断言与 `clickable` 的语义合并同时失效，而「文件里既有调用、又有那串 tag」
     * 的两条独立计数照旧绿（Task 14B C-1 同族）。窗口判据再配函数体 / 小节区间的
     * **渲染路径归属**，才能挡住「在死函数里补一枚同形调用」这种把计数喂饱的写法。
     */
    private fun callWindows(source: String, anchor: Regex): List<IntRange> =
        anchor.findAll(source).map { match ->
            val open = match.range.first + match.value.indexOf('(')
            var index = open + 1
            var depth = 1
            while (index < source.length && depth > 0) {
                depth += when (source[index]) { '(' -> 1; ')' -> -1; else -> 0 }
                index++
            }
            match.range.first until index
        }.toList()

    private fun windowText(source: String, window: IntRange): String = source.substring(window)

    /** 单词卡函数体区间（顶层函数的右花括号写在第 0 列，与本仓既有形状一致）。 */
    private fun heroBodyRange(source: String): IntRange {
        val start = source.indexOf(HERO_FUNCTION_START)
        assertTrue(start >= 0, "找不到 $HERO_FUNCTION_START：本屏的单词卡取样前提变了，须先改判据口径")
        val end = source.indexOf("\n}", start).let { if (it == -1) source.length else it + 2 }
        return start until end
    }

    /** 某一小节（起锚到下一小节锚）的源码区间：词行调用必须落在这里才算长在渲染路径上。 */
    private fun sectionRange(source: String, from: String, to: String): IntRange {
        val start = source.indexOf(from)
        assertTrue(start >= 0, "找不到小节锚 $from：本屏的小节结构变了，词行的渲染路径归属判据失去取样面")
        val end = source.indexOf(to, start)
        assertTrue(end > start, "小节锚 $from 之后找不到结束锚 $to：取样区间无法闭合")
        return start until end
    }

    // ---------- 判据 ----------

    /**
     * 发音件归一（Task 16 主判据）：三枚调用点全在共享药片上，私有件删净，文案不在屏侧复述。
     *
     * 计数 `== 3` 之外另配三条承重点，缺一即「锚绿但账没还」：
     *  ① 三枚窗口**都在单词卡函数体内**（死函数里的同形调用喂不饱这条）；
     *  ② 每枚窗口的 tag / 口音标注 / 态映射各归各处（防上提、防错挂、防丢掉美英标注）；
     *  ③ 窗口内 `contentDescription =` 整体缺席（防拼接与模板复述）。
     */
    @Test
    fun the_word_card_uses_three_shared_pills_on_the_render_path() {
        val source = uiScreenSource(CARD_DETAIL_SCREEN)
        assertEquals(3, countMatches(source, sharedPillCall), "待播美 / 待播英 / 播控暂停-继续三枚都必须是共享药片")

        val hero = heroBodyRange(source)
        val windows = callWindows(source, sharedPillCall)
        assertEquals(3, windows.size, "SpeakerButton 的调用窗口应恰三枚，实际=${windows.size}")
        assertEquals(
            3,
            windows.count { it.first in hero },
            "三枚药片必须全部长在 $HERO_FUNCTION_START 的函数体里：把调用挪进死函数 / 未被调用的私有件，" +
                "计数照旧够三枚，但它不在渲染路径上（14A / Task 15 的「锚必须绑生效路径」口径）",
        )

        assertTrue(
            windows.any { windowText(source, it).contains(SPEAK_TAG) && windowText(source, it).contains("SpeakerState.Idle") &&
                countMatches(windowText(source, it), americanAccent) == 1 },
            "美式药片必须自带 $SPEAK_TAG、态为待播档，并经 accentLabel 给出「美」标注",
        )
        assertTrue(
            windows.any { windowText(source, it).contains(BRITISH_TAG) && windowText(source, it).contains("SpeakerState.Idle") &&
                countMatches(windowText(source, it), britishAccent) == 1 },
            "英式药片必须自带 $BRITISH_TAG、态为待播档，并经 accentLabel 给出「英」标注（丢了标注两口音就分不出来）",
        )
        assertTrue(
            windows.any { windowText(source, it).contains(TOGGLE_TAG) && windowText(source, it).contains(STATUS_MAPPING_CALL) },
            "暂停-继续药片必须自带 $TOGGLE_TAG，且态经 $STATUS_MAPPING_CALL 从宿主呈现态映射",
        )
        windows.forEach { window ->
            val text = windowText(source, window)
            if (text.contains(TOGGLE_TAG)) {
                assertEquals(
                    0,
                    countMatches(text, americanAccent) + countMatches(text, britishAccent),
                    "播控药片不该带口音标注：它表达的是「暂停 / 继续」，不是第二口发音。实际窗口：$text",
                )
            }
        }
        assertEquals(
            0,
            windows.sumOf { countMatches(windowText(source, it), pillContentDescriptionAssignment) },
            "屏侧不得给共享药片另设 contentDescription（直写、模板、拼接、局部 val 皆算）：" +
                "三态朗读文案只在组件一处定义，药片的可见文字已由 clickable 合并进同一枚语义节点",
        )
    }

    /** 私有播放件与它的自绘字形、触控凑数、凑底色一并清零（spec §3「两套同构几何」的详情页那一半）。 */
    @Test
    fun the_private_playback_widgets_are_gone() {
        val source = uiScreenSource(CARD_DETAIL_SCREEN)
        assertEquals(0, countMatches(source, privatePlaybackDefinitions), "私有播放件定义必须删净（留着就是第二份同构几何）")
        assertEquals(0, countMatches(source, privateSpeakerButtonCall), "私有药片调用点必须删净")
        assertEquals(0, countMatches(source, privateSpeakerGlyphCall), "私有字形调用点必须删净")
        assertEquals(0, countMatches(source, canvasGlyph), "屏内不得再自绘喇叭字形：AppIcons 的 33 枚清单之外不加图形")
        assertEquals(0, countMatches(source, minimumTouchSizeWorkaround), "48dp 触控下限由药片的 heightIn 交付，屏侧不再凑数")

        val hero = heroBodyRange(source)
        assertEquals(
            0,
            countMatches(windowText(source, hero), pillBackdrop),
            "单词卡内不得再给自己铺浅绿底：药片底色是组件墨色纯表的 backdrop 槽（唯一真值）。" +
                "本屏那枚词性小标签仍在（未触碰区，归批 3），故禁令按单词卡窗口取样而不是整文件",
        )
    }

    /** 屏侧不复述朗读文案：三枚逐字文案 + 旧私有模板 + 经组件取文案的调用，全部 0 处。 */
    @Test
    fun the_screen_restates_no_playback_wording() {
        val source = uiScreenSource(CARD_DETAIL_SCREEN)
        literalReadouts.forEach { (probe, reason) ->
            assertEquals(0, countMatches(source, probe), "屏侧不得出现该文案形状：$reason")
        }
        assertEquals(0, countMatches(source, templateReadout), "屏侧不得出现旧私有件的模板朗读文案：一口发音一份描述 = 第二份真值")
        assertEquals(0, countMatches(source, readoutFunctionCall), "屏侧不必调 speakerContentDescription：文案由药片的可见文字承载")
    }

    /** 「停止」这一档的文案仍由本屏一处给出，且朗读文案与屏上文字各一枚（口径同词卡屏 Task 15）。 */
    @Test
    fun the_stop_action_keeps_its_single_wording_in_this_screen() {
        val source = uiScreenSource(CARD_DETAIL_SCREEN)
        assertEquals(1, countMatches(source, stopReadoutLiteral), "「停止播放发音」朗读文案恰一处（第二处 = 播控组长出第二份文案）")
        assertEquals(1, countMatches(source, stopLabelLiteral), "屏上「停止」文字恰一处")
    }

    /** 页头接共享组件：恰一处调用，标题与返回回调都传到位，私有页头那套形状整体消失。 */
    @Test
    fun the_detail_screen_header_is_the_shared_component() {
        val source = uiScreenSource(CARD_DETAIL_SCREEN)
        assertEquals(1, countMatches(source, sharedHeaderCall), "详情层是带返回的二级层，页头必须用共享组件（恰一处）")

        val header = windowText(source, callWindows(source, sharedHeaderCall).single())
        assertTrue(
            header.contains("""title = "词义详情""""),
            "页头标题必须以 title 具名实参传给共享组件（写在窗口外 = 组件收到的是默认值，屏上标题会丢）",
        )
        assertTrue(
            header.contains("onBack = onBack"),
            "返回回调必须经 onBack 传给共享组件（组件的返回位只在 onBack 非空时渲染）",
        )

        assertEquals(0, countMatches(source, headlineStyleRead), "私有页头那枚大字号版式读取必须随组件接线一起消失")
        assertEquals(0, countMatches(source, arrowBackLiteral), "自绘「←」字符不在 AppIcons 的 33 枚清单里，页头接共享组件后不许留残余")
        assertEquals(
            0,
            countMatches(source, backReadoutLiteral),
            "「返回」朗读文案归组件的返回位一处定义（spec §3 文案一处定义）；" +
                "屏侧再写一份就是第二份真值，屏侧的返回入口也只剩自绘那一套几何",
        )
    }

    /** 三节关联词行走共享词行：每节恰一枚、实参带齐 lemma/gloss，带词性的两节还带 meta。 */
    @Test
    fun the_relation_rows_use_the_shared_word_row() {
        val source = uiScreenSource(CARD_DETAIL_SCREEN)
        assertEquals(0, countMatches(source, privateRelationLineDefinition), "私有行件的定义必须删净")
        assertEquals(0, countMatches(source, privateRelationLineCall), "私有行件的调用点必须删净")
        assertEquals(3, countMatches(source, sharedWordRowCall), "派生词 / 关联短语 / 近义词三节各一枚共享词行")

        val windows = callWindows(source, sharedWordRowCall)
        assertEquals(3, windows.size, "WordRow 的调用窗口应恰三枚，实际=${windows.size}")
        windows.forEach { window ->
            val text = windowText(source, window)
            assertTrue(
                countMatches(text, wordRowLemmaArgument) == 1 && countMatches(text, wordRowGlossArgument) == 1,
                "词行的 lemma / gloss 槽必须以具名实参各传一次，实际窗口：$text",
            )
        }

        val derived = sectionRange(source, DERIVED_SECTION, PHRASES_SECTION)
        val phrases = sectionRange(source, PHRASES_SECTION, SYNONYMS_SECTION)
        val synonyms = sectionRange(source, SYNONYMS_SECTION, INFLECTIONS_SECTION)
        listOf(
            DERIVED_SECTION to derived,
            PHRASES_SECTION to phrases,
            SYNONYMS_SECTION to synonyms,
        ).forEach { (anchor, range) ->
            val inRegion = windows.filter { it.first in range }
            assertEquals(1, inRegion.size, "$anchor 小节里的词行调用应恰一枚（渲染路径归属），实际=${inRegion.size}")
            val text = windowText(source, inRegion.single())
            if (anchor == PHRASES_SECTION) {
                assertEquals(
                    0,
                    countMatches(text, wordRowMetaArgument),
                    "关联短语没有词性这一栏，不许拿 meta 槽塞别的东西凑数；实际窗口：$text",
                )
            } else {
                assertEquals(
                    1,
                    countMatches(text, wordRowMetaArgument),
                    "$anchor 带词性，必须以 meta 槽保留（丢了就是详情页少一栏信息）；实际窗口：$text",
                )
            }
        }
    }

    /**
     * 宿主呈现态 → 药片三态的映射逐档点名（形状锚只钉「调用了这枚映射」，映射本身对不对得在这里判）。
     *
     * 播控组只在 Playing / Paused 这一档窗口里出现（F3-02 决策 §2），所以只有 `Paused` 该映射成
     * `Paused`（点下去是「继续」那一档）；把 Idle / Played 也映射成 Paused，药片会在用户从没暂停过时
     * 念「继续」那一档。connected 读不到 `SpeakerState`，这档映射只能在 JVM 侧逐档钉死。
     * 遍历 `entries` 而不是手列枚举名：将来给这枚枚举加一档时，本用例会把新档顶出来而不是漏测。
     */
    @Test
    fun the_host_status_maps_onto_the_playback_window_states() {
        assertEquals(SpeakerState.Playing, cardDetailSpeakerState(PronunciationStatus.Playing), "正在播时点下去该是暂停那一档")
        assertEquals(SpeakerState.Paused, cardDetailSpeakerState(PronunciationStatus.Paused), "已暂停时点下去该是继续那一档")
        PronunciationStatus.entries.filter { it != PronunciationStatus.Paused }.forEach { status ->
            assertEquals(
                SpeakerState.Playing,
                cardDetailSpeakerState(status),
                "$status 不是「已暂停」档，映射成 Paused 会让药片在用户从没暂停过时念继续那一档",
            )
        }
        assertEquals(6, PronunciationStatus.entries.size, "呈现态枚数变了：上面那句 filter 的遍历前提要复评（是否需要第三态）")
    }

    // ---------- 正则自证（正片段 + 负片段） ----------

    /**
     * 每条探针的承重自证：负片段一律取「真会被写错的近似形状」（私有同族名、tag 上提、
     * 死函数里的同形调用、模板朗读文案、丢 meta 的词行、挪出小节的词行），不是随手编的无关文本。
     */
    @Test
    fun probes_match_their_target_and_reject_the_near_miss() {
        val pill = "SpeakerButton(\n    state = SpeakerState.Idle,\n    onClick = { onSpeak(card.lemma) },\n" +
            "    modifier = Modifier.testTag(\"card_detail_speak\"),\n    accentLabel = \"美\",\n)"
        assertEquals(1, countMatches(pill, sharedPillCall), "正控：共享药片调用命中")
        assertEquals(
            1,
            callWindows(pill, sharedPillCall).size,
            "正控：窗口里的 lambda 实参含嵌套括号（发音回调套了一层），配平走访不许提前收尾",
        )
        assertEquals(1, countMatches(windowText(pill, callWindows(pill, sharedPillCall).single()), americanAccent), "正控：口音标注尾缀命中")
        assertEquals(0, countMatches("AccentSpeakerButton(accentLabel = \"美\")", sharedPillCall), "负控：私有同族名不得算成共享调用")
        assertEquals(1, countMatches("AccentSpeakerButton(accentLabel = \"美\")", privateSpeakerButtonCall), "正控：私有药片调用点必须被抓到")
        assertEquals(0, countMatches("private fun PillButton(label: String) {}", privateSpeakerButtonCall), "负控：无关私有件不得被本探针误伤")
        assertEquals(1, countMatches("private fun SpeakerGlyph() {}", privatePlaybackDefinitions), "正控：私有字形定义命中")
        assertEquals(0, countMatches("private fun SenseRow(partOfSpeech: String) {}", privatePlaybackDefinitions), "负控：私有释义行不属本探针（未触碰区）")
        assertEquals(1, countMatches("Canvas(modifier = Modifier.size(24.dp)) { }", canvasGlyph), "正控：自绘字形命中")
        assertEquals(0, countMatches("AppGlyph(icon = speakerGlyph(state))", canvasGlyph), "负控：共享字形不得命中")
        assertEquals(1, countMatches("Modifier.minimumInteractiveComponentSize()", minimumTouchSizeWorkaround), "正控：屏侧凑触控下限命中")
        assertEquals(0, countMatches("Modifier.heightIn(min = 48.dp)", minimumTouchSizeWorkaround), "负控：组件自带的 48dp 下限不得命中")
        assertEquals(1, countMatches("Row(modifier.background(MintTint, AppShape.Pill))", pillBackdrop), "正控：屏内凑底色命中")
        assertEquals(0, countMatches("Card(colors = CardDefaults.cardColors(containerColor = MintSurface))", pillBackdrop), "负控：卡片容器色不得命中")

        literalReadouts.forEach { (probe, _) ->
            assertEquals(1, countMatches("""semantics { contentDescription = ${probe.pattern} }""", probe), "正控：屏侧另写 ${probe.pattern} 必须被抓到")
            assertEquals(0, countMatches("Text(text = speakerContentDescription(state))", probe), "负控：经组件取文案不得被误判")
        }
        assertEquals(1, countMatches(LEGACY_TEMPLATE_READOUT, templateReadout), "正控：旧私有件的模板朗读文案必须被本探针抓到")
        assertEquals(0, countMatches("""semantics { contentDescription = "停止播放发音" }""", templateReadout), "负控：「停止」那枚屏侧合法定语不许被模板探针牵连")
        assertEquals(1, countMatches("""semantics { contentDescription = "停止播放发音" }""", stopReadoutLiteral), "正控：停止朗读文案命中")
        assertEquals(0, countMatches("""semantics { contentDescription = "停止播放" }""", stopReadoutLiteral), "负控：文案改一档即不再是那一处定义")
        assertEquals(1, countMatches("""Text("停止")""", stopLabelLiteral), "正控：停止文字命中")
        assertEquals(0, countMatches("""Text("停止播放发音")""", stopLabelLiteral), "负控：把朗读文案写到屏上不得算成文字那一处")

        val hoistedTag = "Box(modifier = Modifier.testTag(\"card_detail_speak\")) {\n    SpeakerButton(state = SpeakerState.Idle, onClick = {})\n}"
        assertEquals(
            0,
            callWindows(hoistedTag, sharedPillCall).sumOf { window -> countMatches(windowText(hoistedTag, window), Regex("card_detail_speak")) },
            "负控（14B C-1 同型）：tag 上提到包裹容器时必须被窗口判在窗外",
        )
        val pillWithConcatReadout = "SpeakerButton(\n    state = cardDetailSpeakerState(pronunciationStatus),\n" +
            "    onClick = { onPauseResume() },\n    modifier = Modifier.testTag(\"card_detail_pronunciation_toggle\")\n" +
            "        .semantics { contentDescription = \"暂停\" + \"发音\" },\n)"
        assertEquals(
            1,
            callWindows(pillWithConcatReadout, sharedPillCall).sumOf { window -> countMatches(windowText(pillWithConcatReadout, window), pillContentDescriptionAssignment) },
            "负控：两半拼出来的朗读文案必须被窗口定语锚点名——字面量计数抓不到它",
        )
        assertEquals(
            0,
            callWindows(pill, sharedPillCall).sumOf { window -> countMatches(windowText(pill, window), pillContentDescriptionAssignment) },
            "正控：现网药片只给 tag 与动作，本锚不得误伤",
        )

        // 渲染路径归属的合成自证：把一枚药片挪进没被调用的私有件，计数仍三枚，但函数体归属只剩两枚。
        val deadFakeSource = "private fun WordHeroCard(card: String) {\n$pill\n" +
            "    SpeakerButton(state = SpeakerState.Idle, onClick = {}, modifier = Modifier.testTag(\"card_detail_speak_british\"), accentLabel = \"英\")\n" +
            "}\n" +
            "private fun UnusedBlock() {\n    SpeakerButton(state = SpeakerState.Idle, onClick = {}, modifier = Modifier.testTag(\"dead_tag\"))\n}"
        assertEquals(3, countMatches(deadFakeSource, sharedPillCall), "合成前提：死函数里补一枚同形调用确实能把计数喂到三枚")
        val deadFakeHero = heroBodyRange(deadFakeSource)
        assertEquals(
            2,
            callWindows(deadFakeSource, sharedPillCall).count { it.first in deadFakeHero },
            "负控（反空转）：计数被死函数喂饱时，渲染路径归属判据必须只认单词卡体内那两枚——" +
                "本锚认得出「调用存在但不在生效路径上」，简报那句纯计数认不出来",
        )

        val withHeader = "AppScreenHeader(\n    title = \"词义详情\",\n    onBack = onBack,\n)"
        assertEquals(1, countMatches(withHeader, sharedHeaderCall), "正控：共享页头调用命中")
        assertEquals(0, countMatches("private fun AppScreenHeaderAlias() {}", sharedHeaderCall), "负控：私有同族名不得算成页头调用")
        assertEquals(1, countMatches(withHeader, Regex("""title = "词义详情"""")), "正控：页头标题实参命中")
        assertEquals(0, countMatches("AppScreenHeader(title = \"今日计划\", onBack = onBack)", Regex("""title = "词义详情"""")), "负控：别的标题不得算成本屏那一处")
        assertEquals(1, countMatches("""Text("词义详情", style = AppType.Headline)""", headlineStyleRead), "正控：私有页头版式读取命中")
        assertEquals(0, countMatches("Text(title, style = AppType.Title)", headlineStyleRead), "负控：组件那侧的 Title 档不得被误判")
        assertEquals(1, countMatches("""semantics { contentDescription = "返回" }""", backReadoutLiteral), "正控：屏侧自写「返回」定语必须被抓到")
        assertEquals(0, countMatches("clickable(onClick = onBack)", backReadoutLiteral), "负控：只传回调不写定语不得被误判")
        assertEquals(1, countMatches("""Text("←", fontSize = 22.sp)""", arrowBackLiteral), "正控：自绘返回箭头字符命中")

        val rowWithMeta = "WordRow(lemma = row.lemma, gloss = row.meaningZh, meta = row.partOfSpeech)"
        assertEquals(1, callWindows(rowWithMeta, sharedWordRowCall).size, "正控：共享词行调用取到窗口")
        assertEquals(1, countMatches(rowWithMeta, wordRowMetaArgument), "正控：词性走 meta 槽命中")
        assertEquals(0, countMatches("WordRow(lemma = row.lemma, gloss = row.meaningZh)", wordRowMetaArgument), "负控：短语节（无词性）不该有 meta")
        assertEquals(0, countMatches("Row { Text(card.lemma); Text(card.meaningZh) }", sharedWordRowCall), "负控：裸组合行不得算成共享调用")
        assertEquals(1, countMatches("private fun RelationLine(word: String) {}", privateRelationLineDefinition), "正控：私有行件定义命中")
        assertEquals(0, countMatches("private fun SenseRow(partOfSpeech: String) {}", privateRelationLineDefinition), "负控：释义行不是本行件")

        val threeSections = "DetailSection(title = \"派生词\") {\n    WordRow(lemma = a, gloss = b, meta = c)\n}\n" +
            "DetailSection(title = \"关联短语\") {\n    WordRow(lemma = d, gloss = e)\n}\n" +
            "DetailSection(title = \"近义词\") {\n    WordRow(lemma = f, gloss = g, meta = h)\n}\n" +
            "DetailSection(title = \"词形变化\") {}"
        val syntheticDerived = sectionRange(threeSections, DERIVED_SECTION, PHRASES_SECTION)
        val syntheticPhrases = sectionRange(threeSections, PHRASES_SECTION, SYNONYMS_SECTION)
        val syntheticSynonyms = sectionRange(threeSections, SYNONYMS_SECTION, INFLECTIONS_SECTION)
        val syntheticWindows = callWindows(threeSections, sharedWordRowCall)
        assertEquals(
            listOf(1, 1, 1),
            listOf(syntheticDerived, syntheticPhrases, syntheticSynonyms).map { range -> syntheticWindows.count { it.first in range } },
            "正控：三节各落一枚词行——小节取样面本身必须站得住",
        )
        assertEquals(
            0,
            syntheticWindows.count { window ->
                window.first !in syntheticDerived && window.first !in syntheticPhrases && window.first !in syntheticSynonyms
            },
            "合成前提：片段里没有小节外的词行，取样面闭合（否则下面那条负控无意义）",
        )
        val misplaced = threeSections.replace("    WordRow(lemma = f, gloss = g, meta = h)\n}\n", "}\n") +
            "private fun DeadRelationBlock() {\n    WordRow(lemma = f, gloss = g, meta = h)\n}"
        assertEquals(
            0,
            callWindows(misplaced, sharedWordRowCall).count { it.first in syntheticSynonyms },
            "负控：把近义词那节的词行挪出小节（塞进死函数），本节的渲染路径归属判据必须失守——" +
                "整文件计数仍三枚、 lemma/gloss/meta 也在，只有本锚认得出「它不在这一节渲染」",
        )
    }
}
