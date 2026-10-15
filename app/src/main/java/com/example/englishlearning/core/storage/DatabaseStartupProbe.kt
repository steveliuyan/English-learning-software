package com.example.englishlearning.core.storage

/**
 * SP-B 决策（2026-10-15 §3）：数据库启动失败必须呈现为**可恢复错误**，
 * 绝不静默删库、绝不 destructive fallback。UI 状态机经此端口读取启动结论，
 * 避免 AppViewModel 直接依赖 AppDatabase 造成 DI 图改动扩散。
 */
interface DatabaseStartupProbe {
    /** 进程启动时数据库打开失败的原因摘要（固定安全短句）；null = 启动正常。 */
    val failureReason: String?

    /** 用户点「重试」后的再次打开（同样绝不删除任何文件）；返回是否恢复成功。 */
    fun retry(): Boolean
}
