package com.example.englishlearning.backup.restore

/**
 * AC4-07（D-12）：替换提交一旦开始，进程级数据库已被 checkpointAndClose 关闭且永不
 * 复用——当前进程的数据访问终止，UI 必须提供重启入口让用户拿到健康的进程。
 * 设备侧实现负责重新拉起 launcher 并结束当前进程。
 */
interface ProcessRestarter {
    fun relaunch()
}
