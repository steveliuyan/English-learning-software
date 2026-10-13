package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrImage
import com.example.englishlearning.language.domain.OcrOutcome
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MlKitTextRecognitionProviderTest {
    @Test
    fun `maps client blocks to regions with coordinates and confidence`() = runTest {
        val client =
            FakeMlClient(
                listOf(
                    MlClientBlock("first line", left = 0, top = 1, right = 50, bottom = 10, confidence = 0.8f),
                    MlClientBlock("second line", left = 0, top = 12, right = 40, bottom = 20, confidence = null),
                ),
            )
        val provider = MlKitTextRecognitionProvider(client)

        val outcome = provider.recognize(OcrImage(byteArrayOf(1)))

        assertIs<OcrOutcome.Success>(outcome)
        assertEquals(OcrEngineKind.MlKit, outcome.engine)
        assertEquals("first line\nsecond line", outcome.result.text)
        assertEquals(2, outcome.result.regions.size)
        assertEquals(0.8f, outcome.result.regions[0].confidence, 0.0001f)
        assertTrue(outcome.result.regions[1].confidence.isNaN())
        assertEquals(50, outcome.result.regions[0].right)
    }

    @Test
    fun `empty recognition is a success with empty text, not a failure`() = runTest {
        val provider = MlKitTextRecognitionProvider(FakeMlClient(emptyList()))

        val outcome = provider.recognize(OcrImage(byteArrayOf(1)))

        assertIs<OcrOutcome.Success>(outcome)
        assertEquals("", outcome.result.text)
    }

    @Test
    fun `missing dependency exception maps to Unavailable`() = runTest {
        val provider = MlKitTextRecognitionProvider(FakeMlClient(error = MissingDependencyException("no gms")))

        val outcome = provider.recognize(OcrImage(byteArrayOf(1)))

        assertEquals(OcrOutcome.Unavailable, outcome)
    }

    @Test
    fun `downloadable model exception nested in a cause chain maps to Unavailable`() = runTest {
        val wrapped = IllegalStateException("outer", RuntimeException(DownloadableModelException("not ready")))
        val provider = MlKitTextRecognitionProvider(FakeMlClient(error = wrapped))

        val outcome = provider.recognize(OcrImage(byteArrayOf(1)))

        assertEquals(OcrOutcome.Unavailable, outcome)
    }

    @Test
    fun `other exceptions map to RecognitionFailed with reason preserved, never Unavailable`() = runTest {
        val provider = MlKitTextRecognitionProvider(FakeMlClient(error = IllegalArgumentException("bad image")))

        val outcome = provider.recognize(OcrImage(byteArrayOf(1)))

        assertIs<OcrOutcome.RecognitionFailed>(outcome)
        assertEquals("bad image", outcome.reason)
    }

    private class MissingDependencyException(message: String) : RuntimeException(message)

    private class DownloadableModelException(message: String) : RuntimeException(message)

    private class FakeMlClient(
        private val blocks: List<MlClientBlock> = emptyList(),
        private val error: Throwable? = null,
    ) : MlTextRecognitionClient {
        override suspend fun recognize(image: OcrImage): List<MlClientBlock> {
            error?.let { throw it }
            return blocks
        }
    }
}
