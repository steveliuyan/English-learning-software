package com.example.englishlearning.learning

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.dao.InternalVocabularySearchIndexDao
import com.example.englishlearning.core.storage.dao.VocabularySearchIndexSql
import com.example.englishlearning.core.storage.entity.VocabularySearchIndexEntity
import com.example.englishlearning.learning.domain.WordCard
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val PLACEHOLDER_CARD_PREFIX = "placeholder:"

/** 由 `AppModule` 显式提供（与 [RoomVocabularySearchHistoryRepository] 同一装配方式），因此不加 `@Inject`。 */
class RoomVocabularySearchIndexRepository(
    private val database: AppDatabase,
    private val io: CoroutineDispatcher,
) : VocabularySearchIndexRepository {
    override suspend fun replaceWordBook(
        wordBookId: String,
        wordBookName: String,
        dataVersion: String,
        cards: List<WordCard>,
    ): RepositoryResult<Unit> = storageResult(io) {
        val rows = cards
            .filterNot { it.cardId.startsWith(PLACEHOLDER_CARD_PREFIX) }
            .map { card ->
                VocabularySearchIndexEntity(
                    wordBookId = wordBookId,
                    cardId = card.cardId,
                    wordBookName = wordBookName,
                    dataVersion = dataVersion,
                    lemma = card.lemma,
                    normalizedLemma = normalizeIndexedTerm(card.lemma),
                    normalizedPhrases = encodeIndexedTerms(
                        card.phrases.map { normalizeIndexedTerm(it.text) }.filter { it.isNotEmpty() },
                    ),
                    ipa = card.ipa,
                    meaningZh = card.meaningZh,
                )
            }
        // 一个事务里改**两张表**（基表索引行 + gram 字串索引）：中途失败时旧索引原样保留，
        // 绝不会出现「基表有行、gram 表没有」的半册状态——那会让 contains 静默缺词。
        database.withTransaction {
            val raw = database.openHelper.writableDatabase
            database.internalVocabularySearchIndexDao().deleteWordBook(wordBookId)
            if (rows.isEmpty()) {
                VocabularySearchGramStore.deleteWordBook(raw, wordBookId)
            } else {
                database.internalVocabularySearchIndexDao().insertAll(rows)
                VocabularySearchGramStore.replaceWordBook(raw, wordBookId, rows)
            }
        }
    }

    override suspend fun deleteWordBook(wordBookId: String): RepositoryResult<Unit> = storageResult(io) {
        // 两张表要一起删，所以这里也必须在事务内（早先只有单条 DELETE，不需要）。
        database.withTransaction {
            database.internalVocabularySearchIndexDao().deleteWordBook(wordBookId)
            VocabularySearchGramStore.deleteWordBook(database.openHelper.writableDatabase, wordBookId)
        }
    }

    override suspend fun deleteMissing(keepWordBookIds: Collection<String>): RepositoryResult<Unit> = storageResult(io) {
        val keep = keepWordBookIds.toList().ifEmpty { listOf(KEEP_NOTHING_WORD_BOOK) }
        database.withTransaction {
            database.internalVocabularySearchIndexDao().deleteMissing(keep)
            VocabularySearchGramStore.deleteMissing(database.openHelper.writableDatabase, keep)
        }
    }

    override suspend fun search(
        normalizedQuery: String,
        limit: Int,
    ): RepositoryResult<List<VocabularySearchIndexEntry>> = storageResult(io) {
        val capped = limit.coerceAtLeast(0)
        if (capped == 0) {
            emptyList()
        } else {
            val escaped = escapeLike(normalizedQuery)
            val dao = database.internalVocabularySearchIndexDao()
            val exact = dao.searchExact(normalizedQuery, capped).toEntries()
            val prefix = dao.searchPrefix(normalizedQuery, prefixUpperBound(normalizedQuery), capped).toEntries()
            val contains = containsTier(dao, normalizedQuery, escaped, capped)
            VocabularySearchIndexRanker.merge(exact, prefix, contains, capped)
        }
    }

    override suspend fun indexedVersions(): RepositoryResult<Map<String, String>> = storageResult(io) {
        database.internalVocabularySearchIndexDao().indexedVersions().associate { it.wordBookId to it.dataVersion }
    }

    /**
     * 包含匹配这一级：**索引只负责把候选集缩小，精确判断仍由 `LIKE` 做**。
     *
     * - 查询串短于 3 字符 → 没有 gram 可用，回退整表 `LIKE`；
     * - 其余情况 → FTS4 gram 短语查询收窄候选集，再在候选集上 `LIKE` 精确判断。
     *
     * 这样最终结果与「整表 `LIKE '%q%'`」逐行一致（编码是逐字符同态映射，子串关系不丢），
     * 而扫描范围从 45,290 行降到「命中的那一小撮行」。
     */
    private suspend fun containsTier(
        dao: InternalVocabularySearchIndexDao,
        normalizedQuery: String,
        escaped: String,
        limit: Int,
    ): List<VocabularySearchIndexEntry> {
        val phrase = gramPhraseOf(normalizedQuery)
        return if (phrase == null) {
            dao.searchContainsShort(escaped, limit).toEntries()
        } else {
            dao.searchContainsIndexed(
                SimpleSQLiteQuery(
                    VocabularySearchIndexSql.CONTAINS,
                    arrayOf(phrase, escaped, escaped, limit),
                ),
            ).toEntries()
        }
    }

    /**
     * 行到摘要的映射没有可失败的步骤（不复存在「单行 JSON 解不开」这种坏行），
     * 所以这里不像旧实现那样需要 `mapNotNull` + 逐行兜底。
     */
    private fun List<VocabularySearchIndexEntity>.toEntries(): List<VocabularySearchIndexEntry> =
        map { entity ->
            VocabularySearchIndexEntry(
                wordBookId = entity.wordBookId,
                cardId = entity.cardId,
                wordBookName = entity.wordBookName,
                lemma = entity.lemma,
                ipa = entity.ipa,
                meaningZh = entity.meaningZh,
                normalizedTerms = decodeIndexedTerms(entity.normalizedLemma, entity.normalizedPhrases),
            )
        }
}

