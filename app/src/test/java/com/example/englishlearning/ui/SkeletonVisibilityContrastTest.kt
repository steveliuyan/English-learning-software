package com.example.englishlearning.ui

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.components.ShimmerSpec
import com.example.englishlearning.ui.components.SkeletonInk
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppColorSet
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 C-7 骨架可见性判据（判据来源：用户 2026-11-07 裁决「提到可见档」，读数与踩坑见
 * `docs/verification/ui-redesign-2026-10.md` §批 C-6 与 `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` §4 末）。
 *
 * **这一族为什么现在才存在**：批 C-6 把骨架与 shimmer 的结构判据跑满了（节点画了、`scale=0` 定格逐字节等于静息骨架、
 * 光带确实在扫、读屏不念假数字），但用户在实机上看到的仍是一张空白页——通道差只有 1–4 级。
 * 「差多少级才算看得见」本来只能靠像素取样事后发现；而它是纯算术（同一份 token、同一个 alpha、合成后的通道差），
 * 所以本批把它提前钉成 JVM 判据，从此**改回不可见档会当场红**，不用再等一轮截图册。
 *
 * 尺子取「相对容器的最大通道差 ≥ [MIN_VISIBLE_CHANNEL_DELTA]」而不是 WCAG 比：
 *  - WCAG 1.4.11 的 ≥3:1 是给**图标/非文本信息载体**的（`docs/design/visual-style-guide.md` §对比度口径、
 *    `IconBadgeContrastContractTest` 按亮度复算的那套）。骨架不承担信息，且 pastel 档里**没有任何一枚 token 够得着 3:1**
 *    （`outline` 满值压页底只有 1.159:1），硬凑 3:1 只能画成深灰块，与设计语言冲突。
 *  - 于是取本轮实测里「唯一看得见的一档」做锚：卡壳（`surface` 压页底）差 16 级看得见，骨架条与光带至少要做到同强。
 *    这条是**经验锚**，不是标准；观感强弱仍归截图册目视，本判据只保证不再退回到「读作空白」。
 *
 * 形状沿用禁令族惯例：正向判据 + 反空转（旧档必须被同一条线判死）+ 唯一落点锚点。
 */
class SkeletonVisibilityContrastTest {

    /** 与批 C-6 像素取样同口径：渲染后与容器的**最大单通道差**，单位是 8-bit 级。 */
    private fun channelDelta(a: IntArray, b: IntArray): Int = (0..2).maxOf { abs(a[it] - b[it]) }

    private fun rgb(color: Color): IntArray = color.toArgb().let { intArrayOf((it shr 16) and 255, (it shr 8) and 255, it and 255) }

    /** `fg.copy(alpha = a)` 压在 `bg` 上的实际落点（SrcOver，逐通道线性）。 */
    private fun srcOver(fg: Color, bg: Color): IntArray {
        val f = rgb(fg)
        val b = rgb(bg)
        val a = fg.alpha
        return IntArray(3) { (b[it] + (f[it] - b[it]) * a).toInt().coerceIn(0, 255) }
    }

    /**
     * 光带落点：`screen(x, y) = 1 - (1 - x)(1 - y)` 再按不透明度线性回掺。
     * 这是 Skia 对 `BlendMode.Screen` + coverage 的算法，与 `SkeletonInk.bandBlend` 的取值绑定
     * （机制本身由「band mechanism is a lightening blend not a theme branch」单独钉，两条一起才成立）。
     */
    private fun screen(dst: IntArray, src: Color, alpha: Float): IntArray {
        val s = rgb(src)
        return IntArray(3) { i ->
            val blended = 255.0 * (1.0 - (1.0 - dst[i] / 255.0) * (1.0 - s[i] / 255.0))
            (dst[i] + (blended - dst[i]) * alpha).toInt().coerceIn(0, 255)
        }
    }

    private fun palette(name: String, set: AppColorSet) = "$name（页底 #${"%06X".format(set.background.toArgb() and 0xFFFFFF)} / " +
        "卡面 #${"%06X".format(set.surface.toArgb() and 0xFFFFFF)}）"

