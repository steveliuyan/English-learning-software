package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 4 Task 3：AI 服务重排的源码契约（spec §5「AI 服务」行 + 计划对账 3/4）。
 *
 * 该屏原来是一根长表单：5 枚玻璃分节卡 + 保存按钮跟着滚动 + 危险动作点了就执行。重做口径：
 *  - 两个页头都换成共享 [com.example.englishlearning.ui.components.AppScreenHeader]（旧「← 返回…」
 *    药片下线）；
 *  - 13 个控件按「服务与模型 / 语音 / 高级与密钥」归 3 枚 [com.example.englishlearning.ui.components.SoftCard]，
 *    **字段顺序、tag、校验文案一字不动**（数据与安全零改动：这一屏只搬外观）；
 *  - 批 3 又补了第 4 枚 `SoftCard`「未接入的选项」（裁决 B：原型有四段超出 V1 请求白名单的控件，
 *    画出来、禁用、标「暂未接入」）。它只装禁用行、不持有任何可写草稿的控件，
 *    所以上面「13 个控件归 3 组」的归属判据**一字未减**；下面第 ② 条把容器数从 3 改成 4 是期望值变化
 *    （新增了一枚有据可依的容器），不是放宽；
 *  - 保存固定吸底，流内不再有第二枚保存入口；
 *  - 「清除密钥 / 删除配置」不再点了就执行：屏内零对话框，点击只发请求，确认框归宿主 `AppScreen`
 *    （判据的另一半在 `AppScreenOverlayContainerContractTest` 的守卫站点与 `DangerInkPlacementContractTest`
 *    的危险墨色窗口）；
 *  - 列表行从卡片改裸行。
 *
 * 读法是注释剥除后的源文本（[uiScreenSource]），按**符号 + 花括号配对**取窗口，不锚行号。
 */
class AiProfileLayoutContractTest {

    private val source: String by lazy { uiScreenSource("AiProfileSettingsScreen.kt") }

    private val editorBody: String by lazy { bodyOf(source, "private fun AiProfileEditor(") }

    // ---------- ① 两个页头 ----------

    @Test
    fun `both pages wear the shared header and the old back pills are gone`() {
        val listBody = bodyOf(source, "private fun AiProfileList(")
        assertTrue(listBody.isNotEmpty(), "取不到 `AiProfileList(` 本体，判据会空转")
        assertTrue(editorBody.isNotEmpty(), "取不到 `AiProfileEditor(` 本体，判据会空转")

        assertEquals(
            1,
            countMatches(listBody, headerCall),
            "列表页页头必须恰一处共享 AppScreenHeader（旧的 `PillButton(\"← 返回上一层\")` 下线）",
        )
        assertEquals(
            1,
            countMatches(editorBody, headerCall),
            "编辑页页头必须恰一处共享 AppScreenHeader（旧的 `PillButton(\"← 返回 AI 服务\")` 下线）",
        )
        assertEquals(
            2,
            countMatches(source, headerCall),
            "全文件 `AppScreenHeader(` 应恰 2 处（列表 + 编辑各一）",
        )

        // 旧返回药片：`← 返回…` 这族字面量整屏清零（两个页头都改成返回位图标）。
        assertEquals(
            0,
            countMatches(source, Regex("""←\s*返回""")),
            "屏内不得再出现「← 返回…」药片文案：返回位归 AppScreenHeader（contentDescription 固定「返回」）",
        )
        assertTrue(
            "onBack = onBack" in listBody,
            "列表页页头必须把 `onBack` 接进去（退出整层的那条链路）",
        )
        assertTrue(
            "onBack = onClose" in editorBody,
            "编辑页页头必须把 `onClose` 接进去（关编辑页那条链路）",
        )
    }

    // ---------- ② 分 3 组（+1 枚禁用组） ----------

