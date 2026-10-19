package com.example.englishlearning.ui

import com.example.englishlearning.ui.components.SpeakerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 批 2 Task 15：`WordCardScreen.kt` 的 JVM 侧源码契约（退出停播接线 + 发音控件统一 + 揭晓焦点不动）。
 *
 * 为什么是「源码文本即契约」（`SourceRatchet.kt` 文件头的同一口径）：本仓 JVM 单测没有 Compose
 * 渲染能力（`ui-test-junit4` 只在 `androidTestImplementation`），`DisposableEffect` 的触发时机、
 * 大字号排版、语义合并这些只能在 connected 侧量；本文件因此只钉**形状**，
 * 「真的被触发过」由两侧设备用例负责：
 *  - 屏侧 `WordCardScreenTest.leavingTheScreenForwardsTheStopActionExactlyOnce`（离开组合 → 宿主回调恰一次），
 *  - 宿主侧 `AppScreenTest.leavingLearningStopsTheStillPlayingAudioAtTheProvider`（退学习流 → provider 收到一次停播）。
 * 三者合起来才挡住「守卫存在但永不触发」这一族假绿：摘掉接线 → 本文件与设备用例同时红；
 * 接线进不去（分支包死）→ 设备用例红；屏侧自己去碰播放器 → 本文件的播放器符号闸红。
 *
 * 全部探针先经 [stripComments]（[countMatches] 内置）：本屏与守卫类的 KDoc 会合法复述这些词，
 * 不剥注释就把契约写空了。按 14A 的书写坑口径，注释里**也不内嵌**被钉的精确调用形状，
 * 免得判据面哪天改成读原文时自我命中。
 *
 * 「文案一处定义」（spec §3）的口径按 14B R-14.3 走**全文件级计数**，另配两条窗口判据：
 *  - [callWindows] 的实参窗口取样钉 **tag 归属**（防「上提」，Task 14B C-1 同型坑）；
 *  - 同一批窗口内钉 **`contentDescription =` 整体缺席**（防「拼接 / 模板 / copy」等一切复述形态，
 *    修复轮 1 的 I-2：字面量计数抓不住 `"暂停" + "发音"` 那种两半拼出来的定语）。
 */
class WordCardScreenContractTest {

    private companion object {
        const val WORD_CARD_SCREEN = "WordCardScreen.kt"

        /** 停播接线必须落在这句之前（整屏级副作用，不跟状态分支走）。 */
        const val WHEN_STATE = "when (state) {"

        /** 停播接线区段的起点取样锚（`rememberUpdatedState` 那一行）。 */
        const val WIRING_START = "val stopIntent = rememberUpdatedState("
    }

    // ---------- 退出停播接线 ----------

    private val disposableEffectDefinition = Regex("""DisposableEffect\(""")
    private val onDisposeDefinition = Regex("""onDispose\s*\{""")

    /**
     * 正面锚：`onDispose` 体里**只有**「把 dispose 转交给守卫」这一句。
     * 体首锚死 `\{\s*` + 体尾锚死 `\s*\}`，于是 `if (…) guard.onDispose()`、`runCatching { … }`、
     * 再加一句别的动作这些形状都不算命中——停播被条件包住就是「逻辑在、永不触发」的写法。
     */
    private val disposeForwardsToGuard = Regex("""onDispose\s*\{\s*[A-Za-z_][A-Za-z0-9_]*\.onDispose\(\)\s*\}""")

    /** 反面锚：命中即「dispose 体被分支/异常处理包住了」，失败消息直接点名成因。 */
    private val disposeBodyIsGuarded = Regex("""onDispose\s*\{\s*(?:if|when|runCatching|try|return)\b""")

    private val guardClassDefinition = Regex("""class WordCardExitStopGuard\(""")
    private val guardBodyInvokesStop = Regex("""fun onDispose\(\)\s*\{[\s\S]{0,200}?\bonStop\(\)""")

    /** 守卫必须读「最后一版的宿主回调」，否则会拿到首次组合时闭包里的旧回调。 */
    private val updatedStateRead = Regex("""rememberUpdatedState\(""")

