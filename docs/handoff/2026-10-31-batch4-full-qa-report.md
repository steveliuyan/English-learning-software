# 2026-10-31 批 4 全量 QA 工作报告

计划文档：`docs/superpowers/plans/2026-10-29-ui-prototype-alignment.md`（批次表第 4 行：深浅两套、fontScale 2.0、
TalkBack、动效减弱、真机走查；验收 = 门禁 + 真机清单）。
分支：`stage-5-ui-r2-learning-path`。**本轮零 main 源码改动**：只改 5 个 connected 测试类 + 计划台账 + 本报告。

## 1. 本次目标

用户在「推送被代理阻塞」期间拍板「开始批 4 全量 QA（推荐）」：把 UI 原型对齐按批次表第 4 行做全量 QA，
交付门禁结果与真机走查清单。

## 2. 完成了什么

1. **修绿 5 个 connected 测试类（原 10 条失败 → 0）**，全部是「期望值随生产/原型变更」或「测试自身的采样/可达性缺陷」，
   没有一条是放宽判据：
   | 类 | 原失败 | 处置 | 依据 |
   |---|---|---|---|
   | `SetupScreenScreenshotTest` | 1（旧文案断言） | 断言改读原型批 2 裁决后的副标题「只为把每天的量定得刚好，随时可以改。」+ 眉题「第 1 步 / 共 2 步」 | 原型 `sOnboarding1`（`assets/app.js:1444-1446`）；**期望值变更**，测试内已写「为什么是期望值变化而非放宽」 |
   | `GlassPanelThemeInkTest` | 4（像素读数 ≠ 期望） | 卡底期望 `surface` → `tint`；浅套墨色反例改用深套 `AiSpeech.deep` | 实测像素 `#E8F2EA / #1C2A24 / #263F36 / #1C2A24` 与 `tint` 逐字相同；`onMint` 已是 brandDeep 别名，旧反例自相矛盾 |
   | `PillButtonTest` | 1（负例自撞） | 负例改钉**历史字面量** `#188F76`（4.016 缺陷墨） | 同上：现 token 下「活 token 反例」不可能同时成立 |
   | `GlassDialogThemeInkTest` | 1（`#E6F8F8F8` vs `#E0FFFFFF`，Δ7 > 6） | 采样分层：alpha 腿取全像素众数、RGB/墨色/对比度腿取「alpha 恰为填充值」的纯填充像素 | 临时直方图探针实证纯填充 `#E0FFFFFF` 仍存 14781 px；**容忍度未放宽**（fill alpha 0xEF→0xE0 使 24dp 阴影透出翻倍） |
   | `MyWordsScreenTest` | 2（仅 `font_scale 2.0` 下） | ①三行先 `performScrollTo`；②行点击改为行体左段注入（`click(Offset(40f, centerY))`）；③页尾提示条位置腿改量未裁切根坐标（`getUnclippedBoundsInRoot`）+ 前置 `performScrollTo`/`assertIsDisplayed` | 2.0 档：行不在视口 → 点击落窗外；行内动作药片吃掉大半行宽 → 点行中心会落在药片上（实测中心 x=540）；提示条整枚在根窗口外被裁成 `Rect.Zero`（实测 `0,0,0,0`，末段仍为 `53,1465,1027,1731`）。**断言本体一字未动** |
2. **模拟器侧 QA 证据**（`emulator-5574`，API 30 / 2GB，全程 `install -r` + `am instrument`，未用 `connectedDebugAndroidTest`）：
   - `com.example.englishlearning.ui` 全包 1.0 档：**561 条 / 失败 3**，3 条全为必须 2.0 档的双字号用例（该档下同类 **OK (6 tests)**）；
   - fontScale 2.0 冒烟 6 类 134 条：`AppScreenTest` 58 · `WordBankScreenTest` 4 · `TodayPlanScreenTest` 27 · `WordCardScreenTest` 22 · `MyWordsScreenTest` 17 · `AppScreenOverlayContainerTest` 6，全绿；
   - 动效减弱：`animator_duration_scale 0` → `TodayPlanScreenTest` **OK (27 tests)**；
   - 深色两套：组件/像素层在册（见 §3），36 屏逐屏深色截图无 golden → 归真机目视；
   - TalkBack：模拟器无 TalkBack 包，语义层由 connected 语义断言承重 → 实读归真机。
