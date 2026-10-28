# 交接文档：全应用 UI 重做 + 手机效果稿（2026-11-03）

> 用途：上一个对话上下文过长，交接给新会话 / 另一个 AI。本文只写「事实 + 判决 + 下一步」，不复述推理过程。
> 读法：先读 §1 现状与 §4 在办任务，§2/§6 是环境与令牌速查，§7 是红线（踩了会毁数据）。

---

## 1. 一句话现状

App 侧 **33 组界面版式重做已全部收官、门禁全绿**（配色 + 图标 + 版式五批 + 遗留收口）。
当前**唯一在办的事是「出一份给人看的效果稿 HTML」**：把 37 个屏 + 12 个弹窗按手机画框排好版，供另一个「主力开发 AI」照着实现。
**该效果稿至今一个字节都没写**，只有计划。

两个必须分清的文件：

| 文件 | 是什么 | 状态 |
|---|---|---|
| `docs/design/2026-11-03-full-app-ui-redesign-reference.html` | **规格稿**（3266 行 / 224KB）：ASCII 分层条 + 规格表 + 状态 + 改动说明 | ✅ 已落盘 |
| `docs/design/2026-11-03-full-app-ui-phone-gallery.html` | **效果稿**（本任务待交付物）：纯视觉手机画框 | ❌ 不存在，待创建 |

---

## 2. 项目与环境

- 工程：`H:\EnglishLearningSoftware`，Kotlin + Jetpack Compose 原生 Android 英语学习 App。**不是 HTML 项目**，`index.html` 不存在。
- 构建：`compileSdk=36` / `minSdk=26` / `targetSdk=36`，compose BOM `2025.12.00`。
- **Gradle 必须用本机 8.13**（禁用 `./gradlew`、禁用 9.x）：
  ```
  C:\Users\steveliuyan\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat
  ```
- 单测：`gradle.bat :app:testDebugUnitTest --tests "<FQCN>" --offline --no-daemon --no-build-cache -q`，结果读 `app\build\test-results\testDebugUnitTest\*.xml`。
- 设备：真机 `bf353dda` / `M2102J2SC`（Android 13 / SDK 33，1080×2340，density 440，已锁竖屏）；模拟器 `emulator-5574`（SDK 30）。
- 真机启动（`am start` 报 not exported，必须用 monkey）：
  ```
  adb -s bf353dda shell monkey -p com.example.englishlearning -c android.intent.category.LAUNCHER 1
  ```

---

## 3. 已完成（可直接引用，无需重做）

### 3.1 配色
- 轻松档配色全量落地：`AppColors.kt`、`AppPalette.kt`、`IconBadge.kt`、`res/values/themes.xml`（`windowBackground #F6F9F6`、`colorAccent #1A7A63`）、`res/values/colors.xml`（`splash_background #F6F9F6`）。
- 强调层 `accentPaths` 已真正参与渲染（`AppIcon.kt` / `IconBadge.kt`）。

### 3.2 图标体系
- 28 枚位图插画 256×256 → `res/drawable-xhdpi/app_icon_*.png`（972KB），经 `AppIllustrations.kt` 映射；`IconBadge.kt` 有 `illustrated` 分支；`EntryIconBadge` / `InlineIconBadge`(30dp+backdrop) 双档。
- 33 枚三层矢量（`fillPaths` 浅 0.42 / 深 0.30 + `accentPaths` + `strokePaths` 2px），`GRADIENT_START_FRACTION = 0.18`，5 枚 `StrokeOnly`（`Back`/`ChevronRight`/`ChevronDown`/`Close`/`Check`）。
- 母版态：`docs/design/icon-master/`（1024 母版、`STYLE-CONTRACT.md`、`README.md`、`audit/` 4 脚本 + JSON、总览图 7×4 1916×1188）、评审页 `2026-10-28-icon-master-sheet.html`；审计 28/28 通过。
- 强调层色：浅套 = 域 `deep` 压暗 22%；深套 = `onBrand`（D6 结案 A，对比度无解后的定案）。

