package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F4-01 决策⑤错词派生（androidTest，真库 SQL）：mockk 钉不了派生口径本身，这里钉三件事——
 * 「最近一次反馈为 Again 才在列」（Again→Good 挽回后消失）、
 * 排序用 `card_review_states.nextReviewAtEpochMillis`、无状态行退回事件时间、
 * 以及 profile 隔离（learning_events 的 profileId 是唯一按档案可审计来源）。
 */
@RunWith(AndroidJUnit4::class)
class RoomWrongWordsDerivationTest {
    @Test
    fun wrongWordsListsLatestAgainCardsOrderedByDueAndIsolatedByProfile(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "wrong-words-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
        try {
            val db = database.openHelper.writableDatabase
            // cat：Again 后又 Again（最近仍是 Again）；有复习状态行 nextReview=80。
            event(db, "e1", "p1", "imported:cat", 100L, "Again")
            event(db, "e2", "p1", "imported:cat", 500L, "Again")
            state(db, "imported:cat", 80L, "Again")
            // run：Again 后挽回为 Good → 不在列，哪怕它的状态行到期更早/更晚。
            event(db, "e3", "p1", "imported:run", 200L, "Again")
            event(db, "e4", "p1", "imported:run", 300L, "Good")
            state(db, "imported:run", 999L, "Good")
            // big：最近是 Again，状态行最早到期 → 排第一。
            event(db, "e5", "p1", "imported:big", 400L, "Again")
            state(db, "imported:big", 50L, "Again")
            // dog：最近是 Again 且无状态行 → 用事件时间 70 排序（big=50 之后、cat=80 之前）。
            event(db, "e6", "p1", "imported:dog", 70L, "Again")
            // 另一档案的 Again 不得混入 p1。
            event(db, "e7", "p2", "imported:fox", 600L, "Again")

            val repository = RoomWrongWordsRepository(database, Dispatchers.Unconfined)

            assertEquals(
                "按 COALESCE(nextReviewAt, lastAgainAt) 升序；挽回的 run 缺席",
                listOf("imported:big", "imported:dog", "imported:cat"),
                repository.wrongWords("p1").getOrThrow().map { it.cardId },
            )
            assertEquals(
                listOf("imported:fox"),
                repository.wrongWords("p2").getOrThrow().map { it.cardId },
            )
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(name)
        }
    }

    private fun event(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        eventId: String,
        profileId: String,
        cardId: String,
        occurredAt: Long,
        feedback: String,
    ) {
        db.execSQL(
            "INSERT INTO learning_events (eventId, profileId, planId, cardId, wordBookId, feedback, " +
                "occurredAtEpochMillis, algorithmVersion, paramsVersion, dueBeforeEpochMillis, nextReviewAtEpochMillis) " +
                "VALUES (?, ?, 'plan-1', ?, 'imported', ?, ?, 'fsrs-1', 'params-1', NULL, ?)",
            arrayOf<Any>(eventId, profileId, cardId, feedback, occurredAt, occurredAt + 1_000L),
        )
    }

    private fun state(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        cardId: String,
        nextReviewAt: Long,
        lastFeedback: String,
    ) {
        db.execSQL(
            "INSERT INTO card_review_states (cardId, wordBookId, lastFeedback, lastReviewedAtEpochMillis, nextReviewAtEpochMillis) " +
                "VALUES (?, 'imported', ?, ?, ?)",
            arrayOf<Any>(cardId, lastFeedback, nextReviewAt - 1L, nextReviewAt),
        )
    }
}
