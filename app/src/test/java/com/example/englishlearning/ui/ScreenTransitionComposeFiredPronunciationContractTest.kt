package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 D 终评补口 第 4 条：转场两站腿体内「组合即发起出站/发音」的闭集源码闸。
 *
 * 判据本体（`docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md` 批 D 终评补口第 4 条原文）：
 * F5-06 的「转场不得产生第二次计费出站」这条红线，在仪器化上只被**结构性**测到——
 * `ScreenTransitionInstrumentedTest` 的 AI 出站计数腿被夹具钉死恒为 0（词卡腿整段导航本就零 AI 出站），
 * 那一半边没有真靶面。裁定：**不再往仪器化上加码**，改立本闭集源码闸，枚举两站腿体内
 * 「组合即发起」（在 `LaunchedEffect` 里、随组合自动跑，而非 `onSpeak` 那类点击回调里）的发音发起点，
 * 清单外多一枚即判红。
 *
 * 为什么收成闭集而不是动词黑名单：AGENTS.md 本地优先红线要求「转场不许让本来不花钱的一次换屏变成花钱」，
 * 而一次发音（`pronunciationProvider?.speakWord/speakSentence`）在源码面上和一次合规本地取数**长得一模一样**，
 * 靠猜动词分不清。D-3 轮的五处退场闸（`if (route != overlayRoute) return@LaunchedEffect`）就是给这些自动发音立的；
 * 闸只闸「发起」，退场腿重启即早退。本闸登记的正是这批退场闸守护的自动发音发起点，把「多一枚自动发音」变成源码面判红。
 *
 * 扫描面（本文件唯一读取口径，照 `StartupPrefetchLoadPointContractTest`）：`AppScreen.kt` 里位于**两站腿体**内的
 * `LaunchedEffect` 块——腿体区间由 `[OverlayLeg(route == overlayRoute) {, private fun OverlayLeg()]` 两个稳定锚界定：
 * 站 1（全屏子页链）与站 2（tab 分发，嵌在站 1 的 route==null 分支里）都落在这段内；链外的顶层预读
 * `LaunchedEffect(state.profile.id)` 一族（`StartupPrefetchLoadPointContractTest` 的管辖面）在区间之前、不计入。
 * 一个块算「组合即发起发音」当且仅当它调用发音网关 `pronunciationProvider?.(speakWord|speakSentence|speak)`；
 * 点击回调（`onSpeak = { pronunciationProvider?.speakWord(...) }`）不是 `LaunchedEffect`，天然不在扫描面内。
 *
 * 发起点记成「网关方法名@LaunchedEffect-key 首标识符」而不是只记方法名：枚枚自动发音靠 **key** 区分是哪一腿重启
 * （词卡腿 key=cardState、查词详情腿 key=card），只记方法名会让「新增一枚同名自动发音」混在已登记方法里看不见。
 *
 * 三条形状 + 双边沿用本仓房规（`StartupPrefetchLoadPointContractTest`）：
 *  - 读法走共享夹具 [uiScreenSource]（内部已 [stripComments]，注释与 KDoc 里的示例调用不算发起点，**每份源码只剥一次**）；
 *  - 覆盖面有反空转守卫 [composed_pronunciation_extraction_is_not_an_empty_scan]（抽不到已知的两枚自动发音锚点即当场判红，
 *    而不是让 offenders 为空冒充合规）；
 *  - 台账只减不增的反面 [every_registered_composed_pronunciation_site_is_still_wired]（摘掉一枚在册自动发音而不清登记也判红）；
 *  - 判据另有合成双边 [the_composed_pronunciation_closure_flags_an_unregistered_site]（假源码里多写一枚未登记发起点必被命中、
 *    已登记形状与点击回调发音必须放行）。
 *
 * 已知局限，如实登记不假装覆盖：本闸判的是**发音网关的自动发起点名字**。AI「计费出站」在两站腿体内没有任何
 * `LaunchedEffect` 直接发起（今日实测组合即发起的出站计数恒为 0，正是判据 4 的仪器化计数腿被夹具钉零的原因），
 * 若将来有人把一次出站包进腿体 `LaunchedEffect`，需要经由 `pronunciationProvider` 之外的网关——那一层由
 * `ScreenTransitionInstrumentedTest` 的 transport 计数腿与批 C 的零出站台账负责，本闸只锁发音这一族，不越界宣称覆盖 AI 出站。
 */
