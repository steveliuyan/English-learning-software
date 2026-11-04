package com.example.englishlearning.wordbook

import com.example.englishlearning.ui.stripComments
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * 「批量出网工具不得混进 connected 门禁」的契约（决策 2026-11-05 后的验证债收口）。
 *
 * `wordbook/` 里的打包器与探针类**名字带 Test、住在 androidTest**，却是打包阶段的批处理工具：
 * 逐词发真实付费 AI 请求、产出 TSV/JSONL 供人工核质量，不对产品行为做任何断言。
 * 它们在门禁里给出的从来只有「与本次改动无关的红」（2026-11-03 与 2026-11-05 两次全量走查形状一致：
 * 缺已落盘词条 / 设备未配默认文本或生图服务），而每一任都要重付一遍「是不是我改坏了」的判断成本。
 *
 * 本契约钉三件事：
 *  1. **识别面**：只要类里出现真实网络传输（`UrlConnection*HttpTransport(`），它就是工具类，
 *     每个 `@Test` 的第一行必须是 `assumeOperatorToolRequested(...)`——新增第四个工具类同样跑不掉；
 *  2. **诚实面**：闸的跳过理由必须自带 `-e operatorTool true` 与「跳过 ≠ 通过」，
 *     否则一次静默跳过会被读成绿灯（本仓最恨的空转）；
 *  3. **判据不是摆设**：把同一批判据喂给一份「工具类没装闸」的合成源码，必须抓到
 *     （本仓源码契约反复踩过「正则写错→永远绿」，must-flag 那一条就是判据真的抓得住的证据）。
 *
 * 注意本契约**不删**工具类原有的前置断言：显式 opt-in 之后它们仍然会红，那是给操作者的真信号。
 */
class OperatorToolGateContractTest {

    @Test
    fun everyRealOutboundToolClassGatesEveryTestOnExplicitOptIn() {
        val sources = toolSources()
        assertTrue(sources.isNotEmpty(), "wordbook/ 下解析不出任何工具类：识别面失效，本契约不能作为证据")
        val violations = unpaidToolViolations(sources)
        assertTrue(
            violations.isEmpty(),
            "以下批量出网工具的用例没装显式 opt-in 闸（第一行必须是 assumeOperatorToolRequested）：" +
                violations.joinToString("; ") +
                "——它们在 connected 门禁里只会提供与改动无关的红",
        )
    }

    @Test
    fun theJudgementsFlagAnUngatedToolInsteadOfQuietlyPassing() {
        val ungated = """
            class UngatedTool {
                private val transport = UrlConnectionAiHttpTransport(Dispatchers.IO)
                @Test
                fun packsFiveHundredWords() {
                    assertTrue("必须配置默认文本服务", profile != null)
                }
            }
        """.trimIndent()
        val gated = """
            class GatedTool {
                private val transport = UrlConnectionAiHttpTransport(Dispatchers.IO)
                @Test
                fun packsFiveHundredWords() {
                    assumeOperatorToolRequested("GatedTool")
                    assertTrue("必须配置默认文本服务", profile != null)
                }
            }
        """.trimIndent()

        val flagged = unpaidToolViolations(mapOf("UngatedTool.kt" to ungated))
        assertFalse(
            flagged.isEmpty(),
            "判据抓不住「工具类没装闸」的合成样本：上一条对真实文件的绿不能作为证据",
        )
        assertTrue(
            flagged.single().startsWith("UngatedTool.kt#packsFiveHundredWords"),
            "违规项没点名到「文件#用例」，维护者拿不到可下手的锚点：$flagged",
        )
        assertTrue(
            unpaidToolViolations(mapOf("GatedTool.kt" to gated)).isEmpty(),
            "判据把装了闸的样本也判成违规：口径过严，会逼维护者去放宽判据而不是补闸",
        )
    }

    @Test
    fun theSkipReasonStaysHonestAboutWhatSkippingMeans() {
        val gate = strippedOf("OperatorToolGate.kt")
        assertTrue(gate.isNotBlank(), "读不到 OperatorToolGate.kt：本契约的诚实面将空转")
        assertTrue(
            gate.contains("const val OPERATOR_TOOL_FLAG = \"$EXPECTED_FLAG\""),
            "opt-in 开关名被改动（应为 $EXPECTED_FLAG）：文档与真机运行方式都要同步改，先确认再动",
        )
        assertTrue(
            gate.contains("-e \$OPERATOR_TOOL_FLAG") || gate.contains("-e $EXPECTED_FLAG"),
            "跳过理由里不再告诉操作者怎么显式点名（应写成 `-e $EXPECTED_FLAG true`，插值或硬编码都算）：" +
                "一次跳过会被当成绿灯",
        )
        assertTrue(
            gate.contains("跳过 ≠ 通过"),
            "跳过理由里删掉了「跳过 ≠ 通过」：与本仓反空转口径冲突",
        )
        assertTrue(
            gate.contains("assumeTrue"),
            "闸不再走 assumeTrue：请确认它没有变成 @Ignore（@Ignore 会让用例连跳过的痕迹都变轻）",
        )
    }

    private fun wordbookDir(): File =
        listOf(
            File("src/androidTest/java/com/example/englishlearning/wordbook"),
            File("app/src/androidTest/java/com/example/englishlearning/wordbook"),
        ).firstOrNull { it.isDirectory }
            ?: error("找不到 wordbook 的 androidTest 目录：本契约将空转，必须先修锚点")

    private fun strippedOf(name: String): String {
        val file = File(wordbookDir(), name)
        assertTrue(file.isFile, "读不到 $name：判据将空转")
        return stripComments(file.readText(Charsets.UTF_8))
    }

    /** 真实网络传输 = 会烧钱的工具类；纯假件/纯内存的用例类不在本契约范围内。 */
    private fun toolSources(): Map<String, String> =
        wordbookDir().listFiles { f -> f.isFile && f.extension == "kt" }
            ?.map { it.name to stripComments(it.readText(Charsets.UTF_8)) }
            ?.filter { (_, text) -> REAL_TRANSPORTS.any(text::contains) }
            ?.filterNot { (name, _) -> name == "OperatorToolGate.kt" }
            ?.toMap()
            ?: emptyMap()

    private fun unpaidToolViolations(sources: Map<String, String>): List<String> =
        sources.flatMap { (name, text) ->
            val methods = TEST_METHOD.findAll(text).toList()
            when {
                methods.isEmpty() -> listOf("$name：解析不出任何 @Test（解析面失效）")
                else -> methods.filterNot { match ->
                    match.groupValues[2].trimStart().lineSequence().take(2).any { line ->
                        line.trimStart().startsWith(GATE_CALL)
                    }
                }.map { match -> "${name}#${match.groupValues[1]}" }
            }
        }

    private companion object {
        const val EXPECTED_FLAG = "operatorTool"
        const val GATE_CALL = "assumeOperatorToolRequested("
        val REAL_TRANSPORTS = listOf("UrlConnectionAiHttpTransport(", "UrlConnectionAudioHttpTransport(")
        val TEST_METHOD = Regex(
            """@Test\b[\s\S]{0,160}?\bfun\s+`?([A-Za-z0-9_]+)`?\s*\(\s*\)\s*\{([\s\S]{0,240})"""
        )
    }
}
