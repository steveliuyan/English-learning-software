package com.example.englishlearning.ui

import android.net.Uri
import com.example.englishlearning.backup.pairing.MigrationChallenge
import com.example.englishlearning.backup.pairing.MigrationChallengePolicy
import com.example.englishlearning.backup.pairing.MigrationCodeCodec
import com.example.englishlearning.backup.pairing.MigrationCodeFields
import com.example.englishlearning.backup.restore.BackupRestoreCoordinator
import com.example.englishlearning.backup.restore.ProcessRestarter
import com.example.englishlearning.backup.restore.RestoreCommitResult
import com.example.englishlearning.backup.restore.RestoreFailure
import com.example.englishlearning.backup.restore.RestoreOutcome
import com.example.englishlearning.backup.restore.StagedRestore
import com.example.englishlearning.backup.restore.StagedRestoreSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D-12（AC4-07）：替换提交一旦开始（成功或失败回滚），进程级数据库已被
 * `checkpointAndClose` 关闭且永不复用——当前进程的数据访问已终止，UI 必须
 * 要求重启应用后才可继续使用；commit 之前的任何失败不触发重启要求。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val coordinator = mockk<BackupRestoreCoordinator>()
    private val restarter = mockk<ProcessRestarter>(relaxed = true)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val code = MigrationCodeFields(
        publicKeySpki = ByteArray(91) { 1 },
        nonce = ByteArray(MigrationChallengePolicy.NONCE_BYTES) { 2 },
        secret = ByteArray(MigrationChallengePolicy.SECRET_BYTES) { 3 },
        issuedAtEpochSeconds = 1_700_000_000L,
        expiresAtEpochSeconds = 1_700_000_000L + MigrationChallengePolicy.CHALLENGE_TTL_SECONDS,
    )

    private val session = StagedRestoreSession(
        sessionId = "s1",
        recordCount = 23,
        mediaCount = 0,
        plaintextBytes = 1024L,
        pairingIssuedAtEpochSeconds = code.issuedAtEpochSeconds,
        pairingExpiresAtEpochSeconds = code.expiresAtEpochSeconds,
        staged = StagedRestore(emptyList(), emptyList()),
    )

    private fun newViewModel(): BackupRestoreViewModel = BackupRestoreViewModel(coordinator, restarter)

    private fun rawCode(): String = MigrationCodeCodec.encode(
        MigrationChallenge(
            publicKeySpki = code.publicKeySpki,
            nonce = code.nonce,
            secret = code.secret,
            issuedAtEpochSeconds = code.issuedAtEpochSeconds,
            expiresAtEpochSeconds = code.expiresAtEpochSeconds,
        ),
    )

    private fun stubPrepareStage() {
        every { coordinator.prepare(any(), any(), any()) } returns RestoreOutcome.Staged(session)
    }

    private suspend fun TestScope.pickAndScan(vm: BackupRestoreViewModel) {
        vm.onPackagePicked(mockk<Uri>(relaxed = true), byteArrayOf(1, 2, 3))
        vm.onCodeScanned(rawCode())
        advanceUntilIdle()
    }

    private suspend fun TestScope.stageSuccessfully(vm: BackupRestoreViewModel) {
        stubPrepareStage()
        pickAndScan(vm)
        assertNotNull(vm.state.value.staged, "must reach Confirming")
    }

    @Test
    fun `successful replace commit requires app restart and names restart in result message`() = runTest(dispatcher) {
        val vm = newViewModel()
        every { coordinator.commit(session) } returns RestoreCommitResult.Restored
        stageSuccessfully(vm)
        vm.onReplaceConfirmed()
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state.appRestartRequired, "restored commit must require restart (AC4-07)")
        assertContains(state.resultMessage!!, "重启")
    }

    @Test
    fun `failed commit after rollback also requires app restart`() = runTest(dispatcher) {
        val vm = newViewModel()
        every { coordinator.commit(session) } returns
            RestoreCommitResult.Failed(RestoreFailure.RollbackVerification)
        stageSuccessfully(vm)
        vm.onReplaceConfirmed()
        advanceUntilIdle()
        assertTrue(
            vm.state.value.appRestartRequired,
            "commit was attempted: formal db was closed and rolled back—old process data access is dead",
        )
    }

    @Test
    fun `failure before commit does not require app restart`() = runTest(dispatcher) {
        val vm = newViewModel()
        every { coordinator.prepare(any(), any(), any()) } returns
            RestoreOutcome.Failed(RestoreFailure.IntegrityFailure)
        pickAndScan(vm)
        assertFalse(vm.state.value.appRestartRequired)
    }

    @Test
    fun `restartNow delegates to the process restarter`() = runTest(dispatcher) {
        val vm = newViewModel()
        every { coordinator.commit(session) } returns RestoreCommitResult.Restored
        stageSuccessfully(vm)
        vm.onReplaceConfirmed()
        advanceUntilIdle()
        vm.restartNow()
        verify(exactly = 1) { restarter.relaunch() }
    }

    @Test
    fun `cancel before commit never demands restart`() = runTest(dispatcher) {
        val vm = newViewModel()
        every { coordinator.abandon("s1") } returns Unit
        stageSuccessfully(vm)
        vm.cancel()
        assertFalse(vm.state.value.appRestartRequired)
    }
}