3. **文档**：计划新增「批 4 全量 QA 台账」11 行；迁移策略「批 4 删除兼容字段」按实跑改口（五字段未删，计数在案）；
   本报告附 §8 真机走查清单；进度简报状态头更新。
4. **环境复原**：`font_scale` 实测 `1.0`；`animator_duration_scale` 删除后实测 `null`（开工前同为 `null`）。
5. **真机清单可代理项在模拟器代跑**（§9）：今日学习 / 词卡三档 / 今日完成 / 我的词 / 设置与关于 / 深色 / 2.0
   逐屏目视通过；未配 AI 时生成入口给出可行动引导。新发现 F-1 对比度缺陷（深色 1.10:1）与 F-2~F-5 四条
   大字号/文案问题，均登记待下一轮开单，批 4 不改源码。

## 3. 验证记录

### 3.1 门禁（本机 Gradle 8.13，`--offline`）

- `assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin` → **BUILD SUCCESSFUL（61 tasks）**。
- JVM：`testDebugUnitTest --rerun` 强制重跑 → **BUILD SUCCESSFUL in 30s**；按 XML 聚合
  **tests 2221 · failures 0 · errors 0 · skipped 0**（与 10-30 收尾基线 `2221/0` 一致，无增无减）。
- 日志：`.git/qa-scratch/batch4-jvm-gate.log`、`.git/qa-scratch/batch4-jvm-rerun.log`（不入库）。

### 3.2 connected（`emulator-5574`）

| 命令（`-e class …`） | 结果 |
|---|---|
| `-e package com.example.englishlearning.ui`（1.0 档） | **Tests run: 561, Failures: 3**（失败全部是 `AppScreenOverlayContainerTest` 的双字号用例） |
| `AppScreenOverlayContainerTest`（2.0 档） | **OK (6 tests)** |
| `MyWordsScreenTest`（2.0 档） | **OK (17 tests)** |
| `MyWordsScreenTest`（1.0 档） | **OK (17 tests)** |
| `AppScreenTest`（2.0 档） | **OK (58 tests)** |
| `WordBankScreenTest`（2.0 档） | **OK (4 tests)** |
| `TodayPlanScreenTest`（2.0 档） | **OK (27 tests)** |
| `WordCardScreenTest`（2.0 档） | **OK (22 tests)** |
| `TodayPlanScreenTest`（`animator_duration_scale 0`） | **OK (27 tests)** |
| 修复后单类复验 | `SetupScreenScreenshotTest` OK (1) · `GlassPanelThemeInkTest` OK (8) · `PillButtonTest` OK (9) · `GlassDialogThemeInkTest` OK (3) |

日志：`.git/qa-scratch/batch4-ui-package-1.0.log`、`.git/qa-scratch/batch4-full-fontscale-1.0.log`（不入库）。

### 3.3 全包一次跑完为什么做不到（环境债，非回归）

1. `com.example.englishlearning.backup.restore.DevicePairedRestoreE2ETest` 2 条：
   `java.io.FileNotFoundException: /data/local/tmp/qa-b1-package.zip: open failed: ENOENT`
   （`loadInputs(DevicePairedRestoreE2ETest.kt:159)`）。该输入是**一次性 QA 夹具**：需 A 机 30 分钟窗口内
   认证导出包 + 二维码挑战码解码，流程见 `docs/handoff/2026-10-14-qa-round2-closeout-briefing.md` §4；
   且测试内 `recordCount==23` 绑定当时的 A 机演示数据，重建夹具时需同步断言。
2. `com.example.englishlearning.core.storage.VocabularySearchGramIndexTest`：
   真实语料全表对照（需整库复制/加载）在 2GB AVD 上把进程拖崩，实跑中止于
   `INSTRUMENTATION_RESULT: shortMsg=Process crashed.` / `INSTRUMENTATION_CODE: 0`
   （该类在崩溃前已完成 7 条）。
   崩溃前同一轮已跑绿的包：`ai`（2 类 6 条）、`backup.device`（2 条）、`backup.infrastructure`（3 条）、
   `core.security`（6 条）、`core.storage` 除该类之外的 4 类（29+1+1+2 = 33 条）。

