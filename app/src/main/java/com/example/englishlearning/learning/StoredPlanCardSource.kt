package com.example.englishlearning.learning

import java.time.Instant

/**
 * Raised when the learning event store cannot answer a plan-card query.
 *
 * [PlanCardSource] returns plain id lists with no error channel, so a storage failure
 * cannot be reported as "no cards". It is thrown instead, and plan generation turns it
 * into [TodayPlanResult.StorageUnavailable]: the day's plan is an immutable snapshot, so
 * silently persisting an empty one would lose the whole day.
 */
class PlanCardSourceUnavailable : Exception("learning event store unavailable")

/**
 * [PlanCardSource] backed by the event store and the word card content source (spec F1-02/F1-03).
 *
 * "Due" comes from the derived card schedule, "new" is every card of the word book that has
 * never been reviewed, and the two sets are disjoint by construction: a reviewed card has a
 * derived state, a new card does not. New cards are capped at the profile's daily target and
 * fall short when the word book has less content left.
 */
class StoredPlanCardSource(
    private val content: WordCardSource,
    private val events: LearningEventRepository,
) : PlanCardSource {
    override suspend fun dueCardIds(wordBookId: String, now: Instant): List<String> =
        events.dueCardIds(wordBookId, now).orUnavailable()

    override suspend fun newCardIds(wordBookId: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val reviewed = events.reviewedCardIds(wordBookId).orUnavailable().toSet()
        return content.cardIds(wordBookId).filterNot(reviewed::contains).take(limit)
    }

    private fun <T> RepositoryResult<T>.orUnavailable(): T =
        when (this) {
            is RepositoryResult.Success -> value
            is RepositoryResult.Failure -> throw PlanCardSourceUnavailable()
        }
}
