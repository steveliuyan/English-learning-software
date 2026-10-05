package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.WordCard
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RefreshVocabularySearchIndexUseCaseTest {
    @Test
    fun `indexes a visible book that has no index yet`() = runTest {
        val index = RecordingIndex()
        val cards = content(mapOf("cet4" to listOf(card("cet4:ability", "ability", "cet4"))))
        val useCase = useCase(listOf(book("cet4", totalWords = 1, version = "v1")), cards, index)

        val result = useCase()

        assertEquals(1, (result as RepositoryResult.Success).value)
        assertEquals(listOf("cet4"), index.replaced.map { it.wordBookId })
        assertEquals("v1", index.replaced.single().dataVersion)
        assertEquals(listOf("cet4:ability"), index.replaced.single().cards.map { it.cardId })
        assertEquals(listOf(listOf("cet4")), index.prunedWith)
    }

    @Test
    fun `does not reread a book whose version is already indexed`() = runTest {
        val index = RecordingIndex(indexed = mapOf("cet4" to "v1"))
        val cards = content(mapOf("cet4" to listOf(card("cet4:ability", "ability", "cet4"))))
        val useCase = useCase(listOf(book("cet4", totalWords = 1, version = "v1")), cards, index)

        val result = useCase()

        assertEquals(0, (result as RepositoryResult.Success).value)
        assertEquals(emptyList(), index.replaced)
        // 版本没变就不该再解析词书包——这正是重复刷新几乎不花钱的原因。
        assertEquals(emptyList(), cards.cardIdsCalls)
    }

    @Test
    fun `reindexes when the word book version changes`() = runTest {
        val index = RecordingIndex(indexed = mapOf("cet4" to "v1"))
        val cards = content(mapOf("cet4" to listOf(card("cet4:ability", "ability", "cet4"))))
        val useCase = useCase(listOf(book("cet4", totalWords = 1, version = "v2")), cards, index)

        val result = useCase()

        assertEquals(1, (result as RepositoryResult.Success).value)
        assertEquals("v2", index.replaced.single().dataVersion)
    }

    @Test
    fun `placeholder only book is skipped rather than indexed as empty`() = runTest {
        val index = RecordingIndex()
        val cards = rawIds(mapOf("cet4" to listOf("placeholder:cet4:ability")))
        val useCase = useCase(listOf(book("cet4", totalWords = 1, version = "v1")), cards, index)

        val result = useCase()

        assertEquals(0, (result as RepositoryResult.Success).value)
        assertTrue(index.replaced.isEmpty(), "占位册内容还没交付，不能写成空索引，否则下次不会再重试")
    }

    @Test
    fun `book declaring words but returning none fails closed and keeps the old index`() = runTest {
        val index = RecordingIndex(indexed = mapOf("cet4" to "v0"))
        val cards = rawIds(mapOf("cet4" to emptyList()))
        val useCase = useCase(listOf(book("cet4", totalWords = 3039, version = "v1")), cards, index)

        val result = useCase()

        assertTrue(result is RepositoryResult.Failure)
        assertTrue(index.replaced.isEmpty(), "读不到内容时必须保留旧索引，而不是用空内容覆盖")
        assertTrue(index.prunedWith.isEmpty(), "失败路径不许顺手清理，避免把索引清空")
    }

    @Test
    fun `incomplete content fails closed and keeps the old index`() = runTest {
        val index = RecordingIndex(indexed = mapOf("cet4" to "v0"))
        val cards = contentWith(
            ids = mapOf("cet4" to listOf("cet4:ability", "cet4:absent")),
            byBook = mapOf("cet4" to listOf(card("cet4:ability", "ability", "cet4"))),
        )
        val useCase = useCase(listOf(book("cet4", totalWords = 2, version = "v1")), cards, index)

        val result = useCase()

        assertTrue(result is RepositoryResult.Failure)
        assertTrue(index.replaced.isEmpty())
    }

    @Test
    fun `index rows of deleted word books are pruned`() = runTest {
        val index = RecordingIndex(indexed = mapOf("cet4" to "v1", "deleted-book" to "v1"))
        val cards = content(mapOf("cet4" to listOf(card("cet4:ability", "ability", "cet4"))))
        val useCase = useCase(listOf(book("cet4", totalWords = 1, version = "v1")), cards, index)

        useCase()

        assertEquals(listOf(listOf("cet4")), index.prunedWith)
    }

    @Test
    fun `book that is neither bundled nor imported is not indexed`() = runTest {
        val index = RecordingIndex()
        val cards = content(mapOf("ghost" to listOf(card("ghost:ability", "ability", "ghost"))))
        val useCase = useCase(
            books = listOf(book("ghost", totalWords = 1, version = "v1")),
            cards = cards,
            index = index,
            bundled = emptySet(),
            imported = emptySet(),
        )

        val result = useCase()

        assertEquals(0, (result as RepositoryResult.Success).value)
        assertEquals(emptyList(), cards.cardIdsCalls)
        assertEquals(listOf(emptyList<String>()), index.prunedWith)
    }

    @Test
    fun `index write failure is propagated`() = runTest {
        val index = RecordingIndex(replaceFailure = true)
        val cards = content(mapOf("cet4" to listOf(card("cet4:ability", "ability", "cet4"))))
        val useCase = useCase(listOf(book("cet4", totalWords = 1, version = "v1")), cards, index)

        assertTrue(useCase() is RepositoryResult.Failure)
    }

    private fun useCase(
        books: List<WordBook>,
        cards: WordCardSource,
        index: VocabularySearchIndexRepository,
        bundled: Set<String> = books.map(WordBook::id).toSet(),
        imported: Set<String> = emptySet(),
    ) = RefreshVocabularySearchIndexUseCase(
        profiles = FakeProfiles(books),
        cards = cards,
        index = index,
        bundledIds = BundledWordBookIdSource { bundled },
        importedIds = ImportedWordBookIdSource { imported },
    )

    /** 词卡内容即事实来源：id 列表由卡片自身推导，避免同一份夹具写两遍而写歪。 */
    private fun content(byBook: Map<String, List<WordCard>>) =
        RecordingCards(idsByBook = byBook.mapValues { (_, cards) -> cards.map { it.cardId } }, cardsByBook = byBook)

    private fun rawIds(byBook: Map<String, List<String>>) = RecordingCards(idsByBook = byBook)

    private fun contentWith(ids: Map<String, List<String>>, byBook: Map<String, List<WordCard>>) =
        RecordingCards(idsByBook = ids, cardsByBook = byBook)

    private fun book(id: String, totalWords: Int, version: String) =
        WordBook(id, "词书-$id", "", totalWords, version, "bundled")

    private fun card(cardId: String, lemma: String, bookId: String) =
        WordCard(cardId, bookId, lemma, "", "n.", "释义")

    private class FakeProfiles(private val books: List<WordBook>) : LearningProfileRepository {
        override suspend fun current(profileId: String) = RepositoryResult.Success<LearningProfile?>(null)

        override suspend fun save(profile: LearningProfile) = RepositoryResult.Success(Unit)

        override suspend fun listWordBooks() = RepositoryResult.Success(books)

        override suspend fun findWordBook(id: String) = RepositoryResult.Success(books.find { it.id == id })

        override suspend fun upsertWordBook(wordBook: WordBook) = RepositoryResult.Success(Unit)
    }

    private class RecordingCards(
        private val idsByBook: Map<String, List<String>>,
        private val cardsByBook: Map<String, List<WordCard>> = emptyMap(),
    ) : WordCardSource {
        val cardIdsCalls = mutableListOf<String>()

        override suspend fun cardIds(wordBookId: String): List<String> {
            cardIdsCalls += wordBookId
            return idsByBook[wordBookId].orEmpty()
        }

        override suspend fun cards(cardIds: List<String>): List<WordCard> {
            val wanted = cardIds.toSet()
            return cardsByBook.values.flatten().filter { it.cardId in wanted }
        }
    }

    private class RecordingIndex(
        private val indexed: Map<String, String> = emptyMap(),
        private val replaceFailure: Boolean = false,
    ) : VocabularySearchIndexRepository {
        data class Replaced(
            val wordBookId: String,
            val wordBookName: String,
            val dataVersion: String,
            val cards: List<WordCard>,
        )

        val replaced = mutableListOf<Replaced>()
        val prunedWith = mutableListOf<List<String>>()

        override suspend fun replaceWordBook(
            wordBookId: String,
            wordBookName: String,
            dataVersion: String,
            cards: List<WordCard>,
        ): RepositoryResult<Unit> {
            if (replaceFailure) {
                return RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            }
            replaced += Replaced(wordBookId, wordBookName, dataVersion, cards)
            return RepositoryResult.Success(Unit)
        }

        override suspend fun deleteWordBook(wordBookId: String) = RepositoryResult.Success(Unit)

        override suspend fun search(normalizedQuery: String, limit: Int) =
            RepositoryResult.Success(emptyList<VocabularySearchIndexEntry>())

        override suspend fun indexedVersions() = RepositoryResult.Success(indexed)

        override suspend fun deleteMissing(keepWordBookIds: Collection<String>): RepositoryResult<Unit> {
            prunedWith += keepWordBookIds.toList()
            return RepositoryResult.Success(Unit)
        }
    }
}
