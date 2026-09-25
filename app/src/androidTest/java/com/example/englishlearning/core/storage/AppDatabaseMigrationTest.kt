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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun migrateV7ToV8_createsAiProfileTableWithoutCredentialValueColumn() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 7).close()

        helper.runMigrationsAndValidate(TEST_DB, 8, true, AppDatabase.MIGRATION_7_8).apply {
            query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'ai_profiles'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            query("PRAGMA table_info(ai_profiles)").use { cursor ->
                val names = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertFalse(names.any { it.contains("key", ignoreCase = true) || it.contains("secretValue", ignoreCase = true) })
            }
            close()
        }
    }

    @Test
    fun migrateV8ToV9_addsGenerationProvenanceAndDisplayModeWithoutLosingRows() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 8).apply {
            // 造一行 v8 版文章与一条阅读偏好：既要证明老行没丢，也要证明新列给老行回填了默认值。
            execSQL(
                "INSERT INTO articles (articleId, profileId, localDate, activeWordBookId, articleType, lengthTier, " +
                    "version, title, englishText, chineseText, generatedAtEpochMillis, modelName) " +
                    "VALUES ('a1', 'default', '2026-09-24', 'primary-school', 'STORY', 'STANDARD', 1, " +
                    "'Old title', 'English body', '中文正文', 1, 'm1')",
            )
            execSQL(
                "INSERT INTO reading_preferences (profileId, defaultArticleType, explicitLengthTier) " +
                    "VALUES ('default', 'STORY', NULL)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9).apply {
            query("SELECT title, englishText, coveredLemmas, parameterSummary FROM articles WHERE articleId = 'a1'").use { cursor ->
                assertTrue("the v8 article row must survive the migration", cursor.moveToFirst())
                assertEquals("Old title", cursor.getString(0))
                assertEquals("English body", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals("", cursor.getString(3))
            }
            query("SELECT defaultArticleType, displayMode FROM reading_preferences WHERE profileId = 'default'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("STORY", cursor.getString(0))
                assertEquals("ENGLISH_FIRST", cursor.getString(1))
            }
            close()
        }
    }

    @Test
    fun migrateV9ToV10_addsSourceColumnsWithoutLosingRows() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 9).apply {
            // 造一行 v9 版文章与一条阅读偏好。v9 时代只有 AI 生成一条来源路径，所以老行只能
            // 回填成 AI_GENERATED；其余来源列必须是空串而不是 null（列声明为 NOT NULL）。
            execSQL(
                "INSERT INTO articles (articleId, profileId, localDate, activeWordBookId, articleType, lengthTier, " +
                    "version, title, englishText, chineseText, generatedAtEpochMillis, coveredLemmas, " +
                    "parameterSummary, modelName) " +
                    "VALUES ('a1', 'default', '2026-09-24', 'primary-school', 'STORY', 'STANDARD', 1, " +
                    "'Old title', 'English body', '中文正文', 1, '[\"story\"]', 'model=m1 temperature=0.7', 'm1')",
            )
            // displayMode 在 9.json 里没有 defaultValue（迁移 SQL 的 DEFAULT 不会写进 schema 导出），
            // 所以 v9 造数必须显式给值；只有走迁移的行才会被 DEFAULT 回填。
            execSQL(
                "INSERT INTO reading_preferences (profileId, defaultArticleType, explicitLengthTier, displayMode) " +
                    "VALUES ('default', 'STORY', NULL, 'ENGLISH_FIRST')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 10, true, AppDatabase.MIGRATION_9_10).apply {
            query(
                "SELECT title, coveredLemmas, parameterSummary, modelName, sourceType, sourceId, " +
                    "sourceDisplayName, sourceUrl, sourceLicenseNote, sourceAttribution " +
                    "FROM articles WHERE articleId = 'a1'",
            ).use { cursor ->
                assertTrue("the v9 article row must survive the migration", cursor.moveToFirst())
                assertEquals("Old title", cursor.getString(0))
                assertEquals("[\"story\"]", cursor.getString(1))
                assertEquals("model=m1 temperature=0.7", cursor.getString(2))
                assertEquals("m1", cursor.getString(3))
                assertEquals("AI_GENERATED", cursor.getString(4))
                assertEquals("", cursor.getString(5))
                assertEquals("", cursor.getString(6))
                assertEquals("", cursor.getString(7))
                assertEquals("", cursor.getString(8))
                assertEquals("", cursor.getString(9))
            }
            query("SELECT defaultArticleType, displayMode FROM reading_preferences WHERE profileId = 'default'").use { cursor ->
                assertTrue("the v9 preference row must survive the migration", cursor.moveToFirst())
                assertEquals("STORY", cursor.getString(0))
                assertEquals("ENGLISH_FIRST", cursor.getString(1))
            }
            close()
        }
    }

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

        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4).close()

        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
            .apply {
                openHelper.writableDatabase.apply {
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
                    assertTodayPlanConstraints()
                }
                close()
            }
    }

    @Test
    fun migrateV4ToV5_preservesPlansAndCreatesLearningEventStorage() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 4).apply {
            insertWordBook()
            insertLearningProfile("default", "primary-school", 10)
            insertTodayPlan("plan-1", "default", "2026-09-19")
            insertTodayPlanTask("plan-1", "card-1", "NEW", 0)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).apply {
            query("SELECT cardId FROM today_plan_tasks WHERE planId = 'plan-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("card-1", cursor.getString(0))
            }
            assertTableExists("learning_events")
            assertTableExists("card_review_states")
            assertIndexExists("learning_events", "index_learning_events_profileId_cardId")
            assertIndexExists("learning_events", "index_learning_events_planId")
            assertIndexExists("card_review_states", "index_card_review_states_wordBookId_nextReviewAtEpochMillis")
            close()
        }
    }

    @Test
    fun migrateV5ToV6_createsLearningSettingsWithDefaultsAndPreservesExistingData() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 5).apply {
            insertWordBook()
            insertLearningProfile("default", "primary-school", 10)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, *AppDatabase.MIGRATIONS).apply {
            assertTableExists("learning_settings")
            query(
                "SELECT profileId, openDetailOnKnown, openDetailOnFuzzy, openDetailOnForgotten " +
                    "FROM learning_settings WHERE profileId = 'default'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("default", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
            }
            query("SELECT displayName FROM word_books WHERE id = 'primary-school'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("小学", cursor.getString(0))
            }
            close()
        }
    }

    @Test
    fun migrateV6ToV7_createsArticleAndReadingPreferenceTables() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 6).apply {
            insertWordBook()
            insertLearningProfile("default", "primary-school", 10)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7).apply {
            assertTableExists("articles")
            assertTableExists("reading_preferences")
            assertIndexExists("articles", "index_articles_reuse_key")
            close()
        }
    }

    @Test
    fun freshV4Database_rejectsInvalidTodayPlanTaskKindOnInsertAndUpdate() {
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
            .apply {
                openHelper.writableDatabase.apply {
                    insertTodayPlan("plan-1", "default", "2026-09-19")
                    assertThrows(SQLiteConstraintException::class.java) {
                        insertTodayPlanTask("plan-1", "card-invalid", "INVALID", 0)
                    }
                    insertTodayPlanTask("plan-1", "card-new", "NEW", 0)
                    assertThrows(SQLiteConstraintException::class.java) {
                        execSQL("UPDATE today_plan_tasks SET taskKind = 'INVALID' WHERE planId = 'plan-1'")
                    }
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

    private fun SupportSQLiteDatabase.assertTodayPlanConstraints() {
        execSQL("PRAGMA foreign_keys = ON")
        insertTodayPlan("plan-1", "default", "2026-09-19")
        assertThrows(SQLiteConstraintException::class.java) {
            insertTodayPlan("plan-2", "default", "2026-09-19")
        }
        insertTodayPlanTask("plan-1", "card-new", "NEW", 0)
        assertThrows(SQLiteConstraintException::class.java) {
            insertTodayPlanTask("plan-1", "card-invalid", "INVALID", 1)
        }
        assertThrows(SQLiteConstraintException::class.java) {
            execSQL("UPDATE today_plan_tasks SET taskKind = 'INVALID' WHERE planId = 'plan-1'")
        }
        assertThrows(SQLiteConstraintException::class.java) {
            execSQL("DELETE FROM today_plans WHERE planId = 'plan-1'")
        }
        query("PRAGMA index_list('today_plan_tasks')").use { cursor ->
            var hasPlanIdIndex = false
            var hasPlanIdOrdinalIndex = false
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                hasPlanIdIndex = hasPlanIdIndex || name == "index_today_plan_tasks_planId"
                hasPlanIdOrdinalIndex = hasPlanIdOrdinalIndex || name == "index_today_plan_tasks_planId_ordinal"
            }
            assertTrue(hasPlanIdIndex)
            assertTrue(hasPlanIdOrdinalIndex)
        }
        query("PRAGMA foreign_key_list('today_plan_tasks')").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("NO ACTION", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }
    }

    private fun SupportSQLiteDatabase.insertTodayPlan(planId: String, profileId: String, localDate: String) {
        execSQL(
            "INSERT INTO today_plans (planId, profileId, localDate, zoneId, activeWordBookId, newTarget, dueTarget, ruleVersion, generatedAtEpochMillis) " +
                "VALUES ('$planId', '$profileId', '$localDate', 'Asia/Shanghai', 'primary-school', 1, 0, 'v1', 1)",
        )
    }

    private fun SupportSQLiteDatabase.insertTodayPlanTask(planId: String, cardId: String, taskKind: String, ordinal: Int) {
        execSQL(
            "INSERT INTO today_plan_tasks (planId, cardId, taskKind, ordinal) " +
                "VALUES ('$planId', '$cardId', '$taskKind', $ordinal)",
        )
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

    @Test
    fun migrateV10ToV11_addsShowLearnedMarksDefaultingToOn() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                "INSERT INTO reading_preferences (profileId, defaultArticleType, explicitLengthTier, displayMode) " +
                    "VALUES ('default', 'STORY', NULL, 'ENGLISH_FIRST')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 11, true, AppDatabase.MIGRATION_10_11).apply {
            query("SELECT showLearnedMarks FROM reading_preferences WHERE profileId = 'default'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrateV11ToV12_createsReadingCompletions() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 11).apply {
            execSQL(
                "INSERT INTO articles (articleId, profileId, localDate, activeWordBookId, articleType, lengthTier, " +
                    "version, title, englishText, chineseText, generatedAtEpochMillis, coveredLemmas, " +
                    "parameterSummary, modelName, sourceType, sourceId, sourceDisplayName, sourceUrl, " +
                    "sourceLicenseNote, sourceAttribution) " +
                    "VALUES ('a1', 'default', '2026-09-25', 'primary-school', 'STORY', 'STANDARD', 1, " +
                    "'T', 'E', 'C', 1, '[]', '', 'm1', 'AI_GENERATED', '', '', '', '', '')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 12, true, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12).apply {
            execSQL(
                "INSERT INTO reading_completions (articleId, profileId, localDate, completedAtEpochMillis) " +
                    "VALUES ('a1', 'default', '2026-09-25', 5)",
            )
            query("SELECT COUNT(*) FROM articles").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM reading_completions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }
}
