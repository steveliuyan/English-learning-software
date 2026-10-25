# 全应用版式重做 · 批 1 骨架 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把「类型阶梯 + 三档容器 + 底栏第四格 + 学习/词库/我的/阅读/记录/打卡 六屏」按 2026-10-25 规范铺成新骨架，使后续四批只需换皮、不再动骨架。

**Architecture:** 先立地基（AppType 换档、AppColors 增两枚 strong 配对、`StrongCard`/`SoftCard`/`BareRow` 三档容器），再逐屏换装。新页（词库、我的）不新建第二套数据通路：`WordBankScreen` 只做**分段落壳**，四段各自把现成屏当 `@Composable` 槽注入（`showHeader = false`）；`ProfileScreen` 复用从 `SettingsScreen` 抽出的 `SettingsGroupsContent`，原 `SettingsScreen` 保留为 connected 测试的装载缝。所有改动先在 JVM 源码契约测试里写成红灯，再写最小实现转绿。

**Tech Stack:** Kotlin / Jetpack Compose / Material 3、Hilt、JVM 源码契约测试（`stripComments` + 花括号配对取样 + WCAG 相对亮度复算）、本机 Gradle 8.13（`$HOME/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle.bat`，**禁用 `./gradlew` 与 9.x**）。

**Spec:** `docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md`（§3–§8；本计划逐条落地 §6「批 1 骨架」）

---

## Global Constraints

- **改期望值，不放宽判据。** 任何既有契约测试因本次改动变红时，只许把**钉住的现值**改成新值（计数、字面量、集合），不许删断言、不许改成 `assertTrue(true)`、不许放宽到 `>=`。删掉一枚判据必须在同一条提交里写明它被哪条新判据替代。
- **TDD 先红。** 每个任务的 Step 1 是改/写测试，Step 2 必须实跑并看到**预期的那条红**（贴出失败消息），再进 Step 3。
- **门禁口径**（每个任务收尾跑本任务相关测试；Task 9 跑全量）：
  ```bash
  G=$(ls -d "$HOME/.gradle/wrapper/dists/gradle-8.13-bin"/*/gradle-8.13/bin/gradle.bat | head -1)
  "$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.<TestClass>" --offline --no-daemon --no-build-cache
  ```
  Task 9 全量门禁：
  ```bash
  "$G" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
      verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
  ```
- **数据与安全零改动。** 不碰 Room 表、FSRS、备份格式、Keystore、AI 请求体；本批只动 `ui/` 呈现层与 `CheckInViewModel` 的两枚只读字段。
- **真机红线。** 真机 `bf353dda` 只做 `adb install -r` + 手工目视，**永不跑 instrument**；connected 测试只在 `emulator-5574` / `emulator-5576` 用 `am instrument`，**禁 `connectedDebugAndroidTest`**，**禁清数据**。
- **提交身份。** 每枚提交必须 `git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit …`；禁 `--amend`、禁 `--no-verify`；**不 push、不合并 `main`**（两者均未获授权）。
- **共享工作树。** 不 `git stash`；每任务一提交，提交前 `git status --porcelain` 必须只含本任务文件。
- **文案与层级。** 一级页仍只留一层文字层级：页头 → 主卡 → 次卡 → 裸行组。新页面禁止再出现「白卡+描边」的卡片汤（§4.1：强档卡一屏至多一枚）。
- **大字号不退化。** 所有新容器在 `fontScale = 2f` 下必须仍可读、可点（BareRow 命中区 ≥48dp、行高随文字增高而非截断）。

---

## 文件地图

| 动作 | 文件 | 责任 |
|---|---|---|
| 改 | `app/src/main/java/.../ui/theme/AppType.kt` | Display 44sp/tnum、Headline 28sp |
| 改 | `app/src/main/java/.../ui/theme/AppColors.kt` | 新增 `strongSurface` / `onStrong`（16→18 字段） |
| 新 | `app/src/main/java/.../ui/components/Containers.kt` | `StrongCard` / `SoftCard` / `BareRow` / `hairlineTop` |
| 改 | `app/src/main/java/.../ui/components/EntryRow.kt` | 私有 `hairlineTop` 迁出，改调共享版 |
| 改 | `app/src/main/java/.../ui/components/WordRow.kt` | 增 `topDivider` |
| 改 | `app/src/main/java/.../ui/AppTab.kt` / `AppBottomBar.kt` | 第四格「我的」+ `AppIcon.Person` |
| 改 | `app/src/main/java/.../ui/TodayPlanScreen.kt` | 四段式重排 |
| 新 | `app/src/main/java/.../ui/WordBankScreen.kt` | 词库页壳 + 四分段 |
| 改 | `app/src/main/java/.../ui/MyWordsScreen.kt` | 增 `visibleSections` / `showHeader` |
| 改 | `app/src/main/java/.../ui/WordBookWordsScreen.kt` | 增 `showHeader` / `modifier` |
| 新 | `app/src/main/java/.../ui/ProfileScreen.kt` | 我的页 |
| 改 | `app/src/main/java/.../ui/SettingsScreen.kt` | 抽出 `SettingsGroupsContent` |
| 改 | `app/src/main/java/.../ui/ReadingAccessScreen.kt` | 今日文章卡 + 更多组 + 换一篇进页头 |
| 改 | `app/src/main/java/.../ui/LearningRecordsScreen.kt` | 卡→裸行、删「反馈：」前缀 |
| 改 | `app/src/main/java/.../ui/CheckInScreen.kt` / `CheckInViewModel.kt` | Display 唯一 + 去重复完成度 |
| 改 | `app/src/main/java/.../ui/AppScreen.kt` | 词库/我的挂载、检查预加载、回调改线 |
| 改/新 | `app/src/test/java/.../ui/*ContractTest.kt` | 期望值同步 + 四条新契约 |
| 改 | `app/src/androidTest/java/.../ui/*Test.kt` | 装配面同步（编译必须过，运行走模拟器） |

---

### Task 1: 类型换档 + 两枚 strong token

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/theme/AppType.kt:57-58`
- Modify: `app/src/main/java/com/example/englishlearning/ui/theme/AppColors.kt:28-80`
- Test: `app/src/test/java/com/example/englishlearning/ui/DesignTokenContractTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/MaterialThemeAssemblyContractTest.kt`

**Interfaces:**
- Produces: `AppColorSet.strongSurface: Color`、`AppColorSet.onStrong: Color`；`AppType.Display` 44sp Bold / letterSpacing −0.5sp / `fontFeatureSettings = "tnum"`；`AppType.Headline` 28sp Bold。

- [ ] **Step 1: RED — 改测试期望值**

`DesignTokenContractTest`：

1. `lightHex` 末尾追加两行（键是命名键，顺序不影响集合比较；值带 `L` 位形）：
   ```kotlin
   "strongSurface" to 0xFF0C2A22L,
   "onStrong" to 0xFFE9F2ECL,
   ```
2. `darkHex` 末尾追加：
   ```kotlin
   "strongSurface" to 0xFF1E2B25L,
   "onStrong" to 0xFFE9F2ECL,
   ```
3. `crossSetIdenticalNeutralTokenExemptions` 由 `setOf("onBrand")` 改为：
   ```kotlin
   private val crossSetIdenticalNeutralTokenExemptions: Set<String> = setOf("onBrand", "onStrong")
   ```
   并在该常量注释补一句：「`onStrong` 压 `strongSurface` 两套都是浅墨压深底，明暗同值 #E9F2EC 是有意的（容器两套都深）」。
4. 两处 `assertEquals(16, appColorSetFieldNames(appColorsText()).size, …)` 改为 `18`，消息改「spec §2.1 中性 18 枚（本批新增 strongSurface/onStrong）」。
5. `allowed pairs meet WCAG floors computed from source tokens` 追加：
   ```kotlin
   assertAtLeast("onStrong/strongSurface-light", l("onStrong"), l("strongSurface"), 4.5)
   assertAtLeast("dark onStrong/strongSurface", d("onStrong"), d("strongSurface"), 4.5)
   ```
6. `pinned ratio table holds as documented WCAG facts` 追加两条字面量：
   ```kotlin
   assertAtLeast("onStrong/strongSurface", 0xFFE9F2EC.toInt(), 0xFF0C2A22.toInt(), 4.5)
   assertAtLeast("dark onStrong/strongSurface", 0xFFE9F2EC.toInt(), 0xFF1E2B25.toInt(), 4.5)
   ```

`MaterialThemeAssemblyContractTest`：

1. `appTypeSpecSegments` 的 Display 段改为：
   ```kotlin
   "Display" to "private val DisplaySpec = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, " +
       "letterSpacing = (-0.5).sp, fontFeatureSettings = \"tnum\")",
   ```
   Headline 段改为：
   ```kotlin
   "Headline" to "private val HeadlineSpec = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, " +
       "letterSpacing = 0.2.sp)",
   ```
2. `fieldOf` 的 `when` 增两枚分支：
   ```kotlin
   "strongSurface" -> set.strongSurface
   "onStrong" -> set.onStrong
   ```

- [ ] **Step 2: 实跑，确认红**

```bash
G=$(ls -d "$HOME/.gradle/wrapper/dists/gradle-8.13-bin"/*/gradle-8.13/bin/gradle.bat | head -1)
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.DesignTokenContractTest" \
    --tests "com.example.englishlearning.ui.MaterialThemeAssemblyContractTest" --offline --no-daemon --no-build-cache
```
期望：`DesignTokenContractTest` 红在「生产 AppColorSet 解析出的字段数应为 18」，`MaterialThemeAssemblyContractTest` 红在 Display/Headline 字面量。**任何一条因解析器崩掉而红，先修解析器，别改断言。**

- [ ] **Step 3: GREEN — 最小实现**

`AppType.kt:57-58` 整两行替换（**必须写成单行**：`appTypeSpecSegments` 是按连续字符串包含判定的，换行会当场红）：
```kotlin
private val DisplaySpec = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum")
private val HeadlineSpec = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
```
> 注：`Display` 的 getter 仍只 `copy(color = …)`，`fontFeatureSettings` 必须落在 Spec 上，否则 getter 里 copy 会丢。

`AppColors.kt`：
- 在 `data class AppColorSet(` 的 `val success: Color,` **之前**插两行：
  ```kotlin
  /** 强档卡容器底（学习页主卡 / 我的页统计条）：两套都是深底，前景配 [onStrong]。 */
  val strongSurface: Color,
  /** 强档卡前景：明暗同值，压两套 strongSurface 均 ≥4.5（§4.1）。 */
  val onStrong: Color,
  ```
- `AppColorLight` 追加 `strongSurface = Color(0xFF0C2A22), onStrong = Color(0xFFE9F2EC),`
- `AppColorDark` 追加 `strongSurface = Color(0xFF1E2B25), onStrong = Color(0xFFE9F2EC),`
- 文件头「16 个中性 token」与 data class KDoc 的「16 个」改「18 个」。

- [ ] **Step 4: 实跑转绿**

同 Step 2 命令，期望 `BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/theme/AppType.kt \
        app/src/main/java/com/example/englishlearning/ui/theme/AppColors.kt \
        app/src/test/java/com/example/englishlearning/ui/DesignTokenContractTest.kt \
        app/src/test/java/com/example/englishlearning/ui/MaterialThemeAssemblyContractTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): Display/Headline 换档并新增 strong 前景底色配对"
