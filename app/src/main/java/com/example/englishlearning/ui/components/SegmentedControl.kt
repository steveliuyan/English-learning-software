package com.example.englishlearning.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppMotion
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 分段控件（原型 `assets/app.css` `.seg` / `.seg-solid`）。
 *
 * 几何逐条对位原型：轨道内距 3dp、段项间隙 2dp、药片形、轨道底 `surface-2` + 1dp `line` 描边；
 * 选中档 `surface` 底 + `ink` 墨（`.seg-item[aria-pressed='true']`），`solid = true` 时改 `strong`
 * 底 + `on-strong` 墨（`.seg-solid`）。未选中墨 `ink-2`，段项 12sp/600。
 *
 * **一处登记偏离**：原型段项 `min-height: 34px`，低于 F5-03 的 48dp 触控下限。spec §2.3 明令
 * 无障碍下限优先，故段项实际高 48dp（`heightIn(min = 48.dp)`）；内距仍是 3/2，视觉厚度只多出
 * 内容盒的垂直余量。该偏离写在批 0 报告里，不在屏侧逐处重议。
 *
 * 语义用 `selectable`（TalkBack 播报「已选择」）而不是裸 `clickable`；组容器挂 `selectableGroup()`，
 * 让读屏把这一排当成一枚单选组。选中底色走 160ms 入场缓动（原型 `--dur-fast var(--ease-out)`）。
 *
 * **两枚可选形参（2026-11-09 设计系统收口批）**，都不改既有调用点的形状：
 *  - [role]：段项的角色，默认 [Role.Tab]（「切换内容视图」语义）。同一件控件也被当成**设置项**用
 *    （「我的」页末的三态主题），那时每个段项该念「单选按钮，已选中」——调用点传 [Role.RadioButton]，
 *    读屏口径才与「这是一组互斥选项」对齐（判据 `ProfileScreenTest`）。
 *  - [itemTestTags]：逐段 testTag，默认 `null`（不加）。tag 是**调用点的词汇表**（哪一档叫什么由页面
 *    定义），共享件不替任何页面命名；「我的」页按 `profile_theme_${storageKey}` 传三枚，
 *    connected 侧据此定位与断选中态。
 *  - [itemContentDescriptions]：逐段的读屏文本，默认 `null`（不加）。有值时每段补一枚
 *    `contentDescription`，把视觉记号翻成人话——词库页的「错词 —」要念成「错词，条数未读到」，
 *    em dash 不能交给 TalkBack 去猜。同一件控件被当成分段标签带计数时（`生词 3`）也走这条通道，
 *    文本由调用点构造，共享件不拼标签。
 *  - [announceSelection]：默认 `false`；置 `true` 时每段语义块补
 *    `stateDescription = if (selected) "已选中" else "未选中"`。`selectable` 自带的 `Selected`
 *    属性在 TalkBack 上念作「已选择」，与底导 / 侧栏 / 学习记录那些 `Role.Tab` 行的「已选中」
 *    同义而不同词；词库页要与底导逐字一致，故显式补这一句（判据 `WordBankScreenTest`）。
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    role: Role = Role.Tab,
    itemTestTags: List<String>? = null,
    itemContentDescriptions: List<String>? = null,
    announceSelection: Boolean = false,
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShape.Pill)
            .background(colors.surface2)
            .border(1.dp, colors.outline, AppShape.Pill)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val fill = if (selected) {
                if (solid) colors.strongSurface else colors.surface
            } else {
                colors.surface2.copy(alpha = 0f)
            }
            val fillAnimated by animateColorAsState(
                targetValue = fill,
                animationSpec = tween(durationMillis = AppMotion.Fast, easing = AppMotion.EasingOut),
                label = "segmented_fill",
            )
            val ink = when {
                !selected -> colors.text2
                solid -> colors.onStrong
                else -> colors.text1
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(AppShape.Pill)
                    .background(fillAnimated)
                    .selectable(
                        selected = selected,
                        role = role,
                        onClick = { onSelect(index) },
                    )
                    .then(
                        if (itemTestTags == null) {
                            Modifier
                        } else {
                            Modifier.testTag(itemTestTags[index])
                        },
                    )
                    .then(
                        if (itemContentDescriptions == null && !announceSelection) {
                            Modifier
                        } else {
                            Modifier.semantics {
                                if (itemContentDescriptions != null) {
                                    contentDescription = itemContentDescriptions[index]
                                }
                                if (announceSelection) {
                                    stateDescription = if (selected) "已选中" else "未选中"
                                }
                            }
                        },
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = AppType.Caption,
                    color = ink,
                    fontWeight = FontWeight(600),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
