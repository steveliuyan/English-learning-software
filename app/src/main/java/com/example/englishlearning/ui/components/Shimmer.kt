package com.example.englishlearning.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.englishlearning.ui.theme.AppMotion
import com.example.englishlearning.ui.theme.appColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * 骨架循环微光的规格与绘制（批 C-1；口径见
 * `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 2）。
 *
 * **为什么先有一张纯函数表**：批 A 的字节码与设备实测证明了两件事——Compose 自己遵守
 * `animator_duration_scale`（应用侧再乘一次就是双重缩放，`MotionSpecContractTest` 已把换算入口钉死），
 * 而**循环动画在 `scale=0` 时会停在一个周期的末尾值上然后不再要帧**
 * （emulator-5554：`infiniteRepeatable(tween(600))` 在 scale=0 的 2.5s 窗口只观测到 3 次重组，
 * 对照 scale=1 的 150 次）。于是 shimmer 真正要设计的不是「动起来好不好看」，而是
 * 「**周期末尾那一刻画面是什么**」：末尾停在半亮，用户关掉系统动画就得到一张永久半亮的骨架。
 *
 * 这一条结论原来是「不可源码判定、归截图取证」（`MotionSpecContractTest` 已知局限第 2 条）。
 * 把相位→外观的换算抽成 [ShimmerSpec] 的纯函数之后，**相位语义**这一层可以单测判定
 * （`ShimmerSpecContractTest` 的 R1/R2/R3），截图只负责观感强弱。两者不是同一件事，截图册那一步不许因此省掉。
 */
object ShimmerSpec {
    /**
     * 一次完整扫光的周期 = 4 枚 [AppMotion.Slow]（1200ms）。
     * 时长只许写成 token 算式：裸数字等于绕过批 A 的 token 表（R3 判据）。
     */
    val cycleMillis: Int = AppMotion.Slow * 4

    /**
     * 光带走完行程的时刻：此后到周期末尾它**停在画面外**，这 1/4 周期就是静息停格段。
     * `scale=0` 的定格值取的是周期末尾，所以这一段停格直接决定关闭动画时的外观。
     */
    val travelMillis: Int = AppMotion.Slow * 3

    /** 光带宽度（相对被画节点宽度的比例）。 */
    const val bandFraction: Float = 0.42f

    /** 出画余量：首末相位让**整条带**（含两端渐隐）都在画面外，定格才不留一条亮边。 */
    const val offscreenMargin: Float = 0.06f

    /** 峰值不透明度：光泽是修饰，压得比骨架本体（`outlineSoft` 0.22f）还亮就成了主角。 */
    const val peakAlpha: Float = 0.16f

    /**
     * 相位 [0,1] → 光带中心相对节点宽度的位置。
     * 起于左边界外（-[bandFraction]/2 - 余量），止于右边界外（1 + 同样距离）：
     * 两端同幅、方向唯一，`centerAt(1f)` 也就是循环重启前最后被画到的位置。
     */
    fun centerAt(phase: Float): Float {
        val outside = bandFraction / 2f + offscreenMargin
        return -outside + (1f + 2f * outside) * phase
    }

    /**
     * 相位 [0,1] → 光带不透明度：正弦半波，**首末相位恰为 0f**（R1 的落点），中点取 [peakAlpha]。
     * 两端同值还有一层用处：循环重启时不会看见一次跳变。
     */
    fun alphaAt(phase: Float): Float {
        if (phase <= 0f || phase >= 1f) return 0f
        return (sin(phase * PI.toFloat()) * peakAlpha).coerceIn(0f, peakAlpha)
    }
}

/**
 * 给骨架色块加一层从左扫到右的循环微光。
 *
 * 三条设计约束（都有判据，见 `ShimmerSpecContractTest`）：
 *  - 相位由 [rememberInfiniteTransition] + [keyframes] 驱动，行程段用 [AppMotion.EasingOut]，
 *    末段留出静息停格——**不许**用 `tween` 到点即重启（那样末值会落在峰值上），
 *    也**不许**在这里读 `animator_duration_scale` 或做任何时长换算（批 A R1）。
 *  - 只画在 `drawBehind` 里：光带是绘制层的修饰，不新增内容节点，因此不新增任何会被 TalkBack
 *    念出来的东西，也不参与布局。
 *  - 取色只走 `appColors().tint`，禁裸 hex；深浅两档同一份算式，不需要按主题分支。
 *
 * 调用方负责裁剪（骨架色块应 `.clip(shape)` 后再上微光），本扩展自己不做 clipping——
 * 圆角档由被画对象决定，写死在这里就没法跟着真卡的形状走。
 */
@Composable
fun Modifier.skeletonShimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = ShimmerSpec.cycleMillis
                // 前 3/4 周期走完行程（段末用 EasingOut 收住），后 1/4 停在 1f（画面外、光泽 0f）——静息位。
                0f at 0
                1f at ShimmerSpec.travelMillis with AppMotion.EasingOut
                1f at ShimmerSpec.cycleMillis
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonShimmerPhase",
    )
    val highlight = appColors().tint
    return drawBehind {
        val alpha = ShimmerSpec.alphaAt(phase)
        if (alpha <= 0f) return@drawBehind
        val center = ShimmerSpec.centerAt(phase) * size.width
        val halfBand = ShimmerSpec.bandFraction * size.width / 2f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight.copy(alpha = alpha), Color.Transparent),
                start = Offset(center - halfBand, 0f),
                end = Offset(center + halfBand, size.height),
            ),
        )
    }
}
