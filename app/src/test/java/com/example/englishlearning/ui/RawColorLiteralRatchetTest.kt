package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * AC-R-02 裸 hex 棘轮（批 1 Task 8 Step 2）。
 *
 * 管什么：`ui` 包（`ui/theme/` 整目录除外）里新增一处 `Color(0xAARRGGBB)` 裸字面量即当场红；
 * 存量站点由本文件的 seed 表逐文件登记，且两侧都不许偏——
 *  ① 命中行不在 seed 行集里 → 新增即挡；
 *  ② 每文件实际命中数 != 该文件 seed 计数，两个方向都挡：既挡「seed 有而文件已消失/改名」，
 *    也挡「实际减了而 seed 没跟着减」。
 * 所以 token 化掉一处裸 hex 时必须同步把本文件的 seed 减 1：seed 是债务台账，不是永久豁免。
 *
 * 不管什么：本棘轮不宣称「全站零裸 hex」。AC-R-02 的那个「零」由批 3 收口时把 seed 表清空来兑现，
 * 批 1 只负责把债务冻住，并让它在批 2/批 3 的每屏迁移里单调递减。
 *
 * 口径（与共享夹具 `SourceRatchet` 一致）：
 *  - 先剥注释再匹配；offender 形如「文件名: 折叠空白的源码行」，不报行号（块注释剥离会把其后行
 *    折叠进同一行文本，此时行号已不可信）。
 *  - offender 取样不做 distinct：同一物理行含两处命中时，distinct 会把两次命中显示成一条、掩盖真实
 *    计数；「数量精确」由按文件计数用例保证，违规集只负责点名。
 *  - `ui/theme/` 豁免的是令牌定义本体（AppPalette / AppColors / MintPalette 必须写 hex）。豁免是否
 *    名副其实由 `violations` 的目录守卫与本文件的「豁免确实在承担命中」用例一起兜底：豁免名覆盖不到
 *    任何文件、或主题目录一处 hex 都没有时当场判红，防止路径失效后棘轮静默换扫法。
 *  - 规则形状逐字取自计划 Task 8 Step 2，只认 8 位 hex。6 位缩写、`Color.argb(...)`、命名常量异名
 *    同色都不在本棘轮视野内（那是禁令族与亮度复算契约的事）；放宽正则会把存量债务一次性炸出来，
 *    批 1 不做。同理，合成用例的样例值必须写满 8 位（`0xFF112233`），写成 10 位的 `Color(0xFF11223344)`
 *    规则抓不到——那是规则的边界而不是夹具坏了，别拿它当「夹具不工作」的证据去放宽正则。
 */

/**
 * 债务台账：`ui` 包内（`ui/theme/` 除外）裸 hex 的按文件计数，key 为相对 `ui` 根的路径。
 * 批 2/批 3 每屏迁移的收口证据就是这张表对应行减到 0；批 3 收口时整表清空。
 */
internal val RawColorSeedAllowlist: Map<String, Int> = mapOf(
    "AppScreen.kt" to 4,
    "TodayPlanScreen.kt" to 5,
    "ArticleReadingScreen.kt" to 1,
    "CheckInScreen.kt" to 1,
    "components/glass/GlassButton.kt" to 1,
    "mascot/AiMascot.kt" to 1,
)

/**
 * 同一批站点的「文件名: 折叠源码行」形状，供 `violations` 作行级 allowlist。
 * 与 [RawColorSeedAllowlist] 必须互相吻合（本测试当场核验）。吻合断言同时是「两条站点折叠后同文」
 * 的探测器：真出现同文站点，Set 形状的 allowlist 会静默少计一条，那条断言当场红——届时必须把这张表
 * 换成「行内容 → 应有次数」的 Map，而不是删掉重复条目蒙混过去。
 */
internal val RawColorSeedSiteLines: List<String> = listOf(
    "AppScreen.kt: targetValue = if (selected) MintPrimary else Color(0xFFDCEAE1),",
    "AppScreen.kt: .background(Color(0xFFCDEDD9), RoundedCornerShape(50))",
    "AppScreen.kt: colors = SwitchDefaults.colors(checkedTrackColor = MintPrimary, uncheckedTrackColor = Color(0xFFCDEFE1)),",
    "AppScreen.kt: inactiveTrackColor = Color(0xFFCDEFE1),",
    "ArticleReadingScreen.kt: style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF17342A)),",
    "CheckInScreen.kt: drawArc(color = Color(0xFFFFF1DB), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Round))",
    "TodayPlanScreen.kt: CountCard(\"今日新增 \${state.newTarget} 词\", \"今日新增 \${state.newTarget} 词\", \"today_plan_new_count\", Color(0xFF68BDA6))",
    "TodayPlanScreen.kt: CountCard(\"今日复习 \${state.dueTarget} 词\", \"今日复习 \${state.dueTarget} 词\", \"today_plan_due_count\", Color(0xFFC7A47D))",
    "TodayPlanScreen.kt: colors = ButtonDefaults.buttonColors(containerColor = AppPalette.Surface, contentColor = Color(0xFF795B36)),",
    "TodayPlanScreen.kt: private val WorkloadReviewColor = Color(0xFFC7A47D)",
    "TodayPlanScreen.kt: private val WorkloadNewColor = Color(0xFF68BDA6)",
    "GlassButton.kt: Brush.verticalGradient(listOf(AppleMintStart, AppleMintMiddle, Color(0xFF65D3B9)))",
    "AiMascot.kt: private val EyeColor: Color = Color(0xFF188F76)",
)

