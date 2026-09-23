package com.example.englishlearning.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiLearningScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setScreen(
        todayWordCount: Int? = 12,
        dueWordCount: Int? = 5,
        aiConfigured: Boolean = false,
        onOpenFeature: (AiFeature) -> Unit = {},
        onOpenWordList: () -> Unit = {},
    ) {
        composeRule.setContent {
            AiLearningScreen(
                todayWordCount = todayWordCount,
                dueWordCount = dueWordCount,
                aiConfigured = aiConfigured,
                onOpenFeature = onOpenFeature,
                onOpenWordList = onOpenWordList,
            )
        }
    }

    @Test fun renders_the_header_card_and_the_ai_status_badge() {
        setScreen()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onNodeWithTag("ai_learning_header").assertExists()
        composeRule.onNodeWithTag("ai_learning_status_badge").assertExists()
        composeRule.onNodeWithText("AI 学").assertExists()
    }

    /** AI 网关还没接通，页面必须如实说「尚未接通」，不能让人以为已经能用。 */
    @Test fun badge_admits_the_ai_gateway_is_not_connected_yet() {
        setScreen(aiConfigured = false)
        composeRule.onNodeWithText("AI 尚未接通").assertExists()
        composeRule.onNodeWithText("AI 已配置").assertDoesNotExist()
    }

    @Test fun badge_switches_once_a_profile_exists() {
        setScreen(aiConfigured = true)
        composeRule.onNodeWithText("AI 已配置").assertExists()
        composeRule.onNodeWithText("AI 尚未接通").assertDoesNotExist()
    }

    @Test fun renders_all_four_feature_rows() {
        setScreen()
        AiFeature.entries.forEach { feature ->
            composeRule
                .onNodeWithTag("ai_feature_${feature.key}")
                .assertExists()
                .assertHasClickAction()
            composeRule.onNodeWithText(feature.summary).assertExists()
        }
    }

    /**
     * 用户明确抱怨过「点了没反应」。四个入口都必须真的把回调打出去。
     */
    @Test fun every_feature_row_reports_its_own_feature() {
        val received = mutableListOf<AiFeature>()
        setScreen(onOpenFeature = { received += it })
        AiFeature.entries.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").performScrollTo().performClick()
        }
        composeRule.waitForIdle()
        assertEquals(AiFeature.entries.toList(), received)
    }

    @Test fun navigation_card_shows_the_real_word_counts() {
        setScreen(todayWordCount = 12, dueWordCount = 5)
        composeRule.onNodeWithTag("ai_learning_nav_card").assertExists()
        composeRule.onNodeWithTag("ai_learning_word_list_count").performScrollTo().assertExists()
    }

    /** 今日计划不可读时必须说不可读，不能拿 0 冒充。 */
    @Test fun navigation_card_does_not_fake_zero_when_the_plan_is_unavailable() {
        setScreen(todayWordCount = null, dueWordCount = null)
        composeRule.onNodeWithTag("ai_learning_nav_card").assertExists().performScrollTo()
        composeRule.onNodeWithText("今日计划还没读出来，稍后重试。").assertExists()
    }

    @Test fun detail_button_opens_the_word_list() {
        var opened = 0
        setScreen(onOpenWordList = { opened++ })
        composeRule.onNodeWithTag("ai_learning_nav_detail").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test fun explainer_starts_collapsed_and_expands_on_tap() {
        setScreen()
        val toggle = composeRule.onNodeWithTag("ai_learning_explainer_toggle")
        toggle.performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription("AI 学如何起作用说明").assertExists()
        composeRule.onNodeWithText("会用到的数据").assertDoesNotExist()
        toggle.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("会用到的数据").assertExists()
        composeRule.onNodeWithText("数据发往哪里").assertExists()
    }
}
