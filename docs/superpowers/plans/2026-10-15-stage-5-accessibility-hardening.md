# 阶段 5 F5-03 无障碍收口与发音四控 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 关闭 F5-03/F3-02 审计缺口——发音播放/暂停/重播/速度四控、tag 当描述与低对比编码禁令、Switch/选中态/展开态/liveRegion 语义批、48dp 触控与动态字体批——并收口矩阵与报告。

**Architecture:** 三层推进：①JVM 源码契约测试固化禁则（防回归、进门禁）；②发音控制面从 `PronunciationProvider` 接口→`PronunciationRouter` 会话→`AudioPlayer`/TTS 引擎逐层上收，语速作为播放侧参数持久化（Room v32）；③Compose 语义/触控/字号弹性批按屏修复。深色模式与硬编码色 token 化归 UI 重设计轮（决策 §1）。

**Tech Stack:** Kotlin、Jetpack Compose（semantics/stateDescription/liveRegion/customActions/FocusRequester/minimumInteractiveComponentSize）、Room 2.8.4、JUnit (kotlin.test + JUnit4 androidTest)、Hilt、adb instrument。

**Spec:** `docs/specs/05-release-quality-and-compliance.md` F5-03 + `docs/specs/03-dictionary-pronunciation-and-photo-translation.md` F3-02
**Decisions:** `docs/decisions/2026-10-15-f5-03-accessibility-hardening-semantics.md`（下称「决策」，各任务引用的 § 编号以它为准）

## Global Constraints

- 分支：`stage-5-f5-03-accessibility-hardening`（自 main tip `4b3f1a6`，本计划+决策为首提交，后续任务逐提交）。
- 门禁 = JVM 单测全绿且基线只增不减（当前 **1396**）。detekt/ktlint 存量债不属门禁（2026-09-19 基线裁决）。
  - **基线订正（终审 M-7 批，2026-10-17，追加不改原文）**：上方 1396 是本计划首提交时的基线，仅作历史留痕；
    实际随本轮推进至 **1467**（第一批终审修复轮收口时），本批（第二批/最后一批修复轮）再加
    `PronunciationIdleFallbackContractTest` 的 4 条 JVM 源码契约 → **1471**。后续阶段的「只增不减」以最新登记数为准，
    计数口径=逐份累加 `app/build/test-results/testDebugUnitTest/*.xml` 的 `tests` 属性，并核对 XML mtime
    晚于最后一次生产码改动（UP-TO-DATE 的旧结果不得当新鲜证据）。
- 门禁命令（顺序不可颠倒，Kokoro 边界测试读 APK 产物）：
  ```bash
  GRADLE="C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat"
  [ -f "$GRADLE" ] || GRADLE=$(ls -1 C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle.bat | head -1)
  "$GRADLE" :app:assembleDebug :app:testDebugUnitTest --no-daemon --no-build-cache 2>&1 | grep -aE 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
  ```
- TDD：每个任务先写失败测试并观察 RED（记录失败原因），再最小实现转 GREEN。UI-only 修复按任务指定改用「JVM 契约断言」或「connected 语义断言」承担红绿证据。
- Compose/Room connected 验证只用 `adb install -r` + `am instrument`（`android-connected-qa-loop` skill；**禁 connectedDebugAndroidTest，会清空设备数据**）；一律 `export ANDROID_SERIAL=emulator-5576`。
  - **设备锚点订正（终审 M-7 批，2026-10-17，追加不改原文）**：本轮 connected 取证实际全部跑在 **`emulator-5574`**（上方
    `emulator-5576` 是派发时的设想值，未成为事实；真机 `bf353dda` 禁止用于本轮自动化取证）。后续任何「逐类 `am instrument`」
    步骤按 `emulator-5574` 执行，其余纪律不变（只允许 `adb install -r` + `am instrument -e class <FQCN>`）。
- 红线：不删库、不 `fallbackToDestructiveMigration`；滑动删除 `confirmValueChange` 恒 false 语义不动；API Key/密钥面零接触；不新增依赖/账号/云同步/订阅。
- 提交带身份参数、逐个 `git add`、禁 `--amend`/`--no-verify`：
  ```bash
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "<type>(<scope>): <中文摘要>"
  ```
- 界面文案一律中文；新增语义标签用中文（决策 §3 禁令的字面含义）。
- 任务顺序即依赖顺序：1→2→3（契约批）→4→5→6（语义批）→7→8→9（发音链，严格串行）→10→11（触控/字体批）→12（收口）。同文件相邻任务串行执行，不得并行改 `AppScreen.kt`/`WordCardScreen.kt`/`ArticleReadingScreen.kt`。

---

### Task 1: tag-当-描述禁令契约 + 六处字面量修正

**Files:**
- Create: `app/src/test/java/com/example/englishlearning/ui/AccessibilityLabelContractTest.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordBookWordsScreen.kt:108,117,215,248`
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt:59,68,232`

**Interfaces:**
- Consumes: 无。
- Produces: `AccessibilityLabelContractTest`（后续任务不得使其变红）。

- [ ] **Step 1: 写失败契约测试**

创建 `AccessibilityLabelContractTest.kt`（复用 `RestoreNoBypassContractTest` 剥注释样板）：

```kotlin
package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * F5-03 源码契约（决策 §3 禁令1）：ui 包内 contentDescription 字面量必须含中文——
 * TalkBack 会把英文 testTag 字符串原样念成内容描述（设备 QA 审计 blocker）。
 * 含插值但不含中文的字面量（如 "${filter.label()} $count"）同样违规：运行时中文
 * 不能替代静态可读性审查，修复方式是删除该 semantics 行（子节点合并朗读已足够）。
 */
class AccessibilityLabelContractTest {
    private val uiDir = File("src/main/java/com/example/englishlearning/ui")

    private fun codeOf(file: File): String = file.readText()
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//.*"), "")

    @Test
    fun `contentDescription literals in ui sources always contain cjk text`() {
        val literal = Regex("""contentDescription = "([^"]*)"""")
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                literal.findAll(codeOf(file))
                    .filter { m -> !m.groupValues[1].any { ch -> ch.code in 0x4E00..0x9FFF } }
                    .map { m -> "${file.name}: contentDescription = \"${m.groupValues[1]}\"" }
                    .toList()
            }
            .toList()
        assertTrue(offenders.isEmpty(), "英文 contentDescription 字面量：\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `no contentDescription is assigned from a raw tag variable`() {
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { Regex("""contentDescription = tag\b""").containsMatchIn(codeOf(it)) }
            .map { it.name }
        assertTrue(offenders.isEmpty(), "contentDescription = tag 直赋：${offenders.joinToString()}")
    }
}
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `"$GRADLE" :app:testDebugUnitTest --tests "*AccessibilityLabelContractTest" --no-daemon 2>&1 | grep -a -A20 "contentDescription"`
Expected: FAIL，offender 列表含 `WordBookWordsScreen.kt`（screen/row/chip 字面量）与 `LearningRecordsScreen.kt`。把完整 offender 清单贴进提交信息；若出现下表之外的新 offender，按同一口径修（保中文、纯 tag 删行、静态无中文的插值删行）。

- [ ] **Step 3: 修正六处 + 前置 grep**

```bash
grep -rn "wordbook_words_screen\|learning_records_screen\|wordbook_words_row_\|learning_records_row_" app/src/androidTest | grep -a "ContentDescription"
```
若命中按 contentDescription 定位的 androidTest 断言，同步改为按 testTag 定位（testTag 全部保留原值）。

`WordBookWordsScreen.kt`：
- :108 删除整行 `.semantics { contentDescription = "wordbook_words_screen" },`（保留上一行 `.testTag(...)`）。
- :117 `.semantics { contentDescription = "wordbook_words_back" },` → `.semantics { contentDescription = "返回" },`
- :215 删除整行 `.semantics { contentDescription = "${filter.label()} $count" },`（FilterChip label 已被合并朗读）。
- :248 删除整行 `.semantics { contentDescription = "wordbook_words_row_$safeId" },`（行内 lemma/释义/状态均为可见文本，节点合并朗读）。

`LearningRecordsScreen.kt`：
- :59 删除整行 `.semantics { contentDescription = "learning_records_screen" },`
- :68 `.semantics { contentDescription = "learning_records_back" },` → `.semantics { contentDescription = "返回" },`
- :230-233 semantics 块整段删除：
  ```kotlin
  // 删除这 4 行（:230-233）：
  //     .semantics {
  //         // 保留既有稳定 contentDescription；中文释义、反馈和原因同时作为可见语义文本。
  //         contentDescription = tag
  //     },
  ```
  保留 :229 `.testTag(tag)`。

- [ ] **Step 4: 跑契约转 GREEN + 全量单测**

Run: Step 2 命令（不带 --tests 过滤再全量跑一次 `:app:testDebugUnitTest`）
Expected: `AccessibilityLabelContractTest` PASS；全量 ≥1396+2 用例 0 失败。

- [ ] **Step 5: 提交**

```bash
git add app/src/test/java/com/example/englishlearning/ui/AccessibilityLabelContractTest.kt app/src/main/java/com/example/englishlearning/ui/WordBookWordsScreen.kt app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "test(a11y): contentDescription 中文契约并清除 tag 字面量（F5-03 禁令1）"
```

---

### Task 2: 低对比组合禁令契约 + 五处主 CTA 与徽标修复

**Files:**
- Create: `app/src/test/java/com/example/englishlearning/ui/AccessibilityContrastContractTest.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/theme/AppPalette.kt`（文件头部 token 区）
- Modify: `app/src/main/java/com/example/englishlearning/ui/AiFeatureScreen.kt:157`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt:1669`
- Modify: `app/src/main/java/com/example/englishlearning/ui/ArticleImportScreen.kt:114`
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordAiQaScreen.kt:163-168`
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReadingAccessScreen.kt:403-405`
- Modify: `app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt:393-397`（徽标）
- Modify: `app/src/main/java/com/example/englishlearning/ui/components/glass/GlassButton.kt:62`

**Interfaces:**
- Consumes: 无。
- Produces: `AppPalette.OnMintPrimary`（Task 3 的 `ErrorText/SuccessText` 同文件追加）；`AccessibilityContrastContractTest`。

- [ ] **Step 1: 写失败契约测试**

```kotlin
package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * F5-03 源码契约（决策 §3 禁令2）：禁「浅薄荷 MintPrimary 容器 + 白字」主 CTA 组合
 * （实测 ≈2.1:1，TalkBack 用户同样受害于低对比）。修复色统一 AppPalette.OnMintPrimary。
 */
class AccessibilityContrastContractTest {
    private val uiDir = File("src/main/java/com/example/englishlearning/ui")

