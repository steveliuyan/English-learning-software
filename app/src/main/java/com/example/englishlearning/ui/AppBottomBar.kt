package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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
 */
private const val TabGlyphIdleAlpha = 0.5f

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { tab ->
                TabSlot(tab = tab, selected = tab == selected, onSelect = onSelect)
            }
        }
    }
}

/**
 * 四枚 tab 与图标台账定稿字形的对位（学习=合上的书、阅读=报面、AI=星芒、我的=人像）；
 * 「我的」这一格取 `Person` 而不是滑杆：第四格从「设置」升成个人中心（档案 / 统计 / 设置三合一），
 * 滑杆只说得清设置，说不清存档与统计。`Gear` 字形仍在枚举里，设置组内部照旧可用。
 * 台账出处与许可结论见 `components/AppIcons.kt` 文件头，枚名与几何不在这里复制。
 */
private val AppTab.glyph: AppIcon
    get() = when (this) {
        AppTab.LEARNING -> AppIcon.Book
        AppTab.READING -> AppIcon.Newspaper
        AppTab.AI -> AppIcon.Spark
        AppTab.SETTINGS -> AppIcon.Person
    }

@Composable
private fun RowScope.TabSlot(tab: AppTab, selected: Boolean, onSelect: (AppTab) -> Unit) {
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
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .testTag("app_tab_${tab.name.lowercase()}")
            .clickable { onSelect(tab) }
            .semantics {
                contentDescription = tab.contentDescription
                role = Role.Tab
                stateDescription = if (selected) "已选中" else "未选中"
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InlineIconBadge(icon = tab.glyph, domain = AppDomain.AiSpeech, modifier = glyphModifier, backdrop = selected)
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = labelInk,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
