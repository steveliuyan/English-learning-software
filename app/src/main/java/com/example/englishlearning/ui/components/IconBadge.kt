package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.LocalIsDark
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain
import kotlin.math.roundToInt

/**
 * B 案图标的两种呈现：`EntryIconBadge` 一族的 ≥40dp 入口容器（40dp 档，内分实底渐变与弱档 tint
 * 两档强度，见 [EntryIconTone]）与 30dp 行内徽标（tint 底 + 单层描边字形）。这里的「入口容器」专指
 * 这一族，不含页头 `.back` 那种 surface 底 + 1dp outlineSoft 的容器（几何见稿 `mockups.html:119-122`，
 * 由 Task 9 `AppScreenHeader` 承载）。
 *
 * 本文件的颜色数学与容器几何都是契约对象，不是随手写法：`IconBadgeContrastContractTest` 用测试端
 * 独立实现的 int 域混色复算同一端点，并把容器尺寸、裁角、描边、字形度量（入口 22dp / 行内 17dp）
 * 与层开关按源码文本钉死。
 * 域色一律经 [appDomain] 取（`AppColors.kt` 的两套映射），中性色一律经 [appColors] 取，
 * 本文件不出现任何裸 hex。
 */

/**
 * 入口容器渐变亮端在 `deep` 与 `base` 之间的插值比重：视觉稿的容器底是
 * `linear-gradient(140deg, color-mix(in srgb, var(--d) 65%, var(--db)), var(--d))`，
 * 稿里 `--d` 取域 deep、`--db` 取域 base，即亮端 = deep 混 35% base。
 */
private const val GRADIENT_START_FRACTION = 0.35

/**
 * 入口容器渐变的亮端 = [deep] 混 35% [base]（[GRADIENT_START_FRACTION]）。
 *
 * 为什么不是 `Color.mix`：本工程的 compose-ui 版本没有 `mix`；能用的 `Color.lerp` 先把两端换算到
 * Oklab 再插值，与视觉稿的 `color-mix(in srgb, …)` 不是同一个口径，端点会偏，白字形的 3:1 复算就
 * 失去依据。所以按计划 Step 2 的授权手写 ARGB 逐通道线性插值（alpha 一并插值：两套域色都不透明，
 * 结果仍不透明；半透明端点不会被强行改成实心）。
 *
 * 计算在字节域做，与测试端那份独立实现（`IconBadgeContrastContractTest.mixArgb`）逐通道可比：
 * 两端各算一次还得同一个点，这条契约才算立住。
 */
fun gradientStartForDomain(deep: Color, base: Color): Color =
    Color(mixArgb(deep.toArgb(), base.toArgb(), GRADIENT_START_FRACTION))

/** 逐通道（含 alpha）线性插值后重新打包成 `0xAARRGGBB`。 */
private fun mixArgb(from: Int, to: Int, fraction: Double): Int {
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * fraction).roundToInt()
    }
    return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/**
 * 入口容器的准入门槛：两层豁免表（`StrokeOnlyIcons`，决策文档裁决 4）里的字形都是开放折线，
 * 没有可闭合的内部区域，塞进 `EntryIconBadge` 一族的域渐变入口容器只会得到一枚「看起来缺了层」的字形，
 * 故只作行内/尾部指示；页头 `.back` 的 surface 容器不属这一族，不受本函数管辖。
 * 判定抽成纯函数是为了让这条规则能在 JVM 单测里双向复算（`EntryIconBadge` 用它做 `require`）。
 */
fun allowsInEntryContainer(icon: AppIcon): Boolean = icon !in StrokeOnlyIcons

/**
 * 入口容器的两档强度（裁决 R-10.2）：稿 `.ic`（实底域渐变 + 白墨）与 `.ic-tint`（同 40dp 容器、
 * 底 = 共享中性 `tint` 令牌、字形 = `deep` 单层描边、**无描边高光**）是同族两档。
 *
 * Task 27（裁决 22-2）把弱档底色从「每域各混 16% surface」改成共享的 `appColors().tint`：
 * 逐域混色意味着七个域有七个近似却不同的浅底，同屏入口行读成「九块彩色」；收敛后同组行共用同一底色，
 * 域色只染字形。`.ic` 实底渐变档保留在枚举里，但退出入口行族（今日计划六行全走 Tint）。
 *
 * 加档位而不是再造组件：弱档另起一份容器 = 让批 2 手搓第二份 40dp 容器，正撞计划 Task 12 的组件份数棘轮。
 * 两档共用同一枚外层 `Box`（`size(40.dp)` 与 `clip(AppShape.Icon)` 各只写一遍），差别只在底色、
 * 是否描玻璃高光、以及字形层开关——这也是 `IconBadgeContrastContractTest` 把容器计数钉成「各恰一处」、
 * 把入口字形调用钉成「恰两处」的原因。
 */
enum class EntryIconTone { Gradient, Tint }

