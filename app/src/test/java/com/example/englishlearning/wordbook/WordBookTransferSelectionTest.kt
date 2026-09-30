package com.example.englishlearning.wordbook

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class WordBookTransferSelectionTest {
    @Test
    fun `selected export book must belong to refreshed books`() {
        val books = listOf("book-a", "book-b")

        assertEquals("book-b", selectExportBook(books, "book-b"))
        assertNull(selectExportBook(books, "missing"))
    }

    @Test
    fun `single book may be selected without implicit first book in caller`() {
        assertEquals("only", selectExportBook(listOf("only"), "only"))
        assertNull(selectExportBook(emptyList(), "only"))
    }
}
