package com.example.englishlearning.language.spike

import com.example.englishlearning.language.domain.PronunciationAccent
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface KokoroAndroidEngine {
    suspend fun prepare(): Result<Unit>
    fun isReady(): Boolean
    suspend fun synthesize(text: String, accent: PronunciationAccent): Result<ByteArray>
    suspend fun synthesize(
        input: KokoroWordInput,
        accent: PronunciationAccent,
        lexicon: Map<String, String> = emptyMap(),
    ): Result<ByteArray>
    suspend fun close()
}

data class KokoroAsset(val relativePath: String, val size: Long, val sha256: String)

interface KokoroRuntime : AutoCloseable {
    fun frontend(accent: PronunciationAccent, ids: LongArray): Array<Array<FloatArray>>
    fun audio(tokens: LongArray, style: FloatArray, speed: Float): FloatArray
}

fun interface KokoroRuntimeFactory {
    fun create(paths: Map<String, File>, registerPartial: (AutoCloseable) -> Unit): KokoroRuntime
}

class OnnxKokoroEngine(
    private val assetRoot: File,
    private val factory: KokoroRuntimeFactory,
    private val manifest: Map<String, KokoroAsset> = DEFAULT_MANIFEST,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : KokoroAndroidEngine {
    companion object {
        val DEFAULT_MANIFEST: Map<String, KokoroAsset> = mapOf(
            // 固定审核资产；仅允许测试/外部注入替换manifest。
            "model" to KokoroAsset("model-files-v1.0/kokoro-v1.0.int8.onnx", 92361271L, "6e742170d309016e5891a994e1ce1559c702a2ccd0075e67ef7157974f6406cb"),
            "voices" to KokoroAsset("model-files-v1.0/voices-v1.0.bin", 28214398L, "bca610b8308e8d99f32e6fe4197e7ec01679264efed0cac9140fe9c29f1fbf7d"),
            "config" to KokoroAsset("phonemis-audit/kmp-runtime-kokoro-v1.0-config.json", 2351L, "5abb01e2403b072bf03d04fde160443e209d7a0dad49a423be15196b9b43c17f"),
            "frontend-en-us" to KokoroAsset("phonemis-audit/phonemizer_en_us.onnx", 7105527L, "479cd9d89819a60c4f8609dbb116ae5b362d46c0c592b97605968cbaef1e7bc6"),
            "frontend-en-gb" to KokoroAsset("phonemis-audit/phonemizer_en_gb.onnx", 7105527L, "065baba624db8391905e53c78706a12c5f4cba12a77e62a90900b2113bdb17e7"),
        )
    }

    private val mutex = Mutex()
    @Volatile private var ready = false
    private var closed = false
    private var runtime: KokoroRuntime? = null
    private var vocabulary: Map<Int, Long> = emptyMap()
    private var voiceData: Map<String, FloatArray> = emptyMap()

    override suspend fun prepare(): Result<Unit> = withContext(dispatcher) {
        mutex.withLock {
            val partial = mutableListOf<AutoCloseable>()
            try {
                check(!closed) { "Engine已关闭" }
                if (!ready) {
                    val paths = manifest.mapValues { (_, spec) ->
                        File(assetRoot, spec.relativePath).also { verifyAsset(it, spec) }
                    }
                    val parsedVocab = KokoroFormats.vocab(paths.getValue("config").readText())
                    val parsedVoices = KokoroFormats.voices(paths.getValue("voices"))
                    val created = factory.create(paths) { partial += it }
                    vocabulary = parsedVocab
                    voiceData = parsedVoices
                    runtime = created
                    ready = true
                }
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                cleanup(partial, cancelled)
                throw cancelled
            } catch (failure: Throwable) {
                if (failure is Error) {
                    cleanup(partial, failure)
                    throw failure
                }
                cleanup(partial, failure)
                Result.failure(failure)
            }
        }
    }

    override fun isReady(): Boolean = ready

    override suspend fun synthesize(text: String, accent: PronunciationAccent): Result<ByteArray> =
        synthesize(KokoroWordInput(text), accent)

    override suspend fun synthesize(
        input: KokoroWordInput,
        accent: PronunciationAccent,
        lexicon: Map<String, String>,
    ): Result<ByteArray> = withContext(dispatcher) {
        mutex.withLock {
            try {
                val current = runtime
                check(ready && !closed && current != null) { "Engine未就绪" }
                val accentKey = if (accent == PronunciationAccent.American) "en-us" else "en-gb"
                val resolution = KokoroLexiconResolver.resolve(input.text, accentKey, input.partOfSpeechTag, lexicon)
                val phonemes = resolution?.phonemes ?: run {
                    val ids = KokoroFormats.graphemes(input.text)
                    currentCoroutineContext().ensureActive()
                    val logits = current.frontend(accent, ids)
                    currentCoroutineContext().ensureActive()
                    KokoroFormats.decode(logits)
                }
                val points = phonemes.codePoints().toArray()
                require(points.size in 1..509) { "音素长度不合法" }
                val tokens = LongArray(points.size + 2)
                points.forEachIndexed { index, point -> tokens[index + 1] = vocabulary[point] ?: throw IllegalArgumentException("未知音素") }
                val voice = voiceData.getValue(if (accent == PronunciationAccent.American) "af_heart.npy" else "bf_emma.npy")
                val start = points.size * 256
                val style = voice.copyOfRange(start, start + 256)
                currentCoroutineContext().ensureActive()
                val audio = current.audio(tokens, style, 1f)
                currentCoroutineContext().ensureActive()
                Result.success(KokoroFormats.wav(audio))
            } catch (cancelled: CancellationException) {
                releaseRuntime(cancelled)
                throw cancelled
            } catch (failure: Throwable) {
                releaseRuntime(failure)
                if (failure is Error) throw failure
                Result.failure(failure)
            }
        }
    }

    override suspend fun close(): Unit = withContext(NonCancellable + dispatcher) {
        mutex.withLock {
            closed = true
            ready = false
            releaseRuntime()
        }
    }

    private fun cleanup(resources: List<AutoCloseable>, primary: Throwable) {
        resources.asReversed().forEach { resource ->
            try {
                resource.close()
            } catch (closeFailure: Throwable) {
                if (closeFailure !== primary) primary.addSuppressed(closeFailure)
            }
        }
    }

    private fun releaseRuntime(primary: Throwable? = null) {
        ready = false
        val resource = runtime
        runtime = null
        vocabulary = emptyMap()
        voiceData = emptyMap()
        if (resource != null) {
            try {
                resource.close()
            } catch (closeFailure: Throwable) {
                if (primary != null && closeFailure !== primary) {
                    primary.addSuppressed(closeFailure)
                } else if (primary == null) {
                    throw closeFailure
                }
            }
        }
    }

    private fun verifyAsset(file: File, spec: KokoroAsset) {
        require(file.isFile && file.length() == spec.size) { "资产长度不匹配" }
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        val actual = hash.digest().joinToString("") { "%02x".format(it) }
        require(actual == spec.sha256) { "资产摘要不匹配" }
    }
}
