package com.example.englishlearning.core.storage

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.dao.VocabularySearchIndexSql
import com.example.englishlearning.learning.VocabularySearchGramStore
import com.example.englishlearning.learning.decodeIndexedTerms
import com.example.englishlearning.learning.escapeLike
import com.example.englishlearning.learning.gramDocumentOf
import com.example.englishlearning.learning.gramPhraseOf
import com.example.englishlearning.learning.prefixUpperBound
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **真实语料上的等价性验证**（批次 3 的核心证据）。
 *
 * 把真机用户库复制到缓存目录（所有读写都发生在副本上，**不碰用户库**），在副本上建 gram 索引
 * （用的就是迁移里那条 DDL），再用**生产的编码函数**灌数据，然后比对「新路径」与「旧写法」的结果。
 *
 * 两个用例各自建一份语料，而不是塞进同一个方法：先失败的断言会中断用例，
 * 让后面的断言**从未执行**——分开才能证明每条断言都真的跑过。
 */
@RunWith(AndroidJUnit4::class)
class VocabularySearchGramIndexTest {
    /**
     * contains：`FTS4 gram 收窄 + LIKE 精确`必须与整表 `LIKE '%q%'` **逐行一致**（含排序与 LIMIT）。
     *
     * 这条之所以成立，靠的是编码是逐字符同态映射：「子串」关系不会因为折叠而丢失，
     * FTS 只可能多选、不可能漏选，多选的部分由 `LIKE` 删掉。
     */
    @Test
    fun realCorpusContainsResultsMatchTheFullTableScanExactly() {
        withRealCorpus { database, rows ->
            var comparedNonEmpty = 0
            for (needle in CONTAINS_NEEDLES) {
                val escaped = escapeLike(needle)
                val phrase = gramPhraseOf(needle)
                if (phrase == null) {
                    // 短于 3 字符没有 gram 可用——必须走兜底，不能拿空短语去 MATCH。
                    assertTrue("查询串「$needle」短于 3 字符，应当没有 gram 短语", needle.length < 3)
                    continue
                }
                val expected = database.keysOf(VocabularySearchIndexSql.CONTAINS_SHORT, escaped, LIMIT)
                val actual = database.keysOf(VocabularySearchIndexSql.CONTAINS, phrase, escaped, escaped, LIMIT)
                assertEquals("查询串「$needle」的索引路径必须与整表 LIKE 逐行一致", expected, actual)
                if (expected.isNotEmpty()) comparedNonEmpty += 1
            }
            assertTrue("所有 needle 都命中 0 行等于没测；至少要有若干条非空结果参与比对", comparedNonEmpty >= 5)
            // 语料规模自证：不是拿一份空库在跑「0 == 0」。
            assertEquals(rows.size, database.scalarCount("SELECT count(*) FROM `vocabulary_search_gram`"))
        }
    }

    /**
     * prefix：把 `LIKE q || '%'` 换成区间比较 `[q, q 末字符加一)` 之后，
     * 结果必须与旧写法逐行一致——性能手段不能改语义。
     */
    @Test
    fun realCorpusPrefixRangeMatchesTheLikeFormExactly() {
        withRealCorpus { database, _ ->
            var comparedNonEmpty = 0
            for (needle in PREFIX_NEEDLES) {
                val escaped = escapeLike(needle)
                val expected = database.keysOf(PREFIX_LIKE_REFERENCE, escaped, LIMIT)
                val actual = database.keysOf(
                    VocabularySearchIndexSql.PREFIX,
                    needle,
                    prefixUpperBound(needle),
                    LIMIT,
                )
                assertEquals("前缀「$needle」的区间查询必须与 LIKE 形式逐行一致", expected, actual)
                comparedNonEmpty += if (expected.isEmpty()) 0 else 1
            }
            assertTrue("至少要有一条前缀命中非空结果参与比对", comparedNonEmpty > 0)
        }
    }

    /** 短查询串没有 gram，只能回退——这是编码的下限，钉住它免得有人以为短串也走了索引。 */
    @Test
    fun needlesShorterThanTheGramLengthHaveNoPhrase() {
        assertNull(gramPhraseOf("a"))
        assertNull(gramPhraseOf("it"))
        assertNull(gramPhraseOf("a_"))
        assertTrue(gramPhraseOf("the") != null)
    }

