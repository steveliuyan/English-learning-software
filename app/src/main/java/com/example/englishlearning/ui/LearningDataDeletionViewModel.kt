package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.LearningDataDeletionImpact
import com.example.englishlearning.learning.LearningDataDeletionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LearningDataDeletionUiState(
    val pending: LearningDataDeletionImpact? = null,
    val busy: Boolean = false,
    val notice: String? = null,
)

/**
 * F4-01 决策 §9「删除全部学习数据」：预览→二次确认→删除三步状态机。
 * 确认框未点「确认删除」前仓储 deleteAllLearningData 零调用；
 * 只有真删成功才递增 [dataDeletions]，宿主据此重放今日/统计/错词/收藏加载。
 */
@HiltViewModel
class LearningDataDeletionViewModel @Inject constructor(
    private val repository: LearningDataDeletionRepository,
    @javax.inject.Named("io") private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LearningDataDeletionUiState())
    val uiState: StateFlow<LearningDataDeletionUiState> = _uiState

    private val _dataDeletions = MutableStateFlow(0)
    val dataDeletions: StateFlow<Int> = _dataDeletions

    fun requestDelete(profileId: String) {
        viewModelScope.launch {
            val impact = kotlinx.coroutines.withContext(io) { repository.deletionImpact(profileId) }.getOrNull()
            if (impact == null) {
                _uiState.value = LearningDataDeletionUiState(notice = "暂时读不到删除影响，请稍后重试。")
            } else {
                _uiState.value = _uiState.value.copy(pending = impact, notice = null)
            }
        }
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pending = null)
    }

    fun confirmDelete(profileId: String) {
        val pending = _uiState.value.pending ?: return
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true)
        viewModelScope.launch {
            val outcome = kotlinx.coroutines.withContext(io) { repository.deleteAllLearningData(profileId) }.getOrNull()
            if (outcome == null) {
                _uiState.value = LearningDataDeletionUiState(notice = "删除失败：存储不可用，请稍后重试。")
            } else {
                _uiState.value = LearningDataDeletionUiState(
                    notice = "已删除学习记录 ${outcome.eventCount} 条、复习调度 ${outcome.reviewStateCount} 条、" +
                        "计划 ${outcome.planCount} 天、阅读完成 ${outcome.completionCount} 条、迁移审计 ${outcome.migrationAuditCount} 条。" +
                        "文章 ${outcome.keptArticleCount} 篇与收藏 ${outcome.keptFavoriteCount} 条保留。",
                )
                _dataDeletions.value += 1
            }
        }
    }
}