    private fun codeOf(file: File): String = file.readText()
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//.*"), "")

    @Test
    fun `no mint-primary container with white content in same buttonColors call`() {
        val forbidden = Regex("""buttonColors\([^)]*containerColor = MintPrimary[^)]*contentColor = Color\.White[^)]*\)""")
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { forbidden.containsMatchIn(codeOf(it)) }
            .map { it.name }
        assertTrue(offenders.isEmpty(), "MintPrimary+白字组合：${offenders.joinToString()}")
    }
}
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `"$GRADLE" :app:testDebugUnitTest --tests "*AccessibilityContrastContractTest" --no-daemon 2>&1 | grep -a -A10 "MintPrimary"`
Expected: FAIL，offenders 至少含 `AiFeatureScreen.kt`、`AppScreen.kt`、`ArticleImportScreen.kt`、`WordAiQaScreen.kt`、`ReadingAccessScreen.kt`。新 offender 同口径修。

- [ ] **Step 3: 加 token 并修五处 CTA + 徽标 + PillButton 引用**

`AppPalette.kt` 在文件头部 `object AppPalette {`（或顶层 val 区，与现有 `TextPrimary` 同层级）加：

```kotlin
/** 浅薄荷主色容器上的文字色：与 MintPrimary 约 4.6:1（AA 正文），替代「白字压浅底」。 */
val OnMintPrimary = Color(0xFF174C44)
```

五处 `buttonColors(containerColor = MintPrimary, contentColor = Color.White…)` 中的 `contentColor = Color.White` 全部替换为 `contentColor = AppPalette.OnMintPrimary`（各文件按 `import com.example.englishlearning.ui.theme.AppPalette` 补齐；AiFeatureScreen :157、AppScreen :1669、ArticleImportScreen :114、WordAiQaScreen :164、ReadingAccessScreen :404）。

`SettingsScreen.kt` 徽标 :394：`.background(accent.base, AppShape.Button)` → `.background(accent.deep, AppShape.Button)`（白字保留；决策 §3 末段口径）。

`GlassButton.kt` :62：`style == PillStyle.Primary && usesBrandGradient -> Color(0xFF174C44)` → `style == PillStyle.Primary && usesBrandGradient -> AppPalette.OnMintPrimary`。

- [ ] **Step 4: GREEN + 全量单测**

Expected: 契约 PASS；全量单测 0 失败（基线只增不减）。

- [ ] **Step 5: 提交**

```bash
git add app/src/test/java/com/example/englishlearning/ui/AccessibilityContrastContractTest.kt app/src/main/java/com/example/englishlearning/ui/theme/AppPalette.kt app/src/main/java/com/example/englishlearning/ui/AiFeatureScreen.kt app/src/main/java/com/example/englishlearning/ui/AppScreen.kt app/src/main/java/com/example/englishlearning/ui/ArticleImportScreen.kt app/src/main/java/com/example/englishlearning/ui/WordAiQaScreen.kt app/src/main/java/com/example/englishlearning/ui/ReadingAccessScreen.kt app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt app/src/main/java/com/example/englishlearning/ui/components/glass/GlassButton.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "fix(a11y): 主 CTA 浅底白字改深字 token 并固化低对比契约（F5-03 禁令2）"
```

---

### Task 3: 裸 Color-Red 禁令契约 + ErrorText-SuccessText token

**Files:**
- Create: `app/src/test/java/com/example/englishlearning/ui/AccessibilityColorTokenContractTest.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/theme/AppPalette.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReminderSettingsScreen.kt:62`
- Modify: `app/src/main/java/com/example/englishlearning/ui/BackupExportScreen.kt:99-100`

**Interfaces:**
- Consumes: `AppPalette`（Task 2）。
- Produces: `AppPalette.ErrorText`、`AppPalette.SuccessText`。

- [ ] **Step 1: 写失败契约测试**

```kotlin
package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * F5-03 源码契约（决策 §3 禁令3）：main 源码禁裸 Color.Red（白底约 3.7:1 且不可主题化）；
 * 错误/成功状态文字统一 AppPalette.ErrorText / SuccessText。
 */
class AccessibilityColorTokenContractTest {
    @Test
    fun `no raw Color-Red anywhere in main sources`() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { Regex("""Color\.Red\b""").containsMatchIn(it.readText()) }
            .map { it.name }
        assertTrue(offenders.isEmpty(), "裸 Color.Red：${offenders.joinToString()}")
    }
}
```

- [ ] **Step 2: 跑测试确认 RED**

Expected: FAIL，offenders 含 `ReminderSettingsScreen.kt`、`BackupExportScreen.kt`（如全仓扫描揭示更多，同口径修）。

- [ ] **Step 3: 加 token + 两处替换**

`AppPalette.kt`（紧邻 OnMintPrimary）：

```kotlin
/** 错误状态文字（= DomainColors.Danger.deep，白底 ≥7:1）。 */
val ErrorText = Color(0xFFB30000)
/** 成功/提示状态文字（深绿，白底 ≥7:1）。 */
val SuccessText = Color(0xFF1B5E20)
```

- `ReminderSettingsScreen.kt:62`：`color = androidx.compose.ui.graphics.Color.Red` → `color = AppPalette.ErrorText`
- `BackupExportScreen.kt:99`：`color = Color.Red` → `color = AppPalette.ErrorText`
- `BackupExportScreen.kt:100`：`color = Color(0xFF1B5E20)` → `color = AppPalette.SuccessText`

（两文件按需补 `import com.example.englishlearning.ui.theme.AppPalette`。）

- [ ] **Step 4: GREEN + 全量单测** — Expected: 契约 PASS，全量 0 失败。

- [ ] **Step 5: 提交**

```bash
git add app/src/test/java/com/example/englishlearning/ui/AccessibilityColorTokenContractTest.kt app/src/main/java/com/example/englishlearning/ui/theme/AppPalette.kt app/src/main/java/com/example/englishlearning/ui/ReminderSettingsScreen.kt app/src/main/java/com/example/englishlearning/ui/BackupExportScreen.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "fix(a11y): 状态色统一 ErrorText/SuccessText token 并固化裸红字禁令（F5-03 禁令3）"
```

---

### Task 4: Switch 语义标签四处（契约 + 行为断言双轨）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReminderSettingsScreen.kt:78-84`
- Modify: `app/src/main/java/com/example/englishlearning/ui/ArticleReadingScreen.kt:180-184`
- Modify: `app/src/main/java/com/example/englishlearning/ui/BackupExportScreen.kt:76-80`
- Modify: `app/src/main/java/com/example/englishlearning/ui/WorksheetSettingsScreen.kt:273-277`
- Create: `app/src/test/java/com/example/englishlearning/ui/AccessibilitySwitchLabelContractTest.kt`

**Interfaces:**
- Consumes: `AccessibilityLabelContractTest` 的 `codeOf` 样板。
- Produces: 各 Switch 节点带中文 contentDescription（Task 5/9 断言基于此模式）。

- [ ] **Step 1: 写失败契约测试**

```kotlin
package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * F5-03（决策 §4）：Switch 必须携带中文标签语义（照 AppScreen 设置行样板）。
 * 契约按「testTag → 同链 80 字符内有 contentDescription」静态判定；行为断言走
 * ArticleReadingScreenTest（connected）。
 */
class AccessibilitySwitchLabelContractTest {
    private fun codeOf(name: String): String =
        File("src/main/java/com/example/englishlearning/ui/$name").readText()
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .replace(Regex("//.*"), "")

    @Test
    fun `every labelled switch carries a chinese contentDescription in its modifier chain`() {
        val cases = listOf(
            "ReminderSettingsScreen.kt" to "reminder_enabled_switch",
            "ArticleReadingScreen.kt" to "article_marks_switch",
            "BackupExportScreen.kt" to "backup_export_audio_switch",
        )
        cases.forEach { (file, tag) ->
            val chain = Regex("""testTag\("$tag"\)[\s\S]{0,80}?contentDescription""").containsMatchIn(codeOf(file))
            assertTrue(chain, "$file: switch $tag 的修饰链缺少 contentDescription（TalkBack 只念「开/关」）")
        }
    }

    @Test
    fun `worksheet settings row switch is labelled by its title`() {
        val code = codeOf("WorksheetSettingsScreen.kt")
        assertTrue(
            Regex("""Switch\([\s\S]{0,200}?contentDescription = title""").containsMatchIn(code),
            "WorksheetSettingsScreen 的裸 Switch 必须 contentDescription = title",
        )
    }
}
```

- [ ] **Step 2: 跑测试确认 RED** — Expected: FAIL（四个 switch 全缺标签）。

- [ ] **Step 3: 四处修复**

- `ReminderSettingsScreen.kt:83`：`modifier = Modifier.testTag("reminder_enabled_switch"),` →
  ```kotlin
  modifier = Modifier.testTag("reminder_enabled_switch").semantics { contentDescription = "开启学习提醒" },
  ```
- `ArticleReadingScreen.kt:183`：`modifier = Modifier.testTag("article_marks_switch"),` →
  ```kotlin
  modifier = Modifier.testTag("article_marks_switch").semantics { contentDescription = "标记已背的词" },
  ```
- `BackupExportScreen.kt:79`：`modifier = Modifier.testTag("backup_export_audio_switch"),` →
  ```kotlin
  modifier = Modifier.testTag("backup_export_audio_switch").semantics { contentDescription = "包含 AI 音频文件" },
  ```
- `WorksheetSettingsScreen.kt:273-277` 的 `Switch(…)` 增加：
  ```kotlin
  Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(checkedTrackColor = MintPrimary),
      modifier = Modifier.semantics { contentDescription = title },
  )
  ```
四文件按需补 `import androidx.compose.ui.semantics.contentDescription` 与 `import androidx.compose.ui.semantics.semantics`（若尚未导入）。

- [ ] **Step 4: GREEN + ArticleReadingScreenTest 行为断言**

在 `app/src/androidTest/java/com/example/englishlearning/ui/ArticleReadingScreenTest.kt` 追加（复用该文件现有 setContent 夹具；若无直接组屏夹具，用现有构造文章的 helper）：

```kotlin
@Test
fun marks_switch_exposes_chinese_label() {
    // …既有方式渲染 ArticleReadingScreen（showLearnedMarks=true）…
    composeTestRule.onNodeWithTag("article_marks_switch")
        .assertContentDescriptionContains("标记已背的词", substringMatcher = true)
}
```
（`assertContentDescriptionContains` 来自 `androidx.compose.ui.test`；RED 阶段先跑 connected 看它失败，再回 Step 3 修复转绿。）

Run（connected 单法）：
```bash
export ANDROID_SERIAL=emulator-5576
"$GRADLE" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.englishlearning.ui.ArticleReadingScreenTest#marks_switch_exposes_chinese_label com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
```

- [ ] **Step 5: 全量门禁 + 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/ReminderSettingsScreen.kt app/src/main/java/com/example/englishlearning/ui/ArticleReadingScreen.kt app/src/main/java/com/example/englishlearning/ui/BackupExportScreen.kt app/src/main/java/com/example/englishlearning/ui/WorksheetSettingsScreen.kt app/src/test/java/com/example/englishlearning/ui/AccessibilitySwitchLabelContractTest.kt app/src/androidTest/java/com/example/englishlearning/ui/ArticleReadingScreenTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "fix(a11y): 四处 Switch 补中文语义标签并双轨固化（F5-03 决策§4）"
```

---

### Task 5: 选中态 / Tab 角色 / 展开态 / Slider 值语义批

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（WordBookCard Row ~:1981；Slider ~:2119）
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppBottomBar.kt:78`
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt:85-91`
- Modify: `app/src/main/java/com/example/englishlearning/ui/LicensesScreen.kt:123`
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReadingHistoryScreen.kt:91`
- Modify: `app/src/test/java/com/example/englishlearning/ui/AccessibilityStateSemanticsContractTest.kt`（新建）

**Interfaces:**
- Consumes: 无新接口。
- Produces: 词书卡 `stateDescription=已选/未选`；底部导航 `Role.Tab`；展开组件 `已展开/已收起`；Slider 描述带值。

- [ ] **Step 1: 写失败契约测试**

```kotlin
package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** F5-03（决策 §4）：选中态/角色/展开态/滑杆值必须有静态可验证的语义声明。 */
class AccessibilityStateSemanticsContractTest {
    private fun codeOf(name: String): String =
        File("src/main/java/com/example/englishlearning/ui/$name").readText()
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .replace(Regex("//.*"), "")

    @Test
    fun `wordbook card announces selected state`() {
        assertTrue(
            Regex("""stateDescription = if \(selected\) "已选" else "未选"""").containsMatchIn(codeOf("AppScreen.kt")),
            "WordBookCard 必须把选中态并入语义（stateDescription），「已选」不能只是视觉",
        )
    }

    @Test
    fun `bottom bar tabs have role and selected state`() {
        val code = codeOf("AppBottomBar.kt")
        assertTrue(code.contains("role = Role.Tab"), "TabSlot 缺 Role.Tab")
        assertTrue(Regex("""stateDescription = if \(selected\)""").containsMatchIn(code), "TabSlot 缺选中态播报")
    }

    @Test
    fun `learning records tabs declare tab role`() {
        assertTrue(codeOf("LearningRecordsScreen.kt").contains("role = Role.Tab"), "学习记录 Tab 缺角色")
    }

    @Test
    fun `expandable cards announce expand state`() {
        listOf("LicensesScreen.kt" to "已展开", "ReadingHistoryScreen.kt" to "已展开").forEach { (file, label) ->
            assertTrue(Regex("""stateDescription = if \(expanded\) "$label"""").containsMatchIn(codeOf(file)),
                "$file 展开组件缺 stateDescription")
        }
    }

    @Test
    fun `daily target slider description embeds current value`() {
        assertTrue(
            Regex("""contentDescription = "每日新词目标，当前 \$\{displayedTarget} 词"""").containsMatchIn(codeOf("AppScreen.kt")),
            "Slider 描述必须带当前值",
        )
    }
}
```

- [ ] **Step 2: 确认 RED** — Run: `--tests "*AccessibilityStateSemanticsContractTest"`，五断言全红。

- [ ] **Step 3: 修复**

- `AppScreen.kt` WordBookCard Row（:1981 处）：
  ```kotlin
  .semantics {
      contentDescription = "选择词书 ${book.displayName}"
      stateDescription = if (selected) "已选" else "未选"
  },
  ```
- `AppScreen.kt` Slider（:2119 处）：`contentDescription = "每日新词目标"` → `contentDescription = "每日新词目标，当前 ${displayedTarget} 词"`
- `AppBottomBar.kt:78`：`.semantics { contentDescription = tab.contentDescription },` →
  ```kotlin
  .semantics {
      contentDescription = tab.contentDescription
      role = Role.Tab
      stateDescription = if (selected) "已选中" else "未选中"
  },
  ```
  （补 `import androidx.compose.ui.semantics.Role`、`stateDescription`。）
- `LearningRecordsScreen.kt:90`：`.semantics { contentDescription = label },` → `.semantics { contentDescription = label; role = Role.Tab },`（同 import。）
- `LicensesScreen.kt:123` NoticeCard：`.semantics { contentDescription = title },` →
  ```kotlin
  .semantics {
      contentDescription = title
      stateDescription = if (expanded) "已展开" else "已收起"
  },
  ```
- `ReadingHistoryScreen.kt:91` 组卡：`modifier = Modifier.fillMaxWidth().testTag("reading_history_group_$key"),` →
  ```kotlin
  modifier = Modifier
      .fillMaxWidth()
      .testTag("reading_history_group_$key")
      .semantics { stateDescription = if (expanded) "已展开" else "已收起" },
  ```

全文件补 `import androidx.compose.ui.semantics.stateDescription`。

- [ ] **Step 4: GREEN + 全量门禁** — Expected: 契约 5/5 PASS、全量单测 0 失败。connected 抽查：AppBottomBarTest 增一条 `onNodeWithTag("app_tab_learn").assertContentDescription…` 非必需；以契约+编译为准，行为播报归 QA 第三轮。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/AppScreen.kt app/src/main/java/com/example/englishlearning/ui/AppBottomBar.kt app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt app/src/main/java/com/example/englishlearning/ui/LicensesScreen.kt app/src/main/java/com/example/englishlearning/ui/ReadingHistoryScreen.kt app/src/test/java/com/example/englishlearning/ui/AccessibilityStateSemanticsContractTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(a11y): 选中态/Tab角色/展开态/滑杆值语义批（F5-03）"
```

---

### Task 6: liveRegion + 揭晓焦点顺序 + 滑动删除 customActions

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordCardScreen.kt`（submitting/发音状态 Text；揭晓块 :203-227）
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（SwipableWordBookCard :1920-1925）
- Create: `app/src/androidTest/java/com/example/englishlearning/ui/WordCardRevealFocusTest.kt`

**Interfaces:**
- Consumes: `WordCardScreen(state, onReveal=…)` 既有签名。
- Produces: `word_card_meaning` 节点在揭晓瞬间获焦；`word_card_submitting`/发音消息 liveRegion；`wordbook_swipe_*` 带「删除词书」customAction。

- [ ] **Step 1: 写失败 connected 行为测试**

`WordCardRevealFocusTest.kt`（fixture 复用：先 `grep -rn "WordCardUiState.Ready(" app/src/androidTest` 找现成构造 helper，照 `LearningFlowFixtures.kt` 用法组屏；Ready 态取 `meaningRevealed=false`）：

```kotlin
@Test
fun revealing_meaning_moves_focus_to_meaning() {
    var revealed = false
    composeTestRule.setContent {
        WordCardScreen(
            state = readyState(meaningRevealed = revealed),
            onReveal = { revealed = true },
        )
    }
    composeTestRule.onNodeWithTag("word_card_reveal").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("word_card_meaning").assert(isFocused())
}
```
（`isFocused` 来自 `androidx.compose.ui.test`；若组屏状态机比示意复杂，用测试侧 `mutableStateOf` 驱动 `meaningRevealed`。）

- [ ] **Step 2: 跑 connected 确认 RED** — 用 Global Constraints 的 am instrument 命令跑该单法；Expected: 焦点仍在按钮/容器上，断言失败。

- [ ] **Step 3: WordCardScreen 改造**

在包含揭晓块的 composable 顶部（`state.meaningRevealed` 可读的作用域内）加：

```kotlin
val meaningFocusRequester = remember { FocusRequester() }
var wasRevealed by remember { mutableStateOf(state.meaningRevealed) }
LaunchedEffect(state.meaningRevealed) {
    if (state.meaningRevealed && !wasRevealed) {
        runCatching { meaningFocusRequester.requestFocus() }
    }
    wasRevealed = state.meaningRevealed
}
```

`word_card_meaning` Text（:204-211）modifier 链首插 `.focusRequester(meaningFocusRequester)`（置于 `.testTag(...)` 之前）。

liveRegion：`word_card_submitting`（:265）与 `word_card_pronunciation_message`（:267-274）的 modifier 各追加
`.semantics { liveRegion = LiveRegionMode.Polite }`。

Imports：`androidx.compose.runtime.LaunchedEffect`（若无）、`androidx.compose.runtime.mutableStateOf`、`androidx.compose.runtime.getValue/setValue`、`androidx.compose.ui.focus.FocusRequester`、`androidx.compose.ui.focus.focusRequester`、`androidx.compose.ui.semantics.LiveRegionMode`、`androidx.compose.ui.semantics.liveRegion`。

- [ ] **Step 4: AppScreen 滑动删除 customActions**

`SwipableWordBookCard`（:1925）`SwipeToDismissBox` 的 `modifier = Modifier.testTag("wordbook_swipe_${book.id}"),` →

```kotlin
modifier = Modifier
    .testTag("wordbook_swipe_${book.id}")
    .semantics {
        onDelete?.let { triggerDelete ->
            customActions(SemanticsAction(label = "删除词书", action = { triggerDelete() }))
        }
    },
```

手势红线不动：`confirmValueChange` 恒 return false、`backgroundContent` 红底呈现全部保持。补 `import androidx.compose.ui.semantics.SemanticsAction`。若 `customActions(...)` vararg 签名不符编译要求，改 `customActions(listOf(SemanticsAction(...)))` 并以编译器为准；**该 customAction 无可自动化断言（Semantics 动作矩阵不在测试 API），connected 仅跑既有 AppScreenTest 词书族回归确认无破坏，删除动作可达性登记 QA 第三轮 TalkBack 取证**。

- [ ] **Step 5: GREEN** — connected 跑 `WordCardRevealFocusTest` OK；`:app:compileDebugAndroidTestKotlin` 通过；全量 JVM 门禁 0 失败。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/WordCardScreen.kt app/src/main/java/com/example/englishlearning/ui/AppScreen.kt app/src/androidTest/java/com/example/englishlearning/ui/WordCardRevealFocusTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(a11y): 揭晓焦点迁移、状态 liveRegion 与滑动删除无障碍动作（F5-03）"
```

---

### Task 7: 发音域层四控（Provider/Router/AudioPlayer/TTS，全 JVM TDD）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationProvider.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationRouter.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/AndroidTextToSpeechProvider.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/AudioPlayer.kt`（接口文件，含 `AudioPlaybackResult`）
- Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/AndroidAudioPlayer.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/KokoroPronunciationProvider.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/MiMoPronunciationProvider.kt`、`OpenAiCompatiblePronunciationProvider.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/PronunciationStatus.kt`
- Test: `app/src/test/java/com/example/englishlearning/language/domain/PronunciationRouterControlsTest.kt`（新建）、`.../infrastructure/AndroidTextToSpeechProviderTest.kt`（扩展）、`.../infrastructure/KokoroPronunciationProviderControlsTest.kt`（新建）、`app/src/test/java/com/example/englishlearning/ui/PronunciationStatusTest.kt`（新建或扩展）

**Interfaces:**
- Consumes: 无。
- Produces（Task 8/9 依赖，签名精确）:
  - `PronunciationProvider.pausePlayback(): Boolean` / `resumePlayback(): Boolean` / `stopPlayback(): Unit`（默认 false/false/空）
  - `AudioPlayer.pauseCurrentPlayback(): Boolean` / `resumeCurrentPlayback(): Boolean` / `stopCurrentPlayback(): Unit`（默认同上）
  - `PronunciationStatus.Paused` + 文案「已暂停播放，再次点按可继续。」

- [ ] **Step 1: 写失败测试（三组全 RED）**

`PronunciationRouterControlsTest.kt`：

```kotlin
package com.example.englishlearning.language.domain

import com.example.englishlearning.language.SpeechPreferenceRepository
import kotlinx.coroutines.test.runTest
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PronunciationRouterControlsTest {
    private class FakeProvider(val name: String, val pauseSupported: Boolean = true) : PronunciationProvider {
        val spoken = mutableListOf<String>()
        var pauseCount = 0
        var resumeCount = 0
        var stopCount = 0
        override fun capabilities(): Set<PronunciationCapability> = emptySet()
        override suspend fun speak(text: String): PronunciationResult { spoken += text; return PronunciationResult.Played }
        override fun pausePlayback(): Boolean { pauseCount++; return pauseSupported }
        override fun resumePlayback(): Boolean { resumeCount++; return pauseSupported }
        override fun stopPlayback() { stopCount++ }
    }

    private class FakePrefRepo(private val preference: SpeechPreference) : SpeechPreferenceRepository {
        override suspend fun get(): Result<SpeechPreference> = Result.success(preference)
        override suspend fun save(preference: SpeechPreference): Result<Unit> = Result.success(Unit)
    }

    private fun router(
        kokoro: PronunciationProvider,
        system: PronunciationProvider,
        preference: SpeechPreference = SpeechPreference(),
    ) = PronunciationRouter(kokoro, system, MiMoPronunciationProviderFactory { error("unused") }, FakePrefRepo(preference))

    @Test
    fun `pause without any active session is unsupported`() = runTest {
        val router = router(FakeProvider("kokoro"), FakeProvider("system"))
        assertFalse(router.pausePlayback())
        assertFalse(router.resumePlayback())
    }

    @Test
    fun `word playback routes controls to kokoro`() = runTest {
        val kokoro = FakeProvider("kokoro")
        val router = router(kokoro, FakeProvider("system"))
        router.speakWord("ability", PronunciationAccent.American)
        assertTrue(router.pausePlayback())
        assertEquals(1, kokoro.pauseCount)
        assertTrue(router.resumePlayback())
        assertEquals(1, kokoro.resumeCount)
        router.stopPlayback()
        assertEquals(1, kokoro.stopCount)
        // 停止后会话清空：再按暂停不得命中旧会话
        assertFalse(router.pausePlayback())
    }

    @Test
    fun `sentence playback routes controls to the routed engine`() = runTest {
        val system = FakeProvider("system")
        val router = router(FakeProvider("kokoro"), system, SpeechPreference(selectedEngine = PronunciationEngine.SystemTts))
        router.speakSentence("An example sentence")
        assertTrue(router.pausePlayback())
        assertEquals(1, system.pauseCount)
    }

    @Test
    fun `engine without pause support reports failure without touching others`() = runTest {
        val system = FakeProvider("system", pauseSupported = false)
        val router = router(FakeProvider("kokoro"), system, SpeechPreference(selectedEngine = PronunciationEngine.SystemTts))
        router.speakSentence("text")
        assertFalse(router.pausePlayback())
        assertFalse(router.resumePlayback())
    }
}
```

`PronunciationStatusTest.kt`（若已存在同名文件则在尾部加方法）：

```kotlin
package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PronunciationStatusTest {
    @Test
    fun `paused status has fixed chinese message`() {
        assertEquals("已暂停播放，再次点按可继续。", PronunciationStatus.Paused.message())
    }
}
```

`AndroidTextToSpeechProviderTest`：`FakeEngine` 增加 `override` 三方法（见 Step 3 的引擎接口）并加测试：

```kotlin
@Test
fun `pause resume and stop delegate to engine`() {
    val engine = FakeEngine(initialized = true)
    val provider = AndroidTextToSpeechProvider(engine)
    assertTrue(provider.pausePlayback()); assertEquals(1, engine.pauseCount)
    assertTrue(provider.resumePlayback()); assertEquals(1, engine.resumeCount)
    provider.stopPlayback(); assertEquals(1, engine.stopCount)
}

@Test
fun `providers without session report unsupported by default`() {
    val bare = object : PronunciationProvider {
        override fun capabilities(): Set<com.example.englishlearning.language.domain.PronunciationCapability> = emptySet()
        override suspend fun speak(text: String) = com.example.englishlearning.language.domain.PronunciationResult.Played
    }
    kotlin.test.assertFalse(bare.pausePlayback())
    kotlin.test.assertFalse(bare.resumePlayback())
    bare.stopPlayback()
}
```
（`bare` 测试挪进 `PronunciationRouterControlsTest` 亦可；就近原则由执行者定，测试数量只增。）

`KokoroPronunciationProviderControlsTest.kt`：fake `AudioPlayer`（记录 pause/resume/stop 计数，返回值可控），断言 `KokoroPronunciationProvider(...).pausePlayback()` 委托 `player.pauseCurrentPlayback()` 且透传返回值；stop/resume 同族。

- [ ] **Step 2: RED 确认** — Run: `--tests "*PronunciationRouterControlsTest" --tests "*PronunciationStatusTest" --tests "*AndroidTextToSpeechProviderTest" --tests "*KokoroPronunciationProviderControlsTest"`；Expected: 编译失败（方法不存在）即红，贴输出。

- [ ] **Step 3: 最小实现**

`PronunciationProvider.kt` 接口体内 `capabilities()` 之后加：

```kotlin
    /** 暂停当前发声会话；不支持或无会话时 false（决策 §2）。 */
    fun pausePlayback(): Boolean = false

