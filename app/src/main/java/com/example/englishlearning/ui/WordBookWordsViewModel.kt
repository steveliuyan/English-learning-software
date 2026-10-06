package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.LearningRecordRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import dagger.hilt.android.lifecycle.HiltViewModel
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

/**
 * 词书单词（D2，docs/decisions/2026-10-06-auto-pronunciation-and-wordbook-words.md）：
 * 从「学习记录 → 全部单词」拆出来的独立浏览入口。数据源沿用
 * [LearningRecordRepository.allWords]，只是不再混在学习记录的 tab 里。
 */
data class WordBookWordsLetterGroup(
    val letter: String,
    val records: List<WordBookRecord>,
)

data class WordBookWordsUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val words: List<WordBookRecord> = emptyList(),
    val filter: WordBookRecordStatus? = null,
) {
    /** 过滤在本地完成：分组/过滤都不回源，切换筛选零成本。 */
    val groups: List<WordBookWordsLetterGroup>
        get() = groupWordBookWordsByLetter(words.filter { filter == null || it.status == filter })
}

/** 首字母归桶：A–Z（大小写折叠）各一桶，其余（数字、汉字、空、符号）进 “#”，# 桶排在最后。 */
internal fun letterBucketOf(lemma: String): String {
    val first = lemma.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (first in 'A'..'Z') first.toString() else "#"
}

internal fun groupWordBookWordsByLetter(words: List<WordBookRecord>): List<WordBookWordsLetterGroup> = words
    .groupBy { letterBucketOf(it.displayLemma) }
    .map { (letter, rows) ->
        WordBookWordsLetterGroup(
            letter = letter,
            records = rows.sortedWith(compareBy({ it.displayLemma.lowercase() }, { it.displayLemma })),
        )
    }
    .sortedWith(compareBy({ if (it.letter == "#") 1 else 0 }, { it.letter }))

@HiltViewModel
class WordBookWordsViewModel @Inject constructor(
    private val repository: LearningRecordRepository,
    private val clock: ClockProvider,
    // 仓库自身负责 IO；保留 dispatcher 参数以兼容 JVM 单测的调度器注入。
    @Named("io") private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val mutable = MutableStateFlow(WordBookWordsUiState())
    val state: StateFlow<WordBookWordsUiState> = mutable.asStateFlow()
    private var loadGeneration = 0L
    private var loadJob: Job? = null
    private var lastWordBookId: String? = null

    fun load(wordBookId: String?) {
        lastWordBookId = wordBookId
        loadJob?.cancel()
        val generation = ++loadGeneration
        if (wordBookId == null) {
            // 尚未设置词书时按空态处理，不报错也不转圈。
            mutable.update { it.copy(words = emptyList(), loading = false, error = null, filter = null) }
            return
        }
        mutable.update { it.copy(words = emptyList(), loading = true, error = null) }
        loadJob = viewModelScope.launch(dispatcher) {
            val result = repository.allWords(wordBookId, clock.instant())
            if (generation != loadGeneration) return@launch
            when (result) {
                is RepositoryResult.Success -> mutable.update {
                    it.copy(words = result.value, loading = false, error = null)
                }
                is RepositoryResult.Failure -> mutable.update {
                    it.copy(words = emptyList(), loading = false, error = "词书单词暂时无法读取")
                }
            }
        }
    }

    fun retry() {
        load(lastWordBookId)
    }

    fun selectFilter(filter: WordBookRecordStatus?) {
        mutable.update { it.copy(filter = filter) }
    }
}
