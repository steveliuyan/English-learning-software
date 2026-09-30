package com.example.englishlearning.wordbook

import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.WordCard
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * 合成词卡来源：按**册**把请求路由给「导入册」或「内置占位册」。
 *
 * 这是「导入的词书真的能学」的最后一环——没有它，导入成功也只会在
 * 「调整词书与目标」里出现一个名字，学习流程仍然只读代码内的 12 个占位词。
 *
 * 路由键必须是 `wordBookId` 而不是「先问 A 再问 B」：两册同 id 时（内置册与导入册重名）
 * 必须先问导入册，否则用户导入的新内容会被内置旧内容盖住。
 */
class CompositeWordCardSourceTest {

    private fun tempRoot(): File = Files.createTempDirectory("composite-root").toFile().apply { mkdirs() }

    private fun importedSource(root: File): ImportedWordBookSource {
        val packageDirectory = File(root, WordBookPackageFixture.BOOK_ID)
        WordBookPackageFixture.writePackage(
            packageDirectory,
            cards = listOf(WordBookPackageFixture.CardSpec("surf", 1204)),
        )
        return ImportedWordBookSource(root)
    }

    @Test
    fun importedBookWinsOverTheBundledOneWithTheSameId() = runTest {
        val root = tempRoot()
        val bundled = FakeSource(WordBookPackageFixture.BOOK_ID, listOf("old-placeholder-word"))
        val source = CompositeWordCardSource(importedSource(root), bundled)

        val ids = source.cardIds(WordBookPackageFixture.BOOK_ID)

        assertEquals(listOf("${WordBookPackageFixture.BOOK_ID}:surf"), ids)
    }

    @Test
    fun bundledBookIsUsedWhenNothingWasImported() = runTest {
        val root = tempRoot()
        val bundled = FakeSource("cet4", listOf("ability", "achieve"))
        val source = CompositeWordCardSource(importedSource(root), bundled)

        assertEquals(
            listOf("cet4:ability", "cet4:achieve"),
            source.cardIds("cet4"),
        )
    }

    @Test
    fun cardsAreResolvedFromWhicheverSourceOwnsThem() = runTest {
        val root = tempRoot()
        val bundled = FakeSource("cet4", listOf("ability"))
        val source = CompositeWordCardSource(importedSource(root), bundled)

        val cards = source.cards(
            listOf("${WordBookPackageFixture.BOOK_ID}:surf", "cet4:ability", "unknown:missing"),
        )

        assertEquals(listOf("surf", "ability"), cards.map(WordCard::lemma))
    }

    @Test
    fun emptyRequestTouchesNeitherSource() = runTest {
        val root = tempRoot()
        val bundled = FakeSource("cet4", listOf("ability"))
        val source = CompositeWordCardSource(importedSource(root), bundled)

        assertTrue(source.cards(emptyList()).isEmpty())
        assertTrue(source.cardIds("").isEmpty())
    }

    private class FakeSource(
        private val wordBookId: String,
        private val lemmas: List<String>,
    ) : WordCardSource {
        override suspend fun cardIds(wordBookId: String): List<String> =
            if (wordBookId == this.wordBookId) lemmas.map { "$wordBookId:$it" } else emptyList()

        override suspend fun cards(cardIds: List<String>): List<WordCard> =
            cardIds.filter { it.startsWith("$wordBookId:") }.map { id ->
                val lemma = id.substringAfter(':')
                WordCard(
                    cardId = id,
                    wordBookId = wordBookId,
                    lemma = lemma,
                    ipa = "ipa",
                    partOfSpeech = "n.",
                    meaningZh = "释义",
                )
            }
    }
}