    /** 继续已暂停的会话；不支持或无会话时 false。 */
    fun resumePlayback(): Boolean = false

    /** 结束当前会话；默认实现为空操作（未播放时安全无副作用）。 */
    fun stopPlayback() { }
```

`PronunciationRouter.kt`：类内加 `private var activeProvider: PronunciationProvider? = null`；
`speakWord(word, accent, forceRegenerate)` 改为：

```kotlin
    override suspend fun speakWord(
        word: String,
        accent: PronunciationAccent,
        forceRegenerate: Boolean,
    ): PronunciationResult {
        activeProvider = kokoroProvider
        return kokoroProvider.speak(word, accent, forceRegenerate)
    }
```

`routeSentence` 的两个分支前分别置 `activeProvider = systemProvider` /（MiMo 成功路径）`activeProvider = remoteProvider`（`speakMiMoOrSystem` 里 create 出的实例赋给局部 val 后设置；系统降级分支设 `activeProvider = systemProvider`）。类尾加：

```kotlin
    override fun pausePlayback(): Boolean = activeProvider?.pausePlayback() ?: false
    override fun resumePlayback(): Boolean = activeProvider?.resumePlayback() ?: false
    override fun stopPlayback() {
        activeProvider?.stopPlayback()
        activeProvider = null
    }
```

`AndroidTextToSpeechProvider.kt`：`internal interface TextToSpeechEngine` 加 `fun pause(): Boolean`、`fun resume(): Boolean`；实现 `override fun pause(): Boolean = textToSpeech.pause() == TextToSpeech.SUCCESS`、`override fun resume(): Boolean = textToSpeech.resume() == TextToSpeech.SUCCESS`；`FakeEngine`（测试文件）同步加 `var pauseCount = 0; var resumeCount = 0` 与两个 override（返回 true）。Provider 加：

```kotlin
    override fun pausePlayback(): Boolean = engine.pause()
    override fun resumePlayback(): Boolean = engine.resume()
    override fun stopPlayback() { engine.stop() }
```

`AudioPlayer.kt` 接口：

```kotlin
interface AudioPlayer {
    suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult

