package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiOutboundGrantStore
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

    /** 出站确认：确认前零字节出网；[host] 是确认目标。本屏与查词问答共享一份会话内域名台账
     *  （决策 2026-11-05 D-1：这两屏按域名确认一次；文章生成、拍照翻译、生图出图仍逐次确认）。 */
    data class NeedsOutboundConfirmation(val host: String) : SentenceAnalysisUiState

    data class NotConfigured(val reason: SentenceAnalysisNotConfiguredReason) : SentenceAnalysisUiState

    data class Failed(val failure: AiFailure) : SentenceAnalysisUiState
}

/**
 * 长难句分析状态机。确认重放沿用同一句；[reset] 在重新进入功能页时清掉上一句的
 * 结果——不允许把 A 句的分析显示到 B 句的屏幕上。
 *
 * **`reset` 不清 [outboundGrants]，这是有意的**：清了就等于「进出一次页面问一次」，
 * 决策 D-1 放宽的那点收益当场还回去。台账本身只活在本进程（`AiOutboundGrantStore` 拿不到
 * Context），重启后仍会重新问一次。
 */
@HiltViewModel
class SentenceAnalysisViewModel @Inject constructor(
    private val analyze: SentenceAnalysisUseCase,
    private val outboundGrants: AiOutboundGrantStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SentenceAnalysisUiState>(SentenceAnalysisUiState.Idle)
    val uiState: StateFlow<SentenceAnalysisUiState> = _uiState

    private var pendingSentence: String? = null

    fun analyze(sentence: String, profileId: String) {
        pendingSentence = sentence
        _uiState.value = SentenceAnalysisUiState.Analyzing
        runAnalysis(sentence, confirmedTextHosts = outboundGrants.confirmedHosts())
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
        // 记账发生在「用户点了确认」这一刻，而不是请求成功之后：确认陈述的是「这段文本和密钥
        // 会发给这个域名」这个出站动作，动作失败不改变用户已经授权过它这件事。
        // 代价是一次失败的网络请求会换来一次免确认重试——比「弹两次同样的确认」更接近用户预期。
        outboundGrants.confirm(pending.host)
        _uiState.value = SentenceAnalysisUiState.Analyzing
        runAnalysis(sentence, confirmedTextHosts = setOf(pending.host))
    }

    fun reset() {
        pendingSentence = null
        _uiState.value = SentenceAnalysisUiState.Idle
    }

    private fun runAnalysis(sentence: String, confirmedTextHosts: Set<String>) {
        viewModelScope.launch {
            try {
                when (val result = analyze.analyze(sentence, confirmedTextHosts)) {
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
