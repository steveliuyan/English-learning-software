package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 词库页（spec §3.3）的源码契约：四分段、一段一面、旧两页降为 pane。
 *
 * 为什么这层判据必须存在：`WordBankScreen` 是**壳**——它不含数据、不含词行，四段的内容分别由
 * `WordBookWordsScreen`（词书段）与 `MyWordsScreen`（生词 / 错词 / 收藏段）承担。壳一旦漏挂某段、
 * 或者两段接到同一个面上，源码看是「四段都在」，用户看到的是「两段一模一样」或「有一段永远空白」。
 * 反向的两件事同样致命：两枚被复用的屏各自再画一次页头（同屏三个返回位）、或宿主把旧的两条独立路由
 * （`showWordBookWords` / `showMyWords`）留在册（同一入口有两条通路，返回键行为随即分裂）。
 *
 * 行为面由 connected `WordBankScreenTest` 的四段切换 smoke 承重；本文件钉形状与宿主接线。
 * 读法与批 1 其余契约一致（`SourceRatchet.kt` 的 `uiScreenSource` 已剥注释）：枚数即判据，不写「至少」。
 */
class WordBankScreenContractTest {
    private val bank: String by lazy { uiScreenSource("WordBankScreen.kt") }
    private val host: String by lazy { uiScreenSource("AppScreen.kt") }
    private val myWords: String by lazy { uiScreenSource("MyWordsScreen.kt") }
    private val bookWords: String by lazy { uiScreenSource("WordBookWordsScreen.kt") }

    @Test
    fun `the bank exposes exactly four segments`() {
        assertEquals(
            listOf("Book", "New", "Wrong", "Favorite"),
            WordBankSection.entries.map { it.name },
            "四分段顺序钉死：词书 / 生词 / 错词 / 收藏（§3.3）——顺序即用户的心智模型，" +
                "「词书」必须在第一格（它是默认落点，也是唯一带筛选与字母栏的一段）",
        )
        listOf(
            "word_bank_segment_book",
            "word_bank_segment_new",
            "word_bank_segment_wrong",
            "word_bank_segment_favorite",
        ).forEach { tag ->
            assertEquals(1, countMatches(bank, Regex(Regex.escape(tag))), "分段锚「$tag」必须恰一处")
        }
        assertEquals(1, countMatches(bank, Regex("""AppScreenHeader\(""")), "词库页恰一枚共享页头")
        assertEquals(1, countMatches(bank, Regex("""testTag\("word_bank_screen"\)""")), "屏根锚必须恰一处")
        // 四段各挨一个面：少一段 = 那一格点了没反应；多一段 = 同一段的两面互相抢屏。
        WordBankSection.entries.forEach { section ->
            assertEquals(
                1,
                countMatches(bank, Regex("""WordBankSection\.${section.name} -> \w+Pane\(\)""")),
                "`when (section)` 里 ${section.name} 段必须恰接一个面（0 = 该格是死格子）",
            )
        }
    }