    fun pauseCurrentPlayback(): Boolean = false
    fun resumeCurrentPlayback(): Boolean = false
    fun stopCurrentPlayback() { }
}
```

`AndroidAudioPlayer.kt` 全文件重写（要点：`active` 句柄 + `pending` 续程；停止以 Played 收口；prepared 后才挂 active；cancellation 清引用）：

```kotlin
package com.example.englishlearning.language.infrastructure

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidAudioPlayer(private val context: Context) : AudioPlayer {
    private var active: MediaPlayer? = null
    private var pending: CancellableContinuation<AudioPlaybackResult>? = null

    override suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult =
        suspendCancellableCoroutine { continuation ->
            val player = MediaPlayer()
            pending = continuation
            fun finish(result: AudioPlaybackResult) {
                if (active === player) active = null
                if (pending === continuation) pending = null
                if (continuation.isActive) continuation.resume(result)
                player.release()
            }
            try {
                player.setDataSource(ByteArrayMediaDataSource(bytes))
                player.setOnPreparedListener { mp -> active = mp; mp.start() }
                player.setOnCompletionListener { finish(AudioPlaybackResult.Played) }
                player.setOnErrorListener { _, _, _ -> finish(AudioPlaybackResult.Failed); true }
                player.prepareAsync()
                continuation.invokeOnCancellation {
                    if (active === player) active = null
                    if (pending === continuation) pending = null
                    player.release()
                }
            } catch (_: Exception) {
                finish(AudioPlaybackResult.Failed)
            }
        }

    override fun pauseCurrentPlayback(): Boolean {
        val player = active ?: return false
        return runCatching { if (player.isPlaying) { player.pause(); true } else false }.getOrDefault(false)
    }

    override fun resumeCurrentPlayback(): Boolean {
        val player = active ?: return false
        return runCatching { if (!player.isPlaying) { player.start(); true } else false }.getOrDefault(false)
    }

    /** 用户主动停止=一次正常结束：以 Played 收口挂起续程，防止 speak 悬挂。 */
    override fun stopCurrentPlayback() {
        pending?.let { continuation ->
            if (continuation.isActive) continuation.resume(AudioPlaybackResult.Played)
        }
        pending = null
        active?.let { player -> active = null; runCatching { player.release() } }
    }

    private class ByteArrayMediaDataSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size, bytes.size - position.toInt())
            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()
        override fun close() = Unit
    }
}
```

`KokoroPronunciationProvider.kt` 类尾加三行委托（`player` 字段名按现状）：

```kotlin
    override fun pausePlayback(): Boolean = player.pauseCurrentPlayback()
    override fun resumePlayback(): Boolean = player.resumeCurrentPlayback()
    override fun stopPlayback() { player.stopCurrentPlayback() }