```

---

### Task 2: 三档容器组件 `Containers.kt`

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/Containers.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/components/EntryRow.kt:220-235`
- Test: `app/src/test/java/com/example/englishlearning/ui/SharedComponentUniquenessRatchetTest.kt:244-274`
- Test: `app/src/test/java/com/example/englishlearning/ui/ContainerContractTest.kt`（新）

**Interfaces:**
- Produces:
  ```kotlin
  @Composable fun StrongCard(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(18.dp), content: @Composable ColumnScope.() -> Unit)
  @Composable fun SoftCard(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit)
  @Composable fun BareRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: String? = null, leading: @Composable (() -> Unit)? = null, showChevron: Boolean = true, topDivider: Boolean = false, onClick: (() -> Unit)? = null)
  internal fun Modifier.hairlineTop(color: Color?, startIndent: Dp = 0.dp): Modifier
  val BareRowDividerIndent: Dp = 52.dp
  ```
- Consumes: `appColors()`、`AppType`、`AppGlyph`、`AppIcon.ChevronRight`。

- [ ] **Step 1: RED — 组件棘轮登记 + 新契约**

1. `SharedComponentUniquenessRatchetTest`：
   - `canonicalComponentFiles` 增 `"Containers.kt"`（放在 `"ConfirmDestructiveDialog.kt"` 之前，保持按用途排序）；
   - `canonicalDefinitions` 增：
     ```kotlin
     "StrongCard" to "Containers.kt",
     "SoftCard" to "Containers.kt",
     "BareRow" to "Containers.kt",
     ```
2. 新建 `ContainerContractTest.kt`：
   ```kotlin
   package com.example.englishlearning.ui

   import kotlin.test.Test
   import kotlin.test.assertEquals
   import kotlin.test.assertTrue

   /**
    * 批 1 三档容器的源码契约（spec §4.1）。
    *
    * 三档的分工不靠注释自觉：强档一屏至多一枚、软档不描边、裸行靠 1dp 分隔线与 52dp 缩进
    * 表达「同组」，命中区 ≥48dp。全部按**符号**取源，不锚行号。
    */
   class ContainerContractTest {
       private val containers: String by lazy { stripComments(uiComponentSource("Containers.kt")) }
       private val todayPlan: String by lazy { stripComments(uiScreenSource("TodayPlanScreen.kt")) }

       @Test
       fun `the three tiers are defined exactly once in the shared file`() {
           listOf("fun StrongCard(", "fun SoftCard(", "fun BareRow(").forEach { signature ->
               assertEquals(1, countMatches(containers, Regex(Regex.escape(signature))), "Containers.kt 必须恰一处 $signature")
           }
           // 反空转：同一计数器对「抄两份」的形状必须数出 2。
           assertEquals(2, countMatches("fun BareRow(\nfun BareRow(", Regex(Regex.escape("fun BareRow("))))
       }

       @Test
       fun `the strong card is at most one per screen`() {
           assertEquals(
               1,
               countMatches(todayPlan, Regex("""\bStrongCard\(""")),
               "学习页必须恰一枚强档卡（spec §4.1：一屏至多一枚；0 = 主区没落地，2 = 又回到卡片汤）",
           )
           assertTrue(
               "colors.strongSurface" in containers,
               "强档卡底色必须取 appColors().strongSurface，不许回退到 surface + 描边",
           )
           assertEquals(
               0,
               countMatches(containers, Regex("""\bCard\(""")),
               "Containers.kt 不得退回 M3 Card：三档容器就是替代它的那一层",
           )
       }

       @Test
       fun `the soft card carries no border`() {
           val body = bodyOf(containers, "fun SoftCard(")
           assertTrue(body.isNotEmpty(), "取不到 SoftCard 本体，判据会空转")
           assertTrue("border" !in body, "软档卡禁止描边：层次由 surface 与 strongSurface 的明度差表达")
           assertTrue("RoundedCornerShape(20.dp)" in body, "软档卡圆角 20dp（§4.1）")
       }

       @Test
       fun `the bare row keeps a 52dp divider indent and a 48dp touch floor`() {
           val body = bodyOf(containers, "fun BareRow(")
           assertTrue(body.isNotEmpty(), "取不到 BareRow 本体，判据会空转")
           assertTrue("heightIn(min = 52.dp)" in body, "裸行最小高度 52dp（§4.3 三档行高）")
           assertTrue("padding(horizontal = 15.dp, vertical = 13.dp)" in body, "裸行内边距 15/13（§4.3）")
           assertTrue("hairlineTop(" in body, "裸行靠 hairlineTop 画同组分隔线")
           assertTrue("BareRowDividerIndent" in body, "分隔线必须用具名缩进常量，不许散写 52.dp")
           assertTrue("val BareRowDividerIndent: Dp = 52.dp" in containers, "缩进常量必须是 52dp：与 40dp 图标 + 12dp 间距对齐")
       }

       @Test
       fun `the entry row no longer owns a private hairline helper`() {
           val entry = stripComments(uiComponentSource("EntryRow.kt"))
           assertTrue("private fun hairlineTop(" !in entry, "EntryRow 的私有 hairlineTop 必须迁到 Containers.kt，否则同形两份")
           assertTrue("hairlineTop(" in entry, "EntryRow 必须改调共享 hairlineTop")
       }

       /** 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。 */
       private fun bodyOf(source: String, signature: String): String {
           val start = source.indexOf(signature)
           if (start < 0) return ""
           val open = source.indexOf('{', start)
           if (open < 0) return ""
           var depth = 0
           for (index in open until source.length) {
               when (source[index]) {
                   '{' -> depth++
                   '}' -> {
                       depth--
                       if (depth == 0) return source.substring(open + 1, index)
                   }
               }
           }
           return ""
       }
   }
   ```
   > `bodyOf` 在 `TodayPlanNumberAuthorityTest` 里是**私有**的（不可跨类调用），所以本文件带上自己这一份；`countMatches` / `stripComments` / `uiComponentSource` / `uiScreenSource` 才是 `SourceRatchet.kt` 的包级共享夹具。

- [ ] **Step 2: 实跑，确认红**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.SharedComponentUniquenessRatchetTest" \
    --tests "com.example.englishlearning.ui.ContainerContractTest" --offline --no-daemon --no-build-cache
```
期望：`Containers.kt` 缺失的具名红（`uiComponentSource` 的目录/存在守卫），以及 EntryRow 仍带私有 `hairlineTop`。

- [ ] **Step 3: GREEN — 写组件**

`Containers.kt`：
```kotlin
package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/** 裸行分隔线相对屏内容左缘的缩进：与 40dp 图标 + 12dp 间距对齐（§4.3）。 */
val BareRowDividerIndent: Dp = 52.dp

/**
 * 强档卡（§4.1）：深底 + 28dp 圆角 + 18dp 内边距，一屏至多一枚，承载当日唯一的主数字与主 CTA。
 * 前景文字一律由调用方取 `appColors().onStrong`——本组件不猜文字颜色。
 */
@Composable
fun StrongCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.strongSurface)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** 软档卡（§4.1）：中性 surface + 20dp 圆角 + 16dp 内边距，**不描边**；同组窄行靠 [BareRow] 的分隔线。 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 裸行（§4.1 第三档）：无卡、无描边，同组之间仅 1dp 分隔线（自 [BareRowDividerIndent] 起画）。
 * 命中区 ≥52dp；`onClick == null` 时整行不可点、也不挂 chevron（不可用要看起来不可用）。
 */
@Composable
fun BareRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    leading: @Composable (() -> Unit)? = null,
    showChevron: Boolean = true,
    topDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = appColors()
    val clickable = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .then(hairlineTop(if (topDivider) colors.outlineSoft else null, startIndent = BareRowDividerIndent))
            .heightIn(min = 52.dp)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AppType.Body, color = colors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = AppType.Footnote, color = colors.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Text(trailing, style = AppType.Footnote, color = colors.text2, maxLines = 1)
        }
        if (showChevron && onClick != null) {
            AppGlyph(icon = AppIcon.ChevronRight, tint = colors.text2, size = 16.dp, layer = AppGlyphLayer.StrokeOnly)
        }
    }
}

/** 1dp 顶部分隔线：画在内容盒顶部中线，再下压 1dp 把线让出布局（EntryRow 与 BareRow 共用）。 */
internal fun Modifier.hairlineTop(color: Color?, startIndent: Dp = 0.dp): Modifier = if (color == null) {
    this
} else {
    this
        .drawBehind {
            val thickness = 1.dp.toPx()
            val start = startIndent.toPx()
            drawLine(
                color = color,
                start = Offset(start, thickness / 2f),
                end = Offset(size.width, thickness / 2f),
                strokeWidth = thickness,
                cap = StrokeCap.Butt,
            )
        }
        .padding(top = 1.dp)
}
```

> 注意：`Containers.kt` 里用到 `AppGlyph` / `AppIcon` / `AppGlyphLayer`，它们在 `components/` 同包；`AppType`/`appColors` 需 import。`FontWeight` 未用就删掉 import。

`EntryRow.kt`：删除文件末尾 `private fun hairlineTop(color: Color?)`（220-235 行），文件头 import 增 `import com.example.englishlearning.ui.components.hairlineTop` 不需要（同包），但需确保 `Offset` / `StrokeCap` / `drawBehind` / `Color` 若不再使用则一并清理。

- [ ] **Step 4: 实跑转绿**

同 Step 2 命令，期望全绿；再跑 `:app:compileDebugKotlin` 确认无未解析符号。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/components/Containers.kt \
        app/src/main/java/com/example/englishlearning/ui/components/EntryRow.kt \
        app/src/test/java/com/example/englishlearning/ui/ContainerContractTest.kt \
        app/src/test/java/com/example/englishlearning/ui/SharedComponentUniquenessRatchetTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): 新增强档/软档/裸行三档容器并收编 hairline 助手"
```

