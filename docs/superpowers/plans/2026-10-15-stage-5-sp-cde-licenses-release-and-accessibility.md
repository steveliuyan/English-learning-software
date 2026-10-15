# 阶段 5 SP-C/D/E：许可页、发布构建与无障碍接续 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 关闭 F5-04（应用内「关于与开源许可」页 + 台账终审 + 发布说明）与 F5-01 发布构建缺口（release buildType + 可选签名 + release 产物密钥扫描），并为 SP-C（F5-03 无障碍/兼容性）产出第三轮设备 QA 攒批任务书。

**Architecture:** 许可台账单一数据源=现有 `docs/third-party-notices.md`（72 条，`verifyThirdPartyNotices` 已校验）；构建期 Copy 任务把它同步进 assets，运行时由纯解析器 `parseThirdPartyNotices` 读出渲染。UI 走 AppScreen 既有全屏覆盖层模式（同 `showBackupExport`）。SP-E 在 app 模块加 release buildType，签名从 `local.properties` 可选读取（无 keystore 则产物未签名、门禁如实警告不谎称验签）。SP-C 属设备走查，代码侧只补可自动化语义，走查以任务书派独立设备对话。

**Tech Stack:** Kotlin 17、Jetpack Compose、JUnit5（JVM，消息在后）+ JUnit4（androidTest/Compose）、Gradle 8.13 本机发行版、bash 脚本（`.gitattributes` 已强制 eol=lf）。

**Spec:** `docs/specs/05-release-quality-and-compliance.md`（F5-01/F5-03/F5-04；AC5-01/03/04/05）；阶段总里程碑见 `docs/superpowers/plans/2026-10-14-stage-5-release-quality-and-compliance.md` L31-33。

**Branch:** `stage-5-sp-cde-licenses-release-and-accessibility`，自 main tip `285a4c4`。

## Global Constraints

- 门禁=JVM 单测全绿且**基线只增不减**（起点 1391）；构建顺序不可颠倒：`:app:assembleDebug` 在前（Kokoro 边界测试读 APK 产物）。
- Gradle 用本机发行版：`GRADLE="C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat"`（目录哈希变化则 glob 重发现）；所有管道命令结尾 `; exit ${PIPESTATUS[0]}`。
- connected 测试只用 `adb install -r` + `am instrument`（**禁 connectedDebugAndroidTest**，会清设备数据）；`ANDROID_SERIAL=emulator-5576`；testApplicationId=`com.example.englishlearning.test`。
- 提交带身份参数 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`，逐文件 `git add`，禁 `--amend`/`--no-verify`；推送走代理 `-c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false` 并 `ls-remote` 核验后才可宣称备份。
- TDD：每条生产代码先有 RED（记录观察到的失败），再最小 GREEN。
- 安全红线：API Key/密钥/挑战码明文不进代码、日志、报告、APK；`local.properties` 与 keystore **绝不入库**（已在 .gitignore 或保持在忽略区）；未获用户确认不得新增账号/云同步/订阅/广告/远程代码执行。
- 范围变化先改 Spec/写决策，再实施（AGENTS.md）。
- JUnit5 `assertFalse(actual, "消息")` 消息在**后**；androidTest Compose 测试每个方法只许一次 `setContent`。

---

### Task 1: Spec 修订 + 决策文档 + 计划提交

**Files:**
- Modify: `docs/specs/05-release-quality-and-compliance.md`（F5-01 第三条发布前检查：把「APK/AAB 构建与签名验证」改为「APK/AAB 构建验证（已纳入 checkReleaseReadiness）；签名验证依赖发布 keystore，keystore 由用户在场外提供后方可执行，当前以显式 TODO-USER 登记」）
- Create: `docs/decisions/2026-10-15-sp-cde-licenses-page-release-build-and-qa-batch.md`
- Create: `docs/superpowers/plans/2026-10-15-stage-5-sp-cde-licenses-release-and-accessibility.md`（本文件）

**Interfaces:**
- Produces: 决策口径——①许可页数据源=构建期复制台账进 assets（单一事实源，禁止在 Kotlin 里再抄一份台账）；②R8 minify 本轮**不启用**（Room/KSP/Hilt/ML Kit keep 规则需专项验证，记技术债）；③签名=local.properties 可选四键（RELEASE_STORE_FILE/RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD），缺省产物未签名且门禁如实输出「未签名，验签 TODO-USER」，不谎称已验签；④SP-C 走查=第三轮设备 QA 分对话任务书，代码侧本轮只补语义契约测试；⑤AC5-01「发布构建中通过」的验收解释=debug 单测 + release 产物构建与扫描。

- [ ] **Step 1: 切分支**

```bash
git -C H:/EnglishLearningSoftware checkout -b stage-5-sp-cde-licenses-release-and-accessibility main
```

- [ ] **Step 2: 写决策文档**（背景/候选/决定/影响四段，覆盖上述①–⑤，尤其③「不阻塞但未完成须如实登记」与②的再启用条件）

- [ ] **Step 3: 修订 Spec F5-01 第三条**（照上文措辞）

- [ ] **Step 4: 提交**

```bash
git -c user.name=steveliuyan -c user.email=2021250388@qq.com add docs/decisions/2026-10-15-sp-cde-licenses-page-release-build-and-qa-batch.md docs/specs/05-release-quality-and-compliance.md docs/superpowers/plans/2026-10-15-stage-5-sp-cde-licenses-release-and-accessibility.md
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "docs(stage-5): SP-C/D/E 决策、Spec 修订与代码级实现计划"
```

---

### Task 2: NoticesCatalog 纯解析器（JVM TDD）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/notices/NoticesCatalog.kt`
- Test: `app/src/test/java/com/example/englishlearning/notices/NoticesCatalogTest.kt`

