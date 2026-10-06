package com.example.englishlearning.learning

import java.util.Locale

/**
 * 词条索引的 **3-gram 编码**（批次 3）。
 *
 * ## 为什么需要它
 *
 * `contains` 这一级要判断「查询串是不是词项的任意子串」，用 `LIKE '%q%'` 只能全表扫描
 * （45,290 行）。SQLite 的 `trigram` 分词器能原生做这件事，但本机 Android 13 的 framework
 * SQLite 是 3.32.2——`fts3`/`fts4` 可用、**`fts5` 未编译**（探针实测），`trigram` 更无从谈起。
 *
 * 因此这里把「子串匹配」手工降到 `FTS4` 能表达的形式：**把词项切成 3-gram token**，
 * 于是「查询串是词项的子串」等价于「查询串的 gram 序列在词项里连续出现」，
 * 而后者正是 FTS 的**短语查询**（`"g1 g2 g3"`）能精确判断的（FTS 索引里存了位置）。
 *
 * ## 两个必须守住的约束
 *
 * 1. **gram 必须是单个 token。** 只要 gram 里混进一个分词器眼里的分隔符，它就会被切碎，
 *    短语查询随即漏命中——这是**静默缺词**，用户察觉不到。做法是把所有非 `[a-z0-9]`
 *    字符统一折叠成 [GRAM_FILLER] 并把它注册成 `tokenchars`，于是任何 gram 都只含
 *    `[a-z0-9~]`，与分词器的 Unicode 表无关（不依赖 Kotlin 与 SQLite 对「字母」的判定一致）。
 * 2. **折叠必须逐字符同态。** 折叠是非单射的（`a-b` 与 `a b` 都变成 `a~b`），
 *    所以 gram 命中只是**超集**：真的命中的一定被选中，假命中的可能被多选。
 *    真正的相等判断由 `LIKE` 在**候选集上**补做（见 `RoomVocabularySearchIndexRepository`），
 *    所以最终结果与全表 `LIKE` 逐行一致。逐字符映射保证「子串关系」在编码后仍然成立，
 *    这是「不漏」的关键。
 */

/** gram 长度。也是 FTS 加速路径能处理的最短查询长度：短于它只能回退 `LIKE`。 */
internal const val GRAM_LENGTH: Int = 3

/** 非字母数字字符统一折叠成它，并注册为 FTS `tokenchars`，保证 gram 永远是单个 token。 */
internal const val GRAM_FILLER: Char = '~'

/**
 * 词项之间的屏障 token。
 *
 * 4 个字符，而 gram 恰好 3 个字符，所以它**永远不可能等于某个 gram**，也就永远不会
 * 被 needle 的 gram 序列跨过——效果是「短语查询不会跨词项命中」。
 * 语料是英文词与短语，`zzzz` 出现在同一词项的 gram 序列里也不影响：
 * needle 的 gram 必须逐个相等，屏障占位就会打断匹配。
 */
internal const val GRAM_BARRIER: String = "zzzz"

/**
 * 把一个词项编码到 gram 字母表：`[a-z0-9]` 原样保留，其余一律折叠成 [GRAM_FILLER]。
 *
 * 先 `lowercase` 再折叠：查询串与词项走同一条路径，比较方向才一致。
 */
internal fun encodeForGram(value: String): String {
    val lowered = value.lowercase(Locale.ROOT)
    return buildString(lowered.length) {
        for (ch in lowered) {
            append(if (ch in 'a'..'z' || ch in '0'..'9') ch else GRAM_FILLER)
        }
    }
}

/** 编码串的全部 3-gram（按出现顺序，可能重复——重复对短语查询无影响）。 */
internal fun gramTokensOf(term: String): List<String> {
    val encoded = encodeForGram(term)
    if (encoded.length < GRAM_LENGTH) return emptyList()
    return (0..encoded.length - GRAM_LENGTH).map { offset ->
        encoded.substring(offset, offset + GRAM_LENGTH)
    }
}

/**
 * 把一张词卡的全部词项压成一行 FTS 内容：各词项的 gram 用 [GRAM_BARRIER] 隔开。
 *
 * 「各词项分别切 gram、而不是对拼接后的整串切」是**精确性要求**：
 * `normalizedPhrases` 用 `\u0001` 连接短语，而 `LIKE '%q%'` 的查询串里不会出现 `\u0001`，
 * 所以「整串命中」等价于「某一个词项命中」。逐词项切 gram + 屏障正是这条等价关系的编码。
 *
 * 返回值可能为空串：该卡所有词项都短于 3 字符时本来就不可能被 ≥3 字符的查询命中。
 */
internal fun gramDocumentOf(terms: List<String>): String =
    terms.asSequence()
        .map(::gramTokensOf)
        .filter { it.isNotEmpty() }
        .joinToString(" $GRAM_BARRIER ") { it.joinToString(" ") }

/**
 * 查询串对应的 FTS4 短语表达式（**含双引号**）。
 *
 * 长度不足 [GRAM_LENGTH] 时返回 `null`——这类查询（1~2 个字符）没有 gram 可用，
 * 调用方必须回退 `LIKE`，否则会漏结果。
 *
 * gram 只含 `[a-z0-9~]`，不含 FTS 查询语法字符（`"`/`*`/`-`/`:`），故拼进短语无需再转义。
 */
internal fun gramPhraseOf(query: String): String? {
    val grams = gramTokensOf(query)
    if (grams.isEmpty()) return null
    return "\"" + grams.joinToString(" ") + "\""
}
