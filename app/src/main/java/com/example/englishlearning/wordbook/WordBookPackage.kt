package com.example.englishlearning.wordbook

import com.example.englishlearning.learning.WordBookMetadata
import com.example.englishlearning.learning.WordBookMetadataPolicy
import com.example.englishlearning.learning.MetadataValidationResult
import com.example.englishlearning.learning.domain.DerivedWord
import com.example.englishlearning.learning.domain.PhraseEntry
import com.example.englishlearning.learning.domain.RelatedWord
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.learning.domain.WordSense
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 包内一张图片的记录：路径（相对包根）、sha256 与字节数。 */
data class PackageImage(val file: String, val sha256: String, val bytes: Int)

/** 解析通过、校验完整的词书包。 */
data class WordBookPackage(
    val metadata: WordBookMetadata,
    val cards: List<WordCard>,
    val images: List<PackageImage>,
)

/** 拒绝原因。逐个具名而不是一句话，便于测试精确断言与界面分因提示。 */
enum class WordBookPackageRejection {
    MissingBookJson,
    InvalidBookJson,
    InvalidBookId,
    UnsupportedFormatVersion,
    NoCards,
    InvalidCard,
    MetadataRejected,
    MissingManifest,
    InvalidManifest,
    ImageMissing,
    ImageHashMismatch,
    OrphanImage,
    ManifestEntryUnused,
    /** 条目数、单文件、总量或压缩比超过 [ImportLimits]：防解压炸弹。 */
    PackageTooLarge,
    /** 包里有清单之外的文件：只允许 book.json、manifest.json 与清单列出的图片。 */
    UnlistedFile,
}

/**
 * 包被拒绝。`rejection` 是给界面分因提示用的具名原因；`cause` 保留底层异常（如 JSON 解析器
 * 的报错）**仅供测试与日志诊断**——没有 cause 时，「所有包都 InvalidBookJson」这类共性故障
 * 只能靠猜（本次就因此多花了一轮构建才定位到序列化器没生成）。
 */
class WordBookPackageException(
    val rejection: WordBookPackageRejection,
    cause: Throwable? = null,
) : RuntimeException(rejection.name, cause, false, false)

/**
 * 词书包解析与校验（**离线**，不联网、不读数据库、不碰密钥）。
 *
 * 校验规则（任何一条不过 → 整包拒绝，不产出半份数据）：
 * 1. `formatVersion` 必须等于 [FORMAT_VERSION]；
 * 2. 词书元数据必须过 [WordBookMetadataPolicy]（词表来源白名单 + 来源说明措辞）；
 * 3. 册 id 不含路径分隔符与冒号、不占用工作目录前缀；册名与等级非空且有长度上限；
 * 4. 词条：`cardId` 唯一且以 `<bookId>:` 开头、必填字段非空、`senses` 1–4 条、派生/短语/近义词各 ≤3 条，
 *    所有文本字段非空且不超过对应长度上限（导入内容不可信）；
 * 5. 每张被引用的图片都必须在 `manifest.json` 里有记录，且**文件存在、sha256 与字节数都对得上**；
 * 6. `manifest.json` 里不允许重复条目，也不允许没有任何词条引用的孤儿图片（防止包内塞私货或漂移）。
 *
 * 未知字段容忍（`ignoreUnknownKeys = true`）：包格式将来会加字段，旧版本不该因此拒绝整包；
 * 但**缺失必填字段一定拒绝**（kotlinx 的必填语义天然做到）。
 */
