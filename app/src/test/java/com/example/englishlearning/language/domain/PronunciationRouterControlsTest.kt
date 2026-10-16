package com.example.englishlearning.language.domain

import com.example.englishlearning.language.SpeechPreferenceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 发音四控中的暂停/继续/停止在 Router 层的会话路由取证（决策 §2，F3-02）。
 * 主线程约定：Router 的 `activeProvider` 是普通可变字段，speak 与三个控制按钮
 * 都在主线程派发，不加锁（详见 [PronunciationRouter]）。
 */
class PronunciationRouterControlsTest {
    private class FakeProvider(
        val name: String,
        val pauseSupported: Boolean = true,
        private val result: PronunciationResult = PronunciationResult.Played,
    ) : PronunciationProvider {
        val spoken = mutableListOf<String>()
        var pauseCount = 0
        var resumeCount = 0
        var stopCount = 0
        override fun capabilities(): Set<PronunciationCapability> = emptySet()
        override suspend fun speak(text: String): PronunciationResult { spoken += text; return result }
        override fun pausePlayback(): Boolean { pauseCount++; return pauseSupported }
        override fun resumePlayback(): Boolean { resumeCount++; return pauseSupported }
        override fun stopPlayback() { stopCount++ }
    }

    private class FakePrefRepo(private val preference: SpeechPreference) : SpeechPreferenceRepository {
        override suspend fun get(): Result<SpeechPreference> = Result.success(preference)
        override suspend fun save(preference: SpeechPreference): Result<Unit> = Result.success(Unit)
    }

    private fun router(
        kokoro: PronunciationProvider,
        system: PronunciationProvider,
        preference: SpeechPreference = SpeechPreference(),
    ) = PronunciationRouter(kokoro, system, MiMoPronunciationProviderFactory { error("unused") }, FakePrefRepo(preference))

    @Test
    fun `pause without any active session is unsupported`() = runTest {
        val router = router(FakeProvider("kokoro"), FakeProvider("system"))
        assertFalse(router.pausePlayback())
        assertFalse(router.resumePlayback())
        // 无活动会话时 stop 必须是安全空操作：不抛异常，也不因此建立会话（决策 §2）。
        router.stopPlayback()
        assertFalse(router.pausePlayback())
    }

    @Test
    fun `word playback routes controls to kokoro`() = runTest {
        val kokoro = FakeProvider("kokoro")
        val router = router(kokoro, FakeProvider("system"))
        router.speakWord("ability", PronunciationAccent.American)
        assertTrue(router.pausePlayback())
        assertEquals(1, kokoro.pauseCount)
        assertTrue(router.resumePlayback())
        assertEquals(1, kokoro.resumeCount)
        router.stopPlayback()
        assertEquals(1, kokoro.stopCount)
        // 停止后会话清空：再按暂停不得命中旧会话
        assertFalse(router.pausePlayback())
    }

    @Test
    fun `sentence playback routes controls to the routed engine`() = runTest {
        val system = FakeProvider("system")
        val router = router(FakeProvider("kokoro"), system, SpeechPreference(selectedEngine = PronunciationEngine.SystemTts))
        router.speakSentence("An example sentence")
        assertTrue(router.pausePlayback())
        assertEquals(1, system.pauseCount)
    }

    @Test
    fun `engine without pause support reports failure without touching others`() = runTest {
        val system = FakeProvider("system", pauseSupported = false)
        val router = router(FakeProvider("kokoro"), system, SpeechPreference(selectedEngine = PronunciationEngine.SystemTts))
        router.speakSentence("text")
        assertFalse(router.pausePlayback())
        assertFalse(router.resumePlayback())
    }

    @Test
    fun `mimo failure that falls back to system routes controls to system`() = runTest {
        val mimo = FakeProvider("mimo", result = PronunciationResult.Unavailable("offline"))
        val system = FakeProvider("system")
        val router = PronunciationRouter(
            kokoroProvider = FakeProvider("kokoro"),
            systemProvider = system,
            miMoFactory = MiMoPronunciationProviderFactory { mimo },
            preferences = FakePrefRepo(
                SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "mimo-profile"),
            ),
        )

        assertEquals(PronunciationResult.Played, router.speakSentence("An example sentence"))
        assertTrue(router.pausePlayback())
        // 降级后控制必须落在真正出声的 system，而不是悬空的 MiMo 实例。
        assertEquals(1, system.pauseCount)
        assertEquals(0, mimo.pauseCount)
    }

    @Test
    fun `mimo success that plays keeps controls on the mimo instance`() = runTest {
        val mimo = FakeProvider("mimo", result = PronunciationResult.Played)
        val system = FakeProvider("system")
        val router = PronunciationRouter(
            kokoroProvider = FakeProvider("kokoro"),
            systemProvider = system,
            miMoFactory = MiMoPronunciationProviderFactory { mimo },
            preferences = FakePrefRepo(
                SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "mimo-profile"),
            ),
        )

        // 远程真的出声：会话句柄必须是 MiMoFactory 造出的这个实例，三个控制都不得漏到 system。
        assertEquals(PronunciationResult.Played, router.speakSentence("An example sentence"))
        assertTrue(router.pausePlayback())
        assertEquals(1, mimo.pauseCount)
        assertEquals(0, system.pauseCount)
        assertTrue(router.resumePlayback())
        assertEquals(1, mimo.resumeCount)
        assertEquals(0, system.resumeCount)
        router.stopPlayback()
        assertEquals(1, mimo.stopCount)
        assertEquals(0, system.stopCount)
        // stop 之后会话清空，同一批控件不得继续命中那个已出声的 MiMo 实例。
        assertFalse(router.pausePlayback())
        assertEquals(1, mimo.pauseCount)
    }

    @Test
    fun `providers without overrides report unsupported controls by default`() {
        val bare = object : PronunciationProvider {
            override fun capabilities(): Set<PronunciationCapability> = emptySet()
            override suspend fun speak(text: String): PronunciationResult = PronunciationResult.Played
        }

        assertFalse(bare.pausePlayback())
        assertFalse(bare.resumePlayback())
        bare.stopPlayback()
    }
}
