package com.example.englishlearning.ui

import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppIllustrations
import com.example.englishlearning.ui.components.StrokeOnlyIcons
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 插画档（2026-10-30）契约：`docs/design/icon-master/` 的 calm 母版位图直接进运行态后，
 * 「哪些字形有插画」「位图是否真的躺在盘上」「入口容器是否优先走插画」三件事必须可执行地钉住。
 *
 * 为什么不是「扫一眼截图就行」：插画表一旦少一枚，该入口会静默退回 24×24 线框矢量（`EntryIconBadge`
 * 的兜底分支），界面上只表现为「这一行图标不太一样」，评审时极易被当成有意为之。所以这里做三面夹击：
 * 枚举侧（键集 == 33 − 5 豁免）、文件侧（28 张 PNG 在盘且都是 256×256 RGBA）、源码侧（40dp 入口与
 * 30dp 行内两处优先分支 + 各自的兜底档都在）。
 */
class AppIllustrationContractTest {

    /** `icon-master/README.md` 的映射表在测试端的独立副本：文件名 → 字形（下划线形式的资源名）。 */
    private val expectedFiles = mapOf(
        "Book" to "app_icon_book",
        "BookOpen" to "app_icon_books",
        "Clock" to "app_icon_clock",
        "Document" to "app_icon_document",
        "Mic" to "app_icon_microphone",
        "Speaker" to "app_icon_speaker",
        "Spark" to "app_icon_spark",
        "Chart" to "app_icon_chart",
        "Camera" to "app_icon_camera",
        "Shield" to "app_icon_shield",
        "Upload" to "app_icon_upload",
        "Download" to "app_icon_download",
        "Search" to "app_icon_search",
        "List" to "app_icon_list",
        "Bookmark" to "app_icon_bookmark",
        "Star" to "app_icon_star",
        "Translate" to "app_icon_translate",
        "Image" to "app_icon_image",
        "Newspaper" to "app_icon_newspaper",
        "Gear" to "app_icon_gear",
        "Person" to "app_icon_person",
        "Info" to "app_icon_info",
        "Bell" to "app_icon_bell",
        "Warning" to "app_icon_warning",
        "Trash" to "app_icon_trash",
        "Play" to "app_icon_play",
        "Pause" to "app_icon_pause",
        "Stop" to "app_icon_stop",
    )

    private fun resourceDir(): File =
        listOf(File("src/main/res/drawable-xhdpi"), File("app/src/main/res/drawable-xhdpi"))
            .firstOrNull { it.isDirectory }
            ?: error("找不到 drawable-xhdpi，工作目录 ${File(".").absolutePath}")

    private fun iconBadgeSource(): String =
        listOf(
            File("src/main/java/com/example/englishlearning/ui/components/IconBadge.kt"),
            File("app/src/main/java/com/example/englishlearning/ui/components/IconBadge.kt"),
        ).firstOrNull { it.isFile }?.readText()
            ?: error("找不到 IconBadge.kt，工作目录 ${File(".").absolutePath}")

    // ---------- 枚举侧 ----------

    @Test
    fun illustrated_set_is_every_icon_minus_the_five_stroke_only_exemptions() {
        val actual = AppIcon.values().filter { AppIllustrations.has(it) }.map { it.name }.toSet()
        val expected = expectedFiles.keys
        assertEquals(
            expected,
            actual,
            "插画表必须恰好覆盖「全 33 枚 − 5 枚豁免」；少一枚 = 该入口静默退回线框，" +
                "多一枚 = 表里有幽灵项（母版并不存在）",
        )
        assertEquals(28, expected.size, "母版共 28 枚（`icon-color-calm__01…28`）")
        assertTrue(
            StrokeOnlyIcons.none { AppIllustrations.has(it) },
            "Back / Chevron / Close / Check 没有母版，必须继续走矢量",
        )
    }

    @Test
    fun every_illustration_resolves_to_a_distinct_non_empty_drawable() {
        val ids = AppIcon.values().mapNotNull { AppIllustrations.resOf(it) }
        assertEquals(28, ids.size, "resOf 对 28 枚都必须给出资源 id")
        ids.forEach { assertNotEquals(0, it, "资源 id 不得为 0（漏抄 R.drawable 条目）") }
        assertEquals(28, ids.toSet().size, "两枚字形共用同一张位图 = 映射抄错")
    }

    @Test
    fun exempt_icons_return_null_instead_of_a_fallback_drawable() {
        StrokeOnlyIcons.forEach { icon ->
            assertEquals(null, AppIllustrations.resOf(icon), "$icon 不属插画档")
        }
    }

    // ---------- 文件侧 ----------

