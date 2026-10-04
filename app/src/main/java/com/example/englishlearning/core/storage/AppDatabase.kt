package com.example.englishlearning.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.englishlearning.core.storage.dao.InternalAiProfileDao
import com.example.englishlearning.core.storage.dao.InternalArticleDao
import com.example.englishlearning.core.storage.dao.InternalAssetDao
import com.example.englishlearning.core.storage.dao.InternalReadingPreferenceDao
import com.example.englishlearning.core.storage.dao.InternalSpeechPreferenceDao
import com.example.englishlearning.core.storage.dao.InternalReadingCompletionDao
import com.example.englishlearning.core.storage.dao.InternalLearningEventDao
import com.example.englishlearning.core.storage.dao.InternalLearningStatsDao
import com.example.englishlearning.core.storage.dao.InternalLearningProfileDao
import com.example.englishlearning.core.storage.dao.InternalLearningSettingsDao
import com.example.englishlearning.core.storage.dao.InternalProfileDao
import com.example.englishlearning.core.storage.dao.InternalTodayPlanDao
import com.example.englishlearning.core.storage.dao.InternalWordBookDao
import com.example.englishlearning.core.storage.dao.InternalWordBookProgressMigrationAuditDao
import com.example.englishlearning.core.storage.dao.InternalVocabularyEntryDao
import com.example.englishlearning.core.storage.dao.InternalVocabularySearchHistoryDao
import com.example.englishlearning.core.storage.entity.AiPreferenceEntity
import com.example.englishlearning.core.storage.entity.WordAiNoteEntity
import com.example.englishlearning.core.storage.entity.AiProfileEntity
import com.example.englishlearning.core.storage.entity.ArticleEntity
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.CardReviewStateEntity
import com.example.englishlearning.core.storage.entity.KeyAliasEntity
import com.example.englishlearning.core.storage.entity.LearningEventEntity
import com.example.englishlearning.core.storage.entity.LearningProfileEntity
import com.example.englishlearning.core.storage.entity.LearningSettingsEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import com.example.englishlearning.core.storage.entity.ReadingCompletionEntity
import com.example.englishlearning.core.storage.entity.ReadingPreferenceEntity
import com.example.englishlearning.core.storage.entity.SchemaMetaEntity
import com.example.englishlearning.core.storage.entity.SpeechPreferenceEntity
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity
import com.example.englishlearning.core.storage.entity.WordBookProgressMigrationAuditEntity
import com.example.englishlearning.core.storage.entity.VocabularyEntryEntity
import com.example.englishlearning.core.storage.entity.VocabularySearchHistoryEntity

/**
 * Versioned Room metadata store. Every version transition must be supplied through [MIGRATIONS].
 * Destructive migration fallback is intentionally never configured by callers.
 */
