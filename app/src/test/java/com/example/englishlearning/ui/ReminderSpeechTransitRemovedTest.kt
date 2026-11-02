package com.example.englishlearning.ui

import java.io.File
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * U-13（决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §9）：
 * 「提醒与发音」是一枚**不改任何状态的纯中转页**——本层不读仓库、不写状态，
 * 用户到提醒设置或发音设置都要在这里多点一跳。2026-11-02 用户拍板拆解：
 * hub 台账拆一行成两行直达（「学习提醒」进学习组、「语音朗读」进应用组），中转页整页退役。
 *
 * 三件事各自钉住：
 *  ① 中转页源文件不存在，且它的家族锚（tag、回调名、状态名）在 main 全树归零——
 *     少删一处接线就是一枚「画不出来但编译得过」的死代码，下一次改名时会拖出第二条错链；
 *  ② 页名「提醒与发音」在 main 剥注释后归零：它退役后任何屏上再出现这三个字都是假话
 *     （设置页那句范围说明同批改写）。剥注释口径同 [ReminderRuleOnceContractTest]——
 *     决策文档讲「那一页曾经存在」是合法引用，生产注释里留着旧页名就是下一批的抄写模板；
 *  ③ 真值派生 `SpeechEngineStatuses` / `engineStatuses()` 活着且只住在 `SpeechSettingsScreen.kt`——
 *     它不是中转页的私产（发音屏摘要与 hub「语音朗读」行副标题都吃这一份），
 *     删页时最容易顺手把它一起删掉，本条钉住它必须随搬家幸存。
 *
 * 「点了真的去哪」的渲染侧判据归 connected `ProfileScreenTest`（hub 行逐行落点），
 * 本文件只承源码形状。
 */
class ReminderSpeechTransitRemovedTest {

    private companion object {
        /** 退役中转页的页名：退役后任何屏上再写它都是指着一间不存在的房间。 */
        const val RETIRED_PAGE_TITLE = "提醒与发音"

        val MAIN_KT_ROOT = Path("src/main/java/com/example/englishlearning")

        fun mainKtSources(): List<File> =
            MAIN_KT_ROOT.toFile().walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
    }

    @Test
    fun `the transit screen file is gone and its anchors are zeroed across main`() {
        val transit = File("src/main/java/com/example/englishlearning/ui/ReminderSpeechScreen.kt")
        assertFalse(
            transit.exists(),
            "中转页 `ReminderSpeechScreen.kt` 必须整页退役：它不读仓库、不写状态，" +
                "只让用户多点一跳（U-13，决策 §9）",
        )
        val all = mainKtSources()
        assertTrue(all.size > 50, "main 源树扫描异常（只扫到 ${all.size} 个文件）：夹具坏了要修夹具，不许放宽判据")
        listOf(
            "reminder_speech_screen",
            "reminder_speech_open_reminder",
            "reminder_speech_open_speech",
            "onOpenReminderSpeech",
            "showReminderSpeech",
            "ReminderSpeechScreen",
        ).forEach { anchor ->
            val offenders = all.filter { it.readText().contains(anchor) }
            assertEquals(
                emptyList(),
                offenders,
                "中转页家族锚「$anchor」必须在 main 全树归零（现值 ${offenders.map { it.name }}）：" +
                    "少删一处就是死代码或死链，下一次改名会从这里拖出第二条错链",
            )
        }
    }

    @Test
    fun `the retired page name never reaches a user-visible string again`() {
        // 剥注释口径（同 ReminderRuleOnceContractTest）：注释里讲历史由决策文档承载，不在生产里复读。
        val offenders = mainKtSources()
            .map { it to stripComments(it.readText()) }
            .filter { (_, src) -> src.contains(RETIRED_PAGE_TITLE) }
        assertEquals(
            emptyList(),
            offenders,
            "退役页名「$RETIRED_PAGE_TITLE」在 main 剥注释后必须归零（现值 ${offenders.map { it.first.name }}）：" +
                "那一页已不存在，屏上或说明条里再写它就是把用户指向一间没有的房间",
        )
    }

    /** 反空转：退役页名对真实旧形状必命中，锚清单对合成负片段必命中——否则上面两条对任何树都恒绿。 */
    @Test
    fun `the removal gate recognizes the old transit shapes and is not vacuous`() {
        val oldTransitFixture = """
            @Composable
            fun ReminderSpeechScreen(onOpenReminderSpeech: () -> Unit, onBack: () -> Unit) {
                Column(modifier = Modifier.testTag("reminder_speech_screen")) {
                    AppScreenHeader(title = "提醒与发音", onBack = onBack)
                    EntryRow(
                        title = "学习提醒",
                        modifier = Modifier.testTag("reminder_speech_open_reminder"),
                        onClick = onOpenReminderSpeech,
                    )
                }
            }
            var showReminderSpeech by rememberSaveable { mutableStateOf(false) }
        """.trimIndent()
        assertEquals(
            1,
            Regex(Regex.escape("提醒与发音")).findAll(stripComments(oldTransitFixture)).count(),
            "退役页名没被认出：旧形状放行则归零判据空转",
        )
        listOf(
            "reminder_speech_screen" to 1,
            // 参数定义 + lambda 引用：旧形状里这枚回调天然出现两处。
            "onOpenReminderSpeech" to 2,
            "showReminderSpeech" to 1,
            "ReminderSpeechScreen" to 1,
        ).forEach { (anchor, expected) ->
            assertEquals(
                expected,
                Regex(Regex.escape(anchor)).findAll(oldTransitFixture).count(),
                "家族锚「$anchor」没被认出：旧形状放行则归零判据空转",
            )
        }
    }

    @Test
    fun `the engine status truth survives the removal and lives in exactly one home`() {
        val speechScreen = File("src/main/java/com/example/englishlearning/ui/SpeechSettingsScreen.kt")
        assertTrue(
            speechScreen.exists(),
            "`SpeechSettingsScreen.kt` 读不到（${speechScreen.absolutePath}，工作目录 ${File(".").absolutePath}）",
        )
        val src = speechScreen.readText()
        // 两件搬家幸存物：数据类 + 扩展派生。声明各恰一处，且就在发音屏（发音屏摘要与 hub 行副标题同吃它）。
        assertEquals(
            1,
            Regex("""(?m)^\s*data class SpeechEngineStatuses\(""").findAll(src).count(),
            "`SpeechEngineStatuses` 必须恰声明一处且在 `SpeechSettingsScreen.kt`：中转页退役时它要随搬家幸存，" +
                "发音屏摘要与 hub「语音朗读」行的副标题真值都吃这一份",
        )
        assertEquals(
            1,
            Regex("""(?m)^\s*fun SpeechSettingsUiState\.engineStatuses\(""").findAll(src).count(),
            "`engineStatuses()` 必须恰声明一处且在 `SpeechSettingsScreen.kt`（同上）",
        )
    }
}
