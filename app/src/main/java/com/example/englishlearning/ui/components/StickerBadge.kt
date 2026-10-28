package com.example.englishlearning.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.englishlearning.R
import com.example.englishlearning.ui.theme.LocalIsDark
import com.example.englishlearning.ui.theme.appColors

/**
 * 原型贴纸（`assets/icons/<name>.png`，32 枚 256×256）及其徽标容器（`assets/app.css` `.badge`）。
 *
 * 为什么用 `Sticker` 枚举而不是直接透传 `AppIcon`：原型的贴纸清单与旧字形清单**不是同一集合**
 * ——贴纸多出 `cloud / qr / edit / target` 四枚，而 `Back / Chevron / Close / Check` 没有贴纸版。
 * 用枚举把资源 id 与编译期常量绑在一起，屏侧写 `Sticker.Newspaper` 拼错即编译失败；若透传
 * `AppIcon`，四枚新贴纸就没有入口，只能退回旧字形，成品与原型不一致。
 *
 * 32 枚必须与原型 `app.js` 的 `STICKERS` 清单逐字一致，资源落在 `res/drawable-nodpi/sticker_<name>.png`
 * （本地打包，不联网取图）。清单与文件的双向核验在 `SharedComponentContractTest`。
 */
enum class Sticker(val asset: String, val res: Int) {
    Book("book", R.drawable.sticker_book),
    Books("books", R.drawable.sticker_books),
    Clock("clock", R.drawable.sticker_clock),
    Document("document", R.drawable.sticker_document),
    Microphone("microphone", R.drawable.sticker_microphone),
    Speaker("speaker", R.drawable.sticker_speaker),
    Spark("spark", R.drawable.sticker_spark),
    Chart("chart", R.drawable.sticker_chart),
    Camera("camera", R.drawable.sticker_camera),
    Shield("shield", R.drawable.sticker_shield),
    Upload("upload", R.drawable.sticker_upload),
    Download("download", R.drawable.sticker_download),
    Search("search", R.drawable.sticker_search),
    List("list", R.drawable.sticker_list),
    Bookmark("bookmark", R.drawable.sticker_bookmark),
    Star("star", R.drawable.sticker_star),
    Translate("translate", R.drawable.sticker_translate),
    Image("image", R.drawable.sticker_image),
    Newspaper("newspaper", R.drawable.sticker_newspaper),
    Gear("gear", R.drawable.sticker_gear),
    Person("person", R.drawable.sticker_person),
    Info("info", R.drawable.sticker_info),
    Bell("bell", R.drawable.sticker_bell),
    Warning("warning", R.drawable.sticker_warning),
    Trash("trash", R.drawable.sticker_trash),
    Play("play", R.drawable.sticker_play),
    Pause("pause", R.drawable.sticker_pause),
    Stop("stop", R.drawable.sticker_stop),
    Cloud("cloud", R.drawable.sticker_cloud),
    Qr("qr", R.drawable.sticker_qr),
    Edit("edit", R.drawable.sticker_edit),
    Target("target", R.drawable.sticker_target),
}

/**
 * 原型徽标三档（`app.js` 的 `st()`：圆角 = 边长 × 0.31 取整）。
 *
 * 44 是行首默认档，64 用于详情页大徽标，32 用于紧凑行。空状态大图（84/26）不在这三档里，
 * 由 [StickerBadge] 的 `dimension/corner` 覆盖参数承载——它不是一个「徽标」，只是同一张贴纸画大了。
 */
enum class StickerSize(val dp: Dp, val radius: Dp) {
    Small(32.dp, 10.dp),
    Medium(44.dp, 14.dp),
    Large(64.dp, 20.dp),
}

/**
 * 贴纸徽标：`tint` 底 + 圆角 + 铺满裁切的贴纸 + 1dp 内描边（原型 `.badge`）。
 *
 * 内描边是**画在内容之上**的：`box-shadow: inset` 不会被 `overflow: hidden` 裁掉，而 Compose 里
 * 若把 `border` 排在 `clip` 之后会同时占掉 1dp 布局，贴纸就不满铺。`drawWithContent` 先画贴纸再压
 * 一圈 1dp 线，几何与原型一致（`Stroke` 居中于边界，视觉上是内缩半像素，可忽略）。
 *
 * 描边色按主题分套：浅套 `rgba(21,33,28,.05)` 就是 `ink-1` 的 5% alpha、深套 `rgba(255,255,255,.09)`
 * 是纯白的 9%——两套都用 token 的透明档表达，不写裸十六进制（`RawColorLiteralRatchetTest` 会挡）。
 *
 * 位图是纯装饰（`contentDescription = null`），语义由同行文案承载——与 `IllustratedIcon` 同一口径。
 */
@Composable
fun StickerBadge(
    sticker: Sticker,
    modifier: Modifier = Modifier,
    size: StickerSize = StickerSize.Medium,
    dimension: Dp = size.dp,
    corner: Dp = size.radius,
) {
    val colors = appColors()
    val isDark = LocalIsDark.current
    // 原型 `.badge` 的内描边：浅套 rgba(21,33,28,.05) = ink-1 5%，深套 rgba(255,255,255,.09) = 白 9%。
    val ring = if (isDark) Color.White.copy(alpha = 0.09f) else colors.text1.copy(alpha = 0.05f)
    Box(
        modifier = modifier
            .size(dimension)
            .clip(RoundedCornerShape(corner))
            .background(colors.tint)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = ring,
                    cornerRadius = CornerRadius(corner.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
    ) {
        Image(
            painter = painterResource(sticker.res),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
