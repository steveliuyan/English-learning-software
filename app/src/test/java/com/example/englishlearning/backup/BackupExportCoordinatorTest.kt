package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupExportCoordinator
import com.example.englishlearning.backup.domain.BackupExportFailure
import com.example.englishlearning.backup.domain.BackupExportResult
import com.example.englishlearning.backup.domain.BackupMediaGateway
import com.example.englishlearning.backup.domain.BackupMediaOutcome
import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.BackupRowReader
import com.example.englishlearning.backup.domain.BackupSourceRows
import com.example.englishlearning.backup.domain.PackageReadResult
import com.example.englishlearning.backup.domain.PackageValidationResult
import com.example.englishlearning.backup.infrastructure.ZipPackagePackager
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupExportCoordinatorTest {
    private val createdAt = 1_700_000_000_000L

    private val baseRows = BackupSourceRows(
        profiles = listOf(LocalProfileEntity("p-1", "Me", 100L)),
        learningProfiles = emptyList(),
        learningSettings = emptyList(),
        todayPlans = emptyList(),
        todayPlanTasks = emptyList(),
        learningEvents = emptyList(),
        cardReviewStates = emptyList(),
        wordBooks = listOf(WordBookEntity("wb-1", "Core", "cet4", 100, "1", "src")),
        vocabularyEntries = emptyList(),
        migrationAudits = emptyList(),
        articles = emptyList(),
        readingCompletions = emptyList(),
        readingPreferences = emptyList(),
        searchHistory = emptyList(),
        wordAiNotes = emptyList(),
        photoTranslations = emptyList(),
        favorites = listOf(com.example.englishlearning.core.storage.entity.FavoriteWordEntity("p-1", "card-1", "wb-1", "cat", 95L)),
        assetRecords = listOf(AssetRecordEntity("asset-1", "hash-1", "assets/asset-1", 3L, 40L)),
    )

    private val assetBytes = byteArrayOf(1, 2, 3)

    private class FakeRowReader(var rows: BackupSourceRows) : BackupRowReader {
        override suspend fun readAll(): BackupSourceRows = rows
    }

    private class FakeMedia(
        private val stored: Map<String, ByteArray> = mapOf("asset-1" to byteArrayOf(1, 2, 3)),
        private val mismatchIds: Set<String> = emptySet(),
    ) : BackupMediaGateway {
        var calls = 0
            private set

        override suspend fun load(asset: AssetRecordEntity): BackupMediaOutcome {
            calls += 1
            if (asset.id in mismatchIds) return BackupMediaOutcome.Mismatch
            val bytes = stored[asset.id] ?: return BackupMediaOutcome.Missing
            return BackupMediaOutcome.Loaded(bytes)
        }
    }

    private fun coordinator(
        rows: BackupSourceRows = baseRows,
        media: BackupMediaGateway = FakeMedia(),
    ) = BackupExportCoordinator(FakeRowReader(rows), media, ZipPackagePackager()) { createdAt }

    @Test
    fun `prepares a valid v2 package listing every record and resource`() = runTest {
        val result = coordinator().prepare(includeMediaAssets = true)

        val prepared = assertIs<BackupExportResult.Prepared>(result)
        assertEquals(createdAt, prepared.createdAtEpochMillis)
        assertEquals(4, prepared.recordCount)
        assertEquals(1, prepared.mediaCount)

        val validator = BackupPackageValidator()
        val valid = assertIs<PackageValidationResult.Valid>(validator.validate(prepared.bytes))
        assertEquals(listOf("asset-records", "favorites", "media", "profiles", "word-books"), valid.manifest.scope)
        assertEquals(createdAt, valid.manifest.createdAtEpochMillis)
        assertEquals("not-protected", valid.manifest.signatureStatus)
        val paths = valid.manifest.entries.map { it.path }.toSet()
        assertTrue("records/profiles/p-1.json" in paths)
        assertTrue("records/word-books/wb-1.json" in paths)
        assertTrue(paths.any { it.startsWith("records/favorites/") }, "收藏走 records/favorites/<sha256>.json：$paths")
        assertTrue("resources/asset-1" in paths)

        val read = assertIs<PackageReadResult.Read>(validator.readEntries(prepared.bytes))
        assertContentEqualsList(assetBytes.toList(), read.entries.getValue("resources/asset-1").toList())
        assertTrue(String(read.entries.getValue("records/profiles/p-1.json")).contains("\"displayName\":\"Me\""))
    }

    @Test
    fun `same snapshot and same creation time produce identical bytes and different time changes them`() = runTest {
        val first = assertIs<BackupExportResult.Prepared>(coordinator().prepare(includeMediaAssets = true))
        val second = assertIs<BackupExportResult.Prepared>(coordinator().prepare(includeMediaAssets = true))
        assertContentEqualsList(first.bytes.toList(), second.bytes.toList())

        val changedRows = baseRows.copy(profiles = listOf(LocalProfileEntity("p-1", "Renamed", 100L)))
        val third = assertIs<BackupExportResult.Prepared>(
            BackupExportCoordinator(FakeRowReader(changedRows), FakeMedia(), ZipPackagePackager()) { createdAt }.prepare(true),
        )
        val digestOf = { bytes: ByteArray -> BackupPackageValidator().validate(bytes).let { (it as PackageValidationResult.Valid).manifest.payloadSha256 } }
        assertTrue(digestOf(first.bytes) != digestOf(third.bytes))
    }

    @Test
    fun `missing media fails the whole export instead of silently skipping`() = runTest {
        val result = coordinator(media = FakeMedia(stored = emptyMap())).prepare(includeMediaAssets = true)

        assertEquals(BackupExportResult.Failed(BackupExportFailure.ResourceUnavailable), result)
    }

    @Test
    fun `media byte mismatch fails with integrity category`() = runTest {
        val result = coordinator(media = FakeMedia(mismatchIds = setOf("asset-1"))).prepare(includeMediaAssets = true)

        assertEquals(BackupExportResult.Failed(BackupExportFailure.IntegrityFailure), result)
    }

    @Test
    fun `excluding media drops asset records and never touches the media gateway`() = runTest {
        val media = object : BackupMediaGateway {
            override suspend fun load(asset: AssetRecordEntity): BackupMediaOutcome =
                error("media gateway must not be called")
        }

        val result = coordinator(media = media).prepare(includeMediaAssets = false)

        val prepared = assertIs<BackupExportResult.Prepared>(result)
        assertEquals(0, prepared.mediaCount)
        val valid = assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(prepared.bytes))
        assertTrue(valid.manifest.entries.none { it.path.startsWith("resources/") || it.path.contains("asset-records") })
        assertEquals(listOf("favorites", "profiles", "word-books"), valid.manifest.scope)
    }

    @Test
    fun `asset id that is not a safe package id fails as invalid snapshot`() = runTest {
        val rows = baseRows.copy(assetRecords = listOf(AssetRecordEntity("bad/../id", "hash", "x", 3L, 1L)))

        val result = coordinator(rows = rows).prepare(includeMediaAssets = true)

        assertEquals(BackupExportResult.Failed(BackupExportFailure.InvalidSnapshot), result)
    }

    @Test
    fun `packager limit failure surfaces as limit exceeded`() = runTest {
        val huge = ByteArray(70_000_000)
        val rows = baseRows.copy(assetRecords = listOf(AssetRecordEntity("asset-1", "hash-1", "assets/asset-1", huge.size.toLong(), 40L)))

        val result = coordinator(rows = rows, media = FakeMedia(stored = mapOf("asset-1" to huge))).prepare(includeMediaAssets = true)

        assertEquals(BackupExportResult.Failed(BackupExportFailure.LimitExceeded), result)
    }

    private fun assertContentEqualsList(expected: List<Byte>, actual: List<Byte>) {
        assertEquals(expected, actual)
    }
}
