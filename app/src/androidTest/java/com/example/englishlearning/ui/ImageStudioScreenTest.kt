package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = { backed++ }, onOpenAiSettings = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_subject_input").assertExists()
        composeRule.onNodeWithTag("image_studio_generate_prompt").assertIsNotEnabled()
        composeRule.onNodeWithTag("image_studio_cost_hint").assertExists()
        // 批 4 Task 6：返回位归共享页头（旧 `image_studio_back` 药片退役），可及名固定「返回」。
        composeRule.onNodeWithTag("image_studio_back").assertDoesNotExist()
        composeRule.onNodeWithTag("app_screen_header").assertExists()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        assertEquals(1, backed)
    }

    @Test
    fun typingASubjectEnablesPromptGenerationAndReportsIt() {
        var subject: String? = null
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.Idle,
                onGeneratePrompt = { subject = it }, onGenerateImage = {}, onConfirm = {}, onBack = {}, onOpenAiSettings = {},
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
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {}, onOpenAiSettings = {},
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
                onGeneratePrompt = {}, onGenerateImage = { generated++ }, onConfirm = {}, onBack = {}, onOpenAiSettings = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_prompt").assertExists()
        composeRule.onNodeWithText("a watercolor apple").assertExists()
        composeRule.onNodeWithTag("image_studio_image_cost_hint").assertExists()
        composeRule.onNodeWithTag("image_studio_generate_image").performClick()
        composeRule.waitForIdle()
        assertEquals(1, generated)
    }

    /**
     * 批 4 Task 6（spec §5「AI 生图 | 画面描述可编辑后再生成」）：模型稿先落进输入框，用户改字之后
     * 点「生成图片」，报给宿主的是**改过的那一句**——可编辑却把原稿送出去，等于让界面当面说谎。
     *
     * 与 JVM 侧 `ImageStudioLayoutContractTest` 第 2 条互补：那条只证「tag 长在可编辑输入框上」，
     * 这条证「改过的字真的从键上流出去」。
     */
    @Test
    fun theDescriptionIsEditableAndTheEditedWordsAreWhatGoOut() {
        var reported: String? = null
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.PromptReady("a watercolor apple"),
                onGeneratePrompt = {}, onGenerateImage = { reported = it }, onConfirm = {}, onBack = {}, onOpenAiSettings = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_prompt").assertTextContains("a watercolor apple", substring = true)
        // 吸底条不许压住输入：任何滚动内容都能被滚到完整可见（大字号 2.0 下同样成立，见计划 Task 8 Step 3 ④）。
        composeRule.onNodeWithTag("image_studio_prompt").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("image_studio_generate_image").assertIsDisplayed()
        composeRule.onNodeWithTag("image_studio_prompt").performTextReplacement("a hand edited watercolor apple")
        composeRule.onNodeWithTag("image_studio_generate_image").performClick()
        composeRule.waitForIdle()
        assertEquals("a hand edited watercolor apple", reported)
    }

    @Test
    fun textConfirmationDialogNamesTheHostAndDeclineReportsFalse() {
        var answer: Boolean? = null
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.AwaitingConfirmation("api.example.com", ImageStudioConfirmationStage.Prompt),
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = { answer = it }, onBack = {}, onOpenAiSettings = {},
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
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {}, onOpenAiSettings = {},
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
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {}, onOpenAiSettings = {},
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
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {}, onOpenAiSettings = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_result").assertExists()
    }

    @Test
    fun unreadableCacheFileFallsBackToAConstantMessage() {
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.ImageReady("/does/not/exist.png"),
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {}, onOpenAiSettings = {},
            )
        }

        composeRule.onNodeWithTag("image_studio_result_unreadable").assertExists()
    }

    @Test
    fun notConfiguredCardUsesTheFixedCopy() {
        composeRule.setContent {
            ImageStudioScreen(
                state = ImageStudioUiState.NotConfigured(ImageStudioNotConfiguredReason.NoDefaultProfile),
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {}, onOpenAiSettings = {},
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
                onGeneratePrompt = {}, onGenerateImage = {}, onConfirm = {}, onBack = {}, onOpenAiSettings = {},
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
