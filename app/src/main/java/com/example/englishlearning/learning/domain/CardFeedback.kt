package com.example.englishlearning.learning.domain

/**
 * User-facing three-tier feedback for a single card. The V1 mapping to
 * [ReviewFeedback] is fixed by spec F1-03 and must not be re-tuned per card.
 */
enum class CardFeedback {
    Unknown,
    Fuzzy,
    Known,
    ;

    /** V1 fixed mapping: 不认识→Again, 模糊→Hard, 认识→Good. */
    fun toReviewFeedback(): ReviewFeedback =
        when (this) {
            Unknown -> ReviewFeedback.Again
            Fuzzy -> ReviewFeedback.Hard
            Known -> ReviewFeedback.Good
        }
}