### 3.4 覆盖口径（避免把「没跑」写成「已验」）

- **fontScale 2.0**：只跑核心路径 6 类（134 条）+ 全仓组合级 `fontScale = 2f` 用例；**未**把 561 条整体搬到 2.0 跑。
  原因：全包在 2.0 下会大量暴露「用例自身按 1.0 设计」的可达性差异（本批已实证 `MyWordsScreenTest` 两例），
  这些不是产品缺陷；批次表验收是「门禁 + 真机清单」，2.0 的逐屏目视归真机清单。
- **深色两套**：`androidTest` 全目录 33 行显式深色构造（`AppThemeMode.Dark` / `darkTheme = true` / `ThemeMode.Dark`），
  `ui` 目录 `AppColorDark` 76 处/8 文件（含 `ThemeModeSwitchTest` 19 处、`GlassPanelThemeInkTest` 19 处、
  `GlassDialogThemeInkTest` 12 处）；**无逐屏深色截图基线**。
- **TalkBack**：`adb shell pm list packages | grep -i talkback` 空输出（该 AVD 未装 TalkBack），模拟器不可实读；
  语义层证据 = 561 条 ui 包中全部语义断言 + 既有 F5-03 无障碍加固用例。
- **动效减弱**：平台 `MotionDurationScale` 直接缩放 Compose 补间；`AppMotion` 只是 160/220/300 常量与两条缓动，
  不读设置（全仓唯一无限动画是 `AiMascot` 的 `rememberInfiniteTransition`，随平台缩放）。

## 4. Git 状态

- 分支：`stage-5-ui-r2-learning-path`；本报告与 5 个测试类、计划台账、进度简报**同一提交**（`5f2667d`）；
  §9 模拟器代跑记录另做一个 `docs(handoff)` 提交。
- 远端：`origin/stage-5-ui-r2-learning-path` 落后 **42 个提交**（`git rev-list --count origin/… ..HEAD`）；本轮**未推送**——
  用户已批准推送但代理 `http://127.0.0.1:3067` 不可达，用户明确改为「等我把代理打开之后再推送」，
  恢复后重试并以 `git ls-remote` 核对远端哈希。
- 未提交遗留：无（`.git/qa-scratch/` 下的日志与截图按红线不入库）。

## 5. 未完成 / 遗留问题

1. **兼容字段未删**（计划迁移策略原写「批 4 删除」）：`onBrand` 38 处/11 文件、`surfaceGlass` 12/5、`tint3` 14/7、
   `onMint` 30/11、`errorFill` 10/3（含声明与 KDoc 提及）；`PrototypeTokenContractTest` 仍钉五字段在册。
   直接删会打回 5 个屏的半迁移态，需先给字段找替代语义——**待用户裁决是否另立任务**。
2. **`DevicePairedRestoreE2ETest` QA 输入未重建**（2 条用例当前无法在模拟器跑通）。
3. **真机 `bf353dda` 走查未执行**：清单在 §8，需人工按表走并回报（真机只 `install -r`，不跑 instrument）。
   模拟器已代跑可达项（§9），代跑结论**不能**替代真机项 1/8/9/10/13/15 的实读。
4. **2.0 全包与逐屏深色截图回归**未做（口径见 §3.4）。
5. 推送仍待代理恢复。
6. **F-1 对比度缺陷待修**（§9.3）：`AppType.Label` 内建 `text2` 覆盖 M3 `Button` 的 `contentColor`，
   插入页两枚按钮深色 1.10:1 / 浅色推算 2.76:1。批 4 零源码改动，下一轮先补失败测试再修。

## 6. 下一任接手须知

1. 先读：本报告 → 计划「批 4 全量 QA 台账」→ `docs/handoff/2026-10-30-ui-prototype-progress.md`。
2. 设备循环：`assembleDebug` + `assembleDebugAndroidTest` → `adb -s emulator-5574 install -r` 两个 APK →
   `am instrument -w -e class <FQCN> com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner`；
   **不要**用 `connectedDebugAndroidTest`。
