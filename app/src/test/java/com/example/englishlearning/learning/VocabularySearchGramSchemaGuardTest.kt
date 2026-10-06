package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 建表语句与 gram 编码之间的**防漂移**守卫。
 *
 * 字串索引的建表 DDL 放在 `core.storage.AppDatabase`（与其他迁移 DDL 同处一地），
 * 而 gram 编码在 `learning`；`tokenchars` 里那个字符必须与 [GRAM_FILLER] 一致，
 * 否则折叠出来的 gram 会被分词器当成分隔符切碎，短语查询随即漏命中——**静默缺词**。
 *
 * 两个常量分居不同包，没法共用一处定义，所以用这条测试把它们钉在一起。
 */
class VocabularySearchGramSchemaGuardTest {
    @Test
    fun `the tokenizer tokenchar matches the gram filler`() {
        val ddl = AppDatabase.VOCABULARY_SEARCH_GRAM_DDL
        assertTrue(
            ddl.contains("tokenchars=$GRAM_FILLER"),
            "建表语句的 tokenchars 必须等于 GRAM_FILLER（$GRAM_FILLER），否则 gram 会被切碎：$ddl",
        )
    }

    /**
     * 必须落在 FTS4 上。真机探针实测 Android 13 的 framework SQLite 是 3.32.2，
     * `fts5` **未编译**、`trigram` 要求 ≥3.34——「FTS5 虚拟表」这台设备上根本不可用。
     */
    @Test
    fun `the index is built on fts4 never fts5`() {
        val ddl = AppDatabase.VOCABULARY_SEARCH_GRAM_DDL.lowercase()
        assertTrue(ddl.contains("using fts4("), "字串索引必须建在设备可用的 FTS4 上：$ddl")
        assertFalse(ddl.contains("fts5"), "本机没有 FTS5，写 fts5 会在真机上直接失败：$ddl")
        assertFalse(ddl.contains("trigram"), "trigram 分词器要求 SQLite ≥3.34，本机是 3.32.2：$ddl")
        // FTS 子串方案依赖 gram 是单个 token：折叠字符必须出现在 tokenize 参数里。
        assertTrue(ddl.contains("tokenize="), "必须声明分词器，否则会用默认分词器（丢弃 tokenchars）：$ddl")
    }

    @Test
    fun `the store writes into the same table the schema creates`() {
        assertTrue(
            VocabularySearchGramStore.INSERT_SQL.contains(AppDatabase.VOCABULARY_SEARCH_GRAM_TABLE),
            "写入口与建表语句必须是同一张表：${VocabularySearchGramStore.INSERT_SQL}",
        )
        assertTrue(AppDatabase.VOCABULARY_SEARCH_GRAM_DDL.contains(AppDatabase.VOCABULARY_SEARCH_GRAM_TABLE))
    }

    /**
     * gram 与屏障的长度关系：屏障必须**不是** 3 个字符，否则它可能恰好等于某个 gram，
     * 「不跨词项命中」这条就失效了。
     */
    @Test
    fun `the barrier cannot be mistaken for a gram`() {
        assertTrue(GRAM_BARRIER.length != GRAM_LENGTH, "屏障长度不能等于 gram 长度")

        val needleGrams = gramTokensOf(GRAM_BARRIER)
        assertFalse(needleGrams.any { it == GRAM_BARRIER }, "屏障本身不能被切成自己")
    }
}
