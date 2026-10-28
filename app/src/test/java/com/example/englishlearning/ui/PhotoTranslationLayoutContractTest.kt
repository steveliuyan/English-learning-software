package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 4 Task 4：拍照翻译重排的源码契约（spec §5「拍照翻译」行 + 计划对账 4）。
 *
 * 该屏原来是一根 760 行的长滚动：自写返回药片 + 五块常驻卡（已保存历史 / 离线模型 / 隐私说明 / 成本句 /
 * 引擎标签）平铺在一条流里，历史「删除」点了就执行。重做口径：
 *  - 页头换共享 [com.example.englishlearning.ui.components.AppScreenHeader]（旧「← 返回 AI 学」药片下线）；
 *  - **5 块收成历史一行 + 离线模型折叠**：历史与离线模型各变成一枚
 *    [com.example.englishlearning.ui.components.EntryRow] 折叠行，默认收起；隐私说明搬进模型卡展开体，
 *    成本句只贴在翻译按钮之后一次，引擎标签只留一处；
 *  - 历史删除不再点了就执行：屏内零对话框，点击只发 `onRequestDeleteSaved(`，确认框归宿主 `AppScreen`
 *    （判据另一半在 `AppScreenOverlayContainerContractTest` 的守卫站点与 `DangerInkPlacementContractTest`
 *    的危险墨窗口）；
 *  - 排印与墨色一律走六档 `AppType` 与组合期令牌：`Mint*` 读点只减不增、`MaterialTheme.typography` 清零、
 *    M3 `Card(` / `Button(` 族清零（容器走 `SoftCard`，按钮走共享 `PillButton`）。
 *
 * 读法是注释剥除后的源文本（[uiScreenSource]），按**字面 + 窗口**取位，不锚行号；
 * 数据通路与安全红线（图片不出设备、每次出站都确认、OCR ≠ 翻译）一字不动——本契约只管外观与确认路径。
 */
class PhotoTranslationLayoutContractTest {

    private val source: String by lazy { uiScreenSource("PhotoTranslationScreen.kt") }

    private val appSource: String by lazy { uiScreenSource("AppScreen.kt") }

    // ---------- ① 页头 ----------

