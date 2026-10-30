package com.example.englishlearning.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 选择行（原型 `assets/app.css` `.pick` / `.pick-mark` / `.pick-radio`）。
 *
 * 行：上下 12dp、无横向内距（`padding: var(--s3) 0`，横向归宿主卡片）、标记与文字间隙 12dp、
 * 最小高 48dp（F5-03 下限；原型自然高 22 + 24 = 46，抬 2dp）。顶部 1dp `line-soft` 分隔线由宿主
 * 用 `hairlineTop` 决定——原型 `.pick` 自带 `border-top`，但组首行要 `:first-child { border-top: 0 }`，
 * 组件不替调用方决定「这是不是组首行」。
 *
 * 标记：22dp，复选圆角 7dp、单选 50%，1.5dp 描边；未选 `surface` 底 + `line` 边，选中 `brand-deep`
 * 底 + `brand-deep` 边 + 白色勾（原型 `color: #fff`，勾与底一起出现）。
 *
 * 尾部值 [trailing] 是原型 `.row-value` 那一档（`app.css:544`：13sp / `--ink-3`，靠右、不参与换行），
 * 只给「这一行还有一句要说的」的调用点（选词书给它念「使用中」）。
 *
 * ## 手势：两条形状，不是「放宽成裸 clickable」
 *
 * 默认走 `selectable`：`radio = true` → `Role.RadioButton`，否则 `Role.Checkbox`（单选组仍由宿主挂
 * `selectableGroup()`，与 `SegmentedControl` 同口径）。
 *
 * 一旦给了 [onSecondaryAction]（同一行还要挂第二动作，例如长按删除），`selectable` 就装不下了——
 * 它只发一条 `OnClick`。这时改走 `combinedClickable(role = …, onClickLabel = …, onLongClickLabel = …)`，
 * 并把 `selectable` 本来会自动写进语义的 `Selected` 与角色**自己补回来**（`selected = checked` +
 * 同一个 `role`）。所以「不再是 selectable」不等于「选中语义没了」：`Role` / `Selected` /
 * `stateDescription` 三样在两条形状上都在，TalkBack 念得出的东西一件没少。
 * [SharedComponentContractTest] 因此同时钉两条手势的字面形状，去掉任何一条都会红。
 *
 * 第二动作在长按之外还登记成 `customActions`（F5-03：读屏用户做不出长按手势，动作声明就是第二条路）。
 *
 * 手势、`contentDescription`、`stateDescription`、`customActions`、[trailingTag] 之外的**语义必须落在
 * 同一个 modifier 链上**（终审 A 的 I-A2：父节点属性不下发给子节点）。分隔线是宿主给的事，所以顶部的
 * `hairlineTop` 由调用方挂在自己那条链上，组件不越俎代庖。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    radio: Boolean = false,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: String? = null,
    trailingTag: String? = null,
    contentDescription: String? = null,
    onClickLabel: String? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val colors = appColors()
    val markShape: Shape = if (radio) CircleShape else RoundedCornerShape(7.dp)
    val markEdge = if (checked) colors.brandDeep else colors.outline
    val role = if (radio) Role.RadioButton else Role.Checkbox
    val toggle = { onCheckedChange(!checked) }
    // 先在函数体作用域里把形参接出来：`semantics { }` 块里 `contentDescription` 是接收者的属性名，
    // 与形参同名，不接出来就要靠 `this.` 与隐式接收者的优先级赌一把（Kotlin 在这里的解析并不直观）。
    val description = contentDescription
    // 第二动作存在 ⇒ 一条链上要有两条手势，只能 combinedClickable；它不发 Selected，下面自己补。
    val gesture: Modifier = if (onSecondaryAction != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClickLabel = secondaryActionLabel,
            onLongClick = onSecondaryAction,
            onClick = toggle,
        )
    } else {
        Modifier.selectable(
            selected = checked,
            enabled = enabled,
            role = role,
            onClick = toggle,
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(gesture)
            .semantics {
                selected = checked
                stateDescription = if (checked) "已选" else "未选"
                if (description != null) {
                    this.contentDescription = description
                }
                if (onSecondaryAction != null && secondaryActionLabel != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(
                            label = secondaryActionLabel,
                            // Boolean 返回值语义是「动作是否已执行」：确认框弹出来了就算执行。
                            action = {
                                onSecondaryAction()
                                true
                            },
                        ),
                    )
                }
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(markShape)
                .background(if (checked) colors.brandDeep else colors.surface)
                .border(1.5.dp, markEdge, markShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                AppGlyph(
                    icon = AppIcon.Check,
                    tint = Color.White,
                    size = 14.dp,
                    layer = AppGlyphLayer.StrokeOnly,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = AppType.Body,
                color = if (enabled) colors.text1 else colors.text3,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, style = AppType.Caption, color = colors.text3)
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = AppType.Meta,
                color = colors.text3,
                maxLines = 1,
                modifier = if (trailingTag == null) Modifier else Modifier.testTag(trailingTag),
            )
        }
    }
}
