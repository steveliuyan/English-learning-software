package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.WordCard

fun normalizeWordLemma(value: String): String = value.trim().lowercase().split(Regex("\\s+")).joinToString(" ")

data class ProgressMigrationCandidate(
    val source: WordCard,
    val target: WordCard,
    val sourceState: CardReviewState,
)

data class ProgressMigrationPreview(
    val candidates: List<ProgressMigrationCandidate>,
    val ambiguousLemmas: Set<String>,
)

object WordBookProgressMigration {
    fun preview(
        sourceCards: List<WordCard>,
        targetCards: List<WordCard>,
        sourceStates: Map<String, CardReviewState>,
    ): ProgressMigrationPreview {
        val targets = targetCards.groupBy { normalizeWordLemma(it.lemma) }
        val ambiguous = mutableSetOf<String>()
        val candidates = sourceCards.mapNotNull { source ->
            val state = sourceStates[source.cardId] ?: return@mapNotNull null
            val key = normalizeWordLemma(source.lemma)
            val matches = targets[key].orEmpty()
            if (matches.size != 1) {
                if (matches.size > 1) ambiguous += key
                null
            } else {
                ProgressMigrationCandidate(source, matches.single(), state)
            }
        }
        return ProgressMigrationPreview(candidates, ambiguous)
    }
}