### 3.3 版式五批 + 遗留
- Batch 1 骨架 9 Task / Batch 2 学习链路 7 Task / Batch 3 阅读链路 5 屏 / Batch 4 AI 链路 7 Task / Batch 5 设置与系统 10 Task —— **全部 ✅**。
- 遗留 1（`AppScreen.kt:2661` `DetailToggleRow`、`WorksheetSettingsScreen.kt:398` `OptionSwitchRow` 改 `if (LocalIsDark.current) domain.base else colors.strongSurface`）、遗留 7（吸底槽补 `hairlineTop(colors.outline)`）、遗留 8（底导定名「AI 助学」）—— **已收口**。
- 两条全局闸测试：`SwitchTrackColorGuardTest`、`StickyFooterHairlineGuardTest`。

### 3.4 门禁与取证（上次实测）
- 全量 JVM：**278 XML / 2043 tests / 0 failures / 0 errors**。
- 产物：`app-debug.apk`(344MB) + `app-debug-androidTest.apk`(2.9MB)。
- 真机 `am instrument` 全绿：`AppBottomBarTest` 9、`AiLearningScreenTest` 18、`PhotoTranslationScreenTest` 8、`SentenceAnalysisScreenTest` 9、`AiProfileSettingsScreenTest` 24、`AppScreenTest#readyPlan…` 1。
- 截图：`docs/design/app-tab-label-ai-zhuxue-{light,dark}-20261102.png`、`app-ai-home-hero-zhuxue-light-20261102.png`、`app-today-plan-strongcard-light-20261031.png`、`app-profile-statcard-bottombar-light-20261031.png`。
- 收口报告：`docs/handoff/2026-11-02-layout-redesign-batch-5-report.md`（§8 遗留 / §9 门禁 / §10 取证 / §11 判据）。

---

## 4. 在办任务：手机效果稿 HTML

### 4.1 目标
一份**纯看效果**的手机画框页面，给另一个 AI 开发当视觉参照。**本任务只写 HTML，不改 Kotlin、不动测试、不跑真机。**

### 4.2 交付路径
`docs/design/2026-11-03-full-app-ui-phone-gallery.html`（新建，**不要改** `...-reference.html`）

### 4.3 复用与实测结论（重要修正）
- 规格稿 `...-reference.html` 实测：3266 行 / 224KB / 13 个 `<section>`；`:root` ✅、`.phone`（296×632）✅、`.strip`（ASCII 分层条）✅。
- ⚠️ **`.p-*` 原子件并不存在**（`class="p-"` 计数 = 0）。旧记忆里「有 `.p-*` 可复用」是错的，效果稿需要自己补画框内部样式。
- 视觉语法可直接抄：`2026-10-17-ui-redesign-mockups.html`(125KB)、`2026-10-25-icon-system-design-sheet.html`(98KB)、`2026-10-28-icon-master-sheet.html`(27KB)。

### 4.4 页面结构（计划已定）
1. 封面窄条 + 吸顶控制条：分组过滤（全部 / 学习 15 / 阅读 5 / AI 7 / 我的 10 / 宿主 1 / 弹窗 12）+ 浅套·深套开关 + 屏名搜索框。
2. 每组小标题 + 一句话视觉基调。
3. 手机画框网格（3–4 列），caption 格式「编号 · 屏名 · `XxxScreen.kt`」。
4. 画框内部按真实结构排：状态栏 → `AppScreenHeader` → 内容 → 底导（按屏高亮，第三格「AI 助学」）。
5. **共 49 个画框** = 37 屏 + 12 弹窗；弹窗**压在所属屏上**（底图 + 遮罩叠放），不单独成页。
6. 末段：四处缺陷「改前 / 改后」并排对照（见 §5）。
7. 打印样式 `break-inside: avoid`，保证分页不切框。

### 4.5 屏清单（分组）
35 个 `*Screen.kt` + `AppScreen.kt` 内并屏（`LearningSetupScreen` / `WordBookPicker` 等）= 37 屏级 composable，另加宿主壳 `AppScreen` 与 12 处弹窗。

