package com.example.englishlearning.learning.spelling

import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.SubmitFeedbackCommand
import com.example.englishlearning.learning.SubmitFeedbackResult
import com.example.englishlearning.learning.domain.CardFeedback
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * 拼写作答 → 复习排期这条写通路。
 *
 * 钉的是四件事：三档判分各自折成哪个 [CardFeedback]；planId 用合成前缀（不冒充今日计划任务）；
 * eventId 走幂等键（重试同一题不会写第二次）；存储不可用时不假装排期动过。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordSpellingFeedbackUseCaseTest {

    private val prompt = SpellingPrompt(
        cardId = "c-1",
        wordBookId = "wb-1",
        answer = "confidence",
        partOfSpeech = "n.",
        meaningZh = "自信",
        ipa = "/ˈkɒnfɪdəns/",
        example = "She spoke with confidence.",
    )
    private val nextReviewAt = Instant.parse("2026-11-10T09:30:00Z")

    private val captured = mutableListOf<SubmitFeedbackCommand>()

    private fun useCase(
        result: SubmitFeedbackResult = SubmitFeedbackResult.Recorded(nextReviewAt),
    ): RecordSpellingFeedbackUseCase =
        RecordSpellingFeedbackUseCase(eventIds = EventIdFactory { "evt-fixed" }) { command ->
            captured += command
            result
        }

    @Test
    fun `correct answer is recorded as known`() = runTest {
        val outcome = useCase().record("p-1", prompt, SpellingVerdict.Correct)

        val command = captured.single()
        assertEquals(CardFeedback.Known, command.feedback)
        assertEquals("c-1", command.cardId)
        assertEquals("wb-1", command.wordBookId)
        assertEquals("p-1", command.profileId)
        assertEquals("evt-fixed", command.eventId, "默认 eventId 来自 EventIdFactory，是幂等键")
        assertEquals(nextReviewAt, (outcome as RecordSpellingResult.Recorded).nextReviewAt)
    }

    @Test
    fun `close miss is recorded as fuzzy and wrong as unknown`() = runTest {
        val case = useCase()
        case.record("p-1", prompt, SpellingVerdict.CloseMiss(distance = 1))
        case.record("p-1", prompt, SpellingVerdict.Wrong(distance = 3))

        assertEquals(listOf(CardFeedback.Fuzzy, CardFeedback.Unknown), captured.map { it.feedback })
    }

    @Test
    fun `plan id is synthetic so a spelling drill is not a plan task`() = runTest {
        useCase().record("p-9", prompt, SpellingVerdict.Correct)

        assertEquals("spelling:p-9", captured.single().planId)
        assertTrue(captured.single().planId.startsWith(RecordSpellingFeedbackUseCase.PLAN_PREFIX))
    }

    @Test
    fun `a retried submission reuses the caller event id`() = runTest {
        val case = useCase()
        case.record("p-1", prompt, SpellingVerdict.Correct, eventId = "evt-retry")
        case.record("p-1", prompt, SpellingVerdict.Correct, eventId = "evt-retry")

        assertEquals(listOf("evt-retry", "evt-retry"), captured.map { it.eventId })
    }

    @Test
    fun `storage failure is reported as unavailable and carries no schedule`() = runTest {
        val outcome =
            useCase(result = SubmitFeedbackResult.StorageUnavailable)
                .record("p-1", prompt, SpellingVerdict.Wrong(distance = 2))

        assertEquals(RecordSpellingResult.StorageUnavailable, outcome)
    }
}
