package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.learning.domain.WordSense
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * m-5（评审席 `task-20A-review.md` Minor 段）：词汇列表的四个格式化 helper
 * （日期档、时分档、来源档、释义回退）在 `7a30a47` 之前是**两屏逐字同文**的一对私有副本
 * （`MyWordsScreen.kt:72-85` 与 `VocabularyScreen.kt:124-130`），且这族重复**没有任何门管**——
 * 份数棘轮族 4 的取样窗口只认 `^private fun …Row…\(`，对这两段隐形。
 * 控制者裁定「取抽一处那条」：收敛到 `ui/WordListFormat.kt` 一枚 internal，两屏同调。
 * 本文件因此同时是**行为用例**（搬家不改语义）与**重复门**（私有副本不得再长回来）。
 *
 * m-6（同一 Minor 段的第二件）：同一个「收藏于」时间戳在两页两种形（旧收藏页日期档、
 * 合并页时分档），而合并页自己的注释写着「用户在任何一处读到的解释必须还是同一句」。
 * 裁定统一到**日期档**（对齐旧页），所以这里钉「两页的收藏时间戳走同一枚日期档入口」，
 * 并钉生词本那条沿用旧页的时分档没有被顺手改掉（m-6 只管收藏时间戳，改两处的口径是扩面）。
 *
 * 口径沿用本仓源码契约：读 main 源码文本、先 `stripComments` 再匹配（`countMatches` 已内置剥注释）。
 */
class WordListFormatTest {

    private val instant: Instant = Instant.parse("2026-10-19T00:30:00Z")

    private fun card(meaningZh: String, vararg senses: WordSense) = WordCard(
        cardId = "card-1",
        wordBookId = "cet4",
        lemma = "surf",
        ipa = "",
        partOfSpeech = "v.",
        meaningZh = meaningZh,
        senses = senses.toList(),
    )

    /** 日期档：收藏时间戳只到日（m-6 取旧收藏页那一档，信息量对列表已够）。 */
    @Test
    fun date_label_is_the_date_tier() {
        assertEquals(
            "2026-10-19",
            wordListDateLabel(instant, ZoneOffset.UTC),
            "日期档必须是 yyyy-MM-dd（时分一出现，两页的「收藏于」就又分叉了——m-6 的缺陷形状）",
        )
    }

    /** 时分档：生词本条目的加入时间保持旧生词页那一档，搬家不得顺手改语义。 */
    @Test
    fun datetime_label_keeps_the_time_tier() {
        assertEquals(
            "2026-10-19 00:30",
            wordListDateTimeLabel(instant, ZoneOffset.UTC),
            "时分档必须是 yyyy-MM-dd HH:mm（`VocabularyScreen` 的「加入时间」与合并页的「加入于」共用它）",
        )
        // 同一时刻换时区就换日期：两页都按调用方传入的 zoneId 换算，不在 helper 里偷读系统时区。
        assertEquals(
            "2026-10-19 08:30",
            wordListDateTimeLabel(instant, java.time.ZoneId.of("Asia/Shanghai")),
        )
    }

    /** 来源档：已知的两枚反馈翻成人话，未知的一律保留原文（旧页口径逐字）。 */
    @Test
    fun source_label_maps_known_feedback_and_passes_unknown_through() {
        assertEquals("来源：不认识", wordListSourceLabel("Again"))
        assertEquals("来源：手动加入", wordListSourceLabel("Manual"))
        assertEquals("来源：Hard", wordListSourceLabel("Hard"))
        assertEquals("来源：", wordListSourceLabel(""))
    }

    /** 释义档：主释义空则退第一义项，两者都空给 null（调用方据此不给死释义）。 */
    @Test
    fun meaning_falls_back_to_the_first_sense_then_null() {
        assertEquals("冲浪", wordListMeaning(card("冲浪")))
        assertEquals(
            "浪花",
            wordListMeaning(card("", WordSense("n.", "浪花"), WordSense("v.", "冲浪"))),
            "主释义为空串时必须退到第一枚义项，不是直接判空",
        )
        assertNull(wordListMeaning(card("")))
        assertNull(wordListMeaning(card("   ", WordSense("n.", ""))), "全空白也算没有释义")
    }

    /**
     * 重复门（m-5 的承重面）：这四个 helper 在整个 `ui/` 源码树里**只许存在一处定义**，
     * 三屏各自的私有副本必须为 0；两屏的收藏时间戳必须调同一枚日期档入口。
     */
    @Test
    fun the_format_helpers_live_in_exactly_one_place() {
        val names = listOf(
            "wordListDateLabel",
            "wordListDateTimeLabel",
            "wordListSourceLabel",
            "wordListMeaning",
        )
        val declarations = uiFiles().map { file ->
            file.name to names.count { name ->
                countMatches(file.readText(), Regex("""fun $name\(""")) > 0
            }
        }.filter { it.second > 0 }
        assertEquals(
            listOf("WordListFormat.kt" to names.size),
            declarations.sortedBy { it.first },
            "词汇列表的格式化 helper 必须恰在 WordListFormat.kt 一处定义、四枚齐备" +
                "（别处再长一份 = m-5 的逐字复制回来了，而这族重复不在份数棘轮的取样窗口里，没有门会响）",
        )

        listOf("MyWordsScreen.kt", "VocabularyScreen.kt", "FavoritesScreen.kt").forEach { screenName ->
            val screen = uiScreenSource(screenName)
            assertEquals(
                0,
                countMatches(screen, Regex("""private (?:val|fun) \w*(?:DateFormatter|DateLabel|AddedLabel|SourceLabel|Meaning)\w*""")),
                "$screenName 不得再留私有格式化 helper（m-5：两份逐字同文的副本就是这次要收敛的那份债）",
            )
        }

        // 两页并存期间「收藏于」必须同文：同一枚日期档入口、每页该串恰一处。
        val myWords = uiScreenSource("MyWordsScreen.kt")
        val favorites = uiScreenSource("FavoritesScreen.kt")
        listOf("MyWordsScreen.kt" to myWords, "FavoritesScreen.kt" to favorites).forEach { (name, source) ->
            assertEquals(
                1,
                countMatches(source, Regex("""收藏于 \$\{wordListDateLabel\(""")),
                "$name 的「收藏于」时间戳必须恰一处走 wordListDateLabel（0 = 又各自内联一套格式，" +
                    "2 = 同一页长出两枚说法）",
            )
            assertFalse(
                "toLocalDate()" in source,
                "$name 不得再自己拼日期（m-6 的缺陷形状就是两页各拼各的）",
            )
        }
        // 生词本两页的时分档与来源档同样只能走共享入口（m-5 收敛的另一半）。
        val vocabulary = uiScreenSource("VocabularyScreen.kt")
        listOf(myWords, vocabulary).forEach { source ->
            assertTrue(
                countMatches(source, Regex("""wordListSourceLabel\(""")) >= 1,
                "来源档必须调共享 wordListSourceLabel（旧生词页与合并页同文）",
            )
            assertTrue(
                countMatches(source, Regex("""wordListDateTimeLabel\(""")) >= 1,
                "加入时间档必须调共享 wordListDateTimeLabel（时分档只在这一处口径里保留）",
            )
        }
        assertTrue(
            File("${UI_MAIN_SOURCE_PATH}/WordListFormat.kt").isFile,
            "WordListFormat.kt 必须真实存在于 ui/ 根（缺失时上面的扫描会静默放行）",
        )
    }
}
