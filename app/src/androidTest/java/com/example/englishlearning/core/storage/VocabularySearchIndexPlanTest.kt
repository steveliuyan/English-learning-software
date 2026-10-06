package com.example.englishlearning.core.storage

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.core.storage.dao.VocabularySearchIndexSql
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 词条索引三级查询的**查询计划守卫**（批次 3 的判据）。
 *
 * 为什么用 `EXPLAIN QUERY PLAN` 而不是计时：批次 3 要修的是「contains 与 prefix 在 45,290 行的
 * 表上全表扫描」这两件**结构性缺陷**。计划里走不走索引是确定性的、可机器判定的，
 * 而耗时受设备负载影响会变成假红假绿。计划行里的 `SCAN <表>` 就是「全表扫描」本身。
 *
 * 判据用**生产真正执行的那条 SQL**（[VocabularySearchIndexSql] 与 `@Query` / `@RawQuery` 同源），
 * 不是在测试里另抄一份——抄一份就迟早漂移。
 *
 * 断言刻意不写成「包含 `SEARCH vocabulary_search_index USING INDEX`」这种整串匹配：
 * Android 的 SQLite 3.32 输出的是 `SEARCH TABLE vocabulary_search_index USING INDEX ...`，
 * 多了个 `TABLE`，整串匹配会假红。这里改成分别判 `SEARCH` / `SCAN` + 表名。
 */
@RunWith(AndroidJUnit4::class)
class VocabularySearchIndexPlanTest {
    /**
     * contains 的候选必须来自 gram 索引的 `MATCH`——这是「索引真被用上」的契约。
     *
     * 单独成一条，不混进计划断言：先失败的断言会中断用例，后面的**从未执行**。
     * 而计划本身判不出这件事——虚拟表的计划行不论用不用索引都写着
     * `VIRTUAL TABLE INDEX`（实测：把 `MATCH` 换成 `LIKE` 后计划行形状不变，断言照样绿）。
     * 所以这条盯的是生产 SQL 文本。
     */
    @Test
    fun containsAsksTheGramIndexForCandidates() {
        assertTrue(
            "contains 必须用 MATCH 从 gram 索引取候选，否则退化成整表扫描：" + VocabularySearchIndexSql.CONTAINS,
            VocabularySearchIndexSql.CONTAINS.contains("MATCH :gramPhrase"),
        )
    }

    /**
     * 并且计划里不能出现对基表的整表扫描——45,290 行全扫正是批次 3 要修的东西。
     *
     * RED（批次 3 前，真机实测）：`SCAN TABLE vocabulary_search_index USING INDEX ...`。
     */
    @Test
    fun containsMustNotFullScanTheBaseTable() {
        withMigratedDatabase { db ->
            val plan = db.explain(VocabularySearchIndexSql.CONTAINS, "\"abi bil ili\"", "abili", "abili", "30")
            assertFalse("contains 不能整表扫描基表：$plan", plan.fullScans(INDEX_TABLE))
        }
    }

    /**
     * prefix 必须命中 `normalizedLemma` 上的 B 树索引。
     *
     * RED（批次 3 前，真机实测）：
     * `SCAN TABLE vocabulary_search_index USING INDEX index_vocabulary_search_index_normalizedLemma`。
     */
    @Test
    fun prefixMustSearchTheNormalizedLemmaIndex() {
        withMigratedDatabase { db ->
            val plan = db.explain(VocabularySearchIndexSql.PREFIX, "ability", "abilit", "30")
            assertTrue("prefix 必须命中 normalizedLemma 索引：$plan", plan.searchesIndex(INDEX_TABLE))
            assertFalse("prefix 不能整表扫描：$plan", plan.fullScans(INDEX_TABLE))
        }
    }

    /** exact 本来就是索引等值查找——「两级不动」里的那一半，这条守着它别被顺手改坏。 */
    @Test
    fun exactKeepsSearchingTheIndex() {
        withMigratedDatabase { db ->
            val plan = db.explain(VocabularySearchIndexSql.EXACT, "ability", "30")
            assertTrue("exact 必须命中 normalizedLemma 索引：$plan", plan.searchesIndex(INDEX_TABLE))
        }
    }