- **L1–L15 学习链路（15）**：TodayPlan、LearningSetup、WordCard、WordCard 揭晓态、CardDetail、WordBookPicker、WordBank、WordBookWords、MyWords、GlobalVocabularySearch、VocabularyRelearn、LearningRecords、CheckIn、WorksheetSettings、WorksheetPreview
- **R1–R5 阅读链路（5）**：ReadingAccess、ReadingFeed、ArticleReading、ArticleImport、ReadingHistory
- **A1–A7 AI 链路（7）**：AiLearning、AiFeature、PhotoTranslation、SentenceAnalysis、WordAiQa、ImageStudio、AiProfileSettings
- **M1–M10 我的与系统（10）**：Profile、Settings、SpeechSettings、ReminderSettings、BackupHub、BackupExport、BackupRestore、BackupPairingExport、Licenses、Recovery
- **H 宿主（1）**：`AppScreen`（含底导与吸底槽）
- **D1–D12 弹窗/浮层（12）**：含 `AppScreen.kt:450/738/1260/1280…` 各站点 + `WorksheetSettings` 默写纸选择 + AI 配置/密钥 + 阅读请求 + 备份确认 + 删除确认等

### 4.6 写入方式（必须分块）
单次 `write` 内容过长会 JSON 解析失败。已验证有效的做法：
1. 先写骨架，埋 `<!-- PART1 -->` … `<!-- PART6 -->` 占位；
2. 逐段 `edit` 替换占位。

分块内容：
- PART1：外壳 + 全量 CSS（画框、网格、吸顶条、深浅套变量）+ 封面 + 令牌/图标一览
- PART2：学习 15 屏
- PART3：阅读 5 + AI 7
- PART4：我的 10 + 宿主 1
- PART5：弹窗 12
- PART6：四处缺陷改前/改后对照 + 页脚

### 4.7 完工自检
- 画框数 = 49，各分组数与前表一致；
- 深套开关全框生效（无漏改硬编码色）；
- 打印预览不切框；
- 交付路径 + 打开方式。

---

## 5. 用户点名的四处缺陷（根因 + 处方，均已核源码）

### ① 主页进度条 / 百分比圆环过深
- 根因：`TodayPlanScreen.kt:238 / 260 / 268` 把 `ink = LocalContentColor.current` 传下去，`StrongCard` 浅套里 `text1 = #1F2B26`，于是在 `tint2 = #E4EFE9` 底上糊成一片深色。
- 处方：改用 `AppDomain` 的 `base → deep` 渐变；轨道降到 `base @12%`。

### ② 词库版式不统一
- 根因：`WordBankScreen.kt` 只是壳，`bookPane` 里接 `WordBookWordsScreen.kt` —— 用 M3 `Card` + `BorderStroke(1.dp, MintOutline)` + 卡内 16dp，而这批卡铺在已缩进 20 的列表里；且 `start=20 / end=36` 不对称（36 是历史字母槽让位）。另三段（`MyWordsScreen` 等）是裸铺 `WordRow`。
- 处方：统一为裸铺 + 两侧 16 对称；`FilterChip` 换 `PillButton(Secondary)`；壳内提供「列表头槽」。

### ③ 搜索框不连贯
- 根因：主页 `TodayPlanScreen.kt:350-361` 是**假框**（`tint` 底 / 圆角 14 / 18dp 描边放大镜），`GlobalVocabularySearchScreen.kt:118` 是**真 M3 `TextField`**，两套几何。
- 处方：抽共享 `SearchEntry` —— 假框 = 导航入口、真框 = 输入态，**同几何**。

### ④ 发音药片错开
- 根因：`CardDetailScreen.kt:304` 用 `FlowRow`（`itemVerticalAlignment = CenterVertically`、`spacedBy 8.dp`），把 IPA + 美音 + 英音挤成可折行流，宽度一不够就断行。
- 处方：IPA 独占一行；下方美音 / 英音**等分两列并排药片**；2.0 字号下退化为上下两行。

---

## 6. 令牌与组件权威值速查

### 6.1 色板 —— 浅套
`bg #F6F9F6`、`surface #FFFFFF`、`tint #E9F4EE`、`tint2 #E4EFE9`、`tint3 #DEEAE4`、`outline #D3E7DC`、`text1 #1F2B26`、`text2 #566B62`、`text3 #7C9188`、`onMint #174C44`、`onBrand #04241C`、`strongSurface #1F4739`、`onStrong #E9F2EC`

### 6.2 色板 —— 深套
`bg #111A16`、`surface #17201C`、`tint #1B2A25`、`tint3 #27392F`、`strongSurface #22332C`

