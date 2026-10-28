package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
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
 * 批 0 原型对齐（2026-10-29）：`InlineNotice` 三态墨色必须是**纯表**，且逐枚对位原型
 * `assets/app.css` 的 `.notice` / `.notice-error`。
 *
 * 为什么这张表只有 JVM 一个面：connected 侧读不到颜色（Task 9 实测口径——`AppGlyph`/`Text` 的
 * 颜色不是语义节点能取的东西），所以「哪个态取哪几枚 token」只能在这里钉死；connected 只钉
 * 几何、语义、可点数量与裁切。
 *
 * 原型口径（本文件全部判据的出处）：
 *  - `.notice`：`background: var(--tint)`、`color: var(--ink-2)`、**无描边**；
 *  - `.notice-info`：`background: var(--surface-2)`、`color: var(--ink-2)`（本仓取 `.notice` 本体那一档）；
 *  - `.notice-error`：`background: var(--danger-tint)`、`color: var(--danger)`。
 *
 * 双向口径：每条用例既钉「等哪一枚 token」（写法漂移会红），又钉「压自家底 ≥4.5」（取值漂移会红），
 * 浅深两套各跑一遍。比值一律由共享夹具 [ratio]（`SourceRatchet.kt`）当场复算并**只写进断言消息**：
 * 注释里不抄数值（抄了会随 token 取值漂移）。
 *
 * 动作墨 [com.example.englishlearning.ui.components.NoticeInk.action] 一律 `text1`（R-10.1b）：原型的
 * 动作链接写域色，域色浅端压浅套提示底不足正文 4.5；这条裁决的依据本身在
 * [info_notice_action_ink_must_be_text1_because_the_mock_domain_color_fails_aa] 里承重。
 */
class InlineNoticeKindTest {

    /** 两套主题都要跑：浅深各钉一遍，缺一套就是「只验了半张表」。 */
    private val sets: List<AppColorSet> = listOf(AppColorLight, AppColorDark)

    /** 亮度取样只看 RGB 三字节（与 `IconBadgeContrastContractTest.rgbOf` 同式，alpha 不参与比值）。 */
    private fun rgbOf(color: Color): Int = color.toArgb() and 0xFFFFFF

    /** 前景压背景的 WCAG 比值：算式在共享夹具里，本文件只做 Color→int 的转换。 */
    private fun ratioOf(fg: Color, bg: Color): Double = ratio(rgbOf(fg), rgbOf(bg))