class ScreenTransitionComposeFiredPronunciationContractTest {

    /**
     * 两站腿体内「组合即发起发音」的闭集（批 D 终评补口第 4 条开工实测两枚，逐枚核对退场闸守护的是它们）。
     * 新增一枚 = 一次范围变化：先确认它是否该受退场闸守护，再在决策册点一句，然后登记到这里（AGENTS.md「范围变化先更新 Spec」）。
     */
    private val allowedComposeFiredPronunciationSites = setOf(
        // 查词详情腿（AppOverlayRoute.VocabularyDetail，腿体 :795 `LaunchedEffect(card.cardId)` 经 `launch{}` 发起，退场闸 :799）
        "speakWord@card",
        // 词卡腿（AppOverlayRoute.LearningFlow，腿体 :1106 `LaunchedEffect((cardState as? …)?.card?.cardId)` 体内直接发起，退场闸 :1109）
        "speakWord@cardState",
    )

    @Test
    fun `composed fired pronunciation sites are all registered`() {
        val offenders = composedPronunciationSites().map { it.signature }.filterNot { it in allowedComposeFiredPronunciationSites }.distinct()
        assertTrue(
            offenders.isEmpty(),
            "两站腿体内出现未登记的「组合即发起」发音点：${offenders.joinToString()}。\n" +
                "转场退场腿会在 300ms overlap 窗口内重启这些自动发音（F5-06 红线：转场不得多一次计费出站/发音）。\n" +
                "若这枚发起点确有退场闸守护且属本地/受控发音，先在决策册点名，再登记进 allowedComposeFiredPronunciationSites 并附一句是哪一腿。",
        )
    }

    @Test
    fun `every registered composed pronunciation site is still wired`() {
        // 台账只减不增的反面：登记过的自动发音今天必须都还在腿体内走。摘掉一枚却不删登记，
        // 闸就会绿在一份没人走的名单上，下一任分不清这是护栏还是化石。
        val wired = composedPronunciationSites().map { it.signature }.toSet()
        val missing = allowedComposeFiredPronunciationSites - wired
        assertTrue(
            missing.isEmpty(),
            "自动发音名单里有已经不在腿体内的发起点：${missing.joinToString()}——请同批删掉登记。",
        )
    }

    @Test
    fun `composed pronunciation extraction is not an empty scan`() {
        val sites = composedPronunciationSites()
        assertEquals(
            EXPECTED_COMPOSED_PRONUNCIATION_SITES,
            sites.size,
            "腿体内自动发音发起点枚数与在册账不符：期望 $EXPECTED_COMPOSED_PRONUNCIATION_SITES，实际 ${sites.size}" +
                "（下限 $MIN_COMPOSED_PRONUNCIATION_SITES）。枚数塌陷通常是腿体锚写法或本文件行走法变了，" +
                "此时 offenders 为空是空扫而不是合规。",
        )
        assertTrue(sites.size >= MIN_COMPOSED_PRONUNCIATION_SITES, "低于在册下限：${sites.size} 枚。")
        // 两枚已知发起点各自证明一种形状被读到：块内单层 `launch{}` 里的发音（查词详情腿）与直接在 effect 体内的发音（词卡腿）。
        val signatures = sites.map { it.signature }
        listOf(
            "speakWord@card" to "查词详情腿的自动发音没被抽到（LaunchedEffect(card.cardId) 里经 launch 发起）",
            "speakWord@cardState" to "词卡腿的自动发音没被抽到（LaunchedEffect((cardState as? …)?.card?.cardId) 体内直接发起）",
        ).forEach { (anchor, why) ->
            assertTrue(anchor in signatures, "抽取面失效：$why —— 锚点 `$anchor` 读不到。")
        }
    }

