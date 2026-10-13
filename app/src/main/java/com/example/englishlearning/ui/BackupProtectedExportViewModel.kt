package com.example.englishlearning.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.backup.device.AndroidMigrationSigner
import com.example.englishlearning.backup.domain.BackupCryptoPolicy
import com.example.englishlearning.backup.domain.BackupExportFailure
import com.example.englishlearning.backup.domain.BackupExportResult
import com.example.englishlearning.backup.domain.ProtectedPackageInputs
import com.example.englishlearning.backup.domain.ProtectedPackagingSpec
import com.example.englishlearning.backup.infrastructure.BouncyCastleArgon2idKdf
import com.example.englishlearning.backup.infrastructure.SecureRandomBackupEntropy
import com.example.englishlearning.backup.infrastructure.TinkAes256GcmAead
import com.example.englishlearning.backup.pairing.MigrationChallenge
import com.example.englishlearning.backup.pairing.MigrationCodeCodec
import com.example.englishlearning.backup.pairing.MigrationKeyOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * F4-04 旧设备受保护导出状态：认证门控（BiometricPrompt 由 Screen 调起，结果回填）→
 * 生成一次性挑战（MigrationChallengeIssuer 语义：单活、取代、台账不落 S）→ 显示二维码 →
 * 可选备份密码 → 受保护导出（签名 + Argon2id/AES-GCM）。密码错误固定类别；遗忘密码
 * 不可恢复必须在文案中如实说明。
 *
 * 落盘两段式（与 F4-03 同语义）：导出成功只在内存持有待写包并给出确定性文件名，
 * 用户经 SAF 选中保存位置才真正写入；取消零写入；写入失败保留待写包允许换位置重试。
 */
data class BackupProtectedExportUiState(
    val phase: ProtectedExportPhase = ProtectedExportPhase.Idle,
    val busy: Boolean = false,
    val qrPayload: String? = null,
    val issuedAtEpochSeconds: Long = 0L,
    val expiresAtEpochSeconds: Long = 0L,
    val usePassword: Boolean = false,
    val packageReady: Boolean = false,
    val fileName: String? = null,
    val error: String? = null,
    val message: String? = null,
)

enum class ProtectedExportPhase { Idle, Authenticating, Active, Exporting, Done }

@HiltViewModel
class BackupProtectedExportViewModel @Inject constructor(
    private val exporter: ProtectedExportExecutor,
    private val destinationWriter: BackupDestinationWriter,
) : ViewModel() {

    private val mutable = MutableStateFlow(BackupProtectedExportUiState())
    val state: StateFlow<BackupProtectedExportUiState> = mutable.asStateFlow()

    private var challenge: MigrationChallenge? = null

    // 待写字节只在内存持有；新一轮导出/成功/取消后置空，保证不会写入陈旧包。
    private var pending: ByteArray? = null

    /** 用户已通过系统认证（BiometricPrompt 成功回调）后调用。 */
    fun onAuthenticated() {
        // 新挑战取代旧会话：上一轮待写包绑定旧挑战，必须同步作废。
        pending = null
        mutable.value = mutable.value.copy(
            phase = ProtectedExportPhase.Active,
            error = null,
            packageReady = false,
            fileName = null,
        )
        viewModelScope.launch {
            when (val outcome = exporter.issueChallenge()) {
                is ProtectedExportExecutor.ChallengeOutcome.Issued -> {
                    challenge = outcome.challenge
                    mutable.value = mutable.value.copy(
                        qrPayload = MigrationCodeCodec.encode(outcome.challenge),
                        issuedAtEpochSeconds = outcome.challenge.issuedAtEpochSeconds,
                        expiresAtEpochSeconds = outcome.challenge.expiresAtEpochSeconds,
                    )
                }
                ProtectedExportExecutor.ChallengeOutcome.KeyUnavailable -> mutable.value = mutable.value.copy(
                    error = "无法创建迁移签名密钥（设备未设置安全锁屏或 Keystore 不可用）。请先在系统设置中启用锁屏。",
                )
            }
        }
    }

    /** 认证失败/取消：不签发任何挑战。 */
    fun onAuthenticationRejected() {
        mutable.value = mutable.value.copy(phase = ProtectedExportPhase.Idle, error = null)
    }

    fun setUsePassword(use: Boolean) {
        mutable.value = mutable.value.copy(usePassword = use)
    }

    /** 挑战有效期内发起受保护导出；超期包不可恢复（决策 D3），必须重新配对。 */
    fun exportProtected(includeMediaAssets: Boolean, password: CharArray?) {
        val active = challenge
        if (active == null) {
            mutable.value = mutable.value.copy(error = "请先完成配对认证。")
            return
        }
        if (usePassword() && (password == null || !BackupCryptoPolicy.isPasswordShapeValid(password.size))) {
            password?.fill('\u0000')
            mutable.value = mutable.value.copy(error = "密码需为 10–128 个字符。注意：密码遗忘则备份无法恢复。")
            return
        }
        pending = null
        mutable.value = mutable.value.copy(
            busy = true,
            error = null,
            message = null,
            phase = ProtectedExportPhase.Exporting,
            packageReady = false,
            fileName = null,
        )
        viewModelScope.launch {
            when (val result = exporter.export(includeMediaAssets, active, if (usePassword()) password else null)) {
                is BackupExportResult.Prepared -> {
                    pending = result.bytes
                    mutable.value = mutable.value.copy(
                        busy = false,
                        phase = ProtectedExportPhase.Done,
                        packageReady = true,
                        fileName = suggestedFileName(result.createdAtEpochMillis),
                        message = "受保护备份包已生成：共 ${result.recordCount} 条记录 · ${result.mediaCount} 个音频文件。" +
                            "请在 30 分钟配对有效期内于新设备完成恢复；超期须重新配对导出。",
                    )
                }
                is BackupExportResult.Failed -> mutable.value = mutable.value.copy(
                    busy = false,
                    phase = ProtectedExportPhase.Active,
                    error = fixedMessage(result.reason),
                )
            }
        }
    }

    /** SAF 位置回填：选中才落盘待写包；取消零写入并丢弃内存包；失败保留待写包允许重试。 */
    fun onDocumentPicked(destination: Uri?) {
        if (destination == null) {
            pending = null
            mutable.value = mutable.value.copy(
                packageReady = false,
                fileName = null,
                message = "已取消导出，未写入任何文件。",
            )
            return
        }
        val bytes = pending ?: return // 尚未生成或已陈旧：拒绝写入缺失/过期的包。
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            if (destinationWriter.write(destination, bytes)) {
                pending = null
                mutable.value = mutable.value.copy(
                    busy = false,
                    packageReady = false,
                    fileName = null,
                    message = "已导出受保护备份包，请在新设备 30 分钟配对有效期内完成恢复。",
                    error = null,
                )
            } else {
                mutable.value = mutable.value.copy(
                    busy = false,
                    error = "写入目标失败，请换一个位置重试。",
                )
            }
        }
    }

    fun passwordShapeValid(length: Int): Boolean = BackupCryptoPolicy.isPasswordShapeValid(length)

    private fun usePassword(): Boolean = mutable.value.usePassword

    private fun fixedMessage(reason: BackupExportFailure): String = when (reason) {
        BackupExportFailure.PasswordRejected -> "密码不符合要求，已停止导出。"
        BackupExportFailure.CryptoUnavailable -> "加密组件不可用，已停止导出。"
        BackupExportFailure.ResourceUnavailable -> "部分 AI 音频文件缺失，已停止导出。"
        BackupExportFailure.IntegrityFailure -> "数据完整性校验失败，已停止导出。"
        else -> "受保护备份包生成失败，请重试或联系支持。"
    }
}

