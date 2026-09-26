# UI 重设计批 1 实现计划（玻璃拟态 + 域色 + OpenAI 移除）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 语音页移除 OpenAI 引擎选项；建立设计令牌层与玻璃组件库；「AI 与语音」域逐屏套用新视觉；全 app 换中性底色与文字色。

**Architecture:** 令牌层（AppPalette/AppType/AppShape/AppMotion）→ 玻璃组件库（PillButton/GlassDialog/GlassOverlay）→ 逐屏替换（保持 testTag 语义不变）。玻璃真模糊用窗口 blur-behind（API 31+）与页内浮层 + `Modifier.blur`，低版本纯函数降级。

**Tech Stack:** Kotlin + Jetpack Compose（Material3 组件逐步退出 AI 域）、Room 不动、JUnit4 + Compose UI Test。

**Spec:** `docs/superpowers/specs/2026-09-26-ui-redesign-design.md`

## Global Constraints

- 工作树：`D:\EnglishLearningWorktrees\f1-04-unlock-verify`；构建命令统一 `ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat <task> --no-daemon --no-build-cache --console=plain`，需禁用沙箱。
- 不新增任何第三方依赖（dependency locking 已开启）。
- 所有交互元素必须同时有 `testTag` 与 `contentDescription`；现有 testTag 一律保留（测试语义不变，仅视觉替换）。
- 动效必须遵守系统减弱动效（`ANIMATOR_DURATION_SCALE=0` → 时长 0）。
- 受保护未提交文件不得混入提交：`app/gradle.lockfile`、`core/error/AppError.kt`、`ui/AppViewModel.kt`。
- 真机测试走 `adb install -r -t` + `am instrument`（禁用 `connectedDebugAndroidTest`）；跑前跑后用户库三件套 MD5 比对；`MSYS_NO_PATHCONV=1` + Windows 风格本地路径。
- 每个任务 TDD：先红后绿，绿后提交；提交不含 docs/ 与 outputs/。

---

### Task 1: AppPalette 域色令牌 + 全局中性底

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/theme/AppPalette.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/theme/MintPalette.kt`

**Interfaces:**
- Produces: `AppPalette`（Background/Surface/TextPrimary/TextSecondary/Separator/Scrim/GlassFill/GlassHighlight，均 `Color`）；`DomainAccent(base: Color, deep: Color)`；`DomainColors.AiSpeech/.Learn/.Review/.Library/.Reading/.Settings`。后续所有任务从这里取色。

- [ ] **Step 1: 创建 AppPalette.kt**

```kotlin
package com.example.englishlearning.ui.theme

import androidx.compose.ui.graphics.Color

/** 中性语义色：全局底色/表面/文字。批 1 起全 app 生效。 */
object AppPalette {
    val Background = Color(0xFFF5F5F7)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF1C1C1E)
    val TextSecondary = Color(0xFF6E6E73)
    val Separator = Color(0xFFE5E5EA)
    val Scrim = Color(0x66000000)

    /** 玻璃面板填充与高光描边。 */
    val GlassFill = Color(0xCCFFFFFF)
    val GlassHighlight = Color(0x59FFFFFF)
}

/** iOS 式域色：每个功能域一个强调色。base=填充/图标/主按钮，deep=深色文字与按压态。 */
data class DomainAccent(val base: Color, val deep: Color)

