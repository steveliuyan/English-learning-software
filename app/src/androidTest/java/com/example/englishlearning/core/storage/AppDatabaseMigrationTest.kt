package com.example.englishlearning.core.storage

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun migrateAllHistoricalSchemasWithoutDestructiveFallback() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 1).apply {
            insertStageOneRows()
            close()
        }

        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
            .openHelper
            .writableDatabase
            .apply {
                assertStageOneRowsPreserved()
                query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'local_profiles'").use {
                    cursor -> check(cursor.moveToFirst())
                }
                close()
            }
    }

    @Test
    fun migrateV3ToV4_preservesLearningDataAndCreatesTodayPlanTables() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 3).apply {
            insertWordBook()
            insertLearningProfile("default", "primary-school", 10)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4).apply {
            query("SELECT activeWordBookId, dailyNewTarget FROM learning_profiles WHERE profileId = 'default'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("primary-school", cursor.getString(0))
                assertEquals(10, cursor.getInt(1))
            }
            query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'today_plans'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'today_plan_tasks'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            close()
        }
    }

    @Test
    fun createV3Database_rejectsNonPositiveDailyNewTarget() {
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
            .apply {
                openHelper.writableDatabase.apply {
                    execSQL("PRAGMA foreign_keys = ON")
                    insertWordBook()
                    insertLearningProfile("valid-target", "primary-school", 1)
                    assertTargetConstraintRejected { insertLearningProfile("zero-target", "primary-school", 0) }
                    assertTargetConstraintRejected { insertLearningProfile("negative-target", "primary-school", -1) }
                    assertTargetConstraintRejected { updateLearningProfile("valid-target", 0) }
                    assertTargetConstraintRejected { updateLearningProfile("valid-target", -1) }
                }
                close()
            }
    }

    @Test
    fun migrateV2ToV3_preservesLocalProfileAndRejectsNonPositiveDailyTarget() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO local_profiles (id, displayName, createdAt) VALUES ('default', 'Ada', 1)")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3,
        ).apply {
            execSQL("PRAGMA foreign_keys = ON")
            query("SELECT displayName FROM local_profiles WHERE id = 'default'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Ada", cursor.getString(0))
            }
            insertWordBook()
            insertLearningProfile("default", "primary-school", 1)
            assertTargetConstraintRejected { insertLearningProfile("zero-target", "primary-school", 0) }
            assertTargetConstraintRejected { insertLearningProfile("negative-target", "primary-school", -1) }
            assertTargetConstraintRejected { updateLearningProfile("default", 0) }
            assertTargetConstraintRejected { updateLearningProfile("default", -1) }
            close()
        }
    }

    private fun migrationHelper() =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            requireNotNull(AppDatabase::class.java.canonicalName),
            FrameworkSQLiteOpenHelperFactory(),
        )

    private fun SupportSQLiteDatabase.insertStageOneRows() {
        execSQL("INSERT INTO schema_meta(`key`, `value`) VALUES ('migration-test', 'preserved')")
        execSQL("INSERT INTO asset_records(id, sha256, relativePath, byteSize, createdAt) VALUES ('asset-1', 'abc', 'assets/a', 3, 1)")
        execSQL("INSERT INTO key_aliases(purpose, alias, createdAt) VALUES ('ai', 'alias-1', 1)")
    }

    private fun SupportSQLiteDatabase.assertStageOneRowsPreserved() {
        query("SELECT value FROM schema_meta WHERE `key` = 'migration-test'").use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0) == "preserved")
        }
        query("SELECT sha256 FROM asset_records WHERE id = 'asset-1'").use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0) == "abc")
        }
        query("SELECT alias FROM key_aliases WHERE purpose = 'ai'").use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0) == "alias-1")
        }
    }

    private fun SupportSQLiteDatabase.insertWordBook() {
        execSQL(
            "INSERT INTO word_books (id, displayName, level, totalWords, dataVersion, sourceId) " +
                "VALUES ('primary-school', '小学', 'primary', 100, 'v1', 'source')",
        )
    }

    private fun SupportSQLiteDatabase.insertLearningProfile(
        profileId: String,
        wordBookId: String,
        dailyNewTarget: Int,
    ) {
        execSQL(
            "INSERT INTO learning_profiles (profileId, activeWordBookId, dailyNewTarget) " +
                "VALUES ('$profileId', '$wordBookId', $dailyNewTarget)",
        )
    }

    private fun SupportSQLiteDatabase.updateLearningProfile(profileId: String, dailyNewTarget: Int) {
        execSQL("UPDATE learning_profiles SET dailyNewTarget = $dailyNewTarget WHERE profileId = '$profileId'")
    }

    private fun assertTargetConstraintRejected(action: () -> Unit) {
        assertThrows(SQLiteConstraintException::class.java, action)
    }

    private companion object {
        const val TEST_DB = "app-database-migration-test"
    }
}
