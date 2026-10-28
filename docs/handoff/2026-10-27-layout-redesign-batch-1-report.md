# 2026-10-27 版式重做批 1（骨架）工作报告

## 1. 本次目标

用户原话：「照这套铺 35 屏（实产 33 屏），分批做」——按
`docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md` 与
`docs/superpowers/plans/2026-10-25-layout-redesign-batch-1-skeleton.md`，
把批 1 的 9 屏骨架（类型换档 + 三档容器 + 底栏四格 + 学习/词库/我的/阅读/打卡/学习记录）
铺完并过全量门禁。

## 2. 完成了什么

计划 Task 1–8 的代码面已全部落盘（Task 9 见第 3、5 节）：

| Task | 落点 | 实测状态 |
|---|---|---|
| 1 类型换档 + 两枚 strong token | `ui/theme/AppType.kt`、`ui/theme/AppColors.kt` | 完成 |
| 2 三档容器 | 新建 `ui/components/Containers.kt`（`StrongCard`/`SoftCard`/`BareRow` + 共享 `hairlineTop`）；`EntryRow.kt` 迁出发丝线实现 | 完成 |
| 3 底栏四格 + 「我的」 | `ui/AppTab.kt`、`ui/AppBottomBar.kt`（`AppIcon.Person`） | 完成 |
| 4 学习页重排 | `ui/TodayPlanScreen.kt` 整屏重写（页头 + 强档卡 + 环形进度 + 两条细进度 + 单一 CTA + 分组小标题 + 入口行组） | 完成 |
| 5 词库页分段落壳 | 新建 `ui/WordBankScreen.kt`（120 行壳）；`MyWordsScreen.kt`/`WordBookWordsScreen.kt` 收 `showHeader = false`；`AppScreen.kt` 接线四段 | 完成 |
| 6 我的页 + 设置抽离 | 新建 `ui/ProfileScreen.kt`；`SettingsScreen.kt` 抽出 `SettingsGroupsContent`（原 `SettingsScreen` 保留为 connected 装载缝） | 完成 |
| 7 阅读主页页头收编 | `ui/ReadingAccessScreen.kt` 改用 `AppScreenHeader`；`SharedComponentUniquenessRatchetTest` 页头 seed 摘掉该类并清掉 headline 读点 | 完成 |
| 8 学习记录/打卡改裸行 | `ui/LearningRecordsScreen.kt`（`WordRow(topDivider = index > 0)`、`itemsIndexed`、零行距）；`ui/CheckInScreen.kt`（Display 唯一 + 删完成度环 + 四枚 `SoftCard`）；`components/WordRow.kt` 加 `topDivider` | 完成 |
| 9 全量门禁 + 模拟器 connected + 真机目视 | 见第 3 节 | 完成（真机第 6 项部分，见第 5 节） |

新增/修改的文档：本报告；计划文件末尾追加「执行状态」小节（见第 4 节的 checkbox 说明）。

## 3. 验证记录

**JVM 全量（离线、8.13、无 daemon、无构建缓存）**

```
:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
  verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
BUILD SUCCESSFUL in 25s
```

`app/build/test-results/testDebugUnitTest`：249 个 XML，**1831 tests / 0 failures / 0 errors / 0 skipped**。
（计划里写的批前基线是 1748 枚，实测 +83。）

**模拟器 connected（`emulator-5574`，`am instrument`，未清数据）**

```
am instrument -w -e class TodayPlanScreenTest,WordBankScreenTest,ProfileScreenTest,
  CheckInScreenTest,LearningRecordsScreenTest,ReadingAccessScreenTest,AppBottomBarTest
Time: 65.541
OK (68 tests)
```

**真机目视（`bf353dda`，`install -r` 只装不打）**

截图落 `.git/qa-scratch/`（不入库）。