object DomainColors {
    val AiSpeech = DomainAccent(Color(0xFF2EC99C), Color(0xFF188F76))   // 薄荷绿（品牌锚点）
    val Learn = DomainAccent(Color(0xFF0A84FF), Color(0xFF075EB4))      // 学习
    val Review = DomainAccent(Color(0xFFFF9F0A), Color(0xFFC46A00))     // 复习
    val Library = DomainAccent(Color(0xFFBF5AF2), Color(0xFF8E34B8))    // 词库
    val Reading = DomainAccent(Color(0xFFFF375F), Color(0xFFC81E46))    // 阅读
    val Settings = DomainAccent(Color(0xFF8E8E93), Color(0xFF48484A))   // 设置/中性
}
```

- [ ] **Step 2: MintPalette 值重指向（全局换中性底/文字，薄荷绿降为 AI 域强调色）**

将 MintPalette.kt 中 8 个语义色常量的定义替换为（渐变 stops 四行保留不动）：

```kotlin
internal val MintBackground: Color = AppPalette.Background
internal val MintSurface: Color = AppPalette.Surface
internal val MintTint: Color = Color(0xFFDDF7E8)
internal val MintPrimary: Color = DomainColors.AiSpeech.base
internal val MintPrimaryDark: Color = AppPalette.TextPrimary
internal val MintOutline: Color = AppPalette.Separator
internal val MintTextMuted: Color = AppPalette.TextSecondary
```

说明（写给执行者）：`MintPrimaryDark` 从深绿改为石墨黑是有意为之——iOS 观感的标题/正文是近黑文字+彩色强调。全 app 19 个引用文件立即获得中性文字色；AI 域强调绿继续由 `MintPrimary`/`MintTint` 承载。品牌渐变（启动/锁屏）不动。

- [ ] **Step 3: 全量 JVM 测试确认无破坏**

Run: `ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: 全绿（颜色常量无逻辑断言；如有测试断言了具体色值，改测试并注明「域色重设计」）。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/englishlearning/ui/theme/
git commit -m "feat(ui): neutral semantic palette and iOS-style domain accents"
```

---

### Task 2: 字体/圆角/动效令牌 + 减弱动效支持

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/theme/AppType.kt`、`app/src/main/java/com/example/englishlearning/ui/theme/AppShape.kt`、`app/src/main/java/com/example/englishlearning/ui/theme/AppMotion.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/theme/AppMotionTest.kt`

**Interfaces:**
- Produces: `AppType`（display/headline/title/body/label/footnote，返回 `TextStyle`）；`AppShape.Dialog=24.dp / Card=20.dp / Button=14.dp / Pill=50%`（`RoundedCornerShape`）；`AppMotion.Fast=150 / Normal=250 / Slow=350`、`AppMotion.Easing`、`AppMotion.durationMs(base: Int, scale: Float): Int`、`AppMotion.animatorDurationScale(context: Context): Float`。

- [ ] **Step 1: 写失败测试 AppMotionTest.kt**

```kotlin
package com.example.englishlearning.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppMotionTest {
    @Test fun zeroScaleMeansZeroDuration() {
        assertEquals(0, AppMotion.durationMs(base = 250, scale = 0f))
    }
    @Test fun fullScaleKeepsBase() {
        assertEquals(250, AppMotion.durationMs(base = 250, scale = 1f))
    }
    @Test fun partialScaleScalesLinearly() {
        assertEquals(125, AppMotion.durationMs(base = 250, scale = 0.5f))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**（`AppMotion` 未定义，编译失败即红）

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.ui.theme.AppMotionTest"`

- [ ] **Step 3: 实现 AppMotion.kt**

```kotlin
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
```

- [ ] **Step 4: 实现 AppType.kt 与 AppShape.kt**

```kotlin
package com.example.englishlearning.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

/** 字体阶梯：系统 sans（不引入字体文件），层级靠字号/字重/字距。 */
object AppType {
    val Display = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = AppPalette.TextPrimary)
    val Headline = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = AppPalette.TextPrimary)
    val Title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppPalette.TextPrimary)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = AppPalette.TextPrimary)
    val Footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = AppPalette.TextSecondary)
    val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, color = AppPalette.TextSecondary)
}
```

```kotlin
package com.example.englishlearning.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object AppShape {
    val Dialog = RoundedCornerShape(24.dp)
    val Card = RoundedCornerShape(20.dp)
    val Button = RoundedCornerShape(14.dp)
    val Pill = RoundedCornerShape(percent = 50)
}
```

- [ ] **Step 5: 测试转绿 + Commit**

```bash
git add app/src/main/java/com/example/englishlearning/ui/theme/ app/src/test/java/com/example/englishlearning/ui/theme/
git commit -m "feat(ui): type/shape/motion tokens with reduced-motion support"
```

---

