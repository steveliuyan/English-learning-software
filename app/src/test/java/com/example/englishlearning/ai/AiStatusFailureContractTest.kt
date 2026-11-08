package com.example.englishlearning.ai

import com.example.englishlearning.ui.APP_MAIN_SOURCE_PATH
import com.example.englishlearning.ui.countMatches
import com.example.englishlearning.ui.relativePathFrom
import com.example.englishlearning.ui.uiFiles
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AC2-15（决策 `docs/decisions/2026-11-08-http-403-auth-failure-single-source.md` §6）：
 * 「401 与 403 同属密钥被拒」这条判据**只许存在于一处权威**。
 *
 * 为什么要有这个类：N-1 的根因不是谁打错了一个数字，而是同一条规则在带密钥的响应路径上
 * 被内联抄了六遍、每一遍都漏了 403——只有 `AiConnectionProbe` 写了，于是「测试连接」报
 * 「鉴权失败」、真正干活那几屏报「返回内容不合格」。行为用例只钉得住**当下这六处**；
 * 下一处新代码再内联一份，就得靠本类在提交前拦住。
 *
 * 三条判据合起来才完整，缺一条都会假绿：
 *  ① 内联鉴权判据的**文件集合**等于白名单（权威 + 一枚登记在案的例外）——防「长出第二份」；
 *  ② 权威文件里那条 `401, 403 -> AiFailure.Unauthorized` **逐字还在**——防「把六处删干净、
 *     又把 403 从权威里删掉」这种两边同时失明的收法；
 *  ③ 七个消费点各调一次 `AiStatusFailure.of(`——防「抽了权威但没人用」的空转。
 *
 * 扫描一律把**原始源码**交给 `countMatches`（它内部自己剥注释），本类不预先剥：
 * 二次剥注释会让字符串字面量里的块注释闭合序列被当成真闭合、吞掉后面的代码（本仓既有教训，
 * 那条字面量在本类里就只能用文字指代——写出来会先把这段注释提前关掉）。
 */
class AiStatusFailureContractTest {

    private companion object {
        /** 扫描根：整个 `main` 源码树，不只 `ai/`——会长出内联判据的地方谁也不保证在哪个包。 */
        val mainRoot: File = File(APP_MAIN_SOURCE_PATH)

        /** 唯一权威。 */
        const val AUTHORITY = "ai/AiStatusFailure.kt"

        /**
         * 唯一登记在案的例外：`GeneratedImageStore` 下载的是 CDN 图片 URL、**不带密钥**，
         * 那里的鉴权码说的是「链接不可取」。理由见决策 §5 与该文件内的注释。
         */
        const val REGISTERED_EXCEPTION = "imagegen/GeneratedImageStore.kt"

        val allowedInlineSites = setOf(AUTHORITY, REGISTERED_EXCEPTION)

        /** 六个带密钥的 AI 响应路径 + 探活（后者只借鉴权档，429/5xx 仍归「服务返回错误状态」）。 */
        val consumerSites = listOf(
            "wordqa/WordQaResponseParser.kt",
            "reading/ArticleResponseParser.kt",
            "sentence/SentenceAnalysisParser.kt",
            "phototranslation/PhotoTranslationParser.kt",
            "imagegen/ImageGenerationResponseParser.kt",
            "imagegen/ImageGenerationUseCase.kt",
            "ai/AiConnectionProbe.kt",
        )

        /** 当判据写的 401 或 403（含 `401, 403 ->` 这种并列形状）——即「内联鉴权档」。 */
        val inlineAuthCase = Regex("""\b40[13](?:\s*,\s*40[13])?\s*->""")

        val authorityRule = Regex(Regex.escape("401, 403 -> AiFailure.Unauthorized"))
        val consumptionShape = Regex(Regex.escape("AiStatusFailure.of("))

        fun mainSources(): Map<String, String> =
            uiFiles(mainRoot, "AI 状态码映射契约扫描根").associate { it.relativePathFrom(mainRoot) to it.readText() }

        /**
         * 纯函数：给定「相对路径 → 原始源码」，返回把鉴权档内联在了白名单之外的文件。
         * 抽成函数是为了能用合成源码当场验它**会报**（见 [theRuleFlagsAPlantedInlineAuthCase]）。
         */
        fun offenders(rawSources: Map<String, String>): List<String> =
            rawSources
                .filter { (path, source) -> path !in allowedInlineSites && countMatches(source, inlineAuthCase) > 0 }
                .keys
                .sorted()

        fun consumptionCounts(rawSources: Map<String, String>): Map<String, Int> =
            consumerSites.associateWith { path -> countMatches(rawSources.getValue(path), consumptionShape) }
    }