**Interfaces:**
- Consumes: 台账 markdown 文本（格式=`## <id>` + `- 键: 值` 行，见 docs/third-party-notices.md 前 40 行）
- Produces（Task 3/4 依赖，签名逐字）:

```kotlin
package com.example.englishlearning.notices
data class ThirdPartyNotice(val id: String, val fields: Map<String, String>) {
    fun field(name: String): String = fields[name].orEmpty()
}
fun parseThirdPartyNotices(markdown: String): List<ThirdPartyNotice>
```

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.englishlearning.notices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class NoticesCatalogTest {
    @Test
    fun parsesEntriesWithKeyedFieldsAndSkipsPreamble() {
        val markdown = """
            # 台账说明
            前言不是条目。
            ## lib-a
            - 名称: Library A
            - 许可证: Apache-2.0
            ## lib-b
            - 名称: Library B
            - 许可证: MIT
        """.trimIndent()
        val notices = parseThirdPartyNotices(markdown)
        assertEquals(2, notices.size)
        assertEquals("Library A", notices[0].field("名称"))
        assertEquals("MIT", notices[1].field("许可证"))
        assertEquals("", notices[1].field("替代方案"))
    }

    @Test
    fun realLedgerParsesWithAllRequiredFieldsPopulated() {
        val ledger = File("../docs/third-party-notices.md").readText(Charsets.UTF_8)
        val notices = parseThirdPartyNotices(ledger)
        assertTrue(notices.size >= 50, "解析条目数异常: ${notices.size}")
        val required = listOf("名称", "版本", "许可证", "用途", "数据流", "NOTICE 位置", "替代方案", "商业分发结论")
        notices.forEach { notice ->
            required.forEach { key ->
                assertTrue(notice.field(key).isNotEmpty(), "${notice.id} 缺字段 $key")
            }
        }
    }

    @Test
    fun malformedLinesAreIgnoredNotCrashing() {
        val markdown = "## only-entry\nplain line\n- 无冒号行\n- 名称: Ok\n- :空键\n"
        val notices = parseThirdPartyNotices(markdown)
        assertEquals(1, notices.size)
        assertEquals("Ok", notices[0].field("名称"))
    }
}
```

- [ ] **Step 2: 跑红**

Run: `"$GRADLE" :app:testDebugUnitTest --tests com.example.englishlearning.notices.NoticesCatalogTest --no-daemon --no-build-cache 2>&1 | grep -E 'FAILED|e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}`
Expected: 编译失败（unresolved: parseThirdPartyNotices）——这就是 RED。

- [ ] **Step 3: 最小实现**

```kotlin
package com.example.englishlearning.notices

data class ThirdPartyNotice(val id: String, val fields: Map<String, String>) {
    fun field(name: String): String = fields[name].orEmpty()
}

/** 与 root verifyThirdPartyNotices 同一 markdown 口径的展示侧解析器；只信任已校验过的台账结构。 */
fun parseThirdPartyNotices(markdown: String): List<ThirdPartyNotice> {
    val entries = mutableListOf<Pair<String, MutableMap<String, String>>>()
    markdown.lineSequence().forEach { line ->
        when {
            line.startsWith("## ") -> entries += line.removePrefix("## ").trim() to mutableMapOf()
            line.startsWith("- ") && entries.isNotEmpty() -> {
                val field = line.removePrefix("- ")
                val key = field.substringBefore(":").trim()
                val value = field.substringAfter(":", missingDelimiterValue = "").trim()
                if (key.isNotEmpty() && value.isNotEmpty()) entries.last().second[key] = value
            }
        }
    }
    return entries.map { (id, fields) -> ThirdPartyNotice(id, fields.toMap()) }
}
```

