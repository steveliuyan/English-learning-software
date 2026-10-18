package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.components.DialogButtonRole
import com.example.englishlearning.ui.components.dialogButtonInk
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppColorSet
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 11 裁决 **R-11.1**：破坏性确认按钮的墨色契约（作废 brief Step 3 原稿的「`errorFill` 容器 + 白字」）。
 *
 * 为什么原稿作废（控制者实算，WCAG 相对亮度）：浅套白 on `errorFill #FF3B30` = 3.547（原稿写的 4.0 是错的），
 * 深套白 on `errorFill #FF6B61` = **2.790**，连非文本 3:1 的图形口径都不过；而 17sp ≈ 12.75pt 够不到
 * WCAG 大字豁免线（14pt bold / 18pt regular），所以「按大字享受 3:1」的前提根本不成立。
 * 裁决改成**容器 `set.error` + 前景 `set.surface`**：两套都真 ≥4.5:1，且只用既有 token、零新增裸字面量。
 * `errorFill` 保持它现有的职责（`InlineNotice` 的 Error 背景/描边、装配点的 `errorContainer`），
 * **不得**用作按钮容器。
 *
 * 口径照抄 `EntryRowInkTest` / `InlineNoticeKindTest`：比值一律由共享夹具 [ratio]（`SourceRatchet.kt`，
 * int 域 WCAG 实现）当场复算并**只写进断言消息**，注释里不抄数值（抄了会随 token 取值漂移）；
 * connected 侧读不到颜色，所以「哪枚 token 当容器」只能在这里钉死。
 *
 * 与滑动删除的 `#B3261E` 红底白字**同族但不是同一条**：那一族是「深底浅字 / 浅底深字**按套分叉**」
 * （浅套白字压深红、深套墨字压浅红），不是「红底一律白字」；本契约两套各按 `error`/`surface` 分叉取值，
 * 因此**不宣称大字豁免**，两套都按正文 4.5:1 判。
 */
class ConfirmDestructiveInkTest {

    /** 两套主题都要跑：浅深各钉一遍，缺一套就是「只验了半张表」。 */
    private val sets: List<AppColorSet> = listOf(AppColorLight, AppColorDark)

    /** 亮度取样只看 RGB 三字节（alpha 不参与比值，与 `EntryRowInkTest.rgbOf` 同式）。 */
    private fun rgbOf(color: Color): Int = color.toArgb() and 0xFFFFFF

    /** 前景压背景的 WCAG 比值：算式在共享夹具里，本文件只做 Color→int 的转换。 */
    private fun ratioOf(fg: Color, bg: Color): Double = ratio(rgbOf(fg), rgbOf(bg))

    /**
     * R-11.1 的正题：破坏性确认按钮 = `error` 容器 + `surface` 前景，两套各 ≥4.5。
     *
     * 后半段是**被拒候选的永久钉**（同 `EntryRowInkTest` 的 `rejectedRatio < 3.1`、
     * `InlineNoticeKindTest` 的 `mockActionRatio < 4.5` 一族）：白字压两套 `errorFill` 都必须不合格，
     * 这样「红底一律白字」这个错配方就回不了潮——若哪天 token 漂移让白字压 `errorFill` 达标了，
     * 这条会红，提示裁决重议（而不是有人悄悄把配方改回去）。
     */
    @Test
    fun destructive_confirm_button_pairs_error_container_with_surface_label() {
        sets.forEach { set ->
            val ink = dialogButtonInk(DialogButtonRole.DestructiveConfirm, set)
            assertEquals(set.error, ink.container, "破坏性确认的容器必须是 error 文 token（R-11.1），不是 errorFill 填充")
            assertEquals(set.surface, ink.label, "破坏性确认的前景必须是 surface（两套各自分叉：浅套白、深套墨）")
            val contrast = ratioOf(ink.label, ink.container)
            assertTrue(
                contrast >= 4.5,
                ("破坏性确认按钮前景压容器只有 %.2f:1，不足正文 AA 4.5（set=$set）——" +
                    "17sp ≈ 12.75pt 够不到大字豁免线，不得按 3:1 图形口径放行").format(contrast)
            )
        }

        val rejectedLight = ratioOf(Color.White, AppColorLight.errorFill)
        assertTrue(
            rejectedLight < 4.5,
            "裁决依据失效：白字压浅套 errorFill 已升到 %.2f:1（≥4.5），「容器不得取 errorFill + 白字」需重议"
                .format(rejectedLight)
        )
        val rejectedDark = ratioOf(Color.White, AppColorDark.errorFill)
        assertTrue(
            rejectedDark < 4.5,
            "裁决依据失效：白字压深套 errorFill 已升到 %.2f:1（≥4.5），「容器不得取 errorFill + 白字」需重议"
                .format(rejectedDark)
        )
        assertTrue(
            rejectedDark < 3.0,
            ("深套白字压 errorFill 必须连非文本 3:1 都不过（控制者实算 2.790）：实际 %.2f:1 —— " +
                "这条钉的是「原稿连图形口径都撑不住」，不是只撑不住正文口径").format(rejectedDark)
        )
    }