/** 域层导出编排的薄包装，方便测试注入。 */
class ProtectedExportExecutor @Inject constructor(
    private val signerFactory: () -> com.example.englishlearning.backup.domain.MigrationSigner,
    private val keySpki: () -> ByteArray?,
    private val delegate: com.example.englishlearning.backup.domain.BackupExportCoordinator,
    private val entropy: com.example.englishlearning.backup.domain.BackupSecretEntropy,
    private val clock: () -> Long,
) {
    sealed interface ChallengeOutcome {
        data class Issued(val challenge: MigrationChallenge) : ChallengeOutcome
        data object KeyUnavailable : ChallengeOutcome
    }

    fun issueChallenge(): ChallengeOutcome {
        val spki = keySpki() ?: return ChallengeOutcome.KeyUnavailable
        val nonce = entropy.nextBytes(com.example.englishlearning.backup.pairing.MigrationChallengePolicy.NONCE_BYTES)
        val secret = entropy.nextBytes(com.example.englishlearning.backup.pairing.MigrationChallengePolicy.SECRET_BYTES)
        val issuedAt = clock()
        return ChallengeOutcome.Issued(
            MigrationChallenge(
                publicKeySpki = spki,
                nonce = nonce,
                secret = secret,
                issuedAtEpochSeconds = issuedAt,
                expiresAtEpochSeconds = issuedAt + com.example.englishlearning.backup.pairing.MigrationChallengePolicy.CHALLENGE_TTL_SECONDS,
            ),
        )
    }

    suspend fun export(
        includeMediaAssets: Boolean,
        challenge: MigrationChallenge,
        password: CharArray?,
    ): BackupExportResult {
        val inputs = ProtectedPackageInputs(
            publicKeySpki = challenge.publicKeySpki,
            nonce = challenge.nonce,
            secret = challenge.secret,
            issuedAtEpochSeconds = challenge.issuedAtEpochSeconds,
            expiresAtEpochSeconds = challenge.expiresAtEpochSeconds,
        )
        val spec = ProtectedPackagingSpec(
            inputs = inputs,
            signer = signerFactory(),
            passwordChars = password?.copyOf(),
            kdf = if (password != null) BouncyCastleArgon2idKdf() else null,
            aead = if (password != null) TinkAes256GcmAead() else null,
            entropy = SecureRandomBackupEntropy(),
        )
        return delegate.prepareProtected(
            includeMediaAssets = includeMediaAssets,
            packager = com.example.englishlearning.backup.infrastructure.ZipProtectedPackagePackager(),
            protection = spec,
        )
    }
}