### 6.3 七域色板（base / light / deep，`AppColors.kt:104-115`）
| 域 | base | light | deep |
|---|---|---|---|
| Learn | `#7BB4E6` | `#CDE6F7` | `#175A93` |
| Library | `#B7A0D9` | `#E4DAF2` | `#65478A` |
| Review | `#EBC68B` | `#F7E7CB` | `#8A5712` |
| Settings | `#A6B0AC` | `#DCE4E0` | `#4A5551` |
| AiSpeech | `#6CCBAB` | `#CFEDE2` | `#1A7A63` |
| Reading | `#E297A4` | `#F6DCE0` | `#9C3B4F` |
| Danger | `#DD8B81` | `#F7DCD6` | `#9E2B24` |

品牌渐变**不动**：`#A8F3C8 → #5DDFB4 → #35B9B5`；`mint-light #D9FFF0`；`AppleMintPillEnd #65D3B9`（`MintPalette.kt`）。

### 6.4 字体（`AppType.kt`，六档，`@Composable @ReadOnlyComposable` getter，不写 `lineHeight`）
| 档 | 字号/字重/字距 | 墨色 |
|---|---|---|
| Display | 44sp / Bold / -0.5 / tnum | `text1` |
| Headline | 28sp / Bold / +0.2 | `text1` |
| Title | 17sp / SemiBold | `text1` |
| Body | 15sp | `text1` |
| Footnote | 13sp | `text2` |
| Label | 12sp / Medium / +0.3 | `text2` |

### 6.5 圆角（`AppShape.kt`）
Dialog 24 / Card 20 / Button 14 / Pill 50% / Icon 12dp / Inline 9dp

### 6.6 容器（`Containers.kt`）
- `StrongCard`：圆角 28dp、`contentPadding = 18dp`、`spacedBy(10.dp)`；浅套 `tint2` + `text1`，深套 `strongSurface` + `onStrong`
- `SoftCard`：20dp / 16dp / `spacedBy(8.dp)` / 不描边
- `BareRow`：`heightIn(min = 52.dp)`、`padding(horizontal = 15.dp, vertical = 13.dp)`、`spacedBy(12.dp)`、内层 `spacedBy(2.dp)`、`BareRowDividerIndent = 52.dp`
- `EntryRow`：56dp

### 6.7 页头与按钮
- `AppScreenHeader(title, modifier, subtitle = null, onBack = null)`：返回位 40dp 视觉 + 48dp 命中，tag `app_screen_header`，`contentDescription = "返回"`；subtitle 无标签槽。
- `PillButton(text, onClick, modifier, style = PillStyle.Primary, domain = AppDomain.AiSpeech, enabled, testTag, contentDescription)`；`PillStyle.Secondary / Text`。

### 6.8 底导
- `AppBottomBar.TabSlot` 两档均 `InlineIconBadge(backdrop = selected)`；未选中 `alpha(TabGlyphIdleAlpha = 0.5f)`。
- glyph：`Book` / `Newspaper` / `Spark` / `Person`；第三格已定名 **「AI 助学」**（`AppTab.AI("AI 助学", "AI 助学")`）。

---

## 7. 红线（踩了会不可恢复）

1. **真机测试必须用 `am instrument`**。**禁用 `connectedDebugAndroidTest`** —— 上一轮误用导致卸载 APK，真机词书 / 学习进度 / AI 配置被清空，**不可恢复**。
2. 数据与安全**零改动**；本任务只碰 `docs/`。
3. **不放宽测试判据**：不删断言、不写 `assertTrue(true)`、不放宽 `>=`。改期望值必须先改测试再改实现（TDD 先红）。
4. 生图禁用 `gpt-image-2`（报 `no OpenAI credential`）；用 provider `custom-image` / `gpt-image-2.5`；**不向用户索要 API key**。
5. 效果稿是**新文件**，不得覆盖 `...-reference.html`。

---

## 8. 环境坑（逐条都是踩过的）

| 坑 | 处理 |
|---|---|
| `git` 不在 PATH | 直调 `C:\Program Files\Git\cmd\git.exe` |
| `rg` 不可用 | 用 `Select-String` |
| PowerShell 中文乱码 | 读写走文件工具，或 `-Encoding UTF8` |
| `2>&1` 抛 node stderr 异常 | 用 `Start-Process` 重定向 |
| `adb` | `C:\Windows\adb.exe` |
| 单次 `write` 超长 → JSON 解析失败 | 分块写 + `edit` 替换占位 |
| 真机 `am start` 报 not exported | 用 `monkey -c android.intent.category.LAUNCHER` |
| 双倍字号用例 | 先 `adb shell settings put system font_scale 2.0`，跑完恢复 `1.0` |