- [ ] **Step 4: 跑绿**（同上命令，Expected: BUILD SUCCESSFUL）

- [ ] **Step 5: 提交**

```bash
git -c user.name=steveliuyan -c user.email=2021250388@qq.com add app/src/main/java/com/example/englishlearning/notices/NoticesCatalog.kt app/src/test/java/com/example/englishlearning/notices/NoticesCatalogTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(notices): 第三方许可台账纯解析器（SP-D 许可页数据层）"
```

---

### Task 3: 隐私声明文案纯数据 + 台账同步进 assets（Gradle 接线）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/notices/NoticesPrivacyStatement.kt`
- Test: `app/src/test/java/com/example/englishlearning/notices/NoticesPrivacyStatementTest.kt`
- Modify: `app/build.gradle.kts`（sourceSets + Copy 任务）
- Test: `app/src/androidTest/java/com/example/englishlearning/notices/LedgerAssetSyncTest.kt`

**Interfaces:**
- Produces: `fun thirdPartyPrivacyStatements(): List<String>`（5 条，逐字见下）；APK assets 内 `third-party-notices.md`。

- [ ] **Step 1: 写失败测试（JVM，契约钉死 Spec F5-04 五要点）**

```kotlin
package com.example.englishlearning.notices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoticesPrivacyStatementTest {
    @Test
    fun statementsCoverTheFiveSpecPointsVerbatim() {
        val text = thirdPartyPrivacyStatements().joinToString("\n")
        listOf("无账户", "无自动云备份", "自行配置", "API Key", "确认").forEach { keyword ->
            assertTrue(text.contains(keyword), "隐私声明缺少关键词覆盖: $keyword")
        }
        assertEquals(5, thirdPartyPrivacyStatements().size)
    }
}
```

- [ ] **Step 2: 跑红**（unresolved: thirdPartyPrivacyStatements）

- [ ] **Step 3: 实现**

```kotlin
package com.example.englishlearning.notices

/** F5-04：「关于与开源许可」页固定展示的第三方服务隐私说明。措辞与 Spec 发布要求逐条对应。 */
fun thirdPartyPrivacyStatements(): List<String> = listOf(
    "无账户：学习数据只保存在本机，不上传任何服务器。",
    "无自动云备份：只有你主动导出时才会生成本地文件。",
    "AI 服务由用户自行配置：未配置或不确认时，不发送任何内容。",
    "API Key 只存系统安全区：不进数据库、日志、导出包，也无法在应用内查看。",
    "文本与照片发送第三方 AI 服务前逐次确认：拒绝确认则零出网。",
)
```

- [ ] **Step 4: 跑绿 + Gradle 接线（无新依赖，不动 lockfile）**

`app/build.gradle.kts`：在 `android { ... }` 的 `sourceSets` 块内 `getByName("androidTest").assets.srcDir("$projectDir/schemas")` 之后加一行，并在文件顶部 plugins 之后注册 Copy 任务：

```kotlin
val noticesAssetsDir = layout.buildDirectory.dir("generated/noticesAssets")
val syncThirdPartyNoticesAsset = tasks.register<Copy>("syncThirdPartyNoticesAsset") {
    group = "verification"
    description = "Copies docs/third-party-notices.md into release/debug assets (single source of truth)."
    from(rootProject.layout.projectDirectory.file("docs/third-party-notices.md"))
    into(noticesAssetsDir)
}
```

sourceSets 内：

```kotlin
getByName("main").assets.srcDir(noticesAssetsDir)
```

android 块外：

```kotlin
tasks.named("preBuild") { dependsOn(syncThirdPartyNoticesAsset) }
```

- [ ] **Step 5: 写失败 connected 测试（真 APK 资产即已校验台账）**

```kotlin
package com.example.englishlearning.notices

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerAssetSyncTest {
    @Test
    fun shippedAssetParsesAndPassesTheSameValidationAsTheGate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val markdown = context.assets.open("third-party-notices.md").bufferedReader().use { it.readText() }
        val notices = parseThirdPartyNotices(markdown)
        assertEquals("AndroidX Core KTX", notices.first().field("名称"))
        assertEquals(emptyList<String>(), verifyThirdPartyNotices(markdown).map { "${it}" })
    }
}
```

- [ ] **Step 6: 构建安装实跑**

