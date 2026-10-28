package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 批 5 Task 10（真机目视追加）：学习提醒的**通知权限通路由 launcher 回调扛**，不许再靠组合期快照。
 *
 * 旧写法（真机 `bf353dda` 实测的 1 号缺陷）：主机在组合期把 `notificationGranted` 读成一个布尔快照，
 * 再把它捕获进 `onEnableRequested` 的 lambda——
 *  - 首进本页时该快照必为 `false`（还没申请过），点开关只发出一次 `ActivityCompat.requestPermissions`；
 *  - 用户在系统弹窗点「允许」之后**没有任何回调**落到 `setEnabled(true)`，
 *    于是开关停在关、列表停在「未开启」，要退出本页再进来（快照重算）才显示已开启。
 *
 * 现写法：`rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` 的
 * 结果回调里直接 `setEnabled(true)`；已授权时点一下当场落库，不重复弹窗。
 * **落库口径零改动**：仍然只有 `setEnabled(true)` 一个入口，状态机与偏好写入都没碰。
 *
 * 本契约只读宿主分支（`AppScreen.kt` 的 `else if (showReminderSettings)` 整块）的源码，
 * 按符号与花括号配对取窗口，不锚行号；改判据时配套改本文件的期望值并在批 5 计划与报告里留因。
 */
class ReminderPermissionLaunchContractTest {

    private val source: String by lazy { uiScreenSource("AppScreen.kt") }

    /** 提醒设置分支整块（`else if (showReminderSettings) { … }`），取不到就响亮失败，不许空转。 */
    private val reminderBranch: String by lazy {
        branchBodyAfter(reminderBranchAt, "else if (showReminderSettings)")
    }

    /** 分支锚在 `source` 里的下标（窗口助手都在 `source` 上取位置，下标必须同源，不许跨窗口混用）。 */
    private val reminderBranchAt: Int by lazy {
        val anchor = "else if (showReminderSettings)"
        val at = source.indexOf(anchor)
        if (at < 0) fail("找不到 `$anchor`：提醒设置分支被改名/搬走，本契约不许空转")
        at
    }

    /** `ReminderSettingsScreen(` 调用在 `source` 里的下标（限定在提醒分支之后）。 */
    private val screenCallAt: Int by lazy {
        val at = source.indexOf("ReminderSettingsScreen(", reminderBranchAt)
        if (at < 0) fail("提醒分支里找不到 `ReminderSettingsScreen(`：调用被搬走，本契约不许空转")
        at
    }

