package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * F5-03 终审 M-1 源码契约（判据：决策 §2「发音四控语义」——计划原文五处
 * `else -> PronunciationStatus.Idle` 一律作废，未被引擎接受时**保持原状态**）：
 * `AppScreen.kt` 剥注释后不得出现 `else -> PronunciationStatus.Idle`。
 *
 * 为什么需要这一条：五个 `onPauseResume` 宿主（`AppScreen.kt:738/928/999/1403/1471`）的
 * `else` 分支分别落 `held` / `wordCardPronunciationStatus` / `held` / `articlePronunciationStatus`
 * / `held`，其中 `:738`（记录详情宿主）与 `:999`（收藏详情宿主）与被测的 `:1471`（阅读点词详情）
 * 逐字同形、共用同一状态变量 `detailPronunciationMessage`，而 connected 只钉了三处状态持有者
 * 各一个（`AppScreenTest.kt:855/892/927`）。风险=未来只改其中一份拷贝把 `else` 写回 `Idle`
 * 时无人报警：落 `Idle` 会让整组播控（含唯一可用的「停止」）在朗读仍在继续时消失。
 * 一条文件级禁令比补两条设备导航用例便宜，且天然覆盖五份拷贝（含将来新增的拷贝）。
 *
 * 形状沿用本目录既有源码契约（`AccessibilityStateSemanticsContractTest` 等禁令族）：
 *  1. 路径常量 + 目录/文件存在性守卫（`walkTopDown()` 对缺失路径返回空序列，禁止契约静默空扫）；
 *  2. 目标文件恰好一个 + 剥注释后再匹配 + 锚点存在性（`onPauseResume = {` 五处宿主形状），
 *     文件被搬空/整段改名时报「契约空转」而不是无声通过；
 *  3. 合成双边用例：禁令形状必须被命中（must-flag 反例，内存假源码，不碰生产文件），
 *     声明写进注释必须被放行（剥注释防线），HEAD 三个真实 `else -> 原状态` 形状必须被放行。
 *
 * 已知局限（如实记录，不为未修复的面做假测试）：
 *  - `stripComments` 的行注释正则会把字符串字面量里的 `//`（如 https 地址）之后整行抹掉
 *    （与本仓库禁令 1/3 与 Switch 标签契约同款已知债，统一处置见
 *     `docs/verification/f5-03-accessibility-hardening-2026-10.md` §1.1），本契约的匹配对象是
 *     纯字面量 `else -> PronunciationStatus.Idle`，与含 `//` 的 URL 同行概率极低，但形状仍成立；
 *  - 文件级子串匹配只锁「该字面形状」：写成 `else -> PronunciationStatus.Idle.also { … }`、
 *    或把 `else ->` 与 `PronunciationStatus.Idle` 拆成两行的等价形态不在匹配面内——与禁令族同一口径，
 *     由 connected 三处状态持有者用例与代码评审兜住，此处不做 AST 解析；
 *  - 锚点计数下限（5）是有意的刚性：删减播控宿主必须显式改本契约，新增宿主不报错。
 */
class PronunciationIdleFallbackContractTest {
    private val uiDir = File(PRONUNCIATION_IDLE_FALLBACK_SOURCE_PATH)

    /** 剥注释样板（沿用禁令 1/2/3 与状态语义契约）：注释里的历史写法不计入代码形状。 */
    private fun stripComments(text: String): String = text
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//.*"), "")

    /** 禁令形状：决策 §2 作废的计划原文写法（播控失败落空闲）。 */
    private val forbiddenShape = "else -> PronunciationStatus.Idle"

    /** 宿主锚点：五个 `onPauseResume` 宿主的共同形状，防契约对着被搬空的空文件无声通过。 */
    private val pauseResumeHostAnchor = "onPauseResume = {"

    /** 纯函数：命中禁令形状则返回 offender（含命中次数便于定位），空列表 = 合规。 */
    private fun idleFallbackOffenders(source: String): List<String> {
        val stripped = stripComments(source)
        val hits = stripped.lines().count { it.contains(forbiddenShape) }
        if (hits == 0) return emptyList()
        return listOf(
            "出现 $forbiddenShape 共 $hits 处：决策 §2 已作废该写法——播控请求未被引擎接受时必须" +
                "**保持原状态**，落 Idle 会让整组控件（含唯一可用的「停止」）在朗读仍在继续时消失",
        )
    }

