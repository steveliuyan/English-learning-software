package com.example.englishlearning.learning.worksheet

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WorksheetDocumentBuilderTest {
    @Test
    fun `both directions keep corresponding word numbers`() {
        val items = (1..3).map { index ->
            WorksheetItem("id$index", "word$index", "/w/", "n.", "释义$index", null)
        }
        val source = WorksheetSource(LocalDate.of(2026, 9, 23), "book", items, 0)
        val document = WorksheetDocumentBuilder().build(
            source,
            WorksheetSettings(directions = setOf(WorksheetDirection.ZH_TO_EN, WorksheetDirection.EN_TO_ZH)),
        )

        assertEquals(listOf(1, 2, 3), document.sections[0].questions.map { it.number })
        assertEquals(listOf(1, 2, 3), document.sections[1].questions.map { it.number })
        assertEquals(listOf("word1", "word2", "word3"), document.sections[0].questions.map { it.item.lemma })
        assertEquals(listOf("word1", "word2", "word3"), document.sections[1].questions.map { it.item.lemma })
    }
}
