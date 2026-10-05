package com.example.englishlearning.learning

data class WordBookProgress(
    val learned: Int,
    val total: Int,
    val todayNew: Int = 0,
    val todayReview: Int = 0,
) {
    val unlearned: Int get() = (total - learned).coerceAtLeast(0)

    val fraction: Float
        get() = if (total <= 0) 0f else (learned.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    companion object {
        fun calculate(totalWords: Int, cardIds: List<String>, reviewedCardIds: List<String>): WordBookProgress {
            val allowed = cardIds.toSet()
            val learned = reviewedCardIds.asSequence().filter(allowed::contains).toSet().size
            return WordBookProgress(learned = learned.coerceAtMost(totalWords.coerceAtLeast(0)), total = totalWords.coerceAtLeast(0))
        }
    }
}
