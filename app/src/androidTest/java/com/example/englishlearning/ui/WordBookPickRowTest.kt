package com.example.englishlearning.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.learning.WordBookProgress
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 选词书行（原型 `.pick.pick-radio`，`app.js:587-606`）的 connected 判据。
 *
 * 前身是 `WordBookTileTest`：那版钉的是批 5 的两列色块。批 1 Task 13-A 按用户裁决把版面改回原型
 * 的通栏单选行（色块与滑删一起退役），本文件接住它原有的四条护栏，并补上「行说的是原型那一套」的判据：
 *
 *  1. 行**永远不画红色错误底**（原红底护栏的等价判据：行自己的捕获图里不许有 error 色像素）；
 *  2. 「删除词书」自定义动作必须挂在**被 TalkBack 聚焦的那个可点击节点**上（终审 A 的 I-A2：手势、
 *     `contentDescription`、`stateDescription`、`customActions` 不得分家）；
 *  3. 删除入口是**长按**，且长按只「请求删除」（弹确认框），点按只「选中」——两条手势不许串线；
 *  4. 内置册（`canDelete` 为 false）连长按入口都不存在：结构上没有被删的可能；
 *  5. 行是**单选**（`Role.RadioButton` + `Selected` + 已选/未选播报），选中档的圆标记真填 `brand-deep`；
 *  6. 副行逐字是原型那句「N 词 · 已学 M / 未开始」，进度四档各说各的，等级行不再出现在屏上；
 *  7. 「使用中」只挂在**已生效的活动册**上，草稿选中位不许把它搬走。
 *
 * 一个 `@Test` 只 `setContent` 一次（本仓真机怪癖：同一测试内两次 setContent 必崩），所以第 5 与第 6
 * 条按状态分成了不同用例，每条自己喂自己要的那一态。
 */
@RunWith(AndroidJUnit4::class)
class WordBookPickRowTest {
    @get:Rule val composeRule = createComposeRule()

    private val importedBook = WordBook("imported-book", "导入的词书", "自定义", 4308, "v1", "user-provided-xlsx")
    private val bundledBook = WordBook("cet4", "大学英语四级", "CET-4", 4634, "v1", "ngsl-nawl-1.2")

    @Composable
    private fun picker(
        wordBooks: List<WordBook>,
        selectedId: String? = null,
        activeBookId: String? = null,
        progressByBook: Map<String, WordBookProgress> = emptyMap(),
        progressLoading: Boolean = false,
        canDelete: (String) -> Boolean = { true },
        onSelect: (String) -> Unit = {},
        onRequestDelete: (WordBook) -> Unit = {},
    ) {
        MintGlassTheme(themeMode = ThemeMode.Light) {
            WordBookPicker(
                wordBooks = wordBooks,
                loading = false,
                selectedId = selectedId,
                activeBookId = activeBookId,
                progressByBook = progressByBook,
                progressLoading = progressLoading,
                canDelete = canDelete,
                onSelect = onSelect,
                onRequestDelete = onRequestDelete,
            )
        }
    }

