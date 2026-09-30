package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiFailureUiText
import com.example.englishlearning.imagegen.DrawingPromptPolicy
import com.example.englishlearning.imagegen.ImageStudioNotConfiguredReason
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AI 生图功能页：主题输入（数据位，100 上限）、两段式按钮、两种出站确认弹层、
 * 生成结果渲染与未配置/失败常量文案。
 */
@RunWith(AndroidJUnit4::class)
class ImageStudioScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun idleBlankSubjectDisablesPromptGenerationAndBackReportsCallback() {
        var backed = 0
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.Idle,
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = { backed++ },
            )
        }

        composeRule.onNodeWithTag("image_studio_subject_input").assertExists()
        composeRule.onNodeWithTag("image_studio_generate_prompt").assertIsNotEnabled()
        composeRule.onNodeWithTag("image_studio_cost_hint").assertExists()
        composeRule.onNodeWithTag("image_studio_back").performClick()
        composeRule.waitForIdle()
        assertEquals(1, backed)
    }

    @Test
    fun typingASubjectEnablesPromptGenerationAndReportsIt() {
        var subject: String? = null
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.Idle,
                onGeneratePrompt = { subject = it }, onGenerateImage = {}, onConfirm = {}, onBack = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_subject_input").performTextInput("苹果")
        composeRule.onNodeWithTag("image_studio_generate_prompt").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals("苹果", subject)
    }

    @Test
    fun subjectInputIsCappedAtThePolicyLimit() {
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.Idle,
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_subject_input").performTextInput("a".repeat(150))
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            "${DrawingPromptPolicy.maxSubjectLength}/${DrawingPromptPolicy.maxSubjectLength}",
            substring = true,
        ).assertExists()
    }

    @Test
    fun promptReadyShowsThePromptAndTheImageButton() {
        var generated = 0
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.PromptReady("a watercolor apple"),
                onGeneratePrompt = {}, onGenerateImage = { generated++ }, onConfirm = {}, onBack = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_prompt").assertExists()
        composeRule.onNodeWithText("a watercolor apple").assertExists()
        composeRule.onNodeWithTag("image_studio_image_cost_hint").assertExists()
        composeRule.onNodeWithTag("image_studio_generate_image").performClick()
        composeRule.waitForIdle()
        assertEquals(1, generated)
    }

    @Test
    fun textConfirmationDialogNamesTheHostAndDeclineReportsFalse() {
        var answer: Boolean? = null
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.AwaitingConfirmation("api.example.com", ImageStudioConfirmationStage.Prompt),
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = { answer = it }, onBack = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_outbound_confirmation_text")
            .assertTextContains("api.example.com", substring = true)
        composeRule.onNodeWithTag("image_studio_decline_outbound").performClick()
        composeRule.waitForIdle()
        assertEquals(false, answer)
    }

    @Test
    fun imageConfirmationDialogWarnsAboutPerImageBilling() {
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.AwaitingConfirmation("image.example.com", ImageStudioConfirmationStage.Image),
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_outbound_confirmation_text")
            .assertTextContains("按张计费", substring = true)
        composeRule.onNodeWithTag("image_studio_confirm_outbound").assertExists()
    }

    @Test
    fun progressStatesAreVisible() {
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.DraftingPrompt,
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {},
            )
        }
        composeRule.onNodeWithTag("image_studio_drafting").assertExists()
        // 生成中主题输入与按钮都禁用
        composeRule.onNodeWithTag("image_studio_subject_input").assertIsNotEnabled()
    }

    @Test
    fun imageReadyRendersTheDecodedCacheFile() {
        val cacheFile = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "image-studio-screen-test.png",
        )
        cacheFile.writeBytes(Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64))

        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.ImageReady(cacheFile.absolutePath),
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_result").assertExists()
    }

    @Test
    fun unreadableCacheFileFallsBackToAConstantMessage() {
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.ImageReady("/does/not/exist.png"),
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_result_unreadable").assertExists()
    }

    @Test
    fun notConfiguredCardUsesTheFixedCopy() {
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.NotConfigured(ImageStudioNotConfiguredReason.NoDefaultProfile),
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {},
            )
        }
        composeRule.onNodeWithTag("image_studio_not_configured")
            .assertTextContains("设置 · AI", substring = true)
    }

    @Test
    fun failureCardOnlyShowsTheMappedConstantCopy() {
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.Failed(AiFailure.InvalidResponse),
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {},
            )
        }
        composeRule.onNodeWithTag("image_studio_failure")
            .assertTextContains(AiFailureUiText.InvalidResponse.message, substring = true)
    }

    private companion object {
        /** 1×1 透明 PNG。 */
        const val ONE_PIXEL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    }
}
