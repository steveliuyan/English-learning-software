package com.example.englishlearning.core.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设备端 SQLite 能力记录（**只读探针，不建业务表、不碰用户库**）。
 *
 * 批次 3 原本计划「FTS5 虚拟表 + `trigram` 分词器」来实现 contains 的任意子串匹配。
 * 真机实测（设备 bf353dda / Android 13）推翻了它：framework SQLite 是 **3.32.2**，
 * `fts3`/`fts4` 可用、**`fts5` 未编译**，`trigram` 还额外要求 SQLite ≥3.34。
 *
 * 这条用例把该事实**钉成可执行记录**：任何想在这台设备上改用 FTS5/trigram 的改动都会先在这里
 * 失败，从而被迫读到这个结论，而不是等到真机上出问题才发现。方案因此落在 FTS4 + 手写 3-gram
 * 上，见 `VocabularySearchGram`。
 */
@RunWith(AndroidJUnit4::class)
class SqliteFtsCapabilityProbeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file: File = File(context.cacheDir, "sqlite-fts-probe.db")
    private var db: SQLiteDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        db = null
        file.delete()
        for (suffix in listOf("-wal", "-shm", "-journal")) File(file.path + suffix).delete()
    }

    private fun open(): SQLiteDatabase {
        file.delete()
        return SQLiteDatabase.openOrCreateDatabase(file, null).also { db = it }
    }

    private fun SQLiteDatabase.canCreate(module: String, extra: String = ""): Boolean =
        runCatching { execSQL("CREATE VIRTUAL TABLE probe_$module USING $module(a$extra)") }.isSuccess

    private fun SQLiteDatabase.scalar(sql: String): String? =
        rawQuery(sql, null).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun SQLiteDatabase.plan(sql: String): List<String> =
        rawQuery("EXPLAIN QUERY PLAN $sql", null).use { cursor ->
            buildList {
                val detail = cursor.getColumnIndex("detail")
                while (cursor.moveToNext()) add(cursor.getString(detail))
            }
        }

    @Test
    fun fts4IsAvailableAndFts5IsNot() {
        val database = open()
        val version = database.scalar("SELECT sqlite_version()")
        val fts3 = database.canCreate("fts3")
        val fts4 = database.canCreate("fts4")
        val fts5 = database.canCreate("fts5")
        val trigram = runCatching {
            database.execSQL("CREATE VIRTUAL TABLE probe_tri USING fts5(a, tokenize='trigram')")
        }.isSuccess

        assertTrue("设计依赖 FTS4，它必须可用：sqlite=$version fts3=$fts3 fts4=$fts4", fts4)
        assertEquals(
            "设备能力记录：sqlite=$version fts3=$fts3 fts4=$fts4 fts5=$fts5 trigram=$trigram。" +
                "若 FTS5 变为可用，说明换了设备或换了 SQLite 引擎，届时可以重新评估用原生 trigram 子串搜索" +
                "（索引 8.8MB，比手写 3-gram 的 ~11.7MB 更省）——但那是一次架构变更，需要重新决策。",
            false,
            fts5,
        )
        assertFalse("trigram 依赖 FTS5，同样不可用", trigram)
    }

    /**
     * 记录「为什么需要字串索引」：基表上的 `LIKE '%q%'` 永远是整表扫描。
     * 这条查询是**故意**写成生产修复前的形状，作为缺陷的常驻记录。
     */
    @Test
    fun likeContainsOnTheBaseTableIsAlwaysAFullScan() {
        val database = open()
        database.execSQL(
            "CREATE TABLE vocab(`wordBookId` TEXT NOT NULL, `cardId` TEXT NOT NULL, `wordBookName` TEXT NOT NULL, " +
                "`dataVersion` TEXT NOT NULL, `lemma` TEXT NOT NULL, `normalizedLemma` TEXT NOT NULL, " +
                "`normalizedPhrases` TEXT NOT NULL, `ipa` TEXT NOT NULL, `meaningZh` TEXT NOT NULL, " +
                "PRIMARY KEY(`wordBookId`, `cardId`))",
        )
        database.execSQL("CREATE INDEX `index_vocabulary_search_index_normalizedLemma` ON `vocab` (`normalizedLemma`)")

        val plan = database.plan(
            "SELECT * FROM vocab WHERE normalizedLemma LIKE '%ability%' " +
                "OR normalizedPhrases LIKE '%ability%' ORDER BY normalizedLemma, wordBookName LIMIT 30",
        )
        assertTrue(
            "基表 LIKE '%q%' 必然是整表扫描——这正是 contains 必须走字串索引的原因：$plan",
            plan.any { it.contains("SCAN") && it.contains("vocab") },
        )
    }
}
