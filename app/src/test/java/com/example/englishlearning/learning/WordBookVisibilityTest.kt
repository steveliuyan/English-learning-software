package com.example.englishlearning.learning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordBookVisibilityTest {
    private fun book(id: String, name: String = id) = WordBook(id, name, "基础", 10, "v1", "ngsl-nawl-1.2")

    @Test
    fun hidesLegacyPlaceholderBooksThatAreNeitherBundledNorImported() {
        val all = listOf(book("cet4"), book("postgraduate-entrance-exam"), book("my-import"))

        val visible = WordBookVisibility.visible(
            all = all,
            bundledIds = setOf("cet4", "cet6"),
            importedIds = setOf("my-import"),
        )

        assertEquals(listOf("cet4", "my-import"), visible.map(WordBook::id))
    }

    @Test
    fun keepsBookOrderOfTheSourceList() {
        val all = listOf(book("toefl"), book("cet4"), book("ielts"))

        val visible = WordBookVisibility.visible(all, setOf("cet4", "ielts", "toefl"), emptySet())

        assertEquals(listOf("toefl", "cet4", "ielts"), visible.map(WordBook::id))
    }

    @Test
    fun importDirectoryAloneIsNotEnoughWithoutDatabaseRow() {
        // 导入目录存在、但数据库里没有元数据行时不应显示——列表以数据库行为准，
        // 目录只是「是否可删」的判据之一。
        val visible = WordBookVisibility.visible(emptyList(), setOf("cet4"), setOf("ghost-dir"))

        assertTrue(visible.isEmpty())
    }

    @Test
    fun onlyImportedBooksAreDeletable() {
        val bundled = setOf("cet4")
        val imported = setOf("my-import")

        assertTrue(WordBookVisibility.deletable("my-import", bundled, imported))
        assertFalse(WordBookVisibility.deletable("cet4", bundled, imported))
        assertFalse(WordBookVisibility.deletable("unknown", bundled, imported))
    }

    @Test
    fun parsesBundledIdsFromMetadataJson() {
        val json = """
            [
              {"id":"cet4","displayName":"四级词汇","level":"CET-4","totalWords":4308,
               "dataVersion":"v1","sourceId":"ngsl-nawl-1.2","sourcePolicy":"x"},
              {"id":"toefl","displayName":"托福词汇","level":"TOEFL","totalWords":7438,
               "dataVersion":"v1","sourceId":"ngsl-nawl-1.2","sourcePolicy":"x"}
            ]
        """.trimIndent()

        assertEquals(setOf("cet4", "toefl"), bundledWordBookIds(json))
    }

    @Test
    fun skipsEntriesWithoutIdAndSurvivesBrokenJson() {
        assertEquals(
            setOf("cet4"),
            bundledWordBookIds("""[{"id":"cet4"},{"displayName":"缺 id"}]"""),
        )
        assertEquals(emptySet(), bundledWordBookIds("not json"))
    }
}
