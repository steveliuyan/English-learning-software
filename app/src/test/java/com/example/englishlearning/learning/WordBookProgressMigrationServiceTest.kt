package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.ReviewFeedback
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class WordBookProgressMigrationServiceTest {
    @Test
    fun `migration passes identity and profile to one atomic repository operation`() = runTest {
        val source = card("old:ability", "ability")
        val target = card("new:ability", "ability")
        val state = CardReviewState(source.cardId, source.wordBookId, ReviewFeedback.Good, Instant.ofEpochMilli(10), Instant.ofEpochMilli(20))
        val events = RecordingMigrationRepository(state)
        val service = WordBookProgressMigrationService(
            cards = Cards(mapOf(source.cardId to source, target.cardId to target)),
            events = events,
        )

        val preview = service.preview("profile-a", source.wordBookId, target.wordBookId).getOrThrow()
        val result = service.migrate("profile-a", source.wordBookId, target.wordBookId, preview)

        assertEquals(ProgressMigrationResult.Applied(1), result)
        assertEquals(listOf("profile-a|old|new|old:ability|new:ability"), events.calls)
    }

    private fun card(id: String, lemma: String) = WordCard(
        cardId = id,
        wordBookId = id.substringBefore(':'),
        lemma = lemma,
        partOfSpeech = "n.",
        meaningZh = "能力",
        ipa = "",
        example = "",
        exampleZh = "",
        derived = emptyList(),
        phrases = emptyList(),
        synonyms = emptyList(),
        imagePath = null,
    )

    private class Cards(private val values: Map<String, WordCard>) : WordCardSource {
        override suspend fun cardIds(wordBookId: String) = values.values.filter { it.wordBookId == wordBookId }.map { it.cardId }
        override suspend fun cards(cardIds: List<String>) = cardIds.mapNotNull(values::get)
    }

    private class RecordingMigrationRepository(private val state: CardReviewState) : LearningEventRepository {
        val calls = mutableListOf<String>()
        override suspend fun append(event: com.example.englishlearning.learning.domain.LearningEvent, nextState: CardReviewState) = AppendEventResult.Appended(false)
        override suspend fun findEvent(eventId: String): RepositoryResult<com.example.englishlearning.learning.domain.LearningEvent?> = RepositoryResult.Success(null)
        override suspend fun findCardState(cardId: String): RepositoryResult<CardReviewState?> = RepositoryResult.Success(null)
        override suspend fun countEventsForCard(planId: String, cardId: String): RepositoryResult<Int> = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String): RepositoryResult<List<String>> = RepositoryResult.Success(emptyList())
        override suspend fun reviewedCardIds(wordBookId: String): RepositoryResult<List<String>> = RepositoryResult.Success(listOf(state.cardId))
        override suspend fun dueCardIds(wordBookId: String, now: Instant): RepositoryResult<List<String>> = RepositoryResult.Success(emptyList())
        override suspend fun reviewedStates(wordBookId: String) = RepositoryResult.Success(listOf(state))
        override suspend fun migrateReviewStates(profileId: String, sourceBookId: String, targetBookId: String, candidates: List<ProgressMigrationCandidate>): RepositoryResult<Int> {
            calls += candidates.joinToString { "$profileId|${sourceBookId.removeSuffix("-book")}|${targetBookId.removeSuffix("-book")}|${it.source.cardId}|${it.target.cardId}" }
            return RepositoryResult.Success(candidates.size)
        }
    }
}