---

### Task 3: 底栏第四格改名「我的」+ 换 `Person`

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppTab.kt:13`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppBottomBar.kt:82`
- Test: `app/src/test/java/com/example/englishlearning/ui/AppBottomBarContractTest.kt:262`
- Test: `app/src/test/java/com/example/englishlearning/ui/AppTabTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/AppBottomBarTest.kt:41`

**Interfaces:**
- Produces: `AppTab.SETTINGS.label == "我的"`、`contentDescription == "我的"`；底栏第四枚字形 `AppIcon.Person`（枚举与路径已存在，不需要补字形）。

- [ ] **Step 1: RED**

`AppBottomBarContractTest` 的字形清单改：
```kotlin
listOf("AppIcon.Book", "AppIcon.Newspaper", "AppIcon.Spark", "AppIcon.Person").forEach { glyph ->
    assertEquals(1, count(bar, glyph), "底导字形映射里「$glyph」必须恰一处（学习 Book / 阅读 Newspaper / AI Spark / 我的 Person）")
}
```
`AppTabTest` 追加：
```kotlin
/** 第四格是「我的」而不是「设置」：设置内容并入我的页，标签必须跟着走（2026-10-25 规范 §3.1）。 */
@Test
fun `the fourth destination is the profile hub rather than plain settings`() {
    assertEquals("我的", AppTab.SETTINGS.label)
    assertEquals("我的", AppTab.SETTINGS.contentDescription)
}
```
`AppBottomBarTest`（androidTest）第 41 行标签清单改：
```kotlin
listOf("学习", "阅读", "AI 学", "我的").forEach { label ->
```

- [ ] **Step 2: 实跑，确认红**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.AppBottomBarContractTest" \
    --tests "com.example.englishlearning.ui.AppTabTest" --offline --no-daemon --no-build-cache
```
期望：字形 Pin 与「我的」标签各红一条。

- [ ] **Step 3: GREEN**

`AppTab.kt`：`SETTINGS("设置", "设置"),` → `SETTINGS("我的", "我的"),`
`AppBottomBar.kt`：`AppTab.SETTINGS -> AppIcon.Gear` → `AppTab.SETTINGS -> AppIcon.Person`

- [ ] **Step 4: 实跑转绿**

同 Step 2。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/AppTab.kt \
        app/src/main/java/com/example/englishlearning/ui/AppBottomBar.kt \
        app/src/test/java/com/example/englishlearning/ui/AppBottomBarContractTest.kt \
        app/src/test/java/com/example/englishlearning/ui/AppTabTest.kt \
        app/src/androidTest/java/com/example/englishlearning/ui/AppBottomBarTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): 底栏第四格改为我的并入个案图标"
```

---

