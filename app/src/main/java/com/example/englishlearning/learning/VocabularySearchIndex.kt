package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.WordCard

/**
 * 索引里的一条命中——**摘要，不是完整词卡**。
 *
 * [lemma]、[ipa]、[meaningZh]、[wordBookName] 正好是结果列表要渲染的四项；
 * 点开这条结果时用 [cardId] 回源取完整卡片（`OpenVocabularySearchResultUseCase`）。
 *
 * [normalizedTerms] 是归一化后的检索词项：首项是 lemma，其余是短语。匹配计分只依赖它，
 * 因此不需要在查询路径上读整张词卡。
 */
data class VocabularySearchIndexEntry(
    val wordBookId: String,
    val cardId: String,
    val wordBookName: String,
    val lemma: String,
    val ipa: String,
    val meaningZh: String,
    val normalizedTerms: List<String>,
)

/** 短语之间的分隔符：`\u0001` 不可能出现在真实短语里，用它拼接不会造成跨短语误命中。 */
internal const val INDEXED_PHRASE_SEPARATOR: String = "\u0001"

/** 把一批检索词项压成一个可 `LIKE` 的字符串。 */
internal fun encodeIndexedTerms(terms: List<String>): String =
    terms.joinToString(INDEXED_PHRASE_SEPARATOR)

/**
 * 把索引行还原成检索词项：首项是 lemma，其余是短语。
 *
 * 编码/解码成对出现，是为了让「短语也参与检索」这件事在两层之间不丢：
 * 丢了短语，按短语查不到词是用户察觉不到的静默缺词。
 */
internal fun decodeIndexedTerms(normalizedLemma: String, normalizedPhrases: String): List<String> =
    buildList {
        add(normalizedLemma)
        if (normalizedPhrases.isNotEmpty()) {
            addAll(normalizedPhrases.split(INDEXED_PHRASE_SEPARATOR).filter { it.isNotEmpty() })
        }
    }

/**
 * 本地词条搜索索引（spec: 全量词汇搜索）。
 *
 * 索引是**派生数据**：唯一事实来源仍是词书包与 `WordCardSource`。索引只负责把
 * 「查询要遍历全部词卡」变成「查询命中 B 树」。
 *
 * 因此它必须满足两条约束：
 * - [replaceWordBook] 要么整册替换成功、要么完全不动，不允许出现半册索引；
 * - [deleteMissing] 用于清理已删除词书的残留行，避免搜索结果指向不存在的词书。
 */
interface VocabularySearchIndexRepository {
    suspend fun replaceWordBook(
        wordBookId: String,
        wordBookName: String,
        dataVersion: String,
        cards: List<WordCard>,
    ): RepositoryResult<Unit>

    suspend fun deleteWordBook(wordBookId: String): RepositoryResult<Unit>

    suspend fun search(
        normalizedQuery: String,
        limit: Int,
    ): RepositoryResult<List<VocabularySearchIndexEntry>>

    /** 已建立索引的词书及建立时的 `dataVersion`，供刷新用例判断过期。 */
    suspend fun indexedVersions(): RepositoryResult<Map<String, String>>

    /** 删除不在 [keepWordBookIds] 中的词书的索引行。 */
    suspend fun deleteMissing(keepWordBookIds: Collection<String>): RepositoryResult<Unit>
}

/**
 * 把三个匹配等级的三次查询结果合并成最终候选集。
 *
 * 抽成纯函数是为了能在 JVM 上直接验证**生产逻辑本身**：等级顺序、按 `(wordBookId, cardId)` 去重、
 * 以及截断发生在合并之后——这三点都是「先 `LIMIT` 再排序」会悄悄弄错的地方。
 */
object VocabularySearchIndexRanker {
    fun merge(
        exact: List<VocabularySearchIndexEntry>,
        prefix: List<VocabularySearchIndexEntry>,
        contains: List<VocabularySearchIndexEntry>,
        limit: Int,
    ): List<VocabularySearchIndexEntry> {
        val capped = limit.coerceAtLeast(0)
        if (capped == 0) return emptyList()
        val seen = HashSet<String>()
        val merged = ArrayList<VocabularySearchIndexEntry>(minOf(exact.size + prefix.size + contains.size, capped))
        for (tier in listOf(exact, prefix, contains)) {
            for (entry in tier) {
                if (merged.size == capped) return merged
                val key = entry.wordBookId + "\u0000" + entry.cardId
                if (!seen.add(key)) continue
                merged += entry
            }
        }
        return merged
    }
}