class RawColorLiteralRatchetTest {
    private val uiDir = File(UI_MAIN_SOURCE_PATH)

    /** 计划 Task 8 Step 2 原文规则，逐字照用。 */
    private val rule = Regex("""Color\(\s*0x[0-9A-Fa-f]{8}\s*\)""")

    private fun File.relativeUiPath(): String = relativeTo(uiDir).invariantSeparatorsPath

    /** 「文件名: 行」形状要成立，文件名必须在扫描集内唯一，否则两个同名文件的站点会互相冒名顶替。 */
    private fun filesByName(): Map<String, File> = uiFiles(uiDir).associateBy { it.name }

    private fun File.isInThemeDir(): Boolean =
        relativeUiPath().split('/').dropLast(1).contains(THEME_EXEMPT_DIR_NAME)

    private fun offenders(): List<String> =
        violations("裸 hex", rule, RawColorSeedSiteLines.toSet(), uiDir, exemptDirNames = setOf(THEME_EXEMPT_DIR_NAME))

    private fun actualCounts(): Map<String, Int> = uiFiles(uiDir)
        .filterNot { it.isInThemeDir() }
        .associate { it.relativeUiPath() to countMatches(it.readText(), rule) }
        .filterValues { it > 0 }

    private fun tempSources(vararg sources: Pair<String, String>): File {
        val dir = java.nio.file.Files.createTempDirectory("raw-color-ratchet").toFile()
        sources.forEach { (path, text) ->
            val target = File(dir, path)
            target.parentFile?.mkdirs()
            target.writeText(text)
        }
        return dir
    }

    @Test
    fun `scan scope resolves to real ui sources and names files unambiguously`() {
        val files = uiFiles(uiDir)
        assertTrue(
            files.any { it.name.endsWith("Screen.kt") },
            "裸 hex 棘轮扫到 .kt 却不含任何 *Screen.kt，扫描范围疑似错位：$UI_MAIN_SOURCE_PATH"
        )
        val collidedNames = files.groupBy { it.name }.filterValues { it.size > 1 }
        assertTrue(
            collidedNames.isEmpty(),
            "offender 用「文件名: 行」形状，但扫描集里有重名文件，站点会互相冒名顶替：$collidedNames"
        )
        assertTrue(
            files.any { it.isInThemeDir() },
            "裸 hex 棘轮的整目录豁免在扫描集里一个文件都没覆盖到，豁免名疑似失效：$UI_THEME_SOURCE_PATH"
        )
    }

    @Test
    fun `seed tables agree with each other and keep every site line distinct`() {
        assertTrue(
            RawColorSeedSiteLines.size == RawColorSeedSiteLines.toSet().size,
            "seed 行集里有折叠后同文的两个站点：Set 形状的 allowlist 会静默少计一条，" +
                "此时必须把这张表换成「行内容 → 应有次数」的 Map，而不是删掉重复条目"
        )
        val byName = filesByName()
        val unknownNames = RawColorSeedSiteLines
            .map { it.substringBefore(SEED_LINE_SEPARATOR) }
            .distinct()
            .filterNot { it in byName }
        assertTrue(
            unknownNames.isEmpty(),
            "seed 行集点到扫描集里不存在的文件（改名/删除必须同步 seed）：$unknownNames"
        )
        val countsFromLines = RawColorSeedSiteLines
            .groupingBy { byName.getValue(it.substringBefore(SEED_LINE_SEPARATOR)).relativeUiPath() }
            .eachCount()
        assertEquals(
            RawColorSeedAllowlist,
            countsFromLines,
            "seed 行集与 seed 计数表不吻合：两张台账是同一批债务的两个面，必须同进同退"
        )
    }

    @Test
    fun `no raw hex outside the seed - a newly added literal fails the ratchet`() {
        val offenders = offenders()
        assertTrue(
            offenders.isEmpty(),
            "新增裸 hex（应改为 appColors().x / appDomain(AppDomain.X).deep 等 token；" +
                "若属令牌定义请把站点移进 ui/theme，若是确证的不可迁移站点，先修订计划再登记 seed）：\n" +
                offenders.joinToString("\n")
        )
    }

