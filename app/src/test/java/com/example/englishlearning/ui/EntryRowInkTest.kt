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
     * **2026-10-29 原型 token 迁移改写本腿的依据面**：浅套 `text3` 从旧值换到原型 `#65756C` 后，
     * 压浅套 `tint` 是 4.25:1——已过装饰档 3:1+余量（≥3.1），旧口径「被拒候选当场不合格」的
     * 算术依据**作废**（比值现算，不抄进注释）。也就是说 `text3` 现在正好落在「装饰档合格、
     * 正文档不合格（<4.5）」这一带：拿它当 chevron 在图形档确实能看，本 pin 仍取 `text2`
     * 的理由转为**设计一致性**（chevron 与尾部值同档）、不再是「text3 不合格」。
     * 若批次 1 按原型改回 `text-3`，须同批撤此 pin 并重议（原型优先）。
     *
     * **3.1 的含义**（R-10.6，Task 1 裁决 #1 同口径的算术写法）：装饰墨按 3:1 **加余量**判——
     * 卡在边界上的合格不算合格，所以本套阈值从 `>= 3.0` 收紧成 `>= 3.1`。
     * 评审轮删掉了 `assertTrue(inks.chevron != set.text3)`：两枚 token 取值本不相同，
     * 等值 `assertEquals(set.text2, inks.chevron)` 成立即不等式必然成立，它永远不单独红（重言）。
     * 反向承重改成「被拒候选仍落在装饰档带内」：`text3` 压 `tint` 必须 ∈ [3.1, 4.5)——跌出下界
     * 说明它连装饰档都不合格（旧理由复活，须重写本腿），升过上界说明它成了正文档墨（tier 语义漂移，
     * 得先重议 pin 再动代码）。两个方向都会红。
     * **浅套是这条裁决的现场**；深套的被拒候选取值达标，所以本条只按浅套断言。
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
        // 被拒候选的现状：text3 落在装饰档带内（≥3.1 且 <4.5）——它不再「当场不合格」，
        // 但也没资格当正文墨；本 pin 的等值断言承重，这条把「依据已过期」变成可判红的事实。
        val rejectedRatio = ratioOf(AppColorLight.text3, AppColorLight.tint)
        assertTrue(
            rejectedRatio >= 3.1 && rejectedRatio < 4.5,
            "被拒候选 text3 压浅套 tint 现在是 %.2f:1，已跌出装饰档带 [3.1, 4.5)——" +
                "要么它连图形档都不合格（旧口径的「不合格」理由复活，须重写本腿）、" +
                "要么它升成了正文档墨（text3 的从属档身份漂移，先重议 chevron pin）".format(rejectedRatio)
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
