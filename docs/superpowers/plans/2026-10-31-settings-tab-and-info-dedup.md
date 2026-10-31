# 设置独立为第五栏 + 信息入口去重（2026-10-31）

**状态：** 进行中（用户已拍板「结构性重排（新批次）」；AI 配置入口「暂不加」）
**依据：** `docs/decisions/2026-10-31-settings-as-fifth-tab.md`、
`docs/design/2026-10-31-information-architecture-audit.md`、
`docs/superpowers/specs/2026-09-23-ai-learning-tab-design.md`（2026-10-31 修订）
**门禁：** `assembleDebug → testDebugUnitTest → compileDebugAndroidTestKotlin`（`--offline`），
connected 只在 `emulator-5574`（`adb install -r` + `am instrument`），真机 `bf353dda` 只做手工 `install -r` 目视。

## 目标

1. 底导五栏：学习 / 阅读 / AI 助学 / 我的 / 设置，顺序固定。
2. 「我的」只放个人进度（档案头 + 三格统计 + 学习记录），不再内联设置。
3. 「设置」成为独立一级页（页头「设置」+ 四组），承载全部调整入口。
4. 三处重复收口：许可唯一入口在关于页；备份唯一入口在设置；关于页死行删除。
5. 不加 AI 配置入口（用户裁定）。

## 任务

### Task 1：`AppTab` 五栏（JVM TDD）

- 文件：`app/src/main/java/.../ui/AppTab.kt`、`app/src/test/.../ui/AppTabTest.kt`
- 先改测试到五栏：`LEARNING, READING, AI, PROFILE, SETTINGS`；锁顺序、标签唯一、`PROFILE`=「我的」、
  `SETTINGS`=「设置」；新断言：五格标签互不相同且都不为空。
- 再改枚举；`SETTINGS` 的原有「我的」语义迁给 `PROFILE`。
- 红→绿证据：`AppTabTest` 先失败（枚举仍是四栏），改枚举后通过。

### Task 2：`AppBottomBar` 五格

- 文件：`app/src/main/java/.../ui/AppBottomBar.kt`、`app/src/test/.../ui/AppBottomBarContractTest.kt`、
  `app/src/androidTest/.../ui/AppBottomBarTest.kt`
- `glyph` 映射：`PROFILE -> Person`、`SETTINGS -> Gear`；五格均分（`weight(1f)`），`heightIn(min = 64.dp)` 不变。
- 契约测试：节点 `app_tab_profile` / `app_tab_settings` 同时存在；五枚 `Role.Tab`；选中态语义不变。
- 视觉核验：360dp/411dp 两档 labelSmall 下「AI 助学」不折行；若折行，记录并回问用户是否允许缩为「AI」
  （不得自行改标签）。

### Task 3：`ProfileScreen` 摘掉设置槽

- 文件：`ProfileScreen.kt`、`ProfilePageContractTest.kt`、`ProfileScreenTest.kt`、`ProfilePageHomeContractTest.kt`
- 删除 `settingsContent: @Composable ColumnScope.() -> Unit` 参数与 `settingsContent()` 调用；
  页尾到「学习记录」行为止。
- 测试：保留三格统计与学习记录断言；新增「不得再出现设置分组」的反向断言
  （源码扫描 `ProfileScreen.kt` 不含 `settingsContent`；connected 断言 `settings_group_learning` 不存在）。

### Task 4：设置独立根页 + 删许可行

- 文件：`SettingsScreen.kt`、新增/改造 `SettingsPage`（可用 `SettingsScreen.kt` 内的新组合函数，
  页头「设置」+ 副标题）、`SettingsScreenTest.kt`、`SettingsPrototypeAlignmentContractTest.kt`、
  `SettingsNoDeveloperJargonTest.kt`
- `SettingsGroups` 台账：删除「开源许可与隐私说明」行（`settings_open_licenses`），「外观与关于」组只剩
  主题卡 + 关于；行数契约从「9 行」改为「8 行」（含 tag 清单的测试全部同步）。
- 新根页：`testTag("settings_screen")`，`AppScreenHeader(title = "设置")`，滚动容器只有一个；
  宿主不再把设置塞进 `ProfileScreen`。
- 保留 `SettingsGroupsContent` 作为内容槽供测试直挂，避免出现两份页头。

### Task 5：`AppScreen` 接线

- 文件：`AppScreen.kt`
- `when (selectedTab)` 增加 `AppTab.PROFILE -> ProfileScreen(...)` 与 `AppTab.SETTINGS -> SettingsPage(...)`。
- 学习页齿轮：`onOpenSettings = { selectedTab = AppTab.SETTINGS }`（本来就是，确认语义换名后仍成立）。
- 保存逻辑：`rememberSaveable(state.profile.id) { mutableStateOf(AppTab.LEARNING) }`，
  枚举序列化兼容（改名会导致旧 Bundle 反序列化失败——`rememberSaveable` 按枚举名恢复，旧进程恢复场景可接受：
  桌面重启而非升级迁移；在报告中如实记录）。
- `AppScreenProductionWiringTest` 需要把新增/改名的参数（若有）接到 MainActivity。

### Task 6：`AboutScreen` 去重

- 文件：`AboutScreen.kt`、`AppScreen.kt`（关于页回调）、`AboutScreenContractTest.kt`、`AboutScreenTest.kt`
- 删除「备份与恢复」行（连同 `onOpenBackup` 参数）；
- 删除「隐私说明」死行；
- 「开源许可与来源」→「开源许可与隐私说明」（与许可页标题、设置侧旧名逐字一致）；
- 保留应用块、隐私一句话、致谢；关于页只剩一条说明行。
- 测试：`about_backup_row` 不存在、`about_privacy_row` 不存在、许可行标题逐字断言；
  连接完成后点许可行进 `LicensesScreen`。

### Task 7：门禁与设备验证

1. `assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin --offline`，JVM 全绿；
   JVM 用例数变化必须能逐条解释（删掉的设置槽断言 −N，新增五栏/设置页/关于页断言 +M）。
2. `emulator-5574`：`install -r` + `am instrument` 跑
   `AppBottomBarTest`、`AppScreenTest`、`ProfileScreenTest`、`SettingsScreenTest`、`AboutScreenTest`、
   `AppScreenOverlayContainerTest`，以及受影响的其余类；
   1.0 与 2.0 两档字号各跑一次需要的类。
3. 模拟器截图：五栏底导（浅/深）、我的页瘦身、设置页、关于页、许可页入口。
4. 真机 `bf353dda`：`install -r` 后手工目视五栏与关于页；不跑 instrument。
5. 报告：`docs/handoff/2026-10-31-settings-tab-ia-dedup-report.md`（按 REPORT-TEMPLATE 七节）。

### Task 8：推送（待代理）

- 用户开启代理后 `git push origin stage-5-ui-r2-learning-path`，`ls-remote` 核验；
  推送前 `git remote get-url origin` 确认仍是 GitHub 远端。

## 验收（本批）

- [ ] JVM 全绿，且用例数变化可解释
- [ ] connected 六类全绿（模拟器），1.0/2.0 双档
- [ ] 五栏标签与顺序在真机 `uiautomator dump` 可读
- [ ] 我的页不再出现设置分组；设置页只有一个滚动容器与一个页头
- [ ] 关于页只剩一条说明入口，点开进许可页；备份只能从设置进
- [ ] 学习页齿轮落到设置 tab
- [ ] 真机复装、数据未丢
