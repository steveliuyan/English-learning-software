package com.example.englishlearning.backup

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Source-level contract: the production backup data path is read-only, touches
 * only whitelisted tables, and never bulk-exports DAO rows through reflection.
 */
class BackupSourceReadOnlyContractTest {
    private val productionFiles = listOf(
        "src/main/java/com/example/englishlearning/backup/domain/BackupSnapshot.kt",
        "src/main/java/com/example/englishlearning/backup/domain/BackupExport.kt",
        "src/main/java/com/example/englishlearning/backup/infrastructure/RoomBackupRowReader.kt",
        "src/main/java/com/example/englishlearning/backup/infrastructure/InternalBackupSourceDao.kt",
        "src/main/java/com/example/englishlearning/backup/infrastructure/FileOpsBackupMediaGateway.kt",
        "src/main/java/com/example/englishlearning/backup/infrastructure/ZipPackagePackager.kt",
    )

    private val writeOperations = listOf(".insert(", ".update(", ".delete(", ".upsert(", "execSQL", ".writeVerified(", "setTransactionSuccessful")

    @Test
    fun `backup production sources contain no write operations`() {
        productionFiles.forEach { relative ->
            val source = projectFile(relative).readText()
            writeOperations.forEach { marker ->
                assertTrue(!source.contains(marker), "$relative must not contain write marker $marker")
            }
        }
    }

    @Test
    fun `backup dao queries are select-only over whitelisted tables`() {
        val daoSource = projectFile("src/main/java/com/example/englishlearning/backup/infrastructure/InternalBackupSourceDao.kt").readText()

        val queries = Regex("@Query\\(\"([^\"]+)\"\\)").findAll(daoSource).map { it.groupValues[1] }.toList()
        assertEquals(16, queries.size, "one SELECT per whitelisted table")
        queries.forEach { query ->
            assertTrue(query.trim().startsWith("SELECT", ignoreCase = false), "query must be SELECT: $query")
        }

        val tables = Regex("FROM\\s+([a-z_]+)").findAll(daoSource).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "local_profiles", "learning_profiles", "learning_settings", "today_plans", "today_plan_tasks",
                "learning_events", "card_review_states", "word_books", "vocabulary_entries",
                "word_book_progress_migration_audits", "articles", "reading_completions", "reading_preferences",
                "vocabulary_search_history", "word_ai_notes", "asset_records",
            ),
            tables,
        )
        val forbiddenTables = setOf(
            "ai_profiles", "ai_preferences", "key_aliases", "speech_preferences", "reminder_preferences",
            "schema_meta", "vocabulary_search_index", "vocabulary_search_gram",
        )
        assertTrue(tables.intersect(forbiddenTables).isEmpty())
    }

    @Test
    fun `snapshot assembly uses explicit per-type serializers and no reflection bulk export`() {
        val snapshotSource = projectFile("src/main/java/com/example/englishlearning/backup/domain/BackupSnapshot.kt").readText()

        listOf("java.lang.reflect", "kotlin.reflect", "memberProperties", "getAll(").forEach { marker ->
            assertTrue(!snapshotSource.contains(marker), "BackupSnapshot must not use reflection marker $marker")
        }
    }

    private fun projectFile(relativePath: String): File =
        listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.isFile }
            ?: error("missing production source for $relativePath")
}