```

`MiMoPronunciationProvider.kt` / `OpenAiCompatiblePronunciationProvider.kt`：各按其 `AudioPlayer` 字段（`grep -n "AudioPlayer" <文件>` 确认字段名）加同款三委托 + `suspend` 路径的 `is`/`invokeOnCancellation` 清理逻辑不变。

`PronunciationStatus.kt`：enum 加 `Paused,`（`Playing` 之后）；`message()` 加 `PronunciationStatus.Paused -> "已暂停播放，再次点按可继续。"`。

- [ ] **Step 4: GREEN + 全量门禁** — Expected: 新测试全绿；全量 ≥1396+10 0 失败；`compileDebugAndroidTestKotlin` 通过（既有 AndroidAudioPlayer androidTest 用户 KokoroProductionPronunciationTest 不破）。connected 抽查：Kokoro 播中暂停/停止以设备 QA 取证（长句可及），登记 TODO-USER。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/language app/src/main/java/com/example/englishlearning/ui/PronunciationStatus.kt app/src/test/java/com/example/englishlearning/language app/src/test/java/com/example/englishlearning/ui/PronunciationStatusTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(pronunciation): Provider/Router/AudioPlayer 三层暂停-继续-停止会话控制（F3-02 四控）"
```

---

### Task 8: 语速持久化（Room v32）+ 播放侧变速接线

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/entity/SpeechPreferenceEntity.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/SpeechPreference.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/RoomSpeechPreferenceRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`（version=32、MIGRATION_31_32、ALL_MIGRATIONS）
- Create: `app/schemas/com.example.englishlearning.core.storage.AppDatabase/32.json`（构建生成后入库）
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationProvider.kt`（4 参 speak 重载）
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationRouter.kt`（rate 透传）
- Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/AndroidTextToSpeechProvider.kt`、`KokoroPronunciationProvider.kt`、`AndroidAudioPlayer.kt`、`MiMoPronunciationProvider.kt`、`OpenAiCompatiblePronunciationProvider.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/SpeechSettingsViewModel.kt`、`SpeechSettingsScreen.kt`、`AppScreen.kt`（调用点）
- Test: JVM `PronunciationRouterControlsTest`（+rate 用例）、`AndroidTextToSpeechProviderTest`（+rate 用例）、`KokoroPronunciationProviderControlsTest`（+rate 用例）、`SpeechSettingsViewModelTest`（+cycleSpeechRate 用例）；androidTest `AppDatabaseMigrationTest`（+v31→v32）

**Interfaces:**
- Consumes: Task 7 全部产出。
- Produces:
  - `SpeechPreference.speechRate: Float = 1.0f`（clamp 0.5–2.0 双侧）
  - `PronunciationProvider.speak(text, accent, forceRegenerate, rate)` 重载（默认忽略 rate 委托 3 参）
  - `AudioPlayer.play(bytes, format, rate: Float = 1f)`（默认实现忽略）
  - `SpeechSettingsViewModel.cycleSpeechRate()`（档位 {0.75,1.0,1.25,1.5} 循环）+ `SpeechSettingsUiState.speechRate`
  - Room `MIGRATION_31_32`（公开 val，与 MIGRATION_30_31 同区）

- [ ] **Step 1: 写失败测试**

JVM（追加进 `PronunciationRouterControlsTest.kt`）：

```kotlin
    @Test
    fun `sentence and word playback pass persisted rate to the engine`() = runTest {
        class RateCapturingProvider : PronunciationProvider {
            var lastRate: Float? = null
            override fun capabilities(): Set<PronunciationCapability> = emptySet()
            override suspend fun speak(text: String): PronunciationResult = PronunciationResult.Played
            override suspend fun speak(
                text: String,
                accent: PronunciationAccent,
                forceRegenerate: Boolean,
                rate: Float,
            ): PronunciationResult { lastRate = rate; return PronunciationResult.Played }
        }
        val system = RateCapturingProvider()
        val kokoro = RateCapturingProvider()
        val router = PronunciationRouter(
            kokoro, system,
            MiMoPronunciationProviderFactory { error("unused") },
            FakePrefRepo(SpeechPreference(selectedEngine = PronunciationEngine.SystemTts, speechRate = 1.25f)),
        )
        router.speakSentence("sentence")
        assertEquals(1.25f, system.lastRate)
        router.speakWord("word", PronunciationAccent.American)
        assertEquals(1.25f, kokoro.lastRate)
    }
```

JVM（`AndroidTextToSpeechProviderTest`，FakeEngine 加 `var lastRate: Float? = null; override fun setSpeechRate(rate: Float): Boolean { lastRate = rate; return true }`）：

```kotlin
    @Test
    fun `rate overrides applied before speak`() {
        val engine = FakeEngine(initialized = true)
        val provider = AndroidTextToSpeechProvider(engine)
        runTest { provider.speak("hello", PronunciationAccent.American, false, 1.5f) }
        assertEquals(1.5f, engine.lastRate)
    }
```

JVM（`KokoroPronunciationProviderControlsTest` 的 fake player：`play` 重载记录 rate）：断言 4 参 speak 把 rate 带到 `player.play(..., rate)`。

JVM（`SpeechSettingsViewModelTest`——若不存在，照 `AiProfileSettingsViewModelTest` 的构造样板新建，fake `SpeechPreferenceRepository` + VM 实际构造参数按文件现状）：

```kotlin
    @Test
    fun `cycle speech rate advances step and persists`() = runTest {
        // load 后 1.0f → cycle → 1.25f，且 fake repo 收到 speechRate=1.25f
    }
```
（测试体按 VM 现有 `load()`/state collect 写法补全，断言 `state.speechRate == 1.25f` 与 fake repo 保存值；禁止只留注释——此行是任务要求。）

androidTest（`AppDatabaseMigrationTest.kt` 追加，helper 用文件既有 `migrationHelper()`/`TEST_DB`）：