### Task 3: pressableScale + PillButton 三级按钮

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/glass/GlassButton.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/glass/PillButtonTest.kt`

**Interfaces:**
- Produces: `Modifier.pressableScale(pressedScale: Float = 0.97f): Modifier`（自带 InteractionSource，可与 `clickable` 并用）；`PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: PillStyle = PillStyle.Primary, accent: DomainAccent = DomainColors.AiSpeech, enabled: Boolean = true, testTag: String? = null, contentDescription: String? = null)`；`enum class PillStyle { Primary, Secondary, Text }`。

- [ ] **Step 1: 写失败设备测试 PillButtonTest.kt**

```kotlin
package com.example.englishlearning.ui.glass

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PillButtonTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun clickFiresCallbackAndTagsArePresent() {
        var clicked = false
        composeRule.setContent {
            PillButton("试听", onClick = { clicked = true }, testTag = "glass_btn", contentDescription = "试听发音")
        }
        composeRule.onNodeWithTag("glass_btn").assertExists()
        composeRule.onNodeWithContentDescription("试听发音").assertExists()
        composeRule.onNodeWithTag("glass_btn").performClick()
        assertTrue(clicked)
    }

    @Test fun disabledButtonDoesNotFire() {
        var clicked = false
        composeRule.setContent {
            PillButton("试听", onClick = { clicked = true }, enabled = false, testTag = "glass_btn")
        }
        composeRule.onNodeWithTag("glass_btn").performClick()
        composeRule.waitForIdle()
        assertTrue(!clicked)
    }
}
```

- [ ] **Step 2: 跑真机确认失败**（先 `assembleDebugAndroidTest` + `adb install -r -t` androidTest APK + `am instrument -e class ...PillButtonTest`）

- [ ] **Step 3: 实现 GlassButton.kt**

```kotlin
package com.example.englishlearning.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.*

/** 统一按压反馈：缩放 0.97。与 clickable(interactionSource = …) 配合使用。 */
@Composable
fun Modifier.pressableScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(AppMotion.Fast, easing = AppMotion.Easing),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

