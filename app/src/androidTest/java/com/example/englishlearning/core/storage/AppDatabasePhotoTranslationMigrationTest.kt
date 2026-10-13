package com.example.englishlearning.core.storage

import android.database.SQLException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * F3-03 拍照翻译历史表（v28→v29）。只有用户明确保存才写入；原图字节留在私有资产区，
 * Room 行只带 imageAssetId 引用（AGENTS.md「图片默认不保存」与备份媒体分离）。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabasePhotoTranslationMigrationTest {
    @Test
    fun migrateV28ToV29_createsPhotoTranslationRecords() {
        val helper =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                requireNotNull(AppDatabase::class.java.canonicalName),
                FrameworkSQLiteOpenHelperFactory(),
            )
        helper.createDatabase(TEST_DB, 28).close()

        helper.runMigrationsAndValidate(TEST_DB, 29, true, AppDatabase.MIGRATION_28_29).apply {
            assertTableExists("photo_translation_records")
            assertIndexExists(
                "photo_translation_records",
                "index_photo_translation_records_profileId_createdAtEpochMillis",
            )
            execSQL(
                "INSERT INTO photo_translation_records (recordId, profileId, sourceText, translatedText, engine, imageAssetId, createdAtEpochMillis) " +
                    "VALUES ('r1', 'default', 'Birds fly.', '鸟儿会飞。', 'MlKit', 'asset-9', 10)",
            )
            execSQL(
                "INSERT INTO photo_translation_records (recordId, profileId, sourceText, translatedText, engine, imageAssetId, createdAtEpochMillis) " +
                    "VALUES ('r2', 'default', 'Manual text.', '手动文本。', 'None', NULL, 20)",
            )
            assertThrows(SQLException::class.java) {
                execSQL(
                    "INSERT INTO photo_translation_records (recordId, profileId, sourceText, translatedText, engine, createdAtEpochMillis) " +
                        "VALUES ('r1', 'other', 'Dup.', '重复主键。', 'None', 30)",
                )
            }
            query(
                "SELECT profileId, sourceText, translatedText, engine, imageAssetId, createdAtEpochMillis " +
                    "FROM photo_translation_records ORDER BY createdAtEpochMillis DESC",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("default", cursor.getString(cursor.getColumnIndexOrThrow("profileId")))
                assertEquals("None", cursor.getString(cursor.getColumnIndexOrThrow("engine")))
                assertTrue("无图记录的 imageAssetId 必须为 NULL", cursor.isNull(cursor.getColumnIndexOrThrow("imageAssetId")))
                assertTrue(cursor.moveToNext())
                assertEquals("default", cursor.getString(cursor.getColumnIndexOrThrow("profileId")))
                assertEquals("Birds fly.", cursor.getString(cursor.getColumnIndexOrThrow("sourceText")))
                assertEquals("鸟儿会飞。", cursor.getString(cursor.getColumnIndexOrThrow("translatedText")))
                assertEquals("MlKit", cursor.getString(cursor.getColumnIndexOrThrow("engine")))
                assertEquals("asset-9", cursor.getString(cursor.getColumnIndexOrThrow("imageAssetId")))
                assertEquals(10L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAtEpochMillis")))
            }
            close()
        }
    }

    private fun SupportSQLiteDatabase.assertTableExists(table: String) {
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'").use { cursor ->
            assertTrue("expected table $table", cursor.moveToFirst())
        }
    }

    private fun SupportSQLiteDatabase.assertIndexExists(table: String, index: String) {
        query("PRAGMA index_list('$table')").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                found = found || cursor.getString(cursor.getColumnIndexOrThrow("name")) == index
            }
            assertTrue("expected index $index on $table", found)
        }
    }

    private companion object {
        const val TEST_DB = "migration-photo-translation-test"
    }
}
