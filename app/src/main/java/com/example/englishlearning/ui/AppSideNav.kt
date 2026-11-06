package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
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
import com.example.englishlearning.ui.components.InlineIconBadge
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.LocalIsDark
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 侧边导航栏（批 B，2026-11-06）：中等/宽窗口替代底部四栏导航。
 *
 * **它是 [AppBottomBar] 的换摆法，不是第二套导航**：同一枚 [AppTab] 枚举、同一份 `AppTab.glyph`
 * 字形表、同一组无障碍三件（testTag / `Role.Tab` / 选中态播报）、同一套选中墨与半透明档位。
 * 这层「同形」不是靠注释维持的——`AppWindowLayoutContractTest` 逐枚锚点比对两份文件的字面形状，
 * 任一侧单独改动（比如给侧栏另起一版 `stateDescription`）当场判红。
 *
 * 不自绘字形、不引 M3 `NavigationRailItem`：
 *  - 自绘会绕开图标台账（批 2 Task 17A 的同一理由）；
 *  - M3 那枚自带 indicator 与 scheme 取色，而选中档前景在本仓是按套钉死的
 *    （浅套 `onMint`、深套 `deep`，见 [AppBottomBar] 的取色注释），
 *    交给 scheme 等于新开一处没人复算过的对比度现场。容器与分隔线走 `appColors()` 主题路径，
 *    不沿用底栏残留的 `MintSurface` 静态浅套读点。
 *
 * 宽度 80dp（Material 侧栏档），每格 `heightIn(min = 48.dp)` 保住 F5-03 的触控下限；
 * 横屏手机根窗口实测 ≈339dp 高（决策 §2），四格 + 间距在此高度内仍放得下，
 * 这一点由批 B 的三档 connected 取证目视确认，不是纸面推算。
 */
@Composable
fun AppSideNav(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row {
        Column(
            modifier = modifier
                .width(80.dp)
                .fillMaxHeight()
                .background(appColors().surface)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppTab.entries.forEach { tab ->
                SideNavSlot(tab = tab, selected = tab == selected, onSelect = onSelect)
            }
        }
        // 与底栏那条 1dp 分隔线同一职责：把导航面与内容面切开，方向转 90 度。
        Box(Modifier.fillMaxHeight().width(1.dp).background(appColors().outlineSoft))
    }
}

@Composable
private fun SideNavSlot(tab: AppTab, selected: Boolean, onSelect: (AppTab) -> Unit) {
    val tint = if (selected) appDomain(AppDomain.AiSpeech).deep else appColors().text2
    val labelInk = if (selected && !LocalIsDark.current) appColors().onMint else tint
    val glyphModifier = if (selected) Modifier else Modifier.alpha(TabGlyphIdleAlpha)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
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
        // 与底栏逐枚同形：选中档带 tint 药片底、未选中档压同一档半透明（`backdrop = selected`）。
        // 这里若换成「选中由整格容器承担」，两栏的选中档就不再是同一件事，配对闸也会当场判红。
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
