package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.components.EntryRowInk
import com.example.englishlearning.ui.components.entryRowInks
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 10 Step 1（R-10.4）：`EntryRow` 的五枚墨色是纯表 `entryRowInks(set)`。
 *
 * 这张表是入口行墨色的**唯一**证据面：connected 侧读不到颜色（Task 9 实测口径），
 * 「断言 chevron 颜色 == `text2`」那种写法在真机上不可执行，计划原文那句已作废。
 * 生产侧 `EntryRow` 只读这张表，不在组件里凑色；浅深两套各跑一遍。
 *
 * 亮度复算复用共享夹具 [ratio]（`SourceRatchet.kt` 的 int 域 WCAG 实现），测试端只加 Color→int 取样壳；
 * 比值只写进断言消息，注释不抄数（抄了会随 token 取值漂移）。
 */
class EntryRowInkTest {

    private fun rgbOf(color: Color): Int = color.toArgb() and 0xFFFFFF

    private fun ratioOf(fg: Color, bg: Color): Double = ratio(rgbOf(fg), rgbOf(bg))

    /**
     * 四个「槽位」各归各的 token：标题 `text1`、副标题与尾部值 `text2`、发丝线 `outlineSoft`。
     *
     * 两条 `!=` 是反空转：若有人把整张表塌成「反正都是灰绿」——标题与副标题同色、或发丝线与副标题
     * 同色——层级会在两套主题里同时消失，但每条 `assertEquals` 单独看都还「等于某枚 token」。
     * 最后两条比值把「正文级墨色压本套 surface 必须达 AA」钉住：槽位取错深浅（如拿 `outlineSoft`
     * 当副标题）会在这里红，而不只是在等值断言里红。
     */
    @Test
    fun entry_row_inks_are_pinboard_slots_not_ad_hoc_colors() {
        listOf(AppColorLight, AppColorDark).forEach { set ->
            val inks = entryRowInks(set)
            assertEquals(set.text1, inks.title, "入口行标题必须取 text1")
            assertEquals(set.text2, inks.subtitle, "入口行副标题必须取 text2")
            assertEquals(set.text2, inks.trailing, "入口行尾部值与副标题同档（稿 `.entry .val`）")
            assertEquals(set.outlineSoft, inks.divider, "行间发丝线必须取 outlineSoft（稿 `.entry + .entry`）")
            assertTrue(
                inks.title != inks.subtitle,
                "标题与副标题塌成同一枚墨色，层级消失：$inks"
            )
            assertTrue(
                inks.divider != inks.subtitle,
                "发丝线与副标题塌成同一枚墨色：$inks"
            )
            val titleRatio = ratioOf(inks.title, set.surface)
            assertTrue(
                titleRatio >= 4.5,
                "标题压本套 surface 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(titleRatio)
            )
            val subtitleRatio = ratioOf(inks.subtitle, set.surface)
            assertTrue(
                subtitleRatio >= 4.5,
                "副标题压本套 surface 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(subtitleRatio)
            )
        }
    }

    /**
     * R-10.4 的裁决落点：chevron 取 `text2`，**不是**稿 `.entry .chev` 写的 `text-3`。
     *
     * `text3` 压浅套 `tint` 恰好卡在非文本 3:1 线上（复算即断言消息里那个数，注释不抄值），
     * 边界上的合格不算合格；批 1 Task 1 的裁决 #1 又禁止 `text-3` 作前景。所以这里三条都钉：
     *  ① 等 `text2`（正向槽位）；② 显式 `!= text3`（防有人「按稿改回去」）；
     *  ③ 压 tint ≥3.0（非文本图形下限，浅深两套各一遍）。
     */
    @Test
    fun chevron_is_pinned_to_text2_and_explicitly_not_to_text3() {
        // text3 压浅套 tint 恰 3.00:1，卡在非文本 3:1 线上；Task 1 裁决 #1 又禁 text-3 作前景
        listOf(AppColorLight, AppColorDark).forEach { set ->
            val inks = entryRowInks(set)
            assertEquals(set.text2, inks.chevron)
            assertTrue(inks.chevron != set.text3, "chevron 不得回到 text3：$inks")
            assertTrue(ratioOf(inks.chevron, set.tint) >= 3.0, "chevron 压 tint 的非文本对比：${ratioOf(inks.chevron, set.tint)}")
        }
        // 反空转：本用例的 ≥3.0 口径必须判得出「装饰性细线压 tint 不合格」，
        // 否则换成 outline/outlineSoft 当 chevron 也算过——那就是取错字段却测不出。
        val decoyRatio = ratioOf(AppColorLight.outline, AppColorLight.tint)
        assertTrue(
            decoyRatio < 3.0,
            "合成负例被放行：浅套装饰线压 tint 竟也过非文本 3:1（%.2f:1），阈值或公式失控".format(decoyRatio)
        )
    }
}
