package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.WordCard

/**
 * Content port for word cards (spec F1-03).
 *
 * The learning flow asks only for the cards its plan selected, so a licence-verified
 * dataset can be plugged in later without touching the UI, the event store or the
 * scheduler. V1 ships [PlaceholderWordCardSource], which holds app-authored placeholder
 * content only — no third-party word book or dictionary data is bundled.
 */
interface WordCardSource {
    /** Card ids for [wordBookId] in a stable order; empty when the word book has no content. */
    suspend fun cardIds(wordBookId: String): List<String>

    /** Content for [cardIds], in the requested order; ids with no content are dropped. */
    suspend fun cards(cardIds: List<String>): List<WordCard>
}
