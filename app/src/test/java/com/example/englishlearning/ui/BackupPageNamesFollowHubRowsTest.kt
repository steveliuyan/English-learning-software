package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * U-11（决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §5）：
 * 「备份与恢复」hub 的行名与落地页标题必须是同一个名字，不许一屏两名。
 *
 * 现状：hub 那四行自称唯一真值（`BackupHubScreen.kt` 的 KDoc「四枚行标题与副标题在本屏是唯一真值」，
 * connected 侧 `BackupEntryUnificationTest` 也按这几句取证），但落地页各叫各的——
 * hub「导出学习数据留档」→ 页面「导出备份」，用户在 hub 点的名字和到了页里看到的标题不是同一个词。
 * 另两条已经对齐（hub 带括号限定、页面取括号前那半），所以本批**按 hub 改落地页**，不改 hub。
 *
 * 为什么这里连注释一起扫（与 [countMatches] 的剥注释口径相反）：改名类判据钉的是「这个名字还有没有人
 * 在写」，注释里的旧名是下一批会照抄的模板（同 [AiGuidanceMapNamingTest] 的口径）。
 *
 * 旧名只按**文件**归零，不做全仓扫：「导出备份」在别的屏里是动词短语
 * （`BackupHubScreen.kt` 的警告「…请先导出备份。」、`LearningRecordsScreen.kt` 的「…记得导出备份。」），
 * 那些句子讲的是动作不是页面名，把它们判成残留会逼下一批去改无关文案。
 */
class BackupPageNamesFollowHubRowsTest {

    private companion object {
        const val HUB_FILE = "ui/BackupHubScreen.kt"
        const val STALE_EXPORT_TITLE = "导出备份"

        /** hub 行标题 → 落地页（页面取括号限定之前的那半名）。 */
        val pairs = listOf(
            Triple("导出学习数据留档", "ui/BackupExportScreen.kt", "导出学习数据留档"),
            Triple("认证导出（可用于新设备恢复）", "ui/BackupPairingExportScreen.kt", "认证导出"),
            Triple("从旧设备恢复（认证恢复）", "ui/BackupRestoreScreen.kt", "从旧设备恢复"),
        )

        val appMain = File(APP_MAIN_SOURCE_PATH)

        fun read(relativePath: String): String {
            val file = File(appMain, relativePath)
            assertTrue(file.isFile, "$relativePath 读不到（${file.path}，工作目录 ${File(".").absolutePath}）")
            return file.readText()
        }

        fun occurrences(text: String, phrase: String): Int = Regex(Regex.escape(phrase)).findAll(text).count()
    }

    @Test
    fun theHubRowTitlesStayPutAndDriveThePageNames() {
        val hub = read(HUB_FILE)
        pairs.forEach { (rowTitle, _, _) ->
            assertEquals(
                1,
                occurrences(hub, "title = \"$rowTitle\""),
                "hub 行名「$rowTitle」必须恰一处：本契约的推导源。hub 改名要同步改这里，不许悄悄换锚",
            )
        }
        // 「导出全部学习数据」这行不在本契约里：它是 hub 内的危险区动作，没有落地页（确认框由宿主承载）。
        assertEquals(1, occurrences(hub, "title = \"删除全部学习数据\""), "hub 第四枚行名不动")
    }

    @Test
    fun eachLandingPageTitledExactlyWhatTheHubRowPromises() {
        pairs.forEach { (rowTitle, pageFile, expectedTitle) ->
            val page = read(pageFile)
            assertTrue(
                occurrences(page, "title = \"$expectedTitle\"") >= 1,
                "$pageFile 的页标题必须是「$expectedTitle」（hub 行「$rowTitle」去掉括号限定后的那半名）：" +
                    "用户在 hub 点的是这个名字，进了页里换名就等于让他确认自己没走错地方",
            )
            assertTrue(
                page.contains("AppScreenHeader("),
                "$pageFile 必须用共享 `AppScreenHeader` 承载标题：自写标题会绕过本契约的「title =」形状",
            )
        }
    }

    @Test
    fun theExportScreenStopsCallingItselfExportBackup() {
        val page = read("ui/BackupExportScreen.kt")
        assertEquals(
            0,
            occurrences(page, STALE_EXPORT_TITLE),
            "旧页名「$STALE_EXPORT_TITLE」必须在 `BackupExportScreen.kt` 整文件归零（含注释）：" +
                "它和 hub 行名「导出学习数据留档」是同一件事的两个名字，留着注释等于给下一批留旧模板。" +
                "注意别的屏里「请先导出备份」那类是动词短语，不在本判据范围内",
        )
        // 反空转：新名在同一份源里必须真的存在，否则上一条 ==0 会因为「整页被删空」而绿。
        assertTrue(
            occurrences(page, "title = \"导出学习数据留档\"") >= 1,
            "「$STALE_EXPORT_TITLE」归零的反面必须是对照片段（新标题在源里），否则本条抓不到「把标题整行删了」这种回退",
        )
    }
}
