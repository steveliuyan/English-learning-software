# 2026-11-02 版式重做 批 5（设置与系统 9 屏）交付报告

计划文档：`docs/superpowers/plans/2026-11-02-layout-redesign-batch-5-settings-and-system.md`
规范文档：`docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md`
裁决文档：`docs/decisions/2026-11-02-batch-5-routing-and-picker-rulings.md`
改判留痕：本文记录的「选词书改 **2 列色块卡**」版面在 2026-10-29 逐屏对照原型后被用户翻掉，现行是通栏
`.pick.pick-radio` 行列表（`docs/decisions/2026-10-29-word-book-picker-rows-supersede-tiles.md`、
批 1 台账 Task 13-A）。本文其余记录仍是当时的事实，不覆写；「删除走长按」「不引 `LazyVerticalGrid`」
两条结论搬进行列表后继续有效。
范围：`SpeechSettingsScreen` / `ReminderSettingsScreen` / `LicensesScreen` / `BackupHubScreen` /
`BackupExportScreen` / `BackupRestoreScreen` / `BackupPairingExportScreen` / `RecoveryScreen` +
`AppScreen.kt` 内启动/建档窗口（九屏 2160 行，全量重排 8 个屏文件 + 宿主分支）

## 1. 本次目标

把「设置与系统」九屏从「自写返回 + 卡片汤 + 手搓字号/静态 Mint 色桥」推到批 1 立的骨架：
`AppType` 六档排印、`appColors()` / `appDomain()` 令牌、`AppScreenHeader` 共享页头、
`StrongCard`/`SoftCard`/`BareRow` 三档容器（本批九屏**零强卡**）、危险动作带确认。同批收掉 spec §5 的
十行逐屏要求：试听卡降为一行、开关统一 `Switch`、语速改滑杆、时间控件改 Material `TimePicker`、
加载失败带重试、整屏可滚（六屏补滚）、许可证空态带重试、入口名与页名统一、两条导出分主次、
危险删除沉底、包含/不包含分两段、密码掩码、恢复顶栏返回、单点即毁写明后果、恢复异常去技术串 +
补主操作、启动建档三屏内完成 + 品牌渐变封面、选词书改 2 列色块卡。

约束（全程未松）：只改版式与信息架构，**不动** Room / FSRS / 备份格式 / Keystore / AI 请求策略；
改期望值不放宽判据；TDD 先红并贴失败读数；`fontScale = 2f` 不退化；一屏一个主操作；
connected 只用 `am instrument`（**禁** `connectedDebugAndroidTest`，上一轮误用已不可恢复地清空真机数据）。

## 2. 完成了什么

| Task | 屏 | 契约测试 | 实测状态 |
|---|---|---|---|
| 1 | 语音合成 `SpeechSettingsScreen.kt` | `SpeechSettingsLayoutContractTest`（7） | ✅ 首跑 4 红 3 绿 → 绿；+4 条 VM；1972 tests |
| 2 | 学习提醒 `ReminderSettingsScreen.kt` | `ReminderSettingsLayoutContractTest`（7） | ✅ 首跑 7 红 → 绿；+2 条 VM；1981 tests |
| 3 | 许可证 `LicensesScreen.kt` | `LicensesLayoutContractTest`（7） | ✅ 红 6 绿 1（第 6 条是存量钉）→ 绿；1988 tests |
| 4 | 备份中心 `BackupHubScreen.kt` | `BackupHubLayoutContractTest`（6） | ✅ 首跑 6 红 → 绿；1994 tests |
| 5 | 导出 `BackupExportScreen.kt` | `BackupExportLayoutContractTest`（7） | ✅ 首跑 6 红 → 绿；2001 tests |
| 6 | 恢复 `BackupRestoreScreen.kt` | `BackupRestoreLayoutContractTest`（**8**） | ✅ 首跑 7 红 → 绿；2009 tests |
| 7 | 认证导出 `BackupPairingExportScreen.kt` | `PairingExportLayoutContractTest`（7） | ✅ 首跑 6 红 → 绿；2016 tests |
| 8 | 恢复异常 `RecoveryScreen.kt` | `RecoveryLayoutContractTest`（7） | ✅ 首跑 4 红（另 3 条本就该绿）→ 绿；2023 tests |
| 9 | 启动/建档 `AppScreen.kt:1983-2676` | `LearningSetupLayoutContractTest`（8 → **13**） | ✅ 首跑 8 红 → 绿；2028 tests |
| 10 | 全量门禁 + connected + 大字号 + 真机目视 + 报告 | `ReminderPermissionLaunchContractTest`（**4**，本 Task 新增） | ✅ 见 §3；2032 tests |

### 屏级变化摘要

- **语音合成**：页头换共享 `AppScreenHeader("语音合成", onBack)`，`MintBackground` → `appColors().background`；
  「学习时自动发音」的 `Row + clickable` + 布尔文字「开/关」→ `Switch`（`speech_auto_speak_state` 退役，
  CD「学习时自动发音」逐字）；「发音语速」点按循环 → `Slider` + `speech_rate_value`（`1x`/`1.25x` 去尾零），
  VM 增 `setSpeechRate(Float)`（吸附走同一张 `SpeechRateSteps` 档位表）；试听区从常驻玻璃卡降为
  一条 `BareRow("试听效果")` + `if (previewExpanded)` 展开（`rememberSaveable`、默认收起），
  **`PreviewCard` 本体与其两枚形参一字未动**（`GlassPanelInkPairingContractTest` / `GlassPanelThemeInkTest` 前提）。