---

## 9. 待决 / 尚未补读

- **待裁决**：默写纸设置页 `Checkbox` 的 `checkedTrackColor` 口径（`WorksheetSettingsScreen.kt`），闸内唯一悬项。
- **尚未补读**（写效果稿前需看）：
  - `AiProfileSettingsScreen.kt` 内部弹窗（33KB，用户点名必做）
  - `AiLearningScreen.kt` 内弹窗
  - `ReadingAccessScreen.kt` 请求弹窗
  - 备份四屏：`BackupHub` / `BackupExport` / `BackupRestore` / `BackupPairingExport`
- **已叫停、转交另一 AI 的功能项**（不在本任务范围）：
  - AI 入口 `AppIcon.Spark` 去重（`AiLearningScreen.kt:130/131`、`:168/169`、`:276`；`AiFeatureScreen.kt:176`）
  - aora-bot 吉祥物（Emotion Ball，32 表情，纯 SVG+JS，`https://github.com/sam70361/aora-bot`；Compose 需 Canvas 重绘或 WebView 桥接；LICENSE 待确认，若采用须登记 `docs/third-party-notices.md`）
  - 拍照翻译原位覆盖（需 OCR bbox，现 `PhotoTranslationScreen.kt` 为分块展示）
  - 遗留 6：提醒权限注入夹具
- 上下文丢失风险：真机数据已被清空，不要再假设设备上有历史词书/进度。

---

## 10. 下一步执行顺序

1. 补读 §9 未看的屏与弹窗（只读，不改）。
2. 建 `docs/design/2026-11-03-full-app-ui-phone-gallery.html` 骨架 + 全量 CSS + `<!-- PART1..6 -->` 占位。
3. 按 PART1→PART6 填充，每段写完用文件工具校验闭合。
4. 自检：49 画框 / 分组数 / 深套开关 / 打印分页。
5. 交付路径与打开方式给用户。

---

## 11. 文件索引

**必读**
- `AGENTS.md` — 工程规则、阶段路线、测试先行
- `docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md` — 版式规范（骨架 A–F、§5 逐屏行、§6 批次表）
- `docs/design/2026-11-03-full-app-ui-redesign-reference.html` — 规格稿（复用 `:root` / `.phone` / `.strip`）
- `docs/handoff/2026-11-02-layout-redesign-batch-5-report.md` — 最新收口报告

**源码（令牌与组件）**
- `ui/theme/AppColors.kt`、`AppType.kt`、`AppShape.kt`、`AppPalette.kt`、`MintPalette.kt`
- `ui/components/Containers.kt`、`IconBadge.kt`、`AppIllustrations.kt`、`AppIcons.kt`、`AppIcon.kt`、`AppScreenHeader.kt`、`glass/GlassButton.kt`
- `ui/AppBottomBar.kt`、`ui/theme/AppTab.kt`

**源码（四处缺陷）**
- `ui/TodayPlanScreen.kt`（`:238/260/268` 过深、`:350-361` 假搜索框）
- `ui/WordBankScreen.kt`、`ui/WordBookWordsScreen.kt`、`ui/MyWordsScreen.kt`（版式不统一）
- `ui/GlobalVocabularySearchScreen.kt`（`:118` 真 TextField）
- `ui/CardDetailScreen.kt`（`:304` FlowRow）

**设计稿**
- `docs/design/2026-10-17-ui-redesign-mockups.html`、`2026-10-25-icon-system-design-sheet.html`、`2026-10-28-icon-master-sheet.html`
- `docs/design/icon-master/`（母版 + `STYLE-CONTRACT.md` + `audit/`）

**裁决记录**
- `docs/decisions/2026-11-02-batch-5-routing-and-picker-rulings.md`
- `docs/decisions/2026-11-02-ui-closeout-selection-marks-and-ai-icons.md`
- `docs/decisions/2026-10-31-ui-review-three-rulings.md`
- `docs/decisions/2026-10-27-relaxed-palette-rulings.md`
- `docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`
- `docs/decisions/2026-10-29-icon-vector-redraw-from-master.md`
- `docs/decisions/2026-10-30-icon-illustration-bitmap-landing.md`
