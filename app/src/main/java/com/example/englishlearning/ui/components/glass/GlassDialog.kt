package com.example.englishlearning.ui.components.glass

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.appColors

/** API 31+ 走窗口 blur-behind 真模糊（半径 60），以下版本 0（纯 scrim 降级）。 */
fun blurRadiusFor(apiLevel: Int): Int = if (apiLevel >= 31) 60 else 0

/** 窗口后置压暗量：31+ 真模糊下淡压暗，低版本浓 scrim 补偿无模糊。 */
fun scrimAlphaFor(apiLevel: Int): Float = if (apiLevel >= 31) 0.15f else 0.45f

/**
 * 玻璃弹窗：API 31+ 窗口 blur-behind 真模糊；以下版本靠浓 dim scrim 降级。
 * 压暗用窗口 FLAG_DIM_BEHIND 的 dimAmount 实现，只作用于内容区之外的背景，
 * 不会盖住弹窗内容本身。
 *
 * 批 2 全分支终审修复席 A（task-18fix）两条落地：
 *  - **容器底随主题换套**：原来读静态浅套 `AppPalette.GlassFill`（`0xEFFFFFFF`），现读
 *    `appColors().surfaceGlass`——浅套取值与之逐字相同，故浅色侧像素零变化，深色侧才拿得到那套深玻璃。
 *    描边 `AppPalette.GlassHighlight` 是**主题无关的 alpha 蒙版**（两套同值，见 `ThemeInvariantMaskMembers`），
 *    按台账口径保持静态读点不动。容器与面板内前景**必须同批迁**：面板内文字全走 `style = AppType.*`
 *    且逐枚显式 `color = appColors().text1`，否则深色档会变成「深玻璃压深墨」的当场不可读
 *    （批 2 seatB 评审 C-1 的 2.18:1 回归）。判据见 `GlassPanelInkPairingContractTest`。
 *  - **内容兜底可滚**：`heightIn(max = 560.dp)` 给面板一个低于窗口高度的硬上限，超限内容交 `verticalScroll`，
 *    24.dp 内边距留在滚动内容之内（链序即语义：上限 → 滚动 → 边距，边距放进滚动里才不吞首尾行）。
 *    设备字号 2.0 叠长列表（导出词书选择器十几册）时面板底部的「取消」仍可达，不再依赖「内容恰好不高」；
 *    上限取 560.dp 是为了让两册短列表在 2.0 缩放下一屏可见，connected 侧既有的零滚动断言据此继续成立。
 */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ApplyDialogWindowEffects()
        Box(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = AppShape.Dialog,
                color = appColors().surfaceGlass,
                border = BorderStroke(1.dp, AppPalette.GlassHighlight),
                shadowElevation = 24.dp,
            ) {
                Column(
                    Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) { content() }
            }
        }
    }
}

@Composable
private fun ApplyDialogWindowEffects() {
    val view = LocalView.current
    LaunchedEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        val sdk = Build.VERSION.SDK_INT
        window.setBackgroundDrawableResource(android.R.color.transparent)
        if (sdk >= 31) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.setBlurBehindRadius(blurRadiusFor(sdk)) }
        }
        window.setDimAmount(scrimAlphaFor(sdk))
    }
}
