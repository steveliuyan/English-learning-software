package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiOutboundConfirmation
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.imagegen.DrawingPromptResult
import com.example.englishlearning.imagegen.DrawingPromptUseCase
import com.example.englishlearning.imagegen.GeneratedImageStore
import com.example.englishlearning.imagegen.ImageGenerationResult
import com.example.englishlearning.imagegen.ImageGenerationUseCase
import com.example.englishlearning.imagegen.ImageStudioNotConfiguredReason
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 出站确认的两个阶段。两者的确认语义不同，所以用枚举而不是布尔量区分。 */
enum class ImageStudioConfirmationStage {
    /** 生成提示词：文本载荷，按域名确认一次。 */
    Prompt,

    /** 生图：图片载荷，每次都要确认。 */
    Image,
}

sealed interface ImageStudioUiState {
    /** 初始态：主题输入可用。 */
    data object Idle : ImageStudioUiState

    data object DraftingPrompt : ImageStudioUiState

    /** 提示词就绪（模型输出，只作展示与出站载荷，不出现在屏幕签名里）。 */
    data class PromptReady(val prompt: String) : ImageStudioUiState

    data class AwaitingConfirmation(val host: String, val stage: ImageStudioConfirmationStage) : ImageStudioUiState

    data object GeneratingImage : ImageStudioUiState

    /** 图片已落盘到 cache；屏幕按路径渲染。 */
    data class ImageReady(val filePath: String) : ImageStudioUiState

    data class NotConfigured(val reason: ImageStudioNotConfiguredReason) : ImageStudioUiState

    data class Failed(val failure: AiFailure) : ImageStudioUiState
}

/**
 * 生图工作台状态机：主题 →（文本确认，按域名一次）→ 提示词 →（图片确认，每次）→ 图片落盘。
 *
 * 提示词与主题只留在内存里，不落库；[reset] 在离开功能页时把它们连同「已确认域名」
 * 一起清掉——换用户/换页面后不该沿用上一个人的确认。
 */
