package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.EmptyState
import com.example.englishlearning.ui.components.Sticker
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.appColors

/**
 * 页面不可用（原型 `sUnavailable`，`app.js:1511-1523`）。用户 2026-10-29 拍板授权的五枚新屏之一。
 *
 * 承载点是**未知 AI 功能 key**：`selectedFeatureKey` 是 `rememberSaveable` 的字符串，枚举改名 /
 * 删项后旧状态仍可能带着一个认不出的 key 恢复回来。旧宿主此时落到 `AiFeatureScreen(feature = …)`
 * 之外没有任何分支，整个覆盖层会直接消失（用户以为返回了，实际只是叠层没了）。本屏是那枚兜底：
 * 说明当前版本没开放，并给两条**真出口**（回学习 / 回 AI 助学看看别的）。
 *
 * 文案逐字对齐原型。原型没有「重试」——这一页不是失败态，是可预期的路由落空，画一枚重试只会
 * 让人反复点同一处（`Batch2EntryScreensContractTest` 从另一侧钉住这枚缺席）。
 * 几何照抄原型：`emptyState` + 260dp 上限的两枚通栏键，主键实底、次键描边。
 */
@Composable
fun UnavailableScreen(
    onBack: () -> Unit,
    onGoLearning: () -> Unit,
    onBrowseFeatures: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        AppScreenHeader(
            title = "页面不可用",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                sticker = Sticker.Info,
                title = "这个页面在当前版本没有开放",
                body = "功能还在打磨，先回到今日学习。已经生成的文章与词单不会受到影响。",
                modifier = Modifier.testTag("unavailable_screen"),
                action = {
                    // 原型那枚 grid：`width:100%; max-width:260px; gap:8; margin-top:8`（`app.js:1519`）。
                    // `widthIn` 必须在 `fillMaxWidth` 之外，否则上限被满宽顶掉。
                    Column(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PillButton(
                            text = "回到今日学习",
                            onClick = onGoLearning,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "unavailable_learning",
                            contentDescription = "回到今日学习",
                        )
                        PillButton(
                            text = "看看别的功能",
                            onClick = onBrowseFeatures,
                            style = PillStyle.Secondary,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "unavailable_browse",
                            contentDescription = "看看别的功能",
                        )
                    }
                },
            )
        }
    }
}
