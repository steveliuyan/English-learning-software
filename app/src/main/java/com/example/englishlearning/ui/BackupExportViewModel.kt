package com.example.englishlearning.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.backup.domain.BackupExportCoordinator
import com.example.englishlearning.backup.domain.BackupExportFailure
import com.example.englishlearning.backup.domain.BackupExportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * F4-03 备份导出（无认证 Salvage 包）确认页状态。
 *
 * 两段式：先在内存生成受完整性校验的包并展示诚实的范围/数量/大小估算，再让用户经 SAF
 * 选保存位置；只有选中位置才落盘，取消不写任何文件。改动范围会让旧的待写包失效，避免写入
 * 与估算不一致的陈旧字节。错误只展示固定类别，绝不回传实体内容、路径或密钥。
 */
data class BackupExportUiState(
    val disclosure: String = DEFAULT_DISCLOSURE,
    val includeAudio: Boolean = true,
    val busy: Boolean = false,
    val packageReady: Boolean = false,
    val summary: String = "",
    val fileName: String? = null,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class BackupExportViewModel @Inject constructor(
    private val coordinator: BackupExportCoordinator,
    private val destinationWriter: BackupDestinationWriter,
) : ViewModel() {

    private val mutable = MutableStateFlow(BackupExportUiState())
    val state: StateFlow<BackupExportUiState> = mutable.asStateFlow()

    // 待写字节只在内存持有；范围变化或成功/取消后置空，保证不会写入陈旧包。
    private var pending: ByteArray? = null

    fun setIncludeAudio(include: Boolean) {
        pending = null
        mutable.value = mutable.value.copy(
            includeAudio = include,
            packageReady = false,
            summary = "",
            fileName = null,
            error = null,
            message = null,
        )
    }

    fun prepare() {
        val includeAudio = mutable.value.includeAudio
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            when (val result = coordinator.prepare(includeAudio)) {
                is BackupExportResult.Prepared -> {
                    pending = result.bytes
                    mutable.value = mutable.value.copy(
                        busy = false,
                        packageReady = true,
                        summary = formatSummary(result.recordCount, result.mediaCount, result.uncompressedBytes),
                        fileName = suggestedFileName(result.createdAtEpochMillis),
                        error = null,
                        message = null,
                    )
                }
                is BackupExportResult.Failed -> {
                    pending = null
                    mutable.value = mutable.value.copy(
                        busy = false,
                        packageReady = false,
                        summary = "",
                        fileName = null,
                        error = fixedMessage(result.reason),
                    )
                }
            }
        }
    }

    fun onDocumentPicked(destination: Uri?) {
        if (destination == null) {
            // 取消：不写任何文件，清掉内存中的待写包。
            pending = null
            mutable.value = mutable.value.copy(
                packageReady = false,
                summary = "",
                fileName = null,
                message = "已取消导出，未写入任何文件。",
            )
            return
        }
        val bytes = pending ?: return // 范围已改动或尚未生成：拒绝写入陈旧/缺失的包。
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            if (destinationWriter.write(destination, bytes)) {
                pending = null
                mutable.value = mutable.value.copy(
                    busy = false,
                    packageReady = false,
                    summary = "",
                    fileName = null,
                    message = "已导出备份包，请妥善保存。",
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

    private fun fixedMessage(reason: BackupExportFailure): String = when (reason) {
        BackupExportFailure.ResourceUnavailable -> "部分 AI 音频文件缺失，已停止导出。"
        BackupExportFailure.IntegrityFailure -> "AI 音频完整性校验失败，已停止导出。"
        BackupExportFailure.LimitExceeded -> "数据超出备份包大小限制，已停止导出。"
        BackupExportFailure.InvalidSnapshot,
        BackupExportFailure.ArchiveRejected,
        BackupExportFailure.DestinationFailure,
        BackupExportFailure.Cancelled,
        BackupExportFailure.PasswordRejected,
        BackupExportFailure.CryptoUnavailable,
        -> "备份包生成失败，请重试或联系支持。"
    }
}

/**
 * 面向用户的诚实声明：这是**数据留档包**（只能留档、不能换机恢复），并指向真实可用的认证导出出口；
 * 不含密钥。固定文案，供 UI 与测试共用。
 */
const val DEFAULT_DISCLOSURE =
    "本备份为数据留档包：只能用于留档，不能用于换机恢复；换机恢复请在「备份与恢复」中选择「认证导出」生成受保护备份。" +
        "包中不包含 API Key、Keystore 密钥、系统 TTS 数据或临时图片。" +
        "卸载、清除数据或设备损坏前未导出的学习记录可能无法恢复；无账户、无云同步，请自行妥善保存备份文件。"

private fun formatSummary(recordCount: Int, mediaCount: Int, uncompressedBytes: Long): String =
    "共 $recordCount 条记录 · $mediaCount 个音频文件 · 约 ${uncompressedBytes / 1024} KB（未压缩）"

/** 确定性文件名：UTC 时间戳 + `.elpbak` 扩展名，同一时刻输入必得同一名称。 */
fun suggestedFileName(epochMillis: Long): String {
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(epochMillis))
    return "englishlearning-backup-$stamp.elpbak"
}
