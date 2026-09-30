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

    fun generatePrompt(subject: String) {
        pendingSubject = subject
        _uiState.value = ImageStudioUiState.DraftingPrompt
        runPrompt(subject, confirmedTextHost)
    }

    fun generateImage() {
        val prompt = pendingPrompt ?: run {
            _uiState.value = ImageStudioUiState.Idle
            return
        }
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

    fun reset() {
        pendingSubject = null
        pendingPrompt = null
        confirmedTextHost = null
        _uiState.value = ImageStudioUiState.Idle
    }

    private fun runPrompt(subject: String, confirmedTextHost: String?) {
        viewModelScope.launch {
            try {
                when (val result = drawPrompt.generate(subject, confirmedTextHost)) {
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
        viewModelScope.launch {
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
