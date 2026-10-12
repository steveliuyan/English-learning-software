package com.example.englishlearning.language.spike

import com.example.englishlearning.language.domain.PronunciationAccent
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineStart
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicInteger

class OnnxKokoroEngineTest {
    @TempDir lateinit var root: File

    @Test fun `prepare establishes readiness exactly once and close is terminal`() = runTest {
        val runtime = Runtime()
        var creates = 0
        val engine = OnnxKokoroEngine(root, { _, _ -> creates++; runtime }, fixture(), StandardTestDispatcher(testScheduler))
        assertFalse(engine.isReady())
        assertTrue(engine.synthesize("apple", PronunciationAccent.American).isFailure)
        assertEquals(0, creates)
        assertTrue(engine.prepare().isSuccess)
        assertTrue(engine.isReady())
        assertTrue(engine.prepare().isSuccess)
        assertEquals(1, creates)
        engine.close()
        engine.close()
        assertEquals(1, runtime.closes)
        assertFalse(engine.isReady())
        assertTrue(engine.prepare().isFailure)
        assertEquals(1, creates)
    }

    @Test fun `all five size gates prevent runtime construction`() = runTest {
        for (name in listOf("model", "voices", "config", "frontend-en-us", "frontend-en-gb")) {
            val manifest = fixture()
            File(root, manifest.getValue(name).relativePath).appendBytes(byteArrayOf(1))
            var creates = 0
            val engine = OnnxKokoroEngine(root, { _, _ -> creates++; Runtime() }, manifest, StandardTestDispatcher(testScheduler))
            assertTrue(engine.prepare().isFailure, name)
            assertFalse(engine.isReady())
            assertEquals(0, creates)
        }
    }

    @Test fun `all five hash gates reject equal size corruption before runtime`() = runTest {
        for (name in listOf("model", "voices", "config", "frontend-en-us", "frontend-en-gb")) {
            val manifest = fixture()
            val file = File(root, manifest.getValue(name).relativePath)
            val bytes = file.readBytes(); bytes[0] = (bytes[0].toInt() xor 1).toByte(); file.writeBytes(bytes)
            var creates = 0
            val engine = OnnxKokoroEngine(root, { _, _ -> creates++; Runtime() }, manifest, StandardTestDispatcher(testScheduler))
            assertTrue(engine.prepare().isFailure, name)
            assertEquals(0, creates, name)
        }
    }

    @Test fun `partial factory failure attempts cleanup in reverse and preserves primary with suppressed close failure`() = runTest {
        val events = mutableListOf<String>()
        val first = CloseableResource("first", events)
        val second = CloseableResource("second", events, IllegalStateException("close-second"))
        val engine = OnnxKokoroEngine(root, { _, register -> register(first); register(second); error("primary") }, fixture(), StandardTestDispatcher(testScheduler))
        val result = engine.prepare()
        val failure = result.exceptionOrNull()!!
        assertEquals("primary", failure.message)
        assertEquals(listOf("second", "first"), events)
        assertEquals(listOf("close-second"), failure.suppressed.map { it.message })
        assertFalse(engine.isReady())
    }

    @Test fun `tagged record VB uses source-derived phonemes and bypasses frontend`() = runTest {
        for ((accent, phonemes) in listOf(
            PronunciationAccent.American to "ɹəkˈɔɹd",
            PronunciationAccent.British to "ɹɪkˈɔːd",
        )) {
            val runtime = Runtime()
            val engine = OnnxKokoroEngine(root, { _, _ -> runtime }, fixture(), StandardTestDispatcher(testScheduler))
            engine.prepare().getOrThrow()

            val result = engine.synthesize(
                KokoroWordInput("record", "VB"),
                accent,
                mapOf("record|VERB" to phonemes),
            )

            assertTrue(result.isSuccess)
            val resolution = KokoroLexiconResolver.resolve(
                "record",
                if (accent == PronunciationAccent.American) "en-us" else "en-gb",
                "VB",
                mapOf("record|VERB" to phonemes),
            )
            assertEquals("record|VERB", resolution?.matchedKey)
            assertEquals(phonemes, resolution?.phonemes)
            assertEquals(0, runtime.frontendCalls)
            assertTrue(phonemes.codePoints().toArray().map(::fixtureToken).toLongArray().contentEquals(runtime.lastTokens!!.copyOfRange(1, phonemes.codePointCount(0, phonemes.length) + 1)))
            engine.close()
        }
    }

    @Test fun `untagged two-argument synthesis still invokes frontend`() = runTest {
        val runtime = Runtime()
        val engine = OnnxKokoroEngine(root, { _, _ -> runtime }, fixture(), StandardTestDispatcher(testScheduler))
        engine.prepare().getOrThrow()

        assertTrue(engine.synthesize("apple", PronunciationAccent.American).isSuccess)
        assertEquals(1, runtime.frontendCalls)
    }

    @Test fun `unrecognized tagged input follows neural frontend fallback`() = runTest {
        val runtime = Runtime()
        val engine = OnnxKokoroEngine(root, { _, _ -> runtime }, fixture(), StandardTestDispatcher(testScheduler))
        engine.prepare().getOrThrow()

        assertTrue(engine.synthesize(KokoroWordInput("apple", "VB"), PronunciationAccent.American, emptyMap()).isSuccess)
        assertEquals(1, runtime.frontendCalls)
    }

