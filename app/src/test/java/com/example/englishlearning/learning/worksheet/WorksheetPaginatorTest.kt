package com.example.englishlearning.learning.worksheet

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WorksheetPaginatorTest {
    private val source = WorksheetSource(
        localDate = LocalDate.of(2026, 9, 23),
        wordBookId = "cet4",
        items = listOf(item("ability", "能力；才能"), item("achieve", "实现；达到")),
        missingCardCount = 0,
    )

    @Test
    fun `both directions render Chinese to English before English to Chinese`() {
        val pages = WorksheetPaginator().paginate(WorksheetDocumentBuilder().build(source, WorksheetSettings(directions = setOf(WorksheetDirection.ZH_TO_EN, WorksheetDirection.EN_TO_ZH))))
        assertEquals(WorksheetDirection.ZH_TO_EN, pages.first().direction)
        assertEquals(WorksheetDirection.EN_TO_ZH, pages.first { it.direction == WorksheetDirection.EN_TO_ZH }.direction)
    }

    @Test
    fun `answer page preserves question number and word order`() {
        val pages = WorksheetPaginator().paginate(WorksheetDocumentBuilder().build(source, WorksheetSettings(includeAnswerPage = true)))
        assertEquals(listOf(1, 2), pages.last().answers.map(WorksheetAnswer::number))
        assertEquals(listOf("ability", "achieve"), pages.last().answers.map(WorksheetAnswer::lemma))
    }

    @Test
    fun `answer pages split large lists`() {
        val manyItems = source.copy(items = (1..41).map { item("word$it", "释义$it") })
        val pages = WorksheetPaginator().paginate(WorksheetDocumentBuilder().build(manyItems, WorksheetSettings(includeAnswerPage = true)))
        val answerPages = pages.filter { it.answers.isNotEmpty() }
        assertEquals(listOf(20, 20, 1), answerPages.map { it.answers.size })
        assertEquals(listOf(1, 21, 41), answerPages.map { it.answers.first().number })
    }

    @Test
    fun `spelling template keeps twenty rows per page and starts each direction on its own page`() {
        val manyItems = source.copy(items = (1..25).map { item("word$it", "释义$it") })
        val pages = WorksheetPaginator().paginate(
            WorksheetDocumentBuilder().build(
                manyItems,
                WorksheetSettings(
                    directions = setOf(WorksheetDirection.ZH_TO_EN, WorksheetDirection.EN_TO_ZH),
                    includeAnswerPage = false,
                ),
            ),
        )

        assertEquals(listOf(20, 5, 20, 5), pages.map { it.questionRows.size })
        assertEquals(
            listOf(
                WorksheetDirection.ZH_TO_EN,
                WorksheetDirection.ZH_TO_EN,
                WorksheetDirection.EN_TO_ZH,
                WorksheetDirection.EN_TO_ZH,
            ),
            pages.map { it.direction },
        )
    }

    @Test
    fun `non directional templates emit a single section`() {
        val pages = WorksheetPaginator().paginate(
            WorksheetDocumentBuilder().build(
                source,
                WorksheetSettings(
                    template = WorksheetTemplate.FULL_LIST,
                    directions = setOf(WorksheetDirection.ZH_TO_EN, WorksheetDirection.EN_TO_ZH),
                    includeAnswerPage = false,
                ),
            ),
        )

        assertEquals(listOf(2), pages.map { it.questionRows.size })
        assertEquals(listOf(null), pages.map { it.direction })
    }

    private fun item(lemma: String, meaning: String) = WorksheetItem(lemma, lemma, "/$lemma/", "n.", meaning, null)
}
