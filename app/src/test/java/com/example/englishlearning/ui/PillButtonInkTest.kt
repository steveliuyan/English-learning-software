package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.components.glass.PillInk
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.components.glass.pillButtonInk
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.DomainLight
import com.example.englishlearning.ui.theme.DomainPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 14A-i（R-14.1 + R-14.4）：`PillButton` 的三档容器-前景-描边配对是一张**纯表** `pillButtonInk`。
 *
 * 与 `EntryRowInkTest` 同口径：connected 侧读不到颜色（Task 9 实测口径），所以配对合法性的唯一证据面
 * 是这张 JVM 表——测试直接喂两套 `AppColorSet` + `DomainPalette`，不经 Compose 运行时复算 WCAG 相对亮度；
 * 生产侧 `PillButton` 只读这张表，不在组件里凑色（组件真用这张表由本文件末尾的源码门承重）。
 *
 * 比值一律当场复算：复用 `SourceRatchet.kt` 的 int 域 `ratio` 实现，测试端只加 Color→int 取样壳。
 * 阈值口径：
 *  - 前景压容器／渐变每一端／页面底：正文 AA `>= 4.5`；
 *  - 禁用态：WCAG 1.4.3 豁免正文阈值，本契约按 `>= 3.0` 断「整枚没糊成同色」——刻意不写 4.1，
 *    浅套禁用比值只剩 0.046 余量（与 14B 那条零余量高度代理同病）；
 *  - 描边**不宣称对比**：本套调色板没有任何达标发丝线（浅 1.178 / 深 1.212，批 1 `EntryRow` 行间线同一口径），
 *    所以本文件只断前景，不给描边编一条会误伤的阈值。
 */
class PillButtonInkTest {

    /** 一套主题的三元组：中性 token 集 + 域色表 + `LocalIsDark` 取值；逐字对齐 `MintGlassTheme` 的装配。 */
    private data class ThemeSet(
        val label: String,
        val set: AppColorSet,
        val palettes: Map<AppDomain, DomainPalette>,
        val isDark: Boolean,
    )

    private val themeSets = listOf(
        ThemeSet("浅套", AppColorLight, DomainLight, false),
        ThemeSet("深套", AppColorDark, DomainDark, true),
    )

    private val allDomains = AppDomain.entries.toList()
    private val nonBrandDomains = AppDomain.entries.filter { it != AppDomain.AiSpeech }
    private val solidStyles = listOf(PillStyle.Primary, PillStyle.Secondary, PillStyle.Text)

    /** 品牌前景那枚主题无关深墨：按值比对（`AppPalette.OnMintPrimary` 的取值），不经生产符号。 */
    private val brandForeground: Color = Color(0xFF174C44)

    private fun rgbOf(color: Color): Int = color.toArgb() and 0xFFFFFF

    private fun ratioOf(fg: Color, bg: Color): Double = ratio(rgbOf(fg), rgbOf(bg))

    private fun inkOf(theme: ThemeSet, style: PillStyle, domain: AppDomain, enabled: Boolean = true): PillInk =
        pillButtonInk(style, domain, enabled, theme.set, theme.palettes.getValue(domain), theme.isDark)

    /**
     * 前景压「它真实落在的底」：有渐变压渐变每一端；否则不透明容器压容器；透明容器的真实底是页面，
     * 于是 `surface` 与 `background` 两条都算。
     */
    private fun assertForegroundClears(
        subject: PillInk,
        theme: ThemeSet,
        site: String,
        threshold: Double,
    ) {
        val backdrops: List<Color> = when {
            subject.gradient != null -> subject.gradient
            subject.container != Color.Transparent -> listOf(subject.container)
            else -> listOf(theme.set.surface, theme.set.background)
        }
        backdrops.forEach { backdrop ->
            val value = ratioOf(subject.foreground, backdrop)
            assertTrue(
                value >= threshold,
                "$site（${theme.label}）前景压 ${rgbOf(backdrop).toString(16)} 只有 %.2f:1，低于 %.1f".format(value, threshold)
            )
        }
    }

