package com.example.englishlearning.learning

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 前缀区间上界的性质验证。
 *
 * 前缀匹配从 `LIKE q || '%'`（用不上索引）换成区间比较 `[q, upperBound(q))`，
 * 语义等价全押在「这个上界是否恰好圈住所有以 q 为前缀的串」上。
 * 这里把这条性质直接钉住：对任意以 q 开头的串 s，都应有 `q <= s < upperBound(q)`。
 */
class PrefixUpperBoundTest {
    @Test
    fun `the bound is the last character incremented`() {
        assertEquals("r", prefixUpperBound("q"))
        assertEquals("abilj", prefixUpperBound("abili"))
        assertEquals("b", prefixUpperBound("a"))
        assertEquals("0", prefixUpperBound("/"))
    }

    @Test
    fun `the bound covers every string that starts with the prefix`() {
        val prefixes = listOf("a", "ab", "abili", "the", "one's", "50", "异")
        val extensions = listOf("", "z", "zzz", "A", " ", "~", "异国", "\uFFFF")
        for (prefix in prefixes) {
            val bound = prefixUpperBound(prefix)
            for (extension in extensions) {
                val value = prefix + extension
                assertTrue(
                    value >= prefix && value < bound,
                    "「$value」以「$prefix」为前缀，必须落在 [prefix, bound) 内：bound=$bound",
                )
            }
        }
    }

    /** 不是前缀的串**不得**被区间捞进来——否则前缀这一级会多出结果。 */
    @Test
    fun `strings that do not start with the prefix fall outside the bound`() {
        val prefix = "abili"
        val bound = prefixUpperBound(prefix)
        for (value in listOf("abilh", "abz", "b", "abil", "z")) {
            assertTrue(value < prefix || value >= bound, "「$value」不是「$prefix」的前缀串，不该落进区间")
        }
    }

    @Test
    fun `an empty query has an empty bound`() {
        assertEquals("", prefixUpperBound(""))
    }
}
