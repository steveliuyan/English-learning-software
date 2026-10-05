package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.OpenVocabularySearchResultUseCase
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SearchVocabularyResult
import com.example.englishlearning.learning.SearchVocabularyOperator
import com.example.englishlearning.learning.VocabularySearchHistory
import com.example.englishlearning.learning.VocabularySearchHistoryRepository
import com.example.englishlearning.learning.domain.WordCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HISTORY_LIMIT = 20
private const val MAX_QUERY_LENGTH = 64

sealed interface GlobalVocabularySearchUiState {
    data object Idle : GlobalVocabularySearchUiState
    data object Loading : GlobalVocabularySearchUiState
    data class Ready(
        val history: List<GlobalVocabularySearchHistoryItem>,
        val results: List<GlobalVocabularySearchResultItem>,
        /**
         * 「点开的那条结果读不到词卡」——索引行残留了已删除词书，或词书包损坏。
         * 用标记而不是把整页切成失败态：搜索结果本身是好的，不该因为点不开一条就丢掉。
         */
        val openFailed: Boolean = false,
    ) : GlobalVocabularySearchUiState
    data object Empty : GlobalVocabularySearchUiState
    data object Failure : GlobalVocabularySearchUiState
    data object ClearingHistory : GlobalVocabularySearchUiState
}

data class GlobalVocabularySearchHistoryItem(
    val history: VocabularySearchHistory,
    val searchCountForDisplay: Int?,
)

data class GlobalVocabularySearchResultItem(
    val result: SearchVocabularyResult,
    val searchCount: Int,
    val searchCountForDisplay: Int?,
)