```kotlin
    @Test
    fun migrateV31ToV32_addsSpeechRateColumnWithBackfillWithoutLosingRows() {
        val helper = migrationHelper()
        helper.createDatabase(TEST_DB, 31).apply {
            execSQL(
                "INSERT INTO speech_preferences (preferenceId, selectedEngine, openAiProfileId, miMoProfileId, autoSpeakOnCardReady) " +
                    "VALUES ('device', 'SystemTts', NULL, NULL, 1)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 32, true, AppDatabase.MIGRATION_31_32).apply {
            query("SELECT autoSpeakOnCardReady, speech_rate FROM speech_preferences WHERE preferenceId = 'device'").use { cursor ->
                assertTrue("the v31 preference row must survive", cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals(1.0f, cursor.getFloat(1), 0.0001f)
            }
            close()
        }
    }
```

- [ ] **Step 2: RED 确认** — JVM 编译失败（API 缺失）+ 迁移测试未跑（先编译过 Step 3 后 connected 验 RED：临时把 migration 里 SQL 写成会 validate 失败的列名 `speech_rate_tmp`——**不，红线**：直接按缺列跑 `runMigrationsAndValidate` 预期失败一次，贴输出后即恢复正确 SQL 转 GREEN。允许的证据形式：首次 connected 跑发生在 Entity/migration 落地之前、只含 schema 变更时，validate 因 schema 32.json 与表结构不符而红。）

- [ ] **Step 3: 实现**

- `SpeechPreference.kt`：`val speechRate: Float = 1.0f`（autoSpeak 字段后）。
- `SpeechPreferenceEntity.kt`：
  ```kotlin
  /** v32 起存在；播放侧变速参数（决策 §2），迁移对历史行回填 1.0。 */
  @ColumnInfo(defaultValue = "1.0") val speechRate: Float = 1.0f,
  ```
- `RoomSpeechPreferenceRepository.kt`：`toEntity()` 加 `speechRate = speechRate.coerceIn(0.5f, 2.0f)`；`toDomain()` 加 `speechRate = speechRate.coerceIn(0.5f, 2.0f)`。
- `AppDatabase.kt`：`version = 32`；`MIGRATION_30_31` 之后加（同 `val MIGRATION_x_y` 风格）：
  ```kotlin
  val MIGRATION_31_32: Migration = object : Migration(31, 32) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE speech_preferences ADD COLUMN speech_rate REAL NOT NULL DEFAULT 1.0")
      }
  }
  ```
  并把 `MIGRATION_31_32` 追加到 ALL_MIGRATIONS `arrayOf(...)` 尾部。
- `PronunciationProvider.kt` 接口加 4 参重载：
  ```kotlin
  suspend fun speak(
      text: String,
      accent: PronunciationAccent,
      forceRegenerate: Boolean,
      rate: Float,
  ): PronunciationResult = speak(text, accent, forceRegenerate)

  suspend fun speakWord(
      text: String,
      accent: PronunciationAccent = PronunciationAccent.American,
      forceRegenerate: Boolean = false,
      rate: Float = 1.0f,
  ): PronunciationResult = speak(text, accent, forceRegenerate, rate)
  ```
  （注意：新增 4 参 speakWord 与既有 3 参默认重载共存时以编译器为准消歧——若签名冲突，保留 4 参版并把原 3 参版删去，Router 与全部调用点改走 4 参；决策 §2 已允许该形态，单测口径不变。）
- `PronunciationRouter.kt`：`speakWord(..., rate)` 与 `routeSentence` 调 `savedPreference()` 后把 `preference.speechRate`（routeSentence 已读 preference；speakWord 新增一次读取）传进 4 参 `provider.speak(text, accent, forceRegenerate, rate)`；speakMiMoOrSystem 同样透传 rate 给 remote 与降级 system 调用。
- `AndroidTextToSpeechProvider.kt`：引擎接口加 `fun setSpeechRate(rate: Float): Boolean`，实现 `textToSpeech.setSpeechRate(rate) != TextToSpeech.ERROR`；Provider 加 4 参 override：
  ```kotlin
  override suspend fun speak(
      text: String,
      accent: PronunciationAccent,
      forceRegenerate: Boolean,
      rate: Float,
  ): PronunciationResult {
      engine.setSpeechRate(rate)
      return speak(text)
  }
  ```
- `AudioPlayer.kt`：`suspend fun play(bytes: ByteArray, format: String, rate: Float = 1f): AudioPlaybackResult`（原 2 参声明合并为带默认值的单一声明）。
- `AndroidAudioPlayer.kt`：`play(…, rate)` 在 `setOnPreparedListener` 内 `mp.start()` 前加 `runCatching { mp.playbackParams = mp.playbackParams.setSpeed(rate) }`。
- `KokoroPronunciationProvider.kt`：`speakPrepared/playAndCache/playCached` 各加 `rate: Float` 参数并传给 `player.play(audio, "wav", rate)`；4 参 speak override（mutex 包裹，同 3 参版）。
- `MiMoPronunciationProvider.kt` / `OpenAiCompatiblePronunciationProvider.kt`：同法（grep `player.play(` 逐点补 rate）。
- `SpeechSettingsViewModel.kt`：`SpeechSettingsUiState` 加 `val speechRate: Float = 1.0f`；`load()` 映射偏好；`save/其它写路径` 的 `copy(...)` 保留 speechRate（检查 `setSelectedEngine`/`setAutoSpeakOnCardReady` 的 next.copy 是否需带上——偏好读改写来自 stored，天然保留）；新增：
  ```kotlin
  /** 语速四档循环（决策 §2）：0.75x → 1.0x → 1.25x → 1.5x → 0.75x。 */
  fun cycleSpeechRate() = viewModelScope.launch {
      val steps = listOf(0.75f, 1.0f, 1.25f, 1.5f)
      val stored = preferences.get().getOrDefault(SpeechPreference())
      val nearest = steps.indices.minByOrNull { i -> kotlin.math.abs(steps[i] - stored.speechRate) } ?: 1
      val next = steps[(nearest + 1) % steps.size]
      if (preferences.save(stored.copy(speechRate = next)).isSuccess) {
          _state.value = _state.value.copy(speechRate = next, message = null)
      } else {
          _state.value = _state.value.copy(message = STORAGE_FAILED_MESSAGE)
      }
  }
  ```
- `SpeechSettingsScreen.kt`：签名加 `onCycleSpeechRate: () -> Unit = {}`；在自动发音 Row（:89-109）之后加：
  ```kotlin
  // 语速（F3-02 四控之速）：播放侧变速，不重新生成音频（决策 §2）。
  Row(
      modifier = Modifier
          .fillMaxWidth()
          .clickable { onCycleSpeechRate() }
          .padding(vertical = 10.dp)
          .testTag("speech_rate_toggle")
          .semantics { contentDescription = "发音语速" },
      verticalAlignment = Alignment.CenterVertically,
  ) {
      Column(Modifier.weight(1f)) {
          Text("发音语速", style = AppType.Title)
          Text("只调整本机播放速度，不会重新生成音频；对系统朗读与已缓存发音均生效。", style = AppType.Footnote)
      }
      Text(
        if (state.speechRate == 1f) "1x" else "${state.speechRate}x",
        modifier = Modifier.testTag("speech_rate_value"),
        style = AppType.Label,
        color = DomainColors.AiSpeech.deep,
      )
  }
  ```
- `AppScreen.kt`：`SpeechSettingsScreen(` 调用点（grep 定位）补 `onCycleSpeechRate = { speechSettingsViewModel?.cycleSpeechRate() },`。
- 构建生成 schema：`"$GRADLE" :app:assembleDebug --no-daemon` 后确认 `app/schemas/.../32.json` 生成。