    @Test
    fun `the permission result comes back through a launcher callback not a composition snapshot`() {
        assertTrue(reminderBranch.isNotBlank(), "提醒分支窗口是空的：判据会空转")
        assertTrue(
            "rememberLauncherForActivityResult(" in reminderBranch,
            "提醒分支必须挂一枚 `rememberLauncherForActivityResult(`：授权结果没有回调，开关就只能等下次进页才更新",
        )
        assertTrue(
            "ActivityResultContracts.RequestPermission()" in reminderBranch,
            "launcher 的契约必须是 `ActivityResultContracts.RequestPermission()`（只申请通知这一项，不批量要权限）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""notificationGranted""")),
            "组合期快照 `notificationGranted` 必须为 0：它是 1 号缺陷的根，留着就等于把「授权后开关不动」放回来",
        )
        assertTrue(
            countMatches(reminderBranch, Regex("""needsNotificationPermission""")) >= 1,
            "必须保留组合期**判定**（`needsNotificationPermission`）：反空转——两条都不在时上一条断言会白绿",
        )
    }

    @Test
    fun `granting the permission opens the switch in the same frame`() {
        val launcherAt = reminderBranch.indexOf("rememberLauncherForActivityResult(")
        val callbackOpen = reminderBranch.indexOf(')', reminderBranch.indexOf("RequestPermission()", launcherAt))
        assertTrue(launcherAt >= 0 && callbackOpen > launcherAt, "取不到 launcher 回调区，判据会空转")
        val callback = reminderBranch.substring(callbackOpen, reminderBranch.indexOf("ReminderSettingsScreen(", callbackOpen))
        assertTrue(
            "granted" in callback,
            "launcher 的结果回调必须接住 `granted`（布尔结果）：不接就等于又一次「授权完什么都不做」",
        )
        assertTrue(
            "setEnabled(true)" in callback,
            "授权成功后必须在**回调里**当场 `setEnabled(true)`：这是开关能立刻变亮的唯一通路",
        )
        assertTrue(
            "setEnabled(false)" !in callback,
            "回调里不许出现 `setEnabled(false)`：拒绝时保持原状（Android 自己记住拒绝态），不替用户关一次",
        )
    }

    @Test
    fun `the obsolete runtime permission plumbing is gone`() {
        assertEquals(
            0,
            countMatches(source, Regex("""ActivityCompat\.requestPermissions""")),
            "`ActivityCompat.requestPermissions` 必须为 0：权限申请只走 launcher（宿主侧不再有第二条通路）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""REMINDER_PERMISSION_REQUEST_CODE""")),
            "请求码常量必须为 0：`onRequestPermissionsResult` 手工分发的旧链路随 launcher 一起退役",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""onRequestPermissionsResult""")),
            "`onRequestPermissionsResult` 必须为 0：结果由 launcher 回调接，不再走 Activity 覆写",
        )
    }

    @Test
    fun `the screen call keeps its harness and still enables on an already granted device`() {
        val callAt = screenCallAt
        assertTrue(callAt > 0, "提醒分支里必须仍调用 `ReminderSettingsScreen(`")
        val callArgs = callArgsAfter(callAt, "ReminderSettingsScreen(")
        assertTrue(
            "onEnableRequested =" in callArgs,
            "`onEnableRequested` 形参名必须逐字保留：`ReminderSettingsScreenTest` 与本闸都按它读宿主契约",
        )
        assertTrue(
            "notificationLauncher.launch(" in callArgs,
            "`onEnableRequested` 未授权时必须 `notificationLauncher.launch(...)`：别的地方发起申请会绕过上面的回调",
        )
        assertTrue(
            "setEnabled(true)" in callArgs,
            "`onEnableRequested` 在已授权分支里必须直接 `setEnabled(true)`：不重复弹窗也不空转",
        )
        assertTrue(
            "viewModel = reminderSettingsViewModel" in callArgs,
            "`viewModel = reminderSettingsViewModel` 必须逐字保留：本闸只换权限通路，不改数据来源",
        )
    }

    /** 从 [from] 起第一个 `{` 到其配对的 `}`（字符串里的花括号不参与计数）。 */
    private fun branchBodyAfter(from: Int, label: String): String = delimitedAfter(from, label, '{', '}')

    /** 从 [from] 起第一个 `(` 到其配对的 `)`——调用实参表（`ReminderSettingsScreen(` 是括号调用，不是尾随 lambda）。 */
    private fun callArgsAfter(from: Int, label: String): String = delimitedAfter(from, label, '(', ')')

    private fun delimitedAfter(from: Int, label: String, open: Char, close: Char): String {
        val start = source.indexOf(open, from)
        if (start < 0) fail("`$label` 之后找不到 `$open`：判据会空转")
        var depth = 0
        var inString = false
        var escaped = false
        var index = start
        while (index < source.length) {
            val c = source[index]
            when {
                inString -> when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }

                c == '"' -> inString = true
                c == open -> depth++
                c == close -> {
                    depth--
                    if (depth == 0) {
                        val body = source.substring(start + 1, index)
                        if (body.isBlank()) fail("`$label` 的窗口是空的：判据会空转")
                        return body
                    }
                }
            }
            index++
        }
        fail("`$label` 的窗口没有闭合：源码被截断或 `$open` / `$close` 不配对")
        return ""
    }
}
