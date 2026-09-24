package com.example.englishlearning.reading

import com.example.englishlearning.learning.domain.WordCard

/** 一次命中：卡片、词元、**原文里实际出现的写法**（保留大小写），以及原文区间。 */
data class WordHighlight(
    val cardId: String,
    val lemma: String,
    val matched: String,
    val start: Int,
    val end: Int,
)

/** 派生结果。[uncoveredLemmas] 按输入卡片的顺序排列，供「未覆盖词」提示使用。 */
data class ArticleCoverage(
    val highlights: List<WordHighlight>,
    val uncoveredLemmas: List<String>,
)

/**
 * 从正文派生高亮与未覆盖词。**高亮位置永远在本地算出来**（spec F2-03）：隔天重读、
 * 换设备重读都会得到相同坐标，且不依赖模型的任何位置信息。
 *
 * 匹配语义：`\\b` 词边界 + 忽略大小写；词形变化按长度降序进 alternation（长词优先，
 * 否则 `apple` 会先吃掉 `apples` 的前五个字符留下 `s`）。`-` 与 `'` 在 Java 正则的
 * `\\b` 语义下本身就是边界，所以 `apple-shaped`、`apple's` 都各算一次。
 *
 * 重叠处理：不同卡片可能在同一区域命中（`app` 与 `apple`），按 start 升序、end 降序
 * 排序后贪心去重，同一段文字不会被标成两个词。
 */
object ArticleHighlightPolicy {
    fun derive(englishText: String, cards: List<WordCard>): ArticleCoverage {
        if (englishText.isEmpty() || cards.isEmpty()) return ArticleCoverage(emptyList(), emptyList())

        data class Candidate(val start: Int, val end: Int, val cardId: String, val lemma: String, val matched: String)

        val candidates = mutableListOf<Candidate>()
        for (card in cards) {
            val variants = (listOf(card.lemma) + card.inflections).distinct()
            val alternation = variants.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            val bounded = Regex("\\b(?:$alternation)\\b", RegexOption.IGNORE_CASE)
            for (match in bounded.findAll(englishText)) {
                candidates += Candidate(
                    start = match.range.first,
                    end = match.range.last + 1,
                    cardId = card.cardId,
                    lemma = card.lemma,
                    matched = match.value,
                )
            }
        }

        val highlights = candidates
            .sortedWith(compareByDescending<Candidate> { it.end }.thenBy { it.start })
            .fold(mutableListOf<Candidate>()) { selected, candidate ->
                val overlaps = selected.any { candidate.start < it.end && it.start < candidate.end }
                if (!overlaps) selected += candidate
                selected
            }
            .map { WordHighlight(it.cardId, it.lemma, it.matched, it.start, it.end) }
            .sortedBy { it.start }

        val coveredLemmas = highlights.map { it.lemma }.toSet()
        val uncovered = cards.map { it.lemma }.filterNot { it in coveredLemmas }
        return ArticleCoverage(highlights, uncovered)
    }

    /**
     * 阅读页从库里的 lemma 列表重算历史文章时用（隔天重读）。此时没有卡片身份，
     * [WordHighlight.cardId] 以 lemma 占位——高亮渲染只需要词与区间。
     * 两个重载泛型擦除后 JVM 签名相同，故加 @JvmName 区分。
     */
    @JvmName("deriveFromLemmas")
    fun derive(englishText: String, lemmas: List<String>): ArticleCoverage =
        derive(englishText, lemmas.map { lemma -> WordCard(lemma, "", lemma, "", "", "") })
}
