package com.example.englishlearning.learning

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class WordBookDeletionServiceTest {
    @Test
    fun deletesImportedBookAndItsPrivateDirectory() = runTest {
        val root = Files.createTempDirectory("wordbooks").toFile()
        val directory = root.resolve("imported-book").apply {
            mkdirs()
            resolve("book.json").writeText("{}")
            resolve("nested").mkdirs()
        }
        val repository = RecordingRepository(book = importedBook("imported-book"))

        val result = WordBookDeletionService(repository, root) { emptySet() }
            .delete(profileId = "profile", wordBookId = "imported-book")

        assertEquals(WordBookDeletionResult.Deleted, result)
        assertEquals(listOf("profile|imported-book"), repository.deleted)
        assertFalse(directory.exists())
    }

    @Test
    fun refusesDeletingBuiltInBookWithoutChangingStorage() = runTest {
        val root = Files.createTempDirectory("wordbooks").toFile()
        val directory = root.resolve("cet4").apply { mkdirs() }
        val repository = RecordingRepository(book = importedBook("cet4"))

        val result = WordBookDeletionService(repository, root) { setOf("cet4") }
            .delete("profile", "cet4")

        assertEquals(WordBookDeletionResult.ProtectedBuiltIn, result)
        assertTrue(directory.exists())
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun refusesDeletingActiveBookWithoutChangingStorage() = runTest {
        val root = Files.createTempDirectory("wordbooks").toFile()
        val directory = root.resolve("imported-book").apply { mkdirs() }
        val repository = RecordingRepository(
            book = importedBook("imported-book"),
            profile = LearningProfile("profile", "imported-book", 10),
        )

        val result = WordBookDeletionService(repository, root) { emptySet() }
            .delete("profile", "imported-book")

        assertEquals(WordBookDeletionResult.ProtectedActive, result)
        assertTrue(directory.exists())
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun reportsNotFoundWhenRowIsMissing() = runTest {
        val root = Files.createTempDirectory("wordbooks").toFile()
        val repository = RecordingRepository(book = null)

        val result = WordBookDeletionService(repository, root) { emptySet() }
            .delete("profile", "ghost")

        assertEquals(WordBookDeletionResult.NotFound, result)
        assertTrue(repository.deleted.isEmpty())
    }

    /**
     * 写序证明：**先删资源目录、后删数据库行**。
     *
     * 断言在 `deleteWordBook` 被调用的那一刻目录已不存在。若实现改成「先删库」，
     * 这条断言立刻变红——这是对写序的变异判别，而不是对实现细节的复述。
     */
    @Test
    fun removesDirectoryBeforeDeletingDatabaseRow() = runTest {
        val root = Files.createTempDirectory("wordbooks").toFile()
        val directory = root.resolve("imported-book").apply {
            mkdirs()
            resolve("book.json").writeText("{}")
            resolve("nested").mkdirs()
        }
        val repository = RecordingRepository(book = importedBook("imported-book"))
        repository.onDelete = {
            assertFalse(directory.exists(), "删除数据库行时资源目录必须已经清理")
        }

        val result = WordBookDeletionService(repository, root) { emptySet() }
            .delete("profile", "imported-book")

        assertEquals(WordBookDeletionResult.Deleted, result)
    }

    @Test
    fun reportsStorageUnavailableWhenRowDeletionFails() = runTest {
        val root = Files.createTempDirectory("wordbooks").toFile()
        root.resolve("imported-book").apply { mkdirs() }
        val repository = RecordingRepository(
            book = importedBook("imported-book"),
            deleteFails = true,
        )

        val result = WordBookDeletionService(repository, root) { emptySet() }
            .delete("profile", "imported-book")

        assertEquals(WordBookDeletionResult.StorageUnavailable, result)
    }

    @Test
    fun rejectsUnsafeBookIdWithoutTouchingStorage() = runTest {
        val root = Files.createTempDirectory("wordbooks").toFile()
        val repository = RecordingRepository(book = importedBook("../escape"))

        val result = WordBookDeletionService(repository, root) { emptySet() }
            .delete("profile", "../escape")

        assertEquals(WordBookDeletionResult.NotFound, result)
        assertTrue(repository.deleted.isEmpty())
    }

    private fun importedBook(id: String) = WordBook(id, "测试", "基础", 1, "v1", "ngsl-nawl-1.2")

    private class RecordingRepository(
        private val book: WordBook?,
        private val profile: LearningProfile? = null,
        private val deleteFails: Boolean = false,
    ) : LearningProfileRepository {
        val deleted = mutableListOf<String>()
        var onDelete: (() -> Unit)? = null

        override suspend fun current(profileId: String) = RepositoryResult.Success(profile)

        override suspend fun save(profile: LearningProfile) = RepositoryResult.Success(Unit)

        override suspend fun listWordBooks() = RepositoryResult.Success(listOfNotNull(book))

        override suspend fun findWordBook(id: String) = RepositoryResult.Success(book?.takeIf { it.id == id })

        override suspend fun upsertWordBook(wordBook: WordBook) = RepositoryResult.Success(Unit)

        override suspend fun deleteWordBook(profileId: String, wordBookId: String): RepositoryResult<Unit> {
            onDelete?.invoke()
            if (deleteFails) return RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            deleted += "$profileId|$wordBookId"
            return RepositoryResult.Success(Unit)
        }
    }
}