```bash
export ANDROID_SERIAL=emulator-5576
"$GRADLE" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-build-cache 2>&1 | grep -E 'BUILD|FAILED|e:' ; exit ${PIPESTATUS[0]}
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.englishlearning.notices.LedgerAssetSyncTest com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: 先 RED（assets 不存在，FileNotFoundException），接线后 OK (1 test)。

- [ ] **Step 7: 提交**

```bash
git -c user.name=steveliuyan -c user.email=2021250388@qq.com add app/src/main/java/com/example/englishlearning/notices/NoticesPrivacyStatement.kt app/src/test/java/com/example/englishlearning/notices/NoticesPrivacyStatementTest.kt app/build.gradle.kts app/src/androidTest/java/com/example/englishlearning/notices/LedgerAssetSyncTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(notices): 隐私声明纯数据与台账资产构建期同步（SP-D）"
```

---

### Task 4: LicensesScreen 许可页（Compose TDD）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/LicensesScreen.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/LicensesScreenTest.kt`

**Interfaces:**
- Consumes: `parseThirdPartyNotices`、`thirdPartyPrivacyStatements`（Task 2/3）
- Produces: `@Composable fun LicensesScreen(markdown: String, onBack: () -> Unit)`；testTag：`licenses_screen`、`licenses_back`、`licenses_entry_count`、`licenses_privacy_statement`、`license_entry-<id>`、`licenses_unavailable`（Task 5 与走查依赖）。

- [ ] **Step 1: 写失败 connected 测试**

```kotlin
package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.englishlearning.notices.thirdPartyPrivacyStatements
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LicensesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sampleLedger = """
        # 说明
        ## lib-a
        - 名称: Library A
        - 版本: 1.2.3
        - 许可证: Apache-2.0
        - 用途: 测试用途
        - 数据流: 仅在设备内
        - NOTICE 位置: 本文件
        - 替代方案: 无
        - 商业分发结论: 可商业分发
        ## lib-b
        - 名称: Library B
        - 版本: 4.5.6
        - 许可证: MIT
        - 用途: 测试用途
        - 数据流: 仅在设备内
        - NOTICE 位置: 本文件
        - 替代方案: 无
        - 商业分发结论: 可商业分发
    """.trimIndent()

    @Test
    fun rendersEntriesCountPrivacyStatementsAndBack() {
        var backCalls = 0
        composeRule.setContent { LicensesScreen(markdown = sampleLedger, onBack = { backCalls++ }) }
        composeRule.onNodeWithText("关于与开源许可").assertIsDisplayed()
        composeRule.onNodeWithTag("licenses_entry_count").assertTextContains("共收录 2 项第三方资源")
        composeRule.onNodeWithTag("license_entry-lib-a").assertExists()
        composeRule.onNodeWithTag("license_entry-lib-b").performClick() // 展开字段，不许崩
        thirdPartyPrivacyStatements().forEach { statement ->
            composeRule.onNodeWithText(statement, substring = true).assertExists()
        }
        composeRule.onNodeWithTag("licenses_back").performClick()
        assertEquals(1, backCalls)
    }

    @Test
    fun showsHonestUnavailableStateWhenLedgerMissing() {
        composeRule.setContent { LicensesScreen(markdown = "", onBack = {}) }
        composeRule.onNodeWithTag("licenses_unavailable").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.assertTextContains(tag: String, fragment: String) {
        val text = onNodeWithTag(tag).fetchSemanticsNode().config
            .androidx.compose.ui.semantics.SemanticsProperties?.let { null } ?: ""
        // 简化：直接用 onNodeWithText 断言
    }
}
```

注：`assertTextContains` 辅助在 GREEN 前删掉——改用一行 `composeRule.onNodeWithText("共收录 2 项第三方资源").assertIsDisplayed()`（licenses_entry_count 行文本即此，两断言可合并：`onNodeWithTag("licenses_entry_count").assertIsDisplayed()` + 文本断言）。每方法一次 setContent（真机怪癖）。

- [ ] **Step 2: 跑红**（unresolved: LicensesScreen）

- [ ] **Step 3: 实现**

