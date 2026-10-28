package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
 * `Row` 显式 `fillMaxWidth()`：有界宽宿主下 `weight(1f)` 本来就会撑满，但无界宽宿主下标题列会塌宽
 * （`CheckInScreenTest` 钉过这个反例形态）。组件级宿主造不出无界宽宿主，所以不为此加用例，
 * 屏侧回归归批 2。
 *
 * 控制者裁决 R-9.1：返回位是「稿子 40dp 视觉容器 + 真 48dp 可点区」两层——外层 48dp Box 承载
 * clickable 与 contentDescription "返回"（可点区语义归它，F5-03 的大触控目标由这层满足），
 * 内层 40dp 容器按稿上色（surface 底 + 1dp outlineSoft 描边 + Icon 圆角档），字形取稿 `.back` 容器
 * （`mockups.html:119-122`）+ 同族 `.ic svg` 的 22dp 度量；22dp 是 `mockups.html:528` 定稿散文的
 * **直写值**（「40dp 容器 + 22dp 字形，触控区仍 ≥48dp（外扩 padding）」），不是「稿未单独给尺寸、
 * 仅沿 `.ic` 口径」的推定。`AppIcon.Back` 属三层结构豁免表，这里显式传 `AppGlyphLayer.StrokeOnly`
 * （只靠 `AppIcons` 里 `Back` 的 `fillPaths = emptyList()` 兜住属「靠数据凑对」，同族写法见
 * `InlineIconBadge`）。豁免表禁令的执行点是 `EntryIconBadge` 开头的 `require`，管的是**域渐变入口容器**
 * 那一族；页头 `.back` 是稿子里另一种容器（surface 底 + 1dp outlineSoft + `text1` 描边字形），
 * 不经过该 `require`，稿也支持它这样存在——批 2 不得照「豁免枚不得进入 ≥40dp 入口容器」把这里
 * 「修」成 `EntryIconBadge`，那会在运行时抛 `require`。
 *
 * 裁决 R-9.2：`onBack == null` 时整段不渲染、不留占位——稿子里不带返回键的首屏标题贴左，
 * 空占位是稿中不存在的排印。
 *
 * 排印取六档 token 而非稿面数值直译：标题 `AppType.Title`（稿 17px/700，六档里 Title 为 17sp SemiBold，
 * 字重差按 token 近似登记）；副标题 `AppType.Footnote`（稿 12px/400，六档无 12sp Regular 档，
 * 引新档会破 spec §2.2 的六档锁，取同族 `.page-sub` 的 13sp/400，同样登记为 token 近似）。
 * 两档都必须显式传 `color`（`AppType` 各档自带浅套静态墨，直落深色模式会黑压黑）。
 * 标题 `maxLines = 2` 且不省略号（`TextOverflow.Clip`）：页标题裁掉即丢信息，超长靠两行换行吸收。
 * 两行这个上限的依据：spec 把组件的大字号验收钉在 `fontScale` **2.0**（§3 口径与 AC-R-07，
 * `docs/superpowers/specs/2026-10-17-ui-redesign-design.md:114`、`:251`），评审的 3f 探针实测
 * `lineCount=2 overflow=false`，说明两行档在钉住的验收口径内仍有富余，故本轮**不改** `maxLines`、
 * **不改** `Clip`。代价与触发条件一并写清：① 超过两行容量时 `Clip` 静默丢字且零信号，当前唯一防线是
 * 文案纪律（按 411dp 宽实测容量约 2f 档 20 字 / 3f 档 17 字）；② 若 AC-R-07 抬到 3.0 或产品要支持
 * 英文长标题，本组件必须改 `maxLines = 3` 或引 `Ellipsis` 并同步改稿——届时有
 * `AppScreenHeaderTest` 里 `!hasVisualOverflow` 那条断言自动把这件事顶出来（批 2 触发条件）。
 *
 * 组件内不写底部外边距：稿子的 `margin-bottom:6px` 归各屏的列节奏（`verticalArrangement`），
 * 写进组件会与列间隙叠成幽灵间距（F5-03 移交账点过名）。
 *
 * ## 两档字级（2026-10-30 版式重做）
 *
 * `hero = false`（默认，二级页）标题取 `AppType.Title` 17sp——返回位 + 17sp 标题是「进到里面去了」的口径，
 * 与全站 `.page-sub` 那套紧凑页头一致，也是 `AppScreenHeaderTest` 大字号两条判据的现行参照。
 *
 * `hero = true`（一级页 / tab 根页）标题取 `AppType.Headline` 28sp，`eyebrow` 是标题上沿的一行
 * `Label` 小字（日期 / 计数这类「此刻在哪」的定位信息），`badge` 是标题右侧一枚 40dp 入口容器
 * （`EntryIconBadge`，域色只出现在那枚位图里）。一级页没有返回位，靠「大标题 + 徽标 + 眉标」把
 * 层级讲清楚；二级页不传 `hero`，几何一字不动，因此大字号判据不受影响。
 *
 * `badge` 与 `action` 都在尾部，且**不加 clickable**：本组件只摆内容，可点与否交给调用方
 * （一级页的首页用例按「可点节点闭合集合」逐枚数可点目标，页头擅自包一层 clickable 会当场数多）。
 */
@Composable
fun AppScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
    hero: Boolean = false,
    eyebrow: String? = null,
    badge: @Composable (() -> Unit)? = null,
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .testTag("app_screen_header")
            .fillMaxWidth()
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
                    AppGlyph(AppIcon.Back, colors.text1, 22.dp, layer = AppGlyphLayer.StrokeOnly)
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow,
                    style = AppType.Label,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(
                text = title,
                style = if (hero) AppType.Headline else AppType.Title,
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
        if (badge != null) {
            badge()
        }
        if (action != null) {
            action()
        }
    }
}
