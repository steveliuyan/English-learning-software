package com.example.englishlearning.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
class AiFeatureScreenTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * `setContent` 每个测试只能调一次，所以四个功能的轮询走一个可变状态切换，
     * 而不是在循环里反复 setContent。
     */
    private fun setScreen(
        todayWordCount: Int? = 12,
        dueWordCount: Int? = 5,
        onBack: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenLearning: () -> Unit = {},
    ): MutableState<AiFeature> {
        val current = mutableStateOf(AiFeature.WORD_PASSAGE)
        composeRule.setContent {
            AiFeatureScreen(
                feature = current.value,
                todayWordCount = todayWordCount,
                dueWordCount = dueWordCount,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                onOpenLearning = onOpenLearning,
            )
        }
        return current
    }

    /**
     * 四个功能页都只是骨架，但骨架也必须是完整的：标题、进度、依赖、数据、动作一个不少。
     */
    @Test fun every_feature_renders_a_complete_skeleton() {
        val current = setScreen()
        AiFeature.entries.forEach { feature ->
            current.value = feature
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("ai_feature_screen").assertExists()
            composeRule.onNodeWithTag("ai_feature_title").assertExists()
            composeRule.onNodeWithText(feature.title).assertExists()
            composeRule.onNodeWithTag("ai_feature_status_card").assertExists()
            composeRule.onNodeWithTag("ai_feature_status").assertExists()
            composeRule.onNodeWithTag("ai_feature_dependencies_card").assertExists()
            composeRule.onNodeWithTag("ai_feature_data_card").assertExists()
            composeRule.onNodeWithTag("ai_feature_open_settings").assertExists()
        }
    }

    @Test fun dependency_list_matches_the_feature_catalogue() {
        val current = setScreen()
        AiFeature.entries.forEach { feature ->
            current.value = feature
            composeRule.waitForIdle()
            feature.dependencies.indices.forEach { index ->
                composeRule.onNodeWithTag("ai_feature_dependency_$index").assertExists()
            }
            composeRule.onNodeWithTag("ai_feature_dependency_${feature.dependencies.size}").assertDoesNotExist()
        }
    }

    /** 骨架页面上必须出现该功能自己的状态文案，不能被泛化成一句万金油。 */
    @Test fun status_block_repeats_the_catalogue_wording() {
        val current = setScreen()
        AiFeature.entries.forEach { feature ->
            current.value = feature
            composeRule.waitForIdle()
            composeRule.onNodeWithText(feature.status).assertExists()
        }
    }

    @Test fun status_pill_admits_the_feature_is_not_implemented() {
        setScreen()
        composeRule.onNodeWithTag("ai_feature_status_pill").assertExists()
        composeRule.onNodeWithText("未实现").assertExists()
        composeRule.onNodeWithText("可用").assertDoesNotExist()
    }

    @Test fun data_block_reports_the_real_counts() {
        setScreen(todayWordCount = 12, dueWordCount = 5)
        composeRule.onNodeWithText("今日新词 12 个 · 待复习 5 个（来自本机，不会离开设备除非发起 AI 请求）").assertExists()
    }

    @Test fun data_block_does_not_fake_zero_when_the_plan_is_unavailable() {
        setScreen(todayWordCount = null, dueWordCount = null)
        composeRule.onNodeWithText("今日计划还没读出来，稍后重试。").assertExists()
    }

    @Test fun the_usable_actions_report_their_callbacks() {
        var toSettings = 0
        var toLearning = 0
        setScreen(onOpenSettings = { toSettings++ }, onOpenLearning = { toLearning++ })
        composeRule.onNodeWithTag("ai_feature_open_settings").performScrollTo().assertHasClickAction().performClick()
        composeRule.onNodeWithTag("ai_feature_open_learning").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, toSettings)
        assertEquals(1, toLearning)
    }

    /** 这些按钮同时要有 testTag（测试用）与 contentDescription（无障碍 + adb 走查用）。 */
    @Test fun every_action_is_reachable_by_its_content_description() {
        setScreen()
        composeRule.onNodeWithContentDescription("返回 AI 学").assertExists()
        composeRule.onNodeWithContentDescription("去设置里配置 AI").performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription("回到今日学习").performScrollTo().assertExists()
    }

    @Test fun back_button_reports_its_callback() {
        var backed = 0
        setScreen(onBack = { backed++ })
        composeRule.onNodeWithTag("ai_feature_back").assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, backed)
    }
}
