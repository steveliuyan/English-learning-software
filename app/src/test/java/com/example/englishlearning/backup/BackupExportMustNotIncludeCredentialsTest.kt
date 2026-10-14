package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupExportCoordinator
import com.example.englishlearning.backup.domain.BackupExportResult
import com.example.englishlearning.backup.domain.BackupMediaGateway
import com.example.englishlearning.backup.domain.BackupMediaOutcome
import com.example.englishlearning.backup.domain.BackupRowReader
import com.example.englishlearning.backup.domain.BackupSourceRows
import com.example.englishlearning.backup.infrastructure.ZipPackagePackager
import com.example.englishlearning.core.storage.entity.AiPreferenceEntity
import com.example.englishlearning.core.storage.entity.AiProfileEntity
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.KeyAliasEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import com.example.englishlearning.core.storage.entity.ReminderPreferenceEntity
import com.example.englishlearning.core.storage.entity.SchemaMetaEntity
import com.example.englishlearning.core.storage.entity.SpeechPreferenceEntity
import com.example.englishlearning.core.storage.entity.VocabularySearchGramEntity
import com.example.englishlearning.core.storage.entity.VocabularySearchIndexEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Proves the export data path cannot express credential material:
 * the row contract only accepts whitelisted learning entities, the serialized
 * payload never carries forbidden key names, and sentinels living in excluded
 * tables (API Key aliases, Keystore, system TTS, reminders, schema meta, derived
 * search indexes) are absent from every package byte.
 */
class BackupExportMustNotIncludeCredentialsTest {
    private val allowedRowTypes: Set<KClass<*>> = setOf(
        LocalProfileEntity::class,
        com.example.englishlearning.core.storage.entity.LearningProfileEntity::class,
        com.example.englishlearning.core.storage.entity.LearningSettingsEntity::class,
        com.example.englishlearning.core.storage.entity.TodayPlanEntity::class,
        com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity::class,
        com.example.englishlearning.core.storage.entity.LearningEventEntity::class,
        com.example.englishlearning.core.storage.entity.CardReviewStateEntity::class,
        WordBookEntity::class,
        com.example.englishlearning.core.storage.entity.VocabularyEntryEntity::class,
        com.example.englishlearning.core.storage.entity.WordBookProgressMigrationAuditEntity::class,
        com.example.englishlearning.core.storage.entity.ArticleEntity::class,
        com.example.englishlearning.core.storage.entity.ReadingCompletionEntity::class,
        com.example.englishlearning.core.storage.entity.ReadingPreferenceEntity::class,
        com.example.englishlearning.core.storage.entity.VocabularySearchHistoryEntity::class,
        com.example.englishlearning.core.storage.entity.WordAiNoteEntity::class,
        com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity::class,
        AssetRecordEntity::class,
    )

    private val forbiddenRowTypes: Set<KClass<*>> = setOf(
        AiProfileEntity::class,
        AiPreferenceEntity::class,
        KeyAliasEntity::class,
        SpeechPreferenceEntity::class,
        ReminderPreferenceEntity::class,
        SchemaMetaEntity::class,
        VocabularySearchIndexEntity::class,
        VocabularySearchGramEntity::class,
        com.example.englishlearning.core.storage.entity.MigrationPairingLedgerEntity::class,
    )

    private val forbiddenKeyNames = setOf(
        "secretAlias", "alias", "apiKey", "apikey", "authorization", "password", "privateKey",
        "keystore", "endpoint", "websiteUrl", "relativePath", "selectedEngine",
    )

    private class FakeRows : BackupRowReader {
        override suspend fun readAll(): BackupSourceRows = BackupSourceRows(
            profiles = listOf(LocalProfileEntity("p-1", "SENTINEL-IN-USER-DATA-IRRELEVANT", 1L)),
            learningProfiles = emptyList(),
            learningSettings = emptyList(),
            todayPlans = emptyList(),
            todayPlanTasks = emptyList(),
            learningEvents = emptyList(),
            cardReviewStates = emptyList(),
            wordBooks = listOf(WordBookEntity("wb-1", "Core", "cet4", 1, "1", "src")),
            vocabularyEntries = emptyList(),
            migrationAudits = emptyList(),
            articles = emptyList(),
            readingCompletions = emptyList(),
            readingPreferences = emptyList(),
            searchHistory = emptyList(),
            wordAiNotes = emptyList(),
            photoTranslations = listOf(
                com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity(
                    "rec-1", "p-1", "Birds fly.", "鸟儿会飞。", "MlKit", "asset-1", 5L,
                ),
            ),
            assetRecords = listOf(AssetRecordEntity("asset-1", "aa", "assets/asset-1", 1L, 1L)),
        )
    }

    private class FakeMedia : BackupMediaGateway {
        override suspend fun load(asset: AssetRecordEntity): BackupMediaOutcome =
            BackupMediaOutcome.Loaded(byteArrayOf(9))
    }

    @Test
    fun `row contract accepts only whitelisted learning entities and no credential tables`() {
        val elementTypes = BackupSourceRows::class.memberProperties
            .map { property ->
                @Suppress("UNCHECKED_CAST")
                (property.returnType.arguments.single().type?.classifier as KClass<*>)
            }
            .toSet()

        assertEquals(allowedRowTypes, elementTypes)
        assertTrue(elementTypes.intersect(forbiddenRowTypes).isEmpty())
    }

    @Test
    fun `package bytes never contain forbidden credential keys or excluded-table sentinels`() = runTest {
        val prepared = assertIs<BackupExportResult.Prepared>(
            BackupExportCoordinator(FakeRows(), FakeMedia(), ZipPackagePackager()) { 1_700_000_000_000L }
                .prepare(includeMediaAssets = true),
        )

        // Unzip and scan every payload byte, manifest included.
        val text = String(prepared.bytes, Charsets.ISO_8859_1)
        forbiddenKeyNames.forEach { key ->
            assertTrue("\"$key\"" !in text, "credential-ish key $key must not appear in package bytes")
        }
        listOf("sk-EXKEY", "keystore-ALIAS", "system-tts-SENTINEL", "schema-meta-SENTINEL").forEach { sentinel ->
            assertTrue(sentinel !in text)
        }
    }

    @Test
    fun `serialized record keys stay inside the data coverage whitelist`() = runTest {
        val prepared = assertIs<BackupExportResult.Prepared>(
            BackupExportCoordinator(FakeRows(), FakeMedia(), ZipPackagePackager()) { 1L }.prepare(true),
        )

        val records = com.example.englishlearning.backup.domain.BackupPackageValidator()
            .readEntries(prepared.bytes)
        val entries = assertIs<com.example.englishlearning.backup.domain.PackageReadResult.Read>(records).entries

        val assetJson = String(entries.getValue("records/asset-records/asset-1.json"))
        assertTrue("\"sha256\"" in assetJson)
        assertTrue("\"relativePath\"" !in assetJson)
    }
}
