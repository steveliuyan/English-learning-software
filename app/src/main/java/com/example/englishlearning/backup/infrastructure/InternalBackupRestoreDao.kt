package com.example.englishlearning.backup.infrastructure

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Upsert
import com.example.englishlearning.core.storage.entity.ArticleEntity
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.CardReviewStateEntity
import com.example.englishlearning.core.storage.entity.LearningEventEntity
import com.example.englishlearning.core.storage.entity.LearningProfileEntity
import com.example.englishlearning.core.storage.entity.LearningSettingsEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import com.example.englishlearning.core.storage.entity.ReadingCompletionEntity
import com.example.englishlearning.core.storage.entity.ReadingPreferenceEntity
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import com.example.englishlearning.core.storage.entity.VocabularyEntryEntity
import com.example.englishlearning.core.storage.entity.VocabularySearchHistoryEntity
import com.example.englishlearning.core.storage.entity.WordAiNoteEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity
import com.example.englishlearning.core.storage.entity.WordBookProgressMigrationAuditEntity

/**
 * Write-side counterpart of [InternalBackupSourceDao]: bulk inserts used only by
 * the F4-04 replacement-restore database builder, never by app runtime paths.
 */
@Dao
internal interface InternalBackupRestoreDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfiles(rows: List<LocalProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWordBooks(rows: List<WordBookEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVocabularyEntries(rows: List<VocabularyEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLearningProfiles(rows: List<LearningProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLearningSettings(rows: List<LearningSettingsEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTodayPlans(rows: List<TodayPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTodayPlanTasks(rows: List<TodayPlanTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLearningEvents(rows: List<LearningEventEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCardReviewStates(rows: List<CardReviewStateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMigrationAudits(rows: List<WordBookProgressMigrationAuditEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArticles(rows: List<ArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReadingCompletions(rows: List<ReadingCompletionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReadingPreferences(rows: List<ReadingPreferenceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSearchHistory(rows: List<VocabularySearchHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWordAiNotes(rows: List<WordAiNoteEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAssetRecords(rows: List<AssetRecordEntity>)
}
