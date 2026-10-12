package com.example.englishlearning.language.spike

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.language.domain.PronunciationAccent
import com.example.englishlearning.language.infrastructure.AndroidAudioPlayer
import com.example.englishlearning.language.infrastructure.AudioPlaybackResult
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KokoroEngineAvailabilityTest {
    @Test
    fun fixed_words_generate_valid_us_and_gb_wav_and_repeat_deterministically() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetRoot = context.getExternalFilesDir("kokoro-spike-assets")
            ?: throw AssertionError("Kokoro 外部测试资产目录不可用")
        val missing = OnnxKokoroEngine.DEFAULT_MANIFEST.values
            .map { it.relativePath }
            .filterNot { File(assetRoot, it).isFile }
        assertTrue(
            "Kokoro 外部测试资产缺失（相对路径）: ${missing.joinToString()}",
            missing.isEmpty(),
        )

        val runtimeFactory = CountingKokoroRuntimeFactory(OrtKokoroRuntimeFactory())
        val engine = OnnxKokoroEngine(assetRoot, runtimeFactory)
        val reviewAudioRoot = context.getExternalFilesDir("kokoro-spike-listen-unknown-word")
            ?: throw AssertionError("Kokoro 未知词试听输出目录不可用")
        val player = AndroidAudioPlayer(context)
        val memorySampler = AndroidProcessMemorySampler(intervalMs = 100)
        try {
            memorySampler.markPhase("prepare")
            val prepareStart = System.nanoTime()
            assertTrue("prepare failed", engine.prepare().isSuccess)
            val prepareMs = (System.nanoTime() - prepareStart) / 1_000_000
            assertTrue("engine did not become ready", engine.isReady())
            println("kokoro prepareMs=$prepareMs")

            val words = listOf("apple", "schedule", "record")
            for (word in words) {
                for (accent in listOf(PronunciationAccent.American, PronunciationAccent.British)) {
                    memorySampler.markPhase("synthesis:$word:$accent")
                    val start = System.nanoTime()
                    val audio = engine.synthesize(word, accent).getOrThrow()
                    val synthesisMs = (System.nanoTime() - start) / 1_000_000
                    assertWav(audio)
                    val byteCount = audio.size
                    val sha = sha256(audio)
                    val accentLabel = if (accent == PronunciationAccent.American) "us" else "gb"
                    val exported = KokoroReviewAudioExporter.write(reviewAudioRoot, "$word-$accentLabel.wav", audio)
                    assertEquals(byteCount, exported.length().toInt())
                    assertEquals(sha, sha256(exported.readBytes()))
                    val heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                    memorySampler.markPhase("playback:$word:$accent")
                    val playbackStart = System.nanoTime()
                    val playback = try {
                        player.play(audio, "wav")
                    } finally {
                        audio.fill(0)
                    }
                    val playbackMs = (System.nanoTime() - playbackStart) / 1_000_000
                    assertEquals("playback failed for word=$word accent=$accent", AudioPlaybackResult.Played, playback)
                    println("kokoro word=$word accent=$accent synthesisMs=$synthesisMs playbackMs=$playbackMs bytes=$byteCount sha256=$sha heap=$heap")
                }
            }

            for (accent in listOf(PronunciationAccent.American, PronunciationAccent.British)) {
                memorySampler.markPhase("repeat:apple:$accent")
                val first = engine.synthesize("apple", accent).getOrThrow()
                val firstSha = sha256(first)
                first.fill(0)
                val second = engine.synthesize("apple", accent).getOrThrow()
                try {
                    assertWav(second)
                    assertEquals("repeated synthesis changed for accent=$accent", firstSha, sha256(second))
                } finally {
                    second.fill(0)
                }
            }

            val unknownWordDigests = mutableSetOf<String>()
            for (accent in listOf(PronunciationAccent.American, PronunciationAccent.British)) {
                memorySampler.markPhase("unknown-workbuddy:$accent")
                val start = System.nanoTime()
                val audio = engine.synthesize("workbuddy", accent).getOrThrow()
                val synthesisMs = (System.nanoTime() - start) / 1_000_000
                try {
                    assertWav(audio)
                    val sha = sha256(audio)
                    assertTrue("unknown word must not produce an empty digest", sha.isNotBlank())
                    unknownWordDigests += sha
                    val accentLabel = if (accent == PronunciationAccent.American) "us" else "gb"
                    val exported = KokoroReviewAudioExporter.write(reviewAudioRoot, "workbuddy-$accentLabel.wav", audio)
                    assertEquals(audio.size.toLong(), exported.length())
                    assertEquals(sha, sha256(exported.readBytes()))
                    val playbackStart = System.nanoTime()
                    val playback = try {
                        player.play(audio, "wav")
                    } finally {
                        audio.fill(0)
                    }
                    val playbackMs = (System.nanoTime() - playbackStart) / 1_000_000
                    assertEquals("unknown word playback failed for accent=$accent", AudioPlaybackResult.Played, playback)
                    println("kokoro unknownWord=workbuddy accent=$accent synthesisMs=$synthesisMs playbackMs=$playbackMs bytes=${exported.length()} sha256=$sha")
                } finally {
                    audio.fill(0)
                }
            }
            assertEquals("US and GB neural fallback should use distinct frontend/voice outputs", 2, unknownWordDigests.size)
        } finally {
            memorySampler.close()
            try {
                val peak = memorySampler.snapshot()
                assertTrue("memory sampler did not collect periodic samples", peak.samples > 1)
                println(
                    "kokoro memory intervalMs=100 samples=${peak.samples} " +
                        "peakPssKb=${peak.peakPssKb} peakPssPhase=${peak.peakPssPhase} " +
                        "peakNativePssKb=${peak.peakNativePssKb} peakNativePssPhase=${peak.peakNativePssPhase} " +
                        "peakNativeHeapBytes=${peak.peakNativeHeapBytes} peakNativeHeapPhase=${peak.peakNativeHeapPhase} " +
                        "peakJavaHeapBytes=${peak.peakJavaHeapBytes} peakJavaHeapPhase=${peak.peakJavaHeapPhase}",
                )
            } finally {
                engine.close()
            }
        }
    }

    @Test
    fun record_noun_and_explicit_vb_generate_distinct_us_and_gb_audio() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetRoot = context.getExternalFilesDir("kokoro-spike-assets")
            ?: throw AssertionError("Kokoro asset directory unavailable")
        val runtimeFactory = CountingKokoroRuntimeFactory(OrtKokoroRuntimeFactory())
        val engine = OnnxKokoroEngine(assetRoot, runtimeFactory)
        val sampler = AndroidProcessMemorySampler(intervalMs = 100)
        withKokoroRecordResources(
            closeSampler = { sampler.close() },
            snapshot = {
                val peak = sampler.snapshot()
                println("kokoro record memory samples=${peak.samples} peakPssKb=${peak.peakPssKb} peakNativePssKb=${peak.peakNativePssKb} peakNativeHeapBytes=${peak.peakNativeHeapBytes}")
            },
            closeEngine = { engine.close() },
        ) {
            sampler.markPhase("record:prepare")
            val start = System.nanoTime()
            engine.prepare().getOrThrow()
            println("kokoro record prepareMs=${(System.nanoTime() - start) / 1_000_000}")
            runRecordPartOfSpeechAcceptance(context, assetRoot, engine, runtimeFactory, AndroidAudioPlayer(context), sampler)
        }
    }

    private class CountingKokoroRuntimeFactory(
        private val delegate: KokoroRuntimeFactory,
    ) : KokoroRuntimeFactory {
        var frontendCalls = 0
            private set
        var lastTokens: LongArray? = null
            private set

        override fun create(paths: Map<String, File>, registerPartial: (AutoCloseable) -> Unit): KokoroRuntime {
            val runtime = delegate.create(paths, registerPartial)
            return object : KokoroRuntime {
                override fun frontend(accent: PronunciationAccent, ids: LongArray): Array<Array<FloatArray>> {
                    frontendCalls++
                    return runtime.frontend(accent, ids)
                }

                override fun audio(tokens: LongArray, style: FloatArray, speed: Float): FloatArray {
                    lastTokens = tokens.copyOf()
                    return runtime.audio(tokens, style, speed)
                }

                override fun close() = runtime.close()
            }
        }
    }

    private suspend fun runRecordPartOfSpeechAcceptance(
        context: Context,
        assetRoot: File,
        engine: OnnxKokoroEngine,
        runtimeFactory: CountingKokoroRuntimeFactory,
        player: AndroidAudioPlayer,
        sampler: AndroidProcessMemorySampler,
    ) {
        val fixtureBytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("kokoro-lexicon-fixture/record-pos.json").use { it.readBytes() }
        val entries = KokoroRecordFixture.parse(fixtureBytes)
        val vocabulary = KokoroFormats.vocab(
            File(assetRoot, OnnxKokoroEngine.DEFAULT_MANIFEST.getValue("config").relativePath).readText(),
        )
        val outputDirectory = context.getExternalFilesDir("kokoro-spike-listen-record-verb")
            ?: throw AssertionError("Kokoro record noun/verb output directory unavailable")

        val nounVerbDigests = mutableMapOf<String, Set<String>>()
        for ((accent, accentKey, label) in listOf(
            Triple(PronunciationAccent.American, "en-us", "us"),
            Triple(PronunciationAccent.British, "en-gb", "gb"),
        )) {
            val accentEntries = entries.getValue(accentKey)
            val cases = listOf(
                Triple(KokoroWordInput("record"), "noun", "record"),
                Triple(KokoroWordInput("record", "VB"), "verb", "record|VERB"),
            )
            val digests = mutableSetOf<String>()
            val pcmDigests = mutableSetOf<String>()
            for ((input, pos, expectedKey) in cases) {
                val resolution = KokoroLexiconResolver.resolve(input.text, accentKey, input.partOfSpeechTag, accentEntries)
                    ?: throw AssertionError("record fixture must hit lexicon for $accentKey/$pos")
                assertEquals(expectedKey, resolution.matchedKey)
                assertEquals(accentEntries.getValue(expectedKey), resolution.phonemes)
                val beforeCalls = runtimeFactory.frontendCalls
                sampler.markPhase("record:$pos:$label:synthesis")
                val start = System.nanoTime()
                val audio = engine.synthesize(input, accent, accentEntries).getOrThrow()
                val synthesisMs = (System.nanoTime() - start) / 1_000_000
                try {
                    assertEquals("lexicon hit must bypass real frontend", beforeCalls, runtimeFactory.frontendCalls)
                    val points = resolution.phonemes.codePoints().toArray()
                    val expectedTokens = longArrayOf(0) + points.map { vocabulary.getValue(it) }.toLongArray() + longArrayOf(0)
                    assertTrue("ORT must receive resolved phoneme tokens", expectedTokens.contentEquals(runtimeFactory.lastTokens))
                    assertWav(audio)
                    val digest = sha256(audio)
                    val pcmDigest = sha256(audio.copyOfRange(44, audio.size))
                    digests += digest
                    pcmDigests += pcmDigest
                    val exported = KokoroReviewAudioExporter.write(outputDirectory, "record-$pos-$label.wav", audio)
                    assertEquals(audio.size.toLong(), exported.length())
                    assertEquals(digest, sha256(exported.readBytes()))
                    sampler.markPhase("record:$pos:$label:playback")
                    val playbackStart = System.nanoTime()
                    val playback = try {
                        player.play(audio, "wav")
                    } finally {
                        audio.fill(0)
                    }
                    val playbackMs = (System.nanoTime() - playbackStart) / 1_000_000
                    assertEquals(AudioPlaybackResult.Played, playback)
                    assertTrue("playback buffer must be zeroed", audio.all { it == 0.toByte() })
                    println("kokoro record pos=$pos accent=$accentKey matchedKey=${resolution.matchedKey} phonemes=${resolution.phonemes} frontendCalls=${runtimeFactory.frontendCalls - beforeCalls} synthesisMs=$synthesisMs playbackMs=$playbackMs bytes=${exported.length()} sha256=$digest pcmSha256=$pcmDigest")
                } finally {
                    audio.fill(0)
                }
            }
            assertEquals("noun and verb WAV outputs must differ for $accent", 2, digests.size)
            assertEquals("noun and verb PCM outputs must differ for $accent", 2, pcmDigests.size)
            nounVerbDigests[accentKey] = digests
        }
        assertTrue("US and GB noun/verb digest summaries must differ", nounVerbDigests.getValue("en-us") != nounVerbDigests.getValue("en-gb"))
    }

    private fun assertWav(bytes: ByteArray) {
        assertTrue("WAV must contain header and PCM payload", bytes.size > 44)
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF length must match file length", bytes.size.toLong() - 8, header.getInt(4).toLong() and 0xffffffffL)

        var offset = 12
        var format: ByteArray? = null
        var dataOffset: Int? = null
        var dataSize: Long? = null
        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
            val chunkSize = header.getInt(offset + 4).toLong() and 0xffffffffL
            val chunkStart = offset + 8
            assertTrue("WAV chunk exceeds file bounds", chunkSize <= bytes.size - chunkStart)
            when (chunkId) {
                "fmt " -> format = bytes.copyOfRange(chunkStart, chunkStart + chunkSize.toInt())
                "data" -> {
                    dataOffset = offset
                    dataSize = chunkSize
                }
            }
            offset = chunkStart + chunkSize.toInt() + (chunkSize.toInt() and 1)
        }
        assertEquals("WAV chunks must end at file boundary", bytes.size, offset)
        val fmt = format ?: throw AssertionError("WAV is missing fmt chunk")
        assertTrue("fmt chunk must contain PCM format fields", fmt.size >= 16)
        val fmtHeader = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("WAV must be PCM", 1, fmtHeader.short.toInt())
        assertEquals("WAV must be mono", 1, fmtHeader.short.toInt())
        assertEquals("WAV must be 24 kHz", 24000, fmtHeader.int)
        assertEquals("WAV byte rate must be 48 kB/s", 48000, fmtHeader.int)
        assertEquals("WAV block alignment must be 2", 2, fmtHeader.short.toInt())
        assertEquals("WAV must be PCM16", 16, fmtHeader.short.toInt())
        val payloadOffset = (dataOffset ?: throw AssertionError("WAV is missing data chunk")) + 8
        assertEquals("WAV data chunk must match payload length", bytes.size.toLong() - payloadOffset, dataSize)
        assertTrue("WAV must contain non-silent PCM samples", bytes.copyOfRange(payloadOffset, bytes.size).any { it != 0.toByte() })
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