/**
 * `EntryIconBadge` 一族的入口容器（40dp 档，两档强度见 [EntryIconTone]）：
 *
 * - [EntryIconTone.Gradient]（稿 `.ic`，默认档）：域渐变底（亮端 = deep 混 35% base，暗端 = deep）
 *   + 两层字形 + 1dp 玻璃高光内描边；
 * - [EntryIconTone.Tint]（稿 `.ic-tint`）：底 = 共享中性 `tint` 令牌（`appColors().tint`，深浅两套随主题翻；
 *   Task 27 / 裁决 22-2 的口径，不再是逐域 `lerp(surface, deep, 0.16)`）+ `deep` 单层描边字形，
 *   **不描 1dp 玻璃高光边**（稿 `.ic-tint` 无描边，高光边属于 `.ic` 实底档）。
 *
 * 字形度量按稿 `.ic svg { width:22px; height:22px }`（`mockups.html:153`）取 22dp（裁决 R-10.3，
 * 与本任务同步把 Task 7 出厂的 24dp 对齐；`EntryIconBadge` 目前零生产消费方，改动不波及任何屏）。
 *
 * 字形墨色分主题两套（实底档）：浅套取白，深套取中性墨 `AppColorDark.onBrand`。这不是随手挑的——深套域色在
 * 深色下就是浅底（`DomainDark.deep` 与 base 合一），白描边压它不达非文本 3:1 线，故视觉稿在深色下
 * 也把白描边改成墨色。具体比值不抄在这里（与 `AppColors.kt` 同一口径：抄进注释就会随取值漂移），
 * 两条配对各由 `IconBadgeContrastContractTest` 当场按相对亮度复算并钉住 3:1。弱档那对「`deep` 字形压
 * 共享 `tint` 底」同样由该契约按 7 域 × 2 主题逐对复算（浅套最低是 `AiSpeech`、深套最低是 `Danger`，
 * 都在 3:1 线以上；数值只写进断言消息，这里不抄）。
 * Task 27 之后弱档底换成共享 `tint`（浅 `#DDF7E8` / 深 `#1C2C26`）；
 * 旧「16% 混色」与活 token 的差异由 `IconBadgeContrastContractTest` 的反空转断言钉住。
 *
 * 开头那条 `require` 是豁免表的执行点：表里的开放折线（返回、箭头、关闭、勾选一类）没有可闭合的
 * 内部区域，放进这一族容器只会得到一枚「看起来缺了层」的字形，属于设计层面的误用，直接拦死。
 * **两档都过这道门**（R-10.2）：弱档也不承接豁免枚——豁免枚只在行内/尾部出现。
 * 它管的只有本函数这一族：页头 `.back` 那种 surface 底 + 1dp outlineSoft 的容器不经过这条 `require`，
 * 字形度量由 `AppScreenHeader` 自己按稿承载。
 */
@Composable
fun EntryIconBadge(
    icon: AppIcon,
    domain: AppDomain,
    modifier: Modifier = Modifier,
    tone: EntryIconTone = EntryIconTone.Gradient,
) {
    require(allowsInEntryContainer(icon)) {
        "$icon 属两层豁免表，只作行内/尾部指示，不得进入 ≥40dp 入口容器"
    }
    val palette = appDomain(domain)
    val colors = appColors()
    val tintTone = tone == EntryIconTone.Tint
    // 两档共用同一枚外层容器：底色与描边的差异收进这一条链，尺寸与裁角只写一遍（R-10.2）。
    val backdropModifier = if (tintTone) {
        Modifier.background(appColors().tint)
    } else {
        Modifier
            .background(
                Brush.linearGradient(
                    listOf(gradientStartForDomain(palette.deep, palette.base), palette.deep)
                )
            )
            .border(1.dp, AppPalette.GlassHighlight, AppShape.Icon)
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(AppShape.Icon)
            .then(backdropModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (tintTone) {
            AppGlyph(icon = icon, tint = palette.deep, size = 22.dp, layer = AppGlyphLayer.StrokeOnly)
        } else {
            val ink = if (LocalIsDark.current) colors.onBrand else Color.White
            AppGlyph(icon = icon, tint = ink, size = 22.dp)
        }
    }
}

/**
 * 30dp 行内徽标：主题 tint 底 + 17dp 单层描边字形（域 `deep`，填充层不显示）。
 *
 * 两种呈现的分工就在这里：行内已有文案承载语义，容器只是定位辅助，浅底上再铺一层按主题给的
 * 实心填充（`DuotoneFillAlpha`）只会把字形糊成一坨；能达标的配对是「域 `deep` 单层描边压 tint」，
 * 这一对由 `IconBadgeContrastContractTest` 当场按相对亮度复算，注释不抄比值（抄了就会随取值漂移）。
 * 底色走 `appColors().tint`（深浅两套随主题翻），不写死浅绿字面量；
 * 容器圆角走 `AppShape.Inline`（视觉稿行内 chip 档），与入口的 `AppShape.Icon` 是两档不同数值，不是同一档。
 */
@Composable
fun InlineIconBadge(icon: AppIcon, domain: AppDomain, modifier: Modifier = Modifier) {
    val palette = appDomain(domain)
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(AppShape.Inline)
            .background(appColors().tint),
        contentAlignment = Alignment.Center,
    ) {
        AppGlyph(icon = icon, tint = palette.deep, size = 17.dp, layer = AppGlyphLayer.StrokeOnly)
    }
}
