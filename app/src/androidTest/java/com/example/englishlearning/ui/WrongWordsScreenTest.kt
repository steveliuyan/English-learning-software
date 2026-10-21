package com.example.englishlearning.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 20-D（brief §二.2）：错词页**独立屏**的渲染级冒烟。
 *
 * 补的缺口由重算命令自证：`grep -rln "FavoritesScreen\|WrongWordsScreen" app/src/androidTest`
 * 在本批前只命中 `MyWordsScreenTest.kt` 的注释 `:160`/`:214`（描述缺陷形状，不是挂屏）
 * ⇒ 独立错词页零渲染级用例，而它仍挂在导航树上（`AppScreen.kt:1132`，打卡页 `onOpenWrongWords` 可达），
 * 所以走「保留则各补一枚冒烟」这一支。
 *
 * 本类**不**带 `meta` 布局级判据，这是 R-20D.1① 的口径而非遗漏：
 * `WrongWordsScreen.kt:127-131` 的 `WordRow` 只传 `lemma` + `gloss`，**不传 `meta`/`status`**
 * ⇒ 右列 120dp 上限与换行策略（R-20D.2）打不到这一屏，重算命令
 * `grep -rn "meta = " app/src/main/java/com/example/englishlearning/ui/WrongWordsScreen.kt` 现盘零命中。
 * 本席同时不动那三行（brief §三.4）。
 */
@RunWith(AndroidJUnit4::class)
class WrongWordsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun card(lemma: String, index: Int, meaning: String = "释义$index") = WordCard(
        cardId = "card-$index",
        wordBookId = "cet4",
        lemma = lemma,
        ipa = "",
        partOfSpeech = "n.",
        meaningZh = meaning,
    )

    /**
     * 渲染级冒烟：`Ready` 两行真被组合（词头 + 释义都读得到）、空态与降级态各留自己的诚实文案，
     * 且点行的出口真回交宿主那张卡（判据是回调实参，不是「点击没崩」）。
     */
    @Test
    fun ready_rows_render_and_open_the_card_the_host_was_given() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            MintGlassTheme {
                WrongWordsScreen(
                    state = WrongWordsUiState.Ready(cards = listOf(card("gossamer", 0), card("dune", 1))),
                    onBack = {},
                    onOpenCard = { opened += it.lemma },
                )
            }
        }
        composeRule.onNodeWithTag("wrong_words_screen").assertExists()
        composeRule.onNodeWithTag("wrong_words_card_0").assertExists()
        composeRule.onNodeWithTag("wrong_words_card_1").assertExists()
        composeRule.onNodeWithText("gossamer").assertExists()
        composeRule.onNodeWithText("释义0").assertExists()
        composeRule.onNodeWithText("最近一次标记为「不认识」的词，按到期先后排列。").assertExists()
        composeRule.onNodeWithTag("wrong_words_empty").assertDoesNotExist()

        composeRule.onNodeWithTag("wrong_words_card_1").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("dune"), opened)
    }

    /** 诚实降级面（冒烟的第二半）：空册与读不出各说各的话，不是一片白屏。 */
    @Test
    fun empty_and_unavailable_states_say_their_own_copy() {
        composeRule.setContent {
            MintGlassTheme {
                WrongWordsScreen(state = WrongWordsUiState.Ready(cards = emptyList()), onBack = {}, onOpenCard = {})
            }
        }
        composeRule.onNodeWithTag("wrong_words_empty")
            .assertExists()
            .assertTextEquals("当前没有待纠正的错词。复习中标记「不认识」的词会出现在这里，重新答对后自动消失。")
        composeRule.onNodeWithTag("wrong_words_unavailable").assertDoesNotExist()
    }
}
