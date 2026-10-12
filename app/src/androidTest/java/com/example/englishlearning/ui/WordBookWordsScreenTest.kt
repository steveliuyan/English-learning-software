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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
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
        composeRule.onNodeWithContentDescription("wordbook_words_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_letter_A").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_letter_B").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_letter_#").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_apple").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_banana").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_苹果").assertIsDisplayed()
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

    /** 当前滚动分组应在右侧导航栏中以 active 标记突出显示，并随跳转更新。 */
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
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_rail_active_A").assertDoesNotExist()
        composeRule.onNodeWithTag("wordbook_words_rail_active_Z").assertIsDisplayed()
    }

    @Test
    fun lastGroupIsHighlightedWhenItsHeaderCannotReachTheTop() {
        showAlphabet()
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.onNodeWithTag("wordbook_words_rail_active_Z").assertIsDisplayed()
    }

    @Test
    fun draggingDownTheAlphabetRailJumpsToZ() {
        showAlphabet()
        composeRule.onNodeWithTag("wordbook_words_rail").performTouchInput {
            down(Offset(center.x, 1f))
            moveTo(Offset(center.x, height - 1f), 500)
            up()
        }
        assertNavigationState("Z")
    }

    @Test
    fun draggingToTheVisibleMiddleLetterJumpsToF() {
        showAlphabet()
        val rail = composeRule.onNodeWithTag("wordbook_words_rail").fetchSemanticsNode().boundsInRoot
        val target = composeRule.onNodeWithTag("wordbook_words_rail_F").fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("wordbook_words_rail").performTouchInput {
            down(Offset(center.x, 1f))
            moveTo(Offset(target.x - rail.left, target.y - rail.top), 500)
            up()
        }
        assertNavigationState("F")
    }

    @Test
    fun draggingUpTheAlphabetRailJumpsBackToA() {
        showAlphabet()
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.onNodeWithTag("wordbook_words_rail").performTouchInput {
            down(Offset(center.x, height - 1f))
            moveTo(Offset(center.x, 1f), 500)
            up()
        }
        assertNavigationState("A")
    }

    @Test
    fun compressedRailMapsTheActualXSlotToX() {
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
        dragToLetter("X")
        assertNavigationState("X")
    }

    @Test
    fun compressedRailKeepsActiveGlyphWithinItsLayout() {
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
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithContentDescription("导航字母 A")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue(
            "压缩轨道的当前字母不应发生字形溢出: size=${layout.size}, overflow=${layout.hasVisualOverflow}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}",
            !layout.hasVisualOverflow,
        )
    }

    @Test
    fun tappingTheActualRailSlotUsesTheLetterClickAction() {
        composeRule.setContent {
            WordBookWordsScreen(
                state = WordBookWordsUiState(words = ('A'..'Z').flatMap { letter ->
                    (1..3).map { word("book:$letter$it", "${letter.lowercaseChar()}word$it") }
                }),
                onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {},
            )
        }
        composeRule.onNodeWithTag("wordbook_words_rail_F").performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()
        assertNavigationState("F")
    }

    @Test
    fun draggingAfterFilteringUsesTheNewGroupIndices() {
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
        dragToLetter("F")
        assertNavigationState("F")
    }

    @Test
    fun draggingToAMissingLetterUsesTheNextAvailableGroup() {
        showWords(listOf('A', 'F', 'Z').flatMap { letter ->
            (1..10).map { word("book:$letter$it", "${letter.lowercaseChar()}word$it") }
        })
        dragToLetter("E")
        assertNavigationState("F")
    }

    @Test
    fun draggingUpdatesGroupsBeforeTheFingerIsReleased() {
        showAlphabet()
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        assertNavigationState("Z")
        val rail = composeRule.onNodeWithTag("wordbook_words_rail").fetchSemanticsNode().boundsInRoot
        val targets = listOf("F", "M", "A").map { letter ->
            letter to (composeRule.onNodeWithTag("wordbook_words_rail_$letter").fetchSemanticsNode().boundsInRoot.center - rail.topLeft)
        }
        val node = composeRule.onNodeWithTag("wordbook_words_rail")
        val failures = mutableListOf<Throwable>()
        node.performTouchInput { down(Offset(center.x, height - 1f)) }
        try {
            targets.forEach { (letter, position) ->
                node.performTouchInput { moveTo(position, 500) }
                // 同一次按下中逐步验证；前一步失败不遮蔽后面的 M/A，也不提前抬手。
                checkNavigationState(letter, failures)
            }
        } finally {
            node.performTouchInput { up() }
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

    private fun dragToLetter(letter: String) {
        val rail = composeRule.onNodeWithTag("wordbook_words_rail").fetchSemanticsNode().boundsInRoot
        val target = composeRule.onNodeWithTag("wordbook_words_rail_$letter").fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("wordbook_words_rail").performTouchInput {
            down(Offset(center.x, 1f))
            moveTo(target - rail.topLeft, 500)
            up()
        }
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
        composeRule.onNodeWithContentDescription("wordbook_words_row_a1").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_letter_Z").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_z1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_a1").assertDoesNotExist()
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
        composeRule.onNodeWithContentDescription("全部 4").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("未学 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("待复习 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_banana").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_filter_unlearned").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("wordbook_words_row_banana").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("wordbook_words_row_apple").assertIsDisplayed()
    }

    private fun word(
        cardId: String,
        lemma: String,
        status: WordBookRecordStatus = WordBookRecordStatus.Unlearned,
    ): WordBookRecord {
        val card = WordCard(cardId, "book", lemma, "", "", "释义")
        return WordBookRecord(card, cardId, lemma, status)
    }
}
