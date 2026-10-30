# 全应用信息架构盘点与去重提案（2026-10-31）

> 触发：用户真机走查提出「我的里面有一个『开源许可与隐私说明』，关于里面也有『开源许可与来源』，这是重复的」，
> 并要求把「目前 App 上的每个功能以及位置」重新考虑一遍。
>
> 本文只做**盘点与提案**，未改任何界面。拍板后按仓库惯例先改 `docs/superpowers/specs/` 与决策文档，再动代码。

## 1. 现状：四个一级 tab 及其全部入口

底部导航四栏：学习 / 阅读 / AI 助学 / 我的（`AppTab`）。「设置」不是一级 tab，它以
`SettingsGroupsContent` 的形式住在「我的」页下半屏（`ProfileScreen.kt` 的 `settingsContent` 槽）。

### 1.1 学习 tab（`TodayPlanScreen`）

| 入口 | 目的地 | 落点 |
|---|---|---|
| 开始学习 / 继续学习 | 词卡 | `showLearning` |
| 计划（次按钮） | 计划重建 | `TodayPlanScreen` 内 |
| 今日词单（N） | 今日词单二级层 | `showTodayWordList` |
| 复习队列（M） | 复习队列二级层 | `showReviewQueue` |
| 今日文章 · 未解锁 / 已读 | **切到阅读 tab**（不是直接开文章） | `onOpenReading` → `AppTab.READING` |
| 词书行（四级英语词汇 · 已学 N / M） | 词库页（词书段） | `showWordBank` + `WordBankSection.Book` |
| 查一个词 | 全局查词 | `showGlobalVocabularySearch` |
| 页头 ☰（齿轮/菜单） | **切到我的 tab**（设置长在那一页里） | `selectedTab = AppTab.SETTINGS` |

### 1.2 阅读 tab（`ReadingAccessScreen`）

| 入口 | 目的地 | 落点 |
|---|---|---|
| 今日阅读卡（生成 / 打开今日文章 / 重生成） | 文章 | `readingTarget` |
| 生成前参数（类型选择） | 本屏 | `selectType` |
| 外刊选读 | 外刊 Feed 二级层 | `showReadingFeed` |
| 粘贴导入 | 导入页 | `showArticleImport` |
| 阅读历史（共 N 篇） | 阅读历史二级层 | `showReadingHistory` |
| 本周阅读统计 | 只读 | — |

### 1.3 AI 助学 tab（`AiLearningScreen`）

| 入口 | 目的地 | 落点 |
|---|---|---|
| 现在能用 · 长难句分析 | `SentenceAnalysisScreen` | `selectedFeatureKey` |
| 现在能用 · 拍照翻译 | `PhotoTranslationScreen` | 同上 |
| 现在能用 · AI 生图 | `ImageStudioScreen` | 同上 |
| 未上线功能（4 个） | `AiFeatureScreen` 骨架 | 同上 |
| 查看词表 | 切到学习 tab | `selectedTab = AppTab.LEARNING` |
| 说明区（AI 学如何起作用）/ 隐私提示 | 只读 | — |
| **（无）AI 配置入口** | — | 配置页只在 设置 → AI 与语音 → AI 服务与密钥；功能页内的「去配置 AI 服务」可直达 `AiProfileSettingsScreen` |

### 1.4 我的 tab（`ProfileScreen` + 内联 `SettingsGroupsContent`）

| 入口 | 目的地 | 落点 |
|---|---|---|
| 统计格 · 已学词 | 词库页（词书段） | `showWordBank` + `Book` |
| 统计格 · 连续天数 | 学习打卡 | `showCheckIn` |
| 统计格 · 文章 | 阅读历史 | `showReadingHistory` |
| 学习记录 | 学习记录二级层 | `showLearningRecords` |
| 设置 · 词书与每日目标 | 选词书 / 目标 | `showSetup` |
| 设置 · 学习提醒 | 提醒设置 | `showReminderSettings` |
| 设置 · 生成默写纸 | 默写纸参数 | `showWorksheetSettings` |
| 设置 · AI 服务与密钥 | AI 配置 | `showAiProfiles` |
| 设置 · 语音朗读 | 语音设置 | `showSpeechSettings` |
| 设置 · 主题（三态分段卡） | 本屏 | `AppThemePreferences` |
| 设置 · 开源许可与隐私说明 | `LicensesScreen` | `showLicenses` |
| 设置 · 关于 | `AboutScreen` | `showAbout` |
| 设置 · 词书内容包（折叠） | 导入 / 导出 .wbpack | 本屏折叠区 |
| 设置 · 备份与恢复 | `BackupHubScreen` | `showBackupHub` |

