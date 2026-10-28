package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 吸底槽发丝线闸（批 5 遗留 7 的收口，来源见
 * `docs/handoff/2026-11-02-layout-redesign-batch-5-report.md` §8 第 7 条与 §3.5-7）。
 *
 * 管什么：**吸底槽自铺底色时，槽顶必须有一条 `hairlineTop`**。
 *
 * 起因：吸底槽与它上面的软卡同色（多数屏是 `colors.surface`，导入/阅读两条是 `background`）时，
 * 2× 字号下的长文正好断在槽沿上，没有那条 1dp 线就会被读成「卡片内容被截断」。批 5 只给导出屏
 * 补了线，其余吸底槽没扫——本闸把「哪一档是吸底槽」和「它有没有线」一起冻住。
 *
 * 台账是**双向**的（第一条用例）：
 *  - 扫到的吸底槽没登记 → 红。新屏的吸底槽不许绕过这张表；识别符号是 `align(Alignment.BottomCenter)`
 *    与 `testTag("<名字>bar...")`，两种写法本仓都在用。
 *  - 登记了但源码里扫不到 → 红。站点被删/改名/搬走时必须把台账减回去，否则闸对着空气绿。
 *
 * 「自铺底色」是必要条件而不是充分条件：词卡屏的评分条透明地压在 `background` 上（`colors.surface`
 * 一次都不出现），不着色就没有缝，不需要线——这类站点登记为 `paintsOwnFill = false` 并写明理由，
 * 而不是从台账里删掉（删掉就再也挡不住「哪天给它加了底色却没补线」）。
 *
 * 读法是源码文本（已剥注释，KDoc 里提到 `hairlineTop` 不算数）：取证窗口是命中行上下各
 * [StickyFooterWindowRadius] 行，一个 Modifier 链不会长到这个尺度；真实像素由 connected 侧
 * 2× 字号截图承担。
 */
class StickyFooterHairlineGuardTest {

    private val alignBottomCenter = Regex("""align\(Alignment\.BottomCenter\)""")

    /** 吸底槽命名族：`_bar` / `_action_bar` / `_feedback_bar` 都落在这里。 */
    private val barTag = Regex("""testTag\("([a-z0-9_]*bar[a-z0-9_]*)"\)""")

    private val hairlineCall = "hairlineTop("

    @Test
    fun `the sticky footer ledger covers every bottom slot in both directions`() {
        val scanned = mutableSetOf<String>()
        uiSources().forEach { (file, source) ->
            alignBottomCenter.findAll(source).forEach { scanned += "$file|$ClaimBottomCenter" }
            barTag.findAll(source).forEach { scanned += "$file|testTag:${it.groupValues[1]}" }
        }
        val claimed = StickyFooterLedger
            .flatMap { footer -> footer.claims.map { claim -> "${footer.file}|$claim" } }
            .toSet()
        assertTrue(scanned.isNotEmpty(), "一个吸底槽都没扫到：识别符号被改名或扫描路径错位，本闸已空转。")
        assertEquals(
            claimed,
            scanned,
            "吸底槽台账与实际站点两向都不许偏。新扫到的槽 → 登记进台账并补 `hairlineTop`；" +
                "台账里扫不到的槽 → 站点已删/改名，把台账减回去。",
        )
    }

