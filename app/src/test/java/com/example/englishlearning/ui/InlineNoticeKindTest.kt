package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.noticeInk
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 10 Step 1（R-10.1 / R-10.1b / R-10.1c）：`InlineNotice` 三态墨色必须是**纯表**。
 *
 * 为什么这张表只有 JVM 一个面：connected 侧读不到颜色（Task 9 实测口径——`AppGlyph`/`Text` 的
 * 颜色不是语义节点能取的东西），所以「哪个态取哪几枚 token」只能在这里钉死；connected 只钉
 * 几何、语义、可点数量与裁切。
 *
 * 四字段全钉：稿 `.banner` 每个态钉的是**前景 + 底 + 描边 + 动作墨**四个值（`mockups.html:178-186`），
 * 控制者派前把原 `Pair<Color, Color>` 扩成 [NoticeInk] 四字段——只钉前两枚的表不叫纯表。
 *
 * 双向口径：每条用例既钉「等哪一枚 token」（写法漂移会红），又钉「压自家底 ≥4.5」（取值漂移会红），
 * 浅深两套各跑一遍。比值一律由共享夹具 [ratio]（`SourceRatchet.kt`，Task 2 的 int 域 WCAG 实现）
 * 当场复算并**只写进断言消息**：公式与本仓另两份对比契约同源、测试端只加一枚 Color→int 取样壳，
 * 注释里不抄数值（抄了会随 token 取值漂移，与 `IconBadgeContrastContractTest` 同一口径）。
 *
 * 底色混色按 `androidx.compose.ui.graphics.lerp` 复算：CSS `color-mix(in srgb, X 12%, surface)`
 * 与 `lerp(surface, X, 0.12f)` 是同一个 sRGB 逐通道插值（dispatch 环境事实），
 * 不复制 `IconBadge.kt` 私有的 `mixArgb`（那是 int 域第二套口径）。
 */
class InlineNoticeKindTest {

    /** 两套主题都要跑：浅深各钉一遍，缺一套就是「只验了半张表」。 */
    private val sets: List<AppColorSet> = listOf(AppColorLight, AppColorDark)

    /** 亮度取样只看 RGB 三字节（与 `IconBadgeContrastContractTest.rgbOf` 同式，alpha 不参与比值）。 */
    private fun rgbOf(color: Color): Int = color.toArgb() and 0xFFFFFF

    /** 前景压背景的 WCAG 比值：算式在共享夹具里，本文件只做 Color→int 的转换。 */
    private fun ratioOf(fg: Color, bg: Color): Double = ratio(rgbOf(fg), rgbOf(bg))

    /**
     * R-10.1 的 `.err` 配对：前景取 `error`（error **文**），底取稿的 `color-mix(error-fill 12%, surface)`。
     *
     * 评审轮（R-10.6）把「防回潮」从 `assertTrue(ink.foreground != set.errorFill)`（重言：两枚 token
     * 取值本不相同，等值 `assertEquals(set.error, ink.foreground)` 成立即不等式必然成立，永不单独红）
     * 改成**被拒候选当场不合格**的复算：error-fill 压浅套 Error 底不足正文 4.5——这才是
     * 「error 文 vs error-fill 填充」这条裁决的依据本身在承重（与 `info_notice…` 里 R-10.1b 的
     * `mockActionRatio < 4.5` 同族）。**浅套是这条裁决的现场**；深套 errorFill 达标，所以只按浅套
     * 断言——若哪天 token 漂移让浅套 errorFill 也压得动自家 Error 底，这条会红，提示裁决重议。
     */
    @Test
    fun error_notice_foreground_is_the_error_text_token_not_the_error_fill() {
        sets.forEach { set ->
            val ink = noticeInk(InlineNoticeKind.Error, set)
            assertEquals(set.error, ink.foreground, "Error 态前景必须是 error 文 token（R-10.1）")
            assertEquals(
                lerp(set.surface, set.errorFill, 0.12f),
                ink.backdrop,
                "Error 底必须是 surface 混 12% error-fill（稿 `.banner.err` 的 color-mix）"
            )
            assertEquals(
                set.errorFill.copy(alpha = 0.36f),
                ink.border,
                "Error 描边必须是 error-fill 36% alpha（稿 `color-mix(… 36%, transparent)` 的等价写法）"
            )
            assertEquals(set.text1, ink.action, "动作墨两套同值取 text1（R-10.1b）")

            val foreground = ratioOf(ink.foreground, ink.backdrop)
            assertTrue(
                foreground >= 4.5,
                "Error 前景压自家底只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(foreground)
            )
            val action = ratioOf(ink.action, ink.backdrop)
            assertTrue(
                action >= 4.5,
                "Error 动作墨压自家底只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(action)
            )
        }
        // 裁决依据承重（R-10.6）：被拒候选 error-fill 压浅套 Error 底（lerp(surface, errorFill, 0.12)）
        // 不足正文 4.5——这是「Error 前景取 error 文、不取 error-fill 填充」的依据；深套 errorFill 达标，
        // 浅套才是现场。若 token 漂移让该比值升到 ≥4.5，这条红，提示裁决重议。
        val rejectedFillRatio = ratioOf(
            AppColorLight.errorFill,
            noticeInk(InlineNoticeKind.Error, AppColorLight).backdrop,
        )
        assertTrue(
            rejectedFillRatio < 4.5,
            "裁决依据失效：被拒候选 error-fill 压浅套 Error 底已升到 %.2f:1（≥4.5），" +
                "「Error 前景必须偏离 error-fill」需重议".format(rejectedFillRatio)
        )
    }

