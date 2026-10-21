package com.example.englishlearning.ui

import com.example.englishlearning.learning.FavoriteWord
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 「我的词」入口尾部计数串（`myWordsSummary`）的 JVM 用例（I-3 修复轮 1）。
 *
 * 规则原文（评审点名「一条判据都没有」的那段宿主计算）：**三段全 `Ready` 才给 `"fav · wb · wrong"`，
 * 任一还没读出来就整体 `null`（宁缺）**——半个数字会让人把「还在读」当成「0 个」。
 * 「三段各读好后恰好显示各段条数」「空段显示 0 而不是整串消失」都是这条规则的组成部分。
 *
 * 计算本体已从 `AppScreen` 的组合代码抽成 [myWordsSummaryOf] 纯函数（评审 I-3 选项 ① 的「更好的做法」），
 * 宿主只留一处调用；宿主接线端到端由 connected `AppScreenTest` 的两枚新用例承重。
 */
class MyWordsSummaryTest {

    private fun favorite(lemma: String, index: Int) = FavoriteWord(
        profileId = "p1",
        cardId = "fav-card-$index",
        wordBookId = "cet4",
        lemma = lemma,
        addedAt = Instant.EPOCH,
    )

    private fun card(lemma: String, index: Int) = WordCard(
        cardId = "card-$index",
        wordBookId = "cet4",
        lemma = lemma,
        ipa = "",
        partOfSpeech = "n.",
        meaningZh = "释义$index",
    )

    private fun vocabularyItem(index: Int) = VocabularyItem(
        entry = com.example.englishlearning.learning.VocabularyEntry(
            profileId = "p1",
            wordBookId = "cet4",
            cardId = "card-$index",
            addedAt = Instant.EPOCH,
            lastFeedback = "Again",
        ),
        card = card("word-$index", index),
    )

    @Test
    fun all_three_ready_gives_the_three_counts_in_order() {
        assertEquals(
            "2 · 3 · 4",
            myWordsSummaryOf(
                favorites = FavoritesUiState.Ready(words = listOf(favorite("a", 0), favorite("b", 1))),
                vocabulary = VocabularyUiState.Ready(items = (0..2).map { vocabularyItem(it) }),
                wrongWords = WrongWordsUiState.Ready(cards = (0..3).map { card("w$it", it) }),
            ),
        )
    }

    @Test
    fun empty_ready_sections_show_zero_not_a_missing_string() {
        assertEquals(
            "0 · 0 · 0",
            myWordsSummaryOf(
                favorites = FavoritesUiState.Ready(),
                vocabulary = VocabularyUiState.Ready(items = emptyList()),
                wrongWords = WrongWordsUiState.Ready(cards = emptyList()),
            ),
        )
    }

    @Test
    fun any_section_not_ready_gives_no_half_number() {
        val readyFavorites = FavoritesUiState.Ready(words = listOf(favorite("a", 0)))
        val readyVocabulary = VocabularyUiState.Ready(items = listOf(vocabularyItem(0)))
        val readyWrong = WrongWordsUiState.Ready(cards = listOf(card("w", 0)))
        // 逐段点名：任一缺位（null / Loading / Unavailable）都必须整体 null，而不是给两三段的部分数。
        assertNull(myWordsSummaryOf(favorites = null, vocabulary = readyVocabulary, wrongWords = readyWrong))
        assertNull(myWordsSummaryOf(favorites = FavoritesUiState.Loading, vocabulary = readyVocabulary, wrongWords = readyWrong))
        assertNull(myWordsSummaryOf(favorites = readyFavorites, vocabulary = null, wrongWords = readyWrong))
        assertNull(myWordsSummaryOf(favorites = readyFavorites, vocabulary = VocabularyUiState.Unavailable, wrongWords = readyWrong))
        assertNull(myWordsSummaryOf(favorites = readyFavorites, vocabulary = readyVocabulary, wrongWords = null))
        assertNull(myWordsSummaryOf(favorites = readyFavorites, vocabulary = readyVocabulary, wrongWords = WrongWordsUiState.Loading))
    }
}
