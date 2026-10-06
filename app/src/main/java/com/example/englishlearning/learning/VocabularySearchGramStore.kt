package com.example.englishlearning.learning

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.VocabularySearchIndexEntity

/**
 * gram 表（FTS4 3-gram 字串索引）的同步：**所有写操作都必须由调用方放在事务内执行**。
 *
 * 与基表索引行的增删要么一起成功、要么一起不动，否则会出现「基表有行、字串索引没有」的
 * 不完整状态，表现为 contains 查不到词——静默缺词。
 *
 * 表名与建表 DDL 定义在 [AppDatabase]（和其他迁移 DDL 放在一起，只留一处），
 * 这里只负责按 `(wordBookId, cardId)` 回连基表的写入 SQL。
 */
internal object VocabularySearchGramStore {
    private const val TABLE = AppDatabase.VOCABULARY_SEARCH_GRAM_TABLE

    const val INSERT_SQL =
        "INSERT INTO `$TABLE`(`tokens`, `wordBookId`, `cardId`) VALUES (?, ?, ?)"

    private const val DELETE_BOOK_SQL = "DELETE FROM `$TABLE` WHERE `wordBookId` = ?"

    /** 整册替换：先删该册全部 gram 行，再按传入的索引行重建。 */
    fun replaceWordBook(
        database: SupportSQLiteDatabase,
        wordBookId: String,
        rows: List<VocabularySearchIndexEntity>,
    ) {
        deleteWordBook(database, wordBookId)
        if (rows.isEmpty()) return
        database.compileStatement(INSERT_SQL).use { insert ->
            for (row in rows) {
                insert.clearBindings()
                insert.bindString(
                    1,
                    gramDocumentOf(decodeIndexedTerms(row.normalizedLemma, row.normalizedPhrases)),
                )
                insert.bindString(2, row.wordBookId)
                insert.bindString(3, row.cardId)
                insert.executeInsert()
            }
        }
    }

    fun deleteWordBook(database: SupportSQLiteDatabase, wordBookId: String) {
        database.execSQL(DELETE_BOOK_SQL, arrayOf(wordBookId))
    }

    fun deleteMissing(database: SupportSQLiteDatabase, keepWordBookIds: Collection<String>) {
        val keep = keepWordBookIds.toList().ifEmpty { listOf(KEEP_NOTHING_WORD_BOOK) }
        val placeholders = keep.joinToString(",") { "?" }
        database.execSQL(
            "DELETE FROM `$TABLE` WHERE `wordBookId` NOT IN ($placeholders)",
            keep.toTypedArray(),
        )
    }
}
