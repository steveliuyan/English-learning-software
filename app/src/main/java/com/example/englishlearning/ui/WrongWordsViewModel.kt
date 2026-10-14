package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.WrongWordsRepository
import com.example.englishlearning.learning.domain.WordCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WrongWordsUiState {
    data object Loading : WrongWordsUiState

    /** 派生或回源任一环节不可得即整页诚实降级（与打卡/收藏同一口径）。 */
    data object Unavailable : WrongWordsUiState

    /** 已按派生顺序解析好的词卡；内容随词书删除的卡在加载时剔除。 */
    data class Ready(val cards: List<WordCard>) : WrongWordsUiState
}

/**
 * F4-01 决策⑤错词列表 ViewModel：派生（每卡最近反馈 Again）在仓储 SQL，
 * 这里只负责回源成可渲染词卡并保持顺序；点词由宿主交给只读详情（星标/笔记天然可用）。
 */
@HiltViewModel
class WrongWordsViewModel @Inject constructor(
    private val repository: WrongWordsRepository,
    private val cards: WordCardSource,
    @javax.inject.Named("io") private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow<WrongWordsUiState>(WrongWordsUiState.Loading)
    val uiState: StateFlow<WrongWordsUiState> = _uiState

    private var generation = 0L

    fun load(profileId: String) {
        val expected = ++generation
        _uiState.value = WrongWordsUiState.Loading
        viewModelScope.launch {
            val result = repository.wrongWords(profileId)
            if (expected != generation) return@launch
            val rows = result.getOrNull()
            if (rows == null) {
                _uiState.value = WrongWordsUiState.Unavailable
                return@launch
            }
            val resolved = try {
                withContext(io) { cards.cards(rows.map { it.cardId }) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (expected != generation) return@launch
            if (resolved == null) {
                _uiState.value = WrongWordsUiState.Unavailable
            } else {
                val byId = resolved.associateBy { it.cardId }
                _uiState.value = WrongWordsUiState.Ready(rows.mapNotNull { byId[it.cardId] })
            }
        }
    }
}
