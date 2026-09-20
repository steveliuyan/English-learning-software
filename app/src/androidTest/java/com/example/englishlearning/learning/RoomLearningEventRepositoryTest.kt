package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.ReviewFeedback
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Storage-level proof of the F1-03 idempotency rule: `eventId` is the primary key, so a
 * replayed submission is rejected by the database itself and the derived card state is
 * left untouched, instead of being guarded by a racy read-then-write check.
 *
 * This must run on a device: Room/SQLite behaviour is not reproducible in the local unit
 * test source set, which runs on the JUnit 5 platform only — no JUnit 4 runner and no
 * Robolectric registration exist there, so `ApplicationProvider` cannot resolve.
 */
@RunWith(AndroidJUnit4::class)
class RoomLearningEventRepositoryTest {
    private val earliest: Instant = Instant.parse("2026-09-18T08:00:00Z")
    private val earlier: Instant = Instant.parse("2026-09-19T08:00:00Z")
    private val later: Instant = Instant.parse("2026-09-22T08:00:00Z")
    private val now: Instant = Instant.parse("2026-09-20T08:00:00Z")

    @Test
    fun duplicateEventIdIsRejectedAndKeepsDerivedState() = runBlocking {
        withRepository { repository ->
            assertEquals(
                AppendEventResult.Appended(duplicate = false),
                repository.append(event("event-1", "card-1", earlier), state("card-1", earlier)),
            )
            assertEquals(
                AppendEventResult.Appended(duplicate = true),
                repository.append(event("event-1", "card-1", later), state("card-1", later)),
            )

            assertEquals(earlier, eventNextReview(repository, "event-1"))
            assertEquals(1, eventCount(repository))
            assertEquals(earlier, stateNextReview(repository, "card-1"))
        }
    }

    @Test
    fun planQueriesSeparateCompletedReviewedAndDueCards() = runBlocking {
        withRepository { repository ->
            repository.append(event("event-1", "card-1", later), state("card-1", later))
            repository.append(event("event-2", "card-2", earlier), state("card-2", earlier))
            repository.append(event("event-3", "card-3", earliest), state("card-3", earliest))

            assertEquals(
                "every recorded plan item counts as complete",
                setOf("card-1", "card-2", "card-3"),
                completedCardIds(repository),
            )
            assertEquals(
                "reviewed cards must stop being new",
                setOf("card-1", "card-2", "card-3"),
                reviewedCardIds(repository),
            )
            assertEquals(
                "only cards due at or before now are returned, earliest first",
                listOf("card-3", "card-2"),
                dueCardIds(repository),
            )
        }
    }

    @Test
    fun aCardIsNotDueBeforeItsScheduledInstant() = runBlocking {
        withRepository { repository ->
            repository.append(event("event-1", "card-1", later), state("card-1", later))

            assertEquals(emptyList<String>(), dueCardIds(repository))
            assertEquals(emptySet<String>(), completedCardIds(repository, planId = "another-plan"))
        }
    }

    @Test
    fun recordedEventAndDerivedStateSurviveAReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "learning-event-${System.nanoTime()}.db"

        val first = openDatabase(context, name)
        try {
            RoomLearningEventRepository(first, Dispatchers.Unconfined)
                .append(event("event-1", "card-1", later), state("card-1", later))
        } finally {
            first.close()
        }

