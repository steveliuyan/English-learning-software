package com.example.englishlearning.backup.domain

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** One versioned, canonical-JSON logical record destined for `records/<type>/<id>.json`. */
data class BackupRecord(val type: String, val stableId: String, val jsonBytes: ByteArray) {
    val path: String
        get() = recordPackagePath(type, stableId)

    fun jsonText(): String = String(jsonBytes, Charsets.UTF_8)

    override fun equals(other: Any?): Boolean =
        this === other || (other is BackupRecord && type == other.type && stableId == other.stableId && jsonBytes.contentEquals(other.jsonBytes))

    override fun hashCode(): Int = ((type.hashCode() * 31 + stableId.hashCode()) * 31) + jsonBytes.contentHashCode()
}

/**
 * Explicit data coverage whitelist for the backup snapshot (F4-03).
 * Credential tables (AI profiles, key aliases), device-local state (reminders,
 * speech), schema bookkeeping and derived search indexes are intentionally
 * inexpressible here.
 */
data class BackupSourceRows(
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
    val assetRecords: List<AssetRecordEntity>,
)

object BackupSnapshotBuilder {
    private val json = Json { encodeDefaults = true }

    fun buildRecords(rows: BackupSourceRows, includeMediaAssets: Boolean): List<BackupRecord> {
        val records = mutableListOf<BackupRecord>()
        rows.profiles.forEach { records += record("profiles", singleId(it.id), ProfileRecord(it)) }
        rows.learningProfiles.forEach { records += record("learning-profiles", singleId(it.profileId), LearningProfileRecord(it)) }
        rows.learningSettings.forEach { records += record("learning-settings", singleId(it.profileId), LearningSettingsRecord(it)) }
        rows.todayPlans.forEach { records += record("today-plans", singleId(it.planId), TodayPlanRecord(it)) }
        rows.todayPlanTasks.forEach { records += record("today-plan-tasks", compositeId(it.planId, it.cardId), TodayPlanTaskRecord(it)) }
        rows.learningEvents.forEach { records += record("learning-events", singleId(it.eventId), LearningEventRecord(it)) }
        rows.cardReviewStates.forEach { records += record("card-review-states", singleId(it.cardId), CardReviewStateRecord(it)) }
        rows.wordBooks.forEach { records += record("word-books", singleId(it.id), WordBookRecord(it)) }
        rows.vocabularyEntries.forEach { records += record("vocabulary-entries", compositeId(it.profileId, it.wordBookId, it.cardId), VocabularyEntryRecord(it)) }
        rows.migrationAudits.forEach {
            records += record("migration-audits", compositeId(it.profileId, it.sourceBookId, it.targetBookId, it.sourceCardId, it.targetCardId), MigrationAuditRecord(it))
        }
        rows.articles.forEach { records += record("articles", singleId(it.articleId), ArticleRecord(it)) }
        rows.readingCompletions.forEach { records += record("reading-completions", singleId(it.articleId), ReadingCompletionRecord(it)) }
        rows.readingPreferences.forEach { records += record("reading-preferences", singleId(it.profileId), ReadingPreferenceRecord(it)) }
        rows.searchHistory.forEach { records += record("search-history", compositeId(it.profileId, it.normalizedQuery), SearchHistoryRecord(it)) }
        rows.wordAiNotes.forEach { records += record("word-ai-notes", singleId(it.noteId), WordAiNoteRecord(it)) }
        if (includeMediaAssets) {
            rows.assetRecords.forEach { records += record("asset-records", singleId(it.id), AssetRecordDto(it)) }
        }
        return records.sortedBy { it.path }
    }

    private inline fun <reified T : Any> record(type: String, stableId: String, dto: T): BackupRecord =
        BackupRecord(type, stableId, json.encodeToString(dto).toByteArray(Charsets.UTF_8))

    private fun singleId(raw: String): String = if (isSafePackageId(raw)) raw else sha256Hex(raw)