- **学习提醒**：整文件重写（D 骨架）；自搓九宫格 `TimeGrid` 全部退役 → M3 `AlertDialog` + `TimePicker`
  （M3 无 `TimePickerDialog`，见计划 Step 3 偏差）；加载失败补 `reminder_retry`，VM 增 `retry()`
  （`load()` 有「只在 `loading == true` 时落结果」的守卫，直接调 `load()` 会是假按钮）；
  两分支各补 `verticalScroll` + 共享页头；裸 `.sp` 与 `Mint*` 归零。
- **许可证**：页名统一「开源许可与隐私说明」（入口行文案同批改）；`Mint*` 19 → 0、`MaterialTheme.typography` 8 → 0；
  隐私长文本去 `horizontalScroll`，整屏并成**一个 `LazyColumn`**（外层滚 + 内层列表会得无界高子节点）；
  空态 `licenses_retry` 由宿主 `licensesReload` 计数重读 assets（旧无键 `remember` 会把一次失败永久缓存成空串）；
  页头族 seed 摘掉最后一枚 → **空集**，`headerSeedRetentionReason` 同批重写为「不再覆盖任何文件」。
- **备份中心**：`BackupHubSection`(M3 `Card` + `CardDefaults`) → `SoftCard(contentPadding = 0.dp)`
  + 卡内 `BareRow`；两条导出改裸行 + `"推荐"` 徽标表达主次（**药片在 13 字标题下会折行顶高到 90dp，
  且实底键会与子屏吸底真主键抢焦点**）；节标题「删除」→「危险操作」+ 卡内 `AppType.Label`「不可撤销」取
  `colors.error`，危险区沉底为最后一段；四枚行标题/副标题一字未动；回执条移到危险区正上方。
- **导出**：页头换共享；「包含/不包含」拆两段 `SoftCard`，段一四条 `BareRow`（`backup_export_scope_$tag`），
  段二一句例外 + 开关；**段一窗口内「音频」为 0**（正向断言）；`backup_export_save` 吸底
  （`PillStyle.Primary` + 同槽状态行「共 N 条 · M 个音频 · 约 X KB」），`backup_export_prepare`
  降为屏体文字次键（本批收口再改为贴右收窄，见 §3.5-6）。
- **恢复**：页头换共享（返回位接「先 `viewModel.cancel()` 再 `onBack()`」的 lambda，判据钉三笔）；
  口令框加 `PasswordVisualTransformation()`；确认页文案写明「覆盖本机现有数据，且无法恢复上一步」；
  四态骨架换 `SoftCard`/`BareRow` + `AppType` + `appColors()`；取景框权限提示由裸 `Text`（深套黑压黑）改取 `text1`；
  phase 机、SAF、CameraX 三件套零改动。
- **认证导出**：页头换共享（标题取「认证导出」，与备份中心入口同名）；密码遗忘警告两段合一；
  口令框加掩码；**配对码「不复显」按 D-11 改判为正向钉住**（`_reveal_qr` 恰 1 处 + 其调用点前最近
  `runWithMigrationAuth(` 必须存在 + `onPairingRevealed` 回填），理由见裁决文 §2；QR 双色保留。
- **恢复异常**：标题从「带技术串的 reason」改中性「数据暂时打不开」，`reason` 收进「详情」折叠行；
  `Mint*` 8 → 0、`Button(` → `PillButton`；两条路「导出本机留档」（`PillStyle.Primary`）+「重试」
  （`PillStyle.Secondary`）；宿主按**对账 1 改判**做分支内层覆盖（见裁决文 §1）；新增
  `AndroidTestFailingDatabaseStartupProbe` 让恢复态在设备上可达。
- **启动/建档**：新建 `WordBookPicker`（`chunked(2)` + 行内 `weight(1f)` + 单数行 `Spacer`）与
  `WordBookTile`（域色 `Library.deep` 描边 + 选中 `tint` 底 + `IllustratedIcon(AppIcon.Book)` +
  书名两行 + `共 N 词` + 进度条）+ `WordBookTileSkeleton`；`combinedClickable` 一槽两义
  （点＝选中、长按＝请求删除），同节点保留 `customActions`「删除词书」、CD「选择词书 X」、
  `stateDescription`（F5-03）；`WordBookCard` / `WordBookList` / `WordBookListDivider` /
  `SwipableWordBookCard` / `SwipeToDismissBox` 全部下线，17 条 import 同批摘除（`AppScreen.kt:2435`
  的 `Color.White` 随之消失）；`ProfileCreationScreen` 补 148dp 品牌渐变封面（`AppleMintGradient` + 标 +
  两句），`Mint*` 18 → 0、`AppType` 排印、CTA 取 `strongSurface`/`onStrong`/`AppShape.Button`、整屏可滚。

## 3. 验证记录

### 3.1 全量 JVM 门禁（Step 1，收口终值）

```
& $g.FullName :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin `
    verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
```

结果：**BUILD SUCCESSFUL**

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest` | **275 XML / 2032 tests / 0 failures / 0 errors / 0 skipped**（遍历 XML 累加实测） |
| `:app:assembleDebug` | 通过 |
| `:app:compileDebugAndroidTestKotlin` | 通过 |
| `verifyThirdPartyNotices` | `Validated 76 third-party notice entries.`（与批 4 一致，本批未引入依赖） |
| `verifyAcTraceabilityMatrix` | `AC matrix covered 56 ids with evidence.`（与批 4 一致） |

条数轨迹（每步都是实跑数）：

