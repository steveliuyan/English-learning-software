package com.example.englishlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

/**
 * 四栏底部导航。
 *
 * 图标用 [Canvas] 自绘而不是引图标库：项目开启了 `dependencyLocking`，为四个字形引入
 * material-icons-extended 会连带改动锁文件，代价远大于收益。
 *
 * 高度固定 64dp 内容区，再叠 [navigationBarsPadding]，保证三大金刚键/手势条不压住文字。
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
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { tab ->
                TabSlot(tab = tab, selected = tab == selected, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun RowScope.TabSlot(tab: AppTab, selected: Boolean, onSelect: (AppTab) -> Unit) {
    val tint = if (selected) MintPrimary else MintTextMuted
    Column(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .testTag("app_tab_${tab.name.lowercase()}")
            .clickable { onSelect(tab) }
            .semantics { contentDescription = tab.contentDescription },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(22.dp)) { drawTabGlyph(tab, tint) }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 四个字形都在 24×24 的逻辑画布上定义，画布尺寸由 [Canvas] 缩放，改尺寸不用改坐标。
 */
private fun DrawScope.drawTabGlyph(tab: AppTab, tint: Color) {
    // 24dp 画布 → 实际画布。
    val unit = size.minDimension / 24f
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
    val rail = 1.8f * unit
    val round = StrokeCap.Round

    when (tab) {
        // 一本摊开的书：书壳 + 书脊
        AppTab.LEARNING -> {
            drawRoundRect(
                color = tint,
                topLeft = p(3.5f, 5f),
                size = Size(17f * unit, 14f * unit),
                cornerRadius = CornerRadius(2.5f * unit, 2.5f * unit),
                style = Stroke(width = rail),
            )
            drawLine(tint, p(12f, 5f), p(12f, 19f), rail, round)
        }
        // 一篇文章：三条横线，末行留白以区别于书本
        AppTab.READING -> {
            drawLine(tint, p(4f, 7f), p(20f, 7f), rail, round)
            drawLine(tint, p(4f, 12f), p(20f, 12f), rail, round)
            drawLine(tint, p(4f, 17f), p(14f, 17f), rail, round)
        }
        // AI：四角星
        AppTab.AI -> drawSparkle(tint, unit)
        // 设置：三条滑杆，旋钮位置错开
        AppTab.SETTINGS -> {
            val railAlpha = 0.45f
            drawLine(tint.copy(alpha = railAlpha), p(4f, 8f), p(20f, 8f), rail, round)
            drawLine(tint.copy(alpha = railAlpha), p(4f, 13f), p(20f, 13f), rail, round)
            drawLine(tint.copy(alpha = railAlpha), p(4f, 18f), p(20f, 18f), rail, round)
            drawCircle(tint, 2.4f * unit, p(9f, 8f))
            drawCircle(tint, 2.4f * unit, p(15f, 13f))
            drawCircle(tint, 2.4f * unit, p(10f, 18f))
        }
    }
}

/** 四个凹边组成的尖角星，控制点贴近中线即可得到细长的星芒。 */
private fun DrawScope.drawSparkle(tint: Color, unit: Float) {
    val path = Path().apply {
        moveTo(12f * unit, 2.5f * unit)
        quadraticTo(13.5f * unit, 10.5f * unit, 21.5f * unit, 12f * unit)
        quadraticTo(13.5f * unit, 13.5f * unit, 12f * unit, 21.5f * unit)
        quadraticTo(10.5f * unit, 13.5f * unit, 2.5f * unit, 12f * unit)
        quadraticTo(10.5f * unit, 10.5f * unit, 12f * unit, 2.5f * unit)
        close()
    }
    drawPath(path, tint)
}
