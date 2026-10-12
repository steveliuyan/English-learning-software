package com.example.englishlearning.backup.infrastructure

import android.content.Context
import android.net.Uri
import com.example.englishlearning.ui.BackupDestinationWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * F4-03 导出目的地实现：把内存中的备份包字节写入用户经 SAF `CreateDocument` 选中的位置。
 *
 * 只走 SAF URI 输出流，不落任何临时文件、不申请存储权限；任一步失败都返回 `false`，
 * 由 ViewModel 映射为固定文案，绝不回传原始异常、URI 或路径。写操作在 IO 线程执行。
 */
class ContentResolverBackupDestinationWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupDestinationWriter {
    override suspend fun write(destination: Uri, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                    output.write(bytes)
                    output.flush()
                } != null
            } catch (_: Exception) {
                false
            }
        }
}
