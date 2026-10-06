package com.example.englishlearning.core.storage.dao

/**
 * 词条索引三级查询的 SQL 常量——**`@Query` 与查询计划守卫测试共用同一份**。
 *
 * 为什么抽出来：批次 3 要证明「contains 不再全表扫描」。把 SQL 抄一份进测试，
 * 抄的那份迟早和生产漂移；共用常量以后，`EXPLAIN QUERY PLAN` 断言的就是生产真正执行的那条语句。
 *
 * 三级语义（必须分开查，不能合并成一条 `LIKE '%q%'` 加 `LIMIT`，理由见 DAO 文档）：
 * 1. [EXACT]：`normalizedLemma` 完全匹配，B 树等值查找；
 * 2. [PREFIX]：`normalizedLemma` 前缀匹配，B 树区间扫描；
 * 3. [CONTAINS]：包含匹配，先由 FTS4 gram 索引收窄候选集，再在候选集上用 `LIKE` 做精确判断。
 */
internal object VocabularySearchIndexSql {
    const val EXACT =
        "SELECT * FROM vocabulary_search_index WHERE normalizedLemma = :normalizedQuery " +
            "ORDER BY lemma, wordBookName LIMIT :limit"

    /**
     * 前缀匹配。**不用 `LIKE :q || '%'`**：`LIKE` 的索引优化只认「字面量或参数」，
     * 对 `? || '%'` 这种表达式不生效，实测计划是 `SCAN`（全表扫 45,290 行）。
     * 换成 `[q, q 的末字符加一)` 的区间比较后走同一条 B 树索引（实测 `SEARCH ... USING INDEX`），
     * 结果集与原 `LIKE` 逐行一致（两边都是归一化后的小写串）。
     */
    const val PREFIX =
        "SELECT * FROM vocabulary_search_index WHERE normalizedLemma >= :normalizedQuery " +
            "AND normalizedLemma < :prefixUpper ORDER BY normalizedLemma, wordBookName LIMIT :limit"

    /**
     * 包含匹配的**索引路径**：
     * 1. `g.tokens MATCH :gramPhrase` 用 FTS4 gram 索引把候选集收窄（只当**超集筛子**）；
     * 2. `LIKE '%…%'` 在候选集上做**精确**判断。
     *
     * 「索引收窄 + LIKE 精确」这套分工是结果等价性的保证：编码是逐字符同态映射，
     * 「子串」关系在编码后仍然成立，所以真命中的一行都不会被 FTS 漏掉；而 FTS 多选的假命中
     * 由 `LIKE` 删掉。两个 `LIKE` 的绑定参数刻意取了不同名字（`:needleForLemma` /
     * `:needleForPhrases`）——SQLite 里重名的命名参数共用一个绑定槽，分开写才不必依赖那条规则。
     */
    const val CONTAINS =
        "SELECT v.* FROM vocabulary_search_index v " +
            "JOIN vocabulary_search_gram g ON g.wordBookId = v.wordBookId AND g.cardId = v.cardId " +
            "WHERE g.tokens MATCH :gramPhrase " +
            "AND (v.normalizedLemma LIKE '%' || :needleForLemma || '%' ESCAPE '\\' " +
            "OR v.normalizedPhrases LIKE '%' || :needleForPhrases || '%' ESCAPE '\\') " +
            "ORDER BY v.normalizedLemma, v.wordBookName LIMIT :limit"

    /**
     * 包含匹配的**兜底路径**：查询串短于 3 个字符时没有 gram 可用（3-gram 编码的下限），
     * 只能回退整表 `LIKE`。这类查询（1~2 个字符）本来就极少且结果集巨大，
     * 不值得为它把索引做复杂。
     */
    const val CONTAINS_SHORT =
        "SELECT * FROM vocabulary_search_index " +
            "WHERE normalizedLemma LIKE '%' || :normalizedQuery || '%' ESCAPE '\\' " +
            "OR normalizedPhrases LIKE '%' || :normalizedQuery || '%' ESCAPE '\\' " +
            "ORDER BY normalizedLemma, wordBookName LIMIT :limit"
}
