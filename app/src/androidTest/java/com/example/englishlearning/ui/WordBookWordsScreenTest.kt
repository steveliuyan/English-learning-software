package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.util.Log
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.learning.domain.WordCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.MultipleFailureException

@RunWith(AndroidJUnit4::class)
class WordBookWordsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * `getUnclippedBoundsInRoot()` 把每条边**各自** px→dp 换算后再相减，48dp 见方的芯片会算成
     * `47.999962dp`（emulator-5574 density 2.625 实测：`DpRect(left=219.80952, right=267.8095)`，
     * 而像素侧 126px 是精确的 48dp）。尺寸下限比较因此允许 0.05dp 的**换算**容差；
     * 要挡的红是旧竖排压缩槽的 24×18dp（差 24dp 量级），容差不构成放行空间。
     */
    private val dpConversionTolerance = 0.05.dp

    /** D2 验收：词书单词页按字母分组，组头可见、行可见。 */
    @Test
    fun letterGroupsRenderWithStickyHeadersAndRows() {
        composeRule.setContent {
            WordBookWordsScreen(
                state = WordBookWordsUiState(
                    words = listOf(
                        word("book:avocado", "avocado"),
                        word("book:apple", "apple"),
                        word("book:banana", "banana", WordBookRecordStatus.Due),
                        word("book:苹果", "苹果"),
                    ),
                ),
                onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {},
            )
        }
        composeRule.onNodeWithTag("wordbook_words_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_letter_A").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_letter_B").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_letter_#").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_row_apple").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_row_banana").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_row_苹果").assertIsDisplayed()
    }

    /** D2 验收：右侧 A–Z 快速导航栏 26 个字母常驻，每个都可点击。 */
    @Test
    fun alphabetRailIsRenderedFromAToZ() {
        composeRule.setContent {
            WordBookWordsScreen(state = WordBookWordsUiState(words = listOf(word("book:a", "apple"))), onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {})
        }
        composeRule.onNodeWithTag("wordbook_words_rail").assertIsDisplayed()
        ('A'..'Z').forEach { letter ->
            composeRule.onNodeWithTag("wordbook_words_rail_$letter").assertExists()
        }
    }

    /**
     * Task 21 / F5-03 移交账（AC-R-04）：字母索引**每一枚**的可点区必须 ≥48dp 见方，
     * 并且**带 tag 的那枚节点自己**承担 click action（不靠父槽、更不靠整轨拖动代偿）。
     *
     * 天然红锚：旧竖排压缩槽里 tag 在 `24×18dp`（屏矮时更小）的 `Box` 槽上、`clickable` 在子 `Text` 上，
     * 本用例的两条（尺寸 + click action 落在 tag 节点）在旧实现上同时判红。
     */
    @Test
    fun everyRailLetterIsAFourEightTouchTarget() {
        showAlphabet()
        val failures = mutableListOf<Throwable>()
        ('A'..'Z').forEach { letter ->
            val chip = composeRule.onNodeWithTag("wordbook_words_rail_$letter")
            recordAssertion(failures, "$letter 存在") { chip.assertExists() }
            recordAssertion(failures, "$letter 自带 click action") { chip.assertHasClickAction() }
            recordAssertion(failures, "$letter 命中区 ≥48dp 见方") {
                val hit = chip.getUnclippedBoundsInRoot()
                assertTrue(
                    "字母 $letter 的命中区必须 ≥48dp 见方（AC-R-04 钉的是可点区，不是视觉高度），实测 $hit",
                    hit.bottom - hit.top >= 48.dp - dpConversionTolerance &&
                        hit.right - hit.left >= 48.dp - dpConversionTolerance,
                )
            }
        }
        MultipleFailureException.assertEmpty(failures)
    }

    /** Task 21 / F5-03：相邻字母的命中区不得相交（相交即「点 F 落到后画的字母」，正是压缩槽时代红过的形状）。
     *  覆盖面比退役的 `railLetterHitAreaStaysInsideItsSlotAndCoversItsCentre`（只看 F/G 一对）强：本条逐对扫 A/B … Y/Z 全部相邻对。 */
    @Test
    fun adjacentRailLetterHitAreasDoNotOverlap() {
        showAlphabet()
        val tolerance = 0.5.dp
        val failures = mutableListOf<Throwable>()
        ('A'..'Y').forEach { letter ->
            val next = ('B'..'Z').first { it > letter }.toString()
            val left = composeRule.onNodeWithTag("wordbook_words_rail_$letter").getUnclippedBoundsInRoot()
            val right = composeRule.onNodeWithTag("wordbook_words_rail_$next").getUnclippedBoundsInRoot()
            recordAssertion(failures, "$letter/$next 命中区不相交") {
                val separated = left.right <= right.left + tolerance || left.left >= right.right - tolerance ||
                    left.bottom <= right.top + tolerance || left.top >= right.bottom - tolerance
                assertTrue("相邻字母 $letter 与 $next 的命中区不得重叠：$left vs $right", separated)
            }
        }
        MultipleFailureException.assertEmpty(failures)
    }

    /**
     * Task 21（顶掉退役的 `compressedRailMapsTheActualXSlotToX` 的「视口被压小」前提）：
     * 400dp 高、density 1 的小视口里字母芯片仍必须各占 48dp 见方且**互不相交**，并能把 X 走通。
     * 旧实现槽高 `min(18dp, 400/26≈15dp)` → 尺寸断言当场红。
     */
    @Test
    fun railLettersKeepTheTouchTargetFloorInATinyViewport() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.height(400.dp)) {
                    WordBookWordsScreen(
                        state = WordBookWordsUiState(words = ('A'..'Z').flatMap { letter ->
                            (1..10).map { word("book:$letter$it", "${letter.lowercaseChar()}word$it") }
                        }),
                        onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {},
                    )
                }
            }
        }
        val tolerance = 0.5.dp
        val failures = mutableListOf<Throwable>()
        ('A'..'Z').forEach { letter ->
            recordAssertion(failures, "$letter 命中区 ≥48dp 见方") {
                val hit = composeRule.onNodeWithTag("wordbook_words_rail_$letter").getUnclippedBoundsInRoot()
                assertTrue(
                    "小视口不得把字母芯片压回 48dp 以下（48dp 是下限，不是视口分配值），实测 $hit",
                    hit.bottom - hit.top >= 48.dp && hit.right - hit.left >= 48.dp,
                )
            }
        }
        ('A'..'Y').forEach { letter ->
            val next = ('B'..'Z').first { it > letter }.toString()
            val left = composeRule.onNodeWithTag("wordbook_words_rail_$letter").getUnclippedBoundsInRoot()
            val right = composeRule.onNodeWithTag("wordbook_words_rail_$next").getUnclippedBoundsInRoot()
            recordAssertion(failures, "$letter/$next 命中区不相交") {
                val separated = left.right <= right.left + tolerance || left.left >= right.right - tolerance ||
                    left.bottom <= right.top + tolerance || left.top >= right.bottom - tolerance
                assertTrue("小视口里相邻字母 $letter 与 $next 的命中区不得重叠：$left vs $right", separated)
            }
        }
        MultipleFailureException.assertEmpty(failures)
        scrollToRailLetter("X")
        composeRule.onNodeWithTag("wordbook_words_rail_X").performClick()
        composeRule.waitForIdle()
        assertNavigationState("X")
    }

    /** 当前滚动分组应在字母索引中以 active 标记突出显示，并随跳转更新。 */
    @Test
    fun activeRailLetterTracksCurrentScrollGroup() {
        val words = ('A'..'Z').flatMap { letter ->
            (1..3).map { index -> word("book:${letter.lowercaseChar()}$index", "${letter.lowercase()}word$index") }
        }
        composeRule.setContent {
            WordBookWordsScreen(state = WordBookWordsUiState(words = words), onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_rail_active_A").assertIsDisplayed()
        scrollToRailLetter("Z")
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_rail_active_A").assertDoesNotExist()
        composeRule.onNodeWithTag("wordbook_words_rail_active_Z").assertIsDisplayed()
    }

    @Test
    fun lastGroupIsHighlightedWhenItsHeaderCannotReachTheTop() {
        showAlphabet()
        scrollToRailLetter("Z")
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.onNodeWithTag("wordbook_words_rail_active_Z").assertIsDisplayed()
    }

    /**
     * Task 21 顶掉退役的 `draggingDownTheAlphabetRailJumpsToZ`：48dp 芯片条里「一次点到底」由滚动 + tap 承担，
     * 拖动面已删除。这里钉住同一末端契约——把索引滚到头、点最后一枚字母，必须落到 Z 组。
     */
    @Test
    fun tappingTheLastRailLetterJumpsToZ() {
        showAlphabet()
        scrollToRailLetter("Z")
        composeRule.onNodeWithTag("wordbook_words_rail_Z").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.waitForIdle()
        assertNavigationState("Z")
    }

    /**
     * Task 21 顶掉退役的 `draggingUpTheAlphabetRailJumpsBackToA`：从 Z 回 A 仍是一次 tap（滚回 A 后点它）。
     */
    @Test
    fun tappingTheFirstRailLetterAfterZJumpsBackToA() {
        showAlphabet()
        scrollToRailLetter("Z")
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.waitForIdle()
        assertNavigationState("Z")
        scrollToRailLetter("A")
        composeRule.onNodeWithTag("wordbook_words_rail_A").performClick()
        composeRule.waitForIdle()
        assertNavigationState("A")
    }

    /**
     * Task 21 顶掉退役的 `compressedRailKeepsActiveGlyphWithinItsLayout`：当前字母的字形整颗必须落在
     * 它那枚 48dp 芯片里（旧判据是「字形不越出压缩槽位」，语义等价，只是夹具换成真 48dp）。
     *
     * 为什么不沿用 `TextLayoutResult.hasVisualOverflow`：设备实测单枚字母在固有宽度上
     * `didOverflowWidth` 为真（`size=30 x 52`，行的可见右缘比取整后的布局宽度大 0.x px），
     * 是取整造成的假红而不是真越界；「字形包围盒 ⊆ 芯片包围盒」才是这条契约的直接表达，
     * 且照样可伪——芯片缩到小于字形、或字号涨到撑破 48dp，包含关系都会失败。
     */
    @Test
    fun activeRailGlyphStaysWithinItsChip() {
        showAlphabet()
        scrollToRailLetter("A")
        val glyph = composeRule.onNodeWithContentDescription("导航字母 A", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val chip = composeRule.onNodeWithTag("wordbook_words_rail_A").getUnclippedBoundsInRoot()
        val tolerance = 0.5.dp
        // DpRect 只有 left/top/right/bottom，宽高自己减（Rect 才有 width/height）
        val glyphWidth = glyph.right - glyph.left
        val glyphHeight = glyph.bottom - glyph.top
        val chipWidth = chip.right - chip.left
        val chipHeight = chip.bottom - chip.top
        assertTrue(
            "当前字母的字形必须整颗落在它的 48dp 芯片内: glyph=$glyph chip=$chip",
            glyph.left >= chip.left - tolerance &&
                glyph.right <= chip.right + tolerance &&
                glyph.top >= chip.top - tolerance &&
                glyph.bottom <= chip.bottom + tolerance,
        )
        assertTrue(
            "字形包围盒不得为零尺寸（为零说明该字母没渲染出内容）: glyph=$glyph",
            glyphWidth > 0.dp && glyphHeight > 0.dp,
        )
        assertTrue(
            "芯片自身不得跌破 48dp 触摸下限: chip=$chip",
            chipWidth >= 48.dp && chipHeight >= 48.dp,
        )
    }

    /**
     * Task 21（点屏内可点的芯片本身，不是屏外的槽位）：tap 芯片中心必须触发该字母的 click action。
     * 顶掉退役的 `railLetterHitAreaStaysInsideItsSlotAndCoversItsCentre` 里「点槽心选对该字母」那一半，
     * 另外两半（命中区不越槽、不与相邻重叠）由 `everyRailLetterIsAFourEightTouchTarget` +
     * `adjacentRailLetterHitAreasDoNotOverlap` 以更强的「48dp 见方 + 相邻不相交」形式承重。
     * 同时收下退役的 `draggingToTheVisibleMiddleLetterJumpsToF` 的契约：中间那枚字母（这里是 F）
     * 滚进视口后一次点中即落到 F 组——原用例靠拖到该字母的槽位坐标，本用例靠「滚进视口 + 点芯片中心」。
     */
    @Test
    fun tappingTheRailLetterUsesTheLetterClickAction() {
        composeRule.setContent {
            WordBookWordsScreen(
                state = WordBookWordsUiState(words = ('A'..'Z').flatMap { letter ->
                    (1..3).map { word("book:$letter$it", "${letter.lowercaseChar()}word$it") }
                }),
                onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {},
            )
        }
        scrollToRailLetter("F")
        composeRule.onNodeWithTag("wordbook_words_rail_F").performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()
        assertNavigationState("F")
    }

    /**
     * Task 21（原 `draggingAfterFilteringUsesTheNewGroupIndices` 的 tap 时代等价）：筛选改变分组后，
     * 字母 → 下标表必须跟着重算，点 F 仍落到筛选结果里的 F 组。
     */
    @Test
    fun tappingAfterFilteringUsesTheNewGroupIndices() {
        var filter by mutableStateOf<WordBookRecordStatus?>(null)
        val words = ('A'..'Z').flatMap { letter ->
            (1..10).map { word("book:$letter$it", "${letter.lowercaseChar()}word$it",
                if (letter in listOf('B', 'F', 'Z')) WordBookRecordStatus.Due else WordBookRecordStatus.Unlearned) }
        }
        composeRule.setContent {
            WordBookWordsScreen(
                state = WordBookWordsUiState(words = words, filter = filter),
                onBack = {}, onFilter = { filter = it }, onRetry = {}, onOpenWord = {},
            )
        }
        composeRule.onNodeWithTag("wordbook_words_filter_due").performClick()
        scrollToRailLetter("F")
        composeRule.onNodeWithTag("wordbook_words_rail_F").performClick()
        composeRule.waitForIdle()
        assertNavigationState("F")
    }

    /** Task 21（顶掉退役的 `draggingToAMissingLetterUsesTheNextAvailableGroup`）：点没有单词的字母仍跳字母序最近的下一组。 */
    @Test
    fun tappingAMissingLetterUsesTheNextAvailableGroup() {
        showWords(listOf('A', 'F', 'Z').flatMap { letter ->
            (1..10).map { word("book:$letter$it", "${letter.lowercaseChar()}word$it") }
        })
        scrollToRailLetter("E")
        composeRule.onNodeWithTag("wordbook_words_rail_E").performClick()
        composeRule.waitForIdle()
        assertNavigationState("F")
    }

    /**
     * Task 21（原 `draggingUpdatesGroupsBeforeTheFingerIsReleased` 的 tap 时代等价）：拖动面退役后，
     * 「一次连点里每一步都要立刻反映到列表」改成逐枚 tap 连点，仍用累加失败的写法——
     * 前一步红不遮蔽后一步（`checkNavigationState` 的既有价值原样保留）。
     */
    @Test
    fun successiveRailTapsUpdateTheNavigationStateEachStep() {
        showAlphabet()
        scrollToRailLetter("Z")
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.waitForIdle()
        assertNavigationState("Z")
        val failures = mutableListOf<Throwable>()
        listOf("F", "M", "A").forEach { letter ->
            scrollToRailLetter(letter)
            composeRule.onNodeWithTag("wordbook_words_rail_$letter").performClick()
            composeRule.waitForIdle()
            checkNavigationState(letter, failures)
        }
        MultipleFailureException.assertEmpty(failures)
    }

    private fun assertNavigationState(letter: String) {
        val failures = mutableListOf<Throwable>()
        checkNavigationState(letter, failures)
        MultipleFailureException.assertEmpty(failures)
    }

    private fun checkNavigationState(letter: String, failures: MutableList<Throwable>) {
        recordAssertion(failures, "$letter 高亮") {
            composeRule.onNodeWithTag("wordbook_words_rail_active_$letter").assertIsDisplayed()
        }
        recordAssertion(failures, "$letter 组头") {
            composeRule.onNodeWithTag("wordbook_words_letter_$letter").assertIsDisplayed()
        }
    }

    private fun recordAssertion(failures: MutableList<Throwable>, label: String, assertion: () -> Unit) {
        try {
            assertion()
        } catch (failure: AssertionError) {
            failures += AssertionError("$label: ${failure.message}", failure)
        }
    }

    /**
     * 把某枚字母芯片滚进视口。26×48dp（实测 Rail 内容宽 1295dp）远超屏宽，点靠后的字母前必须让容器先滚，
     * 否则 `performClick` 的坐标落在视口外、打不到芯片（设备实测：`Z after scroll` 的裁剪后矩形为 0×0）。
     *
     * 用 `performScrollTo()` 而不是手推 `SemanticsActions.ScrollBy`：后者在本 Compose 版本上对
     * `Modifier.horizontalScroll` 传 (dx, 0f) **不发生横向位移**（当场实测无反应），
     * 而 `performScrollTo()` 会自己找可滚祖先并按容器语义补齐位移。
     */
    private fun scrollToRailLetter(letter: String) {
        composeRule.onNodeWithTag("wordbook_words_rail_$letter").performScrollTo()
        composeRule.waitForIdle()
    }

    @Test
    fun shortListHighlightsTheTopGroupInsteadOfTheLastGroup() {
        showWords(listOf(word("book:apple", "apple"), word("book:banana", "banana")))
        val failures = mutableListOf<Throwable>()
        recordAssertion(failures, "短列表 A 高亮") {
            composeRule.onNodeWithTag("wordbook_words_rail_active_A").assertIsDisplayed()
        }
        recordAssertion(failures, "短列表 B 非当前组") {
            composeRule.onNodeWithTag("wordbook_words_rail_active_B").assertDoesNotExist()
        }
        MultipleFailureException.assertEmpty(failures)
    }

    @Test
    fun scrollingTheListUpdatesTheCurrentLetterWithoutRailClicks() {
        showWords(('A'..'C').flatMap { letter ->
            (1..20).map { word("book:$letter$it", "${letter.lowercaseChar()}word$it") }
        })
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        val failures = mutableListOf<Throwable>()
        recordAssertion(failures, "列表滚动 B 高亮") {
            composeRule.onNodeWithTag("wordbook_words_rail_active_B").assertIsDisplayed()
        }
        recordAssertion(failures, "列表滚动 A 非当前组") {
            composeRule.onNodeWithTag("wordbook_words_rail_active_A").assertDoesNotExist()
        }
        MultipleFailureException.assertEmpty(failures)
    }

    @Test
    fun scrollingToHashGroupClearsAlphabetHighlight() {
        showWords((1..20).map { word("book:A$it", "aword$it") } +
            (1..20).map { word("book:hash$it", "1word$it") })
        composeRule.onNodeWithTag("wordbook_words_rail_active_A").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        composeRule.onNodeWithTag("wordbook_words_letter_#").assertIsDisplayed()
        val failures = mutableListOf<Throwable>()
        ('A'..'Z').forEach { letter ->
            recordAssertion(failures, "# 分组清除 $letter 高亮") {
                composeRule.onNodeWithTag("wordbook_words_rail_active_$letter").assertDoesNotExist()
            }
        }
        MultipleFailureException.assertEmpty(failures)
    }

    @Test
    fun activeLetterUsesLargerText() {
        showAlphabet()
        val active = composeRule.onNodeWithContentDescription("导航字母 A").textLayout().layoutInput.style
        val normal = composeRule.onNodeWithContentDescription("导航字母 B").textLayout().layoutInput.style
        assertTrue("当前字母应大于普通字母", active.fontSize.value > normal.fontSize.value)
    }

    @Test
    fun activeLetterUsesADifferentColor() {
        showAlphabet()
        val active = composeRule.onNodeWithContentDescription("导航字母 A").textLayout().layoutInput.style
        val normal = composeRule.onNodeWithContentDescription("导航字母 B").textLayout().layoutInput.style
        assertTrue("当前字母颜色应区别于普通字母", active.color != normal.color)
        saveScreenshot("current-A")
        // 去留理由（裁定 R-21.1）：本条有证伪力——文本节点确实能经 `textLayout().layoutInput.style` 读到
        // 字形取值（20-B 评审席已更正「设备侧读不到颜色」的旧口径），把 active 换成与 normal 同值即当场红。
        // 因此保留，只把点靠后字母补上「先滚进视口」这一步（横向 48dp 芯片条的必然改动）。
        scrollToRailLetter("F")
        composeRule.onNodeWithTag("wordbook_words_rail_F").performClick()
        saveScreenshot("current-F")
    }

    private fun saveScreenshot(name: String) {
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "wordbook-rail-evidence")
        output.mkdirs()
        val bitmap = composeRule.onNodeWithTag("wordbook_words_screen").captureToImage().asAndroidBitmap()
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun showAlphabet() = showWords(('A'..'Z').flatMap { letter ->
        (1..3).map { word("book:$letter$it", "${letter.lowercaseChar()}word$it") }
    })

    private fun showWords(words: List<WordBookRecord>) {
        composeRule.setContent {
            WordBookWordsScreen(state = WordBookWordsUiState(words = words), onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {})
        }
        composeRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    /**
     * D2 验收：点导航字母直接跳到对应组。断言钉用户契约——
     * 起点在 A（aword1 可见），点 Z 后离开 A（aword1 不可见）且 Z 组头可见。
     * stickyHeader 是 overlay 钉顶实现，不钉具体某一行是否进入可视区。
     */
    @Test
    fun tappingRailLetterScrollsToItsGroup() {
        val words = ('A'..'Z').flatMap { letter ->
            (1..3).map { index -> word("book:${letter.lowercaseChar()}$index", "${letter.lowercase()}word$index") }
        }
        composeRule.setContent {
            WordBookWordsScreen(state = WordBookWordsUiState(words = words), onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_row_a1").assertIsDisplayed()
        scrollToRailLetter("Z")
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_letter_Z").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_row_z1").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_row_a1").assertDoesNotExist()
    }

    /** 状态筛选沿用原「全部单词」的语义，只是换到了独立页：计数 + 本地过滤。 */
    @Test
    fun filterChipsShowCountsAndSelectingNarrowsRows() {
        var filter by mutableStateOf<WordBookRecordStatus?>(null)
        composeRule.setContent {
            WordBookWordsScreen(
                state = WordBookWordsUiState(
                    words = listOf(
                        word("book:apple", "apple", WordBookRecordStatus.Unlearned),
                        word("book:avocado", "avocado", WordBookRecordStatus.Unlearned),
                        word("book:banana", "banana", WordBookRecordStatus.Due),
                        word("book:苹果", "苹果", WordBookRecordStatus.Learning),
                    ),
                    filter = filter,
                ),
                onBack = {},
                onFilter = { filter = it },
                onRetry = {},
                onOpenWord = {},
            )
        }
        composeRule.onNodeWithText("全部 4").assertIsDisplayed()
        composeRule.onNodeWithText("未学 2").assertIsDisplayed()
        composeRule.onNodeWithText("待复习 1").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_row_banana").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_filter_unlearned").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_row_banana").assertDoesNotExist()
        composeRule.onNodeWithTag("wordbook_words_row_apple").assertIsDisplayed()
    }

    /**
     * F5-03 / Task 10（B2 文字链）：词书单词页「← 返回」的触控目标必须 ≥48dp 见方
     * （现状只有 `padding(8.dp)`）。返回链在根 Column 首行、不在滚动容器内，无需 performScrollTo。
     */
    @Test
    fun backLinkMeetsTheMinimumTouchTarget() {
        composeRule.setContent {
            WordBookWordsScreen(
                state = WordBookWordsUiState(words = listOf(word("book:apple", "apple"))),
                onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {},
            )
        }
        composeRule.onNodeWithTag("wordbook_words_back").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_back").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("wordbook_words_back").assertWidthIsAtLeast(48.dp)

        val bounds = composeRule.onNodeWithTag("wordbook_words_back").getUnclippedBoundsInRoot()
        assertTrue("wordbook_words_back 的触控目标应至少 48dp 高，实际=$bounds", bounds.bottom - bounds.top >= 48.dp)
        assertTrue("wordbook_words_back 的触控目标应至少 48dp 宽，实际=$bounds", bounds.right - bounds.left >= 48.dp)
    }

    /**
     * Task 20-B 修复轮 1 · F1 判据（行为级、可量）：渲染禁用行时，原因串「这个词已不在当前词书中」
     * 必须**完整呈现**——不被组件右列的 `widthIn(max = 120.dp)` + `maxLines = 1` + Ellipsis 吃掉。
     *
     * 判据取派工单点名口径：承载该串的文本布局实测宽度**明显大于** 120dp 右列上限
     * （阈值 120×1.10 = 132dp：现盘 `meta` 写法被上限卡死在 ≈120dp 当场红；改走 `gloss` 左列后
     * 整句 ≈143dp，对 120dp 上限余量 ≈19%、对 132dp 阈值余量 ≈8%，数走 logcat tag `T20B_F1`），
     * 且该槽必须允许多行（`layoutInput.maxLines ≥ 2`，钉住「不再落回单行右列」这一形状）。
     * 迁移前这枚 `Text` 在满宽 `Column` 里整句可见；本条把「呈现层不丢信息」钉成设备实测。
     */
    @Test
    fun disabledRowShowsTheUnavailableNoticeInFull() {
        composeRule.setContent {
            WordBookWordsScreen(
                state = WordBookWordsUiState(words = listOf(disabledWord("book:ghost", "ghost"))),
                onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {},
            )
        }
        val notice = composeRule.onNodeWithText("这个词已不在当前词书中")
        notice.assertIsDisplayed()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val layout = notice.textLayout()
        val widthDp = layout.size.width / density
        val maxLines = layout.layoutInput.maxLines
        Log.i("T20B_F1", "notice widthDp=$widthDp maxLines=$maxLines density=$density textLayoutWidthPx=${layout.size.width}")
        assertTrue(
            "禁用行原因串必须完整呈现：实测宽度应明显大于右列上限 120dp（阈值 132dp），" +
                "被上限卡住即省略号吞信息。实测 ${widthDp}dp，maxLines=$maxLines",
            widthDp > 132.0,
        )
        assertTrue(
            "承载原因串的槽必须允许多行（maxLines ≥ 2）；实测 maxLines=$maxLines 说明它还在单行右列槽里",
            maxLines >= 2,
        )
    }

    /** F1 夹具：禁用行——`card == null` 且 `canOpenDetail == false`（与释义槽按构造互斥的前提见 JVM 锚）。 */
    private fun disabledWord(cardId: String, lemma: String): WordBookRecord =
        WordBookRecord(
            card = null,
            cardId = cardId,
            displayLemma = lemma,
            status = WordBookRecordStatus.Unlearned,
            canOpenDetail = false,
        )

    private fun word(
        cardId: String,
        lemma: String,
        status: WordBookRecordStatus = WordBookRecordStatus.Unlearned,
    ): WordBookRecord {
        val card = WordCard(cardId, "book", lemma, "", "", "释义")
        return WordBookRecord(card, cardId, lemma, status)
    }
}