    @Test
    fun `the old screens become panes instead of second headers`() {
        // 两枚被复用的屏必须能关掉自己的页头：同屏只许词库页那一枚返回位。
        assertEquals(
            1,
            countMatches(myWords, Regex("""showHeader: Boolean = true""")),
            "MyWordsScreen 必须能关掉自己的页头（默认 true：既有直接挂载点与 connected 夹具一格不动）",
        )
        assertEquals(
            1,
            countMatches(myWords, Regex("""visibleSections: List<MyWordsSection> = MyWordsSection\.entries""")),
            "MyWordsScreen 必须能只渲染点名段（默认全集：既有「三段同屏」的行为判据不受影响）",
        )
        assertEquals(
            1,
            countMatches(bookWords, Regex("""showHeader: Boolean = true""")),
            "WordBookWordsScreen 必须能关掉自己的页头（默认 true：无头版的散页挂载不破）",
        )
        assertEquals(
            1,
            countMatches(host, Regex("""\bWordBankScreen\(""")),
            "宿主恰挂一枚 WordBankScreen（0 = 词库页没有宿主，2 = 长出第二条词库通路）",
        )
        assertEquals(
            1,
            countMatches(host, Regex("""\bWordBookWordsScreen\(""")),
            "词书段必须恰接一枚 WordBookWordsScreen（0 = 词书段空着，2 = 词书段里长了两份列表）",
        )
        assertEquals(
            0,
            countMatches(host, Regex("""showWordBookWords\b""")),
            "旧的 showWordBookWords 状态位必须并进 showWordBank（留着 = 同一目的地两条通路）",
        )
        assertEquals(
            0,
            countMatches(host, Regex("""showMyWords\b""")),
            "旧的 showMyWords 状态位必须并进 showWordBank（留着 = 同一目的地两条通路）",
        )
        // 一面 = 一枚收窄了 visibleSections 的 MyWordsScreen。三段各点名一次，
        // 且三段共用同一处窄化写法（`visibleSections = listOf(section)` 只写一遍）——
        // 「其余实参照搬」抄三遍时最容易漏的那枚形参（masteryState 之类）就被这层 DRY 钉死。
        listOf("Wordbook", "Wrong", "Favorites").forEach { name ->
            assertEquals(
                1,
                countMatches(host, Regex("""myWordsPane\(MyWordsSection\.$name\)""")),
                "宿主必须恰一处用 $name 段注入 MyWordsScreen（0 = 该段没有面，2 = 同一段长出两面）",
            )
        }
        assertEquals(
            1,
            countMatches(host, Regex("""visibleSections = listOf\(""")),
            "四段的面必须共用同一处 visibleSections 窄化（三条各抄一遍 = 迟早漏掉其中一条）",
        )
        // 两枚 pane 屏都必须以 showHeader = false 挂进来（词书段 + 复用面各一枚）：
        // 漏了就是同屏两个返回位、两套标题。
        assertEquals(
            2,
            countMatches(host, Regex("""showHeader = false""")),
            "词书段与复用面都必须关掉自己的页头（词库页那枚 AppScreenHeader 是唯一返回位）",
        )
    }

    /**
     * 版式重做批 2（原型 `sMeWords`，`assets/app.js:1292`）：三格标签补「生词 N / 错词 N / 收藏 N」，
     * 词书格**永远不带数字**——它没有对应的词表 UiState，数字无从谈起。
     *
     * 计数是纯显示层：宿主从三段**已有**的 UiState 现算，形参默认 `null`。`null` 的语义是
     * 「读不到」（Loading / Unavailable），此时必须回落到裸段名——摆一个 0 会把「还没读出来」
     * 读成「确实是空的」，这是本条的主要反向面。`0` 只有在 `Ready` 且空册时才是真条数。
     */
    @Test
    fun `segment labels carry counts only when the host can read them`() {
        assertEquals(
            "词书",
            wordBankSegmentLabel(WordBankSection.Book, 3),
            "词书格不带数字：词书段没有条数来源，即使宿主多给一个数也不许显示",
        )
        assertEquals("词书", wordBankSegmentLabel(WordBankSection.Book, null))
        assertEquals("生词 3", wordBankSegmentLabel(WordBankSection.New, 3))
        assertEquals("错词 12", wordBankSegmentLabel(WordBankSection.Wrong, 12))
        assertEquals("收藏 8", wordBankSegmentLabel(WordBankSection.Favorite, 8))
        assertEquals(
            "生词 0",
            wordBankSegmentLabel(WordBankSection.New, 0),
            "0 是读得到的真实条数（Ready 且空册），要如实显示；它与被禁止的「拿 0 冒充读不到」不是一回事",
        )
        listOf(WordBankSection.New, WordBankSection.Wrong, WordBankSection.Favorite).forEach { section ->
            assertEquals(
                section.label,
                wordBankSegmentLabel(section, null),
                "${section.name} 段读不到条数时必须回落到裸段名（不许用 0 冒充）",
            )
        }
        // 三枚形参各恰一处、默认 null：默认值不是 0，壳在没人喂数时不带数字。
        listOf("newCount", "wrongCount", "favoriteCount").forEach { name ->
            assertEquals(
                1,
                countMatches(bank, Regex("""\b$name: Int\? = null""")),
                "$name 形参必须恰一处声明且默认 null（0 = 计数无从注入；2 = 两套计数口径）",
            )
        }
        assertEquals(
            2,
            countMatches(bank, Regex("""\bwordBankSegmentLabel\(""")),
            "标签构造必须只有一处调用点（声明 + 调用 = 2）：四格各拼一句就有四份口径",
        )
    }

