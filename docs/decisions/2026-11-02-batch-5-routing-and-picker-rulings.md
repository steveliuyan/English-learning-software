# 2026-11-02 批 5（设置与系统）路由与选择器类裁决

计划文档：`docs/superpowers/plans/2026-11-02-layout-redesign-batch-5-settings-and-system.md`
交付报告：`docs/handoff/2026-11-02-layout-redesign-batch-5-report.md`
关联既有裁决：`docs/decisions/2026-10-31-ui-review-three-rulings.md`（D-11 揭示门）

本文件的用途：把批 5 实施中**偏离计划原文**的每一处连因写清，避免后来者把「现在的写法」当成
「当初的决定」。每条格式统一为：**原判 → 事实 → 裁定 → 留因**。

---

## 1. 恢复异常屏的「导出本机留档」路由（对账 1）

**原判**：把覆盖层判断提到 `NeedsRecovery` 之前，并在恢复态置 `showBackupExport = true`。

**事实**：`showBackupExport` 是 `AppUiState.Ready ->` 分支内的局部
`rememberSaveable(state.profile.id)`（`AppScreen.kt:197`），而 `NeedsRecovery` 是同一个 `when` 的
**另一支**。要把它提到 `when` 之外，就必须去掉 profile 键——那是状态机改动，超出「只改版式与信息架构」的批次边界。

**裁定**：改**分支内层覆盖**。`NeedsRecovery` 分支自持 `showRecoveryExport`；为真时整屏让位给
`BackupExportScreen`，返回键只把布尔翻回 false，不触发任何状态迁移。效果与「覆盖层优先」等价，
状态机、Room、迁移路径零改动。

**留因**：等价性判据不是「看着一样」，而是行为：点「导出本机留档」→ 出现 `backup_export_screen` →
返回回到恢复态（`AppScreenTest#recoveryScreenOffersTheArchiveExportAndReachesTheExportScreen`）。
副作用一处：`backupExportViewModel == null` 时不再被拦一道，导出屏自己走只读降级分支
（`UnavailableContent`）——比让主键「按了什么也没发生」更诚实，故保留。

---

## 2. 认证导出屏的「配对码不复显」（对账 6，Task 7）

**原判**（计划 Task 7 Step 1 第 3 条）：`backup_pairing_export_reveal_qr` 退役为 0；
配对码绑定 `Completed` 分支，`Idle` 分支不出码；「重来」清空 `rememberSaveable` 里的码串。

**事实**：该写法与 **D-11**（AC4-08：`Done` 阶段经再次系统认证**重显同一配对码**，不重发、不延长 30 分钟）
直接冲突。认证门控属安全语义，不属版式批次可改范围——按批次边界，「让码只在 Completed 出现」等于
把安全模型改了。

**裁定**：改为**正向钉住**（不是删判据）：
1. `backup_pairing_export_reveal_qr` 恰 1 处；
2. 该调用点**向前最近的** `runWithMigrationAuth(` 必须存在（即揭示动作必须发生在认证之后）；
3. `onPairingRevealed` 必须回填（拿到码要交回 VM）；
4. `QrCodeDisplay(` 调用点恰 1（不分叉出第二个出码口）。

同时保留 `PairingExportQrFlowContractTest` 全绿；**未**采纳「重来后 QR 为 0」这一条（该设计已不采纳）。

**留因**：同一件事（不泄露码）有两条相反的落点——「不显示旧码」与「只在重新认证后显示旧码」。
后者是产品/安全已定的语义（D-11），批次只许加固判据，不许反转语义。

---

## 3. 选词书：滑删 → 长按删除，`LazyVerticalGrid` → `chunked(2)`（对账 4，Task 9）

> **本节 §3 的「两列色块」版面已作废（2026-10-29）**：逐屏对照原型后由用户改判为通栏 `.pick.pick-radio` 行列表，
> 见 `docs/decisions/2026-10-29-word-book-picker-rows-supersede-tiles.md`。**本节两条结论仍然有效**——
> 「删除走长按 + `customActions`「删除词书」、内置册不挂」「不引 `LazyVerticalGrid`（根已 `Column(verticalScroll)`，
> 嵌懒网格抛无限高约束）」，这两条随版面一起搬到了行列表上；作废的只有「列数 = 2、行内等宽、缝宽 = `WordBookTileGap`、
> 单数行半宽」那四条几何约束与色块容器本身。下面原文按当时事实保留，不覆写。

