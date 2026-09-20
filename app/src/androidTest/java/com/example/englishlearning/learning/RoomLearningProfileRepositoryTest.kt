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
 * Room/SQLite behaviour must be proven on a device. The local unit test source set runs on
 * the JUnit 5 platform only — no JUnit 4 runner and no Robolectric registration exist there,
 * so `ApplicationProvider` cannot resolve and the test fails with "No instrumentation
 * registered". This file therefore lives in `androidTest`, like the other Room tests.
 */
@RunWith(AndroidJUnit4::class)
class RoomLearningProfileRepositoryTest {
    @Test
    fun savedActiveWordbookAndTargetSurviveDatabaseReopen(): Unit = runBlocking {
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
        assertEquals(
            RepositoryResult.Success(LearningProfile("default", "primary-school", 10)),
            RoomLearningProfileRepository(reopened, Dispatchers.Unconfined).current("default"),
        )
        reopened.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun closedDatabaseReturnsStableStorageError(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "closed-learning-profile-${System.nanoTime()}.db"
        val database = openDatabase(context, databaseName)
        database.openHelper.writableDatabase // force Room to actually open the connection
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
