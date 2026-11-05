package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * U-1 + U-2 + U-3（决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §1）：
 * 出站确认的**对话框、措辞与策略**各只留一份权威。
 *
 * 决策前那一批的实测快照（历史，不再是现状；行号随 consolidation 已失效，别按行号找）：共享件
 * `ui/components/OutboundConfirmDialog.kt` 只有阅读首页一处在调，另外四屏各画一份同名私写对话框，
 * 标题逐字相同、正文各写各的——`SentenceAnalysisScreen.kt` 那一份就漏了「并可能产生费用」。
 * 措辞漂移在这条线上不是文案问题：AGENTS.md 要求「首次调用第三方 Endpoint 前展示域名并确认」，
 * 正文里少了费用告知，用户在一次真实计费调用前就少读了一句。四份实现 = 四次要人肉对齐的机会。
 * 今天的调用点用这条命令重算：`grep -rn "OutboundConfirmDialog(" app/src/main/java --include=*.kt`。
 *
 * 判据都是**跨文件计数**，钉的是「这句文案由谁**写进代码**」：
 *  1. 标题「确认发送请求」全 main 恰一处，且就在共享件里；
 *  2. 正文配方「与该服务的密钥将发送给」全 main 只许出现在共享件（`outboundConfirmBody` 的文本/图片两格）；
 *  3. 每屏各自**恰调一次** `OutboundConfirmDialog(`——0 = 那屏的出站确认整族消失（安全面退化），
 *     2 = 又长出第二份；
 *  4. 屏侧不许再有私写的出站确认 composable（`fun OutboundConfirmationDialog` / `fun ConfirmationDialog`）；
 *  5. U-3：UI 侧第二份枚举 `enum class PayloadKind` 与零调用的 `mustAskEveryTime` 归零，
 *     组件改吃域层 `AiPayloadKind`（真值只由 `ai/AiOutboundConfirmation.kt` 的 `requiredConfirmation` 决定）；
 *  6. 搬家不等于删功能：对话框自带的两枚排印档（标题 + 正文）必须还长在组件里——
 *     各屏版式契约随之少掉的那两处 `AppType` 读点由这一条接住
 *     （`SentenceAnalysisLayoutContractTest` ⑦ 的阈值附注指向这里）。
 *
 * 第 1、2 条走 [countMatches]（**剥注释**、字符串感知）而不是原文计数：本仓的注释惯例正是把权威措辞
 * 引一遍再解释为什么（组件与长难句屏的 KDoc 各引了一次），按原文数会把诚实的文档判成违规。
 * 反过来，`AiGuidanceMapNamingTest` 那类**改名**判据必须连注释一起扫——那里的注释是下一批会照抄的模板，
 * 这里是已经说清楚的历史。两种口径的差别写在各自的消息里，不要互相"统一"。
 *
 * 屏级 tag（`*_outbound_confirmation_text` / `*_confirm_outbound` / `*_decline_outbound` 一族）
 * 由各屏经实参传给共享件，**不迁成组件常量**：各屏版式契约与 connected 都点名它们
 * （`ReadingHomeLayoutContractTest:127` 的教训就是「接线时锚不许静默消失」）。
 */
class OutboundDialogFamilyTest {

    private companion object {
        const val DIALOG_TITLE = "确认发送请求"
        const val BODY_RECIPE = "与该服务的密钥将发送给"
        const val COMPONENT = "ui/components/OutboundConfirmDialog.kt"

        /**
         * 必须经共享件画出站确认的宿主屏。
         *
         * 前四枚是 `2026-11-03-duplication-consolidation.md` §1 实测到的私写屏；第五枚
         * `AiProfileSettingsScreen.kt` 由 2026-11-04 的连接测试探活新增（决策同目录
         * `2026-11-04-ai-profile-vision-and-connection-test.md` §5）：带 Key 的 `GET /models` 对一个新域名的
         * 第一次出站同样是「把文本和密钥发给第三方」，AGENTS.md 那句约束不区分「真请求」和「探活」。
         * 复算：`grep -rn "OutboundConfirmDialog(" app/src/main/java --include=*.kt`。
         */
        val privateScreens = listOf(
            "SentenceAnalysisScreen.kt",
            "PhotoTranslationScreen.kt",
            "WordAiQaScreen.kt",
            "ImageStudioScreen.kt",
            "AiProfileSettingsScreen.kt",
        )

        val appMain = File(APP_MAIN_SOURCE_PATH)
        fun mainText(relativePath: String): String = File(appMain, relativePath).readText()

        /** 代码里的命中数（走 [countMatches]：剥注释、字符串感知，口径见类 KDoc）。 */
        fun count(text: String, phrase: String): Int = countMatches(text, Regex(Regex.escape(phrase)))
    }

