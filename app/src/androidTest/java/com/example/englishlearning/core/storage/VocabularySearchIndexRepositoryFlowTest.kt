package com.example.englishlearning.core.storage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.RoomVocabularySearchIndexRepository
import com.example.englishlearning.learning.domain.PhraseEntry
import com.example.englishlearning.learning.domain.WordCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 索引的**写读闭环**：走真实仓储 + 真实 Room 库（内存），不碰真机用户库。
 *
 * 为什么需要它：`VocabularySearchIndexPlanTest` 只断言 SQL 与迁移，
 * `VocabularySearchGramIndexTest` 只断言「SQL + 编码函数」的等价性——两者都**绕开了仓储**。
 * 于是仓储这一层成了盲区：它给 contains 传的是不是转义过的查询串、短查询串有没有走兜底、
 * gram 行到底有没有在 `replaceWordBook` 里被写进去，都没有断言盯着。
 * 而这个盲区是真实存在的——把「前缀上界」传错的变异，上面两个测试都抓不到。
 *
 * 这里的判据全部落在**用户可见的结果**上，尤其是「词中片段」：只有当 gram 行真的被写进去时，
 * `bili` / `bility` 这类查询才可能命中（它们的候选只能来自 gram 索引）。
 */
@RunWith(AndroidJUnit4::class)
class VocabularySearchIndexRepositoryFlowTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomVocabularySearchIndexRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        repository = RoomVocabularySearchIndexRepository(database, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * 词中片段能查到 ⟹ 仓储确实把 gram 行写进了 FTS4 表。
     *
     * 若 `replaceWordBook` 漏写 gram（或写错表），FTS 给不出候选，contains 这一级会**恒空**，
     * 而 `ability` 这类「完全匹配」仍旧查得到——所以必须用只能靠 contains 命中的 needle。
     */
    @Test
    fun midWordNeedlesOnlyReachableThroughTheGramIndex() = runBlocking {
        replaceFixtureBook()

        assertCardIds(listOf("cet4:ability", "cet4:disability"), search("bili"))
        assertCardIds(listOf("cet4:ability", "cet4:disability"), search("bility"))
        assertCardIds(listOf("cet4:absent-minded"), search("mind"))
    }

    /** 等级顺序不变：完全匹配必须排在包含匹配前面。 */
    @Test
    fun exactOutranksContainsEndToEnd() = runBlocking {
        replaceFixtureBook()

        assertCardIds(listOf("cet4:ability", "cet4:disability"), search("ability"))
    }

    /**
     * 转义必须发生在**仓储**这一层。
     *
     * `a_` 里的 `_` 是 LIKE 通配符：转义后它只匹配字面 `a_`（本夹具里没有 → 空），
     * 不转义则等于「任意单字符」，会把 ability / disability 全捞出来——差别一眼可辨。
     */
    @Test
    fun theRepositoryEscapesLikeWildcardsBeforeQuerying() = runBlocking {
        replaceFixtureBook()

        assertCardIds(emptyList(), search("a_"))
    }

    /**
     * 走**索引路径**（查询串 ≥3 字符）时同样必须转义。
     *
     * 单独一条、且夹具里放了 `a-b`：`a_b` 的 gram 候选正是 `a-b`（折叠成同一个 gram 串），
     * 所以这条真正经过 `FTS 收窄 → LIKE 精确` 两个阶段：
     * - 转义正确时 LIKE 找的是字面 `a_b`，`a-b` 不该命中 → 空；
     * - 不转义时 `_` 变通配符，`a-b` 被捞出来 → 非空。
     * 下面那条 sanity 断言同时证明：字面查询 `a-b` **确实**能查到它——否则上面那个「空」
     * 可能只是因为这张卡压根没进索引。
     */
    @Test
    fun theRepositoryEscapesLikeWildcardsInsideTheIndexedPath() = runBlocking {
        replaceFixtureBook()

        assertCardIds(listOf("cet4:a-b"), search("a-b"))
        assertCardIds(emptyList(), search("a_b"))
    }

    /** 短于 gram 长度（<3）的查询串没有 gram 可用，必须走 LIKE 兜底且仍能命中词中片段。 */
    @Test
    fun needlesShorterThanTheGramLengthFallBackAndStillHit() = runBlocking {
        replaceFixtureBook()

        assertCardIds(listOf("cet4:ability", "cet4:disability"), search("il"))
    }

    /** 整册替换是幂等的：同一册再灌一次不会把结果翻倍。 */
    @Test
    fun replacingAWordBookTwiceDoesNotDuplicateResults() = runBlocking {
        replaceFixtureBook()
        replaceFixtureBook()

        assertCardIds(listOf("cet4:ability", "cet4:disability"), search("bili"))
    }

    private suspend fun replaceFixtureBook() {
        val result = repository.replaceWordBook(
            wordBookId = "cet4",
            wordBookName = "四级词书",
            dataVersion = "v1",
            cards = listOf(
                card("cet4:ability", "ability", phrases = listOf("to the best of my ability")),
                card("cet4:disability", "disability"),
                card("cet4:absent-minded", "absent-minded"),
                // 用来验证 LIKE 转义：`a_b` 与 `a-b` 折叠成同一个 gram 串，因此 `a_b` 能拿到 `a-b`
                // 这个候选；转义与否决定它最终命中还是被精确判断剔除。
                card("cet4:a-b", "a-b"),
            ),
        )
        assertEquals("换册写索引必须成功", true, result is RepositoryResult.Success)
    }

    private suspend fun search(needle: String) = when (val result = repository.search(needle, LIMIT)) {
        is RepositoryResult.Success -> result.value.map { it.cardId }
        is RepositoryResult.Failure -> throw AssertionError("查询「$needle」失败：$result")
    }

    private fun assertCardIds(expected: List<String>, actual: List<String>) {
        assertEquals("索引查询结果（顺序与内容都要一致）", expected, actual)
    }

    private fun card(cardId: String, lemma: String, phrases: List<String> = emptyList()) = WordCard(
        cardId = cardId,
        wordBookId = cardId.substringBefore(':'),
        lemma = lemma,
        ipa = "/$lemma/",
        partOfSpeech = "n.",
        meaningZh = "释义",
        phrases = phrases.map { PhraseEntry(it, "释义") },
        rank = 1,
    )

    private companion object {
        const val LIMIT = 30
    }
}
