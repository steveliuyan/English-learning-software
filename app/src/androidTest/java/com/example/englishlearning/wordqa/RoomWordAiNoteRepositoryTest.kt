package com.example.englishlearning.wordqa

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 词 AI 笔记仓储契约：按 profileId + lemma 检索、跨 profile 隔离、按时间降序。
 * 全程使用 in-memory 测试库，不触碰真机用户库。
 */
@RunWith(AndroidJUnit4::class)
class RoomWordAiNoteRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: WordAiNoteRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomWordAiNoteRepository(database, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = database.close()

    private fun note(
        noteId: String,
        profileId: String = "default",
        lemma: String = "apple",
        createdAt: Long = 1L,
    ) = WordAiNote(
        noteId = noteId,
        profileId = profileId,
        lemma = lemma,
        kind = WordQaKind.Sentence,
        answer = "I ate an apple.",
        createdAtEpochMillis = createdAt,
    )

    @Test
    fun savedNotesRoundTripWithKindDecoded() = runTest {
        repository.save(note("n1", lemma = "apple")).getOrThrow()

        val loaded = repository.list("default", "apple").getOrThrow()

        assertEquals(1, loaded.size)
        assertEquals(WordQaKind.Sentence, loaded.single().kind)
        assertEquals("I ate an apple.", loaded.single().answer)
    }

    @Test
    fun notesFromAnotherProfileAreInvisible() = runTest {
        repository.save(note("n1", profileId = "default")).getOrThrow()
        repository.save(note("n2", profileId = "someone-else", lemma = "apple")).getOrThrow()

        // 必须断言内容而不是数量：size 恰好相等时，拿错行的缺陷会被绿灯掩盖。
        assertEquals(listOf("n1"), repository.list("default", "apple").getOrThrow().map { it.noteId })
        assertEquals(listOf("n2"), repository.list("someone-else", "apple").getOrThrow().map { it.noteId })
    }

    @Test
    fun notesAreFilteredByLemmaAndNewestFirst() = runTest {
        repository.save(note("n1", lemma = "apple", createdAt = 10L)).getOrThrow()
        repository.save(note("n2", lemma = "apple", createdAt = 30L)).getOrThrow()
        repository.save(note("n3", lemma = "apple", createdAt = 20L)).getOrThrow()
        repository.save(note("n4", lemma = "brief", createdAt = 40L)).getOrThrow()

        val loaded = repository.list("default", "apple").getOrThrow()

        assertEquals(listOf("n2", "n3", "n1"), loaded.map { it.noteId })
    }
}
