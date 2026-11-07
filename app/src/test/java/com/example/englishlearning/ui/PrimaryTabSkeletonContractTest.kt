package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 批 C-2 一级页首帧契约（判据：`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 3 与
 * `docs/specs/05-release-quality-and-compliance.md` F5-05 / AC5-06）。
 *
 * 这一批把「骨架只住在选词书那一屏」改成「四枚一级 tab 的首帧各有交代」。开工实测的形状差是三件事：
 *
 *  - **学习 tab**（`TodayPlanScreen`）有 `Loading` 分支，但画的是 48dp 转圈 + 一句「正在准备今日计划…」——
 *    形状是空的，回填时页头下方整块长出一张强卡和四行，是版面跳变而不是渐进加载；
 *  - **阅读 tab**（`ReadingAccessScreen`）的 `Loading` 分支只有一行 `Text`，连形状都没有；
 *  - **我的 tab**（`ProfileScreen`）**根本没有 Loading 这一档**：宿主把还没读到的打卡快照写成
 *    `checkInReady?.streakDays ?: 0`，于是首帧播报「连续打卡 0 天 · 累计学过 0 个词」并把统计格画成 `0`。
 *    这正是 F1-01 / 2026-11-06 占位诚实化那条禁令在「数字」维度上的同一枚缺陷，只是换了承载；
 *  - **AI tab**（`AiLearningScreen`）实测**没有任何异步读盘**（功能目录是 `AiFeature.entries` 静态派生，
 *    屏侧不收 UiState），所以它不在铺面范围内——本契约用「静态页不许长出没有骨架的 Loading 分支」
 *    把它的位置钉住：将来真接入异步态，这条闸会要求同时铺骨架，而不是先长出一枚转圈。
 *
 * 因此本契约三族判据：
 *  1. **分支形状**：`Loading` 分支体内必须调用骨架族，且不得再出现 `CircularProgressIndicator(` / `Text(`
 *     （进行中语义由形状承担；文案位留给真值，见 F5-05 第 1、2 条）；
 *  2. **锚点与行数**：两枚屏各自的稳定 tag（`today_plan_loading` / `reading_access_loading`）恰好一处，
 *     骨架行数只许读**本屏的生产常量**而不是字面量（与 C-1 选词书屏同一口径），
 *     且那枚常量的**取值**必须等于本屏真实行数（[skeleton_row_constants_equal_the_real_row_counts_on_their_screen]，
 *     值判据用变异探针实测过可红：把 `TODAY_PLAN_NEXT_ROWS` 改成 5 → 本类恰一枚红）；
 *  3. **真值缺席不许冒充零**：`ProfileScreen` 三枚统计入参改成 `Int?`，宿主在快照 `Loading` 时传 `null`，
 *     屏侧画占位格；`Unavailable` 不在这一档（那是读到了「读不到」，沿用既有表态口径，本批不扩）。
 *
 * 另附一枚全仓棘轮 [spinner_ledger_is_exact_and_only_shrinks]：`CircularProgressIndicator(` 的剩余站点
 * 按「文件名: 折叠行」逐条点名，C-3/C-4 只能把这份台账改短、不许变长——这是「转圈只减不增」的可执行形式，
 * 也是批 C 收口时判定「铺面真的做完」的账本。
 *
 * 形状沿用本目录禁令族惯例（`MotionSpecContractTest`、`SkeletonFamilyContractTest`）：
 * 目录守卫走 [uiFiles] / [uiScreenSource]，覆盖面锚点走 [MIN_PRIMARY_TAB_SKELETON_CALL_SITES]，
 * 分支判定配一条合成双边（[arm_rule_flags_a_spinner_and_a_caption]）。
 * 已知局限如实记录：分支体判的是「字面形状 + 范围」，跨行写法（把骨架调进另一个私有函数）不在匹配面内，
 * 由代码评审与截图册兜住；观感（骨架高度是否贴近真卡）归批 C-6 的截图取证。
 */
class PrimaryTabSkeletonContractTest {

    /** 骨架族的五件基本形状（与 `SkeletonFamilyContractTest` 同一份名单，本处只用于调用点计数）。 */
    private val skeletonPrimitives = listOf(
        "SkeletonBar",
        "SkeletonRow",
        "SkeletonList",
        "SkeletonCard",
        "SkeletonStatGrid",
    )

    /** 四枚一级 tab 屏（`AppTab` 枚举顺序），本契约的扫描面。 */
    private val primaryTabScreens = listOf(
        "TodayPlanScreen.kt",
        "ReadingAccessScreen.kt",
        "AiLearningScreen.kt",
        "ProfileScreen.kt",
    )

    @Test
    fun `learning tab loading arm draws the skeleton family`() {
        val arm = loadingArm(
            fileName = "TodayPlanScreen.kt",
            marker = "TodayPlanUiState.Loading ->",
            endMarker = "TodayPlanUiState.MissingSetup ->",
        )
        val found = armOffenders("TodayPlanScreen.kt", arm)
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    @Test
    fun `reading tab loading arm draws the skeleton family`() {
        val arm = loadingArm(
            fileName = "ReadingAccessScreen.kt",
            marker = "ReadingAccessUiState.Loading ->",
            endMarker = "ReadingAccessUiState.Unavailable ->",
        )
        val found = armOffenders("ReadingAccessScreen.kt", arm)
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    @Test
    fun `primary tab screens carry no spinner at all`() {
        val found = primaryTabScreens.flatMap { name ->
            val hits = countMatches(uiScreenSource(name), spinnerCall)
            if (hits == 0) {
                emptyList()
            } else {
                listOf("$name 仍有 $hits 处 CircularProgressIndicator（含 import）：一级 tab 的首帧由骨架承担，转圈退役")
            }
        }
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    @Test
    fun `static ai tab keeps no loading arm of its own`() {
        // AI 屏没有异步读盘（功能目录静态派生）。这条判的不是「AI 屏不许有骨架」，而是
        // 「不许先长出进行中文案或转圈、骨架等下一批再补」：一旦本屏出现 Loading 分支，
        // 前两条分支判据的表就必须把它一起纳进来。
        val source = uiScreenSource("AiLearningScreen.kt")
        assertTrue(
            countMatches(source, loadingArmMarker) == 0,
            "AiLearningScreen 出现了 `Loading ->` 分支：本契约要求先把它加进 primaryTabScreens 的分支判据表并铺骨架，" +
                "静态页不许悄悄接一枚转圈进来。",
        )
    }

    @Test
    fun `skeleton row counts read this screen constant and keep the stable tags`() {
        val learning = uiScreenSource("TodayPlanScreen.kt")
        val reading = uiScreenSource("ReadingAccessScreen.kt")
        listOf(
            "TodayPlanScreen.kt" to learning,
            "ReadingAccessScreen.kt" to reading,
        ).forEach { (fileName, source) ->
            assertTrue(
                literalRows.findAll(source).toList().isEmpty(),
                "$fileName 有骨架行数写成字面量（`rows = 4` 这类）：行数必须读本屏的生产常量，" +
                    "否则真实列表改版时骨架与列表各改一处，回填会顶偏滚动位置（C-1 选词书屏同一口径）。",
            )
            assertTrue(
                constantRows.findAll(source).toList().isNotEmpty(),
                "$fileName 找不到 `rows = <常量>` 形状：说明骨架列表调用没接上，或被改成字面量（见上一条）。",
            )
        }
        assertTrue(
            countMatches(learning, Regex(Regex.escape("today_plan_loading"))) == 1,
            "学习 tab 的骨架锚 `today_plan_loading` 必须原样保留且恰好一处——connected `TodayPlanScreenTest.loading_has_stable_tag` 读它。",
        )
        assertTrue(
            countMatches(reading, Regex(Regex.escape("reading_access_loading"))) == 1,
            "阅读 tab 的骨架锚 `reading_access_loading` 缺一或不止一处：与学习 tab 同一形状，" +
                "connected 侧要有一枚按 tag 的存在性判据，不能只靠人眼。",
        )
    }

    /**
     * 骨架行的常量值必须等于本屏真实行数（把「同权威」从注释升级成判据）。
     *
     * 上一条只管到「写法是常量不是字面量」，管不到**值**：`TODAY_PLAN_NEXT_ROWS` 若是 3 而真实「接下来」
     * 有四行，写法全绿、回填照样顶偏滚动位置——而这类偏移恰恰是骨架唯一要防的那件事。
     *
     * 判的是「本屏 `BareRow(` 调用点数」而不是「那一组里的调用点数」：两屏 today 都只有骨架镜像的那一组
     * 裸行（学习 4 / 阅读 3），所以整屏计数就是那组的行数。局限如实记录——将来某屏在别处长出第二组裸行时
     * 本条会**红**而不是悄悄绿：那次红要求人判定「第二组该不该也进骨架、骨架行数该镜像哪一组」，
     * 正是想要的强制提醒；真要精确到组，等第二组真的出现再按分支切范围，不提前造一把只有一件东西的尺。
     */
    @Test
    fun `skeleton row constants equal the real row counts on their screen`() {
        mapOf(
            "TodayPlanScreen.kt" to "TODAY_PLAN_NEXT_ROWS",
            "ReadingAccessScreen.kt" to "READING_MORE_ROWS",
        ).forEach { (fileName, constName) ->
            val source = uiScreenSource(fileName)
            val declared = rowConstantValue(source, constName)
            val realRows = countMatches(source, bareRowCall)
            assertTrue(
                declared != null && declared == realRows,
                "$fileName 的骨架行数常量 `$constName = $declared` 与本屏真实裸行数（$realRows 处 `BareRow(`）不符：" +
                    "骨架比列表少一行或多一行，回填时滚动位置就会顶偏——同权威不只是「读常量」，还得起码读对。",
            )
        }
    }

    @Test
    fun `profile stats take a pending value and placeholder cells`() {
        val source = uiScreenSource("ProfileScreen.kt")
        listOf(
            "streakDays: Int?" to "连续打卡入参要允许「还没读到」",
            "learnedWordCount: Int?" to "已学词入参要允许「还没读到」",
            "articleCount: Int?" to "文章数入参要允许「还没读到」",
            "value: String?" to "统计格的数值位要允许占位",
            "SkeletonBar(" to "占位格只画形状条，不画数字",
            "listOfNotNull(" to "页头副标题要由「读到的那几枚」拼，不许拼一份 0",
        ).forEach { (anchor, why) ->
            assertTrue(
                anchor in source,
                "ProfileScreen 缺锚点 `$anchor`（$why）：三枚统计在快照未回来时必须是 null 而不是 0（F5-05 第 2 条）。",
            )
        }
        assertTrue(
            countMatches(source, Regex(Regex.escape("连续打卡 \$streakDays 天"))) == 0,
            "ProfileScreen 还在把 streakDays 直接插进副标题：null 会被插成「连续打卡 null 天」，" +
                "0 会被念成「连续 0 天」——两种都是在替用户下结论。",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("当前 \$value"))) == 0,
            "ProfileScreen 还在把可空的 value 直接插进 contentDescription：占位格对读屏必须念「正在统计」，" +
                "不能念「当前 null」。",
        )
    }

    @Test
    fun `host passes pending profile stats instead of coercing them to zero`() {
        val host = uiScreenSource("AppScreen.kt")
        listOf(
            "val profileStreakDays: Int?",
            "val profileLearnedWordCount: Int?",
            "val profileArticleCount: Int?",
            "streakDays = profileStreakDays",
            "learnedWordCount = profileLearnedWordCount",
            "articleCount = profileArticleCount",
        ).forEach { anchor ->
            assertTrue(
                countMatches(host, Regex(Regex.escape(anchor))) == 1,
                "AppScreen 的「$anchor」应恰好一处，实际 ${countMatches(host, Regex(Regex.escape(anchor)))} 处：" +
                    "我的页三枚统计的 pending 口径只在宿主派生一次，屏侧不得自己读仓库。",
            )
        }
        assertTrue(
            countMatches(host, Regex(Regex.escape("articleCount = readingState.historyOrEmpty.size"))) == 0,
            "AppScreen 仍把 `historyOrEmpty.size` 直接当文章数传给我的页：`Loading` 时那份 emptyList 就是假 0。",
        )
    }

    @Test
    fun `arm rule flags a spinner and a caption but passes a skeleton`() {
        // 合成双边之一：违例形状必须被命中（内存假源码，不碰生产文件）。
        val violating = """
            CircularProgressIndicator(color = colors.brandDeep)
            Text("正在准备今日计划…", style = AppType.Footnote)
        """.trimIndent()
        val found = armOffenders("FakeScreen.kt", violating)
        assertTrue(
            found.any { it.contains("没调用骨架族") },
            "分支判据漏判「没有骨架」：${found.joinToString()}",
        )
        assertTrue(
            found.any { it.contains("CircularProgressIndicator(") },
            "分支判据漏判转圈：${found.joinToString()}",
        )
        assertTrue(
            found.any { it.contains("Text(") },
            "分支判据漏判进行中文案：${found.joinToString()}",
        )
        // 合成双边之二：合规形状必须放行，注释里的字面量不得算 offender。
        val compliant = """
            // 旧形状：CircularProgressIndicator 加一句 Text("正在准备…")，随批 C-2 退役。
            SkeletonCard(modifier = Modifier.testTag("fake_loading"), minHeight = 120.dp)
            SkeletonList(rows = FAKE_NEXT_ROWS, rowHeight = 48.dp, tagPrefix = "fake_row_skeleton_")
        """.trimIndent()
        assertTrue(
            armOffenders("CompliantScreen.kt", compliant).isEmpty(),
            "合规分支被误判：${armOffenders("CompliantScreen.kt", compliant).joinToString()}",
        )
    }

    @Test
    fun `spinner ledger is exact and only shrinks`() {
        val actual = violations(
            label = "批 C 进行中站点台账",
            rule = spinnerCall,
            allowlist = emptySet(),
            scanDir = File(UI_MAIN_SOURCE_PATH),
        ).sorted()
        assertTrue(
            actual == SPINNER_LEDGER.sorted(),
            "转圈台账与现状不符（新增必须同时改台账 = 铺面在倒退；消失则说明 C-3/C-4 收了一处，" +
                "要把那一行从台账里删掉）。\n" +
                "当前 ${actual.size} 处，台账 ${SPINNER_LEDGER.size} 处。\n" +
                "只在当前：${actual.filterNot { it in SPINNER_LEDGER }.joinToString()}\n" +
                "只在台账：${SPINNER_LEDGER.filterNot { it in actual }.joinToString()}",
        )
        val skeletonCallSites = uiFiles().sumOf { file ->
            skeletonPrimitives.sumOf { name -> countMatches(file.readText(), Regex(Regex.escape("$name("))) }
        }
        assertTrue(
            skeletonCallSites >= MIN_PRIMARY_TAB_SKELETON_CALL_SITES,
            "骨架族调用点只有 $skeletonCallSites 处（下限 $MIN_PRIMARY_TAB_SKELETON_CALL_SITES）：" +
                "台账绿了但没人调用骨架，说明扫描根错位或铺面被整批摘掉。",
        )
    }

    /**
     * 一条 `Loading` 分支的**分支体**（不含分支头，止于下一条分支头）。
     * 三个锚都按字面量定位：分支改名/换序时本契约响亮失败，而不是读错范围后对着别处的代码绿。
     */
    private fun loadingArm(fileName: String, marker: String, endMarker: String): String {
        val source = uiScreenSource(fileName)
        val start = source.indexOf(marker)
        assertTrue(
            start >= 0,
            "$fileName 里找不到 `$marker` 分支：改名或整批搬家必须同时改本契约（分支判据会读错范围）。",
        )
        val rest = source.substring(start + marker.length)
        val end = rest.indexOf(endMarker)
        assertTrue(
            end >= 0,
            "$fileName 的 `Loading` 分支找不到收尾锚 `$endMarker`：分支顺序被改动，本契约的读取范围会吞进下一条分支。",
        )
        return rest.substring(0, end)
    }

    /** 分支体的三条禁令：没调用骨架族、还在转圈、还在写进行中文案。 */
    private fun armOffenders(fileName: String, arm: String): List<String> {
        val found = mutableListOf<String>()
        if (!arm.contains("Skeleton")) {
            found += "$fileName: Loading 分支没调用骨架族——首帧要么转圈要么空屏，F5-05 要求同形状骨架"
        }
        if (countMatches(arm, spinnerCall) > 0) {
            found += "$fileName: Loading 分支仍有 CircularProgressIndicator(——一级 tab 的进行中语义由形状承担"
        }
        if (countMatches(arm, textCall) > 0) {
            found += "$fileName: Loading 分支仍有 Text(——骨架零文案、零数字，文案位留给真值"
        }
        return found
    }

    private val spinnerCall = Regex("""(?<![A-Za-z0-9_])CircularProgressIndicator\(""")

    private val textCall = Regex("""(?<![A-Za-z0-9_])Text\(""")

    private val loadingArmMarker = Regex("""(?<![A-Za-z0-9_])Loading\s*->""")

    /** 骨架行数的两种写法：字面量（违例）与读本屏常量（合规）。 */
    private val literalRows = Regex("""rows\s*=\s*\d+""")

    private val constantRows = Regex("""rows\s*=\s*[A-Z][A-Z0-9_]{2,}""")

    /** 真实列表行：两屏的「接下来」/「更多」组都由裸行构成（`BareRow` 定义在组件层，本屏只有调用点）。 */
    private val bareRowCall = Regex("""(?<![A-Za-z0-9_])BareRow\(""")

    /** 读 `private const val <name> = <数字>` 的值；[uiScreenSource] 已剥注释，KDoc 里的提及不会算命中。 */
    private fun rowConstantValue(source: String, name: String): Int? =
        Regex(Regex.escape("const val $name") + """\s*=\s*(\d+)""")
            .find(source)
            ?.groupValues
            ?.get(1)
            ?.toInt()
}

/**
 * 批 C 进行中的全仓转圈台账。条目形状与 [violations] 的返回一致：
 * 「文件名: 折叠空白后的源码行」。C-3（本地读盘子页）与 C-4（网络/AI 等待屏）逐屏收口时**屏侧只能删行**；
 * 想加一行必须先在该屏的分支判据里补骨架，否则本闸判红。
 *
 * **唯一被预先声明的例外**（决策 2026-11-07 决定 3 的 C-4 段、`docs/specs/05` F5-05 第 4 条同口径）：
 * `components/RequestInProgress.kt` 那一行。批 C-4 把五处 AI 等待臂的活动指示**收进组件**，
 * 站点数因此不降反升一——升的是「同一枚指示只有一份实现」，不是新的转圈面：
 * 五臂各自画转圈的形状被 `AiWaitInProgressContractTest` 禁掉了（臂内出现任何进度指示即红），
 * 所以这一行是唯一落点，计数恰 1；多一枚就要在那条闸里一起改。
 *
 * 两枚 `BackupPairingExportScreen.kt` 是同一条折叠行的两次命中（配对页两态各一枚），
 * [violations] 刻意不做 distinct，台账因此按多重集比对而不是集合比对。
 *
 * 剩下的站点不是「还没排到」，而是**已判定不该换骨架**的那两类（决策 2026-11-07 决定 3 的三分类）：
 * 「动作进行中」——接下来落地的是回执而不是这个形状的内容，画骨架等于预告一个不会以那个形状落地的东西，
 * 所以保留活动指示 + 真话文案；「网络 / AI 等待」——归 C-4 的进行中语义，指示器住在组件里。逐行理由写在行尾。
 */
private val SPINNER_LEDGER = listOf(
    // 进行中族的唯一落点：五处 AI 等待臂的活动指示都从这里出，屏侧不许再自己画（见上一段与 AiWaitInProgressContractTest）。
    "RequestInProgress.kt: CircularProgressIndicator(",
    // 动作进行中：配对码生成/等待扫码，接下来落的是配对回执，不是这一屏的形状。
    "BackupPairingExportScreen.kt: CircularProgressIndicator(color = settingsPalette.deep)",
    "BackupPairingExportScreen.kt: CircularProgressIndicator(color = settingsPalette.deep)",
    // 动作进行中：恢复校验/写入，等待的是「成功或回滚」这两句回执。
    "BackupRestoreScreen.kt: CircularProgressIndicator(color = appDomain(AppDomain.Settings).deep)",
    // 动作进行中：`GlobalSearchProgress` 一支两用（Loading 查本地词库 / ClearingHistory 清空历史）。
    // 清空历史等的是回执，保留转圈；同屏 Loading 那一支属「等数据」，C-3 收它时把它拆出骨架，
    // 这一行因此**留在台账里**——删行的判据是「这处转圈的调用面只剩动作进行中」。
    "GlobalVocabularySearchScreen.kt: CircularProgressIndicator(color = colors.text2)",
    // 动作进行中：`rendering` 生成 PDF 预览（判据是「正在生成 / 还没有页面」），等待的是页面图与落盘回执，
    // 骨架预告不了「第 N 页长什么样」。
    "WorksheetPreviewScreen.kt: CircularProgressIndicator(color = colors.strongSurface)",
    // C-3 已收三屏（生词复习 / 词书单词 / 词卡流，均为「等数据」→ 骨架），台账按「一条一屏」逐屏删行；
    // 剩余的 `GlobalVocabularySearchScreen` / `Backup*` / `WorksheetPreview` 四行是**已判定不该换骨架**的豁免站点。
)

/**
 * 覆盖面下限：C-2 铺完两枚一级页后，`ui/` 根下骨架族的调用点数不会低于这个量
 * （族内互相调用 + 选词书迁移 + 学习/阅读两屏 + 我的页占位格）。
 * 它不是「铺完了」的证明——那是台账归零那一天的事——只防扫描根错位导致的空扫。
 */
private const val MIN_PRIMARY_TAB_SKELETON_CALL_SITES = 12