| 里程碑 | tests | 增量 |
|---|---|---|
| 批 4 收口 | 1961 | — |
| Task 1 语音合成 | 1972 | +11（7 契约 + 4 条 VM） |
| Task 2 学习提醒 | 1981 | +9（7 契约 + 2 条 VM） |
| Task 3 许可证 | 1988 | +7 |
| Task 4 备份中心 | 1994 | +6 |
| Task 5 导出 | 2001 | +7 |
| Task 6 恢复 | 2009 | +8（第 8 条：页头与 M3 按钮收拢拆开判） |
| Task 7 认证导出 | 2016 | +7 |
| Task 8 恢复异常 | 2023 | +7 |
| Task 9 启动/建档 | 2028 | +5（原 8 条未动，扩 5 条） |
| Task 10 收口 | 2032 | +4（`ReminderPermissionLaunchContractTest`） |

XML 数 266 → 275（八枚新契约 + Task 10 一枚）。

### 3.2 connected（真机 `bf353dda`，只 `am instrument`，`font_scale` 1.0）

> 口径变更说明：计划 Step 2 原写「模拟器 `emulator-5574`」，实际收口在**真机 `bf353dda`** 跑——
> 两个设备上都只跑 `am instrument`、都不碰数据目录；真机是目视同一台，跑完即可回归目视。
> 真机本地数据已被上轮误用 `connectedDebugAndroidTest` 清空（不可恢复），故本批截图里的
> 词书/进度是空档状态，属数据态而非版式问题。

十三类 **76 例**（装最新 `app-debug.apk` + `app-debug-androidTest.apk` 后整批跑，一次成型）：

| 类 | 结果 | 类 | 结果 |
|---|---|---|---|
| `SpeechSettingsScreenTest` | 13/13 | `SettingsScreenTest` | 18/18 |
| `ReminderSettingsScreenTest` | 3/3 | `SettingsLicensesEntryTest` | 1/1 |
| `LicensesScreenTest` | 3/3 | `ThemeModeSwitchTest` | 4/4 |
| `RecoveryScreenTest` | 5/5 | `GlassPanelThemeInkTest` | 8/8 |
| `BackupEntryUnificationTest` | 7/7 | `AppScreenOverlayContainerTest` | 6/6（见下） |
| `WordbookPickerHeaderTest` | 3/3 | `WordBookTileTest` | 4/4 |
| `SetupScreenScreenshotTest` | 1/1 | 合计 | **76** |

`AppScreenTest` 另跑 **52/52**（收口最后一处改动——§3.5-7 的吸底槽发丝线——之后重装 `app-debug.apk` 复跑一次，仍 52/52）。

两处前置条件（都不是实现缺陷）：
1. `AppScreenOverlayContainerTest#hostedWordBookDeletePanelStaysFullyReadableAndTappableAtDoubleFontScale`
   在设备 `font_scale = 1.0` 下**故意判红**（消息原文：「connected 侧需先把设备 font_scale 置 2.0，
   护栏不许在 scale 1.0 下空转」）。按提示 `settings put system font_scale 2.0` 后该类 **6/6 绿**，
   跑完已恢复 `1.0`（已复核 `get system font_scale` = 1.0）。
2. **装包纪律（新坑，写进 §6）**：只 `:app:compileDebugAndroidTestKotlin` 不重装测试 APK 时，
   设备上跑的是**旧测试 APK 对着新实现**，会抛 `NoSuchMethodError: No static method
   SpeechSettingsScreen(...)`（首跑 15 条假红）。必须 `:app:assembleDebugAndroidTest` +
   `adb install -r` 两枚 APK 再跑。

### 3.3 大字号专项（Step 3）

`font_scale = 2.0` 下逐屏手点 + `uiautomator dump` 读 bounds，七屏取证（截图见下行表），
每屏都验到「可滚 + 控件可点」；两屏设备侧不可达（见下）。

| 2× 字号截图 | 取证点 |
|---|---|
| `app-batch5-font2x-settings-20261102.png` | 「我的」页整屏可滚到底（外观与关于 → 数据与安全 → 备份与恢复）；统计格标签「连续天数」自动折两行且不截断；主题三芯片在一行内不挤 |
| `app-batch5-font2x-speech-rate-20261102.png` | 「学习时自动发音」标题 + 开关同一行、说明独立三行不窄列（修复 2 在 2× 下的复验）；「发音语速 / 1x」+ 说明 + 滑杆可拖（滑杆轨道与拇指比例正常） |
| `app-batch5-font2x-reminder-20261102.png` | 每日提醒开关行、提醒时间「20:00 + 修改」药片不溢出 |
| `app-batch5-font2x-timepicker-20261102.png` | M3 `TimePicker` 对话框：小时表盘数字全可见、选中圈与指针正常、取消/确定两枚药片不裁切 |
| `app-batch5-font2x-licenses-20261102.png` | 长清单滑到底（末条可达）、条目「展开」按钮仍可点、标题单行省略号、副标题两行 |
| `app-batch5-font2x-backup-hub-20261102.png` | 三段分组在 2× 下顺序不乱，危险区「不可撤销」眉标 + 「删除全部学习数据」行完整 |
| `app-batch5-font2x-export-20261102.png` | 「包含内容」长文从吸底槽下穿过时有**发丝线**交代边界（修复 7 的取证）、吸底主键与状态行不裁切 |
| `app-batch5-font2x-restore-20261102.png` | 「开始之前」「第一步 · 选择备份包」两卡 + 主键「选择备份文件」满宽不溢出 |

**设备侧不可达的两屏**（不是被跳过，是这套版式在这两屏只在特定状态下出现，端上无可达路径）：
- **认证导出**（`BackupPairingExportScreen`）：`Idle` 分支要系统认证（指纹/密码）才会进 `Completed`，
  目视只能停在认证卡前；`Completed` 形态由 `PairingExportLayoutContractTest` + `PairingExportQrFlowContractTest` 覆盖。
