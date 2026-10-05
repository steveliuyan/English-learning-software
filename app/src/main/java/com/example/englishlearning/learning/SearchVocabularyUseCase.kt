package com.example.englishlearning.learning

import com.example.englishlearning.core.time.ClockProvider
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface SearchVocabularyOperator {
    suspend fun search(profileId: String, query: String): RepositoryResult<List<SearchVocabularyResult>>
}

private const val DEFAULT_MAX_RESULTS = 30

/**
 * 一条搜索结果的**摘要**。
 *
 * 它刻意不是完整 `WordCard`：索引为了控体积只保存结果列表要显示的四项
 * （lemma / 音标 / 中文释义 / 所属词书）。详情页要的释义分组、例句、派生词、短语、
 * 近义词、配图由 `OpenVocabularySearchResultUseCase` 在用户点开时按 [cardId] 回源取。
 */
data class SearchVocabularyResult(
    val wordBookId: String,
    val cardId: String,
    val wordBookName: String,
    val lemma: String,
    val ipa: String,
    val meaningZh: String,
)

/**
 * 全量词汇搜索用例（spec: 全量词汇搜索）。
 *
 * **查询路径不读词书包**：候选全部来自本地词条索引 [VocabularySearchIndexRepository]，
 * 不再调用 `WordCardSource.cardIds()` / `cards()`。这一点是本用例存在的理由——
 * 之前的实现每次查询都要遍历全部可见词书、逐册解析词书包（导入册还会重算图片哈希），
 * 词库一大就慢到不可接受。
 *
 * 词书的可见性（内置 + 已导入）在**建索引时**已经确定，所以这里不需要再读
 * `LearningProfileRepository`，也就没有「查询时顺便列一遍词书」这一步。
 */
class SearchVocabularyUseCase(
    private val history: VocabularySearchHistoryRepository,
    private val index: VocabularySearchIndexRepository,
    private val clock: ClockProvider,
    private val maxResults: Int = DEFAULT_MAX_RESULTS,
) : SearchVocabularyOperator {
    override suspend fun search(profileId: String, query: String): RepositoryResult<List<SearchVocabularyResult>> {
        val normalized = normalizeVocabularySearchQuery(query)
            ?: return RepositoryResult.Success(emptyList())
        val limit = maxResults.coerceAtLeast(0)
        val indexed = when (val result = index.search(normalized, limit)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return result
        }
        currentCoroutineContext().ensureActive()
        val candidates = indexed
            .mapNotNull { entry ->
                val score = matchScore(entry, normalized) ?: return@mapNotNull null
                score to SearchVocabularyResult(
                    wordBookId = entry.wordBookId,
                    cardId = entry.cardId,
                    wordBookName = entry.wordBookName,
                    lemma = entry.lemma,
                    ipa = entry.ipa,
                    meaningZh = entry.meaningZh,
                )
            }
            .sortedWith(
                compareBy<Pair<Int, SearchVocabularyResult>> { it.first }
                    .thenBy { it.second.lemma.lowercase(Locale.ROOT) }
                    .thenBy { it.second.wordBookName },
            )
            .map { it.second }
            .take(limit)

        val representative = candidates.firstOrNull()?.let {
            SearchRepresentative(it.wordBookId, it.cardId)
        }
        when (val record = history.record(profileId, query, representative, clock.instant())) {
            is RepositoryResult.Success -> Unit
            is RepositoryResult.Failure -> return record
        }
        return RepositoryResult.Success(candidates)
    }

    /**
     * 匹配等级 0/1/2，与索引的三个查询等级同义。判据是索引行里的归一化词项
     * （lemma + 短语），不是在索引里现算卡片字段——两者逐条等价，
     * `normalizeIndexedTerm` 是确定性的，建索引时算过一次就够。
     */
    private fun matchScore(entry: VocabularySearchIndexEntry, query: String): Int? = when {
        entry.normalizedTerms.any { it == query } -> 0
        entry.normalizedTerms.any { it.startsWith(query) } -> 1
        entry.normalizedTerms.any { it.contains(query) } -> 2
        else -> null
    }
}
