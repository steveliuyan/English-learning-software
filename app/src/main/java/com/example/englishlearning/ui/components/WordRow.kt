package com.example.englishlearning.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 批 1 Task 12：词行（spec §3「一行 lemma + 释义 + 状态/时间 + 右侧动作」，现状 7 份私有实现、0 复用）。
 *
 * 收的是 spec §3 点名的那批裸组合（`WordBookWordsScreen`/`LearningRecordsScreen`/
 * `GlobalVocabularySearchScreen` 两变体/`VocabularyScreen`/`WrongWordsScreen`/`FavoritesScreen`）：
 * 它们没有共同的函数名，形状是「同枚私有 Row 组合里并排两枚 Text（lemma + 释义）」，
 * 故 `SharedComponentUniquenessRatchetTest` 按**私有 Row 函数体窗口**取样，不按函数名搜。
 * 批 2/批 3 逐屏接线时删私有份并递减该棘轮 seed。
 *
 * 墨色全部来自纯表 [wordRowInks]（无 `@Composable`），所以每槽取哪枚 token 能在 JVM 侧被钉死：
 * connected 读不到颜色（Task 9 实测口径），本组件只读这张表、不在组件里凑色。
 *
 * [domain] 形参**不参与取色**（`WordRowSpeakerContractTest.word_row_never_reads_a_domain_accent`
 * 把「本文件 0 处 `appDomain(`」钉住）：词行的四枚墨是中性档（lemma `text1`、gloss/status/meta `text2`），
 * 域色只出现在 `leading` 槽里由调用方组合的 [InlineIconBadge]（那枚徽标自己按 domain 取色）。
 * 形参留在签名里（计划 Task 12 Interfaces 逐字口径）是为了让批 3 逐屏接线时「行的域归属」与
 * 「前导徽标的域归属」在调用点只写一遍、不会各挑一枚；若哪天确证某屏需要**行级**域色，
 * 处置是改 [wordRowInks] 纯表并同步补对比契约（按 R-10.1b 的口径先算后改），
 * 不是在组件里悄悄 `appDomain(...)` 凑一枚强调色——那条路径已被上面那枚断言挡死。
 */

/**
 * 词行四枚槽位墨：lemma、释义、状态、时间/出处。
 *
 * 四字段而不是「一个 text2 复用三次」：与 [EntryRowInk] / [NoticeInk] / [NotConfiguredCardInk] 同口径，
 * 具名字段才能让「哪槽取哪枚 token」在契约里被逐槽点名断言（塌成同一枚灰绿会当场红）。
 */
data class WordRowInk(
    val lemma: Color,
    val gloss: Color,
    val status: Color,
    val meta: Color,
)

/**
 * 两套主题 → 四枚槽位墨色的纯函数（[set] 就是浅套或深套那两套之一）。
 *
 * 槽位分配（计划 Task 12 Step 2 逐字）：lemma [AppType.Body] + `text1`（这一行的主信息）、
 * gloss/status/meta [AppType.Footnote] + `text2`（次要档，与 [EntryRow] 的副标题/尾部值同槽位同 token）。
 * 四槽都压本套 `surface` 达正文 AA 4.5:1，由 `WordRowSpeakerContractTest` 当场按 WCAG 相对亮度复算
 * （比值只写进断言消息，注释不抄数——抄了会随 token 取值漂移）。
 */
fun wordRowInks(set: AppColorSet): WordRowInk = WordRowInk(
    lemma = set.text1,
    gloss = set.text2,
    status = set.text2,
    meta = set.text2,
)

