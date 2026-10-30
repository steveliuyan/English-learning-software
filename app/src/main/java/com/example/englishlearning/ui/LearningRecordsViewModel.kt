package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.LearningHistoryGroup
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.LearningRecordRepository
import com.example.englishlearning.learning.LearningStatsRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.learning.domain.DailyLearningHistory
import com.example.englishlearning.learning.domain.WordCard
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 「全部单词」已拆为独立入口 WordBookWordsViewModel/WordBookWordsScreen（D2，2026-10-06）。
enum class LearningRecordsTab { TODAY, HISTORY }

data class LearningRecordsUiState(
    val selectedTab: LearningRecordsTab = LearningRecordsTab.TODAY,
    val today: List<LearningRecord> = emptyList(),
    val history: List<LearningHistoryGroup> = emptyList(),
    // F4-01 按日详展（AC4-01）：与 history 分组同批惰性加载，HISTORY 页签分组头渲染用。
    val historyStats: List<DailyLearningHistory> = emptyList(),
    val loaded: Set<LearningRecordsTab> = emptySet(),
    val loading: Set<LearningRecordsTab> = emptySet(),
    val errors: Map<LearningRecordsTab, String> = emptyMap(),
    val selectedDetail: WordCard? = null,
    // 学习记录屏「本周」刻度的窗口基准日（原型 `sMeRecords` 的 `section('本周', weekRow(), '3 / 7 天')`）。
    // 由宿主注入的 `ClockProvider` 在 `load(...)` 时取一次，屏内不读钟（AGENTS.md「UI 之外保持原样」）。
    // 为 `null` 时不画「本周」区块（分母未知，画出来只能是假的）。
    val todayLocalDate: LocalDate? = null,
)

@HiltViewModel
class LearningRecordsViewModel @Inject constructor(
    private val repository: LearningRecordRepository,
    private val statsRepository: LearningStatsRepository,
    private val clock: ClockProvider,
    // 保留末位默认参以兼容现有 JVM 单测；仓库自身负责 IO，默认状态更新在 Main。
    @Named("io") private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private data class LoadContext(
        val profileId: String,
        val planId: String?,
        val wordBookId: String?,
    )

    private val mutable = MutableStateFlow(LearningRecordsUiState())
    val state: StateFlow<LearningRecordsUiState> = mutable.asStateFlow()
    private var context: LoadContext? = null
    private var loadGeneration = 0L
    private val tabJobs = mutableMapOf<LearningRecordsTab, Job>()

    fun load(profileId: String, planId: String?, activeWordBookId: String?) {
        tabJobs.values.forEach { it.cancel() }
        tabJobs.clear()
        val generation = ++loadGeneration
        context = LoadContext(profileId, planId, activeWordBookId)
        // 「本周」刻度的窗口基准日：与 `historyRange` 的区间终点取同一个 `clock`（不新开时钟读点）。
        val todayLocalDate = clock.instant().atZone(clock.zoneId()).toLocalDate()
        mutable.update {
            it.copy(
                today = emptyList(),
                history = emptyList(),
                historyStats = emptyList(),
                loaded = emptySet(),
                loading = emptySet(),
                errors = emptyMap(),
                selectedDetail = null,
                todayLocalDate = todayLocalDate,
            )
        }
        tabJobs[mutable.value.selectedTab] = launchTab(mutable.value.selectedTab, force = true, generation = generation)
    }

    fun selectTab(tab: LearningRecordsTab) {
        mutable.update { it.copy(selectedTab = tab, selectedDetail = null) }
        loadTab(tab)
    }

    fun retry() {
        val tab = mutable.value.selectedTab
        mutable.update { it.copy(errors = it.errors - tab, selectedDetail = null) }
        loadTab(tab, force = true)
    }

    fun openRow(row: LearningRecord) {
        row.wordCard?.let { card -> mutable.update { it.copy(selectedDetail = card) } }
    }

    fun openRow(row: WordBookRecord) {
        row.card?.let { card -> mutable.update { it.copy(selectedDetail = card) } }
    }

    fun clearDetail() {
        mutable.update { it.copy(selectedDetail = null) }
    }

    private fun loadTab(tab: LearningRecordsTab, force: Boolean = false) {
        if (!force && (tab in mutable.value.loaded || tab in mutable.value.loading)) return
        tabJobs[tab]?.cancel()
        tabJobs[tab] = launchTab(tab, force, loadGeneration)
    }

    private fun launchTab(tab: LearningRecordsTab, force: Boolean, generation: Long): Job {        if (!force && (tab in mutable.value.loaded || tab in mutable.value.loading)) return Job()
        mutable.update {
            it.copy(
                loading = it.loading + tab,
                errors = it.errors - tab,
                selectedDetail = null,
            )
        }
        val snapshot = context
        return viewModelScope.launch(dispatcher) {
            when (tab) {
                LearningRecordsTab.TODAY -> {
                    val result: RepositoryResult<List<LearningRecord>> = if (snapshot?.planId != null) {
                        repository.today(snapshot.profileId, snapshot.planId)
                    } else {
                        RepositoryResult.Success(emptyList())
                    }
                    mutable.update { current ->
                        if (generation != loadGeneration || snapshot !== context) return@update current
                        when (result) {
                            is RepositoryResult.Success -> current.copy(
                                today = result.value,
                                loaded = current.loaded + tab,
                                loading = current.loading - tab,
                            )
                            is RepositoryResult.Failure -> current.copy(
                                loading = current.loading - tab,
                                errors = current.errors + (tab to "学习记录暂时无法读取"),
                            )
                        }
                    }
                }
                LearningRecordsTab.HISTORY -> {
                    val result: RepositoryResult<List<LearningHistoryGroup>> = if (snapshot != null) {
                        repository.history(snapshot.profileId)
                    } else {
                        RepositoryResult.Success(emptyList())
                    }
                    // F4-01 详展与逐词分组同页签成对加载；任一侧失败即整页签失败（不留半屏数据）。
                    // 区间终点不用 `LocalDate.ofInstant(...)`：那是 API 34+ 的静态工厂，Android 13 真机
                    // 切到「历史记录」页签会 NoSuchMethodError 崩溃（批 1 真机取证）。`atZone` 自 API 26 起可用。
                    val statsResult: Result<List<DailyLearningHistory>> = if (snapshot != null) {
                        val rangeEnd = clock.instant().atZone(clock.zoneId()).toLocalDate()
                        statsRepository.historyRange(snapshot.profileId, HISTORY_STATS_FROM, rangeEnd)
                    } else {
                        Result.success(emptyList())
                    }
                    mutable.update { current ->
                        if (generation != loadGeneration || snapshot !== context) return@update current
                        val stats = statsResult.getOrNull()
                        when {
                            result is RepositoryResult.Success && stats != null -> current.copy(
                                history = result.value,
                                historyStats = stats,
                                loaded = current.loaded + tab,
                                loading = current.loading - tab,
                            )
                            else -> current.copy(
                                loading = current.loading - tab,
                                errors = current.errors + (tab to "学习记录暂时无法读取"),
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        // 详展覆盖全部历史计划日：下界取项目起点之前，是诚实的全量查询而非截断。
        val HISTORY_STATS_FROM: LocalDate = LocalDate.of(2000, 1, 1)
    }
}
