package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupRecordTypes
import com.example.englishlearning.backup.domain.BackupSnapshotBuilder
import com.example.englishlearning.backup.domain.BackupSourceRows
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.ArticleEntity
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupSnapshotBuilderTest {
    private val emptyRows = BackupSourceRows(
        profiles = emptyList(),
        learningProfiles = emptyList(),
        learningSettings = emptyList(),
        todayPlans = emptyList(),
        todayPlanTasks = emptyList(),
        learningEvents = emptyList(),
        cardReviewStates = emptyList(),
        wordBooks = emptyList(),
        vocabularyEntries = emptyList(),
        migrationAudits = emptyList(),
        articles = emptyList(),
        readingCompletions = emptyList(),
        readingPreferences = emptyList(),
        searchHistory = emptyList(),
        wordAiNotes = emptyList(),
        photoTranslations = emptyList(),
        favorites = emptyList(),
        assetRecords = emptyList(),
    )

    private val sampleRows = emptyRows.copy(
        profiles = listOf(LocalProfileEntity("p-1", "Me", 100L)),
        learningProfiles = listOf(LearningProfileEntity("p-1", "wb-1", 20)),
        learningSettings = listOf(LearningSettingsEntity("p-1", true, false, true, true)),
        todayPlans = listOf(TodayPlanEntity("plan-1", "p-1", "2026-10-13", "Asia/Shanghai", "wb-1", 20, 5, "v1", 111L)),
        todayPlanTasks = listOf(TodayPlanTaskEntity("plan-1", "card-1", "new", 0)),
        learningEvents = listOf(LearningEventEntity("ev-1", "p-1", "plan-1", "card-1", "wb-1", "known", 120L, "fsrs-1", "params-1", null, 130L)),
        cardReviewStates = listOf(CardReviewStateEntity("card-1", "wb-1", "known", 120L, 130L)),
        wordBooks = listOf(WordBookEntity("wb-1", "Core", "cet4", 100, "1", "src")),
        vocabularyEntries = listOf(VocabularyEntryEntity("p-1", "wb-1", "card-1", 90L, "known")),
        migrationAudits = listOf(WordBookProgressMigrationAuditEntity("p-1", "wb-a", "wb-b", "card-a", "card-b", 80L)),
        articles = listOf(
            ArticleEntity(
                "art-1", "p-1", "2026-10-13", "wb-1", "story", "short", 1, "Title",
                "english", "chinese", 70L, "[]", "params", null,
                "AI_GENERATED", "", "", "", "", "",
            ),
        ),
        readingCompletions = listOf(ReadingCompletionEntity("art-1", "p-1", "2026-10-13", 75L)),
        readingPreferences = listOf(ReadingPreferenceEntity("p-1", "story", null, "bilingual", true)),
        searchHistory = listOf(VocabularySearchHistoryEntity("p-1", "hello world", "Hello World", 3, 60L, 65L, "wb-1", "card-1")),
        wordAiNotes = listOf(WordAiNoteEntity("note-1", "p-1", "wb-1", "card-1", "hello", "definition", "answer text", 50L)),
        photoTranslations = listOf(
            com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity(
                "rec-1", "p-1", "Birds fly.", "鸟儿会飞。", "MlKit", "a-1", 45L,
            ),
        ),
        favorites = listOf(FavoriteWordEntity("p-1", "card-1", "wb-1", "cat", 95L)),
        assetRecords = listOf(AssetRecordEntity("a-1", "cafe", "assets/a-1", 9L, 40L)),
    )

    @Test
    fun `empty rows produce no records`() {
        assertEquals(emptyList(), BackupSnapshotBuilder.buildRecords(emptyRows, includeMediaAssets = true))
    }

    @Test
    fun `every row becomes a whitelisted typed record with a safe path`() {
        val records = BackupSnapshotBuilder.buildRecords(sampleRows, includeMediaAssets = true)

        val singleKeyPaths = setOf(
            "records/articles/art-1.json",
            "records/asset-records/a-1.json",
            "records/card-review-states/card-1.json",
            "records/learning-events/ev-1.json",
            "records/learning-profiles/p-1.json",
            "records/learning-settings/p-1.json",
            "records/profiles/p-1.json",
            "records/reading-completions/art-1.json",
            "records/reading-preferences/p-1.json",
            "records/today-plans/plan-1.json",
            "records/word-ai-notes/note-1.json",
            "records/photo-translation-records/rec-1.json",
            "records/word-books/wb-1.json",
        )
        val compositeTypes = setOf("favorites", "migration-audits", "search-history", "today-plan-tasks", "vocabulary-entries")
        val paths = records.map { it.path }.toSet()

        assertEquals(18, records.size)
        singleKeyPaths.forEach { assertTrue(it in paths, "missing $it") }
        compositeTypes.forEach { type ->
            val hashed = paths.filter { it.startsWith("records/$type/") }
            assertEquals(1, hashed.size, "type $type must produce exactly one record")
            val id = hashed.single().removePrefix("records/$type/").removeSuffix(".json")
            assertEquals(64, id.length)
            assertTrue(id.all { it in '0'..'9' || it in 'a'..'f' }, "composite id must be canonical: $id")
        }
        records.forEach { record ->
            val type = record.path.removePrefix("records/").substringBefore("/")
            assertTrue(BackupRecordTypes.isValidType(type), "type $type must be whitelisted")
        }
    }

    @Test
    fun `composite ids with unsafe characters fall back to sha256 and keep raw ids in payload`() {
        val records = BackupSnapshotBuilder.buildRecords(sampleRows, includeMediaAssets = true)

        val history = records.first { it.path.contains("search-history") }
        val id = history.path.removePrefix("records/search-history/").removeSuffix(".json")
        assertEquals(64, id.length)
        assertTrue(id.all { it in '0'..'9' || it in 'a'..'f' })

        val json = Json.parseToJsonElement(history.jsonText()).jsonObject
        assertEquals("hello world", json["normalizedQuery"]?.jsonPrimitive?.content)
        assertEquals("p-1", json["profileId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `record payload carries schema version and exact whitelisted keys`() {
        val records = BackupSnapshotBuilder.buildRecords(sampleRows, includeMediaAssets = true)
        val keysOf: (String) -> Set<String> = { type ->
            val record = records.first { it.path.startsWith("records/$type/") }
            Json.parseToJsonElement(record.jsonText()).jsonObject.keys
        }

        assertEquals(setOf("schemaVersion", "id", "displayName", "createdAt"), keysOf("profiles"))
        assertEquals(setOf("schemaVersion", "profileId", "activeWordBookId", "dailyNewTarget"), keysOf("learning-profiles"))
        assertEquals(
            setOf("schemaVersion", "profileId", "openDetailOnKnown", "openDetailOnFuzzy", "openDetailOnForgotten", "showVocabularySearchCount"),
            keysOf("learning-settings"),
        )
        assertEquals(
            setOf("schemaVersion", "planId", "profileId", "localDate", "zoneId", "activeWordBookId", "newTarget", "dueTarget", "ruleVersion", "generatedAtEpochMillis"),
            keysOf("today-plans"),
        )
        assertEquals(setOf("schemaVersion", "planId", "cardId", "taskKind", "ordinal"), keysOf("today-plan-tasks"))
        assertEquals(
            setOf(
                "schemaVersion", "eventId", "profileId", "planId", "cardId", "wordBookId", "feedback",
                "occurredAtEpochMillis", "algorithmVersion", "paramsVersion", "dueBeforeEpochMillis", "nextReviewAtEpochMillis",
            ),
            keysOf("learning-events"),
        )
        assertEquals(
            setOf("schemaVersion", "cardId", "wordBookId", "lastFeedback", "lastReviewedAtEpochMillis", "nextReviewAtEpochMillis"),
            keysOf("card-review-states"),
        )
        assertEquals(setOf("schemaVersion", "id", "displayName", "level", "totalWords", "dataVersion", "sourceId"), keysOf("word-books"))
        assertEquals(setOf("schemaVersion", "profileId", "wordBookId", "cardId", "addedAtEpochMillis", "lastFeedback"), keysOf("vocabulary-entries"))
        assertEquals(
            setOf("schemaVersion", "profileId", "sourceBookId", "targetBookId", "sourceCardId", "targetCardId", "migratedAtEpochMillis"),
            keysOf("migration-audits"),
        )
        assertEquals(
            setOf(
                "schemaVersion", "articleId", "profileId", "localDate", "activeWordBookId", "articleType", "lengthTier",
                "version", "title", "englishText", "chineseText", "generatedAtEpochMillis", "coveredLemmas",
                "parameterSummary", "modelName", "sourceType", "sourceId", "sourceDisplayName", "sourceUrl",
                "sourceLicenseNote", "sourceAttribution",
            ),
            keysOf("articles"),
        )
        assertEquals(setOf("schemaVersion", "articleId", "profileId", "localDate", "completedAtEpochMillis"), keysOf("reading-completions"))
        assertEquals(setOf("schemaVersion", "profileId", "defaultArticleType", "explicitLengthTier", "displayMode", "showLearnedMarks"), keysOf("reading-preferences"))
        assertEquals(
            setOf(
                "schemaVersion", "profileId", "normalizedQuery", "displayQuery", "searchCount",
                "firstSearchedAtEpochMillis", "lastSearchedAtEpochMillis", "representativeWordBookId", "representativeCardId",
            ),
            keysOf("search-history"),
        )
        assertEquals(setOf("schemaVersion", "noteId", "profileId", "wordBookId", "cardId", "lemma", "kind", "answer", "createdAtEpochMillis"), keysOf("word-ai-notes"))
        assertEquals(
            setOf("schemaVersion", "profileId", "cardId", "wordBookId", "lemma", "addedAtEpochMillis"),
            keysOf("favorites"),
        )
        assertEquals(
            setOf(
                "schemaVersion", "recordId", "profileId", "sourceText", "translatedText", "engine",
                "imageAssetId", "createdAtEpochMillis",
            ),
            keysOf("photo-translation-records"),
        )
        assertEquals(setOf("schemaVersion", "id", "sha256", "byteSize", "createdAt"), keysOf("asset-records"))
    }

    @Test
    fun `asset payload never carries the private relative location`() {
        val records = BackupSnapshotBuilder.buildRecords(sampleRows, includeMediaAssets = true)

        val asset = records.first { it.path.startsWith("records/asset-records/") }

        assertFalse(asset.jsonText().contains("relativePath"))
        assertFalse(asset.jsonText().contains("assets/a-1"))
    }

    @Test
    fun `photo translation payload references only the asset id and never a private location`() {
        val records = BackupSnapshotBuilder.buildRecords(sampleRows, includeMediaAssets = true)

        val photo = records.first { it.path.startsWith("records/photo-translation-records/") }

        assertTrue(photo.jsonText().contains("\"imageAssetId\":\"a-1\""))
        assertFalse(photo.jsonText().contains("relativePath"))
        assertFalse(photo.jsonText().contains("assets/"))
    }

    @Test
    fun `a photo record saved without an image encodes imageAssetId as explicit null`() {
        val rowsWithoutImage = sampleRows.copy(
            photoTranslations = listOf(
                com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity(
                    "rec-2", "p-1", "Manual.", "手动。", "None", null, 45L,
                ),
            ),
        )

        val records = BackupSnapshotBuilder.buildRecords(rowsWithoutImage, includeMediaAssets = true)
        val photo = records.first { it.path.startsWith("records/photo-translation-records/") }

        assertTrue(photo.jsonText().contains("\"imageAssetId\":null"))
        assertTrue(photo.jsonText().contains("\"engine\":\"None\""))
    }

    @Test
    fun `nullable fields are encoded explicitly so serialization is stable`() {
        val records = BackupSnapshotBuilder.buildRecords(sampleRows, includeMediaAssets = true)

        val event = records.first { it.path.startsWith("records/learning-events/") }
        assertTrue(event.jsonText().contains("\"dueBeforeEpochMillis\":null"))
        val article = records.first { it.path.startsWith("records/articles/") }
        assertTrue(article.jsonText().contains("\"modelName\":null"))
    }

    @Test
    fun `excluding media drops asset records entirely`() {
        val withoutMedia = BackupSnapshotBuilder.buildRecords(sampleRows, includeMediaAssets = false)

        assertTrue(withoutMedia.none { it.path.contains("asset-records") })
        assertTrue(withoutMedia.any { it.path.contains("photo-translation-records") }, "文本记录不受媒体开关影响")
        assertTrue(withoutMedia.any { it.path.contains("favorites") }, "收藏不受媒体开关影响")
        assertEquals(17, withoutMedia.size)
    }

    @Test
    fun `same rows always produce byte identical records in path order`() {
        val first = BackupSnapshotBuilder.buildRecords(sampleRows, includeMediaAssets = true)
        val second = BackupSnapshotBuilder.buildRecords(sampleRows, includeMediaAssets = true)

        assertEquals(first.map { it.path }, second.map { it.path })
        first.zip(second).forEach { (a, b) -> assertContentEqualsByte(a.jsonText(), b.jsonText()) }
    }

    private fun assertContentEqualsByte(a: String, b: String) {
        assertEquals(a, b)
    }
}
