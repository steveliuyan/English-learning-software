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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
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

        /**
         * 按**第几发**挂起（批 C-4 的取消判据）：立即返回的假传输上 `advanceUntilIdle()` 之后协程早跑完了，
         * VM 不持句柄也能通过取消用例——那种绿什么都没证明。取号而不是全局一枚门，是为了把
         * 「旧发被顶掉」与「新发落地」分成两件事量（生图两臂各有一条）。
         */
        var hangOn: ((call: Int) -> Deferred<AiHttpResult>?)? = null

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            requests += request
            hangOn?.invoke(requests.size)?.await()?.let { return it }
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

    /**
     * 批 4 Task 6（计划对账 6）：画面描述改成可编辑之后，**出站的是用户改过的那一句**。
     *
     * 这一条钉的是本批唯一越出「只动呈现层」的改动——[ImageStudioViewModel.generateImage] 收
     * `promptOverride`。屏内能改而送出去的还是模型原句，等于用户改了白改：不但白花一次文本调用的钱，
     * 界面还当着他的面说谎。所以判据落在**真请求体**上，不落在状态字段上。
     */
    @Test
    fun theEditedPromptIsWhatActuallyGoesOut() = runTest {
        val f = fixture()

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        f.viewModel.generateImage("a hand edited watercolor apple")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()

        assertEquals(1, f.imageTransport.requests.size)
        val body = f.imageTransport.requests.single().body
        assertTrue(
            body.contains("a hand edited watercolor apple"),
            "生图请求体里必须是用户改过的那一句（实际请求体：$body）",
        )
        assertTrue(
            !body.contains("\"a watercolor apple\""),
            "模型原句不得冒充用户改过的描述（实际请求体：$body）",
        )
    }

    /**
     * 改空了就退回模型原稿：屏侧把「生成图片」按空白禁用，这一层是第二道防线——
     * 空描述出站等于拿一次按张计费的请求换一张随机图。
     */
    @Test
    fun aBlankEditFallsBackToTheModelDraft() = runTest {
        val f = fixture()

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        f.viewModel.generateImage("   ")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()

        assertEquals(1, f.imageTransport.requests.size)
        val body = f.imageTransport.requests.single().body
        assertTrue(
            body.contains("\"a watercolor apple\""),
            "改空之后必须退回模型原稿，而不是把空白送出去（实际请求体：$body）",
        )
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

    // ---------- 批 C-4：两枚在飞各取消各的，取消不许把已付费的描述一起销毁 ----------

    /**
     * 起草描述在飞时取消 → 回 `Idle`，迟到的描述不回写，也不多要一次。
     *
     * `hang` 是这一批的前提：立即返回的假传输上协程早跑完了，VM 不持句柄也能「通过」取消用例。
     */
    @Test
    fun cancellingThePromptDraftDiscardsTheLateDescription() = runTest {
        val f = fixture()
        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        val gate = CompletableDeferred<AiHttpResult>()
        f.promptTransport.hangOn = { call -> if (call == 1) gate else null }
        f.viewModel.confirm(true)
        advanceUntilIdle()

        assertEquals(ImageStudioUiState.DraftingPrompt, f.viewModel.uiState.value)
        assertEquals(1, f.promptTransport.requests.size)

        f.viewModel.cancelGeneration()
        assertEquals(ImageStudioUiState.Idle, f.viewModel.uiState.value)

        gate.complete(promptResponse)
        advanceUntilIdle()

        assertEquals(ImageStudioUiState.Idle, f.viewModel.uiState.value, "取消之后迟到的描述不许把界面推成结果")
        assertEquals(1, f.promptTransport.requests.size, "取消不该再要一次描述")
    }

    /**
     * 生成图片在飞时取消 → 回**那份描述**（`PromptReady`），不是 `Idle`。
     *
     * 描述是一次已付费的文本出站换来的（决策 2026-11-07 §3 决定 3 第 2 条的例外）：把它连同图片一起销毁，
     * 用户想再出图就得再付一次文本调用。迟到的图片不许落盘——落一张没人等的图是白花钱，
     * 所以取消之后目录里必须还是一片干净。
     */
    @Test
    fun cancellingTheImageKeepsThePaidDescriptionAndDiscardsTheLatePicture() = runTest {
        val f = fixture()
        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        f.viewModel.generateImage()
        advanceUntilIdle()
        val gate = CompletableDeferred<AiHttpResult>()
        f.imageTransport.hangOn = { call -> if (call == 1) gate else null }
        f.viewModel.confirm(true)
        advanceUntilIdle()

        assertEquals(ImageStudioUiState.GeneratingImage, f.viewModel.uiState.value)
        assertEquals(1, f.imageTransport.requests.size)

        f.viewModel.cancelGeneration()
        assertEquals(
            ImageStudioUiState.PromptReady("a watercolor apple"),
            f.viewModel.uiState.value,
            "取消一次出图不该销毁已经花钱买到的画面描述",
        )

        gate.complete(f.imageTransport.result)
        advanceUntilIdle()

        assertTrue(f.viewModel.uiState.value !is ImageStudioUiState.ImageReady, "取消之后迟到的图片不许回写")
        assertTrue(
            directory.listFiles()?.none { it.name.endsWith(".png") } ?: true,
            "取消之后不许落盘",
        )
    }

    /**
     * 描述起草在飞时又点一次生成描述：旧的那发必须先被顶掉，不许两发同时在飞。
     *
     * 屏侧那枚主操作在 `busy` 时本来就禁用（connected 用例钉），这一条是 VM 侧的第二道防线：
     * 慢帧下两次点击都进了 VM，也只能有最后一发的结果落地。
     */
    @Test
    fun startingANewDraftCancelsThePreviousInFlightDescription() = runTest {
        val f = fixture()
        val first = CompletableDeferred<AiHttpResult>()
        val second = CompletableDeferred<AiHttpResult>()
        f.promptTransport.hangOn = { call -> when (call) { 1 -> first; 2 -> second; else -> null } }

        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        f.viewModel.confirm(true)
        advanceUntilIdle()
        assertEquals(1, f.promptTransport.requests.size)
        assertEquals(ImageStudioUiState.DraftingPrompt, f.viewModel.uiState.value)

        // 域名已确认过，第二发不再问第二次——正是这一发把上一发顶掉的那一档。
        f.viewModel.generatePrompt("青苹果")
        advanceUntilIdle()
        assertEquals(2, f.promptTransport.requests.size, "重新一发应当真的重发，而不是把在飞的那次当成已完成")

        first.complete(promptResponse)
        advanceUntilIdle()
        assertEquals(
            ImageStudioUiState.DraftingPrompt,
            f.viewModel.uiState.value,
            "被顶掉的那一发不许回写",
        )

        second.complete(
            AiHttpResult.Responded(AiHttpResponse(200, """{"choices":[{"message":{"content":"a green apple"}}]}""")),
        )
        advanceUntilIdle()
        assertEquals(ImageStudioUiState.PromptReady("a green apple"), f.viewModel.uiState.value)
    }

    /**
     * 离开功能页（`reset`）时在飞的那一发同样要停掉：清掉界面却留着请求，等于让用户回到本页时
     * 被一张他没在等的图片顶掉当前版面。
     */
    @Test
    fun leavingThePageStopsTheInFlightDescription() = runTest {
        val f = fixture()
        f.viewModel.generatePrompt("苹果")
        advanceUntilIdle()
        val gate = CompletableDeferred<AiHttpResult>()
        f.promptTransport.hangOn = { call -> if (call == 1) gate else null }
        f.viewModel.confirm(true)
        advanceUntilIdle()

        f.viewModel.reset()
        gate.complete(promptResponse)
        advanceUntilIdle()

        assertEquals(ImageStudioUiState.Idle, f.viewModel.uiState.value, "离开页面后迟到的描述不该把界面推成结果")
    }

    private companion object {
        /** 1×1 透明 PNG。 */
        const val PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    }
}
