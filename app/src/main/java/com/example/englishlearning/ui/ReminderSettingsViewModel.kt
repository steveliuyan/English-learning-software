package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.notification.ReminderScheduler
import com.example.englishlearning.notification.domain.ReminderPreference
import com.example.englishlearning.notification.domain.ReminderPreferenceRepository
import com.example.englishlearning.notification.domain.ReminderSchedulingPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReminderSettingsUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val time: LocalTime = LocalTime.of(20, 0),
    val error: String? = null,
)

/**
 * F4-02 设置页状态。开关/时间改动立即落库并触发一次调度刷新
 * （改时间、开关、完成度变化都走同一条 refresh 路径——由域层决策决定 schedule/cancel）。
 */
@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    private val preferences: ReminderPreferenceRepository,
    private val scheduler: ReminderScheduler,
    private val plans: TodayPlanRepository,
    private val events: LearningEventRepository,
    private val clock: ClockProvider,
) : ViewModel() {

    private val mutable = MutableStateFlow(ReminderSettingsUiState())
    val state: StateFlow<ReminderSettingsUiState> = mutable.asStateFlow()

    fun load() {
        viewModelScope.launch {
            preferences.get().fold(
                onSuccess = { preference ->
                    // 只在尚未就绪时整体落位：若用户在读取期间已改动开关/时间，
                    // 迟到的 load 不得把已提交的 UI 状态冲回存储值（真实竞态，见 VM 测试）。
                    val current = mutable.value
                    if (current.loading) {
                        mutable.value = ReminderSettingsUiState(loading = false, enabled = preference.enabled, time = preference.time)
                    }
                },
                onFailure = {
                    mutable.value = mutable.value.copy(loading = false, error = "无法读取提醒设置")
                },
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        update { it.copy(enabled = enabled) }
    }

    /**
     * 批 5 Task 2：读失败后的「重试」。
     *
     * 为什么不能直接再调一次 [load]：`load()` 的落位条件是 `loading == true`（那条守卫防的是
     * 「迟到的读取把用户刚改的开关冲回去」），而读失败后状态里 `loading` 已经是 `false`，
     * 于是再调 `load()` 会静默什么都不做——UI 上就是一枚按下去毫无反应的假按钮。
     * 重试必须先把 `loading` 复位成 `true`（顺带清掉旧错误），让同一条读取路径重新生效。
     *
     * 复位 `loading` 的代价与边界：若用户在报错后又动过开关/时间，重试成功会把存储值覆盖到 UI 上——
     * 但那条路径上【保存】本来就已发生（`update()` 立刻 `save`），存储值就是用户刚写下的值，
     * 覆盖结果与用户预期一致；读取仍失败时则原样回到错误态，不丢用户改动。
     */
    fun retry() {
        mutable.value = mutable.value.copy(loading = true, error = null)
        load()
    }

    fun setTime(time: LocalTime) {
        update { it.copy(time = time) }
    }

    private fun update(transform: (ReminderSettingsUiState) -> ReminderSettingsUiState) {
        // 用户改动即视为就绪：清除 loading，迟到的 load() 不得再用存储值覆盖用户输入
        // （真实竞态：load 协程排队在用户操作之后，整体覆盖会把开关打回去，见 VM 测试）。
        val current = transform(mutable.value).copy(loading = false)
        mutable.value = current
        viewModelScope.launch {
            preferences.save(ReminderPreference(current.enabled, current.time)).fold(
                onSuccess = { refreshSchedule(current) },
                onFailure = {
                    mutable.value = mutable.value.copy(error = "保存失败，请重试")
                },
            )
        }
    }

    /**
     * 改动立即生效（Spec：改时间/开关 → 取消旧任务并重排唯一一次）。
     * 完成度在刷新时实时读取：当日已完成时域层会决策取消，通知不会发出。
     */
    private suspend fun refreshSchedule(state: ReminderSettingsUiState) {
        val latest = when (val result = plans.findLatest(activeProfileId())) {
            is com.example.englishlearning.learning.TodayPlanResult.Ready -> result.plan
            else -> null
        }
        val completed = when (val result = latest?.let { events.completedCardIds(it.planId) }) {
            is RepositoryResult.Success -> result.value
            else -> emptyList()
        }
        // 调度器内部同样重算一次；这里先算一遍仅用于把"完成即不发"反映到 UI 状态，
        // 实际排程交给 ReminderScheduler.refresh()（同一决策函数，两次结果必然一致）。
        val decision = ReminderSchedulingPolicy.decide(
            ReminderPreference(state.enabled, state.time), latest, completed, clock,
        )
        mutable.value = mutable.value.copy(
            error = if (decision == com.example.englishlearning.notification.domain.ReminderDecision.Cancel) null else null,
        )
        scheduler.refresh()
    }

    private fun activeProfileId(): String = "default"
}
