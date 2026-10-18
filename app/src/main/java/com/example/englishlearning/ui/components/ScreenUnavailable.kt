package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 整屏不可用态（查词/文章/AI 功能在缺配置、缺数据时的兜底屏中件），几何按视觉稿 §骨架/空态：
 * `.card` 嵌 `.empty` 的形态——surface 底 + 1dp outlineSoft 描边的卡片，内缘取稿的
 * 顶 30 / 左右 18 / 底 26。间隙按稿的三层各归其位（修复轮前曾把 12dp 错配给唯一一层文案）：
 * 12dp 是稿 `.empty .t` 的 `margin-top`（插画/图标→标题），4dp 是稿 `.empty .d` 自身的
 * `margin-top`（标题→说明），动作前的 14dp 是 `--inner-gap`。
 * 控制者裁决 R-9.3：底色是 surface 而非 tint（tint 底属于 banner 与选中卡那一族）；
 * 圆角走 `MaterialTheme.shapes.medium`（Task 4 已把该 M3 槽校准到 spec §2.3 的卡片档，
 * 旧的 `AppShape.Card` 档留给逐屏迁移批，新组件不读它）。
 *
 * 图标用 [AppGlyph] 装饰层呈现（裁决 R-9.3 已定：不用徽标族、不内嵌插画资源），不独立承载语义。
 * 文案按稿 `.card .empty`（`docs/design/2026-10-17-ui-redesign-mockups.html:610-619`）是两层：
 * [title]（`.t`）与 [reason]（`.d`）。裁决 R-9.4 选「补可选 `title` 层」，不选「各屏在卡外自排标题」
 * ——后者的 12/4dp 间隙会被每屏各抄一遍。[title] 为 `null` 时保持单文案卡形态，12dp 仍是图标→文案。
 * 排印走六档 token：[title] 取 `AppType.Body`（15sp，与稿的 15px 同字号档）+ `text1`，稿的字重 700
 * 与 `Body` 的 400 之差按 **token 近似**登记在这里（spec §2.2 六档锁：不引新档、不
 * `copy(fontWeight = …)`）；[reason] 取 `AppType.Footnote` + `text2`（稿 13px/400 的正文档）。
 * 标题层不加行数上限：spec §3 的「`fontScale` 2.0 下不裁文字」硬于稿 panel 的「只给一行」散文。
 *
 * 动作只有**一个**：复用批 1 唯一按钮 [PillButton] 的 Secondary 档并打
 * `screen_unavailable_action` 标签——再造「不可用态专用按钮」会撞上组件份数棘轮（计划 Task 12）。
 * 这枚动作的具体后果要说全（评审 I-4 放行前置①）：`PillButton` 的 Secondary 档容器/文字/描边读的是
 * **静态浅套值**（`AppPalette.Surface`、`accent.deep` = `#188F76`、`AppPalette.Separator`），
 * **不随主题翻色** → 深色模式会在 `#17201C` 的卡底上浮出一枚白药片（`#188F76` 压白 4.02:1、
 * 压深套 surface 4.15:1）。这不是纯视觉档位差异，是计划 Task 8 台账里「组件层静态读点」那一族债务，
 * 修点归批 2 Task 14 的按钮族裁决（本轮不动 `PillButton`），本组件只登记不预判。
 */
@Composable
fun ScreenUnavailable(
    title: String? = null,
    reason: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: AppIcon = AppIcon.Info,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surface)
            .border(1.dp, colors.outlineSoft, MaterialTheme.shapes.medium)
            .padding(start = 18.dp, top = 30.dp, end = 18.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppGlyph(icon, colors.text2, 24.dp)
        if (title != null) {
            Text(
                text = title,
                style = AppType.Body,
                color = colors.text1,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = reason,
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                text = reason,
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        PillButton(
            text = actionLabel,
            onClick = onAction,
            style = PillStyle.Secondary,
            testTag = "screen_unavailable_action",
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}