    @Test
    fun the_row_never_paints_the_error_red_backdrop() {
        var errorColor = 0
        composeRule.setContent {
            errorColor = MaterialTheme.colorScheme.error.toArgb()
            picker(wordBooks = listOf(importedBook))
        }
        val bitmap = composeRule.onNodeWithTag("wordbook_pick_imported-book")
            .captureToImage().asAndroidBitmap()

        var backdropPixels = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (isClose(bitmap.getPixel(x, y), errorColor)) backdropPixels++
            }
        }
        // 滑删时代那层红底是滑动容器**始终**铺在内容之下的，卡片一缩就露出一圈红。
        // 现在没有滑动容器，行自己也不许把 error 色当装饰：允许极少数抗锯齿混色（真机实测 0）。
        assertTrue(
            "词书行露出了 error 色像素：$backdropPixels 个近似 error 色像素（上限 64）——" +
                "红色是删除/危险专用档，不能落在常驻行上",
            backdropPixels <= 64,
        )
    }

    /**
     * I-A2（终审 A §2）：「删除词书」自定义动作必须挂在**被 TalkBack 聚焦的那个可点击节点**上。
     *
     * 长按是视觉手势，读屏用户念不到也做不出来——动作与长按必须走同一个 `onRequestDelete`
     * （只弹确认框，两步确认红线不变）。动作声明若挪到外层不可点击节点，真实无障碍树里就没有
     * 可聚焦目标，本条按「同节点」钉死；顺带钉同一个节点上的 `contentDescription` 与
     * `stateDescription` 都在——换手势（`selectable` → `combinedClickable`）最容易把这两样掉在地上。
     */
    @Test
    fun deleteWordBookCustomActionIsRegisteredOnTheClickableNode() {
        var deleted = 0
        composeRule.setContent {
            picker(wordBooks = listOf(importedBook), onRequestDelete = { deleted++ })
        }

        val nodes = composeRule.onAllNodes(clickableWithDeleteAction()).fetchSemanticsNodes()
        assertEquals(
            "「删除词书」自定义动作必须与 click 动作挂在同一个可聚焦节点上，命中节点数=${nodes.size}",
            1,
            nodes.size,
        )

        val node = nodes.single()
        val action = node.config.getOrNull(SemanticsActions.CustomActions)
            ?.single { it.label == "删除词书" }
            ?: error("可点击节点上没有「删除词书」自定义动作")
        assertEquals(true, action.action())
        assertEquals("自定义动作必须走与长按同一个 onRequestDelete（只请求、不直接删）", 1, deleted)
        assertNotNull(
            "同一节点上还登记着长按本身（读屏动作是第二条路，不是替代路）",
            node.config.getOrNull(SemanticsActions.OnLongClick),
        )
        assertEquals(
            "手势、`contentDescription`、`stateDescription` 三样必须在同一个语义节点上（I-A2）",
            listOf("选择词书 导入的词书"),
            node.config.getOrNull(SemanticsProperties.ContentDescription),
        )
        assertEquals("未选", node.config.getOrNull(SemanticsProperties.StateDescription))
    }

    /**
     * 两条手势不许串线：点按只选中，长按只请求删除。
     *
     * 变异证据：把 `combinedClickable` 的 `onLongClick` 换成 `onClick` 的别名（或把长按接到删除之外的地方），
     * 长按后的 `requested` 归 0，本条立刻红。
     */
    @Test
    fun tap_selects_while_long_press_only_asks_for_confirmation() {
        var selected = 0
        var requested = 0
        composeRule.setContent {
            picker(
                wordBooks = listOf(importedBook),
                onSelect = { selected++ },
                onRequestDelete = { requested++ },
            )
        }

        val row = composeRule.onNodeWithTag("wordbook_pick_imported-book")
        row.performClick()
        assertEquals("点按必须只触发选中", 1, selected)
        assertEquals("点按不得触发删除请求", 0, requested)

        row.performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals("长按只请求删除（弹确认框），不得跳过确认直接删", 1, requested)
        assertEquals("长按不得同时把册选中", 1, selected)
    }

    /**
     * 内置册（`canDelete` 为 false）：长按入口与「删除词书」动作**都不存在**，从结构上保证没人能删它。
     */
    @Test
    fun the_bundled_book_has_no_long_press_delete_entry() {
        composeRule.setContent {
            picker(wordBooks = listOf(bundledBook), canDelete = { false })
        }
        val node = composeRule.onNodeWithTag("wordbook_pick_cet4").fetchSemanticsNode()
        assertNull(
            "内置册不得登记长按手势：结构上就没有被删的可能，而不是靠运行时拦一下",
            node.config.getOrNull(SemanticsActions.OnLongClick),
        )
        assertNull(
            "内置册不得有「删除词书」自定义动作",
            node.config.getOrNull(SemanticsActions.CustomActions),
        )
        // 选中态仍在语义里（换手势不许把 F5-03 的选中播报一起撕掉）。
        assertEquals(
            "词书行的选中态必须进 stateDescription",
            "未选",
            node.config.getOrNull(SemanticsProperties.StateDescription),
        )
    }

    /**
     * 行是**单选**，不是三个按钮：`Role.RadioButton` + `Selected` + 已选/未选播报 + 点击动作都在，
     * 选中档的圆标记真填 `brand-deep`（原型 `[aria-checked='true'] .pick-mark`），未选档不填。
     *
     * 变异证据：`radio = true` 去掉 → Role 变 Checkbox；`selected = checked` 不补 → `Selected` 缺失；
     * 标记底色不随 checked 走 → 两档的 brand-deep 像素数互换，本条当场红。
     *
     * 像素取样落在**整行**上：行内除标记外没有任何 `brand-deep` 用途（标题 `text1`、副行 `text3`），
     * 所以「选中行的 brand-deep 像素远多于未选行」就是标记填色本身在说话，不靠取坐标点。
     */
    @Test
    fun the_rows_expose_their_selected_state_to_talkback() {
        composeRule.setContent {
            picker(
                wordBooks = listOf(bundledBook, importedBook),
                selectedId = bundledBook.id,
                activeBookId = importedBook.id,
                progressByBook = mapOf(bundledBook.id to WordBookProgress(learned = 0, total = bundledBook.totalWords)),
            )
        }
        val chosen = composeRule.onNodeWithTag("wordbook_pick_${bundledBook.id}")
        val other = composeRule.onNodeWithTag("wordbook_pick_${importedBook.id}")

        chosen.assertIsSelected()
        other.assertIsNotSelected()
        chosen.assertHasClickAction()
        other.assertHasClickAction()
        for (row in listOf(chosen to "已选", other to "未选")) {
            val node = row.first.fetchSemanticsNode()
            assertEquals(
                "选词书是单选组，角色必须是 RadioButton（${row.second} 那一行）",
                Role.RadioButton,
                node.config.getOrNull(SemanticsProperties.Role),
            )
            assertEquals(
                "选中态要用 stateDescription 念出来（单选没有「勾选」这个词）",
                row.second,
                node.config.getOrNull(SemanticsProperties.StateDescription),
            )
        }

        val brandDeep = AppColorLight.brandDeep.toArgb() and 0xFFFFFF
        val chosenPixels = countClose(chosen.captureToImage().asAndroidBitmap(), brandDeep)
        val otherPixels = countClose(other.captureToImage().asAndroidBitmap(), brandDeep)
        assertTrue(
            "选中行的圆标记必须填 brand-deep（实测只有 $chosenPixels 个像素，下限 $MIN_FILLED_MARK_PIXELS）",
            chosenPixels >= MIN_FILLED_MARK_PIXELS,
        )
        assertTrue(
            "未选行不得出现 brand-deep 标记（实测 $otherPixels 个像素，上限 $MAX_EMPTY_MARK_PIXELS）",
            otherPixels <= MAX_EMPTY_MARK_PIXELS,
        )
    }

    /**
     * 副行逐字是原型 `app.js:595` 那一句：`b.words + ' 词' + ' · 已学 ' + b.learnt`，
     * 词量取**册元数据**而不是进度里的数（`4308 词 · 已学 1284`）。
     *
     * 同时钉「等级行撤下」：原型 `.pick` 只有 title + sub + value 三档，没有第四行，
     * 所以 `CET-4` / `自定义` 这些 level 字样不许出现在屏上（等级在词书详情屏与导入流程里念得到）。
     */
    @Test
    fun the_row_subtitle_is_the_prototype_words_and_learned_sentence() {
        composeRule.setContent {
            picker(
                wordBooks = listOf(importedBook, bundledBook),
                activeBookId = importedBook.id,
                progressByBook = mapOf(
                    importedBook.id to WordBookProgress(learned = 1284, total = importedBook.totalWords),
                    bundledBook.id to WordBookProgress(learned = 0, total = bundledBook.totalWords),
                ),
            )
        }
        composeRule.onNodeWithText("4308 词 · 已学 1284").assertExists()
        composeRule.onNodeWithText("4634 词 · 未开始").assertExists()
        // 「共」是批 5 色块自己加的修饰，原型没有；随色块一起退役。
        composeRule.onNodeWithText("共 4308 词").assertDoesNotExist()
        composeRule.onNodeWithText("自定义").assertDoesNotExist()
        composeRule.onNodeWithText("CET-4").assertDoesNotExist()
    }

    /**
     * 进度还在算时**分开说**，不许合并成「已学 0」（本地优先：降级要如实呈现）。
     *
     * 一次 `setContent` 喂三本册，靠各自的册数区分：`learned = 0` 是「未开始」（进度到位了，只是还没学）、
     * 进度还没轮到的是「进度计算中」。「暂时读不到进度」这一档要求 `progressLoading = false`，
     * 与本用例的状态互斥，另由 [the_progress_labels_switch_when_loading_is_over] 承重
     * （本仓一测试两次 `setContent` 必崩，不在这儿并排演）。
     *
     * 变异证据：把 `progressLoading` 那一条挪到 `progress != null` 之前 → 「进度计算中」盖掉已算出来的
     * 「未开始」，本条第一句就红；把「未开始」写成「已学 0」→ 第二句红。
     */
    @Test
    fun the_progress_states_say_which_one_is_which() {
        val stillLoading = WordBook("wb-loading", "还在算的那本", "L1", 111, "v1", "user-provided-xlsx")
        val notYetReached = WordBook("wb-unreadable", "还没轮到的那本", "L1", 222, "v1", "user-provided-xlsx")
        val notStarted = WordBook("wb-not-started", "没开始的那本", "L1", 333, "v1", "user-provided-xlsx")
        composeRule.setContent {
            picker(
                wordBooks = listOf(notStarted, stillLoading, notYetReached),
                progressByBook = mapOf(notStarted.id to WordBookProgress(learned = 0, total = notStarted.totalWords)),
                progressLoading = true,
            )
        }
        composeRule.onNodeWithText("333 词 · 未开始").assertExists()
        // 还没算出来的册念「进度计算中」——这一档盖过「暂时读不到进度」：算不完 ≠ 读不到。
        composeRule.onNodeWithText("111 词 · 进度计算中").assertExists()
        composeRule.onNodeWithText("222 词 · 进度计算中").assertExists()
        composeRule.onNodeWithText("111 词 · 暂时读不到进度").assertDoesNotExist()
        composeRule.onNodeWithText("111 词 · 已学 0").assertDoesNotExist()
        composeRule.onNodeWithText("333 词 · 暂时读不到进度").assertDoesNotExist()
    }

    /** 「暂时读不到进度」档：`progressLoading = false` 且这一本不在进度表里。 */
    @Test
    fun the_progress_labels_switch_when_loading_is_over() {
        val unreadable = WordBook("wb-unreadable", "读不到的那本", "L1", 222, "v1", "user-provided-xlsx")
        composeRule.setContent {
            picker(wordBooks = listOf(unreadable), progressLoading = false)
        }
        composeRule.onNodeWithText("222 词 · 暂时读不到进度").assertExists()
        composeRule.onNodeWithText("222 词 · 已学 0").assertDoesNotExist()
        composeRule.onNodeWithText("222 词 · 进度计算中").assertDoesNotExist()
    }

    /**
     * 「使用中」（原型 `.row-value`）只挂在**已生效的活动册**上，且整屏只有一处。
     *
     * 这一条钉的是绑法：宿主给的是 `state.savedWordBookId`，不是屏上的草稿选中位。用例故意让
     * 「草稿选中第二本、活动册是第一本」，谁把 `active` 换回 `selected`（或改绑进度），标签就搬到
     * 第二行、命中行边界断言立刻不成立。
     */
    @Test
    fun the_in_use_label_stays_on_the_active_book() {
        composeRule.setContent {
            picker(
                wordBooks = listOf(importedBook, bundledBook),
                selectedId = bundledBook.id,
                activeBookId = importedBook.id,
                progressByBook = mapOf(bundledBook.id to WordBookProgress(learned = 999, total = bundledBook.totalWords)),
            )
        }
        val labels = composeRule.onAllNodesWithText("使用中").fetchSemanticsNodes()
        assertEquals("「使用中」一处且只一处（活动册是唯一的）", 1, labels.size)
        // `.row-value` 是行里的子 Text，被父行的合并语义吸进去（I-A2 要求手势与文案在同一枚节点上）：
        // 按 tag 取它必须走 unmerged 树，否则命中的是整行、比较边界就成了自证。
        val label = composeRule.onNodeWithTag("wordbook_active_${importedBook.id}", useUnmergedTree = true)
        label.assertExists()
        composeRule.onNodeWithTag("wordbook_active_${bundledBook.id}", useUnmergedTree = true).assertDoesNotExist()
        val rowBounds = composeRule.onNodeWithTag("wordbook_pick_${importedBook.id}")
            .fetchSemanticsNode().boundsInRoot
        val otherRowBounds = composeRule.onNodeWithTag("wordbook_pick_${bundledBook.id}")
            .fetchSemanticsNode().boundsInRoot
        val labelBounds = label.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "「使用中」必须落在活动册那一行里（行 ${rowBounds.top}..${rowBounds.bottom}，" +
                "标签 ${labelBounds.top}..${labelBounds.bottom}）——绑错就是把它搬给了草稿选中位",
            labelBounds.top >= rowBounds.top - 1f && labelBounds.bottom <= rowBounds.bottom + 1f,
        )
        assertTrue(
            "被草稿选中、但**不是**活动册的那一行不得带「使用中」（它 ${otherRowBounds.top}..${otherRowBounds.bottom}，" +
                "标签 ${labelBounds.top}..${labelBounds.bottom}）",
            labelBounds.top >= otherRowBounds.bottom - 1f || labelBounds.bottom <= otherRowBounds.top + 1f,
        )
    }

    private fun countClose(bitmap: android.graphics.Bitmap, expected: Int): Int {
        var hits = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (isClose(bitmap.getPixel(x, y) and 0xFFFFFF, expected)) hits++
            }
        }
        return hits
    }

    private fun isClose(actual: Int, expected: Int): Boolean {
        fun channel(shift: Int) = ((actual shr shift) and 0xFF) - ((expected shr shift) and 0xFF)
        return kotlin.math.abs(channel(16)) <= 10 &&
            kotlin.math.abs(channel(8)) <= 10 &&
            kotlin.math.abs(channel(0)) <= 10
    }

    private fun clickableWithDeleteAction() = SemanticsMatcher("可点击且带「删除词书」自定义动作") { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null &&
            node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "删除词书" } == true
    }

    private companion object {
        /**
         * 22dp 实心圆（π·11² ≈ 380dp²）在最窄的取样密度上也还剩几千像素；白色勾挖掉的那点不算，
         * 所以下限只取到 150，未选档上限 40 留给抗锯齿。
         */
        const val MIN_FILLED_MARK_PIXELS = 150
        const val MAX_EMPTY_MARK_PIXELS = 40
    }
}