    /**
     * 默认态（原型 `.notice` 本体）：`tint` 底 + `ink-2` 墨 + `text1` 动作墨，**无描边**。
     *
     * 三枚字段全钉：只钉背底不钉墨的表会让「哪个态用哪枚前景」变成屏侧自由发挥的第二套真值；
     * `NoticeInk` 已经没有 border 字段（原型 `.notice` 没有 `border`），这里也不该再钉一枚描边。
     */
    @Test
    fun info_notice_pins_tint_backdrop_and_ink2_foreground() {
        sets.forEach { set ->
            val ink = noticeInk(InlineNoticeKind.Info, set)
            assertEquals(set.text2, ink.foreground, "Info 态前景必须是 ink-2（原型 `.notice` 的 `color: var(--ink-2)`）")
            assertEquals(set.tint, ink.backdrop, "Info 态底必须是 tint token 直取（原型 `.notice` 的 `background: var(--tint)`）")
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
    }

    /**
     * 错误态（原型 `.notice-error`）：`danger-tint` 底 + `danger` 墨 + `text1` 动作墨，**无描边**。
     *
     * 末尾那条反空转断言把「为什么前景不取 `errorFill`」钉进测试：`errorFill` 是本仓的**填充**红
     * （原型 `--danger` 的实底色），当墨压在 `danger-tint` 底上不足正文 4.5。若哪天 token 漂移让
     * 它达标，这条会红并提示重议「哪一枚红才是文本墨」。
     */
    @Test
    fun error_notice_pins_danger_tint_backdrop_and_danger_foreground() {
        sets.forEach { set ->
            val ink = noticeInk(InlineNoticeKind.Error, set)
            assertEquals(set.error, ink.foreground, "Error 态前景必须是 danger token（原型 `.notice-error` 的 `color: var(--danger)`）")
            assertEquals(set.dangerTint, ink.backdrop, "Error 态底必须是 danger-tint（原型 `.notice-error` 的 `background: var(--danger-tint)`）")
            assertEquals(set.text1, ink.action, "动作墨两套同值取 text1（R-10.1b）")

            val foreground = ratioOf(ink.foreground, ink.backdrop)
            assertTrue(
                foreground >= 4.5,
                "Error 前景压 danger-tint 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(foreground)
            )
            val action = ratioOf(ink.action, ink.backdrop)
            assertTrue(
                action >= 4.5,
                "Error 动作墨压 danger-tint 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(action)
            )
        }
        // 被拒候选当场不合格：errorFill 当墨压**浅套** danger-tint 不足 4.5 —— 这是「Error 前景取
        // danger 文墨、不取 errorFill 亮红」的依据。深套里亮红压深底反而达标，所以只按浅套承重：
        // 浅套是这条裁决的现场（与 R-10.6 的 errorFill 判据同族）。
        val rejectedInkRatio = ratioOf(
            AppColorLight.errorFill,
            noticeInk(InlineNoticeKind.Error, AppColorLight).backdrop,
        )
        assertTrue(
            rejectedInkRatio < 4.5,
            "裁决依据失效：被拒候选 errorFill 当墨压浅套 danger-tint 已升到 %.2f:1（≥4.5），" +
                "「Error 前景必须取 danger 文墨」需重议".format(rejectedInkRatio)
        )
    }

    /**
     * 回执态（原型无样张）：前景取既有 `success` token、底取 `.notice` 本体的 `tint`、动作墨 `text1`。
     *
     * **这是原型没画到的状态**，保留原因写在 `InlineNotice.kt` 的 KDoc：spec §组件表把本组件定义为
     * 「错误 / 回执行统一样式」，既有屏幕的完成回执在用；墨色不新造色，只复用已登记的 `success`。
     * 等原型补出成功回执样张时，这一条要让位给样张。枚举仍只有三态——`Warning` 不建成第四态
     * （原型没有 `.notice-warn`，`AppIcons.kt` 也已把 `Warning` 字形登记给 Error）。
     */
    @Test
    fun success_notice_clears_aa_on_the_default_tint_backdrop_in_both_sets() {
        assertEquals(
            listOf(InlineNoticeKind.Error, InlineNoticeKind.Info, InlineNoticeKind.Success),
            InlineNoticeKind.entries.toList(),
            "R-10.1c：只建三态（枚举顺序即声明顺序，多一枚即在此红）",
        )
        sets.forEach { set ->
            val ink = noticeInk(InlineNoticeKind.Success, set)
            assertEquals(set.success, ink.foreground, "Success 前景必须是既有 success token（不是新造色）")
            assertEquals(set.tint, ink.backdrop, "Success 底取原型 `.notice` 本体的 tint（原型未画成功样张）")
            assertEquals(set.text1, ink.action, "动作墨两套同值取 text1（R-10.1b）")

            val foreground = ratioOf(ink.foreground, ink.backdrop)
            assertTrue(
                foreground >= 4.5,
                "Success 前景压 tint 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(foreground)
            )
            val action = ratioOf(ink.action, ink.backdrop)
            assertTrue(
                action >= 4.5,
                "Success 动作墨压 tint 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(action)
            )
        }
    }

    /**
     * R-10.1b 的裁决依据必须承重：原型 `.notice .act` 用域色（浅套 AiSpeech 那一枚）当链接墨，
     * 它压浅套 `tint` 底不足正文 4.5 —— 这就是动作墨必须改取 `text1` 的理由。
     */
    @Test
    fun info_notice_action_ink_must_be_text1_because_the_mock_domain_color_fails_aa() {
        val mockAction = DomainLight.getValue(AppDomain.AiSpeech).base
        val mockActionRatio = ratioOf(mockAction, AppColorLight.tint)
        assertTrue(
            mockActionRatio < 4.5,
            "原型 `.act`（浅套 AiSpeech base）压浅套 tint 已升到 %.2f:1：R-10.1b 的「动作墨必须偏离稿值」依据失效，需重议".format(
                mockActionRatio
            )
        )
    }
}