    /**
     * 一枚填充在两套容器上的实际可见度。
     *
     * 比的是**合成后的落点 vs 容器本色**，不是 token vs 落点：`SkeletonInk.fill` 现在取满值 `outline`（alpha=1），
     * 「token 与它压上去的结果差多少」恒等于 0，那样写出来的判据只会红在算式上而不是红在观感上——
     * 本仓批 C-6 第一次跑这族判据就红在这儿（两条 fill 腿报 0 级，而实机截图明明看得见色块）。
     * 用户看得见的是「条与它身后那点页底/卡面差几级」，所以两向都按合成后取。
     */
    private fun visibility(fill: Color, set: AppColorSet): Pair<Int, Int> =
        channelDelta(srcOver(fill, set.background), rgb(set.background)) to
            channelDelta(srcOver(fill, set.surface), rgb(set.surface))

    @Test
    fun `bar fill reads against both containers in light theme`() {
        val fill = SkeletonInk.fill(AppColorLight)
        val (onPage, onCard) = visibility(fill, AppColorLight)
        assertTrue(
            onPage >= MIN_VISIBLE_CHANNEL_DELTA && onCard >= MIN_VISIBLE_CHANNEL_DELTA,
            "浅色档骨架条不可见：填充 #${"%06X".format(fill.toArgb() and 0xFFFFFF)} alpha=${fill.alpha} " +
                "压页底差 $onPage 级、压卡面差 $onCard 级，两向都要 ≥$MIN_VISIBLE_CHANNEL_DELTA（卡壳实测锚）。" +
                palette("浅", AppColorLight),
        )
    }

    @Test
    fun `bar fill reads against both containers in dark theme`() {
        val fill = SkeletonInk.fill(AppColorDark)
        val (onPage, onCard) = visibility(fill, AppColorDark)
        assertTrue(
            onPage >= MIN_VISIBLE_CHANNEL_DELTA && onCard >= MIN_VISIBLE_CHANNEL_DELTA,
            "深色档骨架条不可见：填充 #${"%06X".format(fill.toArgb() and 0xFFFFFF)} alpha=${fill.alpha} " +
                "压页底差 $onPage 级、压卡面差 $onCard 级，两向都要 ≥$MIN_VISIBLE_CHANNEL_DELTA。" +
                palette("深", AppColorDark),
        )
    }

    @Test
    fun `band lifts the bar instead of painting it darker in both themes`() {
        listOf("浅" to AppColorLight, "深" to AppColorDark).forEach { (name, set) ->
            val bar = srcOver(SkeletonInk.fill(set), set.surface)
            val peak = screen(bar, SkeletonInk.band(set), ShimmerSpec.peakAlpha)
            val delta = channelDelta(peak, bar)
            val darker = peak.sum() < bar.sum()
            assertTrue(
                delta >= MIN_VISIBLE_CHANNEL_DELTA && !darker,
                "$name 色档光带峰值相对骨架条只差 $delta 级（需 ≥$MIN_VISIBLE_CHANNEL_DELTA），" +
                    "取色 #${"%06X".format(SkeletonInk.band(set).toArgb() and 0xFFFFFF)} peakAlpha=${ShimmerSpec.peakAlpha}" +
                    if (darker) " 且方向是**画暗**（裁决要求比骨架条亮）" else "",
            )
        }
    }

    @Test
    fun `band mechanism is a lightening blend not a theme branch`() {
        // 提亮机制本身是判据成立的前提：SrcOver 的方向由底色与取色的相对明暗决定，浅色卡面上必然画暗。
        assertEquals(
            BlendMode.Screen,
            SkeletonInk.bandBlend,
            "光带仍是 ${SkeletonInk.bandBlend}：Screen 这类提亮混合才能保证「结果只可能比底色亮」，" +
                "深浅两档同一份算式；换回 SrcOver 就等于把方向交回 palette 顺序。",
        )
    }