### 1.5 关于页（`AboutScreen`）

| 入口 | 目的地 | 落点 |
|---|---|---|
| 应用块 + 版本 + 隐私一句话 | 只读 | `about_privacy_line` |
| 说明 · 隐私说明 | **不可点（`onClick = null`）** | `AboutScreen.kt:88-94` |
| 说明 · 开源许可与来源 | `LicensesScreen` | `AboutScreen.kt:95-102` |
| 说明 · 备份与恢复 | `BackupHubScreen` | `AboutScreen.kt:103-110` |
| 致谢 | 只读 | — |

## 2. 重复与冲突（按严重度）

### D-1 同一页两套名字、两个入口：开源许可与隐私说明

- 设置「外观与关于」行：`SettingsScreen.kt:142` 标题「开源许可与隐私说明」，副标题「第三方组件、许可证与隐私说明」
  （渲染点 `SettingsScreen.kt:386-390`）。
- 关于页「说明」行：`AboutScreen.kt:95-102` 标题「开源许可与来源」，副标题「N 项第三方组件与内容来源」。
- 两者都进 `showLicenses`（`AppScreen.kt:1526`、`AppScreen.kt:2096`），页面自身标题是「开源许可与隐私说明」
  （`LicensesScreen.kt:77`）。
- 后果：同一目的地有 **3 个名字**；用户从「关于」点进去，页名与行名对不上；且「关于」与「许可」在设置里是相邻两行，
  点「关于」后再看到「开源许可与来源」，会以为存在第二个许可页。

### D-2 同一页两个入口：备份与恢复

- 设置「数据与安全」行：`SettingsScreen.kt:151`「备份与恢复」，渲染 `SettingsScreen.kt:444-449`。
- 关于页「备份与恢复」行：`AboutScreen.kt:103-110`。
- 两者都进 `showBackupHub`（`AppScreen.kt:1527`、`AppScreen.kt:2095`）。
- 名字一致（这一点比 D-1 好），但「关于」是信息页，备份是**操作 + 危险操作（清空数据）**，动机不同。

### D-3 关于页「隐私说明」是死行，且与许可页首段重复

- `AboutScreen.kt:88-94`：`onClick = null`，却带着盾牌图标、标题与副标题「图片本机识别、密钥只存本机、可随时导出」，
  在外观上与其他可点行完全相同。用户点了没反应（比没有这一行更糟）。
- 隐私说明正文的唯一出处是 `LicensesScreen.kt:134-` 的首条 item（与第三方清单同一 `LazyColumn`）。
- 后果：不可点的行 + 与 D-1 目标重复 + 「可随时导出」实际描述的是备份（第三个目的地）。

### D-4 「设置」没有独立位置，却有两个入口

- 「我的」tab 实际是「进度 + 设置」两段式；设置不是一级 tab。
- 学习页页头齿轮 → `selectedTab = AppTab.SETTINGS`（`AppScreen.kt:2003`），落在「我的」**页顶**，
  用户还得滚过统计与学习记录才能看到设置组。
- 后果：不是同屏重复，但「想去设置」的路径与视觉落点不一致。

### D-5 「今日文章」行落到阅读首页，而不是文章

- 学习 tab 的「今日文章 · 未解锁」行 → `onOpenReading` → 切 tab（`AppScreen.kt:1938`）。
- 阅读 tab 还需再点一次今日卡才打开文章。
- 后果：标签承诺「文章」，落点是「阅读首页」，多一次点击；与阅读 tab 的今日卡形成两个入口但语义不等价。

### D-6 / D-7 同一目的地的跨 tab 深链（可接受，记录在案）

- 阅读历史：阅读 tab「阅读历史」行 + 我的「文章」格（`AppScreen.kt:1968`、`AppScreen.kt:2072`）。
- 词库（词书段）：学习 tab 词书行 + 我的「已学词」格（`AppScreen.kt:1943`、`AppScreen.kt:2070`）。
- 判断：这两组是**同一数据在两个语境下的自然入口**（阅读 tab 里是「我的阅读」，我的里是「我的进度」），
  不是需要删除的重复；只要两处落点一致即可（现状一致）。

### D-8 「学习记录」与「学习打卡」名字接近

