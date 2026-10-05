package com.example.englishlearning.wordbook

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.runTest

/**
 * 已导入词书包作为 [com.example.englishlearning.learning.WordCardSource] 的行为：
 * 顺序稳定（按频率排名）、坏包退化成空列表而不是崩学习页。
 */
class ImportedWordBookSourceTest {

    private fun tempDirectory(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    private fun rootWithPackage(cards: List<WordBookPackageFixture.CardSpec> = listOf(
        WordBookPackageFixture.CardSpec("possible", 303),
        // parent 显式给两词性：`cardsReturnsRequestedOnesWithImagePaths` 要验证「一词多词性多条释义」
        // 能被完整解析出来，用 CardSpec 的默认单条释义会让那条断言失去意义。
        WordBookPackageFixture.CardSpec(
            "parent", 305,
            senses = listOf("n." to "父母；母亲或父亲", "v." to "养育；做父母"),
        ),
        WordBookPackageFixture.CardSpec("often", 301),
    )): File {
        val root = tempDirectory("imported-root").apply { mkdirs() }
        val packageDirectory = File(root, WordBookPackageFixture.BOOK_ID)
        WordBookPackageFixture.writePackage(packageDirectory, cards)
        return root
    }

    @Test
    fun cardIdsComeBackOrderedByFrequencyRank() = runTest {
        val source = ImportedWordBookSource(rootWithPackage())

        val ids = source.cardIds(WordBookPackageFixture.BOOK_ID)

        assertEquals(
            listOf("often", "possible", "parent").map { "${WordBookPackageFixture.BOOK_ID}:$it" },
            ids,
        )
    }

    @Test
    fun cardsReturnsRequestedOnesWithImagePaths() = runTest {
        val source = ImportedWordBookSource(rootWithPackage())

        val cards = source.cards(listOf("${WordBookPackageFixture.BOOK_ID}:parent"))

        assertEquals(1, cards.size)
        assertEquals("parent", cards.single().lemma)
        assertTrue(File(cards.single().imagePath!!).isFile)
        assertEquals(listOf("n.", "v."), cards.single().senses.map { it.partOfSpeech })
    }

    @Test
    fun unknownBookOrIdsReturnEmptyInsteadOfFailing() = runTest {
        val source = ImportedWordBookSource(rootWithPackage())

        assertTrue(source.cardIds("not-installed").isEmpty())
        assertTrue(source.cards(listOf("not-installed:x")).isEmpty())
        assertTrue(source.cards(emptyList()).isEmpty())
    }

    @Test
    fun brokenPackageDegradesToEmptyRatherThanThrowing() = runTest {
        val root = rootWithPackage()
        File(root, "${WordBookPackageFixture.BOOK_ID}/images/parent.webp").writeBytes("broken".toByteArray())
        val source = ImportedWordBookSource(root)

        assertTrue(source.cardIds(WordBookPackageFixture.BOOK_ID).isEmpty())
    }

    /** 记下有没有被调度过：解析与算哈希必须切到它上面，而不是留在调用方（学习页是 Main）。 */
    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatched = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatched = true
            block.run()
        }
    }

    @Test
    fun readingIsMovedOntoTheIoDispatcher() = runTest {
        val forIds = RecordingDispatcher()
        ImportedWordBookSource(rootWithPackage(), forIds).cardIds(WordBookPackageFixture.BOOK_ID)
        assertTrue(forIds.dispatched, "cardIds 必须在 IO dispatcher 上解析")

        val forCards = RecordingDispatcher()
        ImportedWordBookSource(rootWithPackage(), forCards).cards(listOf("${WordBookPackageFixture.BOOK_ID}:parent"))
        assertTrue(forCards.dispatched, "cards 必须在 IO dispatcher 上解析")
    }

    @Test
    fun leftoverBackupDirectoryIsNotReadAsABook() = runTest {
        val root = rootWithPackage()
        // 备份删除失败时会留下同 bookId 的旧包；它不能再被当作一册来读
        WordBookPackageFixture.writePackage(
            File(root, ".backup-leftover"),
            cards = listOf(WordBookPackageFixture.CardSpec("stale", 1)),
        )
        val source = ImportedWordBookSource(root)

        assertTrue(source.cards(listOf("${WordBookPackageFixture.BOOK_ID}:stale")).isEmpty())
    }

    @Test
    fun onlyPublishedBookDirectoriesAreListed() {
        val root = rootWithPackage()
        File(root, ".backup-leftover").mkdirs()
        File(root, ".staging-leftover").mkdirs()
        File(root, "stray.txt").writeText("not a book")

        val listed = importedBookDirectories(root).map { it.name }

        assertEquals(listOf(WordBookPackageFixture.BOOK_ID), listed)
    }

    @Test
    fun isImportedDistinguishesInstalledBooks() = runTest {
        val root = rootWithPackage()
        val source = ImportedWordBookSource(root)

        assertTrue(source.isImported(WordBookPackageFixture.BOOK_ID))
        assertTrue(!source.isImported("cet4-planning"))
    }

    @Test
    fun isImportedDoesNotTreatWorkDirectoryAsInstalledBook() = runTest {
        val root = rootWithPackage()
        WordBookPackageFixture.writePackage(
            File(root, ".backup-${WordBookPackageFixture.BOOK_ID}"),
            cards = listOf(WordBookPackageFixture.CardSpec("stale", 1)),
        )
        val source = ImportedWordBookSource(root)

        assertTrue(!source.isImported(".backup-${WordBookPackageFixture.BOOK_ID}"))
    }
}
