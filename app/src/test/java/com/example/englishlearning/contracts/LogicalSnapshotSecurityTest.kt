package com.example.englishlearning.contracts

import com.example.englishlearning.core.export.LogicalSnapshot
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertTrue

class LogicalSnapshotSecurityTest {
    @Test
    fun `logical export dto graph contains no credential fields or secret references`() {
        val forbidden = listOf("apikey", "authorization", "password", "privatekey", "secret")
        val inspectedTypes = mutableSetOf<KClass<*>>()

        fun inspect(type: KType) {
            val classifier = type.classifier as? KClass<*> ?: return
            if (!inspectedTypes.add(classifier)) return

            assertTrue(
                forbidden.none { forbiddenToken -> classifier.qualifiedName.orEmpty().contains(forbiddenToken, ignoreCase = true) },
                "Export type ${classifier.qualifiedName} must not expose credential material",
            )
            classifier.memberProperties.forEach { property ->
                assertTrue(
                    forbidden.none { forbiddenToken -> property.name.contains(forbiddenToken, ignoreCase = true) },
                    "Export property ${classifier.qualifiedName}.${property.name} must not expose credential material",
                )
                inspect(property.returnType)
            }
        }

        inspect(LogicalSnapshot::class.createType())
    }
}
