package com.example.englishlearning.ui

import com.example.englishlearning.core.storage.DatabaseStartupProbe

/**
 * 测试夹具（不进生产）：健康启动探针——无失败原因、重试恒成功，
 * 让既有 AppViewModel 测试保持原状态机语义，不为 SP-B 的第三构造参数重写断言。
 */
internal object HealthyDatabaseStartupProbe : DatabaseStartupProbe {
    override val failureReason: String? get() = null
    override fun retry(): Boolean = true
}