    @Test
    fun `the composed pronunciation closure flags an unregistered site`() {
        // 合成双边之一：腿体里新增一枚未登记的自动发音（哪怕复用已登记的方法名，只要 key 换了腿），正是本闸存在的理由。
        val offending = """
            OverlayLeg(route == overlayRoute) {
                if (route is AppOverlayRoute.ArticleReading) {
                    LaunchedEffect(readingTarget) {
                        pronunciationProvider?.speakSentence(route.toString(), entity = null)
                    }
                }
            }
            private fun OverlayLeg(isCurrentTarget: Boolean, content: @Composable () -> Unit) {}
        """.trimIndent()
        val flagged = offenders(offending)
        assertTrue(
            flagged.contains("speakSentence@readingTarget"),
            "判据漏判「腿体内长出一枚未登记自动发音」：实际命中 ${flagged.joinToString()}（应含 speakSentence@readingTarget）。",
        )
        // 合成双边之二：已登记形状（同 key 同方法的自动发音）必须放行，否则名单形同虚设、真违规也会混在假红里看不见。
        val compliant = """
            OverlayLeg(route == overlayRoute) {
                if (route is AppOverlayRoute.LearningFlow) {
                    LaunchedEffect(cardState) {
                        pronunciationProvider?.speakWord(lemma, entity = null)
                    }
                }
            }
            private fun OverlayLeg(isCurrentTarget: Boolean, content: @Composable () -> Unit) {}
        """.trimIndent()
        assertTrue(offenders(compliant).isEmpty(), "合规形状被判成违例：${offenders(compliant).joinToString()}")
        // 合成双边之三：点击回调里的发音不是「组合即发起」，不该进扫描面——否则退场闸风险形状与用户主动发音混为一谈。
        val clickHandler = """
            OverlayLeg(route == overlayRoute) {
                CardDetailScreen(onSpeak = { pronunciationProvider?.speakWord(lemma, entity = null) })
            }
            private fun OverlayLeg(isCurrentTarget: Boolean, content: @Composable () -> Unit) {}
        """.trimIndent()
        assertTrue(composedPronunciationSitesIn(clickHandler).isEmpty(), "点击回调里的发音被误算成「组合即发起」发起点：${composedPronunciationSitesIn(clickHandler)}")
    }

    // ---------- 扫描（读法照 StartupPrefetchLoadPointContractTest：逐行走 uiScreenSource，抽 LaunchedEffect 块） ----------

    /** 一枚「组合即发起」发音点：网关方法名 + 所在 `LaunchedEffect` 的 key 首标识符。 */
    private data class Site(val callee: String, val keyAnchor: String) {
        val signature: String get() = "$callee@$keyAnchor"
    }

    /** 真实扫描：先定位两站腿体区间，再在区间内抽自动发音发起点。 */
    private fun composedPronunciationSites(): List<Site> {
        val lines = uiScreenSource("AppScreen.kt").split('\n')
        val legStart = lines.indexOfFirst { LEG_BODY_START_ANCHOR in it }
        val legEnd = lines.indexOfFirst { LEG_BODY_END_ANCHOR in it }
        assertTrue(legStart >= 0, "腿体起点锚 `$LEG_BODY_START_ANCHOR` 在 AppScreen.kt 读不到——站 1 的 AnimatedContent lambda 写法变了，本闸扫描范围会失真。")
        assertTrue(legEnd > legStart, "腿体终点锚 `$LEG_BODY_END_ANCHOR` 读不到或不在起点之后（$legStart / $legEnd）——两站腿体的收口界定失效。")
        return sitesIn(lines, legStart, legEnd)
    }

    /** 真实与合成共用判定：判据只此一份，真实扫描与双边用例不许各写一套口径。 */
    private fun offenders(source: String): Set<String> =
        composedPronunciationSitesIn(source).map { it.signature }.filterNot { it in allowedComposeFiredPronunciationSites }.toSet()

    private fun composedPronunciationSitesIn(source: String): List<Site> = sitesIn(source.split('\n'), 0, source.count { it == '\n' } + 1)