/**
 * 统一把存储异常映射成 [RepositoryResult.Failure]，并让取消原样穿透。
 *
 * `runCatching` 会把 `CancellationException` 也吞掉折成失败，表现为「取消」变成一次假错误态，
 * 所以这里显式先捕获取消再抛出。
 */
private suspend fun <T> storageResult(
    io: CoroutineDispatcher,
    block: suspend () -> T,
): RepositoryResult<T> = withContext(io) {
    try {
        RepositoryResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
    }
}

/** 与 `normalizeVocabularySearchQuery` 同一套规则，但用于词卡自身字段（空白必非 null）。 */
internal fun normalizeIndexedTerm(value: String): String =
    value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

/**
 * 转义 LIKE 的通配符。用户输入里的 `%`、`_`、`\` 是普通字符，
 * 不转义会让 `a_b` 命中 `axb`、`%` 命中全表。
 */
internal fun escapeLike(value: String): String = buildString(value.length + 4) {
    for (ch in value) {
        when (ch) {
            '\\', '%', '_' -> {
                append('\\')
                append(ch)
            }
            else -> append(ch)
        }
    }
}

/**
 * 前缀区间查询的上界：把查询串的最后一个字符加一。
 *
 * `[q, q+1)` 精确覆盖「以 q 为前缀的全部字符串」（逐个字符比较：前 `n-1` 位相同，
 * 第 `n` 位"末字符" < "末字符+1"，所以无论后面还跟什么，都比上界小）。
 *
 * 它替代的是 `LIKE q || '%'`——后者用不上索引（`LIKE` 的优化只认字面量/参数，
 * 不认 `? || '%'` 这种表达式）。末字符是 `\uFFFF` 时无处可进，退化成「拼一个
 * `\uFFFF`」——归一化后的英中词项里不会出现 `\uFFFF`，这里只是不留未定义行为。
 */
internal fun prefixUpperBound(normalizedQuery: String): String {
    if (normalizedQuery.isEmpty()) return normalizedQuery
    val last = normalizedQuery.last()
    return if (last == Char.MAX_VALUE) {
        normalizedQuery + Char.MAX_VALUE
    } else {
        normalizedQuery.dropLast(1) + (last + 1)
    }
}
