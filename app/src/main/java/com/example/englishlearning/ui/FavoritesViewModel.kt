package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.FavoriteWord
import com.example.englishlearning.learning.FavoriteWordRepository
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.WordCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState

    /** 收藏列表读不出即整页诚实降级（与打卡页同一口径）。 */
    data object Unavailable : FavoritesUiState

    data class Ready(
        val words: List<FavoriteWord> = emptyList(),
        /** 点开后原词条内容已随词书删除时的提示；成功打开新的详情会清除。 */
        val notice: String? = null,
    ) : FavoritesUiState
}

/**
 * F4-01 决策⑦收藏页 ViewModel。收藏集合在档案就绪时加载一次，
 * 词卡详情的星标状态由它派生（`Ready.words` 的 cardId 集合）；
 * 详情只读入口复用 [WordCardSource] 回源，取不到内容时诚实提示而不是硬跳空卡。
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: FavoriteWordRepository,
    private val cards: WordCardSource,
    @javax.inject.Named("io") private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FavoritesUiState>(FavoritesUiState.Loading)
    val uiState: StateFlow<FavoritesUiState> = _uiState

    /** 一次性事件：界面消费后必须 [consumeOpenedCard] 置空，否则第二次点同一条不重跑。 */
    private val _openedCard = MutableStateFlow<WordCard?>(null)
    val openedCard: StateFlow<WordCard?> = _openedCard

    private var profileId: String? = null
    private var generation = 0L
    /** 点开详情与列表加载各自一条守卫：回源不能作废列表刷新的结果，反之亦然。 */
    private var openGeneration = 0L

    fun load(profileId: String) {
        this.profileId = profileId
        val expected = ++generation
        _uiState.value = FavoritesUiState.Loading
        viewModelScope.launch {
            val result = repository.listFavorites(profileId)
            if (expected != generation || this@FavoritesViewModel.profileId != profileId) return@launch
            _uiState.value = if (result.isSuccess) {
                FavoritesUiState.Ready(result.getOrThrow())
            } else {
                FavoritesUiState.Unavailable
            }
        }
    }

    fun toggle(cardId: String, wordBookId: String, lemma: String) {
        val id = profileId ?: return
        viewModelScope.launch {
            val result = repository.toggle(id, cardId, wordBookId, lemma)
            if (expected(id)) {
                if (result.isFailure) {
                    _uiState.value = FavoritesUiState.Unavailable
                } else {
                    reload(id)
                }
            }
        }
    }

    fun open(favorite: FavoriteWord) {
        val id = profileId ?: return
        val expectedOpen = ++openGeneration
        viewModelScope.launch {
            val card = try {
                withContext(io) { cards.cards(listOf(favorite.cardId)).firstOrNull { it.cardId == favorite.cardId } }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (expectedOpen != openGeneration || profileId != id) return@launch
            if (card != null) {
                _openedCard.value = card
                _uiState.update { if (it is FavoritesUiState.Ready) it.copy(notice = null) else it }
            } else {
                _uiState.update {
                    if (it is FavoritesUiState.Ready) {
                        it.copy(notice = "「${favorite.lemma}」的内容已随原词书删除，无法打开详情")
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun consumeOpenedCard() {
        _openedCard.value = null
    }

    private suspend fun reload(id: String) {
        val expected = ++generation
        val result = repository.listFavorites(id)
        if (expected != generation || profileId != id) return
        _uiState.value = if (result.isSuccess) {
            FavoritesUiState.Ready(result.getOrThrow())
        } else {
            FavoritesUiState.Unavailable
        }
    }

    private fun expected(id: String): Boolean = profileId == id
}