object WordBookPackageParser {
    const val FORMAT_VERSION = 1
    const val MAX_SENSES = 4
    const val MAX_LIST = 3
    const val MAX_MEANING_CHARS = 30
    /** 册名、等级：要进词书列表的单行文本。 */
    const val MAX_NAME_CHARS = 40
    /** 单词、音标、词性、短语：一行放得下的词级文本。 */
    const val MAX_TERM_CHARS = 64
    /** 例句及其译文。 */
    const val MAX_SENTENCE_CHARS = 300

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(directory: File): Result<WordBookPackage> = runCatching {
        val bookFile = File(directory, "book.json")
        if (!bookFile.isFile) throw reject(WordBookPackageRejection.MissingBookJson)
        val book = runCatching { json.decodeFromString<BookDto>(bookFile.readText(Charsets.UTF_8)) }
            .getOrElse { throw reject(WordBookPackageRejection.InvalidBookJson, it) }
        if (book.formatVersion != FORMAT_VERSION) throw reject(WordBookPackageRejection.UnsupportedFormatVersion)
        if (book.id.isBlank() || book.id == "." || book.id == ".." ||
            book.id.contains('/') || book.id.contains('\\') || book.id.contains(':') ||
            WordBookPackageImporter.isReservedName(book.id)) {
            throw reject(WordBookPackageRejection.InvalidBookId)
        }
        if (!book.displayName.fits(MAX_NAME_CHARS) || !book.level.fits(MAX_NAME_CHARS)) {
            throw reject(WordBookPackageRejection.InvalidBookJson)
        }
        if (book.cards.isEmpty()) throw reject(WordBookPackageRejection.NoCards)

        val metadata = WordBookMetadata(
            id = book.id,
            displayName = book.displayName,
            level = book.level,
            totalWords = book.cards.size,
            dataVersion = "v1",
            sourceId = book.sourceId,
            sourcePolicy = book.sourcePolicy,
        )
        if (WordBookMetadataPolicy.validatePackaged(metadata) != MetadataValidationResult.Valid) {
            throw reject(WordBookPackageRejection.MetadataRejected)
        }

        val manifestFile = File(directory, "manifest.json")
        if (!manifestFile.isFile) throw reject(WordBookPackageRejection.MissingManifest)
        val manifest = runCatching {
            json.decodeFromString<ManifestDto>(manifestFile.readText(Charsets.UTF_8))
        }.getOrElse { throw reject(WordBookPackageRejection.InvalidManifest, it) }
        if (manifest.formatVersion != FORMAT_VERSION) throw reject(WordBookPackageRejection.UnsupportedFormatVersion)
        // 重复条目会让 associateBy 只留最后一条，导出时还会写出重复 zip 条目
        if (manifest.images.map { it.file }.toSet().size != manifest.images.size) {
            throw reject(WordBookPackageRejection.InvalidManifest)
        }
        val manifestByFile = manifest.images.associateBy { it.file }

        val seenIds = mutableSetOf<String>()
        val usedImages = mutableSetOf<String>()
        val cards = book.cards.map { card ->
            validateCard(book.id, card)
            if (!seenIds.add(card.cardId)) throw reject(WordBookPackageRejection.InvalidCard)
            val senses = card.senses.map { WordSense(it.pos, it.meaningZh) }
            val imagePath = card.image?.let { reference ->
                val entry = manifestByFile[reference.file] ?: throw reject(WordBookPackageRejection.OrphanImage)
                if (entry.sha256 != reference.sha256) throw reject(WordBookPackageRejection.ImageHashMismatch)
                usedImages += entry.file
                verifyImage(directory, entry)
            }
            WordCard(
                cardId = card.cardId,
                wordBookId = book.id,
                lemma = card.lemma,
                ipa = card.ipa,
                partOfSpeech = senses.first().partOfSpeech,
                meaningZh = senses.first().meaningZh,
                example = card.example,
                senses = senses,
                exampleZh = card.exampleZh,
                derived = card.derived.map { DerivedWord(it.lemma, it.pos, it.meaningZh) },
                phrases = card.phrases.map { PhraseEntry(it.text, it.meaningZh) },
                synonyms = card.synonyms.map { RelatedWord(it.lemma, it.pos, it.meaningZh) },
                imagePath = imagePath,
                rank = card.rank,
            )
        }
        if (manifest.images.any { it.file !in usedImages }) {
            throw reject(WordBookPackageRejection.ManifestEntryUnused)
        }
        WordBookPackage(metadata, cards, manifest.images.map { PackageImage(it.file, it.sha256, it.bytes) })
    }

