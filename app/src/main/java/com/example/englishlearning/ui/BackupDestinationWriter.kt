package com.example.englishlearning.ui

import android.net.Uri

/**
 * F4-03 导出目的地端口：把已在内存里生成的备份包字节写入用户经 SAF 选中的位置。
 * 返回 `false` 表示写入失败（由 ViewModel 映射为固定文案），实现不得回传原始异常或路径。
 */
interface BackupDestinationWriter {
    suspend fun write(destination: Uri, bytes: ByteArray): Boolean
}
