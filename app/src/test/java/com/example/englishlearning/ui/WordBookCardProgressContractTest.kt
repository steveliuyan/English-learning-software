package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 词书卡进度呈现契约（spec F1-01 渐进发布 + 决策 2026-10-17 §决定 2；终审 I-1）。
 *
 * 规则（都是对 `AppScreen.WordBookCard` 的文件级形状断言，JVM 侧无 Compose 测试设施，
 * 与本目录 F5-03 禁令族同口径：先锚点存在性防契约空转，再判规则）：
 *  规则1 词量必须来自册元数据本身（文案取自 book.totalWords 的插值形状），不得挂在 progress 上——
 *        spec 第 17 行写明「册列表（名称、等级、词量）读到即先发布」，而 `WordBookProgress.total`
 *        只是同一个数字的二手包装：把它绑到进度上，就等于「进度计算中」的册连词量都不见了。
 *  规则2 进度未到位时不得画出 0% 实心轨道（禁令形状：`progress = { progress?.fraction ?: 0f }` 不许出现）：
 *        把「不知道」画成「一点没学」是假数字，与 `TodayPlanScreenTest`、`TodayWorkloadBarTest`
 *        钉住的「无数据连条子一起不画」同一条口径。
 *  规则3 三种进度态必须分开播报：已算出（learned 插值）/ 整页回填中（「进度计算中」）/
 *        这一本读不出来（「暂时读不到进度」）。后两态都对应 `progress == null`，只有 `progressLoading`
 *        能把它们分开；合成一个「已学 0」就是撒谎（AGENTS.md「降级要如实呈现」）。
 *
 * 已知局限（如实记录）：剥注释口径已统一到共享夹具（批 1 终审 F6）——本类私有的正则版实现已删除，
 * 改调 `SourceRatchet.kt` 那份字符串感知实现，所以 F5-03 债 M-B2 记的「字符串里的双斜杠之后整行被抹掉」
 * 「字面量里的星号紧跟斜杠吞掉其后真实代码」两条对本类都不再成立；
 * 规则锁精确书写形状，
 * 换空格/换变量名会触发契约——重排版时要有意识地一起改，而不是事后无声放宽。
 */
class WordBookCardProgressContractTest {
    private val uiDir = File(WORD_BOOK_PROGRESS_CONTRACT_SOURCE_PATH)

    private val totalFromBook = "text = \"共 \${book.totalWords} 词\""
    private val totalFromProgress = "共 \${it.total}"
    private val zeroPercentBar = "progress = { progress?.fraction ?: 0f }"
    private val learnedShape = "\"已学 \${progress.learned}\""
    private val computingShape = "\"进度计算中\""
    private val unreadableShape = "\"暂时读不到进度\""

    /** 规则1 纯函数：词量取自册元数据（缺正向形状或残留 progress 形状都算违规）。 */
    private fun wordCountOffenders(source: String): List<String> {
        val stripped = stripComments(source)
        val offenders = mutableListOf<String>()
        if (!stripped.contains(totalFromBook)) {
            offenders += "WordBookCard 缺 $totalFromBook —— 词量是册元数据，必须随册列表首帧可见（spec F1-01）"
        }
        if (stripped.contains(totalFromProgress)) {
            offenders += "WordBookCard 仍用 $totalFromProgress —— 词量被绑在 progress 上，进度未到位的册会连词量一起消失"
        }
        return offenders
    }

    /** 规则2 纯函数（禁令形）：无进度不许画 0% 实心轨道。 */
    private fun zeroPercentBarOffenders(source: String): List<String> =
        if (stripComments(source).contains(zeroPercentBar)) {
            listOf("WordBookCard 出现 $zeroPercentBar —— 把「不知道」画成「一点没学」")
        } else {
            emptyList()
        }

    /** 规则3 纯函数：三态文案必须齐备。 */
    private fun progressStateOffenders(source: String): List<String> {
        val stripped = stripComments(source)
        return listOf(
            learnedShape to "缺进度已算出文案 $learnedShape",
            computingShape to "缺回填中文案 $computingShape —— 与下一态混成同一个数字",
            unreadableShape to "缺读不出文案 $unreadableShape —— 降级被写成假数字",
        ).filterNot { (shape, _) -> stripped.contains(shape) }.map { (_, message) -> "WordBookCard $message" }
    }

