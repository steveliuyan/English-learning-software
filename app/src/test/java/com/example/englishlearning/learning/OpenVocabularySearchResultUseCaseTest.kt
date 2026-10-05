package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.DerivedWord
import com.example.englishlearning.learning.domain.PhraseEntry
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.learning.domain.WordSense
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * 回源取卡用例。
 *
 * 它存在的理由：搜索索引为了控体积只存结果列表要显示的几列（lemma / 音标 / 中文释义 / 词书名），
 * 详情页要的释义分组、例句、派生词、短语、近义词、配图**都不在索引里**。
 * 所以点开一条结果必须按 `cardId` 回词书取完整卡片——这也正是「索引不存整卡快照」能成立的前提。
 */
class OpenVocabularySearchResultUseCaseTest {
    @Test
    fun `returns the full card with everything the detail screen renders`() = runTest {
        val full = WordCard(
            cardId = "cet4:ability",
            wordBookId = "cet4",
            lemma = "ability",
            ipa = "/əˈbɪləti/",
            partOfSpeech = "n.",
            meaningZh = "能力",
            example = "She has the ability to lead.",
            exampleZh = "她有领导能力。",
            senses = listOf(WordSense("n.", "能力；才能"), WordSense("n.", "本领")),
            derived = listOf(DerivedWord("able", "adj.", "能够的")),
            phrases = listOf(PhraseEntry("to the best of my ability", "尽我所能")),
            imagePath = "/tmp/ability.png",
        )
        val source = RecordingCardSource(mapOf("cet4:ability" to full))

        val opened = OpenVocabularySearchResultUseCase(source)(summary("cet4:ability"))

        assertEquals(full, opened)
        assertEquals(listOf(WordSense("n.", "能力；才能"), WordSense("n.", "本领")), opened?.senses)
        assertEquals("She has the ability to lead.", opened?.example)
        assertEquals(listOf(PhraseEntry("to the best of my ability", "尽我所能")), opened?.phrases)
    }

    /**
     * 只请求被点开的那一条：一次点击解析一册，而不是把整份结果集都补全。
     * 结果集常跨多本词书（`ability` 精确命中 9 本），批量补全会让每次搜索重新解析命中的每一册，
     * 正是索引要消除的开销。
     */
    @Test
    fun `asks the source for exactly the opened card`() = runTest {
        val source = RecordingCardSource(mapOf("cet4:ability" to card("cet4:ability")))

        OpenVocabularySearchResultUseCase(source)(summary("cet4:ability"))

        assertEquals(listOf(listOf("cet4:ability")), source.requested)
    }

    /**
     * 词书被删除后索引行可能短暂残留（索引是派生数据，下次刷新才收敛）。
     * 读不到必须返回 `null`，让调用方显示「这个词卡暂时打不开」——
     * 绝不能返回一张空壳卡，那会让详情页显示成「这个词没有释义」。
     */
    @Test
    fun `returns null when the word book no longer has the card`() = runTest {
        val source = RecordingCardSource(emptyMap())

        assertNull(OpenVocabularySearchResultUseCase(source)(summary("cet4:ability")))
    }

    /** 源返回了别的卡（数据不一致）也不能顶替：拿错词比拿不到更糟。 */
    @Test
    fun `does not accept a card with a different id`() = runTest {
        val source = RecordingCardSource(mapOf("cet4:other" to card("cet4:other")))
        source.forceResponse = listOf(card("cet4:other"))

        assertNull(OpenVocabularySearchResultUseCase(source)(summary("cet4:ability")))
    }

    /**
     * 用例本身**不吞异常**：读卡失败是存储层的事实，应该原样抛给调用方。
     * 把「失败降级成 null」放在 ViewModel 里做，是为了让降级行为可见、可测，而不是悄悄吃掉错误
     * （见 `GlobalVocabularySearchViewModelTest`）。
     */
    @Test
    fun `a failing source propagates instead of being silently swallowed`() = runTest {
        val source = RecordingCardSource(emptyMap(), failWith = IllegalStateException("broken package"))

        // 不能写成 `assertFailsWith { ... }`：块里要调的是 suspend 函数，
        // 而 kotlin.test 的 assertFailsWith 接收的是非挂起 lambda。
        val thrown = try {
            OpenVocabularySearchResultUseCase(source)(summary("cet4:ability"))
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertNotNull(thrown, "读卡失败必须原样抛出，不能被吞成 null")
        assertTrue(source.requested.isNotEmpty())
    }

    private fun summary(cardId: String) = SearchVocabularyResult(
        wordBookId = cardId.substringBefore(':'),
        cardId = cardId,
        wordBookName = "四级英语词汇",
        lemma = cardId.substringAfter(':'),
        ipa = "/x/",
        meaningZh = "释义",
    )

    private fun card(cardId: String) = WordCard(
        cardId = cardId,
        wordBookId = cardId.substringBefore(':'),
        lemma = cardId.substringAfter(':'),
        ipa = "",
        partOfSpeech = "n.",
        meaningZh = "释义",
    )

    private class RecordingCardSource(
        private val content: Map<String, WordCard>,
        private val failWith: Exception? = null,
    ) : WordCardSource {
        val requested = mutableListOf<List<String>>()
        var forceResponse: List<WordCard>? = null

        override suspend fun cardIds(wordBookId: String): List<String> =
            content.keys.filter { it.startsWith("$wordBookId:") }

        override suspend fun cards(cardIds: List<String>): List<WordCard> {
            requested += cardIds
            failWith?.let { throw it }
            return forceResponse ?: cardIds.mapNotNull(content::get)
        }
    }
}
