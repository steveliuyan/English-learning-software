package com.example.englishlearning.language.infrastructure

import android.content.Context
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.example.englishlearning.language.domain.PronunciationAccent
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
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

data class KokoroAsset(val relativePath: String, val size: Long, val sha256: String)

interface KokoroRuntime : AutoCloseable {
    fun frontend(accent: PronunciationAccent, ids: LongArray): Array<Array<FloatArray>>
    fun audio(tokens: LongArray, style: FloatArray, speed: Float): FloatArray
}

fun interface KokoroRuntimeFactory {
    fun create(paths: Map<String, File>, registerPartial: (AutoCloseable) -> Unit): KokoroRuntime
}

class KokoroAndroidEngine(
    context: Context,
    private val factory: KokoroRuntimeFactory = OrtKokoroRuntimeFactory(),
    private val manifest: Map<String, KokoroAsset> = DEFAULT_MANIFEST,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : KokoroEngine {
    private val assetRoot = AssetMaterializer(context.applicationContext, manifest)
    private val mutex = Mutex()
    @Volatile private var ready = false
    private var closed = false
    private var runtime: KokoroRuntime? = null
    private var vocabulary: Map<Int, Long> = emptyMap()
    private var voiceData: Map<String, FloatArray> = emptyMap()

    companion object {
        private const val ASSET_ROOT = "kokoro-production"
        val DEFAULT_MANIFEST: Map<String, KokoroAsset> = mapOf(
            "model" to KokoroAsset("model-files-v1.0/kokoro-v1.0.int8.onnx", 92361271L, "6e742170d309016e5891a994e1ce1559c702a2ccd0075e67ef7157974f6406cb"),
            "voices" to KokoroAsset("model-files-v1.0/voices-v1.0.bin", 28214398L, "bca610b8308e8d99f32e6fe4197e7ec01679264efed0cac9140fe9c29f1fbf7d"),
            "config" to KokoroAsset("phonemis-audit/kmp-runtime-kokoro-v1.0-config.json", 2351L, "5abb01e2403b072bf03d04fde160443e209d7a0dad49a423be15196b9b43c17f"),
            "frontend-en-us" to KokoroAsset("phonemis-audit/phonemizer_en_us.onnx", 7105527L, "479cd9d89819a60c4f8609dbb116ae5b362d46c0c592b97605968cbaef1e7bc6"),
            "frontend-en-gb" to KokoroAsset("phonemis-audit/phonemizer_en_gb.onnx", 7105527L, "065baba624db8391905e53c78706a12c5f4cba12a77e62a90900b2113bdb17e7"),
        )
    }

    override suspend fun prepare(): Result<Unit> = withContext(dispatcher) {
        mutex.withLock {
            val partial = mutableListOf<AutoCloseable>()
            try {
                check(!closed) { "Engine已关闭" }
                if (!ready) {
                    val paths = assetRoot.materialize()
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
                cleanup(partial, failure)
                if (failure is Error) throw failure
                Result.failure(failure)
            }
        }
    }

    override fun isReady(): Boolean = ready

    override suspend fun synthesize(text: String, accent: PronunciationAccent): Result<ByteArray> = withOwnedAudio(dispatcher) {
        mutex.withLock {
            try {
                val current = runtime
                check(ready && !closed && current != null) { "Engine未就绪" }
                val phonemes = KokoroFormats.decode(current.frontend(accent, KokoroFormats.graphemes(text)))
                val points = phonemes.codePoints().toArray()
                require(points.size in 1..509) { "音素长度不合法" }
                val tokens = LongArray(points.size + 2)
                points.forEachIndexed { index, point ->
                    tokens[index + 1] = vocabulary[point] ?: throw IllegalArgumentException("未知音素")
                }
                val voice = voiceData.getValue(if (accent == PronunciationAccent.American) "af_heart.npy" else "bf_emma.npy")
                val style = voice.copyOfRange(points.size * 256, points.size * 256 + 256)
                currentCoroutineContext().ensureActive()
                val pcm = current.audio(tokens, style, 1f)
                var wav: ByteArray? = null
                try {
                    currentCoroutineContext().ensureActive()
                    wav = KokoroFormats.wav(pcm)
                    currentCoroutineContext().ensureActive()
                    Result.success(wav)
                } catch (cancelled: CancellationException) {
                    wav?.fill(0)
                    throw cancelled
                } finally {
                    pcm.fill(0f)
                }
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
            try { resource.close() } catch (closeFailure: Throwable) {
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
            try { resource.close() } catch (closeFailure: Throwable) {
                if (primary != null && closeFailure !== primary) primary.addSuppressed(closeFailure)
                else if (primary == null) throw closeFailure
            }
        }
    }
}

private class AssetMaterializer(
    private val context: Context,
    private val manifest: Map<String, KokoroAsset>,
) {
    private val root = File(context.cacheDir, "kokoro-production")

    fun materialize(): Map<String, File> {
        val assets = context.assets
        return manifest.mapValues { (_, spec) ->
            val target = File(root, spec.relativePath)
            target.parentFile?.mkdirs()
            assets.open("kokoro-production/${spec.relativePath}").use { input -> target.outputStream().use { input.copyTo(it) } }
            require(target.length() == spec.size) { "资产长度不匹配" }
            val actual = MessageDigest.getInstance("SHA-256").digest(target.readBytes()).joinToString("") { "%02x".format(it) }
            require(actual == spec.sha256) { "资产摘要不匹配" }
            target
        }
    }
}

class OrtKokoroRuntimeFactory : KokoroRuntimeFactory {
    override fun create(paths: Map<String, File>, registerPartial: (AutoCloseable) -> Unit): KokoroRuntime {
        val environment = OrtEnvironment.getEnvironment()
        registerPartial(environment)
        val us = createSession(environment, paths.getValue("frontend-en-us"), registerPartial).also(::validateFrontend)
        val gb = createSession(environment, paths.getValue("frontend-en-gb"), registerPartial).also(::validateFrontend)
        val kokoro = createSession(environment, paths.getValue("model"), registerPartial).also(::validateKokoro)
        return Runtime(environment, us, gb, kokoro)
    }

    private fun createSession(environment: OrtEnvironment, model: File, registerPartial: (AutoCloseable) -> Unit): OrtSession {
        val options = OrtSession.SessionOptions()
        return try {
            options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1); options.addCPU(false)
            environment.createSession(model.path, options).also(registerPartial)
        } finally { options.close() }
    }

    private fun validateFrontend(session: OrtSession) {
        require(session.inputNames == setOf("input_ids") && session.outputNames == setOf("logits")) { "unexpected frontend contract" }
        val input = session.getInputInfo().getValue("input_ids").info as? TensorInfo ?: error("input_ids is not tensor")
        require(input.type == OnnxJavaType.INT64 && input.getShape().size == 2 && input.getShape()[0] == 1L) { "unexpected frontend input contract" }
        val output = session.getOutputInfo().getValue("logits").info as? TensorInfo ?: error("logits is not tensor")
        val outputShape = output.getShape()
        require(output.type == OnnxJavaType.FLOAT && outputShape.size == 3 && outputShape[0] == 1L && outputShape[2] == 116L) {
            "unexpected frontend output contract"
        }
    }

    private fun validateKokoro(session: OrtSession) {
        require(session.inputNames == setOf("tokens", "style", "speed") && session.outputNames == setOf("audio")) { "unexpected Kokoro contract" }
    }

    private class Runtime(private val environment: OrtEnvironment, private val us: OrtSession, private val gb: OrtSession, private val kokoro: OrtSession) : KokoroRuntime {
        override fun frontend(accent: PronunciationAccent, ids: LongArray): Array<Array<FloatArray>> {
            val session = if (accent == PronunciationAccent.American) us else gb
            OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { input ->
                session.run(mapOf("input_ids" to input)).use { result ->
                    val tensor = result.get("logits").orElseThrow { error("missing logits") } as OnnxTensor
                    tensor.use { output ->
                        val info = output.info as TensorInfo; val shape = info.getShape(); val values = output.getFloatBuffer()
                        return Array(1) { Array(shape[1].toInt()) { FloatArray(116) { values.get() } } }
                    }
                }
            }
        }

        override fun audio(tokens: LongArray, style: FloatArray, speed: Float): FloatArray {
            OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong())).use { tokenTensor ->
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(style), longArrayOf(1, 256)).use { styleTensor ->
                    OnnxTensor.createTensor(environment, FloatBuffer.wrap(floatArrayOf(speed)), longArrayOf(1)).use { speedTensor ->
                        kokoro.run(mapOf("tokens" to tokenTensor, "style" to styleTensor, "speed" to speedTensor)).use { result ->
                            val output = result.get("audio").orElseThrow { error("missing audio") } as OnnxTensor
                            output.use {
                                val values = it.getFloatBuffer()
                                return FloatArray(values.remaining()) { values.get() }
                            }
                        }
                    }
                }
            }
        }

        override fun close() {
            var failure: Throwable? = null
            listOf(kokoro, gb, us, environment).forEach { resource ->
                try {
                    resource.close()
                } catch (error: Throwable) {
                    if (failure == null) failure = error else failure?.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        }
    }
}
