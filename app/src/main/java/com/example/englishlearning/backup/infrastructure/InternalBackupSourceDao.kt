package com.example.englishlearning.backup.infrastructure

import androidx.room.Dao
import androidx.room.Query

/**
 * Read-only access for the F4-03 backup snapshot. Deliberately SELECT-only and
 * limited to the whitelisted learning tables; credential, device-local, schema
 * bookkeeping and derived-index tables are not expressible here.
 */
@Dao
internal interface InternalBackupSourceDao {
    @Query("SELECT * FROM local_profiles")
    suspend fun allProfiles(): List<com.example.englishlearning.core.storage.entity.LocalProfileEntity>

    @Query("SELECT * FROM learning_profiles")
    suspend fun allLearningProfiles(): List<com.example.englishlearning.core.storage.entity.LearningProfileEntity>

    @Query("SELECT * FROM learning_settings")
    suspend fun allLearningSettings(): List<com.example.englishlearning.core.storage.entity.LearningSettingsEntity>

    @Query("SELECT * FROM today_plans")
    suspend fun allTodayPlans(): List<com.example.englishlearning.core.storage.entity.TodayPlanEntity>

    @Query("SELECT * FROM today_plan_tasks")
    suspend fun allTodayPlanTasks(): List<com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity>

    @Query("SELECT * FROM learning_events")
    suspend fun allLearningEvents(): List<com.example.englishlearning.core.storage.entity.LearningEventEntity>

    @Query("SELECT * FROM card_review_states")
    suspend fun allCardReviewStates(): List<com.example.englishlearning.core.storage.entity.CardReviewStateEntity>

    @Query("SELECT * FROM word_books")
    suspend fun allWordBooks(): List<com.example.englishlearning.core.storage.entity.WordBookEntity>

    @Query("SELECT * FROM vocabulary_entries")
    suspend fun allVocabularyEntries(): List<com.example.englishlearning.core.storage.entity.VocabularyEntryEntity>

    @Query("SELECT * FROM word_book_progress_migration_audits")
    suspend fun allMigrationAudits(): List<com.example.englishlearning.core.storage.entity.WordBookProgressMigrationAuditEntity>

    @Query("SELECT * FROM articles")
    suspend fun allArticles(): List<com.example.englishlearning.core.storage.entity.ArticleEntity>

    @Query("SELECT * FROM reading_completions")
    suspend fun allReadingCompletions(): List<com.example.englishlearning.core.storage.entity.ReadingCompletionEntity>

    @Query("SELECT * FROM reading_preferences")
    suspend fun allReadingPreferences(): List<com.example.englishlearning.core.storage.entity.ReadingPreferenceEntity>

    @Query("SELECT * FROM vocabulary_search_history")
    suspend fun allSearchHistory(): List<com.example.englishlearning.core.storage.entity.VocabularySearchHistoryEntity>

    @Query("SELECT * FROM word_ai_notes")
    suspend fun allWordAiNotes(): List<com.example.englishlearning.core.storage.entity.WordAiNoteEntity>

    @Query("SELECT * FROM asset_records")
    suspend fun allAssetRecords(): List<com.example.englishlearning.core.storage.entity.AssetRecordEntity>
}
