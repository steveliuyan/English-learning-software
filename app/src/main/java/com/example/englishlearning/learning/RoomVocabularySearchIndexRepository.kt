package com.example.englishlearning.learning

import androidx.room.withTransaction
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.VocabularySearchIndexEntity
import com.example.englishlearning.learning.domain.WordCard
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val PLACEHOLDER_CARD_PREFIX = "placeholder:"

/**
 * `NOT IN ()` 在 SQLite 语法上非法，所以「保留集合为空」这一支改写成
 * 「删除除一个不可能存在的 id 之外的全部行」，语义等价于清空整表。
 */
private const val KEEP_NOTHING_PLACEHOLDER = "\u0000keep-nothing"

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
        // 一个事务里「先删后插」：中途失败时旧索引原样保留，绝不会出现半册索引。
        database.withTransaction {
            database.internalVocabularySearchIndexDao().deleteWordBook(wordBookId)
            if (rows.isNotEmpty()) database.internalVocabularySearchIndexDao().insertAll(rows)
        }
    }

    override suspend fun deleteWordBook(wordBookId: String): RepositoryResult<Unit> = storageResult(io) {
        database.internalVocabularySearchIndexDao().deleteWordBook(wordBookId)
    }

    override suspend fun deleteMissing(keepWordBookIds: Collection<String>): RepositoryResult<Unit> = storageResult(io) {
        val keep = keepWordBookIds.toList().ifEmpty { listOf(KEEP_NOTHING_PLACEHOLDER) }
        database.internalVocabularySearchIndexDao().deleteMissing(keep)
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
            val prefix = dao.searchPrefix(escaped, capped).toEntries()
            val contains = dao.searchContains(escaped, capped).toEntries()
            VocabularySearchIndexRanker.merge(exact, prefix, contains, capped)
        }
    }

    override suspend fun indexedVersions(): RepositoryResult<Map<String, String>> = storageResult(io) {
        database.internalVocabularySearchIndexDao().indexedVersions().associate { it.wordBookId to it.dataVersion }
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
