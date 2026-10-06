package com.example.englishlearning.wordbook

import android.content.res.AssetManager
import com.example.englishlearning.learning.WordBookMetadata
import com.example.englishlearning.learning.domain.WordCard
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 已安装的内置词书包**不该每读一次卡就被完整解析一次**。
 *
 * 缺陷形态：`ensureInstalled` 在「目录已存在」这条分支上仍然无条件 `WordBookPackageParser.parse(target)`。
 * 一次完整解析要读 `book.json` + `manifest.json`，并把**每张配图整个读出来算 sha256**——
 * 而 `parse` 是 `object` 上的静态方法，不注入就没法在 JVM 上数字数，所以这里通过
 * [BundledWordBookSource] 的 `parsePackage` 形参观察「读一次卡到底解析了几次」。
 *
 * 这条判据是**行为级**的（解析次数），不是计时：计时会随设备负载抖动成假红假绿。
 */
class BundledWordBookSourceParseCostTest {
    @Test
    fun `an already installed package is parsed once, not on every read`() = runBlocking {
        val root = Files.createTempDirectory("bundled-source").toFile()
        val bookId = "cet4"
        File(root, bookId).mkdirs() // 目录已存在 = 已安装

        var parses = 0
        val source = sourceCounting(root, bookId) { parses++ }

        val first = source.cardIds(bookId)
        repeat(2) { source.cardIds(bookId) }

        assertEquals(listOf("$bookId:ability", "$bookId:able"), first, "读卡结果本身也要正确")
        assertEquals(
            1,
            parses,
            "已安装且校验通过之后，后续读卡不该再完整解析整包：" +
                "每读一次解析一次＝把整包的图片重读一遍并逐个算 sha256",
        )
    }

    /**
     * 修复不能把「内容变了要能发现」一起丢掉：包内文件指纹（路径 + 大小 + mtime）变化时必须重新解析。
     * 这条是上面那条的代价边界——省掉的是**重复**解析，不是**变化检测**。
     */
    @Test
    fun `a package whose files changed is parsed again`() = runBlocking {
        val root = Files.createTempDirectory("bundled-source").toFile()
        val bookId = "cet4"
        val directory = File(root, bookId).apply { mkdirs() }
        File(directory, "book.json").writeText("v1")

        var parses = 0
        val source = sourceCounting(root, bookId) { parses++ }

        source.cardIds(bookId)
        assertEquals(1, parses, "首次读卡必然解析一次")

        File(directory, "book.json").writeText("v2-with-more-bytes")
        source.cardIds(bookId)
        assertEquals(2, parses, "包内容变了必须重新解析，不能因为缓存就看不见改动")
    }

    private fun sourceCounting(root: File, bookId: String, onParse: () -> Unit) =
        BundledWordBookSource(
            assets = mockk<AssetManager>(relaxed = true),
            root = root,
            io = UnconfinedTestDispatcher(),
            parsePackage = {
                onParse()
                Result.success(packageOf(bookId))
            },
        )

    private fun packageOf(bookId: String) = WordBookPackage(
        metadata = WordBookMetadata(
            id = bookId,
            displayName = "四级词书",
            level = "college",
            totalWords = 2,
            dataVersion = "v1",
            sourceId = "test-source",
            sourcePolicy = "TEST_POLICY",
        ),
        cards = listOf(card("$bookId:ability", "ability"), card("$bookId:able", "able")),
        images = emptyList(),
    )

    private fun card(cardId: String, lemma: String) = WordCard(
        cardId = cardId,
        wordBookId = cardId.substringBefore(':'),
        lemma = lemma,
        ipa = "/$lemma/",
        partOfSpeech = "n.",
        meaningZh = "释义",
        rank = 1,
    )
}
