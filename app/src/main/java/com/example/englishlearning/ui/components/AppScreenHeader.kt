package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 批 1 统一页头，几何按视觉稿 §页头（`.appheader`）：`Row`、元素间隙 10dp、`min-height 48dp`、
 * 行尾 `action` 槽（稿 `.tr` 靠 margin-left auto 推到行尾，这里由标题列 `weight` 达成同样效果）。
 *
 * 控制者裁决 R-9.1：返回位是「稿子 40dp 视觉容器 + 真 48dp 可点区」两层——外层 48dp Box 承载
 * clickable 与 contentDescription "返回"（可点区语义归它，F5-03 的大触控目标由这层满足），
 * 内层 40dp 容器按稿上色（surface 底 + 1dp outlineSoft 描边 + Icon 圆角档），字形取同族入口容器的
 * 22dp 口径且本身装饰性不另带语义。
 *
 * 裁决 R-9.2：`onBack == null` 时整段不渲染、不留占位——稿子里不带返回键的首屏标题贴左，
 * 空占位是稿中不存在的排印。
 *
 * 排印取六档 token 而非稿面数值直译：标题 `AppType.Title`（稿 17px/700，六档里 Title 为 17sp SemiBold，
 * 字重差按 token 近似登记）；副标题 `AppType.Footnote`（稿 12px/400，六档无 12sp Regular 档，
 * 引新档会破 spec §2.2 的六档锁，取同族 `.page-sub` 的 13sp/400，同样登记为 token 近似）。
 * 两档都必须显式传 `color`（`AppType` 各档自带浅套静态墨，直落深色模式会黑压黑）。
 * 标题 `maxLines = 2` 且不省略号：页标题裁掉即丢信息，超长靠两行换行吸收。
 *
 * 组件内不写底部外边距：稿子的 `margin-bottom:6px` 归各屏的列节奏（`verticalArrangement`），
 * 写进组件会与列间隙叠成幽灵间距（F5-03 移交账点过名）。
 */
@Composable
fun AppScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .testTag("app_screen_header")
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppShape.Icon)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "返回" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(AppShape.Icon)
                        .background(colors.surface)
                        .border(1.dp, colors.outlineSoft, AppShape.Icon),
                    contentAlignment = Alignment.Center,
                ) {
                    AppGlyph(AppIcon.Back, colors.text1, 22.dp)
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppType.Title,
                color = colors.text1,
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}
