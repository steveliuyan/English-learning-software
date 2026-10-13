package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrImage
import com.example.englishlearning.language.domain.OcrModelAssetStatus
import com.example.englishlearning.language.domain.OcrOutcome
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TesseractOcrProviderTest {
    @Test
    fun `model not installed is Unavailable and the engine is never invoked`() = runTest {
        val runner = RecordingRunner(TesseractRunResult.Blocks(listOf(line("x")), meanConfidence = 90f))
        val provider = TesseractOcrProvider(assetsStatus { OcrModelAssetStatus.NotInstalled }, runner)

        val outcome = provider.recognize(OcrImage(byteArrayOf(1)))

        assertEquals(OcrOutcome.Unavailable, outcome)
        assertEquals(0, runner.calls)
    }

    @Test
    fun `installed model maps lines with normalized mean confidence`() = runTest {
        val runner =
            RecordingRunner(
                TesseractRunResult.Blocks(
                    listOf(
                        line("hello", left = 1, top = 2, right = 30, bottom = 9),
                        line("world", left = 1, top = 12, right = 28, bottom = 19),
                    ),
                    meanConfidence = 87f,
                ),
            )
        val provider = TesseractOcrProvider(assetsStatus { OcrModelAssetStatus.Installed(4_113_088L) }, runner)

        val outcome = provider.recognize(OcrImage(byteArrayOf(1)))

        assertIs<OcrOutcome.Success>(outcome)
        assertEquals(OcrEngineKind.Tesseract, outcome.engine)
        assertEquals("hello\nworld", outcome.result.text)
        assertEquals(2, outcome.result.regions.size)
        assertEquals(0.87f, outcome.result.regions[0].confidence, 0.0001f)
    }

    @Test
    fun `empty lines are a success with empty text`() = runTest {
        val provider =
            TesseractOcrProvider(
                assetsStatus { OcrModelAssetStatus.Installed(1L) },
                RecordingRunner(TesseractRunResult.Blocks(emptyList(), meanConfidence = 0f)),
            )

        val outcome = provider.recognize(OcrImage(byteArrayOf(1)))

        assertIs<OcrOutcome.Success>(outcome)
        assertEquals("", outcome.result.text)
    }

    @Test
    fun `engine init unavailability is Unavailable`() = runTest {
        val provider =
            TesseractOcrProvider(
                assetsStatus { OcrModelAssetStatus.Installed(1L) },
                RecordingRunner(TesseractRunResult.InitUnavailable),
            )

        val outcome = provider.recognize(OcrImage(byteArrayOf(1)))

        assertEquals(OcrOutcome.Unavailable, outcome)
    }

    @Test
    fun `engine exception is RecognitionFailed`() = runTest {
        val runner = RecordingRunner(error = RuntimeException("native crash guarded"))
        val provider = TesseractOcrProvider(assetsStatus { OcrModelAssetStatus.Installed(1L) }, runner)

        val outcome = provider.recognize(OcrImage(byteArrayOf(1)))

        assertIs<OcrOutcome.RecognitionFailed>(outcome)
        assertEquals("native crash guarded", outcome.reason)
    }

    private fun line(
        text: String,
        left: Int = 0,
        top: Int = 0,
        right: Int = 10,
        bottom: Int = 10,
    ) = TesseractLine(text, left, top, right, bottom)

    private fun assetsStatus(block: suspend () -> OcrModelAssetStatus) =
        object : com.example.englishlearning.language.domain.EnglishOcrModelAssets {
            override suspend fun status(): OcrModelAssetStatus = block()

            override suspend fun install(
                onProgress: (Float) -> Unit,
            ): com.example.englishlearning.language.domain.OcrModelAssetInstallOutcome =
                error("not used")

            override suspend fun uninstall() = Unit
        }

    private class RecordingRunner(
        private val result: TesseractRunResult? = null,
        private val error: Throwable? = null,
    ) : TesseractEngineRunner {
        var calls = 0
            private set

        override suspend fun recognize(
            image: OcrImage,
            traineddataFile: java.io.File,
        ): TesseractRunResult {
            calls += 1
            error?.let { throw it }
            return result ?: error("no scripted result")
        }
    }
}