        val reopened = openDatabase(context, name)
        try {
            val repository = RoomLearningEventRepository(reopened, Dispatchers.Unconfined)
            assertEquals(later, eventNextReview(repository, "event-1"))
            assertEquals(ReviewFeedback.Good, stateLastFeedback(repository, "card-1"))
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun closedDatabaseReturnsStableStorageUnavailableForAppend(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "closed-learning-event-append-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        database.openHelper.writableDatabase // force Room to actually open the connection
        val repository = RoomLearningEventRepository(database, Dispatchers.Unconfined)
        database.close()

        assertEquals(
            AppendEventResult.StorageUnavailable,
            repository.append(event("event-1", "card-1", later), state("card-1", later)),
        )
        context.deleteDatabase(name)
    }

    @Test
    fun closedDatabaseReturnsStableStorageUnavailableForReads(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "closed-learning-event-reads-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        database.openHelper.writableDatabase // force Room to actually open the connection
        val repository = RoomLearningEventRepository(database, Dispatchers.Unconfined)
        database.close()

        assertEquals(
            RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable),
            repository.findEvent("event-1"),
        )
        assertEquals(
            RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable),
            repository.completedCardIds("plan-1"),
        )
        context.deleteDatabase(name)
    }

    private suspend fun withRepository(block: suspend (RoomLearningEventRepository) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "learning-event-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        try {
            block(RoomLearningEventRepository(database, Dispatchers.Unconfined))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun eventNextReview(repository: RoomLearningEventRepository, eventId: String): Instant? =
        when (val result = repository.findEvent(eventId)) {
            is RepositoryResult.Success -> result.value?.nextReviewAt
            is RepositoryResult.Failure -> throw AssertionError("findEvent failed: ${result.error}")
        }

    private suspend fun eventCount(repository: RoomLearningEventRepository): Int =
        when (val result = repository.countEventsForCard("plan-1", "card-1")) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> throw AssertionError("countEventsForCard failed: ${result.error}")
        }

    private suspend fun stateNextReview(repository: RoomLearningEventRepository, cardId: String): Instant? =
        when (val result = repository.findCardState(cardId)) {
            is RepositoryResult.Success -> result.value?.nextReviewAt
            is RepositoryResult.Failure -> throw AssertionError("findCardState failed: ${result.error}")
        }

    private suspend fun stateLastFeedback(repository: RoomLearningEventRepository, cardId: String): ReviewFeedback? =
        when (val result = repository.findCardState(cardId)) {
            is RepositoryResult.Success -> result.value?.lastFeedback
            is RepositoryResult.Failure -> throw AssertionError("findCardState failed: ${result.error}")
        }

    private suspend fun completedCardIds(
        repository: RoomLearningEventRepository,
        planId: String = "plan-1",
    ): Set<String> =
        when (val result = repository.completedCardIds(planId)) {
            is RepositoryResult.Success -> result.value.toSet()
            is RepositoryResult.Failure -> throw AssertionError("completedCardIds failed: ${result.error}")
        }

    private suspend fun reviewedCardIds(
        repository: RoomLearningEventRepository,
        wordBookId: String = "cet4",
    ): Set<String> =
        when (val result = repository.reviewedCardIds(wordBookId)) {
            is RepositoryResult.Success -> result.value.toSet()
            is RepositoryResult.Failure -> throw AssertionError("reviewedCardIds failed: ${result.error}")
        }

    private suspend fun dueCardIds(
        repository: RoomLearningEventRepository,
        wordBookId: String = "cet4",
        at: Instant = now,
    ): List<String> =
        when (val result = repository.dueCardIds(wordBookId, at)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> throw AssertionError("dueCardIds failed: ${result.error}")
        }

    private fun openDatabase(context: Context, name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()

    private fun event(eventId: String, cardId: String, nextReviewAt: Instant) =
        LearningEvent(
            eventId = eventId,
            profileId = "profile-1",
            planId = "plan-1",
            cardId = cardId,
            wordBookId = "cet4",
            feedback = ReviewFeedback.Good,
            occurredAt = earlier,
            algorithmVersion = "v1",
            paramsVersion = "v1",
            dueBefore = null,
            nextReviewAt = nextReviewAt,
        )

    private fun state(cardId: String, nextReviewAt: Instant) =
        CardReviewState(
            cardId = cardId,
            wordBookId = "cet4",
            lastFeedback = ReviewFeedback.Good,
            lastReviewedAt = earlier,
            nextReviewAt = nextReviewAt,
        )
}
