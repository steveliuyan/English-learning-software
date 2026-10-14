package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.reading.ArticleDeletionImpact
import com.example.englishlearning.reading.ArticleVersionRepository
import com.example.englishlearning.reading.VersionDeleteOutcome
import com.example.englishlearning.reading.domain.Article
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 确认框的数据源：待删版本与删除前的级联影响预览。 */
data class PendingVersionDelete(
    val article: Article,
    val impact: ArticleDeletionImpact,
)

data class ArticleVersionUiState(
    val pending: PendingVersionDelete? = null,
    val busy: Boolean = false,
    val notice: String? = null,
)

/**
 * F4-01 决策 §8 文章版本管理：预览→二次确认→删除三步状态机。
 * 确认框未点「确认删除」前仓储 deleteVersion 零调用（JVM 用例记数钉死）；
 * 只有真正删成功才递增 [versionDeletions]，宿主据此重放历史加载刷新列表。
 */
@HiltViewModel
class ArticleVersionViewModel @Inject constructor(
    private val repository: ArticleVersionRepository,
    @javax.inject.Named("io") private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArticleVersionUiState())
    val uiState: StateFlow<ArticleVersionUiState> = _uiState

    private val _versionDeletions = MutableStateFlow(0)
    val versionDeletions: StateFlow<Int> = _versionDeletions

    fun requestDelete(article: Article) {
        viewModelScope.launch {
            val impact = kotlinx.coroutines.withContext(io) { repository.deletionImpact(article) }.getOrNull()
            if (impact == null) {
                _uiState.value = _uiState.value.copy(pending = null, notice = "暂时读不到该版本的删除影响，请稍后重试。")
            } else {
                _uiState.value = _uiState.value.copy(pending = PendingVersionDelete(article, impact), notice = null)
            }
        }
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pending = null)
    }

    fun confirmDelete() {
        val pending = _uiState.value.pending ?: return
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true)
        viewModelScope.launch {
            when (val outcome = kotlinx.coroutines.withContext(io) { repository.deleteVersion(pending.article) }.getOrNull()) {
                is VersionDeleteOutcome.Deleted -> {
                    _uiState.value = ArticleVersionUiState(
                        notice = "已删除「${pending.article.title}」第 ${pending.article.version} 版" +
                            "（连带 ${outcome.impact.removedCompletionCount} 条阅读完成记录），" +
                            "同组剩余 ${outcome.impact.remainingVersionCount} 个版本。",
                    )
                    _versionDeletions.value += 1
                }
                VersionDeleteOutcome.RefusedLastVersion -> {
                    _uiState.value = ArticleVersionUiState(
                        notice = "「${pending.article.title}」第 ${pending.article.version} 版是该组唯一版本，不能删除——历史至少保留一版。",
                    )
                }
                null -> {
                    _uiState.value = ArticleVersionUiState(notice = "删除失败：存储不可用，请稍后重试。")
                }
            }
        }
    }
}
