package com.example.englishlearning.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class WorksheetPreviewRenderer(private val context: Context) {
    fun render(file: File, maxWidth: Int = 900): List<Bitmap> {
        require(file.isFile) { "PDF 文件不存在" }
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val width = maxWidth.coerceAtLeast(1)
                        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    }
}
