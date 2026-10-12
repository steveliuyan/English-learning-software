package com.example.englishlearning.language

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.englishlearning.language.domain.PronunciationAccent
import com.example.englishlearning.language.domain.PronunciationResult
import com.example.englishlearning.language.infrastructure.AndroidAudioPlayer
import com.example.englishlearning.language.infrastructure.AudioPlaybackResult
import com.example.englishlearning.language.infrastructure.AudioPlayer
import com.example.englishlearning.language.infrastructure.KokoroAndroidEngine
import com.example.englishlearning.language.infrastructure.KokoroEngine
import com.example.englishlearning.language.infrastructure.KokoroPronunciationProvider
import com.example.englishlearning.language.infrastructure.KokoroRuntime
import com.example.englishlearning.language.infrastructure.KokoroRuntimeFactory
import com.example.englishlearning.language.infrastructure.OrtKokoroRuntimeFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Production APK 中随附的 Kokoro 端到端取证。
 * 与旧 spike 测试隔离：不读取外部测试资产，不写入旧试听目录，只通过 logcat 输出摘要。
 */
class KokoroProductionPronunciationTest {
    @Test
    fun productionRuntimeUsesAccentSpecificFrontendsAndClosesRuntime() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recordingFactory = RecordingRuntimeFactory(OrtKokoroRuntimeFactory())
        val engine = KokoroAndroidEngine(context, factory = recordingFactory)
        try {
            assertTrue(engine.prepare().isSuccess)
            engine.synthesize("schedule", PronunciationAccent.American).getOrThrow().fill(0)
            engine.synthesize("schedule", PronunciationAccent.British).getOrThrow().fill(0)
            assertEquals(
                listOf(PronunciationAccent.American, PronunciationAccent.British),
                recordingFactory.frontendAccents,
            )
        } finally {
            engine.close()
        }
        assertTrue("wrapped production runtime must be closed", recordingFactory.closed)
    }

    @Test
    fun productionEngineGeneratesDistinctUsAndGbPcm16Wav() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = KokoroAndroidEngine(context)
        try {
            val prepared = engine.prepare()
            assertTrue("production Kokoro prepare must succeed: ${prepared.exceptionOrNull()}", prepared.isSuccess)
            assertTrue("production Kokoro must be ready after prepare", engine.isReady())

            val outputs = PronunciationAccent.entries.associateWith { accent ->
                val audio = engine.synthesize("schedule", accent).getOrThrow()
                try {
                    assertPcm16Mono24KhzWav(audio)
                    val digest = sha256(audio)
                    println("KOKORO_PRODUCTION accent=$accent bytes=${audio.size} sha256=$digest")
                    digest to audio.size
                } finally {
                    audio.fill(0)
                }
            }
            assertEquals("US and GB production outputs must differ", 2, outputs.values.map { it.first }.toSet().size)
            assertTrue("US output must be non-empty", outputs.getValue(PronunciationAccent.American).second > 44)
            assertTrue("GB output must be non-empty", outputs.getValue(PronunciationAccent.British).second > 44)

            val cacheRoot = java.io.File(context.cacheDir, "kokoro-production")
            assertTrue("production assets must be materialized in cache", cacheRoot.isDirectory)
            println("KOKORO_PRODUCTION cache=${cacheRoot.absolutePath} files=${cacheRoot.walkTopDown().count { it.isFile }}")
        } finally {
            engine.close()
            assertTrue("engine must be closed after production test", !engine.isReady())
        }
    }

    @Test
    fun productionProviderPlaysAmericanAndBritishKokoroAudioThroughAndroidPlayer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = KokoroAndroidEngine(context)
        val provider = KokoroPronunciationProvider(engine, AndroidAudioPlayer(context))
        try {
            assertEquals(PronunciationResult.Played, provider.speakWord("apple", PronunciationAccent.American))
            assertEquals(PronunciationResult.Played, provider.speakWord("apple", PronunciationAccent.British))
            println("KOKORO_PRODUCTION playback=Played word=apple accents=American,British")
        } finally {
            engine.close()
        }
    }

    @Test
    fun providerKeepsPlayerBufferReferenceAndZeroesItAfterPlayback() = runBlocking {
        val audio = byteArrayOf(1, 2, 3, 4)
        val engine = FakeKokoroEngine(audio)
        val player = CapturingAudioPlayer(AudioPlaybackResult.Played)
        val provider = KokoroPronunciationProvider(engine, player)

        assertEquals(PronunciationResult.Played, provider.speakWord("ability"))
        assertTrue("player must receive the engine buffer by reference", player.received === audio)
        assertTrue("provider must zero the playback buffer after player returns", audio.all { it == 0.toByte() })
    }

    @Test
    fun providerMapsPlayerFailureToFailedAndStillZeroesBuffer() = runBlocking {
        val audio = byteArrayOf(5, 6, 7)
        val engine = FakeKokoroEngine(audio)
        val player = CapturingAudioPlayer(AudioPlaybackResult.Failed)
        val provider = KokoroPronunciationProvider(engine, player)

        assertEquals(PronunciationResult.Failed(), provider.speakWord("ability"))
        assertTrue(audio.all { it == 0.toByte() })
    }

    @Test
    fun providerMapsUnavailableEngineToUnavailableWithoutReadingProductionAssets() = runBlocking {
        val engine = FakeKokoroEngine(prepareResult = Result.failure(IllegalStateException("injected unavailable")))
        val provider = KokoroPronunciationProvider(engine, CapturingAudioPlayer(AudioPlaybackResult.Played))

        val result = provider.speakWord("ability")
        assertTrue(result is PronunciationResult.Unavailable)
        assertEquals(0, engine.synthesizeCalls)
    }

    @Test
    fun productionProviderCanSpeakAgainAfterSynthesisCancellation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cancelled = CancellationException("injected synthesis cancellation")
        var cancelFirst = true
        var runtimeCloseCalls = 0
        val factory = KokoroRuntimeFactory { paths, registerPartial ->
            val runtime = OrtKokoroRuntimeFactory().create(paths, registerPartial)
            object : KokoroRuntime {
                override fun frontend(accent: PronunciationAccent, ids: LongArray): Array<Array<FloatArray>> {
                    if (cancelFirst) {
                        cancelFirst = false
                        throw cancelled
                    }
                    return runtime.frontend(accent, ids)
                }
                override fun audio(tokens: LongArray, style: FloatArray, speed: Float) = runtime.audio(tokens, style, speed)
                override fun close() {
                    runtimeCloseCalls++
                    runtime.close()
                }
            }
        }
        val engine = KokoroAndroidEngine(context, factory = factory)
        val provider = KokoroPronunciationProvider(engine, AndroidAudioPlayer(context))
        try {
            val attempt = runCatching { provider.speakWord("apple") }
            assertTrue("cancellation must propagate unchanged", attempt.exceptionOrNull() === cancelled)
            assertEquals("cancelled inference must release its native runtime", 1, runtimeCloseCalls)
            assertTrue("released runtime must require prepare again", !engine.isReady())
            assertEquals(PronunciationResult.Played, provider.speakWord("schedule", PronunciationAccent.British))
        } finally {
            engine.close()
        }
    }

    @Test
    fun productionSynthesisCancellationAfterNativeAudioDoesNotHandOffOrKeepRuntime() = runBlocking {
        val outcome = cancelAfterNativeAudio()
        assertTrue("native audio must return before cancellation is observed", outcome.audioReturned)
        assertTrue("cancellation must not hand WAV to player", outcome.player.received == null)
        assertEquals("cancelled synthesis must release runtime", 1, outcome.runtimeCloseCalls)
        assertTrue("cancelled engine must no longer be ready", !outcome.ready)
    }

    @Test
    fun productionSynthesisCancellationAfterNativeAudioClearsPcm() = runBlocking {
        val outcome = cancelAfterNativeAudio()
        assertTrue("native PCM must be cleared after cancellation", outcome.pcm.all { it == 0f })
    }

    private data class NativeCancellationOutcome(
        val audioReturned: Boolean,
        val pcm: FloatArray,
        val player: CapturingAudioPlayer,
        val runtimeCloseCalls: Int,
        val ready: Boolean,
    )

    private suspend fun cancelAfterNativeAudio(): NativeCancellationOutcome = coroutineScope {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var requestJob: Job? = null
        var audioReturned = false
        var producedAudio: FloatArray? = null
        var runtimeCloseCalls = 0
        val factory = KokoroRuntimeFactory { paths, registerPartial ->
            val delegate = OrtKokoroRuntimeFactory().create(paths, registerPartial)
            object : KokoroRuntime {
                override fun frontend(accent: PronunciationAccent, ids: LongArray): Array<Array<FloatArray>> =
                    delegate.frontend(accent, ids)
                override fun audio(tokens: LongArray, style: FloatArray, speed: Float): FloatArray =
                    delegate.audio(tokens, style, speed).also {
                        audioReturned = true
                        producedAudio = it
                        requestJob?.cancel()
                    }
                override fun close() {
                    runtimeCloseCalls++
                    delegate.close()
                }
            }
        }
        val engine = KokoroAndroidEngine(context, factory = factory)
        val player = CapturingAudioPlayer(AudioPlaybackResult.Played)
        val provider = KokoroPronunciationProvider(engine, player)
        try {
            val request = launch {
                requestJob = coroutineContext[Job]
                provider.speakWord("schedule")
            }
            request.join()
            NativeCancellationOutcome(audioReturned, requireNotNull(producedAudio), player, runtimeCloseCalls, engine.isReady())
        } finally {
            engine.close()
        }
    }

    @Test
    fun productionProviderCanSpeakAgainAfterAndroidPlaybackCancellation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = KokoroAndroidEngine(context)
        val playbackStarted = CompletableDeferred<Unit>()
        val androidPlayer = AndroidAudioPlayer(context)
        var firstPlayback = true
        var cancelledAudio: ByteArray? = null
        val player = object : AudioPlayer {
            override suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult = coroutineScope {
                if (firstPlayback) {
                    firstPlayback = false
                    cancelledAudio = bytes
                    // UNDISPATCHED 保证 MediaPlayer 已设置并开始异步准备后才通知测试取消。
                    val playback = async(start = CoroutineStart.UNDISPATCHED) { androidPlayer.play(bytes, format) }
                    playbackStarted.complete(Unit)
                    playback.await()
                } else {
                    androidPlayer.play(bytes, format)
                }
            }
        }
        val provider = KokoroPronunciationProvider(engine, player)
        try {
            val request = launch { provider.speakWord("apple") }
            withTimeout(60_000) { playbackStarted.await() }
            request.cancelAndJoin()
            assertTrue("cancelled player bytes must be zeroed", requireNotNull(cancelledAudio).all { it == 0.toByte() })
            assertEquals(PronunciationResult.Played, provider.speakWord("apple", PronunciationAccent.British))
        } finally {
            engine.close()
        }
    }

    private class RecordingRuntimeFactory(private val delegate: KokoroRuntimeFactory) : KokoroRuntimeFactory {
        val frontendAccents = mutableListOf<PronunciationAccent>()
        var closed = false

        override fun create(paths: Map<String, java.io.File>, registerPartial: (AutoCloseable) -> Unit): KokoroRuntime {
            val runtime = delegate.create(paths, registerPartial)
            return object : KokoroRuntime {
                override fun frontend(accent: PronunciationAccent, ids: LongArray): Array<Array<FloatArray>> {
                    frontendAccents += accent
                    return runtime.frontend(accent, ids)
                }

                override fun audio(tokens: LongArray, style: FloatArray, speed: Float): FloatArray =
                    runtime.audio(tokens, style, speed)

                override fun close() {
                    closed = true
                    runtime.close()
                }
            }
        }
    }

    private class CapturingAudioPlayer(private val result: AudioPlaybackResult) : AudioPlayer {
        var received: ByteArray? = null

        override suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult {
            received = bytes
            return result
        }
    }

    private class FakeKokoroEngine(
        private val audio: ByteArray = byteArrayOf(1, 2, 3),
        private val prepareResult: Result<Unit> = Result.success(Unit),
    ) : KokoroEngine {
        var synthesizeCalls = 0
        private var ready = false

        override suspend fun prepare(): Result<Unit> {
            if (prepareResult.isSuccess) ready = true
            return prepareResult
        }

        override fun isReady() = ready

        override suspend fun synthesize(text: String, accent: PronunciationAccent): Result<ByteArray> {
            synthesizeCalls++
            return Result.success(audio)
        }

        override suspend fun close() { ready = false }
    }

    private fun assertPcm16Mono24KhzWav(bytes: ByteArray) {
        assertTrue("WAV must contain header and PCM payload", bytes.size > 44)
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF size must match file length", bytes.size - 8, header.getInt(4))
        var offset = 12
        var fmt: ByteArray? = null
        var dataOffset = -1
        var dataSize = -1
        while (offset + 8 <= bytes.size) {
            val id = bytes.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
            val size = header.getInt(offset + 4)
            val start = offset + 8
            assertTrue("WAV chunk must fit within file", size >= 0 && start + size <= bytes.size)
            when (id) {
                "fmt " -> fmt = bytes.copyOfRange(start, start + size)
                "data" -> { dataOffset = start; dataSize = size }
            }
            offset = start + size + (size and 1)
        }
        assertEquals("WAV chunks must end at file boundary", bytes.size, offset)
        val format = fmt ?: error("WAV is missing fmt chunk")
        val fields = ByteBuffer.wrap(format).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("PCM", 1, fields.short.toInt())
        assertEquals("mono", 1, fields.short.toInt())
        assertEquals("24 kHz", 24000, fields.int)
        assertEquals("byte rate", 48000, fields.int)
        assertEquals("block alignment", 2, fields.short.toInt())
        assertEquals("PCM16", 16, fields.short.toInt())
        assertTrue("data chunk must exist", dataOffset >= 0)
        assertEquals("data chunk must cover payload", bytes.size - dataOffset, dataSize)
        assertTrue("PCM payload must not be silent", bytes.copyOfRange(dataOffset, bytes.size).any { it != 0.toByte() })
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
