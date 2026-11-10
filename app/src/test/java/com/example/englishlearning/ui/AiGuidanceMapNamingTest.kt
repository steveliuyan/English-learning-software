package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * U-4 + U-5（决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §2、§3）：
 * AI 指引地图改指向真存在的落点，且同一页对外只留一个名字。
 *
 * 为什么要一枚**跨文件、连注释一起扫**的闸：`grep -rn "设置 · AI" app/src/main/java` 本轮实测 21 命中
 * （用户可见文案 17 + 注释 4）；而设置子页自批 5 起只剩外观、2026-11-10（m03373 选项 1）更整体退役，
 * 外观内联进「我的」——也就是说这 21 句都在把用户指向一个**已经不存在的地图**。
 * 单屏 connected（`ImageStudioScreenTest` / `NotConfiguredCardTest` / `SentenceAnalysisScreenTest` /
 * `WordAiQaScreenTest` 各钉一句）只看得到自己那一屏，残留会一直活到下一次走查；
 * hub 台账类（`ProfileHubLedgerTest`）数的是行名，也不看指引句。
 *
 * 判据口径三条，都刻意与 [countMatches] 不同：
 *  - **用原文计数，不剥注释**（[countMatches] 会剥）。注释里的旧地图同样要归零——留着它就等于给
 *    下一批留一份可以直接抄的旧措辞，那正是这 21 处的来历。
 *  - 旧短语**整仓归零**并按文件列出残留清单，而不是只数总量：总数为 0 与逐文件为 0 等价，
 *    但失败消息里点名文件才不至于让人再 grep 一次。
 *  - 新短语**逐文件 ≥1**（那 9 个文件本来就各自持有旧短语，改名后必须各自持有新措辞），
 *    防止「只改了引用测试的那两屏、其余四屏漏掉」这种半成品。
 *
 * 常量而不是字面量：本类的说明文本会复述两枚短语，写字面量会让「旧名归零」这条判据扫到自己的正文
 * （与 `CheckInScreenHeaderShapeContractTest` 的 D-8 改名闸同一口径）。
 */
class AiGuidanceMapNamingTest {

    private companion object {
        const val STALE_POINTER = "设置 · AI"
        const val HUB_POINTER = "我的 → AI 服务"
        const val AI_PAGE_NAME = "AI 服务"
        const val AI_PAGE_ALIAS = "AI 服务与密钥"

        /** 本轮（2026-11-03）实测持有旧地图的文件；重算：`grep -rn "设置 · AI" app/src/main/java --include=*.kt`。 */
        val guidanceFiles = listOf(
            "ai/AiFailure.kt",
            "ui/AiFeature.kt",
            "ui/AiProfileSettingsScreen.kt",
            "ui/AiProfileSettingsViewModel.kt",
            "ui/components/NotConfiguredCard.kt",
            "ui/ImageStudioScreen.kt",
            "ui/PhotoTranslationScreen.kt",
            "ui/SentenceAnalysisScreen.kt",
            "ui/WordAiQaScreen.kt",
        )

        val appMain = File(APP_MAIN_SOURCE_PATH)

        /** 原文命中数（**不剥注释**，短语按字面匹配）。 */
        fun occurrences(text: String, phrase: String): Int = Regex(Regex.escape(phrase)).findAll(text).count()

        /** 全 main 源码里某短语的残留清单（只留 >0 的文件，带相对路径便于直接定位）。 */
        fun staleFiles(phrase: String): List<String> {
            assertTrue(appMain.isDirectory, "扫描根缺失：${appMain.path}（工作目录 ${File(".").absolutePath}）")
            return appMain.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { it.relativePathFrom(appMain) to occurrences(it.readText(), phrase) }
                .filter { (_, count) -> count > 0 }
                .map { (path, count) -> "$path ×$count" }
                .sorted()
                .toList()
        }

        fun read(relativePath: String): String = File(appMain, relativePath).readText()
    }

    @Test
    fun theStaleSettingsAiPointerIsGoneFromEveryMainSourceFile() {
        val hits = staleFiles(STALE_POINTER)
        assertTrue(
            hits.isEmpty(),
            "旧指引「$STALE_POINTER」在 main 源码里必须归零（U-4）：批 5 后 AI 配置入口在「我的」hub，" +
                "设置子页只剩外观，这句话把用户指向不存在的地图。" +
                "注释里的命中同样算残留——下一批会照抄它。实际残留：" + hits.joinToString(),
        )
    }

    @Test
    fun eachGuidanceFileNowNamesTheHubEntry() {
        val missing = guidanceFiles.filter { occurrences(read(it), HUB_POINTER) < 1 }
        assertTrue(
            missing.isEmpty(),
            "这些文件原本各持有一枚「$STALE_POINTER」指引，改名后必须各持有一枚「$HUB_POINTER」" +
                "（U-4：入口名 = hub 行名 = AI 配置页标题）。缺改的文件：" + missing.joinToString(),
        )
    }

