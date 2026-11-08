package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 D-2 转场契约闸（判据：计划批 D 节 D-2 Step 1 三条 + 决策 §1.3-1、§3.1）。
 *
 * 这一段**零动画**，所以本契约钉的全是「链搬完之后行为不许变」的静态形状：
 *
 *  1. **判据不残留**（[chainHasNoMountLevelFlagConditionsAnymore]）：链区每一条顶层
 *     `if (` / `} else if (` 的条件文本都必须改读 route（`route is AppOverlayRoute.…`，tab 宿主是
 *     `route == null`），原来那 35 枚挂载级原判据必须整体下线。
 *     扫描是**正向白名单**，不是「残留标志位」负则：35 支里有 4 支的条件文字
 *     （`selectedFeature == AiFeature.X` 三支 + `readingTarget != null || historyArticle != null`）
 *     落在 `} else if ((?:show…|selected… != null|setupRequired)` 这类模式之外，
 *     照负则写闸会让这 4 支残留静默过关——批 A 的「反空转」纪律要防的正是这个形状。
 *  2. **route 全覆盖**（[everyRouteVariantIsConsumedByExactlyOneBranch]）：`AppOverlayRoute.kt` 的
 *     35 枚变体每一枚在 `AppScreen.kt` 剥注释后恰好一枚 `is AppOverlayRoute.…` 消费点；
 *     枚数不等于 `OVERLAY_ROUTE_COUNT` 直接报「route 表被改而本契约未同步」，解析到 0 枚同样判红。
 *  3. **链序 = resolver 序**（[chainBranchOrderEqualsResolverArmOrder]）：链上按出现顺序取出的 route 名
 *     （末支再补 tab 宿主）与 `AppOverlayRouteResolver.kt` 里 `when` 的分支顺序逐行相等，
 *     且等价于 `mountIndex` 0..34 升序。链的声明序就是 z 序与返回键优先级（QA3 A1 的许可/关于就是这条），
 *     D-2 之后原判据文字下线，这条顺序只剩本闸能钉。
 *
 * 扫描纪律（Global Constraints 四条齐备）：路径常量 + 文件数响亮守卫 + 合成 must-flag/must-not-flag 双边 +
 * 反空转消费点计数；剥注释只用 `SourceRatchet` 的 [stripComments]，每份源码**只剥一次**（二次剥会把
 * 字面量里的斜杠星号序列当注释起点，本批实测过的假红形状）。
 *
 * 已知局限（如实登记，不为判不到的面做假测试）：条件按「单行整行、括号内不含括号」取样；
 * 将来有人把某支条件拆行或加括号，本闸按「扫描盲区」响亮判红而不是静默跳过。
 */
class ScreenTransitionContractTest {

    /** 路径常量 + 文件存在守卫（读空/读不到都响亮失败，不许让契约对着空文本绿）。 */
    private fun readSource(path: String): String {
        val file = File(path)
        assertTrue(
            file.isFile,
            "转场契约源码缺失（期望 $path，相对 JVM 工作目录 ${File(".").absolutePath}）：" +
                "包名重构 / 工作目录变化都会让本契约静默读空，必须先修此处路径。",
        )
        val text = file.readText()
        assertTrue(text.isNotBlank(), "$path 读出来是空文件：空文件上的所有判据都是空转。")
        return text
    }

    /** 文件数响亮守卫：目标文件在 `ui/` 扫描根下必须恰一枚（0 = 搬家，≥2 = 同名冒名）；返回该文件供调用方直接读。 */
    private fun assertExactlyOne(name: String): File {
        val hits = uiFiles().filter { it.name == name }
        assertTrue(
            hits.size == 1,
            "$name 应在 $UI_MAIN_SOURCE_PATH 下恰好一枚，实际 ${hits.size} 枚" +
                "（0 枚 = 文件搬家让本契约读不到，≥2 枚 = 同名文件互相冒名顶替）。",
        )
        return hits.single()
    }

    // ---------------------------------------------------------------------------------------------
    // 判据 1：链上不许残留挂载级原判据（正向白名单）
    // ---------------------------------------------------------------------------------------------

