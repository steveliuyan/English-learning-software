package com.example.englishlearning.wordbook

import com.example.englishlearning.learning.WordBookMetadataPolicy
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 词书包测试夹具：造出**合法**的 `book.json + manifest.json + images/`，
 * 并支持局部篡改（改字节、删文件、改哈希、加孤儿图）以验证拒绝路径。
 */
object WordBookPackageFixture {

    const val BOOK_ID = "ngsl-core-100"
    const val SOURCE_ID = WordBookMetadataPolicy.PACKAGED_BOOK_SOURCE_ID
    const val SOURCE_POLICY = WordBookMetadataPolicy.PACKAGED_BOOK_POLICY

    data class CardSpec(
        val lemma: String,
        val rank: Int,
        val senses: List<Pair<String, String>> = listOf("n." to "示例释义"),
        val example: String = "An example sentence for $lemma.",
        val imageBytes: ByteArray? = "image-bytes-$lemma".toByteArray(),
    )

    fun defaultCards(): List<CardSpec> = listOf(
        CardSpec("parent", 305, listOf("n." to "父母；母亲或父亲", "v." to "养育；做父母")),
        CardSpec("possible", 303, listOf("adj." to "可能的；可实现的")),
    )

    /** 写出一个合法包，返回包目录。 */
    fun writePackage(
        directory: File,
        cards: List<CardSpec> = defaultCards(),
        bookFormatVersion: Int = 1,
        manifestFormatVersion: Int = 1,
        sourceId: String = SOURCE_ID,
        sourcePolicy: String = SOURCE_POLICY,
        extraManifestEntries: List<String> = emptyList(),
        imageShaOverride: String? = null,
        truncateImageBytes: Boolean = false,
    ): File {
        directory.mkdirs()
        val imagesDirectory = File(directory, "images").apply { mkdirs() }
        val manifestEntries = mutableListOf<String>()
        val cardEntries = cards.map { card ->
            val imageJson = card.imageBytes?.let { bytes ->
                val name = "images/${card.lemma}.webp"
                File(imagesDirectory, "${card.lemma}.webp").writeBytes(bytes)
                val sha = imageShaOverride ?: sha256(bytes)
                val size = if (truncateImageBytes) bytes.size - 1 else bytes.size
                manifestEntries +=
                    """{"file":"$name","sha256":"$sha","bytes":$size,"width":512,"height":512,"license":"ai-generated","model":"m-test"}"""
                """{"file":"$name","sha256":"$sha"}"""
            }
            val senses = card.senses.joinToString(",") { (pos, meaning) ->
                """{"pos":"$pos","meaningZh":"$meaning"}"""
            }
            """
            {"cardId":"$BOOK_ID:${card.lemma}","lemma":"${card.lemma}","rank":${card.rank},"ipa":"test-ipa",
             "senses":[$senses],"example":"${card.example}","exampleZh":"例句译文",
             "derived":[{"lemma":"derived","pos":"n.","meaningZh":"派生"}],
             "phrases":[{"text":"a phrase","meaningZh":"短语"}],
             "synonyms":[{"lemma":"synonym","pos":"n.","meaningZh":"近义"}]${imageJson?.let { ",\"image\":$it" }.orEmpty()}}
            """.trimIndent().replace("\n", "")
        }
        extraManifestEntries.forEach { name ->
            File(imagesDirectory, File(name).name).writeBytes("orphan".toByteArray())
            manifestEntries += """{"file":"$name","sha256":"${sha256("orphan".toByteArray())}","bytes":6}"""
        }
        File(directory, "book.json").writeText(
            """{"formatVersion":$bookFormatVersion,"id":"$BOOK_ID","displayName":"测试词书","level":"基础",""" +
                """"sourceId":"$sourceId","sourcePolicy":"$sourcePolicy","attribution":"test","cards":[${cardEntries.joinToString(",")}]}""",
            Charsets.UTF_8,
        )
        File(directory, "manifest.json").writeText(
            """{"formatVersion":$manifestFormatVersion,"images":[${manifestEntries.joinToString(",")}]}""",
            Charsets.UTF_8,
        )
        return directory
    }

    /** 把包目录打成 `.wbpack`。 */
    fun zip(packageDirectory: File, target: File): File {
        ZipOutputStream(target.outputStream()).use { zip ->
            packageDirectory.walkTopDown().filter { it.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(packageDirectory).path))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
        }
        return target
    }

    /** 造一个带路径穿越条目的恶意包（用于验证解包防护）。 */
    fun zipWithZipSlip(target: File, entryName: String = "../escaped.txt"): File {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entryName)); zip.write("pwned".toByteArray()); zip.closeEntry()
        }
        return target
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