### Task 4: 学习页四段式重排

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`（整屏重写）
- Test: `app/src/test/java/com/example/englishlearning/ui/TodayPlanNumberAuthorityTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/TodayPlanEntryColorContractTest.kt` → `git mv` 为 `TodayPlanBareRowContractTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/TodayPlanScreenTest.kt`

**Interfaces:**
- Consumes: `StrongCard` / `SoftCard` / `BareRow`、`AppType.Display/Title/Footnote/Label`、`appColors()`、`appDomain()`。
- Produces:
  ```kotlin
  @Composable fun TodayPlanScreen(
      state: TodayPlanUiState,
      onRetry: () -> Unit = {},
      onOpenSetup: () -> Unit = {},
      onStartLearning: () -> Unit = {},
      onOpenReading: () -> Unit = {},
      onOpenWordBank: () -> Unit = {},
      onReviewWords: () -> Unit = {},
      onOpenSearch: () -> Unit = {},
      streakDays: Int = 0,
      todayArticleRead: Boolean = false,
      modifier: Modifier = Modifier,
  )
  ```
  保留 tag：`today_plan_screen`、`today_plan_search_entry`、`today_plan_summary`、`today_plan_progress_summary`、`today_plan_total_progress`、`today_plan_task_total`、`today_plan_progress`、`today_plan_empty`、`today_plan_start_learning`、`today_plan_reading_status`。
  新增 tag：`today_plan_new_progress`、`today_plan_review_progress`、`today_plan_plan_footnote`、`today_plan_open_word_bank`。
  移出（回 AppScreen 的入口不再由本屏承载）：`today_plan_open_wordbook_words`、`today_plan_open_my_words`、`today_plan_open_learning_records`、`today_plan_open_check_in`、`today_plan_open_setup_entry`。

- [ ] **Step 1: RED — 契约改期望**

`TodayPlanNumberAuthorityTest`（逐条改现值，**不删判据**）：

1. 「Display 恰一枚」保留；「Display + Headline 合计恰一枚」保留（强档卡主数字是唯一 Display，Headline 不出现）。
2. `today_plan_total_progress` 的窗口判据：仍要求窗口内含 Display 与 `NeutralInk`；`NeutralInk` 的实现改为断言窗口内含 `colors.onStrong`（强档卡前景）。
3. 顺序判据 `today_plan_progress_summary` < Display < `today_plan_start_learning` 保留。
4. `EntryRow(` 计数判据（≥3）改为 **`BareRow(` 恰 2**（今日文章状态行 + 词库行），并追加「`EntryRow(` 恒 0」。
5. Footnote 中性墨清单删掉 `today_plan_learned_percent` / `today_plan_book_words` / `today_plan_book_unlearned`（词书卡已并入词库行），保留 `today_plan_progress` / `today_plan_empty`。
6. 六枚 tag 存在性清单替换为上面的「保留 tag + 新增 tag」两组合集，逐枚 `assertTrue(tag in source)`。
7. 搜索框判据：`today_plan_search_entry` 必须出现在 `when (state)` 之前——保留，但把「`EntryRow(`」换成「`SearchBox(`」。

`TodayPlanEntryColorContractTest` → `TodayPlanBareRowContractTest`（`git mv` 后整文件重写为新的两组判据）：
```kotlin
package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 学习页（批 1 §3.2）的两条落点判据：
 *  1. 入口不再平铺在主页——学习页只剩「今日文章状态」与「词库」两条裸行，二者同组；
 *  2. 主 CTA 两态同锚点：进行中与已完成都叫 `today_plan_start_learning`，只换文案与回调。
 */
class TodayPlanBareRowContractTest {
    private val screen: String by lazy { stripComments(uiScreenSource("TodayPlanScreen.kt")) }

    @Test
    fun `the learning home keeps only two bare rows`() {
        assertEquals(2, countMatches(screen, Regex("""\bBareRow\(""")), "学习页只留两条裸行（今日文章 / 词库）")
        assertEquals(0, countMatches(screen, Regex("""\bEntryRow\(""")), "五个入口行必须离场：两个去词库、三个去我的")
        listOf("today_plan_reading_status", "today_plan_open_word_bank").forEach { tag ->
            assertEquals(1, countMatches(screen, Regex(Regex.escape(tag))), "裸行锚「$tag」必须恰一处")
        }
        assertTrue("SoftCard(" in screen, "两条裸行同住一枚软档卡（§4.1 同组分隔线）")
        listOf(
            "today_plan_open_wordbook_words",
            "today_plan_open_my_words",
            "today_plan_open_learning_records",
            "today_plan_open_check_in",
            "today_plan_open_setup_entry",
        ).forEach { gone ->
            assertTrue(gone !in screen, "入口锚「$gone」已迁出学习页（spec §3.2 移出清单），不得残留")
        }
    }

    @Test
    fun `the main cta keeps one anchor across the two states`() {
        assertEquals(1, countMatches(screen, Regex("""\btoday_plan_start_learning""")), "主 CTA 两态必须同锚点，恰一处")
        listOf("开始学习", "开始复习", "去读今日文章", "复习生词本").forEach { label ->
            assertTrue(label in screen, "完成态/进行态文案「$label」缺一枚：四种状态都要有落点")
        }
        assertEquals(1, countMatches(screen, Regex("""\bPillButton\(""")), "主 CTA 只能有一枚 PillButton")
    }
}
```

`TodayPlanScreenTest`（androidTest）：把 `onOpenCheckIn` / `onOpenWordBookWords` / `onOpenMyWords` 三枚具名实参全部删除，补 `onOpenWordBank = {}` / `onReviewWords = {}`；删掉对五枚已移出 tag 的断言；`click closure` 计数从 `1+1+1+rowTags.size` 降为 3（搜索 + 主 CTA + 两条裸行）。

- [ ] **Step 2: 实跑，确认红**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.TodayPlanNumberAuthorityTest" \
    --tests "com.example.englishlearning.ui.TodayPlanBareRowContractTest" --offline --no-daemon --no-build-cache
```
期望：`EntryRow(` 计数、五枚移出 tag、`today_plan_open_word_bank` 缺失各红。

- [ ] **Step 3: GREEN — 重写 `TodayPlanScreen.kt`**

删除 `TodayPlanEntryGroup`、`TodayWorkloadBar` 的旧调用位与旧「词书卡 / 进度卡 / 白色 CTA」三块，按下面结构重写（保留文件内 `learnedPercentLabel` / `WorkloadKind` 中仍被引用的部分；`WorkloadKind` 若已无调用方整段删除）：

```kotlin
@Composable
fun TodayPlanScreen(
    state: TodayPlanUiState,
    onRetry: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onStartLearning: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenWordBank: () -> Unit = {},
    onReviewWords: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    streakDays: Int = 0,
    todayArticleRead: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("today_plan_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppScreenHeader(title = "今日学习")
        SearchBox(onClick = onOpenSearch)
        when (state) {
            TodayPlanUiState.Loading -> Text("正在读取今日计划…", style = AppType.Footnote, color = colors.text2)
            TodayPlanUiState.MissingSetup -> MissingSetupCard(onOpenSetup, onRetry)
            TodayPlanUiState.Unavailable -> UnavailableCard(onRetry)
            is TodayPlanUiState.Ready -> ReadyContent(
                state = state,
                streakDays = streakDays,
                todayArticleRead = todayArticleRead,
                onStartLearning = onStartLearning,
                onOpenReading = onOpenReading,
                onOpenWordBank = onOpenWordBank,
                onReviewWords = onReviewWords,
            )
        }
    }
}

@Composable
private fun SearchBox(onClick: () -> Unit) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.tint)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("today_plan_search_entry")
            .semantics { contentDescription = "搜索单词或短语" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppGlyph(icon = AppIcon.Search, tint = colors.text2, size = 18.dp, layer = AppGlyphLayer.StrokeOnly)
        Text("查一个词", style = AppType.Body, color = colors.text2)
    }
}

@Composable
private fun ReadyContent(
    state: TodayPlanUiState.Ready,
    streakDays: Int,
    todayArticleRead: Boolean,
    onStartLearning: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenWordBank: () -> Unit,
    onReviewWords: () -> Unit,
) {
    val colors = appColors()
    val newDone = state.newDone.coerceAtMost(state.newTarget)
    val dueDone = state.dueDone.coerceAtMost(state.dueTarget)
    val totalDone = newDone + dueDone
    val totalTarget = state.totalTasks
    val allDone = totalDone >= totalTarget && totalTarget > 0
    val cta = when {
        allDone && !todayArticleRead -> "去读今日文章" to onOpenReading
        allDone -> "复习生词本" to onReviewWords
        newDone < state.newTarget -> "开始学习" to onStartLearning
        else -> "开始复习" to onStartLearning
    }

    StrongCard(modifier = Modifier.testTag("today_plan_summary")) {
        Text(state.wordBookName, style = AppType.Title, color = colors.onStrong)
        Text(
            "新学 ${newDone}/${state.newTarget} · 复习 ${dueDone}/${state.dueTarget}",
            style = AppType.Footnote,
            color = colors.onStrong.copy(alpha = 0.72f),
            modifier = Modifier.testTag("today_plan_task_total"),
        )
        Column(
            modifier = Modifier.fillMaxWidth().testTag("today_plan_progress_summary"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$totalDone/$totalTarget",
                    style = AppType.Display,
                    color = colors.onStrong,
                    modifier = Modifier.testTag("today_plan_total_progress"),
                )
                Text(
                    " 项已完成",
                    style = AppType.Footnote,
                    color = colors.onStrong.copy(alpha = 0.72f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            ThinProgress(label = "新学", done = newDone, target = state.newTarget, tag = "today_plan_new_progress", track = colors.onStrong.copy(alpha = 0.18f), ink = colors.onStrong)
            ThinProgress(label = "复习", done = dueDone, target = state.dueTarget, tag = "today_plan_review_progress", track = colors.onStrong.copy(alpha = 0.18f), ink = colors.onStrong)
            if (totalTarget == 0) {
                Text("今天没有待办", style = AppType.Footnote, color = colors.onStrong.copy(alpha = 0.72f), modifier = Modifier.testTag("today_plan_empty"))
            } else if (allDone) {
                Text(
                    "今日已完成 · 连续 $streakDays 天",
                    style = AppType.Footnote,
                    color = colors.onStrong.copy(alpha = 0.72f),
                    modifier = Modifier.testTag("today_plan_plan_footnote"),
                )
            } else {
                Text(
                    if (state.isUnlocked) "今日已解锁文章" else state.unlockReason,
                    style = AppType.Footnote,
                    color = colors.onStrong.copy(alpha = 0.72f),
                    modifier = Modifier.testTag("today_plan_progress"),
                )
            }
        }
        PillButton(
            text = cta.first,
            onClick = cta.second,
            modifier = Modifier.fillMaxWidth(),
            testTag = "today_plan_start_learning",
        )
    }

    SoftCard(contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
        BareRow(
            title = when {
                !state.isUnlocked -> "今日文章 · 未解锁"
                todayArticleRead -> "今日文章 · 已读"
                else -> "今日文章 · 可阅读"
            },
            subtitle = if (state.isUnlocked) "去读今天生成的短文" else "完成今日计划后解锁",
            onClick = onOpenReading,
            leading = { AppGlyph(AppIcon.Newspaper, tint = appDomain(AppDomain.Reading).deep, size = 20.dp, layer = AppGlyphLayer.StrokeOnly) },
            modifier = Modifier.testTag("today_plan_reading_status"),
        )
        BareRow(
            title = state.bookProgress?.let { "${state.wordBookName} · 已学 ${it.learned} / ${it.total}" } ?: state.wordBookName,
            subtitle = "词书 · 生词 · 错词 · 收藏",
            topDivider = true,
            onClick = onOpenWordBank,
            leading = { AppGlyph(AppIcon.Book, tint = appDomain(AppDomain.Library).deep, size = 20.dp, layer = AppGlyphLayer.StrokeOnly) },
            modifier = Modifier.testTag("today_plan_open_word_bank"),
        )
    }
}

@Composable
private fun ThinProgress(label: String, done: Int, target: Int, tag: String, track: Color, ink: Color) {
    val ratio = if (target <= 0) 1f else (done.toFloat() / target).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = AppType.Label, color = ink.copy(alpha = 0.72f), modifier = Modifier.width(28.dp))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(track)
                .testTag(tag),
        ) {
            Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(ink))
        }
        Text("$done/$target", style = AppType.Label, color = ink.copy(alpha = 0.72f))
    }
}
```
> 三处按现有 API 对齐（不改判据）：`PillButton` 的实参名以现签名为准（若是 `title` 而非 `text`，按现名）；`MissingSetupCard` / `UnavailableCard` 用现有私有卡片改名后保留，tag 与 `onRetry` 行为不变。

- [ ] **Step 4: 实跑转绿**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.TodayPlanNumberAuthorityTest" \
    --tests "com.example.englishlearning.ui.TodayPlanBareRowContractTest" --offline --no-daemon --no-build-cache
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt \
        app/src/test/java/com/example/englishlearning/ui/TodayPlanNumberAuthorityTest.kt \
        app/src/test/java/com/example/englishlearning/ui/TodayPlanBareRowContractTest.kt \
        app/src/androidTest/java/com/example/englishlearning/ui/TodayPlanScreenTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): 学习页重排为搜索/强档卡/文章/词库四段"
```

---

### Task 5: 词库页 `WordBankScreen`（四分段壳）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/WordBankScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/MyWordsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordBookWordsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/WordBankScreenContractTest.kt`（新）
- Test: `app/src/test/java/com/example/englishlearning/ui/MyWordsScreenContractTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/ContainerContractTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/WordBankScreenTest.kt`（新，只做装配 smoke）

**Interfaces:**
- Produces:
  ```kotlin
  enum class WordBankSection { Book, New, Wrong, Favorite }

  @Composable fun WordBankScreen(
      wordBookName: String,
      section: WordBankSection,
      onSelectSection: (WordBankSection) -> Unit,
      onBack: () -> Unit,
      bookPane: @Composable () -> Unit,
      newPane: @Composable () -> Unit,
      wrongPane: @Composable () -> Unit,
      favoritePane: @Composable () -> Unit,
      modifier: Modifier = Modifier,
  )
  ```
  以及 `MyWordsScreen(…, visibleSections: List<MyWordsSection> = MyWordsSection.entries, showHeader: Boolean = true)`、`WordBookWordsScreen(…, showHeader: Boolean = true, modifier: Modifier = Modifier)`（新形参**追加在末尾且有默认值**，既有调用点全部不动）。
- Consumes: `AppScreenHeader`、`SoftCard`/`BareRow`、`AppType`、`appColors()`。

- [ ] **Step 1: RED — 新契约 + 宿主接线判据**

新建 `WordBankScreenContractTest.kt`：
```kotlin
package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 词库页（spec §3.3）的源码契约：四分段、一段一面、旧两页降为 pane。
 */
class WordBankScreenContractTest {
    private val bank: String by lazy { stripComments(uiScreenSource("WordBankScreen.kt")) }
    private val host: String by lazy { stripComments(uiScreenSource("AppScreen.kt")) }
    private val myWords: String by lazy { stripComments(uiScreenSource("MyWordsScreen.kt")) }
    private val bookWords: String by lazy { stripComments(uiScreenSource("WordBookWordsScreen.kt")) }

    @Test
    fun `the bank exposes exactly four segments`() {
        assertEquals(
            listOf("Book", "New", "Wrong", "Favorite"),
            WordBankSection.entries.map { it.name },
            "四分段顺序钉死：词书 / 生词 / 错词 / 收藏（§3.3）",
        )
        listOf("word_bank_segment_book", "word_bank_segment_new", "word_bank_segment_wrong", "word_bank_segment_favorite").forEach { tag ->
            assertEquals(1, countMatches(bank, Regex(Regex.escape(tag))), "分段锚「$tag」必须恰一处")
        }
        assertEquals(1, countMatches(bank, Regex("""AppScreenHeader\(""")), "词库页恰一枚共享页头")
        assertTrue("word_bank_screen" in bank, "屏根锚必须存在")
    }

    @Test
    fun `the old screens become panes instead of second headers`() {
        assertEquals(1, countMatches(myWords, Regex("""showHeader: Boolean = true""")), "MyWordsScreen 必须能关掉自己的页头")
        assertTrue("visibleSections" in myWords, "MyWordsScreen 必须能只渲染一段")
        assertEquals(1, countMatches(bookWords, Regex("""showHeader: Boolean = true""")), "WordBookWordsScreen 必须能关掉自己的页头")
        // 宿主只挂一枚词库壳：旧的两枚全屏挂载必须退役。
        assertEquals(1, countMatches(host, Regex("""\bWordBankScreen\(""")), "宿主恰挂一枚 WordBankScreen")
        assertEquals(0, countMatches(host, Regex("""showWordBookWords\b""")), "旧的 showWordBookWords 状态位必须并进 showWordBank")
        assertEquals(0, countMatches(host, Regex("""showMyWords\b""")), "旧的 showMyWords 状态位必须并进 showWordBank")
        assertEquals(3, countMatches(host, Regex("""visibleSections = listOf""")), "生词/错词/收藏三段各注入一面 MyWordsScreen")
    }
}
```

`MyWordsScreenContractTest`：把两处旧口径改期望——
- `today_plan_open_my_words` 的存在性判据改为 `today_plan_open_word_bank`；
- `AppScreen` 挂载判据从 `else if (showMyWords)` + `MyWordsScreen(` 一次，改为 `else if (showWordBank)` + `WordBankScreen(` 一次；
- 段标签判据从「三张卡同屏」改为「`visibleSections` 收窄后只渲染点名段」。

`ContainerContractTest` 的 `screens` 名单追加 `WordBankScreen.kt`（StrongCard 计数 ≤1 的适用范围扩到这里）。

- [ ] **Step 2: 实跑，确认红**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.WordBankScreenContractTest" \
    --tests "com.example.englishlearning.ui.MyWordsScreenContractTest" \
    --tests "com.example.englishlearning.ui.ContainerContractTest" --offline --no-daemon --no-build-cache
```

- [ ] **Step 3: GREEN**

1. 新建 `WordBankScreen.kt`：
```kotlin
package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/** 词库四分段（§3.3）：词书 / 生词 / 错词 / 收藏。 */
enum class WordBankSection(val label: String, val tag: String) {
    Book("词书", "word_bank_segment_book"),
    New("生词", "word_bank_segment_new"),
    Wrong("错词", "word_bank_segment_wrong"),
    Favorite("收藏", "word_bank_segment_favorite"),
}

/**
 * 词库页壳：只做「页头 + 分段 + 一段一面」，四面的数据与交互仍由宿主与既有屏承担。
 * 这样批 1 不新建第二套数据通路，也避免把 `MyWordsScreen` / `WordBookWordsScreen` 的逻辑搬两遍。
 */
@Composable
fun WordBankScreen(
    wordBookName: String,
    section: WordBankSection,
    onSelectSection: (WordBankSection) -> Unit,
    onBack: () -> Unit,
    bookPane: @Composable () -> Unit,
    newPane: @Composable () -> Unit,
    wrongPane: @Composable () -> Unit,
    favoritePane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("word_bank_screen"),
    ) {
        AppScreenHeader(title = "词库", subtitle = wordBookName, onBack = onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.tint2)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            WordBankSection.entries.forEach { cell ->
                val selected = cell == section
                Text(
                    text = cell.label,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.surface else colors.tint2)
                        .clickable(role = Role.Tab) { onSelectSection(cell) }
                        .testTag(cell.tag)
                        .semantics {
                            contentDescription = cell.label
                            role = Role.Tab
                            stateDescription = if (selected) "已选中" else "未选中"
                        }
                        .padding(vertical = 13.dp),
                    style = AppType.Body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (selected) colors.text1 else colors.text2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        when (section) {
            WordBankSection.Book -> bookPane()
            WordBankSection.New -> newPane()
            WordBankSection.Wrong -> wrongPane()
            WordBankSection.Favorite -> favoritePane()
        }
    }
}
```

2. `MyWordsScreen.kt`：签名末尾追加两枚有默认值的形参；页头与三张卡各自包一层 `if`；滚动效应加护栏：
```kotlin
    visibleSections: List<MyWordsSection> = MyWordsSection.entries,
    showHeader: Boolean = true,
```
```kotlin
        if (showHeader) {
            AppScreenHeader(title = "我的词", subtitle = subtitle, onBack = onBack)
        }
```
```kotlin
        LaunchedEffect(section) {
            if (section !in visibleSections) return@LaunchedEffect
            // 原有滚动逻辑不动
        }
```
三张卡各自 `if (MyWordsSection.Favorites in visibleSections) { MyWordsSectionCard(…) }`（生词/错词同理）。

3. `WordBookWordsScreen.kt`：签名末尾追加 `showHeader: Boolean = true, modifier: Modifier = Modifier`；根 Column 改 `modifier = modifier.fillMaxSize()…`；私有页头 Row（`wordbook_words_back`）包 `if (showHeader) { … }`。

4. `AppScreen.kt` 接线：
- 变量：`var showMyWords` / `var showWordBookWords` / `val wordBookWordsListState` 三行合并为
  ```kotlin
  var showWordBank by rememberSaveable(state.profile.id) { mutableStateOf(false) }
  var wordBankSection by rememberSaveable(state.profile.id) { mutableStateOf(WordBankSection.Book) }
  val wordBookWordsListState = key(state.profile.id, showWordBank) { LazyListState() }
  ```
- 所有 `showMyWords` → `showWordBank`（含两处 `LaunchedEffect` 与删除回执刷新；条件保留 `in` 语义：错词段加载改为 `showWordBank && wordBankSection == WordBankSection.Wrong`，生词段同理在段选择时加载）。
- `overlayOpen` 里的 `showWordBookWords ||` 与 `|| showMyWords` 从两行并成一行 `showWordBank ||`。
- 两个 `BackHandler`（`showMyWords` 与 `showWordBookWords`）合成一个：
  ```kotlin
  BackHandler(
      enabled = showWordBank && selectedFavoriteCard == null &&
          selectedSearchCard == null && selectedLearningRecordCard == null,
  ) { showWordBank = false }
  ```
- 挂载：删掉 `else if (showWordBookWords) { WordBookWordsScreen(…) }` 与 `else if (showMyWords) { MyWordsScreen(…) }` 两段，改在**原 showMyWords 位置**挂一枚：
  ```kotlin
  } else if (showWordBank) {
      WordBankScreen(
          wordBookName = todayReady?.wordBookName ?: "未选择词书",
          section = wordBankSection,
          onSelectSection = { wordBankSection = it },
          onBack = { showWordBank = false },
          bookPane = {
              WordBookWordsScreen(
                  state = wordBookWordsState,
                  onBack = {},
                  onFilter = { wordBookWordsViewModel?.selectFilter(it) },
                  onRetry = { wordBookWordsViewModel?.load(todayReady?.activeWordBookId) },
                  onOpenWord = { selectedWordBookWord = it },
                  listState = wordBookWordsListState,
                  showHeader = false,
              )
          },
          newPane = {
              MyWordsScreen(/* 原 MyWordsScreen 实参原样照搬，下同 */
                  section = MyWordsSection.Wordbook,
                  visibleSections = listOf(MyWordsSection.Wordbook),
                  showHeader = false,
              )
          },
          wrongPane = { MyWordsScreen(section = MyWordsSection.Wrong, visibleSections = listOf(MyWordsSection.Wrong), showHeader = false, /* 其余实参照搬 */) },
          favoritePane = { MyWordsScreen(section = MyWordsSection.Favorites, visibleSections = listOf(MyWordsSection.Favorites), showHeader = false, /* 其余实参照搬 */) },
      )
  }
  ```
  > 「其余实参照搬」不是省略：四面的 `favoritesState` / `vocabularyState` / `wrongWordsState` / 六个回调 / `masteryState` / `onRetry` / `zoneId` 必须逐枚从原 `MyWordsScreen(...)` 调用处复制过来，一处不少。`bookPane` 的 `state` 取原 `WordBookWordsScreen(...)` 里的同一个状态变量。
- LEARNING 分支：`onOpenWordBookWords = { showWordBookWords = true }` 与 `onOpenMyWords = { showMyWords = true }` 删除，改 `onOpenWordBank = { wordBankSection = WordBankSection.Book; showWordBank = true }`、`onReviewWords = { wordBankSection = WordBankSection.New; showWordBank = true }`、`onOpenCheckIn` / `onOpenLearningRecords` / `onOpenSetup` 三枚删除（学习页不再有这三个入口）。

- [ ] **Step 4: 实跑转绿**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.WordBankScreenContractTest" \
    --tests "com.example.englishlearning.ui.MyWordsScreenContractTest" \
    --tests "com.example.englishlearning.ui.ContainerContractTest" --offline --no-daemon --no-build-cache
"$G" :app:compileDebugKotlin --offline --no-daemon --no-build-cache
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/WordBankScreen.kt \
        app/src/main/java/com/example/englishlearning/ui/MyWordsScreen.kt \
        app/src/main/java/com/example/englishlearning/ui/WordBookWordsScreen.kt \
        app/src/main/java/com/example/englishlearning/ui/AppScreen.kt \
        app/src/test/java/com/example/englishlearning/ui/WordBankScreenContractTest.kt \
        app/src/test/java/com/example/englishlearning/ui/MyWordsScreenContractTest.kt \
        app/src/test/java/com/example/englishlearning/ui/ContainerContractTest.kt \
        app/src/androidTest/java/com/example/englishlearning/ui/WordBankScreenTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): 新建词库页四分段壳并合并两个旧入口"
```

---

### Task 6: 我的页 `ProfileScreen` + 设置内容抽出

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt`（抽出 `SettingsGroupsContent`）
- Create: `app/src/main/java/com/example/englishlearning/ui/ProfileScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/CheckInUiState`（`CheckInViewModel.kt`：`streakDays` / `learnedWordCount` / `load(keepReady)`）
- Test: `app/src/test/java/com/example/englishlearning/ui/ProfilePageContractTest.kt`（新）
- Test: `app/src/test/java/com/example/englishlearning/ui/CheckInViewModelTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/SettingsNoDeveloperJargonTest.kt`（不改判据，仅确认仍绿）
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/ProfileScreenTest.kt`（新，装配 smoke）

**Interfaces:**
- Produces:
  ```kotlin
  @Composable internal fun SettingsGroupsContent(
      wordBookName: String?,
      onOpenSetup: () -> Unit,
      onOpenWorksheet: () -> Unit,
      onOpenAiProfiles: () -> Unit,
      onOpenSpeechSettings: () -> Unit,
      onOpenReminderSettings: () -> Unit,
      onOpenBackupHub: () -> Unit,
      aiProfileSubtitle: String?,
      speechEngineStatuses: SpeechEngineStatuses,
      onImportWordBook: () -> Unit,
      onExportWordBook: () -> Unit,
      importedBookCount: Int,
      transferBusy: Boolean,
      transferMessage: String?,
      onOpenLicenses: () -> Unit,
  )

  @Composable fun ProfileScreen(
      profileName: String,
      streakDays: Int,
      learnedWordCount: Int,
      articleCount: Int,
      onOpenWordBank: () -> Unit,
      onOpenCheckIn: () -> Unit,
      onOpenReadingHistory: () -> Unit,
      onOpenLearningRecords: () -> Unit,
      settingsContent: @Composable ColumnScope.() -> Unit,
      modifier: Modifier = Modifier,
  )
  ```
  `CheckInUiState.Ready` 增 `streakDays: Int = 0`、`learnedWordCount: Int = 0`；`CheckInViewModel.load(profileId: String, keepReady: Boolean = false)`。
- Consumes: `StrongCard`/`SoftCard`/`BareRow`、`SettingsGroups`、`AppType`。

- [ ] **Step 1: RED**

新建 `ProfilePageContractTest.kt`：
```kotlin
package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 我的页（spec §3.6）：档案头 + 三格统计深链 + 学习记录行 + 原设置四组九行。 */
class ProfilePageContractTest {
    private val profile: String by lazy { stripComments(uiScreenSource("ProfileScreen.kt")) }
    private val host: String by lazy { stripComments(uiScreenSource("AppScreen.kt")) }
    private val settings: String by lazy { stripComments(uiScreenSource("SettingsScreen.kt")) }

    @Test
    fun `three stat cells each carry their own deep link`() {
        listOf("profile_stat_learned", "profile_stat_streak", "profile_stat_articles").forEach { tag ->
            assertEquals(1, countMatches(profile, Regex(Regex.escape(tag))), "统计格锚「$tag」必须恰一处")
        }
        listOf("onOpenWordBank", "onOpenCheckIn", "onOpenReadingHistory").forEach { cb ->
            assertTrue(cb in profile, "统计格回调「$cb」必须由本页接出，不许只画数字")
        }
        assertEquals(1, countMatches(profile, Regex("""\bStrongCard\(""")), "三格统计住唯一一枚强档卡（§4.1）")
        assertTrue("AppType.Headline" in profile, "统计数值用 Headline（§4.2），不许再上一枚 Display")
        assertTrue("AppType.Display" !in profile, "我的页不出现 Display：全屏只学习页与打卡页各一枚")
    }

    @Test
    fun `the settings ledger is reused not duplicated`() {
        assertEquals(1, countMatches(profile, Regex("""\bsettingsContent\(\)""")), "我的页必须调用宿主注入的设置内容槽")
        assertEquals(1, countMatches(settings, Regex("""internal fun SettingsGroupsContent\(""")), "设置内容必须抽成可复用的一段")
        assertTrue("SettingsScreen(" in host, "旧 SettingsScreen 保留为装载缝（connected 测试仍挂它）")
        assertTrue("ProfileScreen(" in host, "宿主必须挂 ProfileScreen")
        assertTrue("profile_open_learning_records" in profile, "学习记录一行的锚必须存在")
        assertEquals(0, countMatches(profile, Regex("""Mint[A-Z]\w*""")), "我的页不得读 Mint* 静态 token")
        assertEquals(0, countMatches(profile, Regex("""Color\(0x""")), "我的页不得出现裸 hex")
    }
}
```

`CheckInViewModelTest`：追加一条（用现成的 fake repository，构造 `streak = 3`、`learnedWordCount = 12`）：
```kotlin
@Test
fun `ready carries the streak and learned totals for the profile page`() {
    // 构造/驱动方式沿用本文件既有用例的 fake 与 runTest 写法，只换断言
    // state.streakDays == 3 && state.learnedWordCount == 12
}
```
> 这条必须真写断言并留下失败→通过的痕迹；不许只写注释。

- [ ] **Step 2: 实跑，确认红**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.ProfilePageContractTest" \
    --tests "com.example.englishlearning.ui.CheckInViewModelTest" --offline --no-daemon --no-build-cache
```

- [ ] **Step 3: GREEN**

1. `SettingsScreen.kt` 抽出 `SettingsGroupsContent`：把 `SettingsScreen` 的 `Column { AppScreenHeader(...); 四组内容 }` 中**页头之后**的全部内容原样搬进新函数；`SettingsScreen` 改为：
```kotlin
@Composable
fun SettingsScreen(/* 原签名一字不动 */) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "设置",
            subtitle = "$profileName · API Key 只保存在本机加密区。",
        )
        SettingsGroupsContent(
            wordBookName = wordBookName,
            onOpenSetup = onOpenSetup,
            /* 其余十三枚逐枚转接 */
        )
    }
}
```
`SettingsGroupsContent` 内保留 `val context = LocalContext.current`、`val themeMode = rememberAppThemeMode()`、`var wordBooksExpanded by rememberSaveable { mutableStateOf(false) }`，最外层为 `Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { … }`（**不加** `fillMaxSize` / `verticalScroll` / `testTag("settings_screen")`——滚动与屏根归宿主）。

2. 新建 `ProfileScreen.kt`：
```kotlin
@Composable
fun ProfileScreen(
    profileName: String,
    streakDays: Int,
    learnedWordCount: Int,
    articleCount: Int,
    onOpenWordBank: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenReadingHistory: () -> Unit,
    onOpenLearningRecords: () -> Unit,
    settingsContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("profile_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = profileName,
            subtitle = "连续学习 $streakDays 天 · API Key 只保存在本机加密区。",
        )
        StrongCard(modifier = Modifier.testTag("profile_stats")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileStatCell("已学词", learnedWordCount.toString(), "profile_stat_learned", onOpenWordBank, Modifier.weight(1f))
                ProfileStatCell("连续天数", streakDays.toString(), "profile_stat_streak", onOpenCheckIn, Modifier.weight(1f))
                ProfileStatCell("文章", articleCount.toString(), "profile_stat_articles", onOpenReadingHistory, Modifier.weight(1f))
            }
        }
        SoftCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            BareRow(
                title = "学习记录",
                subtitle = "今日已学与历史",
                onClick = onOpenLearningRecords,
                modifier = Modifier.testTag("profile_open_learning_records"),
            )
        }
        settingsContent()
    }
}