    @Test
    fun all_twenty_eight_pngs_ship_at_256_square_rgba() {
        val dir = resourceDir()
        expectedFiles.forEach { (icon, name) ->
            val file = File(dir, "$name.png")
            assertTrue(file.isFile, "$icon 的插画位图缺失：${file.path}")
            val bytes = file.readBytes()
            assertEquals(0x89.toByte(), bytes[0], "$name.png 不是 PNG")
            val width = bytes.uint32(16)
            val height = bytes.uint32(20)
            assertEquals(256, width, "$name.png 宽度应为 256（离线脚本统一降采样后的口径）")
            assertEquals(256, height, "$name.png 高度应为 256")
            assertEquals(6, bytes[25].toInt(), "$name.png 必是 RGBA（colorType 6）：去底后靠 alpha 透出底色")
            assertTrue(
                bytes.size > 4_000,
                "$name.png 只有 ${bytes.size} 字节，像是占位图；插画档的位图不该小到这个量级",
            )
        }
    }

    // ---------- 源码侧 ----------

    @Test
    fun entry_container_prefers_the_illustration_and_keeps_the_vector_fallback() {
        val source = iconBadgeSource()
        val entryBadge = source.substringAfter("fun EntryIconBadge(").substringBefore("fun InlineIconBadge(")
        assertEquals(
            1,
            Regex("""AppIllustrations\.has\(icon\)""").findAll(entryBadge).count(),
            "入口容器应恰好一处判「有没有插画」",
        )
        assertTrue(
            Regex("""IllustratedIcon\(icon = icon, size = 36\.dp\)""").containsMatchIn(entryBadge),
            "入口容器的插画按 36dp 画在 40dp 容器里（`Fit` 留 2dp 光学边，贴满会顶到圆角）",
        )
        assertTrue(
            Regex("""if \(illustrated\) \{""").containsMatchIn(entryBadge),
            "插画分支必须排在底色分支之前：先染底再画位图 = 域色渐变从位图透明区透出来",
        )
        assertEquals(
            2,
            Regex("""AppGlyph\(""").findAll(entryBadge).count(),
            "兜底路径仍要保留两档 AppGlyph（实底档 + 弱档），少于两条等于悄悄砍掉矢量档",
        )
        assertTrue(
            !source.contains("EntryIconTone.Illustrated"),
            "不再新增第三档枚举：有母版就画插画，没有就按原两档走，避免三档组合爆炸",
        )
    }

    /**
     * 行内徽标（30dp）与底部导航四个页签共用 `InlineIconBadge`，2026-10-30 起同样先看插画表：
     * 首页「今日文章 / 词书」两行与设置页入口口径一致，选中页签也顺带拿到彩色字形。
     * 位图缩到 26dp 仍守住「容器 − 2dp 光学边」的规律，兜底一枚 17dp 单层描边字形不变。
     *
     * **2026-10-31（用户评审 ①）**：底导两档统一画同一枚插画后，徽标多了一枚 `backdrop` 开关——
     * 药片底只留给「需要定位感」的站点（入口行、页签选中档），底导的未选中三格不要药片（否则底栏成了
     * 四块浅薄荷方块）。开关必须是**默认开着**的布尔参数，既有调用点一字不动；底是真受它控制，
     * 不是「传了也不看」。
     */
    @Test
    fun inline_badge_also_prefers_the_illustration_at_a_smaller_size() {
        val source = iconBadgeSource()
        val inlineBadge = source.substringAfter("fun InlineIconBadge(")
        assertEquals(
            1,
            Regex("""AppIllustrations\.has\(icon\)""").findAll(inlineBadge).count(),
            "行内徽标应恰好一处判「有没有插画」",
        )
        assertTrue(
            Regex("""IllustratedIcon\(icon = icon, size = 26\.dp\)""").containsMatchIn(inlineBadge),
            "行内徽标按 26dp 画在 30dp 容器里；再大就顶到 `AppShape.Inline` 的圆角",
        )
        assertEquals(
            1,
            Regex("""AppGlyph\(icon = icon, tint = palette\.deep, size = 17\.dp""").findAll(inlineBadge).count(),
            "无母版的 5 枚豁免字形（如 `Check`）在行内仍是 17dp 单层描边",
        )
        assertTrue(
            Regex("""\.background\(appColors\(\)\.tint\)""").containsMatchIn(inlineBadge),
            "行内徽标保留 tint 底：30dp 容器太薄，位图直接落在卡片上会失去「徽标」的定位感",
        )
        assertTrue(
            Regex("""backdrop: Boolean = true""").containsMatchIn(inlineBadge),
            "徽标的药片底必须留一枚默认开着的 `backdrop` 开关：底导未选中档要同一枚插画但不要四块药片",
        )
        assertTrue(
            Regex("""if \(backdrop\) Modifier\.background\(appColors\(\)\.tint\) else Modifier""").containsMatchIn(inlineBadge),
            "药片底必须真的受 `backdrop` 控制（传了不看的开关 = 底导未选中格仍带底，两档仍不一致的另一种形态）",
        )
    }

    private fun ByteArray.uint32(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
}
