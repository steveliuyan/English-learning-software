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
            "时分档必须是 yyyy-MM-dd HH:mm（合并页「加入于」用它；原生词本页的「加入时间」同口径，该屏已删）",
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

        // Task 22-B：名单里的收藏页屏已下线（`FavoritesScreen.kt` 随屏摘除）——本行是**消费方减少**，
        // 循环体内三条禁令逐字未动。2026-10-25 简化批又删掉了孤儿屏 `VocabularyScreen.kt`
        // （全仓零调用点），消费方只剩合并页 MyWordsScreen。裁决见
        // `docs/decisions/2026-10-25-ui-simplification-deletions.md`。
        listOf("MyWordsScreen.kt").forEach { screenName ->
            val screen = uiScreenSource(screenName)
            assertEquals(
                0,
                countMatches(screen, Regex("""private (?:val|fun) \w*(?:DateFormatter|DateLabel|AddedLabel|SourceLabel|Meaning)\w*""")),
                "$screenName 不得再留私有格式化 helper（m-5：两份逐字同文的副本就是这次要收敛的那份债）",
            )
            // N-3（修复轮 3，复评 §C Minor）：上面那条只认「`private` 前缀 + 五个名字片段」，
            // 于是这两半它都挡不住——① 把 helper 改个名（`myWordsFormatter` 之类不在名单里的名字）；
            // ② 根本不新建声明，把 `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")` **内联进屏**，
            // 此时私有副本正则为 0、共享名的声明扫描也 0、用量锚 `>= 1` 还被另一条分支喂饱 ⇒ 全绿。
            // 补一道**按形态**的闸：名单里每一屏的源码都不得出现 `DateTimeFormatter` 与 `ofPattern(` 任何一个，
            // 格式化只许走 WordListFormat.kt 那一处；改名与内联两条路一起堵死。
            listOf("DateTimeFormatter" to "自建 formatter 类型", """ofPattern\(""" to "就地拼格式")
                .forEach { (forbidden, why) ->
                    assertEquals(
                        0,
                        countMatches(screen, Regex(forbidden)),
                        "$screenName 不得出现「$forbidden」（$why）：N-3 的缺陷形状就是「不新建 private 声明、" +
                            "直接把格式内联回屏里」，上面那道只认私有声明的门对它是瞎的，" +
                            "格式化只能走 WordListFormat.kt 那一处（改名副本也同样被这条挡住）",
                    )
                }
        }
        // 反向钉（派工单 N-3「确认这条新断言不会误伤 WordListFormat.kt 本体」）：禁令只作用于**屏**，
        // helper 本体必须还在原地造 formatter——否则「名单里每一屏都 0 命中」会因为唯一真值被搬空而假绿。
        val formatSource = File("${UI_MAIN_SOURCE_PATH}/WordListFormat.kt").readText()
        assertEquals(
            2,
            countMatches(formatSource, Regex("""private val \w+ = DateTimeFormatter\.ofPattern\(""")),
            "WordListFormat.kt 必须仍恰有两枚顶层 formatter（日期档 + 时分档）：0/1 枚 = 唯一真值被搬空或减档，" +
                "上面那组「三屏 0 命中」会随之假绿",
        )

        // 「收藏于」时间戳的口径闸（Task 22-B 后只剩合并页一枚消费方——收藏页屏已随 22-B 下线）：
        // 断言本体一字未松，仍要求「恰一处走 wordListDateLabel」+「不得自拼 toLocalDate()」；
        // 少的是**取样面**（原两页同文对照里的第二页），不是判据强度。
        val myWords = uiScreenSource("MyWordsScreen.kt")
        listOf("MyWordsScreen.kt" to myWords).forEach { (name, source) ->
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
        // 生词本段的时分档与来源档同样只能走共享入口（m-5 收敛的另一半）。
        // 旧生词页 `VocabularyScreen.kt` 已于 2026-10-25 简化批删除（全仓零调用点，
        // 见 `docs/decisions/2026-10-25-ui-simplification-deletions.md`）；断言强度不变，
        // 取样面从两页收成一页。
        assertTrue(
            countMatches(myWords, Regex("""wordListSourceLabel\(""")) >= 1,
            "来源档必须调共享 wordListSourceLabel（生词段 / 错词段共用同一句口径）",
        )
        assertTrue(
            countMatches(myWords, Regex("""wordListDateTimeLabel\(""")) >= 1,
            "加入时间档必须调共享 wordListDateTimeLabel（时分档只在这一处口径里保留）",
        )
        assertTrue(
            File("${UI_MAIN_SOURCE_PATH}/WordListFormat.kt").isFile,
            "WordListFormat.kt 必须真实存在于 ui/ 根（缺失时上面的扫描会静默放行）",
        )
    }

    /**
     * 词性 + 释义那一档（原型 `sLearnQueue` 的 `wordRow` 副文案 `w.pos + ' ' + w.meaning`，`app.js:366`）。
     *
     * 批 1 Task 16 新增：今日词单要把它画成词行的副文案。收进本文件而不是屏侧自拼，理由与 m-5 同源——
     * 「词汇类列表的格式化 helper 只许一处」，屏侧再拼一份 `pos + " " + meaning` 就会在下一屏长出第三档
     * （空词性留前导空格、空释义留尾随空格这类形状只有在这一处才收敛得掉）。
     */
    @Test
    fun gloss_pairs_part_of_speech_with_the_meaning_and_drops_blanks() {
        val both = WordCard(
            cardId = "c1", wordBookId = "cet4", lemma = "surf", ipa = "",
            partOfSpeech = "v.", meaningZh = "冲浪",
        )
        assertEquals("v. 冲浪", wordListGloss(both), "词性与释义都在：`词性 释义`，中间一个空格（原型那一档）")

        val sensesOnly = WordCard(
            cardId = "c2", wordBookId = "cet4", lemma = "wave", ipa = "",
            partOfSpeech = "", meaningZh = "",
            senses = listOf(WordSense(partOfSpeech = "n.", meaningZh = "波浪")),
        )
        assertEquals("n. 波浪", wordListGloss(sensesOnly), "主档为空时退到第一枚义项，词性跟着那枚义项走（不混用两档）")

        val meaningOnly = WordCard(
            cardId = "c3", wordBookId = "cet4", lemma = "surfing", ipa = "",
            partOfSpeech = "", meaningZh = "冲浪运动",
        )
        assertEquals("冲浪运动", wordListGloss(meaningOnly), "词性缺失时不得留前导空格")

        val empty = WordCard(
            cardId = "c4", wordBookId = "cet4", lemma = "unknown", ipa = "",
            partOfSpeech = "", meaningZh = "",
        )
        assertNull(wordListGloss(empty), "两样都没有就给 null：调用方不渲染副文案槽，而不是画一行空白")
    }

    /**
     * 重复门：副文案档只许住在 `WordListFormat.kt`，屏侧不得再拼第二份。
     *
     * 判据钉的是**拼法只有一处**，不是调用次数：原型 `wordRow`（`assets/app.js:222-231`）在新学段与
     * 复习段各调一次，本屏两节同形，所以 `wordListGloss(` 恰 2 处是**预期**；把两节收成一次调用要引入
     * 「先合并两节再分段渲染」的中间结构，那是原型没有的形态。真正的重复信号是屏侧出现第二份拼法
     * ——即本屏自己读 `partOfSpeech`（`wordListGloss` 的实现细节），所以那条计数恒 0。
     */
    @Test
    fun gloss_helper_lives_only_in_the_shared_format_file() {
        val formatSource = File("${UI_MAIN_SOURCE_PATH}/WordListFormat.kt").readText()
        assertEquals(
            1,
            countMatches(formatSource, Regex("""internal fun wordListGloss\(""")),
            "wordListGloss 必须在 WordListFormat.kt 恰定义一处",
        )
        uiScreenSource("TodayWordListScreen.kt").let { queue ->
            assertEquals(
                2,
                countMatches(queue, Regex("""wordListGloss\(""")),
                "两节各一枚行渲染调用点（原型 wordRow 在新学/复习两段各调一次）：第三处即另起一节或" +
                    "在别处再拼一遍副文案",
            )
            assertEquals(
                0,
                countMatches(queue, Regex("""partOfSpeech""")),
                "屏侧不许自己读词性档：读了就是绕开 wordListGloss 的第二份拼法（空值退让口径会漂）",
            )
        }
        // 批 1 Task 17：复习队列屏的到期词节是这份副文案的第二个消费者，同一个门。
        uiScreenSource("ReviewQueueScreen.kt").let { review ->
            assertEquals(
                1,
                countMatches(review, Regex("""wordListGloss\(""")),
                "复习队列只有「到期词」一节摆词行（原型 `sReview` 的 `section('到期词', …)`，`app.js:484-490`）：" +
                    "第二处即另起一节再拼一遍，零处即绕开共享档自己拼",
            )
            assertEquals(
                0,
                countMatches(review, Regex("""partOfSpeech""")),
                "复习队列屏侧同样不许自己读词性档",
            )
        }
        assertTrue("partOfSpeech" in formatSource, "词性档读的是卡片自己的 partOfSpeech，不在屏侧读")
    }
}