- **恢复异常**（`RecoveryScreen`）：要数据库迁移真的失败才进 `NeedsRecovery`；
  端上唯一入口是 androidTest 的 `AndroidTestFailingDatabaseStartupProbe`，而按纪律不跑 instrument 到真机。
  该屏的 2× 版式由 `AppScreenOverlayContainerTest`（2× 那条）+ `RecoveryScreenTest` 覆盖。

`font_scale` 已恢复 1.0（`settings get system font_scale` = 1.0，屏上复核见
`app-batch5-font2x-settings-20261102.png` 之后的「跟随系统」复选状态）。

已知偶发（未改实现）：`AppScreenTest#startLearningOpens...` 在 2× 字号整类跑时曾 `ComposeTimeoutException`
一次，单跑与整类复跑均绿——与该类已登记的 `pressSystemBack()` 时序抖动同源。

### 3.4 真机目视（Step 4）

截图落 `docs/design/app-batch5-*-20261102.png`（共 22 张：浅/深 13 张 + 2× 字号 8 张 + 深套覆盖层 1 张）：

| 截图 | 取证点 |
|---|---|
| `app-batch5-settings-light-20261102.png` | 「我的」页：统计格 / 学习组 / AI 组 / 设置组 / 数据与安全组，行距与分组弱卡一致 |
| `app-batch5-settings-appearance-light-20261102.png` | 外观三芯片（跟随系统 / 浅色 / 深色）与「开源许可与隐私说明」入口行 |
| `app-batch5-speech-light-20261102.png` | 自动发音行（标题 + 开关一行，说明独立一行）、语速滑杆 + `1x` 读数、试听一条裸行（收起态） |
| `app-batch5-reminder-on-light-20261102.png` | 提醒开关亮起后出现「提醒时间 20:00 / 修改」行（修复 1 的证据） |
| `app-batch5-reminder-timepicker-light-20261102.png` | M3 `TimePicker` 对话框（时/分表盘 + 取消/确定两枚药片） |
| `app-batch5-licenses-light-20261102.png` | 页名「开源许可与隐私说明」、隐私段整段换行、条目卡可展开 |
| `app-batch5-backup-hub-light-20261102.png` | 导出区两行（留档带「推荐」徽标）/ 恢复区 / 危险操作区沉底 + 「不可撤销」眉标 |
| `app-batch5-backup-export-light-20261102.png` | 页头 + 段一四条范围行 |
| `app-batch5-backup-export-scope-light-20261102.png` | 段二「不包含」+ 开关 + **贴右收窄的「生成备份包」** + 吸底「选择保存位置并导出」 |
| `app-batch5-backup-restore-light-20261102.png` | 「从旧设备恢复」四态之首（挑选备份文件） |
| `app-batch5-settings-dark-20261102.png` / `-backup-hub-dark-` / `-speech-dark-` | 深套三屏：卡片底/墨、开关与滑杆在深套可见 |
| `app-batch5-danger-dialog-dark-20261102.png` | **覆盖层（深套）**：清空数据确认弹窗——弹窗底与页底分明、三行后果（删几条/保留什么/不可撤销）全可读、「确认删除」危险档药片可读；底下的备份中心深套版仍可见「推荐」徽标与「不可撤销」眉标 |

### 3.5 目视发现并修掉的 7 处（判据同批改，无一放宽）

| # | 现象 | 读因 | 修法 | 判据动作 |
|---|---|---|---|---|
| 1 | 学习提醒点开关**永不打开**，要退出本页再进来才显示已开启 | 宿主把 `notificationGranted` 当**组合期快照**捕获进 `onEnableRequested` lambda：授权走了 `ActivityCompat.requestPermissions`，但「允许」之后**没有任何回调**去 `setEnabled(true)` | `AppScreen.kt` 改 `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`，回调里当场 `setEnabled(true)`；删请求码常量与 `ActivityCompat.requestPermissions` | **新增** `ReminderPermissionLaunchContractTest`（4 条）：launcher 形态 / 回调里 `setEnabled(true)` / 旧链路三个符号为 0 / 屏调用夹具逐字保留；已做**反空转探针**（把回调体改空 → 立即判红，探针已撤） |
| 2 | 语音「学习时自动发音」说明被开关挤成 20 字窄列、语速说明同样窄换行 | 说明与控件同在一个 `Row` 里，控件占位后 `Text` 只剩窄列 | 两处均改「标题 `weight(1f)` + 控件」一行 + 说明独立成下方 `Text(AppType.Footnote)`；试听副标题缩为「播放一段文字，配置对不对当场听见。」 | 版式断言在 `SpeechSettingsLayoutContractTest` 内已按窗口判（开关在 tag 之后 400 字符内），本次不改判据 |
| 3 | 深套里开关/滑杆几乎看不见（拇指与轨道都压在 `surface` 上） | 开关 `checkedTrackColor = colors.strongSurface`、滑杆同源，深套 `strongSurface #22332C` 压 `surface #17201C` 只差一档 | 两处改 `if (LocalIsDark.current) domain.base else colors.strongSurface`（浅套零变化），新增 `LocalIsDark` import | 不变（浅套取值逐字保留）；深套由截图复验 |
| 4 | UI 文案里露出 Markdown 反引号（`.elpbak` / `.wbpack` 被包在反引号里，正文显示成代码片段） | 文案直接抄了文档写法 | 5 处去反引号：`BackupHubScreen` 副标题、`BackupExportScreen` 页头、`BackupRestoreScreen` 挑选文案、`SettingsScreen` 词书导入/导出副标题 | 同步改夹具：`BackupEntryUnificationTest:120`、`SettingsScreenTest:317` |
| 5 | 「AI Profile 的 API Key」等开发者术语（同批还改了恢复成功回执） | 屏上直呼内部实体名 | 改「AI 服务的 API Key」；`BackupRestoreViewModel.RESTORED_MESSAGE` 同步 | 全 search 确认无测试钉住；`SettingsNoDeveloperJargonTest` 仍绿 |
| 6 | 「生成备份包」是 `PillStyle.Text` **满宽**透明药片，与吸底主键同宽同位，真机上读成「第二枚快捷键」 | 文字档 + `fillMaxWidth()` = 一条通栏，视觉上就是第二枚键 | `BackupExportScreen.kt` 改 `Modifier.align(Alignment.End)`（宽度回到字宽） | **先红后绿**：`BackupExportLayoutContractTest` 第 ④ 条新增两笔（`fillMaxWidth` 必须不在实参表、`Alignment.End` 必须在），首跑红在该行（第 240 行），改实现后转绿 |
| 7 | 2× 字号下「包含内容」长文正好断在吸底槽沿，槽色与软卡同为 `colors.surface`，被读成「卡片被截断」 | 吸底 `Surface(color = colors.surface)` 与 `SoftCard` 同色，槽顶没有边界线 | 槽顶压一条发丝线：`Modifier` 链首加 `hairlineTop(colors.outline)`（复用 `Containers.kt` 的共享助手） | **先红后绿**：`BackupExportLayoutContractTest` 第 ④ 条再追加一笔「吸底槽窗口内必须出现 `hairlineTop(`」，首跑 FAILED 在同一用例（`Alignment.BottomCenter` 前 300 / 后 900 字符窗口），改实现后 7/7 绿 |

