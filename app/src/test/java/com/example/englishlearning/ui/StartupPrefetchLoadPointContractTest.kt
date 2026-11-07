package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 C-5 tab 预读对账闸（判据：`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 4 第 2、3 条与
 * §4 最后一句「tab 预读清单与既有装载点逐条对账（新增出站装载点即红）」；
 * 规格面是 `docs/specs/05-release-quality-and-compliance.md` F5-05 / AC5-06）。
 *
 * 这一条闸要防的是**将来**那一行代码：有人为了「让 AI 页首帧更快」，把一条本来只在点击时才发的出站
 * 挪进 `LaunchedEffect(state.profile.id)`。那一句既花钱又违反 AGENTS.md 的本地优先红线，而它在源码面上
 * 与一条合规的本地取数**长得一模一样**——所以判据不能靠动词黑名单去猜「哪些方法会出站」，
 * 反过来收成闭集：自动预读里只许出现下面登记过的十四枚装载点，第十五枚必须先来这里登记并附一句「它读的是本地哪张表」。
 *
 * 「自动预读」的取法（本文件唯一的读取口径）：`AppScreen.kt` 里 key 名单含 `state.profile.id` 或 `selectedTab`
 * 的 `LaunchedEffect` 块。这两个 key 各自代表「档案就绪」与「换 tab」，都不是用户点出来的动作事件；
 * 用户动作触发的 effect（`onTranslate`、`openSearch`、`consumeOpenedCard` 那一族）不在扫描面内——
 * 把它们算进来会让闸变成「全仓不许有出站」这种没人能守的口号。
 *
 * 装载点记成「接收者.被调名」而不是只记被调名：只记被调名的话，`probeOutbound?.invoke()`
 * 会顶着已登记的 `invoke` 混过去，而 `refreshVocabularySearchIndex?.invoke()` 真正做的事写在**接收者**名字里。
 *
 * 三条形状沿用本目录禁令族惯例（`PrimaryTabSkeletonContractTest`、`AiWaitInProgressContractTest`）：
 * 读法走共享夹具 [uiScreenSource]（内部已 [stripComments]，注释里的示例调用不算违例），
 * 覆盖面有守卫 [prefetch_extraction_is_not_an_empty_scan]（抽不到已知装载点就当场判红，
 * 而不是让 offenders 为空冒充合规），判据另有合成双边 [the_callee_closure_flags_an_outbound_load_point]。
 *
 * 已知局限，如实登记不假装覆盖：闸判的是**装载点名字**。若将来有人把出站包进一枚已登记的
 * `load()`（例如给某个 ViewModel 的 `load` 里塞连接探活），本闸读不到——那一层由 ViewModel 侧的既有用例负责
 * （`AiConnectionProbeTest` 那一族），真出站由批 C-6 的零出站台账复算兜住。
 */
class StartupPrefetchLoadPointContractTest {

    /**
     * 自动预读允许调用点的闭集（批 C-5 开工实测十四枚，逐枚核对过它们是本地读）。
     * 新增一枚等于一次范围变化：先改决策册，再改这里（AGENTS.md「范围变化先更新 Spec」）。
     */
    private val allowedPrefetchCalls = setOf(
        // —— 今日计划与档案级派生数据 ——
        "todayPlanViewModel.load",                    // 今日计划快照：Room 的 daily_plans + 词书
        "refreshVocabularySearchIndex.invoke",        // 把派生索引对齐到当前可见词书：纯本地建索引（决策 §2.4 点名）
        "globalVocabularySearchViewModel.setProfile", // 只对齐身份，不发请求，也不清查询词与结果
        // —— 设置摘要（AI 配置是设备级的，页头徽章要说本机真实状态）——
        "aiProfileViewModel.load",                    // 本地 AI Profile 名单：Room + Keystore 侧摘要，不出站
        "speechSettingsViewModel.load",               // 发音偏好单行表
        // —— 词库页四段 + 收藏集合 ——
        "wordBookWordsViewModel.load",                // 词书页词条
        "vocabularyViewModel.load",                   // 生词段
        "wrongWordsViewModel.load",                   // 错词段（learning_events 派生）
        "favoritesViewModel.load",                    // 收藏段 + 星标派生源
        "vocabularyRelearnViewModel.load",            // 重学列表
        "learningRecordsViewModel.load",              // 学习记录按日详展
        // —— tab 级预读：阅读历史与打卡快照 ——
        "readingAccessViewModel.load",                // 当日文章 + 历史文章（本地表；生成是另一条按钮的事）
        "checkInViewModel.load",                      // 打卡快照
        // —— 拍照翻译历史：`history.listRecent(profileId)` 一句本地读，OCR/翻译都在点击之后 ——
        "photoTranslationViewModel.refreshHistory",
    )

    @Test
    fun `automatic prefetch calls only the registered local load points`() {
        val offenders = prefetchEffects().flatMap { effect ->
            effect.tokens.filterNot { it in allowedPrefetchCalls }.map { it to effect.header }
        }.distinct().toList()
        assertTrue(
            offenders.isEmpty(),
            "自动预读里出现未登记的调用点：${offenders.joinToString("\n") { (token, header) -> "`$token`（在 ${header.foldWhitespace()}）" }}。\n" +
                "预取只能是本地读——AGENTS.md 本地优先 + 决定 4 第 3 条：不得让任何一次点击「本来不发请求」变成「已经发过请求」。\n" +
                "若这枚调用点确实是本地读，先在决策册写清它读哪张表，再把它加进 allowedPrefetchCalls 并附一句为什么。",
        )
    }

    @Test
    fun `every registered load point is still wired`() {
        // 台账只减不增的反面：登记过的十四枚今天必须都还在走。摘掉一枚预读却留着登记，
        // 闸就会绿在一份没人走的名单上，下一任分不清这是护栏还是化石。
        val wired = prefetchEffects().flatMap { it.tokens }.toSet()
        val missing = allowedPrefetchCalls - wired
        assertTrue(missing.isEmpty(), "预读名单里有已经不在用的装载点：${missing.joinToString()}——请同批删掉登记。")
    }

    @Test
    fun `prefetch extraction is not an empty scan`() {
        val effects = prefetchEffects()
        assertEquals(
            EXPECTED_PREFETCH_EFFECTS,
            effects.size,
            "自动预读 effect 枚数与在册账不符：期望 $EXPECTED_PREFETCH_EFFECTS，实际 ${effects.size}" +
                "（下限 $MIN_PREFETCH_EFFECTS）。枚数本身不是红线，但每一枚都要能逐条对上账；" +
                "数量塌陷通常是 key 写法或本文件的行走法变了，此时 offenders 为空是空扫而不是合规。",
        )
        assertTrue(effects.size >= MIN_PREFETCH_EFFECTS, "低于在册下限：${effects.size} 枚。")
        // 三枚已知装载点各自证明一种形状被读到：单行式、块内 `if` 守卫、跨行实参。
        val bodies = prefetchEffects().map { it.body }
        listOf(
            "refreshVocabularySearchIndex?.invoke()" to "单行式 effect 没被抽到（索引对齐那一枚）",
            "checkInViewModel?.load(state.profile.id, keepReady = true)" to "tab 级块 effect 没被抽到（打卡快照那一枚）",
            "readingAccessViewModel?.load(" to "跨行实参的 effect 没被抽到（阅读装载那一枚）",
        ).forEach { (anchor, why) ->
            assertTrue(bodies.any { it.contains(anchor) }, "抽取面失效：$why —— 锚点 `$anchor` 读不到。")
        }
    }

    @Test
    fun `the callee closure flags an outbound load point`() {
        // 合成双边之一：把出站塞进档案级预读，正是本闸存在的理由。
        val offending = """
            LaunchedEffect(state.profile.id) {
                if (showLearning) wordQaViewModel?.ask(profileId = state.profile.id, question = "hello")
            }
        """.trimIndent()
        val flagged = offenders(offending)
        assertTrue(
            flagged.contains("wordQaViewModel.ask"),
            "判据漏判「预读里长出一枚未登记调用」：实际命中 ${flagged.joinToString()}（应含 wordQaViewModel.ask）。",
        )
        // 合成双边之二：同形状的本地取数必须放行，否则名单形同虚设、真违规也会混在假红里看不见。
        val compliant = """
            LaunchedEffect(state.profile.id) {
                if (showLearning) favoritesViewModel?.load(state.profile.id)
            }
        """.trimIndent()
        assertTrue(offenders(compliant).isEmpty(), "合规形状被判成违例：${offenders(compliant).joinToString()}")
        // 合成双边之三：只记被调名会被 `已登记名 + 陌生接收者` 混过去，本闸记的是成对的形状。
        val disguised = """
            LaunchedEffect(state.profile.id) {
                probeOutbound?.invoke()
            }
        """.trimIndent()
        assertTrue(
            offenders(disguised).contains("probeOutbound.invoke"),
            "接收者名不参与判定：`probeOutbound?.invoke()` 必须作为未登记调用点被命中（invoke 只许配 refreshVocabularySearchIndex）。",
        )
    }

    private data class PrefetchEffect(val header: String, val body: String, val tokens: Set<String>)

    /** 逐行走 [uiScreenSource] 的结果，抽出 key 名单命中锚点的 `LaunchedEffect` 块。 */
    private fun prefetchEffects(): List<PrefetchEffect> {
        val lines = uiScreenSource("AppScreen.kt").split('\n')
        val found = mutableListOf<PrefetchEffect>()
        for (start in lines.indices) {
            // 用 find 而不是 matchEntire：头行后面还跟着 key 名单与 `{`，整行匹配等于要求正则吃掉全文，
            // 那样本函数会安静地返回空列表——正是本闸第三条用例要防的那种空扫。
            val headerMatch = EFFECT_HEAD.find(lines[start]) ?: continue
            val indent = headerMatch.groupValues[1].length
            var cursor = start
            var header = lines[start].trim()
            while (parenBalance(header) > 0 && cursor + 1 < lines.size) {
                cursor += 1
                header += " " + lines[cursor].trim()
            }
            assertTrue(parenBalance(header) == 0, "第 $start 行起的 LaunchedEffect 头括号不闭合：$header")
            val body = if (header.endsWith("}")) {
                header.substringAfter('{', "").substringBeforeLast("}")
            } else {
                val collected = mutableListOf<String>()
                var i = cursor + 1
                while (i < lines.size && lines[i].trimEnd() != buildString { repeat(indent) { append(' ') } }.plus("}")) {
                    if (lines[i].isNotBlank()) collected += lines[i].trim()
                    i += 1
                }
                assertTrue(
                    i < lines.size,
                    "第 $start 行起的 LaunchedEffect 找不到同缩进的收尾 `}`（缩进 $indent）：行走法与真实版面已脱节，" +
                        "本契约的读取范围会吞进整段版面。",
                )
                collected.joinToString("\n")
            }
            if (PREFETCH_KEY_ANCHORS.any { it in header }) {
                found += PrefetchEffect(header = header, body = body, tokens = callTokens(body))
            }
        }
        return found
    }

    /** 一块源码里的「接收者.被调名」集合；裸调用（`probe()`）原样交出，由闭集判成未登记。 */
    private fun callTokens(body: String): Set<String> {
        val withoutBraces = body.substringAfter("{", body)
        val tokens = mutableSetOf<String>()
        NULLABLE_CALL.findAll(withoutBraces).forEach { tokens += "${it.groupValues[1]}.${it.groupValues[2]}" }
        DIRECT_CALL.findAll(withoutBraces).forEach { tokens += "${it.groupValues[1]}.${it.groupValues[2]}" }
        BARE_CALL.findAll(withoutBraces).forEach { tokens += it.groupValues[1] }
        return tokens
    }

    /** 合成用例与共用的判定：判据只此一份，真实扫描与双边用例不许各写一套口径。 */
    private fun offenders(source: String): Set<String> = callTokens(source).filterNot { it in allowedPrefetchCalls }.toSet()

    private fun String.foldWhitespace(): String = replace(Regex("""\s+"""), " ").trim()

    private companion object {
        /** 一枚 `LaunchedEffect(` 的头行：缩进 + 调用名 + 左括号。 */
        val EFFECT_HEAD = Regex("""^(\s*)LaunchedEffect\(""")

        /** `接收者?.被调名(`：本仓 ViewModel 形参几乎全是可空调用。 */
        val NULLABLE_CALL = Regex("""([A-Za-z_][A-Za-z0-9_]*)\?\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)\(""")

        /** `接收者.被调名(`：非空形参（如 `todayPlanViewModel.load`）。 */
        val DIRECT_CALL = Regex("""([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)\(""")

        /** 裸函数调用（无接收者）：一律按未登记处理，除非将来专门为它开一档并说明理由。 */
        val BARE_CALL = Regex("""(?<![.\w])([A-Za-z_][A-Za-z0-9_]*)\(""")

        /** 自动预读的 key 锚点：档案就绪与换 tab，两者都不是用户点出来的动作。 */
        val PREFETCH_KEY_ANCHORS = listOf("state.profile.id", "selectedTab")

        /** 批 C-5 开工实测枚数（逐枚对上 [allowedPrefetchCalls]）；下限留一档余量给版面微调。 */
        const val EXPECTED_PREFETCH_EFFECTS = 15
        const val MIN_PREFETCH_EFFECTS = 12

        fun parenBalance(text: String): Int = text.count { it == '(' } - text.count { it == ')' }
    }
}
