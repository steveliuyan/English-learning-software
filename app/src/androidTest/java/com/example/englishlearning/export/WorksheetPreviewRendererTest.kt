package com.example.englishlearning.export

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.learning.worksheet.WorksheetAnswer
import com.example.englishlearning.learning.worksheet.WorksheetPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WorksheetPreviewRendererTest {
    @Test
    fun rendersPdfPagesAsBoundedBitmaps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rendered = renderPdf(context)
        val bitmaps = WorksheetPreviewRenderer(context).render(rendered.file, maxWidth = 320)

        assertEquals(2, bitmaps.size)
        assertTrue(bitmaps.all { it.width == 320 && it.height > 0 })
        assertEquals(Bitmap.Config.ARGB_8888, bitmaps.first().config)
        bitmaps.forEach(Bitmap::recycle)
        rendered.file.delete()
    }

    private fun renderPdf(context: Context): RenderedWorksheet {
        val renderer = WorksheetPdfRenderer(context)
        return renderer.render(
            pages = listOf(
                WorksheetPage(pageNumber = 1, pageCount = 2, direction = null, answers = listOf(WorksheetAnswer(1, "apple", "æpl", "n.", "苹果"))),
                WorksheetPage(pageNumber = 2, pageCount = 2, direction = null, answers = listOf(WorksheetAnswer(2, "book", "bʊk", "n.", "书"))),
            ),
            useFourLineGrid = true,
        ).getOrThrow()
    }
}
