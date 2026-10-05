package com.example.englishlearning.learning

import com.example.englishlearning.core.time.FixedClockProvider
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SearchVocabularyUseCaseTest {
    private val now = Instant.parse("2026-10-04T09:00:00Z")

    @Test
    fun `searches the local index and never reads word books`() = runTest {
        val history = FakeHistoryRepository()
        val index = RecordingIndex(
            listOf(
                entry("other:ability", "ability", bookName = "其他词书"),
                entry("active:alpha", "alpha", bookName = "当前词书"),
            ),
        )
        val useCase = useCase(history = history, index = index)

        val result = useCase.search("profile", "ability")

        assertEquals(
            listOf("other:ability"),
            (result as RepositoryResult.Success<List<SearchVocabularyResult>>).value.map { it.cardId },
        )
        assertEquals("other:ability", history.recorded.single().representative?.cardId)
        assertEquals("ability", index.lastQuery)
    }

    /**
     * 结构性地锁死「查询不读词书」：用例的构造器里**不允许**出现 `WordCardSource`。
     *
     * 用反射而不是运行时计数，是因为计数只能证明「这次没调用」；一旦有人把依赖加回去
     * （哪怕当前分支没走到），计数断言照样绿。依赖本身不存在，才是真正的保证。
     */
    @Test
    fun `use case has no word book source dependency at all`() {
        val dependencyTypes = SearchVocabularyUseCase::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.name }

        assertTrue(
            dependencyTypes.none { it == WordCardSource::class.java.name },
            "查询用例不得依赖 WordCardSource，否则索引化会被悄悄退回全量扫描：$dependencyTypes",
        )
        assertTrue(
            dependencyTypes.any { it == VocabularySearchIndexRepository::class.java.name },
            "索引仓储是查询路径的唯一数据来源，必须是构造器依赖：$dependencyTypes",
        )
    }

    @Test
    fun `normalizes query before lookup and records the original input`() = runTest {
        val history = FakeHistoryRepository()
        val index = RecordingIndex(listOf(entry("book:ability", "ability", bookName = "词书")))
        val useCase = useCase(history = history, index = index)

        useCase.search("profile", "  ABILity  ")

        assertEquals("ability", index.lastQuery)
        assertEquals("  ABILity  ", history.recorded.single().query)
        assertEquals("profile", history.recorded.single().profileId)
    }

    @Test
    fun `empty query does not query the index and does not record history`() = runTest {
        val history = FakeHistoryRepository()
        val index = RecordingIndex(emptyList())
        val useCase = useCase(history = history, index = index)

        val result = useCase.search("profile", "   ")

        assertEquals(emptyList(), (result as RepositoryResult.Success<List<SearchVocabularyResult>>).value)
        assertEquals(0, index.searchCalls)
        assertTrue(history.recorded.isEmpty())
    }

    @Test
    fun `index failure is propagated instead of reporting an empty result`() = runTest {
        val index = RecordingIndex(emptyList(), failure = true)
        val useCase = useCase(history = FakeHistoryRepository(), index = index)

        val result = useCase.search("profile", "ability")

        assertTrue(result is RepositoryResult.Failure)
    }

    @Test
    fun `history failure is propagated instead of reporting a successful search`() = runTest {
        val history = FakeHistoryRepository(recordFailure = true)
        val index = RecordingIndex(listOf(entry("book:ability", "ability", bookName = "词书")))
        val useCase = useCase(history = history, index = index)

        val result = useCase.search("profile", "ability")

        assertTrue(result is RepositoryResult.Failure)
    }

    /**
     * 上限必须**传给索引**（仓库据此提前收敛行数，而不是把整库捞回来再丢），
     * 且最终结果不得超过上限。
     *
     * 顺序按 spec「结果排序」的规则：三条都是前缀匹配（同等级），因此落到第 4 条
     * **lemma 字典序** —— `abilities` < `ability` < `ability-able`（后者以前者为前缀，短者在前）。
     * 索引已按上限只回两条，`ability-able` 本就不在候选里。
     */
    @Test
    fun `index result limit follows maxResults`() = runTest {
        val index = RecordingIndex(
            listOf(
                entry("book:ability", "ability"),
                entry("book:abilities", "abilities"),
                entry("book:ability-able", "ability-able"),
            ),
        )
        val useCase = useCase(history = FakeHistoryRepository(), index = index, maxResults = 2)

        val result = useCase.search("profile", "abil") as RepositoryResult.Success<List<SearchVocabularyResult>>

        assertEquals(2, index.lastLimit)
        assertEquals(2, result.value.size)
        assertEquals(listOf("abilities", "ability"), result.value.map { it.lemma })
    }

    @Test
    fun `same lemma in two word books is kept as two results`() = runTest {
        val index = RecordingIndex(
            listOf(
                entry("book-a:ability", "ability", bookName = "A"),
                entry("book-b:ability", "ability", bookName = "B"),
            ),
        )
        val useCase = useCase(history = FakeHistoryRepository(), index = index)

        val result = useCase.search("profile", "ability") as RepositoryResult.Success<List<SearchVocabularyResult>>

        assertEquals(listOf("book-a", "book-b"), result.value.map { it.wordBookId })
        assertEquals(listOf("A", "B"), result.value.map { it.wordBookName })
    }

    /**
     * 结果项是「摘要」：必须自带结果列表要显示的 lemma / 音标 / 中文释义 / 所属词书，
     * 因为索引不再携带可渲染的完整词卡。少任何一项，结果行就会显示空白。
     */
    @Test
    fun `result carries the fields the result list has to render`() = runTest {
        val index = RecordingIndex(
            listOf(
                VocabularySearchIndexEntry(
                    wordBookId = "cet4",
                    cardId = "cet4:ability",
                    wordBookName = "四级英语词汇",
                    lemma = "ability",
                    ipa = "/əˈbɪləti/",
                    meaningZh = "能力；才能",
                    normalizedTerms = listOf("ability"),
                ),
            ),
        )
        val useCase = useCase(history = FakeHistoryRepository(), index = index)

        val result = useCase.search("profile", "ability") as RepositoryResult.Success<List<SearchVocabularyResult>>

        val only = result.value.single()
        assertEquals("cet4:ability", only.cardId)
        assertEquals("ability", only.lemma)
        assertEquals("/əˈbɪləti/", only.ipa)
        assertEquals("能力；才能", only.meaningZh)
        assertEquals("四级英语词汇", only.wordBookName)
    }

    /**
     * 只在**短语**上命中的卡片必须照常返回：词项还原（lemma + 短语）不能丢短语，
     * 否则「按短语搜不到词」这类缺词是用户察觉不到的静默错误。
     */
    @Test
    fun `a card matched only through a phrase is still returned`() = runTest {
        val index = RecordingIndex(
            listOf(
                VocabularySearchIndexEntry(
                    wordBookId = "cet4",
                    cardId = "cet4:ability",
                    wordBookName = "四级英语词汇",
                    lemma = "ability",
                    ipa = "",
                    meaningZh = "能力",
                    normalizedTerms = listOf("ability", "to the best of my ability"),
                ),
            ),
        )
        val useCase = useCase(history = FakeHistoryRepository(), index = index)

        val result = useCase.search("profile", "the best") as RepositoryResult.Success<List<SearchVocabularyResult>>

        assertEquals(listOf("cet4:ability"), result.value.map { it.cardId })
    }

    /**
     * 短语与查询词完全相等时按**完全匹配**计分，排在前缀级之前。
     * 这一条由索引的包含级查询带回，再由评分提升到 0 级——是「匹配等级」而非「查询所在等级」在决定顺序。
     *
     * 第二条 `in places` 必须真的被查询命中，否则它只是被过滤掉而不是排在后面：
     * `in-places`（带连字符）并不包含 `in place`，用它做对照是假的对照。
     */
    @Test
    fun `a phrase equal to the query scores as an exact match`() = runTest {
        val index = RecordingIndex(
            listOf(
                VocabularySearchIndexEntry("cet4", "cet4:in-place", "四级", "in-place", "", "原地", listOf("in-place", "in place")),
                VocabularySearchIndexEntry("cet4", "cet4:in-places", "四级", "in places", "", "多处", listOf("in places")),
            ),
        )
        val useCase = useCase(history = FakeHistoryRepository(), index = index)

        val result = useCase.search("profile", "in place") as RepositoryResult.Success<List<SearchVocabularyResult>>

        assertEquals(listOf("cet4:in-place", "cet4:in-places"), result.value.map { it.cardId })
    }

    private fun useCase(
        history: FakeHistoryRepository,
        index: VocabularySearchIndexRepository,
        maxResults: Int = 30,
    ) = SearchVocabularyUseCase(
        history = history,
        index = index,
        clock = FixedClockProvider(now, ZoneOffset.UTC),
        maxResults = maxResults,
    )

    private fun entry(
        cardId: String,
        lemma: String,
        bookName: String = "词书",
        phrases: List<String> = emptyList(),
    ) = VocabularySearchIndexEntry(
        wordBookId = cardId.substringBefore(':'),
        cardId = cardId,
        wordBookName = bookName,
        lemma = lemma,
        ipa = "/$lemma/",
        meaningZh = "释义",
        normalizedTerms = listOf(lemma) + phrases,
    )

    private class RecordingIndex(
        private val response: List<VocabularySearchIndexEntry>,
        private val failure: Boolean = false,
    ) : VocabularySearchIndexRepository {
        var searchCalls = 0
        var lastQuery: String? = null
        var lastLimit: Int? = null

        override suspend fun replaceWordBook(
            wordBookId: String,
            wordBookName: String,
            dataVersion: String,
            cards: List<com.example.englishlearning.learning.domain.WordCard>,
        ) = RepositoryResult.Success(Unit)

        override suspend fun deleteWordBook(wordBookId: String) = RepositoryResult.Success(Unit)

        override suspend fun search(
            normalizedQuery: String,
            limit: Int,
        ): RepositoryResult<List<VocabularySearchIndexEntry>> {
            searchCalls += 1
            lastQuery = normalizedQuery
            lastLimit = limit
            return if (failure) {
                RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            } else {
                RepositoryResult.Success(response.take(limit))
            }
        }

        override suspend fun indexedVersions() = RepositoryResult.Success(emptyMap<String, String>())

        override suspend fun deleteMissing(keepWordBookIds: Collection<String>) = RepositoryResult.Success(Unit)
    }

    private class FakeHistoryRepository(
        private val recordFailure: Boolean = false,
    ) : VocabularySearchHistoryRepository {
        val recorded = mutableListOf<Call>()

        override suspend fun list(profileId: String, limit: Int) =
            RepositoryResult.Success(emptyList<VocabularySearchHistory>())

        override suspend fun record(
            profileId: String,
            query: String,
            representative: SearchRepresentative?,
            at: Instant,
        ): RepositoryResult<Unit> {
            recorded += Call(profileId, query, representative, at)
            return if (recordFailure) {
                RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            } else {
                RepositoryResult.Success(Unit)
            }
        }

        override suspend fun find(profileId: String, normalizedQuery: String) =
            RepositoryResult.Success<VocabularySearchHistory?>(null)

        override suspend fun clear(profileId: String) = RepositoryResult.Success(Unit)

        data class Call(
            val profileId: String,
            val query: String,
            val representative: SearchRepresentative?,
            val at: Instant,
        )
    }
}
