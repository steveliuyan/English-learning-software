# 主题（跟随系统 / 浅色 / 深色）从「设置」子页搬到「我的」（页头统计卡之下），整个设置子页退役

- **日期：** 2026-11-10
- **状态：** 已接受（用户 m03373 提出「主题挪到我的」；m03681 在两个选项里答「1」＝删「设置」行 + 整页，
  主题三态内联进「我的」，不留空壳设置页）
- **上游：** `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`（四栏 + 我的 hub 13 行台账）、
  `docs/decisions/2026-11-03-duplication-consolidation.md` §2/§5（设置子页只剩外观、「关于」行名收口）

## 背景

自批 5 起，「设置」子页被一路瘦身到只剩一段「外观」（主题三态）+ 一句范围说明：

> 这一页只放全局开关。学习提醒、语音朗读、计划、词书、备份与许可都在「我的」里，不在这里重复出现。

也就是说这一页存在的唯一理由是那三枚主题分段，而这三枚分段本身挂在「我的 → 应用 → 设置」一行之下——
用户为了切一个本机显示开关，要先进第四栏、再进一层子页。用户 m03373 因此要求把主题搬到「我的」。
搬走之后设置页**没有任何内容**，继续留着它就是一个点进去只有一句「这里什么都没有」的空壳，
反而制造第二个入口层级。所以本决策一并把它整体退役。

## 决定

### 1. 「外观」（主题三态）内联到「我的」页头统计卡之下

- `ProfileScreen.kt` 在页头统计卡（`profile_stats`）之后、第一组「我的词汇」之前追加一段：`SectionHeading(text = "外观", testTag = "profile_group_appearance")`
  + `ThemeModeRow(mode = rememberAppThemeMode(), onSelect = { AppThemePreferences.write(context, it) })`。
- `ThemeModeRow` 与 `ThemeMode.userLabel` **整份从 `SettingsScreen.kt` 搬来，设计一字未改**
  （`AppCard` + `SegmentedControl` + `Role.RadioButton` + 页内说明句「切换后立即生效，仅影响本机显示，
  不属于学习数据。」）。只换 tag 词表：`settings_theme_mode` / `settings_theme_<storageKey>`
  → `profile_theme_mode` / `profile_theme_<storageKey>`，段标题 tag `settings_group_appearance` →
  `profile_group_appearance`。理由：tag 前缀是「这块内容住哪一页」的声明，搬了页还叫 `settings_`
  就是在 tag 层继续说谎，且会让「旧 tag 不得残留」那类闸失去意义。
- 读写仍**自包含在屏内**（`AppThemePreferences.write` + `rememberAppThemeMode()`，`LocalContext` 就地取）。
  宿主 `AppScreen` 至今没有任何主题状态（grep `ThemeMode` 零命中），唯一装配点是
  `MainActivity.kt` 的 `MintGlassTheme(themeMode = rememberAppThemeMode())`；偏好经监听即时换套。
  为了搬一块 UI 而给宿主新增一条状态位，是把纯展示页的问题升级成宿主状态机问题——不做。
- **位置补正（m04040）：** 首版落在「我的」页末（`ProfileGroups.all` 之后），用户投屏指出「太低了、要放在统计卡红线下方」，
  故最终装配点为 `StrongCard(profile_stats)` 之后、`ProfileGroups.all.forEach` 之前——外观是显示偏好不是学习内容，
  排在统计卡正下方既符合「页头 → 外观」的视觉分层，也避免用户滚到页末才找得到（与 m03560「别把常用控件放在拇指够不着的地方」同一条人机工程口径）。

### 2. 「设置」子页整体退役

删除的东西（不是留空壳，也不是改成只读）：

- `ui/SettingsScreen.kt` 整份删除（130 行）。
- 路由：`AppOverlayRoute.Settings`（`mountIndex = 34`）删除；`AppOverlayRouteResolver.TAB_HOST_INDEX`
  35 → 34；`AppOverlayRoute` KDoc 的「0..34」→「0..33」；`AppMountFlags.showSettings` 删除。