- [ ] **Step 4: GREEN** — JVM 新用例全绿；connected 跑 `AppDatabaseMigrationTest#migrateV31ToV32_addsSpeechRateColumnWithBackfillWithoutLosingRows` OK（am instrument 单法）；全量门禁 0 失败（基线再 +4~5）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/core app/src/main/java/com/example/englishlearning/language app/src/main/java/com/example/englishlearning/ui/SpeechSettingsViewModel.kt app/src/main/java/com/example/englishlearning/ui/SpeechSettingsScreen.kt app/src/main/java/com/example/englishlearning/ui/AppScreen.kt app/src/test/java/com/example/englishlearning app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseMigrationTest.kt app/schemas/com.example.englishlearning.core.storage.AppDatabase/32.json
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(pronunciation): 语速偏好 Room v32 迁移与播放侧变速透传（F3-02）"
```

---

### Task 9: 四控 UI 接线（词卡 / 词卡详情 / 阅读页 + AppScreen 宿主）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordCardScreen.kt`（签名 + 控制按钮组）
- Modify: `app/src/main/java/com/example/englishlearning/ui/CardDetailScreen.kt`（签名 + 控制按钮组）
- Modify: `app/src/main/java/com/example/englishlearning/ui/ArticleReadingScreen.kt`（签名 + 状态文字与按钮组）
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（词卡/详情/阅读三组宿主状态与回调；`pronunciationProvider` 调用）
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/WordCardPronunciationControlsTest.kt`（新建）

**Interfaces:**
- Consumes: Task 7 `pausePlayback/resumePlayback/stopPlayback` + `PronunciationStatus.Paused`；Task 8 rate（经设置页生效）。
- Produces: 新公共参数——
  - `WordCardScreen(..., pronunciationStatus: PronunciationStatus = PronunciationStatus.Idle, onPauseResume: () -> Unit = {}, onStopPronunciation: () -> Unit = {})`
  - `CardDetailScreen(..., onPauseResume: () -> Unit = {}, onStopPronunciation: () -> Unit = {})`（status 已有入参）
  - `ArticleReadingScreen(..., pronunciationMessage: String? = null, pronunciationStatus: PronunciationStatus = PronunciationStatus.Idle, onPauseResume: () -> Unit = {}, onStopPronunciation: () -> Unit = {})`
  - testTag：`word_card_pronunciation_toggle|stop`、`card_detail_pronunciation_toggle|stop`、`article_pronunciation_toggle|stop|message`

- [ ] **Step 1: 写失败 connected 行为测试**

`WordCardPronunciationControlsTest.kt`（组屏 fixture 照 `WordCardRevealFocusTest`）：

```kotlin
@Test
fun controls_appear_only_while_playing_or_paused_and_delegate() {
    var paused = false
    var status = PronunciationStatus.Idle
    composeTestRule.setContent {
        WordCardScreen(
            state = readyState(),
            pronunciationStatus = status,
            onPauseResume = { status = if (status == PronunciationStatus.Playing) PronunciationStatus.Paused else PronunciationStatus.Playing },
            onStopPronunciation = { status = PronunciationStatus.Idle },
        )
    }
    composeTestRule.onNodeWithTag("word_card_pronunciation_toggle").assertDoesNotExist()
    status = PronunciationStatus.Playing
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("word_card_pronunciation_toggle").assertIsDisplayed()
    composeTestRule.onNodeWithTag("word_card_pronunciation_stop").performClick()
    composeTestRule.onNodeWithTag("word_card_pronunciation_toggle").assertDoesNotExist()
    assertEquals(PronunciationStatus.Idle, status)
}
```
（`paused` 变量为示意状态机演示，实现按 `status` 单变量即可；RED = 参数不存在编译失败。）

- [ ] **Step 2: RED** — `:app:compileDebugAndroidTestKotlin` 因签名缺参报错，贴输出。

- [ ] **Step 3: Screen 侧实现**

`WordCardScreen.kt` 签名加三个参数（见 Interfaces）。在 speaker 按钮块（:228-233）之后插：

```kotlin
val speakActive = pronunciationStatus == PronunciationStatus.Playing || pronunciationStatus == PronunciationStatus.Paused
if (speakActive) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onPauseResume,
            modifier = Modifier
                .testTag("word_card_pronunciation_toggle")
                .semantics { contentDescription = if (pronunciationStatus == PronunciationStatus.Paused) "继续播放发音" else "暂停发音" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MintPrimaryDark),
        ) { Text(if (pronunciationStatus == PronunciationStatus.Paused) "继续" else "暂停") }
        OutlinedButton(
            onClick = onStopPronunciation,
            modifier = Modifier
                .testTag("word_card_pronunciation_stop")
                .semantics { contentDescription = "停止播放发音" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MintPrimaryDark),
        ) { Text("停止") }
    }
}
```
并把 speaker 按钮的 contentDescription 改为「播放/重播」双关文案：`"播放 $lemma 发音"` 保持不变即满足重播语义（再点即重播，QUEUE_FLUSH）。

`CardDetailScreen.kt`：签名加两参；在发音状态 message 块（:300-306）之后插同款按钮组（testTag 前缀 `card_detail_`，onClick 用新参数；可见条件同 `pronunciationStatus`）。

`ArticleReadingScreen.kt`：签名加四参；在「发音」按钮同一 Row 下方加：

```kotlin
pronunciationMessage?.let { message ->
    Text(
        text = message,
        color = MintTextMuted,
        modifier = Modifier
            .testTag("article_pronunciation_message")
            .semantics { contentDescription = message; liveRegion = LiveRegionMode.Polite },
    )
}
if (pronunciationStatus == PronunciationStatus.Playing || pronunciationStatus == PronunciationStatus.Paused) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPauseResume,
            modifier = Modifier.testTag("article_pronunciation_toggle").semantics {
                contentDescription = if (pronunciationStatus == PronunciationStatus.Paused) "继续播放发音" else "暂停发音"
            },
            colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
        ) { Text(if (pronunciationStatus == PronunciationStatus.Paused) "继续" else "暂停") }
        Button(
            onClick = onStopPronunciation,
            modifier = Modifier.testTag("article_pronunciation_stop").semantics { contentDescription = "停止播放发音" },
            colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
        ) { Text("停止") }
    }
}
```

- [ ] **Step 4: AppScreen 宿主接线**

词卡（:292-293 区域）：`wordCardPronunciationMessage` 旁加 `var wordCardPronunciationStatus by remember { mutableStateOf(PronunciationStatus.Idle) }`。自动发音 LaunchedEffect（:826-835）与 `onSpeak`（:838-849）里每次赋值 message 的同时赋 `wordCardPronunciationStatus`（请求前 `Playing`，回来后 `result`——为此把 `pronunciationStatus(...)` 结果存局部 val 再分别取 `.message()` 与状态，替换现有链式 `.message()`）。`WordCardScreen(` 调用点补：

> **失效标记（终审 M-7，2026-10-17 第二批修复轮补）**：本步骤代码块字面 `else -> PronunciationStatus.Idle`
> 已被 `docs/decisions/2026-10-15-f5-03-accessibility-hardening-semantics.md` §2 作废——五处 `onPauseResume`
> 宿主的 `else` 分支一律**保持原状态**（`else -> held` / `else -> 当前状态变量`），不得落 `Idle`：
> 落 `Idle` 会让整组播控（含唯一可用的「停止」）在朗读仍在继续时消失。防回归由
> `app/src/test/java/com/example/englishlearning/ui/PronunciationIdleFallbackContractTest.kt` 锁
> `AppScreen.kt` 剥注释后不得出现该字面形状。照本节正文实现前请先按本标记改写。

```kotlin
pronunciationStatus = wordCardPronunciationStatus,
onPauseResume = {
    val next = when {
        wordCardPronunciationStatus == PronunciationStatus.Playing && pronunciationProvider?.pausePlayback() == true -> PronunciationStatus.Paused
        wordCardPronunciationStatus == PronunciationStatus.Paused && pronunciationProvider?.resumePlayback() == true -> PronunciationStatus.Playing
        // 本行字面已被 docs/decisions/2026-10-15-f5-03-accessibility-hardening-semantics.md §2 作废：
        // 五处 else 一律保持原状态（else -> held / 当前状态变量），不得落 PronunciationStatus.Idle。
        else -> PronunciationStatus.Idle
    }
    wordCardPronunciationStatus = next
    wordCardPronunciationMessage = next.message()
},
onStopPronunciation = {
    wordCardPronunciationRequest++
    pronunciationProvider?.stopPlayback()
    wordCardPronunciationStatus = PronunciationStatus.Idle
    wordCardPronunciationMessage = null
},
```

详情：`detailPronunciationMessage`（已是 PronunciationStatus?）各 CardDetailScreen 调用点（grep `pronunciationStatus = detailPronunciationMessage`，共 3 处）补两组同构 lambda（以对应 `detailPronunciationRequest++` 与 `detailPronunciationMessage = …` 替换 setter；暂停/继续**不**自增代次——暂停不改写请求归属，只有 stop 自增）。onSpeak/onSpeakBritish 请求前把 `detailPronunciationMessage = PronunciationStatus.Playing`（现状已是）。

阅读页（:1264-1267 fire-and-forget 替换）：新增 remember 三件套（`articlePronunciationMessage/Status/Request`），宿主代码：

```kotlin
onSpeakSentence = { sentence ->
    val request = ++articlePronunciationRequest
    articlePronunciationStatus = PronunciationStatus.Playing
    articlePronunciationMessage = PronunciationStatus.Playing.message()
    pronunciationScope.launch {
        val result = pronunciationStatus(pronunciationProvider?.speakSentence(sentence))
        if (request == articlePronunciationRequest) {
            articlePronunciationStatus = result
            articlePronunciationMessage = result.message()
        }
    }
},
```
`ArticleReadingScreen(` 调用点补 `pronunciationMessage/pronunciationStatus/onPauseResume/onStopPronunciation`（onPauseResume/onStop 结构照词卡，用 article 前缀变量；`pronunciationProvider` 为 `PronunciationProvider?` 类型，pause/resume/stop 走 Task 7 接口）。清场时机：`pronunciationProvider` 切卡/退页处已有代次自增逻辑不动。

- [ ] **Step 5: GREEN** — connected `WordCardPronunciationControlsTest` OK；`CardDetailScreenTest`/`ArticleReadingScreenTest`/`AppScreenTest` 编译跑通（AppScreenTest 已知 8 flaky 按基线对照法判定）；全量 JVM 门禁 0 失败。connected 手动烟测（am start 到学习流）：播放长句中点暂停→静音、点继续→复播、点停止→文案回空；TTS 引擎下暂停按钮窗口短属决策 §2 已登记限制。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui/WordCardScreen.kt app/src/main/java/com/example/englishlearning/ui/CardDetailScreen.kt app/src/main/java/com/example/englishlearning/ui/ArticleReadingScreen.kt app/src/main/java/com/example/englishlearning/ui/AppScreen.kt app/src/androidTest/java/com/example/englishlearning/ui/WordCardPronunciationControlsTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(a11y): 词卡/详情/阅读页接入播放-暂停-重播-停止四控（F3-02/F5-03）"
```

---

### Task 10: 48dp 触控批

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordCardScreen.kt`（WordCardSpeakerButton :327-338）
- Modify: `app/src/main/java/com/example/englishlearning/ui/CardDetailScreen.kt`（AccentSpeakerButton :473-504）
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordBookWordsScreen.kt`（返回 :110-120；字母 :318-347）
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt`（返回 :62-71）
- Modify: `app/src/main/java/com/example/englishlearning/ui/LicensesScreen.kt`（‹ :56-64）
- Modify: `app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt`（三链接 :65-71）
- Modify: `app/src/main/java/com/example/englishlearning/ui/FavoritesScreen.kt`（返回 :70-79）
- Modify: `app/src/main/java/com/example/englishlearning/ui/WrongWordsScreen.kt`（返回 :62-71）
- Test: androidTest `CardDetailScreenTest` 追加尺寸断言

**Interfaces:**
- Consumes: 无。
- Produces: 小控件触控 ≥48dp。

- [ ] **Step 1: 断言先行（connected RED）**

`CardDetailScreenTest.kt` 追加：

```kotlin
@Test
fun accent_speaker_button_meets_minimum_touch_target() {
    // …既有组屏方式渲染含 card_detail_speak 的详情…
    composeTestRule.onNodeWithTag("card_detail_speak")
        .assertHeightIsAtLeast(48.dp.minHeight())
        .assertWidthIsAtLeast(48.dp.minWidth())
}
```
（`dp.minHeight()/minWidth()` 来自 `androidx.compose.ui.unit`。）跑该法确认 RED（现状高≈36dp）。

- [ ] **Step 2: 实现**

- `WordCardSpeakerButton`（WordCardScreen :330 行链）：`.padding(6.dp)` 后追加 `.minimumInteractiveComponentSize()`。
- `AccentSpeakerButton`（CardDetailScreen :489 行链）：`.padding(horizontal = 10.dp, vertical = 6.dp)` 后追加 `.minimumInteractiveComponentSize()`。
- 字母栏（WordBookWordsScreen :326）：`.clickable(onClick = { onLetter(letterText) })` 后追加 `.minimumInteractiveComponentSize()`（拖动几何 slotPixels 不动——见 :287 注释，手势槽与视觉布局保持，仅扩触控命中区）。
- 文字链接（各屏「← 返回」「返回」「我的收藏」「错词本」「‹」）：在 `.clickable(...)` **之前**插入 `.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`；保留现有 padding 与 testTag/semantics 链。
- Imports：`androidx.compose.foundation.layout.minimumInteractiveComponentSize`、`androidx.compose.foundation.layout.defaultMinSize`。

- [ ] **Step 3: GREEN** — connected 断言 OK；字母栏既有 connected（若有 rail 拖动测试）不回归；全量门禁 0 失败。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui app/src/androidTest/java/com/example/englishlearning/ui/CardDetailScreenTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "fix(a11y): 发音按钮/字母栏/文字链接触控目标补到 48dp（F5-03）"
```

---

### Task 11: 动态字体批（固定高改下限、chip 行可滚动、省略号）

**Files:**（行号为本计划基线快照，执行者以 grep 复核漂移）
- Modify: `TodayPlanScreen.kt:151,201,210,234,243,252,261`（`.height(5x.dp)` → `.heightIn(min = …)`；:210 `height(46.dp)` → `heightIn(min = 48.dp)`——并收 Task 10 遗留的 46dp 按钮）
- Modify: `ReadingAccessScreen.kt:357,376,401,416`（357 加 `overflow = TextOverflow.Ellipsis`；固定高 → heightIn(min=54.dp)）
- Modify: `ArticleReadingScreen.kt:167,222,231`（222 → heightIn(min=52.dp)；167 ModeChips Row 加 `.horizontalScroll(rememberScrollState())`；231 未覆盖词 Row → `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp))`）
- Modify: `WordBookWordsScreen.kt:203-206`（筛选 Chip Row 加 `.horizontalScroll(rememberScrollState())`）
- Modify: `WordAiQaScreen.kt:158-159`、`ArticleImportScreen.kt:111`、`AiFeatureScreen.kt:153`（固定高 → heightIn(min=…))
- Modify: `CheckInScreen.kt:102,117,160,166`（:102/:117 图例行 → heightIn(min=88.dp/120.dp)：柱高按 56/72*ratio 计算不动，行容器放宽；:160/:166 日历单元 → heightIn(min = 34.dp)）。`:132 size(132.dp)` Canvas 环**保留固定**（绘制几何；百分比数字在环下有同值文本兜底——记录于验证文档，不视为缺口）。
- 无测试文件改动；契约豁免：本任务不改任何 contentDescription/颜色。

**Interfaces:**
- Consumes: Task 9 已改的 `ArticleReadingScreen.kt`（本任务在其上追加，顺序不可颠倒）。
- Produces: 大字号下无裁切版面（代码侧）。

- [ ] **Step 1: 机械替换**

逐点 `Modifier…height(N.dp)`（Button/Card/Row 容器且内含 Text）→ `heightIn(min = N.dp)`；`overflow` 与滚动容器按上列 Files 执行。Imports：`androidx.compose.foundation.horizontalScroll`、`androidx.compose.foundation.rememberScrollState`、`androidx.compose.foundation.layout.FlowRow`、`androidx.compose.ui.text.style.TextOverflow`。

- [ ] **Step 2: 自查 grep 清零**

```bash
grep -rn "\.height([0-9]" app/src/main/java/com/example/englishlearning/ui | grep -avE "heightIn|Spacer|size\(|Canvas|Box\(.*aspect" 
```
Expected: 剩余命中逐项说明（保留理由写进提交信息：Canvas 几何/装饰分隔）；不得有 Button/Card/Text 容器裸 `height()`。

- [ ] **Step 3: 门禁** — `:app:assembleDebug :app:testDebugUnitTest` 0 失败；connected 抽查 `LearningRecordsScreenTest` 全类 OK（既有断言不含尺寸，预期绿）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "fix(a11y): 固定高度改弹性下限、chip 行可滚动、标题补省略号（F5-03 动态字体）"
```

---

### Task 12: 收口——矩阵升级、验证文档、connected 全清单、报告与推送

**Files:**
- Modify: `docs/verification/ac-traceability-matrix.md`（阶段 5 AC 节 AC5-03 行）
- Create: `docs/verification/f5-03-accessibility-hardening-2026-10.md`
- Create: `docs/handoff/2026-10-15-stage-5-f5-03-accessibility-hardening-report.md`

**Interfaces:**
- Consumes: 前 11 任务全部产出。
- Produces: 可推送的收口证据链。

- [ ] **Step 1: 全量门禁**

```bash
"$GRADLE" :app:assembleDebug :app:testDebugUnitTest --no-daemon --no-build-cache 2>&1 | grep -aE 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
"$GRADLE" verifyAcTraceabilityMatrix verifyThirdPartyNotices --no-daemon 2>&1 | grep -aE 'BUILD|FAIL' ; exit ${PIPESTATUS[0]}
bash scripts/secret-scan.sh ; echo "secret-scan exit=$?"
```
Expected: 单测 ≥1396 且 0 失败（新增数与报告 §3 对账）；矩阵校验绿；secret-scan exit 0。

- [ ] **Step 2: connected 全清单**（emulator-5576，`adb install -r`，逐类 `am instrument`）

`WordCardRevealFocusTest`、`WordCardPronunciationControlsTest`、`ArticleReadingScreenTest`、`CardDetailScreenTest`、`AppBottomBarTest`、`LearningRecordsScreenTest`、`LicensesScreenTest`、`ReadingHistoryScreenTest`、`AppDatabaseMigrationTest`（全类，含 v31→32）、`KokoroProductionPronunciationTest`（发音链不回归）、`AppScreenTest`（已知 8 flaky 基线对照法判定）。全部结果（OK(n tests)/失败名+定性）写进验证文档。

- [ ] **Step 3: 矩阵 AC5-03 行升级**

把 AC5-03 证据行改写为「代码侧完成」口径（保留 AC id 与列结构；校验器 `AC[0-4]` 正则口径不动）：内容含——四控接口与 UI（Task 7/8/9 测试与提交哈希）、语义批契约（Task 1-5 五个 JVM 契约类名）、触控/字体批提交哈希、connected 清单结果；**设备 TalkBack 走查标注「归 2026-10-15 QA 第三轮任务书，未完成不得宣称 AC5-03 全绿」**。

- [ ] **Step 4: 验证文档 + 报告**

`docs/verification/f5-03-accessibility-hardening-2026-10.md`：三禁令契约、四控语义（含 TTS「已请求」限制、customAction 无自动化断言、Canvas 固定高保留清单）、设备侧 TODO-USER 列表（TalkBack 六链、滑动删除动作可达性、真机暂停长句取证）。
报告按 `docs/handoff/REPORT-TEMPLATE.md` 七节写，§3 记录单测增量与 connected 清单，§5 记深色/token 化/ClickableText 逐词三条重设计轮遗留。

- [ ] **Step 5: 提交 + 推送 + 核验**

```bash
git add docs/verification/ac-traceability-matrix.md docs/verification/f5-03-accessibility-hardening-2026-10.md docs/handoff/2026-10-15-stage-5-f5-03-accessibility-hardening-report.md
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "docs(stage-5): F5-03 收口——矩阵升级、验证文档与工作报告"
git -c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false \
    -c user.name=steveliuyan -c user.email=2021250388@qq.com push origin stage-5-f5-03-accessibility-hardening
git -c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false ls-remote origin stage-5-f5-03-accessibility-hardening
git rev-parse HEAD
```
Expected: 远端 tip == 本地 HEAD；写进报告 §4。是否 FF 合 main 听用户拍板（惯例：验收后合）。

---

## Self-Review 记录

1. **Spec 覆盖**：F5-03 四控=Task 7/8/9；动态字体=Task 11；TalkBack 语义（标签/状态/播报/焦点/动作）=Task 1-6；触控=Task 10；对比度=Task 2/3；文字状态=既有 message 链+Task 9 阅读页补齐；深色/离线文字状态设备面=决策 §1/§6 显式移交（QA 轮+重设计轮），无静默丢弃。
2. **占位符扫描**：Task 8 Step 1 的 VM 测试体以「按现有 VM 测试样板补全」交付（构造参数依文件现状），为本计划唯一允许的执行侧补全点，断言与数据流已在任务约束中钉死；其余代码均逐字。
3. **类型一致性**：`pausePlayback/resumePlayback/stopPlayback`（Provider 层）与 `pauseCurrentPlayback/resumeCurrentPlayback/stopCurrentPlayback`（AudioPlayer 层）命名全程一致；`speechRate: Float` 自 Entity→Domain→VM→State 同名；`PronunciationStatus.Paused` 在 Task 7 定义、Task 9 消费；测试基线口径（1396 只增）全程未破坏。
