package com.example.englishlearning.backup.device

import com.example.englishlearning.backup.domain.ArticleRecord
import com.example.englishlearning.backup.domain.AssetRecordDto
import com.example.englishlearning.backup.domain.CardReviewStateRecord
import com.example.englishlearning.backup.domain.FavoriteWordRecord
import com.example.englishlearning.backup.domain.LearningEventRecord
import com.example.englishlearning.backup.domain.LearningProfileRecord
import com.example.englishlearning.backup.domain.LearningSettingsRecord
import com.example.englishlearning.backup.domain.MigrationAuditRecord
import com.example.englishlearning.backup.domain.ProfileRecord
import com.example.englishlearning.backup.domain.ReadingCompletionRecord
import com.example.englishlearning.backup.domain.ReadingPreferenceRecord
import com.example.englishlearning.backup.domain.SearchHistoryRecord
import com.example.englishlearning.backup.domain.TodayPlanRecord
import com.example.englishlearning.backup.domain.TodayPlanTaskRecord
import com.example.englishlearning.backup.domain.VocabularyEntryRecord
import com.example.englishlearning.backup.domain.WordAiNoteRecord
import com.example.englishlearning.backup.domain.WordBookRecord
import com.example.englishlearning.backup.restore.StagedRestoreSession
import com.example.englishlearning.core.storage.entity.ArticleEntity
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.CardReviewStateEntity
import com.example.englishlearning.core.storage.entity.FavoriteWordEntity
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Decodes staged record JSON (export DTOs) back into Room entities for the
 * replacement database. Export is entity→DTO; this is the exact inverse, so any
 * schema drift fails here at compile time. Malformed JSON fails the whole
 * restore (fail-closed).
 */