    @Test
    fun theDialogTitleIsAuthoredExactlyOnceInMain() {
        val hits = appMain.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.relativePathFrom(appMain) to count(it.readText(), DIALOG_TITLE) }
            .filter { (_, n) -> n > 0 }
            .toList()
        assertEquals(
            listOf("$COMPONENT ×1"),
            hits.map { (path, n) -> "$path ×$n" },
            "出站确认标题「$DIALOG_TITLE」必须恰一处、且在共享件里（U-1）：" +
                "多一处 = 又一屏自己画了对话框，少一处 = 共享件不再说这句话而各屏自行回退",
        )
    }

    @Test
    fun theOutboundBodyRecipeLivesOnlyInTheSharedComponent() {
        val hits = appMain.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.relativePathFrom(appMain) to count(it.readText(), BODY_RECIPE) }
            .filter { (_, n) -> n > 0 }
            .toList()
        assertEquals(
            listOf(COMPONENT),
            hits.map { (path, _) -> path },
            "「$BODY_RECIPE」这句只许由 `outboundConfirmBody` 写（U-2）：" +
                "各屏私写正文是「并可能产生费用」漏字的来历，AGENTS.md 的域名 + 密钥 + 计费三件事必须同源",
        )
        assertEquals(
            2,
            count(mainText(COMPONENT), BODY_RECIPE),
            "共享件正文配方应恰两格（文本 / 图片），实际 ${count(mainText(COMPONENT), BODY_RECIPE)}" +
                "（一格丢了 = 某一档载荷没有正文可走，两格以上 = 组件里又开始分屏写措辞）",
        )
    }

    @Test
    fun eachPrivateScreenCallsTheSharedDialogExactlyOnce() {
        privateScreens.forEach { file ->
            val source = uiScreenSource(file)
            assertEquals(
                1,
                count(source, "OutboundConfirmDialog("),
                "$file 必须恰一处调用共享件 `OutboundConfirmDialog(`（0 = 那屏的出站确认消失，" +
                    "2 = 又长出第二份；U-1）",
            )
        }
    }

    @Test
    fun noScreenAuthorsItsOwnOutboundDialogComposable() {
        val leftovers = privateScreens
            .map { it to count(mainText("ui/$it"), "fun OutboundConfirmationDialog(") + count(mainText("ui/$it"), "fun ConfirmationDialog(") }
            .filter { (_, n) -> n > 0 }
            .toList()
        assertEquals(
            emptyList<String>(),
            leftovers.map { (file, n) -> "$file ×$n" },
            "屏侧不得再私写出站确认 composable（U-1）：私写的那一份正是措辞漂移的载体。实际残留：" +
                leftovers.joinToString { (file, n) -> "$file ×$n" },
        )
    }

    @Test
    fun theSharedDialogStillSetsItsOwnType() {
        // 各屏版式契约里那两处 `AppType` 读点不是被删掉了，是跟着对话框一起搬进本件
        // （`SentenceAnalysisLayoutContractTest` ⑦ 的阈值附注指向这里）。
        // 档数不是一句「>= 1」的安慰句：标题与正文各恰一枚，多一枚 = 组件里又开始分屏排版式，
        // 少一枚 = 对话框有一块退回 Material 默认字。
        val component = mainText(COMPONENT)
        assertEquals(
            1,
            countMatches(component, Regex("""AppType\.Title""")),
            "出站确认框的标题必须恰一处显式排印档（0 = 标题回退默认字，2 = 组件里长出第二种标题形状）",
        )
        // 2026-11-05 弹窗排印统一（决策 `docs/decisions/2026-11-05-dialog-typography-and-wordbook-token-cleanup.md` §1–2）：
        // 正文档从 `AppType.Footnote` 改锚 `AppType.Body`。阈值仍是「恰一枚」，没放松——
        // 这条钉的是「正文必须有显式档」，不是「正文必须是 13sp」；档名换了不改判据强度，
        // 少一枚 = 正文退回 Material 默认字，多一枚 = 组件里又开始分屏排版式。
        assertEquals(
            1,
            countMatches(component, Regex("""AppType\.Body""")),
            "出站确认框的正文（含各屏追加的那句 note）必须恰一处显式排印档（0 = 正文回退默认字，2 = 分屏写排版）",
        )
    }

    @Test
    fun theUiSidePayloadKindAndSecondPolicyPredicateAreGone() {
        val componentSource = mainText(COMPONENT)
        assertEquals(
            0,
            count(componentSource, "enum class PayloadKind"),
            "UI 侧第二份载荷枚举必须删除（U-3）：`ai/AiPayloadKind` 才是域层真值",
        )
        assertEquals(
            0,
            count(componentSource, "fun mustAskEveryTime"),
            "UI 侧第二份「要不要每次问」的判定必须删除（U-3）：规则由 `requiredConfirmation` 定，" +
                "一份零调用却自称权威的纯函数就是下一次漂移的落点",
        )
        // 正文配方按域层两档分派：`when (payloadKind)` 里 Text / Image 各一格。
        // 数「代码里的档位分派」而不是数 `AiPayloadKind` 这个词——那个词在 import、形参、KDoc 里都会出现。
        assertEquals(
            1,
            countMatches(componentSource, Regex("""AiPayloadKind\.Text""")),
            "共享件必须按域层 `AiPayloadKind.Text` 分派文本档（U-3），多一格 = 组件里又开始分屏写措辞",
        )
        assertEquals(
            1,
            countMatches(componentSource, Regex("""AiPayloadKind\.Image""")),
            "共享件必须按域层 `AiPayloadKind.Image` 分派图片档（U-3）",
        )
    }
}