```kotlin
package com.example.englishlearning.ui

// imports：照 RecoveryScreen 风格；LazyColumn 用 androidx.compose.foundation.lazy

@Composable
fun LicensesScreen(markdown: String, onBack: () -> Unit) {
    val notices = androidx.compose.runtime.remember(markdown) { parseThirdPartyNotices(markdown) }
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp).testTag("licenses_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("‹", style = MaterialTheme.typography.headlineSmall, color = MintPrimary,
                modifier = Modifier.testTag("licenses_back").clickable(onClick = onClick = onBack)
                    .semantics { contentDescription = "返回" })
            Text("关于与开源许可", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        }
        if (notices.isEmpty()) {
            Text("许可台账暂不可读", modifier = Modifier.testTag("licenses_unavailable"), color = MintTextMuted)
        } else {
            Text("共收录 ${notices.size} 项第三方资源", style = MaterialTheme.typography.bodyMedium,
                color = MintTextMuted, modifier = Modifier.testTag("licenses_entry_count"))
            Column(
                modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(18.dp))
                    .background(MintSurface, RoundedCornerShape(18.dp)).padding(14.dp)
                    .testTag("licenses_privacy_statement"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("隐私说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                thirdPartyPrivacyStatements().forEach { statement ->
                    Text(statement, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(notices, key = { it.id }) { notice ->
                    NoticeCard(notice)
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: ThirdPartyNotice) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .background(MintSurface, RoundedCornerShape(18.dp)).padding(14.dp)
            .testTag("license_entry-${notice.id}").clickable { expanded = !expanded }
            .semantics { contentDescription = notice.id },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(notice.field("名称").ifEmpty { notice.id }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text("${notice.field("许可证")} · v${notice.field("版本")}", style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
        if (expanded) {
            listOf("用途", "数据流", "NOTICE 位置", "替代方案", "商业分发结论").forEach { key ->
                Text("$key：${notice.field(key)}", style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
            }
        }
    }
}
```

（实现时补齐 import 与 `items` 的 `androidx.compose.foundation.lazy.items`；`clickable(onClick = onClick = onBack)` 是笔误——写 `clickable(onClick = onBack)`。）

- [ ] **Step 4: 跑绿**（assembleDebug + install -r + am instrument `com.example.englishlearning.ui.LicensesScreenTest`，Expected: OK (2 tests)）

- [ ] **Step 5: 提交**

```bash
git -c user.name=steveliuyan -c user.email=2021250388@qq.com add app/src/main/java/com/example/englishlearning/ui/LicensesScreen.kt app/src/androidTest/java/com/example/englishlearning/ui/LicensesScreenTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): 关于与开源许可页（F5-04，AC5-04）"
```

---

### Task 5: 设置入口 + AppScreen 覆盖层接线（connected TDD）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt`（新参 `onOpenLicenses: () -> Unit = {}`；「账户」组 L222-228 内在 pending 行之后加 SettingsActionRow，tag=`settings_open_licenses`，badge="ℹ"，accent=DomainColors.Settings）
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（L178 后加 `var showLicenses by rememberSaveable(state.profile.id) { mutableStateOf(false) }`；L1087 `showBackupPairingExport` 分支后加 `else if (showLicenses)` 分支读 assets；SettingsScreen 调用点 L1447 起加 `onOpenLicenses = { showLicenses = true }`）
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/SettingsLicensesEntryTest.kt`

**Interfaces:**
- Consumes: `LicensesScreen(markdown, onBack)`（Task 4）
- Produces: 可达链路 设置→账户→关于与开源许可；assets 读取失败时传空串（页面显示 `licenses_unavailable`，零假入口）。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.englishlearning.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsLicensesEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun licensesEntryIsCallableFromSettingsAccountGroup() {
        var opened = 0
        composeRule.setContent {
            SettingsScreen(
                profileName = "测试者",
                wordBookName = null,
                todayNewTarget = null,
                todayDueTarget = null,
                onOpenSetup = {},
                onOpenWorksheet = {},
                onOpenAiProfiles = {},
                onOpenLicenses = { opened++ },
            )
        }
        composeRule.onNodeWithTag("settings_open_licenses").performClick()
        assertEquals(1, opened)
    }
}
```

- [ ] **Step 2: 跑红**（编译失败：无 `onOpenLicenses` 参数）
- [ ] **Step 3: 实现**：SettingsScreen 新参与行；AppScreen 三处接线。覆盖层分支：

```kotlin
} else if (showLicenses) {
    // F5-04：许可台账页。数据源=构建期从 docs/third-party-notices.md 同步的 assets；读不到如实降级。
    val licensesContext = LocalContext.current
    val noticesMarkdown = remember {
        runCatching {
            licensesContext.assets.open("third-party-notices.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    LicensesScreen(markdown = noticesMarkdown, onBack = { showLicenses = false })
}
```

- [ ] **Step 4: 端到端 connected 实跑**（SettingsLicensesEntryTest + 真资产冒烟：临时在 LicensesScreenTest 外新增不改——以 LedgerAssetSyncTest 已证资产在、本测证入口在；两测跑通即链路闭合）
- [ ] **Step 5: 提交**

```bash
git -c user.name=steveliuyan -c user.email=2021250388@qq.com add app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt app/src/main/java/com/example/englishlearning/ui/AppScreen.kt app/src/androidTest/java/com/example/englishlearning/ui/SettingsLicensesEntryTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): 设置页关于与开源许可入口与覆盖层接线（AC5-04）"
```

