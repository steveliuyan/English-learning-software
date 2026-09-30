package com.example.englishlearning.ui

import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.DefaultImageProfileResult
import com.example.englishlearning.ai.DefaultImageProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResponse
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpResult
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.imagegen.DrawingPromptUseCase
import com.example.englishlearning.imagegen.GeneratedImageStore
import com.example.englishlearning.imagegen.ImageGenerationUseCase
import com.example.englishlearning.imagegen.ImageStudioNotConfiguredReason
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 生图工作台状态机：主题 →（文本确认，按域名一次）→ 提示词 →（图片确认，每次）→ 图片落盘。
 * 两条确认语义不同，是本状态机最容易被写错的地方，所以每一条都有独立用例。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageStudioViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val directory = Files.createTempDirectory("image-studio-vm").toFile()

    private val textProfile = AiProfile(
        profileId = "p-text",
        displayName = "文本",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "gpt-x",
        capabilities = setOf(AiCapability.Text),
        secretReference = SecretReference("ai-profile-p-text"),
    )

    private val imageProfile = AiProfile(
        profileId = "p-img",
        displayName = "生图",
        websiteUrl = "https://example.com",
        endpoint = "https://image.example.com/v1",
        model = "img-x",
        capabilities = setOf(AiCapability.ImageGeneration),
        secretReference = SecretReference("ai-profile-p-img"),
    )

    private class FakeSecretStore : SecretStore {
        override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
        override fun read(reference: SecretReference) = Result.success("k3y".toCharArray())
        override fun delete(reference: SecretReference) = Result.success(Unit)
        override fun has(reference: SecretReference) = Result.success(true)
    }

    private class RecordingTransport(
        var result: AiHttpResult = AiHttpResult.Responded(AiHttpResponse(200, "{}")),
    ) : AiHttpTransport {
        val requests = mutableListOf<AiHttpRequest>()
        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            requests += request
            return result
        }
    }

    private class RecordingAudioTransport(var result: AudioHttpResult = AudioHttpResult.Success(byteArrayOf())) : AudioHttpTransport {
        val requests = mutableListOf<AudioHttpRequest>()
        override suspend fun send(request: AudioHttpRequest): AudioHttpResult {
            requests += request
            return result
        }
    }

    private val promptResponse = AiHttpResult.Responded(
        AiHttpResponse(200, """{"choices":[{"message":{"content":"a watercolor apple"}}]}"""),
    )

    private class Fixture(
        val viewModel: ImageStudioViewModel,
        val promptTransport: RecordingTransport,
        val imageTransport: RecordingTransport,
        val audioTransport: RecordingAudioTransport,
    )

    private fun fixture(
        promptResult: DefaultTextProfileResult = DefaultTextProfileResult.Selected(textProfile),
        imageResult: DefaultImageProfileResult = DefaultImageProfileResult.Selected(imageProfile),
        imageTransportResult: AiHttpResult = AiHttpResult.Responded(
            // 1x1 PNG 的最小 base64（含 PNG 魔数，扩展名判定走真路径）
            AiHttpResponse(200, """{"data":[{"b64_json":"${PNG_BASE64}"}]}"""),
        ),
    ): Fixture {
        val secrets = AiProfileSecretUseCase(FakeSecretStore())
        val promptTransport = RecordingTransport(promptResponse)
        val imageTransport = RecordingTransport(imageTransportResult)
        val audioTransport = RecordingAudioTransport()
        val viewModel = ImageStudioViewModel(
            drawPrompt = DrawingPromptUseCase(
                defaultTextProfile = DefaultTextProfileResolver { promptResult },
                secrets = secrets,
                transport = promptTransport,
            ),
            generateImage = ImageGenerationUseCase(
                defaultImageProfile = DefaultImageProfileResolver { imageResult },
                secrets = secrets,
                transport = imageTransport,
            ),
            store = GeneratedImageStore(audioTransport, directory),
        )
        return Fixture(viewModel, promptTransport, imageTransport, audioTransport)
    }

    @Test
    fun promptGenerationAsksForConfirmationBeforeAnyByteLeaves() = runTest {
        val f = fixture()

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()

        assertEquals(
            ImageStudioUiState.AwaitingConfirmation("api.example.com", ImageStudioConfirmationStage.Prompt),
            f.viewModel.uiState.value,
        )
        assertEquals(0, f.promptTransport.requests.size)
    }

    @Test
    fun confirmingTheTextHostProducesTheEditablePrompt() = runTest {
        val f = fixture()

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()

        assertEquals(ImageStudioUiState.PromptReady("a watercolor apple"), f.viewModel.uiState.value)
        assertEquals(1, f.promptTransport.requests.size)
    }

    @Test
    fun theTextConfirmationIsReusedForTheSameHost() = runTest {
        val f = fixture()

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        f.viewModel.generatePrompt("香蕉")
        advanceUntilIdle()

        assertEquals(ImageStudioUiState.PromptReady("a watercolor apple"), f.viewModel.uiState.value)
        assertEquals(2, f.promptTransport.requests.size) // 第二次没有再弹确认，直接出站
    }

    @Test
    fun decliningTheTextConfirmationReturnsToIdle() = runTest {
        val f = fixture()

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(false)
        advanceUntilIdle()

        assertEquals(ImageStudioUiState.Idle, f.viewModel.uiState.value)
        assertEquals(0, f.promptTransport.requests.size)
    }

    @Test
    fun imageGenerationAsksAgainEvenAfterTheTextHostWasConfirmed() = runTest {
        val f = fixture()

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        f.viewModel.generateImage()
        advanceUntilIdle()

        assertEquals(
            ImageStudioUiState.AwaitingConfirmation("image.example.com", ImageStudioConfirmationStage.Image),
            f.viewModel.uiState.value,
        )
        assertEquals(0, f.imageTransport.requests.size, "确认前生图请求一个字节都不能出去")
    }

    @Test
    fun decliningTheImageConfirmationGoesBackToThePrompt() = runTest {
        val f = fixture()

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        f.viewModel.generateImage()
        advanceUntilIdle()
        f.viewModel.confirm(false)
        advanceUntilIdle()

        assertEquals(ImageStudioUiState.PromptReady("a watercolor apple"), f.viewModel.uiState.value)
        assertEquals(0, f.imageTransport.requests.size)
    }

    @Test
    fun confirmingTheImageProducesACachedFileFromBase64() = runTest {
        val f = fixture()

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        f.viewModel.generateImage()
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertTrue(state is ImageStudioUiState.ImageReady, "expected ImageReady but was $state")
        val file = java.io.File((state as ImageStudioUiState.ImageReady).filePath)
        assertTrue(file.exists() && file.parentFile.absolutePath == directory.absolutePath)
        assertEquals(1, f.imageTransport.requests.size)
    }

    @Test
    fun aUrlImageIsDownloadedAndCached() = runTest {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 7, 7, 7)
        val f = fixture(
            imageTransportResult = AiHttpResult.Responded(
                AiHttpResponse(200, """{"data":[{"url":"https://cdn.example.com/img.png"}]}"""),
            ),
        )
        f.audioTransport.result = AudioHttpResult.Success(bytes.copyOf())

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        f.viewModel.generateImage()
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertTrue(state is ImageStudioUiState.ImageReady, "expected ImageReady but was $state")
        val file = java.io.File((state as ImageStudioUiState.ImageReady).filePath)
        assertTrue(bytes.contentEquals(file.readBytes()))
    }

    @Test
    fun undecodableImageDataSurfacesAsInvalidResponse() = runTest {
        val f = fixture(
            imageTransportResult = AiHttpResult.Responded(
                AiHttpResponse(200, """{"data":[{"b64_json":"!!!not-base64!!!"}]}"""),
            ),
        )

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        f.viewModel.generateImage()
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()

        assertEquals(ImageStudioUiState.Failed(AiFailure.InvalidResponse), f.viewModel.uiState.value)
    }

    @Test
    fun notConfiguredProfileIsSurfacedWithItsReason() = runTest {
        val f = fixture(promptResult = DefaultTextProfileResult.NoSelection)

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()

        assertEquals(
            ImageStudioUiState.NotConfigured(ImageStudioNotConfiguredReason.NoDefaultProfile),
            f.viewModel.uiState.value,
        )
    }

    @Test
    fun failedPromptGenerationSurfacesTheFailure() = runTest {
        val f = fixture()
        f.promptTransport.result = AiHttpResult.NetworkUnavailable

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()

        assertEquals(ImageStudioUiState.Failed(AiFailure.NetworkUnavailable), f.viewModel.uiState.value)
    }

    @Test
    fun resetReturnsToIdleAndForgetsTheConfirmedHost() = runTest {
        val f = fixture()

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        f.viewModel.reset()
        advanceUntilIdle()
        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()

        assertEquals(
            ImageStudioUiState.AwaitingConfirmation("api.example.com", ImageStudioConfirmationStage.Prompt),
            f.viewModel.uiState.value,
        )
    }

    private companion object {
        /** 1×1 透明 PNG。 */
        const val PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    }
}
