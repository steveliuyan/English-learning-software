package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.SubmitFeedbackResult
import com.example.englishlearning.learning.spelling.RecordSpellingFeedbackUseCase
import com.example.englishlearning.learning.spelling.SpellingPrompt
import com.example.englishlearning.learning.spelling.SpellingVerdict
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 拼写测验的排期回执：写入成功就报调度器**真正写入**的时间，写入失败就明说没记上。
 *
 * 回执里那个「约 N 分钟 / 明天 / N 天」必须从 `nextReviewAt` 反推——生产调度器是 FSRS 档
 * （Again 10 分钟 / Hard 12 小时 / Good 1 天），界面写死任何一个数字都会和实际排期打架。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpellingQuizViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-11-09T10:00:00Z")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val prompt = SpellingPrompt(
        cardId = "c-1",
        wordBookId = "wb-1",
        answer = "confidence",
        partOfSpeech = "n.",
        meaningZh = "自信",
        ipa = "/ˈkɒnfɪdəns/",
    )

    private fun viewModel(result: SubmitFeedbackResult): SpellingQuizViewModel {
        val useCase = RecordSpellingFeedbackUseCase(eventIds = EventIdFactory { "evt-1" }) { result }
        return SpellingQuizViewModel(
            recordSpellingFeedback = useCase,
            clock = FixedClockProvider(now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `successful record reports the interval the scheduler wrote`() = runTest(dispatcher) {
        val vm = viewModel(SubmitFeedbackResult.Recorded(now.plusSeconds(10 * 60)))

        vm.record("p-1", prompt, SpellingVerdict.Wrong(distance = 2))
        advanceUntilIdle()

        assertEquals(SpellingScheduleState.Recorded("约 10 分钟后再问一次"), vm.schedule.value)
    }

    @Test
    fun `a full day is reported as tomorrow not as 24 hours`() = runTest(dispatcher) {
        val vm = viewModel(SubmitFeedbackResult.Recorded(now.plusSeconds(24 * 60 * 60)))

        vm.record("p-1", prompt, SpellingVerdict.Correct)
        advanceUntilIdle()

        assertEquals(SpellingScheduleState.Recorded("明天再问一次"), vm.schedule.value)
    }

    @Test
    fun `storage failure never claims the schedule moved`() = runTest(dispatcher) {
        val vm = viewModel(SubmitFeedbackResult.StorageUnavailable)

        vm.record("p-1", prompt, SpellingVerdict.CloseMiss(distance = 1))
        advanceUntilIdle()

        assertEquals(SpellingScheduleState.Failed, vm.schedule.value)
    }

    @Test
    fun `clearing drops the receipt of the previous question`() = runTest(dispatcher) {
        val vm = viewModel(SubmitFeedbackResult.Recorded(now.plusSeconds(12 * 60 * 60)))
        vm.record("p-1", prompt, SpellingVerdict.Correct)
        advanceUntilIdle()
        assertEquals(SpellingScheduleState.Recorded("约 12 小时后再问一次"), vm.schedule.value)

        vm.clear()

        assertEquals(SpellingScheduleState.Idle, vm.schedule.value)
    }
}
