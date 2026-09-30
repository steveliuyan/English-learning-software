package com.example.englishlearning.wordbook

import com.example.englishlearning.learning.MetadataValidationResult
import com.example.englishlearning.learning.WordBookMetadata
import com.example.englishlearning.learning.WordBookMetadataPolicy
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 词书包解析与校验契约：**任何一项不过就整包拒绝**，「离线可用」的前提是包本身可信。
 */
class WordBookPackageParserTest {

    private fun tempDirectory(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()

    private fun rejectionOf(directory: File): WordBookPackageRejection = try {
        WordBookPackageParser.parse(directory).getOrThrow()
        fail("expected rejection for ${directory.name}")
    } catch (expected: WordBookPackageException) {
        expected.rejection
    }

    @Test
    fun validPackageIsAcceptedWithImagesResolved() {
        val directory = WordBookPackageFixture.writePackage(tempDirectory("ok"))

        val parsed = WordBookPackageParser.parse(directory).getOrThrow()

        assertEquals(WordBookPackageFixture.BOOK_ID, parsed.metadata.id)
        assertEquals(2, parsed.metadata.totalWords)
        assertEquals(2, parsed.cards.size)
        assertEquals(2, parsed.images.size)

        val parent = parsed.cards.first { it.lemma == "parent" }
        assertEquals(listOf("n.", "v."), parent.senses.map { it.partOfSpeech })
        // 首条释义同时写进旧字段，旧界面继续可用
        assertEquals("n.", parent.partOfSpeech)
        assertEquals("父母；母亲或父亲", parent.meaningZh)
        assertEquals("例句译文", parent.exampleZh)
        assertEquals(1, parent.derived.size)
        assertEquals(1, parent.phrases.size)
        assertEquals(1, parent.synonyms.size)
        assertEquals(305, parent.rank)
        assertTrue(parent.imagePath?.endsWith("parent.webp") == true)
        assertTrue(File(parent.imagePath!!).isFile)
    }

    @Test
    fun missingBookJsonIsRejected() {
        val directory = tempDirectory("nobook").apply { mkdirs() }
        assertEquals(WordBookPackageRejection.MissingBookJson, rejectionOf(directory))
    }

    @Test
    fun unsupportedFormatVersionIsRejected() {
        val directory = WordBookPackageFixture.writePackage(tempDirectory("v2"), bookFormatVersion = 2)
        assertEquals(WordBookPackageRejection.UnsupportedFormatVersion, rejectionOf(directory))

        val manifestVersion = WordBookPackageFixture.writePackage(tempDirectory("mv2"), manifestFormatVersion = 2)
        assertEquals(WordBookPackageRejection.UnsupportedFormatVersion, rejectionOf(manifestVersion))
    }

    @Test
    fun pathLikeBookIdIsRejected() {
        val directory = WordBookPackageFixture.writePackage(tempDirectory("bad-id"))
        val book = File(directory, "book.json").readText(Charsets.UTF_8)
            .replace("ngsl-core-100", "../escaped")
        File(directory, "book.json").writeText(book, Charsets.UTF_8)

        assertEquals(WordBookPackageRejection.InvalidBookId, rejectionOf(directory))
    }

    private fun packageWithBookJson(prefix: String, edit: (String) -> String): File {
        val directory = WordBookPackageFixture.writePackage(tempDirectory(prefix))
        val bookFile = File(directory, "book.json")
        bookFile.writeText(edit(bookFile.readText(Charsets.UTF_8)), Charsets.UTF_8)
        return directory
    }

    /** 导入册的 cardId 必须落在自己的 `<bookId>:` 命名空间里，否则能顶替别的册（含内置册）的词卡。 */
    @Test
    fun cardIdOutsideTheBookNamespaceIsRejected() {
        val spoofing = packageWithBookJson("spoof") {
            it.replace("\"cardId\":\"ngsl-core-100:parent\"", "\"cardId\":\"cet4-planning:parent\"")
        }
        assertEquals(WordBookPackageRejection.InvalidCard, rejectionOf(spoofing))
    }

    /** 冒号是 cardId 的命名空间分隔符：`a` 与 `a:b` 两册的词卡前缀会互相重叠。 */
    @Test
    fun bookIdContainingColonIsRejected() {
        val colon = packageWithBookJson("colon-id") { it.replace("ngsl-core-100", "ngsl:core") }
        assertEquals(WordBookPackageRejection.InvalidBookId, rejectionOf(colon))
    }

    /** 同一文件列两次：校验只核对最后一条，导出时还会因重复 zip 条目而永远失败。 */
    @Test
    fun duplicateManifestEntryIsRejected() {
        val directory = WordBookPackageFixture.writePackage(tempDirectory("dup-manifest"))
        val manifestFile = File(directory, "manifest.json")
        val manifest = manifestFile.readText(Charsets.UTF_8)
        val firstEntry = manifest.substringAfter("\"images\":[").substringBefore("}") + "}"
        manifestFile.writeText(manifest.replace("\"images\":[", "\"images\":[$firstEntry,"), Charsets.UTF_8)

        assertEquals(WordBookPackageRejection.InvalidManifest, rejectionOf(directory))
    }

    /** 导入内容不可信：名称与句子类字段不得为空、长度有上限，免得撑坏词书列表与词卡布局。 */
    @Test
    fun blankOrOverlongTextFieldsAreRejected() {
        val cases: List<Triple<String, WordBookPackageRejection, (String) -> String>> = listOf(
            Triple("blank displayName", WordBookPackageRejection.InvalidBookJson,
                { s: String -> s.replace("\"displayName\":\"测试词书\"", "\"displayName\":\" \"") }),
            Triple("long displayName", WordBookPackageRejection.InvalidBookJson,
                { s: String -> s.replace("\"displayName\":\"测试词书\"", "\"displayName\":\"${"名".repeat(41)}\"") }),
            Triple("blank level", WordBookPackageRejection.InvalidBookJson,
                { s: String -> s.replace("\"level\":\"基础\"", "\"level\":\"\"") }),
            Triple("long lemma", WordBookPackageRejection.InvalidCard,
                { s: String -> s.replace("\"lemma\":\"parent\"", "\"lemma\":\"${"a".repeat(65)}\"") }),
            Triple("long example", WordBookPackageRejection.InvalidCard,
                { s: String -> s.replace("An example sentence for parent.", "x".repeat(301)) }),
            Triple("blank exampleZh", WordBookPackageRejection.InvalidCard,
                { s: String -> s.replaceFirst("\"exampleZh\":\"例句译文\"", "\"exampleZh\":\" \"") }),
            Triple("blank phrase text", WordBookPackageRejection.InvalidCard,
                { s: String -> s.replaceFirst("\"text\":\"a phrase\"", "\"text\":\" \"") }),
            Triple("long phrase meaning", WordBookPackageRejection.InvalidCard,
                { s: String -> s.replaceFirst("\"meaningZh\":\"短语\"", "\"meaningZh\":\"${"短".repeat(31)}\"") }),
            Triple("blank synonym lemma", WordBookPackageRejection.InvalidCard,
                { s: String -> s.replaceFirst("\"lemma\":\"synonym\"", "\"lemma\":\"\"") }),
        )
        cases.forEach { (name, expected, edit) ->
            assertEquals(expected, rejectionOf(packageWithBookJson("field", edit)), name)
        }
    }

    /** 保留前缀是导入流程的暂存/备份目录名：用它当 id 的册导入后会从列表里消失。 */
    @Test
    fun reservedWorkDirectoryPrefixBookIdIsRejected() {
        listOf(".staging-x", ".backup-x").forEach { reserved ->
            val directory = WordBookPackageFixture.writePackage(tempDirectory("reserved-id"))
            val book = File(directory, "book.json").readText(Charsets.UTF_8)
                .replace("ngsl-core-100", reserved)
            File(directory, "book.json").writeText(book, Charsets.UTF_8)

            assertEquals(WordBookPackageRejection.InvalidBookId, rejectionOf(directory), "id=$reserved")
        }
    }

    @Test
    fun packageWithoutCardsIsRejected() {
        val directory = WordBookPackageFixture.writePackage(tempDirectory("empty"), cards = emptyList())
        assertEquals(WordBookPackageRejection.NoCards, rejectionOf(directory))
    }

    /** 词表来源必须过白名单：来源不明的词表不许进产品（许可风险）。 */
    @Test
    fun unknownSourceIdOrPolicyIsRejected() {
        val unknownSource = WordBookPackageFixture.writePackage(tempDirectory("src"), sourceId = "random-list")
        assertEquals(WordBookPackageRejection.MetadataRejected, rejectionOf(unknownSource))

        val wrongPolicy = WordBookPackageFixture.writePackage(tempDirectory("pol"), sourcePolicy = "官方考试大纲词表")
        assertEquals(WordBookPackageRejection.MetadataRejected, rejectionOf(wrongPolicy))

        // 应用内分组说明必须逐字一致
        assertTrue(WordBookMetadataPolicy.APPLICATION_GROUPING_POLICY.isNotBlank())
    }

    /**
     * 打包册必须用**打包**策略校验，不能沿用占位分组册的策略。
     *
     * 回归防线：曾经打包器复用 `APPLICATION_GROUPING_POLICY`（结尾是「词条尚未随本任务打包」），
     * 而解析器也拿它逐字校验——于是包里写着「没有词条」却装着 100 个词条，且导入永远被拒。
     * 这条测试把两条策略的**差异**钉住：占位文案必须被打包校验拒绝，反之亦然。
     */
    @Test
    fun packagedBookRejectsThePlaceholderGroupingPolicy() {
        val placeholderPolicy = WordBookPackageFixture.writePackage(
            tempDirectory("placeholder-policy"),
            sourcePolicy = WordBookMetadataPolicy.APPLICATION_GROUPING_POLICY,
        )
        assertEquals(
            WordBookPackageRejection.MetadataRejected,
            rejectionOf(placeholderPolicy),
            "打包册用了占位分组文案（含「词条尚未随本任务打包」）时必须拒绝——那与包里有词条的事实矛盾",
        )

        assertTrue(
            WordBookMetadataPolicy.PACKAGED_BOOK_POLICY != WordBookMetadataPolicy.APPLICATION_GROUPING_POLICY,
            "两条策略必须不同，否则打包校验等于没校验",
        )
        assertEquals(
            MetadataValidationResult.Valid,
            WordBookMetadataPolicy.validatePackaged(
                WordBookMetadata(
                    id = WordBookPackageFixture.BOOK_ID,
                    displayName = "测试词书",
                    level = "基础",
                    totalWords = 2,
                    dataVersion = "v1",
                    sourceId = WordBookMetadataPolicy.PACKAGED_BOOK_SOURCE_ID,
                    sourcePolicy = WordBookMetadataPolicy.PACKAGED_BOOK_POLICY,
                ),
            ),
        )
    }

    @Test
    fun senseAndListCapsAreEnforced() {
        val tooManySenses = WordBookPackageFixture.writePackage(
            tempDirectory("senses"),
            cards = listOf(
                WordBookPackageFixture.CardSpec(
                    "over", 1,
                    senses = listOf("n." to "一", "v." to "二", "adj." to "三", "adv." to "四", "prep." to "五"),
                ),
            ),
        )
        assertEquals(WordBookPackageRejection.InvalidCard, rejectionOf(tooManySenses))

        val blankSense = WordBookPackageFixture.writePackage(
            tempDirectory("blank"),
            cards = listOf(WordBookPackageFixture.CardSpec("blank", 1, senses = listOf("n." to "   "))),
        )
        assertEquals(WordBookPackageRejection.InvalidCard, rejectionOf(blankSense))

        val longMeaning = WordBookPackageFixture.writePackage(
            tempDirectory("long"),
            cards = listOf(WordBookPackageFixture.CardSpec("long", 1, senses = listOf("n." to "长".repeat(31)))),
        )
        assertEquals(WordBookPackageRejection.InvalidCard, rejectionOf(longMeaning))
    }

    @Test
    fun imagePathEscapingPackageRootIsRejected() {
        val directory = WordBookPackageFixture.writePackage(tempDirectory("image-slip"))
        val book = File(directory, "book.json").readText(Charsets.UTF_8)
            .replace("images/parent.webp", "../outside.webp")
        val manifest = File(directory, "manifest.json").readText(Charsets.UTF_8)
            .replace("images/parent.webp", "../outside.webp")
        File(directory, "book.json").writeText(book, Charsets.UTF_8)
        File(directory, "manifest.json").writeText(manifest, Charsets.UTF_8)
        File(directory.parentFile, "outside.webp").writeBytes(File(directory, "images/parent.webp").readBytes())

        assertEquals(WordBookPackageRejection.InvalidManifest, rejectionOf(directory))
    }

    @Test
    fun missingImageFileIsRejected() {
        val directory = WordBookPackageFixture.writePackage(tempDirectory("noimg"))
        File(directory, "images/parent.webp").delete()
        assertEquals(WordBookPackageRejection.ImageMissing, rejectionOf(directory))
    }

    @Test
    fun tamperedImageBytesAreRejected() {
        val directory = WordBookPackageFixture.writePackage(tempDirectory("tamper"))
        val image = File(directory, "images/parent.webp")
        image.writeBytes("EVIL-BYTES-SAME-LENGTH??".toByteArray().copyOf(image.readBytes().size))
        assertEquals(WordBookPackageRejection.ImageHashMismatch, rejectionOf(directory))
    }

    @Test
    fun byteCountMismatchIsRejectedEvenWhenHashMatches() {
        // 哈希校验之外还要核对字节数：sha 相同但长度不符说明 manifest 与文件不一致
        val directory = WordBookPackageFixture.writePackage(tempDirectory("bytes"), truncateImageBytes = true)
        assertEquals(WordBookPackageRejection.ImageHashMismatch, rejectionOf(directory))
    }

    @Test
    fun cardReferencingAnImageAbsentFromManifestIsRejected() {
        val directory = WordBookPackageFixture.writePackage(tempDirectory("orphan"))
        // 词条仍然引用图片，但 manifest 里没有任何记录
        File(directory, "manifest.json").writeText("""{"formatVersion":1,"images":[]}""", Charsets.UTF_8)
        assertEquals(WordBookPackageRejection.OrphanImage, rejectionOf(directory))
    }

    @Test
    fun manifestEntryNotReferencedByAnyCardIsRejected() {
        val directory = WordBookPackageFixture.writePackage(
            tempDirectory("unused"),
            extraManifestEntries = listOf("images/unused.webp"),
        )
        assertEquals(WordBookPackageRejection.ManifestEntryUnused, rejectionOf(directory))
    }

    @Test
    fun cardHashDisagreeingWithManifestIsRejected() {
        val directory = WordBookPackageFixture.writePackage(tempDirectory("hash"))
        val book = File(directory, "book.json").readText(Charsets.UTF_8)
            .replace(shaOf(directory, "parent"), "0".repeat(64))
        File(directory, "book.json").writeText(book, Charsets.UTF_8)
        assertEquals(WordBookPackageRejection.ImageHashMismatch, rejectionOf(directory))
    }

    private fun shaOf(directory: File, lemma: String): String =
        WordBookPackageFixture.sha256(File(directory, "images/$lemma.webp").readBytes())

    private fun sizeOf(directory: File, lemma: String): Int =
        File(directory, "images/$lemma.webp").readBytes().size
}