    @Test
    fun `per-file hit count equals the seed count in both directions`() {
        val actual = actualCounts()
        val staleSeed = RawColorSeedAllowlist.filterKeys { it !in actual }
        assertTrue(
            staleSeed.isEmpty(),
            "seed 登记的裸 hex 文件已不在命中集里（文件被改名/删除，或该文件的债务已 token 化完）：$staleSeed" +
                " —— 已迁移的必须把本文件 seed 一并删掉，棘轮不接受残留豁免"
        )
        val unseeded = actual.filterKeys { it !in RawColorSeedAllowlist }
        assertTrue(
            unseeded.isEmpty(),
            "出现未登记的裸 hex 文件：$unseeded —— 应改为 appColors().x / appDomain(AppDomain.X).deep，" +
                "并把本屏迁移交回批 2/批 3 的收口任务，不许在此处临时开豁免"
        )
        val drifted = actual.filter { (path, count) -> RawColorSeedAllowlist[path] != count }
        assertTrue(
            drifted.isEmpty(),
            "裸 hex 计数与 seed 不符（增即挡，减了不同步改 seed 也挡）：\n" +
                drifted.entries.joinToString("\n") { (path, count) ->
                    "$path 实际 $count 处，seed ${RawColorSeedAllowlist[path]} 处——迁走一处就把本文件 seed 减 1"
                }
        )
    }

    @Test
    fun `theme exemption is load bearing not a blanket blind spot`() {
        val themeFiles = uiFiles(File(UI_THEME_SOURCE_PATH))
        val themeHexHits = themeFiles.sumOf { countMatches(it.readText(), rule) }
        assertTrue(
            themeHexHits > 0,
            "ui/theme 豁免的理由是「令牌定义本体必须写 hex」，但该目录一处 hex 都没有：$UI_THEME_SOURCE_PATH" +
                "（${themeFiles.size} 个 .kt）——要么定义已换写法、要么豁免路径已失效，都必须当场处理，" +
                "不能让棘轮靠一条空豁免继续跑"
        )
    }

    @Test
    fun `allowlisted site disappears from the violation set and an unlisted one stays`() {
        val dir = tempSources(
            "Plain.kt" to "private val a = Color(0xFF112233)\nprivate val b = Color(0xFF556677)\n",
        )
        try {
            val all = violations("合成裸 hex", rule, emptySet(), dir)
            assertEquals(
                listOf(
                    "Plain.kt: private val a = Color(0xFF112233)",
                    "Plain.kt: private val b = Color(0xFF556677)",
                ),
                all,
                "violations 必须逐命中给出一条「文件名: 行」，同一行的两处命中不得合并"
            )
            val afterExemption = violations("合成裸 hex", rule, setOf(all.first()), dir)
            assertEquals(listOf(all.last()), afterExemption, "allowlist 命中即从违规集里消掉，未登记的必须留下")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `sites spread over several lines are caught and exempted in their folded form`() {
        val spread = "private val palette = listOf(\n    Color(\n        0xFF123456\n    ),\n)\n"
        val dir = tempSources("Spread.kt" to spread)
        try {
            val offenders = violations("合成裸 hex", rule, emptySet(), dir)
            assertEquals(1, offenders.size, "换行 + 缩进的真实形态必须被抓：$offenders")
            val folded = offenders.single()
            // offender 取的是「命中所在的整行」（折叠空白后），命中右侧的尾随逗号也在行内——seed 抄行时必须连它一起抄。
            assertEquals("Spread.kt: Color( 0xFF123456 ),", folded, "offender 必须是折叠空白后的整行形状")
            assertTrue(
                violations("合成裸 hex", rule, setOf(folded), dir).isEmpty(),
                "折叠形状写进 allowlist 后必须豁免得掉，否则 seed 永远写不中"
            )
            assertTrue(
                violations("合成裸 hex", rule, setOf("Spread.kt:$SEED_LINE_SEPARATOR$spread"), dir).size == 1,
                "按原始未折叠文本写 allowlist 不得生效——seed 只能用折叠形状"
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `directory exemption applies to the synthetic scan and a dead exemption fails`() {
        val dir = tempSources(
            "Plain.kt" to "private val a = Color(0xFF112233)\n",
            "theme/Defs.kt" to "private val b = Color(0xFF999999)\n",
        )
        try {
            val offenders = violations("合成裸 hex", rule, emptySet(), dir, exemptDirNames = setOf(THEME_EXEMPT_DIR_NAME))
            assertEquals(listOf("Plain.kt: private val a = Color(0xFF112233)"), offenders, "整目录豁免只吃掉 theme 下的站点")
            val deadExemption = assertFailsWith<AssertionError> {
                violations("合成裸 hex", rule, emptySet(), dir, exemptDirNames = setOf("no_such_dir"))
            }
            assertTrue(
                deadExemption.message?.contains("no_such_dir") == true,
                "指向不存在目录的豁免必须当场判红并点名该豁免，实际：${deadExemption.message}"
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `missing scan directory fails loudly instead of passing on an empty scan`() {
        val missing = File("$UI_MAIN_SOURCE_PATH/__not_a_real_package__")
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）"
        )
        val error = assertFailsWith<AssertionError> { violations("裸 hex", rule, emptySet(), missing) }
        assertTrue(
            error.message?.contains(UI_MAIN_SOURCE_PATH) == true,
            "守卫失败信息必须点名期望路径与当前 JVM 工作目录，实际：${error.message}"
        )
    }

    private companion object {
        const val THEME_EXEMPT_DIR_NAME = "theme"
        const val SEED_LINE_SEPARATOR = ": "
    }
}