- 我的页「学习记录」→ `LearningRecordsScreen`（当日词卡明细 + 历史按日）。
- 我的「连续天数」格 → `CheckInScreen`（连续天数、周柱、月历）。
- 判断：内容不重复，但两个名字都像「记录」，且都从我的页出发。可在副标题上进一步区分（低优先）。

### D-9 AI 配置能力的位置与心智不一致

- AI 能力集中在「AI 助学」tab，但配置入口只在 设置 → AI 与语音 → AI 服务与密钥。
- AI 助学页有「AI 尚未接通」徽章，却不可点；用户要配置必须离开 AI tab（或先进任一功能页再点「去配置 AI 服务」）。
- 判断：这是**缺失的入口**，不是重复；但与「功能与位置是否顺手」直接相关。

## 3. 处理方案

### 方案 A：最小去重（推荐）

1. 关于页删「备份与恢复」行（设置里已有）→ 关于只剩 1 条说明行。
2. 关于页「隐私说明」死行删除；它的三句信息并入应用块下方已有的 Privacy line（或把该行改成可点、跳许可页）。
3. 关于页「开源许可与来源」改名，与设置行、页标题**逐字一致**为「开源许可与隐私说明」；或反过来给许可页改短名——二选一，不再保留三个名字。
4. 设置里「关于」与「许可」两行相邻的现状保留（它们不再是同一目的地）。

- 影响：`AboutScreen.kt` 少两行、改一行；`AboutScreenContractTest` 与 connected `AboutScreenTest` 需同步；
  设置页行数不变（仍是 10 行台账）。不新增页面、不动导航结构。
- 风险：低。缺点是「关于」页变短（版本 + 一句隐私 + 许可入口 + 致谢），但这正是关于页该有的信息量。

### 方案 B：功能按「调整 / 查阅」重新分工

1. 设置只保留「会改变状态」的行（词书与每日目标、提醒、默写纸、AI 服务、语音、主题、词书内容包）。
2. 备份、许可、隐私三个「查 / 数据」入口全部迁到关于页（关于成为信息中心，含备份入口）。
3. 设置「数据与安全」组只剩词书内容包。

- 影响：设置从 4 组 10 行缩为 3 组 8 行左右；`SettingsGroupsContent` 台账、`AC-R-21` 行数契约、
  `SettingsScreenTest`、`AppScreenOverlayContainerTest`、`AboutScreenTest` 全要改。
- 风险：中。备份是操作而非信息，放在「关于」下并不直观；且用户最常找备份的路径是「设置 → 数据」。

### 方案 C：结构性重排（下一个批次）

1. 设置升级为独立一级 tab（底部五栏：学习 / 阅读 / AI 助学 / 我的 / 设置），「我的」只留进度。
2. AI 助学页头部增加「配置 AI 服务」入口（状态徽章可点，或徽章下一行按钮），直达 `AiProfileSettingsScreen`。
3. 学习页「今日文章」行改为直接打开今日文章（未解锁时仍切阅读 tab 并说明原因）。
4. 统一全站「学习记录 / 打卡 / 阅读历史」的命名与副标题。

- 影响：底导从四栏变五栏，涉及 `AppTab` 契约测试、`AppBottomBar`、四栏标签快照、`rememberSaveable` 状态、
  以及所有 connected 导航用例；需要单独立项与计划。
- 风险：高，收益也最高；但会与「原型四栏」的既有验收（AC-1/AC-7/AC-8）冲突，必须先改规格。

## 4. 建议

- **本轮（去重）走方案 A**：用户指出的重复能一次性消掉，代价最小，不动导航结构，不与原型四栏验收冲突。
- **方案 C 中的第 2 条（AI 配置入口）单独拿出来做**：它是一个「缺失」而非「重复」，且用户正在用 AI tab，
  成本只是一行入口或一个可点徽章。建议与方案 A 同批落地，但作为独立决策记录。
- 方案 B 与方案 C 的第 1、3、4 条留待用户对整体层级有更大调整意愿时再开单。

## 5. 重算命令（写进本文的每个落点都可现场复算）

```bash
grep -rn "onOpenLicenses\|onOpenBackup\|showLicenses = true\|showBackupHub = true\|showAbout = true" app/src/main/java --include=*.kt
grep -n "onClick = null" app/src/main/java/com/example/englishlearning/ui/AboutScreen.kt
grep -n "settings_open_licenses\|settings_open_about\|settings_backup" app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt
grep -n "onOpenSettings = { selectedTab = AppTab.SETTINGS }" app/src/main/java/com/example/englishlearning/ui/AppScreen.kt
```