@HiltViewModel
class GlobalVocabularySearchViewModel @Inject constructor(
    private val searchVocabulary: SearchVocabularyOperator,
    private val historyRepository: VocabularySearchHistoryRepository,
    private val openResult: OpenVocabularySearchResultUseCase,
    @javax.inject.Named("io") private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _query = kotlinx.coroutines.flow.MutableStateFlow("")
    val query: kotlinx.coroutines.flow.StateFlow<String> = _query
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow<GlobalVocabularySearchUiState>(GlobalVocabularySearchUiState.Idle)
    val uiState: kotlinx.coroutines.flow.StateFlow<GlobalVocabularySearchUiState> = _uiState

    /**
     * 用户点开结果后回源取到的**完整词卡**，由界面消费一次后置空
     * （[consumeOpenedCard]）。
     *
     * 为什么必须置空：它是一个一次性事件。若一直留在流里，第二次点同一条时值没有变化，
     * 界面上的 `LaunchedEffect` 不会重跑——用户看到的就是「点了没反应」。
     */
    private val _openedCard = MutableStateFlow<WordCard?>(null)
    val openedCard: StateFlow<WordCard?> = _openedCard

    private var profileId: String? = null
    private var showCount = true
    private var generation = 0L
    private var job: Job? = null
    private var openGeneration = 0L
    private var openJob: Job? = null
    private var lastQuery: String? = null
    private var lastHistory: List<VocabularySearchHistory> = emptyList()

    fun setProfile(value: String) {
        if (profileId == value) return
        profileId = value
        generation++
        job?.cancel()
        job = null
        // 已打开的词卡属于上一个资料，不能跨资料留在内存里。
        openGeneration++
        openJob?.cancel()
        openJob = null
        _openedCard.value = null
        lastQuery = null
        lastHistory = emptyList()
        _query.value = ""
        _uiState.value = GlobalVocabularySearchUiState.Idle
    }

    fun setShowCount(value: Boolean) {
        showCount = value
        _uiState.value = remapDisplay(_uiState.value)
    }

    fun updateQuery(value: String) {
        _query.value = value.take(MAX_QUERY_LENGTH)
        _uiState.value = GlobalVocabularySearchUiState.Idle
    }

    /**
     * 点开一条结果：按 `cardId` 回源取完整词卡交给界面，而不是把索引里的摘要当卡片用。
     *
     * 参数是摘要本身（而不是列表项）：回源只需要 `cardId`，搜索次数之类的展示字段与它无关。
     *
     * 竞态与「查询 vs 查询」同一套处理：新的一次点开取消旧的，并用代号守卫，
     * 让先发起、后返回的那次不能覆盖用户最后的选择。
     */
    fun open(result: SearchVocabularyResult) {
        val id = profileId ?: return
        val expected = ++openGeneration
        openJob?.cancel()
        clearOpenFailure()
        openJob = viewModelScope.launch {
            val card = try {
                withContext(io) { openResult(result) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (expected != openGeneration || profileId != id) return@launch
            if (card != null) {
                _openedCard.value = card
            } else {
                _uiState.value = markOpenFailed(_uiState.value)
            }
        }
    }

    /** 界面把 [openedCard] 交给详情页之后调用，让同一条结果可以再次点开。 */
    fun consumeOpenedCard() {
        _openedCard.value = null
    }

    fun search() {
        val id = profileId ?: return
        val raw = _query.value
        val normalized = raw.trim().replace(Regex("\\s+"), " ")
        lastQuery = normalized.takeIf { it.isNotEmpty() }
        run(id, lastQuery)
    }

    fun retry() {
        val id = profileId ?: return
        run(id, lastQuery)
    }

    /**
     * 进入全量查词页时的**唯一入口**：设置输入框内容并立即出结果。
     *
     * 两个入口都走这里，只是初始查询词不同：
     * - 主页查词传空串 → 清掉上次的查询词，落在「最近搜索」；
     * - 阅读页点未知词传被点中的词 → 直接出这个词的结果。
     *
     * 收敛成一个方法是为了让界面层不必各自写一份「设置查询词 + 搜索」：
     * 少写一半（只设置不搜索、或搜完又被界面清空）都会让用户看不到他点的那个词。
     */
    fun openSearch(query: String) {
        updateQuery(query)
        search()
    }

    private fun clearOpenFailure() {
        _uiState.value = when (val state = _uiState.value) {
            is GlobalVocabularySearchUiState.Ready -> state.copy(openFailed = false)
            else -> state
        }
    }

    private fun markOpenFailed(state: GlobalVocabularySearchUiState): GlobalVocabularySearchUiState =
        when (state) {
            is GlobalVocabularySearchUiState.Ready -> state.copy(openFailed = true)
            else -> state
        }

    fun clearHistory() {
        val id = profileId ?: return
        val expected = ++generation
        job?.cancel()
        _uiState.value = GlobalVocabularySearchUiState.ClearingHistory
        job = viewModelScope.launch {
            when (historyRepository.clear(id)) {
                is RepositoryResult.Success -> {
                    if (expected != generation) return@launch
                    lastHistory = emptyList()
                    _uiState.value = GlobalVocabularySearchUiState.Empty
                }
                is RepositoryResult.Failure -> {
                    if (expected != generation) return@launch
                    _uiState.value = GlobalVocabularySearchUiState.Failure
                }
            }
        }
    }

    private fun run(id: String, query: String?) {
        val requestGeneration = ++generation
        job?.cancel()
        _uiState.value = GlobalVocabularySearchUiState.Loading
        job = viewModelScope.launch {
            try {
                val historyResult = withContext(io) { historyRepository.list(id, HISTORY_LIMIT) }
                if (requestGeneration != generation) return@launch
                when (historyResult) {
                    is RepositoryResult.Failure -> _uiState.value = GlobalVocabularySearchUiState.Failure
                    is RepositoryResult.Success -> {
                        lastHistory = historyResult.value
                        if (query == null) {
                            _uiState.value = if (lastHistory.isEmpty()) GlobalVocabularySearchUiState.Empty else ready(lastHistory, emptyList())
                            return@launch
                        }
                        when (val result = withContext(io) { searchVocabulary.search(id, query) }) {
                            is RepositoryResult.Failure -> _uiState.value = GlobalVocabularySearchUiState.Failure
                            is RepositoryResult.Success -> {
                                if (requestGeneration != generation) return@launch
                                val count = lastHistory.firstOrNull { it.normalizedQuery == query }?.searchCount ?: 0
                                val items = result.value.map { GlobalVocabularySearchResultItem(it, count, count.takeIf { showCount }) }
                                _uiState.value = if (items.isEmpty()) GlobalVocabularySearchUiState.Empty else ready(lastHistory, items)
                            }
                        }
                    }
                }
            } catch (c: CancellationException) { throw c } catch (_: Exception) {
                if (requestGeneration == generation) _uiState.value = GlobalVocabularySearchUiState.Failure
            }
        }
    }

    private fun ready(
        history: List<VocabularySearchHistory>,
        results: List<GlobalVocabularySearchResultItem>,
        openFailed: Boolean = false,
    ) = GlobalVocabularySearchUiState.Ready(
        history = history.map { GlobalVocabularySearchHistoryItem(it, it.searchCount.takeIf { showCount }) },
        results = results,
        openFailed = openFailed,
    )

    private fun remapDisplay(state: GlobalVocabularySearchUiState): GlobalVocabularySearchUiState = when (state) {
        is GlobalVocabularySearchUiState.Ready -> ready(
            lastHistory,
            state.results.map { it.copy(searchCountForDisplay = it.searchCount.takeIf { showCount }) },
            openFailed = state.openFailed,
        )
        else -> state
    }
}
