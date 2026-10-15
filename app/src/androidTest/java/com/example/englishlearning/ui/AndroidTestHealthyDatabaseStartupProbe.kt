package com.example.englishlearning.ui

import com.example.englishlearning.core.storage.DatabaseStartupProbe

/**
 * androidTest 版健康启动探针夹具（test 源集不互通，与 JVM 侧同名物各自一份）：
 * 无失败原因、重试恒成功——既有 AppScreen/Setup 截图测试不受第三构造参数影响。
 */
internal object AndroidTestHealthyDatabaseStartupProbe : DatabaseStartupProbe {
    override val failureReason: String? get() = null
    override fun retry(): Boolean = true
}