@Composable
private fun ProfileStatCell(label: String, value: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(vertical = 10.dp, horizontal = 6.dp)
            .testTag(tag)
            .semantics { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = AppType.Headline, color = colors.onStrong)
        Text(label, style = AppType.Footnote, color = colors.onStrong.copy(alpha = 0.72f))
    }
}
```

3. `CheckInViewModel.kt`：
```kotlin
data class Ready(
    …,
    val streakDays: Int = 0,
    val learnedWordCount: Int = 0,
)
```
```kotlin
fun load(profileId: String, keepReady: Boolean = false) {
    val previous = _uiState.value
    if (!keepReady) _uiState.value = CheckInUiState.Loading
    viewModelScope.launch {
        …
        _uiState.value = CheckInUiState.Ready(
            …,
            streakDays = streak,
            learnedWordCount = totals.learnedWordCount,
        )
    }
}
```
两条失败分支统一改成：
```kotlin
_uiState.value = if (keepReady && previous is CheckInUiState.Ready) previous else CheckInUiState.Unavailable
```

4. `AppScreen.kt`：
- 把原来在 `showCheckIn` 分支里 `collectAsState` 的 `checkInState` 提到 `Scaffold` 之前（与 `readingState` 同层），供学习页与我的页共用；`showCheckIn` 分支复用同一变量。
- 加预加载：
```kotlin
LaunchedEffect(selectedTab, state.profile.id) {
    if (selectedTab == AppTab.LEARNING || selectedTab == AppTab.SETTINGS) {
        checkInViewModel?.load(state.profile.id, keepReady = true)
    }
}
```
- LEARNING 分支传：
```kotlin
streakDays = (checkInState as? CheckInUiState.Ready)?.streakDays ?: 0,
todayArticleRead = (checkInState as? CheckInUiState.Ready)?.today?.articleRead ?: false,
```
- SETTINGS 分支把 `SettingsScreen(...)` 换成：
```kotlin
ProfileScreen(
    profileName = state.profile.displayName,
    streakDays = (checkInState as? CheckInUiState.Ready)?.streakDays ?: 0,
    learnedWordCount = (checkInState as? CheckInUiState.Ready)?.learnedWordCount ?: 0,
    articleCount = readingState.historyOrEmpty.size,
    onOpenWordBank = { wordBankSection = WordBankSection.Book; showWordBank = true },
    onOpenCheckIn = { showCheckIn = true },
    onOpenReadingHistory = { showReadingHistory = true },
    onOpenLearningRecords = { showLearningRecords = true },
    settingsContent = {
        SettingsGroupsContent(
            wordBookName = todayReady?.wordBookName,
            /* 原 SettingsScreen 的十四枚实参逐枚照搬 */
        )
    },
)
```

- [ ] **Step 4: 实跑转绿**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.ProfilePageContractTest" \
    --tests "com.example.englishlearning.ui.CheckInViewModelTest" \
    --tests "com.example.englishlearning.ui.SettingsNoDeveloperJargonTest" --offline --no-daemon --no-build-cache
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt \
        app/src/main/java/com/example/englishlearning/ui/ProfileScreen.kt \
        app/src/main/java/com/example/englishlearning/ui/CheckInViewModel.kt \
        app/src/main/java/com/example/englishlearning/ui/AppScreen.kt \
        app/src/test/java/com/example/englishlearning/ui/ProfilePageContractTest.kt \
        app/src/test/java/com/example/englishlearning/ui/CheckInViewModelTest.kt \
        app/src/androidTest/java/com/example/englishlearning/ui/ProfileScreenTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): 新建我的页并抽出可复用设置内容"
```

