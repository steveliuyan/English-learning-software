package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.domain.WordCard
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
)

@HiltViewModel
class ArticleReadingViewModel @Inject constructor(
    private val preferences: ReadingPreferenceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ArticleReadingUiState?>(null)
    val uiState: StateFlow<ArticleReadingUiState?> = _uiState

    private var loadedPreference: ReadingPreference? = null

    fun load(article: Article, cards: List<WordCard>) {
        viewModelScope.launch {
            // 偏好读失败不拦阅读：它只是呈现设置，退回默认呈现即可。
            val preference = preferences.getPreference(article.profileId).getOrNull()
            loadedPreference = preference
            _uiState.value = buildState(article, cards, preference?.displayMode ?: ArticleDisplayMode.ENGLISH_FIRST)
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

    private fun buildState(
        article: Article,
        cards: List<WordCard>,
        mode: ArticleDisplayMode,
    ): ArticleReadingUiState {
        val coverage = ArticleHighlightPolicy.derive(article.englishText, article.coveredLemmas)
        // 区间合法性防御：越界或倒序的脏区间直接丢弃，不让一处坏数据崩掉整页。
        val highlights = coverage.highlights.filter {
            it.start >= 0 && it.end <= article.englishText.length && it.start < it.end
        }
        return ArticleReadingUiState(
            article = article,
            mode = mode,
            translationExpanded = mode == ArticleDisplayMode.FULL_TRANSLATION,
            highlights = highlights,
            uncoveredLemmas = coverage.uncoveredLemmas,
            cards = cards,
        )
    }
}
