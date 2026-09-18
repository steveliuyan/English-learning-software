package com.example.englishlearning.contracts

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProvider
import com.example.englishlearning.backup.domain.BackupProvider
import com.example.englishlearning.language.domain.OcrProvider
import com.example.englishlearning.language.domain.OcrRegion
import com.example.englishlearning.language.domain.OcrResult
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderContractTest {
    @Test
    fun `ai provider is replaceable and exposes capabilities only`() {
        val provider: AiProvider = FakeAiProvider(setOf(AiCapability.Text))

        assertEquals(setOf(AiCapability.Text), provider.capabilities())
        assertTrue(
            AiProvider::class.memberProperties
                .map { it.returnType.toString() }
                .none { it.containsForbiddenTransportType() },
        )
    }

    @Test
    fun `ocr result exposes text coordinates and confidence only`() {
        val provider: OcrProvider = FakeOcrProvider(
            OcrResult(
                text = "word",
                regions = listOf(OcrRegion(left = 1, top = 2, right = 3, bottom = 4, confidence = 0.9f)),
            ),
        )

        val result = provider.recognize()

        assertEquals("word", result.text)
        assertEquals(0.9f, result.regions.single().confidence)
        assertEquals(
            setOf("left", "top", "right", "bottom", "confidence"),
            OcrRegion::class.memberProperties.map { it.name }.toSet(),
        )
    }

    @Test
    fun `backup provider accepts logical snapshot and media manifest only`() {
        val parameters = BackupProvider::class.memberFunctions
            .single { it.name == "export" }
            .parameters
            .drop(1)
            .map { it.type.toString() }

        assertEquals(2, parameters.size)
        assertTrue(parameters.none { it.containsForbiddenBackupInput() })
    }

    private fun String.containsForbiddenTransportType(): Boolean =
        listOf("OkHttpClient", "Request", "Header", "HttpUrl", "URL").any(::contains)

    private fun String.containsForbiddenBackupInput(): Boolean =
        listOf("File", "Path", "SecretReference", "Key").any(::contains)
}

private class FakeAiProvider(
    private val supportedCapabilities: Set<AiCapability>,
) : AiProvider {
    override fun capabilities(): Set<AiCapability> = supportedCapabilities
}

private class FakeOcrProvider(
    private val result: OcrResult,
) : OcrProvider {
    override fun recognize(): OcrResult = result
}
