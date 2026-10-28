package com.example.englishlearning.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing

/**
 * 动效 token：2026-10-29 原型对齐批 0 取值（原型 `--dur-fast/--dur/--dur-slow` 与两条缓动）。
 */
object AppMotion {
    const val Fast = 160
    const val Normal = 220
    const val Slow = 300

    /** 入场/展开缓动（原型 `--ease-out: cubic-bezier(.2,.7,.3,1)`）。 */
    val EasingOut = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

    /** 退场/收起缓动（原型 `--ease-in: cubic-bezier(.4,0,.8,.3)`）。 */
    val EasingIn = CubicBezierEasing(0.4f, 0f, 0.8f, 0.3f)

    /** 迁移期兼容别名：旧读点仍写 `AppMotion.Easing`，统一指向入场缓动。 */
    val Easing = EasingOut

    /** 系统减弱动效：scale=0 时时长归零（MIUI 真机已验证该行为是正确遵守）。 */
    fun durationMs(base: Int, scale: Float): Int =
        if (scale <= 0f) 0 else (base * scale).toInt().coerceAtLeast(1)

    fun animatorDurationScale(context: Context): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}
