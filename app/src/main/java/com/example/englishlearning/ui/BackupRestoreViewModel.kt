package com.example.englishlearning.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.backup.domain.BackupCryptoPolicy
import com.example.englishlearning.backup.pairing.MigrationCodeCodec
import com.example.englishlearning.backup.pairing.MigrationCodeDecodeResult
import com.example.englishlearning.backup.pairing.MigrationCodeFields
import com.example.englishlearning.backup.restore.BackupRestoreCoordinator
import com.example.englishlearning.backup.restore.RestoreCommitResult
import com.example.englishlearning.backup.restore.RestoreFailure
import com.example.englishlearning.backup.restore.RestoreOutcome
import com.example.englishlearning.backup.restore.StagedRestoreSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * F4-04 新设备恢复流状态机（AC4-06 无旁路）：
 * 选文件 → 扫码配对 →（如设密码）输入密码 → 验证/暂存 → 二次确认替换 → 结果。
 * 没有跳过校验、强制导入或降级为 Salvage 恢复的状态——旧的 v2 包在验证链入口
 * 即被拒绝（SalvageNotRestorable），密码遗忘时没有恢复路径（诚实文案）。
 */
data class BackupRestoreUiState(
    val phase: BackupRestorePhase = BackupRestorePhase.Picking,
    val busy: Boolean = false,
    val staged: StagedRestoreSession? = null,
    val scannedCode: MigrationCodeFields? = null,
    val needsPassword: Boolean = false,
    val passwordConsumed: Boolean = false,
    val errorMessage: String? = null,
    val resultMessage: String? = null,
)

enum class BackupRestorePhase { Picking, Scanning, Password, Confirming, Restoring, Done }

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val coordinator: BackupRestoreCoordinator,
) : ViewModel() {

    private val mutable = MutableStateFlow(BackupRestoreUiState())
    val state: StateFlow<BackupRestoreUiState> = mutable.asStateFlow()

    /** The user-selected package bytes; held in memory only, cleared on cancel/finish. */
    private var packageBytes: ByteArray? = null

    fun onPackagePicked(uri: Uri?, bytes: ByteArray?) {
        if (uri == null || bytes == null) {
            mutable.value = mutable.value.copy(resultMessage = "已取消选择备份文件。")
            return
        }
        packageBytes = bytes
        mutable.value = mutable.value.copy(phase = BackupRestorePhase.Scanning, errorMessage = null)
    }

    fun onCodeScanned(raw: String) {
        val bytes = packageBytes ?: return
        val code = when (val parsed = MigrationCodeCodec.decode(raw)) {
            is MigrationCodeDecodeResult.Parsed -> parsed.fields
            MigrationCodeDecodeResult.Malformed -> {
                mutable.value = mutable.value.copy(errorMessage = fixedMessage(RestoreFailure.PairingMismatch))
                return
            }
        }
        mutable.value = mutable.value.copy(busy = true, scannedCode = code, errorMessage = null)
        viewModelScope.launch {
            dispatchPrepare(bytes, code, passwordChars = null)
        }
    }

    fun onPasswordEntered(password: CharArray) {
        val bytes = packageBytes ?: return
        val code = mutable.value.scannedCode ?: return
        mutable.value = mutable.value.copy(busy = true, errorMessage = null)
        viewModelScope.launch {
            dispatchPrepare(bytes, code, password)
        }
    }

    private fun dispatchPrepare(bytes: ByteArray, code: MigrationCodeFields, passwordChars: CharArray?) {
        when (val outcome = coordinator.prepare(bytes, code, passwordChars)) {
            is RestoreOutcome.Staged -> mutable.value = mutable.value.copy(
                busy = false,
                phase = BackupRestorePhase.Confirming,
                staged = outcome.session,
            )
            is RestoreOutcome.Failed ->
                if (outcome.reason == RestoreFailure.PasswordRequired) {
                    mutable.value = mutable.value.copy(busy = false, phase = BackupRestorePhase.Password)
                } else {
                    failWith(outcome.reason)
                }
        }
    }

    /** Second confirmation: the user explicitly accepts "替换本机数据、不可合并". */
    fun onReplaceConfirmed() {
        val session = mutable.value.staged ?: return
        mutable.value = mutable.value.copy(busy = true, phase = BackupRestorePhase.Restoring)
        viewModelScope.launch {
            when (val result = coordinator.commit(session)) {
                RestoreCommitResult.Restored -> mutable.value = mutable.value.copy(
                    busy = false,
                    phase = BackupRestorePhase.Done,
                    resultMessage = RESTORED_MESSAGE,
                )
                is RestoreCommitResult.Failed -> failWith(result.reason)
            }
        }
    }

    fun cancel() {
        mutable.value.staged?.let { coordinator.abandon(it.sessionId) }
        packageBytes = null
        mutable.value = BackupRestoreUiState(resultMessage = "已取消恢复，本机数据未改动。")
    }

    /** 出结果或失败后清掉内存中的包字节。 */
    fun dismissResult() {
        packageBytes = null
        mutable.value = BackupRestoreUiState()
    }

    private fun failWith(reason: RestoreFailure) {
        packageBytes = null
        mutable.value = mutable.value.copy(
            busy = false,
            phase = BackupRestorePhase.Done,
            staged = null,
            errorMessage = fixedMessage(reason),
        )
    }

    fun passwordShapeError(length: Int): Boolean = !BackupCryptoPolicy.isPasswordShapeValid(length)

    private fun fixedMessage(reason: RestoreFailure): String = when (reason) {
        RestoreFailure.UnsupportedRestoreVersion -> "该备份包版本不受支持，无法恢复。"
        RestoreFailure.SalvageNotRestorable -> "此包为应急保全包（Salvage），不支持认证恢复。"
        RestoreFailure.ManifestSchemaInvalid -> "备份包结构无效，已拒绝恢复。"
        RestoreFailure.PairingExpired -> "配对码已过期，请在旧设备重新发起配对。"
        RestoreFailure.PairingMismatch -> "配对码与备份包不匹配，已拒绝恢复。"
        RestoreFailure.PairingSignatureInvalid -> "备份包签名无效，已拒绝恢复。"
        RestoreFailure.PasswordRequired -> "该备份包受密码保护，请输入备份密码。"
        RestoreFailure.PasswordIncorrect -> "备份密码不正确。注意：密码遗忘则备份无法恢复。"
        RestoreFailure.DecryptionFailure -> "备份包解密失败，数据可能已被篡改。"
        RestoreFailure.IntegrityFailure -> "备份包完整性校验失败，已拒绝恢复。"
        RestoreFailure.ResourceRelationshipInvalid -> "备份包内的音频资源关系不完整，已拒绝恢复。"
        RestoreFailure.EntityReferenceInvalid -> "备份包内数据引用不完整，已拒绝恢复。"
        RestoreFailure.StorageInsufficient -> "设备存储空间不足，无法完成恢复。"
        RestoreFailure.RollbackVerification -> "恢复后的数据校验失败，已自动回滚到恢复前状态。"
    }

    private companion object {
        const val RESTORED_MESSAGE =
            "恢复完成：本机数据已替换为备份内容，搜索索引与提醒已重建，" +
                "所有 AI Profile 的 API Key 已按安全策略删除，需重新填写。"
    }
}
