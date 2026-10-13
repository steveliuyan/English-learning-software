package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.PronunciationAccent
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KokoroPronunciationProviderTest {
    @Test
    fun `american and british use different voice profiles`() = runTest {
        val engine = RecordingKokoroEngine(ready = false)
        val player = RecordingAudioPlayer()
        val provider = KokoroPronunciationProvider(engine, player)

        provider.speak("schedule", PronunciationAccent.American)
        provider.speak("schedule", PronunciationAccent.British)

        assertEquals(1, engine.prepareCalls)
        assertEquals(
            listOf(PronunciationAccent.American, PronunciationAccent.British),
            engine.accents,
        )
        assertEquals(2, player.played.size)
    }

    @Test
    fun `prepare failure returns unavailable and does not synthesize`() = runTest {
        val failure = IllegalStateException("missing model")
        val engine = RecordingKokoroEngine(prepareFailure = failure)

        val result = KokoroPronunciationProvider(engine, RecordingAudioPlayer()).speak("hello")

        assertEquals(PronunciationResult.Unavailable("Kokoro engine unavailable"), result)
        assertEquals(1, engine.prepareCalls)
        assertTrue(engine.texts.isEmpty())
    }

    @Test
    fun `synthesis cancellation leaves shared engine reusable for the next word`() = runTest {
        val engine = RecordingKokoroEngine(synthesisFailure = kotlinx.coroutines.CancellationException("cancel"))
        val player = RecordingAudioPlayer()
        val provider = KokoroPronunciationProvider(engine, player)

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            provider.speakWord("hello")
        }
        engine.synthesisFailure = null

        assertEquals(PronunciationResult.Played, provider.speakWord("schedule", PronunciationAccent.British))
        assertEquals(listOf("hello", "schedule"), engine.texts)
        assertEquals(1, player.played.size)
        assertEquals(0, engine.closeCalls)
    }

    @Test
    fun `playback cancellation clears bytes and allows the next word on the same provider`() = runTest {
        val engine = RecordingKokoroEngine()
        var cancelPlayback = true
        val player = object : AudioPlayer {
            override suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult {
                if (cancelPlayback) throw kotlinx.coroutines.CancellationException("cancel playback")
                return AudioPlaybackResult.Played
            }
        }
        val provider = KokoroPronunciationProvider(engine, player)

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            provider.speakWord("hello")
        }
        val cancelledAudio = requireNotNull(engine.lastAudio)
        cancelPlayback = false

        // 首先独立保护后续朗读；缓冲清零另有专门测试，避免先失败遮蔽复用缺陷。
        assertEquals(PronunciationResult.Played, provider.speakWord("schedule", PronunciationAccent.British))
        assertTrue(cancelledAudio.all { it == 0.toByte() })
        assertEquals(0, engine.closeCalls)
    }

    @Test
    fun `forced word regeneration synthesizes fresh audio for both accents without a cache`() = runTest {
        for (accent in PronunciationAccent.entries) {
            val engine = RecordingKokoroEngine()
            val player = RecordingAudioPlayer()
            val provider: com.example.englishlearning.language.domain.PronunciationProvider =
                KokoroPronunciationProvider(engine, player)

            assertEquals(PronunciationResult.Played, provider.speakWord("schedule", accent))
            val firstAudio = engine.lastAudio
            assertEquals(PronunciationResult.Played, provider.speakWord("schedule", accent, forceRegenerate = true))

            assertEquals(listOf("schedule", "schedule"), engine.texts)
            assertEquals(listOf(accent, accent), engine.accents)
            assertEquals(2, player.played.size)
            assertTrue(firstAudio !== engine.lastAudio)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `replacement word waits for cancelled inference cleanup before checking readiness`() = runTest {
        val inferenceStarted = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        val engineMutex = Mutex()
        var ready = true
        val engine = object : KokoroEngine {
            override fun isReady() = ready
            override suspend fun prepare(): Result<Unit> = engineMutex.withLock {
                ready = true
                Result.success(Unit)
            }
            override suspend fun synthesize(text: String, accent: PronunciationAccent): Result<ByteArray> = engineMutex.withLock {
                if (!ready) return@withLock Result.failure(IllegalStateException("runtime released"))
                if (text == "first") {
                    inferenceStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            cleanupStarted.complete(Unit)
                            allowCleanup.await()
                            ready = false
                        }
                    }
                }
                Result.success(byteArrayOf(1, 2))
            }
            override suspend fun close() { ready = false }
        }
        val provider = KokoroPronunciationProvider(engine, RecordingAudioPlayer())
        val first = launch { provider.speakWord("first") }
        inferenceStarted.await()
        first.cancel()
        cleanupStarted.await()
        val replacement = async { provider.speakWord("schedule", PronunciationAccent.British) }
        // 让第二请求在旧推理仍清理且 ready=true 时进入，确定性复现 TOCTOU。
        runCurrent()
        allowCleanup.complete(Unit)
        first.join()

        assertEquals(PronunciationResult.Played, replacement.await())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `cancelled dispatcher handoff zeroes WAV that was never delivered`() = runTest {
        val worker = ManualDispatcher()
        val caller = ManualDispatcher()
        val audio = byteArrayOf(82, 73, 70, 70, 1, 2)
        var generated = false
        val request = async(context = caller, start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            withOwnedAudio(worker) {
                generated = true
                Result.success(audio)
            }
        }
        worker.runNext()
        assertTrue(generated, "WAV must be generated before return dispatch is cancelled")
        assertTrue(!request.isCompleted, "caller must not have received audio yet")
        request.cancel()
        caller.runUntilIdle()
        request.join()

        assertTrue(audio.all { it == 0.toByte() }, "undelivered WAV must be zeroed")
    }

    private class ManualDispatcher : kotlinx.coroutines.CoroutineDispatcher() {
        private val queue = java.util.ArrayDeque<Runnable>()

        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runNext() {
            check(queue.isNotEmpty()) { "expected a queued coroutine continuation" }
            queue.removeFirst().run()
        }

        fun runUntilIdle() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    @Test
    fun `successful dispatcher handoff preserves WAV until caller owns it`() = runTest {
        val audio = byteArrayOf(82, 73, 70, 70, 1, 2)
        val result = withOwnedAudio(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)) {
            Result.success(audio)
        }
        assertTrue(result.getOrThrow() === audio)
        assertEquals(listOf<Byte>(82, 73, 70, 70, 1, 2), audio.toList())
    }

    @Test
    fun `default speak forwards american accent`() = runTest {
        val engine = RecordingKokoroEngine()
        val provider = KokoroPronunciationProvider(engine, RecordingAudioPlayer())

        assertIs<PronunciationResult.Played>(provider.speak("schedule"))
        assertEquals(listOf(PronunciationAccent.American), engine.accents)
    }

    @Test
    fun `blank text is unavailable without invoking engine`() = runTest {
        val engine = RecordingKokoroEngine()

        val result = KokoroPronunciationProvider(engine, RecordingAudioPlayer()).speak("  ")

        assertEquals(PronunciationResult.Unavailable("blank text"), result)
        assertTrue(engine.texts.isEmpty())
    }

    @Test
    fun `engine not ready is unavailable without synthesis`() = runTest {
        val engine = RecordingKokoroEngine(prepareFailure = IllegalStateException("not ready"))

        val result = KokoroPronunciationProvider(engine, RecordingAudioPlayer()).speak("hello")

        assertEquals(PronunciationResult.Unavailable("Kokoro engine unavailable"), result)
        assertTrue(engine.texts.isEmpty())
    }

    @Test
    fun `engine exception returns failed`() = runTest {
        val error = IllegalStateException("synthesis failed")
        val engine = RecordingKokoroEngine(error = error)

        val result = KokoroPronunciationProvider(engine, RecordingAudioPlayer()).speak("hello")

        assertIs<PronunciationResult.Failed>(result)
        assertEquals(error, result.cause)
    }

    @Test
    fun `playback failure returns failed and clears synthesized bytes`() = runTest {
        val engine = RecordingKokoroEngine(audio = byteArrayOf(1, 2, 3))
        val player = RecordingAudioPlayer(AudioPlaybackResult.Failed)

        val result = KokoroPronunciationProvider(engine, player).speak("hello")

        assertIs<PronunciationResult.Failed>(result)
        assertEquals("wav", player.formats.single())
        assertTrue(engine.lastAudio!!.all { it == 0.toByte() })
    }

    @Test
    fun `successful synthesis plays wav and clears temporary bytes`() = runTest {
        val engine = RecordingKokoroEngine(audio = byteArrayOf(4, 5))
        val player = RecordingAudioPlayer(AudioPlaybackResult.Played)

        val result = KokoroPronunciationProvider(engine, player).speak("hello", PronunciationAccent.British)

        assertIs<PronunciationResult.Played>(result)
        assertEquals(listOf(byteArrayOf(4, 5).toList()), player.played)
        assertEquals("wav", player.formats.single())
        assertTrue(engine.lastAudio!!.all { it == 0.toByte() })
    }

    @Test
    fun `capabilities expose on device audio`() {
        assertEquals(
            setOf(PronunciationCapability.OnDeviceAudio),
            KokoroPronunciationProvider(RecordingKokoroEngine(), RecordingAudioPlayer()).capabilities(),
        )
    }

    // ---------------------------------------------------------------- 合成音频缓存（2026-10-13 真机反馈：重复点击重新推理太慢）

    @Test
    fun `cached audio plays without touching the engine at all`() = runTest {
        val engine = RecordingKokoroEngine()
        val player = RecordingAudioPlayer()
        val cache = FakeSynthAudioCache()
        cache.stored["hello" to PronunciationAccent.American] = byteArrayOf(9, 8, 7)
        val provider = KokoroPronunciationProvider(engine, player, cache)

        assertEquals(PronunciationResult.Played, provider.speakWord("hello"))

        assertEquals(0, engine.prepareCalls)
        assertTrue(engine.texts.isEmpty())
        assertEquals(listOf(byteArrayOf(9, 8, 7).toList()), player.played)
    }

    @Test
    fun `successful synthesis is stored so the next tap skips inference`() = runTest {
        val engine = RecordingKokoroEngine(audio = byteArrayOf(4, 5))
        val player = RecordingAudioPlayer()
        val cache = FakeSynthAudioCache()
        val provider = KokoroPronunciationProvider(engine, player, cache)

        assertEquals(PronunciationResult.Played, provider.speakWord("hello", PronunciationAccent.American))
        assertEquals(PronunciationResult.Played, provider.speakWord("hello", PronunciationAccent.American))

        assertEquals(listOf("hello"), engine.texts)
        assertEquals(2, player.played.size)
        assertEquals(listOf(byteArrayOf(4, 5).toList(), byteArrayOf(4, 5).toList()), player.played)
    }

    @Test
    fun `cache is keyed by accent`() = runTest {
        val engine = RecordingKokoroEngine(audio = byteArrayOf(1, 2))
        val cache = FakeSynthAudioCache()
        cache.stored["hello" to PronunciationAccent.American] = byteArrayOf(9)
        val provider = KokoroPronunciationProvider(engine, RecordingAudioPlayer(), cache)

        assertEquals(PronunciationResult.Played, provider.speakWord("hello", PronunciationAccent.British))

        assertEquals(listOf("hello"), engine.texts)
        assertEquals(PronunciationAccent.British, engine.accents.single())
    }

    @Test
    fun `forced regeneration bypasses the cache and refreshes it`() = runTest {
        val engine = RecordingKokoroEngine(audio = byteArrayOf(1, 2))
        val cache = FakeSynthAudioCache()
        val provider = KokoroPronunciationProvider(engine, RecordingAudioPlayer(), cache)

        provider.speakWord("hello", PronunciationAccent.American)
        provider.speakWord("hello", PronunciationAccent.American, forceRegenerate = true)

        assertEquals(listOf("hello", "hello"), engine.texts)
    }

    @Test
    fun `a failing cache read degrades to synthesis instead of failing playback`() = runTest {
        val engine = RecordingKokoroEngine(audio = byteArrayOf(4, 5))
        val player = RecordingAudioPlayer()
        val cache = FakeSynthAudioCache().apply { throwOnRead = true }
        val provider = KokoroPronunciationProvider(engine, player, cache)

        assertEquals(PronunciationResult.Played, provider.speakWord("hello"))

        assertEquals(listOf("hello"), engine.texts)
        assertEquals(1, player.played.size)
    }

    private class FakeSynthAudioCache : SynthesizedAudioCache {
        val stored = mutableMapOf<Pair<String, PronunciationAccent>, ByteArray>()
        var throwOnRead = false

        override suspend fun read(text: String, accent: PronunciationAccent): ByteArray? {
            if (throwOnRead) throw IllegalStateException("cache read boom")
            return stored[text to accent]?.copyOf()
        }

        override suspend fun write(text: String, accent: PronunciationAccent, wav: ByteArray) {
            stored[text to accent] = wav.copyOf()
        }
    }

    private class RecordingKokoroEngine(
        private var ready: Boolean = true,
        private val audio: ByteArray = byteArrayOf(1, 2),
        private val error: Throwable? = null,
        private val prepareFailure: Throwable? = null,
        var synthesisFailure: Throwable? = null,
    ) : KokoroEngine {
        val accents = mutableListOf<PronunciationAccent>()
        val texts = mutableListOf<String>()
        var lastAudio: ByteArray? = null
        var prepareCalls = 0
        var closeCalls = 0
        private var closed = false

        override suspend fun prepare(): Result<Unit> {
            prepareCalls++
            if (closed) return Result.failure(IllegalStateException("engine permanently closed"))
            return prepareFailure?.let { Result.failure(it) } ?: Result.success(Unit).also { ready = true }
        }

        override fun isReady(): Boolean = ready && prepareFailure == null

        override suspend fun close() {
            closeCalls++
            closed = true
            ready = false
        }

        override suspend fun synthesize(text: String, accent: PronunciationAccent): Result<ByteArray> {
            texts += text
            accents += accent
            synthesisFailure?.let { throw it }
            error?.let { return Result.failure(it) }
            return Result.success(audio.copyOf().also { lastAudio = it })
        }
    }

    private class RecordingAudioPlayer(
        private val outcome: AudioPlaybackResult = AudioPlaybackResult.Played,
    ) : AudioPlayer {
        val played = mutableListOf<List<Byte>>()
        val formats = mutableListOf<String>()

        override suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult {
            played += bytes.toList()
            formats += format
            return outcome
        }
    }
}