    @Test
    fun `the screen wears the shared header and drops its own back pill`() {
        assertEquals(
            1,
            countMatches(source, headerCall),
            "页头必须恰一处共享 `AppScreenHeader(`（旧 `TextButton` + 「← 返回 AI 学」药片下线）",
        )
        assertTrue("onBack = onBack" in source, "页头必须把 `onBack` 接进去（退出整层那条链路）")
        assertEquals(
            0,
            countMatches(source, Regex("""←\s*返回""")),
            "屏内不得再出现「← 返回…」药片文案：返回位归 AppScreenHeader（contentDescription 固定「返回」）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""photo_translation_back""")),
            "旧返回位 tag `photo_translation_back` 必须随药片一起退役：它挂的就是那枚自写返回按钮",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bTextButton\(""")),
            "M3 `TextButton(` 必须整屏清零：返回位上移后，流内的复制 / 朗读 / 点词也一律走共享 `PillButton` 的文字档",
        )
    }

    // ---------- ② 两块收成两枚折叠行 ----------

    @Test
    fun `history and the offline model collapse into two entry rows that start folded`() {
        assertEquals(
            2,
            countMatches(source, Regex("""\bEntryRow\(""")),
            "常驻 5 块里有两块必须收成入口行（已保存历史 / 离线备用模型），全屏恰 2 枚 `EntryRow(`",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""expanded = historyExpanded""")),
            "历史那枚入口行必须声明成折叠件（`expanded = historyExpanded`）：否则 TalkBack 读不出展开/收起",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""expanded = modelExpanded""")),
            "离线模型那枚入口行必须声明成折叠件（`expanded = modelExpanded`）",
        )
        listOf("historyExpanded", "modelExpanded").forEach { fold ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("var $fold by rememberSaveable { mutableStateOf(false) }"))),
                "折叠态「$fold」必须**默认收起**（`var $fold by rememberSaveable { mutableStateOf(false) }` 恰一处）：默认展开等于没收",
            )
        }
        assertFoldedBehind(source, "SavedHistoryList(", "historyExpanded", "已保存历史")
        assertFoldedBehind(source, "OfflineModelCard(", "modelExpanded", "离线备用模型")
    }

    // ---------- ③ 常驻五块的归位 ----------

    @Test
    fun `the five always on blocks no longer sit flat in the scroll`() {
        // 引擎标签：一处，不是每块卡里各贴一枚。
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("photo_translation_engine_label"))),
            "`photo_translation_engine_label` 必须恰 1 处：引擎标签常驻一遍就够，不许随卡片复制",
        )
        // 成本句：只贴在翻译按钮之后一次（spec §5「费用句只讲一次」同族口径）。
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("photo_translation_cost_hint"))),
            "`photo_translation_cost_hint` 必须恰 1 处",
        )
        assertAnchoredBefore(source, "photo_translation_cost_hint", "photo_translation_translate_button", 900, "成本句必须贴在翻译按钮之后")
        // 隐私说明搬进模型卡展开体：只有展开模型才读得到那一段。
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("photo_translation_privacy_note"))),
            "`photo_translation_privacy_note` 必须恰 1 处",
        )
        assertAnchoredBefore(source, "photo_translation_privacy_note", "modelExpanded", 1200, "隐私说明必须落在 `if (modelExpanded)` 展开体内")
        // 历史卡整枚下线（它变成入口行 + 展开列表），M3 `Card(` 族与本屏分节卡一起清零。
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("photo_translation_history_card"))),
            "`photo_translation_history_card` 必须清零：历史那块不再是一张常驻卡",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "M3 `Card(` 必须整屏清零：容器层是三档共享件（`SoftCard` / `BareRow` / `EntryRow`）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""CardDefaults""")),
            "`CardDefaults` 必须整屏清零（随 `Card(` 一起下线）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bSectionCard\(""")),
            "旧的 `SectionCard(` 必须整屏清零",
        )
        assertTrue(
            countMatches(source, Regex("""\bSoftCard\(""")) >= 2,
            "识别问题 / 翻译结果 / 离线模型这些卡片块必须走共享 `SoftCard(`（实测 ${countMatches(source, Regex("""\bSoftCard\("""))} 处）",
        )
    }

    // ---------- ④ 历史删除改回调 ----------

    @Test
    fun `deleting a saved record only sends a request and the screen owns no dialog`() {
        assertEquals(
            0,
            countMatches(source, Regex("""onDeleteSaved\(""")),
            "屏内不得再直调 `onDeleteSaved(`：历史删除必须先经宿主确认框",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""onRequestDeleteSaved\(""")),
            "历史行的「删除」必须发 `onRequestDeleteSaved(`（恰一处）",
        )
        listOf("AlertDialog(", "ConfirmDestructiveDialog(").forEach { dialog ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(dialog))),
                "屏内零破坏性对话框（$dialog）：确认框归宿主 AppScreen 的 ConfirmDestructiveDialog",
            )
        }
        assertEquals(
            1,
            countMatches(source, Regex("""\bGlassDialog\(""")),
            "屏内只留一枚 `GlassDialog(`：出站确认框（`pendingConfirmationHost` 那一枚），别的浮层一律不进本屏",
        )
    }

    // ---------- ⑤ token / 排印闸 ----------

    @Test
    fun `the screen does not grow new mint reads or fall back to material type and buttons`() {
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
        assertEquals(
            0,
            countMatches(source, Regex("""\bFontWeight""")),
            "六档锁：不得用 `FontWeight` 临场改字重（要强调就换档）",
        )
        listOf("""\bButton\(""", """\bOutlinedButton\(""", """\bTextButton\(""").forEach { pattern ->
            assertEquals(
                0,
                countMatches(source, Regex(pattern)),
                "M3 裸按钮（$pattern）必须清零：按钮一律走共享 `PillButton`（含 `PillStyle.Text` 的文字档）",
            )
        }
        assertEquals(
            1,
            countMatches(source, Regex("""\bOutlinedTextField\(""")),
            "识别文本的可编辑框必须恰 1 枚（校对是这一屏的核心动作，不许被折叠走）",
        )
    }

    // ---------- ⑥ 可测面守恒 ----------

    @Test
    fun `every tag that connected and the guards hang on survives exactly once`() {
        listOf(
            "photo_translation_screen",
            "photo_translation_camera_button",
            "photo_translation_gallery_button",
            "photo_translation_permission_hint",
            "photo_translation_permission_settings",
            "photo_translation_camera_box",
            "photo_translation_camera_close",
            "photo_translation_busy",
            "photo_translation_ocr_unavailable",
            "photo_translation_ocr_failed",
            "photo_translation_engine_label",
            "photo_translation_text_editor",
            "photo_translation_low_confidence",
            "photo_translation_translate_button",
            "photo_translation_cost_hint",
            "photo_translation_not_configured",
            "photo_translation_failure",
            "photo_translation_result",
            "photo_translation_copy_button",
            "photo_translation_speak_button",
            "photo_translation_speak_feedback",
            "photo_translation_save_button",
            "photo_translation_save_issue",
            "photo_translation_privacy_note",
            "photo_translation_model_card",
            "photo_translation_model_install",
            "photo_translation_model_uninstall",
            "photo_translation_model_progress",
            "photo_translation_model_issue",
            "photo_translation_outbound_confirmation_text",
            "photo_translation_decline_outbound",
            "photo_translation_confirm_outbound",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "tag「$tag」必须恰出现一处：重排换的是容器与层级，不是可测面（改名或复制都算破锚）",
            )
        }
        // 历史行是插值 tag，两枚都得在原地（删除入口与行本体）。
        listOf(
            "photo_translation_history_\${record.recordId}",
            "photo_translation_history_delete_\${record.recordId}",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "历史行 tag「$tag」必须恰一处：折叠只是收起了整块，行与删除位都还在",
            )
        }
        // 点词条：`photo_translation_word_$index` 同族。
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"photo_translation_word_\$index\""))),
            "点词条 tag「photo_translation_word_\$index」必须保留",
        )
    }

    // ---------- ⑦ 宿主接线 ----------

    @Test
    fun `the host sends the delete request into its own confirmation dialog`() {
        assertTrue(source.isNotBlank(), "读不到 PhotoTranslationScreen.kt")
        assertTrue(appSource.isNotBlank(), "读不到 AppScreen.kt")
        assertEquals(
            0,
            countMatches(appSource, Regex("""onDeleteSaved =""")),
            "宿主不得再把历史删除直连 ViewModel：`onDeleteSaved =` 必须随重排一起下线",
        )
        assertEquals(
            1,
            countMatches(appSource, Regex("""onRequestDeleteSaved =""")),
            "宿主必须把 `onRequestDeleteSaved =` 接到本地 pending 态（恰一处）",
        )
        assertEquals(
            1,
            countMatches(appSource, Regex("""pendingPhotoTranslationDelete = recordId""")),
            "`onRequestDeleteSaved` 的落点必须是 `pendingPhotoTranslationDelete = recordId`（只置 pending，不执行）",
        )
        assertTrue(
            appSource.contains("pendingPhotoTranslationDelete?.let"),
            "拍照翻译的历史删除必须在宿主那枚 `?.let` 守卫下弹出确认框（可达性锚与别的站点同形）",
        )
        assertEquals(
            0,
            countMatches(appSource, Regex(Regex.escape("photo_translation_history_delete_confirm"))),
            "屏侧私有确认按钮 tag 一律不进宿主：确认框走组件固定 tag `confirm_destructive_*`",
        )
    }

    // ---------- 夹具 ----------

    private val headerCall = Regex("""\bAppScreenHeader\(""")

    /**
     * 「这枚块的调用被折叠态守着」：调用点**之前** [WINDOW] 字符内必须出现 `if (fold)`。
     *
     * 锚点取在**调用处**而不是行内 tag 处：`SavedHistoryList(` 的行渲染在它自己的私有函数体里，
     * 拿行内 tag 取窗会落到那个函数的内部（跟调用处无关）。守着调用点才是「默认不收 = 用户看不见」
     * 这条判据真正要问的事——整块不渲染，行自然不渲染。
     */
    private fun assertFoldedBehind(text: String, call: String, fold: String, label: String) {
        val at = text.indexOf(call)
        assertTrue(at > 0, "源码里找不到 `$call` 锚点：$label 那块被判据认不出来")
        val window = text.substring(maxOf(0, at - WINDOW), at)
        assertTrue(
            Regex("""if \($fold\)""").containsMatchIn(window),
            "$label 必须收进折叠行：`$call` 之前 $WINDOW 字符内找不到 `if ($fold)`" +
                "（窗口：${window.takeLast(160).replace(Regex("""\s+"""), " ")}）",
        )
    }

    /** 「[anchor] 之前 [width] 字符内出现 [earlier]」：钉的是相邻关系（成本句贴按钮、隐私句贴展开体）。 */
    private fun assertAnchoredBefore(text: String, anchor: String, earlier: String, width: Int, message: String) {
        val at = text.indexOf(anchor)
        assertTrue(at > 0, "源码里找不到 `$anchor` 锚点")
        val window = text.substring(maxOf(0, at - width), at)
        assertTrue(
            earlier in window,
            "$message：`$anchor` 之前 $width 字符内找不到 `$earlier`" +
                "（窗口：${window.takeLast(160).replace(Regex("""\s+"""), " ")}）",
        )
    }

    private companion object {
        /** 折叠 / 相邻取窗宽度：足够跨过「EntryRow 声明 + 说明行」，又不至于跨到上一块去。 */
        const val WINDOW = 900

        /**
         * `Mint*` 读点上限。现值 0：底色、墨色、域色全部走 `appColors()` / `appDomain(AppDomain.AiSpeech)`
         * 与 `PillButton`；留 2 格余量只为「将来真需要一枚组合期 getter」时可登记，而不是给回退开口子。
         */
        const val MINT_READ_CEILING = 2
    }
}