    /**
     * R-14.1 ① + R-14.4 第一条：非品牌域 Primary 容器**收成纯色 `palette.deep`**，前景按套分叉
     * （浅套白字、深套 `set.surface`）。
     *
     * 深套前景不能取 `Color.White`：R-14.1 ① 那句「前景保持白色」是拿浅套静态 `DomainColors` 推的，
     * 成立前提是容器为浅套深值；改读 `appDomain()` 后深套容器是**亮色**，白字压它 1.692~2.790 全线崩。
     * 也不能取 `set.text1`（深套 `text1` 是亮墨，压亮容器只有 1.5~2.4）。
     * 等值 pin 与比值 pin 双承重：只写比值则「换成另一枚恰好也达标的墨」看不见，只写等值则令牌漂移看不见。
     */
    @Test
    fun primary_non_brand_is_solid_deep_and_foreground_meets_aa_in_both_sets() {
        themeSets.forEach { theme ->
            nonBrandDomains.forEach { domain ->
                val palette = theme.palettes.getValue(domain)
                val subject = inkOf(theme, PillStyle.Primary, domain)
                val site = "Primary/$domain"
                assertEquals(palette.deep, subject.container, "$site（${theme.label}）容器必须是纯色 palette.deep")
                assertEquals(null, subject.gradient, "$site（${theme.label}）不许再出现 base→deep 渐变（R-14.1 ①）")
                assertEquals(null, subject.border, "$site（${theme.label}）主按钮不画描边")
                val expectedForeground = if (theme.isDark) theme.set.surface else Color.White
                assertEquals(
                    expectedForeground,
                    subject.foreground,
                    "$site（${theme.label}）前景必须按套分叉：浅套白字、深套 set.surface（R-14.4）",
                )
                assertForegroundClears(subject, theme, site, 4.5)
            }
        }
    }

    /**
     * R-14.1 ① 末句：品牌分支（Primary + AiSpeech）现状已合规，本席**一行不改**。
     *
     * 三端逐个 ≥4.5，且两套取值逐字相同——这就是「这枚前景必须主题无关」的自证：不许改成 `set.onMint`
     * （深套 `onMint = #DFF6EC` 是亮墨，压薄荷渐变会塌到 1.x）。
     */
    @Test
    fun every_gradient_stop_of_the_brand_branch_meets_aa_with_its_ink() {
        val light = inkOf(themeSets.first(), PillStyle.Primary, AppDomain.AiSpeech)
        val dark = inkOf(themeSets.last(), PillStyle.Primary, AppDomain.AiSpeech)
        assertEquals(light, dark, "品牌渐变浅深两套同三段：取值一旦分叉，说明有人把品牌墨改成了主题墨")
        assertEquals(Color.Transparent, light.container, "品牌档容器保持透明，底色由渐变承载（Surface 无 brush 重载）")
        assertEquals(null, light.border, "品牌档不画描边")
        val stops = light.gradient ?: error("品牌分支必须携带渐变")
        assertEquals(3, stops.size, "品牌渐变三端（AppleMintStart/Middle + 裸 hex 末端）少一端即配方漂移")
        assertEquals(brandForeground, light.foreground, "品牌前景必须仍是那枚主题无关深墨")
        stops.forEach { stop ->
            val value = ratioOf(light.foreground, stop)
            assertTrue(
                value >= 4.5,
                "品牌渐变某一端压前景只有 %.2f:1，不足正文 AA 4.5（stop=${rgbOf(stop).toString(16)}）".format(value)
            )
        }
    }