**原判**：2 列色块卡，`LazyVerticalGrid(GridCells.Fixed(2))`；删除入口按计划推荐为长按。

**事实两点**：
1. 启动/建档链路根节点已是 `Column(verticalScroll)`（`AppScreen.kt` 的建档分支），在无限高约束里嵌
   `LazyVerticalGrid` 会直接抛约束异常——不是「不必要的复杂度」，是**跑不起来**。
2. 旧实现是 `SwipeToDismissBox` 滑删 + 每张卡一个 `⋯`；两列色块下，滑与整格点选抢手势。

**裁定**：
- 网格改为 `const val WORD_BOOK_TILE_COLUMNS = 2` + `chunked(WORD_BOOK_TILE_COLUMNS)` + 行内 `weight(1f)`，
  单数行补 `Spacer` 占位；契约同时钉 `LazyVerticalGrid(` 与 `GridCells.` **必须为 0**。
- 交互改 `combinedClickable` 一槽两义：**点＝选中、长按＝请求删除**；同节点保留
  `customActions = listOf(CustomAccessibilityAction("删除词书"))`、CD「选择词书 X」、`stateDescription`
  （`if (selected) "已选" else "未选"`，F5-03）；宿主侧 `irreversible = true` / `confirmLabel = "删除"` 逐字未动。
- 内置词书（不可删）不挂长按与 `customActions`，`WordBookTileTest` 为此多一条判据（判据不下调）。

**留因**：放弃懒网格不是放松。「列数 = 2、行内等宽、缝宽 = `WordBookTileGap`、单数行半宽」四条几何
约束都进了 `WordbookPickerHeaderTest`；滑删放弃后，`wordbook_swipe_*` 全部配上「必须为 0」的正向对偶，
旧路径不会「悄悄还能用」。

---

## 4. 备份中心两条导出：药片 → 裸行 + 「推荐」徽标（对账 3/5，Task 4）

**原判**：两条导出各一枚药片，主出口用实底、次出口用描边。

**事实**：13 字标题（「导出学习数据留档」）配药片时，药片在真机 411dp 宽下折行、行高被顶到约 90dp；
实底药片还会与子屏真正的吸底主键抢视线——备份中心是**分岔页**，不是动作页。

**裁定**：两条都退成 `BareRow`（裸行），主出口靠行尾 `"推荐"` 徽标表达优先级；危险区沉底为最后一段，
眉标「不可撤销」取 `colors.error`。`BackupEntryUnificationTest` 增设
`theRecommendedExportComesFirstAndTheDangerRowIsLast`（徽标按 `useUnmergedTree` 取证 +
bounds 钉「留档在认证之上」「清空是最后一枚入口」）。

**留因**：一屏一个主操作是批 1 立的骨架；分岔页的主操作是**子屏**的吸底键。备份中心若也放实底药片，
会变成「一屏两个主操作」——这是规范冲突，按规范办。

---

## 5. 学习提醒：时间选择改 M3 `TimePicker`，加载失败补 `retry()`（Task 2）

**原判**：时间控件换 Material `TimePicker`；自搓九宫格 `TimeGrid` 退役。

**事实**：Compose Material3 **没有** `TimePickerDialog`（`material3` 只提供 `TimePicker` / `TimeInput`
作为内容）。计划写的是「改造为时间选择对话框」，未指定容器。

**裁定**：`AlertDialog` 承载 `TimePicker`（`rememberTimePickerState`），标题「提醒时间」，
两枚药片「取消 / 确定」；选中结果经 VM 写回同一偏好（数据层零改动）。

**留因**：JVM 契约只能按符号判（`TimePicker(` 恰 1、`TimeGrid` 为 0），对话框行为由 connected 用例覆盖
（打开 → 选时 → 确定 → 行文案变）；2× 字号下的表盘可读性由真机截图取证
（`docs/design/app-batch5-font2x-timepicker-20261102.png`）。

附带一条实现裁定：`ReminderSettingsViewModel.load()` 有「只在 `loading == true` 时落结果」的守卫，
直接让重试键调 `load()` 是**假按钮**。故新增 `retry()`（先置 `loading` 再 `load()`），
并给 VM 补 2 条用例——这是本批唯一新增的公开 VM 方法。