    @Test
    fun chainHasNoMountLevelFlagConditionsAnymore() {
        val scan = chainScan(readSource(APP_SCREEN_SOURCE_PATH))
        assertTrue(
            scan.branches.isNotEmpty(),
            "链区一条顶层 `if (` / `} else if (` 都没取到：挂载链被改名/搬走/拆形，" +
                "此时本判据对着空档放行不是合规（批 A 反空转纪律）。",
        )

        val offenders = scan.branches.filterNot { it.matchesRouteWhitelist || it.condition == TAB_HOST_CONDITION || it.isExempted }
        assertTrue(
            offenders.isEmpty(),
            "挂载链上仍残留 ${offenders.size} 枚挂载级原判据（应全部改读 route）：\n" +
                offenders.joinToString("\n") { "  ${it.diagnostic}" } +
                "\n口径：链的顶层条件只能是 `route is AppOverlayRoute.…`，tab 宿主是 `route == null`；" +
                "那 35 枚原判据的逐字原文住在 AppOverlayRouteResolver 的 when 臂里，改判据请两边同步。",
        )

        assertEquals(
            OVERLAY_ROUTE_COUNT + 1,
            scan.branches.size,
            "链区取到 ${scan.branches.size} 支顶层条件（应为 $OVERLAY_ROUTE_COUNT 支 route + 1 支 tab 宿主 " +
                "`route == null`；链头在册：${scan.headFound}，链缩进 ${scan.indent} 空格）。" +
                "支数变化 = 挂载层级变化，必须先回计划批 D 的 route 表改口径，再动本契约。",
        )
        assertTrue(
            scan.blindSpots.isEmpty(),
            "链区有 ${scan.blindSpots.size} 行 `} else if (` 落在单行整行取样之外（前若干行：${scan.blindSpots.take(3)}）：" +
                "条件被拆成多行或括号套括号时本扫描读不到，判据就会静默放宽，故响亮判红——" +
                "把形状改回单行，或同步升级本取样。",
        )
        assertEquals(
            OVERLAY_ROUTE_COUNT,
            scan.branches.count { it.matchesRouteWhitelist },
            "链上 route 分支与 route 表的 $OVERLAY_ROUTE_COUNT 枚不等（少一支 = 那一屏根本打不开）。",
        )
        assertEquals(
            1,
            scan.branches.count { it.condition == TAB_HOST_CONDITION },
            "tab 宿主分支（`route == null`）必须恰一支：0 支 = 四个一级 tab 没有落点，≥2 支 = 出现了第二套宿主。",
        )
        assertEquals(
            TAB_HOST_CONDITION,
            scan.branches.last().condition,
            "tab 宿主必须是链的**最后一**支：`if / else if` 先声明者赢，宿主一旦排前面就盖掉所有二级屏。",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 判据 2：route 表 35 枚变体各有且仅有一个消费点
    // ---------------------------------------------------------------------------------------------

    @Test
    fun everyRouteVariantIsConsumedByExactlyOneBranch() {
        assertExactlyOne("AppScreen.kt")
        assertExactlyOne("AppOverlayRoute.kt")
        val appScreen = stripComments(readSource(APP_SCREEN_SOURCE_PATH))
        val variants = routeVariants(stripComments(readSource(ROUTE_SOURCE_PATH)))
        assertTrue(
            variants.isNotEmpty(),
            "从 AppOverlayRoute.kt 解析到 0 枚 route 变体：route 表被改形而本契约读不到，判据 2 空转。",
        )
        assertEquals(
            OVERLAY_ROUTE_COUNT,
            variants.size,
            "route 表被改（解析到 ${variants.size} 枚）而本契约未同步：OVERLAY_ROUTE_COUNT = $OVERLAY_ROUTE_COUNT。" +
                "枚数变化就是挂载层级变化，先回计划批 D 的 route 表改口径。",
        )
        variants.forEach { variant ->
            val consumed = Regex("""\bis AppOverlayRoute\.${Regex.escape(variant)}\b""").findAll(appScreen).count()
            assertEquals(
                1,
                consumed,
                "`AppOverlayRoute.$variant` 在 AppScreen.kt 剥注释后应有恰一枚 `is AppOverlayRoute.$variant` 消费点，" +
                    "实际 $consumed 枚（0 = 这一屏没有落点，用户点进去白屏；≥2 = 先声明那支顶掉后一支，同一 route 挂两处）。",
            )
        }
        val total = Regex("""\bis AppOverlayRoute\.""").findAll(appScreen).count()
        assertEquals(
            OVERLAY_ROUTE_COUNT,
            total,
            "AppScreen.kt 里 `is AppOverlayRoute.` 实测 $total 枚，应恰 $OVERLAY_ROUTE_COUNT 枚：" +
                "挂载判据只有链这一处，长出第二处就是长出第二套层级口径。",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 判据 3：链的分支顺序 == resolver 的 when 分支顺序（== mountIndex 0..34 升序）
    // ---------------------------------------------------------------------------------------------

    @Test
    fun chainBranchOrderEqualsResolverArmOrder() {
        assertExactlyOne("AppOverlayRouteResolver.kt")
        val chainNames = chainScan(readSource(APP_SCREEN_SOURCE_PATH))
            .branches
            .mapNotNull { ROUTE_NAME.find(it.condition)?.groupValues?.get(1) }
        assertTrue(
            chainNames.isNotEmpty(),
            "链上取到 0 支 `route is AppOverlayRoute.…`，与 resolver 的 $OVERLAY_ROUTE_COUNT 支无从对齐：" +
                "D-2 接线之前本判据就该红（它钉的是「搬完之后的顺序」）。",
        )
        assertEquals(
            OVERLAY_ROUTE_COUNT,
            chainNames.size,
            "链上取到 ${chainNames.size} 支 route，与 route 表的 $OVERLAY_ROUTE_COUNT 枚不等，顺序判据拒绝比对。",
        )

        val resolver = stripComments(readSource(RESOLVER_SOURCE_PATH))
        val resolverNames = Regex("""->\s*AppOverlayRoute\.(\w+)""").findAll(resolver).map { it.groupValues[1] }.toList()
        assertEquals(
            OVERLAY_ROUTE_COUNT,
            resolverNames.size,
            "AppOverlayRouteResolver 的 when 臂取到 ${resolverNames.size} 枚，应为 $OVERLAY_ROUTE_COUNT 枚。",
        )
        assertEquals(
            1,
            Regex("""else -> null""").findAll(resolver).count(),
            "resolver 必须恰一枚 `else -> null`（tab 宿主的落点），否则链末的 `route == null` 无源可依。",
        )

        chainNames.forEachIndexed { index, name ->
            assertEquals(
                resolverNames[index],
                name,
                "链的第 $index 支是 `$name`，resolver 的第 $index 枚臂是 `${resolverNames[index]}`——" +
                    "链序就是 z 序与返回键优先级（QA3 A1 的许可/关于正是这条），重排链 = 改产品行为，" +
                    "要改顺序必须先回决策文档重开。",
            )
        }
        val mountIndexes = routeMountIndexes(stripComments(readSource(ROUTE_SOURCE_PATH)))
        val declaredOrder = mountIndexes.entries.sortedBy { it.value }.map { it.key }
        assertEquals(
            (0 until OVERLAY_ROUTE_COUNT).toList(),
            mountIndexes.values.sorted(),
            "route 表的 mountIndex 不是 0..${OVERLAY_ROUTE_COUNT - 1} 各一枚：$mountIndexes",
        )
        assertEquals(
            declaredOrder,
            chainNames,
            "链序与 route 声明序（mountIndex 0..34 升序）不一致：$chainNames",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 唯一豁免：AppScreen.kt:580–584 的 overlayOpen（按文件名 + 原文行区间钉死）
    // ---------------------------------------------------------------------------------------------

    @Test
    fun overlayOpenIsTheOnlyExemptionAndStaysFlagBased() {
        assertEquals(
            1,
            MOUNT_CONDITION_EXEMPTIONS.size,
            "挂载判据的豁免名单只许这一条（决策 §1.3-1）；要新增豁免必须先回决策文档立项。",
        )
        val exemption = MOUNT_CONDITION_EXEMPTIONS.single()
        assertEquals("AppScreen.kt", exemption.fileName, "豁免按文件名 + 行区间钉死，别处的同形代码不许冒领。")

        val rawLines = readSource(APP_SCREEN_SOURCE_PATH).lines()
        val window = rawLines.subList(exemption.firstLine - 1, exemption.lastLine).joinToString(" ")
        assertTrue(
            window.contains("val overlayOpen ="),
            "豁免行区间 ${exemption.fileName}:${exemption.firstLine}–${exemption.lastLine} 已不再指向 overlayOpen" +
                "（链的行号会漂，但这枚豁免钉的就是这一行；名单之外一律判红，行区间变了就必须显式改这里）。",
        )
        assertTrue(
            !window.contains("route"),
            "overlayOpen 不许改成读 `route`：它问的是「有没有覆盖层标志位亮着」（含两枚不在链上的页内浮层位 " +
                "selectedArticleCard / showWordQaCard，且不含 `&& todayReady != null` 那类守卫），" +
                "与「挂载了哪一支」是两个不同问题（决策 §1.3-1）。",
        )
        val appScreen = stripComments(readSource(APP_SCREEN_SOURCE_PATH))
        assertEquals(
            1,
            Regex("""val overlayOpen =""").findAll(appScreen).count(),
            "overlayOpen 声明恰一枚；多一枚就是第二套「有没有覆盖层」口径。",
        )
        assertTrue(
            Regex("""BackHandler\(enabled = !overlayOpen""").findAll(appScreen).toList().isNotEmpty(),
            "overlayOpen 的消费者是低优先级返回键那枚 BackHandler（:587）；它一旦不被消费，" +
                "本豁免的理由就失效，必须重开决策而不是改名单。",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 合成双边（must-flag / must-not-flag）：判据本体不许空转
    // ---------------------------------------------------------------------------------------------

    @Test
    fun judgementOneFlagsResidualFlagConditionsAndPassesRouteConditions() {
        val residualScan = chainScan(
            chainFixture(
                listOf(
                    "route is AppOverlayRoute.VocabularyDetail",
                    "route == null",
                    "selectedFeature == AiFeature.SENTENCE_ANALYSIS",
                ),
            ),
        )
        assertEquals(
            3,
            residualScan.branches.size,
            "合成夹具取样失真：${residualScan.branches.map { it.condition }}",
        )
        assertEquals(
            listOf("selectedFeature == AiFeature.SENTENCE_ANALYSIS"),
            residualScan.branches.filterNot { it.matchesRouteWhitelist || it.condition == TAB_HOST_CONDITION }
                .map { it.condition },
            "判据 1 漏判枚举比较形的残留原判据——正是负则模式放过的那 4 支形状。",
        )

        listOf(
            "selectedFeature == AiFeature.PHOTO_TRANSLATION",
            "selectedFeature == AiFeature.IMAGE_STUDIO",
            "readingTarget != null || historyArticle != null",
            "showReviewQueue && todayReady != null",
            "setupRequired || showSetup",
            "showSettings",
        ).forEach { condition ->
            val offenders = chainScan(chainFixture(listOf(condition, TAB_HOST_CONDITION)))
                .branches
                .filterNot { it.matchesRouteWhitelist || it.condition == TAB_HOST_CONDITION }
            assertEquals(
                listOf(condition),
                offenders.map { it.condition },
                "判据 1 对残留形状 `$condition` 失明（负则模式下会静默过关的那一类）。",
            )
        }

        val compliant = chainScan(
            chainFixture(listOf("route is AppOverlayRoute.Settings", "route is AppOverlayRoute.About", TAB_HOST_CONDITION)),
        )
        assertEquals(3, compliant.branches.size, "合规链形取样失真：${compliant.branches.map { it.condition }}")
        assertTrue(
            compliant.branches.filterNot { it.matchesRouteWhitelist || it.condition == TAB_HOST_CONDITION }.isEmpty(),
            "合规链形被判成残留：判据 1 过严会把正常重构卡死。",
        )

        val commentedOnly = """
            @Composable
            fun FakeHost() {
                Column {
                    // } else if (showSettings) { ← 注释里的旧判据不算残留
                    if (route is AppOverlayRoute.Settings) {
                        SettingsScreen(onBack = {})
                    } else if (route == null) {
                        TabHost()
                    }
                }
            }
        """.trimIndent()
        assertEquals(
            listOf("route is AppOverlayRoute.Settings", TAB_HOST_CONDITION),
            chainScan(commentedOnly).branches.map { it.condition },
            "注释里的 `} else if (showSettings)` 被当成链上的一支：说明取样没走 stripComments（或剥了两次）。",
        )
    }

    @Test
    fun judgementTwoAndThreeSampleTheirOwnAuthorityShapes() {
        val twoVariants = """
            sealed interface FakeRoute {
                data object Settings : FakeRoute
                data class About(val name: String) : FakeRoute
            }
        """.trimIndent()
        assertEquals(
            listOf("Settings", "About"),
            routeVariants(stripComments(twoVariants)),
            "route 变体解析形状失真：判据 2 会拿着错的名单去数消费点。",
        )
        assertTrue(
            routeVariants(stripComments("    // data object Commented : FakeRoute")).isEmpty(),
            "注释里的 route 声明被解析成真变体：判据 2 会被注释喂饱（空转）。",
        )

        val chainNames = chainScan(
            chainFixture(listOf("route is AppOverlayRoute.About", "route is AppOverlayRoute.Licenses", TAB_HOST_CONDITION)),
        ).branches.mapNotNull { ROUTE_NAME.find(it.condition)?.groupValues?.get(1) }
        val resolverNames = Regex("""->\s*AppOverlayRoute\.(\w+)""")
            .findAll(
                """
                fun resolve(): AppOverlayRoute? = when {
                    flags.showLicenses -> AppOverlayRoute.Licenses
                    flags.showAbout -> AppOverlayRoute.About
                    else -> null
                }
                """.trimIndent(),
            )
            .map { it.groupValues[1] }
            .toList()
        assertEquals(listOf("Licenses", "About"), resolverNames, "resolver 臂序取样失真：判据 3 无从比对。")
        assertEquals(
            listOf("About", "Licenses"),
            chainNames,
            "链序取样失真：判据 3 读不到链的顺序就等于没写。",
        )
        assertTrue(
            chainNames != resolverNames,
            "合成夹具没造出「链序 ≠ resolver 序」的形状，判据 3 的双边用例等于没写。",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 批 D-3 四条：站点数 / 规格唯一落点 / 位移口径 / 规格面合成双边（本批唯一可见变化的静态形状闸）
    // 编号①②③④⑤齐全：④ 是修复轮 1 补的 brief Step 9 ④，⑤ 是 controller-carried 的 payload 读者计数。
    // ---------------------------------------------------------------------------------------------

    /**
     * 判据 D-3-①：转场站点全 `ui/` 子树恰两枚，且两枚都住在 `AppScreen.kt`（站 1 包链、站 2 包 tab 分发），
     * 两站 `transitionSpec` 都交给 `AppTransition.contentTransform`。
     * 修复轮 1 把「数站点」从只看 `AppScreen.kt` 扩成**扫全子树**：第三枚站点若长在别的屏文件里，
     * 旧口径读不到，而它就是第三套转场宿主（同判据 ② 的失明面）。
     * 计数只认 `AnimatedContent(`，不认 `AnimatedVisibility(`（`GlassOverlay.kt` 的浮层淡入淡出是另一个 API，
     * 本批末段用一条反证钉住两者不得混记，免得哪天把「组件显隐」也数成站点）。
     */
    @Test
    fun appScreenHasExactlyTwoTransitionStations() {
        assertExactlyOne("AppScreen.kt")
        // 每份源码只剥一次注释，剥完的表既当站点名单也当宿主正文（避免对 AppScreen.kt 二次剥注释）。
        val scannedByName = uiFiles().map { it.name to stripComments(it.readText()) }
        val stations = scannedByName.map { (name, stripped) ->
            name to Regex("""(?<![A-Za-z0-9_])AnimatedContent\(""").findAll(stripped).count()
        }.filter { it.second > 0 }
        assertEquals(
            2,
            stations.sumOf { it.second },
            "全 ui/ 子树剥注释后应恰有两枚 AnimatedContent（站 1 包住整条链、站 2 包住 when(selectedTab)）。" +
                "0 枚 = 转场根本没接上，≥3 枚 = 长出第三个站点（第二套转场宿主）。实测：$stations",
        )
        assertEquals(
            listOf("AppScreen.kt"),
            stations.map { it.first }.distinct(),
            "两枚 AnimatedContent 站点都必须住在 AppScreen.kt（转场只有这两站）；实测散落在：$stations",
        )
        val animatedVisibilityHosts = scannedByName.count {
            Regex("""(?<![A-Za-z0-9_])AnimatedVisibility\(""").containsMatchIn(it.second)
        }
        assertTrue(
            animatedVisibilityHosts >= 1,
            "全 ui/ 子树扫不到任何 AnimatedVisibility(：站点计数器把两枚 API 混成一枚口径了（本判据只数 AnimatedContent，" +
                "浮层淡入淡出归 AnimatedVisibility，两者混记就会让第三套宿主冒充合法站点）。",
        )
        val appScreen = scannedByName.first { it.first == "AppScreen.kt" }.second
        assertEquals(
            2,
            Regex("""AppTransition\.contentTransform\(""").findAll(appScreen).count(),
            "两站各一枚、共两枚 transitionSpec 必须走 AppTransition.contentTransform；" +
                "就地写 transitionSpec 就是在宿主里长出第二份规格。",
        )
        assertEquals(
            2,
            Regex("""transitionSpec\s*=""").findAll(appScreen).count(),
            "transitionSpec 只许两站各一枚（多一枚 = 长出第三个 AnimatedContent 站点）。",
        )
    }

    /**
     * 判据 D-3-②：转场规格面（位置参数形态的 `tween(AppMotion.Slow` + `slide{In,Out}Horizontally` +
     * `togetherWith`）在生产侧的唯一落点是 `theme/AppTransition.kt`，扫描范围是**整个 `ui/` 子树**
     * （修复轮 1 补口：旧版只看 `AppScreen.kt` 一枚文件名，第二套规格换个文件就隐形）；
     * 两站宿主 `AppScreen.kt` 另加「不得有任何 `tween(` / 不得直读 `AppMotion.Slow`」两条。
     * 这是防回归闸（决策 §3.3「全仓唯一转场落点」），改码前后都该绿；它的牙齿由判据 ④ 的合成双边自证。
     * 注意本条**不**泛化到 `AppMotion.Slow` 全仓唯一——`PlanBar`/`Shimmer` 的段宽/循环周期读 Slow 属批 C，不在转场口径内。
     */
    @Test
    fun transitionSpecSurfaceLivesOnlyInAppTransition() {
        assertExactlyOne("AppTransition.kt")
        val offenders = uiFiles().flatMap { transitionSpecOffenders(it.name, stripComments(it.readText())) }
        assertTrue(
            offenders.isEmpty(),
            "转场规格面（tween(AppMotion.Slow / slideIn/OutHorizontally / togetherWith）只能住在 theme/AppTransition.kt" +
                "（AppScreen.kt 作为两站宿主另禁就地 tween( 与 AppMotion.Slow）：\n" + offenders.joinToString("\n"),
        )
    }

    /**
     * 判据 D-3-②/④ 的取样本体（纯函数：文件名 + **已剥注释**的源码 → offender 清单；每份源码只剥一次注释）。
     *
     * 修复轮 1 把口径**扩到全 `ui/` 子树**（brief Step 9 ④ 的存在理由：旧口径按文件名只看 `AppScreen.kt`，
     * 第二份转场规格只要写在别的屏文件里就完全隐形）：
     *  - `theme/AppTransition.kt` 是唯一规格宿主，永不判红；
     *  - 其余任何 `ui/` 文件都不得承载三枚页面转场写法：位置参数形态的 `tween(AppMotion.Slow`、
     *    `slide{In,Out}Horizontally(`、`togetherWith`；
     *  - `AppScreen.kt` 作为两站宿主另加两条更严的禁令：不得出现任何 `tween(`，也不得直读 `AppMotion.Slow`
     *    （宿主就地抄规格 = 第二套时长口径）。
     *
     * **有意放行**（如实收窄，不假装「`AppMotion.Slow` 全仓唯一」那种做不到的绝对口径，见决策 §3.3 与批 C）：
     *  - 具名参数形态 `tween(durationMillis = AppMotion.Slow, …)`——`components/PlanBar.kt:92/97` 的段宽**属性动画**；
     *  - 不在 `tween(` 里的 `AppMotion.Slow` 算术——`components/Shimmer.kt:40/46` 的铺光循环周期；
     *  两者都不是页面转场规格，判红就是把转场口径伸进批 C 的账。双侧都由
     *  [judgementFourFlagsSyntheticScreenBodySpecAndPassesOverlayOpen] 的合成夹具钉住。
     */
    private fun transitionSpecOffenders(fileName: String, stripped: String): List<String> {
        if (fileName == TRANSITION_SPEC_HOST_NAME) return emptyList()
        return buildList {
            if (Regex("""(?<![A-Za-z0-9_])tween\(\s*AppMotion\.Slow\b""").containsMatchIn(stripped)) {
                add("$fileName: tween(AppMotion.Slow 页面转场时长")
            }
            if (Regex("""(?<![A-Za-z0-9_])slide(?:In|Out)Horizontally\(""").containsMatchIn(stripped)) {
                add("$fileName: slide{In,Out}Horizontally 页面位移")
            }
            if (Regex("""\btogetherWith\b""").containsMatchIn(stripped)) {
                add("$fileName: togetherWith 转场组装")
            }
            if (fileName == "AppScreen.kt") {
                if (Regex("""(?<![A-Za-z0-9_])tween\(""").containsMatchIn(stripped)) {
                    add("$fileName: tween( 就地时长")
                }
                if (stripped.contains("AppMotion.Slow")) add("$fileName: AppMotion.Slow 直读")
            }
        }
    }

    /** 判据 D-3-③：位移口径 ¼ 幅——四枚页面 slide 的 lambda 必须 `/ PageOffsetDenominator`（=4），出现整幅即红。 */
    @Test
    fun pageDisplacementIsQuarterAmplitude() {
        assertExactlyOne("AppTransition.kt")
        val host = stripComments(readSource(APP_TRANSITION_SOURCE_PATH))
        assertEquals(
            4,
            Regex("""(?<![A-Za-z0-9_])slide(?:In|Out)Horizontally\(""").findAll(host).count(),
            "AppTransition.kt 应有四枚页面 slide（Forward/Backward × Enter/Exit）；同层 Crossfade 只淡入淡出、不带位移。",
        )
        assertEquals(
            4,
            Regex("""\{ -?it / PageOffsetDenominator \}""").findAll(host).count(),
            "四枚页面 slide 的位移都必须是 ¼ 幅（`{ ±it / PageOffsetDenominator }`），命中数不等于 4 就是有整幅/半幅逃逸。",
        )
        assertTrue(
            Regex("""PageOffsetDenominator = 4""").containsMatchIn(host),
            "PageOffsetDenominator 必须钉成 4（¼ 幅口径，决策 §3.3）。",
        )
        assertTrue(
            !Regex("""\{ -?it \}""").containsMatchIn(host),
            "出现整幅位移 `{ it }` / `{ -it }`：300ms 走完整屏会读成「甩」，页面位移口径是 ¼ 幅不是整幅。",
        )
    }

    /**
     * 判据 D-3-④（brief Step 9 ④，修复轮 1 补口）：规格面取样的**合成双边**。
     *
     * must-flag：假屏体里就地写一枚 `tween(AppMotion.Slow)` 页面转场规格必须被同一套取样判出——
     * 判据 ② 旧口径只按文件名盯 `AppScreen.kt`，别的屏文件长出第二套转场规格完全隐形，
     * 「全仓唯一规格落点」就成了对着空档放行的口号（这正是 ④ 存在的理由）。
     * must-not-flag：唯一豁免 `overlayOpen` 那一行（AppScreen.kt:580–584）必须被放行——
     * 它问的是「有没有覆盖层亮着」，不含任何规格写法；把它判红就是拿规格禁令去改挂载口径。
     *
     * 双边之外再钉两枚「不许空转 / 不许过严」的形状：
     *  - 规格宿主本体（按非宿主文件名取样）必须一次命中三枚写法，名单不是摆设；
     *  - 批 C 的两枚合法 `AppMotion.Slow` 读点（`PlanBar` 的具名参数属性动画、`Shimmer` 的循环周期算术）
     *    必须放行——规则如实收窄，不假装「Slow 全仓唯一」这种根本做不到的绝对口径。
     */
    @Test
    fun judgementFourFlagsSyntheticScreenBodySpecAndPassesOverlayOpen() {
        val syntheticScreen = """
            @Composable
            fun FakeScreen(route: AppOverlayRoute?) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        fadeIn(tween(AppMotion.Slow, easing = AppMotion.EasingOut)) +
                            slideInHorizontally(tween(AppMotion.Slow)) { it / 4 } togetherWith
                            fadeOut(tween(AppMotion.Slow, easing = AppMotion.EasingIn)),
                ) { leg -> FakeBody(leg) }
            }
        """.trimIndent()
        val flagged = transitionSpecOffenders("FakeScreen.kt", stripComments(syntheticScreen))
        assertTrue(
            flagged.any { it.contains("tween(AppMotion.Slow") },
            "判据 ④ 的 must-flag 侧失明：假屏体 FakeScreen.kt 里就地写的 tween(AppMotion.Slow) 没被判出——" +
                "取样只按文件名盯 AppScreen.kt，任何其它 ui/ 文件长出第二份转场规格都隐形。实测命中：$flagged",
        )
        assertTrue(
            flagged.any { it.contains("slide{In,Out}Horizontally") } && flagged.any { it.contains("togetherWith") },
            "判据 ④ 的合成夹具里 slide / togetherWith 两枚写法没同时被取样点名（规格面只盯一面）：$flagged",
        )

        val hostShapes = transitionSpecOffenders(
            "NotTheHost.kt",
            stripComments(readSource(APP_TRANSITION_SOURCE_PATH)),
        )
        assertEquals(
            3,
            hostShapes.size,
            "规格宿主 theme/AppTransition.kt 按「非宿主」取样应一次命中三枚写法（tween(AppMotion.Slow) / " +
                "slide{In,Out}Horizontally / togetherWith），实际 $hostShapes——少一枚就是白名单在服务一份空规格表。",
        )

        val exemption = MOUNT_CONDITION_EXEMPTIONS.single()
        val overlayOpenRegion = readSource(APP_SCREEN_SOURCE_PATH)
            .lines()
            .subList(exemption.firstLine - 1, exemption.lastLine)
            .joinToString("\n")
        assertTrue(
            overlayOpenRegion.contains("val overlayOpen ="),
            "豁免行区间 ${exemption.fileName}:${exemption.firstLine}–${exemption.lastLine} 已不指向 overlayOpen，" +
                "本双边用例的 must-not-flag 一侧等于在测一段别的代码。",
        )
        assertTrue(
            transitionSpecOffenders("AppScreen.kt", stripComments(overlayOpenRegion)).isEmpty(),
            "overlayOpen 那一行被规格面禁令判红：它是挂载层的「有没有覆盖层」口径，不含任何转场规格写法，" +
                "判红只会逼人把豁免行改成读 route（同文件另一条判据明令禁止）。",
        )

        val batchCReaders = listOf("PlanBar.kt", "Shimmer.kt").flatMap { name ->
            transitionSpecOffenders(name, stripComments(assertExactlyOne(name).readText()))
        }
        assertTrue(
            batchCReaders.isEmpty(),
            "批 C 的合法 AppMotion.Slow 读点被转场口径误判：$batchCReaders——" +
                "PlanBar 的 tween(durationMillis = AppMotion.Slow) 是段宽属性动画、Shimmer 的 Slow*4 是循环周期，" +
                "都不在「两站转场规格」的口径内。",
        )

        val commentOnly = """
            // 历史说明：旧写法把 fadeIn(tween(AppMotion.Slow)) + slideInHorizontally(tween(AppMotion.Slow)) togetherWith
            @Composable
            fun FakeCleanScreen() { FakeBody() }
        """.trimIndent()
        assertTrue(
            transitionSpecOffenders("FakeCleanScreen.kt", stripComments(commentOnly)).isEmpty(),
            "注释里的规格字面量被判成违例：说明取样没走 stripComments，或者把同一份源码剥了两次注释。",
        )
    }

    /**
     * 判据 D-3-⑤（controller-carried：计划 Step 9 未定义此闸，本条为补「route payload 冻成零读者」而加）：
     * 每一枚带 payload 的 route 变体，其字段在宿主渲染腿里必须有 `route.<字段>` 读者。零读者 = 值冻进了 route
     * 却没人回读，退场腿期间要么白屏要么读错屏。已知两枚零读者显式挂在 [KNOWN_UNREAD_PAYLOAD_FIELDS]（点名登记，
     * 非静默豁免）：名单之外新长出一枚零读者字段即判红；某枚已登记的字段被接上读者后，把对应项从名单删掉。
     */
    @Test
    fun everyRoutePayloadFieldHasAProductionReader() {
        assertExactlyOne("AppOverlayRoute.kt")
        val routeSource = stripComments(readSource(ROUTE_SOURCE_PATH))
        val payloadFields = Regex("""data class \w+\(([^)]*)\)""")
            .findAll(routeSource)
            .flatMap { ctor -> Regex("""val (\w+)""").findAll(ctor.groupValues[1]).map { it.groupValues[1] } }
            .distinct()
            .sorted()
            .toList()
        assertTrue(
            payloadFields.isNotEmpty(),
            "从 AppOverlayRoute.kt 解析到 0 枚 payload 字段：route 表被改形而本契约读不到，判据 5 空转（批 A 反空转纪律）。",
        )
        val appScreen = stripComments(readSource(APP_SCREEN_SOURCE_PATH))
        val zeroReader = payloadFields.filter {
            Regex("""\broute\.${Regex.escape(it)}\b""").findAll(appScreen).count() == 0
        }
        assertEquals(
            KNOWN_UNREAD_PAYLOAD_FIELDS.sorted(),
            zeroReader,
            "route payload 字段读者情况变了：实测零读者 $zeroReader，已登记零读者 $KNOWN_UNREAD_PAYLOAD_FIELDS。" +
                "新增一枚零读者 = 把值冻进 route 却没人回读（退场腿读不到就白屏/读错屏）；" +
                "若某枚已登记字段这次接上了读者，就把它从 KNOWN_UNREAD_PAYLOAD_FIELDS 删掉。",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 取样助手（纯函数：输入源码文本 → 输出结构；真实文件与合成夹具共用同一份实现）
    // ---------------------------------------------------------------------------------------------

    private data class ChainBranch(val rawLine: Int, val keyword: String, val condition: String) {
        val matchesRouteWhitelist: Boolean get() = ROUTE_CONSUMPTION.matches(condition)
        val diagnostic: String get() = "原文第 $rawLine 行：$keyword$condition) {"
        val isExempted: Boolean get() = MOUNT_CONDITION_EXEMPTIONS.any { it.covers(rawLine) }
    }

    private data class ChainScan(
        val branches: List<ChainBranch>,
        val blindSpots: List<String>,
        val headFound: Boolean,
        val indent: Int,
    )

    /**
     * 取挂载链的顶层条件行。链的识别**不写死缩进**：批 D-3 会把整条链塞进 `AnimatedContent` 的 lambda
     * （整体缩进会变），所以按「同一缩进上 `} else if (` 支数最多」认定那一段就是唯一的挂载链，
     * 链头取它之前最近的一枚同缩进 `if (`。
     */
    private fun chainScan(source: String): ChainScan {
        val strippedLines = stripComments(source).lines()
        val rawLines = source.lines()

        val legIndents = mutableListOf<Int>()
        val legs = mutableListOf<Triple<Int, Int, String>>()
        val unparsed = mutableListOf<Pair<Int, String>>()
        strippedLines.forEachIndexed { number, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("} else if (")) {
                val indent = line.length - trimmed.length
                legIndents += indent
                val match = CHAIN_LEG.matchEntire(line)
                if (match == null) unparsed += indent to trimmed else legs += Triple(number, indent, match.groupValues[2].trim())
            }
        }
        if (legIndents.isEmpty()) return ChainScan(emptyList(), emptyList(), false, -1)

        val chainIndent = legIndents.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        val chainLegs = legs.filter { it.second == chainIndent }
        val headLine = strippedLines
            .take(chainLegs.first().first)
            .mapIndexedNotNull { number, line ->
                val match = CHAIN_HEAD.matchEntire(line)
                if (match != null && match.groupValues[1].length == chainIndent) number else null
            }
            .lastOrNull()

        val pairs = buildList {
            if (headLine != null) {
                add(Triple(headLine, "if (", CHAIN_HEAD.matchEntire(strippedLines[headLine])!!.groupValues[2].trim()))
            }
            chainLegs.forEach { add(Triple(it.first, "} else if (", it.third)) }
        }
        return ChainScan(
            branches = pairs.map { (strippedLine, keyword, condition) ->
                ChainBranch(rawLineOf(rawLines, strippedLines, strippedLine), keyword, condition)
            },
            blindSpots = unparsed.filter { it.first == chainIndent }.map { it.second },
            headFound = headLine != null,
            indent = chainIndent,
        )
    }

    /** 把剥注释后的行号映射回**原文**行号（块注释会吞换行，两套行号本就不同源；豁免名单钉的是原文行区间）。 */
    private fun rawLineOf(rawLines: List<String>, strippedLines: List<String>, strippedLine: Int): Int {
        val needle = strippedLines[strippedLine].trim()
        // 唯一性守卫：按 trimmed 原文匹配必须恰命中一行，否则「重复的一支」会把行区间豁免指向错误的
        // 那一行（豁免靠原文行号钉死），判据就会对着错行放行。将来出现同形重复腿 → 响亮判红而不是猜第一条。
        assertEquals(
            1,
            rawLines.count { it.trim() == needle },
            "链上取样条件 `$needle` 在原文里命中 ${rawLines.count { it.trim() == needle }} 行，" +
                "rawLineOf 无法唯一把剥注释行映射回原文行（重复的腿会让豁免行区间瞄准错误的那一枚）。",
        )
        val hit = rawLines.indexOfFirst { it.trim() == needle }
        return if (hit == -1) strippedLine + 1 else hit + 1
    }

    /** `AppOverlayRoute.kt` 的变体名，按声明顺序（= mountIndex 升序）。 */
    private fun routeVariants(strippedRouteSource: String): List<String> =
        Regex("""^ *data (?:class|object) (\w+)""", RegexOption.MULTILINE)
            .findAll(strippedRouteSource)
            .map { it.groupValues[1] }
            .toList()

    /** 变体名 → 声明里的 mountIndex 字面量（判据 3 用它核对「链序 = 0..34 升序」）。 */
    private fun routeMountIndexes(strippedRouteSource: String): Map<String, Int> {
        val names = routeVariants(strippedRouteSource)
        val indexes = Regex("""override val mountIndex = (\d+)""")
            .findAll(strippedRouteSource)
            .map { it.groupValues[1].toInt() }
            .toList()
        assertEquals(
            names.size,
            indexes.size,
            "route 变体 ${names.size} 枚但 mountIndex 声明 ${indexes.size} 枚：route 表与序号表脱节，判据 3 拒绝比对。",
        )
        return names.zip(indexes).toMap()
    }

    /** 合成夹具：一支一行的假链，与真实链同形（缩进只是形状，取样按缩进档位自适应）。 */
    private fun chainFixture(conditions: List<String>): String = buildString {
        appendLine("@Composable")
        appendLine("fun FakeHost() {")
        appendLine("    Column {")
        conditions.forEachIndexed { index, condition ->
            val keyword = if (index == 0) "if (" else "} else if ("
            appendLine("        $keyword$condition) {")
            appendLine("            MountedScreen(index = $index)")
            appendLine("        }")
        }
        appendLine("    }")
        appendLine("}")
    }
}

/** 豁免名单条目：按「文件名 + 原文行区间」钉死，区间之外一律判红（裁决 2）。 */
private data class MountConditionExemption(
    val fileName: String,
    val firstLine: Int,
    val lastLine: Int,
    val reason: String,
) {
    fun covers(rawLine: Int): Boolean = rawLine in firstLine..lastLine
}

/**
 * 全契约**唯一**一条豁免：`AppScreen.kt:580–584` 的 `overlayOpen`。
 * 它问的是「有没有覆盖层标志位亮着」（含两枚不在链上的页内浮层位 `selectedArticleCard` / `showWordQaCard`，
 * 且不含 `&& todayReady != null` 那类守卫），唯一消费者是 :587 的 `BackHandler`，
 * 与「挂载了哪一支」是两个不同问题（决策 §1.3-1）；它本来不是 `} else if` 条件行，正向扫描天然不命中。
 * **不许**把它改成 `route != null`（同文件另有 `overlayOpenIsTheOnlyExemptionAndStaysFlagBased` 钉这条）。
 */
private val MOUNT_CONDITION_EXEMPTIONS = listOf(
    MountConditionExemption(
        fileName = "AppScreen.kt",
        firstLine = 580,
        lastLine = 584,
        reason = "BackHandler(enabled = !overlayOpen …) 的低优先级返回键口径，不是挂载判据",
    ),
)

private const val APP_SCREEN_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/AppScreen.kt"
private const val ROUTE_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/AppOverlayRoute.kt"
private const val RESOLVER_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/AppOverlayRouteResolver.kt"
private const val APP_TRANSITION_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/theme/AppTransition.kt"
private const val OVERLAY_ROUTE_COUNT = 35

/** 转场规格面的唯一宿主文件名（判据 D-3-②/④ 的白名单条目；宿主的落点目录见 APP_TRANSITION_SOURCE_PATH）。 */
private const val TRANSITION_SPEC_HOST_NAME = "AppTransition.kt"

/** tab 宿主（原链末 `} else {`，对应 `AppOverlayRouteResolver.TAB_HOST_INDEX`）在链上的条件文本。 */
private const val TAB_HOST_CONDITION = "route == null"

/**
 * 判据 D-3-⑤ 已登记的「冻进 route 但宿主渲染腿无 `route.<字段>` 读者」的 payload 字段（点名，非静默豁免）：
 *  - `featureKey`（`UnknownAiFeature`，route 表 idx 28）：未知 AI 功能 key 的兜底页按链外的 `selectedFeatureKey`
 *    渲染通用脚手架，链上那一支不回读 `route.featureKey`（自 HEAD 起即如此，计划批 D route 表点名的就是这一枚）。
 *  - `historyArticle`（`ArticleReading`，idx 32）：来源判定 `fromHistory` 只读 `route.readingTarget == null`，
 *    历史文章正文由 `articleReadingViewModel` 的 uiState 提供，腿里不回读 `route.historyArticle`（D-2 冻结形状）。
 * 二者的接线属 D-2/后续批的渲染腿语义，不在 D-3 转场范围；此处显式登记，将来某枚接上读者就把对应项删掉。
 */
private val KNOWN_UNREAD_PAYLOAD_FIELDS = setOf("featureKey", "historyArticle")

/** 链上一支的合规形状：条件文本恰为 `route is AppOverlayRoute.<变体名>`。 */
private val ROUTE_CONSUMPTION = Regex("""^route is AppOverlayRoute\.\w+$""")

/** 同一条形状的取名版（判据 3 要的是变体名本身，[ROUTE_CONSUMPTION] 只管合不合规）。 */
private val ROUTE_NAME = Regex("""^route is AppOverlayRoute\.(\w+)$""")

private val CHAIN_HEAD = Regex("""^( *)if \(([^()\n]*)\) \{\s*$""")
private val CHAIN_LEG = Regex("""^( *)\} else if \(([^()\n]*)\) \{\s*$""")
