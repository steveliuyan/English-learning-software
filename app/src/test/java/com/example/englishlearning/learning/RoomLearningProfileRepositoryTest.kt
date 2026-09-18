package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.englishlearning.core.storage.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RoomLearningProfileRepositoryTest {
    @Test
    fun `saved active wordbook and target survive database reopen`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "learning-profile-${System.nanoTime()}.db"
        val database = openDatabase(context, databaseName)
        val repository = RoomLearningProfileRepository(database, Dispatchers.Unconfined)
        val wordBook = WordBook("primary-school", "小学", "基础", 0, "v1", "ngsl-nawl-1.2")

        assertEquals(RepositoryResult.Success(Unit), repository.upsertWordBook(wordBook))
        assertEquals(
            RepositoryResult.Success(Unit),
            repository.save(LearningProfile("default", "primary-school", 10)),
        )
        database.close()

        val reopened = openDatabase(context, databaseName)
        val persisted = RoomLearningProfileRepository(reopened, Dispatchers.Unconfined).current("default")

        assertEquals(
            RepositoryResult.Success(LearningProfile("default", "primary-school", 10)),
            persisted,
        )
        reopened.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `closed database returns stable storage error`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "closed-learning-profile-${System.nanoTime()}.db"
        val database = openDatabase(context, databaseName)
        val repository = RoomLearningProfileRepository(database, Dispatchers.Unconfined)
        database.close()

        assertEquals(
            RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable),
            repository.current("default"),
        )
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase(context: Context, name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
}