    /**
     * 要件②的**消费点**：守卫构造那一枚 lambda 实参的体内必须现读 [WIRING_START] 那个 `stopIntent`。
     *
     * 为什么光钉上一条「声明在场」不够（修复轮 1 的 I-1）：把守卫改成直闭首次组合那版回调后，
     * `rememberUpdatedState` 那一行会留成摆设、其余各锚（DisposableEffect / onDispose 体形状 / 守卫类定义）
     * 全部照旧绿，而退出时转发的是旧闭包——宿主的代次自增与状态清零都写在闭包里，旧值等于漏清。
     * 于是形状钉成「守卫构造的实参必须是一枚体内现读 `stopIntent.value()` 的 lambda」，两种合法书写各一条分支：
     * 尾随 lambda（现文件写法）与具名 `onStop = { … }` / 括号内 lambda。同族三种退化都判红：
     * 直接闭 `onStopPronunciation`、把守卫整个换成方法引用、以及在 `remember` 里当场取值后直传（那仍是首版）。
     * 变量名与 [WIRING_START] 是一对耦合：哪天 `stopIntent` 改名，两条锚必须同批改。
     */
    private val guardReadsUpdatedStateAtDispose = Regex(
        """WordCardExitStopGuard\s*\{\s*stopIntent\.value\(\s*\)\s*\}|WordCardExitStopGuard\(\s*(?:onStop\s*=\s*)?\{\s*stopIntent\.value\(\s*\)\s*\}\s*\)"""
    )

    /**
     * 反面锚：停播接线被「状态门控」包住（`if (state is …) { DisposableEffect(…) }`）。
     *
     * 这种形状在 Ready 之外的态（提交中 / 讲评 / 复习完成 / Loading）根本不会挂守卫，那一路离开屏幕就不停播；
     * Ready 内部换卡又是「条件翻转 → effect  dispose 重建」，变成误停播。
     * 它骗得过上面所有锚（`DisposableEffect`/`onDispose` 计数与体形状都合规），所以必须单独点名。
     */
    private val wiringIsStateGated = Regex("""(?:if|when)\s*\(\s*state\b""")

    /** UI 不碰播放器（既有红线）：屏侧出现任何播放器符号即判红。 */
    private val playerTouch = Regex("""\bstopPlayback\s*\(|\bPronunciationProvider\b""")

    /** 整屏只该有一枚 `LaunchedEffect`（揭晓焦点那枚）；第二枚 = 停播被写成了焦点那种按 key 重跑的形态。 */
    private val launchedEffectDefinition = Regex("""LaunchedEffect\(""")

    // ---------- 发音控件统一 ----------

    /** 共享药片的调用点（`(^|[^A-Za-z0-9_.])` 防 `WordCardSpeakerButton(` 这类私有同族名冒充）。 */
    private val sharedPillCall = Regex("""(^|[^A-Za-z0-9_.])SpeakerButton\(""")

    /** 屏侧自己再念一遍三态文案 = spec §3 点名的第二份真值（含被 Task 15 废掉的旧私有文案）。 */
    private val restatedReadouts = listOf(
        Regex("\"播放发音\"") to "药片待播文案只在 SpeakerButton.kt 的 speakerContentDescription 一处定义",
        Regex("\"暂停发音\"") to "暂停档文案同上，屏侧不得另写一份",
        Regex("\"继续发音\"") to "继续档文案同上，屏侧不得另写一份",
        Regex("\"继续播放发音\"") to "Task 15 起由 speakerContentDescription 的「继续发音」取代，旧私有文案不得回流",
    )

    /** 屏侧连 `speakerContentDescription(` 都不必调：文案由药片自己的可见文字承载（clickable 合并子孙）。 */
    private val readoutFunctionCall = Regex("""speakerContentDescription\(""")

    /**
     * 药片**实参窗口内**的定语禁令（修复轮 1 的 I-2）：`contentDescription =` 一出现即判红，不看右边怎么写。
     *
     * 选型理由（三条路里为什么取「按窗口整体禁令」而非「全文件拼接形状禁令 / 朗读词族碎片计数」）：
     *  - 三态朗读文案的第二份真值只可能长在药片那一枚语义节点上（组件内那句既是朗读文案也是可见文字，
     *    `clickable` 又把子孙合并进同一节点），所以取样面就该是药片的调用窗口，而不是整个屏文件；
     *  - 拼接形状禁令只认 `+` 这一种写法，改走模板、`buildString` 或先赋给局部 val 照样躲得过；
     *    词族碎片计数要绕开本屏有意保留的那一处停止档朗读文案与屏上文字，豁免越写越脆；
     *  - 本屏另有十余处合法 contentDescription（返回计划、进度、音标、词性、释义、例句、重试等），
     *    全文件禁令会逼生产把那些正当定语改成畸形写法——窗口禁令一次封死整族，且不动任何合法形状。
     * 已知残余边界（登记、不在本锚内扩大范围）：先在本窗外构造好定语再 `.then(…)` 挪进药片 modifier，
     * 本锚读不到；那一族形状在屏侧没有正当用途，批 3 复审可再决定是否为它另加一条。
     */
    private val pillContentDescriptionAssignment = Regex("""contentDescription\s*=""")