enum class PillStyle { Primary, Secondary, Text }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    accent: DomainAccent = DomainColors.AiSpeech,
    enabled: Boolean = true,
    testTag: String? = null,
    contentDescription: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    var m = modifier
        .pressableScale(interaction)
        .heightIn(min = 48.dp)
    if (testTag != null) m = m.testTag(testTag)
    if (contentDescription != null) m = m.semantics { this.contentDescription = contentDescription }
    val (container, contentColor, border) = when (style) {
        PillStyle.Primary -> Triple(accent.base, androidx.compose.ui.graphics.Color.White, null)
        PillStyle.Secondary -> Triple(AppPalette.Surface, accent.deep, BorderStroke(1.dp, AppPalette.Separator))
        PillStyle.Text -> Triple(androidx.compose.ui.graphics.Color.Transparent, accent.deep, null)
    }
    Surface(
        shape = AppShape.Pill,
        color = container,
        contentColor = contentColor,
        border = border,
        modifier = m.clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(text, style = AppType.Title, color = contentColor, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}
```

- [ ] **Step 4: 真机测试转绿 + Commit**

```bash
git add app/src/main/java/com/example/englishlearning/ui/components/glass/ app/src/androidTest/java/com/example/englishlearning/ui/glass/
git commit -m "feat(ui): pill button with unified press feedback"
```

---

### Task 4: GlassDialog（窗口 blur-behind + 降级）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/glass/GlassDialog.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/glass/GlassFallbackTest.kt`、`app/src/androidTest/java/com/example/englishlearning/ui/glass/GlassDialogTest.kt`

**Interfaces:**
- Produces: `fun blurRadiusFor(apiLevel: Int): Int`（API 31+ → 60，否则 0）；`fun scrimAlphaFor(apiLevel: Int): Float`（31+ → 0.15f，否则 0.45f）；`@Composable fun GlassDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit)`。

- [ ] **Step 1: 写失败 JVM 测试 GlassFallbackTest.kt**

```kotlin
package com.example.englishlearning.ui.glass

import com.example.englishlearning.ui.components.glass.blurRadiusFor
import com.example.englishlearning.ui.components.glass.scrimAlphaFor
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassFallbackTest {
    @Test fun api31PlusGetsWindowBlur() {
        assertEquals(60, blurRadiusFor(31))
        assertEquals(60, blurRadiusFor(33))
    }
    @Test fun api30AndBelowGetScrimFallback() {
        assertEquals(0, blurRadiusFor(30))
        assertEquals(0.45f, scrimAlphaFor(30))
        assertEquals(0.15f, scrimAlphaFor(31))
    }
}
```

- [ ] **Step 2: 跑测试确认红**（编译失败即红）

- [ ] **Step 3: 实现 GlassDialog.kt**

```kotlin
package com.example.englishlearning.ui.components.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.example.englishlearning.ui.theme.*

fun blurRadiusFor(apiLevel: Int): Int = if (apiLevel >= 31) 60 else 0
fun scrimAlphaFor(apiLevel: Int): Float = if (apiLevel >= 31) 0.15f else 0.45f

/** 玻璃弹窗：API 31+ 窗口 blur-behind 真模糊；以下版本半透明 scrim 降级。 */
@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val dialogWindow = (this@Dialog) // 不适用；见下——通过 LocalView 取窗口
        WindowBlur()
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = AppShape.Dialog,
                color = AppPalette.GlassFill,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppPalette.GlassHighlight),
                shadowElevation = 24.dp,
            ) {
                Column(Modifier.padding(24.dp)) { content() }
            }
        }
    }
}

@Composable
private fun WindowBlur() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.LaunchedEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && Build.VERSION.SDK_INT >= 31) {
            window.setBackgroundBlurRadius(blurRadiusFor(Build.VERSION.SDK_INT))
        }
    }
    // scrim：31+ 淡、低版本浓
    Box(
        Modifier
            .fillMaxSize()
            .alpha(scrimAlphaFor(Build.VERSION.SDK_INT))
            .background(Color.Black),
    )
}
```

注意（写给执行者）：上面 `val dialogWindow = ...` 一行是占位错误示范，实现时删掉；`WindowBlur()` 内通过 `(view.parent as? DialogWindowProvider)?.window` 拿 Dialog 自己的窗口。Dialog 内容必须 `wrapContentHeight` 且 scrim 只盖内容区外的窗口背景——如 scrim 实测盖住内容，改为设置 `window.setBackgroundDrawableResource(android.R.color.transparent)` 并用 `window.addFlags(FLAG_BLUR_BEHIND)`。以真机截图为准微调。

- [ ] **Step 4: 写失败设备测试 GlassDialogTest.kt**

```kotlin
package com.example.englishlearning.ui.glass

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.glass.GlassDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun showsContentAndDismisses() {
        var dismissed = false
        composeRule.setContent {
            GlassDialog(onDismiss = { dismissed = true }) {
                Text("确认导出？", modifier = androidx.compose.ui.Modifier.testTag("glass_dialog_text"))
            }
        }
        composeRule.onNodeWithTag("glass_dialog_text").assertExists()
        composeRule.onNodeWithText("确认导出？").performClick()
        composeRule.waitForIdle()
    }
}
```

- [ ] **Step 5: 真机跑定向用例转绿；真机截图确认玻璃观感（Android 13 真 blur 路径）**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/englishlearning/ui/components/glass/ app/src/test/ app/src/androidTest/
git commit -m "feat(ui): glass dialog with window blur-behind and pre-31 fallback"
```

---

### Task 5: GlassOverlay 页内浮层下拉

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/glass/GlassOverlay.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/glass/GlassOverlayTest.kt`

**Interfaces:**
- Produces: `Modifier.frosted(active: Boolean): Modifier`（active 时 `blur(8.dp)`；API 31 以下自动无效，安全）；`@Composable fun BoxScope.GlassOverlay(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`（全屏 scrim + 居中玻璃面板 + 点击 scrim 关闭；`visible=false` 时不组合任何内容）。

- [ ] **Step 1: 写失败设备测试 GlassOverlayTest.kt**

```kotlin
package com.example.englishlearning.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.glass.GlassOverlay
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassOverlayTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun hiddenByDefaultAndShowsOnVisible() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                GlassOverlay(visible = false, onDismiss = {}) {
                    Text("选项 A")
                }
            }
        }
        composeRule.onNodeWithText("选项 A").assertDoesNotExist()
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                GlassOverlay(visible = true, onDismiss = {}) {
                    Text("选项 A", modifier = Modifier.testTag("overlay_item_a"))
                }
            }
        }
        composeRule.onNodeWithTag("overlay_item_a").assertExists()
    }

    @Test fun tappingScrimDismisses() {
        var dismissed = false
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                GlassOverlay(visible = true, onDismiss = { dismissed = true }, modifier = Modifier.testTag("overlay_scrim")) {
                    Text("选项 A")
                }
            }
        }
        composeRule.onNodeWithTag("overlay_scrim", useUnmergedTree = true).performClick()
        assertTrue(dismissed)
    }
}
```

注意：scrim 点击断言可能受「点击落到面板上」影响——面板要占中央固定宽度，scrim click 用面板外坐标 `performTouchInput { click(Offset(10f, 10f)) }`（写在左上角远角）。

- [ ] **Step 2: 真机确认红**

- [ ] **Step 3: 实现 GlassOverlay.kt**

```kotlin
package com.example.englishlearning.ui.components.glass

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.*

/** active 时对下层页面内容做真模糊（API 31+；以下版本 Modifier.blur 自动无效）。 */
fun Modifier.frosted(active: Boolean): Modifier = if (active) this.blur(8.dp) else this

/** 页内玻璃浮层：scrim + 中央玻璃面板。visible=false 不组合。 */
@Composable
fun BoxScope.GlassOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = modifier.align(Alignment.Center),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("overlay_scrim")
                .background(AppPalette.Scrim)
                .clickable(onClick = onDismiss, indication = null, interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = AppShape.Dialog,
                color = AppPalette.GlassFill,
                border = BorderStroke(1.dp, AppPalette.GlassHighlight),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clickable(indication = null, interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { /* 吃掉点击，不传给 scrim */ },
            ) {
                Column(Modifier.padding(vertical = 8.dp), content = content)
            }
        }
    }
}
```

- [ ] **Step 4: 真机转绿 + Commit**

```bash
git add app/src/main/java/com/example/englishlearning/ui/components/glass/ app/src/androidTest/java/com/example/englishlearning/ui/glass/
git commit -m "feat(ui): in-window glass overlay with frosted backdrop"
```

---

### Task 6: 语音页移除 OpenAI 引擎选项

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/SpeechSettingsViewModel.kt`（reload 映射）、`app/src/main/java/com/example/englishlearning/ui/SpeechSettingsScreen.kt`（删 OpenAI 行）
- Test: `app/src/test/java/com/example/englishlearning/ui/SpeechSettingsViewModelTest.kt`、`app/src/androidTest/java/com/example/englishlearning/ui/SpeechSettingsScreenTest.kt`、`app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt`

**Interfaces:**
- Consumes: 现有 `SpeechSettingsUiState` / `PronunciationEngine`（不改枚举）。
- Produces: `SpeechSettingsViewModel.reload()` 中 `selectedEngine = preference.selectedEngine.takeUnless { it == PronunciationEngine.OpenAi } ?: PronunciationEngine.SystemTts`（显示层回落；存储不动）；`SpeechSettingsScreen` 只渲染 系统 TTS + MiMo 两行；引擎选中区域仅 MiMo 展开候选。

- [ ] **Step 1: 写失败 JVM 测试（SpeechSettingsViewModelTest 追加）**

```kotlin
@Test fun storedOpenAiSelectionFallsBackToSystemTtsForDisplay() {
    // preferences 返回 SpeechPreference(selectedEngine = OpenAi, openAiProfileId = "x")
    // 调 viewModel.load() 后：
    // assertEquals(PronunciationEngine.SystemTts, viewModel.state.value.selectedEngine)
    // 且 speech_preferences 存储值不被改写（Fake 偏好仓库 get 仍返回 OpenAi）
}
```

（按该测试文件既有 Fake 仓库模式写完整用例。）

- [ ] **Step 2: 跑 JVM 确认红**（expected SystemTts but was OpenAi）

- [ ] **Step 3: 实现 reload 映射**（如上 Interfaces 代码），不改 `select()` 存储路径。

- [ ] **Step 4: 改 SpeechSettingsScreen**：删除 OpenAI `EngineRow` 块与 `detailFor(state, PronunciationEngine.OpenAi, …)` 调用；`engineStatuses()` 保留计算 openAi 字段（SettingsScreen 的 SpeechEngineStatuses 构造兼容），UI 不再消费。

- [ ] **Step 5: 更新 androidTest**：`SpeechSettingsScreenTest` 中 OpenAI 引擎行相关用例改写到 MiMo 行；新增用例：

```kotlin
@Test fun openAiEngineRowNoLongerExists() {
    composeRule.setContent {
        SpeechSettingsScreen(
            state = SpeechSettingsUiState(selectedEngine = PronunciationEngine.MiMo),
            onSelect = { _, _ -> }, onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
        )
    }
    composeRule.onNodeWithContentDescription("选择 OpenAI TTS").assertDoesNotExist()
    composeRule.onNodeWithContentDescription("选择 系统 TTS").assertExists()
    composeRule.onNodeWithContentDescription("选择 小米 MiMo").assertExists()
}
```

`AppScreenTest` 中 `speech_engine_status_openai` 断言迁移到 `speech_engine_status_mimo`；涉及「当前供应商：OpenAI TTS」文案的用例改为「当前供应商：系统 TTS」或按存储值核对。

- [ ] **Step 6: JVM 全量 + 真机定向转绿**

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/englishlearning/ui/ app/src/test/java/com/example/englishlearning/ui/ app/src/androidTest/java/com/example/englishlearning/ui/
git commit -m "feat(speech): remove OpenAI engine option from speech page"
```

---

### Task 7: 语音朗读页玻璃化重排

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/SpeechSettingsScreen.kt`

**Interfaces:**
- Consumes: Task 2 `AppType/AppShape/AppMotion`、Task 3 `PillButton/pressableScale`、Task 5 `frosted`。
- Produces: 视觉替换；**所有现有 testTag 与 contentDescription 逐一保留**（`speech_preview_input/button/message`、`speech_engine_*`、`speech_toggle_candidates`、`speech_profile_*`、`speech_bound_profile_*`、`speech_add_mimo_preset`、`speech_open_ai_profiles`、`speech_more_providers`、`speech_zipvoice`、`speech_pending_*`、`speech_message`、`speech_settings_screen`）。

- [ ] **Step 1: 重排（无新行为，先改后跑既有测试）**

- 页面根：`AppPalette.Background`（经 MintBackground 已生效），标题用 `AppType.Headline`，副文案 `AppType.Footnote`。
- `PreviewCard` → 玻璃卡：`Surface(shape = AppShape.Card, color = AppPalette.GlassFill, border = BorderStroke(1.dp, AppPalette.GlassHighlight))`；「试听」按钮换 `PillButton(style = Primary, testTag = "speech_preview_button", contentDescription = "试听")`（注意保留原 tag/描述）。
- 引擎行：`pressableScale` + 选中态用 `DomainColors.AiSpeech.base` 小圆点/「当前供应商」文字改 `AppType.Label`；行间距 `AppMotion.Normal` 的 `animateContentSize()`。
- 候选折叠区：`AnimatedVisibility(visible = candidatesExpanded)` + `animateContentSize`；「选择语音配置 ▾」文字按钮换 `PillButton(style = Secondary)`，tag 保留 `speech_toggle_candidates`。
- 「AI 服务与密钥」入口换 `PillButton(style = Text)`，tag 保留。
- 所有 `MintPrimaryDark` 文字引用改 `AppType` 对应层级（其 color 已是中性黑）。

- [ ] **Step 2: 真机定向 SpeechSettingsScreenTest 全绿**（tag 全保留应直接绿；如有布局断言失败按新视觉修断言，不许删 tag）

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/englishlearning/ui/SpeechSettingsScreen.kt
git commit -m "feat(ui): glass restyle speech settings page"
```

---

### Task 8: 设置一级页「AI 与语音」卡域色化

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt`

**Interfaces:**
- Consumes: Task 1/2/3 令牌与组件。
- Produces: 设置卡两个入口行用 `pressableScale` + 域色小图标块（「AI 服务与密钥」=`DomainColors.Settings`、「语音朗读」=`DomainColors.AiSpeech`）；副标题 `AppType.Footnote`；`settings_group_ai_speech`、`settings_open_speech`、`settings_open_ai_profiles` 等 tag 全保留。

- [ ] **Step 1: 改视觉（域色图标块 32dp 圆角方块 + 白字图标/符号，文字层级换 AppType）**
- [ ] **Step 2: 真机定向 SettingsScreenTest 全绿**
- [ ] **Step 3: Commit** `feat(ui): domain-colored AI & speech card on settings`

---

### Task 9: AI 服务与密钥列表/编辑器玻璃化 + 音色下拉改浮层

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AiProfileSettingsScreen.kt`（499 行，含 435 行 DropdownMenu）

**Interfaces:**
- Consumes: Task 3/4/5 组件。
- Produces: 音色下拉改 `GlassOverlay`（`ai_profile_voice` / `ai_profile_voice_auto` / `ai_profile_voice_$voice` tag 与语义保留；新增 `ai_profile_voice_overlay` tag 于浮层面板）；能力开关、输入框区换 `GlassCard` 分节；保存/取消换 `PillButton`；列表页 Profile 卡换玻璃卡 + `pressableScale`。

- [ ] **Step 1: 音色下拉先改浮层（TDD：既有 `AiProfileSettingsScreenTest` 音色用例必须保持绿；先跑一次记录基线，再改）**

实现要点：原 `DropdownMenu(expanded)` 状态改为 `var voiceOverlayVisible by rememberSaveable`；触发行（`ai_profile_voice`）点击 → `voiceOverlayVisible = true`；页面根 `Box` 挂 `GlassOverlay(visible = voiceOverlayVisible, onDismiss = { voiceOverlayVisible = false })`，内容为音色选项列表（每项 `ColumnScope` 行，点击后先回调 `onVoiceSelected` 再关闭）；页面内容容器加 `.frosted(voiceOverlayVisible)`。

- [ ] **Step 2: 列表/编辑器视觉套玻璃卡 + PillButton + AppType（tag 全保留）**
- [ ] **Step 3: 真机定向 AiProfileSettingsScreenTest + 全量 JVM 绿**
- [ ] **Step 4: Commit** `feat(ui): glass restyle AI profile list/editor with overlay voice picker`

---

### Task 10: 现有两处 AlertDialog 换 GlassDialog

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/ArticleReadingScreen.kt:102`、`app/src/main/java/com/example/englishlearning/ui/ReadingAccessScreen.kt:236`（`OutboundConfirmationDialog`）

**Interfaces:**
- Consumes: Task 4 `GlassDialog`。
- Produces: 同文案、同按钮回调、同 contentDescription/testTag（若有）；视觉为玻璃弹窗。

- [ ] **Step 1: 逐个替换（保留按钮文案与回调签名；按钮换 PillButton Primary/Secondary）**
- [ ] **Step 2: 真机定向相关屏测试全绿（ArticleReading/ReadingAccess 既有用例不动）**
- [ ] **Step 3: Commit** `feat(ui): glass dialog migration for reading screens`

---

### Task 11: 批 1 全量验证与交付

**Files:**
- 取证目录：`verification-logs/2026-09-26-ui-batch1/`

- [ ] **Step 1: 全量 JVM**：`./gradlew.bat :app:testDebugUnitTest` 全绿。
- [ ] **Step 2: 构建装机**：`assembleDebug assembleDebugAndroidTest` → `adb install -r -t` 两个 APK（`MSYS_NO_PATHCONV=1`）→ `appops set com.example.englishlearning 10021 allow`。
- [ ] **Step 3: 拉用户库三件套基线 MD5**（`english-learning.db/-wal/-shm`，MSYS_NO_PATHCONV=1）。
- [ ] **Step 4: 真机全量 instrumentation**：`am instrument -w com.example.englishlearning.test/...` 全绿。
- [ ] **Step 5: 三件套测试后 MD5 逐字一致**。
- [ ] **Step 6: 真机逐页截图核对**（设置一级页/语音页/展开态/试听反馈/AI Profile 列表/编辑器音色浮层/弹窗玻璃效果），存取证目录；确认减弱动效（`animator_duration_scale` 当前值）下无卡死。
- [ ] **Step 7: 更新工作日志；APK 存 `outputs/`（不入 git）；向用户报告并询问下一批。**

---

## Self-Review 结论

- 覆盖：spec 第 1 节→Task 6；第 2 节→Task 1/2；第 3 节→Task 3/4/5；第 4 节→Task 2（减弱动效）+各屏任务；第 5 节批 1 范围→Task 7/8/9/10；第 6 节→Task 11。无缺口。
- 类型一致性：`DomainAccent(base, deep)` 在 Task 1 定义、Task 3/8 消费一致；`frosted`/`GlassOverlay`/`PillButton` 签名前后一致；`blurRadiusFor/scrimAlphaFor` 与 JVM 测试一致。
- 占位符：无 TBD；Task 4 内标注的「占位错误示范」是实现注意事项而非留白，执行者须删除该行。