真机复验：`adb install -r` + 冷启 + 手点，截图见 §3.4（第 6 条证据是
`app-batch5-backup-export-scope-light-20261102.png`：次键在 (740,1801)-(970,1867)，吸底主键跨 55-969）。
`uiautomator dump` 读到的备份中心/导出页文案逐行核对通过（`推荐` 徽标、`不可撤销`、`.elpbak` 文件名形态）。

## 4. 改期望值清单（旧值 → 新值 + 依据）

> 只登记**判据本身变了**的地方；每处都在被改断言上方留了注释，本机无 `git`（不在 PATH），
> 依据取注释 + 计划各 Task 验收段 + 裁决文。

| # | 文件 | 旧值 | 新值 | 依据 |
|---|---|---|---|---|
| 1 | `SharedComponentUniquenessRatchetTest.headerSeed` | 页头族 seed 1 枚（`LicensesScreen.kt`） | **`emptySet()`** | 许可证屏换共享页头、`MaterialTheme.typography` 读点归零；摘行门与「双向集合相等」同一次跑通过 |
| 2 | `SharedComponentUniquenessRatchetTest.headerSeedRetentionReason` | 「仅剩 Licenses 一枚待批 5」 | 「本理由自批 5 Task 3 起不再覆盖任何文件」+ 历史口径留档 | 理由文本与 seed 必须同步，否则留档撒谎 |
| 3 | `ContainerContractTest` 屏级表 | 19 屏 | **27 屏**（八屏逐 Task 加行：20/21/22/23/24/25/26/27） | 本批八屏纳入容器三档判据；三条几何判据逐字未动。启动/建档仍不入表（住 `AppScreen.kt`，按文件数会串账） |
| 4 | `LicensesScreenTest` | 标题「关于与开源许可」+ `licenses_back` + 空态无操作 | 「开源许可与隐私说明」+ 页头「返回」可及名 + `licenses_retry` 可点 | 页名统一是 spec §5 硬要求；旧页名/旧锚各配「必须为 0」正向断言 |
| 5 | `RecoveryScreenTest` | 「retry 恰好 1 枚」+ 禁四词 | 「重试」恰 1 + 「导出本机留档」恰 1（分别计数）+ 标题不得含 `SQLite`/`Exception`/`database`/`IllegalState`/`IOException` | 新增主操作后按钮变 2 枚（收紧：分两条计数）；禁四词逐字保留 |
| 6 | `SpeechSettingsScreenTest` | `speech_auto_speak_state`「开/关」文字；`speech_rate_toggle` 的 `stateDescription` + 点按循环 | `assertIsOn`/`assertIsOff` + CD「学习时自动发音」；滑杆 `SetProgress` 后读 `speech_rate_value`；旧 tag 与点按循环各加「必须为 0」 | 控件形态变了，断言改口径不放宽（折叠态下 `speech_preview_input` **必须不存在**，比原来更强） |
| 7 | `BackupEntryUnificationTest` | 三分区顺序 + 四入口；副标题夹具含反引号 | 顺序保留 + 新增 `theRecommendedExportComesFirstAndTheDangerRowIsLast`（徽标 `useUnmergedTree` 取证 + bounds 钉「留档在认证之上」「清空是最后一枚入口」）；夹具去反引号 | 裸行 `clickable` 默认 `mergeDescendants`，合并树里拿不到尾部节点 |
| 8 | `SettingsScreenTest` | `.wbpack` 夹具含反引号 | 去反引号 | UI 文案同批去掉反引号，夹具必须同步 |
| 9 | `GlassPanelThemeInkTest` | 包名写成 `.ui`；直接进语音页点芯片 | 包名 `.ui.glass`；先点 `speech_preview_toggle` 展开试听区 | 试听区改折叠后玻璃卡默认不组合（折叠是外壳，玻璃卡本体未动） |
| 10 | `AppScreenTest`（AI 能力行） | 直接找 `ai_profile_capability_vision` | 先 `scrollToTag` | 设置页分组增高后该行落到首屏外（锚与文案未变） |
| 11 | `LicensesScreen` + `LicensesScreenTest` | `LazyColumn` 无锚，整屏长文需手滑 | `LazyColumn` 增 `.testTag("licenses_ledger")`，用例改 `performScrollToNode(hasTestTag(...))` | 单个 `LazyColumn` 装全部后，长文末条需要显式滚到（判据「可滚且末条可达」） |
| 12 | `BackupExportLayoutContractTest` 第 ④ 条 | 只钉「`backup_export_prepare` 挂 `PillStyle.Text`」 | 追加两笔：`fillMaxWidth` 不在实参表 + `Alignment.End` 必须在（先红后绿） | §3.5-6：满宽文字档 = 第二枚快捷键 |
| 13 | `WordBookCardProgressContractTest` / `AccessibilityStateSemanticsContractTest` / `AppScreenOverlayContainerContractTest` | `WordBookList(` / `wordbook_swipe_*` / `WordBookCard(` | `WordBookPicker(` / `wordbook_tile_*` / `WordBookTile(`（旧锚各配「必须为 0」） | 对账 4 定案：滑删 → 长按（裁决文 §3） |
| 14 | `WordBookSwipeRowTest` | 滑删语义 3 条 | 重写为 `WordBookTileTest` 4 条（色块无 error 像素 / 「删除词书」动作同节点 / 点按只选中·长按只请求删除 / 内置册无长按无删除动作） | 交互载体变了；判据不下调（多一条「内置册不可删」） |
| 15 | `BackupExportLayoutContractTest` 第 ④ 条（同日第二次追加） | 只钉「主键吸底 + 次键 `PillStyle.Text`」 | 追加一笔：吸底槽窗口内必须出现 `hairlineTop(` | §3.5-7：2× 字号下槽色与软卡同色，长文断在槽沿读成卡片被截断；首跑 FAILED，改实现后 7/7 |

