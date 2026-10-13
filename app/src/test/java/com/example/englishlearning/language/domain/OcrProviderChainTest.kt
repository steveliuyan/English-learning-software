package com.example.englishlearning.language.domain

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OcrProviderChainTest {
    @Test
    fun `uses mlkit first when it succeeds and never calls tesseract`() = runTest {
        val tesseract = ScriptedOcrProvider(OcrEngineKind.Tesseract, listOf(OcrOutcome.Unavailable))
        val mlKit =
            ScriptedOcrProvider(
                OcrEngineKind.MlKit,
                listOf(OcrOutcome.Success(result("hello"), OcrEngineKind.MlKit)),
            )
        val chain = OcrProviderChain(listOf(mlKit, tesseract))

        val outcome = chain.recognize(image())

        assertIs<OcrChainOutcome.Recognized>(outcome)
        assertEquals(OcrEngineKind.MlKit, outcome.engine)
        assertEquals("hello", outcome.result.text)
        assertEquals(0, tesseract.calls)
    }

    @Test
    fun `falls back to tesseract when mlkit reports unavailable`() = runTest {
        val tesseract =
            ScriptedOcrProvider(
                OcrEngineKind.Tesseract,
                listOf(OcrOutcome.Success(result("fallback text"), OcrEngineKind.Tesseract)),
            )
        val mlKit = ScriptedOcrProvider(OcrEngineKind.MlKit, listOf(OcrOutcome.Unavailable))
        val chain = OcrProviderChain(listOf(mlKit, tesseract))

        val outcome = chain.recognize(image())

        assertIs<OcrChainOutcome.Recognized>(outcome)
        assertEquals(OcrEngineKind.Tesseract, outcome.engine)
        assertEquals("fallback text", outcome.result.text)
        assertEquals(1, tesseract.calls)
    }

    @Test
    fun `both providers unavailable yields the fixed OcrUnavailable category, not a recognition failure`() =
        runTest {
            val mlKit = ScriptedOcrProvider(OcrEngineKind.MlKit, listOf(OcrOutcome.Unavailable))
            val tesseract = ScriptedOcrProvider(OcrEngineKind.Tesseract, listOf(OcrOutcome.Unavailable))
            val chain = OcrProviderChain(listOf(mlKit, tesseract))

            val outcome = chain.recognize(image())

            // AC3-04:「OCR 不可用」是独立类别；不得与识别失败或翻译失败混同
            assertEquals(OcrChainOutcome.OcrUnavailable, outcome)
        }

    @Test
    fun `an empty provider chain is OcrUnavailable`() =
        runTest {
            val outcome = OcrProviderChain(emptyList()).recognize(image())

            assertEquals(OcrChainOutcome.OcrUnavailable, outcome)
        }

    @Test
    fun `mlkit recognition failure still tries tesseract and reports the working engine`() = runTest {
        val tesseract =
            ScriptedOcrProvider(
                OcrEngineKind.Tesseract,
                listOf(OcrOutcome.Success(result("from tesseract"), OcrEngineKind.Tesseract)),
            )
        val mlKit =
            ScriptedOcrProvider(
                OcrEngineKind.MlKit,
                listOf(OcrOutcome.RecognitionFailed("bad image")),
            )
        val chain = OcrProviderChain(listOf(mlKit, tesseract))

        val outcome = chain.recognize(image())

        assertIs<OcrChainOutcome.Recognized>(outcome)
        assertEquals(OcrEngineKind.Tesseract, outcome.engine)
        assertEquals(1, tesseract.calls)
    }

    @Test
    fun `all attempted providers failing yields RecognitionFailed with every reason preserved`() =
        runTest {
            val mlKit =
                ScriptedOcrProvider(
                    OcrEngineKind.MlKit,
                    listOf(OcrOutcome.RecognitionFailed("mlkit reason")),
                )
            val tesseract =
                ScriptedOcrProvider(
                    OcrEngineKind.Tesseract,
                    listOf(OcrOutcome.RecognitionFailed("tesseract reason")),
                )
            val chain = OcrProviderChain(listOf(mlKit, tesseract))

            val outcome = chain.recognize(image())

            assertIs<OcrChainOutcome.RecognitionFailed>(outcome)
            assertTrue(outcome.reasons.containsKey(OcrEngineKind.MlKit))
            assertTrue(outcome.reasons.containsKey(OcrEngineKind.Tesseract))
        }

    @Test
    fun `unavailable after a failed attempt stays a recognition failure category, not unavailable`() =
        runTest {
            val mlKit =
                ScriptedOcrProvider(
                    OcrEngineKind.MlKit,
                    listOf(OcrOutcome.RecognitionFailed("mlkit reason")),
                )
            val tesseract = ScriptedOcrProvider(OcrEngineKind.Tesseract, listOf(OcrOutcome.Unavailable))
            val chain = OcrProviderChain(listOf(mlKit, tesseract))

            val outcome = chain.recognize(image())

            assertIs<OcrChainOutcome.RecognitionFailed>(outcome)
            assertEquals(listOf(OcrEngineKind.MlKit), outcome.reasons.keys.toList())
        }

    private fun result(text: String) =
        OcrResult(
            text = text,
            regions = listOf(OcrRegion(left = 0, top = 0, right = 10, bottom = 10, confidence = 0.9f)),
        )

    private fun image() = OcrImage(byteArrayOf(1, 2, 3))

    private class ScriptedOcrProvider(
        override val kind: OcrEngineKind,
        outcomes: List<OcrOutcome>,
    ) : OcrProvider {
        private val queue = ArrayDeque(outcomes)

        var calls = 0
            private set

        override suspend fun recognize(image: OcrImage): OcrOutcome {
            calls += 1
            return queue.removeFirstOrNull() ?: OcrOutcome.Unavailable
        }
    }
}