@Database(
    entities = [
        SchemaMetaEntity::class,
        AiProfileEntity::class,
        AiPreferenceEntity::class,
        ArticleEntity::class,
        ReadingPreferenceEntity::class,
        ReadingCompletionEntity::class,
        AssetRecordEntity::class,
        KeyAliasEntity::class,
        LocalProfileEntity::class,
        WordBookEntity::class,
        LearningProfileEntity::class,
        TodayPlanEntity::class,
        TodayPlanTaskEntity::class,
        LearningEventEntity::class,
        CardReviewStateEntity::class,
        LearningSettingsEntity::class,
        SpeechPreferenceEntity::class,
        WordAiNoteEntity::class,
        VocabularyEntryEntity::class,
        WordBookProgressMigrationAuditEntity::class,
        VocabularySearchHistoryEntity::class,
    ],
    version = 23,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    internal abstract fun internalAiProfileDao(): InternalAiProfileDao

    internal abstract fun internalAiPreferenceDao(): com.example.englishlearning.core.storage.dao.InternalAiPreferenceDao

    internal abstract fun internalWordAiNoteDao(): com.example.englishlearning.core.storage.dao.InternalWordAiNoteDao

    internal abstract fun internalArticleDao(): InternalArticleDao

    internal abstract fun internalReadingPreferenceDao(): InternalReadingPreferenceDao

    internal abstract fun internalReadingCompletionDao(): InternalReadingCompletionDao

    internal abstract fun internalSpeechPreferenceDao(): InternalSpeechPreferenceDao

    internal abstract fun internalAssetDao(): InternalAssetDao

    internal abstract fun internalProfileDao(): InternalProfileDao

    internal abstract fun internalWordBookDao(): InternalWordBookDao

    internal abstract fun internalLearningProfileDao(): InternalLearningProfileDao

    internal abstract fun internalLearningSettingsDao(): InternalLearningSettingsDao

    internal abstract fun internalTodayPlanDao(): InternalTodayPlanDao

    internal abstract fun internalLearningEventDao(): InternalLearningEventDao

    internal abstract fun internalLearningStatsDao(): InternalLearningStatsDao

    internal abstract fun internalVocabularyEntryDao(): InternalVocabularyEntryDao

    internal abstract fun internalVocabularySearchHistoryDao(): InternalVocabularySearchHistoryDao

    internal abstract fun internalWordBookProgressMigrationAuditDao(): InternalWordBookProgressMigrationAuditDao

    companion object {
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `local_profiles` " +
                            "(`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    )
                }
            }

        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `word_books` " +
                            "(`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                            "`level` TEXT NOT NULL, `totalWords` INTEGER NOT NULL, " +
                            "`dataVersion` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                            "PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `learning_profiles` " +
                            "(`profileId` TEXT NOT NULL, `activeWordBookId` TEXT NOT NULL, " +
                            "`dailyNewTarget` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`profileId`), FOREIGN KEY(`activeWordBookId`) " +
                            "REFERENCES `word_books`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_learning_profiles_activeWordBookId` " +
                            "ON `learning_profiles` (`activeWordBookId`)",
                    )
                    createDailyTargetConstraintTriggers(db)
                }
            }

        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `today_plans` " +
                            "(`planId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `localDate` TEXT NOT NULL, " +
                            "`zoneId` TEXT NOT NULL, `activeWordBookId` TEXT NOT NULL, `newTarget` INTEGER NOT NULL, " +
                            "`dueTarget` INTEGER NOT NULL, `ruleVersion` TEXT NOT NULL, " +
                            "`generatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`planId`))",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_today_plans_profileId_localDate` " +
                            "ON `today_plans` (`profileId`, `localDate`)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `today_plan_tasks` " +
                            "(`planId` TEXT NOT NULL, `cardId` TEXT NOT NULL, `taskKind` TEXT NOT NULL, " +
                            "`ordinal` INTEGER NOT NULL, PRIMARY KEY(`planId`, `cardId`), " +
                            "FOREIGN KEY(`planId`) REFERENCES `today_plans`(`planId`) " +
                            "ON UPDATE NO ACTION ON DELETE NO ACTION )",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_today_plan_tasks_planId` " +
                            "ON `today_plan_tasks` (`planId`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_today_plan_tasks_planId_ordinal` " +
                            "ON `today_plan_tasks` (`planId`, `ordinal`)",
                    )
                }
            }

        val MIGRATION_7_8: Migration =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `ai_profiles` " +
                            "(`profileId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `websiteUrl` TEXT NOT NULL, " +
                            "`endpoint` TEXT NOT NULL, `model` TEXT NOT NULL, `capabilities` TEXT NOT NULL, " +
                            "`secretAlias` TEXT NOT NULL, `temperature` REAL NOT NULL, `topP` REAL NOT NULL, " +
                            "`maxTokens` INTEGER NOT NULL, `timeoutSeconds` INTEGER NOT NULL, " +
                            "`systemPromptTemplateId` TEXT NOT NULL, PRIMARY KEY(`profileId`))",
                    )
                }
            }

        val MIGRATION_8_9: Migration =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // ALTER TABLE 加 NOT NULL 列必须带 DEFAULT，否则已有的文章行无法回填。
                    // 默认值刻意取空串/ENGLISH_FIRST：老文章没有生成来源信息，编一个假的比留空更糟。
                    db.execSQL(
                        "ALTER TABLE `articles` ADD COLUMN `coveredLemmas` TEXT NOT NULL DEFAULT ''",
                    )
                    db.execSQL(
                        "ALTER TABLE `articles` ADD COLUMN `parameterSummary` TEXT NOT NULL DEFAULT ''",
                    )
                    db.execSQL(
                        "ALTER TABLE `reading_preferences` ADD COLUMN `displayMode` TEXT NOT NULL DEFAULT 'ENGLISH_FIRST'",
                    )
                }
            }

        val MIGRATION_11_12: Migration =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // F3-02：阅读完成记录。articleId 做主键天然幂等——同一篇文章重复点完成只算一次。
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `reading_completions` " +
                            "(`articleId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `localDate` TEXT NOT NULL, " +
                            "`completedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`articleId`))",
                    )
                }
            }

        val MIGRATION_12_13: Migration =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `speech_preferences` " +
                            "(`preferenceId` TEXT NOT NULL, `selectedEngine` TEXT NOT NULL, " +
                            "`openAiProfileId` TEXT, `miMoProfileId` TEXT, PRIMARY KEY(`preferenceId`))",
                    )
                }
            }

        val MIGRATION_13_14: Migration =
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // MiMo 专用协议上线：老 Profile 全部是 OpenAI 兼容协议，DEFAULT 回填即历史事实。
                    db.execSQL(
                        "ALTER TABLE `ai_profiles` ADD COLUMN `providerKind` TEXT NOT NULL DEFAULT 'OPENAI_COMPATIBLE'",
                    )
                }
            }

        val MIGRATION_14_15: Migration =
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 语音角色（Talkify 式音色选择）：空串 = 自动，老 Profile 行为不变。
                    db.execSQL("ALTER TABLE `ai_profiles` ADD COLUMN `voice` TEXT NOT NULL DEFAULT ''")
                }
            }

        val MIGRATION_15_16: Migration =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `ai_preferences` " +
                            "(`preferenceId` TEXT NOT NULL, `defaultTextProfileId` TEXT, " +
                            "PRIMARY KEY(`preferenceId`))",
                    )
                }
            }

        val MIGRATION_16_17: Migration =
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // F3-03：词 AI 问答笔记。只存 lemma/kind/回答/时间，绝不存 Key 或 Endpoint。
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `word_ai_notes` " +
                            "(`noteId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `lemma` TEXT NOT NULL, " +
                            "`kind` TEXT NOT NULL, `answer` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`noteId`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_word_ai_notes_profileId_lemma` " +
                            "ON `word_ai_notes` (`profileId`, `lemma`)",
                    )
                }
            }

        val MIGRATION_17_18: Migration =
            object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // F3-04：默认生图服务选择。可空列，已有行保持 NULL，不伪造默认值。
                    db.execSQL("ALTER TABLE `ai_preferences` ADD COLUMN `defaultImageProfileId` TEXT")
                }
            }

        val MIGRATION_10_11: Migration =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // F3-01C：阅读标记偏好。已有行回填产品默认「开启标记」。
                    db.execSQL(
                        "ALTER TABLE `reading_preferences` ADD COLUMN `showLearnedMarks` INTEGER NOT NULL DEFAULT 1",
                    )
                }
            }

        val MIGRATION_9_10: Migration =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // ALTER TABLE 加 NOT NULL 列必须带 DEFAULT；已有行只能是 AI 生成，因为此前只有这一条来源。
                    // 不改 MIGRATION_8_9 而新开 v10：v9 库已在真机存在，保留版本号却改 schema 会让
                    // Room 因 identity hash 不匹配而校验失败。
                    db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceType` TEXT NOT NULL DEFAULT 'AI_GENERATED'")
                    db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceDisplayName` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceUrl` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceLicenseNote` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceAttribution` TEXT NOT NULL DEFAULT ''")
                }
            }

        val MIGRATION_6_7: Migration =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `articles` " +
                            "(`articleId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `localDate` TEXT NOT NULL, " +
                            "`activeWordBookId` TEXT NOT NULL, `articleType` TEXT NOT NULL, `lengthTier` TEXT NOT NULL, " +
                            "`version` INTEGER NOT NULL, `title` TEXT NOT NULL, `englishText` TEXT NOT NULL, " +
                            "`chineseText` TEXT NOT NULL, `generatedAtEpochMillis` INTEGER NOT NULL, `modelName` TEXT, " +
                            "PRIMARY KEY(`articleId`))",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_articles_reuse_key` " +
                            "ON `articles` (`profileId`, `localDate`, `activeWordBookId`, `articleType`, `lengthTier`, `version`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_articles_profileId_generatedAtEpochMillis` " +
                            "ON `articles` (`profileId`, `generatedAtEpochMillis`)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `reading_preferences` " +
                            "(`profileId` TEXT NOT NULL, `defaultArticleType` TEXT NOT NULL DEFAULT 'STORY', " +
                            "`explicitLengthTier` TEXT, PRIMARY KEY(`profileId`))",
                    )
                }
            }

        val MIGRATION_5_6: Migration =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `learning_settings` " +
                            "(`profileId` TEXT NOT NULL, `openDetailOnKnown` INTEGER NOT NULL DEFAULT 0, " +
                            "`openDetailOnFuzzy` INTEGER NOT NULL DEFAULT 1, " +
                            "`openDetailOnForgotten` INTEGER NOT NULL DEFAULT 1, " +
                            "PRIMARY KEY(`profileId`))",
                    )
                    db.execSQL(
                        "INSERT INTO `learning_settings` " +
                            "(`profileId`, `openDetailOnKnown`, `openDetailOnFuzzy`, `openDetailOnForgotten`) " +
                            "SELECT `profileId`, 0, 1, 1 FROM `learning_profiles` " +
                            "WHERE `profileId` NOT IN (SELECT `profileId` FROM `learning_settings`)",
                    )
                }
            }

        val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `learning_events` " +
                            "(`eventId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `planId` TEXT NOT NULL, " +
                            "`cardId` TEXT NOT NULL, `wordBookId` TEXT NOT NULL, `feedback` TEXT NOT NULL, " +
                            "`occurredAtEpochMillis` INTEGER NOT NULL, `algorithmVersion` TEXT NOT NULL, " +
                            "`paramsVersion` TEXT NOT NULL, `dueBeforeEpochMillis` INTEGER, " +
                            "`nextReviewAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`eventId`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_learning_events_profileId_cardId` " +
                            "ON `learning_events` (`profileId`, `cardId`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_learning_events_planId` " +
                            "ON `learning_events` (`planId`)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `card_review_states` " +
                            "(`cardId` TEXT NOT NULL, `wordBookId` TEXT NOT NULL, `lastFeedback` TEXT NOT NULL, " +
                            "`lastReviewedAtEpochMillis` INTEGER NOT NULL, " +
                            "`nextReviewAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`cardId`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS " +
                            "`index_card_review_states_wordBookId_nextReviewAtEpochMillis` " +
                            "ON `card_review_states` (`wordBookId`, `nextReviewAtEpochMillis`)",
                    )
                }
            }

        internal val CONSTRAINT_CALLBACK: RoomDatabase.Callback =
            object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createDailyTargetConstraintTriggers(db)
                    createTodayPlanTaskKindTrigger(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    createTodayPlanTaskKindTrigger(db)
                }
            }

        val MIGRATION_18_19: Migration =
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `vocabulary_entries` (`profileId` TEXT NOT NULL, `wordBookId` TEXT NOT NULL, `cardId` TEXT NOT NULL, `addedAtEpochMillis` INTEGER NOT NULL, `lastFeedback` TEXT NOT NULL, PRIMARY KEY(`profileId`, `wordBookId`, `cardId`))")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_entries_profileId_addedAtEpochMillis` ON `vocabulary_entries` (`profileId`, `addedAtEpochMillis`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_entries_profileId_wordBookId_addedAtEpochMillis` ON `vocabulary_entries` (`profileId`, `wordBookId`, `addedAtEpochMillis`)")
                }
            }

        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `word_ai_notes` ADD COLUMN `wordBookId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `word_ai_notes` ADD COLUMN `cardId` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `word_book_progress_migration_audits` (`profileId` TEXT NOT NULL, `sourceBookId` TEXT NOT NULL, `targetBookId` TEXT NOT NULL, `sourceCardId` TEXT NOT NULL, `targetCardId` TEXT NOT NULL, `migratedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `sourceBookId`, `targetBookId`, `sourceCardId`, `targetCardId`))")
            }
        }

        /**
         * 把早期版本写下的 `placeholder:<册>:<词>` 卡片 id 对齐成现在的 `<册>:<词>`。
         *
         * 为什么必须修：`PlaceholderWordCardSource` 已经不再被 DI 绑定，真实词书交付 `<册>:<词>`；
         * 而进度、到期队列、新词去重全都按 id 取交集，前缀不同就等于交集恒为空。三处可见后果：
         * 1. 首页「已学」永远是 0（分子与分母不在同一个 id 空间）；
         * 2. 到期复习队列拿到的是解析不出卡片的 id（`BundledWordBookSource` 会把 `placeholder` 当册名）；
         * 3. 已经学过、正在复习的词被当成新词重新排进当天计划（同一单词同时出现在复习与新学）。
         *
         * 只改前缀，不改「哪一册的哪个词」——`<册>:<词>` 正是 `WordCardSource` 现在期望的形式
         * （册 id 由 `cardId.substringBefore(':')` 反解，且册 id 正则不允许出现冒号，所以前缀可安全剥离）。
         */
        val MIGRATION_21_22: Migration = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                repairLegacyPlaceholderCardIds(db)
            }
        }

        val MIGRATION_22_23: Migration = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vocabulary_search_history` (`profileId` TEXT NOT NULL, `normalizedQuery` TEXT NOT NULL, `displayQuery` TEXT NOT NULL, `searchCount` INTEGER NOT NULL, `firstSearchedAtEpochMillis` INTEGER NOT NULL, `lastSearchedAtEpochMillis` INTEGER NOT NULL, `representativeWordBookId` TEXT, `representativeCardId` TEXT, PRIMARY KEY(`profileId`, `normalizedQuery`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_search_history_profileId_lastSearchedAtEpochMillis` ON `vocabulary_search_history` (`profileId`, `lastSearchedAtEpochMillis`)")
            }
        }

        val MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)

        /** 早期占位卡片 id 的前缀；真实词书的卡片 id 以 `<册>:` 开头。 */
        private val LEGACY_PLACEHOLDER_ID_PREFIX = LegacyCardIdRepair.PLACEHOLDER_PREFIX

        /**
         * [MIGRATION_21_22] 的实现，拆出来是为了让「先解冲突、再统一改写、最后收敛目标数」
         * 这个顺序在一处看清。
         *
         * 改写是**幂等**的：前缀已被剥掉的行不再匹配 `LIKE 'placeholder:%'`，重复执行不会二次改动。
         *
         * @param db 迁移事务内的数据库句柄。
         */
        private fun repairLegacyPlaceholderCardIds(db: SupportSQLiteDatabase) {
            // 计划任务的 PK 是 (planId, cardId)：改写后可能与同一计划里已有的新格式行撞主键。
            // 会出现这种撞车，本身就是当初去重失效的后果——同一个词既排了复习又排了新学。
            // 因为 `placeholder:` 只可能出现在前缀位置，一组冲突最多两行（旧格式 + 新格式各一）。
            // 取舍规则见 [LegacyCardIdRepair.collisionVictim]（复习优先）。
            val plansLosingTasks = mutableListOf<String>()
            val doomedRows = mutableListOf<Pair<String, String>>()
            db.query(
                "SELECT t1.planId, t1.cardId, t1.taskKind, t2.cardId, t2.taskKind " +
                    "FROM today_plan_tasks t1 JOIN today_plan_tasks t2 " +
                    "ON t2.planId = t1.planId " +
                    "AND t2.cardId = replace(t1.cardId, '$LEGACY_PLACEHOLDER_ID_PREFIX', '') " +
                    "WHERE t1.cardId LIKE '$LEGACY_PLACEHOLDER_ID_PREFIX%'",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val planId = cursor.getString(0)
                    val legacyId = cursor.getString(1)
                    val legacyKind = cursor.getString(2)
                    val modernId = cursor.getString(3)
                    val modernKind = cursor.getString(4)
                    val doomed = LegacyCardIdRepair.collisionVictim(legacyId, legacyKind, modernId, modernKind)
                    doomedRows += planId to doomed
                }
            }
            doomedRows.forEach { (planId, cardId) ->
                db.execSQL(
                    "DELETE FROM today_plan_tasks WHERE planId = ? AND cardId = ?",
                    arrayOf(planId, cardId),
                )
                if (planId !in plansLosingTasks) plansLosingTasks += planId
            }
            db.execSQL(
                "UPDATE today_plan_tasks SET cardId = replace(cardId, '$LEGACY_PLACEHOLDER_ID_PREFIX', '') " +
                    "WHERE cardId LIKE '$LEGACY_PLACEHOLDER_ID_PREFIX%'",
            )
            // 少了一行就必须把计划的目标数收敛到实际任务数：严格模式要求
            // newDone == newTarget 且 dueDone == dueTarget，目标数悬空等于当天永远解锁不了文章。
            plansLosingTasks.forEach { planId ->
                db.execSQL(
                    "UPDATE today_plans SET " +
                        "newTarget = (SELECT count(*) FROM today_plan_tasks WHERE planId = ? " +
                        "AND taskKind = '${LegacyCardIdRepair.NEW_TASK_KIND}'), " +
                        "dueTarget = (SELECT count(*) FROM today_plan_tasks WHERE planId = ? " +
                        "AND taskKind = '${LegacyCardIdRepair.DUE_TASK_KIND}') " +
                        "WHERE planId = ?",
                    arrayOf(planId, planId, planId),
                )
            }

            // 复习状态的 PK 是 cardId，先让位再改写。
            db.execSQL(
                "DELETE FROM card_review_states WHERE cardId LIKE '$LEGACY_PLACEHOLDER_ID_PREFIX%' " +
                    "AND replace(cardId, '$LEGACY_PLACEHOLDER_ID_PREFIX', '') IN " +
                    "(SELECT cardId FROM card_review_states WHERE cardId NOT LIKE '$LEGACY_PLACEHOLDER_ID_PREFIX%')",
            )
            db.execSQL(
                "UPDATE card_review_states SET cardId = replace(cardId, '$LEGACY_PLACEHOLDER_ID_PREFIX', '') " +
                    "WHERE cardId LIKE '$LEGACY_PLACEHOLDER_ID_PREFIX%'",
            )

            // 学习事件的 PK 是 eventId，cardId 只是普通列，直接改写即可。
            db.execSQL(
                "UPDATE learning_events SET cardId = replace(cardId, '$LEGACY_PLACEHOLDER_ID_PREFIX', '') " +
                    "WHERE cardId LIKE '$LEGACY_PLACEHOLDER_ID_PREFIX%'",
            )
        }

        private fun createDailyTargetConstraintTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(DAILY_TARGET_INSERT_TRIGGER_SQL)
            db.execSQL(DAILY_TARGET_UPDATE_TRIGGER_SQL)
        }

        private fun createTodayPlanTaskKindTrigger(db: SupportSQLiteDatabase) {
            db.execSQL(TODAY_PLAN_TASK_KIND_INSERT_TRIGGER_SQL)
            db.execSQL(TODAY_PLAN_TASK_KIND_UPDATE_TRIGGER_SQL)
        }

        private const val TODAY_PLAN_TASK_KIND_INSERT_TRIGGER_SQL =
            "CREATE TRIGGER IF NOT EXISTS `today_plan_tasks_kind_insert_check` " +
                "BEFORE INSERT ON `today_plan_tasks` FOR EACH ROW " +
                "WHEN NEW.`taskKind` NOT IN ('NEW', 'DUE') BEGIN " +
                "SELECT RAISE(ABORT, 'taskKind must be NEW or DUE'); END"

        private const val TODAY_PLAN_TASK_KIND_UPDATE_TRIGGER_SQL =
            "CREATE TRIGGER IF NOT EXISTS `today_plan_tasks_kind_update_check` " +
                "BEFORE UPDATE OF `taskKind` ON `today_plan_tasks` FOR EACH ROW " +
                "WHEN NEW.`taskKind` NOT IN ('NEW', 'DUE') BEGIN " +
                "SELECT RAISE(ABORT, 'taskKind must be NEW or DUE'); END"

        private const val DAILY_TARGET_INSERT_TRIGGER_SQL =
            "CREATE TRIGGER IF NOT EXISTS `learning_profiles_daily_target_insert_check` " +
                "BEFORE INSERT ON `learning_profiles` FOR EACH ROW " +
                "WHEN NEW.`dailyNewTarget` < 1 BEGIN " +
                "SELECT RAISE(ABORT, 'dailyNewTarget must be at least 1'); END"

        private const val DAILY_TARGET_UPDATE_TRIGGER_SQL =
            "CREATE TRIGGER IF NOT EXISTS `learning_profiles_daily_target_update_check` " +
                "BEFORE UPDATE OF `dailyNewTarget` ON `learning_profiles` FOR EACH ROW " +
                "WHEN NEW.`dailyNewTarget` < 1 BEGIN " +
                "SELECT RAISE(ABORT, 'dailyNewTarget must be at least 1'); END"
    }
}
