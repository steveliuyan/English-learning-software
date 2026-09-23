package com.example.englishlearning.learning.worksheet

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorksheetTemplateTest {
    private val items = (1..45).map { index ->
        WorksheetItem("id$index", "word$index", "/w$index/", "n.", "释义$index", null)
    }
    private val source = WorksheetSource(LocalDate.of(2026, 9, 23), "cet4", items, 0)

    @Test
    fun `full list template fits forty words per page in two columns`() {
        val pages = WorksheetPaginator().paginate(
            WorksheetDocumentBuilder().build(
                source,
                WorksheetSettings(template = WorksheetTemplate.FULL_LIST, includeAnswerPage = false),
            ),
        )

        assertEquals(listOf(40, 5), pages.map { it.questionRows.size })
        assertTrue(pages.all { it.template == WorksheetTemplate.FULL_LIST })
    }

    @Test
    fun `spelling test template fits twenty words per page`() {
        val pages = WorksheetPaginator().paginate(
            WorksheetDocumentBuilder().build(
                source,
                WorksheetSettings(template = WorksheetTemplate.SPELLING_TEST, includeAnswerPage = false),
            ),
        )

        assertEquals(listOf(20, 20, 5), pages.map { it.questionRows.size })
    }

    @Test
    fun `ebbinghaus template fits twenty words per page`() {
        val pages = WorksheetPaginator().paginate(
            WorksheetDocumentBuilder().build(
                source,
                WorksheetSettings(template = WorksheetTemplate.EBBINGHAUS_REVIEW, includeAnswerPage = false),
            ),
        )

        assertEquals(listOf(20, 20, 5), pages.map { it.questionRows.size })
    }

    @Test
    fun `rows carry the printable columns of the reference template`() {
        val pages = WorksheetPaginator().paginate(
            WorksheetDocumentBuilder().build(
                source,
                WorksheetSettings(template = WorksheetTemplate.FULL_LIST, includeAnswerPage = false),
            ),
        )

        val row = pages.first().questionRows.first()
        assertEquals(1, row.number)
        assertEquals("word1", row.lemma)
        assertEquals("/w1/", row.ipa)
        assertEquals("n.", row.partOfSpeech)
        assertEquals("释义1", row.meaningZh)
    }
}
