package com.example.englishlearning.backup.restore

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
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.Json

/**
 * Strict decode of the package record set back into the versioned DTOs (mirror of
 * [com.example.englishlearning.backup.domain.BackupSnapshotBuilder]). Unknown JSON
 * fields are rejected — silent field migration is forbidden on import.
 */
internal object RestoreRecordCodec {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    internal data class Rows(
        val entries: Map<String, ByteArray>,
        val profiles: List<ProfileRecord>,
        val learningProfiles: List<LearningProfileRecord>,
        val learningSettings: List<LearningSettingsRecord>,
        val todayPlans: List<TodayPlanRecord>,
        val todayPlanTasks: List<TodayPlanTaskRecord>,
        val learningEvents: List<LearningEventRecord>,
        val cardReviewStates: List<CardReviewStateRecord>,
        val wordBooks: List<WordBookRecord>,
        val vocabularyEntries: List<VocabularyEntryRecord>,
        val migrationAudits: List<MigrationAuditRecord>,
        val articles: List<ArticleRecord>,
        val readingCompletions: List<ReadingCompletionRecord>,
        val readingPreferences: List<ReadingPreferenceRecord>,
        val searchHistory: List<SearchHistoryRecord>,
        val wordAiNotes: List<WordAiNoteRecord>,
        val photoTranslations: List<com.example.englishlearning.backup.domain.PhotoTranslationRecordDto>,
        val favorites: List<FavoriteWordRecord>,
        val assetRecords: List<AssetRecordDto>,
    ) {
        /** Plaintext record entries keyed by package path (`records/<type>/<id>.json`). */
        val allRecords: List<RestoredRecord>
            get() = entries.map { (path, bytes) ->
                val segments = path.removePrefix("records/").removeSuffix(".json").split('/')
                RestoredRecord(type = segments[0], pathStableId = segments[1], json = bytes)
            }
    }

    internal sealed interface Result {
        data class Parsed(val rows: Rows) : Result
        data object Unreadable : Result
    }

    internal fun decode(entries: Map<String, ByteArray>): Result {
        val recordEntries = entries.filterKeys { it.startsWith("records/") }
        val buckets = mutableMapOf<String, MutableList<Pair<String, String>>>()
        for ((path, bytes) in recordEntries) {
            val segments = path.removePrefix("records/").removeSuffix(".json").split('/')
            if (segments.size != 2) return Result.Unreadable
            val text = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: Exception) {
                return Result.Unreadable
            }
            buckets.getOrPut(segments[0]) { mutableListOf() } += segments[1] to text
        }

        return try {
            Result.Parsed(
                Rows(
                    entries = recordEntries,
                    profiles = decodeList<ProfileRecord>(buckets["profiles"]),
                    learningProfiles = decodeList<LearningProfileRecord>(buckets["learning-profiles"]),
                    learningSettings = decodeList<LearningSettingsRecord>(buckets["learning-settings"]),
                    todayPlans = decodeList<TodayPlanRecord>(buckets["today-plans"]),
                    todayPlanTasks = decodeList<TodayPlanTaskRecord>(buckets["today-plan-tasks"]),
                    learningEvents = decodeList<LearningEventRecord>(buckets["learning-events"]),
                    cardReviewStates = decodeList<CardReviewStateRecord>(buckets["card-review-states"]),
                    wordBooks = decodeList<WordBookRecord>(buckets["word-books"]),
                    vocabularyEntries = decodeList<VocabularyEntryRecord>(buckets["vocabulary-entries"]),
                    migrationAudits = decodeList<MigrationAuditRecord>(buckets["migration-audits"]),
                    articles = decodeList<ArticleRecord>(buckets["articles"]),
                    readingCompletions = decodeList<ReadingCompletionRecord>(buckets["reading-completions"]),
                    readingPreferences = decodeList<ReadingPreferenceRecord>(buckets["reading-preferences"]),
                    searchHistory = decodeList<SearchHistoryRecord>(buckets["search-history"]),
                    wordAiNotes = decodeList<WordAiNoteRecord>(buckets["word-ai-notes"]),
                    photoTranslations = decodeList<com.example.englishlearning.backup.domain.PhotoTranslationRecordDto>(buckets["photo-translation-records"]),
                    favorites = decodeList<FavoriteWordRecord>(buckets["favorites"]),
                    assetRecords = decodeList<AssetRecordDto>(buckets["asset-records"]),
                ),
            )
        } catch (_: Exception) {
            Result.Unreadable
        }
    }

    private inline fun <reified T> decodeList(entries: List<Pair<String, String>>?): List<T> =
        entries?.map { json.decodeFromString<T>(it.second) } ?: emptyList()
}

/** Referential gates after structural integrity: resources ↔ asset records, then record-level references. */
internal object RestoreContentValidator {
    internal enum class ContentFailure { ResourceRelationship, EntityReference }

    internal sealed interface Result {
        data object Valid : Result
        data class Invalid(val reason: ContentFailure) : Result
    }

    internal fun validate(rows: RestoreRecordCodec.Rows, resources: Map<String, ByteArray>): Result {
        // Resource relationships: every asset record ↔ exactly one resources/<id> payload with matching digest.
        val assetIds = rows.assetRecords.map { it.id }
        if (assetIds.size != assetIds.distinct().size) return Result.Invalid(ContentFailure.ResourceRelationship)
        if (assetIds.toSet() != resources.keys) return Result.Invalid(ContentFailure.ResourceRelationship)
        rows.assetRecords.forEach { asset ->
            val bytes = resources.getValue(asset.id)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            if (digest != asset.sha256 || bytes.size.toLong() != asset.byteSize) {
                return Result.Invalid(ContentFailure.ResourceRelationship)
            }
        }

        val profiles = rows.profiles.map { it.id }.toSet()
        val wordBooks = rows.wordBooks.map { it.id }.toSet()
        val plans = rows.todayPlans.map { it.planId }.toSet()
        val articles = rows.articles.map { it.articleId }.toSet()

        fun references(check: () -> Boolean): Boolean = check()

        val entityChecks = listOf(
            references { rows.learningProfiles.all { it.profileId in profiles && it.activeWordBookId in wordBooks } },
            references { rows.learningSettings.all { it.profileId in profiles } },
            references { rows.todayPlans.all { it.profileId in profiles && it.activeWordBookId in wordBooks } },
            references { rows.todayPlanTasks.all { it.planId in plans } },
            references { rows.learningEvents.all { it.profileId in profiles && it.planId in plans && it.wordBookId in wordBooks } },
            references { rows.cardReviewStates.all { it.wordBookId in wordBooks } },
            references { rows.vocabularyEntries.all { it.profileId in profiles && it.wordBookId in wordBooks } },
            references { rows.migrationAudits.all { it.profileId in profiles } },
            references { rows.articles.all { it.profileId in profiles && it.activeWordBookId in wordBooks } },
            references { rows.readingCompletions.all { it.articleId in articles && it.profileId in profiles } },
            references { rows.readingPreferences.all { it.profileId in profiles } },
            references {
                rows.searchHistory.all { it.profileId in profiles && (it.representativeWordBookId == null || it.representativeWordBookId in wordBooks) }
            },
            references { rows.wordAiNotes.all { it.profileId in profiles && it.wordBookId in wordBooks } },
            references { rows.photoTranslations.all { it.profileId in profiles } },
            references { rows.favorites.all { it.profileId in profiles } },
        )
        return if (entityChecks.all { it }) Result.Valid else Result.Invalid(ContentFailure.EntityReference)
    }
}