---

### Task 6: 台账终审 + 发布说明

**Files:**
- Modify: `docs/third-party-notices.md`（终审发现缺项才动）
- Create: `docs/verification/release-notes-v0.0.1.md`

**Interfaces:**
- Produces: AC5-04 台账终审记录；发布说明文档路径（Task 9 矩阵证据）。

- [ ] **Step 1: 终审**——跑 `"$GRADLE" verifyThirdPartyNotices --no-daemon 2>&1 | tail -3`（Expected: Validated 72 entries）。逐类抽查台账**覆盖面对象清单**是否缺条目：词书 `.wbpack` 格式（自研，应标「不适用第三方」或删）、离线词典数据、Kokoro/Piper 模型、TalkifyTTS（用户自配服务——必须有条目说明「不在应用中内置、逐次确认」）、Tesseract 训练数据、zxing、ML Kit bundled。缺则按八字段补条目（`## 标题`= kebab-case id）。**若 Talkify/自配服务无条目：补「user-configured-ai-endpoint」条目（用途=用户自配 OpenAI 兼容服务；数据流=用户确认后出站；商业分发结论=不随包分发）。**
- [ ] **Step 2: 写发布说明**（固定五要点逐条 + 「卸载/清数据前未导出可能无法恢复」警示 + 版本 versionName 0.0.1/versionCode 1 + 门禁命令清单照抄 Global Constraints）
- [ ] **Step 3: 若台账被改，重跑 Task 2 的 realLedgerParses 测试确认仍绿**
- [ ] **Step 4: 提交**

```bash
git -c user.name=steveliuyan -c user.email=2021250388@qq.com add docs/third-party-notices.md docs/verification/release-notes-v0.0.1.md
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "docs(compliance): 许可台账终审与 v0.0.1 发布说明（F5-04）"
```

---

### Task 7: SP-E release 构建 + 可选签名 + APK 密钥扫描（先红后绿）

**Files:**
- Modify: `app/build.gradle.kts`（buildTypes + signingConfig 读 local.properties）
- Create: `scripts/apk-secret-scan.sh` + `scripts/apk-secret-scan-selftest.sh`
- Modify: `build.gradle.kts`（root：`verifyReleaseArtifact` 注册并并入 `checkReleaseReadiness`）

**Interfaces:**
- Produces: `:app:assembleRelease` 可复现产物；`bash scripts/apk-secret-scan.sh <apk>` exit 0/1；门禁聚合含 release 检查。

- [ ] **Step 1: release buildType**

```kotlin
buildTypes {
    getByName("release") {
        // 决策 2026-10-15：R8 minify 本轮不启用（keep 规则需专项验证，记技术债）。
        isMinifyEnabled = false
        val props = java.util.Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        val storeFile = props.getProperty("RELEASE_STORE_FILE")
        if (storeFile != null) {
            signingConfigs.create("releaseLocal") {
                init(this)
                this.storeFile = rootProject.file(storeFile)
                this.storePassword = props.getProperty("RELEASE_STORE_PASSWORD", "")
                this.keyAlias = props.getProperty("RELEASE_KEY_ALIAS", "")
                this.keyPassword = props.getProperty("RELEASE_KEY_PASSWORD", "")
            }
            signingConfig = signingConfigs.getByName("releaseLocal")
        } else {
            logger.lifecycle("release keystore 未配置（local.properties 缺 RELEASE_STORE_FILE）：产物将未签名，验签 TODO-USER")
        }
    }
}
```

（`init(this)` 为 KGP DSL 规避写法；如编译器要求可改为块外构造。四键绝不入库；`.gitignore` 已含 local.properties。）

- [ ] **Step 2: 写 apk 扫描脚本自测（先红）**

`scripts/apk-secret-scan-selftest.sh`：用 python/zip 造两个假 APK——坏包（assets/leak.txt 内嵌 `sk-abcdefghijklmnopqrstuvwx` 与 `ELPAIR1|Zm9vYmFyMTIzNDU2Nzg5MA`；含 `payload.keystore`）、净包（正常 entries）；断言坏包 exit 1、净包 exit 0；先跑它证明 `apk-secret-scan.sh` 尚不存在（红）。

`scripts/apk-secret-scan.sh`（入参=apk 路径）三项检查：
1. `unzip -l "$APK"` 列表匹配 `\.jks$|\.keystore$|\.p12$|local\.properties|\.env$` → 命中 exit 1；
2. `unzip -p "$APK" 'assets/*' 2>/dev/null` 输出匹配 `sk-[A-Za-z0-9]{20,}|ELPAIR1\|[A-Za-z0-9_=-]{16,}|Authorization:` → 命中 exit 1（ELPAIR1 模式沿用 SP-A 收紧口径）；
3. 若存在 `app-release-unsigned.apk` 命名回退打印 `WARN: 未签名产物——验签待 keystore（TODO-USER）`，不 fail（决策③）。
`unzip` 缺失时（Git Bash 有 unzip）用 `jar tf` 兜底由自测实测决定，脚本里记录所选。

