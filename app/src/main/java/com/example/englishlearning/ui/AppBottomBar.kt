package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.InlineIconBadge
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.LocalIsDark
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 未选中页签的字形不透明度（选中档为 1）。
 *
 * 具名而非散写 `0.5f`：这是「两档同一枚插画」这条判据的第二处差别（第一处是药片底有无），
 * 设备侧按它复算淡出（`AppBottomBarTest`），散写会与同文件其它 alpha 混成一团，
 * 也无从在测试里指名道姓地引用。
 *
 * 2026-11-06 批 B 起改 `internal`：侧栏 [AppSideNav] 读同一枚档位，两枚导航的未选中淡出必须是同一个数，
 * 否则「换摆法不换语义」在这条腿上就断了（配套锚见 `AppWindowLayoutContractTest` 的跨文件比对）。
 */
internal const val TabGlyphIdleAlpha = 0.5f

/**
 * 底栏每一格在**顶档**上下留出的余量（QA3 A2.7）。
 *
 * 存在的理由只有一个：顶档（fontScale 3.0）下栏高恰好等于最高那一格的内容高，不留余量时药片上沿就压在
 * 栏沿 hairline 上、文案第二行压在栏底（设备取色 1442 = hairline，图证
 * `.git/qa-scratch/a2-fix/after-fs300-02-aihub.png`），观感与「画到栏外」无法区分。
 */
private val TabSlotVerticalBreathing = 6.dp

/**
 * 顶档降级阈值下的上下余量：`> 2f` 才留，1.0/2.0 档留 0。
 *
 * 为什么不能三档一律给 6dp（2026-11-08 设备实测，`after-fs100-bar.png` / `after-fs200-bar.png`）：
 *  - 1.0 档内容自然高 58.5dp，被 [heightIn] 的 64dp 下限兜住、居中后上下各余 2.75dp；给了 6+6 就变成
 *    70.5dp——**默认档凭空长高 6.5dp**，而这一档是用户已经目视定稿的观感；
 *  - 2.0 档（AC-R-07 钉住的验收档）四枚标签全是单行（设备 dump 四格标签同沿 `y=1687`），
 *    本来就没有折行格，顶对齐与居中完全同形，加余量只会把栏从 68.7dp 顶到 80.7dp。
 * 只有 `> 2f` 才出现「一格两行、其余一行」的形状，余量与顶对齐才既是必要又不伤已定稿的档。
 *
 * 阈值取严格大于 2f 而不是 `>= 2f`：与 `wordCardPinsRevealAction` / `aiFeatureGridColumns` 同一条口径
 * ——2.0 是钉住的验收档，形状不得被顶档整改带走。
 */
internal fun bottomBarSlotVerticalBreathing(fontScale: Float): Dp =
    if (fontScale > 2f) TabSlotVerticalBreathing else 0.dp

/**
 * 四栏底部导航。
 *
 * 字形走批 1 定稿的共享 duotone 图标族（`AppIcon` 冻结路径 + [InlineIconBadge] 渲染），
 * 不再自绘画布：自绘分支既绕开图标台账，也拿不到按主题的填充层不透明度。
 *
 * **两档画同一枚图形（2026-10-31 用户评审 ①）**：选中与未选中都经 [InlineIconBadge] 画同一枚插画位图，
 * 差别只留两处——选中档带 tint 药片底（`backdrop = selected`）、未选中档压 [TabGlyphIdleAlpha] 半透明。
 * 旧形是「选中换成彩色徽标、未选中退回 24×24 线框」，同一排四个格子读成两套图标，正是评审指出的不一致。
 * 字形腿与标签腿分档不同（图形 vs 正文），所以选中标签的墨在浅套另取 `onMint`，不沿用 `deep`（见 `TabSlot`）；
 * 深浅两套的墨色与底色都经 `appColors()` / `appDomain()` 现取，本文件不留静态浅套读点。
 *
 * 2026-11-02 回到四栏（`docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`）：
 * 上一版的第五格「设置」与「我的」并列是重复的——设置本来就属于「我的」。
 * 现在「我的」是个人中心 hub，设置是它里面的子页；底导只画四个真正并列的目的地。
 *
 * 内容区高度**下限**保持 64dp 档（F5-03 Task 11 成果：大字号下可增高不裁字），
 * 再叠 [navigationBarsPadding]，保证三大金刚键/手势条不压住文字。
 */
@Composable
fun AppBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(MintSurface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MintOutline))
        // QA3 A2.7（用户 2026-11-08 拍板「全部都做」）：顶档（fontScale 3.0）下「AI 助学」这枚标签折成两行，
        // 旧形里这一格自然高 134.1dp、其余三格 86.1dp，四格在默认 `CenterVertically` 里各占一段
        // （设备实测 AI 格 `[540,1442][810,1794]`、其余 `[*,1505][*,1731]`），读起来就是「图标浮到别人之上、
        // 文案第二行掉到别人之下」。修法取走查册给的两种里的「格高随字号」，不取「标签截断」——
        // `AI 助学` 截成 `AI …` 是把目的地名字改没，比栏长高 12dp 严重。
        //
        // 两条腿缺一不可，各自钉住一条判据（`AppBottomBarTest.slotsStayInsideTheBar*`）：
        //  ① `IntrinsicSize.Max` + 每格 `fillMaxHeight`（见 `TabSlot`）让四格同一条矩形、同一块可点区；
        //  ② 每格内部改为**顶对齐**并按档留出上下余量：四枚字形因此落在同一条横线上（①只保证矩形等高，
        //     等高矩形里各自居中仍会把两行标签那格的图标顶到别人之上——这正是 2026-11-08 第一版整改漏掉的那半）。
        // 余量只在 `> 2f` 给出，1.0/2.0 档一像素都不动，理由与实测量见 [bottomBarSlotVerticalBreathing]。
        val breathing = bottomBarSlotVerticalBreathing(LocalDensity.current.fontScale)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(IntrinsicSize.Max)
                .heightIn(min = 64.dp)
                .padding(bottom = breathing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { tab ->
                TabSlot(tab = tab, selected = tab == selected, onSelect = onSelect, breathing = breathing)
            }
        }
    }
}