    /** 三套私有播放件（本 Task 的替换对象）：私有定义 + 自绘字形 + 私有凑底 + 私有触控凑数。 */
    private val privatePlaybackWidgets = Regex("""private fun (?:WordCardSpeakerButton|DetailSpeakerIcon)\(""")
    private val canvasGlyph = Regex("""Canvas\(""")
    private val pillBackdropInScreen = Regex("""\.background\(MintTint""")
    private val minimumTouchSizeWorkaround = Regex("""minimumInteractiveComponentSize\(""")

    /** 「停止」是播控组的职责、不在药片三态里（SpeakerButton.kt KDoc 的同一口径），故文案留本屏、各恰一处。 */
    private val stopReadoutLiteral = Regex("contentDescription = \"停止播放发音\"")
    private val stopLabelLiteral = Regex("""Text\(\s*"停止"\s*\)""")

    /** 药片调用窗口里必须出现的东西：tag 挂在这枚节点上、态从宿主呈现态映射来。 */
    private val speakTagLiteral = """testTag("word_card_speak")"""
    private val toggleTagLiteral = """testTag("word_card_pronunciation_toggle")"""
    private val statusMappingCall = "wordCardSpeakerState(pronunciationStatus)"

    // ---------- 揭晓焦点（F5-03 已建语义顺序，本任务不动） ----------

    private val focusRequesterCreation = Regex("""remember \{ FocusRequester\(\) \}""")
    private val revealFocusRequest = Regex("""runCatching \{ meaningFocusRequester\.requestFocus\(\) \}""")
    private val meaningFocusModifier = Regex("""\.focusRequester\(meaningFocusRequester\)""")
    private val meaningFocusableModifier = Regex("""\.focusable\(\)""")

    // ---------- 取样 ----------

    /**
     * 取每一处 `SpeakerButton(` 调用的**实参窗口**（从调用点到与之配平的第一枚右括号）。
     *
     * 为什么不能只做全文件计数：`testTag` 一旦从药片自己的 modifier 链上被上提到外层容器，
     * connected 的 tag 断言与 `clickable` 的语义合并同时失效，而「文件里既有 `SpeakerButton(`、
     * 又有那串 tag」的两条独立计数照旧绿（Task 14B 评审 C-1 正是这一族）。窗口判据把
     * 「tag 挂在这枚调用上」变成能判红的断言，也顺手封住「把 tag 挪给另一枚药片」的错挂。
     */
    private fun callWindows(source: String, callee: String): List<String> {
        val windows = mutableListOf<String>()
        var from = 0
        while (true) {
            val start = source.indexOf(callee, from)
            if (start < 0) break
            var index = start + callee.length
            var depth = 1
            while (index < source.length && depth > 0) {
                depth += when (source[index]) { '(' -> 1; ')' -> -1; else -> 0 }
                index++
            }
            windows += source.substring(start, index)
            from = index
        }
        return windows
    }

    // ---------- 行为面（无组合依赖的两枚纯件，JVM 侧真跑） ----------

    /**
     * 假播放器：只回答「收到过几次停播」。
     *
     * 调用计数是这一族判据唯一不被恒真形状糊弄的形状——「节点存在」「正则命中」都能被
     * 「逻辑写了但永不触发」骗过去，计数不能。connected 侧另两枚用例（屏侧离开组合、宿主侧真实退出导航）
     * 钉的是「这条路径真被走通」，本用例钉的是「守卫本身转发几次」。
     */
    private class FakePlayer {
        var stopCalls = 0
            private set

        fun stop() {
            stopCalls++
        }
    }