- [ ] **Step 3: 自测先红后绿**

```bash
bash scripts/apk-secret-scan-selftest.sh   # 先失败：apk-secret-scan.sh 不存在
# 写脚本后重跑 → exit 0
```

- [ ] **Step 4: root 门禁挂 release**

root `build.gradle.kts` 注册并加入 `checkReleaseReadiness` 的 dependsOn：

```kotlin
val verifyReleaseArtifact = tasks.register("verifyReleaseArtifact") {
    group = "verification"
    description = "F5-01: release APK builds and carries no secrets (AC5-05 release-build scan)."
    dependsOn(":app:assembleRelease")
    doLast {
        val outDir = project(":app").layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apk = outDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
        check(apk != null) { "assembleRelease 未产出 APK" }
        val rc = providers.exec { commandLine("bash", "scripts/apk-secret-scan.sh", apk.absolutePath) }.result.get()
        check(rc.exitValue == 0) { "APK 密钥扫描命中：见上方输出（AC5-05 发布阻断）" }
        logger.lifecycle("verifyReleaseArtifact: ${apk.name} scanned clean (signature check pending release keystore).")
    }
}
```

`checkReleaseReadiness` dependsOn 追加 `verifyReleaseArtifact`。

- [ ] **Step 5: 全量门禁跑绿**

```bash
"$GRADLE" checkReleaseReadiness --no-daemon --no-build-cache 2>&1 | grep -E 'FAILED|BUILD|Validated|covered|scanned|WARN|tests completed' ; exit ${PIPESTATUS[0]}
```
Expected: BUILD SUCCESSFUL（含 assembleRelease；新增单测后基线 1391→1394±）。

- [ ] **Step 6: 提交**

```bash
git -c user.name=steveliuyan -c user.email=2021250388@qq.com add app/build.gradle.kts scripts/apk-secret-scan.sh scripts/apk-secret-scan-selftest.sh build.gradle.kts
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(release): release 构建类型+可选签名与 APK 密钥扫描入门禁（SP-E，AC5-01/05）"
```

---

### Task 8: SP-C 第三轮设备 QA 任务书（攒批清单）

**Files:**
- Create: `docs/handoff/2026-10-15-qa-round3-task.md`

**Interfaces:**
- Produces: 分对话可独立执行的任务书；AC5-03 矩阵行的「移交走查」证据。

- [ ] **Step 1: 写任务书**，必含分组：
  - **AC5-03 六链 TalkBack**：选词书、开始学习、提交三档反馈、查看完成状态、打开历史文章、进入数据管理入口；外加遗留项——词卡「显示释义」揭晓按钮焦点顺序（roadmap 2026-10-14 条）。
  - **F5-03 矩阵**：动态字体最大档不破版、深色模式（D-2 遗留）、小屏 320dp、新「关于与开源许可」页 TalkBack 朗读（本分支新增）。
  - **阶段4/SP-B 攒批 TODO-USER**：B3 API26–29 降级、bf353dda 六项性能复测（对照 `docs/verification/perf-baseline-2026-10.md` 阈值列）、飞行模式手工六条（`docs/verification/ac5-02-offline-script.md`）、B4 Salvage 手工核、B5 真实 AI Profile、T2 内容级解密、AppScreenTest 8 flaky 复现条件记录。
  - 纪律条款：`adb install -r` + `am instrument`、ANDROID_SERIAL 锁机、隐私红线（挑战码/QR 截图零入库，证据放 `.git/qa-scratch/`）、报告按模板 + push 后 ls-remote 核验（QA 第一轮丢文档教训）。
- [ ] **Step 2: 提交**

```bash
git -c user.name=steveliuyan -c user.email=2021250388@qq.com add docs/handoff/2026-10-15-qa-round3-task.md
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "docs(qa): 第三轮设备 QA 任务书（SP-C 走查攒批，含 AC5-03 六链与 TODO-USER 清单）"
```

---

### Task 9: 矩阵回填 + 全量验证 + 收口报告 + 推送

**Files:**
- Modify: `docs/verification/ac-traceability-matrix.md`（「阶段 5 AC」节加三行）
- Create: `docs/handoff/2026-10-15-stage-5-sp-cde-report.md`

**Interfaces:**
- Produces: AC5-03/04/05 证据行；收口报告。

