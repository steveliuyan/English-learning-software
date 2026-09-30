package com.example.englishlearning.wordbook

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 导出：把一个已校验的词书包目录打成单文件 `*.wbpack`（本质是 zip，可分享）。
 *
 * 先解析校验再打包——不允许把半成品导出去；写临时文件后改名，避免留下半个包。
 */
object WordBookPackageExporter {

    fun export(packageDirectory: File, target: File): Result<File> = runCatching {
        // 先解析校验再打包：不允许把半成品导出去
        val parsed = WordBookPackageParser.parse(packageDirectory).getOrElse { throw it }
        val bookFile = File(packageDirectory, "book.json")
        val manifestFile = File(packageDirectory, "manifest.json")

        val temporary = File(target.parentFile ?: packageDirectory, "${target.name}.tmp-${UUID.randomUUID()}")
        try {
            ZipOutputStream(FileOutputStream(temporary)).use { zip ->
                zip.putNextEntry(ZipEntry("book.json")); zip.write(bookFile.readBytes()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifestFile.readBytes()); zip.closeEntry()
                parsed.images.forEach { image ->
                    val file = File(packageDirectory, image.file.replace('/', File.separatorChar))
                    zip.putNextEntry(ZipEntry(image.file))
                    zip.write(file.readBytes())
                    zip.closeEntry()
                }
            }
            // 一步替换而不是先删旧包再改名：改名失败时旧包仍在
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
        target
    }
}

/**
 * 导入：`.wbpack`（或已解开的目录）→ 校验 → 落到目标目录下的 `<bookId>/`。
 *
 * **失败零写入**：一切校验都在暂存目录里完成，只有全部通过才改名进正式位置；
 * 任何一步失败都只删暂存目录，正式目录一个字节都不动。zip 解包还挡了路径穿越
 * （`../` 与绝对路径一律拒绝），避免包内条目写到包外。
 */
/**
 * 导入时的资源上限（AGENTS.md：限制文件大小、条目数、解压比例，防资源炸弹）。
 *
 * 按**实际读出的字节**计数，不信 zip 头里自报的大小——头可以伪造。
 */
internal data class ImportLimits(
    val maxEntries: Int = 2_000,
    val maxEntryBytes: Long = 5L * 1024 * 1024,
    val maxTotalBytes: Long = 200L * 1024 * 1024,
    val maxCompressionRatio: Long = 100,
) {
    companion object {
        /** 从 URI 拷到缓存的 `.wbpack` 本身的上限：压缩包不会比解开后更大。 */
        const val MAX_ARCHIVE_BYTES: Long = 200L * 1024 * 1024
    }
}

/** 拷贝至多 [limit] 字节；超出即抛 [WordBookPackageRejection.PackageTooLarge]。返回实际拷贝字节数。 */
internal fun copyWithLimit(input: InputStream, output: OutputStream, limit: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return copied
        copied += read
        if (copied > limit) throw WordBookPackageException(WordBookPackageRejection.PackageTooLarge)
        output.write(buffer, 0, read)
    }
}

