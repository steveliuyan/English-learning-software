package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/** 词库四分段（spec §3.3）：词书 / 生词 / 错词 / 收藏。 */
enum class WordBankSection(val label: String, val tag: String) {
    Book("词书", "word_bank_segment_book"),
    New("生词", "word_bank_segment_new"),
    Wrong("错词", "word_bank_segment_wrong"),
    Favorite("收藏", "word_bank_segment_favorite"),
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
 * 分段控件不是 M3 `TabRow`：三档容器的语言里它属于「一层明度差的底」——`tint2` 凹槽 +
 * 选中格 `surface` 凸起，与 `SoftCard` 同一套明度语法，且四格各自 ≥48dp 命中区（AC-R-04）。
 * 选中态同时给 `stateDescription`（TalkBack 读「已选中」），不只靠颜色——颜色单通道在
 * 深色档与色觉差异下都不可靠。
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
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.tint2)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            WordBankSection.entries.forEach { cell ->
                val selected = cell == section
                Text(
                    text = cell.label,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.surface else colors.tint2)
                        .clickable(role = Role.Tab) { onSelectSection(cell) }
                        .testTag(cell.tag)
                        .semantics {
                            contentDescription = cell.label
                            role = Role.Tab
                            stateDescription = if (selected) "已选中" else "未选中"
                        }
                        .padding(vertical = 13.dp),
                    style = AppType.Body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (selected) colors.text1 else colors.text2,
                    textAlign = TextAlign.Center,
                )
            }
        }
        when (section) {
            WordBankSection.Book -> bookPane()
            WordBankSection.New -> newPane()
            WordBankSection.Wrong -> wrongPane()
            WordBankSection.Favorite -> favoritePane()
        }
    }
}
