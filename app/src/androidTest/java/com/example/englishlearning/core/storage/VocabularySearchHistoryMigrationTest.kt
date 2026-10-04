package com.example.englishlearning.core.storage

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VocabularySearchHistoryMigrationTest {
    @Test
    fun migrateV22ToV23_createsHistoryTableAndPreservesExistingRows() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            requireNotNull(AppDatabase::class.java.canonicalName),
            FrameworkSQLiteOpenHelperFactory(),
        )
        helper.createDatabase(TEST_DB, 22).apply {
            execSQL("INSERT INTO schema_meta(`key`, `value`) VALUES ('migration-test', 'preserved')")
            execSQL("INSERT INTO word_books(`id`, `displayName`, `level`, `totalWords`, `dataVersion`, `sourceId`) VALUES ('book-existing', 'Existing', 'A1', 1, 'v1', 'fixture')")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 23, true, AppDatabase.MIGRATION_22_23).apply {
            query("SELECT value FROM schema_meta WHERE `key` = 'migration-test'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("preserved", cursor.getString(0))
            }
            query("SELECT displayName FROM word_books WHERE id = 'book-existing'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Existing", cursor.getString(0))
            }
            query("PRAGMA table_info(vocabulary_search_history)").use { cursor ->
                val columns = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(
                    setOf("profileId", "normalizedQuery", "displayQuery", "searchCount", "firstSearchedAtEpochMillis", "lastSearchedAtEpochMillis", "representativeWordBookId", "representativeCardId"),
                    columns.toSet(),
                )
            }
            query("PRAGMA index_list('vocabulary_search_history')").use { cursor ->
                var found = false
                while (cursor.moveToNext()) found = found || cursor.getString(cursor.getColumnIndexOrThrow("name")) == "index_vocabulary_search_history_profileId_lastSearchedAtEpochMillis"
                assertTrue(found)
            }
            AppDatabase.MIGRATION_22_23.migrate(this)
            query("SELECT COUNT(*) FROM vocabulary_search_history").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM word_books WHERE id = 'book-existing'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "vocabulary-search-history-migration-test"
    }
}
