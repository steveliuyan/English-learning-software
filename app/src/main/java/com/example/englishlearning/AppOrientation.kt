package com.example.englishlearning

import android.app.Activity
import android.content.pm.ActivityInfo

/**
 * 批 B（决策 `docs/decisions/2026-11-06-adaptive-window-layout.md` §10）：横屏只在**大屏**开放。
 *
 * ## 为什么需要这一层
 *
 * 用户 2026-11-05 的话是「横屏也要做适配，不是要硬横过来」。批 B 把版面按窗口宽度分了档
 * （`AppWindowLayoutPolicy`），但设备侧实测给出的是另一件事：手机横屏（`wm size 1920x1080` →
 * **w731dp h339dp**）里主操作整段沉到折叠线下——「AI 问词」三枚问法落在 735dp 处、词卡详情要点两下
 * 才够着。同一批 connected 用例在平板形窗口（w1080dp h737dp）559 例只红 5 枚，在手机横屏 551 例红 35 枚。
 * 也就是说问题不是「没做自适应」，而是**竖排长列的版面在 339dp 高的窗口里本来就不成立**，
 * 而手机横屏恰好就是那种窗口。用户 2026-11-07 据此裁定：手机锁竖屏，sw ≥ 600dp 才自适应。
 *
 * ## 为什么走资源而不是 manifest
 *
 * `android:screenOrientation` 不接受资源引用，做不到「按屏幕形大小分档」；`-sw600dp` 限定符
 * 取的是**最小边**宽，与当前旋转无关——手机转横屏后 sw 仍是 339dp，所以这一形态从入口就被关掉，
 * 不需要运行时猜方向，也不会出现「转过去再转回来」的抖动。
 *
 * ## 契约
 *
 *  - 两个生产 Activity（[MainActivity] 与 `launch.BrandLaunchActivity`）都必须在 `onCreate`
 *    里调用本函数一次；漏掉任何一枚 = 那一枚 Activity 在手机上仍可被转到 339dp 高的横屏。
 *    这条由 `AppOrientationPolicyContractTest` 钉（JVM 源码形状闸；`requestedOrientation` 的
 *    真机形态用设备取证：横持设备后 `dumpsys window | grep mCurrentFocus` + 截图）。
 *  - 本函数**只**设 `requestedOrientation`，不碰 `configChanges`：跨过 600dp 阈值（折叠屏展开）
 *    时按系统默认重建 Activity，不做静默热切——学习态都在 ViewModel/Room，重建不丢数据。
 */
internal object AppOrientationPolicy {

    /**
     * 大屏（sw ≥ 600dp）→ [ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED]（跟随系统旋转设置）；
     * 手机形窗口 → [ActivityInfo.SCREEN_ORIENTATION_PORTRAIT]。
     *
     * 两个常量各出现一处且互斥：`AppOrientationPolicyContractTest` 按「体内恰一处 + 体外零处」计数。
     */
    fun applyTo(activity: Activity) {
        val allowLandscape = activity.resources.getBoolean(R.bool.allow_landscape_layout)
        activity.requestedOrientation = if (allowLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
