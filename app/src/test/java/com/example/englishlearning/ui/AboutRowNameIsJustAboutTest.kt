package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * U-12（决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §5）：
 * 「我的」hub 那一行改叫**关于**，与它打开的页面同名；许可仍走下一跳，两跳保留。
 *
 * 旧行名「关于与许可」承诺的是"这一行里面有两件事"，但第三方清单本体并不在关于页里
 * （批 5 之后关于页只留一句入口 `开源许可与隐私说明`，清单住在许可页）。也就是说行名把两跳
 * 说成一跳，用户在这行下面找不到「许可」那一层会以为走错了页。
 * 另一头，直跳许可页也不行：那样「关于」这一层（版本、致谢）就没有入口了——本批把结论写在文档与
 * 判据里，不靠再删一屏来表达（与 U-13 同一个问题的两端）。
 *
 * 计数口径同 [BackupPageNamesFollowHubRowsTest]：**连注释一起扫**。行名是会被抄进别的屏的措辞，
 * 注释里的旧名就是下一批的模板；这里要求的是「关于与许可」在 `ui/` 整棵子树归零。
 */
class AboutRowNameIsJustAboutTest {

    private companion object {
        const val STALE_ROW_NAME = "关于与许可"
        const val ROW_NAME = "关于"
        const val LICENSES_ENTRY = "开源许可与隐私说明"

        val uiDir = File(UI_MAIN_SOURCE_PATH)

        fun read(relativePath: String): String {
            val file = File(uiDir, relativePath)
            assertTrue(file.isFile, "$relativePath 读不到（${file.path}，工作目录 ${File(".").absolutePath}）")
            return file.readText()
        }

        fun occurrences(text: String, phrase: String): Int = Regex(Regex.escape(phrase)).findAll(text).count()

        /** `ui/` 全棵子树里仍写着旧行名的文件（带命中数，便于直接定位）。 */
        fun staleFiles(): List<String> {
            assertTrue(uiDir.isDirectory, "扫描根缺失：${uiDir.path}（工作目录 ${File(".").absolutePath}）")
            return uiDir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { it.relativePathFrom(uiDir) to occurrences(it.readText(), STALE_ROW_NAME) }
                .filter { (_, count) -> count > 0 }
                .map { (path, count) -> "$path ×$count" }
                .sorted()
                .toList()
        }
    }

    @Test
    fun theStaleAboutRowNameIsGoneAcrossUiSources() {
        val hits = staleFiles()
        assertTrue(
            hits.isEmpty(),
            "旧行名「$STALE_ROW_NAME」必须在 `ui/` 归零（含注释）：注释里的旧名是下一批会照抄的模板。" +
                "实际残留：" + hits.joinToString(),
        )
    }

    @Test
    fun theHubRowAndThePageItOpensShareTheName() {
        val hub = read("ProfileScreen.kt")
        assertEquals(
            1,
            occurrences(hub, "ProfileRow(\"$ROW_NAME\", \"profile_open_about\""),
            "hub 关于行必须逐字取「$ROW_NAME」且锚仍是 `profile_open_about`：" +
                "改名不许顺手换 tag，tag 是 connected 判据与宿主接线的身份证",
        )
        val about = read("AboutScreen.kt")
        assertTrue(
            occurrences(about, "title = \"$ROW_NAME\"") >= 1,
            "关于页的页标题必须也是「$ROW_NAME」：行名与落地页同名才读得出「点的就是这页」",
        )
    }

    @Test
    fun theLicensePageStaysOneHopDeeperRatherThanBecomingTheRowTarget() {
        val about = read("AboutScreen.kt")
        assertEquals(
            1,
            occurrences(about, "\"$LICENSES_ENTRY\""),
            "「$LICENSES_ENTRY」必须仍是关于页里恰一处入口：直跳许可页会让「关于」这一层没有入口，" +
                "把它从关于页摘掉则会让许可无处可去",
        )
        val hub = read("ProfileScreen.kt")
        assertEquals(
            0,
            occurrences(hub, LICENSES_ENTRY),
            "hub 不许抄许可入口那句（**注释里复述也算命中**，本条按整份源码扫，见类头口径）：" +
                "那样「关于」行与「许可」行在同一屏并立，本批收的就是这一层重复",
        )
    }
}