    /**
     * 计数不许新开数据通路：宿主三枚实参必须直接取自三段自己的 UiState 列表长度。
     *
     * 反向面钉「不许造 0 兜底」——`?: 0` 一出现，Loading / 不可用就会显示成「空册」。
     */
    @Test
    fun `the host feeds the three counts straight from the panes own states`() {
        val wires = mapOf(
            "newCount" to """newCount = \(vocabularyState as\? VocabularyUiState\.Ready\)\?\.items\?\.size""",
            "wrongCount" to """wrongCount = \(wrongWordsState as\? WrongWordsUiState\.Ready\)\?\.cards\?\.size""",
            "favoriteCount" to """favoriteCount = \(favoritesState as\? FavoritesUiState\.Ready\)\?\.words\?\.size""",
        )
        wires.forEach { (name, pattern) ->
            assertEquals(
                1,
                countMatches(host, Regex(pattern)),
                "$name 必须恰一处从该段已有的 UiState 现算（0 = 该格计数没有来源，2 = 同一枚数长出两条来源）",
            )
        }
        assertEquals(
            0,
            countMatches(host, Regex("""(newCount|wrongCount|favoriteCount) = [^\n]*\?: 0""")),
            "宿主不得给计数 `?: 0` 兜底：读不到是 null（不带数字），0 只属于「Ready 且确实为空」",
        )
    }

    /**
     * 页头搜索动作的裁决（原型 `sMeWords` 的 `barTop.action` 那枚 search 图标）：**不搬**。
     *
     * 同一能力不在本页开第二个入口——查词是全站能力，今日学习页有常驻查词条
     * （`today_plan_search_entry`），全站另有查词页。本条的静态凭据 + 反向控一起盯：
     * 裁决句必须真写在 KDoc 里（源码注释，故读**原文**而不是 `uiScreenSource` 的剥注释版），
     * 且本屏不得长出任何搜索字形 / 页头 action 槽。
     */
    @Test
    fun `the header search action stays off this page`() {
        val raw = File(UI_MAIN_SOURCE_PATH, "WordBankScreen.kt").readText()
        assertTrue(
            "不搬" in raw && "today_plan_search_entry" in raw,
            "WordBankScreen 的 KDoc 必须写明搜索动作的裁决（不搬 + 查词入口在哪）：" +
                "原型页头那枚 search 图标若被当成「补一个入口」搬进来，同一能力就有两个入口",
        )
        assertEquals(
            0,
            countMatches(bank, Regex("""AppIcon\.Search""")),
            "本屏不得出现搜索字形（0 = 没有第二个查词入口）",
        )
        assertEquals(
            0,
            countMatches(bank, Regex("""AppScreenHeader\([\s\S]{0,200}?action =""")),
            "本屏页头不得挂 action 槽（0 = 页头只有标题与返回位；非 0 = 搜索键从页头长了回来）",
        )
    }

    @Test
    fun `the whole bank is one overlay route`() {
        // 返回键：整页一枚处理器，先让位给三段各自的详情层（三条详情宿主在宿主层已各自有处理器），
        // 都没有时才退整页——与合并页原来的三条件口径一字不差，只是状态位换了名。
        assertEquals(
            1,
            countMatches(host, Regex("""BackHandler\(\s*enabled = showWordBank[\s\S]*?showWordBank = false""")),
            "enabled 以 showWordBank 打头的 BackHandler 体内必须恰一处 `showWordBank = false`（0 = 返回键退不出词库）",
        )
        val overlayOpenBlock = host.substringAfter("val overlayOpen =").substringBefore("BackHandler(")
        assertTrue(
            "showWordBank" in overlayOpenBlock,
            "overlayOpen 聚合式里必须含 showWordBank：这是词库开着时返回键被叠层接管" +
                "（而不是退出整屏 / 穿透到底层导航）的唯一静态凭据",
        )
        assertTrue(
            Regex("""else if \(showWordBank\)[\s\S]*?WordBankScreen\(""").containsMatchIn(host),
            "渲染分支 `else if (showWordBank)` 后必须紧跟 WordBankScreen 挂载（0 = 状态位与渲染脱节）",
        )
    }
}
