package com.example.englishlearning.language

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.dao.InternalSpeechPreferenceDao
import com.example.englishlearning.core.storage.entity.SpeechPreferenceEntity
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.language.domain.SpeechPreference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RoomSpeechPreferenceRepositoryTest {
    @Test
    fun `get returns system tts preference when table is empty`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalSpeechPreferenceDao>()
        every { database.internalSpeechPreferenceDao() } returns dao
        coEvery { dao.find() } returns null

        val preference = RoomSpeechPreferenceRepository(database, Dispatchers.Unconfined).get().getOrThrow()

        assertEquals("device", preference.preferenceId)
        assertEquals(PronunciationEngine.SystemTts, preference.selectedEngine)
        assertEquals(null, preference.openAiProfileId)
        assertEquals(null, preference.miMoProfileId)
    }

    @Test
    fun `save upserts same device preference and preserves profile ids`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalSpeechPreferenceDao>()
        every { database.internalSpeechPreferenceDao() } returns dao
        coEvery { dao.upsert(any()) } returns Unit
        val repository = RoomSpeechPreferenceRepository(database, Dispatchers.Unconfined)

        repository.save(
            SpeechPreference(
                selectedEngine = PronunciationEngine.MiMo,
                openAiProfileId = "openai-profile",
                miMoProfileId = "mimo-profile",
            ),
        ).getOrThrow()

        coVerify {
            dao.upsert(
                SpeechPreferenceEntity(
                    preferenceId = "device",
                    selectedEngine = "MiMo",
                    openAiProfileId = "openai-profile",
                    miMoProfileId = "mimo-profile",
                ),
            )
        }
    }

    @Test
    fun `get round trips selected engine and profile ids`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalSpeechPreferenceDao>()
        every { database.internalSpeechPreferenceDao() } returns dao
        coEvery { dao.find() } returns SpeechPreferenceEntity(
            preferenceId = "device",
            selectedEngine = "MiMo",
            openAiProfileId = "openai-profile",
            miMoProfileId = "mimo-profile",
        )

        val preference = RoomSpeechPreferenceRepository(database, Dispatchers.Unconfined).get().getOrThrow()

        assertEquals(
            SpeechPreference(
                selectedEngine = PronunciationEngine.MiMo,
                openAiProfileId = "openai-profile",
                miMoProfileId = "mimo-profile",
            ),
            preference,
        )
    }
}
