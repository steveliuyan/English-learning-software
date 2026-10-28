package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 5 Task 1：语音合成重排的源码契约（spec §5「语音合成」行 + 计划对账 5）。
 *
 * 该屏旧形态是「静态 `MintBackground` 底 + 自写 `TextButton("返回")` + 布尔文字「开/关」的自动发音行 +
 * 点按循环的语速行 + 常驻玻璃试听卡」。重做口径（spec §5 原话「试听卡降为一行；开关统一 Switch；
 * 语速改 slider + 数值」）：
 *  - 页头换共享 `AppScreenHeader`，旧返回文字键下线；
 *  - 自动发音行改 `Switch`（`speech_auto_speak_state` 的「开/关」文字退役）；
 *  - 语速改 `Slider` + 数值（`onCycleSpeechRate` 点按循环退役）；
 *  - 试听区收成一行 `BareRow`，玻璃卡本体（`PreviewCard`）与它的签名**一字不动**
 *    ——`GlassPanelThemeInkTest` 直接调用它、`GlassPanelInkPairingContractTest` 冻结它的参数。
 *
 * 读法是注释剥除后的源文本（`uiScreenSource`），按符号与花括号配对取窗口，不锚行号。
 */
class SpeechSettingsLayoutContractTest {

    private val source: String by lazy { uiScreenSource("SpeechSettingsScreen.kt") }

    /** 屏内 15 枚保留锚：改动只许换承载控件，不许把锚弄丢。 */
    private val keptTags = listOf(
        "speech_settings_screen",
        "speech_auto_speak_toggle",
        "speech_rate_toggle",
        "speech_rate_value",
        "speech_toggle_candidates",
        "speech_add_mimo_preset",
        "speech_open_ai_profiles",
        "speech_message",
        "speech_preview_input",
        "speech_preview_button",
        "speech_preview_message",
    )

    // ---------- ① 页头 + 静态桥清零 ----------