    /**
     * v25→v26 迁移的三件事：建出 gram 表、**清空派生索引行**、新表可写可查。
     *
     * 为什么必须清空：gram 串只能由 Kotlin 计算（迁移是纯 SQL），所以升级后要由
     * `RefreshVocabularySearchIndexUseCase` 按 `dataVersion` 整册重建。若不清空，
     * `indexedVersions()` 会认为各册没变而跳过重建 → gram 表恒为空 → contains 查询恒空，
     * 而表建得好好的、迁移也「成功」——这正是最危险的静默失效。
     */
    @Test
    fun migration25To26CreatesTheGramIndexAndClearsDerivedRows() {
        val helper = migrationHelper()
        val before = helper.createDatabase(TEST_DB, VERSION_WITHOUT_GRAM_INDEX)
        val hadGramTable = before.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$GRAM_TABLE'",
        ).use { it.moveToFirst() }
        before.apply {
            execSQL(
                "INSERT INTO vocabulary_search_index VALUES " +
                    "('cet4', 'cet4:ability', '四级词书', 'v1', 'ability', 'ability', '', '/əˈbɪləti/', '能力')",
            )
            close()
        }
        assertFalse("v25 库上不该已有 gram 表，否则证明不了 gram 表是迁移建的", hadGramTable)

        helper.runMigrationsAndValidate(TEST_DB, VERSION_WITH_GRAM_INDEX, true, AppDatabase.MIGRATION_25_26).apply {
            query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$GRAM_TABLE'").use { cursor ->
                assertTrue("v26 必须建出 gram 虚拟表", cursor.moveToFirst())
            }
            query("SELECT count(*) FROM `$INDEX_TABLE`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("派生索引行必须被清空，交给启动时的重建流程灌回", 0, cursor.getInt(0))
            }
            // 新表必须真的可用：写一行再按 gram 短语查得到。
            execSQL(
                "INSERT INTO `$GRAM_TABLE`(`tokens`, `wordBookId`, `cardId`) " +
                    "VALUES ('abi bil ili', 'cet4', 'cet4:ability')",
            )
            query("SELECT cardId FROM `$GRAM_TABLE` WHERE `$GRAM_TABLE` MATCH ?", arrayOf("\"abi bil ili\"")).use { cursor ->
                assertTrue("gram 表必须可写可查", cursor.moveToFirst())
                assertEquals("cet4:ability", cursor.getString(0))
            }
            close()
        }
    }

    private fun withMigratedDatabase(block: (SupportSQLiteDatabase) -> Unit) {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, VERSION_WITHOUT_GRAM_INDEX).close()
        helper.runMigrationsAndValidate(TEST_DB, VERSION_WITH_GRAM_INDEX, true, AppDatabase.MIGRATION_25_26).apply {
            try {
                block(this)
            } finally {
                close()
            }
        }
    }

    private fun SupportSQLiteDatabase.explain(sql: String, vararg args: String): List<String> =
        query("EXPLAIN QUERY PLAN $sql", args).use { cursor ->
            buildList {
                val detail = cursor.getColumnIndex("detail")
                while (cursor.moveToNext()) add(cursor.getString(detail))
            }
        }

    /** 计划里有没有**等值/区间查找**该表上索引的那一条。 */
    private fun List<String>.searchesIndex(table: String): Boolean =
        any { it.contains("SEARCH") && it.contains(table) && it.contains("USING INDEX") }

    /** 计划里有没有对**该基表**的整表扫描。 */
    private fun List<String>.fullScans(table: String): Boolean =
        any { it.contains("SCAN") && it.contains(table) }

    private fun migrationHelper() = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(AppDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private companion object {
        const val TEST_DB = "vocabulary-search-index-plan-test"

        /** 批次 3 之前的最新版本：基表索引 + 两个 B 树索引，尚无字串索引。 */
        const val VERSION_WITHOUT_GRAM_INDEX = 25
        const val VERSION_WITH_GRAM_INDEX = 26

        const val INDEX_TABLE = "vocabulary_search_index"
        const val GRAM_TABLE = "vocabulary_search_gram"
    }
}
