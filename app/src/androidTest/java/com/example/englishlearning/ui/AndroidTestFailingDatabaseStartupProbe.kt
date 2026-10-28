package com.example.englishlearning.ui

import com.example.englishlearning.core.storage.DatabaseStartupProbe

/**
 * androidTest 侧块：**失败**的启动探针夹具（与 `AndroidTestHealthyDatabaseStartupProbe` 成对）。
 *
 * 用来把宿主真的推到 `AppUiState.NeedsRecovery`：恢复态是版式重做里唯一不靠点按就能进的
 * 全屏态，没有这枚夹具就没法在真机/模拟器上验证「导出本机留档」是不是真能出去（对账 1）。
 * 重试一律返回 false，保证点「重试」的用例不会把状态机意外推回 Ready。
 */
internal object AndroidTestFailingDatabaseStartupProbe : DatabaseStartupProbe {
    override val failureReason: String? get() = "数据库文件无法打开"
    override fun retry(): Boolean = false
}
