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
        assertEquals(true, preference.autoSpeakOnCardReady)
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
    fun `get normalizes legacy openai selection to mimo when mimo profile exists`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalSpeechPreferenceDao>()
        every { database.internalSpeechPreferenceDao() } returns dao
        coEvery { dao.find() } returns SpeechPreferenceEntity(
            preferenceId = "device",
            selectedEngine = "OpenAi",
            openAiProfileId = "legacy-openai",
            miMoProfileId = "mimo-profile",
        )

        val preference = RoomSpeechPreferenceRepository(database, Dispatchers.Unconfined).get().getOrThrow()

        assertEquals(PronunciationEngine.MiMo, preference.selectedEngine)
        assertEquals("legacy-openai", preference.openAiProfileId)
        assertEquals("mimo-profile", preference.miMoProfileId)
    }

    @Test
    fun `get normalizes legacy openai selection to system when mimo profile is absent`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalSpeechPreferenceDao>()
        every { database.internalSpeechPreferenceDao() } returns dao
        coEvery { dao.find() } returns SpeechPreferenceEntity(
            preferenceId = "device",
            selectedEngine = "OpenAi",
            openAiProfileId = "legacy-openai",
            miMoProfileId = null,
        )

        val preference = RoomSpeechPreferenceRepository(database, Dispatchers.Unconfined).get().getOrThrow()

        assertEquals(PronunciationEngine.SystemTts, preference.selectedEngine)
        assertEquals("legacy-openai", preference.openAiProfileId)
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

    /** D1：自动发音开关必须原样落盘（false 不能被吞成默认 true）。 */
    @Test
    fun `save persists the auto speak toggle`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalSpeechPreferenceDao>()
        every { database.internalSpeechPreferenceDao() } returns dao
        coEvery { dao.upsert(any()) } returns Unit

        RoomSpeechPreferenceRepository(database, Dispatchers.Unconfined)
            .save(SpeechPreference(autoSpeakOnCardReady = false)).getOrThrow()

        coVerify {
            dao.upsert(
                SpeechPreferenceEntity(
                    preferenceId = "device",
                    selectedEngine = "SystemTts",
                    openAiProfileId = null,
                    miMoProfileId = null,
                    autoSpeakOnCardReady = false,
                ),
            )
        }
    }

    /** D1：关闭状态读回来仍是关闭——迁移回填只作用于历史空值，不覆盖显式选择。 */
    @Test
    fun `get round trips the auto speak toggle`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalSpeechPreferenceDao>()
        every { database.internalSpeechPreferenceDao() } returns dao
        coEvery { dao.find() } returns SpeechPreferenceEntity(
            preferenceId = "device",
            selectedEngine = "SystemTts",
            openAiProfileId = null,
            miMoProfileId = null,
            autoSpeakOnCardReady = false,
        )

        val preference = RoomSpeechPreferenceRepository(database, Dispatchers.Unconfined).get().getOrThrow()

        assertEquals(false, preference.autoSpeakOnCardReady)
    }
}