    private fun compositeId(vararg parts: String): String = sha256Hex(parts.joinToString("\u0000"))

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

@Serializable
internal data class ProfileRecord(
    val schemaVersion: Int = 1,
    val id: String,
    val displayName: String,
    val createdAt: Long,
) {
    constructor(entity: LocalProfileEntity) : this(id = entity.id, displayName = entity.displayName, createdAt = entity.createdAt)
}

@Serializable
internal data class LearningProfileRecord(
    val schemaVersion: Int = 1,
    val profileId: String,
    val activeWordBookId: String,
    val dailyNewTarget: Int,
) {
    constructor(entity: LearningProfileEntity) : this(profileId = entity.profileId, activeWordBookId = entity.activeWordBookId, dailyNewTarget = entity.dailyNewTarget)
}

@Serializable
internal data class LearningSettingsRecord(
    val schemaVersion: Int = 1,
    val profileId: String,
    val openDetailOnKnown: Boolean,
    val openDetailOnFuzzy: Boolean,
    val openDetailOnForgotten: Boolean,
    val showVocabularySearchCount: Boolean,
) {
    constructor(entity: LearningSettingsEntity) : this(
        profileId = entity.profileId,
        openDetailOnKnown = entity.openDetailOnKnown,
        openDetailOnFuzzy = entity.openDetailOnFuzzy,
        openDetailOnForgotten = entity.openDetailOnForgotten,
        showVocabularySearchCount = entity.showVocabularySearchCount,
    )
}

@Serializable
internal data class TodayPlanRecord(
    val schemaVersion: Int = 1,
    val planId: String,
    val profileId: String,
    val localDate: String,
    val zoneId: String,
    val activeWordBookId: String,
    val newTarget: Int,
    val dueTarget: Int,
    val ruleVersion: String,
    val generatedAtEpochMillis: Long,
) {
    constructor(entity: TodayPlanEntity) : this(
        planId = entity.planId, profileId = entity.profileId, localDate = entity.localDate, zoneId = entity.zoneId,
        activeWordBookId = entity.activeWordBookId, newTarget = entity.newTarget, dueTarget = entity.dueTarget,
        ruleVersion = entity.ruleVersion, generatedAtEpochMillis = entity.generatedAtEpochMillis,
    )
}

@Serializable
internal data class TodayPlanTaskRecord(
    val schemaVersion: Int = 1,
    val planId: String,
    val cardId: String,
    val taskKind: String,
    val ordinal: Int,
) {
    constructor(entity: TodayPlanTaskEntity) : this(planId = entity.planId, cardId = entity.cardId, taskKind = entity.taskKind, ordinal = entity.ordinal)
}

@Serializable
internal data class LearningEventRecord(
    val schemaVersion: Int = 1,
    val eventId: String,
    val profileId: String,
    val planId: String,
    val cardId: String,
    val wordBookId: String,
    val feedback: String,
    val occurredAtEpochMillis: Long,
    val algorithmVersion: String,
    val paramsVersion: String,
    val dueBeforeEpochMillis: Long?,
    val nextReviewAtEpochMillis: Long,
) {
    constructor(entity: LearningEventEntity) : this(
        eventId = entity.eventId, profileId = entity.profileId, planId = entity.planId, cardId = entity.cardId,
        wordBookId = entity.wordBookId, feedback = entity.feedback, occurredAtEpochMillis = entity.occurredAtEpochMillis,
        algorithmVersion = entity.algorithmVersion, paramsVersion = entity.paramsVersion,
        dueBeforeEpochMillis = entity.dueBeforeEpochMillis, nextReviewAtEpochMillis = entity.nextReviewAtEpochMillis,
    )
}

@Serializable
internal data class CardReviewStateRecord(
    val schemaVersion: Int = 1,
    val cardId: String,
    val wordBookId: String,
    val lastFeedback: String,
    val lastReviewedAtEpochMillis: Long,
    val nextReviewAtEpochMillis: Long,
) {
    constructor(entity: CardReviewStateEntity) : this(
        cardId = entity.cardId, wordBookId = entity.wordBookId, lastFeedback = entity.lastFeedback,
        lastReviewedAtEpochMillis = entity.lastReviewedAtEpochMillis, nextReviewAtEpochMillis = entity.nextReviewAtEpochMillis,
    )
}

@Serializable
internal data class WordBookRecord(
    val schemaVersion: Int = 1,
    val id: String,
    val displayName: String,
    val level: String,
    val totalWords: Int,
    val dataVersion: String,
    val sourceId: String,
) {
    constructor(entity: WordBookEntity) : this(
        id = entity.id, displayName = entity.displayName, level = entity.level,
        totalWords = entity.totalWords, dataVersion = entity.dataVersion, sourceId = entity.sourceId,
    )
}

@Serializable
internal data class VocabularyEntryRecord(
    val schemaVersion: Int = 1,
    val profileId: String,
    val wordBookId: String,
    val cardId: String,
    val addedAtEpochMillis: Long,
    val lastFeedback: String,
) {
    constructor(entity: VocabularyEntryEntity) : this(
        profileId = entity.profileId, wordBookId = entity.wordBookId, cardId = entity.cardId,
        addedAtEpochMillis = entity.addedAtEpochMillis, lastFeedback = entity.lastFeedback,
    )
}

@Serializable
internal data class MigrationAuditRecord(
    val schemaVersion: Int = 1,
    val profileId: String,
    val sourceBookId: String,
    val targetBookId: String,
    val sourceCardId: String,
    val targetCardId: String,
    val migratedAtEpochMillis: Long,
) {
    constructor(entity: WordBookProgressMigrationAuditEntity) : this(
        profileId = entity.profileId, sourceBookId = entity.sourceBookId, targetBookId = entity.targetBookId,
        sourceCardId = entity.sourceCardId, targetCardId = entity.targetCardId, migratedAtEpochMillis = entity.migratedAtEpochMillis,
    )
}

@Serializable
internal data class ArticleRecord(
    val schemaVersion: Int = 1,
    val articleId: String,
    val profileId: String,
    val localDate: String,
    val activeWordBookId: String,
    val articleType: String,
    val lengthTier: String,
    val version: Int,
    val title: String,
    val englishText: String,
    val chineseText: String,
    val generatedAtEpochMillis: Long,
    val coveredLemmas: String,
    val parameterSummary: String,
    val modelName: String?,
    val sourceType: String,
    val sourceId: String,
    val sourceDisplayName: String,
    val sourceUrl: String,
    val sourceLicenseNote: String,
    val sourceAttribution: String,
) {
    constructor(entity: ArticleEntity) : this(
        articleId = entity.articleId, profileId = entity.profileId, localDate = entity.localDate,
        activeWordBookId = entity.activeWordBookId, articleType = entity.articleType, lengthTier = entity.lengthTier,
        version = entity.version, title = entity.title, englishText = entity.englishText, chineseText = entity.chineseText,
        generatedAtEpochMillis = entity.generatedAtEpochMillis, coveredLemmas = entity.coveredLemmas,
        parameterSummary = entity.parameterSummary, modelName = entity.modelName, sourceType = entity.sourceType,
        sourceId = entity.sourceId, sourceDisplayName = entity.sourceDisplayName, sourceUrl = entity.sourceUrl,
        sourceLicenseNote = entity.sourceLicenseNote, sourceAttribution = entity.sourceAttribution,
    )
}

@Serializable
internal data class ReadingCompletionRecord(
    val schemaVersion: Int = 1,
    val articleId: String,
    val profileId: String,
    val localDate: String,
    val completedAtEpochMillis: Long,
) {
    constructor(entity: ReadingCompletionEntity) : this(
        articleId = entity.articleId, profileId = entity.profileId, localDate = entity.localDate,
        completedAtEpochMillis = entity.completedAtEpochMillis,
    )
}

@Serializable
internal data class ReadingPreferenceRecord(
    val schemaVersion: Int = 1,
    val profileId: String,
    val defaultArticleType: String,
    val explicitLengthTier: String?,
    val displayMode: String,
    val showLearnedMarks: Boolean,
) {
    constructor(entity: ReadingPreferenceEntity) : this(
        profileId = entity.profileId, defaultArticleType = entity.defaultArticleType,
        explicitLengthTier = entity.explicitLengthTier, displayMode = entity.displayMode,
        showLearnedMarks = entity.showLearnedMarks,
    )
}

@Serializable
internal data class SearchHistoryRecord(
    val schemaVersion: Int = 1,
    val profileId: String,
    val normalizedQuery: String,
    val displayQuery: String,
    val searchCount: Int,
    val firstSearchedAtEpochMillis: Long,
    val lastSearchedAtEpochMillis: Long,
    val representativeWordBookId: String?,
    val representativeCardId: String?,
) {
    constructor(entity: VocabularySearchHistoryEntity) : this(
        profileId = entity.profileId, normalizedQuery = entity.normalizedQuery, displayQuery = entity.displayQuery,
        searchCount = entity.searchCount, firstSearchedAtEpochMillis = entity.firstSearchedAtEpochMillis,
        lastSearchedAtEpochMillis = entity.lastSearchedAtEpochMillis, representativeWordBookId = entity.representativeWordBookId,
        representativeCardId = entity.representativeCardId,
    )
}

@Serializable
internal data class WordAiNoteRecord(
    val schemaVersion: Int = 1,
    val noteId: String,
    val profileId: String,
    val wordBookId: String,
    val cardId: String,
    val lemma: String,
    val kind: String,
    val answer: String,
    val createdAtEpochMillis: Long,
) {
    constructor(entity: WordAiNoteEntity) : this(
        noteId = entity.noteId, profileId = entity.profileId, wordBookId = entity.wordBookId, cardId = entity.cardId,
        lemma = entity.lemma, kind = entity.kind, answer = entity.answer, createdAtEpochMillis = entity.createdAtEpochMillis,
    )
}

/** Asset metadata intentionally omits the device-local private file location. */
@Serializable
internal data class AssetRecordDto(
    val schemaVersion: Int = 1,
    val id: String,
    val sha256: String,
    val byteSize: Long,
    val createdAt: Long,
) {
    constructor(entity: AssetRecordEntity) : this(
        id = entity.id, sha256 = entity.sha256, byteSize = entity.byteSize, createdAt = entity.createdAt,
    )
}
