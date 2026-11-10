package com.example.englishlearning.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 系统栏（状态栏 / 导航条）底色与图标明暗的对齐点：全仓唯一写窗口的一处。
 *
 * 存在理由（真机走查）：本应用不是 edge-to-edge——窗口被系统栏内缩，内容区从状态栏下沿起算，
 * 于是状态栏/导航条底色只能由窗口属性决定。此前全仓没有任何一处写这两个属性，实机取到的是
 * framework 默认值（深色主题下顶部一条 #757575 亮灰带、底部一条纯黑带），压在深绿页底上非常刺眼；
 * 浅色主题同理，灰带压在近白页底上也脏。
 *
 * 为什么必须在运行期写、而不是只写 themes.xml：App 的主题模式是独立于系统深色开关的用户偏好
 * （rememberAppThemeMode 读 prefs），系统浅色 + App 深色这类组合下，资源里的静态颜色必然是错的。
 * 只有跟着组合期解析出来的 dark 逐次写窗口，两套才都跟得住。themes.xml 里那对静态值只负责
 * Compose 挂载前的那一帧。
 *
 * 图标明暗：深色页底要浅色图标（isAppearanceLightStatusBars = false），反之亦然；
 * 漏掉这一步会在深色页底上得到「深色图标压深色底」的不可读状态栏。
 */
@Composable
fun SyncSystemBars(dark: Boolean, background: Color) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findHostActivityOrNull()?.window ?: return@SideEffect
        val argb = background.toArgb()
        @Suppress("DEPRECATION")
        window.statusBarColor = argb
        @Suppress("DEPRECATION")
        window.navigationBarColor = argb
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/**
 * 从 Compose 的 view context 往上找宿主 Activity：组合树的 context 常被 ContextThemeWrapper
 * 一层层包住，直接 cast 会拿到 null（对话框里的 view 尤其如此）。
 */
private fun Context.findHostActivityOrNull(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