- 宿主 `AppScreen.kt`：`showSettings` 状态位、`overlayOpen` 链里的 `|| showSettings`、
  `BackHandler(enabled = showSettings)`、resolver 实参、`route is AppOverlayRoute.Settings` 挂载分支，
  全部摘除。
- 台账：`ProfileGroups` 从「3 组 14 行」变「3 组 13 行」（我的词汇 3 / 学习 6 / 应用 4），
  `ProfileRow("设置", "profile_open_settings", …)` 一行删除。

### 3. 四处「送去设置页」的落点统一改成 `selectedTab = AppTab.PROFILE`

- `AiFeatureScreen` 的 `onOpenSettings` 兜底（`aiProfileViewModel` 缺失时）：改为切到「我的」hub，
  与既有的 `openAiProfilesOrSettings`（`aiProfileViewModel == null` 时 `selectedTab = AppTab.PROFILE`）
  **完全同一条口径**——不再有两套兜底。
- `TodayPlanScreen` 页头齿轮、`ReadingAccessScreen` 的 `onOpenAiSettings` 兜底：同上。
- 理由：主题不在设置页了，设置页也不存在了；这些入口的用户意图本来就只是「去我的 / 去 AI 配置」。

## 被否决的方案

- **留空壳设置页（m03681 的选项 2）**：用户明确否掉。空壳页等于多一层点击、多一句「这里什么都没有」，
  且它会诱导下一任往里塞新开关，把刚拆掉的层级重新长出来。
- **把主题改成独立一级 tab / 独立浮层**：为一个本机显示开关再加一层导航，与本仓「不许多出入口」的
  既有裁决（`2026-11-02-profile-hub-and-four-tabs.md`）直接冲突。
- **主题状态位提到宿主 `AppScreen`**：见 §1，纯展示页的问题不该升级成宿主状态机。

## 影响面（同步改动，全部随本决策落在同一批）

- 源码：`ProfileScreen.kt`、`SettingsScreen.kt`（删）、`AppOverlayRoute.kt`、`AppOverlayRouteResolver.kt`、
  `AppMountFlags.kt`、`AppScreen.kt`；注释文案同步 `components/SegmentedControl.kt`、`LicensesScreen.kt`。
- JVM 闸：`ProfileHubLedgerTest`、`ProfilePageContractTest`、`BackupEntryUnificationContractTest`、
  `ContainerContractTest`、`ScreenTransitionContractTest`、`ScreenTransitionDirectionTest`、
  `AppOverlayRouteNavigationIdentityTest`、`AppOverlayRouteResolverTest`、`AiGuidanceMapNamingTest`；
  `SettingsPrototypeAlignmentContractTest` 整份删除。
- connected：`SettingsScreenTest`、`SettingsLicensesEntryTest` 删除，「`ThemeModeRow` 是纯三态选择器」
  与「三档 TalkBack 单选语义」两条判据搬进 `ProfileScreenTest`；`AppScreenTest`、
  `ScreenTransitionInstrumentedTest` 的入口锚点从 `profile_open_settings` 改为 `profile_open_about`
  （同为 hub 里的一枚 data object 子页，转场形状不变）。

## 验收

- `ProfileScreenTest`：统计卡下方的「外观」段与三枚分段真在场、可滚可达、当前档 `Selected` + `Role.RadioButton`；
  `profile_open_settings` 与旧 tag `settings_theme_*` 一律不存在。
- `ProfileHubLedgerTest` / `ProfilePageContractTest`：13 行台账、宿主零 `SettingsScreen(`、
  `SettingsScreen.kt` 文件不存在。
- 真机（1080×2340）：进「我的」页头下方即见「外观」，点「深色」整页换套；再点「跟随系统」回平台档。
