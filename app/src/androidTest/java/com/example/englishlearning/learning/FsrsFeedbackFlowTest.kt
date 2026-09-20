package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.FsrsReviewScheduler
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android-level regression coverage for the FSRS feedback integration boundary. */
@RunWith(AndroidJUnit4::class)
class FsrsFeedbackFlowTest {
    private val now = Instant.parse("2026-09-20T04:00:00Z")

    @Test
    fun duplicateEventIdAdvancesOnceAndFsrsMetadataSurvivesReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "fsrs-feedback-${System.nanoTime()}.db"
        val first = openDatabase(context, name)
        try {
            val repository = RoomLearningEventRepository(first, Dispatchers.Unconfined)
            val submit = SubmitCardFeedbackUseCase(
                repository = repository,
                clock = FixedClockProvider(now, ZoneOffset.UTC),
                scheduler = FsrsReviewScheduler(),
            )
            val command = command("event-1", planId = "plan-1")

            val recorded = submit(command)
            val replayed = submit(command)

            assertTrue(recorded is SubmitFeedbackResult.Recorded)
            assertTrue(replayed is SubmitFeedbackResult.AlreadyRecorded)
            assertEquals(recorded.nextReviewAt(), replayed.nextReviewAt())
            assertEquals(1, eventCount(repository, command.planId, command.cardId))
            assertEquals(
                "fsrs-v1",
                (repository.findEvent(command.eventId) as RepositoryResult.Success).value?.algorithmVersion,
            )
        } finally {
            first.close()
        }

        val reopened = openDatabase(context, name)
        try {
            val repository = RoomLearningEventRepository(reopened, Dispatchers.Unconfined)
            val event = (repository.findEvent("event-1") as RepositoryResult.Success).value
            assertEquals("fsrs-v1", event?.algorithmVersion)
            assertEquals(now.plusSeconds(86_400), event?.nextReviewAt)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun completionAndReviewQueriesRemainIsolatedByPlanAndWordBook() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "fsrs-isolation-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        try {
            val repository = RoomLearningEventRepository(database, Dispatchers.Unconfined)
            val submit = SubmitCardFeedbackUseCase(
                repository = repository,
                clock = FixedClockProvider(now, ZoneOffset.UTC),
                scheduler = FsrsReviewScheduler(),
            )
            submit(command("event-plan-1", planId = "plan-1", cardId = "card-1", wordBookId = "book-a"))
            submit(command("event-plan-2", planId = "plan-2", cardId = "card-2", wordBookId = "book-b"))

            assertEquals(setOf("card-1"), completed(repository, "plan-1"))
            assertEquals(setOf("card-2"), completed(repository, "plan-2"))
            assertEquals(emptySet<String>(), completed(repository, "plan-missing"))
            assertEquals(setOf("card-1"), reviewed(repository, "book-a"))
            assertEquals(setOf("card-2"), reviewed(repository, "book-b"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun openDatabase(context: Context, name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()

    private fun command(
        eventId: String,
        planId: String,
        cardId: String = "card-1",
        wordBookId: String = "book-a",
    ) = SubmitFeedbackCommand(eventId, "profile-1", planId, cardId, wordBookId, CardFeedback.Known)

    private suspend fun eventCount(repository: RoomLearningEventRepository, planId: String, cardId: String): Int =
        (repository.countEventsForCard(planId, cardId) as RepositoryResult.Success).value

    private suspend fun completed(repository: RoomLearningEventRepository, planId: String): Set<String> =
        (repository.completedCardIds(planId) as RepositoryResult.Success).value.toSet()

    private suspend fun reviewed(repository: RoomLearningEventRepository, wordBookId: String): Set<String> =
        (repository.reviewedCardIds(wordBookId) as RepositoryResult.Success).value.toSet()

    private fun SubmitFeedbackResult.nextReviewAt(): Instant = when (this) {
        is SubmitFeedbackResult.Recorded -> nextReviewAt
        is SubmitFeedbackResult.AlreadyRecorded -> nextReviewAt
        SubmitFeedbackResult.StorageUnavailable -> error("unexpected storage failure")
    }
}
