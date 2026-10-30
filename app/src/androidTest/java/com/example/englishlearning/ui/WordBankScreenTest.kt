package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 词库页壳（spec §3.3）的 connected 装配 smoke。
 *
 * 为什么这层必须真跑一次：本屏是**壳**——四段各接一枚外来的 pane，壳自己不含数据也不含词行。
 * 源码契约（`WordBankScreenContractTest`）只能证「四段各挨一个面」这句形状，证不了
 * 「点了那一格真的换面」「页头副词真的落在页头上」「返回位真的接出去」——
 * 这三件都是组合期的接线，只有把壳真挂进一棵 Compose 树才看得见。
 *
 * 面用带 tag 的空 Box 替身：pane 的内容归各屏自己的测试，这里只看**哪一面在场**。
 * 一段一方法（一个测试方法只一次 `setContent`，本仓硬事实）。
 */
@RunWith(AndroidJUnit4::class)
class WordBankScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun pane(tag: String): @androidx.compose.runtime.Composable () -> Unit = {
        Box(Modifier.fillMaxSize().testTag(tag)) { Text(tag) }
    }

    @Test
    fun fourSegmentsRenderUnderTheHeaderWithTheBookPaneByDefault() {
        composeRule.setContent {
            MintGlassTheme {
                WordBankScreen(
                    wordBookName = "四级核心词",
                    section = WordBankSection.Book,
                    onSelectSection = {},
                    onBack = {},
                    bookPane = pane("pane_book"),
                    newPane = pane("pane_new"),
                    wrongPane = pane("pane_wrong"),
                    favoritePane = pane("pane_favorite"),
                )
            }
        }
        composeRule.onNodeWithTag("word_bank_screen").assertExists()
        // 页头只有这一枚返回位：标题与副词就是本页的「我在哪、看的是哪一册」。
        composeRule.onNodeWithText("词库").assertExists()
        composeRule.onNodeWithText("四级核心词").assertExists()
        composeRule.onNodeWithContentDescription("返回").assertExists()
        WordBankSection.entries.forEach { cell ->
            composeRule.onNodeWithTag(cell.tag).assertExists().assertHeightIsAtLeast(48.dp)
        }
        // 默认落词书段，其余三段不同屏（否则同屏两套列表互相抢屏）。
        composeRule.onNodeWithTag("pane_book").assertExists()
        composeRule.onNodeWithTag("pane_new").assertDoesNotExist()
        composeRule.onNodeWithTag("pane_wrong").assertDoesNotExist()
        composeRule.onNodeWithTag("pane_favorite").assertDoesNotExist()
        composeRule.onNodeWithTag("word_bank_segment_book")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已选中"))
    }

    @Test
    fun tappingASegmentSelectsItAndSwapsThePane() {
        var section by mutableStateOf(WordBankSection.Book)
        var selected: WordBankSection? = null
        composeRule.setContent {
            MintGlassTheme {
                WordBankScreen(
                    wordBookName = "四级核心词",
                    section = section,
                    onSelectSection = {
                        selected = it
                        section = it
                    },
                    onBack = {},
                    bookPane = pane("pane_book"),
                    newPane = pane("pane_new"),
                    wrongPane = pane("pane_wrong"),
                    favoritePane = pane("pane_favorite"),
                )
            }
        }
        composeRule.onNodeWithTag("word_bank_segment_favorite").performClick()
        composeRule.waitForIdle()
        // 回调带的是被点的那一段（不是「随便开一下」），且壳按新段换面。
        assertEquals(WordBankSection.Favorite, selected)
        composeRule.onNodeWithTag("pane_favorite").assertExists()
        composeRule.onNodeWithTag("pane_book").assertDoesNotExist()
        // 选中态有语义通道，不只靠颜色（TalkBack 要读得出「已选中」）。
        composeRule.onNodeWithTag("word_bank_segment_favorite")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已选中"))
        composeRule.onNodeWithTag("word_bank_segment_book")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未选中"))
    }

    @Test
    fun theHeaderBackAffordanceInvokesTheHostCallback() {
        var backPressed = false
        composeRule.setContent {
            MintGlassTheme {
                WordBankScreen(
                    wordBookName = "四级核心词",
                    section = WordBankSection.Book,
                    onSelectSection = {},
                    onBack = { backPressed = true },
                    bookPane = pane("pane_book"),
                    newPane = pane("pane_new"),
                    wrongPane = pane("pane_wrong"),
                    favoritePane = pane("pane_favorite"),
                )
            }
        }
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        assertTrue("返回位必须把退页交给宿主（缺 = 词库页是死胡同）", backPressed)
    }

    /**
     * 分段标签带计数（原型 `sMeWords`，`assets/app.js:1292`）的行为面。
     *
     * 三条腿：
     *  ① 读到条数的两格显示「段名 + 空格 + N」；
     *  ② 读不到（`null`）的那一格显示**裸段名**——不是「错词 0」：0 是「Ready 且空册」才有资格说的数；
     *  ③ 词书格不带数字，且 `Ready` 空册的 0 也如实显示（本夹具把收藏格喂 0 证这一半）。
     * 一次 `setContent` 同时喂三种形态（3 / null / 0），是本仓「一个方法一次组合」的口径。
     */
    @Test
    fun segmentLabelsCarryCountsOnlyWhenTheSectionStateIsReadable() {
        composeRule.setContent {
            MintGlassTheme {
                WordBankScreen(
                    wordBookName = "四级核心词",
                    section = WordBankSection.Book,
                    onSelectSection = {},
                    onBack = {},
                    bookPane = pane("pane_book"),
                    newPane = pane("pane_new"),
                    wrongPane = pane("pane_wrong"),
                    favoritePane = pane("pane_favorite"),
                    newCount = 3,
                    wrongCount = null,
                    favoriteCount = 0,
                )
            }
        }
        composeRule.onNodeWithTag("word_bank_segment_new").assertTextEquals("生词 3")
        composeRule.onNodeWithTag("word_bank_segment_wrong").assertTextEquals("错词")
        composeRule.onNodeWithTag("word_bank_segment_favorite").assertTextEquals("收藏 0")
        composeRule.onNodeWithTag("word_bank_segment_book").assertTextEquals("词书")
        composeRule.onNodeWithText("错词 0").assertDoesNotExist()
    }
}
