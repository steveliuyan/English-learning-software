package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.WordCard

/**
 * V1 [WordCardSource]: **app-authored placeholder content only**.
 *
 * The word books shipped so far carry metadata without any word entries, and no
 * licence-verified dictionary data may be bundled yet (see
 * `docs/decisions/2026-09-19-placeholder-word-card-content.md` and
 * `docs/third-party-notices.md`). Without content the learning flow would be
 * unreachable on a device, so this class supplies a small, self-written set of basic
 * words to keep F1-03 runnable and device-verifiable.
 *
 * It is deliberately loud about what it is: every id is prefixed with
 * [CARD_ID_PREFIX] and the entries are named `PLACEHOLDER_*`. Replace this class —
 * not the UI, the event store or the scheduler — once licence-verified word data
 * lands, and update the notice ledger and `metadata.json` at the same time.
 *
 * Card ids encode the word book (`placeholder:<wordBookId>:<lemma>`), which keeps the
 * same spelling in two word books as two independent progress records (AC1-06).
 */
class PlaceholderWordCardSource(
    private val entries: List<WordCard> = PLACEHOLDER_ENTRIES,
) : WordCardSource {
    private val byId: Map<String, WordCard> = entries.associateBy(WordCard::cardId)
    private val idsByWordBook: Map<String, List<String>> =
        entries.groupBy(WordCard::wordBookId).mapValues { (_, cards) -> cards.map(WordCard::cardId) }

    override suspend fun cardIds(wordBookId: String): List<String> = idsByWordBook[wordBookId].orEmpty()

    override suspend fun cards(cardIds: List<String>): List<WordCard> = cardIds.mapNotNull(byId::get)

    companion object {
        const val CARD_ID_PREFIX: String = "placeholder"

        /** Must stay in sync with `app/src/main/assets/wordbooks/metadata.json`. */
        val WORD_BOOK_IDS: List<String> =
            listOf(
                "primary-school",
                "junior-high-school",
                "senior-high-school",
                "cet4",
                "cet6",
                "postgraduate-entrance-exam",
            )

        fun cardId(wordBookId: String, lemma: String): String = "$CARD_ID_PREFIX:$wordBookId:$lemma"

        /**
         * Inflections are optional extra material, so they live in their own table: that keeps
         * [word] a five-field literal and stops the word list from becoming 12 eight-line blocks.
         *
         * Declared before [PLACEHOLDER_WORDS] on purpose — companion properties initialise in
         * declaration order, and [word] reads this table while the list below is built.
         */
        private val INFLECTIONS: Map<String, List<String>> =
            mapOf(
                "achieve" to listOf("achieved", "achieving", "achieves"),
                "benefit" to listOf("benefits", "benefited"),
                "develop" to listOf("developed", "developing", "develops"),
                "economy" to listOf("economies"),
                "feature" to listOf("features"),
                "influence" to listOf("influences", "influenced", "influencing"),
                "maintain" to listOf("maintained", "maintaining", "maintains"),
                "reduce" to listOf("reduced", "reducing", "reduces"),
            )

        /**
         * App-authored base words. Declared before [PLACEHOLDER_ENTRIES] on purpose: companion
         * properties initialise in declaration order, so reading this list first avoids an
         * `ExceptionInInitializerError` from a not-yet-initialised field.
         */
        private val PLACEHOLDER_WORDS: List<WordCard> =
            listOf(
                word("ability", "əˈbɪləti", "n.", "能力；才能", "She has the ability to explain ideas."),
                word("achieve", "əˈtʃiːv", "v.", "实现；达到", "We achieved the goal ahead of schedule."),
                word("benefit", "ˈbenɪfɪt", "n.", "好处；益处", "Regular review brings a lasting benefit."),
                word("climate", "ˈklaɪmət", "n.", "气候", "The climate here is mild all year round."),
                word("develop", "dɪˈveləp", "v.", "发展；开发", "Small habits develop into strong skills."),
                word("economy", "ɪˈkɒnəmi", "n.", "经济", "The local economy depends on tourism."),
                word("feature", "ˈfiːtʃə", "n.", "特征；特色", "The main feature of the plan is simplicity."),
                word("generous", "ˈdʒenərəs", "adj.", "慷慨的；大方的", "He was generous with his time."),
                word("influence", "ˈɪnfluəns", "n.", "影响；影响力", "Friends can influence our choices."),
                word("maintain", "meɪnˈteɪn", "v.", "维持；保养", "It is hard to maintain focus for hours."),
                word("obvious", "ˈɒbviəs", "adj.", "明显的；显而易见的", "The answer is obvious once you see it."),
                word("reduce", "rɪˈdjuːs", "v.", "减少；降低", "Enough sleep can reduce stress."),
            )

        /**
         * The shared base words expanded into every word book, so each one has the same small
         * amount of content to learn from.
         */
        val PLACEHOLDER_ENTRIES: List<WordCard> =
            WORD_BOOK_IDS.flatMap { wordBookId ->
                PLACEHOLDER_WORDS.map { word ->
                    word.copy(cardId = cardId(wordBookId, word.lemma), wordBookId = wordBookId)
                }
            }

        private fun word(
            lemma: String,
            ipa: String,
            partOfSpeech: String,
            meaningZh: String,
            example: String,
        ): WordCard =
            WordCard(
                cardId = cardId(WORD_BOOK_IDS.first(), lemma),
                wordBookId = WORD_BOOK_IDS.first(),
                lemma = lemma,
                ipa = ipa,
                partOfSpeech = partOfSpeech,
                meaningZh = meaningZh,
                example = example,
                inflections = INFLECTIONS[lemma].orEmpty(),
            )
    }
}