    @Test
    fun theAuthRuleExistsInOneAuthorityPlusOneRegisteredException() {
        val sources = mainSources()
        // 响亮守卫：路径漂了就当场说，不让 walkTopDown 的空序列变成「零违规」的假绿。
        assertTrue(
            sources.size >= 60,
            "`main` 源码扫描面只有 ${sources.size} 个文件，明显不是全树（扫描根 ${mainRoot.path} 漂移？）",
        )
        for (path in allowedInlineSites + consumerSites) {
            assertTrue(path in sources, "白名单/消费点里的文件没被扫到，路径漂移：$path")
        }

        assertEquals(
            emptyList(),
            offenders(sources),
            "鉴权档（401/403）被内联抄在了权威之外：每条内联判据都是下一枚「漏了 403」的候选。" +
                "带密钥的 AI 响应请调 `AiStatusFailure.of(`；确属无密钥通道（如 CDN 取图）才允许例外，" +
                "且必须先回决策 §5 把它登记进 `REGISTERED_EXCEPTION`",
        )
        // 白名单不许悄悄变胖，也不许悄悄失去意义：权威之外内联鉴权档的文件必须恰是那一枚例外。
        val inlineOutsideAuthority = sources
            .filter { (path, source) -> path != AUTHORITY && countMatches(source, inlineAuthCase) > 0 }
            .keys
            .sorted()
        assertEquals(
            listOf(REGISTERED_EXCEPTION),
            inlineOutsideAuthority,
            "白名单与实扫结果不一致：要么长出了第二处内联判据，要么 $REGISTERED_EXCEPTION 的例外已不再需要、" +
                "该从本类删掉（删之前先回决策 §5 记一笔）",
        )
    }

    @Test
    fun theAuthorityStillCarriesBothAuthCodes() {
        val authority = File("$APP_MAIN_SOURCE_PATH/$AUTHORITY").readText()

        assertEquals(
            1,
            countMatches(authority, authorityRule),
            "权威里那条 `401, 403 -> AiFailure.Unauthorized` 必须逐字成立：403 掉出鉴权档就是 N-1 复发，" +
                "而本类第一条「白名单外零内联」判据会跟着一起变成假绿（谁都没写过 403，自然没人漏写）",
        )
        // 三档一起钉，防止「只留鉴权档」把 429/5xx 退回各消费点自己判。
        assertEquals(
            1,
            countMatches(authority, Regex(Regex.escape("429 -> AiFailure.RateLimited"))),
            "限流档必须留在权威里，否则消费点又要各自补一支",
        )
        assertEquals(
            1,
            countMatches(authority, Regex(Regex.escape("in 500..599 -> AiFailure.ServerUnavailable"))),
            "服务端档必须留在权威里，同上",
        )
    }

    @Test
    fun everyKeyBearingPathActuallyConsumesTheAuthority() {
        val perSite = consumptionCounts(mainSources())

        assertEquals(
            emptyList(),
            perSite.filter { (_, count) -> count != 1 }.entries.map { (path, count) -> "$path ×$count" },
            "每个消费点调 `AiStatusFailure.of(` **恰好一次**：零次 = 那屏又回到自己判（N-1 复发的形状），" +
                "两次 = 同一份状态码被判了两遍，中间任何不一致都没人管",
        )
        assertEquals(
            consumerSites.size,
            perSite.values.sum(),
            "消费点总调用数应为 ${consumerSites.size}（六处带密钥响应 + 探活借鉴鉴权档）",
        )
    }

    @Test
    fun theRuleFlagsAPlantedInlineAuthCaseInsteadOfQuietlyPassing() {
        val planted = mapOf(
            // must-flag：新写的第 N+1 处内联判据，两种形状都要被点名。
            "ai/SomeNewParser.kt" to "fun f(code: Int) = when (code) {\n    401 -> FAIL\n    else -> OK\n}",
            "ui/AnotherParser.kt" to "fun f(code: Int) = when (code) {\n    401, 403 -> FAIL\n    else -> OK\n}",
            // must-not-flag：合法消费（调权威）不算内联，注释里的历史说明也不算。
            "wordqa/WordQaResponseParser.kt" to "AiStatusFailure.of(httpStatusCode)?.let { throw AiException(it) }",
            "sentence/CommentOnly.kt" to "// 这里曾把 401 -> Unauthorized 各抄一遍（决策 2026-11-08）\nval x = 1",
            "reading/BlockComment.kt" to "/* 曾有 403 落进内容不合格 */\nAiStatusFailure.of(code)",
        )

        assertEquals(
            listOf("ai/SomeNewParser.kt", "ui/AnotherParser.kt"),
            offenders(planted),
            "判据对合成违规失明：白名单外的内联鉴权档必须被点名，否则本类只是装饰",
        )
        assertEquals(
            3,
            planted.keys.size - offenders(planted).size,
            "三枚「应放行」的夹具被误判了：调权威与注释里的历史说明都不是内联判据",
        )
    }

    @Test
    fun theConsumerRulerCountsRealCallsAndNotMentions() {
        val planted = mapOf(
            "wordqa/WordQaResponseParser.kt" to "AiStatusFailure.of(httpStatusCode)?.let { throw AiException(it) }",
            "ai/ZeroConsumption.kt" to "// 本该调 AiStatusFailure.of( 但没调\nval x = 1",
            "ui/DoubleConsumption.kt" to "AiStatusFailure.of(a)\nAiStatusFailure.of(b)",
        )
        val counts = planted.mapValues { (_, source) -> countMatches(source, consumptionShape) }

        assertEquals(
            mapOf("wordqa/WordQaResponseParser.kt" to 1, "ai/ZeroConsumption.kt" to 0, "ui/DoubleConsumption.kt" to 2),
            counts,
            "消费点计数必须数得到真调用、且不被注释里的名字骗到：0 与 2 这两种形状都得被" +
                "[everyKeyBearingPathActuallyConsumesTheAuthority] 判红",
        )
    }
}
