package com.example.englishlearning.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.domain.AiPreference
import com.example.englishlearning.core.storage.AppDatabase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomAiPreferenceRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AiPreferenceRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomAiPreferenceRepository(database, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun emptyStoreReturnsAnUnselectedDevicePreference() = runTest {
        assertEquals(AiPreference(), repository.get().getOrThrow())
    }

    @Test
    fun saveAndGetRoundTripOnlyTheDefaultProfileId() = runTest {
        repository.save(AiPreference(defaultTextProfileId = "p-text")).getOrThrow()
        assertEquals("p-text", repository.get().getOrThrow().defaultTextProfileId)
    }

    @Test
    fun saveAndGetRoundTripBothDefaultIds() = runTest {
        repository.save(AiPreference(defaultTextProfileId = "p-text", defaultImageProfileId = "p-img")).getOrThrow()
        val stored = repository.get().getOrThrow()
        assertEquals("p-text", stored.defaultTextProfileId)
        assertEquals("p-img", stored.defaultImageProfileId)
    }

    @Test
    fun clearingTheImageDefaultKeepsTheTextDefault() = runTest {
        repository.save(AiPreference(defaultTextProfileId = "p-text", defaultImageProfileId = "p-img")).getOrThrow()
        repository.save(AiPreference(defaultTextProfileId = "p-text")).getOrThrow()
        val stored = repository.get().getOrThrow()
        assertEquals("p-text", stored.defaultTextProfileId)
        assertEquals(null, stored.defaultImageProfileId)
    }
}
