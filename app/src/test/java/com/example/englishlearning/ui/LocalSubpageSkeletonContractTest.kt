package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 批 C-3 本地读盘子页首帧契约（判据：`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 3
 * 的三分类 + `docs/specs/05` F5-05）。
 *
 * C-2 把四枚一级 tab 收干净之后，剩下的等待面全在**子页**：那一层实测有 9 处 `CircularProgressIndicator`，
 * 逐处读完上下文后分成三拨（决策册决定 3 原文只写了「本地读盘换骨架 / 网络 AI 归 C-4」两类，
 * 实测发现还有第三拨，本轮按实测口径执行并把分类补回决策册）：
 *
 *  - **等数据**：首帧该有真实形状 → 本契约逐屏钉「loading 单元调骨架族、不含转圈、不含 `Text(`」；
 *  - **动作进行中**（认证中 / 导出中 / 恢复中 / 正在清空历史 / 正在生成 PDF）：接下来落的是回执而不是这个形状的内容，
 *    画骨架就是预告一个不会以那个形状落地的东西 → **保留活动指示 + 真话文案**，
 *    并在 `PrimaryTabSkeletonContractTest` 的转圈台账里逐条写明豁免理由；
 *  - **网络 / AI 等待** → C-4（进行中语义，本契约不管）。
 *
 * 一条一屏、按屏追加：每条都在**该屏开工前**先落地为红，改完转绿，避免一次铺六屏再回头找哪屏没接上。
 * 单元体的读取沿用 C-2 的做法（按字面量锚切范围，锚改名即响亮失败），因为「整个文件里没有转圈」
 * 那种粗判会把别处合法的豁免一起误杀。[LoadingUnit] 的两枚锚既可以是私有函数头，也可以是 `when` 分支头
 * ——判的是「那一帧画的那一段」，与它住在哪种语法壳里无关。
 *
 * 与 C-2 的一处口径差异如实写明：C-2 那两屏的骨架行数**等于**源码里可数的真实行数（版面常量组，
 * 值判据在 `PrimaryTabSkeletonContractTest`）；本批三屏（学习记录 / 词书单词 / 查词结果）的行数是数据量，
 * 首帧根本不知道有几条，所以 [skeleton row counts read the screen constant not a literal] 只钉**写法**
 * （读常量、不写字面量），取值由每屏常量自己的算术交代（填满列表区那一屏、不铺出滚动条）。
 */
class LocalSubpageSkeletonContractTest {

    /**
     * 一屏的 loading 单元：[fileName] 里从 [startMarker] 到 [endMarker] 的那一段就是首帧画的东西。
     * 两个锚都是**函数头 / 分支头级别**的字面量：它们改名的含义就是「这一帧换了实现」，必须同时改这里。
     */
    private data class LoadingUnit(
        val fileName: String,
        val label: String,
        val startMarker: String,
        val endMarker: String,
    )

    /** 本批已收的屏（顺序即开工顺序；C-3 继续时往这里加）。 */
    private val converted: List<LoadingUnit> = listOf(
        LoadingUnit(
            fileName = "LearningRecordsScreen.kt",
            label = "学习记录列表",
            startMarker = "private fun LoadingState()",
            endMarker = "private fun ErrorState",
        ),
        LoadingUnit(
            fileName = "WordCardScreen.kt",
            label = "词卡流首帧",
            startMarker = "WordCardUiState.Loading -> WordCardStateBody {",
            endMarker = "WordCardUiState.MissingSetup ->",
        ),
        LoadingUnit(
            fileName = "VocabularyRelearnScreen.kt",
            label = "生词复习首帧",
            startMarker = "VocabularyRelearnUiState.Idle, VocabularyRelearnUiState.Loading -> Column(",
            endMarker = "VocabularyRelearnUiState.Unavailable -> Column(",
        ),
        LoadingUnit(
            fileName = "WordBookWordsScreen.kt",
            label = "词书单词首帧",
            // 锚只切到分支头为止：把 `SkeletonList(` 写进锚里，锚自己就把这一帧唯一需要的证据字符吃掉了
            // （判据读的是「分支体里有没有骨架」，锚读到 `state.loading ->` 就该停）。
            startMarker = "state.loading ->",
            endMarker = "state.error != null -> Column(",
        ),
        LoadingUnit(
            fileName = "GlobalVocabularySearchScreen.kt",
            label = "查词结果首帧",
            startMarker = "GlobalVocabularySearchUiState.Loading ->",
            endMarker = "GlobalVocabularySearchUiState.ClearingHistory ->",
        ),
        LoadingUnit(
            fileName = "WordBookTransferScreen.kt",
            label = "词书内容包首帧",
            // 用户 2026-11-10 点名的那一处（「加载词书的那个要有预加载骨架」）：uiState 首帧还没到之前
            // 两枚子行是**禁用态 + 「正在处理，请稍候…」**，那是拿假状态顶替等待。判的是这一帧，
            // 锚到 else 分支头为止（`SkeletonList(` 不能写进锚里——锚会把这一帧唯一的证据字符吃掉）。
            startMarker = "if (state == null && viewModel != null) {",
            endMarker = "} else {",
        ),
        LoadingUnit(
            fileName = "ArticleReadingScreen.kt",
            label = "文章正文首帧",
            startMarker = "if (current == null) {",
            endMarker = "} else {",
        ),
    )

    /**
     * 每屏的稳定加载锚。三枚是本批新长的（那些屏的 loading 那一支原本只有裸 `Text` 或转圈，
     * 没有可寻址形状）。connected 侧按 tag 判「占位形状真的落地」，所以锚必须恰好一处——
     * 丢了就没法判，复制成两枚就是同一帧两个加载态。
     */
    private val anchors: Map<String, String> = mapOf(
        "LearningRecordsScreen.kt" to "learning_records_loading",
        "WordCardScreen.kt" to "word_card_loading",
        "WordBookWordsScreen.kt" to "wordbook_words_loading",
        "VocabularyRelearnScreen.kt" to "relearn_loading",
        "GlobalVocabularySearchScreen.kt" to "global_search_loading",
        "ArticleReadingScreen.kt" to "article_reading_loading",
        "WordBookTransferScreen.kt" to "word_book_transfer_loading",
    )

    /** 骨架行的占位行数只许读本屏常量的屏与常量名（数据量型列表：钉写法，不钉与真实条数相等）。 */
    private val rowCountConsts: Map<String, String> = mapOf(
        "LearningRecordsScreen.kt" to "LEARNING_RECORDS_PLACEHOLDER_ROWS",
        "WordBookWordsScreen.kt" to "WORD_BOOK_WORDS_PLACEHOLDER_ROWS",
        "GlobalVocabularySearchScreen.kt" to "GLOBAL_SEARCH_PLACEHOLDER_ROWS",
        "WordBookTransferScreen.kt" to "WORD_BOOK_TRANSFER_PLACEHOLDER_ROWS",
    )

    @Test
    fun `converted loading units draw skeletons without spinners or captions`() {
        val found = converted.flatMap { unit ->
            val body = unitBody(unit)
            val offenders = mutableListOf<String>()
            if (!body.contains("Skeleton")) {
                offenders += "没有调用骨架族（首帧仍是转圈或空块）"
            }
            if (countMatches(body, spinnerCall) > 0) {
                offenders += "仍有 CircularProgressIndicator("
            }
            if (countMatches(body, textCall) > 0) {
                offenders += "仍有 Text(（骨架零文案、零数字）"
            }
            offenders.map { "${unit.fileName} 的「${unit.label}」loading 单元不合格：$it" }
        }
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    @Test
    fun `converted units keep their stable loading anchors`() {
        anchors.forEach { (fileName, tag) ->
            val hits = countMatches(uiScreenSource(fileName), Regex(Regex.escape(tag)))
            assertTrue(
                hits == 1,
                "$fileName 的稳定锚 `$tag` 有 $hits 处（应恰 1）：它是这一帧唯一的可寻址形状，" +
                    "换骨架时既不能丢（connected 读它），也不能复制成两枚（同帧两个加载态）。",
            )
        }
    }

    @Test
    fun `skeleton row counts read the screen constant not a literal`() {
        rowCountConsts.forEach { (fileName, constName) ->
            val source = uiScreenSource(fileName)
            assertTrue(
                Regex(Regex.escape("rows = $constName")) in source,
                "$fileName 的骨架行数没有读 `$constName`：真实列表行数与骨架行数必须同权威（C-2 同一口径）。",
            )
            assertTrue(
                literalRows.findAll(source).toList().isEmpty(),
                "$fileName 出现 `rows = <数字>` 字面量：骨架行数必须读本屏常量。",
            )
            assertTrue(
                Regex(Regex.escape("const val $constName")) in source,
                "$fileName 缺常量 `$constName` 的定义。",
            )
        }
    }

    /**
     * 镜像能力之一：骨架族允许关掉行首的圆点标记。`WordRow` 那一族真实行**没有前导图标**
     * （词头在左列、状态词在右列），骨架照抄时多画一枚圆点就是「形状不像」——而像不像正是 F5-05 唯一的判据。
     * 所以这不是装饰开关：必须有且只有一份实现，且确实被屏侧用上（参数长出来没人用等于没这条能力）。
     */
    @Test
    fun `the row skeleton can drop the leading mark and at least one screen does`() {
        assertMirrorCapability(
            paramName = "leading",
            callForm = "leading = false",
            label = "无前导图标的行（WordRow 那一族）",
        )
    }

    /**
     * 镜像能力之二：骨架族允许把一行画成**有壳的卡行**。`WordBookWordsScreen` 的真实行是
     * `Card(surface + 1dp 描边)` 里套 `WordRow`，行与行之间是 10dp 缝而不是一条发丝线；
     * 用无壳行铺那一屏，首帧就少一层壳、缝也错档。判据与 [converted] 那两条同构，走同一个 helper。
     */
    @Test
    fun `the row skeleton can be framed like a card row and at least one screen is`() {
        assertMirrorCapability(
            paramName = "framed",
            callForm = "framed = true",
            label = "带壳的卡行（Card 包 WordRow 那一族）",
        )
    }

    /** 镜像能力的两条判据：族里恰两枚声明（`SkeletonRow` 定义 + `SkeletonList` 透传），且屏侧确有调用点。 */
    private fun assertMirrorCapability(paramName: String, callForm: String, label: String) {
        val family = uiComponentSource("Skeleton.kt")
        val declarations = countMatches(family, Regex("""$paramName\s*:\s*Boolean"""))
        assertTrue(
            declarations == 2,
            "骨架族的 `$paramName: Boolean`（$label）应恰两枚（`SkeletonRow` 与 `SkeletonList` 各一处），" +
                "实际 $declarations 处：少一枚就没法镜像那一档真实形状，多一枚就是在族里长出第二套开关。",
        )
        val callers = uiFiles().filter { file ->
            stripComments(file.readText()).contains(callForm)
        }
        assertTrue(
            callers.isNotEmpty(),
            "没有任何屏用 `$callForm`：参数长出来却没人用，等于下一屏又开始照不圆的形状画" +
                "（本批它服务的是「$label」）。",
        )
    }

    /** 合成双边：违例形状必须被命中，合规形状必须放行（不碰生产文件）。 */
    @Test
    fun `the unit rule flags a spinner and passes a skeleton`() {
        val violating = """
            private fun LoadingState() {
                CircularProgressIndicator(color = colors.brandDeep)
                Text("正在读取学习记录…")
            }
            private fun ErrorState() {}
        """.trimIndent()
        val body = violating.substringAfter("private fun LoadingState()").substringBefore("private fun ErrorState")
        assertTrue(
            countMatches(body, spinnerCall) == 1 && countMatches(body, textCall) == 1,
            "违例形状没被命中：转圈或 Text 计数不对，判据形同虚设。",
        )
        val compliant = """
            private fun LoadingState() {
                SkeletonList(rows = LEARNING_RECORDS_PLACEHOLDER_ROWS, rowHeight = 48.dp, tagPrefix = "x_", leading = false)
            }
            private fun ErrorState() {}
        """.trimIndent()
        val good = compliant.substringAfter("private fun LoadingState()").substringBefore("private fun ErrorState")
        assertTrue(
            good.contains("Skeleton") && countMatches(good, spinnerCall) == 0 && countMatches(good, textCall) == 0,
            "合规形状被误判。",
        )
    }

    private fun unitBody(unit: LoadingUnit): String {
        val source = uiScreenSource(unit.fileName)
        val start = source.indexOf(unit.startMarker)
        assertTrue(
            start >= 0,
            "${unit.fileName} 里找不到 `${unit.startMarker}`：loading 单元改名或搬家必须同时改本契约，" +
                "否则这条判据会读着别处的代码绿。",
        )
        val rest = source.substring(start + unit.startMarker.length)
        val end = rest.indexOf(unit.endMarker)
        assertTrue(
            end >= 0,
            "${unit.fileName} 的 loading 单元找不到收尾锚 `${unit.endMarker}`：范围会吞进下一个函数。",
        )
        return rest.substring(0, end)
    }

    private val spinnerCall = Regex("""(?<![A-Za-z0-9_])CircularProgressIndicator\(""")

    private val textCall = Regex("""(?<![A-Za-z0-9_])Text\(""")

    private val literalRows = Regex("""rows\s*=\s*\d+""")
}
