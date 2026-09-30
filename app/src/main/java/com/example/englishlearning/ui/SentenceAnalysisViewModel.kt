package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.sentence.SentenceAnalysisNotConfiguredReason
import com.example.englishlearning.sentence.SentenceAnalysisResult
import com.example.englishlearning.sentence.SentenceAnalysisUseCase
import com.example.englishlearning.sentence.SentenceSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SentenceAnalysisUiState {
    /** 初始态：句子输入框可用。 */
    data object Idle : SentenceAnalysisUiState

    data object Analyzing : SentenceAnalysisUiState

    /** 一次分析的结构化结果。 */
    data class Analyzed(val segments: List<SentenceSegment>) : SentenceAnalysisUiState

    /** 出站确认：与文章生成/词问答共用同一条安全约束，确认前零字节出网；[host] 是确认目标。 */
    data class NeedsOutboundConfirmation(val host: String) : SentenceAnalysisUiState

    data class NotConfigured(val reason: SentenceAnalysisNotConfiguredReason) : SentenceAnalysisUiState

    data class Failed(val failure: AiFailure) : SentenceAnalysisUiState
}

/**
 * 长难句分析状态机。确认重放沿用同一句；[reset] 在重新进入功能页时清掉上一句的
 * 结果——不允许把 A 句的分析显示到 B 句的屏幕上。
 */
@HiltViewModel
class SentenceAnalysisViewModel @Inject constructor(
    private val analyze: SentenceAnalysisUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SentenceAnalysisUiState>(SentenceAnalysisUiState.Idle)
    val uiState: StateFlow<SentenceAnalysisUiState> = _uiState

    private var pendingSentence: String? = null

    fun analyze(sentence: String, profileId: String) {
        pendingSentence = sentence
        _uiState.value = SentenceAnalysisUiState.Analyzing
        runAnalysis(sentence, confirmedTextHost = null)
    }

    fun confirmOutbound(confirmed: Boolean) {
        val pending = _uiState.value as? SentenceAnalysisUiState.NeedsOutboundConfirmation
        if (pending == null) return
        if (!confirmed) {
            _uiState.value = SentenceAnalysisUiState.Idle
            return
        }
        val sentence = pendingSentence ?: run {
            _uiState.value = SentenceAnalysisUiState.Idle
            return
        }
        _uiState.value = SentenceAnalysisUiState.Analyzing
        runAnalysis(sentence, confirmedTextHost = pending.host)
    }

    fun reset() {
        pendingSentence = null
        _uiState.value = SentenceAnalysisUiState.Idle
    }

    private fun runAnalysis(sentence: String, confirmedTextHost: String?) {
        viewModelScope.launch {
            try {
                when (val result = analyze.analyze(sentence, confirmedTextHost)) {
                    is SentenceAnalysisResult.Analyzed ->
                        _uiState.value = SentenceAnalysisUiState.Analyzed(result.segments)
                    is SentenceAnalysisResult.NeedsConfirmation ->
                        _uiState.value = SentenceAnalysisUiState.NeedsOutboundConfirmation(result.host)
                    is SentenceAnalysisResult.Failed -> _uiState.value = SentenceAnalysisUiState.Failed(result.failure)
                    is SentenceAnalysisResult.NotConfigured -> _uiState.value = SentenceAnalysisUiState.NotConfigured(result.reason)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.value = SentenceAnalysisUiState.Idle
            }
        }
    }
}