@HiltViewModel
class ImageStudioViewModel @Inject constructor(
    private val drawPrompt: DrawingPromptUseCase,
    private val generateImage: ImageGenerationUseCase,
    private val store: GeneratedImageStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ImageStudioUiState>(ImageStudioUiState.Idle)
    val uiState: StateFlow<ImageStudioUiState> = _uiState

    private var pendingSubject: String? = null
    private var pendingPrompt: String? = null
    private var confirmedTextHost: String? = null

    /**
     * 两枚在飞协程各持一份句柄（批 C-4）：文本起草与图片生成是两次独立出站，
     * 「取消」必须知道自己停的是哪一发——同一时刻两发同时在飞的话，界面显示的哪一张由竞态决定。
     */
    private var promptJob: Job? = null
    private var imageJob: Job? = null

    fun generatePrompt(subject: String) {
        pendingSubject = subject
        _uiState.value = ImageStudioUiState.DraftingPrompt
        runPrompt(subject, confirmedTextHost)
    }

    /**
     * 生成图片。[promptOverride] 是「画面描述可编辑」的出口（批 4 Task 6，计划对账 6）：
     * 屏内改过的描述从这里进来；`null` 或纯空白退回模型原稿——空描述出站等于拿一次按张计费的
     * 请求换一张随机图，屏侧禁用是第一道防线，这里是第二道。
     *
     * 收下的这句同时覆盖 [pendingPrompt]：紧接着的出站确认（[confirm] 的 `Image` 分支）读的就是
     * [pendingPrompt]，不覆盖的话「确认」会把模型原句发出去，用户改过的字在最后一步被悄悄丢弃。
     */
    fun generateImage(promptOverride: String? = null) {
        val prompt = promptOverride?.trim()?.takeIf { it.isNotEmpty() } ?: pendingPrompt ?: run {
            _uiState.value = ImageStudioUiState.Idle
            return
        }
        pendingPrompt = prompt
        _uiState.value = ImageStudioUiState.GeneratingImage
        runImage(prompt, answer = null)
    }

    fun confirm(confirmed: Boolean) {
        val current = _uiState.value as? ImageStudioUiState.AwaitingConfirmation ?: return
        when (current.stage) {
            ImageStudioConfirmationStage.Prompt -> {
                if (!confirmed) {
                    _uiState.value = ImageStudioUiState.Idle
                    return
                }
                val subject = pendingSubject ?: run {
                    _uiState.value = ImageStudioUiState.Idle
                    return
                }
                confirmedTextHost = current.host
                _uiState.value = ImageStudioUiState.DraftingPrompt
                runPrompt(subject, current.host)
            }

            ImageStudioConfirmationStage.Image -> {
                val prompt = pendingPrompt ?: run {
                    _uiState.value = ImageStudioUiState.Idle
                    return
                }
                if (!confirmed) {
                    _uiState.value = ImageStudioUiState.PromptReady(prompt)
                    return
                }
                _uiState.value = ImageStudioUiState.GeneratingImage
                runImage(prompt, AiOutboundConfirmation(current.host, AiPayloadKind.Image, confirmed = true))
            }
        }
    }

    /**
     * 取消正在等待的那一发（批 C-4）。落点按**用户手里已经有什么**分档
     * （决策 2026-11-07 §3 决定 3 第 2 条的例外）：
     *
     *  - 起草描述在飞 → 回 `Idle`：此刻用户只说过一个主题词，没有已付费的东西可保留。
     *  - 生成图片在飞 → 回 `PromptReady(那份描述)`：描述是一次已付费的文本出站换来的结果，
     *    取消一张没等到的图不该把它一起销毁，否则想再出图就得再付一次文本调用。
     *
     * 只停在飞的两种状态下有事可做；确认框、结果态按取消是空操作（那时根本没有在飞的请求）。
     */
    fun cancelGeneration() {
        when (_uiState.value) {
            ImageStudioUiState.DraftingPrompt -> {
                promptJob?.cancel()
                promptJob = null
                _uiState.value = ImageStudioUiState.Idle
            }

            ImageStudioUiState.GeneratingImage -> {
                imageJob?.cancel()
                imageJob = null
                _uiState.value = pendingPrompt?.let { ImageStudioUiState.PromptReady(it) } ?: ImageStudioUiState.Idle
            }

            else -> Unit
        }
    }

    fun reset() {
        promptJob?.cancel()
        imageJob?.cancel()
        promptJob = null
        imageJob = null
        pendingSubject = null
        pendingPrompt = null
        confirmedTextHost = null
        _uiState.value = ImageStudioUiState.Idle
    }

    private fun runPrompt(subject: String, confirmedTextHost: String?) {
        promptJob?.cancel()
        promptJob = viewModelScope.launch {
            try {
                when (val result = drawPrompt.generate(subject, setOfNotNull(confirmedTextHost))) {
                    is DrawingPromptResult.Draft -> {
                        pendingPrompt = result.prompt
                        _uiState.value = ImageStudioUiState.PromptReady(result.prompt)
                    }
                    is DrawingPromptResult.NeedsConfirmation ->
                        _uiState.value = ImageStudioUiState.AwaitingConfirmation(result.host, ImageStudioConfirmationStage.Prompt)
                    is DrawingPromptResult.NotConfigured ->
                        _uiState.value = ImageStudioUiState.NotConfigured(result.reason)
                    is DrawingPromptResult.Failed -> _uiState.value = ImageStudioUiState.Failed(result.failure)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.value = ImageStudioUiState.Idle
            }
        }
    }

    private fun runImage(prompt: String, answer: AiOutboundConfirmation?) {
        imageJob?.cancel()
        imageJob = viewModelScope.launch {
            try {
                when (val result = generateImage.generate(prompt, answer)) {
                    is ImageGenerationResult.Generated -> persist(result)
                    is ImageGenerationResult.NeedsConfirmation ->
                        _uiState.value = ImageStudioUiState.AwaitingConfirmation(result.host, ImageStudioConfirmationStage.Image)
                    is ImageGenerationResult.NotConfigured ->
                        _uiState.value = ImageStudioUiState.NotConfigured(result.reason)
                    is ImageGenerationResult.Failed -> _uiState.value = ImageStudioUiState.Failed(result.failure)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.value = ImageStudioUiState.Idle
            }
        }
    }

    private suspend fun persist(generated: ImageGenerationResult.Generated) {
        val failure = store.persist(generated.image).fold(
            onSuccess = { file ->
                _uiState.value = ImageStudioUiState.ImageReady(file.absolutePath)
                null
            },
            onFailure = { (it as? AiException)?.failure ?: AiFailure.InvalidResponse },
        )
        if (failure != null) _uiState.value = ImageStudioUiState.Failed(failure)
    }
}