    /** 在 lines[from, to) 内逐枚抽出 `LaunchedEffect` 块的自动发音发起点（区间边界由调用方决定；块收集允许越到 to 之后但在文件内闭合）。 */
    private fun sitesIn(lines: List<String>, from: Int, to: Int): List<Site> {
        val sites = mutableListOf<Site>()
        for (start in from until minOf(to, lines.size)) {
            val headerMatch = EFFECT_HEAD.find(lines[start]) ?: continue
            val indent = headerMatch.groupValues[1].length
            var cursor = start
            var header = lines[start].trim()
            while (parenBalance(header) > 0 && cursor + 1 < lines.size) {
                cursor += 1
                header += " " + lines[cursor].trim()
            }
            assertTrue(parenBalance(header) == 0, "第 $start 行起的 LaunchedEffect 头括号不闭合：$header")
            val keyAnchor = KEY_FIRST_IDENT.find(header)?.groupValues?.get(1)
            assertTrue(keyAnchor != null, "第 $start 行起的 LaunchedEffect 头读不出 key 首标识符：$header")
            val body = if (header.endsWith("}")) {
                header.substringAfter('{', "").substringBeforeLast("}")
            } else {
                val collected = mutableListOf<String>()
                var i = cursor + 1
                val close = buildString { repeat(indent) { append(' ') } }.plus("}")
                while (i < lines.size && i - cursor < MAX_BLOCK_LINES && lines[i].trimEnd() != close) {
                    if (lines[i].isNotBlank()) collected += lines[i].trim()
                    i += 1
                }
                assertTrue(
                    i < lines.size && lines[i].trimEnd() == close,
                    "第 $start 行起的 LaunchedEffect 找不到同缩进的收尾 `}`（缩进 $indent，上限 $MAX_BLOCK_LINES 行）：" +
                        "行走法与真实版面已脱节，本契约的读取范围会吞进整段版面。",
                )
                collected.joinToString("\n")
            }
            GATEWAY_CALL.findAll(body).forEach { sites += Site(callee = it.groupValues[1], keyAnchor = keyAnchor!!) }
        }
        return sites
    }

    private companion object {
        /** 一枚 `LaunchedEffect(` 的头行：缩进 + 调用名 + 左括号（照 StartupPrefetchLoadPointContractTest 同款）。 */
        val EFFECT_HEAD = Regex("""^(\s*)LaunchedEffect\(""")

        /** `LaunchedEffect(` 之后 key 名单的首标识符（跳过左括号与空白）：`(cardState as? …)` 取 cardState、`card.cardId` 取 card。 */
        val KEY_FIRST_IDENT = Regex("""LaunchedEffect\(\s*\(*\s*([A-Za-z_][A-Za-z0-9_]*)""")

        /** 发音网关调用：`pronunciationProvider?.speakWord|speakSentence|speak(`——「组合即发起发音」的唯一识别面。 */
        val GATEWAY_CALL = Regex("""pronunciationProvider\s*(?:\?)?\s*\.\s*(speakWord|speakSentence|speak)\s*\(""")

        /** 两站腿体的收口锚：起点是站 1 `AnimatedContent` lambda 里的 `OverlayLeg(...)` 调用行，终点是其函数定义行。 */
        const val LEG_BODY_START_ANCHOR = "OverlayLeg(route == overlayRoute) {"
        const val LEG_BODY_END_ANCHOR = "private fun OverlayLeg("

        /** 批 D 终评补口第 4 条开工实测枚数（逐枚对上 allowedComposeFiredPronunciationSites）；下限留余量给版面微调。 */
        const val EXPECTED_COMPOSED_PRONUNCIATION_SITES = 2
        const val MIN_COMPOSED_PRONUNCIATION_SITES = 2

        /** 单块收集的行数上限，防版面畸形时无限吞行。 */
        const val MAX_BLOCK_LINES = 400

        fun parenBalance(text: String): Int = text.count { it == '(' } - text.count { it == ')' }
    }
}