**零删除**：全批没有 `assertTrue(true)`、没有把断言放宽成 `>=`、没有删掉既有断言；
退役的旧锚（`licenses_back` / `wordbook_swipe_*` / `speech_auto_speak_state` / `backup_pairing_export_reveal_qr` 的旧语义）
都配了「必须为 0」的正向对偶。

## 5. 与规范的对账落地结果

| # | 对账项 | 落地结果 |
|---|---|---|
| 1 | 恢复异常「导出本机留档」要真能出去 | ✅ **改判落实**：分支内层覆盖（`NeedsRecovery` 自持 `showRecoveryExport`），状态机零改动；`AppScreenTest` 加「点留档 → 出现 `backup_export_screen` → 返回回恢复态」 |
| 2 | 许可证空态重试的对象是 assets 读取 | ✅ 宿主 `licensesReload` 计数当 `remember` 的键，点一次重读一次；只重读同一 assets，无新数据通路 |
| 3 | 页名与入口名统一 | ✅ 屏内页头 + 「我的」页入口行 + `licenses_back` 退役，三处同一句 |
| 4 | 2 列色块卡 vs 滑删 | ✅ 采推荐方案（长按删除）+ **判据 1 改判**：不引 `LazyVerticalGrid`，改 `chunked(2)` + `weight(1f)`（根已 `Column(verticalScroll)`，嵌懒网格抛无限高约束）；列数与列宽约束比原稿更紧 |
| 5 | 语速改滑杆需 VM 增参 | ✅ `setSpeechRate(Float)`（同一偏好、同一档位表），`cycleSpeechRate()` 保留（另有调用点） |
| 6 | 认证导出「配对码不复显」 | ⚠️ **改判**：与 D-11 / AC4-08 冲突（Done 阶段经再次认证重显同一码是安全语义），改为正向钉住认证门控，理由见裁决文 §2 |
| 7 | 导出「包含/不包含」分两段、音频例外只讲一次 | ✅ 两段两卡，段一「音频」为 0（正向断言），例外句全文件恰 1 处 |

## 6. 净增判据统计

| 来源 | 条数 | 说明 |
|---|---|---|
| 八枚新契约 + Task 9 扩条 | **+61** | 7+7+7+6+7+8+7+7 = 56，Task 9 净增 5 |
| VM 用例 | **+6** | `SpeechSettingsViewModelTest` +4、`ReminderSettingsViewModelTest` +2 |
| Task 10 新契约 | **+4** | `ReminderPermissionLaunchContractTest` |
| **JVM 合计** | **1961 → 2032（+71）** | 与「61 + 6 + 4」吻合 |
| connected 新增 | **+6** | `ReminderSettingsScreenTest` 3（新建）、`WordBookTileTest` 4（重写自 `WordBookSwipeRowTest` 3）、`AppScreenOverlayContainerTest` +1（建档删除面板 2× 字号）、`WordBookTile` 相关宿主腿 |
| 删除判据 | **0** | 见 §4 末段 |

## 7. 未执行项

- **git 提交**：本机 `git` 不在 PATH，九个 Task 的「登记」步骤与本次收口都记为未执行。
  权威账 = 本报告 + 计划文件 + 裁决文。
- **批次边界外未做**：不动任何 token 取值、不动品牌渐变与 28 枚插画、不动 Room/FSRS/备份格式/Keystore/AI 请求策略。
- **`ToggleRow`（`AppScreen.kt` ~2634 `DetailToggleRow`）与 `WorksheetSettingsScreen.OptionSwitchRow` 的深套轨道**：
  与 §3.5-3 同因（`checkedTrackColor = colors.strongSurface`），但不在本批九屏范围内，本批**未改**，登记为遗留。
  同日收尾轮已按同一口径改完并升级为全局闸（见 §11）。

## 8. 遗留议题