3. 双字号类用例前必须 `settings put system font_scale 2.0`，跑完复原 `1.0`；动效实验后
   `settings delete global animator_duration_scale`（原值 `null`）。
4. 2.0 档点击注意两坑（本批实证）：行内动作药片会吃掉行中心（点词条一侧）；根窗口外的节点
   `boundsInRoot` 会被裁成 `Rect.Zero`（用 `getUnclippedBoundsInRoot`）。
5. `VocabularySearchGramIndexTest` 不要在 2GB AVD 上跑全量；`DevicePairedRestoreE2ETest` 需要重建一次性夹具。

## 7. 用户确认过的决策

- 「开始批 4 全量 QA（推荐）」——验收 = 门禁 + 真机清单（本轮据此执行）。
- 「可以，要是推送不成功的话，等我到时候把代理打开之后再推送吧」——批准真机清单可代理项在模拟器代跑（§9），
  推送推迟到代理恢复后（用户自行开启代理）。
- 此前已批准「现在推送（推荐）」，但代理不可达导致失败；「暂不推送，继续下一步开发」仍有效，
  推送前需再次确认代理可用并逐批请示。

## 8. 真机走查清单（`bf353dda` · Mi 10S / API 33 / MIUI）

**铁律**：真机只手工装机目视；instrument 只在模拟器。MIUI 会自动卸载 adb 装的第三方包——装完立刻走查。

### 8.0 装机与启动校验

```bash
adb -s bf353dda install -r app/build/outputs/apk/debug/app-debug.apk
adb -s bf353dda shell pm list packages | grep englishlearning   # 空 = MIUI 已卸载，需重装再立刻走
adb -s bf353dda shell monkey -p com.example.englishlearning -c android.intent.category.LAUNCHER 1
adb -s bf353dda shell dumpsys window | grep -i mCurrentFocus    # 必须出现目标包，否则回退 monkey 重试
```

第一轮：系统字体「默认」、浅色主题。第二轮：字体「最大」，重走 §8.1–8.4 中 3、4、6 三项。

| # | 项 | 步骤 | 通过判据 |
|---|---|---|---|
| 1 | 首启建档 | 清数据重进：姓名页 → 选词书页 | 眉题「第 1 步 / 共 2 步」、副标题「只为把每天的量定得刚好，随时可以改。」；建完直接落今日学习 |
| 2 | 今日学习 | 进首页看强卡与入口 | 新词/到期进度两条都真、词单与复习入口可达；「每日新增」四枚 chip 可改 |
| 3 | 词卡三档 | 学 3 个词，各用一次「不认识 / 模糊 / 认识」 | 提交即落本地（开关飞行模式仍可提交）；回执不阻塞下一张 |
| 4 | 今日完成 | 学完全部新词 | 「个词」为真数；**不出现**「用时 / 一次答对」（无采集）；「回到今日」可回 |
| 5 | 阅读 | 今日文章 → 正文 → 双语对照；断网重开 | 页脚「双语 / 完成阅读」并列可达；断网读缓存不白屏 |
| 6 | 我的词 | 收藏一段 → 生词本一段 → 错词一段 | 三段同屏、页尾提示条在最后；「取消收藏」即时生效；「标记为已掌握」先出确认框、确认后行消失并出回执 |
| 7 | 设置与关于 | 设置 10 行 → 关于 | 行数 10、无空白行；关于页版本号来自安装包、许可列表可读、备份门户可进 |
| 8 | AI 配置 | 设置 → AI：填 Endpoint/模型/Key → 保存 | 首次调用前弹「域名 + 文本/图片和 Key 将发送给该服务」确认；连接测试给出可行动结果；相册图片每次单独确认 |
| 9 | 提醒 | 开提醒（Android 13 通知权限弹窗）→ 拒绝一次 | 拒绝后不再反复弹；开启时说明「每天最多一次、非精确」 |
| 10 | 备份 | 数据与备份 → 导出 | SAF 落盘文件名以 `.elpbak` 开头（可能被追加 `.zip`）；恢复入口要求旧设备挑战配对，**没有**「跳过认证」 |
| 11 | 深色两套 | 切深色，逐屏看 1–7 的核心屏 | 无白块/纯黑块、描边与玻璃面有层次，正文对比度肉眼可读 |
| 12 | 大字号最大档 | 系统字体调最大，重走 3、4、6 | 无截断、按钮不出屏；词行右侧 meta 可折两行；药片不把词条挤没 |
| 13 | TalkBack | 开启 TalkBack；MIUI 附加的 `MiuiEnhanceTBService` 需从无障碍服务列表移除，只留 TalkBack | 今日计划 / 词卡 / 我的词三屏整页可读、可操作、可返回；读序合理；走完清空服务并复原输入法 |
| 14 | 动效减弱 | 开发者选项：动画缩放 0.5x、再「关闭」 | 进出词卡与页面切换无卡顿、无 >300ms 长动画；关闭时不出现「动画卡在半途」 |
| 15 | 发音 | 词卡点发音 | 系统 TTS 逐词发声（离线英语包）；**无暂停**是已知限制，记下不判失败 |

