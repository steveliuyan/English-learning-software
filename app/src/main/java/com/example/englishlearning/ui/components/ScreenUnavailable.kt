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
 * 顶 30 / 左右 18 / 底 26，元素间隙取稿的 12（图标→文案）与 14（文案→动作）。
 * 控制者裁决 R-9.3：底色是 surface 而非 tint（tint 底属于 banner 与选中卡那一族）；
 * 圆角走 `MaterialTheme.shapes.medium`（Task 4 已把该 M3 槽校准到 spec §2.3 的卡片档，
 * 旧的 `AppShape.Card` 档留给逐屏迁移批，新组件不读它）。
 *
 * 图标用 [AppGlyph] 装饰层呈现（裁决 R-9.3 已定：不用徽标族、不内嵌插画资源），不独立承载语义；
 * 原因文案走 `AppType.Footnote` + `text2`（六档对稿 13px/400 的正文档）。
 * 动作只有**一个**：复用批 1 唯一按钮 [PillButton] 的 Secondary 档并打
 * `screen_unavailable_action` 标签——再造「不可用态专用按钮」会撞上组件份数棘轮（计划 Task 12），
 * 稿面按钮族与现有按钮档位的差异已由计划登记为批 2 的裁决项，本组件不预判。
 */
@Composable
fun ScreenUnavailable(
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
        Text(
            text = reason,
            style = AppType.Footnote,
            color = colors.text2,
            modifier = Modifier.padding(top = 12.dp),
        )
        PillButton(
            text = actionLabel,
            onClick = onAction,
            style = PillStyle.Secondary,
            testTag = "screen_unavailable_action",
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}
