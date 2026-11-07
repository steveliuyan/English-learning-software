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

    /**
     * F5-05 / 批 C-3（分类 1「等数据」）：生词复习的首帧要有**这一屏的真实形状**。
     *
     * 旧形状是 48dp 转圈 + 一句进行中文案：回填时长出一张生词卡 + 底部三档评分条，是版面跳变。
     * 现在镜像的是三件——计数行位置（不画数字，`第 N / M 个` 两个数首帧都不可能知道）、生词卡、吸底三档条。
     * `Idle` 与 `Loading` 同用这一支：两者对用户的承诺相同（词表还没读出来，形状先给）。
     *
     * 两枚状态**各一个测试方法**：同一 Compose 测试里两次 `setContent` 真机必崩（QA 怪癖表既有条目），
     * 所以不写循环。判据逐字相同，走同一个 [assertLoadingDrawsCardShapedSkeleton]。
     */
    @Test
    fun loadingDrawsCardShapedSkeletonWithoutNumbers() {
        assertLoadingDrawsCardShapedSkeleton(VocabularyRelearnUiState.Loading)
    }

    @Test
    fun idleDrawsCardShapedSkeletonWithoutNumbers() {
        assertLoadingDrawsCardShapedSkeleton(VocabularyRelearnUiState.Idle)
    }

    private fun assertLoadingDrawsCardShapedSkeleton(state: VocabularyRelearnUiState) {
        composeRule.setContent {
            VocabularyRelearnScreen(state = state, onSubmit = {}, onBack = {})
        }

        composeRule.onNodeWithTag("relearn_loading").assertExists()
        composeRule.onNodeWithText("正在", substring = true).assertDoesNotExist()
        // 假数字禁令（F1-01 同源）：计数与「本次完成 N 个」都不属于这一帧。
        composeRule.onNodeWithText("第", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("relearn_feedback_bar").assertDoesNotExist()
    }
}