    /**
     * R-10.1 的默认态配对 + R-10.1b 的裁决依据。
     *
     * 底 = `tint` **token 直取**（稿 `.banner` 本体就是 `background: var(--tint)`，没有混色），
     * 前景 = `onMint`，描边 = `outline`。
     *
     * 最后那条反空转断言把 R-10.1b 的理由钉进测试：稿 `.act` 写的是 `var(--d-ai)`（浅套 = AiSpeech
     * 的 deep），它压浅套 tint 底不足正文 4.5，会被批 1 Task 1 的低对比禁令契约当场挡下，所以
     * `action` 一律改取 `text1`。若哪天有人把 action 改回稿值，这条会说明它本来就不合格；
     * 若有人把 `tint` 或 AiSpeech 的 deep 改成别的值导致稿值也达标了，这条同样会红，提示裁决要重议。
     */
    @Test
    fun info_notice_pins_tint_backdrop_and_on_mint_foreground() {
        sets.forEach { set ->
            val ink = noticeInk(InlineNoticeKind.Info, set)
            assertEquals(set.onMint, ink.foreground, "Info 态前景必须是 onMint（稿 `.banner` 的 `color: var(--on-mint)`）")
            assertEquals(set.tint, ink.backdrop, "Info 态底必须是 tint token 直取，不做混色（稿 `.banner` 本体）")
            assertEquals(set.outline, ink.border, "Info 态描边必须是 outline token（稿 `.banner` 的 `border-color: var(--outline)`）")
            assertEquals(set.text1, ink.action, "动作墨两套同值取 text1（R-10.1b）")

            val foreground = ratioOf(ink.foreground, ink.backdrop)
            assertTrue(
                foreground >= 4.5,
                "Info 前景压 tint 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(foreground)
            )
            val action = ratioOf(ink.action, ink.backdrop)
            assertTrue(
                action >= 4.5,
                "Info 动作墨压 tint 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(action)
            )
        }
        // R-10.1b 的裁决依据必须承重：稿的 `.act` 墨色压浅套 tint 底不达正文 4.5。
        val mockAction = DomainLight.getValue(AppDomain.AiSpeech).deep
        val mockActionRatio = ratioOf(mockAction, AppColorLight.tint)
        assertTrue(
            mockActionRatio < 4.5,
            "稿 `.act`（浅套 AiSpeech deep）压浅套 tint 已升到 %.2f:1：R-10.1b 的「动作墨必须偏离稿值」依据失效，需重议".format(
                mockActionRatio
            )
        )
    }

    /**
     * R-10.1c 的成功回执态 + 「只有三态」的枚举形状。
     *
     * 稿面对 Success 没有样张：依据是 spec §组件表把 `InlineNotice` 定义为「错误与回执行的统一样式」，
     * 且 `AppIcons.kt` 已把 `Check` 登记为成功回显。墨色因此**不新造色**：前景与底都按稿 `.warn` 同式
     * 复用既有 `success` token（12% 混 surface + 34% alpha 边）。
     *
     * 枚举只有 `Error`/`Info`/`Success` 三枚是本用例的另一半：稿另有 `.banner warn` 琥珀第四态，
     * 但它的两处出现分别是组件样张本身与 AI 屏「未配置」提示（归 Task 11 `NotConfiguredCard`），
     * 且 `Warning` 字形已登记为 `InlineNoticeKind.Error` 用图——加第四态会让图标登记与
     * `DesignTokenContractTest` 双双对不上（spec §2.1 的 token 表没有 warning 值）。
     */
    @Test
    fun success_notice_clears_aa_on_its_own_backdrop_in_both_sets() {
        assertEquals(
            listOf(InlineNoticeKind.Error, InlineNoticeKind.Info, InlineNoticeKind.Success),
            InlineNoticeKind.entries.toList(),
            "R-10.1c：只建三态，`Warning` 不建成第四态（枚举顺序即声明顺序，多一枚即在此红）",
        )
        sets.forEach { set ->
            val ink = noticeInk(InlineNoticeKind.Success, set)
            assertEquals(set.success, ink.foreground, "Success 前景必须是既有 success token（R-10.1c：不是新造色）")
            assertEquals(
                lerp(set.surface, set.success, 0.12f),
                ink.backdrop,
                "Success 底按稿 `.warn` 同式：surface 混 12% success"
            )
            assertEquals(
                set.success.copy(alpha = 0.34f),
                ink.border,
                "Success 描边必须是 success 34% alpha（稿 `color-mix(… 34%, transparent)` 的等价写法）"
            )
            assertEquals(set.text1, ink.action, "动作墨两套同值取 text1（R-10.1b）")

            val foreground = ratioOf(ink.foreground, ink.backdrop)
            assertTrue(
                foreground >= 4.5,
                "Success 前景压自家底只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(foreground)
            )
            val action = ratioOf(ink.action, ink.backdrop)
            assertTrue(
                action >= 4.5,
                "Success 动作墨压自家底只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(action)
            )
        }
    }
}