---

### Task 7: 每日阅读重排

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReadingAccessScreen.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/ReadingHomeLayoutContractTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/SharedComponentUniquenessRatchetTest.kt:218`（页头 seed 摘行）
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/ReadingAccessScreenTest.kt`

**Interfaces:**
- Consumes: `AppScreenHeader(action = …)`、`ConfirmDestructiveDialog`、`SoftCard`/`BareRow`、`appDomain(AppDomain.Reading)`。
- Produces: 今日文章卡 tag `reading_today_article`；更多组 `reading_more_group`；页头「换一篇」锚 `reading_regenerate`；锁定态一句 `reading_access_locked_types`。

- [ ] **Step 1: RED**

`ReadingHomeLayoutContractTest` 的现值逐条改：

1. `appColors().onBrand` 站点数 **4 → 1**（只剩主 CTA 一枚；换一篇移进页头后不再是 MintPrimary 大按钮）。
2. `containerColor = MintPrimary` 与 `contentColor = appColors().onBrand` 各 **4 → 1**。
3. `ArticleType.entries` 保持 2（锁定态与展开区各一支）。
4. 新增三条：
   ```kotlin
   assertEquals(1, countMatches(home, Regex("""AppScreenHeader\(""")), "阅读主页必须换用共享页头（页头族 seed 已摘此屏）")
   assertEquals(1, countMatches(home, Regex("""reading_today_article""")), "今日文章卡锚必须恰一处")
   assertEquals(1, countMatches(home, Regex("""reading_more_group""")), "外刊/导入/历史必须收进同一个更多组")
   assertEquals(0, countMatches(home, Regex("""SecondaryCtaButton\("换一篇"|"换一篇",\s*"reading_regenerate""")), "换一篇不再是页内次级按钮")
   ```
5. 锁定态判据替换：原「锁定态四枚 TypeButton」改为
   ```kotlin
   assertTrue("reading_access_locked_types" in home, "锁定态用一句说明替代四枚类型按钮")
   assertEquals(0, countMatches(home, Regex("""TypeButton\(type, false""")), "锁定态不得再平铺四枚类型按钮")
   ```