    /**
     * 同族另外两枚按钮角色（本棒自补：一枚纯表就得整表钉，否则「破坏性档对了、其余档凑色」测不出）。
     *
     * `Confirm` = 出站确认的「确认发送」：容器 `onMint` + 前景 `background`，逐字复用装配点已钉的
     * `colorScheme.primary` / `onPrimary` 那一对（`MintGlassTheme.kt`），不新造第三套按钮语义。
     * `Dismiss` = 透明容器 + `text2` 前景：它压在面板 `surface` 上，所以比值按 `surface` 复算。
     */
    @Test
    fun every_dialog_button_role_clears_aa_on_what_it_actually_sits_on() {
        sets.forEach { set ->
            val confirm = dialogButtonInk(DialogButtonRole.Confirm, set)
            assertEquals(set.onMint, confirm.container, "确认档容器必须是 onMint（= 装配点 colorScheme.primary）")
            assertEquals(set.background, confirm.label, "确认档前景必须是 background（= 装配点 colorScheme.onPrimary）")
            val confirmRatio = ratioOf(confirm.label, confirm.container)
            assertTrue(
                confirmRatio >= 4.5,
                "确认档前景压容器只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(confirmRatio)
            )

            val dismiss = dialogButtonInk(DialogButtonRole.Dismiss, set)
            assertEquals(Color.Transparent, dismiss.container, "取消档必须是透明容器（文字按钮，不与确认档争视觉重量）")
            assertEquals(set.text2, dismiss.label, "取消档前景必须是 text2（次要动作，但仍要达正文 AA）")
            val dismissRatio = ratioOf(dismiss.label, set.surface)
            assertTrue(
                dismissRatio >= 4.5,
                "取消档前景压面板 surface 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(dismissRatio)
            )
        }
        assertEquals(
            listOf(DialogButtonRole.DestructiveConfirm, DialogButtonRole.Confirm, DialogButtonRole.Dismiss),
            DialogButtonRole.entries.toList(),
            "只建三档：多一枚就是第二种「主操作」，会与批 1 唯一按钮 PillButton 的档位裁决打架（枚举顺序即声明顺序）",
        )
    }

    /**
     * 源码契约腿（本棒自补）：墨色纯表只有在生产**真读它**时才承重。
     *
     * 与 `SharedComponentGeometryContractTest` 同口径：先剥注释再匹配（实现文件的 KDoc 会合法复述
     * `errorFill` / 白字这些被拒配方，不剥注释的正则会被注释满足，契约就成空转），
     * 并配合成正/负片段自证探针不是恒真。
     */
    @Test
    fun destructive_dialog_source_reads_the_ink_table_and_never_the_rejected_recipe() {
        val source = uiComponentSource("ConfirmDestructiveDialog.kt")
        assertEquals(
            0,
            countMatches(source, Regex("""errorFill""")),
            "R-11.1：`errorFill` 不得出现在破坏性确认对话框里（它只服务 InlineNotice 的 Error 底/描边与装配点 errorContainer）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""Color\.White\b""")),
            "R-11.1：白字配方已作废，`Color.White` 不得出现在本文件（前景一律走纯表的 surface）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""dialogButtonInk\(role, appColors\(\)\)""")),
            "按钮墨色必须恰一处从纯表取（`DialogButton` 内部）；多一处 = 第二套取墨口径",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""role = DialogButtonRole\.DestructiveConfirm""")),
            "破坏性确认按钮必须恰一处按 DestructiveConfirm 档取墨（0 处 = 退回凑色，2 处 = 第二枚破坏性按钮）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\b(?:AppPalette|DomainColors)\.""")),
            "新组件不得读静态浅套源（深色模式会拿到浅值；`DarkTokenConsumptionRatchetTest` 的同一口径）",
        )
        assertEquals(
            0,
            countMatches(source, rawColorLiteral),
            "新组件不得写裸 hex（口径 = 共享夹具 rawColorLiteral，与 `RawColorLiteralRatchetTest` 同一份，批 1 终审 F2）",
        )

        // 合成正/负片段：证明上面几枚探针既抓得住也不越界（house 口径：每条源码契约自带双边取样）。
        val rejected = "Surface(color = set.errorFill) { Text(\"删除\", color = Color.White) }"
        assertEquals(1, countMatches(rejected, Regex("""errorFill""")), "探针失控：errorFill 串根本没被数出来")
        assertEquals(1, countMatches(rejected, Regex("""Color\.White\b""")), "探针失控：Color.White 串根本没被数出来")
        assertEquals(
            1,
            countMatches("val ink = dialogButtonInk(role, appColors())", Regex("""dialogButtonInk\(role, appColors\(\)\)""")),
            "探针失控：纯表取墨的正确形状没被认出来",
        )
        assertEquals(
            0,
            countMatches("val ink = dialogButtonInk(DialogButtonRole.Confirm, set)", Regex("""dialogButtonInk\(role, appColors\(\)\)""")),
            "探针越界：纯表定义处的形状被当成组件里的取墨点",
        )
    }

    // M-3（Task 12 兑现）：本类原先自带一份私有 uiComponentSource，与另外三份逐字相同，已提升为
    // SourceRatchet.kt 的共享夹具（文件恰一个 + 目录守卫两条断言逐字保留），本文件直接调用同名顶层函数。
}
