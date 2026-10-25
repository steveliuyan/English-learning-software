package com.example.englishlearning.ui

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
