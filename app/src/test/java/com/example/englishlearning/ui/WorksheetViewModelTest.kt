package com.example.englishlearning.ui

import com.example.englishlearning.export.RenderedWorksheet
import com.example.englishlearning.export.WorksheetPdfWriter
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.PlanCardFeedback
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.learning.worksheet.BuildWorksheetContentUseCase
import com.example.englishlearning.learning.worksheet.WorksheetDocumentBuilder
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetPage
import com.example.englishlearning.learning.worksheet.WorksheetPaginator
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * 锁定用户实测到的回归：预览一次之后返回设置页，模板被定死在默认值、所有开关失效，
 * 而且再点预览永远显示第一份文档。根因是设置变更与 preview() 都要求状态为 Ready，
 * 但预览会把状态改成 Preview 且从不回退。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorksheetViewModelTest {
    @Test
    fun `template and switches stay editable after returning from preview`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel()
            viewModel.load(PROFILE_ID)
            advanceUntilIdle()
            assertEquals(WorksheetPhase.SETTINGS, viewModel.uiState.value.phase)

            viewModel.preview()
            advanceUntilIdle()
            assertEquals(WorksheetPhase.PREVIEW, viewModel.uiState.value.phase)

            viewModel.dismissPreview()
            assertEquals(WorksheetPhase.SETTINGS, viewModel.uiState.value.phase)

            // 返回后必须还能换模板，且开关继续生效。
            viewModel.selectTemplate(WorksheetTemplate.FULL_LIST)
            viewModel.toggleGrid(false)
            viewModel.toggleAnswers(false)
            viewModel.toggleDirection(WorksheetDirection.EN_TO_ZH)

            val settings = viewModel.uiState.value.settings
            assertEquals(WorksheetTemplate.FULL_LIST, settings.template)
            assertEquals(false, settings.useFourLineGrid)
            assertEquals(false, settings.includeAnswerPage)
            assertTrue(WorksheetDirection.EN_TO_ZH in settings.directions)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `each preview rebuilds pages from the currently selected template`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel()
            viewModel.load(PROFILE_ID)
            advanceUntilIdle()
            // 关掉答案页，让断言只针对题目页的分页（答案页的 questionRows 为空会污染断言）。
            viewModel.toggleAnswers(false)

            viewModel.preview()
            advanceUntilIdle()
            assertEquals(listOf(20, 20, 5), viewModel.uiState.value.pages.map { it.questionRows.size })
            assertTrue(viewModel.uiState.value.pages.all { it.template == WorksheetTemplate.SPELLING_TEST })

            viewModel.dismissPreview()
            viewModel.selectTemplate(WorksheetTemplate.FULL_LIST)
            viewModel.preview()
            advanceUntilIdle()

            // 换模板后必须重算分页：完整词表每页 40 词，与拼写测试的 20 词/页不同。
            assertEquals(listOf(40, 5), viewModel.uiState.value.pages.map { it.questionRows.size })
            assertTrue(viewModel.uiState.value.pages.all { it.template == WorksheetTemplate.FULL_LIST })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `full list preview does not require a dictation direction`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel()
            viewModel.load(PROFILE_ID)
            advanceUntilIdle()
            viewModel.toggleAnswers(false)
            viewModel.selectTemplate(WorksheetTemplate.FULL_LIST)
            viewModel.toggleDirection(WorksheetDirection.ZH_TO_EN)
            assertTrue(viewModel.uiState.value.settings.directions.isEmpty())
            assertTrue(!viewModel.uiState.value.settings.requiresDirection())

            viewModel.preview()
            advanceUntilIdle()

            assertEquals(WorksheetPhase.PREVIEW, viewModel.uiState.value.phase)
            assertEquals(listOf(40, 5), viewModel.uiState.value.pages.map { it.questionRows.size })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `spelling test without any direction stays on settings with a readable reason`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel()
            viewModel.load(PROFILE_ID)
            advanceUntilIdle()
            viewModel.toggleDirection(WorksheetDirection.ZH_TO_EN)

            viewModel.preview()
            advanceUntilIdle()

            assertEquals(WorksheetPhase.SETTINGS, viewModel.uiState.value.phase)
            assertEquals("至少选择一种默写方向后才能预览。", viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `export shares the rendered file and clears the request once consumed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel()
            viewModel.load(PROFILE_ID)
            advanceUntilIdle()
            viewModel.preview()
            advanceUntilIdle()
            val rendered = viewModel.uiState.value.renderedFile
            assertTrue(rendered != null && rendered.exists())

            viewModel.export()
            assertEquals(rendered, viewModel.shareRequest.value)

            viewModel.consumeShareRequest()
            assertEquals(null, viewModel.shareRequest.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel() = WorksheetViewModel(
        buildContent = BuildWorksheetContentUseCase(
            plans = Plans,
            events = Events(CARDS.map(WordCard::cardId)),
            content = Content(CARDS),
        ),
        documentBuilder = WorksheetDocumentBuilder(),
        paginator = WorksheetPaginator(),
        pdfWriter = FakePdfWriter,
    )

    private object Plans : TodayPlanRepository {
        override suspend fun find(profileId: String, localDate: LocalDate): TodayPlanResult = TodayPlanResult.NotFound

        override suspend fun findLatest(profileId: String): TodayPlanResult = TodayPlanResult.Ready(
            TodayPlan(
                planId = PLAN_ID,
                profileId = profileId,
                localDate = LocalDate.of(2026, 9, 23),
                zoneId = "Asia/Shanghai",
                activeWordBookId = "book",
                newTarget = CARDS.size,
                dueTarget = 0,
                newCardIds = CARDS.map(WordCard::cardId),
                dueCardIds = emptyList(),
                ruleVersion = "f1-v1",
                generatedAt = Instant.parse("2026-09-23T00:00:00Z"),
            ),
        )

        override suspend fun saveIfAbsent(plan: TodayPlan): TodayPlanResult = TodayPlanResult.Ready(plan)
    }

    private class Events(private val completed: List<String>) : LearningEventRepository {
        override suspend fun append(event: LearningEvent, nextState: CardReviewState) = AppendEventResult.StorageUnavailable

        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)

        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)

        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)

        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(completed)

        override suspend fun completedCardFeedback(planId: String): RepositoryResult<List<PlanCardFeedback>> =
            RepositoryResult.Success(emptyList())

        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())

        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    }

    private class Content(private val cards: List<WordCard>) : WordCardSource {
        override suspend fun cardIds(wordBookId: String): List<String> = cards.map(WordCard::cardId)

        override suspend fun cards(cardIds: List<String>): List<WordCard> = cards.filter { it.cardId in cardIds }
    }

    private object FakePdfWriter : WorksheetPdfWriter {
        override suspend fun write(pages: List<WorksheetPage>, useFourLineGrid: Boolean): Result<RenderedWorksheet> {
            val file = File.createTempFile("worksheet-test", ".pdf").apply {
                deleteOnExit()
                writeBytes(ByteArray(8))
            }
            return Result.success(RenderedWorksheet(file, pages.size))
        }
    }

    private companion object {
        const val PROFILE_ID = "profile-1"
        const val PLAN_ID = "plan-1"
        /** 45 词：完整词表按 40/页 拆成 [40, 5]，拼写测试与艾宾浩斯按 20/页 拆成 [20, 20, 5]。 */
        val CARDS = (1..45).map { index ->
            WordCard(
                cardId = "card-$index",
                wordBookId = "book",
                lemma = "word$index",
                ipa = "/w$index/",
                partOfSpeech = "n.",
                meaningZh = "释义$index",
            )
        }
    }
}