    /** 守卫每轮组合至多转交一次，且新一轮组合照常再转一次（幂等位不得做成跨轮共享）。 */
    @Test
    fun the_guard_forwards_the_stop_exactly_once_per_composition() {
        val player = FakePlayer()
        val guard = WordCardExitStopGuard(player::stop)

        assertEquals(0, player.stopCalls, "守卫构造期（= 组合期）一次都不许转交停播")
        guard.onDispose()
        assertEquals(1, player.stopCalls, "离开组合必须把停播转交恰好一次")
        guard.onDispose()
        assertEquals(1, player.stopCalls, "同一枚守卫被 dispose 两次（替换组合 + 宿主 teardown）只许转一次")

        WordCardExitStopGuard(player::stop).onDispose()
        assertEquals(2, player.stopCalls, "重进学习流是一枚新守卫：那一轮离开照样要停播")
    }

    /**
     * 宿主呈现态 → 药片三态的映射逐档点名（播控窗口只有 Playing / Paused 两档，决策 §2）。
     *
     * 只有 `Paused` 该映射成 `Paused`（点下去是「继续发音」）；把 Idle / Played 也映射成 Paused，
     * 药片会在用户从没暂停过时念「继续」——connected 读不到 `SpeakerState`，这档映射只能在 JVM 侧钉。
     */
    @Test
    fun the_host_status_maps_onto_the_playback_window_states() {
        assertEquals(SpeakerState.Playing, wordCardSpeakerState(PronunciationStatus.Playing), "正在播时点下去是暂停")
        assertEquals(SpeakerState.Paused, wordCardSpeakerState(PronunciationStatus.Paused), "已暂停时点下去是继续")
        listOf(
            PronunciationStatus.Idle,
            PronunciationStatus.Played,
            PronunciationStatus.Unavailable,
            PronunciationStatus.Failed,
        ).forEach { status ->
            assertEquals(
                SpeakerState.Playing,
                wordCardSpeakerState(status),
                "$status 不属于「已暂停」档，映射成 Paused 会让药片在没暂停过时念「继续发音」",
            )
        }
    }

    // ---------- 判据 ----------

    /**
     * F5-03 移交账「退出学习不停播」的还账形状：整屏级 `DisposableEffect` → 守卫 → 恰好一次的宿主停播回调。
     *
     * `rememberUpdatedState` 与守卫类都是判据的一部分，不是风格问题：
     * 少了前者，退出时转发的可能是首次组合那版闭包（宿主的代次计数器与状态写入都在闭包里，旧值等于漏清）；
     * 少了守卫类，JVM 侧就没有可计数的假播放器落点（Compose 组合期在 JVM 跑不出来）。
     * 前者还要钉到**消费点**（[guardReadsUpdatedStateAtDispose]），只数声明行等于给「声明在场但没人读」留门。
     */
    @Test
    fun exit_disposal_forwards_the_stop_to_the_guard_exactly_once() {
        val source = uiScreenSource(WORD_CARD_SCREEN)
        assertEquals(1, countMatches(source, disposableEffectDefinition), "整屏只应一枚 DisposableEffect（多一枚 = 第二个停播点）")
        assertEquals(1, countMatches(source, onDisposeDefinition), "onDispose 只应一处")
        assertEquals(
            1,
            countMatches(source, disposeForwardsToGuard),
            "onDispose 体必须只此一句「把 dispose 转交给守卫」；被 if/when/runCatching 包住、或挪去别处都不算命中",
        )
        assertEquals(0, countMatches(source, disposeBodyIsGuarded), "停播调用被条件/异常处理包死 = 逻辑存在但可能永不触发")
        assertEquals(1, countMatches(source, guardClassDefinition), "退出停播守卫必须恰一处定义")
        assertEquals(1, countMatches(source, guardBodyInvokesStop), "守卫的 onDispose 体必须转调 onStop()")
        assertEquals(1, countMatches(source, updatedStateRead), "守卫必须经 rememberUpdatedState 读宿主回调的最新值")
        assertEquals(
            1,
            countMatches(source, guardReadsUpdatedStateAtDispose),
            "守卫构造那一枚 lambda 实参必须**在 dispose 时**现读 stopIntent.value（要件②的消费点）：" +
                "把宿主的 onStopPronunciation 直接闭进 remember、或提前在 remember 里取值再直传，" +
                "拿到的都是首次组合那版回调，rememberUpdatedState 会退化成摆设",
        )

        val whenIndex = source.indexOf(WHEN_STATE)
        assertTrue(whenIndex >= 0, "找不到 $WHEN_STATE（本屏的状态分派结构变了，本判据的「整屏级」取样前提失效，须先改判据口径）")
        val effectIndex = source.indexOf("DisposableEffect(")
        assertTrue(
            effectIndex in 0 until whenIndex,
            "停播接线必须在 $WHEN_STATE 之前：写进状态分支就变成「切卡 / 揭晓 / 提交翻态」时的误停播，" +
                "而不是「离开屏幕」时停播（effectIndex=$effectIndex, whenIndex=$whenIndex）",
        )

        // 索引判据只挡住「挪进 when 的分支」，挡不住「留在顶层但用 if (state is …) 包一层」：
        // 那形状各锚计数全合规，却是「非 Ready 态离开屏幕 = 不停播」的漏账，所以把接线所在区段单独取样点名。
        val wiringStart = source.indexOf(WIRING_START)
        assertTrue(
            wiringStart in 0 until whenIndex,
            "停播接线的 $WIRING_START 落点必须存在且在 $WHEN_STATE 之前（找不到说明取样前提变了）",
        )
        assertEquals(
            0,
            countMatches(source.substring(wiringStart, whenIndex), wiringIsStateGated),
            "停播接线被 state 门控包住：Ready 之外的态根本不挂守卫（漏停播），Ready 内换态又 dispose 重建（误停播）",
        )
    }