| # | 项 | 结论 | 证据 |
|---|---|---|---|
| 1 | 底栏四格：学习/阅读/AI 学/我的，第四枚人形图标 | 通过 | `b1_learn.png`；`uiautomator dump` 实测四格 content-desc = 学习 / 阅读 / AI 学 / 我的 |
| 2 | 学习页：搜索框常驻、强档卡深底、大数字、两条细进度条、CTA 在卡内 | 通过 | `b1_learn.png`。真机只有「开始学习」态；「去读今日文章」态需先完成今日计划，改由 `TodayPlanScreenTest`（connected 24 项绿）+ `TodayPlanNumberAuthorityTest` 兜底 |
| 3 | 词库页：四分段可切，词书段字母 Rail + 筛选条正常，其余段各只有一段内容 | 通过 | `b1_wordbank.png`（词书：Rail A–G + 筛选 4 枚 + 词条列表）、`bank_new.png`（生词本 0 个）、`bank_fav.png`（收藏 0 个） |
| 4 | 我的页：昵称 + 连续学习 N 天；三格统计可点；学习记录入口可进入；四组九行设置与旧页一致 | 通过 | `b1_profile.png`、`profile_scrolled.png`；点「连续天数」进打卡页（`b1_checkin.png`），点「学习记录」进记录页（`b1_records.png`） |
| 5 | 打卡页：连续天数是唯一大数字、完成度环消失、周柱状图与月历仍在 | 通过 | `b1_checkin.png` |
| 6 | 学习记录：行间一条细分隔线，右列只「认识/模糊/不认识」，不可用行说明原因 | **部分**：本机 0 条记录（数据此前已被清空），行形态无法目视；由 `LearningRecordsScreenTest`（connected 4 项绿）与 `LearningRecordsRowContractTest` 覆盖 | 见第 5 节遗留 |
| 7 | 深色模式四屏都用新 token，强档卡与背景分层 | 通过 | `dark_learn.png`（强档卡 `strongSurface` 明度高于 `background`）、`dark_reading.png`、`dark_profile.png`、`dark_profile_top.png` |
| 8 | 系统字号最大不截断、不叠字 | **3/4 通过**：学习、阅读、我的 在 `font_scale = 2.0` 下正常（`big_learn.png`、`big_reading.png`、`big_profile.png`）；**AI 页不通过**（`big_ai.png`：功能行标题断成两行、摘要被裁） | AI 页仍是旧版式，归批 2 |

## 4. 改期望值清单（逐条「旧值 → 新值 + 依据」，供审核席核对有没有被偷偷放宽）

| 契约/用例 | 旧值 | 新值 | 依据 |
|---|---|---|---|
| `MaterialThemeAssemblyContractTest`（`LocalContentColor`） | 「全仓恰一处」 | 登记制：三枚 token local 仍恰一处，`LocalContentColor` 登记 2 处（根装配 1 + `Containers.kt` 的 `StrongCard` 1） | 强档卡需覆盖深底前景；计划「现场移交」第 2 笔 |
| `SharedComponentGeometryContractTest`（发丝线锚） | 读 `EntryRow.kt` | 改读 `Containers.kt` 的 `hairlineTop`，并在 `EntryRow.kt` 留零计数反向锚 | Task 2 把画线实现迁出 |
| `MyWordsScreenContractTest` | 锚 `today_plan_open_my_words` / `today_plan_open_wordbook_words` / `myWordsSummaryOf(` | 改指 `today_plan_open_word_bank`（3 处） | §3.2 五枚移出 tag；Task 5 |
| `AppScreenTest`（connected）入口 9 处 | `today_plan_open_wordbook_words` / `today_plan_open_my_words` | `today_plan_open_word_bank`（8 处）+ 目的地断言 `word_bank_screen`（5 处） | 同上 |
| `AppScreenTest`（connected）打卡深链 1 处 | `today_plan_open_check_in` | 我的页统计卡深链 | Task 6 |
| `LearningRecordsRowContractTest` | `status = "反馈：${row.feedback.label()}"` | `status = row.feedback.label()`，并断言「反馈：」不得残留 | Task 8（11 枚汉字把右列挤成省略号） |
| `CheckInScreenTest`（connected） | 三枚完成度锚 + `check_in_totals_text` 文本断言 | 删三枚完成度锚；改断 `check_in_streak_days` 存在 + `check_in_streak_text`（3 处） | Task 8（重复的环整块删除） |
| `SharedComponentUniquenessRatchetTest`（页头 seed） | seed 含 `ReadingAccessScreen.kt` | 摘除该行并清掉其 headline 读点（现计数 0） | Task 7；该文件已改用 `AppScreenHeader` |
| `TodayPlanScreen` 五枚入口 tag | `today_plan_open_wordbook_words` / `_my_words` / `_learning_records` / `_check_in` / `_setup_entry` | 全部离场（源码实测计数：前者的残留 1 处为注释；其余 0） | §3.2 |

