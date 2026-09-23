package com.example.englishlearning.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把私有目录里的 PDF 交给系统分享/打开。
 *
 * 关键点：
 * - authority 用运行时包名推导（等价于 Manifest 里的 `${applicationId}.worksheetfiles`），
 *   避免换 applicationIdSuffix 后 `getUriForFile` 找不到 provider。
 * - 分享面板除了 `ACTION_SEND`（微信/邮件/打印等），还通过 `EXTRA_INITIAL_INTENTS` 提供
 *   `ACTION_VIEW`，让用户能直接用 PDF 阅读器打开导出的文件。
 * - URI 只授予临时只读权限，且同时在 chooser 上再声明一次，避免部分 ROM 的
 *   chooser 不透传内层 Intent 的权限标志。
 */
class WorksheetShareLauncher(
    private val context: Context,
) {
    fun createChooser(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "$PACKAGE_NAME_SUFFIX", file)
        val clip = ClipData.newRawUri("worksheet", uri)

        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, PDF_MIME_TYPE)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "导出单词默写纸").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(view))
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private val PACKAGE_NAME_SUFFIX get() = "${context.packageName}.worksheetfiles"

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }
}
