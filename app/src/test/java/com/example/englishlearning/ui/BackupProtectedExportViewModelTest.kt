package com.example.englishlearning.ui

import android.net.Uri
import com.example.englishlearning.backup.domain.BackupExportCoordinator
import com.example.englishlearning.backup.domain.BackupMediaGateway
import com.example.englishlearning.backup.domain.BackupMediaOutcome
import com.example.englishlearning.backup.domain.BackupRowReader
import com.example.englishlearning.backup.domain.BackupSecretEntropy
import com.example.englishlearning.backup.domain.BackupSourceRows
import com.example.englishlearning.backup.domain.MigrationSigner
import com.example.englishlearning.backup.pairing.ConsumedChallengeLedger
import com.example.englishlearning.backup.pairing.MigrationCodeCodec
import com.example.englishlearning.backup.pairing.MigrationCodeDecodeResult
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
import kotlin.test.assertIs
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
            favorites = emptyList(),
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

    /** AC4-05 台账替身：记录被消费的 nonce；可预置已消费 nonce、模拟读/写故障。 */
    private class FakeLedger : ConsumedChallengeLedger {
        val consumed = mutableListOf<ByteArray>()
        var seeded: List<ByteArray> = emptyList()
        var markFails = false
        var readFails = false

        override suspend fun markConsumed(nonce: ByteArray) {
            if (markFails) throw IllegalStateException("ledger write failed")
            consumed += nonce.copyOf()
        }

        override suspend fun isConsumed(nonce: ByteArray): Boolean {
            if (readFails) throw IllegalStateException("ledger read failed")
            return (seeded + consumed).any { it.contentEquals(nonce) }
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
    private lateinit var ledger: FakeLedger
    private lateinit var viewModel: BackupProtectedExportViewModel

    /** FakeEntropy 下签发的 nonce 恒为 1..16（NONCE_BYTES=16）。 */
    private val expectedNonce = ByteArray(16) { (it + 1).toByte() }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        writer = FakeDestinationWriter()
        ledger = FakeLedger()
        viewModel = BackupProtectedExportViewModel(executor(), writer, ledger)
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
    fun `a second export attempt with a consumed pairing code is rejected and keeps the first package saveable`() = runTest(dispatcher) {
        exportReady()
        assertTrue(viewModel.state.value.packageReady)

        viewModel.exportProtected(includeMediaAssets = false, password = null)
        advanceUntilIdle()

        val error = assertNotNull(viewModel.state.value.error)
        assertTrue(error.contains("已用于生成"), "必须诚实说明配对码已消费，实际：$error")
        assertTrue(error.contains("重新"), "必须指引重新认证获取新配对码，实际：$error")
        // 首个包仍属于这次已完成的签名，消费不撤销它：待写包不得被二次尝试清掉。
        assertTrue(viewModel.state.value.packageReady, "被拒的二次尝试不得破坏已产出待写包")
        assertEquals(1, ledger.consumed.size, "二次尝试不得再次签名或再次登记")

        viewModel.onDocumentPicked(mockk())
        advanceUntilIdle()
        assertEquals(1, writer.writeCalls, "原待写包必须仍可落盘")
    }

    // ---- AC4-05：签发即消费（已使用配对码不得再签新包） ----

    @Test
    fun `producing a protected package consumes the pairing code in the ledger`() = runTest(dispatcher) {
        exportReady()

        val decoded = assertIs<MigrationCodeDecodeResult.Parsed>(
            MigrationCodeCodec.decode(assertNotNull(viewModel.state.value.qrPayload)),
        )
        assertEquals(1, ledger.consumed.size, "成功产包必须恰好登记一次消费")
        assertContentEquals(decoded.fields.nonce, ledger.consumed.single(), "登记的必须是本次签发的 nonce")
        assertContentEquals(expectedNonce, ledger.consumed.single())
    }

    @Test
    fun `a failed export does not consume the pairing code so the retry can still succeed`() = runTest(dispatcher) {
        var signAllowed = false
        val flipExecutor = ProtectedExportExecutor(
            signerFactory = {
                MigrationSigner { scope ->
                    if (!signAllowed) null else {
                        Signature.getInstance("SHA256withECDSA").run {
                            initSign(key.private)
                            update(scope)
                            sign()
                        }
                    }
                }
            },
            keySpki = { key.public.encoded },
            delegate = BackupExportCoordinator(FakeRows(), FakeMedia(), ZipPackagePackager()) { createdAtMillis },
            entropy = FakeEntropy(),
            clock = { issuedAtSeconds },
        )
        val retryLedger = FakeLedger()
        val vm = BackupProtectedExportViewModel(flipExecutor, writer, retryLedger)
        vm.onAuthenticated()
        advanceUntilIdle()

        vm.exportProtected(includeMediaAssets = false, password = null)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error, "D-9：签名不可用必须是可见错误")
        assertEquals(0, retryLedger.consumed.size, "失败的导出不得消费配对码")

        signAllowed = true
        vm.exportProtected(includeMediaAssets = false, password = null)
        advanceUntilIdle()
        assertTrue(vm.state.value.packageReady, "失败后重试必须仍可签名产包")
        assertEquals(1, retryLedger.consumed.size)
    }

    @Test
    fun `a pairing code already used in a previous session is refused before any signing`() = runTest(dispatcher) {
        ledger.seeded = listOf(expectedNonce)

        authenticate()
        viewModel.exportProtected(includeMediaAssets = false, password = null)
        advanceUntilIdle()

        val error = assertNotNull(viewModel.state.value.error)
        assertTrue(error.contains("已用于生成"), "台账命中必须给出消费文案，实际：$error")
        assertFalse(viewModel.state.value.packageReady, "被拒的导出不得产出包")
        assertEquals(0, ledger.consumed.size, "台账已命中的 nonce 不重复登记")
    }

    @Test
    fun `ledger write failure after packaging voids the package and reports honestly`() = runTest(dispatcher) {
        ledger.markFails = true

        authenticate()
        viewModel.exportProtected(includeMediaAssets = false, password = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.packageReady, "无法登记消费即无法保证一次性：包必须作废（fail-closed）")
        viewModel.onDocumentPicked(mockk())
        advanceUntilIdle()
        assertEquals(0, writer.writeCalls, "作废的包不得被落盘")
        assertNotNull(state.error)
    }

    @Test
    fun `a consumed pairing code can still be revealed for the pending package`() = runTest(dispatcher) {
        exportReady()
        assertEquals(1, ledger.consumed.size)
        val payload = viewModel.state.value.qrPayload

        viewModel.onPairingRevealed()

        assertTrue(viewModel.state.value.qrVisible, "AC4-08：重显是配对所需，不因消费而被拒")
        assertEquals(payload, viewModel.state.value.qrPayload, "重显仍是同一配对码")
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
    fun `migration key unavailability surfaces a visible honest error`() = runTest(dispatcher) {
        viewModel.onMigrationKeyUnavailable()

        val error = assertNotNull(viewModel.state.value.error)
        assertTrue(error.contains("锁屏"), "必须指引用户启用安全锁屏，实际：$error")
        assertEquals(ProtectedExportPhase.Idle, viewModel.state.value.phase)
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
        val vm = BackupProtectedExportViewModel(failingExecutor, writer, ledger)
        vm.onAuthenticated()
        advanceUntilIdle()

        vm.exportProtected(includeMediaAssets = false, password = null)
        advanceUntilIdle()

        val error = assertNotNull(vm.state.value.error)
        assertTrue(error.contains("认证"), "签名失败必须诚实提示认证窗口原因，实际：$error")
        assertEquals(ProtectedExportPhase.Active, vm.state.value.phase, "失败回到 Active，重新认证入口即导出按钮")
    }
}
