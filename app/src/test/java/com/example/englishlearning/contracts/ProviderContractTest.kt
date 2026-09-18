package com.example.englishlearning.contracts

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProvider
import com.example.englishlearning.backup.domain.BackupProvider
import com.example.englishlearning.core.export.LogicalSnapshot
import com.example.englishlearning.core.export.MediaManifestItem
import com.example.englishlearning.language.domain.OcrProvider
import com.example.englishlearning.language.domain.OcrRegion
import com.example.englishlearning.language.domain.OcrResult
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderContractTest {
    @Test
    fun `ai provider is replaceable and exposes capabilities only`() {
        val provider: AiProvider = FakeAiProvider(setOf(AiCapability.Text))
        val functions = AiProvider::class.memberFunctions.filter { it.name != "equals" && it.name != "hashCode" && it.name != "toString" }

        assertEquals(setOf(AiCapability.Text), provider.capabilities())
        assertEquals(1, functions.size)
        assertEquals("capabilities", functions.single().name)
        assertEquals(1, functions.single().parameters.size)
        assertEquals(Set::class, functions.single().returnType.classifier)
        assertEquals(AiCapability::class, functions.single().returnType.arguments.single().type?.classifier)
        assertSafeTypeGraph(functions.single().returnType, forbiddenTransportTokens)
        assertTrue(AiProvider::class.memberProperties.isEmpty())
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
    fun `backup provider accepts exact safe logical inputs and safe output`() {
        val export = BackupProvider::class.memberFunctions.single { it.name == "export" }
        val inputs = export.parameters.drop(1).map { it.type }

        assertEquals(2, inputs.size)
        assertEquals(LogicalSnapshot::class, inputs[0].classifier)
        assertEquals(List::class, inputs[1].classifier)
        assertEquals(MediaManifestItem::class, inputs[1].arguments.single().type?.classifier)
        (inputs + export.returnType).forEach { type -> assertSafeTypeGraph(type, forbiddenBackupTokens) }
    }

    private fun assertSafeTypeGraph(
        root: KType,
        forbiddenTokens: List<String>,
    ) {
        val inspectedTypes = mutableSetOf<KType>()
        val inspectedClasses = mutableSetOf<KClass<*>>()

        fun inspect(type: KType) {
            if (!inspectedTypes.add(type)) return
            type.arguments.mapNotNull { it.type }.forEach(::inspect)
            val classifier = type.classifier as? KClass<*> ?: return
            assertTrue(
                forbiddenTokens.none { token -> classifier.qualifiedName.orEmpty().contains(token, ignoreCase = true) },
                "Public contract type ${classifier.qualifiedName} must not expose forbidden material",
            )
            if (!inspectedClasses.add(classifier)) return
            classifier.memberProperties.forEach { property ->
                assertTrue(
                    forbiddenTokens.none { token -> property.name.contains(token, ignoreCase = true) },
                    "Public contract property ${classifier.qualifiedName}.${property.name} must not expose forbidden material",
                )
                inspect(property.returnType)
            }
        }

        inspect(root)
    }

    private companion object {
        val forbiddenTransportTokens = listOf("okhttp", "request", "header", "httpurl", "url")
        val forbiddenBackupTokens = listOf(
            "apikey",
            "authorization",
            "password",
            "privatekey",
            "secret",
            "file",
            "path",
            "database",
        )
    }
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
