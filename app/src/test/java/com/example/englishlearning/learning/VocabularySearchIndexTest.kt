package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.entity.VocabularySearchIndexEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证索引层的**生产逻辑**（不是测试替身）：
 * 等级合并顺序、去重、以及「先合并再截断」——最后一条正是「先 LIMIT 再排序」会静默弄错的地方。
 *
 * 另外锁死一件结构上的事：索引行**不是**完整词卡快照。见
 * [index row keeps only query and display columns, never a full card snapshot]。
 */
class VocabularySearchIndexTest {
    @Test
    fun `exact outranks prefix outranks contains`() {
        val merged = VocabularySearchIndexRanker.merge(
            exact = listOf(entry("book:ability", "ability")),
            prefix = listOf(entry("book:abilities", "abilities")),
            contains = listOf(entry("book:disability", "disability")),
            limit = 30,
        )

        assertEquals(listOf("ability", "abilities", "disability"), merged.map { it.lemma })
    }

    @Test
    fun `a card matched by both exact and prefix appears once at its higher tier`() {
        val card = entry("book:ability", "ability")
        val merged = VocabularySearchIndexRanker.merge(
            exact = listOf(card),
            prefix = listOf(card, entry("book:abilities", "abilities")),
            contains = listOf(card),
            limit = 30,
        )

        assertEquals(listOf("ability", "abilities"), merged.map { it.lemma })
    }

    @Test
    fun `truncation happens after merging so an exact match is never pushed out by contains`() {
        val merged = VocabularySearchIndexRanker.merge(
            exact = listOf(entry("book:ability", "ability")),
            prefix = listOf(entry("book:abilities", "abilities"), entry("book:ability-able", "ability-able")),
            contains = listOf(entry("book:disability", "disability")),
            limit = 2,
        )

        assertEquals(listOf("ability", "abilities"), merged.map { it.lemma })
    }

    @Test
    fun `zero or negative limit yields nothing`() {
        val card = entry("book:ability", "ability")
        assertEquals(emptyList(), VocabularySearchIndexRanker.merge(listOf(card), emptyList(), emptyList(), 0))
        assertEquals(emptyList(), VocabularySearchIndexRanker.merge(listOf(card), emptyList(), emptyList(), -5))
    }

    @Test
    fun `same lemma in two word books stays as two separate entries`() {
        val merged = VocabularySearchIndexRanker.merge(
            exact = listOf(
                entry("book-a:ability", "ability"),
                entry("book-b:ability", "ability"),
            ),
            prefix = emptyList(),
            contains = emptyList(),
            limit = 30,
        )

        assertEquals(listOf("book-a", "book-b"), merged.map { it.wordBookId })
    }

    @Test
    fun `like wildcards in user input are escaped`() {
        assertEquals("a\\_b", escapeLike("a_b"))
        assertEquals("50\\%", escapeLike("50%"))
        assertEquals("back\\\\slash", escapeLike("back\\slash"))
        assertEquals("plain", escapeLike("plain"))
    }

    @Test
    fun `indexed term normalization collapses whitespace and lowercases`() {
        assertEquals("surf the internet", normalizeIndexedTerm("  Surf   the\tInternet "))
    }

    /**
     * 索引行只保留查询与结果列表展示必需的列，**不得再存整张词卡的 JSON 快照**。
     *
     * 2026-10-05 真机实测：45,290 行的 `cardJson` 合计 25.7 MB，占索引文本量的 86.5%，
     * 用户库因此从 270 KB 涨到 46.3 MB——而这些内容本来就在 assets 词书包里。
     *
     * 用反射断言「列本身不存在」，而不是断言「某次写入没带 JSON」：后者挡不住别人
     * 把字段加回来（哪怕当前分支没写到）。同理，如果将来确实要加列，这条测试会强制
     * 改的人先想清楚这一列是不是真的进了查询或结果列表。
     */
    @Test
    fun `index row keeps only query and display columns, never a full card snapshot`() {
        val columns = VocabularySearchIndexEntity::class.java.declaredFields
            // Kotlin 2.x 会为类额外合成 `$stable`（稳定推断用），它不是实体的一列。
            .filterNot { it.name.startsWith("$") }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf(
                "wordBookId", "cardId", "wordBookName", "dataVersion",
                "lemma", "normalizedLemma", "normalizedPhrases",
                // 结果列表要显示的两列：音标与中文释义。其余详情内容按 cardId 回源取。
                "ipa", "meaningZh",
            ),
            columns,
            "索引行必须只保留查询与结果列表所需的列；完整词卡不进索引",
        )
        assertTrue(
            columns.none { it.contains("json", ignoreCase = true) },
            "索引行不得出现任何 JSON 快照列：$columns",
        )
    }

    /**
     * 检索词项在索引里被压成一个字符串（`\u0001` 连接），评分时要能原样还原：
     * 首项是 lemma，其余是短语。丢掉短语会让「按短语查不到词」这种静默缺词无人察觉。
     */
    @Test
    fun `indexed search terms keep the lemma first and the phrases after it`() {
        assertEquals(
            listOf("ability", "to the best of my ability", "abilities"),
            decodeIndexedTerms("ability", encodeIndexedTerms(listOf("to the best of my ability", "abilities"))),
        )
        // 没有短语的词卡只有 lemma 一项，不会多出一个空词项。
        assertEquals(listOf("ability"), decodeIndexedTerms("ability", ""))
    }

    private fun entry(cardId: String, lemma: String) =
        VocabularySearchIndexEntry(
            wordBookId = cardId.substringBefore(':'),
            cardId = cardId,
            wordBookName = "词书",
            lemma = lemma,
            ipa = "/$lemma/",
            meaningZh = "释义",
            normalizedTerms = listOf(lemma),
        )
}