    /**
     * 容器数从 3 改 4 是**期望值变化，不是放宽**：批 3 按裁决 B
     * （`docs/decisions/2026-10-29-ui-prototype-token-alignment.md` §已知冲突）把原型里超出 V1
     * 请求白名单的四段控件补回来，落成第 4 枚「未接入的选项」软卡。三条同时成立才算合规：
     * ① 那枚卡**只装禁用行**（不持有 `EditorField` / 不写草稿），所以 13 个真控件的分组归属不变；
     * ② 它排在最后，groups[0..2] 的语义与顺序一字不动（下面的组归属断言原样承重）；
     * ③ 它自己必须真的禁用（`enabled = false` + 尾值「暂未接入」，由 `Batch3AiScreensContractTest` 钉）。
     */
    @Test
    fun `the editor groups thirteen controls into three soft cards plus the unsupported fourth`() {
        assertEquals(
            4,
            countMatches(editorBody, Regex("""\bSoftCard\(""")),
            "编辑器必须是 4 枚 `SoftCard`：3 枚装 13 个真控件（服务与模型 / 语音 / 高级与密钥）" +
                "+ 1 枚装未接入选项（批 3 裁决 B 补）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bSectionCard\(""")),
            "旧的 `SectionCard(` 必须整屏清零：它已由共享 `SoftCard` 接管（玻璃分节卡下线）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "本屏不得回退 M3 `Card(`：三档容器就是替代它的那一层",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bStrongCard\(""")),
            "AI 服务屏没有强档卡的位置：一屏至多一枚强卡，这一屏一枚都不用",
        )
    }

    // ---------- ③ 13 个控件守恒 + 组归属 ----------

    @Test
    fun `the thirteen controls keep their tags and land in the agreed group`() {
        val groups = softCardBodies(editorBody)
        assertEquals(4, groups.size, "取不到 4 枚 `SoftCard` 的花括号体，组归属判据会空转")

        val serviceAndModel = listOf(
            "ai_profile_name",
            "ai_profile_endpoint",
            "ai_profile_model",
            "ai_profile_capability_text",
            "ai_profile_capability_vision",
            "ai_profile_capability_speech",
            "ai_profile_capability_image",
        )
        val voice = listOf("ai_profile_voice")
        val advancedAndKey = listOf(
            "ai_profile_temperature",
            "ai_profile_top_p",
            "ai_profile_max_tokens",
            "ai_profile_timeout_seconds",
            "ai_profile_key_input",
        )

        serviceAndModel.forEach { tag ->
            assertTrue(tag in groups[0], "「$tag」必须留在第一组（服务与模型 + 四枚能力开关），tag 一字不动")
        }
        voice.forEach { tag ->
            assertTrue(tag in groups[1], "「$tag」必须单独成第二组（语音角色只在 Speech 能力开启时出现）")
        }
        advancedAndKey.forEach { tag ->
            assertTrue(tag in groups[2], "「$tag」必须留在第三组（高级与密钥），tag 一字不动")
        }
        // 第 4 枚是新增的禁用组：它只装四枚「未接入」行，13 个可写控件一个都不许混进来
        // （混进来意味着「把真控件挪进禁用卡」——用户就再也调不了它们）。
        listOf(
            "ai_profile_unsupported_stream",
            "ai_profile_unsupported_voice",
            "ai_profile_unsupported_image",
            "ai_profile_unsupported_connection_test",
        ).forEach { tag ->
            assertTrue(tag in groups[3], "「$tag」必须落在第 4 枚「未接入的选项」卡里")
        }
        (serviceAndModel + voice + advancedAndKey).forEach { tag ->
            assertTrue(
                tag !in groups[3],
                "「$tag」不许混进禁用卡：13 个真控件的分组归属是它的承重面，挪进去等于让用户调不了",
            )
        }

        // 13 个 tag 全文件逐一在册，且恰一处（搬家的手段是换容器，不是改名或复制）。
        (serviceAndModel + voice + advancedAndKey).forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "控件 tag「$tag」必须恰出现一处：重排只搬容器，不许改名、也不许复制出第二个入口",
            )
        }

        // 第二组挂在能力开关上：`ai_profile_voice` 所在的 SoftCard 必须被 `AiCapability.Speech in` 守着。
        assertTrue(
            "AiCapability.Speech in" in editorBody,
            "语音组必须仍由「Speech 能力开启」这一条件守着，否则没有语音能力的服务也会看到音色选择",
        )
        val speechGuard = editorBody.indexOf("AiCapability.Speech in")
        val voiceCard = editorBody.indexOf("SoftCard(", editorBody.indexOf("AiCapability.Speech in"))
        assertTrue(
            speechGuard >= 0 && voiceCard > speechGuard,
            "语音组的 `SoftCard(` 必须排在 `AiCapability.Speech in` 条件之后（条件守卫整枚软卡）",
        )
    }

    // ---------- ④ 保存吸底 ----------

    @Test
    fun `the save button is pinned to a bottom bar and appears exactly once`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"ai_profile_save\""))),
            "全文件 `ai_profile_save` 必须恰 1 处：流内不许再出现第二枚保存入口",
        )
        val at = source.indexOf("\"ai_profile_save\"")
        assertTrue(at > 0, "源码里找不到 `ai_profile_save` 锚点")
        val windowStart = maxOf(0, at - SAVE_BAR_WINDOW)
        val window = source.substring(windowStart, at)
        assertTrue(
            bottomAlign.containsMatchIn(window),
            "保存按钮必须坐进吸底容器：`ai_profile_save` 之前 $SAVE_BAR_WINDOW 字符内找不到 `align(Alignment.BottomCenter)`" +
                "（窗口：${window.takeLast(160).replace(Regex("""\s+"""), " ")}）",
        )
        assertTrue(
            "Surface(" in window,
            "吸底条必须是 `Surface(` 容器（内容色 / 纸面与页底分开），不许裸 Row 直接压在页底",
        )
        assertTrue(
            "ai_profile_save_bar" in window,
            "吸底条要带 `ai_profile_save_bar` tag：它是「保存固定底部」这条例的可点面锚点",
        )
    }

    // ---------- ⑤ 危险动作改回调 ----------

    @Test
    fun `destructive taps only send requests and the screen owns no dialog`() {
        assertEquals(
            0,
            countMatches(source, Regex("""onDeleteProfile\(""")),
            "屏内不得再直调 `onDeleteProfile(`：危险动作必须先经宿主确认框",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""onDeleteKey\(""")),
            "屏内不得再直调 `onDeleteKey(`：危险动作必须先经宿主确认框",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""onRequestDeleteProfile\(""")),
            "「删除这套配置」的点击处必须发 `onRequestDeleteProfile(`（恰一处）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""onRequestDeleteKey\(""")),
            "「清除已保存的密钥」的点击处必须发 `onRequestDeleteKey(`（恰一处）",
        )
        listOf("AlertDialog(", "GlassDialog(", "ConfirmDestructiveDialog(").forEach { dialog ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(dialog))),
                "屏内零对话框（$dialog）：确认框归宿主 AppScreen 的 ConfirmDestructiveDialog，屏侧只发请求",
            )
        }
    }

    // ---------- ⑥ token 闸不倒退 ----------

    @Test
    fun `the screen does not grow new mint reads or fall back to material typography`() {
        val mintReads = countMatches(source, Regex("""\bMint[A-Za-z0-9_]*"""))
        assertTrue(
            mintReads <= MINT_READ_CEILING,
            "`Mint*` 读点只许减少：现值 $mintReads 已超过阈值 $MINT_READ_CEILING（新增读点一律走 `appColors()`/`appDomain()`）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography""")),
            "排印必须走六档 `AppType`，不得回退 `MaterialTheme.typography`",
        )
    }

    // ---------- ⑦ 列表行改裸行 ----------

    @Test
    fun `the list rows become bare rows and every list tag survives`() {
        val rowBody = bodyOf(source, "private fun AiProfileRow(")
        assertTrue(rowBody.isNotEmpty(), "取不到 `AiProfileRow(` 本体，判据会空转")
        assertTrue(
            "BareRow(" in rowBody,
            "列表行必须改裸行（`BareRow(`）：卡片列表是「卡片汤」，一屏最多一枚强卡/若干软卡",
        )
        assertTrue(
            "onClick = { onEdit(" in rowBody,
            "裸行必须自己承载「点行去编辑」的点击面（旧 Surface 的 clickable 随卡片一起下线）",
        )
        listOf(
            "ai_profiles_screen",
            "ai_profiles_add",
            "ai_profiles_empty",
            "ai_profiles_unavailable",
            "ai_profiles_message",
            "ai_profile_item_",
            "ai_profile_key_state_",
            "ai_profile_default_",
            "ai_profile_image_default_",
            "ai_profile_set_default_",
            "ai_profile_set_image_default_",
        ).forEach { tag ->
            assertTrue(tag in source, "列表页 tag「$tag」必须保留：重排换的是容器，不是可测面")
        }
    }

    // ---------- 补充：原先挂在玻璃分节卡上的取墨决定必须继续钉着 ----------

    /**
     * 这四处取墨原本是 `GlassPanelInkPairingContractTest` 里「AI 服务卡 SectionCard」那枚
     * `MigratedPanel` 的 `pinnedInkLiterals`。本轮把玻璃分节卡换成不透明的 `SoftCard`，
     * 那枚面板条目随「容器已迁走」整条摘下（它钉的是**玻璃底**与前景的配对），
     * 但**取墨决定本身仍然有效**，所以改由本契约继续逐字钉住，不许在摘条目的同时把判据一起丢掉。
     */
    @Test
    fun `the ink decisions that used to ride on the glass section card stay pinned`() {
        listOf(
            "color = if (!LocalIsDark.current) appColors().onMint else appDomain(AppDomain.AiSpeech).deep",
            "val background = if (selected) appDomain(AppDomain.AiSpeech).base.copy(alpha = 0.12f) else appColors().surface",
            "val border = if (selected) appDomain(AppDomain.AiSpeech).base else appColors().outlineSoft",
        ).forEach { literal ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape(literal))),
                "取墨决定「$literal」必须逐字保留（原玻璃面板条目已摘，这条是它唯一的守卫）",
            )
        }
    }

    // ---------- 夹具 ----------

    private val headerCall = Regex("""\bAppScreenHeader\(""")

    private val bottomAlign = Regex("""align\(\s*Alignment\.BottomCenter\s*\)""")

    /** 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。 */
    private fun bodyOf(text: String, signature: String): String {
        val start = text.indexOf(signature)
        if (start < 0) return ""
        val open = text.indexOf('{', start)
        if (open < 0) return ""
        val close = braceEnd(text, open)
        return if (close < 0) "" else text.substring(open + 1, close)
    }

    /** 按出现顺序取每枚 `SoftCard(` 的花括号体（尾部 lambda 体），取不到的跳过。 */
    private fun softCardBodies(text: String): List<String> {
        val out = mutableListOf<String>()
        var index = 0
        val regex = Regex("""\bSoftCard\(""")
        while (true) {
            val hit = regex.find(text, index) ?: break
            val open = text.indexOf('{', hit.range.last)
            val close = if (open < 0) -1 else braceEnd(text, open)
            if (close > 0) out.add(text.substring(open + 1, close))
            index = if (close > 0) close else hit.range.last + 1
        }
        return out
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

    private companion object {
        /** 保存按钮之前取多宽的窗口找吸底容器：足够跨过「Surface + 内层 Row + 可选删除按钮」。 */
        const val SAVE_BAR_WINDOW = 800

        /**
         * `Mint*` 读点上限。现值 3 = `MintBackground` 的 import + 列表页 / 编辑页各一次页面底色；
         * 计划文档里写的「现值 4」把 KDoc 里的一次提及也算了进去（判据跑在剥注码后的源码上）。
         */
        const val MINT_READ_CEILING = 3
    }
}