1. **深套开关/滑杆的同类站点还剩两处**（`DetailToggleRow`、`OptionSwitchRow`）：本批只修了语音合成页的两枚。
   建议下一轮按同一条口径（`if (LocalIsDark.current) domain.base else colors.strongSurface`）一次性收口，
   并把「深套开关轨道不许用 `strongSurface`」升级成一条全局闸（按符号扫全仓 `checkedTrackColor`）。
   → **已收口（2026-11-02）**：两处站点 + 默写纸设置页的复选/单选三枚一并改完，
   闸 = `SwitchTrackColorGuardTest`（3 用例，台账 12 枚，源码与台账双向对齐）。见 §11。
2. **`SharedComponentUniquenessRatchetTest.headerSeedRetentionReason` 已不再覆盖任何文件**：下批若新增屏，
   页头族「空集」口径会立即生效（写了 `AppScreenHeader(` 就不许进 seed），这是设计意图，不要误判为回归。
3. **`AppScreenOverlayContainerTest` 的 2× 字号那条依赖设备 `font_scale`**：单跑前必须先
   `settings put system font_scale 2.0`，跑完恢复 1.0；这是护栏的**故意**行为（不许在 1.0 下空转），
   不是 flake。建议在 CI/批处理脚本里把这条单列。
4. **`AppScreenTest` 的 2× 字号 `ComposeTimeoutException` 偶发**：单跑/整类复跑绿，与本批改动无关（§3.3）。
5. **真机数据仍为空档**（词书/进度/AI 配置被上轮误清）：目视截图里的「已学 0 / 待复习 0」是数据态；
   若要复看有数据时的版式（进度条、长书名折行），需先导入一份 `.wbpack`。
6. **提醒权限改动只有源码契约，没有真机回归用例**：`ReminderPermissionLaunchContractTest` 钉的是宿主
   源码形态；设备侧「点开关 → 授权 → 开关亮」由 Task 10 真机目视取证（第 1 条修复），
   若要长期护栏，需要在 `ReminderSettingsScreenTest` 侧造一个可注入的权限结果夹具（改夹具，不动实现）。
7. **「吸底槽与软卡同色、槽顶无边界线」是通行写法，不止导出屏一处**：本批只给导出屏补了
   `hairlineTop(colors.outline)`（§3.5-7），其它屏的吸底槽暂未扫。建议下一轮按符号扫一遍
   `color = colors.surface` 的吸底槽，凡压在 `SoftCard` 之上的都补发丝线，并升级成一条全局闸。
   → **已收口（2026-11-02）**：8 处吸底槽全部登记（7 处自铺底色补线、1 处透明豁免并写明理由），
   闸 = `StickyFooterHairlineGuardTest`（3 用例，双向台账 11 枚认领符号）。见 §11。
8. **底导第三项文案是 `AI("AI 学")`（`AppTab.kt:12`）**：2× 字号下 `uiautomator dump` 仍是「AI 学」，
   实测**不是截断**（1× 下同样如此），是既有契约文案、由 `AppTabTest` 锁住。但「AI 学」读起来像被截断的
   「AI 学习」，建议下一批评估改成「AI 学习」或「AI」（属 IA/文案变更，不在版式批次内，本批未动）。
   → **已裁决并落地（2026-11-02 收尾轮）**：用户定名「**AI 助学**」（既非「AI 学习」也非「AI」）。
   标签、无障碍播报、AI 主页 hero 标题、「AI 助学如何起作用？」折叠行（可见文案 + 可及名）、
   AI 服务空态文案全部跟到定名；判据 = `AppTabTest`（钉定名）+ `AiLearningLayoutContractTest`
   `the hero title speaks the destination name the user settled on`（旧名独立成串 = 0，定名恰 1 处）。

## 9. 下一任接手须知

- **第一步**：读本报告 + 计划文件执行状态表 + `docs/decisions/2026-11-02-batch-5-routing-and-picker-rulings.md` +
  `docs/decisions/2026-11-02-ui-closeout-selection-marks-and-ai-icons.md`（同日收尾轮的口径）；
  全量门禁现值 **278 XML / 2043 tests / 0 failures / 0 errors / 0 skipped**（本批交付时 275 / 2032，
  差额 = 收尾轮两条闸 6 用例 + 图标契约 4 用例 + 底导定名 1 用例），任何回落先查是不是漏装测试 APK（见下）。
- **装包纪律（新增坑）**：改完实现要跑 connected 时，必须
  `:app:assembleDebug :app:assembleDebugAndroidTest` → `adb install -r` **两枚** APK 再 `am instrument`；
  只跑 `compileDebugAndroidTestKotlin` 会留下旧测试 APK，首跑抛一串
  `NoSuchMethodError: No static method ...Screen(...)` 假红（本批实测 15 条）。
- **Gradle**：必须用本机 8.13（`C:\Users\steveliuyan\.gradle\wrapper\dists\gradle-8.13-bin\...\gradle.bat`），
  禁 `./gradlew`、禁 9.x；`--offline --no-daemon --no-build-cache`。
- **connected**：只用 `am instrument -e class ...`，**禁** `connectedDebugAndroidTest`（它会清真机数据）。
- **大字号**：`AppScreenOverlayContainerTest` 单跑前 `settings put system font_scale 2.0`，跑完恢复 1.0。
- **编码**：`AppScreen.kt` / `SettingsScreen.kt` / 计划文档是 CRLF；`BackupExportScreen.kt` /
  `BackupRestoreScreen.kt` / `SpeechSettingsScreen.kt` / 新测试文件是 LF；一律 UTF-8 无 BOM。
- **真机导航**：设置页「备份与恢复」在滚动后 y≈1915-2029；「导出学习数据留档」在备份中心 y≈504-672；
  行位置会随分组高度漂移，**先 `uiautomator dump` 读 bounds 再点**。

## 10. 用户确认过的决策

