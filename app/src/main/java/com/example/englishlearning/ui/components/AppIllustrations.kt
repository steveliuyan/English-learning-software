package com.example.englishlearning.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.englishlearning.R

/**
 * 插画渲染出口的语义锚点（设备侧判据用）：位图是纯装饰（`contentDescription = null`），
 * 语义树里没有别的抓手，所以「这一枚徽标到底画的是插画、还是静默退回了 24×24 线框矢量」
 * 只能靠这个 tag 在 `androidTest` 里判——像素取样分不清「插画」与「域色描边」。
 */
const val IllustratedIconTag: String = "illustrated_icon"

/**
 * 「插画档」图标资源表（2026-10-30）：把 `docs/design/icon-master/` 的 calm 母版位图直接接进运行态。
 *
 * 为什么不再走矢量重绘：母版的体积感来自多层明暗、内高光与圆角厚边，24×24 三层 `ImageVector`
 * 只有「半透明底幅 + 一枚强调色 + 2px 骨架」三档明度，把这套画法压进 24 网格后必然退化成线框
 * （2026-10-29 的三枚样板即此结论，已由用户否决）。所以插画档不是「矢量再画一遍」，而是把
 * 母版去底后的原图按 36dp 直接画出来：几何与层次一笔不改。
 *
 * 与矢量的分工：`StrokeOnlyIcons` 的 5 枚（Back / Chevron / Close / Check）没有母版，仍走
 * [AppGlyph]；其余 28 枚与 `icon-master/README.md` 的映射表一一对应，本表即该表在代码里的唯一副本。
 *
 * 资源规格：源图 1024×1024（`#F6F9F6` 底），由离线脚本四角泛洪去底 → 按主体包围盒裁切 → 补 6%
 * 留白 → 面积平均降采样到 256×256，落 `res/drawable-xhdpi/`。归一化到「包围盒最大边」而不是原图
 * 画布，是为了让细长字形（话筒）与扁宽字形（停止）在同一枚 40dp 容器里读成同等视觉重量。
 */
object AppIllustrations {

    /**
     * `AppIcon` → 去底位图。键集 = 全 33 枚减去 [StrokeOnlyIcons]，由 `AppIllustrationContractTest`
     * 双向钉住（少一枚 = 该入口退回线框，多一枚 = 表里有幽灵项）。
     */
    private val resources: Map<AppIcon, Int> = mapOf(
        AppIcon.Book to R.drawable.app_icon_book,
        AppIcon.BookOpen to R.drawable.app_icon_books,
        AppIcon.Clock to R.drawable.app_icon_clock,
        AppIcon.Document to R.drawable.app_icon_document,
        AppIcon.Mic to R.drawable.app_icon_microphone,
        AppIcon.Speaker to R.drawable.app_icon_speaker,
        AppIcon.Spark to R.drawable.app_icon_spark,
        AppIcon.Chart to R.drawable.app_icon_chart,
        AppIcon.Camera to R.drawable.app_icon_camera,
        AppIcon.Shield to R.drawable.app_icon_shield,
        AppIcon.Upload to R.drawable.app_icon_upload,
        AppIcon.Download to R.drawable.app_icon_download,
        AppIcon.Search to R.drawable.app_icon_search,
        AppIcon.List to R.drawable.app_icon_list,
        AppIcon.Bookmark to R.drawable.app_icon_bookmark,
        AppIcon.Star to R.drawable.app_icon_star,
        AppIcon.Translate to R.drawable.app_icon_translate,
        AppIcon.Image to R.drawable.app_icon_image,
        AppIcon.Newspaper to R.drawable.app_icon_newspaper,
        AppIcon.Gear to R.drawable.app_icon_gear,
        AppIcon.Person to R.drawable.app_icon_person,
        AppIcon.Info to R.drawable.app_icon_info,
        AppIcon.Bell to R.drawable.app_icon_bell,
        AppIcon.Warning to R.drawable.app_icon_warning,
        AppIcon.Trash to R.drawable.app_icon_trash,
        AppIcon.Play to R.drawable.app_icon_play,
        AppIcon.Pause to R.drawable.app_icon_pause,
        AppIcon.Stop to R.drawable.app_icon_stop,
    )

    /** 该字形是否有插画档；没有就必须回退到矢量（豁免枚）。 */
    fun has(icon: AppIcon): Boolean = icon in resources

    /** 插画资源 id；豁免枚返回 `null`，调用方不必自己判表。 */
    fun resOf(icon: AppIcon): Int? = resources[icon]
}

/**
 * 插画档的渲染出口：装饰性位图（`contentDescription = null`），语义仍由同行文案承载。
 *
 * 36dp 是 40dp 入口容器里的观感上限——母版自带 6% 留白，再内缩就比原来的 22dp 字形还小；
 * `ContentScale.Fit` 只做等比落位，不裁切（裁切会把母版的圆角厚边切掉）。
 */
@Composable
fun IllustratedIcon(
    icon: AppIcon,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val res = AppIllustrations.resOf(icon) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .testTag(IllustratedIconTag),
    )
}
