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
    fun migrateV12ToV13_preservesAiProfileAndCreatesSpeechPreferencesWithoutCredentialFields() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 12).apply {
            execSQL(
                "INSERT INTO ai_profiles (profileId, displayName, websiteUrl, endpoint, model, capabilities, " +
                    "secretAlias, temperature, topP, maxTokens, timeoutSeconds, systemPromptTemplateId) " +
                    "VALUES ('existing-profile', 'Existing', 'https://example.com', 'https://example.com/v1', " +
                    "'model', 'Speech', 'alias', 0.7, 1.0, 256, 30, 'default')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 13, true, AppDatabase.MIGRATION_12_13).apply {
            query("SELECT displayName FROM ai_profiles WHERE profileId = 'existing-profile'").use { cursor ->
                assertTrue("the v12 AI Profile must survive the migration", cursor.moveToFirst())
                assertEquals("Existing", cursor.getString(0))
            }
            query("PRAGMA table_info(speech_preferences)").use { cursor ->
                val names = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(
                    setOf("preferenceId", "selectedEngine", "openAiProfileId", "miMoProfileId"),
                    names.toSet(),
                )
                assertFalse(names.any { it.contains("key", ignoreCase = true) || it.contains("endpoint", ignoreCase = true) })
            }
            close()
        }
    }

    @Test
    fun migrateV13ToV14_addsProviderKindDefaultingToOpenAiCompatibleWithoutLosingRows() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 13).apply {
            execSQL(
                "INSERT INTO ai_profiles (profileId, displayName, websiteUrl, endpoint, model, capabilities, " +
                    "secretAlias, temperature, topP, maxTokens, timeoutSeconds, systemPromptTemplateId) " +
                    "VALUES ('existing-profile', 'Existing', 'https://example.com', 'https://example.com/v1', " +
                    "'model', 'Speech', 'alias', 0.7, 1.0, 256, 30, 'default')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 14, true, AppDatabase.MIGRATION_13_14).apply {
            // 老行必须活下来，且 providerKind 回填为历史事实：全部是 OpenAI 兼容协议。
            query("SELECT displayName, providerKind FROM ai_profiles WHERE profileId = 'existing-profile'").use { cursor ->
                assertTrue("the v13 AI Profile must survive the migration", cursor.moveToFirst())
                assertEquals("Existing", cursor.getString(0))
                assertEquals("OPENAI_COMPATIBLE", cursor.getString(1))
            }
            close()
        }
    }

    @Test
    fun migrateV14ToV15_addsVoiceDefaultingToAutoWithoutLosingRows() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 14).apply {
            execSQL(
                "INSERT INTO ai_profiles (profileId, displayName, websiteUrl, endpoint, model, capabilities, " +
                    "secretAlias, temperature, topP, maxTokens, timeoutSeconds, systemPromptTemplateId, providerKind) " +
                    "VALUES ('existing-profile', 'Existing', 'https://example.com', 'https://example.com/v1', " +
                    "'model', 'Speech', 'alias', 0.7, 1.0, 256, 30, 'default', 'XIAOMI_MIMO')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 15, true, AppDatabase.MIGRATION_14_15).apply {
            // 老行活下来，voice 回填空串 = 自动音色，行为与升级前一致。
            query("SELECT displayName, providerKind, voice FROM ai_profiles WHERE profileId = 'existing-profile'").use { cursor ->
                assertTrue("the v14 AI Profile must survive the migration", cursor.moveToFirst())
                assertEquals("Existing", cursor.getString(0))
                assertEquals("XIAOMI_MIMO", cursor.getString(1))
                assertEquals("", cursor.getString(2))
            }
            close()
        }
    }

    @Test
    fun migrateV15ToV16_preservesAiSpeechAndArticleRowsAndCreatesCredentialFreeAiPreferences() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                "INSERT INTO ai_profiles (profileId, displayName, websiteUrl, endpoint, model, capabilities, " +
                    "secretAlias, temperature, topP, maxTokens, timeoutSeconds, systemPromptTemplateId, providerKind, voice) " +
                    "VALUES ('existing-profile', 'Existing', 'https://example.com', 'https://example.com/v1', " +
                    "'model', 'Text', 'alias', 0.7, 1.0, 256, 30, 'default-reading-v1', 'OPENAI_COMPATIBLE', '')",
            )
            execSQL(
                "INSERT INTO speech_preferences (preferenceId, selectedEngine, openAiProfileId, miMoProfileId) " +
                    "VALUES ('device', 'SystemTts', NULL, NULL)",
            )
            execSQL(
                "INSERT INTO articles (articleId, profileId, localDate, activeWordBookId, articleType, lengthTier, " +
                    "version, title, englishText, chineseText, generatedAtEpochMillis, coveredLemmas, " +
                    "parameterSummary, modelName, sourceType, sourceId, sourceDisplayName, sourceUrl, " +
                    "sourceLicenseNote, sourceAttribution) VALUES ('article-1', 'default', '2026-09-27', " +
                    "'primary-school', 'STORY', 'STANDARD', 1, 'Today', 'English', '中文', 1, '[]', '', " +
                    "'model', 'AI_GENERATED', '', '', '', '', '')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 16, true, AppDatabase.MIGRATION_15_16).apply {
            query("SELECT displayName FROM ai_profiles WHERE profileId = 'existing-profile'").use { cursor ->
                assertTrue("the v15 AI Profile must survive the migration", cursor.moveToFirst())
                assertEquals("Existing", cursor.getString(0))
            }
            query("SELECT selectedEngine FROM speech_preferences WHERE preferenceId = 'device'").use { cursor ->
                assertTrue("the v15 speech preference must survive the migration", cursor.moveToFirst())
                assertEquals("SystemTts", cursor.getString(0))
            }
            query("SELECT title FROM articles WHERE articleId = 'article-1'").use { cursor ->
                assertTrue("the v15 article must survive the migration", cursor.moveToFirst())
                assertEquals("Today", cursor.getString(0))
            }
            query("PRAGMA table_info(ai_preferences)").use { cursor ->
                val names = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(setOf("preferenceId", "defaultTextProfileId"), names.toSet())
                assertFalse(names.any { it.contains("key", ignoreCase = true) || it.contains("secret", ignoreCase = true) || it.contains("endpoint", ignoreCase = true) || it.contains("model", ignoreCase = true) })
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

    @Test
    fun migrateV16ToV17_createsWordAiNotes() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 16).close()

        helper.runMigrationsAndValidate(TEST_DB, 17, true, AppDatabase.MIGRATION_16_17).apply {
            assertTableExists("word_ai_notes")
            assertIndexExists("word_ai_notes", "index_word_ai_notes_profileId_lemma")
            execSQL(
                "INSERT INTO word_ai_notes (noteId, profileId, lemma, kind, answer, createdAtEpochMillis) " +
                    "VALUES ('n1', 'default', 'apple', 'Sentence', 'I ate an apple.', 10)",
            )
            query("SELECT lemma, kind, answer FROM word_ai_notes WHERE profileId = 'default'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("apple", cursor.getString(0))
                assertEquals("Sentence", cursor.getString(1))
                assertEquals("I ate an apple.", cursor.getString(2))
            }
            close()
        }
    }

    @Test
    fun migrateV17ToV18_addsDefaultImageProfileId() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 17).apply {
            execSQL("INSERT INTO ai_preferences (preferenceId, defaultTextProfileId) VALUES ('device', 'p-text')")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 18, true, AppDatabase.MIGRATION_17_18).apply {
            query("SELECT defaultTextProfileId, defaultImageProfileId FROM ai_preferences WHERE preferenceId = 'device'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("p-text", cursor.getString(0))
                assertTrue("迁移后新列必须是 NULL，不能伪造一个默认值", cursor.isNull(1))
            }
            execSQL("UPDATE ai_preferences SET defaultImageProfileId = 'p-img' WHERE preferenceId = 'device'")
            query("SELECT defaultImageProfileId FROM ai_preferences WHERE preferenceId = 'device'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("p-img", cursor.getString(0))
            }
            close()
        }
    }

    /**
     * v21→v22 是历史遗留 id 的一次性对齐：早期版本把卡片 id 写成 `placeholder:<册>:<词>`，
     * 真实词书交付 `<册>:<词>`，前缀不同意味着进度、到期队列、新词去重全都取不到交集。
     *
     * 这条用例把三件事一起钉住：三张表都改写、撞主键时按「复习优先」丢行、计划目标数收敛到实际任务数
     * （目标数悬空就等于当天永远解锁不了文章）。
     */
    @Test
    fun migrateV21ToV22_realignsLegacyPlaceholderCardIds() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 21).apply {
            execSQL(
                "INSERT INTO today_plans (planId, profileId, localDate, zoneId, activeWordBookId, " +
                    "newTarget, dueTarget, ruleVersion, generatedAtEpochMillis) " +
                    "VALUES ('p1', 'default', '2026-10-04', 'Asia/Shanghai', 'cet4', 3, 2, 'f1-v1', 1)",
            )
            // 新学 3 个，其中 ability 与下面那条到期复习是同一个词——正是去重失效留下的重复。
            listOf("cet4:a", "cet4:ability", "cet4:able").forEachIndexed { index, cardId ->
                execSQL(
                    "INSERT INTO today_plan_tasks (planId, cardId, taskKind, ordinal) " +
                        "VALUES ('p1', '$cardId', 'NEW', $index)",
                )
            }
            listOf("placeholder:cet4:ability", "placeholder:cet4:benefit").forEachIndexed { index, cardId ->
                execSQL(
                    "INSERT INTO today_plan_tasks (planId, cardId, taskKind, ordinal) " +
                        "VALUES ('p1', '$cardId', 'DUE', $index)",
                )
            }
            execSQL(
                "INSERT INTO card_review_states (cardId, wordBookId, lastFeedback, lastReviewedAtEpochMillis, " +
                    "nextReviewAtEpochMillis) VALUES ('placeholder:cet4:ability', 'cet4', 'Good', 1, 2)",
            )
            execSQL(
                "INSERT INTO learning_events (eventId, profileId, planId, cardId, wordBookId, feedback, " +
                    "occurredAtEpochMillis, algorithmVersion, paramsVersion, dueBeforeEpochMillis, " +
                    "nextReviewAtEpochMillis) " +
                    "VALUES ('e1', 'default', 'p1', 'placeholder:cet4:benefit', 'cet4', 'Good', 1, 'fsrs-v1', 'p1', NULL, 2)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 22, true, AppDatabase.MIGRATION_21_22).apply {
            query("SELECT count(*) FROM card_review_states WHERE cardId LIKE 'placeholder:%'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            query("SELECT wordBookId FROM card_review_states WHERE cardId = 'cet4:ability'").use { cursor ->
                assertTrue("改写必须保住「哪一册的哪个词」，只改前缀", cursor.moveToFirst())
                assertEquals("cet4", cursor.getString(0))
            }
            query("SELECT cardId FROM learning_events WHERE eventId = 'e1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("cet4:benefit", cursor.getString(0))
            }
            query("SELECT count(*) FROM today_plan_tasks WHERE cardId LIKE 'placeholder:%'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            query("SELECT taskKind, count(*) FROM today_plan_tasks GROUP BY taskKind ORDER BY taskKind").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("DUE", cursor.getString(0))
                assertEquals("复习任务一条都不能少", 2, cursor.getInt(1))
                assertTrue(cursor.moveToNext())
                assertEquals("NEW", cursor.getString(0))
                assertEquals("ability 已经在复习队列里，新学那一份必须丢掉", 2, cursor.getInt(1))
            }
            query("SELECT newTarget, dueTarget FROM today_plans WHERE planId = 'p1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("newTarget 必须收敛到实际 NEW 任务数", 2, cursor.getInt(0))
                assertEquals("dueTarget 不该被动", 2, cursor.getInt(1))
            }
            close()
        }
    }

    /** 没有冲突行时只做改写，目标数一动不动——收敛逻辑不能误伤正常计划。 */
    @Test
    fun migrateV21ToV22_keepsPlanTargetsWhenNothingCollides() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 21).apply {
            execSQL(
                "INSERT INTO today_plans (planId, profileId, localDate, zoneId, activeWordBookId, " +
                    "newTarget, dueTarget, ruleVersion, generatedAtEpochMillis) " +
                    "VALUES ('p2', 'default', '2026-09-27', 'Asia/Shanghai', 'cet4', 2, 0, 'f1-v1', 1)",
            )
            listOf("placeholder:cet4:economy", "placeholder:cet4:feature").forEachIndexed { index, cardId ->
                execSQL(
                    "INSERT INTO today_plan_tasks (planId, cardId, taskKind, ordinal) " +
                        "VALUES ('p2', '$cardId', 'NEW', $index)",
                )
            }
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 22, true, AppDatabase.MIGRATION_21_22).apply {
            query("SELECT cardId FROM today_plan_tasks WHERE planId = 'p2' ORDER BY ordinal").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("cet4:economy", cursor.getString(0))
                assertTrue(cursor.moveToNext())
                assertEquals("cet4:feature", cursor.getString(0))
                assertFalse("没有冲突就不该删行", cursor.moveToNext())
            }
            query("SELECT newTarget, dueTarget FROM today_plans WHERE planId = 'p2'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
            }
            close()
        }
    }

    /** 同一个词同时存在两种 id 的复习状态时是纯重复：保留新格式那行，旧行必须让位。 */
    @Test
    fun migrateV21ToV22_dropsTheDuplicateLegacyReviewState() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 21).apply {
            execSQL(
                "INSERT INTO card_review_states (cardId, wordBookId, lastFeedback, lastReviewedAtEpochMillis, " +
                    "nextReviewAtEpochMillis) VALUES ('placeholder:cet4:ability', 'cet4', 'Again', 1, 2)",
            )
            execSQL(
                "INSERT INTO card_review_states (cardId, wordBookId, lastFeedback, lastReviewedAtEpochMillis, " +
                    "nextReviewAtEpochMillis) VALUES ('cet4:ability', 'cet4', 'Good', 7, 8)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 22, true, AppDatabase.MIGRATION_21_22).apply {
            query("SELECT count(*) FROM card_review_states").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("撞主键的两行必须合成一行，否则同一张卡会有两份进度", 1, cursor.getInt(0))
            }
            query("SELECT lastFeedback, lastReviewedAtEpochMillis FROM card_review_states").use { cursor ->
                assertTrue("活下来的是新格式那一行", cursor.moveToFirst())
                assertEquals("Good", cursor.getString(0))
                assertEquals(7L, cursor.getLong(1))
            }
            close()
        }
    }

    /**
     * v23 → v24：新增词条搜索索引表。
     *
     * 这一版迁移只加表、不改既有表，所以最需要证明的是「老数据一行不少」——
     * 搜索历史是用户可见的数据，建索引绝不能顺手把它重置掉。
     */
    @Test
    fun migrateV23ToV24_createsSearchIndexTableAndKeepsEveryExistingRow() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 23).apply {
            execSQL(
                "INSERT INTO vocabulary_search_history (profileId, normalizedQuery, displayQuery, searchCount, " +
                    "firstSearchedAtEpochMillis, lastSearchedAtEpochMillis, representativeWordBookId, representativeCardId) " +
                    "VALUES ('default', 'ability', 'Ability', 3, 1, 2, 'cet4', 'cet4:ability')",
            )
            execSQL(
                "INSERT INTO learning_settings (profileId, openDetailOnKnown, openDetailOnFuzzy, " +
                    "openDetailOnForgotten, showVocabularySearchCount) VALUES ('default', 1, 0, 0, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 24, true, AppDatabase.MIGRATION_23_24).apply {
            query("SELECT searchCount, normalizedQuery, representativeCardId FROM vocabulary_search_history WHERE profileId = 'default'").use { cursor ->
                assertTrue("v23 的搜索历史必须原样保留", cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
                assertEquals("ability", cursor.getString(1))
                assertEquals("cet4:ability", cursor.getString(2))
            }
            query("SELECT showVocabularySearchCount FROM learning_settings WHERE profileId = 'default'").use { cursor ->
                assertTrue("设置行也必须保留", cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'vocabulary_search_index'").use { cursor ->
                assertTrue("v24 必须建出词条索引表", cursor.moveToFirst())
            }
            query("PRAGMA table_info(vocabulary_search_index)").use { cursor ->
                val names = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(
                    "列顺序必须与实体声明一致，否则 Room 校验会失败",
                    listOf(
                        "wordBookId",
                        "cardId",
                        "wordBookName",
                        "dataVersion",
                        "lemma",
                        "normalizedLemma",
                        "normalizedPhrases",
                        "cardJson",
                    ),
                    names,
                )
            }
            // 迁移后的表要能真的用：写入一行再按 normalizedLemma 检索命中。
            execSQL(
                "INSERT INTO vocabulary_search_index VALUES " +
                    "('cet4', 'cet4:ability', '四级词书', 'v1', 'ability', 'ability', '', '{\"cardId\":\"cet4:ability\"}')",
            )
            query("SELECT cardId FROM vocabulary_search_index WHERE normalizedLemma = 'ability'").use { cursor ->
                assertTrue("索引表必须可写可查", cursor.moveToFirst())
                assertEquals("cet4:ability", cursor.getString(0))
            }
            close()
        }
    }

    /**
     * v24 → v25：索引行从「整张词卡的 JSON 快照」精简为查询与结果列表所需的列。
     *
     * 三条要一起成立：
     * 1. 新表**没有** `cardJson`、有 `ipa` / `meaningZh`（结果列表要显示的列）；
     * 2. 索引是派生数据，允许重建，所以迁移里直接 `DROP` 重来——但**别的业务表一行都不能少**；
     * 3. 新形状的表必须真的可写可查，否则重建出来的索引也建不起来。
     */
    @Test
    fun migrateV24ToV25_dropsTheCardSnapshotColumnAndKeepsEveryOtherRow() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 24).apply {
            execSQL(
                "INSERT INTO vocabulary_search_index VALUES " +
                    "('cet4', 'cet4:ability', '四级词书', 'v1', 'ability', 'ability', '', " +
                    "'{\"cardId\":\"cet4:ability\",\"ipa\":\"/əˈbɪləti/\",\"meaningZh\":\"能力\"}')",
            )
            execSQL(
                "INSERT INTO vocabulary_search_history (profileId, normalizedQuery, displayQuery, searchCount, " +
                    "firstSearchedAtEpochMillis, lastSearchedAtEpochMillis, representativeWordBookId, representativeCardId) " +
                    "VALUES ('default', 'ability', 'Ability', 5, 1, 2, 'cet4', 'cet4:ability')",
            )
            execSQL(
                "INSERT INTO learning_settings (profileId, openDetailOnKnown, openDetailOnFuzzy, " +
                    "openDetailOnForgotten, showVocabularySearchCount) VALUES ('default', 1, 0, 0, 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 25, true, AppDatabase.MIGRATION_24_25).apply {
            query("PRAGMA table_info(vocabulary_search_index)").use { cursor ->
                val names = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(
                    "索引行必须只剩查询与结果列表所需的列",
                    listOf(
                        "wordBookId",
                        "cardId",
                        "wordBookName",
                        "dataVersion",
                        "lemma",
                        "normalizedLemma",
                        "normalizedPhrases",
                        "ipa",
                        "meaningZh",
                    ),
                    names,
                )
                assertFalse("整卡 JSON 快照列必须消失", names.contains("cardJson"))
            }
            query("SELECT searchCount, representativeCardId FROM vocabulary_search_history WHERE profileId = 'default'").use { cursor ->
                assertTrue("搜索历史必须原样保留", cursor.moveToFirst())
                assertEquals(5, cursor.getInt(0))
                assertEquals("cet4:ability", cursor.getString(1))
            }
            query("SELECT showVocabularySearchCount FROM learning_settings WHERE profileId = 'default'").use { cursor ->
                assertTrue("学习设置必须原样保留", cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            // 重建出来的表要能真的用：写入一行再按 normalizedLemma 检索命中。
            execSQL(
                "INSERT INTO vocabulary_search_index VALUES " +
                    "('cet4', 'cet4:ability', '四级词书', 'v1', 'ability', 'ability', '', '/əˈbɪləti/', '能力')",
            )
            query("SELECT ipa, meaningZh FROM vocabulary_search_index WHERE normalizedLemma = 'ability'").use { cursor ->
                assertTrue("瘦身后的索引表必须可写可查", cursor.moveToFirst())
                assertEquals("/əˈbɪləti/", cursor.getString(0))
                assertEquals("能力", cursor.getString(1))
            }
            close()
        }
    }
}
