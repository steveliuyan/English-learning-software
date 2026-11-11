package com.example.englishlearning.wordbook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 2026-11-11 真机走查发现的展示缺陷的资产级闸门（AC5-03 侧的配套回归）：
 * 有 304 张随包卡片的 `ipa` 是占位串 `/- /`，其中 300 张把真音标错放进了
 * `senses[0].pos` —— 查词列表和详情页会把它原样画成 `/- /`。
 *
 * 数据已按 `tools/wordbooks/normalize_ipa.py` 归一，ETL 侧由
 * `excel_to_wordbook.split_pronunciation` 兜住未来重导。本类把结论钉在资产上：
 *
 * 1. 没有任何卡片的 `ipa` 是「只有斜杠/短横/空白」的占位串——缺失必须是空串，
 *    由 UI 的 `isNotBlank()` 判定隐藏整块，而不是画一根占位短横。
 * 2. 没有任何 `senses[].pos` 是斜杠包裹的音标（`/hɪə(r)/`）或斜杠包裹的词性（`/n./`）：
 *    音标归 `ipa`，词性归 `pos`，两列不许互换。
 * 3. 非空 `ipa` 必须是斜杠包裹的形态（`/.../`），不得是裸文本。
 */
class WordBookIpaFieldHonestyTest {

    private val root = File("src/main/assets/wordbooks")
    private val placeholder = Regex("""^[\s/\\\u2013\u2014~\u00b7-]*$""")
    private val ipaShaped = Regex("""^/[^/]{1,62}/$""")

    /**
     * 异音词是合法数据：`/kləʊz/ v.; /kləʊs/ adj.`、`/lɪv/；/laɪv/` 都由多段 `/.../` 组成。
     * 这里只钉两条用户可见的底线：非空的音标必须以 `/` 起头，且必须闭合（至少两个 `/`）。
     */
    private fun isIpaField(value: String): Boolean {
        val text = value.trim()
        return text.startsWith("/") && text.count { it == '/' } >= 2
    }

    private data class Card(val book: String, val lemma: String, val ipa: String, val poses: List<String>)

    private fun cards(): List<Card> {
        assumeTrue(root.isDirectory, "随包词书目录缺失：$root")
        val books = root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }.orEmpty()
        assumeTrue(books.isNotEmpty(), "随包词书目录为空：$root")
        return books.flatMap { dir ->
            val json = Json { ignoreUnknownKeys = true }
            val text = File(dir, "book.json").readText()
            val obj = json.parseToJsonElement(text).jsonObject
            obj["cards"]!!.jsonArray.map { element ->
                val card = element.jsonObject
                val senses = card["senses"]?.jsonArray.orEmpty()
                Card(
                    book = dir.name,
                    lemma = card["lemma"]?.jsonPrimitive?.content.orEmpty(),
                    ipa = card["ipa"]?.jsonPrimitive?.content.orEmpty(),
                    poses = senses.mapNotNull { it.jsonObject["pos"]?.jsonPrimitive?.content },
                )
            }
        }
    }

    @Test
    fun `no bundled card carries a dash placeholder in the ipa field`() {
        val offenders = cards().filter { it.ipa.isNotBlank() && placeholder.matches(it.ipa) }
            .map { "${it.book}/${it.lemma}=\"${it.ipa}\"" }
        assertTrue(
            offenders.isEmpty(),
            "这些卡片的 ipa 是占位串而不是空串（缺失要留空，由 UI 隐藏整块）：${offenders.take(10)}",
        )
    }

    @Test
    fun `no part of speech smuggles a pronunciation or hides behind slashes`() {
        val offenders = cards().flatMap { card -> card.poses.map { card to it } }
            .filter { (_, pos) -> ipaShaped.matches(pos.trim()) }
            .map { (card, pos) -> "${card.book}/${card.lemma}.pos=\"$pos\"" }
        assertTrue(
            offenders.isEmpty(),
            "音标不许待在 pos 列，词性也不许被斜杠包着：${offenders.take(10)}",
        )
    }

    @Test
    fun `every pronunciation that exists is slash wrapped`() {
        val offenders = cards().filter { it.ipa.isNotBlank() && !isIpaField(it.ipa) }
            .map { "${it.book}/${it.lemma}=\"${it.ipa}\"" }
        assertTrue(offenders.isEmpty(), "这些 ipa 不是 /.../ 形态：${offenders.take(10)}")
    }

    @Test
    fun `the shipped corpus still has all forty five thousand cards`() {
        assertEquals(45_290, cards().size, "词书卡片总数变了，先确认是否换代而不是静默漂移")
    }
}
