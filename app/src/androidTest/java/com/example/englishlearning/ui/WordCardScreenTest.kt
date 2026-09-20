package com.example.englishlearning.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.WordCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Semantics of the F1-03 word card screen.
 *
 * Only static structure and input-injected behaviour are asserted: under `createComposeRule()`
 * the `viewModelScope` coroutines of a real ViewModel are not driven by `waitForIdle()`, so
 * asynchronous loading is covered by `WordCardViewModelTest` instead.
 */
@RunWith(AndroidJUnit4::class)
class WordCardScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun readyRendersLemmaIpaPartOfSpeechAndMeaning() {
        composeRule.setContent { WordCardScreen(state = ready()) }

        composeRule.onNodeWithTag("word_card_screen").assertExists()
        composeRule.onNodeWithTag("word_card_lemma").assertExists()
        composeRule.onNodeWithText("ability").assertExists()
        composeRule.onNodeWithTag("word_card_ipa").assertExists()
        composeRule.onNodeWithText("əˈbɪləti").assertExists()
        composeRule.onNodeWithTag("word_card_part_of_speech").assertExists()
        composeRule.onNodeWithText("n.").assertExists()
        composeRule.onNodeWithTag("word_card_meaning").assertExists()
        composeRule.onNodeWithText("能力；才能").assertExists()
        composeRule.onNodeWithTag("word_card_progress").assertExists()
    }

    @Test
    fun theThreeTiersReportTheirFixedFeedback() {
        val reported = mutableListOf<CardFeedback>()
        composeRule.setContent { WordCardScreen(state = ready(), onSubmit = { reported += it }) }

        composeRule.onNodeWithContentDescription("不认识").assertExists().assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("模糊").assertExists().assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("认识").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(CardFeedback.Unknown, CardFeedback.Fuzzy, CardFeedback.Known),
            reported,
        )
    }

    @Test
    fun optionalExampleAndInflectionsAreRenderedWhenProvided() {
        composeRule.setContent {
            WordCardScreen(
                state = ready(card(example = "She has the ability to explain ideas.", inflections = listOf("abilities"))),
            )
        }

        composeRule.onNodeWithTag("word_card_example").assertExists()
        composeRule.onNodeWithText("She has the ability to explain ideas.").assertExists()
        composeRule.onNodeWithTag("word_card_inflections").assertExists()
        composeRule.onNodeWithText("词形变化：abilities").assertExists()
    }

    @Test
    fun optionalExampleAndInflectionsAreAbsentWhenNotProvided() {
        composeRule.setContent { WordCardScreen(state = ready(card(example = null, inflections = emptyList()))) }

        composeRule.onNodeWithTag("word_card_example").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_inflections").assertDoesNotExist()
    }

    @Test
    fun noUndoEntryIsOfferedForASubmittedFeedback() {
        composeRule.setContent { WordCardScreen(state = ready()) }

        composeRule.onNodeWithText("撤销").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("撤销").assertDoesNotExist()
        composeRule.onNodeWithText("重做").assertDoesNotExist()
    }

    @Test
    fun savingDisablesTheFeedbackActions() {
        composeRule.setContent { WordCardScreen(state = ready(submitting = true)) }

        composeRule.onNodeWithTag("word_card_submitting").assertExists()
        composeRule.onNodeWithTag("word_card_feedback_unknown").assertIsNotEnabled()
        composeRule.onNodeWithTag("word_card_feedback_fuzzy").assertIsNotEnabled()
        composeRule.onNodeWithTag("word_card_feedback_known").assertIsNotEnabled()
    }

    @Test
    fun aFailedSaveKeepsTheCardVisibleAndShowsAnActionableMessage() {
        composeRule.setContent { WordCardScreen(state = ready(message = "反馈未保存成功，请重试")) }

        composeRule.onNodeWithTag("word_card_lemma").assertExists()
        composeRule.onNodeWithTag("word_card_message").assertExists()
        composeRule.onNodeWithContentDescription("反馈未保存成功，请重试").assertExists()
        composeRule.onNodeWithContentDescription("认识").assertExists()
    }

    @Test
    fun unavailableOffersRetry() {
        var retried = 0
        composeRule.setContent { WordCardScreen(state = WordCardUiState.Unavailable, onRetry = { retried++ }) }

        composeRule.onNodeWithTag("word_card_unavailable").assertExists()
        composeRule.onNodeWithContentDescription("重试").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, retried)
    }

    @Test
    fun allDoneOffersTheWayBackToTheTodayPlan() {
        var returned = 0
        composeRule.setContent {
            WordCardScreen(state = WordCardUiState.AllDone(total = 3, completedCount = 3), onBackToPlan = { returned++ })
        }

        composeRule.onNodeWithTag("word_card_all_done").assertExists()
        composeRule.onNodeWithContentDescription("返回今日计划").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, returned)
    }

    @Test
    fun noCardsKeepsTheWayBackToTheTodayPlan() {
        composeRule.setContent { WordCardScreen(state = WordCardUiState.NoCards) }

        composeRule.onNodeWithTag("word_card_no_cards").assertExists()
        composeRule.onNodeWithContentDescription("返回今日计划").assertExists()
    }

    private fun ready(
        card: WordCard = card(),
        submitting: Boolean = false,
        message: String? = null,
    ) = WordCardUiState.Ready(
        card = card,
        position = 1,
        total = 3,
        completedCount = 0,
        submitting = submitting,
        message = message,
    )

    private fun card(
        example: String? = "She has the ability to explain complex ideas simply.",
        inflections: List<String> = emptyList(),
    ) = WordCard(
        cardId = "placeholder:cet4:ability",
        wordBookId = "cet4",
        lemma = "ability",
        ipa = "əˈbɪləti",
        partOfSpeech = "n.",
        meaningZh = "能力；才能",
        example = example,
        inflections = inflections,
    )
}