6. `GlassDialog(` 禁令与 `OutboundConfirmDialog(` 恰一处、`PayloadKind.Text` 恰一处三条**原样保留**。
7. `AppScreen.kt` 的 `ConfirmDestructiveDialog(` 新接线：在 home 内计数 == 1，用于「换一篇」二次确认。

`SharedComponentUniquenessRatchetTest` 的页头族 seed 删除 `"ReadingAccessScreen.kt",` 一行（该屏已调用 `AppScreenHeader(` 且 headline 读点清零）。

`ReadingAccessScreenTest`（androidTest）：删 `check_in` 无关断言；把「换一篇」按钮的查找从 `reading_regenerate` 文本按钮改为页头 action 锚；删锁定态四枚类型按钮断言，加一句说明锚。

- [ ] **Step 2: 实跑，确认红**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.ReadingHomeLayoutContractTest" \
    --tests "com.example.englishlearning.ui.SharedComponentUniquenessRatchetTest" --offline --no-daemon --no-build-cache
```

- [ ] **Step 3: GREEN**

`ReadingAccessScreen.kt` 结构改为：

```kotlin
var regenerateConfirm by remember { mutableStateOf(false) }

Column(/* 原根 Column 不动：fillMaxSize / background / verticalScroll / padding(20, 24) / tag reading_access_screen */) {
    AppScreenHeader(
        title = "每日阅读",
        action = if (state is ReadingAccessUiState.Ready && state.todayArticle != null) {
            {
                TextButton(
                    onClick = { regenerateConfirm = true },
                    modifier = Modifier.testTag("reading_regenerate"),
                ) { Text("换一篇", style = AppType.Body, color = appColors().text2) }
            }
        } else null,
    )
    when (state) {
        ReadingAccessUiState.Loading -> Text("正在读取本地阅读状态…", style = AppType.Footnote, color = colors.text2)
        ReadingAccessUiState.Unavailable -> Text("本地阅读状态暂时无法读取", style = AppType.Footnote, color = colors.text2)
        is ReadingAccessUiState.Locked -> {
            AccessCard("文章尚未解锁", state.reason, "reading_access_locked_reason")
            Text(
                "今日计划完成后可生成新文章；已解锁的历史文章随时可读。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("reading_access_locked_types"),
            )
            HistoryButton(onOpenHistory, state.history.size)
        }
        is ReadingAccessUiState.Ready -> ReadyContent(…)
    }
    ConfirmDestructiveDialog(
        state = regenerateConfirm,
        title = "换一篇今天的文章？",
        cascadeLines = listOf("当前这篇文章会被新生成的文章替换"),
        keepLines = listOf("已解锁的历史文章与阅读记录不受影响"),
        irreversible = false,
        confirmLabel = "换一篇",
        onConfirm = { regenerateConfirm = false; onRegenerate() },
        onDismiss = { regenerateConfirm = false },
    )
}
```

`ReadyContent`：
```kotlin
if (state.todayArticle != null) {
    // 今日文章卡：Reading 域低透明度底压 surface（§4.4 一屏一域色）。
    val readingTint = appDomain(AppDomain.Reading).base.copy(alpha = 0.12f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .background(readingTint)
            .padding(16.dp)
            .testTag("reading_today_article"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("今日文章", style = AppType.Label, color = appDomain(AppDomain.Reading).deep)
        Text(state.todayArticle.title, style = AppType.Title, color = colors.text1, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            state.todayArticle.chineseText.lineSequence().firstOrNull().orEmpty(),
            style = AppType.Footnote,
            color = colors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${state.todayArticle.articleType.label} · ${state.todayArticle.lengthTier.label}",
            style = AppType.Label,
            color = colors.text2,
        )
        PrimaryCtaButton("阅读今天的文章", "reading_open_today", "阅读今天的文章", onOpenTodayArticle)
    }
} else {
    Text("今天还没有生成文章", style = AppType.Title, color = colors.text1)
    PrimaryCtaButton("生成今天的文章", "reading_source_ai", "用 AI 生成今天的文章", onGenerate)
}
Text(/* 费用提示原样保留：generation_cost_hint */)

AccessCard("文章已解锁", "三种来源任选：AI 生成、外刊选取，或粘贴你自己的文章。", "reading_access_unlocked")
// 生成状态分支原样保留（generation_status / generation_storage_failed / 失败横幅 / 出站确认）

GenerationParamsToggle(expanded = generationParamsExpanded, onClick = onToggleGenerationParams)
if (generationParamsExpanded) { /* 原偏好卡 + 四枚类型行原样保留 */ }

SoftCard(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.testTag("reading_more_group")) {
    BareRow("外刊选读", subtitle = "从已核验的来源挑一篇", onClick = onOpenFeed, leading = { AppGlyph(AppIcon.Newspaper, tint = appDomain(AppDomain.Reading).deep, size = 20.dp, layer = AppGlyphLayer.StrokeOnly) })
    BareRow("粘贴导入", subtitle = "用你自己的文章学", topDivider = true, onClick = onOpenImport, leading = { AppGlyph(AppIcon.Document, tint = appDomain(AppDomain.Reading).deep, size = 20.dp, layer = AppGlyphLayer.StrokeOnly) })
    BareRow("阅读历史", subtitle = "共 ${state.historyCount} 篇", topDivider = true, onClick = onOpenHistory, leading = { AppGlyph(AppIcon.List, tint = appDomain(AppDomain.Reading).deep, size = 20.dp, layer = AppGlyphLayer.StrokeOnly) })
}
```
- 删除 `SecondaryCtaButton("换一篇", …)`、`SecondaryCtaButton("从外刊选取", …)`、`SecondaryCtaButton("粘贴文章", …)`、`HistoryButton(onOpenHistory, …)` 在 `ReadyContent` 里的四处调用；`HistoryButton` 保留给锁定态。
- `reading_regenerate` 从此只出现在页头 action。
- 新增 import：`androidx.compose.material3.TextButton`、`androidx.compose.foundation.background`（今日文章卡的叠色底）、`androidx.compose.ui.text.style.TextOverflow`、`com.example.englishlearning.ui.components.AppScreenHeader`、`com.example.englishlearning.ui.components.BareRow`、`com.example.englishlearning.ui.components.SoftCard`、`com.example.englishlearning.ui.components.AppGlyph`、`com.example.englishlearning.ui.components.AppIcon`、`com.example.englishlearning.ui.components.AppGlyphLayer`；`ConfirmDestructiveDialog` 同包无需 import。

- [ ] **Step 4: 实跑转绿**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.ReadingHomeLayoutContractTest" \
    --tests "com.example.englishlearning.ui.SharedComponentUniquenessRatchetTest" --offline --no-daemon --no-build-cache
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/ReadingAccessScreen.kt \
        app/src/test/java/com/example/englishlearning/ui/ReadingHomeLayoutContractTest.kt \
        app/src/test/java/com/example/englishlearning/ui/SharedComponentUniquenessRatchetTest.kt \
        app/src/androidTest/java/com/example/englishlearning/ui/ReadingAccessScreenTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): 阅读主页改为今日文章卡加更多组"
```

---

### Task 8: 学习记录与打卡改裸行列表

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/LearningRecordsRowContractTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/CheckInScreenHeaderShapeContractTest.kt`（不改判据）
- Test: `app/src/test/java/com/example/englishlearning/ui/CheckInDisplayAuthorityTest.kt`（新）
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/LearningRecordsScreenTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/CheckInScreenTest.kt`

**Interfaces:**
- Produces: `WordRow(topDivider: Boolean = false)`；学习记录行 `status = row.feedback.label()`（无前缀）。
- Consumes: `SoftCard`、`AppType.Display`（打卡页唯一一枚）。

- [ ] **Step 1: RED**

1. `WordRow` 的 `topDivider`：在 `EntryRow`/WordRow 契约测试所在文件（`SharedComponentUniquenessRatchetTest` 的词行族不涉及）之外，新建的断言放在 `LearningRecordsRowContractTest`：
   ```kotlin
   @Test
   fun `the record row is a bare word row with a hairline between rows`() {
       assertTrue("topDivider = true" in screen, "同日多条记录靠 topDivider 画分隔线（§4.1 裸行族）")
       assertEquals(0, countMatches(screen, Regex("""\bCard\(""")), "学习记录不再套卡：每条记录就是一行")
   }
   ```
2. `LearningRecordsRowContractTest` 现值改：「`status = "反馈：${row.feedback.label()}"`」的存在性判据改为
   ```kotlin
   assertTrue(screen.contains("status = row.feedback.label()"), "反馈文案不再带「反馈：」前缀（§5 学习记录）")
   assertTrue("反馈：" !in screen, "「反馈：」前缀必须删净，否则 11 枚汉字把右列挤成省略号")
   ```
3. 新建 `CheckInDisplayAuthorityTest.kt`：
   ```kotlin
   package com.example.englishlearning.ui

   import kotlin.test.Test
   import kotlin.test.assertEquals
   import kotlin.test.assertTrue

   /** 打卡页（§5）：连续天数是唯一 Display，累计降为 Title，重复的完成度环整块删除。 */
   class CheckInDisplayAuthorityTest {
       private val screen: String by lazy { stripComments(uiScreenSource("CheckInScreen.kt")) }

       @Test
       fun `the streak is the only display figure`() {
           assertEquals(1, countMatches(screen, Regex("""AppType\.Display""")), "打卡页恰一枚 Display：连续天数")
           assertTrue("check_in_streak_days" in screen, "Display 必须落在连续天数锚上")
           assertTrue("AppType.Title" in screen, "累计统计必须是 Title（§5）")
       }

       @Test
       fun `the duplicated completion ring is gone`() {
           listOf("check_in_completion_ratio", "check_in_completion_arc", "check_in_completion_percent").forEach { gone ->
               assertTrue(gone !in screen, "重复的完成度环「$gone」必须删除（§5）")
           }
           assertTrue("Canvas(" !in screen, "环删了，Canvas 也应随之离场")
       }
   }
   ```

`CheckInScreenTest`（androidTest）：删除三枚完成度锚的断言与 `check_in_totals_text` 的文本断言（改为 `check_in_streak_days` 存在 + `check_in_streak_text` 标题）；`LearningRecordsScreenTest`：删掉「反馈：」前缀的文本匹配。

- [ ] **Step 2: 实跑，确认红**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.LearningRecordsRowContractTest" \
    --tests "com.example.englishlearning.ui.CheckInDisplayAuthorityTest" --offline --no-daemon --no-build-cache
```

- [ ] **Step 3: GREEN**

1. `WordRow.kt`：签名追加 `topDivider: Boolean = false,`（放在 `actions` 之前）；Row 的 modifier 链在 `then(touchModifier)` 之后插入：
```kotlin
.then(if (topDivider) hairlineTop(inks.meta, startIndent = 15.dp) else Modifier)
```
import `com.example.englishlearning.ui.components.hairlineTop` 同包无需；`inks.meta` 即分隔线色（`WordRowInk` 里的 text2 档）。

2. `LearningRecordsScreen.kt`：
- 两处 `items(...)` 改 `itemsIndexed`，行调用传 `index`（新增 import `androidx.compose.foundation.lazy.itemsIndexed`）：
  ```kotlin
  itemsIndexed(state.today, key = { _, it -> "today-${it.planId}-${it.cardId}" }) { index, row ->
      LearningRecordRow(row, index, onOpenRecord)
  }
  ```
  历史分支同理（每组内 `index` 从 0 起）。
- `LearningRecordRow(row, index, onOpenRow)` 删掉 `Card`，改为：
  ```kotlin
  @Composable
  private fun LearningRecordRow(row: LearningRecord, index: Int, onOpenRow: (LearningRecord) -> Unit) {
      val safeId = row.safeId()
      val enabled = row.canOpenDetail
      val unavailableNotice = if (enabled) null else "这个词已不在当前词书中"
      WordRow(
          lemma = row.displayLemma,
          gloss = row.wordCard?.meaningZh?.takeIf { it.isNotBlank() } ?: unavailableNotice,
          status = row.feedback.label(),
          topDivider = index > 0,
          onClick = if (enabled) ({ onOpenRow(row) }) else null,
          modifier = Modifier
              .testTag(if (enabled) "learning_record_row_$safeId" else "learning_record_missing_$safeId")
              .padding(horizontal = 20.dp),
      )
  }
  ```
- `LazyColumn` 的 `contentPadding` 横向改 0（缩进由行内 20dp 承担），`verticalArrangement = Arrangement.spacedBy(0.dp)`。

3. `CheckInScreen.kt`：
- 删 `Canvas` 完成度卡整块（`check_in_completion_ratio`），删 `Canvas`/`StrokeCap`/`Stroke`/`size`/`CircleShape` 相关 import。
- `check_in_today` → `SoftCard(modifier = Modifier.testTag("check_in_today"))`。
- `check_in_totals` → `SoftCard(modifier = Modifier.testTag("check_in_totals"))`，内容改为：
  ```kotlin
  Text("连续学习", style = AppType.Label, color = colors.text2)
  Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
          state.streakDays.toString(),
          style = AppType.Display,
          color = appDomain(AppDomain.Review).deep,
          modifier = Modifier.testTag("check_in_streak_days"),
      )
      Text("天", style = AppType.Footnote, color = colors.text2, modifier = Modifier.padding(bottom = 8.dp).testTag("check_in_streak_text"))
  }
  Text(state.totalsText, style = AppType.Title, color = colors.text1, modifier = Modifier.testTag("check_in_totals_text"))
  ```
- `check_in_week_chart` / `check_in_calendar` 的 `Card(...)` 换成 `SoftCard(modifier = Modifier.testTag(...))`，内部 `Column(Modifier.padding(18.dp))` 去掉（内边距归 SoftCard）。
- 卡片标题统一 `AppType.Title`，正文 `AppType.Body`，辅助 `AppType.Footnote`；删掉 `MaterialTheme.typography.*` 与 `FontWeight.*` 读取（`CheckInScreen` 的 JVM 契约会统计 `MaterialTheme.typography` 为 0）。
- `MonthCalendar` 的 `Text(date.dayOfMonth.toString(), …)` 保留语义，颜色改 `appDomain(AppDomain.Review).deep` / `colors.text2`。

- [ ] **Step 4: 实跑转绿**

```bash
"$G" :app:testDebugUnitTest --tests "com.example.englishlearning.ui.LearningRecordsRowContractTest" \
    --tests "com.example.englishlearning.ui.CheckInDisplayAuthorityTest" \
    --tests "com.example.englishlearning.ui.CheckInScreenHeaderShapeContractTest" --offline --no-daemon --no-build-cache
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt \
        app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt \
        app/src/main/java/com/example/englishlearning/ui/components/WordRow.kt \
        app/src/test/java/com/example/englishlearning/ui/LearningRecordsRowContractTest.kt \
        app/src/test/java/com/example/englishlearning/ui/CheckInDisplayAuthorityTest.kt \
        app/src/androidTest/java/com/example/englishlearning/ui/LearningRecordsScreenTest.kt \
        app/src/androidTest/java/com/example/englishlearning/ui/CheckInScreenTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): 学习记录与打卡改裸行并把连续天数提为唯一大数字"
