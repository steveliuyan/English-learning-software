package com.example.englishlearning.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.domain.WordCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VocabularyRelearnScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val card = WordCard(
        cardId = "cet4:ability",
        wordBookId = "cet4",
        lemma = "ability",
        partOfSpeech = "n.",
        meaningZh = "能力",
        ipa = "/əˈbɪləti/",
        example = "She has the ability to learn.",
    )

    @Test
    fun readyStateShowsCardAndThreeFeedbackActions() {
        composeRule.setContent {
            VocabularyRelearnScreen(
                state = VocabularyRelearnUiState.Ready(card, 1, 2),
                onSubmit = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("重新学习").assertExists()
        composeRule.onNodeWithText("第 1 / 2 个").assertExists()
        composeRule.onNodeWithText("ability").assertExists()
        composeRule.onNodeWithText("不认识").assertExists()
        composeRule.onNodeWithText("模糊").assertExists()
        composeRule.onNodeWithText("认识").assertExists()
    }

    @Test
    fun unavailableStateOffersRetry() {
        var retries = 0
        composeRule.setContent {
            VocabularyRelearnScreen(
                state = VocabularyRelearnUiState.Unavailable,
                onSubmit = {},
                onBack = {},
                onRetry = { retries++ },
            )
        }

        composeRule.onNodeWithText("生词本暂时无法加载").assertExists()
        composeRule.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun doneStateExplainsWhichWordsRemain() {
        composeRule.setContent {
            VocabularyRelearnScreen(
                state = VocabularyRelearnUiState.Done(3),
                onSubmit = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("重新学习完成").assertExists()
        composeRule.onNodeWithText("本次完成 3 个生词").assertExists()
        composeRule.onNodeWithText("认识的词已移出生词本，其他词会保留供下次复习。").assertExists()
    }
}