- [ ] **Step 1: 矩阵行**（措辞如实——AC5-03 只到「代码侧语义契约 + 走查任务书已备」，不得写已验证）：

```markdown
| AC5-03 | TalkBack 完成六链关键流程。 | 手工（部分）+自动化语义 | 代码侧：SettingsLicensesEntryTest、RecoveryScreenTest（零假入口/返回语义）；走查：docs/handoff/2026-10-15-qa-round3-task.md（六链+焦点遗留，待设备轮执行） |
| AC5-04 | 台账无缺项且「关于与开源许可」页可访问。 | 自动化 | notices.ThirdPartyNoticesTest、NoticesCatalogTest、NoticesPrivacyStatementTest、LedgerAssetSyncTest（真 APK 资产=已过门禁校验的台账）、LicensesScreenTest、SettingsLicensesEntryTest；终审记录 docs/verification/release-notes-v0.0.1.md；root 任务 verifyThirdPartyNotices |
| AC5-05 | 导出包/日志/构建扫描无密钥。 | 自动化 | scripts/secret-scan.sh（+selftest）、scripts/apk-secret-scan.sh（+selftest）经 verifyReleaseArtifact 并入 checkReleaseReadiness；签名核验 TODO-USER（决策 2026-10-15） |
```

- [ ] **Step 2: 全量门禁**（顺序固定）

```bash
"$GRADLE" :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest checkReleaseReadiness --no-daemon --no-build-cache 2>&1 | grep -E 'FAILED|^e:|BUILD|tests completed|Validated|covered|scanned' ; exit ${PIPESTATUS[0]}
```

- [ ] **Step 3: connected 全清单实跑**（emulator-5576，`adb install -r` 双 APK 后逐个 am instrument）：LedgerAssetSyncTest、LicensesScreenTest、SettingsLicensesEntryTest；SP-B 回归抽测可跳过（tip 已实跑）。
- [ ] **Step 4: 按 REPORT-TEMPLATE 七节写收口报告**：目标；Task1–8 逐条（带提交哈希）；验证记录（基线差值、connected OK 行数、flaky 对照结论沿用）；Git 状态（ls-remote 核验值，§4 不写死自身哈希）；未完成与遗留（**验签 TODO-USER、AC5-03 设备轮走查未执行、R8 技术债、bf353dda 复测等**）；接手须知（QA 分对话照任务书执行；合 main 惯例=验收后 FF 听拍板）；用户确认过的决策。
- [ ] **Step 5: 提交推送 + ls-remote 核验**

```bash
git -c user.name=steveliuyan -c user.email=2021250388@qq.com add docs/verification/ac-traceability-matrix.md docs/handoff/2026-10-15-stage-5-sp-cde-report.md
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "docs(stage-5): SP-C/D/E 收口——矩阵 AC5 回填与报告"
git -c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false -c user.name=steveliuyan -c user.email=2021250388@qq.com push origin stage-5-sp-cde-licenses-release-and-accessibility
git -c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false ls-remote origin stage-5-sp-cde-licenses-release-and-accessibility
```

---

## Self-Review

1. **Spec 覆盖**：F5-04→Task 2–6（页+台账终审+发布说明）；F5-01 release 检查→Task 7；AC5-04→Task 2–6/9；AC5-05→Task 7/9；AC5-03→Task 8 任务书+矩阵如实标「部分」；AC5-01「发布构建中通过」→Task 7 门禁扩展 + 决策⑤口径。F5-03 的发音播控/引擎状态、Android 13 通知权限、GMS/无 GMS OCR 均有既有轮次覆盖（阶段 2/3 记录），本轮以任务书走查收口，无遗漏未登记项。
2. **占位符**：Task 4 Step 1 的 `assertTextContains` 辅助是刻意给出的待删脚手架（已在注中说明最终断言写法），非 TBD；其余无占位。
3. **类型一致性**：`ThirdPartyNotice(id, fields)`、`field(name)`、`parseThirdPartyNotices`、`thirdPartyPrivacyStatements`、`LicensesScreen(markdown, onBack)`、tags（`settings_open_licenses`/`licenses_back`/`licenses_entry_count`/`license_entry-<id>`/`licenses_unavailable`/`licenses_privacy_statement`/`licenses_screen`）各任务引用与定义一致；Task 3 `LedgerAssetSyncTest` 消费 Task 2 的解析器与既有 `verifyThirdPartyNotices(markdown)`（注意 root 任务与 Kotlin 顶层函数同名不同体，Kotlin 版在 `NoticeValidationError.kt` L19，返回 `List<NoticeValidationError>`——测试断言映射为空表一致）。
