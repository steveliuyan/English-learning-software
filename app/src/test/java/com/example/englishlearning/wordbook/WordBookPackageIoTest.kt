package com.example.englishlearning.wordbook

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 导入/导出契约。核心是两条纪律：
 * 1. **失败零写入**——校验不通过时正式目录一个字节都不许动（暂存目录必须清干净）；
 * 2. **解包不许穿越**——zip 里的 `../` 条目不得写到包外。
 */
class WordBookPackageIoTest {

    private fun tempDirectory(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    private fun rejectionOf(outcome: Result<File>): WordBookPackageRejection? =
        (outcome.exceptionOrNull() as? WordBookPackageException)?.rejection

    /** 最宽松的上限：只收紧被测那一项，免得别的检查先触发。 */
    private val unlimited = ImportLimits(
        maxEntries = Int.MAX_VALUE,
        maxEntryBytes = Long.MAX_VALUE,
        maxTotalBytes = Long.MAX_VALUE,
        maxCompressionRatio = Long.MAX_VALUE,
    )

    private fun importer(limits: ImportLimits) =
        WordBookPackageImporter(limits = limits)

    @Test
    fun exportedPackageCanBeImportedBackAndMatches() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("src"))
        val archive = WordBookPackageFixture.zip(source, File(tempDirectory("zip"), "book.wbpack"))
        val destination = tempDirectory("dest").apply { mkdirs() }

        val imported = WordBookPackageImporter().import(archive, destination).getOrThrow()