    @Test
    fun `every self filled sticky footer draws a top hairline`() {
        val sources = uiSources()
        val filled = StickyFooterLedger.filter { it.paintsOwnFill }
        assertTrue(
            filled.size >= 7,
            "自铺底色的吸底槽至少 7 处（六屏各一 + 备份导出一处无 testTag 的槽）；" +
                "少于这个数说明台账被削薄，闸的可信度随之下降。",
        )
        val offenders = filled.flatMap { footer ->
            val source = sources[footer.file]
                ?: return@flatMap listOf("${footer.file}: 不在扫描集里（文件被删/改名？）")
            footer.claims.mapNotNull { claim ->
                val hits = hitsOf(source, claim)
                when {
                    hits.isEmpty() -> "${footer.file}: 认领「$claim」在源码里不存在"
                    hits.size > 1 -> "${footer.file}: 认领「$claim」出现 ${hits.size} 次（一处吸底槽只该有一处锚）"
                    hairlineCall !in windowAround(source, hits.single()) ->
                        "${footer.file}: 吸底槽「$claim」缺 `hairlineTop`（${footer.reason}）"
                    else -> null
                }
            }
        }
        assertEquals(
            emptyList<String>(),
            offenders,
            "吸底槽与它上面的滚动内容同色时，槽顶缺线会被读成「内容被截断」。" +
                "补法与 `BackupExportScreen.kt:221` 同形：`.then(hairlineTop(colors.outline))`" +
                "或直接作为 `Surface` 的 modifier 首项。违规站点：\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun `footers without their own fill are registered but exempt from the hairline`() {
        val sources = uiSources()
        val exempt = StickyFooterLedger.filterNot { it.paintsOwnFill }
        assertTrue(exempt.isNotEmpty(), "「不铺底色所以不需要线」的豁免一个都没有——口径被整表删光了。")
        exempt.forEach { footer ->
            val source = sources.getValue(footer.file)
            footer.claims.forEach { claim ->
                assertTrue(
                    hitsOf(source, claim).size == 1,
                    "${footer.file} 的「$claim」应恰好一处，否则豁免对错了站点。",
                )
            }
            assertTrue(
                footer.reason.isNotBlank(),
                "${footer.file} 被豁免补线，必须在台账里写明「为什么不铺底色」，否则豁免就是免死金牌。",
            )
        }
    }

    /** 认领符号在源码里的字符下标。 */
    private fun hitsOf(source: String, claim: String): List<Int> =
        if (claim == ClaimBottomCenter) {
            alignBottomCenter.findAll(source).map { it.range.first }.toList()
        } else {
            val tag = claim.removePrefix("testTag:")
            Regex("""testTag\("${Regex.escape(tag)}"\)""").findAll(source).map { it.range.first }.toList()
        }

    /** 命中位置上下各 [StickyFooterWindowRadius] 行：足够罩住一个 Modifier 链，又不至于串到隔壁组件。 */
    private fun windowAround(source: String, index: Int): String {
        val hitLine = source.take(index).count { it == '\n' }
        val lines = source.lines()
        val from = (hitLine - StickyFooterWindowRadius).coerceAtLeast(0)
        val to = (hitLine + StickyFooterWindowRadius).coerceAtMost(lines.lastIndex)
        return lines.subList(from, to + 1).joinToString("\n")
    }

    private fun uiSources(): Map<String, String> =
        uiFiles().associate { it.name to stripComments(it.readText()) }

    private data class StickyFooter(
        /** `ui/` 根下的屏文件名。 */
        val file: String,
        /** 认领符号：`align:BottomCenter`，或 `testTag:<锚点名>`。 */
        val claims: List<String>,
        /** 自铺底色（`surface` / `background`）→ 需要槽顶发丝线。 */
        val paintsOwnFill: Boolean,
        /** 台账自述：为什么这一档需要（或不需要）线。 */
        val reason: String,
    )

    companion object {
        private const val StickyFooterWindowRadius = 10

        private const val ClaimBottomCenter = "align:BottomCenter"

        /**
         * 吸底槽台账（8 处、11 枚认领符号）。
         *
         * 前七处都自铺底色，槽顶必须有 `hairlineTop`；最后一处（词卡屏）透明，登记但不要求补线。
         */
        private val StickyFooterLedger: List<StickyFooter> = listOf(
            StickyFooter(
                file = "ArticleImportScreen.kt",
                claims = listOf("testTag:import_action_bar"),
                paintsOwnFill = true,
                reason = "条底铺页底色 `background`，靠顶部 1dp 与可滚表单分层",
            ),
            StickyFooter(
                file = "ArticleReadingScreen.kt",
                claims = listOf("testTag:article_complete_action_bar"),
                paintsOwnFill = true,
                reason = "条底铺页底色 `background`，正文从它上面滚过",
            ),
            StickyFooter(
                file = "BackupExportScreen.kt",
                claims = listOf(ClaimBottomCenter),
                paintsOwnFill = true,
                reason = "槽色 `surface` 与屏体软卡同色（批 5 §3.5-7 的样板站点，本表以此为模板）",
            ),
            StickyFooter(
                file = "AiProfileSettingsScreen.kt",
                claims = listOf(ClaimBottomCenter, "testTag:ai_profile_save_bar"),
                paintsOwnFill = true,
                reason = "保存条铺 `surface`，表单卡片滚动穿过它下面",
            ),
            StickyFooter(
                file = "ImageStudioScreen.kt",
                claims = listOf(ClaimBottomCenter, "testTag:image_studio_action_bar"),
                paintsOwnFill = true,
                reason = "主操作槽铺 `surface`，描述正文从它下面穿过",
            ),
            StickyFooter(
                file = "VocabularyRelearnScreen.kt",
                claims = listOf("testTag:relearn_feedback_bar"),
                paintsOwnFill = true,
                reason = "三档评分条铺 `surface`，与背景 `background` 只差一层",
            ),
            StickyFooter(
                file = "WorksheetPreviewScreen.kt",
                claims = listOf("testTag:worksheet_preview_action_bar"),
                paintsOwnFill = true,
                reason = "导出条铺 `surface`，预览页正文从它上面滚过",
            ),
            StickyFooter(
                file = "WordCardScreen.kt",
                claims = listOf("testTag:word_card_progress_bar", "testTag:word_card_feedback_bar"),
                paintsOwnFill = false,
                reason = "评分条不铺底色（透明压在 `background` 上，`colors.surface` 一次都不出现），" +
                    "进度条是行内控件不是吸底槽；两者都没有颜色缝，故不补线",
            ),
        )
    }
}
