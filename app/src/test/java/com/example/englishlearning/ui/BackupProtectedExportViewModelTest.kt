package com.example.englishlearning.ui

import android.net.Uri
import com.example.englishlearning.backup.domain.BackupExportCoordinator
import com.example.englishlearning.backup.domain.BackupMediaGateway
import com.example.englishlearning.backup.domain.BackupMediaOutcome
import com.example.englishlearning.backup.domain.BackupRowReader
import com.example.englishlearning.backup.domain.BackupSecretEntropy
import com.example.englishlearning.backup.domain.BackupSourceRows
import com.example.englishlearning.backup.domain.MigrationSigner
import com.example.englishlearning.backup.infrastructure.ZipPackagePackager
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import io.mockk.mockk
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

/**
 * F4-04 遗留修复：受保护导出页的 SAF 落盘两段式接线。导出成功只生成内存待写包，
 * 用户经 SAF 选中位置才写入；取消零写入；失败保留待写包允许换位置重试（与 F4-03 同语义）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupProtectedExportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val createdAtMillis = 1_700_000_000_000L
    private val issuedAtSeconds = 1_700_000_000L
    private val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private class FakeRows : BackupRowReader {
        override suspend fun readAll() = BackupSourceRows(
            profiles = listOf(LocalProfileEntity("p-1", "Amy", 1L)),
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
            assetRecords = listOf(AssetRecordEntity("asset-1", "aa", "assets/asset-1", 3L, 1L)),
        )
    }

    private class FakeMedia : BackupMediaGateway {
        override suspend fun load(asset: AssetRecordEntity): BackupMediaOutcome = BackupMediaOutcome.Loaded(byteArrayOf(7, 7, 7))
    }

    /** 挑战签发用的确定性熵；打包侧（无密码路径）不需要额外熵源。 */
    private class FakeEntropy : BackupSecretEntropy {
        override fun nextBytes(count: Int): ByteArray = ByteArray(count) { (it + 1).toByte() }
    }

    private class FakeDestinationWriter : BackupDestinationWriter {
        var written: Pair<Uri, ByteArray>? = null
        var writeCalls = 0
        var result = true

        override suspend fun write(destination: Uri, bytes: ByteArray): Boolean {
            writeCalls += 1
            written = destination to bytes
            return result
        }
    }

    private fun executor(): ProtectedExportExecutor = ProtectedExportExecutor(
        signerFactory = {
            MigrationSigner { scope ->
                Signature.getInstance("SHA256withECDSA").run {
                    initSign(key.private)
                    update(scope)
                    sign()
                }
            }
        },
        keySpki = { key.public.encoded },
        delegate = BackupExportCoordinator(FakeRows(), FakeMedia(), ZipPackagePackager()) { createdAtMillis },
        entropy = FakeEntropy(),
        clock = { issuedAtSeconds },
    )

    private lateinit var writer: FakeDestinationWriter
    private lateinit var viewModel: BackupProtectedExportViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        writer = FakeDestinationWriter()
        viewModel = BackupProtectedExportViewModel(executor(), writer)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.authenticate() {
        viewModel.onAuthenticated()
        advanceUntilIdle()
    }

    private fun TestScope.exportReady() {
        authenticate()
        viewModel.exportProtected(includeMediaAssets = false, password = null)
        advanceUntilIdle()
    }

    @Test
    fun `authenticated phase issues a challenge with qr payload`() = runTest(dispatcher) {
        authenticate()

        val state = viewModel.state.value
        assertEquals(ProtectedExportPhase.Active, state.phase)
        assertNotNull(state.qrPayload)
    }

    @Test
    fun `successful protected export prepares an in-memory package with deterministic file name`() = runTest(dispatcher) {
        exportReady()

        val state = viewModel.state.value
        assertEquals(ProtectedExportPhase.Done, state.phase)
        assertTrue(state.packageReady)
        assertNull(state.error)
        assertEquals(suggestedFileName(createdAtMillis), state.fileName)
        assertTrue(state.message!!.contains("受保护备份包已生成"))
    }

    @Test
    fun `picking a destination writes the prepared package bytes and clears the pending package`() = runTest(dispatcher) {
        exportReady()
        val uri = mockk<Uri>()

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
        exportReady()

        viewModel.onDocumentPicked(null)
        advanceUntilIdle()

        assertEquals(0, writer.writeCalls)
        assertFalse(viewModel.state.value.packageReady)
        assertTrue(viewModel.state.value.message!!.contains("取消"))
    }

    @Test
    fun `write failure keeps the package pending so the user can retry another location`() = runTest(dispatcher) {
        exportReady()
        writer.result = false

        viewModel.onDocumentPicked(mockk())
        advanceUntilIdle()

        assertEquals("写入目标失败，请换一个位置重试。", viewModel.state.value.error)
        assertTrue(viewModel.state.value.packageReady)

        writer.result = true
        viewModel.onDocumentPicked(mockk())
        advanceUntilIdle()
        assertNotNull(writer.written)
        assertFalse(viewModel.state.value.packageReady)
    }

    @Test
    fun `picking a destination without a prepared package writes nothing`() = runTest(dispatcher) {
        viewModel.onDocumentPicked(mockk())
        advanceUntilIdle()

        assertEquals(0, writer.writeCalls)
    }

    @Test
    fun `starting a new export invalidates the previous pending package`() = runTest(dispatcher) {
        exportReady()
        assertTrue(viewModel.state.value.packageReady)

        viewModel.exportProtected(includeMediaAssets = false, password = null)
        // 新一轮生成尚未完成：旧待写字节必须已失效，此刻到达的 URI 不得落盘。
        viewModel.onDocumentPicked(mockk())
        advanceUntilIdle()

        assertEquals(0, writer.writeCalls)
        assertTrue(viewModel.state.value.packageReady)
    }

    // ---- D-10/D-11/D-9（AC4-08）：显示门控与诚实文案 ----

    @Test
    fun `authenticated session shows the pairing qr`() = runTest(dispatcher) {
        authenticate()

        assertTrue(viewModel.state.value.qrVisible, "Active 阶段签发即认证过，配对码应可见")
    }

    @Test
    fun `export completion hides the qr until re-authenticated reveal`() = runTest(dispatcher) {
        exportReady()

        assertFalse(viewModel.state.value.qrVisible, "Done 阶段不得常驻显示挑战码")
        val before = viewModel.state.value.qrPayload
        viewModel.onPairingRevealed()
        assertTrue(viewModel.state.value.qrVisible, "再次认证成功后重显同一配对码")
        assertEquals(before, viewModel.state.value.qrPayload, "重显不得重发新挑战")
    }

    @Test
    fun `saving the package to a destination keeps the qr hidden`() = runTest(dispatcher) {
        exportReady()
        viewModel.onDocumentPicked(mockk())
        advanceUntilIdle()

        assertFalse(viewModel.state.value.qrVisible)
    }

    @Test
    fun `reveal without an issued challenge does nothing`() = runTest(dispatcher) {
        viewModel.onPairingRevealed()

        assertFalse(viewModel.state.value.qrVisible)
        assertNull(viewModel.state.value.qrPayload)
    }

    @Test
    fun `signing unavailability is reported as an authentication-window honest message`() = runTest(dispatcher) {
        val failingExecutor = ProtectedExportExecutor(
            signerFactory = { MigrationSigner { null } },
            keySpki = { key.public.encoded },
            delegate = BackupExportCoordinator(FakeRows(), FakeMedia(), ZipPackagePackager()) { createdAtMillis },
            entropy = FakeEntropy(),
            clock = { issuedAtSeconds },
        )
        val vm = BackupProtectedExportViewModel(failingExecutor, writer)
        vm.onAuthenticated()
        advanceUntilIdle()

        vm.exportProtected(includeMediaAssets = false, password = null)
        advanceUntilIdle()

        val error = assertNotNull(vm.state.value.error)
        assertTrue(error.contains("认证"), "签名失败必须诚实提示认证窗口原因，实际：$error")
        assertEquals(ProtectedExportPhase.Active, vm.state.value.phase, "失败回到 Active，重新认证入口即导出按钮")
    }
}
