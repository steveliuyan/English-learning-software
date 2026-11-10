package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.spelling.RecordSpellingFeedbackUseCase
import com.example.englishlearning.learning.spelling.RecordSpellingResult
import com.example.englishlearning.learning.spelling.SpellingPrompt
import com.example.englishlearning.learning.spelling.SpellingVerdict
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 一道拼写题落地后的排期回执。
 *
 * 界面拿它在一题之下补一句真话：这题写进去了、下次什么时候再问；或者没写进去、排期没动。
 * 只有 [Idle] / [Saving] 没有可说的内容，[Recorded.whenLabel] 一定是从调度器写入的
 * `nextReviewAt` 反推出来的（复用 [receiptWhenLabel]），不写死「3 天后」这类数字。
 */
sealed interface SpellingScheduleState {
    /** 还没有作答，或刚换到下一题。 */
    data object Idle : SpellingScheduleState

    /** 正在写本地事件表。 */
    data object Saving : SpellingScheduleState

    /** 已记入排期，[whenLabel] 是这个词下次被问的时间。 */
    data class Recorded(val whenLabel: String) : SpellingScheduleState

    /** 本地存储不可用：这题的排期没有改动。 */
    data object Failed : SpellingScheduleState
}

/**
 * 拼写测验的排期写入端（2026-11-09）。
 *
 * 这一层只做一件事：把每题的判分交给 [RecordSpellingFeedbackUseCase]，再把结果译成一句可读的回执。
 * 会话本身（第几题、已拼对几个）仍留在 [SpellingQuizScreen] 里的纯逻辑对象上——那是界面状态，
 * 这里不重复持有。
 *
 * 快速连答时后一次的结果必须盖住前一次：用 [sequence] 发号，回来时号不对就丢弃，
 * 免得慢的那次把新一题的「已记入」改回旧题的回执；换轮/清屏时同样发号作废旧的在途写入。
 */
@HiltViewModel
class SpellingQuizViewModel @Inject constructor(
    private val recordSpellingFeedback: RecordSpellingFeedbackUseCase,
    private val clock: ClockProvider,
) : ViewModel() {

    private val mutable = MutableStateFlow<SpellingScheduleState>(SpellingScheduleState.Idle)
    val schedule: StateFlow<SpellingScheduleState> = mutable.asStateFlow()

    private var sequence = 0
    private var inFlight: Job? = null

    fun record(profileId: String, prompt: SpellingPrompt, verdict: SpellingVerdict) {
        val ticket = ++sequence
        inFlight?.cancel()
        mutable.value = SpellingScheduleState.Saving
        inFlight = viewModelScope.launch {
            val result = recordSpellingFeedback.record(
                profileId = profileId,
                prompt = prompt,
                verdict = verdict,
            )
            if (ticket != sequence) return@launch
            mutable.value = when (result) {
                is RecordSpellingResult.Recorded ->
                    SpellingScheduleState.Recorded(
                        receiptWhenLabel(result.nextReviewAt, clock.instant()),
                    )

                RecordSpellingResult.StorageUnavailable -> SpellingScheduleState.Failed
            }
        }
    }

    /** 新一轮开始或离开本屏：作废在途写入，回执清空。 */
    fun clear() {
        sequence += 1
        inFlight?.cancel()
        mutable.value = SpellingScheduleState.Idle
    }
}
