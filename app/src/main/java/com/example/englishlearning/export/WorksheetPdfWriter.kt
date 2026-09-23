package com.example.englishlearning.export

import com.example.englishlearning.learning.worksheet.WorksheetPage
import java.io.File

data class RenderedWorksheet(
    val file: File,
    val pageCount: Int,
)

/**
 * 把分页结果写成 PDF 的能力。抽成端口是为了让 [com.example.englishlearning.ui.WorksheetViewModel]
 * 的状态机可以在 JVM 上被测试，不依赖 `android.graphics.pdf.PdfDocument`。
 *
 * 由实现决定在哪个线程上真正写文件（实现内部切到 IO），调用方只负责在自己的作用域里等待，
 * 这样测试用假实现就能被测试调度器确定性地驱动。
 */
interface WorksheetPdfWriter {
    suspend fun write(pages: List<WorksheetPage>, useFourLineGrid: Boolean): Result<RenderedWorksheet>
}
