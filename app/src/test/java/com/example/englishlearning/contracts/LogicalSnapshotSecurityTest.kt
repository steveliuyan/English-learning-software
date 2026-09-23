package com.example.englishlearning.contracts

import com.example.englishlearning.backup.domain.BackupExport
import com.example.englishlearning.backup.domain.BackupProvider
import com.example.englishlearning.core.export.ExportProfileRecord
import com.example.englishlearning.core.export.LogicalSnapshot
import com.example.englishlearning.core.export.MediaManifestItem
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogicalSnapshotSecurityTest {
    @Test
    fun `logical snapshot permits only explicit export record fields`() {
        assertEquals(
            setOf("profileId", "displayName", "createdAtEpochMillis"),
            ExportProfileRecord::class.memberProperties.map { it.name }.toSet(),
        )
        assertTrue(
            exportTypeGraph(LogicalSnapshot::class.createType()).none { type ->
                type.classifier == Map::class
            },
            "LogicalSnapshot must not expose an unconstrained Map that can carry credential names or values",
        )
    }

    @Test
    fun `logical export dto graph contains no credential fields or secret references`() {
        assertSecureTypeGraph(LogicalSnapshot::class.createType())
    }

    @Test
    fun `backup boundary recursively contains only safe logical dto types`() {
        val export = BackupProvider::class.memberFunctions.single { it.name == "export" }
        val publicTypes = export.parameters.drop(1).map { it.type } + export.returnType

        assertEquals(
            listOf(LogicalSnapshot::class, List::class, BackupExport::class),
            listOf(publicTypes[0].classifier, publicTypes[1].classifier, publicTypes[2].classifier),
        )
        assertEquals(MediaManifestItem::class, publicTypes[1].arguments.single().type?.classifier)
        publicTypes.forEach(::assertSecureTypeGraph)
    }

    private fun assertSecureTypeGraph(root: KType) {
        val forbidden = listOf("apikey", "authorization", "password", "privatekey", "secret", "file", "path", "database")
        exportTypeGraph(root).forEach { type ->
            val classifier = type.classifier as? KClass<*> ?: return@forEach
            assertTrue(
                !refersToForbidden(classifier.qualifiedName.orEmpty(), forbidden),
                "Export type ${classifier.qualifiedName} must not expose sensitive or storage material",
            )
            classifier.memberProperties.forEach { property ->
                assertTrue(
                    !refersToForbidden(property.name, forbidden),
                    "Export property ${classifier.qualifiedName}.${property.name} must not expose sensitive or storage material",
                )
            }
        }
    }

    private fun exportTypeGraph(root: KType): Set<KType> {
        val inspectedTypes = mutableSetOf<KType>()
        val inspectedClasses = mutableSetOf<KClass<*>>()

        fun inspect(type: KType) {
            if (!inspectedTypes.add(type)) return
            type.arguments.mapNotNull { it.type }.forEach(::inspect)
            val classifier = type.classifier as? KClass<*> ?: return
            if (!inspectedClasses.add(classifier)) return
            classifier.memberProperties.forEach { property -> inspect(property.returnType) }
        }

        inspect(root)
        return inspectedTypes
    }

    /**
     * 按词边界而不是子串判定禁用材料，避免 `ExportProfileRecord` 里的 `Profile`
     * 命中 `file` 这类误报；`java.io.File`、`filePath`、`files`、`apiKey` 仍会被命中。
     */
    private fun refersToForbidden(name: String, forbidden: List<String>): Boolean {
        val words = name
            .split('.', '_', '-', ' ')
            .flatMap { part -> part.split(Regex("(?<=[a-z0-9])(?=[A-Z])")) }
            .map { it.lowercase() }
            .filter { it.isNotEmpty() }
        val candidates = words + words.zipWithNext { first, second -> first + second }
        return forbidden.any { token ->
            candidates.any { candidate -> candidate.startsWith(token.lowercase()) }
        }
    }
}