    /** 扫描范围守卫：目录与 .kt 文件必须真实存在，否则契约测试静默空扫。 */
    private fun scanUiSources(dir: File = uiDir): List<File> {
        assertTrue(
            dir.isDirectory,
            "播控回退契约扫描目录缺失（期望 $PRONUNCIATION_IDLE_FALLBACK_SOURCE_PATH）：${dir.path}，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}；" +
                "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——" +
                "包名重构或工作目录变化必须先修此处路径。",
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "播控回退契约空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
        return files
    }

    /** 按文件名取源码：缺失或被重命名立即失败，禁止契约对着隐形的文件空转。 */
    private fun sourceOf(fileName: String): String {
        val matches = scanUiSources().filter { it.name == fileName }
        assertTrue(
            matches.size == 1,
            "播控回退契约目标文件应恰好一个（$PRONUNCIATION_IDLE_FALLBACK_SOURCE_PATH/$fileName），" +
                "实际 ${matches.size} 个",
        )
        return matches.single().readText()
    }

    @Test
    fun `ui scan scope resolves to real kotlin sources`() {
        val files = scanUiSources()
        val screens = files.filter { it.name.endsWith("Screen.kt") }
        assertTrue(
            screens.isNotEmpty(),
            "扫描到 ${files.size} 个 .kt 文件却不含任何 *Screen.kt，扫描范围疑似错位：$PRONUNCIATION_IDLE_FALLBACK_SOURCE_PATH",
        )
    }

    @Test
    fun `missing scan directory fails loudly instead of passing on an empty scan`() {
        val missing = File("$PRONUNCIATION_IDLE_FALLBACK_SOURCE_PATH/__not_a_real_package__")
        // 危险本体：无守卫时 walkTopDown() 只给空序列，真实文件用例会把空扫当成零违规通过。
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）",
        )
        // 守卫本体：同一缺失路径必须让扫描立即失败而不是返回空列表。
        val error = assertFailsWith<AssertionError> { scanUiSources(missing) }
        assertTrue(
            error.message?.contains(PRONUNCIATION_IDLE_FALLBACK_SOURCE_PATH) == true,
            "守卫失败信息必须点名期望路径，实际：${error.message}",
        )
    }

    @Test
    fun `playback failure keeps its previous state in all five pause resume hosts`() {
        val source = sourceOf("AppScreen.kt")
        val stripped = stripComments(source)
        assertTrue(stripped.isNotBlank(), "AppScreen.kt 剥注释后为空：契约空转（文件被搬空必须显式失败）")
        val hosts = stripped.lines().count { it.contains(pauseResumeHostAnchor) }
        assertTrue(
            hosts >= 5,
            "AppScreen.kt 只找到 $hosts 处 $pauseResumeHostAnchor 宿主（决策 §2 记为 5 处：" +
                "738/928/999/1403/1471）。宿主被删减或整段搬家必须显式改本契约，不能让禁令对着空档放行",
        )
        val found = idleFallbackOffenders(source)
        assertTrue(found.isEmpty(), "AppScreen.kt:\n${found.joinToString("\n")}")
    }

    @Test
    fun `rule flags the forbidden fallback and passes the shapes in production today`() {
        // must-flag 反例（内存假源码，证明匹配面真能命中该形状）：
        val flagged = """
            onPauseResume = {
                val held = detailPronunciationMessage
                val next = when {
                    held == PronunciationStatus.Playing && provider.pausePlayback() -> PronunciationStatus.Paused
                    held == PronunciationStatus.Paused && provider.resumePlayback() -> PronunciationStatus.Playing
                    else -> PronunciationStatus.Idle
                }
                detailPronunciationMessage = next
            }
        """.trimIndent()
        val found = idleFallbackOffenders(flagged)
        assertTrue(found.isNotEmpty(), "播控回退契约漏判计划原文的作废形状：$flagged")
        assertTrue(
            found.single().contains(forbiddenShape) && found.single().contains("共 1 处"),
            "offender 应点名禁令形状与命中次数，实际：$found",
        )

        // HEAD 五处真实形状（决策 §2：保持原状态）必须放行，否则契约对现行代码误判：
        listOf(
            "onPauseResume = { val next = when { x -> PronunciationStatus.Paused; else -> held } }",
            "onPauseResume = { val next = when { x -> PronunciationStatus.Paused; else -> wordCardPronunciationStatus } }",
            "onPauseResume = { val next = when { x -> PronunciationStatus.Paused; else -> articlePronunciationStatus } }",
        ).forEach { snippet ->
            assertTrue(idleFallbackOffenders(snippet).isEmpty(), "播控回退契约误判保持原状态的合规形状：$snippet")
        }

        // 声明只写在注释里（KDoc 叙述历史写法）不构成代码形状，剥注释后必须放行：
        val commented = """
            // 决策 §2 之前的写法 `else -> PronunciationStatus.Idle` 已作废。
            /* 同理：else -> PronunciationStatus.Idle 不再使用 */
            onPauseResume = { val next = when { x -> PronunciationStatus.Paused; else -> held } }
        """.trimIndent()
        assertTrue(idleFallbackOffenders(commented).isEmpty(), "播控回退契约误判注释里的历史写法：$commented")
    }
}

private const val PRONUNCIATION_IDLE_FALLBACK_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui"
