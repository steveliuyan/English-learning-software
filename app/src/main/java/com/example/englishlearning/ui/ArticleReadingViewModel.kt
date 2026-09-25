package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.ArticleCoverage
import com.example.englishlearning.reading.ArticleHighlightPolicy
import com.example.englishlearning.reading.ReadingPreferenceRepository
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleDisplayMode
import com.example.englishlearning.reading.domain.ReadingPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 阅读页的单篇文章状态。高亮在加载时从文章自带的 `coveredLemmas` **本地派生**（spec F2-03）：
 * 隔天重读、换设备重读都得到相同坐标，与今天的计划无关。
 */
data class ArticleReadingUiState(
    val article: Article,
    val mode: ArticleDisplayMode,
    val translationExpanded: Boolean,
    val highlights: List<com.example.englishlearning.reading.WordHighlight>,
    val uncoveredLemmas: List<String>,
    /** 调用方传入的词卡（今日计划的卡片）。屏幕用它把高亮/未覆盖词解析回 [WordCard]。 */
    val cards: List<WordCard>,
    /** F3-01C：是否在正文标记已背词。关掉时 [highlights] 为空，但未覆盖词 chips 不受影响。 */
    val showLearnedMarks: Boolean = true,
    /** F3-02：本会话内已完成阅读。落库成功才置位；[firstCompletion] 区分首记与幂等重放。 */
    val completed: Boolean = false,
    val firstCompletion: Boolean = true,
)

@HiltViewModel
class ArticleReadingViewModel @Inject constructor(
    private val preferences: ReadingPreferenceRepository,
    private val completions: com.example.englishlearning.reading.ReadingCompletionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ArticleReadingUiState?>(null)
    val uiState: StateFlow<ArticleReadingUiState?> = _uiState

    private var loadedPreference: ReadingPreference? = null

    fun load(article: Article, cards: List<WordCard>) {
        viewModelScope.launch {
            // 偏好读失败不拦阅读：它只是呈现设置，退回默认呈现即可。
            val preference = preferences.getPreference(article.profileId).getOrNull()
            loadedPreference = preference
            _uiState.value = buildState(
                article,
                cards,
                preference?.displayMode ?: ArticleDisplayMode.ENGLISH_FIRST,
                preference?.showLearnedMarks ?: true,
            )
        }
    }

    /** F3-01C：切换「标记已背词」。只改呈现与偏好，绝不改写文章内容。 */
    fun setLearnedMarks(enabled: Boolean) {
        val current = _uiState.value ?: return
        if (current.showLearnedMarks == enabled) return
        _uiState.value = if (enabled) {
            val coverage = ArticleHighlightPolicy.derive(current.article.englishText, current.article.coveredLemmas)
            current.copy(showLearnedMarks = true, highlights = validHighlights(current.article, coverage))
        } else {
            current.copy(showLearnedMarks = false, highlights = emptyList())
        }
        viewModelScope.launch {
            preferences.savePreference(
                (loadedPreference ?: ReadingPreference(profileId = current.article.profileId))
                    .copy(showLearnedMarks = enabled),
            )
        }
    }

    /**
     * 切换显示模式只改呈现与偏好，**绝不改写文章内容**；已展开/收起的译文状态保持不变。
     */
    fun setMode(mode: ArticleDisplayMode) {
        val current = _uiState.value ?: return
        _uiState.value = current.copy(mode = mode)
        viewModelScope.launch {
            preferences.savePreference(
                (loadedPreference ?: ReadingPreference(profileId = current.article.profileId)).copy(displayMode = mode),
            )
        }
    }

    fun toggleTranslation() {
        val current = _uiState.value ?: return
        if (current.article.chineseText.isEmpty()) return // 无译文来源：开关在界面上已禁用，这里再守一道
        _uiState.value = current.copy(translationExpanded = !current.translationExpanded)
    }

    /** 点词开词卡详情用；未命中返回 null（例如来自 lemma 占位、词卡已不在词书里）。 */
    fun cardFor(cardId: String): WordCard? =
        _uiState.value?.cards?.firstOrNull { it.cardId == cardId || it.lemma == cardId }

    /** F3-02：完成阅读。幂等落库；失败不改本地状态（按钮仍可重试）。 */
    fun completeReading() {
        val current = _uiState.value ?: return
        if (current.completed) return
        viewModelScope.launch {
            completions.record(current.article).getOrNull()?.let { firstTime ->
                _uiState.value = _uiState.value?.copy(completed = true, firstCompletion = firstTime)
            }
        }
    }

    private fun buildState(
        article: Article,
        cards: List<WordCard>,
        mode: ArticleDisplayMode,
        showLearnedMarks: Boolean = true,
    ): ArticleReadingUiState {
        val coverage = ArticleHighlightPolicy.derive(article.englishText, article.coveredLemmas)
        return ArticleReadingUiState(
            article = article,
            mode = mode,
            translationExpanded = mode == ArticleDisplayMode.FULL_TRANSLATION,
            highlights = if (showLearnedMarks) validHighlights(article, coverage) else emptyList(),
            uncoveredLemmas = coverage.uncoveredLemmas,
            cards = cards,
            showLearnedMarks = showLearnedMarks,
        )
    }

    /** 区间合法性防御：越界或倒序的脏区间直接丢弃，不让一处坏数据崩掉整页。 */
    private fun validHighlights(article: Article, coverage: ArticleCoverage) = coverage.highlights.filter {
        it.start >= 0 && it.end <= article.englishText.length && it.start < it.end
    }
}