    private fun validateCard(bookId: String, card: CardDto) {
        val rejection = WordBookPackageRejection.InvalidCard
        // 必须在本册命名空间内：导入册优先于内置册，冒用别册的 id 就能顶替它的词卡
        val namespace = "$bookId:"
        if (!card.cardId.startsWith(namespace) || card.cardId.length == namespace.length) throw reject(rejection)
        if (!card.lemma.fits(MAX_TERM_CHARS) || !card.ipa.fits(MAX_TERM_CHARS)) throw reject(rejection)
        if (!card.example.fits(MAX_SENTENCE_CHARS)) throw reject(rejection)
        if (card.exampleZh != null && !card.exampleZh.fits(MAX_SENTENCE_CHARS)) throw reject(rejection)
        if (card.senses.isEmpty() || card.senses.size > MAX_SENSES) throw reject(rejection)
        card.senses.forEach { sense ->
            if (!sense.pos.fits(MAX_TERM_CHARS) || !sense.meaningZh.fits(MAX_MEANING_CHARS)) throw reject(rejection)
        }
        listOf(card.derived.size, card.phrases.size, card.synonyms.size).forEach { size ->
            if (size > MAX_LIST) throw reject(rejection)
        }
        val terms = card.derived.map { it.lemma to it.pos } + card.synonyms.map { it.lemma to it.pos }
        terms.forEach { (lemma, pos) ->
            if (!lemma.fits(MAX_TERM_CHARS) || !pos.fits(MAX_TERM_CHARS)) throw reject(rejection)
        }
        card.phrases.forEach { if (!it.text.fits(MAX_TERM_CHARS)) throw reject(rejection) }
        val meanings = card.derived.map { it.meaningZh } + card.phrases.map { it.meaningZh } +
            card.synonyms.map { it.meaningZh }
        meanings.forEach { if (!it.fits(MAX_MEANING_CHARS)) throw reject(rejection) }
    }

    /** 非空白且不超过 [max] 个字符。 */
    private fun String.fits(max: Int): Boolean = isNotBlank() && length <= max

    /** 逐张核对：文件在、sha256 对、字节数对。返回该图的绝对路径。 */
    private fun verifyImage(directory: File, entry: ManifestImageDto): String {
        val root = directory.canonicalFile
        val file = File(root, entry.file.replace('/', File.separatorChar))
        if (!file.canonicalPath.startsWith(root.path + File.separator)) {
            throw reject(WordBookPackageRejection.InvalidManifest)
        }
        if (!file.isFile) throw reject(WordBookPackageRejection.ImageMissing)
        val bytes = file.readBytes()
        if (bytes.size != entry.bytes) throw reject(WordBookPackageRejection.ImageHashMismatch)
        if (sha256Hex(bytes) != entry.sha256) throw reject(WordBookPackageRejection.ImageHashMismatch)
        return file.absolutePath
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun reject(reason: WordBookPackageRejection, cause: Throwable? = null) =
        WordBookPackageException(reason, cause)
}

// ------------------------------------------------------------------ DTO（文件级：object 内部嵌套会导致 @Serializable 序列化器解析不到）


@Serializable
private data class BookDto(
    val formatVersion: Int,
    val id: String,
    val displayName: String,
    val level: String,
    val sourceId: String,
    val sourcePolicy: String,
    val attribution: String? = null,
    val cards: List<CardDto>,
)

@Serializable
private data class CardDto(
    val cardId: String,
    val lemma: String,
    val rank: Int? = null,
    val ipa: String,
    val senses: List<SenseDto>,
    val example: String,
    val exampleZh: String? = null,
    val derived: List<DerivedDto> = emptyList(),
    val phrases: List<PhraseDto> = emptyList(),
    val synonyms: List<RelatedDto> = emptyList(),
    val image: ImageRefDto? = null,
)

@Serializable
private data class SenseDto(val pos: String, val meaningZh: String)

@Serializable
private data class DerivedDto(val lemma: String, val pos: String, val meaningZh: String)

@Serializable
private data class PhraseDto(val text: String, val meaningZh: String)

@Serializable
private data class RelatedDto(val lemma: String, val pos: String, val meaningZh: String)

@Serializable
private data class ImageRefDto(val file: String, val sha256: String)

@Serializable
private data class ManifestDto(val formatVersion: Int, val images: List<ManifestImageDto> = emptyList())

@Serializable
private data class ManifestImageDto(
    val file: String,
    val sha256: String,
    val bytes: Int,
    val width: Int? = null,
    val height: Int? = null,
    val license: String? = null,
)