    /** 本屏不碰播放器：停播、暂停、继续一律回调宿主（决策 §2 的分工，Task 9 已钉过一轮）。 */
    @Test
    fun the_screen_never_touches_the_player_itself() {
        val source = uiScreenSource(WORD_CARD_SCREEN)
        assertEquals(0, countMatches(source, playerTouch), "屏侧不得出现播放器符号：UI 只把动作回调给宿主")
    }

    /**
     * 发音控件统一（Task 15 Step 2）：三套私有播放件换共享药片，文案一处定义。
     *
     * 「停止」保留在本屏是**有意**的：药片只有 Idle/Playing/Paused 三态（计划 Task 12 的 Interfaces 逐字钉的），
     * 停止属于播控组职责，见 `SpeakerButton.kt` 的 KDoc。
     */
    @Test
    fun the_screen_uses_the_shared_pill_and_restates_no_readout() {
        val source = uiScreenSource(WORD_CARD_SCREEN)
        assertEquals(2, countMatches(source, sharedPillCall), "喇叭药片与暂停-继续两枚都必须是共享 SpeakerButton（待播 + 播控档各一枚）")
        restatedReadouts.forEach { (probe, reason) ->
            assertEquals(0, countMatches(source, probe), "屏侧不得出现该文案字面量：$reason")
        }
        assertEquals(0, countMatches(source, readoutFunctionCall), "屏侧不必调 speakerContentDescription：文案由药片的可见文字承载")
        assertEquals(0, countMatches(source, privatePlaybackWidgets), "私有播放件必须删净（留着就是第二份同构几何）")
        assertEquals(0, countMatches(source, canvasGlyph), "屏内不得再自绘喇叭字形：AppIcons 的 33 枚清单之外不加图形")
        assertEquals(0, countMatches(source, pillBackdropInScreen), "屏内不得再给发音控件凑底色（底色归组件的墨色纯表）")
        assertEquals(0, countMatches(source, minimumTouchSizeWorkaround), "48dp 触控下限由药片的 heightIn 交付，屏侧不再用 minimumInteractiveComponentSize 凑数")

        val windows = callWindows(source, "SpeakerButton(")
        assertEquals(2, windows.size, "SpeakerButton 的调用窗口应恰两枚，实际=${windows.size}")
        assertTrue(
            windows.any { it.contains(speakTagLiteral) && it.contains("SpeakerState.Idle") },
            "待播药片必须自带 $speakTagLiteral 且态为 SpeakerState.Idle（tag 上提到包裹容器 = connected 打得开、语义合并没了）",
        )
        assertTrue(
            windows.any { it.contains(toggleTagLiteral) && it.contains(statusMappingCall) },
            "暂停-继续药片必须自带 $toggleTagLiteral 且态经 $statusMappingCall 从宿主呈现态映射",
        )
        assertEquals(
            0,
            windows.sumOf { countMatches(it, pillContentDescriptionAssignment) },
            "屏侧不得给共享药片另设 contentDescription（直写、模板、拼接、局部 val 皆算，见 I-2 选型）：" +
                "三态朗读文案只在组件一处定义，药片的可见文字已由 clickable 合并进同一枚语义节点，" +
                "屏侧再补一句定语就是第二份真值 + 读屏重复朗读",
        )
    }