    /**
     * **逐端通用闸**（防「渐变回潮」的承重）：七域 × 两套 × 三档，凡 `gradient != null` 就断
     * 前景压**每一端** ≥4.5。
     *
     * 为什么不能只断容器：有人把 B 档容器换回 `base→deep` 双色渐变、`container` 仍写 `Transparent`
     * （或写 `deep` 却把真底交给 brush），逐容器断言看不见亮端——白字压浅套 `base` 端 Learn 最好也只有
     * 3.647。本闸对任何一处渐变生效，与它出现在哪一档、哪个域无关。
     */
    @Test
    fun no_ink_ships_a_gradient_that_its_foreground_cannot_survive() {
        themeSets.forEach { theme ->
            solidStyles.forEach { style ->
                allDomains.forEach { domain ->
                    val subject = inkOf(theme, style, domain)
                    val stops = subject.gradient
                    if (stops != null) {
                        stops.forEachIndexed { index, stop ->
                            val value = ratioOf(subject.foreground, stop)
                            assertTrue(
                                value >= 4.5,
                                "$style/$domain（${theme.label}）渐变第 $index 端压前景只有 %.2f:1，不足正文 AA 4.5"
                                    .format(value)
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * R-14.1 ② + R-14.4 第二条：Secondary / Text 档前景统一取 `palette.deep`
     * （深套 `base == deep`，所以「浅 deep / 深 base」的分叉塌成一条），唯一例外是**浅套 AiSpeech**
     * 取 `set.onMint`——它的 `deep` 压白只有 4.016，不达标。
     *
     * 例外分支用等值 pin 钉死：若哪天有人把它改回 `palette.deep` 却指望别的断言蒙过去，这里当场红。
     * 透明容器（Text 档）的真实底是页面，所以 `surface` 与 `background` 两条底都算。
     * Secondary 档容器 pin `set.surface`、描边 pin `set.outlineSoft`（线宽仍在组件里，不属墨色表）。
     */
    @Test
    fun secondary_and_text_foreground_clears_aa_on_both_page_backdrops() {
        themeSets.forEach { theme ->
            listOf(PillStyle.Secondary, PillStyle.Text).forEach { style ->
                allDomains.forEach { domain ->
                    val palette = theme.palettes.getValue(domain)
                    val subject = inkOf(theme, style, domain)
                    val site = "$style/$domain"
                    val expectedForeground =
                        if (!theme.isDark && domain == AppDomain.AiSpeech) theme.set.onMint else palette.deep
                    assertEquals(
                        expectedForeground,
                        subject.foreground,
                        "$site（${theme.label}）前景必须取 palette.deep，浅套 AiSpeech 例外取 set.onMint（R-14.4）",
                    )
                    assertEquals(null, subject.gradient, "$site（${theme.label}）这两档不携带渐变")
                    if (style == PillStyle.Secondary) {
                        assertEquals(theme.set.surface, subject.container, "$site（${theme.label}）容器必须随套取 surface")
                        assertEquals(
                            theme.set.outlineSoft,
                            subject.border,
                            "$site（${theme.label}）描边必须随套取 outlineSoft（浅套取值与 Separator 逐字相同）",
                        )
                    } else {
                        assertEquals(Color.Transparent, subject.container, "$site（${theme.label}）文字档容器保持透明")
                        assertEquals(null, subject.border, "$site（${theme.label}）文字档不画描边")
                    }
                    assertForegroundClears(subject, theme, site, 4.5)
                    val onSurface = ratioOf(subject.foreground, theme.set.surface)
                    val onBackground = ratioOf(subject.foreground, theme.set.background)
                    assertTrue(
                        onSurface >= 4.5 && onBackground >= 4.5,
                        "%s（${theme.label}）前景压页面两底未双达标（surface %.2f / background %.2f）"
                            .format(site, onSurface, onBackground)
                    )
                }
            }
        }
    }

    /**
     * R-14.4 第三条：禁用态一并主题化（本席把 `AppPalette.TextSecondary`/`Surface`/`Separator` 三枚静态读点清掉）。
     *
     * 前景统一 `set.text2`；容器 Primary → `set.tint2`、Secondary → `set.surface`、Text → 透明（真实底是页面）；
     * 描边只给 Secondary。禁用态按 WCAG 1.4.3 豁免正文 4.5，本契约只断 `>= 3.0`，防「整枚糊成同色」。
     */
    @Test
    fun disabled_is_inactive_but_not_washed_out() {
        themeSets.forEach { theme ->
            solidStyles.forEach { style ->
                allDomains.forEach { domain ->
                    val subject = inkOf(theme, style, domain, enabled = false)
                    val site = "disabled $style/$domain"
                    assertEquals(theme.set.text2, subject.foreground, "$site（${theme.label}）禁用前景必须随套取 text2")
                    assertEquals(null, subject.gradient, "$site（${theme.label}）禁用态不许留渐变")
                    val expectedContainer = when (style) {
                        PillStyle.Primary -> theme.set.tint2
                        PillStyle.Secondary -> theme.set.surface
                        PillStyle.Text -> Color.Transparent
                    }
                    assertEquals(expectedContainer, subject.container, "$site（${theme.label}）禁用容器按档分派")
                    val expectedBorder = if (style == PillStyle.Secondary) theme.set.outlineSoft else null
                    assertEquals(expectedBorder, subject.border, "$site（${theme.label}）只有 Secondary 禁用态保留描边")
                    assertForegroundClears(subject, theme, site, 3.0)
                }
            }
        }
    }

    /**
     * R-14.1 ③ 的两条**被拒候选** + 一枚反空转诱饵，读的是生产实际消费的浅套域色源 `DomainLight`
     * （不读 `AppPalette`/`DomainColors` 静态源：那两套漂移不代表本组件的取值漂移）。
     *
     * 三条都是「若达标即红」的裁决依据承重：令牌一旦漂移让这些候选翻身，本条红并提示裁决重议，
     * 而不是让决策表悄悄失去理由。
     */
    @Test
    fun rejected_candidates_are_pinned_and_turn_red_if_tokens_drift() {
        val whiteOnReviewBase = ratioOf(Color.White, DomainLight.getValue(AppDomain.Review).base)
        assertTrue(
            whiteOnReviewBase < 4.5,
            "裁决依据失效：白字压浅套 Review base 已升到 %.2f:1（≥4.5），「容器不许含 base」需重议".format(whiteOnReviewBase)
        )
        val aiSpeechDeepOnSurface = ratioOf(DomainLight.getValue(AppDomain.AiSpeech).deep, AppColorLight.surface)
        assertTrue(
            aiSpeechDeepOnSurface < 4.5,
            "裁决依据失效：浅套 AiSpeech deep 压 surface 已升到 %.2f:1（≥4.5），Secondary/Text 的 set.onMint 例外分支可撤"
                .format(aiSpeechDeepOnSurface)
        )
        // 诱饵（仿 EntryRowInkTest 的反空转写法）：七域里 base 端的最好成绩。它若翻身，说明整套 base 漂移，
        // 上面那条「代表候选」可能仍卡在线下——这条把整套一起看住。
        val whiteOnLearnBase = ratioOf(Color.White, DomainLight.getValue(AppDomain.Learn).base)
        assertTrue(
            whiteOnLearnBase < 4.5,
            "合成负例被放行：浅套 Learn base 压白字竟也达 %.2f:1，亮度公式或阈值失控".format(whiteOnLearnBase)
        )
    }

    /**
     * 源码门：证明**组件真在用这张表**，纯函数不是 dead code，也不许在组件里再算第二遍颜色。
     *
     * 计数与形状（全部先剥注释，走 `uiComponentSource` 唯一读法）：
     *  - `.base` 0 处：组件彻底不读域色 base（容器禁令在源码层的影子）；
     *  - `DomainColors.` 0 处：形参已从静态色对迁成 `AppDomain`；
     *  - `AppPalette.` 恰 1 处：品牌前景那枚刻意主题无关的读点（归属债，批 3 收口，见 R-14.4）；
     *  - `Color(0x` 恰 1 处：品牌渐变末端裸 hex，与 `RawColorLiteralRatchetTest` 的 seed 同一枚，不许增加；
     *  - `fun pillButtonInk(` 恰 2 处（纯表 + 组合期壳），`pillButtonInk(` 恰 4 处（2 定义 + 壳转调 + 组件取用）；
     *  - 壳必须把 `appColors()` / `appDomain(domain)` / `LocalIsDark.current` 三样一起转给纯表，且任何地方都不许
     *    把 `isDark` 写成布尔常量——否则两套分叉在组合期塌回浅套，JVM 表再绿也没用；
     *  - `PillButton`（本文件最后一个顶层 fun）体内恰一次取用这张表、零处自凑色；
     *  - **组件体消费点（修复 I-1 / 评审 M3）**：只钉「取用一次」不钉「消费形状」时，组件可一边把纯表算得漂亮、
     *    一边在 `Surface` 上写死墨色（`contentColor = ink.foreground` → `Color.White`），全部 JVM 门绿。故追加四条
     *    正向判据，钉死容器/前景/描边/渐变必须逐字写 `color = ink.container` / `contentColor = ink.foreground` /
     *    `border = ink.border?.let` / `val gradient = ink.gradient?.let`；再加反向判据灭掉「命名色常量与 Color 构造器凑色」
     *    （`Color.` 成员访问与 `Color(` 构造调用，后者为 R-14A-i.1 扩面）与「组件侧自挂品牌渐变」（`BrandMintGradientStops`）
     *    这两族绕过路径。
     *    被钉形状只存在于被测源码 `GlassButton.kt` 的代码行（`stripComments` 后），测试文件注释/消息里的这些字样不参与计数。
     */
    @Test
    fun glass_button_takes_its_ink_only_from_the_pure_table() {
        val source = uiComponentSource("GlassButton.kt", "/components/glass/")
        assertEquals(0, countMatches(source, Regex("""\.base\b""")), "GlassButton.kt 不许再读域色 base（容器禁令）")
        assertEquals(0, countMatches(source, Regex("""\bDomainColors\.[A-Za-z0-9_]+""")), "静态色对读点必须清零：形参已迁 AppDomain")
        assertEquals(1, countMatches(source, Regex("""\bAppPalette\.[A-Za-z0-9_]+""")), "AppPalette. 只许剩品牌前景一枚（R-14.4 归属债）")
        assertEquals(1, countMatches(source, Regex("""Color\(0x""")), "裸 hex 只许品牌渐变末端那一枚，与裸 hex 棘轮 seed 同值")
        assertEquals(2, countMatches(source, Regex("""fun pillButtonInk\(""")), "纯表 + 组合期壳各一处定义")
        assertEquals(4, countMatches(source, Regex("""pillButtonInk\(""")), "2 定义 + 壳转调 + PillButton 取用，多一处即在组件里凑色")
        assertEquals(
            1,
            countMatches(
                source,
                Regex("""pillButtonInk\(\s*style,\s*domain,\s*enabled,\s*appColors\(\),\s*appDomain\(domain\),\s*LocalIsDark\.current"""),
            ),
            "组合期壳必须把三样主题输入一起转给纯表（把 isDark 写成常量即判红）",
        )
        assertEquals(0, countMatches(source, Regex("""isDark\s*=\s*(?:true|false)""")), "isDark 不许被写成布尔常量")
        val pillButtonBody = source.substringAfter("fun PillButton(")
        assertEquals(1, countMatches(pillButtonBody, Regex("""pillButtonInk\(""")), "PillButton 体内必须恰一次取用这张表")
        assertEquals(
            0,
            countMatches(pillButtonBody, Regex("""\bAppPalette\.|\bDomainColors\.|Color\(0x|appColors\(\)|appDomain\(|LocalIsDark""")),
            "PillButton 体内不许出现第二处取色：容器/前景/描边/渐变一律来自 ink",
        )
        // 修复 I-1（评审 M3 实测全绿）：上面只钉「体内取用一次 + 零处自凑色符号」，不钉组件体对 ink 五个成员的消费点。
        // 于是有人可把 contentColor = ink.foreground 改成 contentColor = Color.White（命名色常量、零字面量、零 token 读取），
        // 既绕开正向「取用一次」，也不触任何既有正则——纯表算得再对，Surface 却写死墨色。以下四条正向钉死消费形状。
        assertEquals(1, countMatches(pillButtonBody, Regex("""color = ink\.container""")), "Surface 容器色必须逐字接自 ink.container（改自其它来源即绕过纯表）")
        assertEquals(1, countMatches(pillButtonBody, Regex("""contentColor = ink\.foreground""")), "Surface 前景色必须逐字接自 ink.foreground（写成 Color.White 等常量即判红，封 M3）")
        assertEquals(1, countMatches(pillButtonBody, Regex("""border = ink\.border\?\.let""")), "Surface 描边必须逐字接自 ink.border?.let（改成 null 或别的来源即判红）")
        assertEquals(1, countMatches(pillButtonBody, Regex("""val gradient = ink\.gradient\?\.let""")), "组件渐变必须逐字接自 ink.gradient?.let（改成常亮或组件侧自挂品牌渐变即判红）")
        // 反向兜底：即便有人避开上面的字面形状，也不许在组件体里落下命名色常量/Color 构造器凑色/自挂品牌渐变这几族绕过路径。
        // R-14A-i.1（14A-i 窄复评实测）：原判据只写 Color\.[A-Za-z]，漏拦 Color(0.5f,0.5f,0.5f) 这类浮点构造器凑色——
        // 判据扩为同时拦一切 Color( 构造调用（生产组件体内零处 Color(，收紧无既有命中）。
        assertEquals(0, countMatches(pillButtonBody, Regex("""Color\.[A-Za-z]|Color\(""")), "组件体不得出现任何 Color.<命名色> 常量或 Color( 构造调用：White/Transparent 与浮点参数凑色都算绕过纯表")
        assertEquals(0, countMatches(pillButtonBody, Regex("""BrandMintGradientStops""")), "组件体不得自挂 BrandMintGradientStops：品牌渐变只能来自 ink.gradient")
    }
}
