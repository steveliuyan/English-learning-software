package com.example.englishlearning.export

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.worksheet.WorksheetAnswer
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetPage
import com.example.englishlearning.learning.worksheet.WorksheetQuestionRow
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorksheetPdfRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun renderedPdfHasSamePageCountAsWorksheetPages() {
        val pages = listOf(
            WorksheetPage(
                pageNumber = 1,
                pageCount = 2,
                direction = WorksheetDirection.ZH_TO_EN,
                template = WorksheetTemplate.SPELLING_TEST,
                questionRows = listOf(row(1, "ability", "能力；才能")),
            ),
            WorksheetPage(
                pageNumber = 2,
                pageCount = 2,
                direction = WorksheetDirection.EN_TO_ZH,
                template = WorksheetTemplate.SPELLING_TEST,
                questionRows = listOf(row(1, "ability", "能力；才能")),
            ),
        )

        val rendered = WorksheetPdfRenderer(context).render(pages, useFourLineGrid = true).getOrThrow()

        assertTrue(rendered.file.exists())
        ParcelFileDescriptor.open(rendered.file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { pdf -> assertEquals(pages.size, pdf.pageCount) }
        }
    }

    @Test
    fun everyTemplateRendersAReadablePage() {
        val questions = (1..24).map { row(it, "word$it", "释义$it") }
        val renderer = WorksheetPdfRenderer(context)

        WorksheetTemplate.values().forEach { template ->
            val rendered = renderer.render(
                listOf(
                    WorksheetPage(
                        pageNumber = 1,
                        pageCount = 1,
                        direction = null,
                        template = template,
                        questionRows = questions,
                    ),
                ),
                useFourLineGrid = true,
            ).getOrThrow()

            assertTrue(template.name, rendered.file.length() > 0L)
            ParcelFileDescriptor.open(rendered.file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf -> assertEquals(1, pdf.pageCount) }
            }
            rendered.file.delete()
        }
    }

    @Test
    fun answerPageRendersEvenWithoutQuestionRows() {
        val rendered = WorksheetPdfRenderer(context).render(
            listOf(
                WorksheetPage(
                    pageNumber = 1,
                    pageCount = 1,
                    direction = null,
                    template = WorksheetTemplate.SPELLING_TEST,
                    answers = listOf(WorksheetAnswer(1, "ability", "əˈbɪləti", "n.", "能力；才能")),
                ),
            ),
            useFourLineGrid = false,
        ).getOrThrow()

        assertTrue(rendered.file.exists())
        rendered.file.delete()
    }

    private fun row(number: Int, lemma: String, meaning: String) = WorksheetQuestionRow(
        number = number,
        lemma = lemma,
        ipa = "/$lemma/",
        partOfSpeech = "n.",
        meaningZh = meaning,
        pageNumber = 1,
    )
}
