# 2026-11-03 U-9「本周完成情况」周视图归一处 工作报告

## 1. 本次目标

用户 2026-11-02 在「下一项整合做什么」里选 **2 = U-9**：把重复画了两遍的「本周完成情况」收成一套——
学习记录屏删七格刻度、保留数值，周视图归打卡屏一处。老流程走：决策节 → RED → GREEN → 门禁 → connected → 真机 → 报告。

## 2. 完成了什么

- **决策**：`docs/decisions/2026-11-03-duplication-consolidation.md` §10 新增「U-9 落地」一节
  （裁定、fontScale 2.0 取证、判据闸清单、明确不做、实施记录）。范围与处置都写在动手之前。
- **生产侧**：`app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt`
  删 `WeekTicks` 调用与整个私有 Composable（净 +8 / −58 行），随行摘掉三枚不再使用的 import
  （`androidx.compose.foundation.border`、`foundation.shape.RoundedCornerShape`、`ui.text.style.TextAlign`）。
  「本周」区块现在是：共享分组标题（左「本周」、右「`doneDays` / 7 天」）+ 统计卡两格数值（本周词数 / 打卡天数）。
- **判据**：`app/src/test/java/com/example/englishlearning/ui/LearningRecordsWeekContractTest.kt`
  新增一条用例钉「`WeekTicks(` 剥注释后 = 0」+「`WeekDayState.Done|Partial|None` 取用 = 0」；
  旧用例 `the week strip and the two real stats are drawn…` 改名 `the week heading and …`（判据体未变，只把已退役的 strip 从名字摘掉）。
- **注释同批改**：本屏文件头「补三件」第 ③ 件、`WeekSection` KDoc、上述 JVM 闸文件头、
  connected `LearningRecordsScreenTest` 那条用例的 KDoc——四处原文都写「七格刻度」，现均改为
  「刻度按 U-9 退役、周视图归打卡屏」并点名决策 §10。闸剥注释抓不到这些句子，但过时说明本身就是漂移。
- **未动**：打卡屏 `check_in_week_chart`（7 根柱 + 内容描述）、`WeekTick` / `WeekDayState` 类型、
  `LearningRecordsWeek.kt` 纯函数与 `doneDays` 派生链（打卡屏与月历仍在用 `ticks`）。

## 3. 验证记录

- **RED**：新判据先跑即红——`.git/qa-scratch/u9-red.log` 第 49-52 行，
  `LearningRecordsWeekContractTest > the week tick strip retires…` FAILED，该类 9 条里 1 红。
  改前现值可复算：`git show HEAD:app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt`
  里 `WeekTicks(` 恰 2 处（定义 + 调用）、`WeekDayState.` 3 处；改后两条腿均 0。
