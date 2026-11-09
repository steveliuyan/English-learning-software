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
 * 终评补口第 1 席追加两条（决策册「2026-11-09 修正」修正 1；与本节判据 3 互不替代——序号仍只管优先级）：
 *
 *  4. **层深默认律**（[routeDepthColumnDefaultsToMountIndexExceptOnOverrideList]）：`transitionDepth` 默认
 *     等于 `mountIndex`（接口默认访问器承载），覆写只允许出现在点名清单上且数值逐枚相符。
 *  5. **覆写清单 == 命名边用例清单**（[transitionDepthOverrideListEqualsNamedEdgeTestList]）：多一条覆写
 *     少一条用例、或用例指着一条已不覆写的边，都判红；牙齿由合成双边自证。
 *
 * 终评补口第 2 席（席 B2）再追加两条（决策册「2026-11-09 修正」修正 2）：
 *
 *  6. **身份列覆写律**（[routeIdentityColumnIsOverriddenOnEveryDataClassBranch]）：`navigationIdentity` 默认
 *     访问器只服务 `data object`，9 枚 `data class` 必须逐枚覆写台账点名的稳定主键并带支别前缀。
 *  7. **站 1 的身份接线**（[stationOneAnimatedContentPassesNavigationIdentityAsContentKey]）：站 1 的
 *     `AnimatedContent` 参数窗里必须有 `contentKey = { it?.navigationIdentity }`，且全文件只许这一枚
 *     （站 2 的 tab 身份就是 `AppTab` 枚举本身，不过度接线）。
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
    // 唯一豁免：AppScreen.kt:583–587 的 overlayOpen（按文件名 + 原文行区间钉死；2026-11-09 现算，消费者 BackHandler 在 :590）
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
     * must-not-flag：唯一豁免 `overlayOpen` 那一行（AppScreen.kt:583–587）必须被放行——
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
     * 却没人回读，退场腿期间要么白屏要么读错屏。已登记的零读者显式挂在 [KNOWN_UNREAD_PAYLOAD_FIELDS]（点名登记，
     * 非静默豁免）：名单之外新长出一枚零读者字段即判红；某枚已登记的字段被接上读者后，把对应项从名单删掉，
     * 或者按终评补口 第 3 条那样把没人读的字段直接从 payload 摘掉（`historyArticle` 走的就是这一支）。
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
    // 判据 D-4fix-①/②（终评补口 第 1 条，Critical 裁定）：transitionDepth 列的默认律与「覆写清单 == 命名边用例清单」
    // ---------------------------------------------------------------------------------------------

    /**
     * 判据 D-4fix-①：`transitionDepth` 默认等于 `mountIndex`（接口默认访问器），
     * 覆写只允许出现在 [TRANSITION_DEPTH_OVERRIDES] 点名的支上且数值逐枚相符；
     * 清单外的覆写、清单内却消失的声明、与清单不符的值、默认访问器失踪，任一形状即判红。
     * 取样面（scoped 复评 2026-11-09 Important 1 补齐）：数值字面量认 `= 6` 与 `get() = 6` 两种形态；
     * 声明存在性认所有写法，于是「有声明但取不到数值」（表达式形态）与「同段两枚声明」也各有一臂，
     * 本闸不再存在「取不到值 = 当没覆写」的静默放行。
     */
    @Test
    fun routeDepthColumnDefaultsToMountIndexExceptOnOverrideList() {
        assertExactlyOne("AppOverlayRoute.kt")
        val routeSource = stripComments(readSource(ROUTE_SOURCE_PATH))
        assertEquals(
            OVERLAY_ROUTE_COUNT,
            routeDepthRows(routeSource).size,
            "从 AppOverlayRoute.kt 解析到 ${routeDepthRows(routeSource).size} 枚 route 变体（应为 $OVERLAY_ROUTE_COUNT 枚）：" +
                "route 表被改形而本判据读不到，层深闸对着空名单放行就是空转（批 A 反空转纪律）。",
        )
        val offenders = depthDeclarationOffenders(routeSource)
        assertTrue(
            offenders.isEmpty(),
            "transitionDepth 列违反默认律（决策册「2026-11-09 修正」修正 1）：\n" + offenders.joinToString("\n") +
                "\n口径：默认 = mountIndex，覆写仅限清单点名的支；新增覆写必须先找到发起点行号、补命名边用例，再进清单。",
        )
    }

    /**
     * 判据 D-4fix-②：覆写清单 == 命名边用例清单。
     * 从 `ScreenTransitionDirectionTest.kt` 扫 `fun namedEdge<From>To<To>IsForward(` 的 to 支集合，
     * 与 [TRANSITION_DEPTH_OVERRIDES] 的键集合必须相等——多一条覆写少一条用例、或用例指着一条已不覆写的边，都判红；
     * 命名边对数恰为 [NAMED_FORWARD_EDGE_COUNT]（扩清单须清单、用例、计数三处同改，禁止只动其一）。
     */
    @Test
    fun transitionDepthOverrideListEqualsNamedEdgeTestList() {
        val directionTest = stripComments(readSource(DIRECTION_TEST_SOURCE_PATH))
        val forwardEdges = NAMED_FORWARD_EDGE.findAll(directionTest).toList()
        assertEquals(
            NAMED_FORWARD_EDGE_COUNT,
            forwardEdges.size,
            "命名前进边用例实测 ${forwardEdges.size} 条（应为 $NAMED_FORWARD_EDGE_COUNT 条：${forwardEdges.map { it.groupValues[0] }}）。" +
                "裁定钉的就是 29→32、30→32、31→32、7→8 四对；增删必须同步决策册与本契约。",
        )
        val offenders = namedEdgePairingOffenders(directionTest)
        assertTrue(
            offenders.isEmpty(),
            "覆写清单与命名边用例清单不相等（决策册「2026-11-09 修正」修正 1 的配套纪律）：\n" + offenders.joinToString("\n"),
        )
    }

    /**
     * 判据 D-4fix-①/② 的合成双边（must-flag / must-not-flag，本闸的牙齿自证）：
     * ①清单外野覆写、②覆写值漂移、③清单内声明失踪、④默认访问器被拆、⑤有覆写没用例、⑥有用例指没覆写的边、
     * ⑦注释里的命名边不算（取样只走一次 stripComments，二次剥是缺陷）；合规假表 + 合规假用例必须全放行。
     * scoped 复评 2026-11-09 Important 1 补的形状（层深列过去只钉数值形态，getter / 表达式形态的覆写在同一份文件里
     * 与 [ROUTE_DEPTH_DEFAULT_ACCESSOR] 的写法不对称）：⑧getter 形态的清单外覆写、⑨表达式形态 = 写法无法取样、
     * ⑩同段两枚声明 = 取样失真、⑪getter 形态的 `override val mountIndex` 由序号列的取样失真臂兜住（第 4 小项的坐实）、
     * ⑫清单内支写成 getter 形态且值相符必须放行、值不符必须走「不符」臂、⑬接口默认访问器（无 `override`）不算覆写声明。
     */
    @Test
    fun judgementDepthGateFlagsSyntheticOverrideAndUnpairedNamedEdge() {
        val fakeRoute = """
            sealed interface FakeRoute {
                val mountIndex: Int
                val transitionDepth: Int get() = mountIndex
                data object LearningFlow : FakeRoute { override val mountIndex = 1; override val transitionDepth = 6 }
                data object Settings : FakeRoute { override val mountIndex = 2 }
                data object Rogue : FakeRoute { override val mountIndex = 3; override val transitionDepth = 0 }
                data object GetterRogue : FakeRoute { override val mountIndex = 4; override val transitionDepth: Int get() = 9 }
                data object ExpressionRogue : FakeRoute { override val mountIndex = 5; override val transitionDepth get() = mountIndex + 1 }
                data object DoubledDepth : FakeRoute { override val mountIndex = 6; override val transitionDepth = 1; override val transitionDepth = 2 }
                data object GetterMountIndex : FakeRoute { override val mountIndex: Int get() = 7 }
            }
        """.trimIndent()
        val offenders = depthDeclarationOffenders(stripComments(fakeRoute))
        assertTrue(
            offenders.any { it.contains("Rogue") },
            "清单外野覆写没被判出：层深默认律对第二套深度口径失明。实测：$offenders",
        )
        assertTrue(
            offenders.none { it.contains("LearningFlow") },
            "合规覆写被误判红（过严会把补口本身卡死）：$offenders",
        )
        // scoped 复评（2026-11-09）Important 1 的四条形状：本闸过去只认 `override val transitionDepth = <数字>`，
        // 于是 getter 形态的覆写、表达式形态的覆写与「同段两枚声明」都落在取样盲区里静默放行（层深退回默认律 = 方向被改掉）。
        assertTrue(
            offenders.any { it.contains("GetterRogue") && it.contains("覆写清单之外") },
            "getter 形态的清单外覆写（`override val transitionDepth: Int get() = 9`）没被判出：取样正则只认数值字面量时，" +
                "这一臂的层深静默退回默认访问器，方向判定被无声改掉。实测：$offenders",
        )
        assertTrue(
            offenders.any { it.contains("ExpressionRogue") && it.contains("写法无法取样") },
            "表达式形态的覆写（`override val transitionDepth get() = mountIndex + 1`）没被判出：取不到数值就当没覆写，" +
                "等于把清单绕过。实测：$offenders",
        )
        assertTrue(
            offenders.any { it.contains("DoubledDepth") && it.contains("取样失真") },
            "同一支段内两枚 transitionDepth 声明没被判出：singleOrNull 静默变 null 后比对被整个跳过。实测：$offenders",
        )
        assertTrue(
            offenders.any { it.contains("GetterMountIndex") && it.contains("取样失真") },
            "getter 形态的 `override val mountIndex: Int get() = 7` 没被序号列的取样失真臂判出：该臂是本条口径的唯一承载体，" +
                "丢牙即成静默盲区。实测：$offenders",
        )
        val listedAsGetter = fakeRoute.replace("override val transitionDepth = 6", "override val transitionDepth: Int get() = 6")
        assertTrue(
            depthDeclarationOffenders(stripComments(listedAsGetter)).none { it.contains("LearningFlow") },
            "清单内支把 `= 6` 写成 getter 形态 `: Int get() = 6` 被判红：放宽后两种字面量形态都要能取到值并与清单比对。" +
                "实测：${depthDeclarationOffenders(stripComments(listedAsGetter))}",
        )
        val listedGetterDrift = fakeRoute.replace(
            "override val transitionDepth = 6",
            "override val transitionDepth: Int get() = 9",
        )
        assertTrue(
            depthDeclarationOffenders(stripComments(listedGetterDrift)).any { it.contains("LearningFlow") && it.contains("不符") },
            "getter 形态的覆写值与清单漂移没按「值不符」口径判出（放宽取样后这条边必须走漂移臂，而不是退回「声明消失」）。" +
                "实测：${depthDeclarationOffenders(stripComments(listedGetterDrift))}",
        )
        val drifted = fakeRoute.replace("override val transitionDepth = 6", "override val transitionDepth = 9")
        assertTrue(
            depthDeclarationOffenders(stripComments(drifted)).any { it.contains("LearningFlow") },
            "覆写值与清单漂移没被判出：改值就是改方向判定，必须走清单。",
        )
        val vanished = fakeRoute.replace("; override val transitionDepth = 6", "")
        assertTrue(
            depthDeclarationOffenders(stripComments(vanished)).any { it.contains("LearningFlow") },
            "清单内支的声明消失没被判出：方向会静默退回比序号，命名边重新被播成返回。",
        )
        val noDefault = fakeRoute.replace("val transitionDepth: Int get() = mountIndex", "val transitionDepth: Int")
        assertTrue(
            depthDeclarationOffenders(stripComments(noDefault)).any { it.contains("默认访问器") },
            "接口默认访问器被拆没被判出：未覆写支的层深从此无源可依。",
        )
        // 存在性正则只许认 `override`：同族的接口默认访问器（ROUTE_DEPTH_DEFAULT_ACCESSOR 那种写法）不是覆写声明。
        val defaultAccessorIsNotAnOverride = """
            sealed interface FakeRoute {
                val mountIndex: Int
                val transitionDepth: Int get() = mountIndex
                data object Plain : FakeRoute { override val mountIndex = 1 }
            }
        """.trimIndent()
        assertTrue(
            depthDeclarationOffenders(stripComments(defaultAccessorIsNotAnOverride)).isEmpty(),
            "存在性正则把接口默认访问器（无 `override` 前缀）误当成覆写声明：未覆写支会被判成「写法无法取样」，" +
                "「默认 = 序号」这条前提反而被闸自己弄失明。实测：${depthDeclarationOffenders(stripComments(defaultAccessorIsNotAnOverride))}",
        )

        val pairedTest = """
            class FakeDirectionTest {
                fun namedEdgeReadingHistoryToArticleReadingIsForward() {}
                fun namedEdgeReadingFeedToArticleReadingIsForward() {}
                fun namedEdgeArticleImportToArticleReadingIsForward() {}
                fun namedEdgeLearningSetupToLearningFlowIsForward() {}
            }
        """.trimIndent()
        assertTrue(
            namedEdgePairingOffenders(pairedTest).isEmpty(),
            "合规的「四对命名边 == 清单」形状被判红（must-not-flag 侧过严）：${namedEdgePairingOffenders(pairedTest)}",
        )
        val missingCase = pairedTest.replace(
            "fun namedEdgeLearningSetupToLearningFlowIsForward() {}",
            "fun learningFlowEdgeWasDeleted() {}",
        )
        assertTrue(
            namedEdgePairingOffenders(missingCase).any { it.contains("LearningFlow") },
            "有覆写没用例的形状没被判出：这正是裁定要防的「覆写清单 ≠ 用例清单」。",
        )
        val strayCase = pairedTest.replace(
            "namedEdgeArticleImportToArticleReadingIsForward",
            "namedEdgeArticleImportToGlobalSearchIsForward",
        )
        assertTrue(
            namedEdgePairingOffenders(strayCase).any { it.contains("GlobalSearch") },
            "用例指向一条不覆写的边没被判出（must-flag 侧）：$strayCase",
        )
        val commentOnly = "// fun namedEdgeReadingHistoryToArticleReadingIsForward()\n// fun namedEdgeReadingFeedToArticleReadingIsForward()"
        assertTrue(
            namedEdgePairingOffenders(stripComments(commentOnly)).any { it.contains("空转") },
            "注释里的命名边被当作用例（取样没剥注释），或空名单守卫失明（对着空清单比对覆写等于没牙）。",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 判据 D-4fix-③/④（终评补口 第 2 条，席 B2）：身份列的覆写律 + 站 1 的 contentKey 接线
    // ---------------------------------------------------------------------------------------------

    /**
     * 判据 D-4fix-③：route 表的**身份列覆写律**。
     * `navigationIdentity` 的默认访问器（`val navigationIdentity: Any get() = this`）只服务 26 枚 `data object`
     * （单例的 `this` 天然稳定）；9 枚 `data class` 必须各自 `override val navigationIdentity` 给出稳定业务主键——
     * 漏一枚就是「payload 一变 = 一枚新身份 = 站 1 起一轮新转场」（第 2 条裁定的缺陷本体）。
     * 支数与身份取值逐枚记在 [NAVIGATION_IDENTITY_LEDGER]（本席报告的「第 2 条身份表」一节同源）。
     */
    @Test
    fun routeIdentityColumnIsOverriddenOnEveryDataClassBranch() {
        assertExactlyOne("AppOverlayRoute.kt")
        val routeSource = stripComments(readSource(ROUTE_SOURCE_PATH))
        assertEquals(
            OVERLAY_ROUTE_COUNT,
            routeIdentityRows(routeSource).size,
            "从 AppOverlayRoute.kt 解析到 ${routeIdentityRows(routeSource).size} 枚 route 变体（应为 $OVERLAY_ROUTE_COUNT 枚）：" +
                "route 表被改形而身份闸读不到，覆写律对着空名单放行就是空转（批 A 反空转纪律）。",
        )
        assertEquals(
            NAVIGATION_IDENTITY_OVERRIDE_COUNT,
            routeIdentityRows(routeSource).count { it.isDataClass },
            "route 表的 data class 支数与身份台账登记数不符（台账 $NAVIGATION_IDENTITY_OVERRIDE_COUNT 枚）：" +
                "支数漂移 = 新增/删改了一枚带 payload 的支，必须先改台账与理由，再动本闸。",
        )
        val offenders = navigationIdentityOffenders(routeSource)
        assertTrue(
            offenders.isEmpty(),
            "navigationIdentity 列违反覆写律（决策册「2026-11-09 修正」修正 2）：\n" + offenders.joinToString("\n") +
                "\n口径：默认访问器承载 data object，data class 逐枚覆写稳定业务主键；新增一支 data class 必须同时给身份与理由。",
        )
    }

    /**
     * 判据 D-4fix-④：站 1 的 `AnimatedContent` 必须把身份列当 `contentKey` 接上。
     * 取样按「`targetState = overlayRoute` 那一行 → 内容 lambda 起点 `) { route ->`」取参数窗，
     * 站 2 不在口径内（tab 的 `AppTab` 枚举本身即身份）⇒ 全文件 `contentKey =` 恰一枚，多一枚即过度接线。
     */
    @Test
    fun stationOneAnimatedContentPassesNavigationIdentityAsContentKey() {
        assertExactlyOne("AppScreen.kt")
        val appScreen = stripComments(readSource(APP_SCREEN_SOURCE_PATH))
        val offenders = stationOneContentKeyOffenders(appScreen)
        assertTrue(
            offenders.isEmpty(),
            "站 1 的身份接线不合格（决策册「2026-11-09 修正」修正 2）：\n" + offenders.joinToString("\n") +
                "\n口径：`AnimatedContent(targetState = overlayRoute, …, contentKey = { it?.navigationIdentity })`——" +
                "没有 contentKey，route 的 data class 等值就是把「同一屏的数据变了」读成「换了一屏」。",
        )
    }

    /**
     * 判据 D-4fix-③/④ 的合成双边（本闸的牙齿自证）：
     * ①data class 支漏覆写身份、②身份不带支别前缀、③台账外新增长出的 data class、④默认访问器被拆、⑤取样空转、
     * ⑥站 1 参数窗没有 contentKey、⑦contentKey 不引用身份列、⑧站 2 长出第二枚 contentKey、⑨站 1 形状失踪，
     * 全部 must-flag；合规假表 + 合规假站点必须全放行（must-not-flag）。
     */
    @Test
    fun judgementIdentityGateFlagsMissingOverrideAndStationWithoutContentKey() {
        val compliantRouteTable = """
            sealed interface FakeRoute {
                val mountIndex: Int
                val navigationIdentity: Any get() = this
                data object Settings : FakeRoute { override val mountIndex = 0 }
                data class UnknownAiFeature(val featureKey: String) : FakeRoute { override val mountIndex = 1; override val navigationIdentity get() = mountIndex to featureKey }
            }
        """.trimIndent()
        assertTrue(
            navigationIdentityOffenders(stripComments(compliantRouteTable)).isEmpty(),
            "合规身份表被判红（must-not-flag 侧过严会把补口本身卡死）：${navigationIdentityOffenders(stripComments(compliantRouteTable))}",
        )

        val missingOverride = compliantRouteTable.replace("; override val navigationIdentity get() = mountIndex to featureKey", "")
        assertTrue(
            navigationIdentityOffenders(stripComments(missingOverride)).any { it.contains("UnknownAiFeature") },
            "data class 支漏覆写身份没被判出：身份列退回 route 实例，一次数据刷新又被读成一次导航。",
        )
        val noBranchPrefix = compliantRouteTable.replace("mountIndex to featureKey", "featureKey")
        assertTrue(
            navigationIdentityOffenders(stripComments(noBranchPrefix)).any { it.contains("支别前缀") },
            "身份不带支别前缀的形状没被判出：同一枚主键的两支不同屏（词卡详情→重学页，AppScreen.kt:830-832）会被并成同一身份。",
        )
        val rogueBranch = compliantRouteTable.replace(
            "data object Settings : FakeRoute { override val mountIndex = 0 }",
            "data class Rogue(val whatever: String) : FakeRoute { override val mountIndex = 0; override val navigationIdentity get() = mountIndex to whatever }",
        )
        assertTrue(
            navigationIdentityOffenders(stripComments(rogueBranch)).any { it.contains("台账") },
            "台账外新长出一枚 data class 没被判红：新支的身份没有登记与理由，闸就会对它默认放行。",
        )
        val noDefault = compliantRouteTable.replace("val navigationIdentity: Any get() = this", "val navigationIdentity: Any")
        assertTrue(
            navigationIdentityOffenders(stripComments(noDefault)).any { it.contains("默认访问器") },
            "接口默认访问器被拆没被判出：26 枚 data object 的身份从此无源可依。",
        )
        assertTrue(
            navigationIdentityOffenders(stripComments("sealed interface Empty { val mountIndex: Int }")).any { it.contains("空转") },
            "取样解析到 0 枚变体时本闸静默放行：正是批 A 要防的空转形状。",
        )

        val compliantStation = """
            @Composable
            fun FakeHost(overlayRoute: AppOverlayRoute?) {
                AnimatedContent(
                    targetState = overlayRoute,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { it?.navigationIdentity },
                    transitionSpec = { AppTransition.contentTransform(ScreenTransitionDirectionPolicy.overlay(initialState, targetState)) },
                ) { route ->
                    OverlayLeg(route == overlayRoute) { Body(route) }
                }
            }
        """.trimIndent()
        assertTrue(
            stationOneContentKeyOffenders(stripComments(compliantStation)).isEmpty(),
            "合规站 1 被判红（must-not-flag 侧过严）：${stationOneContentKeyOffenders(stripComments(compliantStation))}",
        )

        val withoutKey = compliantStation.replace(Regex("""(?m)^ *contentKey = \{ it\?\.navigationIdentity \},\r?\n"""), "")
        assertTrue(
            stationOneContentKeyOffenders(stripComments(withoutKey)).any { it.contains("contentKey") },
            "站 1 缺 contentKey 没被判出：本席判据 ④ 等于没写。",
        )
        val wrongKey = compliantStation.replace("contentKey = { it?.navigationIdentity }", "contentKey = { it?.mountIndex }")
        assertTrue(
            stationOneContentKeyOffenders(stripComments(wrongKey)).any { it.contains("navigationIdentity") },
            "contentKey 指向别的列（如 mountIndex）没被判出：同支换数据照样被读成换屏。",
        )
        val secondStation = compliantStation + "\n" + compliantStation.replace("targetState = overlayRoute", "targetState = selectedTab")
        assertTrue(
            stationOneContentKeyOffenders(stripComments(secondStation)).any { it.contains("恰一枚") },
            "站 2 长出第二枚 contentKey 没被判出：tab 宿主腿的身份就是 AppTab 枚举本身，裁定明令不过度接线。",
        )
        assertTrue(
            stationOneContentKeyOffenders(stripComments("@Composable fun X() { Box { } }")).any { it.contains("参数窗") },
            "站 1 形状失踪（改名/搬走/拆形）时本闸静默放行：判据 ④ 对着空档过关。",
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

    /**
     * 层深闸取样（判据 D-4fix-①/② 与 [routeVariants] 同族但按**支分段**）：
     * 每个 `data class/object` 从自己的起点截到下一枚变体的起点，段内取恰一枚
     * `override val mountIndex`（0 或 ≥2 枚 = 取样失真，记 null 响亮报）与层深列的**两件事**：
     * 数值字面量（`= 6` 与 `get() = 6` 两种形态都取值）与声明枚数（[DECLARED_TRANSITION_DEPTH_EXISTS]，
     * 认所有写法）。枚数 ≥2 = 取样失真、恰 1 枚却取不到数值 = 写法无法取样，两者都在
     * [depthDeclarationOffenders] 里响亮判红——本闸没有「取不到就静默放行」这条路。真实文件与合成夹具走同一实现。
     */
    private data class RouteDepthRow(
        val variant: String,
        val mountIndex: Int?,
        val declaredTransitionDepth: Int?,
        val transitionDepthDeclarationCount: Int,
    )

    private fun routeDepthRows(strippedRouteSource: String): List<RouteDepthRow> {
        val starts = ROUTE_VARIANT_DECL.findAll(strippedRouteSource).map { it.groupValues[1] to it.range.first }.toList()
        return starts.mapIndexed { index, (name, start) ->
            val end = if (index + 1 < starts.size) starts[index + 1].second else strippedRouteSource.length
            val segment = strippedRouteSource.substring(start, end)
            val mountIndexes = DECLARED_MOUNT_INDEX.findAll(segment).map { it.groupValues[1].toInt() }.toList()
            val depths = DECLARED_TRANSITION_DEPTH.findAll(segment).map { it.groupValues[1].toInt() }.toList()
            RouteDepthRow(
                variant = name,
                mountIndex = if (mountIndexes.size == 1) mountIndexes.single() else null,
                declaredTransitionDepth = depths.singleOrNull(),
                transitionDepthDeclarationCount = DECLARED_TRANSITION_DEPTH_EXISTS.findAll(segment).count(),
            )
        }
    }

    /** 判据 D-4fix-① 的本体（纯函数：已剥注释的 route 源码 → offender 文案清单；真实与合成共用）。 */
    private fun depthDeclarationOffenders(strippedRouteSource: String): List<String> = buildList {
        val rows = routeDepthRows(strippedRouteSource)
        if (rows.isEmpty()) {
            add("层深闸从 route 源码解析到 0 枚变体：取样空转，清单比对无从进行。")
            return@buildList
        }
        // 序号列的取样失真臂**无条件**判红，这一条就是 scoped 复评 第 1 条问的「DECLARED_MOUNT_INDEX 是否同款盲区」的答案：
        // 没有静默盲区。getter 形态的 `override val mountIndex: Int get() = 3` 让只认 `= 数字` 的 DECLARED_MOUNT_INDEX
        // 在段内取到 0 枚 ⇒ mountIndex == null ⇒ 本臂直接响亮（合成用例的 must-flag GetterMountIndex 坐实，
        // 见 judgementDepthGateFlagsSyntheticOverrideAndUnpairedNamedEdge）。层深列当年漏的正是这条：
        // 取不到数值时「declared == null && expected == null」无臂可走，比对被静默跳过——现已由下面的
        // 枚数臂（≥2 枚）与「写法无法取样」臂（恰 1 枚却无数值）补齐，故序号列不另加新臂，只把口径写清并留牙齿。
        rows.filter { it.mountIndex == null }.forEach {
            add("route 支 `${it.variant}` 段内枚数正常的 `override val mountIndex` 取不到（0 或重复）：取样失真，本判据拒绝比对。")
        }
        if (!ROUTE_DEPTH_DEFAULT_ACCESSOR.containsMatchIn(strippedRouteSource)) {
            add("route 接口缺少 `val transitionDepth: Int get() = mountIndex` 默认访问器：未覆写支的层深从此无源可依，「默认 = 序号」这条前提静默失效。")
        }
        rows.forEach { row ->
            val declared = row.declaredTransitionDepth
            val expected = TRANSITION_DEPTH_OVERRIDES[row.variant]
            when {
                row.transitionDepthDeclarationCount >= 2 ->
                    add("支 `${row.variant}` 段内有 ${row.transitionDepthDeclarationCount} 枚 `override val transitionDepth` 声明：" +
                        "取样失真，本判据拒绝比对（比照序号列的取样失真臂，禁止让 singleOrNull 静默变 null 绕过清单）。")
                declared != null && expected == null ->
                    add("支 `${row.variant}` 在覆写清单之外声明 transitionDepth = $declared：没有命名边用例背书的覆写即判红。")
                declared != null && declared != expected ->
                    add("支 `${row.variant}` 覆写值 $declared 与清单期望 $expected 不符：改值就是改方向判定，须先回清单与理由。")
                declared == null && row.transitionDepthDeclarationCount == 1 ->
                    add("支 `${row.variant}` 声明了 `override val transitionDepth` 但取不到数值（写法无法取样）：" +
                        "本闸只认数值字面量（`= 6` 与 `get() = 6`），`override val transitionDepth get() = mountIndex + 1` " +
                        "这类表达式形态的覆写等于绕开清单——清单外即野覆写，清单内即静默退回「默认 = 序号」。" +
                        "新增覆写请用数值字面量并先补命名边用例与清单。")
                declared == null && expected != null ->
                    add("支 `${row.variant}` 在覆写清单里但 route 源码的声明消失：方向退回比序号，命名边会重新被播成返回。")
            }
        }
    }

    /** 判据 D-4fix-② 的本体（纯函数：已剥注释的方向用例源码 → offender 文案清单；真实与合成共用）。 */
    private fun namedEdgePairingOffenders(strippedDirectionTestSource: String): List<String> = buildList {
        val forwardTargets = NAMED_FORWARD_EDGE.findAll(strippedDirectionTestSource).map { it.groupValues[2] }.toSet()
        if (forwardTargets.isEmpty()) {
            add("从方向用例源码读不到任何 `fun namedEdge…To…IsForward(` 用例：用例清单为空，与覆写清单的比对就是空转。")
            return@buildList
        }
        (forwardTargets - TRANSITION_DEPTH_OVERRIDES.keys).forEach {
            add("命名边用例指向 `$it`，但覆写清单没有它：这条边要么已回到不覆写（删用例），要么漏了声明（补清单并给出发起点行号）。")
        }
        (TRANSITION_DEPTH_OVERRIDES.keys - forwardTargets).forEach {
            add("覆写清单点名 `$it`，但没有任何命名边用例指向它：无测试的覆写正是裁定要防的形状。")
        }
    }

    /**
     * 身份闸取样（判据 D-4fix-③；与 [routeDepthRows] 同族的**按支分段**取样器，席 B2 新增列不复用旧正则）：
     * 每枚 `data class/object` 从自己的起点截到下一枚变体的起点，段内取至多一枚
     * `override val navigationIdentity …` 声明（整行，含取值表达式）。真实文件与合成夹具走同一份实现。
     */
    private data class RouteIdentityRow(
        val variant: String,
        val isDataClass: Boolean,
        val identityDeclaration: String?,
    ) {
        val declaresNavigationIdentity: Boolean get() = identityDeclaration != null
    }

    private fun routeIdentityRows(strippedRouteSource: String): List<RouteIdentityRow> {
        val starts = ROUTE_VARIANT_KIND_DECL.findAll(strippedRouteSource)
            .map { Triple(it.groupValues[1] == "class", it.groupValues[2], it.range.first) }
            .toList()
        return starts.mapIndexed { index, (isClass, name, start) ->
            val end = if (index + 1 < starts.size) starts[index + 1].third else strippedRouteSource.length
            RouteIdentityRow(
                variant = name,
                isDataClass = isClass,
                identityDeclaration = DECLARED_NAVIGATION_IDENTITY.find(strippedRouteSource.substring(start, end))?.value,
            )
        }
    }

    /** 判据 D-4fix-③ 的本体（纯函数：已剥注释的 route 源码 → offender 文案清单；真实与合成共用）。 */
    private fun navigationIdentityOffenders(strippedRouteSource: String): List<String> = buildList {
        val rows = routeIdentityRows(strippedRouteSource)
        if (rows.isEmpty()) {
            add("身份闸从 route 源码解析到 0 枚变体：取样空转，覆写比对无从进行。")
            return@buildList
        }
        if (!ROUTE_IDENTITY_DEFAULT_ACCESSOR.containsMatchIn(strippedRouteSource)) {
            add("route 接口缺少 `val navigationIdentity: Any get() = this` 默认访问器：26 枚 data object 的身份从此无源可依。")
        }
        rows.filter { it.isDataClass }.forEach { row ->
            val needle = NAVIGATION_IDENTITY_LEDGER[row.variant]
            val declaration = row.identityDeclaration
            when {
                needle == null -> add(
                    "data class 支 `${row.variant}` 不在身份台账 NAVIGATION_IDENTITY_LEDGER 里：新增一支必须先登记稳定业务主键、" +
                        "取值理由与证据行号（台账与本席报告的「第 2 条身份表」同源），再动 route 表。",
                )
                declaration == null -> add(
                    "data class 支 `${row.variant}` 没有 `override val navigationIdentity`：身份退回 route 实例本身，" +
                        "payload 任一字段一变就被站 1 的 AnimatedContent 读成换了一屏（终评补口 第 2 条的缺陷本体）。",
                )
                !declaration.contains(needle) -> add(
                    "支 `${row.variant}` 的身份声明没读出台账点名的稳定主键 `$needle`：实测 `$declaration`。" +
                        "换主键就是换「哪一屏」的判据，须先回台账改口径与理由。",
                )
                !ROUTE_IDENTITY_BRANCH_PREFIX.containsMatchIn(declaration) -> add(
                    "支 `${row.variant}` 的身份没带支别前缀 `mountIndex to`：同一枚主键的两支不同屏" +
                        "（如词卡详情→重学页，AppScreen.kt:830-832）会被并成同一身份而不起转场。",
                )
            }
        }
    }

    /**
     * 站 1 参数窗取样（判据 D-4fix-④）：从 `targetState = overlayRoute,` 那一行扫到内容 lambda 的起点行
     * （`^) { 形参 ->`，缩进自适应），两端都取到才算窗；取不到返回 null，由调用方响亮判红而不是空转放行。
     */
    private fun stationOneArguments(strippedAppScreen: String): String? {
        val lines = strippedAppScreen.lines()
        val start = lines.indexOfFirst { STATION_ONE_TARGET_LINE.containsMatchIn(it) }
        if (start < 0) return null
        val end = (start + 1 until lines.size).firstOrNull { CONTENT_LAMBDA_OPEN.containsMatchIn(lines[it]) } ?: return null
        return lines.subList(start, end).joinToString("\n")
    }

    /** 判据 D-4fix-④ 的本体（纯函数：已剥注释的宿主源码 → offender 文案清单；真实与合成共用）。 */
    private fun stationOneContentKeyOffenders(strippedAppScreen: String): List<String> {
        val window = stationOneArguments(strippedAppScreen)
            ?: return listOf("站 1 的 AnimatedContent 参数窗取不到（`targetState = overlayRoute,` 那行或其后的 `) { 形参 ->` 改了形）：" +
                "身份接线无从核对，判红而不是对着空档放行。")
        return buildList {
            if (!STATION_ONE_CONTENT_KEY.containsMatchIn(window)) {
                add("站 1 的参数窗里没有 `contentKey = { it?.navigationIdentity }`：route 是 data class，" +
                    "没有 contentKey 时「同一屏换数据」与「换一屏」在 AnimatedContent 眼里是同一件事。")
            }
            val total = STATION_CONTENT_KEY_DECL.findAll(strippedAppScreen).count()
            if (total != 1) {
                add("AppScreen.kt 里 `contentKey =` 应恰一枚（站 1），实测 $total 枚：站 2 的 tab 宿主腿身份就是 AppTab 枚举本身，" +
                    "裁定明令不过度接线；0 枚则是判据失踪。")
            }
        }
    }

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
 * 全契约**唯一**一条豁免：`AppScreen.kt:583–587` 的 `overlayOpen`。
 * 它问的是「有没有覆盖层标志位亮着」（含两枚不在链上的页内浮层位 `selectedArticleCard` / `showWordQaCard`，
 * 且不含 `&& todayReady != null` 那类守卫），唯一消费者是 :590 的 `BackHandler`，
 * 与「挂载了哪一支」是两个不同问题（决策 §1.3-1）；它本来不是 `} else if` 条件行，正向扫描天然不命中。
 * **不许**把它改成 `route != null`（同文件另有 `overlayOpenIsTheOnlyExemptionAndStaysFlagBased` 钉这条）。
 */
private val MOUNT_CONDITION_EXEMPTIONS = listOf(
    MountConditionExemption(
        fileName = "AppScreen.kt",
        firstLine = 583,
        lastLine = 587,
        reason = "BackHandler(enabled = !overlayOpen …) 的低优先级返回键口径，不是挂载判据",
    ),
)

private const val APP_SCREEN_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/AppScreen.kt"
private const val ROUTE_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/AppOverlayRoute.kt"
private const val RESOLVER_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/AppOverlayRouteResolver.kt"
private const val APP_TRANSITION_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/theme/AppTransition.kt"

/** 命名边用例的所在文件（判据 D-4fix-② 的用例侧权威；readSource 的存在性守卫就是它的响亮文件数闸）。 */
private const val DIRECTION_TEST_SOURCE_PATH = "src/test/java/com/example/englishlearning/ui/ScreenTransitionDirectionTest.kt"

private const val OVERLAY_ROUTE_COUNT = 35

/**
 * `transitionDepth` 覆写清单（决策册「2026-11-09 修正」修正 1；默认律与「清单 == 用例清单」由
 * [routeDepthColumnDefaultsToMountIndexExceptOnOverrideList] 与 [transitionDepthOverrideListEqualsNamedEdgeTestList] 钉住）。
 * 取值理由（逐边，发起点见方向用例注释）：
 *  - `ArticleReading = 28`：它是 29/30/31 三枚列表层点名的更深一屏，序号 32 却比三枚发起层都「浅」，
 *    只比序号会把「历史/选读/导入 点开文章」三枚前进边播成返回。28 是 <29 的最大整数：
 *    与 ≤27 各臂的先后关系和序号时代逐字相同（零改判），改判区恰为发起臂 29/30/31；
 *    与 UnknownAiFeature(28) 同值只会 Crossfade，而 resolver 的臂序（UnknownAiFeature 支 `:46` 声明在
 *    ArticleReading 支 `:50` 之前，先声明先赢）决定两臂互斥、
 *    全仓没有同时写 `selectedFeatureKey` 与文章目标的发起点，改判的是不存在的边。
 *  - `LearningFlow = 6`：设置臂守卫 `setupRequired || showSetup`（resolver `:25`）压在学习臂（`:26`）之前，
 *    学习流在强制设置落位后回到 8 是把前进播成返回的同形边。6 是 <7 的最大整数，改判区 {6,7} 只有
 *    LearningRecords 与 LearningSetup 两臂：前者全仓没有置 `showLearning` 的回调（`showLearning = true`
 *    实测只落在 `AppScreen.kt:1362/:1384/:2158`，随覆写值仍判 Forward 或不变），后者正是点名的发起臂。
 * 两枚值都必须数值小于 TAB_HOST_INDEX(35)——「子页收回 tab 宿主」仍是 Backward（方向用例
 * `overriddenDepthsStillLeaveTabHostShallowest` 钉住）。
 */
private val TRANSITION_DEPTH_OVERRIDES = mapOf(
    "ArticleReading" to 28,
    "LearningFlow" to 6,
)

/** 命名前进边用例的对数（终评补口点名的 29→32、30→32、31→32、7→8 四对；扩缩清单须清单、用例、计数三处同改）。 */
private const val NAMED_FORWARD_EDGE_COUNT = 4

/** route 接口里「未覆写支默认 = 序号」的唯一机制；丢了它，默认律没有承载体。 */
private val ROUTE_DEPTH_DEFAULT_ACCESSOR = Regex("""val transitionDepth: Int get\(\) = mountIndex""")

/** 命名前进边的形状：`fun namedEdge<From>To<To>IsForward(`，group 2 = to 支变体名。 */
private val NAMED_FORWARD_EDGE = Regex("""fun namedEdge(.+?)To(.+?)IsForward\(""")
private val ROUTE_VARIANT_DECL = Regex("""^ *data (?:class|object) (\w+)""", RegexOption.MULTILINE)
private val DECLARED_MOUNT_INDEX = Regex("""override val mountIndex = (\d+)""")

/**
 * 层深列的数值取样（scoped 复评 2026-11-09 Important 1 放宽）：两种**字面量**形态都要取到值——
 * `override val transitionDepth = 6` 与 `override val transitionDepth: Int get() = 6`。
 * 表达式形态（`get() = mountIndex + 1`）按设计取不到值，由 [DECLARED_TRANSITION_DEPTH_EXISTS] 那条臂响亮记下，
 * 绝不再走「取不到就当没覆写」。
 */
private val DECLARED_TRANSITION_DEPTH =
    Regex("""override val transitionDepth(?:\s*:\s*Int)?\s*(?:get\(\)\s*)?=\s*(\d+)""")

/**
 * 层深列的**存在性**正则（与序号列不同：本闸认 getter / 表达式等所有写法）。必须带 `override` 前缀——
 * 接口默认访问器 [ROUTE_DEPTH_DEFAULT_ACCESSOR]（`val transitionDepth: Int get() = mountIndex`）不是覆写声明，
 * 误命中会把每一枚未覆写支判成「写法无法取样」，让默认律反而失明（合成用例 defaultAccessorIsNotAnOverride 钉住）。
 */
private val DECLARED_TRANSITION_DEPTH_EXISTS = Regex("""override val transitionDepth\b""")

// ---------------------------------------------------------------------------------------------
// 终评补口 第 2 条（席 B2）：导航身份列的取样正则与身份台账
// ---------------------------------------------------------------------------------------------

/** 身份列的默认访问器（26 枚 `data object` 的唯一承载体，写法与 [ROUTE_DEPTH_DEFAULT_ACCESSOR] 同族）。 */
private val ROUTE_IDENTITY_DEFAULT_ACCESSOR = Regex("""val navigationIdentity: Any get\(\) = this""")

/** 一枚 `data class` 支的身份声明整行（到行尾），取样器据此核对台账点名的主键与支别前缀。 */
private val DECLARED_NAVIGATION_IDENTITY = Regex("""override val navigationIdentity[^\n]*""")

/** 变体声明 + 支别种类（group 1 = class/object，group 2 = 支名）；层深闸的 ROUTE_VARIANT_DECL 不带种类，故另开一枚。 */
private val ROUTE_VARIANT_KIND_DECL = Regex("""^ *data (class|object) (\w+)""", RegexOption.MULTILINE)

/** 身份必须带支别前缀：同一枚主键的两支不同屏不能并成同一身份。 */
private val ROUTE_IDENTITY_BRANCH_PREFIX = Regex("""mountIndex to\b""")

/** 站 1 的 AnimatedContent 参数窗起点行（宿主里唯一以 route 为目标的那枚站点；`\s*$` 容忍 CRLF 工作树）。 */
private val STATION_ONE_TARGET_LINE = Regex("""^\s*targetState = overlayRoute,\s*$""")

/** 参数窗终点：内容 lambda 的起点行 `) { 形参 ->`（Compose 的尾随 lambda 形，花括号在箭头之前）。 */
private val CONTENT_LAMBDA_OPEN = Regex("""^\s*\) \{ (\w+) ->\s*$""")

/** 站 1 的身份接线形状（裁定原话：`contentKey = { it?.navigationIdentity }`）。 */
private val STATION_ONE_CONTENT_KEY = Regex("""contentKey\s*=\s*\{\s*it\?\.navigationIdentity\s*\}""")

/** 全文件的 `contentKey =` 声明（只许站 1 一枚；站 2 的 tab 身份就是 AppTab 枚举本身）。 */
private val STATION_CONTENT_KEY_DECL = Regex("""(?<![A-Za-z0-9_])contentKey\s*=""")

/** 带 payload 的 `data class` 支数（route 表 35 枚里的 9 枚；新增/删改一支须三处同改：route 表、台账、本计数）。 */
private const val NAVIGATION_IDENTITY_OVERRIDE_COUNT = 9

/**
 * 身份台账（支名 → 稳定业务主键的取值表达式，判据 D-4fix-③ 逐枚核对）；取值理由与字段证据：
 *  - `card.cardId`（四枚词卡支）：`WordCard.kt:57` 的 `cardId: String` 就是词卡的业务主键（`wordBookId` 已含在
 *    占位 id 的命名空间里，真实册里 cardId 亦全局唯一）；卡面（lemma/ipa/释义/例句/senses）是易变字段，不进身份。
 *  - `plan.planId`（ReviewQueue / TodayWordList）：`TodayPlanViewModel.kt:72` 的 `planId: String?`，
 *    **可空**⇒ 兜底取 `activeWordBookId`（`:73`）+ `localDateLabel`（`:64`）拼出的非空键（见 route 表注释）；
 *    `newDone`/`dueDone`/`isUnlocked`/队列卡面/`bookProgress` 全是派生态，正是本条裁定要剥掉的那批字段。
 *  - `feature`（AiFeatureScaffold）：`AiFeature` 是 enum（`AiFeature.kt:32`），枚举实例本身就是稳定身份。
 *  - `featureKey`（UnknownAiFeature）：未注册 key 的字符串就是那一屏的业务主键。
 *  - `readingTarget`（ArticleReading）：`ReadingTarget.article.articleId`（`Article.kt:4`）；历史腿（`readingTarget == null`）
 *    没有可携带主键（`historyArticle` 已按第 3 条摘除），落到一枚支内哨兵常量上——全仓历史腿的唯一发起点是
 *    `AppScreen.kt:1872`，进出都经过 ReadingHistory 列表，因此不存在「两篇历史文章同身份而不转场」的真实边。
 */
private val NAVIGATION_IDENTITY_LEDGER = mapOf(
    "VocabularyDetail" to "card.cardId",
    "RelearnCard" to "card.cardId",
    "LearningRecordDetail" to "card.cardId",
    "FavoriteDetail" to "card.cardId",
    "ReviewQueue" to "plan.planId",
    "TodayWordList" to "plan.planId",
    "AiFeatureScaffold" to "feature",
    "UnknownAiFeature" to "featureKey",
    "ArticleReading" to "readingTarget",
)

/** 转场规格面的唯一宿主文件名（判据 D-3-②/④ 的白名单条目；宿主的落点目录见 APP_TRANSITION_SOURCE_PATH）。 */
private const val TRANSITION_SPEC_HOST_NAME = "AppTransition.kt"

/** tab 宿主（原链末 `} else {`，对应 `AppOverlayRouteResolver.TAB_HOST_INDEX`）在链上的条件文本。 */
private const val TAB_HOST_CONDITION = "route == null"

/**
 * 判据 D-3-⑤ 已登记的「冻进 route 但宿主渲染腿无 `route.<字段>` 读者」的 payload 字段（点名，非静默豁免）：
 *  - `featureKey`（`UnknownAiFeature`，route 表 idx 28）：未知 AI 功能 key 的兜底页按链外的 `selectedFeatureKey`
 *    渲染通用脚手架，链上那一支不回读 `route.featureKey`（自 HEAD 起即如此，计划批 D route 表点名的就是这一枚）。
 *
 * 终评补口 第 3 条（席 B2）把 `historyArticle` 从登记名单里**删掉**了，走的是裁定给的第二支：
 * 那枚字段被从 payload 摘除（`AppOverlayRoute.ArticleReading` 改为单参 `readingTarget`），
 * 「冻了却没人读」的形状随之消失——来源判定 `fromHistory` 一直只读 `route.readingTarget == null`（`AppScreen.kt:1921`），
 * 正文由 `articleReadingViewModel` 的 uiState 供给，全仓对 `route.historyArticle` 零读者。
 * 本名单继续钉「不得新增冻了不读的子」：新长出一枚零读者即判红。
 */
private val KNOWN_UNREAD_PAYLOAD_FIELDS = setOf("featureKey")

/** 链上一支的合规形状：条件文本恰为 `route is AppOverlayRoute.<变体名>`。 */
private val ROUTE_CONSUMPTION = Regex("""^route is AppOverlayRoute\.\w+$""")

/** 同一条形状的取名版（判据 3 要的是变体名本身，[ROUTE_CONSUMPTION] 只管合不合规）。 */
private val ROUTE_NAME = Regex("""^route is AppOverlayRoute\.(\w+)$""")

private val CHAIN_HEAD = Regex("""^( *)if \(([^()\n]*)\) \{\s*$""")
private val CHAIN_LEG = Regex("""^( *)\} else if \(([^()\n]*)\) \{\s*$""")