    /**
     * U-4 的同一处收口：`openAiProfilesOrSettings`（`AppScreen.kt`）在 `aiProfileViewModel` 缺失时
     * 必须兜底到「我的」hub。历史上这里兜的是 `showSettings = true`（送去只剩「外观」的设置页）——
     * 文案改对了、代码还送错地方就是新漂移。2026-11-10（m03373 选项 1）设置子页整体退役，
     * 这一支改成 `selectedTab = AppTab.PROFILE`；判据仍用**窗口**取样，只钉这一支。
     */
    @Test
    fun theAiProfilesFallbackLandsOnTheHubNotTheSettingsPage() {
        val host = uiScreenSource("AppScreen.kt")
        // `substringAfter` 找不到锚点会**原样返回整串**（假绿入口），所以锚点本身必须先判在。
        assertTrue(
            host.contains("val openAiProfilesOrSettings"),
            "宿主里必须仍有唯一一枚 `openAiProfilesOrSettings`（AI 配置入口的统一通路）；" +
                "锚点缺失时下面的窗口判据会退化成整文件计数，等于没判。",
        )
        val window = host.substringAfter("val openAiProfilesOrSettings").substringBefore("\n            }")
        assertTrue(
            window.contains("AppTab.PROFILE"),
            "AI 配置兜底必须落到「我的」hub（`selectedTab = AppTab.PROFILE`）而不是设置子页；" +
                "兜底分支实际内容：$window",
        )
        assertTrue(
            !window.contains("showSettings = true"),
            "兜底分支里不得再出现 `showSettings = true`——设置子页已整体退役（2026-11-10 m03373 选项 1），该串连编译都过不去。" +
                "兜底分支实际内容：$window",
        )
    }

    /**
     * U-5（决策 §3）：同一页对外只留一个名字。
     *
     * 实测别名（本轮 `grep -rn "AI 服务与密钥" app/src/main`）：发音屏那枚按钮文字
     * （`SpeechSettingsScreen.kt:220`）+ 同屏说明句（`:129`）+ ViewModel 的提示与注释
     * （`SpeechSettingsViewModel.kt:161`、`:192`）。hub 行名与页标题本来就叫「AI 服务」，
     * 所以判据是「**别名归零**」而不是「正名计数」——只数正名会把「哪儿都能写」变成合法。
     */
    @Test
    fun theAiProfilePageHasOneOutwardName() {
        val aliasHits = staleFiles(AI_PAGE_ALIAS)
        assertTrue(
            aliasHits.isEmpty(),
            "AI 配置页对外只叫「$AI_PAGE_NAME」（入口名 = 页标题，U-5）；别名「$AI_PAGE_ALIAS」" +
                "在 main 源码里必须归零，残留意味着同一个页面被念成两件事。实际残留：" + aliasHits.joinToString(),
        )
    }

    /** hub 行名与页标题逐字相同——这是 U-5 的「正名」那一半，也是别名再长出来时的第一道闸。 */
    @Test
    fun theHubRowAndThePageHeaderCarryTheSameName() {
        assertEquals(
            1,
            occurrences(uiScreenSource("ProfileScreen.kt"), """ProfileRow("$AI_PAGE_NAME", "profile_open_ai_profiles""""),
            "hub 里 AI 那一行必须恰一处，且行名就是页标题「$AI_PAGE_NAME」（U-5）",
        )
        assertEquals(
            1,
            occurrences(uiScreenSource("AiProfileSettingsScreen.kt"), """AppScreenHeader(
            title = "$AI_PAGE_NAME","""),
            "AI 配置页页头标题必须是「$AI_PAGE_NAME」（U-5：入口名 = 页标题，两枚逐字相同）",
        )
    }

    /** 保留名闸：这两枚是**故意**留着的不同层，别让下一批"顺手统一"掉。 */
    @Test
    fun theKeptNamesAreStillThere() {
        assertEquals(
            1,
            occurrences(uiScreenSource("AiLearningScreen.kt"), """contentDescription = "AI 服务配置""""),
            "AI 助学页页头齿轮的可及名保留「AI 服务配置」（动作语义，TalkBack 需要）；" +
                "U-5 收的是页名别名，不是这一层",
        )
        assertEquals(
            2,
            occurrences(uiScreenSource("AiFeatureScreen.kt"), "去配置 AI 服务"),
            "AI 功能屏那枚主操作的两处「去配置 AI 服务」（可及名 + 按钮文字）保留：" +
                "它是「动词 + 正名」，不是别名（U-5 决策 §3）",
        )
    }
}
