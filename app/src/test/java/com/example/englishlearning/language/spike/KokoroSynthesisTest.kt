package com.example.englishlearning.language.spike

import com.example.englishlearning.language.domain.PronunciationAccent
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KokoroSynthesisTest {
    @TempDir lateinit var root: File

    @Test fun `synthesize validates word selects accent frontend voice and uses token count style`() = runTest {
        val runtime = RecordingRuntime()
        val engine = engine(runtime)
        assertTrue(engine.prepare().isSuccess)
        val american = engine.synthesize(" Apple ", PronunciationAccent.American).getOrThrow()
        val british = engine.synthesize("apple", PronunciationAccent.British).getOrThrow()
        assertTrue(american.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()))
        assertContentEquals(longArrayOf(2, 17, 17, 13, 6), runtime.graphemes.first())
        assertContentEquals(longArrayOf(0, 43, 44, 0), runtime.tokens.toLongArray())
        assertEquals(512f, runtime.styles.first()[0])
        assertEquals(10512f, runtime.styles[1][0])
        assertEquals(listOf(PronunciationAccent.American, PronunciationAccent.British), runtime.accents)
        assertEquals(listOf(1f, 1f), runtime.speeds)
        assertEquals(2, runtime.frontendCalls)
        assertEquals(2, runtime.audioCalls)
        assertTrue(british.isNotEmpty())
    }

    @Test fun `invalid text is rejected before native frontend`() = runTest {
        val runtime = RecordingRuntime()
        val engine = engine(runtime)
        engine.prepare().getOrThrow()
        val result = engine.synthesize("two words", PronunciationAccent.American)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("单词"))
        assertEquals(0, runtime.frontendCalls)
        assertEquals(0, runtime.audioCalls)
    }

    @Test fun `unknown decoded phone fails after frontend without audio`() = runTest {
        val runtime = RecordingRuntime().apply { phone = "ʑ" }
        val engine = engine(runtime)
        engine.prepare().getOrThrow()
        val result = engine.synthesize("a", PronunciationAccent.American)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("未知音素"))
        assertEquals(1, runtime.frontendCalls)
        assertEquals(0, runtime.audioCalls)
    }

    @Test fun `nonfinite native output fails after audio call`() = runTest {
        val runtime = RecordingRuntime().apply { audio = floatArrayOf(Float.NaN) }
        val engine = engine(runtime)
        engine.prepare().getOrThrow()
        val result = engine.synthesize("a", PronunciationAccent.American)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("非有限"))
        assertEquals(1, runtime.frontendCalls)
        assertEquals(1, runtime.audioCalls)
    }

    @Test fun `synthesize cancellation propagates and does not become failed result`() = runTest {
        val runtime = RecordingRuntime().apply { cancellation = CancellationException("native cancelled") }
        val engine = engine(runtime); engine.prepare()
        val closes = AtomicInteger(); runtime.closeCount = closes
        assertFailsWith<CancellationException> { engine.synthesize("a", PronunciationAccent.American) }
        assertEquals(1, closes.get())
        assertFalse(engine.isReady())
    }

    @Test fun `runtime Error is not swallowed`() = runTest {
        val engine = engine(RecordingRuntime().apply { failure = AssertionError("native bug") }); engine.prepare()
        assertFailsWith<AssertionError> { engine.synthesize("a", PronunciationAccent.American) }
    }

    @Test fun `prepare and synthesize use injected background dispatcher`() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "kokoro-test-worker") }
        try {
            val threads = mutableListOf<String>()
            val factoryThreads = mutableListOf<String>()
            val runtime = RecordingRuntime().apply { calls = threads }
            val engine = OnnxKokoroEngine(root, { _, _ -> factoryThreads += Thread.currentThread().name; runtime }, fixture(), executor.asCoroutineDispatcher())
            engine.prepare(); engine.synthesize("a", PronunciationAccent.American)
            assertTrue(threads.isNotEmpty(), "runtime call capture must not be empty")
            assertTrue(factoryThreads.isNotEmpty(), "factory call capture must not be empty")
            assertTrue(threads.all { it == "kokoro-test-worker" }, threads.toString())
            assertTrue(factoryThreads.all { it == "kokoro-test-worker" }, factoryThreads.toString())
        } finally { executor.shutdownNow() }
    }

    @Test fun `caller cancellation observed after native return closes resources`() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val closes = AtomicInteger()
        val runtime = RecordingRuntime().apply { block = true; this.entered = entered; this.release = release; closeCount = closes }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val dispatcher = executor.asCoroutineDispatcher()
            val engine = OnnxKokoroEngine(root, { _, _ -> runtime }, fixture(), dispatcher)
            engine.prepare().getOrThrow()
            val job = kotlinx.coroutines.CoroutineScope(dispatcher).async { engine.synthesize("a", PronunciationAccent.American) }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); job.cancel(); release.countDown(); assertFailsWith<CancellationException> { job.await() }
            assertEquals(1, closes.get()); assertFalse(engine.isReady())
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun `native failure releases runtime and close remains terminal`() = runTest {
        val closes = AtomicInteger(); val runtime = RecordingRuntime().apply { audio = floatArrayOf(Float.NaN); closeCount = closes }
        val engine = engine(runtime); engine.prepare().getOrThrow()
        assertTrue(engine.synthesize("a", PronunciationAccent.American).isFailure)
        assertEquals(1, closes.get()); assertFalse(engine.isReady()); engine.close(); assertEquals(1, closes.get())
    }

    @Test fun `close waits for in flight synthesis and releases runtime exactly once`() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val closes = AtomicInteger()
        val runtime = RecordingRuntime().apply { block = true; this.entered = entered; this.release = release; closeCount = closes }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val engine = OnnxKokoroEngine(root, { _, _ -> runtime }, fixture(), executor.asCoroutineDispatcher())
            engine.prepare().getOrThrow()
            val job = kotlinx.coroutines.CoroutineScope(executor.asCoroutineDispatcher()).async { engine.synthesize("a", PronunciationAccent.American) }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); val close = kotlinx.coroutines.CoroutineScope(executor.asCoroutineDispatcher()).async { engine.close() }
            Thread.sleep(50); assertEquals(0, closes.get()); release.countDown(); job.await(); close.await(); assertEquals(1, closes.get()); assertFalse(engine.isReady())
        } finally { executor.shutdownNow() }
    }

    @Test fun `phoneme509 boundary accepted and 510 rejected`() = runTest {
        val runtime = RecordingRuntime().apply { phone = ("ab".repeat(254) + "a") }
        val engine = engine(runtime); engine.prepare().getOrThrow()
        engine.synthesize("a", PronunciationAccent.American).getOrThrow(); assertEquals(511, runtime.tokens.size); assertEquals(130304f, runtime.styles.last()[0])
        runtime.phone = "ab".repeat(255); assertTrue(engine.synthesize("a", PronunciationAccent.American).isFailure)
    }

    @Test fun `voice pack rejects truncated target without construction`() = runTest {
        val base = fixture().toMutableMap()
        val voice = File(root, "voices")
        ZipOutputStream(voice.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("af_heart.npy")); zip.write(npy(0f).copyOf(522367)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("bf_emma.npy")); zip.write(npy(10000f)); zip.closeEntry()
        }
        base["voices"] = KokoroAsset("voices", voice.length(), MessageDigest.getInstance("SHA-256").digest(voice.readBytes()).joinToString("") { "%02x".format(it) })
        var created = false
        val engine = OnnxKokoroEngine(root, { _, _ -> created = true; RecordingRuntime() }, base, kotlinx.coroutines.Dispatchers.Default)
        assertTrue(engine.prepare().isFailure); assertFalse(created)
    }

    private fun engine(runtime: RecordingRuntime): OnnxKokoroEngine = OnnxKokoroEngine(root, { _, _ -> runtime }, fixture(), kotlinx.coroutines.Dispatchers.Default)

    @Test fun `all declared assets are checked before factory even on cancellation`() = runTest {
        val broken = fixture().toMutableMap(); val file = File(root, "frontend-en-gb"); file.appendBytes(byteArrayOf(7)); var created = false
        val engine = OnnxKokoroEngine(root, { _, _ -> created = true; RecordingRuntime() }, broken, kotlinx.coroutines.Dispatchers.Default)
        assertTrue(engine.prepare().isFailure); assertFalse(created)
    }

    private fun fixture(): Map<String, KokoroAsset> {
        File(root, "model").writeBytes(byteArrayOf(1, 2)); File(root, "frontend-en-us").writeBytes(byteArrayOf(3)); File(root, "frontend-en-gb").writeBytes(byteArrayOf(4)); File(root, "config").writeText("""{"vocab":{"a":43,"b":44}}""")
        ZipOutputStream(File(root, "voices").outputStream()).use { zip -> for ((name, offset) in listOf("af_heart.npy" to 0f, "bf_emma.npy" to 10000f)) { zip.putNextEntry(ZipEntry(name)); zip.write(npy(offset)); zip.closeEntry() } }
        return listOf("model", "voices", "config", "frontend-en-us", "frontend-en-gb").associateWith { name -> File(root, name).let { KokoroAsset(name, it.length(), MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) }) } }
    }
    private fun npy(offset: Float): ByteArray = ByteBuffer.allocate(522368).order(ByteOrder.LITTLE_ENDIAN).apply { put(byteArrayOf(0x93.toByte(), 78, 85, 77, 80, 89, 1, 0)); putShort(118); put(("{'descr': '<f4', 'fortran_order': False, 'shape': (510, 1, 256), }".padEnd(117, ' ') + "\n").toByteArray()); repeat(510 * 256) { putFloat(offset + it) } }.array()

    private class RecordingRuntime : KokoroRuntime {
        var phone = "ab"; var audio = floatArrayOf(.25f, -.25f); var cancellation: CancellationException? = null; var failure: Throwable? = null; var tokens = mutableListOf<Long>(); var styles = mutableListOf<FloatArray>(); var accents = mutableListOf<PronunciationAccent>(); var calls: MutableList<String>? = null; var block = false; var entered: CountDownLatch? = null; var release: CountDownLatch? = null; var closeCount: AtomicInteger? = null
        var frontendCalls = 0; var audioCalls = 0; val graphemes = mutableListOf<LongArray>(); val speeds = mutableListOf<Float>(); var closeFailure: Throwable? = null
        override fun frontend(accent: PronunciationAccent, ids: LongArray): Array<Array<FloatArray>> { frontendCalls++; graphemes += ids.copyOf(); calls?.add(Thread.currentThread().name); accents += accent; return arrayOf(Array(phone.length) { index -> FloatArray(116) { if (it == when (phone[index]) { 'a' -> 31; 'b' -> 32; else -> 115 }) 10f else -10f } }) }
        override fun audio(tokens: LongArray, style: FloatArray, speed: Float): FloatArray { audioCalls++; speeds += speed; calls?.add(Thread.currentThread().name); this.tokens = tokens.toMutableList(); styles += style; if (block) { entered!!.countDown(); check(release!!.await(5, TimeUnit.SECONDS)) }; cancellation?.let { throw it }; failure?.let { throw it }; return audio }
        override fun close() { closeCount?.incrementAndGet(); closeFailure?.let { throw it } }
    }
}
