package com.example.englishlearning.language.spike

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import com.example.englishlearning.language.domain.PronunciationAccent

class OrtKokoroRuntimeFactory : KokoroRuntimeFactory {
    override fun create(
        paths: Map<String, File>,
        registerPartial: (AutoCloseable) -> Unit,
    ): KokoroRuntime {
        val environment = OrtEnvironment.getEnvironment()
        val us = createSession(environment, paths.getValue("frontend-en-us"), registerPartial)
        validateFrontend(us)
        val gb = createSession(environment, paths.getValue("frontend-en-gb"), registerPartial)
        validateFrontend(gb)
        val kokoro = createSession(environment, paths.getValue("model"), registerPartial)
        validateKokoro(kokoro)
        return Runtime(environment, us, gb, kokoro)
    }

    private fun createSession(
        environment: OrtEnvironment,
        model: File,
        registerPartial: (AutoCloseable) -> Unit,
    ): OrtSession {
        require(model.isFile) { "ORT model missing: ${model.path}" }
        val options = OrtSession.SessionOptions()
        return try {
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            options.addCPU(false)
            val session = environment.createSession(model.path, options)
            try {
                registerPartial(session)
                session
            } catch (failure: Throwable) {
                try {
                    session.close()
                } catch (closeFailure: Throwable) {
                    if (closeFailure !== failure) failure.addSuppressed(closeFailure)
                }
                throw failure
            }
        } finally {
            options.close()
        }
    }

    private fun validateFrontend(session: OrtSession) {
        require(session.inputNames == setOf("input_ids")) { "unexpected frontend input names" }
        require(session.outputNames == setOf("logits")) { "unexpected frontend output names" }
        val input = tensorInfo(session.getInputInfo().getValue("input_ids"), "input_ids")
        require(input.type == OnnxJavaType.INT64 && input.getShape().size == 2 && input.getShape()[0] == 1L) {
            "unexpected frontend input contract"
        }
        val output = tensorInfo(session.getOutputInfo().getValue("logits"), "logits")
        val outputShape = output.getShape()
        require(output.type == OnnxJavaType.FLOAT && outputShape.size == 3 &&
            outputShape[0] == 1L && outputShape[2] == 116L) {
            "unexpected frontend output contract"
        }
    }

    private fun validateKokoro(session: OrtSession) {
        require(session.inputNames == setOf("tokens", "style", "speed")) {
            "unexpected Kokoro input names"
        }
        require(session.outputNames == setOf("audio")) { "unexpected Kokoro output names" }
        val tokens = tensorInfo(session.getInputInfo().getValue("tokens"), "tokens")
        val tokensShape = tokens.getShape()
        require(tokens.type == OnnxJavaType.INT64 && tokensShape.size == 2 && tokensShape[0] == 1L) {
            "unexpected Kokoro tokens contract"
        }
        val style = tensorInfo(session.getInputInfo().getValue("style"), "style")
        require(style.type == OnnxJavaType.FLOAT && style.getShape().contentEquals(longArrayOf(1L, 256L))) {
            "unexpected Kokoro style contract"
        }
        val speed = tensorInfo(session.getInputInfo().getValue("speed"), "speed")
        require(speed.type == OnnxJavaType.FLOAT && speed.getShape().contentEquals(longArrayOf(1L))) {
            "unexpected Kokoro speed contract"
        }
        val audio = tensorInfo(session.getOutputInfo().getValue("audio"), "audio")
        require(audio.type == OnnxJavaType.FLOAT) { "unexpected Kokoro audio contract" }
    }

    private fun tensorInfo(node: ai.onnxruntime.NodeInfo, name: String): TensorInfo {
        return node.info as? TensorInfo ?: error("$name is not a tensor")
    }

    private class Runtime(
        private val environment: OrtEnvironment,
        private val us: OrtSession,
        private val gb: OrtSession,
        private val kokoro: OrtSession,
    ) : KokoroRuntime {
        override fun frontend(
            accent: PronunciationAccent,
            ids: LongArray,
        ): Array<Array<FloatArray>> {
            require(ids.isNotEmpty()) { "frontend input must not be empty" }
            val session = if (accent == PronunciationAccent.American) us else gb
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(ids),
                longArrayOf(1L, ids.size.toLong()),
            ).use { input ->
                session.run(mapOf("input_ids" to input)).use { result ->
                    val output = result.get("logits").orElseThrow { error("missing logits output") }
                    require(output is OnnxTensor) { "logits output is not a tensor" }
                    output.use { tensor ->
                        val info = tensor.info as? TensorInfo ?: error("logits output is not a tensor")
                        val shape = info.getShape()
                        require(info.type == OnnxJavaType.FLOAT && shape.size == 3 &&
                            shape[0] == 1L && shape[2] == 116L && shape[1] > 0L) {
                            "invalid logits output contract"
                        }
                        val rows = shape[1].toInt()
                        val values = tensor.getFloatBuffer()
                        require(values.remaining() == rows * 116) { "invalid logits output size" }
                        return Array(1) {
                            Array(rows) {
                                FloatArray(116) { values.get() }
                            }
                        }
                    }
                }
            }
        }

        override fun audio(tokens: LongArray, style: FloatArray, speed: Float): FloatArray {
            require(tokens.isNotEmpty() && style.size == 256 && speed.isFinite()) {
                "invalid Kokoro input"
            }
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(tokens),
                longArrayOf(1L, tokens.size.toLong()),
            ).use { tokenTensor ->
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(style),
                    longArrayOf(1L, 256L),
                ).use { styleTensor ->
                    OnnxTensor.createTensor(
                        environment,
                        FloatBuffer.wrap(floatArrayOf(speed)),
                        longArrayOf(1L),
                    ).use { speedTensor ->
                        val inputs = mapOf(
                            "tokens" to tokenTensor,
                            "style" to styleTensor,
                            "speed" to speedTensor,
                        )
                        kokoro.run(inputs).use { result ->
                            val output = result.get("audio").orElseThrow { error("missing audio output") }
                            require(output is OnnxTensor) { "audio output is not a tensor" }
                            output.use { tensor ->
                                val info = tensor.info as? TensorInfo ?: error("audio output is not a tensor")
                                require(info.type == OnnxJavaType.FLOAT) { "audio output is not float" }
                                val values = tensor.getFloatBuffer()
                                return FloatArray(values.remaining()) { values.get() }
                            }
                        }
                    }
                }
            }
        }

        override fun close() {
            var failure: Throwable? = null
            listOf(kokoro, gb, us).forEach { session ->
                try {
                    session.close()
                } catch (error: Throwable) {
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        }
    }
}