以上每一条都不是「放宽到 ≥ / 删断言」，而是把钉住的旧锚换成新锚；`LocalContentColor` 那条从「恰一处」改成「逐条登记 + 新增第三处即红」。

## 5. 未完成 / 遗留问题

1. **Git 不可用**：本机 `git` 不在 PATH，`C:\Program Files\Git\cmd\git.exe`、`C:\Program Files (x86)\Git\cmd\git.exe`、
   `%LOCALAPPDATA%\Programs\Git\cmd\git.exe` 均不存在（仓里只有 `.git` 目录）。
   → 计划里 Task 1–8 的 9 枚 `git commit` 与 Task 9 的报告提交**都没有执行**，没有提交哈希；
   计划文件的 45 个 checkbox 也**没有逐格勾选**，实际状态以本报告第 2 节与计划末尾「执行状态」为准。
2. **AI 页（`AiLearningScreen.kt` / `AiFeatureScreen.kt`）仍是旧版式**：M3 `Card` 汤 + 品牌渐变大卡 + 各自带描边的功能卡；
   2.0 字号下功能行标题断行、摘要被裁（`big_ai.png`）。归批 2，改造时须同时满足
   `AiBadgeContrastFixTest`、`DarkTokenConsumptionRatchetTest`（`"AiLearningScreen.kt" to 2`）、
   `RawColorLiteralRatchetTest`、`MintTokenIsComposableContractTest`。
3. **页头收编剩 11 屏**（Task 35）：如 `LearningRecordsScreen.kt` 现在仍是「← 返回」文字页头（`b1_records.png` 可见）。
4. **词书段词条仍是逐条白卡**（`b1_wordbank.png`）：Task 5 只做分段落壳，内层列表改裸行/`WordRow` 归批 2 词库内层。
5. **学习记录行形态无目视证据**：本机学习数据为空，需在有记录的设备/账号上补看一次。
6. **打卡页「本周学习」在 0 数据时留一段空白**（柱状图区高度固定，`b1_checkin.png`）。
7. 结转（计划原有）：`SettingsScreen` 作为 connected 装载缝保留；Task 36 我的词 `LazyColumn`；Task 40 大字号四族覆盖。

## 6. 下一任接手须知

- **门禁命令**（必须用本机 8.13，禁用 `./gradlew` 与 9.x）：
  ```powershell
  $g = Get-ChildItem -Path "$HOME\.gradle\wrapper\dists\gradle-8.13-bin" -Recurse -Filter gradle.bat | Select-Object -First 1
  & $g.FullName :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin `
      verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
  ```
- **设备**：真机 `bf353dda`（1080×2340 / 440dpi）+ 模拟器 `emulator-5574`。
  真机曾自行转到横屏，先 `adb shell settings put system accelerometer_rotation 0` +
  `settings put system user_rotation 0` 再截图。
  `adb` 版本较老（1.0.26），**不支持 `exec-out screencap`**（PowerShell 重定向会得到 0 字节），
  一律 `adb shell screencap -p /sdcard/x.png` + `adb pull`。
- **测字号后必须复位**：`adb shell settings put system font_scale 1.0`（本次已复位并实测读回 1.0）。
- **真机测试禁用 `connectedDebugAndroidTest`**（曾清空真机词书/进度/AI 配置，不可恢复）；用 `am instrument`。
- 底栏四个 tab 的真机点击坐标（竖屏）：学习 (135,2160) / 阅读 (405,2160) / AI 学 (675,2160) / 我的 (945,2160)；
  `uiautomator dump` 读 bounds 更稳（本机 `ui.xml` 的坐标是设备像素，不是截图缩放像素）。
- 第一步建议：先跑上面的门禁确认仍绿，再开批 2（AI 页 + 词库内层 + 页头收编剩余 11 屏）。

## 7. 用户确认过的决策

- 「照这套铺 35 屏（实产 33 屏），分批做」——按规范分批推进，不一次性重排全 App。
- 插画图标走 28 枚位图母版 + 5 枚矢量导航符号的混合路线（本轮已完成，不在批 1 范围）。
- 配色走「轻松档」七域色板；品牌渐变三色固定不动。