    /** 「停止」这一档的文案仍由本屏一处给出，且朗读文案与屏上文字各一枚。 */
    @Test
    fun the_stop_action_keeps_its_single_wording_in_this_screen() {
        val source = uiScreenSource(WORD_CARD_SCREEN)
        assertEquals(1, countMatches(source, stopReadoutLiteral), "「停止播放发音」朗读文案恰一处（第二处 = 播控组长出第二份文案）")
        assertEquals(1, countMatches(source, stopLabelLiteral), "屏上「停止」文字恰一处")
    }

    /** 揭晓焦点（F5-03 已建的语义顺序）在本任务里不许动：`WordCardRevealFocusTest` 是它的设备侧护栏。 */
    @Test
    fun the_reveal_focus_handover_stays_intact() {
        val source = uiScreenSource(WORD_CARD_SCREEN)
        assertEquals(1, countMatches(source, launchedEffectDefinition), "揭晓焦点的 LaunchedEffect 只此一枚")
        assertEquals(1, countMatches(source, focusRequesterCreation), "释义焦点把手只此一处")
        assertEquals(1, countMatches(source, revealFocusRequest), "揭晓后 requestFocus 必须仍被 runCatching 包住（释义节点可能已随提交离开组合）")
        assertEquals(1, countMatches(source, meaningFocusModifier), "释义节点必须仍挂着那枚焦点把手")
        assertEquals(1, countMatches(source, meaningFocusableModifier), "释义节点必须仍 focusable()：只有 FocusableNode 会写 SemanticsProperties.Focused")
    }

    // ---------- 正则自证（正片段 + 负片段） ----------

