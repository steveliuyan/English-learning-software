package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.SegmentedControl
import com.example.englishlearning.ui.theme.appColors

/** 词库四分段（spec §3.3）：词书 / 生词 / 错词 / 收藏。 */
enum class WordBankSection(val label: String, val tag: String) {
    Book("词书", "word_bank_segment_book"),
    New("生词", "word_bank_segment_new"),
    Wrong("错词", "word_bank_segment_wrong"),
    Favorite("收藏", "word_bank_segment_favorite"),
}

/** 「读不到条数」在分段格上的记号（em dash）。见 [wordBankSegmentLabel]。 */
internal const val WordBankCountUnavailableMark: String = "—"

/**
 * 一格分段标签：**词书格永不带数字**（词书段没有对应的词表 UiState，条数无从谈起），
 * 其余三格在宿主读得到条数时补「 段名 N」。
 *
 * [count] 为 `null` 的语义是「读不到」（Loading / 不可用），此时补一枚 em dash 而不是回落成裸段名：
 * 裸段名是**词书格按设计的形状**，让「还没读出来 / 读不出来」借用同一个形状，就等于在同一排四格里
 * 用两种含义画同一种脸——2026-11-06 设备实测正是这一形（进页落在收藏段时 `生词 0 / 错词 / 收藏 0`，
 * 那枚没有数字的格看着既像空册又像设计如此）。摆 0 同样不许：`0` 只在 `Ready` 且空册时才是真条数。
 */
internal fun wordBankSegmentLabel(section: WordBankSection, count: Int?): String = when (section) {
    WordBankSection.Book -> section.label
    else -> if (count == null) "${section.label} $WordBankCountUnavailableMark" else "${section.label} $count"
}

/**
 * 同一格的读屏文本：把视觉记号「—」翻成一句人话。
 *
 * 单独一函数而不是复用 [wordBankSegmentLabel]：`contentDescription` 逐字念出标签，
 * TalkBack 会把 em dash 念成「破折号」或干脆吞掉，两种都不回答问题；而这条判据的既有口径是
 * 「标签构造只有一处调用点」，两函数各写各的分支才不动那条计数。
 */
internal fun wordBankSegmentDescription(section: WordBankSection, count: Int?): String = when (section) {
    WordBankSection.Book -> section.label
    else -> if (count == null) "${section.label}，条数未读到" else "${section.label} $count"
}

/**
 * 词库页壳（spec §3.3）：页头 + 四分段控件 + 一段一面。
 *
 * 本屏**不含数据也不含词行**：词书段接 [WordBookWordsScreen]（筛选 chip + 字母栏 + 词行），
 * 生词 / 错词 / 收藏三段接 [MyWordsScreen] 的同一块复用面。这样批 1 不新建第二套数据通路，
 * 也不把两页的逻辑搬两遍——宿主只需要把已经收好的三份 UiState 与回调按段注入。
 *
 * 两处形状约束（`WordBankScreenContractTest` 按源码钉）：
 *  - 四段各挨**一个**面，段序即 `WordBankSection` 的声明序（词书是第一格：它是默认落点，
 *    也是唯一带筛选与字母栏的一段）；
 *  - 被复用的两屏必须以 `showHeader = false` 挂进来。它们是整屏设计的产物，各自带页头与返回位；
 *    不关掉就是同屏两个返回位、两套标题，用户按哪一枚都可能退错层。
 *
 * 分段控件走共享 [SegmentedControl]（2026-11-09 设计系统收口批），本屏不再自画一排格子：
 * 轨道底色 / 1dp 描边 / 药片形 / 段项 12sp·600 / 48dp 命中区全部归共享件（AC-R-04 的下限由它守），
 * 本屏只交词汇表——四段的标签（[wordBankSegmentLabel]）、读屏文本（[wordBankSegmentDescription]）
 * 与 tag（`WordBankSection.tag`）。选中态有两条语义通道：共享件的 `Selected` 属性，以及本屏
 * 用 `announceSelection = true` 要来的 `stateDescription`（TalkBack 读「已选中 / 未选中」，
 * 与底导逐字一致）——不只靠颜色，颜色单通道在深色档与色觉差异下都不可靠。
 *
 * ## 分段标签的条数（原型 `sMeWords`，`assets/app.js:1292`）
 *
 * 生词 / 错词 / 收藏三格补「段名 N」。条数**只作显示层**：宿主从三段**已有**的 UiState
 * 现算后经 [newCount] / [wrongCount] / [favoriteCount] 传进来，本屏不新开数据通路、不读仓储。
 * 形参默认 `null` = 读不到（Loading / 不可用），此时那格**不带数字**——不许用 0 冒充。
 *
 * ## 页头搜索动作：不搬
 *
 * 原型 `sMeWords` 的 `barTop` 右侧有一枚 search 图标按钮，本轮裁决**不搬**：查词是同一项能力，
 * 全站已有两处入口——今日学习页的常驻查词条（`today_plan_search_entry`）与独立查词页；
 * 在同一能力上对本页再开一个入口，只会让「查词去哪」多一个答案。
 * 本仓也没有「在词库页内打开搜索」的通路可接（词库页四段各自的面都不含搜索），
 * 因此本页页头只留标题 + 返回位，不挂 `action` 槽。若将来要接，必须先有一条既有通路，
 * 并回到 `WordBankScreenContractTest.the header search action stays off this page` 改判据。
 */
@Composable
fun WordBankScreen(
    wordBookName: String,
    section: WordBankSection,
    onSelectSection: (WordBankSection) -> Unit,
    onBack: () -> Unit,
    bookPane: @Composable () -> Unit,
    newPane: @Composable () -> Unit,
    wrongPane: @Composable () -> Unit,
    favoritePane: @Composable () -> Unit,
    // 三格条数（原型 `sMeWords` 的「收藏 12 / 生词本 34 / 易错 8」）：宿主从三段已有的 UiState 现算，
    // 默认 null = 读不到 ⇒ 该格不带数字（不摆 0 冒充）。词书格没有条数来源，故没有对应形参。
    newCount: Int? = null,
    wrongCount: Int? = null,
    favoriteCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val sections = WordBankSection.entries
    // 三格条数按段名取（词书段没有条数来源，恒 null）：一张表把「哪段读哪枚形参」写死一次，
    // 免得四格各写一遍 when 之后漏掉其中一段。
    val counts = sections.associateWith { cell ->
        when (cell) {
            WordBankSection.Book -> null
            WordBankSection.New -> newCount
            WordBankSection.Wrong -> wrongCount
            WordBankSection.Favorite -> favoriteCount
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("word_bank_screen"),
    ) {
        AppScreenHeader(
            title = "词库",
            subtitle = wordBookName,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        SegmentedControl(
            options = sections.map { wordBankSegmentLabel(it, counts[it]) },
            selectedIndex = sections.indexOf(section),
            onSelect = { index -> onSelectSection(sections[index]) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            itemTestTags = sections.map { it.tag },
            itemContentDescriptions = sections.map { wordBankSegmentDescription(it, counts[it]) },
            announceSelection = true,
        )
        when (section) {
            WordBankSection.Book -> bookPane()
            WordBankSection.New -> newPane()
            WordBankSection.Wrong -> wrongPane()
            WordBankSection.Favorite -> favoritePane()
        }
    }
}