class WordBookPackageImporter internal constructor(
    private val publicationOps: PublicationOps = RealPublicationOps,
    private val limits: ImportLimits = ImportLimits(),
) {

    fun import(source: File, destinationRoot: File): Result<File> = runCatching {
        val staging = File(destinationRoot, "$STAGING_PREFIX${UUID.randomUUID()}").apply { mkdirs() }
        try {
            if (source.isDirectory) {
                copyTree(source, staging)
            } else {
                extractZip(source, staging)
            }
            val parsed = WordBookPackageParser.parse(staging).getOrElse { throw it }
            rejectUnlistedFiles(staging, parsed)
            val bookId = parsed.metadata.id
            val target = File(destinationRoot, bookId)
            publish(staging, target)
            target
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        }
    }

    internal interface PublicationOps {
        fun rename(source: File, destination: File): Boolean
        fun deleteRecursively(file: File): Boolean
    }

    internal fun publish(staging: File, target: File) {
        publishForTest(staging, target, publicationOps)
    }

    companion object {
        private const val STAGING_PREFIX = ".staging-"
        private const val BACKUP_PREFIX = ".backup-"

        /** 导入流程自建的暂存/备份目录，不是已发布的词书。 */
        internal fun isWorkDirectory(file: File): Boolean = isReservedName(file.name)

        /** 词书 id 不得占用暂存/备份前缀，否则导入后会被当成工作目录而从列表里消失。 */
        internal fun isReservedName(name: String): Boolean =
            name.startsWith(STAGING_PREFIX) || name.startsWith(BACKUP_PREFIX)

        /**
         * 收拾上次被杀进程或备份删除失败留下的工作目录。
         *
         * - 暂存目录从未发布，直接删；
         * - 备份按包内 `book.json` 的 id 归组（不信目录名）：正式册在则备份已过期，删；
         *   正式册不在且只有一份备份，它可能是唯一的旧数据，改名恢复；
         * - 读不出 id 的、或同一 id 多份备份且正式册不在的，原样保留，不猜。
         *
         * 调用方须保证此时没有进行中的导入，否则会删掉正在写的暂存目录。
         */
        internal fun recoverWorkDirectories(root: File) {
            val work = root.listFiles().orEmpty().filter { it.isDirectory && isWorkDirectory(it) }
            work.filter { it.name.startsWith(STAGING_PREFIX) }.forEach { it.deleteRecursively() }
            work.filter { it.name.startsWith(BACKUP_PREFIX) }
                .mapNotNull { backup ->
                    WordBookPackageParser.parse(backup).getOrNull()?.let { it.metadata.id to backup }
                }
                .groupBy({ it.first }, { it.second })
                .forEach { (bookId, backups) ->
                    val target = File(root, bookId)
                    when {
                        target.exists() -> backups.forEach { it.deleteRecursively() }
                        backups.size == 1 -> backups.single().renameTo(target)
                    }
                }
        }

        private object RealPublicationOps : PublicationOps {
            override fun rename(source: File, destination: File): Boolean =
                source.renameTo(destination)

            override fun deleteRecursively(file: File): Boolean =
                file.deleteRecursively()
        }

        internal fun publishForTest(
            staging: File,
            target: File,
            ops: PublicationOps,
        ) {
            val backup = if (target.exists()) {
                File(target.parentFile, "$BACKUP_PREFIX${UUID.randomUUID()}")
                    .also { check(ops.rename(target, it)) { "cannot backup existing word book" } }
            } else {
                null
            }
            try {
                check(ops.rename(staging, target)) {
                    "cannot move staged word book into place"
                }
            } catch (failure: Throwable) {
                backup?.let {
                    check(ops.rename(it, target)) { "cannot restore existing word book" }
                }
                throw failure
            }
            backup?.let(ops::deleteRecursively)
        }
    }

    private fun extractZip(source: File, staging: File) {
        if (!source.isFile) throw WordBookPackageException(WordBookPackageRejection.MissingBookJson)
        // 压缩比上限折算成总字节上限：解开后超过「包大小 × 比例」即视为炸弹，边读边停
        val archiveBytes = source.length()
        val ratioBytes = if (archiveBytes > Long.MAX_VALUE / limits.maxCompressionRatio) {
            Long.MAX_VALUE
        } else {
            archiveBytes * limits.maxCompressionRatio
        }
        val budget = SizeBudget(minOf(limits.maxTotalBytes, ratioBytes))
        ZipFile(source).use { zip ->
            if (zip.size() > limits.maxEntries) throw tooLarge()
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                val target = File(staging, entry.name)
                if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                    throw WordBookPackageException(WordBookPackageRejection.InvalidManifest)
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> budget.copy(input, output) }
                }
            }
        }
    }

    private fun copyTree(source: File, staging: File) {
        val files = source.walkTopDown().filter { it.isFile }.toList()
        if (files.size > limits.maxEntries) throw tooLarge()
        val budget = SizeBudget(limits.maxTotalBytes)
        files.forEach { file ->
            val target = File(staging, file.relativeTo(source).path)
            target.parentFile?.mkdirs()
            file.inputStream().use { input -> target.outputStream().use { output -> budget.copy(input, output) } }
        }
    }

    /** 跨条目累计的字节预算：每个条目既受单文件上限，也受剩余总量约束。 */
    private inner class SizeBudget(private val totalLimit: Long) {
        private var used = 0L

        fun copy(input: InputStream, output: OutputStream) {
            used += copyWithLimit(input, output, minOf(limits.maxEntryBytes, totalLimit - used))
        }
    }

    /** 解析器会忽略它不认识的文件，所以只在导入时把关：清单之外的一律拒绝，免得被一起发布。 */
    private fun rejectUnlistedFiles(staging: File, parsed: WordBookPackage) {
        val root = staging.canonicalFile
        val allowed = (listOf("book.json", "manifest.json") + parsed.images.map { it.file })
            .map { File(root, it.replace('/', File.separatorChar)).canonicalFile }
            .toSet()
        if (root.walkTopDown().any { it.isFile && it.canonicalFile !in allowed }) {
            throw WordBookPackageException(WordBookPackageRejection.UnlistedFile)
        }
    }

    private fun tooLarge() = WordBookPackageException(WordBookPackageRejection.PackageTooLarge)
}
