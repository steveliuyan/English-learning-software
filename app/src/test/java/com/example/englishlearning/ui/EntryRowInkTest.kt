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
     * 本用例只有两类承重断言（R-10.6，Task 10 评审轮口径）：**等值 pin**（每槽 == 指定 token）与
     * **阈值 pin**（正文级墨色压本套 surface ≥4.5）。「塌色」由等值 pin 抓：若有人把整张表塌成
     * 「反正都是灰绿」，副标题/发丝线不再等于 `text2`/`outlineSoft`，等值断言当场红。
     * 评审轮删掉了原先两条 `inks.title != inks.subtitle` / `inks.divider != inks.subtitle`：
     * 两枚 token 取值本不相同，等值成立即不等式成立，它们在等值 pin 之后**永远不会单独红**——
     * 是文档不是闸（不等式塌不出红），裁决依据的可判红写法见下一条用例。
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
     * 边界上的合格不算合格；批 1 Task 1 的裁决 #1 又禁止 `text-3` 作前景。
     *
     * **3.1 的含义**（R-10.6，Task 1 裁决 #1 同口径的算术写法）：装饰墨按 3:1 **加余量**判——
     * 卡在边界上的合格不算合格，所以本套阈值从 `>= 3.0` 收紧成 `>= 3.1`。
     * 评审轮删掉了 `assertTrue(inks.chevron != set.text3)`：两枚 token 取值本不相同，
     * 等值 `assertEquals(set.text2, inks.chevron)` 成立即不等式必然成立，它永远不单独红（重言）。
     * 裁决的**依据**改用可判红的复算承重：被拒候选 `text3` 压浅套 `tint` 当场不合格（`< 3.1`，
     * 与 `outline` 诱饵、`InlineNoticeKindTest` 的 R-10.1b `mockActionRatio < 4.5` 同族）。
     * **浅套是这条裁决的现场**；深套的被拒候选取值达标，所以本条只按浅套断言——
     * 若哪天浅/深套取值漂移让 `text3` 压 `tint` 达到 3.1，这条会红，提示裁决重议。
     */
    @Test
    fun chevron_is_pinned_to_text2_and_explicitly_not_to_text3() {
        listOf(AppColorLight, AppColorDark).forEach { set ->
            val inks = entryRowInks(set)
            assertEquals(set.text2, inks.chevron)
            val chevronRatio = ratioOf(inks.chevron, set.tint)
            assertTrue(
                chevronRatio >= 3.1,
                "chevron 压 tint 只有 %.2f:1，装饰档按 3:1+余量（≥3.1）判，卡在边界不算合格（set=$set）".format(
                    chevronRatio
                )
            )
        }
        // 被拒候选当场不合格（R-10.6 的可判红裁决依据）：浅套 text3 压 tint 卡在 3:1 线上零余量。
        val rejectedRatio = ratioOf(AppColorLight.text3, AppColorLight.tint)
        assertTrue(
            rejectedRatio < 3.1,
            "裁决依据失效：被拒候选 text3 压浅套 tint 已升到 %.2f:1（≥3.1），「chevron 不得回 text3」需重议"
                .format(rejectedRatio)
        )
        // 反空转：本用例的 ≥3.1 口径必须判得出「装饰性细线压 tint 不合格」，
        // 否则换成 outline/outlineSoft 当 chevron 也算过——那就是取错字段却测不出。
        val decoyRatio = ratioOf(AppColorLight.outline, AppColorLight.tint)
        assertTrue(
            decoyRatio < 3.1,
            "合成负例被放行：浅套装饰线压 tint 竟也过非文本 3:1+余量（%.2f:1），阈值或公式失控".format(decoyRatio)
        )
    }
}