---

## 6. 语音合成：语速滑杆要新 VM 入参（对账 5，Task 1）

**原判**：语速从「点按循环 + 布尔文字」改成滑杆 + 数值读数。

**事实**：旧 VM 只有 `cycleSpeechRate()`（无参数循环档位），滑杆需要一个带参入口。

**裁定**：新增 `setSpeechRate(Float)`，落点仍是同一偏好键、同一张 `SpeechRateSteps` 档位表
（吸附到最近档）；`cycleSpeechRate()` 保留（另有调用点）。试听区从常驻玻璃卡降为一条 `BareRow`
+ `if (previewExpanded)`，**`PreviewCard` 本体与其两枚形参一字未动**（`GlassPanelInkPairingContractTest` /
`GlassPanelThemeInkTest` 的前提），connected 夹具只改成「先点 `speech_preview_toggle` 展开」。

**留因**：把控件形态的变更压在「外壳」上、不碰玻璃卡本体，是为了不让一次版式调整连带改掉
主题墨色配对的护栏；否则下一轮深套墨色回归时会找不到基线。

---

## 7. 目视阶段的三条实现裁定（Task 10）

事故式发现，逐条留因，供下批直接沿用口径。

### 7.1 提醒权限：`ActivityCompat.requestPermissions` → `rememberLauncherForActivityResult`

**事实**：宿主把 `notificationGranted` 当组合期快照捕获进 `onEnableRequested`，且「允许」回调不存在
→ 用户授了权，开关仍不动，退出本页再进来才显示已开启（真机复现）。
**裁定**：改 `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`，
回调里 `if (granted) reminderSettingsViewModel?.setEnabled(true)`；删请求码常量、
`ActivityCompat.requestPermissions` 与 `onRequestPermissionsResult`。
新增 `ReminderPermissionLaunchContractTest`（4 条），并做**反空转探针**（把回调体改空 → 立即判红）。
**留因**：权限结果只在 launcher 回调里可靠；组合期布尔只代表「此刻有没有权限」，不能代表「用户刚同意」。

### 7.2 深套开关 / 滑杆：`colors.strongSurface` → 深套取 `domain.base`

**事实**：`checkedTrackColor = colors.strongSurface`（深套 `#22332C`）压在 `surface`（`#17201C`）上，
只差一档，真机几乎看不见。
**裁定**：两处改 `if (LocalIsDark.current) domain.base else colors.strongSurface`——**浅套取值逐字不变**，
深套改用域色档。
**留因 & 遗留**：同因站点还剩 `AppScreen.kt` 的 `DetailToggleRow`（~:2634）与
`WorksheetSettingsScreen.OptionSwitchRow`（~:379），不在本批九屏内，**未改**，已登记为遗留；
建议下批升级为「`checkedTrackColor` 全仓扫描」的全局闸。

### 7.3 导出屏吸底槽与软卡同色 → 槽顶补发丝线

**事实**：吸底是 `Surface(color = colors.surface)`，与 `SoftCard` 同色；2× 字号下「包含内容」长文
正好断在槽沿，读成「卡片内容被截断」。
**裁定**：槽 `Modifier` 链首加 `hairlineTop(colors.outline)`（复用 `Containers.kt` 的共享助手，
不新造组件）；契约在 `BackupExportLayoutContractTest` 第 ④ 条追加一笔「吸底槽窗口内必须出现
`hairlineTop(`」，先红（首跑 FAILED）后绿（7/7）。
**留因**：这是**2× 字号才暴露**的层级问题，1× 下同色反而显得干净——所以不加「1× 也要有线」的判据，
只钉「槽顶必须有边界线」这一层语义。
**遗留**：其它屏的吸底槽未扫（见报告 §8-7）。

---

## 8. 一句话总结

本批偏离计划原文的只有 7 处，且**没有一处是放宽判据**：三处是把「做不到的实现」换成等价落点
（1/3/5），两处是撞上既有安全/规范裁决后改成正向钉住（2/4），两处是目视暴露的真实缺陷
（7.1/7.2），一处是大字号暴露的层级缺陷（7.3）。每处的判据都留了「旧写法塞回去必须判不到」的对偶，
全批没有 `assertTrue(true)`、没有把断言放宽成 `>=`、没有删掉既有断言。