    @Test fun `runtime close failure is suppressed behind primary synthesis failure`() = runTest {
        val runtime = Runtime().apply {
            audio = floatArrayOf(Float.NaN)
            closeFailure = IllegalStateException("close")
        }
        val engine = OnnxKokoroEngine(root, { _, _ -> runtime }, fixture(), StandardTestDispatcher(testScheduler))
        engine.prepare().getOrThrow()
        val failure = engine.synthesize("a", PronunciationAccent.American).exceptionOrNull()!!
        assertTrue(failure.message.orEmpty().contains("非有限"))
        assertEquals("close", failure.suppressed.single().message)
    }

    @Test fun `runtime close failure cannot mask cancellation`() = runTest {
        val runtime = Runtime().apply {
            cancellation = CancellationException("cancel")
            closeFailure = IllegalStateException("close-cancel")
        }
        val engine = OnnxKokoroEngine(root, { _, _ -> runtime }, fixture(), StandardTestDispatcher(testScheduler))
        engine.prepare().getOrThrow()

        val cancellation = assertFailsWith<CancellationException> {
            engine.synthesize("a", PronunciationAccent.American)
        }

        assertEquals("cancel", cancellation.message)
        assertEquals(1, runtime.closes)
        assertFalse(engine.isReady())
    }

    @Test fun `runtime close failure is suppressed behind synthesis Error`() = runTest {
        val runtime = Runtime().apply {
            failure = AssertionError("error")
            closeFailure = IllegalStateException("close-error")
        }
        val engine = OnnxKokoroEngine(root, { _, _ -> runtime }, fixture(), StandardTestDispatcher(testScheduler))
        engine.prepare().getOrThrow()

        val error = assertFailsWith<AssertionError> {
            engine.synthesize("a", PronunciationAccent.American)
        }

        assertEquals("error", error.message)
        assertEquals(1, runtime.closes)
        assertFalse(engine.isReady())
    }

    @Test fun `cancelled parent still permits close cleanup`() = runTest {
        val runtime = Runtime()
        val engine = OnnxKokoroEngine(root, { _, _ -> runtime }, fixture(), StandardTestDispatcher(testScheduler))
        engine.prepare().getOrThrow()
        val job = launch {
            currentCoroutineContext().cancel()
            engine.close()
        }
        job.join()
        assertEquals(1, runtime.closes)
        assertFalse(engine.isReady())
    }

    private fun fixture(): Map<String, KokoroAsset> {
        File(root, "model").writeBytes(byteArrayOf(1, 2))
        File(root, "frontend-en-us").writeBytes(byteArrayOf(3))
        File(root, "frontend-en-gb").writeBytes(byteArrayOf(4))
        File(root, "config").writeText("""{"vocab":{"a":43,"b":44,"p":58,"ʑ":99,"ɹ":60,"ə":61,"k":62,"ˈ":63,"ɔ":64,"ɪ":65,"ː":66,"d":67}}""")
        ZipOutputStream(File(root, "voices").outputStream()).use { zip ->
            for ((name, offset) in listOf("af_heart.npy" to 0f, "bf_emma.npy" to 10000f)) {
                zip.putNextEntry(ZipEntry(name)); zip.write(npy(offset)); zip.closeEntry()
            }
        }
        return listOf("model", "voices", "config", "frontend-en-us", "frontend-en-gb").associateWith { name ->
            val file = File(root, name)
            KokoroAsset(name, file.length(), digest(file))
        }
    }

    private fun npy(offset: Float): ByteArray {
        val header = "{'descr': '<f4', 'fortran_order': False, 'shape': (510, 1, 256), }".padEnd(117, ' ') + "\n"
        return ByteBuffer.allocate(522368).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(byteArrayOf(0x93.toByte(), 78, 85, 77, 80, 89, 1, 0)); putShort(118)
            put(header.toByteArray(Charsets.US_ASCII))
            repeat(510 * 256) { putFloat(offset + it.toFloat()) }
        }.array()
    }

    private fun digest(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun fixtureToken(codePoint: Int): Long = mapOf(
        'ɹ'.code to 60L,
        'ə'.code to 61L,
        'k'.code to 62L,
        'ˈ'.code to 63L,
        'ɔ'.code to 64L,
        'ɪ'.code to 65L,
        'ː'.code to 66L,
        'd'.code to 67L,
    ).getValue(codePoint)

    private class Runtime : KokoroRuntime {
        var frontendCalls = 0
        var lastTokens: LongArray? = null
        var closes = 0
        var cancellation: CancellationException? = null
        var failure: Error? = null
        var closeFailure: Throwable? = null
        var audio = floatArrayOf(0.25f, -0.25f)
        override fun frontend(accent: PronunciationAccent, ids: LongArray): Array<Array<FloatArray>> {
            frontendCalls++
            return arrayOf(arrayOf(FloatArray(116) { if (it == 31) 10f else -10f }))
        }
        override fun audio(tokens: LongArray, style: FloatArray, speed: Float): FloatArray {
            lastTokens = tokens
            cancellation?.let { throw it }
            failure?.let { throw it }
            return audio
        }
        override fun close() { closes++; closeFailure?.let { throw it } }
    }

    private class CloseableResource(private val name: String, private val events: MutableList<String>, private val failure: Throwable? = null) : AutoCloseable {
        override fun close() { events += name; failure?.let { throw it } }
    }
}