> 走查中任一项不通过：记录屏名 + 截图（截图存 `.git/qa-scratch/`，含用户数据的截图不入库）+ 复现步骤，
> 下一轮开缺陷单；§8 只判「是否可交付」，不在此处改代码。

## 9. 真机清单可代理项 · 模拟器代跑（2026-10-31）

用户口径：「可以，要是推送不成功的话，等我到时候把代理打开之后再推送吧」。据此在 `emulator-5574` 上把 §8
能在模拟器复现的项先跑掉，真机独有项（TalkBack 实读、通知权限、SAF 落盘名、MIUI 卸载、离线 TTS 包）
**不代跑、不记通过**。截图存 `.git/qa-scratch/b4emu/`（不入库）。

### 9.1 前置与数据推进

- 设备 `emulator-5574`（API 30 / 2GB），未装 TalkBack；系统主题浅色，应用内主题随系统。
- 应用内眉题 `2026-10-30` 与 `adb shell date`（Fri Oct 30 2026 GMT）一致，非陈旧日期。
- 为够到「今日完成 → 文章解锁」，实走：3 个到期词各评「认识」→ 到达期复习完成插入页 → 10 个新词各评「认识」。
  计划终态 review 3/3、new 10/10，今日总结「13 个词」，文章解锁。
- 数据推进不可逆（当日计划快照已定格）；复现 `文章已解锁` 需在另一台/清数据后重走，**不建议**在真机走查前推进。

### 9.2 逐项结果

| §8 项 | 结果 | 证据（`b4emu/`） |
|---|---|---|
| 1 首启建档 | 未代跑（需清数据，模拟器数据要留给后续） | — |
| 2 今日学习 | ✅ 浅/深 + 2.0 三档 | `12-today-dark`、`30-today-dark-2x`、`47-today-complete-light`、`52-today-complete-dark` |
| 3 词卡三档 | ✅ 三档同屏、按钮可点、回执即时且不阻塞下一张；本轮实评统一用「认识」（回执「记为「认识」，约 23 小时后再问一次」） | `13/14/15-wordcard-dark`、`31/32/33-*2x` |
| 4 今日完成 | ✅ 真数 13、无「用时 / 一次答对」；2.0 下两枚出口需滚动但可达 | `46-today-summary-light`、`47`、`52`、`54b/54c-today-complete-dark-2x` |
| 5 阅读 | ⚠️ 部分：锁定态与解锁首页 ✅；正文/双语**不可达**（未配 AI Key） | `22/23-readinghome-dark`、`42-reading-dark-2x`、`48-reading-unlocked-light`、`53-reading-unlocked-dark`、`55b-reading-unlocked-dark-2x`、`49-generate-attempt-light` |
| 6 我的词 | ✅ 四段 + 各段空态 + 计数 | `16`–`21`、`38/39/40-*2x` |
| 7 设置与关于 | ✅ 10 行 / 4 组；主题三段可切；关于与许可 | `01`–`03`、`10/11`、`35/36/37`、`51-settings-dark` |
| 8 AI 配置 | 未代跑（需真实 Key） | — |
| 9 提醒 | 未代跑（Android 13 通知权限属真机） | — |
| 10 备份 | 未代跑（SAF / 挑战配对属真机） | — |
| 11 深色两套 | ✅ 上述可达屏全过；无白块/纯黑块，正文可读 | 全部 `*-dark*` |
| 12 大字号 | ✅ 2.0 重走 2/3/4/6/7 与阅读解锁首页 | 全部 `*-2x` |
| 13 TalkBack | ❌ 模拟器无 TalkBack 包 | — |
| 14 动效减弱 | ✅ 见 §3（`TodayPlanScreenTest` 27 条 OK） | `.git/qa-scratch/` 日志 |
| 15 发音 | ⚠️ 点发音进入 Playing 并落「发音已播放。」回执；离线包音质属真机 | `13`、`32`、`33` |