/**
 * 词行。
 *
 * 几何：`Row` 间隙 12dp（与 [EntryRow] 同一档横向节奏）、`heightIn(min = 48.dp)`（spec §2.3 触控下限；
 * 词行在列表里就是触控目标，不给它钉下限等于把 F5-03 的账留给批 3 逐屏再还一次）、
 * `padding(vertical = 8.dp)`（内容上下留白，与入口行同档）。行本体不铺底色、不描边：
 * 卡面与描边归调用方的容器（现网 6 份私有实现都是「Card 里塞一行」，批 3 接线时保留各屏容器）。
 *
 * **修饰符顺序就是契约**（Task 10/11 的实测教训，别按稿面顺序「顺手调回去」）：可点面由两条共同交付——
 * ① 链上存在 `heightIn(min = 48.dp)`；② `clickable` 排在任何 `padding` **之前**。
 * 这里 `clickable` 经 [touchModifier] 落在 `fillMaxWidth()` 之后、`heightIn`/`padding` 之前，
 * 所以可点面是整盒；把 `padding` 写到 `clickable` 前面就会退化成内容盒
 * （`EntryRow` 真机量到 40dp 的成因，现场记在 `EntryRow.kt:70-75`）。
 * `heightIn` 与 `clickable` 的先后**不影响**可点面尺寸（`clickable` 不参与测量），
 * 两处先例（`DialogButton` heightIn 在前、`NotConfiguredCard` 出口 heightIn 在后）connected 都实测 ≥48dp。
 *
 * [onClick] 可空：为空即整行不是触控目标，此时不挂 `clickable`、也就没有 `Role.Button`
 * （计划 Step 2 逐字：`role` 按 `onClick` 是否为空取 `Role.Button`/无）。
 * `testTag` 由调用方经 [modifier] 传入——[modifier] 是链的第一段，与 `clickable` 同在**同一枚节点**上，
 * 所以真机上 `onNodeWithTag(...).performClick()` 打得中（标签在父、click 在子就打空，见 `NotConfiguredCard.kt`）。
 *
 * 排印按六档 token 落，不 `copy(fontWeight = …)`、不引新档（spec §2.2 六档锁）：
 * lemma [AppType.Body]（15sp/400，稿面词头是加粗的，这里按六档锁保持档内字重，
 * 层级靠 `text1` 与 `text2` 的墨色落差承担）、gloss/status/meta [AppType.Footnote]（13sp/400）。
 *
 * 省略号口径与 [EntryRow] 同族、与 [InlineNotice] 相反：词行四槽都是**提示性内容**
 * （行本身是入口，点进详情才读全量），故 lemma/status/meta 各 `maxLines = 1`、
 * gloss `maxLines = 2`（释义比 lemma 长，两行是稿面词表行的实际高度预算），一律 [TextOverflow.Ellipsis]
 * ——不给 `Clip`（裁掉字形不是省略）。fontScale 2.0 下靠省略号退化，不靠裁切。
 *
 * [leading] 是前导槽（调用方组合 [InlineIconBadge]、勾选框或序号），
 * [actions] 是尾部动作槽（`RowScope` 收件，调用方可直接写 `actions = { SpeakerButton(...) }` 一类），
 * 两槽都可空；槽内的触控目标由调用方自己给 48dp 下限（本行只保证自己这一枚可点面达标）。
 * [status] 与 [meta] 右对齐成一小列（稿面词表行的「状态 / 时间」两行右栏），两者都为空时整列不占位。
 */
@Composable
fun WordRow(
    lemma: String,
    gloss: String?,
    modifier: Modifier = Modifier,
    status: String? = null,
    meta: String? = null,
    domain: AppDomain = AppDomain.Library,
    leading: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
) {
    // 四枚墨一律从纯表取（源码契约钉「本文件恰一处 wordRowInks(appColors())」）。
    val inks = wordRowInks(appColors())
    val touchModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    val leadingSlot = leading
    val actionsSlot = actions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(touchModifier)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingSlot != null) leadingSlot()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lemma,
                style = AppType.Body,
                color = inks.lemma,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (gloss != null) {
                Text(
                    text = gloss,
                    style = AppType.Footnote,
                    color = inks.gloss,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (status != null || meta != null) {
            Column(horizontalAlignment = Alignment.End) {
                if (status != null) {
                    Text(
                        text = status,
                        style = AppType.Footnote,
                        color = inks.status,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta != null) {
                    Text(
                        text = meta,
                        style = AppType.Footnote,
                        color = inks.meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (actionsSlot != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actionsSlot,
            )
        }
    }
}
