package com.example.englishlearning.ui

import android.net.Uri
import com.example.englishlearning.backup.domain.BackupExportCoordinator
import com.example.englishlearning.backup.domain.BackupMediaGateway
import com.example.englishlearning.backup.domain.BackupMediaOutcome
import com.example.englishlearning.backup.domain.BackupRowReader
import com.example.englishlearning.backup.domain.BackupSourceRows
import com.example.englishlearning.backup.domain.PackagePackager
import com.example.englishlearning.backup.domain.PackagePackagingOutcome
import com.example.englishlearning.backup.domain.PackagerEntry
import com.example.englishlearning.backup.infrastructure.ZipPackagePackager
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupExportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val createdAt = Instant.ofEpochMilli(1_700_000_000_000L)

    private class FakeReader(var rows: BackupSourceRows) : BackupRowReader {
        override suspend fun readAll() = rows
    }

    private class TrackingMedia : BackupMediaGateway {
        var calls = 0
        override suspend fun load(asset: AssetRecordEntity): BackupMediaOutcome {
            calls += 1
            return BackupMediaOutcome.Loaded(byteArrayOf(1))
        }
    }

    private class MissingMedia : BackupMediaGateway {
        override suspend fun load(asset: AssetRecordEntity) = BackupMediaOutcome.Missing
    }

    private class FakeWriter : BackupDestinationWriter {
        var written: Pair<Uri, ByteArray>? = null
        var result = true

        override suspend fun write(destination: Uri, bytes: ByteArray): Boolean {
            written = destination to bytes
            return result
        }
    }

    private val rows = BackupSourceRows(
        profiles = listOf(LocalProfileEntity("p-1", "Me", 1L)),
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
        assetRecords = listOf(AssetRecordEntity("asset-1", "aa", "assets/asset-1", 1L, 1L)),
    )

    private lateinit var writer: FakeWriter
    private lateinit var media: TrackingMedia
    private lateinit var viewModel: BackupExportViewModel

    @BeforeEach
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        writer = FakeWriter()
        media = TrackingMedia()
        viewModel = BackupExportViewModel(
            coordinator = BackupExportCoordinator(FakeReader(rows), media, ZipPackagePackager()) { createdAt.toEpochMilli() },
            destinationWriter = writer,
        )
    }

    @AfterEach
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows honest salvage disclosure and no ready package`() {
        val state = viewModel.state.value

        assertTrue(state.disclosure.contains("不可用于认证恢复"))
        assertTrue(state.disclosure.contains("不包含 API Key"))
        assertFalse(state.packageReady)
        assertFalse(state.busy)
    }

    @Test
    fun `prepare produces a ready package with deterministic file name and size estimate`() = runTest(dispatcher) {
        viewModel.prepare()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.packageReady)
        assertNull(state.error)
        // 2 records (profile + asset-records), 1 media item.
        assertTrue(state.summary.contains("2"), state.summary)
        assertEquals(suggestedFileName(createdAt.toEpochMilli()), state.fileName)
        assertTrue(state.fileName!!.endsWith(".elpbak"))
    }

    @Test
    fun `saving writes the prepared bytes to the picked destination and clears pending package`() = runTest(dispatcher) {
        viewModel.prepare()
        advanceUntilIdle()
        val uri = mockk<Uri>()
        writer.result = true

        viewModel.onDocumentPicked(uri)
        advanceUntilIdle()

        val saved = assertNotNull(writer.written)
        assertEquals(uri, saved.first)
        assertContentEquals(byteArrayOf(0x50, 0x4b), saved.second.take(2).toByteArray())
        assertFalse(viewModel.state.value.packageReady)
        assertTrue(viewModel.state.value.message!!.contains("已导出"))
    }

    @Test
    fun `cancelling the location picker writes nothing and discards the package`() = runTest(dispatcher) {
        viewModel.prepare()
        advanceUntilIdle()

        viewModel.onDocumentPicked(null)
        advanceUntilIdle()

        assertNull(writer.written)
        assertFalse(viewModel.state.value.packageReady)
        assertTrue(viewModel.state.value.message!!.contains("取消"))
    }

    @Test
    fun `destination write failure maps to the fixed copy without leaking details`() = runTest(dispatcher) {
        viewModel.prepare()
        advanceUntilIdle()
        writer.result = false

        viewModel.onDocumentPicked(mockk())
        advanceUntilIdle()

        assertEquals("写入目标失败，请换一个位置重试。", viewModel.state.value.error)
    }

    @Test
    fun `missing media fails with fixed category and never marks the package ready`() = runTest(dispatcher) {
        val failing = BackupExportViewModel(
            coordinator = BackupExportCoordinator(FakeReader(rows), MissingMedia(), ZipPackagePackager()) { createdAt.toEpochMilli() },
            destinationWriter = FakeWriter(),
        )

        failing.prepare()
        advanceUntilIdle()

        assertFalse(failing.state.value.packageReady)
        assertEquals("部分 AI 音频文件缺失，已停止导出。", failing.state.value.error)
    }

    @Test
    fun `audio opt out skips the media gateway entirely`() = runTest(dispatcher) {
        viewModel.setIncludeAudio(false)
        viewModel.prepare()
        advanceUntilIdle()

        assertEquals(0, media.calls)
        assertTrue(viewModel.state.value.packageReady)
    }

    @Test
    fun `changing scope after a prepared package invalidates the stale pending bytes`() = runTest(dispatcher) {
        viewModel.prepare()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.packageReady)

        viewModel.setIncludeAudio(false)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.packageReady)
        val uri = mockk<Uri>()
        viewModel.onDocumentPicked(uri)
        advanceUntilIdle()
        assertNull(writer.written)
    }

    @Test
    fun `packager rejection maps to a fixed archive error`() = runTest(dispatcher) {
        val rejecting = BackupExportViewModel(
            coordinator = BackupExportCoordinator(
                FakeReader(rows),
                TrackingMedia(),
                object : PackagePackager {
                    override fun write(entries: List<PackagerEntry>, createdAtEpochMillis: Long) =
                        PackagePackagingOutcome.Invalid(com.example.englishlearning.backup.domain.PackageValidationFailure.InvalidPath)
                },
            ) { createdAt.toEpochMilli() },
            destinationWriter = FakeWriter(),
        )

        rejecting.prepare()
        advanceUntilIdle()

        assertEquals("备份包生成失败，请重试或联系支持。", rejecting.state.value.error)
        assertFalse(rejecting.state.value.packageReady)
    }
}