    @Test
    fun `the screen wears the shared header and drops the static mint bridge`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bAppScreenHeader\(""")),
            "全文件 `AppScreenHeader(` 必须恰 1 处（这一屏只有一页页头）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bTextButton\(""")),
            "自写 `TextButton(` 必须清零：返回位归页头，候选区与试听区一律走 `PillButton` 三档",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography""")),
            "排印必须走六档 `AppType`，不得回退 `MaterialTheme.typography`",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bMint[A-Za-z0-9_]*""")),
            "`Mint*` 全局读点必须清零（含 import 行）：底色走 `appColors().background`，" +
                "强调墨走 `appDomain(AppDomain.AiSpeech).deep`",
        )
        val body = bodyOf(source, "fun SpeechSettingsScreen(")
        assertTrue(body.isNotEmpty(), "取不到 `SpeechSettingsScreen(` 本体，判据会空转")
        assertTrue("onBack = onBack" in body, "页头必须把 `onBack` 接进返回位")
    }

    // ---------- ② 自动发音行改 Switch ----------

    @Test
    fun `the auto speak row becomes a real switch labelled for talkback`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bSwitch\(""")),
            "自动发音行必须改成一枚 Material `Switch(`（恰 1 处）：spec §5「开关统一 Switch」，" +
                "布尔文字「开/关」既不可点也不播报状态",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("\"speech_auto_speak_state\""))),
            "`speech_auto_speak_state` 的「开/关」文字锚随文字退役：" +
                "开关状态交给 `Switch` 的语义（`assertIsOn`/`assertIsOff`），不再靠一枚 Text 表达",
        )
        val switchAt = source.indexOf("Switch(")
        assertTrue(switchAt > 0, "源码里找不到 `Switch(` 锚点")
        // 窗口取「`Switch(` 之后 400 字符」而不是「锚点之前」：全站开关的书写约定是
        // `Switch(...)` 在前、`modifier = Modifier.testTag(...).semantics { ... }` 在参数表尾部
        // （见 `ReminderSettingsScreen.kt` 的同款行、`AccessibilitySwitchLabelContractTest` 规则1）。
        val window = source.substring(switchAt, minOf(source.length, switchAt + 400))
        assertTrue(
            "speech_auto_speak_toggle" in window,
            "`Switch(` 的参数表里必须带 `speech_auto_speak_toggle` 锚（窗口：${window.trim()}）",
        )
        assertTrue(
            "onCheckedChange = onToggleAutoSpeakOnCardReady" in window,
            "开关必须直连 `onToggleAutoSpeakOnCardReady`：改开关仍然只走既有回调，不新增通路",
        )
        assertTrue(
            "contentDescription" in window,
            "开关修饰链必须带 `contentDescription`（`AccessibilitySwitchLabelContractTest` 的 80 字符窗口口径）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("contentDescription = \"学习时自动发音\""))),
            "开关的可及名必须逐字保留「学习时自动发音」（`AccessibilitySwitchLabelContractTest` 口径）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"speech_auto_speak_toggle\""))),
            "开关锚 `speech_auto_speak_toggle` 必须恰 1 处：整行再包一层 clickable 会让它变成第二个节点",
        )
    }

    // ---------- ③ 语速改滑杆 ----------

    @Test
    fun `the speech rate becomes a slider with a numeric readout`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bSlider\(""")),
            "语速必须改成 `Slider(`（恰 1 处）：spec §5「语速改 slider + 数值」",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""onCycleSpeechRate""")),
            "点按循环（`onCycleSpeechRate`）必须退役：滑杆是连续值，" +
                "同一行既滑又循环会让「当前语速」有两个真值来源",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"speech_rate_toggle\""))),
            "语速锚 `speech_rate_toggle` 必须恰 1 处（承载控件换成滑杆，锚不变）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"speech_rate_value\""))),
            "数值锚 `speech_rate_value` 必须恰 1 处",
        )
        val valueAt = source.indexOf("\"speech_rate_value\"")
        assertTrue(valueAt > 0, "源码里找不到 `speech_rate_value` 锚点")
        assertTrue(
            "}x\"" in source,
            "数值必须以「x」结尾（形如 `\"\${state.speechRate}x\"`）：spec §5 要的是「数值」，" +
                "裸浮点读不出这是倍速",
        )
    }

    // ---------- ④ 试听区降为一行 ----------

    @Test
    fun `the preview card collapses behind a single row`() {
        // 本文件里 `PreviewCard` 是私有 composable：1 处定义 + 只许 1 处调用（全部落在折叠体内）。
        assertEquals(
            1,
            countMatches(source, Regex("""private fun PreviewCard\(""")),
            "`PreviewCard` 的定义必须恰 1 处（玻璃卡本体保留）",
        )
        assertEquals(
            2,
            countMatches(source, Regex("""\bPreviewCard\(""")),
            "`PreviewCard(` 必须恰 2 处 = 1 定义 + 1 调用：屏内只留一个试听落点",
        )
        assertTrue(
            "previewExpanded" in source,
            "试听区必须由 `previewExpanded` 的折叠布尔驱动：spec §5「试听卡降为一行」，" +
                "常驻玻璃卡占掉半屏正是要改掉的那个毛病",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""\bBareRow\(""")),
            "折叠后的一行必须是 `BareRow(`（恰 1 处）：D 骨架靠裸行表达「同组」，不新造外壳",
        )
        val expandAt = source.indexOf("if (previewExpanded)")
        assertTrue(expandAt > 0, "源码里找不到 `if (previewExpanded)`：折叠壳没有落地")
        val callAt = source.indexOf("PreviewCard(")
        assertTrue(
            callAt > expandAt,
            "调用点必须落在 `if (previewExpanded)` 之后（$callAt vs $expandAt）：" +
                "折叠时不该渲染玻璃卡",
        )
        listOf("speech_preview_input", "speech_preview_button").forEach { tag ->
            val at = source.indexOf("\"$tag\"")
            assertTrue(at > 0, "源码里找不到 `$tag` 锚点")
            assertTrue(
                at > expandAt,
                "「$tag」必须落在 `if (previewExpanded)` 体内（$at vs $expandAt）：" +
                    "折叠时输入框与试听键不该占屏",
            )
        }
    }

    // ---------- ⑤ 玻璃卡本体冻结 ----------

    @Test
    fun `the glass preview card keeps its signature and its glass fill`() {
        val cardBody = bodyOf(source, "private fun PreviewCard(")
        assertTrue(cardBody.isNotEmpty(), "取不到 `PreviewCard(` 本体，判据会空转")
        assertTrue(
            "state: SpeechSettingsUiState" in source && "onPreview: (String) -> Unit" in source,
            "`PreviewCard` 的参数必须保持 `state: SpeechSettingsUiState` / `onPreview: (String) -> Unit`：" +
                "`GlassPanelInkPairingContractTest` 冻结这对参数，`GlassPanelThemeInkTest` 直接调用它",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""appColors\(\)\.surfaceGlass""")),
            "玻璃底 `appColors().surfaceGlass` 必须恰 1 处：试听卡的玻璃合成色由 " +
                "`DarkTokenConsumptionRatchetTest` 冻结（`SpeechSettingsScreen.kt to 2` / 合成底 `to 1`）",
        )
    }

    // ---------- ⑥ 锚清单 ----------

    @Test
    fun `every retained anchor still has exactly one home`() {
        keptTags.forEach { tag ->
            assertTrue(
                countMatches(source, Regex(Regex.escape("\"$tag\""))) >= 1,
                "锚「$tag」丢了：重排只许换承载控件，不许把既有锚弄丢（connected 用例全靠它们定位）",
            )
        }
        listOf(
            "speech_profile_",
            "speech_bound_profile_",
            "speech_engine_",
            "speech_current_",
            "speech_engine_status_",
        ).forEach { prefix ->
            assertTrue(
                countMatches(source, Regex(Regex.escape("\"$prefix"))) >= 1,
                "锚前缀「$prefix」丢了：引擎/候选行的动态锚必须保留",
            )
        }
    }

    // ---------- ⑦ token 与主次 ----------

    @Test
    fun `the screen reads six step type and keeps one primary pill`() {
        val typeReads = countMatches(source, Regex("""AppType\."""))
        assertTrue(typeReads >= 18, "六档排印至少 18 处读点（现 $typeReads）：标题/说明/数值/标签各有所属")
        val pills = countMatches(source, Regex("""\bPillButton\("""))
        assertTrue(pills >= 2, "药片至少 2 处（现 $pills）：试听主键 + 候选次键，主次不能合并成一枚")
        val domainReads = countMatches(source, Regex("""appDomain\(AppDomain\.AiSpeech\)"""))
        assertTrue(domainReads >= 3, "域色至少 3 处读点（现 $domainReads）：本屏属 AiSpeech 域")
        assertEquals(
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "M3 `Card(` 必须为 0：分组走 `SoftCard` / `BareRow` 三档容器",
        )
    }

    // ---------- 夹具 ----------

    /** 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。 */
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
