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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 17A Step 3 对比契约（修复轮 1 按评审 I-1 改成「按真实底复算」）。
 *
 * 底导选中态是**三条腿**，各自铺在不同的底上；旧契约把它们压成一条（`deep` 压 `surface`），
 * 于是既绑错了底、又用错了档位。本席逐腿分开：
 *
 *  1. **未选中档**：字形与标签共用同一枚 `text2`，铺在底导容器 `background(MintSurface)`
 *     （= 当前主题 `surface`）上。标签是正文档 → 判 ≥4.5。轻松档实测：浅 5.714、深 7.543。
 *  2. **选中档字形腿**：走 `InlineIconBadge`。**2026-10-31 起**两档都画同一枚插画位图（用户评审 ①），
 *     所以这条腿复算的已不是「屏幕上那枚字形」，而是徽标内部**无母版时的矢量兜底**（`deep` 单层描边
 *     压 `appColors().tint` 药片底）——那枚兜底仍真实存在于源码与 5 枚豁免字形上，于是这条腿由
 *     「选中态字形腿」转为「徽标兜底腿」：判据温度不变（非文本图形档 ≥3.1、同底诱饵负例），
 *     只是它守的东西从「当选字形」变成「徽标退化路径」，不再是零断言。轻松档实测：浅 4.651、深 8.741。
 *     位图本身的自带墨色对比不归本契约：母版是 PNG，颜色不在这两套 token 里，由
 *     `AppIllustrationContractTest`（在盘/尺寸/映射）与真机走查承担。
 *  3. **选中档标签文字腿**：铺在同一枚容器 `surface` 上，是正文档 → 判 ≥4.5。
 *     浅套按 R-14.4 的 AiSpeech 例外取 `appColors().onMint`（**2026-10-29 原型 token 迁移后 onMint
 *     是 brandDeep 别名 #1A7A63，压 surface 5.24**；迁移前旧值压白 9.75，那组数只是历史留痕），
 *     深套沿用 `appDomain(AiSpeech).deep`（深套 deep 与 base 合一）。
 *     旧契约让浅套 `deep` 走这条腿（当时实测 4.016，够不上 4.5）却按图形 3.0 放行 ⇒ 档位用错。
 *     注意：2026-10-27 轻松档把浅套 `AiSpeech.deep` 压到 #1A7A63 后，同一对已升到 **5.240**，
 *     那枚 4.016 诱饵随之失效——现在改用同域色的 `base` 端（#6CCBAB，压 `surface` 只有 1.950）
 *     当诱饵，仍然证明这条腿确实被 4.5 线判过（不是「怎么填都过」）。
 *
 * 上面那些实测数字只是本轮取证留痕，**判据一律是档位线本身**（4.5 / 3.1）加同底诱饵负例，
 * 不把实测值钉进判据（14A 零余量禁令）。轻松档余量：浅文字 +5.251、深文字 +5.245、
 * 浅图形 +1.551、深图形 +5.641。
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

    /** 逐字计数（修复轮 2 / 复评 B）：与 `AppBottomBarContractTest.count` 同一具 LITERAL 正则机制，不新造方言。 */
    private fun count(hay: String, needle: String): Int = Regex(needle, RegexOption.LITERAL).findAll(hay).count()

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
            // 2026-10-29 原型 token 迁移：浅套 text3 从旧值换到 #65756C 后压白升到 4.87，
            // 不再能当「同式不合格」的诱饵（它现在本身就是合格的三级墨）。诱饵改成装饰线
            // `outline`（压白 1.29）——与深套腿同一枚，证明 4.5 闸在这条底上确实判得动。
            decoy = "浅套装饰线 outline" to rgb(AppColorLight.outline),
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

    // ---------- 腿 2：选中档字形（deep 压 InlineIconBadge 的 tint 药片底，图形档 ≥3.1） ----------

    @Test
    fun `selected ai_speech glyph clears 3 to 1 on the inline badge tint backdrop`() {
        assertPairClears(
            fgName = "浅套 AiSpeech deep 字形", fg = aiSpeechLightDeep,
            bgName = "浅套 tint 药片底", bg = rgb(AppColorLight.tint),
            floor = 3.1,
            // 被拒候选：同一枚域色的 base 端压同一底——公式必须判得出它不足 ≥3.1 线，否则 3.1 是摆设。
            decoy = "浅套 AiSpeech base" to rgb(DomainLight.getValue(AppDomain.AiSpeech).base),
        )
        assertPairClears(
            fgName = "深套 AiSpeech deep 字形", fg = aiSpeechDarkDeep,
            bgName = "深套 tint 药片底", bg = rgb(AppColorDark.tint),
            floor = 3.1,
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
            // 被拒候选：同域色的 `base` 浅端压同一底（1.950）——轻松档把 `deep` 修到达标之后，
            // 就只剩这一端还站在 4.5 线外，诱饵负例改由它承担。
            decoy = "浅套 AiSpeech base（域色浅端）" to rgb(DomainLight.getValue(AppDomain.AiSpeech).base),
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
        // 选中字形腿：必须经行内徽标，且徽标底是主题 tint、字形兜底墨是域 deep。
        // 2026-10-31 起两档都经同一枚徽标（`backdrop = selected` 只决定带不带药片底、alpha 决定淡出），
        // 故锚点取调用前缀 + 两枚状态实参：旧形「选中才套徽标」被 `backdrop = selected` 判出来。
        assertTrue(
            bar.contains("InlineIconBadge(icon = tab.glyph, domain = AppDomain.AiSpeech"),
            "选中字形不再走 InlineIconBadge(AiSpeech)：本契约的 deep-on-tint 图形腿与生效路径脱钩",
        )
        assertTrue(
            bar.contains("backdrop = selected"),
            "徽标必须两档共用、只由 backdrop 决定带不带药片底：deep-on-tint 这条腿复算的底就在那枚药片上",
        )
        assertTrue(
            badge.contains("background(appColors().tint)"),
            "InlineIconBadge 的底色不再取主题 tint：deep-on-tint 这条图形腿失去真实底",
        )
        // 字形墨锚（修复轮 2 / 复评 B）：旧 contains 只钉「tint = palette.deep」字面，而 IconBadge.kt 里有两处可满足
        // （:146 EntryIconBadge 弱档 22dp、:173 InlineIconBadge 17dp）——:173 被换成 palette.base 时旧锚靠 :146 存活仍绿。
        // 升格为 InlineIconBadge 独占的组合形状「域 deep + 17dp 度量」在剥注释源码里**恰一处**：
        // 被渲染的那枚字形才作数，弱档那处的存亡不能给行内徽标的墨垫背。
        assertEquals(
            1, count(badge, BADGE_GLYPH_INK_ANCHOR),
            "InlineIconBadge 的字形墨不再是域 palette.deep 的 17dp 独占形状（计数非恰一处）：deep 压 tint 的复算对象已不是被渲染的那一枚字形",
        )
        // 负控（反空转）：EntryIconBadge 弱档的 22dp 单独形状必须判不到这枚锚；deep→base 的偷换形状同样必须判不到，
        // 否则「:173 换成 base 必红」只是愿望；正向形状自命中。
        assertEquals(
            0,
            count("AppGlyph(icon = icon, tint = palette.deep, size = 22.dp, layer = AppGlyphLayer.StrokeOnly)", BADGE_GLYPH_INK_ANCHOR),
            "反空转失控：EntryIconBadge 弱档形状竟满足 InlineIconBadge 独占锚，锚点没钉到度量段",
        )
        assertEquals(
            0,
            count("AppGlyph(icon = icon, tint = palette.base, size = 17.dp, layer = AppGlyphLayer.StrokeOnly)", BADGE_GLYPH_INK_ANCHOR),
            "反空转失控：deep→base 的偷换形状竟满足独占锚，「:173 换成 base 必红」不成立",
        )
        assertEquals(1, count(BADGE_GLYPH_INK_ANCHOR, BADGE_GLYPH_INK_ANCHOR), "反空转失控：正向形状不满足自身锚点，锚点写歪了")
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

        /** InlineIconBadge 独占的字形墨组合形状（修复轮 2 / 复评 B）：域 `deep` 墨 + 17dp 度量，钉到「哪一枚调用点」而非「字面出现」。 */
        const val BADGE_GLYPH_INK_ANCHOR = "tint = palette.deep, size = 17.dp"
    }
}