/**
 * 四枚 tab 与图标台账定稿字形的对位（学习=合上的书、阅读=报面、AI=星芒、我的=人像）；
 * 「我的」这一格取 `Person`：它装档案 / 统计 / 全部调整入口，滑杆只说得清设置，说不清进度。
 * 设置不再占一格，齿轮只作为「我的」页头的快捷入口出现（`AppIcon.Gear` 仍在图标台账里）。
 * 台账出处与许可结论见 `components/AppIcons.kt` 文件头，枚名与几何不在这里复制。
 *
 * 2026-11-06 批 B 起改 `internal` 并由 [AppSideNav] 共读：目的地→字形是**导航族**的表，不是底栏的私产；
 * 表本体仍留在本文件（`AppBottomBarContractTest` 按「四枚 glyph 各恰一处」扫这份文件，搬家会撞那条锚）。
 */
internal val AppTab.glyph: AppIcon
    get() = when (this) {
        AppTab.LEARNING -> AppIcon.Book
        AppTab.READING -> AppIcon.Newspaper
        AppTab.AI -> AppIcon.Spark
        AppTab.PROFILE -> AppIcon.Person
    }

@Composable
private fun RowScope.TabSlot(
    tab: AppTab,
    selected: Boolean,
    onSelect: (AppTab) -> Unit,
    breathing: Dp,
) {
    val tint = if (selected) appDomain(AppDomain.AiSpeech).deep else appColors().text2
    // 选中态是三条腿，墨不是一枚通吃：
    //  - 字形腿走 `InlineIconBadge`（tint 药片底 + 域 `deep` 描边），非文本图形档；
    //  - 标签文字腿铺在底导容器的 `surface` 上，是**正文档**——浅套那腿不能沿用 `deep`，
    //    按 R-14.4 的 AiSpeech 例外取 `appColors().onMint`（与 `SpeakerButton` 的浅薄荷底前景同族）；
    //    深套 `deep` 与 `base` 合一、压深底本就是亮墨，维持原值。
    // 未选中档两套同用 `text2`。各腿比值一律由 `AppBottomBarContrastTest` 按真实底当场复算，
    // 这里不抄数字（抄进注释就会随 token 取值漂移）。
    val labelInk = if (selected && !LocalIsDark.current) appColors().onMint else tint
    // 图形腿两档共用同一枚插画：只看「带不带药片底」（徽标内由 backdrop 收口）与「压不压半透明」。
    // 半透明用 if 直接给 Modifier，而不是 `.alpha(if (...) 1f else ...)`：选中档不额外套一层无用的图层。
    val glyphModifier = if (selected) Modifier else Modifier.alpha(TabGlyphIdleAlpha)
    // 顶对齐之后，字形与栏沿之间必须自己留出余量，否则两行标签那一格会把药片顶到栏沿 hairline 上，
    // 看着像画到栏外（2026-11-08 设备图证 `after-fs300-02-aihub.png` 取色：药片上沿 1442 = hairline）。
    // 药片自己带 tag：`IllustratedIconTag` 落在里面那枚 26dp 位图上，量不到 30dp 药片的真实外沿，
    // 而「浮出栏沿」判的正是外沿（`AppBottomBarTest` 的第三条判据按药片上沿比四格）。
    // 整枚 modifier 提到局部变量，是为了让下面的调用回到一行：`AppBottomBarContractTest` 的实参级锚
    // `InlineIconBadge(icon = tab.glyph, domain = AppDomain.AiSpeech` 按「剥注释后整行子串」计数，
    // 参数换行排版会让这枚锚判 0（2026-11-08 踩实过一次）。
    val glyphBadgeModifier =
        glyphModifier.padding(top = breathing).testTag("app_tab_glyph_${tab.name.lowercase()}")
    Column(
        modifier = Modifier
            .weight(1f)
            // QA3 A2.7：整格拉满行高（行高由最高的那一格定，见 `AppBottomBar` 里的 `IntrinsicSize.Max`），
            // 四格因此等高、可点区也等宽等高；格内改**顶对齐**（下面的 `spacedBy(..., Alignment.Top)`），
            // 让四枚字形落在同一条横线上——居中会把两行标签那一格的图标顶到别人之上（第一版整改漏掉的那半）。
            .fillMaxHeight()
            .heightIn(min = 64.dp)
            .testTag("app_tab_${tab.name.lowercase()}")
            .clickable { onSelect(tab) }
            .semantics {
                contentDescription = tab.contentDescription
                role = Role.Tab
                stateDescription = if (selected) "已选中" else "未选中"
            },
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InlineIconBadge(icon = tab.glyph, domain = AppDomain.AiSpeech, modifier = glyphBadgeModifier, backdrop = selected)
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = labelInk,
        )
    }
}
