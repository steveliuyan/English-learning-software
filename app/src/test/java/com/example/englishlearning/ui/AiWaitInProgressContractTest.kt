package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 批 C-4 网络 / AI 等待屏的「进行中语义」契约（判据：
 * `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 3 的 C-4 段与 §4，
 * `docs/specs/05-release-quality-and-compliance.md` F5-05 第 4 条 / AC5-06）。
 *
 * ## 为什么这一批不是「把文案改漂亮」
 *
 * 开工实测（决策册 §2.6）：五处 AI 等待臂里四处只有一行裸 `Text`（`正在询问…` / `正在分析…` /
 * `正在生成画面描述…` / `正在生成图片…` / `正在生成…`），拍照翻译的 AI 翻译阶段**连这一行都没有**
 * ——`busy` 只由识别路径写 `true`，翻译在飞时界面零回执、按钮照旧可点，再点一次是
 * `translateJob?.cancel()` 后重发。而四个 VM 里 `private var *Job` **零处**：没有句柄就没有取消，
 * 「可取消」不是文案能改出来的。所以本契约钉的四件事全部有对应的生产改动：
 *
 *  1. **臂必须调用唯一组件** `RequestInProgress(` 且**显式给 `onCancel =`**：
 *     组件的 `onCancel` 没有默认值，屏侧若能给默认空实现，就等于画一枚按了没反应的取消键——
 *     那是本批要消灭的假象，不是它的替代品（`onCancelArms` 判字面 `onCancel =` 的写法，不判组件内部）。
 *  2. **进行中面不许伪装成结果**：臂内不得出现骨架族 / 任何进度指示 / 百分比 / 秒数，
 *     也不得出现「已生成 / 已有 N 篇 / 共 N 条」这类假快照字样
 *     （`2026-11-06-placeholder-text-is-not-learning-content` 同源）。
 *     时长口径同理不许自立：`timeout_seconds` 的唯一权威在 `ai/AiRequestPolicy`，
 *     映射到 `AiFailure.Timeout →「等待超时，稍后再试。」`；等待文案写一个数就是第二个权威，且必然是假承诺。
 *  3. **说清在向谁请求**：`message` 字面量必须含「AI 服务」（与既有费用提示同一口径）。
 *     **不要求**写域名——域名级确认由 `OutboundConfirmDialog` 逐次承担（AC2-14），
 *     等待面既拿不到权威 host，也不该在此处补一句可能过期的域名。
 *  4. **取消的陈述只有一句、且全仓只有一份**：那句「取消只是不再等待这次结果；请求可能已经发出」
 *     收在组件里，屏侧不得各写一份（四份私写就是漏字的来历，见 `SentenceAnalysisScreen` 的 U-1 注释）；
 *     同时不许写成「取消后不会发送」——AC2-11 的「取消即零出站」管的是**未确认时不发送**，
 *     对已出站的请求那句话就是假话。
 *
 * ## 已知局限（如实记录）
 *
 * 分支体判据读的是**字面形状与范围**：把组件调用藏进另一条私有函数会让本闸看不见它，
 * 由代码评审与 §4 的 connected/截图那半条兜住；「取消真的停了」在 JVM 侧由 VM 用例
 * （挂起假 transport + 迟到响应不回写）量，字节层面不在本契约射程内。
 */
class AiWaitInProgressContractTest {

    /** 一枚等待臂：屏文件、分支头、下一条分支头、稳定锚 tag、取消键 tag、以及 message 里必须出现的操作词。 */
    private data class WaitArm(
        val fileName: String,
        val startMarker: String,
        val endMarker: String,
        val statusTag: String,
        val cancelTag: String,
        val operationWord: String,
        val label: String,
    )

    /**
     * 本批的六枚臂（五屏，生图两臂）。
     *
     * C-4b 收前两屏时这张表只有两行，理由是「那一臂还不存在，提前入表只会得到一条找不到分支头的红，
     * 把尚未实现误报成实现漂移」。C-4c 把剩下四枚臂真的建出来了（拍照翻译那枚此前连分支都没有，
     * 随 `state.translating` 补上），所以表补满六行——**这张表现在是全覆盖**，
     * 新增第七枚 AI 等待臂而不入表，本契约不会替谁兜着（决策 §4 那半条口径）。
     */
    private val arms = listOf(
        WaitArm(
            fileName = "WordAiQaScreen.kt",
            startMarker = "WordAiQaUiState.Asking ->",
            endMarker = "is WordAiQaUiState.Answered ->",
            statusTag = "word_qa_asking",
            cancelTag = "word_qa_cancel",
            operationWord = "问",
            label = "AI 问词",
        ),
        WaitArm(
            fileName = "SentenceAnalysisScreen.kt",
            startMarker = "SentenceAnalysisUiState.Analyzing ->",
            endMarker = "is SentenceAnalysisUiState.Analyzed ->",
            statusTag = "sentence_analysis_analyzing",
            cancelTag = "sentence_analysis_cancel",
            operationWord = "分析",
            label = "长难句分析",
        ),
        WaitArm(
            fileName = "ReadingAccessScreen.kt",
            startMarker = "GenerationUiState.Generating ->",
            endMarker = "GenerationUiState.StorageFailed ->",
            statusTag = "generation_status",
            cancelTag = "generation_cancel",
            operationWord = "生成",
            label = "今日文章生成",
        ),
        WaitArm(
            fileName = "ImageStudioScreen.kt",
            startMarker = "ImageStudioUiState.DraftingPrompt ->",
            endMarker = "is ImageStudioUiState.PromptReady ->",
            statusTag = "image_studio_drafting",
            cancelTag = "image_studio_cancel_drafting",
            operationWord = "描述",
            label = "画面描述起草",
        ),
        WaitArm(
            fileName = "ImageStudioScreen.kt",
            startMarker = "ImageStudioUiState.GeneratingImage ->",
            endMarker = "is ImageStudioUiState.ImageReady ->",
            statusTag = "image_studio_generating",
            cancelTag = "image_studio_cancel_image",
            operationWord = "图片",
            label = "生图",
        ),
        WaitArm(
            fileName = "PhotoTranslationScreen.kt",
            startMarker = "if (state.translating) {",
            endMarker = "when (val issue = state.translationIssue)",
            statusTag = "photo_translation_translating",
            cancelTag = "photo_translation_cancel_translate",
            operationWord = "翻译",
            label = "拍照翻译的 AI 翻译",
        ),
    )

    @Test
    fun `every ai wait arm goes through the single in-progress component`() {
        val found = arms.flatMap { arm ->
            offenders(arm.label, armBody(arm))
        }
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    /**
     * 两枚稳定锚必须真的从臂里传进去。
     *
     * 为什么单独钉这一条而不是只靠 connected：connected 用例按 tag 取节点，tag 拼错只会让**那一屏**的用例红；
     * 而本契约读的是臂体字面量，`statusTestTag =` / `cancelTestTag =` 缺任一档，组件会退化成用默认 tag
     * 或干脆不画取消键——那种漂移在 connected 侧可能表现为「取到了另一个节点」而不是「取不到」。
     * 逐臂点名两枚锚，等于把「设备侧要按哪个 tag 找它」写进唯一权威表。
     */
    @Test
    fun `every ai wait arm passes both stable anchors the connected cases read`() {
        val found = arms.mapNotNull { arm ->
            val body = armBody(arm)
            val missing = listOfNotNull(
                """statusTestTag = "${arm.statusTag}"""".takeUnless { it in body },
                """cancelTestTag = "${arm.cancelTag}"""".takeUnless { it in body },
            )
            if (missing.isEmpty()) null else "${arm.label}: 臂内缺 $missing"
        }
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    @Test
    fun `every ai wait message names the configured service and the operation`() {
        val found = arms.flatMap { arm ->
            val body = armBody(arm)
            val message = messageLiteral(body)
            when {
                message == null -> listOf("${arm.label}: 臂内取不到 `message = \"…\"` 字面量：进行中句必须是常量形状，否则读不到在请求什么")
                "AI 服务" !in message -> listOf("${arm.label}: 进行中句「$message」没说清在向谁请求（缺「AI 服务」，与既有费用提示同一口径）")
                arm.operationWord !in message -> listOf("${arm.label}: 进行中句「$message」没说清在做什么（缺「${arm.operationWord}」）")
                FAKE_RESULT_WORDS.any { it in message } -> listOf("${arm.label}: 进行中句「$message」出现假结果字样（$message）")
                else -> emptyList()
            }
        }
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    /**
     * 活动指示的唯一落点。
     *
     * `uiComponentSource` 自带「文件恰好一个」的守卫，所以组件被改名 / 搬走 / 复制成两枚时先在那里响；
     * 这里再钉「那一枚 `CircularProgressIndicator(` 恰 1 处」：一个组件里画两枚转圈，
     * 台账那一条「唯一落点」就只剩形式了。
     */
    @Test
    fun `the activity indicator has exactly one home outside the ledger screens`() {
        val source = uiComponentSource("RequestInProgress.kt")
        val inComponent = countMatches(source, spinnerCall)
        assertTrue(
            inComponent == 1,
            "RequestInProgress.kt 里 `CircularProgressIndicator(` 有 $inComponent 处，期望恰好 1 处：" +
                "进行中族的活动指示只许有一枚，多一枚就是要有人决定哪枚代表什么（F5-05 第 4 条）。",
        )
        val inArms = arms.map { arm ->
            val hits = countMatches(armBody(arm), spinnerCall) + countMatches(armBody(arm), barCall) +
                countMatches(armBody(arm), ringCall)
            if (hits == 0) {
                null
            } else {
                "${arm.label}: 等待臂里自己画了 $hits 枚进度指示——本批要求收进组件，臂只给文案与取消"
            }
        }.filterNotNull()
        assertTrue(inArms.isEmpty(), inArms.joinToString("\n"))
    }

    /**
     * 取消那句陈述全仓只有一份，且必须是「不再等待」而不是「不会发送」。
     *
     * 两向都判：`不会发送 / 不会发出 / 零出站` 出现在组件里就是假话（AC2-11 的射程是未确认时不发送），
     * 而「不再等待」缺一即说明那句话被改写成了别的承诺。
     */
    @Test
    fun `the cancel statement is honest and lives only in the component`() {
        val source = uiComponentSource("RequestInProgress.kt")
        assertTrue(
            countMatches(source, Regex(Regex.escape("不再等待"))) == 1,
            "组件里的取消陈述应恰好一处「不再等待…」，实际 ${countMatches(source, Regex(Regex.escape("不再等待")))} 处：" +
                "多处就是在同一屏念两遍取消语义，零处就是取消位退化成了按了没反应的按钮。",
        )
        val lying = LIE_WORDS.mapNotNull { word ->
            val hits = countMatches(source, Regex(Regex.escape(word)))
            if (hits == 0) null else "组件里出现「$word」$hits 处：对已出站的请求说「不会发送」是假话（AC2-11 管的是未确认时不发送）"
        }
        assertTrue(lying.isEmpty(), lying.joinToString("\n"))
        val duplicatedInArms = arms.mapNotNull { arm ->
            val body = armBody(arm)
            if ("不再等待" in body) "${arm.label}: 臂内自己重抄了取消陈述——那句只许住在组件里" else null
        }
        assertTrue(duplicatedInArms.isEmpty(), duplicatedInArms.joinToString("\n"))
    }

    /**
     * 宿主真的把取消接到了 VM。
     *
     * 判 `AppScreen.kt` 里对 `cancel…()` 的调用次数：AI 问词在宿主有**两枚**调用点（词卡详情两处入口），
     * 少接一处就是「那一处的取消键按了没反应」，而屏侧那条判据看不见宿主。
     * 这里刻意判**方法名**而不是整行字面量：宿主那两处的 surrounding 代码不同（一处带 profileId 派生），
     * 整行判据会逼着两边写成同一行，那是形式不是接线。
     */
    @Test
    fun `host wires each cancel entry to the view model that owns the request`() {
        val host = uiScreenSource("AppScreen.kt")
        listOf(
            "wordQaViewModel?.cancelAsk()" to 2,
            "sentenceAnalysisViewModel?.cancelAnalysis()" to 1,
            "readingAccessViewModel?.cancelGeneration()" to 1,
            "imageStudioViewModel?.cancelGeneration()" to 1,
            "photoTranslationViewModel?.cancelTranslate()" to 1,
        ).forEach { (anchor, expected) ->
            val hits = countMatches(host, Regex(Regex.escape(anchor)))
            assertTrue(
                hits == expected,
                "AppScreen 里 `$anchor` 应恰好 $expected 处，实际 $hits 处：取消键必须指向真正持有在飞请求的那件事，" +
                    "漏一处就是那个入口的取消只改界面不停请求。",
            )
        }
    }

    @Test
    fun `arm rule flags a bare caption and passes the component call`() {
        // 合成双边之一：旧形状（一行裸文案）必须被命中——本批的红就是从这里来的。
        val bare = """
            Text(text = "正在询问…", style = AppType.Footnote, modifier = Modifier.testTag("word_qa_asking"))
        """.trimIndent()
        val found = offenders("假想屏", bare)
        assertTrue(
            found.any { it.contains("RequestInProgress(") },
            "分支判据漏判「只有一行裸文案」：${found.joinToString()}",
        )
        assertTrue(
            found.any { it.contains("onCancel =") },
            "分支判据漏判「没有取消位」：${found.joinToString()}",
        )
        // 合成双边之二：合规形状必须放行，注释里的字面量不得算 offender。
        val compliant = """
            // 旧形状：Text("正在分析…")，随批 C-4 退役；这一行不该被算成违例。
            RequestInProgress(
                message = "正在向你配置的 AI 服务分析这句英文",
                onCancel = onCancelAnalysis,
                statusTestTag = "sentence_analysis_analyzing",
                cancelTestTag = "sentence_analysis_cancel",
            )
        """.trimIndent()
        assertTrue(offenders("假想合规屏", compliant).isEmpty(), offenders("假想合规屏", compliant).joinToString("\n"))
    }

    /** 一枚臂的**分支体**（不含分支头，止于下一条分支头）；锚按字面量找，改名即响亮失败。 */
    private fun armBody(arm: WaitArm): String {
        val source = uiScreenSource(arm.fileName)
        val start = source.indexOf(arm.startMarker)
        assertTrue(
            start >= 0,
            "${arm.fileName} 里找不到 `${arm.startMarker}` 分支：改名或整批搬家必须同时改本契约（分支判据会读错范围）。",
        )
        val rest = source.substring(start + arm.startMarker.length)
        val end = rest.indexOf(arm.endMarker)
        assertTrue(
            end >= 0,
            "${arm.fileName} 的 `${arm.startMarker}` 分支找不到收尾锚 `${arm.endMarker}`：分支顺序被改动，本契约的读取范围会吞进下一条分支。",
        )
        return rest.substring(0, end)
    }

    /** 臂体的四条禁令：没调组件、没给取消、没有稳定锚、自己画了进度形状或写了裸文案。 */
    private fun offenders(label: String, body: String): List<String> {
        val found = mutableListOf<String>()
        if (!body.contains("RequestInProgress(")) {
            found += "$label: 等待臂没调用 RequestInProgress(——进行中语义的四件东西只许在一处给齐（F5-05 第 4 条）"
        }
        if (countMatches(body, Regex(Regex.escape("onCancel ="))) == 0) {
            found += "$label: 等待臂没有 `onCancel =`——取消入口必须显式接线，组件不给默认空实现就是为了这条能被看见"
        }
        if (countMatches(body, skeletonCall) > 0) {
            found += "$label: 等待臂里出现骨架族——骨架承诺「马上出现的就是这个形状」，AI 生成失败率高、时长不可知，画它就是假内容"
        }
        if (countMatches(body, textCall) > 0) {
            found += "$label: 等待臂里还有裸 `Text(`——文案位由组件承担，屏侧只给 message 与取消"
        }
        return found
    }

    /** 取臂内 `message = "…"` 的字面量（进行中句必须是常量形状，插值句读不到承诺内容）。 */
    private fun messageLiteral(body: String): String? =
        Regex("""message\s*=\s*"([^"]*)"""").find(body)?.groupValues?.get(1)

    private val spinnerCall = Regex("""(?<![A-Za-z0-9_])CircularProgressIndicator\(""")

    private val barCall = Regex("""(?<![A-Za-z0-9_])LinearProgressIndicator\(""")

    private val ringCall = Regex("""(?<![A-Za-z0-9_])ProgressRing\(""")

    private val skeletonCall = Regex("""Skeleton(?:Bar|Row|List|Card|StatGrid)\(""")

    private val textCall = Regex("""(?<![A-Za-z0-9_])Text\(""")

    /** 假快照字样：等待面一旦说出「已经有几篇/几条」，就是在替还没回来的结果下结论。 */
    private val FAKE_RESULT_WORDS = listOf("已生成", "已有", "共 ", "篇")

    /** 对已出站请求说不可能的话。 */
    private val LIE_WORDS = listOf("不会发送", "不会发出", "零出站", "尚未发送")
}