```

---

### Task 9: 全量门禁 + 模拟器 connected + 真机目视 + 工作报告

**Files:**
- Modify: `docs/handoff/…`（按 REPORT-TEMPLATE 新增本批报告）

- [ ] **Step 1: 全量门禁**

```bash
G=$(ls -d "$HOME/.gradle/wrapper/dists/gradle-8.13-bin"/*/gradle-8.13/bin/gradle.bat | head -1)
"$G" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
    verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
```
期望：`BUILD SUCCESSFUL`，`testDebugUnitTest` 全绿。把测试总数抄进报告（本批之前的基线是 1748 枚；本批新增 4 个契约类，报告里写实测值，不写推算值）。

- [ ] **Step 2: 模拟器 connected（不清数据）**

```bash
G="…/gradle.bat"
"$G" :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon
adb -s emulator-5574 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5574 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5574 shell am instrument -w \
  -e class com.example.englishlearning.ui.TodayPlanScreenTest,com.example.englishlearning.ui.WordBankScreenTest,com.example.englishlearning.ui.ProfileScreenTest,com.example.englishlearning.ui.CheckInScreenTest,com.example.englishlearning.ui.LearningRecordsScreenTest,com.example.englishlearning.ui.ReadingAccessScreenTest,com.example.englishlearning.ui.AppBottomBarTest \
  com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
```
期望：`OK (N tests)`。任何失败先修，**不许**把测试从命令里删掉充绿。

- [ ] **Step 3: 真机目视（只装不打）**

```bash
adb -s bf353dda install -r app/build/outputs/apk/debug/app-debug.apk
```
逐项目视并把结果写进报告（每项写「通过 / 不通过 + 截图位置」，截图只落 `.git/qa-scratch/`，不入库）：
1. 底栏四格：学习 / 阅读 / AI 学 / 我的，第四枚是人形图标，TalkBack 播报「我的」。
2. 学习页：搜索框常驻；强档卡深底、大数字 44sp、两条细进度条；主 CTA 在两种状态下位置不变（开始学习 ↔ 去读今日文章）。
3. 词库页：四分段可切；词书段字母 Rail 与筛选条正常；生词/错词/收藏段各自只有一段内容。
4. 我的页：头像区显示昵称与「连续学习 N 天」；三格统计可点达；学习记录入口可进入；下方四组九行设置与旧设置页完全一致。
5. 打卡页：连续天数是唯一大数字；完成度环已消失；周柱状图与月历仍在。
6. 学习记录：行与行之间一条细分隔线，右侧只显示「认识/模糊/不认识」，不可用行仍是灰底并说明原因。
7. 深色模式切换一次，四屏都用新 token（强档卡深底在深色下仍与背景分层）。
8. 系统字号调到最大，四屏不截断、不叠字。

- [ ] **Step 4: 写报告并提交**

按 `docs/handoff/REPORT-TEMPLATE`（以仓库现有模板为准）写：
- 本批范围与 9 枚提交哈希；
- 门禁原样输出（测试总数、connected 结果）；
- 真机 8 项目视结论；
- **改期望值清单**：逐条列「旧值 → 新值 + 依据」，供后续审核席核对有没有被偷偷放宽；
- 结转项：`SettingsScreen` 作为装载缝保留（connected 仍挂）；Task 35 页头收编剩 11 屏；Task 36 我的词 LazyColumn；Task 40 大字号四族覆盖。

```bash
git add docs/handoff/<本批报告文件>
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "docs(handoff): 批 1 骨架工作报告与目视结论"
```

- [ ] **Step 5: 请示**

向用户报告批 1 结果，**不自行 push、不合并 `main`**，请示是否推送备份与是否进入批 2。

---

## Self-Review

**Spec 覆盖核对（§6 批 1 六件）**

| §6 要求 | 落点 |
|---|---|
| Display/Headline 换档 | Task 1 |
| `StrongCard`/`SoftCard`/`BareRow` | Task 2 |
| 页头收编（并入 Task 35） | Task 7 摘掉阅读主页的页头债务一行；其余 11 屏仍归 Task 35 |
| 底栏「我的」+ `Person` | Task 3 |
| 学习页重排 | Task 4 |
| 新建词库页 | Task 5 |
| 我的页（档案头 + 统计深链 + 原 4 组 9 行） | Task 6 |
| 每日阅读重排 | Task 7 |
| 学习记录与打卡改裸行 | Task 8 |
| JVM + compile androidTest + 真机目视 | Task 9 |

**§3.2 五枚移出 tag**：`today_plan_open_wordbook_words` / `today_plan_open_my_words` → 词库（Task 5）；`today_plan_open_learning_records` / `today_plan_open_check_in` → 我的页（Task 6）；`today_plan_open_setup_entry` → 既有 `settings_open_setup`（Task 6 的 `settingsContent` 原样复用，`SettingsGroups` 一行未动）。

**§7 新契约**：容器唯一性（Task 2/3 `ContainerContractTest` + Task 5 扩面）、底栏四格（Task 3）、词库四段（Task 5）、统计深链（Task 6）、CTA 两态同锚（Task 4）、打卡 Display 唯一（Task 8）。§7 点名的「设置页词书二列卡」「type ladder」不属批 1 六屏，随批 5 / 批 2 立。

**占位扫描**：本计划未使用「TBD / 同 Task N / 类似上面」；「其余实参照搬」「以现签名为准」两类均点名了具体数量与文件位置，执行时逐枚核对。**不允许**把这两处理解成可以少传。

**类型一致性**：`WordBankSection`（四段：Book/New/Wrong/Favorite）在 Task 5 定义、Task 6 复用；`MyWordsSection`（三段：Favorites/Wordbook/Wrong）保持原枚举不动，`visibleSections` 收窄时用原枚举值。`CheckInUiState.Ready.streakDays` 在 Task 6 定义、Task 4 的 `streakDays` 形参与 Task 8 的 Display 同源（都来自 AppScreen 的 `checkInState`）。