- **JVM 门禁（终局树）**：`./gradlew.bat --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
  BUILD SUCCESSFUL（`.git/qa-scratch/u9-final-gate-2.log`），
  **308 类 / 2263 条 / 0 失败 / 0 错误 / 0 跳过**（U-13 收口基线 308/2262，+1 即本批新判据，无删无跳）。
  重算：`python .git/qa-scratch/tally.py <out.txt>`（读 `app/build/test-results/testDebugUnitTest/*.xml`）。
- **connected（`emulator-5554`，`adb install -r` 双 APK，未用 `connectedDebugAndroidTest`）**：
  `LearningRecordsScreenTest` + `CheckInScreenTest` **OK (13 tests)**（`.git/qa-scratch/u9-connected-final.log`）。
- **真机 `bf353dda` 走查**（只手工试用，`install -r` Success，`lastUpdateTime=2026-11-02 14:31:39`）：
  ① 「我的 → 学习记录 → 历史记录」= 「本周 · 0 / 7 天」+「本周词数 0」「打卡天数 0」，**七格刻度不再出现**
  （`.git/qa-scratch/u9-device-history.png`）；
  ② 「我的」页头「连续天数」格 → 打卡与连续 → 「本周学习」周一…周日 7 根柱**仍在**
  （`.git/qa-scratch/u9-device-checkin-week.png`）。两侧都留了图证，"归一处"是可看的，不只是断言。
- **detekt/ktlint**：未跑为门禁（本仓基线债 1500+ 条，验收口径是单测）。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`（UI 重设计轮，**不合 main**）。
- 本批代码、决策与本报告同批入库（报告不写自身哈希，`git log --oneline` 从尾部看即得）。
  上一枚提交是 `e786da3`（U-14 文档限定语）。**未推送**（推送等用户说「推送」；届时若失败按约定等代理打开再推）。
  领先枚数不写死在本报告里（它会随本批自身提交漂移），重算：
  `git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD`。
- 工作区在提交后应只剩 `.git/qa-scratch/` 下的取证文件（该目录不入库）。

## 5. 未完成 / 遗留问题

- **用户目视确认（累积项，本批新增最后一项）**：备份 hub 页尾提示（31→60 字）、学习记录页尾句、
  导出披露（146→189 字）、ReadingFeed 来源说明（25→33 字）、U-13 hub 两行直达 + 供应商副标题、
  **U-9 学习记录屏「本周」区块**（本批）。
- 盘点表其余挂账：Gradient 摘档、`MyWordsScreenTest` 的 `font_scale 2.0` 债、QA 第三轮任务书、
  AI 缺口批（每次弹确认 / Vision 空转 / 生图不落盘 / 缺连接测试与 nsc）。
- `AppScreenTest.startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan` 批量跑偶发
  `ComposeTimeoutException`（隔离与整类复跑均绿）——负载相关 flake，非本批回归。
- **本批看到但判为不动的两处命名/残留**（记录以免下任误当回归或重复劳动）：
  ① `ReminderSpeechPrototypeAlignmentContractTest`（JVM 闸）类名里的 `ReminderSpeech` 现在读起来像
  U-13 退役的那枚中转页，但它钉的是**两屏**（`ReminderSettingsScreen` / `SpeechSettingsScreen`）的原型落位，
  KDoc 第 10 行也写明「原型是一屏、本仓是两屏」；U-13 的页名归零闸口径**只管 main 生产树**，
  测试与文档本来要能提到退役页名，故不改。真要改名属于「测试命名整理」，不属本批范围。
  ② `docs/handoff/batch-b-pending-tests/` 是 10-30 那次留下的**快照目录**（四份 .kt 副本，已入库），
  其中 `LearningRecordsWeekContractTest.kt` 副本还停在带七格刻度的旧版。它不参与编译、不进门禁，
  但会误导人。建议单开一次「清理 handoff 快照目录」的小批，别混进功能批。

## 6. 下一任接手须知

- 重复项整合盘点表（U-1…U-14）至此**全部收口**；下一批方向在账上二选一：AI 缺口批 或 QA 第三轮任务书。
- 接手第一步：读本文件 + `docs/decisions/2026-11-03-duplication-consolidation.md`（§1-§10 是本轮的完整裁定台账）。
- 环境坑（本批新踩/复踩，都值得记）：
  - 真机 `bf353dda` 当前是**横屏自由窗口**，窗口高度只到 y≈1036：`input swipe` 的坐标落在窗口外会**静默不滚动**，
    先 `uiautomator dump` 读窗口 bounds 再定滑动区间；hub 列表是懒加载，一次滑动只翻出一组行。
  - Git Bash 下 `adb pull /sdcard/x.xml` 会把设备路径改写成 Windows 路径，需 `MSYS_NO_PATHCONV=1`；
    PATH 上的 `adb` 是 v26 桩，一律用 `/c/adb/platform-tools/adb.exe`。
  - 模拟器已是 `emulator-5554`（旧 `emulator-5574` 快照挂死，冷启动 AVD `xtool-s04-api30` 复活，数据未丢）。
  - 取证脚本落在 `.git/qa-scratch/`：`dump_texts.py <xml> <out.txt>`（抽 text+bounds）、
    `tally.py <out.txt>`（汇总单测 XML）。终端直接打印中文是 GBK 乱码，一律写文件后用 Read 看。
- instrument 只在模拟器、真机只手工试用；不要为截图触发付费 AI 生成。

## 7. 用户确认过的决策

- 「下一项整合做哪个」→ 选 **U-9**：学习记录屏删七格刻度、保留数值，周视图归打卡屏一处（2026-11-02）。
- 沿用既有口径：UI 重设计轮不合 main；推送只在用户说「推送」时执行；
  提交带 git 身份 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`；
  范围变化先写决策再动手；QA 取证只落 `.git/qa-scratch/`。