    private fun withRealCorpus(block: (SQLiteDatabase, List<IndexRow>) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = context.getDatabasePath(SOURCE_DB)
        assumeTrue("真机用户库不存在（全新安装）时跳过：本用例验证真实语料上的等价性", source.isFile)

        val workspace = File(context.cacheDir, "batch3-gram-verify").apply {
            deleteRecursively()
            mkdirs()
        }
        val copy = File(workspace, SOURCE_DB)
        try {
            copyDatabase(source, copy)
            SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                assertTrue(
                    "用例前提是真机用户库已含基表索引（v25 起）：version=${database.version}",
                    database.version >= VERSION_BEFORE_BATCH_3,
                )
                // 迁移建的正是这条 DDL。库可能已经是 v26（应用跑过一次），
                // 所以先建（IF NOT EXISTS）再清空，保证灌进去的语料只有本用例这一份。
                database.execSQL(AppDatabase.VOCABULARY_SEARCH_GRAM_DDL)
                database.execSQL("DELETE FROM `vocabulary_search_gram`")
                val rows = database.readIndexRows()
                assertTrue("语料太小，这条等价性验证会失去意义：${rows.size}", rows.size > 1_000)
                populateGramIndex(database, rows)
                block(database, rows)
            }
        } finally {
            workspace.deleteRecursively()
        }
    }

    private fun copyDatabase(source: File, destination: File) {
        source.copyTo(destination, overwrite = true)
        for (suffix in listOf("-wal", "-shm")) {
            val side = File(source.path + suffix)
            if (side.isFile) side.copyTo(File(destination.path + suffix), overwrite = true)
        }
    }

    private fun populateGramIndex(database: SQLiteDatabase, rows: List<IndexRow>) {
        database.compileStatement(VocabularySearchGramStore.INSERT_SQL).use { insert ->
            for (row in rows) {
                insert.clearBindings()
                insert.bindString(1, gramDocumentOf(decodeIndexedTerms(row.normalizedLemma, row.normalizedPhrases)))
                insert.bindString(2, row.wordBookId)
                insert.bindString(3, row.cardId)
                insert.executeInsert()
            }
        }
    }

    private fun SQLiteDatabase.readIndexRows(): List<IndexRow> =
        rawQuery(
            "SELECT wordBookId, cardId, normalizedLemma, normalizedPhrases FROM `vocabulary_search_index`",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(IndexRow(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)))
                }
            }
        }

    private fun SQLiteDatabase.scalarCount(sql: String): Int =
        rawQuery(sql, null).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else -1 }

    /** 只比身份（`wordBookId` + `cardId`）：内容列在两条路径下读的是同一张表，比它没有信息量。 */
    private fun SQLiteDatabase.keysOf(sql: String, vararg args: String): List<String> =
        rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) + "\u0000" + cursor.getString(1))
            }
        }

    private data class IndexRow(
        val wordBookId: String,
        val cardId: String,
        val normalizedLemma: String,
        val normalizedPhrases: String,
    )

    private companion object {
        const val SOURCE_DB = "english-learning.db"
        const val VERSION_BEFORE_BATCH_3 = 25
        const val LIMIT = "30"

        /** 旧写法（参照系），只在本用例里当 oracle：`LIKE` 的索引优化对表达式不生效。 */
        const val PREFIX_LIKE_REFERENCE =
            "SELECT * FROM vocabulary_search_index WHERE normalizedLemma LIKE ? || '%' ESCAPE '\\' " +
                "ORDER BY normalizedLemma, wordBookName LIMIT ?"

        /**
         * 覆盖各种形态：词中片段、词首词尾、多词短语、撇号、连字符、斜杠、`%`、CJK。
         * 短于 3 字符的组合由 `needlesShorterThanTheGramLengthHaveNoPhrase` 覆盖。
         */
        val CONTAINS_NEEDLES = listOf(
            "abili", "bili", "ity", "the", "the ", "tion", "dis", "cern", "train", "zone",
            "ability", "no such", "absent-minded", "one's", "a/b", "50%", "can/", "异国风味",
        )

        val PREFIX_NEEDLES = listOf("ab", "abili", "the", "zone", "异", "can", "one", "x")
    }
}
