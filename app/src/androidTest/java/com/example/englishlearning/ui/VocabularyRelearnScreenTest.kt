package com.example.englishlearning.ui

import androidx.compose.ui.test.junit4.createComposeRule
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
        var submitted: CardFeedback? = null
        composeRule.setContent {
            VocabularyRelearnScreen(
                state = VocabularyRelearnUiState.Ready(card, 1, 2),
                onSubmit = { submitted = it },
                onBack = {},
            )
        }

        // 文本保留：三条用例的定位文本与本屏的可见文案是同一份事实，标签再准也不许把话改掉。
        composeRule.onNodeWithText("重新学习").assertExists()
        composeRule.onNodeWithText("第 1 / 2 个").assertExists()
        composeRule.onNodeWithText("ability").assertExists()
        composeRule.onNodeWithText("不认识").assertExists()
        composeRule.onNodeWithText("模糊").assertExists()
        composeRule.onNodeWithText("认识").assertExists()

        // 版式锚（批 2 Task 3）：吸底条 + 三枚等宽评分 + 进度 / 词头各一枚。
        composeRule.onNodeWithTag("relearn_screen").assertExists()
        composeRule.onNodeWithTag("relearn_feedback_bar").assertExists()
        composeRule.onNodeWithTag("relearn_progress").assertExists()
        composeRule.onNodeWithTag("relearn_lemma").assertExists()

        composeRule.onNodeWithTag("relearn_feedback_unknown").performClick()
        assertEquals(CardFeedback.Unknown, submitted)
        composeRule.onNodeWithTag("relearn_feedback_fuzzy").performClick()
        assertEquals(CardFeedback.Fuzzy, submitted)
        composeRule.onNodeWithTag("relearn_feedback_known").performClick()
        assertEquals(CardFeedback.Known, submitted)
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
        composeRule.onNodeWithTag("relearn_retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun doneStateExplainsWhichWordsRemain() {
        var backs = 0
        composeRule.setContent {
            VocabularyRelearnScreen(
                state = VocabularyRelearnUiState.Done(3),
                onSubmit = {},
                onBack = { backs++ },
            )
        }

        composeRule.onNodeWithText("重新学习完成").assertExists()
        composeRule.onNodeWithText("本次完成 3 个生词").assertExists()
        composeRule.onNodeWithText("认识的词已移出生词本，其他词会保留供下次复习。").assertExists()

        // 完成页没有评分条——三档评分只属于「还有词要复习」的窗口。
        composeRule.onNodeWithTag("relearn_feedback_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("relearn_done_back").performClick()
        assertEquals(1, backs)
    }
}