    private fun scanUiSources(dir: File = uiDir): List<File> {
        assertTrue(
            dir.isDirectory,
            "词书卡进度契约扫描目录缺失（期望 $WORD_BOOK_PROGRESS_CONTRACT_SOURCE_PATH）：${dir.path}，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}；" +
                "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——包名重构或工作目录变化必须先修此处路径。",
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "词书卡进度契约空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
        return files
    }

    private fun sourceOf(fileName: String): String {
        val matches = scanUiSources().filter { it.name == fileName }
        assertTrue(
            matches.size == 1,
            "词书卡进度契约目标文件应恰好一个（$WORD_BOOK_PROGRESS_CONTRACT_SOURCE_PATH/$fileName），实际 ${matches.size} 个",
        )
        return matches.single().readText()
    }

    @Test
    fun `ui scan scope resolves to real kotlin sources`() {
        val files = scanUiSources()
        assertTrue(
            files.any { it.name == "AppScreen.kt" },
            "词书卡进度契约没扫到 AppScreen.kt（${files.size} 个 .kt 文件里没有它）",
        )
    }

    @Test
    fun `word count comes from the book and never from progress`() {
        val source = sourceOf("AppScreen.kt")
        assertTrue(
            stripComments(source).contains("fun WordBookCard("),
            "AppScreen.kt 找不到锚点 \"fun WordBookCard(\"：契约空转（组件被改名/删除必须显式失败）",
        )
        val found = wordCountOffenders(source)
        assertTrue(found.isEmpty(), "AppScreen.kt:\n${found.joinToString("\n")}")
    }

    @Test
    fun `a missing progress never draws a zero percent bar`() {
        val source = sourceOf("AppScreen.kt")
        val found = zeroPercentBarOffenders(source)
        assertTrue(found.isEmpty(), "AppScreen.kt:\n${found.joinToString("\n")}")
    }

    @Test
    fun `the three progress states stay separately labelled`() {
        val source = sourceOf("AppScreen.kt")
        val found = progressStateOffenders(source)
        assertTrue(found.isEmpty(), "AppScreen.kt:\n${found.joinToString("\n")}")
    }

    // ---- 合成片段双边用例：规则真的在判形状，而不是恰好被真实文件喂绿 ----

    @Test
    fun `word count rule accepts the book-bound shape and rejects the progress-bound one`() {
        val compliant = """
            private fun WordBookCard(book: WordBook, progress: WordBookProgress?) {
                Text(text = "共 ${'$'}{book.totalWords} 词")
            }
        """.trimIndent()
        assertTrue(wordCountOffenders(compliant).isEmpty(), "合规形状被误判：${wordCountOffenders(compliant)}")

        val offender = """
            private fun WordBookCard(book: WordBook, progress: WordBookProgress?) {
                progress?.let { Text(text = "共 ${'$'}{it.total} 词") }
            }
        """.trimIndent()
        assertTrue(
            wordCountOffenders(offender).isNotEmpty(),
            "progress-bound 词量被放行：规则1 抓不到它，等于没判",
        )
    }

    @Test
    fun `zero percent bar ban catches the fallback fraction and lets the guarded form pass`() {
        val offender = """
            LinearProgressIndicator(progress = { progress?.fraction ?: 0f })
        """.trimIndent()
        assertTrue(zeroPercentBarOffenders(offender).isNotEmpty(), "0% 兜底形状被放行：禁令形失效")

        val compliant = """
            progress?.let { LinearProgressIndicator(progress = { it.fraction }) }
        """.trimIndent()
        assertTrue(zeroPercentBarOffenders(compliant).isEmpty(), "合规形状被误判：${zeroPercentBarOffenders(compliant)}")

        val commentedOut = "// LinearProgressIndicator(progress = { progress?.fraction ?: 0f })"
        assertTrue(
            zeroPercentBarOffenders(commentedOut).isEmpty(),
            "注释里的旧形状被抓：stripComments 未生效，规则会在真实文件里过判",
        )
    }

    @Test
    fun `state rule needs all three labels and fails on a merged fake zero`() {
        val compliant = """
            text = when {
                progress != null -> "已学 ${'$'}{progress.learned}"
                progressLoading -> "进度计算中"
                else -> "暂时读不到进度"
            }
        """.trimIndent()
        assertTrue(progressStateOffenders(compliant).isEmpty(), "合规形状被误判：${progressStateOffenders(compliant)}")

        val merged = """
            text = "已学 ${'$'}{progress?.learned ?: 0}"
        """.trimIndent()
        val offenders = progressStateOffenders(merged)
        assertTrue(
            offenders.size == 3,
            "把两种 null 态合成「已学 0」必须三条都抓（实际 ${offenders.size} 条）：${offenders.joinToString(" | ")}",
        )
    }
}

private const val WORD_BOOK_PROGRESS_CONTRACT_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui"