1. 批 5 九屏一律**零强卡**（设置/系统页没有「本屏唯一主角数字」）。
2. 选词书改 2 列色块卡、删除走**长按**（而非色块右上 `⋯`）。
3. 备份中心两条导出**不用药片**，用裸行 + 「推荐」徽标表达主次。
4. 认证导出的「配对码不复显」**不改**（认证门控是安全语义，D-11）。
5. 恢复异常的主操作是「导出本机留档」，重试降为次操作。
6. 「生成备份包」保持文字次键形态，但**贴右收窄**，不与吸底主键同宽。

## 11. 同日收尾轮（UI-only，2026-11-02）

用户看过真机后追加一条观感意见（AI 学页「可点行图标全一样」），并明确：**功能类改动全部搁置**，
本轮只把 UI 收干净（功能实现换另一条工作线）。口径与逐条理由见
`docs/decisions/2026-11-02-ui-closeout-selection-marks-and-ai-icons.md`。

### 11.1 关掉的三条

| 来源 | 处置 | 判据（新增测试） |
| --- | --- | --- |
| §8-1 深套开关/滑杆轨道 | 两处遗留站点（`AppScreen.DetailToggleRow` + 每日新增滑杆、`WorksheetSettingsScreen.OptionSwitchRow`）按 `if (LocalIsDark.current) <域色>.base else colors.strongSurface` 改完；同族再把默写纸设置页的复选 1 枚、单选 2 枚并入（原为闸内「待裁决项」） | `SwitchTrackColorGuardTest` 3 用例：无条件 `strongSurface` 站点 = 0；每站点只许两种形状；台账 12 枚双向对齐 |
| §8-7 吸底槽无边界线 | 8 处吸底槽登记（7 处自铺底色补 `hairlineTop(colors.outline)`、词卡屏 1 处透明豁免并写理由：不铺底就没有缝） | `StickyFooterHairlineGuardTest` 3 用例：扫描站点 ↔ 台账双向对齐（11 枚认领符号）；自铺底色必画线；豁免站点 `colors.surface` 计数 = 0 |
| 用户观感「图标全一样」 | 七枚 AI 功能各取一枚专属位图插画（`Newspaper`/`List`/`Speaker`/`Mic`/`Document`/`Camera`/`Image`），`AiFeatureRow` 与 AI 功能页页头统一读 `feature.icon`；两处折叠/说明行保留 `AppIcon.Spark` 当本页 AI 品牌锚点 | `AiFeatureIconContractTest` 4 用例：目录侧一枚功能一枚图标且不重复、七枚逐条钉值、站点侧 `AppIcon.Spark` = 0 |
| §8-8 底导第三项文案 | 用户定名「**AI 助学**」：`AppTab.AI("AI 助学", "AI 助学")`，AI 主页 hero 标题、「AI 助学如何起作用？」折叠行（文案 + 可及名）、AI 服务空态文案同步；「AI 学」三字退役 | `AppTabTest` 2 断言钉定名；`AiLearningLayoutContractTest.the hero title speaks the destination name the user settled on`：`"AI 助学"` 恰 1 处、`"AI 学"` 独立成串 = 0；设备侧 `AppBottomBarTest` / `AppScreenTest` / `AiLearningScreenTest` 按新文案点（`bf353dda` 实跑 9 / 1 / 18 全绿）；真机取证 `docs/design/app-tab-label-ai-zhuxue-light-20261102.png`、`app-tab-label-ai-zhuxue-dark-20261102.png`、`app-ai-home-hero-zhuxue-light-20261102.png` |

### 11.2 用户叫停、本轮不做（交接清单）

1. **aora-bot 吉祥物替换 + 「AI 状态 → emotionId」绑定**：现状 `ui/mascot/AiMascot*.kt` 可用；
   aora-bot（`https://github.com/sam70361/aora-bot`）为纯 SVG + JS 的 32 表情 Emotion Ball，
   Compose 需 Canvas 重绘或 WebView 桥接；LICENSE 未确认，接入前先登记 `docs/third-party-notices.md`。
2. **拍照翻译「原位覆盖译文」**：需 OCR 词框坐标 + 版式重排；当前 `PhotoTranslationScreen.kt` 是分块展示结构。
3. §8-6 提醒权限注入夹具（测试基建）。

### 11.3 门禁

全量 JVM：**278 XML / 2043 tests / 0 failures / 0 errors / 0 skipped**（本批交付 275 / 2032）。
新增判据均先跑出预期红再改实现（`SwitchTrackColorGuardTest` 修前点名 3 个违规站点，修后转绿）。

### 11.4 仍开放

- **深套复选/单选未做真机目视取证**：本轮只到源码闸 + JVM 门禁。理由：取值口径与同屏
  `OptionSwitchRow`、以及已取证过的 `SpeechSettingsScreen` 完全同源（深套取 Settings 域 `base`
  `#A6B0AC` 压在 `surface #17201C` 上），闸已钉住「源码里不许出现第三种取值」；要复看实物需先
  走「我的 → 设置 → 导出默写纸」并切深套，当前真机导航路径未打通（`uiautomator dump` 未见设置入口，
  需先滚到页底）。已装新包：`app-debug.apk` 328.3MB（`adb install -r` 于 `bf353dda` 成功）。
- §8-8 底导第三项文案 **已收口（2026-11-02）**：用户定名「AI 助学」，标签/播报/屏名/折叠行/空态一并改完，
  判据见 §11.1 末行。原挂账「改『AI 学习』还是『AI』」作废——用户选的是第三个答案。
- §8-3 / §8-4 两条大字号用例依赖设备 `font_scale` 与偶发 `ComposeTimeoutException`（环境类，非版式缺陷）。
- §8-5 真机数据仍为空档，有数据时的版式（进度条、长书名折行）需先导入 `.wbpack` 才能复看。