        assertEquals(WordBookPackageFixture.BOOK_ID, imported.name)
        val parsed = WordBookPackageParser.parse(imported).getOrThrow()
        assertEquals(2, parsed.cards.size)
        assertTrue(File(parsed.cards.first().imagePath!!).isFile)
        assertTrue(
            destination.listFiles().orEmpty().none { WordBookPackageImporter.isWorkDirectory(it) },
            "成功导入后不得残留暂存/备份目录：${destination.listFiles()?.map { it.name }}",
        )
    }

    @Test
    fun directorySourceCanBeImportedWithoutZipping() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("dirsrc"))
        val destination = tempDirectory("dirdest").apply { mkdirs() }

        val imported = WordBookPackageImporter().import(source, destination).getOrThrow()

        assertTrue(File(imported, "book.json").isFile)
        assertTrue(File(imported, "images").isDirectory)
    }

    @Test
    fun tamperedPackageIsRejectedWithoutWritingAnything() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("badsrc"))
        File(source, "images/parent.webp").writeBytes("tampered".toByteArray())
        val destination = tempDirectory("baddest").apply { mkdirs() }

        val outcome = WordBookPackageImporter().import(source, destination)

        assertTrue(outcome.isFailure, "被篡改的包必须拒绝")
        assertTrue(
            destination.listFiles().orEmpty().isEmpty(),
            "失败时正式目录必须零写入，实际残留：${destination.listFiles()?.map { it.name }}",
        )
    }

    @Test
    fun zipSlipEntryIsRejectedAndEscapesNothing() {
        val destination = tempDirectory("slipdest").apply { mkdirs() }
        val evil = WordBookPackageFixture.zipWithZipSlip(File(tempDirectory("slipzip"), "evil.wbpack"))

        val outcome = WordBookPackageImporter().import(evil, destination)

        assertTrue(outcome.isFailure, "路径穿越条目必须拒绝")
        assertFalse(File(destination.parentFile, "escaped.txt").exists(), "不得写到包外")
        assertTrue(destination.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun failedPublicationRestoresExistingBook() {
        val root = tempDirectory("publish-failure")
        val target = File(root, "book").apply { mkdirs() }
        File(target, "keep.txt").writeText("old package")
        val staging = File(root, "staging").apply { mkdirs() }
        File(staging, "new.txt").writeText("new package")
        val ops = object : WordBookPackageImporter.PublicationOps {
            override fun rename(source: File, destination: File): Boolean =
                if (source == staging) false else source.renameTo(destination)

            override fun deleteRecursively(file: File): Boolean = file.deleteRecursively()
        }

        val result = runCatching { WordBookPackageImporter.publishForTest(staging, target, ops) }

        assertTrue(result.isFailure)
        assertEquals("old package", File(target, "keep.txt").readText())
        assertFalse(File(target, "new.txt").exists())
    }

    @Test
    fun failedPublicationWithoutExistingBookLeavesNoTarget() {
        val root = tempDirectory("publish-failure-without-backup")
        val target = File(root, "book")
        val staging = File(root, "staging").apply { mkdirs() }
        File(staging, "new.txt").writeText("new package")
        val ops = object : WordBookPackageImporter.PublicationOps {
            override fun rename(source: File, destination: File): Boolean =
                if (source == staging) false else source.renameTo(destination)

            override fun deleteRecursively(file: File): Boolean = file.deleteRecursively()
        }

        val result = runCatching { WordBookPackageImporter.publishForTest(staging, target, ops) }

        assertTrue(result.isFailure)
        assertFalse(target.exists())
        assertTrue(staging.exists())
    }

    @Test
    fun failedImportCleansStagingAndKeepsExistingBook() {
        val destination = tempDirectory("import-publication-failure").apply { mkdirs() }
        val firstSource = WordBookPackageFixture.writePackage(tempDirectory("import-old"))
        val existing = WordBookPackageImporter().import(firstSource, destination).getOrThrow()
        File(existing, "keep.txt").writeText("old package")
        val replacement = WordBookPackageFixture.writePackage(
            tempDirectory("import-new"),
            cards = listOf(WordBookPackageFixture.CardSpec("replacement", 1)),
        )
        val ops = object : WordBookPackageImporter.PublicationOps {
            override fun rename(source: File, destination: File): Boolean =
                if (source.name.startsWith(".staging-")) false else source.renameTo(destination)

            override fun deleteRecursively(file: File): Boolean = file.deleteRecursively()
        }

        val result = WordBookPackageImporter(ops).import(replacement, destination)

        assertTrue(result.isFailure)
        assertEquals("old package", File(existing, "keep.txt").readText())
        assertTrue(existing.isDirectory)
        assertTrue(
            destination.listFiles().orEmpty().none { it.name.startsWith(".staging-") },
            "导入发布失败后不得留下 staging 目录",
        )
    }

    @Test
    fun failedPublicationKeepsBackupWhenRestorationFails() {
        val root = tempDirectory("publish-restore-failure")
        val target = File(root, "book").apply { mkdirs() }
        File(target, "keep.txt").writeText("old package")
        val staging = File(root, "staging").apply { mkdirs() }
        File(staging, "new.txt").writeText("new package")
        val ops = object : WordBookPackageImporter.PublicationOps {
            override fun rename(source: File, destination: File): Boolean = when {
                source == target -> source.renameTo(destination)
                source == staging -> false
                source.name.startsWith(".backup-") -> false
                else -> source.renameTo(destination)
            }

            override fun deleteRecursively(file: File): Boolean = file.deleteRecursively()
        }

        val result = runCatching { WordBookPackageImporter.publishForTest(staging, target, ops) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("cannot restore existing word book"))
        assertFalse(target.exists())
        assertTrue(root.listFiles().orEmpty().any { it.name.startsWith(".backup-") })
    }

    @Test
    fun backupDeletionFailureKeepsPublishedReplacement() {
        val root = tempDirectory("publish-backup-delete-failure")
        val target = File(root, "book").apply { mkdirs() }
        File(target, "keep.txt").writeText("old package")
        val staging = File(root, "staging").apply { mkdirs() }
        File(staging, "new.txt").writeText("new package")
        val ops = object : WordBookPackageImporter.PublicationOps {
            override fun rename(source: File, destination: File): Boolean = source.renameTo(destination)

            override fun deleteRecursively(file: File): Boolean = false
        }

        val result = runCatching { WordBookPackageImporter.publishForTest(staging, target, ops) }

        assertTrue(result.isSuccess)
        assertEquals("new package", File(target, "new.txt").readText())
        assertFalse(File(target, "keep.txt").exists())
        assertTrue(root.listFiles().orEmpty().any { it.name.startsWith(".backup-") })
    }

    @Test
    fun recoveryDeletesLeftoverStaging() {
        val root = tempDirectory("recover-staging")
        val staging = File(root, ".staging-leftover").apply { mkdirs() }
        File(staging, "half.txt").writeText("half copied")

        WordBookPackageImporter.recoverWorkDirectories(root)

        assertFalse(staging.exists(), "从未发布的暂存目录应被清理")
    }

    @Test
    fun recoveryDeletesStaleBackupWhenBookExists() {
        val root = tempDirectory("recover-stale")
        val book = WordBookPackageFixture.writePackage(File(root, WordBookPackageFixture.BOOK_ID))
        val backup = WordBookPackageFixture.writePackage(File(root, ".backup-stale"))

        WordBookPackageImporter.recoverWorkDirectories(root)

        assertFalse(backup.exists(), "正式目录已在时，备份已过期")
        assertTrue(WordBookPackageParser.parse(book).isSuccess, "正式目录不得被动")
    }

    @Test
    fun recoveryRestoresOrphanBackupWhenBookIsMissing() {
        val root = tempDirectory("recover-orphan")
        val backup = WordBookPackageFixture.writePackage(File(root, ".backup-orphan"))

        WordBookPackageImporter.recoverWorkDirectories(root)

        val restored = File(root, WordBookPackageFixture.BOOK_ID)
        assertFalse(backup.exists())
        assertEquals(2, WordBookPackageParser.parse(restored).getOrThrow().cards.size, "唯一的旧数据必须被恢复")
    }

    @Test
    fun recoveryLeavesUnparseableBackupAlone() {
        val root = tempDirectory("recover-broken")
        val backup = File(root, ".backup-broken").apply { mkdirs() }
        File(backup, "book.json").writeText("not json")

        WordBookPackageImporter.recoverWorkDirectories(root)

        assertTrue(backup.exists(), "读不出 bookId 的备份不得删除")
        assertEquals(listOf(".backup-broken"), root.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun recoveryLeavesAmbiguousBackupsAlone() {
        val root = tempDirectory("recover-ambiguous")
        WordBookPackageFixture.writePackage(File(root, ".backup-a"))
        WordBookPackageFixture.writePackage(File(root, ".backup-b"))

        WordBookPackageImporter.recoverWorkDirectories(root)

        assertEquals(
            listOf(".backup-a", ".backup-b"),
            root.listFiles().orEmpty().map { it.name }.sorted(),
            "同一 bookId 有多份备份时不猜该恢复哪份",
        )
    }

    @Test
    fun importingReplacesAnExistingBookOfTheSameId() {
        val firstSource = WordBookPackageFixture.writePackage(tempDirectory("v1"))
        val secondSource = WordBookPackageFixture.writePackage(
            tempDirectory("v2"),
            cards = listOf(WordBookPackageFixture.CardSpec("only", 1)),
        )
        val destination = tempDirectory("replace").apply { mkdirs() }

        val first = WordBookPackageImporter().import(firstSource, destination).getOrThrow()
        assertEquals(2, WordBookPackageParser.parse(first).getOrThrow().cards.size)

        val second = WordBookPackageImporter().import(secondSource, destination).getOrThrow()
        assertEquals(first.canonicalPath, second.canonicalPath)
        assertEquals(1, WordBookPackageParser.parse(second).getOrThrow().cards.size)
    }

    @Test
    fun archiveWithTooManyEntriesIsRejected() {
        val archive = WordBookPackageFixture.zip(
            WordBookPackageFixture.writePackage(tempDirectory("many-src")),
            File(tempDirectory("many-zip"), "b.wbpack"),
        )
        val destination = tempDirectory("many-dest")

        // 夹具包有 book.json、manifest.json 和 2 张图，共 4 个条目
        val outcome = importer(unlimited.copy(maxEntries = 3)).import(archive, destination)

        assertEquals(WordBookPackageRejection.PackageTooLarge, rejectionOf(outcome))
        assertTrue(destination.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun oversizedEntryIsRejected() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("big-entry-src"))
        val archive = WordBookPackageFixture.zip(source, File(tempDirectory("big-entry-zip"), "b.wbpack"))
        val destination = tempDirectory("big-entry-dest")
        val bookJson = File(source, "book.json").length()

        val outcome = importer(unlimited.copy(maxEntryBytes = bookJson - 1)).import(archive, destination)

        assertEquals(WordBookPackageRejection.PackageTooLarge, rejectionOf(outcome))
        assertTrue(destination.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun archiveExceedingTotalSizeIsRejected() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("total-src"))
        val archive = WordBookPackageFixture.zip(source, File(tempDirectory("total-zip"), "b.wbpack"))
        val destination = tempDirectory("total-dest")
        val total = source.walkTopDown().filter { it.isFile }.sumOf { it.length() }

        // 单个条目都不超，只有加起来超
        val outcome = importer(unlimited.copy(maxTotalBytes = total - 1)).import(archive, destination)

        assertEquals(WordBookPackageRejection.PackageTooLarge, rejectionOf(outcome))
        assertTrue(destination.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun highlyCompressibleArchiveIsRejected() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("bomb-src"))
        // 4 MB 的零压缩后只有几 KB：单文件、总量都在默认上限内，只有压缩比超标
        File(source, "padding.bin").writeBytes(ByteArray(4 * 1024 * 1024))
        val archive = WordBookPackageFixture.zip(source, File(tempDirectory("bomb-zip"), "b.wbpack"))
        val destination = tempDirectory("bomb-dest")

        val outcome = WordBookPackageImporter().import(archive, destination)

        assertEquals(WordBookPackageRejection.PackageTooLarge, rejectionOf(outcome))
        assertTrue(destination.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun archiveWithUnlistedFileIsRejected() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("extra-zip-src"))
        File(source, "notes.txt").writeText("not in the manifest")
        val archive = WordBookPackageFixture.zip(source, File(tempDirectory("extra-zip"), "b.wbpack"))
        val destination = tempDirectory("extra-zip-dest")

        val outcome = WordBookPackageImporter().import(archive, destination)

        assertEquals(WordBookPackageRejection.UnlistedFile, rejectionOf(outcome))
        assertTrue(destination.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun directoryWithUnlistedFileIsRejected() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("extra-dir-src"))
        File(source, "images/Thumbs.db").writeText("system junk")
        val destination = tempDirectory("extra-dir-dest")

        val outcome = WordBookPackageImporter().import(source, destination)

        assertEquals(WordBookPackageRejection.UnlistedFile, rejectionOf(outcome))
        assertTrue(destination.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun directorySourceIsHeldToTheSameLimits() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("dir-limit-src"))
        val destination = tempDirectory("dir-limit-dest")
        val bookJson = File(source, "book.json").length()

        val outcome = importer(unlimited.copy(maxEntryBytes = bookJson - 1)).import(source, destination)

        assertEquals(WordBookPackageRejection.PackageTooLarge, rejectionOf(outcome))
        assertTrue(destination.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun copyWithLimitStopsPastTheLimit() {
        val tooBig = runCatching {
            copyWithLimit(ByteArrayInputStream(ByteArray(11)), ByteArrayOutputStream(), limit = 10)
        }
        assertEquals(
            WordBookPackageRejection.PackageTooLarge,
            (tooBig.exceptionOrNull() as? WordBookPackageException)?.rejection,
        )

        val output = ByteArrayOutputStream()
        assertEquals(10L, copyWithLimit(ByteArrayInputStream(ByteArray(10)), output, limit = 10))
        assertEquals(10, output.size())
    }

    @Test
    fun exporterOutputCanBeImportedBack() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("export-src"))
        val archive = WordBookPackageExporter
            .export(source, File(tempDirectory("export-out"), "book.wbpack"))
            .getOrThrow()

        val imported = WordBookPackageImporter().import(archive, tempDirectory("export-dest")).getOrThrow()

        assertEquals(
            WordBookPackageParser.parse(source).getOrThrow().cards.map { it.copy(imagePath = null) },
            WordBookPackageParser.parse(imported).getOrThrow().cards.map { it.copy(imagePath = null) },
            "导出器真实产出的包必须能原样导回",
        )
    }

    @Test
    fun exportReplacesAnExistingFile() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("export-replace-src"))
        val target = File(tempDirectory("export-replace"), "book.wbpack").apply { writeText("old export") }

        WordBookPackageExporter.export(source, target).getOrThrow()

        assertTrue(WordBookPackageImporter().import(target, tempDirectory("export-replace-dest")).isSuccess)
    }

    @Test
    fun failedExportLeavesNoTemporaryFile() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("export-fail-src"))
        val outDirectory = tempDirectory("export-fail-out")
        // 目标位置被非空目录占着，无法替换：导出必须失败、不动目标、不留临时文件
        val target = File(outDirectory, "book.wbpack").apply { mkdirs() }
        File(target, "keep.txt").writeText("occupied")

        val outcome = WordBookPackageExporter.export(source, target)

        assertTrue(outcome.isFailure)
        assertEquals("occupied", File(target, "keep.txt").readText())
        assertEquals(
            listOf("book.wbpack"),
            outDirectory.listFiles().orEmpty().map { it.name },
            "失败后不得残留临时文件",
        )
    }

    @Test
    fun exporterRefusesToPackageAnInvalidDirectory() {
        val broken = tempDirectory("broken").apply { mkdirs() }
        val target = File(tempDirectory("out"), "broken.wbpack")

        val outcome = WordBookPackageExporter.export(broken, target)

        assertTrue(outcome.isFailure)
        assertFalse(target.exists(), "不该留下半个包")
    }

    @Test
    fun roundTripKeepsEveryCardField() {
        val source = WordBookPackageFixture.writePackage(tempDirectory("round"))
        val archive = WordBookPackageFixture.zip(source, File(tempDirectory("roundzip"), "b.wbpack"))
        val destination = tempDirectory("rounddest").apply { mkdirs() }

        val imported = WordBookPackageImporter().import(archive, destination).getOrThrow()
        val original = WordBookPackageParser.parse(source).getOrThrow()
        val roundTripped = WordBookPackageParser.parse(imported).getOrThrow()

        assertEquals(
            original.cards.map { it.copy(imagePath = null) },
            roundTripped.cards.map { it.copy(imagePath = null) },
            "导入导回后词条内容必须逐字段一致",
        )
    }
}
