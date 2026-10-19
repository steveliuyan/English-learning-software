package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.DomainLight
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Task 17A Step 3 对比契约（修复轮 1 按评审 I-1 改成「按真实底复算」）。
 *
 * 底导选中态是**三条腿**，各自铺在不同的底上；旧契约把它们压成一条（`deep` 压 `surface`），
 * 于是既绑错了底、又用错了档位。本席逐腿分开：
 *
 *  1. **未选中档**：字形与标签共用同一枚 `text2`，铺在底导容器 `background(MintSurface)`
 *     （= 当前主题 `surface`）上。标签是正文档 → 判 ≥4.5。本轮实测：浅 5.151、深 7.543。
 *  2. **选中档字形腿**：走 `InlineIconBadge`，字形实际铺在该组件的 `background(appColors().tint)`
 *     药片底上（不是 `surface`），是非文本图形档 → 判 ≥3.0。本轮实测：浅 3.546、深 8.632。
 *     旧契约按 `surface` 复算这一腿，而字形从不落在那个底上 ⇒ 真实腿零断言。
 *  3. **选中档标签文字腿**：铺在同一枚容器 `surface` 上，是正文档 → 判 ≥4.5。
 *     浅套按 R-14.4 的 AiSpeech 例外取 `appColors().onMint`（本轮实测 9.751），
 *     深套沿用 `appDomain(AiSpeech).deep`（深套 deep 与 base 合一，本轮实测 9.850）。
 *     旧契约让浅套 `deep` 走这条腿（实测 4.016，够不上 4.5）却按图形 3.0 放行 ⇒ 档位用错；
 *     那枚 4.016 现在留在本契约里作**诱饵负例**，证明这条腿确实被 4.5 线判过。
 *
 * 上面那些实测数字只是本轮取证留痕，**判据一律是档位线本身**（4.5 / 3.0）加同底诱饵负例，
 * 不把实测值钉进判据（14A 零余量禁令）。本轮余量：浅文字 +5.25、深文字 +5.35、浅图形 +0.55、深图形 +5.63。
 *
 * 契约不只算数：最后一例把这三条复算的腿逐一绑回生效渲染路径——底导容器底 `background(MintSurface)`、
 * 未选中墨 `appColors().text2`、选中字形入口 `InlineIconBadge(icon, domain = AppDomain.AiSpeech)`
 * 与组件内的 tint 药片底 + `deep` 单层描边、选中标签墨的取色表达式、以及 `MintSurface` 的主题转发。
 * 任何一条腿搬家（换底、换墨、换组件）本契约红。
 */
class AppBottomBarContrastTest {

    private fun rgb(color: Color): Int = color.toArgb() and 0xFFFFFF

    private fun sourceOf(name: String, expectedDir: File = File(UI_MAIN_SOURCE_PATH)): String {
        val files = uiFiles().filter { it.name == name }
        assertTrue(files.size == 1, "$name 应恰好一个（期望目录 $UI_MAIN_SOURCE_PATH），实际 ${files.size} 个")
        val file = files.single()
        assertTrue(
            file.parentFile == expectedDir,
            "$name 不在 ${expectedDir.path} 下（搬家会让本契约读错文件）：${file.path}",
        )
        return stripComments(file.readText())
    }

    /** 反空转对：正例必须过线、同式取错字段必须掉线，否则「≥阈值」是公式失控的假象。 */
    private fun assertPairClears(fgName: String, fg: Int, bgName: String, bg: Int, floor: Double, decoy: Pair<String, Int>) {
        val r = ratio(fg, bg)
        assertTrue(
            r >= floor,
            "$fgName 压$bgName 只有 %.2f:1，不足 %.1f:1（浅/深两套 token 漂移到此为止，先对 token 再谈底导）".format(r, floor),
        )
        val decoyRatio = ratio(decoy.second, bg)
        assertTrue(
            decoyRatio < floor,
            "反空转失控：同一条公式判「${decoy.first}」(${decoyRatio}) 也过 %.1f 线，阈值或亮度口径失效".format(floor),
        )
    }

    private val aiSpeechLightDeep get() = rgb(DomainLight.getValue(AppDomain.AiSpeech).deep)
    private val aiSpeechDarkDeep get() = rgb(DomainDark.getValue(AppDomain.AiSpeech).deep)

    // ---------- 腿 1：未选中档（text2 压底导容器 surface） ----------

    @Test
    fun `unselected text2 clears 4 to 1 on the light bar surface`() {
        assertPairClears(
            fgName = "浅套 text2", fg = rgb(AppColorLight.text2),
            bgName = "浅套 surface 底", bg = rgb(AppColorLight.surface),
            floor = 4.5,
            decoy = "浅套装饰线 text3" to rgb(AppColorLight.text3),
        )
    }

    @Test
    fun `unselected text2 clears 4 to 1 on the dark bar surface`() {
        assertPairClears(
            fgName = "深套 text2", fg = rgb(AppColorDark.text2),
            bgName = "深套 surface 底", bg = rgb(AppColorDark.surface),
            floor = 4.5,
            decoy = "深套装饰线 outline" to rgb(AppColorDark.outline),
        )
    }

    // ---------- 腿 2：选中档字形（deep 压 InlineIconBadge 的 tint 药片底，图形档） ----------