    /**
     * 每条探针的承重自证：负片段一律取「真会被写错的近似形状」（条件包死、tag 上提、私有同族名、
     * 注释里的复述），不是随手编的无关文本——先剥注释的源码契约没有正/负双控等于自证不了它在数什么。
     */
    @Test
    fun probes_match_their_target_and_reject_the_near_miss() {
        val wired = """
            val exitStopGuard = remember { WordCardExitStopGuard { stopIntent.value() } }
            DisposableEffect(exitStopGuard) {
                onDispose { exitStopGuard.onDispose() }
            }
        """.trimIndent()
        assertEquals(1, countMatches(wired, disposableEffectDefinition), "正控：整屏级 DisposableEffect 命中")
        assertEquals(1, countMatches(wired, disposeForwardsToGuard), "正控：onDispose 体只转交守卫时必须命中")
        assertEquals(0, countMatches(wired, disposeBodyIsGuarded), "正控：合规形状不得被反面锚命中")

        val conditionalDispose = "DisposableEffect(guard) {\n    onDispose { if (isPlaying) guard.onDispose() }\n}"
        assertEquals(0, countMatches(conditionalDispose, disposeForwardsToGuard), "负控：停播被 if 包死（永不触发的合法写法）不得命中正面锚")
        assertEquals(1, countMatches(conditionalDispose, disposeBodyIsGuarded), "负控：条件包死的 dispose 体必须被反面锚点名")
        val commentedOutDispose = "DisposableEffect(guard) {\n    onDispose {\n        // guard.onDispose()\n    }\n}"
        assertEquals(0, countMatches(commentedOutDispose, disposeForwardsToGuard), "负控：注释掉的转交不得命中（先剥注释）")

        assertEquals(0, countMatches(wired, wiringIsStateGated), "正控：顶层裸接线的区段不得被状态门控锚点名")
        val stateGatedWiring = "if (state is WordCardUiState.Ready) {\n    DisposableEffect(exitStopGuard) {\n" +
            "        onDispose { exitStopGuard.onDispose() }\n    }\n}"
        assertEquals(
            1,
            countMatches(stateGatedWiring, wiringIsStateGated),
            "负控：状态门控包住的接线必须被点名——它其余各锚全合规，只有这条锚认得出来",
        )

        val guardBody = "internal class WordCardExitStopGuard(private val onStop: () -> Unit) {\n" +
            "    private var stopped = false\n\n" +
            "    fun onDispose() {\n        if (stopped) return\n        stopped = true\n        onStop()\n    }\n}"
        assertEquals(1, countMatches(guardBody, guardClassDefinition), "正控：守卫定义命中")
        assertEquals(1, countMatches(guardBody, guardBodyInvokesStop), "正控：守卫体内转调 onStop() 命中（前面允许有幂等位）")
        assertEquals(0, countMatches("class WordCardExitStopGuard(onStop: () -> Unit) {\n    fun onDispose() = Unit\n}", guardBodyInvokesStop), "负控：空实现的 onDispose 不得命中")

        assertEquals(1, countMatches(wired, guardReadsUpdatedStateAtDispose), "正控：守卫经尾随 lambda 现读末版回调必须命中")
        assertEquals(
            1,
            countMatches("val g = remember { WordCardExitStopGuard(onStop = { stopIntent.value() }) }", guardReadsUpdatedStateAtDispose),
            "正控：同一形状的具名实参写法不得被误判（换写法不是换语义）",
        )
        assertEquals(
            1,
            countMatches("val g = remember { WordCardExitStopGuard({ stopIntent.value() }) }", guardReadsUpdatedStateAtDispose),
            "正控：括号内传实参 lambda（不具名）同样命中——本锚认的是「体内现读」，不是括号长相",
        )
        assertEquals(
            0,
            countMatches("val g = remember { WordCardExitStopGuard(onStopPronunciation) }", guardReadsUpdatedStateAtDispose),
            "负控（评审 M1 形状）：守卫直闭首次组合那版回调时必须判红——此时 rememberUpdatedState 声明行原样还在，" +
                "其余各锚（DisposableEffect / onDispose 体 / 守卫类定义 / 声明计数）全绿，只有本锚认得出来",
        )
        assertEquals(
            0,
            countMatches("val g = remember { WordCardExitStopGuard(stopIntent.value()) }", guardReadsUpdatedStateAtDispose),
            "负控：在 remember 里当场取值后直传拿到的同样是首次组合那版，不得命中",
        )
        assertEquals(
            0,
            countMatches("val g = remember { WordCardExitStopGuard { staleIntent.value() } }", guardReadsUpdatedStateAtDispose),
            "负控：改读别的 state 句柄不得算成消费了本接线区那枚 rememberUpdatedState（与 WIRING_START 的耦合口径）",
        )
        assertEquals(
            0,
            countMatches("// WordCardExitStopGuard { stopIntent.value() }", guardReadsUpdatedStateAtDispose),
            "负控：注释里的复述不得算成消费点（先剥注释）",
        )

        val sharedPill = "SpeakerButton(state = SpeakerState.Idle, onClick = { onSpeak(lemma) }, modifier = Modifier.testTag(\"word_card_speak\"))"
        assertEquals(1, countMatches(sharedPill, sharedPillCall), "正控：共享药片调用命中")
        assertEquals(0, countMatches("WordCardSpeakerButton(lemma = lemma)", sharedPillCall), "负控：私有同族名不得算成共享调用")
        assertEquals(0, countMatches("// SpeakerButton(state = SpeakerState.Idle)", sharedPillCall), "负控：注释里的复述不得算成调用")

        assertEquals(1, countMatches("private fun WordCardSpeakerButton(lemma: String) {}", privatePlaybackWidgets), "正控：私有播放件定义必须被抓到")
        assertEquals(0, countMatches("private fun FeedbackButton(state: WordCardUiState.Ready) {}", privatePlaybackWidgets), "负控：无关私有件不得被本探针误伤")
        assertEquals(1, countMatches("Canvas(modifier = Modifier.size(20.dp)) { }", canvasGlyph), "正控：自绘字形命中")
        assertEquals(0, countMatches("AppGlyph(icon = speakerGlyph(state))", canvasGlyph), "负控：共享字形不得命中")
        assertEquals(1, countMatches("Row(modifier.background(MintTint, RoundedCornerShape(50)))", pillBackdropInScreen), "正控：屏内凑底色命中")
        assertEquals(0, countMatches("Card(colors = CardDefaults.cardColors(containerColor = MintSurface))", pillBackdropInScreen), "负控：卡片容器色不得命中")
        assertEquals(1, countMatches("Modifier.minimumInteractiveComponentSize()", minimumTouchSizeWorkaround), "正控：屏侧凑触控下限命中")
        assertEquals(0, countMatches("Modifier.heightIn(min = 48.dp)", minimumTouchSizeWorkaround), "负控：药片自带的 48dp 下限不得命中")

        assertEquals(1, countMatches("""semantics { contentDescription = "停止播放发音" }""", stopReadoutLiteral), "正控：停止朗读文案命中")
        assertEquals(0, countMatches("""semantics { contentDescription = "停止播放" }""", stopReadoutLiteral), "负控：文案改一档即不再是那一处定义")
        assertEquals(1, countMatches("""Text("停止")""", stopLabelLiteral), "正控：停止文字命中")
        assertEquals(0, countMatches("""Text("停止播放发音")""", stopLabelLiteral), "负控：把朗读文案写到屏上不得算成文字那一处")

        restatedReadouts.forEach { (probe, _) ->
            assertEquals(1, countMatches("""Text(text = ${probe.pattern})""", probe), "正控：屏侧另写 ${probe.pattern} 必须被抓到")
            assertEquals(0, countMatches("Text(text = speakerContentDescription(state))", probe), "负控：经组件取文案不得被误判")
        }

        val hoistedTag = "Box(modifier = Modifier.testTag(\"word_card_speak\")) {\n    SpeakerButton(state = SpeakerState.Idle, onClick = {})\n}"
        assertEquals(2, callWindows("$sharedPill\nRow { SpeakerButton(state = SpeakerState.Playing, onClick = {}) }", "SpeakerButton(").size, "正控：两处调用要取出两个窗口")
        assertEquals(0, callWindows("Text(\"没有药片\")", "SpeakerButton(").size, "负控：没有调用时窗口集为空")
        assertTrue(callWindows(sharedPill, "SpeakerButton(").single().contains(speakTagLiteral), "正控：窗口内含本节点的 testTag")
        assertFalse(callWindows(hoistedTag, "SpeakerButton(").single().contains(speakTagLiteral), "负控（14B C-1 同型）：tag 上提到包裹容器时必须被窗口判在窗外")

        val pillWithConcatReadout = "SpeakerButton(\n" +
            "    state = wordCardSpeakerState(pronunciationStatus),\n" +
            "    onClick = { onPauseResume() },\n" +
            "    modifier = Modifier.testTag(\"word_card_pronunciation_toggle\")\n" +
            "        .semantics { contentDescription = \"暂停\" + \"发音\" },\n" +
            ")"
        assertEquals(
            1,
            callWindows(pillWithConcatReadout, "SpeakerButton(").size,
            "负控（评审 M2 形状）：往药片 modifier 链尾追加定语仍落在同一枚实参窗口内，不许被算成窗外",
        )
        assertEquals(
            1,
            callWindows(pillWithConcatReadout, "SpeakerButton(").sumOf { countMatches(it, pillContentDescriptionAssignment) },
            "负控（评审 M2 形状）：两半拼出来的朗读文案必须被窗口定语锚点名——四串字面量计数抓不到它",
        )
        assertEquals(
            0,
            callWindows(sharedPill, "SpeakerButton(").sumOf { countMatches(it, pillContentDescriptionAssignment) },
            "正控：现网药片只给 tag 与动作，本锚不得误伤",
        )
        assertEquals(
            0,
            callWindows(
                "$sharedPill\nOutlinedButton(modifier = Modifier.testTag(\"word_card_pronunciation_stop\")" +
                    ".semantics { contentDescription = \"停止播放发音\" })",
                "SpeakerButton(",
            ).sumOf { countMatches(it, pillContentDescriptionAssignment) },
            "正控：播控组里那枚「停止」按钮是同窗外的兄弟节点，屏侧合法定语不得被本锚牵连",
        )

        val legacyFocus = "val meaningFocusRequester = remember { FocusRequester() }\n" +
            "LaunchedEffect(revealed) { runCatching { meaningFocusRequester.requestFocus() } }\n" +
            "Text(modifier = Modifier.focusRequester(meaningFocusRequester).focusable())"
        assertEquals(1, countMatches(legacyFocus, revealFocusRequest), "正控：揭晓焦点命中")
        assertEquals(0, countMatches("LaunchedEffect(revealed) { meaningFocusRequester.requestFocus() }", revealFocusRequest), "负控：丢掉 runCatching 的写法不得算成同一条护栏")
        assertEquals(1, countMatches(legacyFocus, meaningFocusableModifier), "正控：focusable() 命中")
        assertEquals(0, countMatches("Modifier.focusRequester(other)", meaningFocusModifier), "负控：换焦点把手不得命中")
    }
}
