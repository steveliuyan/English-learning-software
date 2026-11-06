package com.example.englishlearning.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * 动效 token：2026-10-29 原型对齐批 0 取值（原型 `--dur-fast/--dur/--dur-slow` 与两条缓动）。
 *
 * 这里只有 token，**没有系统设置的读取与换算**——批 A 实测（`docs/decisions/2026-11-06-motion-foundation-and-system-reduced-motion.md`）
 * 证明 Compose 已经自己遵守 `animator_duration_scale`：字节码 `doAnimationFrameWithScale` 写的是
 * `if (durationScale == 0f) animation.durationNanos else (frameTimeNanos - startTimeNanos) / durationScale`；
 * emulator-5554 上 `tween(600)` 在 scale=0 时首帧即落目标值，`infiniteRepeatable(tween(600))` 在 scale=0 时
 * 落一个周期的末尾值后**不再要帧**（2.5s 窗口只观测到 3 次重组，对照 scale=1 的 150 次）。
 * 因此任何「把 token 再乘一次缩放系数」的写法都是双重缩放（scale=0.5 会变成 0.25），
 * 本表曾经提供的 `durationMs` / `animatorDurationScale` 两个换算入口已于批 A 删除，
 * 由 `MotionSpecContractTest` 钉住不得接回。
 *
 * 用途口径（批 C/D 消费本表时按此挑选，不是可选装饰）：
 *  - 入场/展开 `Normal` + [EasingOut]；退场/收起 `Normal` + [EasingIn]。
 *  - 按压、开关滑块、分段格位移这类跟手反馈 `Fast` + [EasingOut]。
 *  - 进度条/段宽这类需要被看见的数值变化 `Slow` + [EasingOut]。
 *  - 页面转场（批 D）`Slow`；骨架 shimmer 这类循环动画必须按「减弱动效下定格在周期末尾值」设计静态外观（批 C）。
 */
object AppMotion {
    const val Fast = 160
    const val Normal = 220
    const val Slow = 300

    /** 入场/展开缓动（原型 `--ease-out: cubic-bezier(.2,.7,.3,1)`）。 */
    val EasingOut = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

    /** 退场/收起缓动（原型 `--ease-in: cubic-bezier(.4,0,.8,.3)`）。 */
    val EasingIn = CubicBezierEasing(0.4f, 0f, 0.8f, 0.3f)
}