    @Test
    fun `selected ai_speech glyph clears 3 to 1 on the inline badge tint backdrop`() {
        assertPairClears(
            fgName = "浅套 AiSpeech deep 字形", fg = aiSpeechLightDeep,
            bgName = "浅套 tint 药片底", bg = rgb(AppColorLight.tint),
            floor = 3.0,
            // 被拒候选：同一枚域色的 base 端压同一底——公式必须判得出它不够 3:1，否则 3.0 是摆设。
            decoy = "浅套 AiSpeech base" to rgb(DomainLight.getValue(AppDomain.AiSpeech).base),
        )
        assertPairClears(
            fgName = "深套 AiSpeech deep 字形", fg = aiSpeechDarkDeep,
            bgName = "深套 tint 药片底", bg = rgb(AppColorDark.tint),
            floor = 3.0,
            decoy = "深套装饰线 outline" to rgb(AppColorDark.outline),
        )
    }

    // ---------- 腿 3：选中档标签文字（压底导容器 surface，正文档） ----------

    @Test
    fun `selected label ink clears the body text floor on the light bar surface`() {
        assertPairClears(
            fgName = "浅套 onMint 标签墨", fg = rgb(AppColorLight.onMint),
            bgName = "浅套 surface 底", bg = rgb(AppColorLight.surface),
            floor = 4.5,
            // 被拒候选：旧契约放在这条腿上的那枚 deep（实测 4.016，正文档不达标）。
            decoy = "浅套 AiSpeech deep（旧选中标签墨）" to aiSpeechLightDeep,
        )
    }

    @Test
    fun `selected label ink clears the body text floor on the dark bar surface`() {
        assertPairClears(
            fgName = "深套 AiSpeech deep 标签墨", fg = aiSpeechDarkDeep,
            bgName = "深套 surface 底", bg = rgb(AppColorDark.surface),
            floor = 4.5,
            decoy = "深套装饰线 outline" to rgb(AppColorDark.outline),
        )
    }

    /**
     * 两条文字腿不得合流成「深浅两套同一枚 token」：浅套走中性 `onMint`、深套走域 `deep`，
     * 如果哪天有人把两套都改成同一枚静态值，上面两条用例可能同时绿而依据已经不同——
     * 本例钉住「两套选中标签墨本来就是两个不同的点」，让这条腿的分主题口径显式化。
     */
    @Test
    fun `the two selected label inks are distinct themed points`() {
        val light = rgb(AppColorLight.onMint)
        val dark = aiSpeechDarkDeep
        assertTrue(
            light != dark && light != rgb(AppColorDark.onMint),
            "选中标签墨的深浅两套分叉不见了（浅 #174C44 系 / 深 deep=base 系），本契约的两条腿失去各自依据",
        )
    }

    // ---------- 绑回生效渲染路径 ----------

    @Test
    fun `the recomputed pairs are the ones the bar actually renders`() {
        val bar = sourceOf("AppBottomBar.kt")
        val badge = sourceOf("IconBadge.kt", File("$UI_MAIN_SOURCE_PATH/components"))
        val palette = sourceOf("MintPalette.kt", File(UI_THEME_SOURCE_PATH))

        // 底导容器底：三条腿里两条（未选中档、选中标签）复算的就是这一枚底。
        assertTrue(
            bar.contains("background(MintSurface)"),
            "底导容器底色不再是 MintSurface：本契约复算的底与真实渲染脱钩，须同步改判据（并在报告里留痕）",
        )
        assertTrue(
            bar.contains("else appColors().text2"),
            "未选中墨必须走 appColors().text2（活 token），否则 text2 对比复算对不上生效路径",
        )
        // 选中字形腿：必须经行内徽标，且徽标底是主题 tint、字形墨是域 deep。
        assertTrue(
            bar.contains("InlineIconBadge(icon = tab.glyph, domain = AppDomain.AiSpeech)"),
            "选中字形不再走 InlineIconBadge(AiSpeech)：本契约的 deep-on-tint 图形腿与生效路径脱钩",
        )
        assertTrue(
            badge.contains("background(appColors().tint)"),
            "InlineIconBadge 的底色不再取主题 tint：deep-on-tint 这条图形腿失去真实底",
        )
        // 字形墨只钉「取的是域 deep」这一枚来源，不钉组件的几何字面量（那是 IconBadgeContrastContractTest 的地盘）。
        assertTrue(
            badge.contains("tint = palette.deep"),
            "InlineIconBadge 的字形墨不再来自域 palette.deep：deep 压 tint 的复算对象已不是被渲染的那一枚字形",
        )
        // 选中标签墨：浅套 onMint / 深套沿用 deep 的分主题表达式，且标签真的用它。
        assertTrue(
            bar.contains(SELECTED_LABEL_INK_ANCHOR) && bar.contains("color = labelInk"),
            "选中标签墨不再按主题分叉取 onMint/deep（或标签没接这枚墨）：文字腿 ≥4.5 的复算与生效路径脱钩",
        )
        assertTrue(
            palette.contains("internal val MintSurface: Color") &&
                palette.contains("get() = LocalAppColors.current.surface"),
            "MintSurface 不再桥接 LocalAppColors.surface：浅/深两套 surface 复算失去依据",
        )
    }

    companion object {
        /** 与 `AppBottomBar.kt` 的选中标签取色逐字同源；生产改形状必须连同本契约一起改。 */
        const val SELECTED_LABEL_INK_ANCHOR =
            "if (selected && !LocalIsDark.current) appColors().onMint else tint"
    }
}