@Singleton
class RestoreEntityDecoder @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    data class Rows(
        val profiles: List<LocalProfileEntity>,
        val learningProfiles: List<LearningProfileEntity>,
        val learningSettings: List<LearningSettingsEntity>,
        val todayPlans: List<TodayPlanEntity>,
        val todayPlanTasks: List<TodayPlanTaskEntity>,
        val learningEvents: List<LearningEventEntity>,
        val cardReviewStates: List<CardReviewStateEntity>,
        val wordBooks: List<WordBookEntity>,
        val vocabularyEntries: List<VocabularyEntryEntity>,
        val migrationAudits: List<WordBookProgressMigrationAuditEntity>,
        val articles: List<ArticleEntity>,
        val readingCompletions: List<ReadingCompletionEntity>,
        val readingPreferences: List<ReadingPreferenceEntity>,
        val searchHistory: List<VocabularySearchHistoryEntity>,
        val wordAiNotes: List<WordAiNoteEntity>,
        val photoTranslations: List<com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity>,
        val favorites: List<FavoriteWordEntity>,
        val assetRecords: List<AssetRecordEntity>,
    )

    fun decode(session: StagedRestoreSession): Rows {
        val byType = session.staged.records.groupBy { it.type }
        fun records(type: String) = byType[type].orEmpty()
        fun text(record: com.example.englishlearning.backup.restore.RestoredRecord) = record.json.toString(Charsets.UTF_8)

        return Rows(
            profiles = records("profiles").map { json.decodeFromString<ProfileRecord>(text(it)).let { d -> LocalProfileEntity(d.id, d.displayName, d.createdAt) } },
            learningProfiles = records("learning-profiles").map { json.decodeFromString<LearningProfileRecord>(text(it)).let { d -> LearningProfileEntity(d.profileId, d.activeWordBookId, d.dailyNewTarget) } },
            learningSettings = records("learning-settings").map {
                json.decodeFromString<LearningSettingsRecord>(text(it)).let { d ->
                    LearningSettingsEntity(d.profileId, d.openDetailOnKnown, d.openDetailOnFuzzy, d.openDetailOnForgotten, d.showVocabularySearchCount)
                }
            },
            todayPlans = records("today-plans").map {
                json.decodeFromString<TodayPlanRecord>(text(it)).let { d ->
                    TodayPlanEntity(d.planId, d.profileId, d.localDate, d.zoneId, d.activeWordBookId, d.newTarget, d.dueTarget, d.ruleVersion, d.generatedAtEpochMillis)
                }
            },
            todayPlanTasks = records("today-plan-tasks").map {
                json.decodeFromString<TodayPlanTaskRecord>(text(it)).let { d -> TodayPlanTaskEntity(d.planId, d.cardId, d.taskKind, d.ordinal) }
            },
            learningEvents = records("learning-events").map {
                json.decodeFromString<LearningEventRecord>(text(it)).let { d ->
                    LearningEventEntity(d.eventId, d.profileId, d.planId, d.cardId, d.wordBookId, d.feedback, d.occurredAtEpochMillis, d.algorithmVersion, d.paramsVersion, d.dueBeforeEpochMillis, d.nextReviewAtEpochMillis)
                }
            },
            cardReviewStates = records("card-review-states").map {
                json.decodeFromString<CardReviewStateRecord>(text(it)).let { d ->
                    CardReviewStateEntity(d.cardId, d.wordBookId, d.lastFeedback, d.lastReviewedAtEpochMillis, d.nextReviewAtEpochMillis)
                }
            },
            wordBooks = records("word-books").map {
                json.decodeFromString<WordBookRecord>(text(it)).let { d -> WordBookEntity(d.id, d.displayName, d.level, d.totalWords, d.dataVersion, d.sourceId) }
            },
            vocabularyEntries = records("vocabulary-entries").map {
                json.decodeFromString<VocabularyEntryRecord>(text(it)).let { d ->
                    VocabularyEntryEntity(d.profileId, d.wordBookId, d.cardId, d.addedAtEpochMillis, d.lastFeedback)
                }
            },
            migrationAudits = records("migration-audits").map {
                json.decodeFromString<MigrationAuditRecord>(text(it)).let { d ->
                    WordBookProgressMigrationAuditEntity(d.profileId, d.sourceBookId, d.targetBookId, d.sourceCardId, d.targetCardId, d.migratedAtEpochMillis)
                }
            },
            articles = records("articles").map {
                json.decodeFromString<ArticleRecord>(text(it)).let { d ->
                    ArticleEntity(
                        d.articleId, d.profileId, d.localDate, d.activeWordBookId, d.articleType, d.lengthTier, d.version,
                        d.title, d.englishText, d.chineseText, d.generatedAtEpochMillis, d.coveredLemmas, d.parameterSummary,
                        d.modelName, d.sourceType, d.sourceId, d.sourceDisplayName, d.sourceUrl, d.sourceLicenseNote, d.sourceAttribution,
                    )
                }
            },
            readingCompletions = records("reading-completions").map {
                json.decodeFromString<ReadingCompletionRecord>(text(it)).let { d ->
                    ReadingCompletionEntity(d.articleId, d.profileId, d.localDate, d.completedAtEpochMillis)
                }
            },
            readingPreferences = records("reading-preferences").map {
                json.decodeFromString<ReadingPreferenceRecord>(text(it)).let { d ->
                    ReadingPreferenceEntity(d.profileId, d.defaultArticleType, d.explicitLengthTier, d.displayMode, d.showLearnedMarks)
                }
            },
            searchHistory = records("search-history").map {
                json.decodeFromString<SearchHistoryRecord>(text(it)).let { d ->
                    VocabularySearchHistoryEntity(d.profileId, d.normalizedQuery, d.displayQuery, d.searchCount, d.firstSearchedAtEpochMillis, d.lastSearchedAtEpochMillis, d.representativeWordBookId, d.representativeCardId)
                }
            },
            wordAiNotes = records("word-ai-notes").map {
                json.decodeFromString<WordAiNoteRecord>(text(it)).let { d ->
                    WordAiNoteEntity(d.noteId, d.profileId, d.wordBookId, d.cardId, d.lemma, d.kind, d.answer, d.createdAtEpochMillis)
                }
            },
            photoTranslations = records("photo-translation-records").map {
                json.decodeFromString<com.example.englishlearning.backup.domain.PhotoTranslationRecordDto>(text(it)).let { d ->
                    com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity(
                        d.recordId, d.profileId, d.sourceText, d.translatedText, d.engine, d.imageAssetId, d.createdAtEpochMillis,
                    )
                }
            },
            favorites = records("favorites").map {
                json.decodeFromString<FavoriteWordRecord>(text(it)).let { d ->
                    FavoriteWordEntity(d.profileId, d.cardId, d.wordBookId, d.lemma, d.addedAtEpochMillis)
                }
            },
            assetRecords = records("asset-records").map {
                json.decodeFromString<AssetRecordDto>(text(it)).let { d ->
                    // The device-local file location never travels in the package; restore re-derives it.
                    AssetRecordEntity(d.id, d.sha256, "assets/${d.id}", d.byteSize, d.createdAt)
                }
            },
        )
    }
}
