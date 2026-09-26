package com.example.englishlearning.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing

object AppMotion {
    const val Fast = 150
    const val Normal = 250
    const val Slow = 350

    /** 统一缓动：emphasized 风格。 */
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 系统减弱动效：scale=0 时时长归零（MIUI 真机已验证该行为是正确遵守）。 */
    fun durationMs(base: Int, scale: Float): Int =
        if (scale <= 0f) 0 else (base * scale).toInt().coerceAtLeast(1)

    fun animatorDurationScale(context: Context): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}
