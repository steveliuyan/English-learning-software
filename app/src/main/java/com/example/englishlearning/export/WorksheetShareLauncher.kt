package com.example.englishlearning.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

class WorksheetShareLauncher(
    private val context: Context,
) {
    fun createChooser(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "$AUTHORITY_SUFFIX", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("worksheet", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "导出单词默写纸")
    }

    private companion object {
        const val AUTHORITY_SUFFIX = "com.example.englishlearning.worksheetfiles"
    }
}
