package com.example.englishlearning.learning

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 3-gram 编码的**纯逻辑**验证（JVM，不依赖设备）。
 *
 * 这里钉住三件事，它们正是「FTS4 加速路径不漏词」的前提：
 * 1. gram 只含 `[a-z0-9~]`——任何一个别的字符都会让分词器把 gram 切碎，短语查询随即漏命中；
 * 2. 词项之间被**[屏障]**隔开，短语查询不可能跨词项命中；
 * 3. **子串关系在同态映射后仍然成立**：needle 是 term 的子串 ⟹ needle 的 gram 序列
 *    是 term 的 gram 序列里的连续一段。这条是「不漏」的数学依据，也是 FTS 只当超集筛子、
 *    由 `LIKE` 在候选集上补做精确判断的前提。
 */
class VocabularySearchGramTest {
    @Test
    fun `encoding keeps ascii alphanumerics and folds everything else`() {
        assertEquals("ability", encodeForGram("ability"))
        assertEquals("cet4", encodeForGram("cet4"))
        assertEquals("to~the~best", encodeForGram("To The Best"))
        assertEquals("one~s", encodeForGram("one's"))
        assertEquals("absent~minded", encodeForGram("absent-minded"))
        assertEquals("a~b", encodeForGram("a/b"))
        assertEquals("a~b~c~", encodeForGram("a\u0001b(c)"))
    }

    @Test
    fun `every gram stays inside the token alphabet`() {
        val terms = listOf(
            "ability",
            "can/can't afford (to do)",
            "to the best of one's ability",
            "absent-minded",
            "异国风味",
            "cet4:ability",
            "end.period",
        )
        for (term in terms) {
            for (gram in gramTokensOf(term)) {
                assertEquals(GRAM_LENGTH, gram.length, "gram 长度必须恒为 $GRAM_LENGTH：$gram")
                assertTrue(
                    gram.all { it in 'a'..'z' || it in '0'..'9' || it == GRAM_FILLER },
                    "gram 混入了分词器眼里的分隔符就会被切碎（静默漏词）：$gram",
                )
            }
        }
    }

    @Test
    fun `terms shorter than the gram length produce no tokens`() {
        assertEquals(emptyList(), gramTokensOf("ab"))
        assertEquals(emptyList(), gramTokensOf("a"))
        assertEquals(emptyList(), gramTokensOf(""))
        assertEquals(listOf("abc"), gramTokensOf("abc"))
        assertEquals(listOf("ab~", "b~c", "~cd"), gramTokensOf("ab/cd"))
    }

    /**
     * 超集性质：needle 是 term 的子串 ⟹ needle 的 gram 序列是 term 的 gram 序列里连续的一段。
     * 反例（needle 不是子串）这里不要求不命中——那正是要靠 `LIKE` 在候选集上补做的那一步。
     */
    @Test
    fun `substring needles always appear as a consecutive run of the term grams`() {
        val cases = listOf(
            "disability" to listOf("abili", "bili", "dis", "ity", "disability", "ability"),
            "to the best of one's ability" to listOf("the best", "best of", "one's", "of one"),
            "absent-minded" to listOf("absent", "minded", "ent-m", "-mind", "t~m"),
        )
        for ((term, needles) in cases) {
            val termGrams = gramTokensOf(term)
            for (needle in needles) {
                val needleGrams = gramTokensOf(needle)
                assertTrue(needleGrams.isNotEmpty(), "needle 太短，测试数据不合适：$needle")
                assertTrue(
                    containsConsecutive(termGrams, needleGrams),
                    "「$needle」是「$term」的子串，其 gram 序列必须在词项 gram 里连续出现；" +
                        "term=$termGrams needle=$needleGrams",
                )
            }
        }
    }

    @Test
    fun `a document separates terms with the barrier so grams cannot span two terms`() {
        val document = gramDocumentOf(listOf("ability", "to the best"))
        // 屏障是 4 字符，任何 3-gram 都不可能等于它，needle 的 gram 序列也就永远跨不过去。
        assertEquals(4, GRAM_BARRIER.length, "屏障长度必须不是 3，否则它可能恰好等于某个 gram")
        assertEquals(
            "abi bil ili lit ity $GRAM_BARRIER to~ o~t ~th the he~ e~b ~be bes est",
            document,
            "逐词项切 gram、词项之间插屏障：这样才不会出现横跨两个词项的假 gram",
        )
    }

    @Test
    fun `a document with only short terms is empty`() {
        assertEquals("", gramDocumentOf(listOf("ab", "a")))
        assertEquals(gramTokensOf("ability").joinToString(" "), gramDocumentOf(listOf("ability", "ab")))
    }

    @Test
    fun `phrase expression is null for needles the gram path cannot serve`() {
        assertNull(gramPhraseOf("it"), "2 个字符没有 gram 可用，必须回退 LIKE")
        assertNull(gramPhraseOf("a"))
        assertNull(gramPhraseOf(""))
        assertEquals("\"abi bil ili\"", gramPhraseOf("abili"))
        assertEquals("\"no~ o~s ~su suc uch\"", gramPhraseOf("no such"))
    }

    private fun containsConsecutive(haystack: List<String>, needle: List<String>): Boolean =
        (0..haystack.size - needle.size).any { start ->
            needle.indices.all { haystack[start + it] == needle[it] }
        }
}