    @Test
    fun `the batch c-six tier is rejected by the same threshold`() {
        // 反空转：批 C-6 实测不过的那两个写法必须被同一条线判死，否则这条判据是摆设。
        // 这里的 alpha 一律写**批 C-6 的原值**（0.22f / 0.16f），不引用现值常量：本腿要钉的是「旧写法过不了线」，
        // 引用 ShimmerSpec.peakAlpha 的话，将来有人调峰值就会把这条历史判据一起改掉。
        val oldFillLight = AppColorLight.outlineSoft.copy(alpha = 0.22f)
        val (oldPage, oldCard) = visibility(oldFillLight, AppColorLight)
        assertTrue(
            oldPage < MIN_VISIBLE_CHANNEL_DELTA && oldCard < MIN_VISIBLE_CHANNEL_DELTA,
            "旧档（outlineSoft@0.22 压浅页底/白卡面）没被判死：$oldPage / $oldCard 级，说明阈值或算式是摆设",
        )
        val oldFillDark = AppColorDark.outlineSoft.copy(alpha = 0.22f)
        val (oldDarkPage, oldDarkCard) = visibility(oldFillDark, AppColorDark)
        assertTrue(
            oldDarkPage < MIN_VISIBLE_CHANNEL_DELTA && oldDarkCard < MIN_VISIBLE_CHANNEL_DELTA,
            "旧档（outlineSoft@0.22 压深页底/深卡面）没被判死：$oldDarkPage / $oldDarkCard 级",
        )

        // 旧光带两问：够不够强 + 方向对不对。
        val oldBar = srcOver(oldFillLight, AppColorLight.surface)
        val c6 = IntArray(3) { i ->
            (oldBar[i] + (rgb(AppColorLight.tint)[i] - oldBar[i]) * C6_PEAK_ALPHA).toInt().coerceIn(0, 255)
        }
        assertTrue(
            channelDelta(c6, oldBar) < MIN_VISIBLE_CHANNEL_DELTA,
            "旧光带写法（SrcOver 叠 tint@0.16）没被判死：差 ${channelDelta(c6, oldBar)} 级",
        )
        // 方向单独钉一次：同一条旧骨架上换成今天的峰值，SrcOver 仍然是画暗——
        // 证明「把 alpha 提上去」救不了 SrcOver，机制必须换（裁决那句「比所在容器更亮」）。
        val todayAlpha = IntArray(3) { i ->
            (oldBar[i] + (rgb(AppColorLight.tint)[i] - oldBar[i]) * ShimmerSpec.peakAlpha).toInt().coerceIn(0, 255)
        }
        assertTrue(
            todayAlpha.sum() < oldBar.sum(),
            "SrcOver + tint 在浅色卡面上不再是画暗了（${todayAlpha.toList()} vs ${oldBar.toList()}）：" +
                "这条腿钉的是方向，方向一旦变了说明取色或底色口径动过，必须显式改判据而不是让它悄悄绿",
        )
    }

    @Test
    fun `skeleton ink is the only place skeleton colour is chosen`() {
        // 唯一落点锚：使用点自己挑色，判据就只覆盖了 SkeletonInk 而漏掉真正被画的那一枚。
        val skeleton = stripComments(uiComponentSource("Skeleton.kt"))
        val shimmer = stripComments(uiComponentSource("Shimmer.kt"))
        val fills = Regex("""SkeletonInk\.fill\(""").findAll(skeleton).toList().size
        assertTrue(fills >= 2, "Skeleton.kt 里 `SkeletonInk.fill(` 只 $fills 处（条 + 前导点应各 1 处）：使用点自己挑色，判据管不到")
        assertTrue(
            Regex("""outlineSoft\.copy\(""").findAll(skeleton).toList().isEmpty(),
            "Skeleton.kt 里仍有 `outlineSoft.copy(` 的骨架填充：墨的落点在 SkeletonInk，色档改动必须一处生效全站",
        )
        assertTrue(
            Regex("""SkeletonInk\.band\(""").findAll(shimmer).toList().size >= 1 &&
                Regex("""SkeletonInk\.bandBlend""").findAll(shimmer).toList().size >= 1,
            "Shimmer.kt 没走 SkeletonInk 的取色与混合模式：光带判据会被绕过",
        )
    }

    private companion object {
        /**
         * 16 级 = 批 C-6 实机像素取样里**唯一被眼睛认出**的那一档（卡壳 `surface` 压页底差 16 级 / 1.110:1）。
         * 骨架条与光带至少同强；低于这条的历史档位（1–5 级）实测读作空白页。
         */
        const val MIN_VISIBLE_CHANNEL_DELTA = 16

        /** 批 C-6 的光带峰值（已被本轮裁决换掉）。只在反空转腿里当历史常量用，勿改成引用现值。 */
        const val C6_PEAK_ALPHA = 0.16f
    }
}