第 5 项旁证：未配 AI 时点「生成今天的文章」给出可行动引导「请先选择文章默认 AI 服务。→ 去配置」，
不弹送信确认、不产生请求（`49-generate-attempt-light`）。

### 9.3 本轮新发现（进下一轮缺陷单；批 4 不改代码）

**F-1（高）到期复习完成插入页按钮标签对比度不足** —— 唯一确认的产品缺陷。

- 屏：`WordCardScreen` 的 `ReviewCompleted` 插入页「开始学习新增词」「返回今日计划」，以及 `Unavailable` 分支「重试」。
- 实测：深色填充 `#84C4EE`、标签 `#A5B8AE` → **1.10:1**；浅色按同一构造推算 `#7BB4E6` / `#55665D` → **2.76:1**
  （WCAG AA 正文 4.5:1）。两张深色截图相隔 3s 像素读数一致，非截屏噪声（`43/44-reviewcomplete-dark-1/2`）。
- 根因：`AppType.Label` 自带 `color = appColors().text2`（`AppType.kt:55-58`）；Compose 中样式里的具体色
  优先于 `Button` 的 `contentColor`。这三处 `Button(colors = buttonColors(containerColor = learn.base,
  contentColor = colors.onBrand))` 的标签是裸 `Text(style = AppType.Label)`，于是吃到 `text2`，本该是
  `onBrand`（`#04241C`，8.72:1）。
- 反证（说明不是全屏色板问题）：同屏三档评分按钮显式 `color = content`，正常（`31/32/33`）；`PillButton`
  的「下一个词」是 `onBrand` 深墨，正常（`33`）；「显示释义」用 `tint` 底，`text2` 恰好可读但语义错
  （应为 `onMint`），归同一根因的低危面。
- 证据边界：浅色插入页在复习已 3/3 后无法再进入，**浅色数值为 token 推算而非实测**；`45-wordcard-new-light.png`
  实拍的是新词卡（复习已完成时点「开始学习」直接进新词）。「重试」按钮同构造但本轮未实拍（需触发不可用态）。
- 建议修法（下一轮，需先补失败测试）：三处 `Text` 显式 `color = colors.onBrand`；或给 `AppType.Label`
  增无墨色变体，M3 Button 内统一走 `contentColor`。

**F-2（中）2.0 档评分药片折字且高度不等** —— 「差点没想起」在中间药片被拆成两行（末字「起」独占一行），
该药片比左右两枚高（`32`）。

**F-3（中）2.0 档词卡回执状态被页脚遮挡** —— 回执条出现时「发音已播放。」下半被吸底按钮压住（`33`）。

**F-4（低）阅读锁定态文案指向不存在的区块** —— 锁定分支副标题写「长度与题材在下方『生成前参数』里设置」，
但锁定态没有该区块（解锁后才出现）（`22/42`）。

**F-5（低）2.0 档文字折行观感** —— 我的页页头「连续打卡 0 天 · 累计学过 3 个 / 词」末字独占一行；
统计格「连续天 / 数」拆成两行、格子随之变高（`34`）。不丢内容，属观感问题。

### 9.4 环境复原

- `settings get system font_scale` = `1.0`（实测）；应用主题切回「跟随系统」（`57-restored-check.png`）。
- 删除 `/sdcard/b4.png`、`/sdcard/ui.xml`；`animator_duration_scale` 仍为 `null`。
- 应用内数据保持在「计划已完成 / 文章已解锁」，供下一轮直接看今日完成与阅读解锁态；如需重走学习流程须清数据。
