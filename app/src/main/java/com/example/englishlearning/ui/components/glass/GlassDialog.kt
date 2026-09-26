package com.example.englishlearning.ui.components.glass

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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

/** API 31+ 走窗口 blur-behind 真模糊（半径 60），以下版本 0（纯 scrim 降级）。 */
fun blurRadiusFor(apiLevel: Int): Int = if (apiLevel >= 31) 60 else 0

/** 窗口后置压暗量：31+ 真模糊下淡压暗，低版本浓 scrim 补偿无模糊。 */
fun scrimAlphaFor(apiLevel: Int): Float = if (apiLevel >= 31) 0.15f else 0.45f

/**
 * 玻璃弹窗：API 31+ 窗口 blur-behind 真模糊；以下版本靠浓 dim scrim 降级。
 * 压暗用窗口 FLAG_DIM_BEHIND 的 dimAmount 实现，只作用于内容区之外的背景，
 * 不会盖住弹窗内容本身。
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
                color = AppPalette.GlassFill,
                border = BorderStroke(1.dp, AppPalette.GlassHighlight),
                shadowElevation = 24.dp,
            ) {
                Column(Modifier.padding(24.dp)) { content() }
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
