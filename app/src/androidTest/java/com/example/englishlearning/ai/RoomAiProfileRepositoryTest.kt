package com.example.englishlearning.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.storage.AppDatabase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomAiProfileRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AiProfileRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = RoomAiProfileRepository(database, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun saveAndFindRoundTripNeverStoresPlaintextKey() = runTest {
        val profile = profile()
        repository.save(profile).getOrThrow()

        assertEquals(profile, repository.find("p1").getOrThrow())
        val columns = database.openHelper.writableDatabase.query("PRAGMA table_info(ai_profiles)")
        val names = buildList { while (columns.moveToNext()) add(columns.getString(1)) }.also { columns.close() }
        assertFalse(names.any { it.contains("key", ignoreCase = true) || it.contains("secretValue", ignoreCase = true) })
    }

    @Test
    fun listAndDeleteAreProfileScoped() = runTest {
        repository.save(profile()).getOrThrow()
        repository.save(profile("p2", "Second")).getOrThrow()
        assertEquals(listOf("p1", "p2"), repository.list().getOrThrow().map { it.profileId })

        repository.delete("p1").getOrThrow()
        assertNull(repository.find("p1").getOrThrow())
        assertEquals("p2", repository.find("p2").getOrThrow()?.profileId)
    }

    private fun profile(id: String = "p1", name: String = "Primary") = AiProfile(
        profileId = id,
        displayName = name,
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "model-a",
        capabilities = setOf(AiCapability.Text, AiCapability.Vision),
        secretReference = SecretReference("ai_profile_$id"),
        advancedParameters = AiAdvancedParameters(maxTokens = 2048),
    )
}
