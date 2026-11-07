package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 批 C-1 骨架族源码契约（判据：`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 1）。
 *
 * 这一族要钉的是「全站骨架只有一份实现」这件事，起因是开工实测的两条形同缺陷的现状：
 *
 *  - 生产里真正在画的骨架只有 `AppScreen` 的**私有** `WordBookRowSkeleton`（选词书一屏），
 *    而 `ui/components/Skeleton.kt` 的 `ListSkeleton` / `SkeletonRow` / `SkeletonBar` **全仓零引用**——
 *    死代码；更糟的是它的 KDoc 声称「connected 判据依赖 `wordbook_placeholder_`」，实测该 tag 只出现在
 *    这个文件自己里（定义 + 注释），`app/src/test` 与 `app/src/androidTest` 零命中。批 C 铺全站时如果
 *    照那份文档保留旧前缀，就是在保护一个不存在的判据，所以 [dead_family_and_its_placeholder_tag_are_retired]
 *    把「死代码 + 假 tag」一起关在门外。
 *  - 骨架族一旦在别处（屏侧私有）再长一份，「真卡改版时骨架要跟」这条维护债就变成两处要跟。
 *    所以 [primitive_shapes_are_defined_exactly_once_across_ui_sources] 数的是**整棵 ui 源码树**，不是单文件。
 *
 * 三条既有锚不许动，本契约只加不减：
 *  - `LearningSetupLayoutContractTest:913-921`：选词书屏里 `wordbook_row_skeleton_` 恰好 1 处、
 *    行数必须读生产常量 `WORD_BOOK_PLACEHOLDER_ROWS`；
 *  - `androidTest/WordbookPickerHeaderTest:85-121`：骨架逐行存在、通栏、实测高度不矮于真行
 *    （这一条决定了 tag 必须留在 `hairlineTop` 的**外侧** padding 之前——tag 落在 padding 内侧会让骨架
 *    比真行多测到 1dp，是上一批踩过的坑，随私有骨架一起迁进共享族）；
 *  - 骨架零文案、零数字（`Skeleton.kt` 原有的无障碍口径，F5-05 第二次写成规格）。
 *
 * 形状沿用本目录禁令族惯例（`MotionSpecContractTest`）：目录守卫 + 覆盖面锚点 + 合成双边
 * （违例形状必须被命中、合规形状必须被放行）+ 真实文件零 offender。
 * 已知局限如实记录：字符串锚判的是形状与份数，判不了「画得好看」；观感归批 C-6 的截图册。
 */
class SkeletonFamilyContractTest {

    /** 骨架族的五件基本形状：条、行、列表、卡、分段计数格。 */
    private val familyPrimitives = listOf(
        "SkeletonBar",
        "SkeletonRow",
        "SkeletonList",
        "SkeletonCard",
        "SkeletonStatGrid",
    )

    private fun definitionCount(name: String): Regex = Regex("""fun\s+$name\(""")

    private fun scanUiText(): String = uiFiles().joinToString(separator = "\n") { it.readText() }

    @Test
    fun `skeleton family exposes the five shapes`() {
        val source = uiComponentSource("Skeleton.kt")
        familyPrimitives.forEach { name ->
            assertTrue(
                countMatches(source, definitionCount(name)) == 1,
                "骨架族缺 `$name` 或不止一份（应恰好 1 处定义，实际 " +
                    "${countMatches(source, definitionCount(name))} 处）：批 C 的铺面全部建在这一族上，" +
                    "少一件就意味着下一屏又开始在屏侧私有画骨架。",
            )
        }
    }

    @Test
    fun `primitive shapes are defined exactly once across ui sources`() {
        val wholeTree = scanUiText()
        val duplicated = familyPrimitives.filter { countMatches(wholeTree, definitionCount(it)) > 1 }
        assertTrue(
            duplicated.isEmpty(),
            "骨架基本形状在整棵 ui 源码树里长出了第二份：${duplicated.joinToString()}——" +
                "两份实现意味着真卡改版时要跟两处，批 C 收口成族的意义就没了。",
        )
        // 覆盖面锚点：五件里至少行与条必须真的被屏侧调用（否则契约对着空转绿）。
        val callSites = uiFiles().sumOf { file ->
            familyPrimitives.sumOf { name -> countMatches(file.readText(), Regex(Regex.escape("$name("))) }
        }
        assertTrue(
            callSites >= MIN_SKELETON_FAMILY_CALL_SITES,
            "整棵 ui 源码树对骨架族的调用点只有 $callSites 处（下限 $MIN_SKELETON_FAMILY_CALL_SITES）：" +
                "定义齐全却没人调用，说明铺面没接上或被整批搬走。",
        )
    }

    @Test
    fun `private wordbook row skeleton is migrated but its anchors stay`() {
        val appScreen = uiScreenSource("AppScreen.kt")
        assertTrue(
            countMatches(appScreen, Regex("""fun\s+WordBookRowSkeleton\(""")) == 0,
            "AppScreen 里还留着私有 `WordBookRowSkeleton` 定义：批 C-1 要把它迁进共享族" +
                "（迁形状与两条锚，不迁第二份实现）。",
        )
        assertTrue(
            countMatches(appScreen, Regex(Regex.escape("wordbook_row_skeleton_"))) == 1,
            "选词书骨架的 testTag 前缀必须原样保留且恰好 1 处——`LearningSetupLayoutContractTest` " +
                "与 androidTest 的逐行判据都读它，改名等于拆已验收的闸。",
        )
        assertTrue(
            "WORD_BOOK_PLACEHOLDER_ROWS" in appScreen,
            "骨架行数仍须读生产常量 `WORD_BOOK_PLACEHOLDER_ROWS`，不许在屏侧另写数字（既有闸）。",
        )
        assertTrue(
            countMatches(appScreen, Regex("""SkeletonList\(""")) >= 1,
            "AppScreen 的选词书骨架必须改为调用共享族 `SkeletonList(`，只删私有定义不算迁移。",
        )
    }

    @Test
    fun `dead family and its placeholder tag are retired`() {
        val wholeTree = scanUiText()
        listOf("ListSkeleton", "wordbook_placeholder_").forEach { retired ->
            val hits = countMatches(wholeTree, Regex(Regex.escape(retired)))
            assertTrue(
                hits == 0,
                "`$retired` 仍有 $hits 处命中：`ListSkeleton` 是零引用死代码，`wordbook_placeholder_` " +
                    "是一条不存在的 connected 判据（实测只有该文件自己的定义与注释）。" +
                    "留着就是让下一任以为有两套骨架 tag。",
            )
        }
    }

    @Test
    fun `skeleton family carries no text nodes`() {
        val source = uiComponentSource("Skeleton.kt")
        assertTrue(
            countMatches(source, Regex("""(?<![A-Za-z0-9_])Text\(""")) == 0,
            "骨架里出现了 `Text(`：占位行手里没有数据，任何文案或计数都是假数字" +
                "（Spec 05 F5-05 与 2026-11-06 占位诚实化决策同源，TalkBack 会念出来）。",
        )
    }

    @Test
    fun `skeleton sources use only theme colors and motion tokens`() {
        val offenders = skeletonColorAndTimingOffenders(uiComponentSource("Skeleton.kt"))
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun `rule flags a duplicated private skeleton and a captioned row`() {
        // 合成双边之一：违例形状必须被命中（不碰生产文件）。
        val violating = """
            @Composable
            private fun SkeletonRow(row: Int) { Text("已学 0 词") }
            val c = Color(0xFFEDEDED)
            val d = AppMotion.durationMs(AppMotion.Fast, animatorDurationScale(context))
        """.trimIndent()
        val found = skeletonColorAndTimingOffenders(violating)
        assertTrue(
            found.any { it.contains("Color(0xFFEDEDED)") },
            "裸 hex 未被命中：${found.joinToString()}",
        )
        assertTrue(
            found.any { it.contains("durationMs") },
            "时长换算入口未被命中（批 A 已删、本批不得接回）：${found.joinToString()}",
        )
        // 合成双边之二：合规形状必须放行，注释里的字面量不得算 offender。
        // 合规那一边按批 C-7 的口径写（墨走 `SkeletonInk.fill`）：本文件钉的是「屏侧不许自己画骨架」，
        // 举例若还停留在 `outlineSoft.copy(alpha = 0.22f)`，读的人会以为那是当前合规写法。
        val compliant = """
            // 反例存档：绝不在骨架里写 Color(0xFFEDEDED) 或 AppMotion.durationMs(...)，也绝不写 Text。
            @Composable
            fun SkeletonRow(row: Int, shape: Shape) {
                Box(Modifier.background(SkeletonInk.fill(appColors()), shape))
            }
        """.trimIndent()
        assertTrue(
            skeletonColorAndTimingOffenders(compliant).isEmpty(),
            "合规形状被误判：${skeletonColorAndTimingOffenders(compliant).joinToString()}",
        )
    }

    /**
     * 骨架源的三条禁令：裸 hex、批 A 已删的时长换算入口、骨架内的 `Text(`。
     * 判前一律剥注释（`stripComments`），因此 KDoc 里复述的反例不算命中。
     */
    private fun skeletonColorAndTimingOffenders(source: String): List<String> {
        val stripped = stripComments(source)
        val found = mutableListOf<String>()
        rawColorLiteral.findAll(stripped).forEach { found += "骨架里出现裸 hex ${it.value}：取值只许走 appColors() 族" }
        listOf("durationMs", "animatorDurationScale").forEach { helper ->
            val hits = stripped.lines().count { it.contains(helper) }
            if (hits > 0) found += "骨架里出现 $helper ×$hits：Compose 已按 animator_duration_scale 自行缩放（批 A R1），不得二次换算"
        }
        val textNodes = countMatches(stripped, Regex("""(?<![A-Za-z0-9_])Text\("""))
        if (textNodes > 0) found += "骨架里出现 Text( ×$textNodes：占位不许带文案或假数字"
        return found
    }
}

/**
 * 覆盖面下限：C-1 交付的是族本身，调用点此刻只有选词书迁移的一处 + 族内互相调用。
 * 取 3 而不是更大数：C-2/C-3 会把一级页与子页铺上来，数字只会涨；一旦回落到 3 以下说明铺面被摘掉。
 */
private const val MIN_SKELETON_FAMILY_CALL_SITES = 3
