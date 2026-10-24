# 2026-10-24 UI 重设计批 3 工作报告（Task 19–26）

## 1. 本次目标

按用户「继续开发」推进 UI 重设计批 3：设置页四组九行（AC-R-21）、词汇域三入口、字母 Rail 命中区、打卡页统计职责、
AI 主页默认展开（AC-R-23）、阅读主页版面、三个备份门合一，最后做 Task 26 收口——逐词朗读、契约补牙、
棘轮清零、六屏真机取证（AC-R-10）、深色可读性（AC-R-11）、TalkBack（AC-R-12）、门禁与报告。

## 2. 完成了什么

- **Task 19–25（已提交在前序 commit）**：设置页 4 组 9 行 + 主题三态；词汇域三入口（查词 / 词表 / 我的词）；
  字母 Rail 真 48dp；打卡页收回统计职责、摘两条裸文字链；AI 主页三枚可用能力默认展开、四枚未上线折叠；
  阅读主页主任务上提 + 外刊 Feed 独立二级层；三个备份入口合成一个子页三节。
- **Task 26 Step 1**（`759126f`）：`ClickableText` 逐词无障碍动作与 `WordRange` 标注，不改点击交互。
- **Task 26 Step 2**（`1afd30f`）：契约测试 M-B1 空白刚性、M-B2 字符串感知剥离补合成用例。
- **Task 26 Step 3**：三张棘轮收口——`RawColorLiteralRatchetTest` 只剩 `mascot/AiMascot.kt` 一枚主题不变插画色；
  `SharedComponentUniquenessRatchetTest` 页头 16 行 / 提示条 0 / 未配置卡 0 / 词行 2 行（保留理由逐条写明）；
  `DarkTokenConsumptionRatchetTest` 只剩蒙版族 6 + 主题不变白胶囊 2。四个 AI 屏的私有未配置卡并到共享组件。
- **Task 26 Step 4（真机取证）**：修 `AiLearningScreen` 头卡白字压品牌渐变（1.287/1.656/2.398 → onBrand
  12.807/9.950/6.873），修设置页主题三态缺「哪一档选中」语义（`.selectableGroup()` + `Role.RadioButton`）；
  六屏浅/深 19 张截图与 TalkBack 节点树走查，结论与差异全部入 `docs/verification/ui-redesign-2026-10.md`。
- **Task 26 Step 4b（性能）**：新建 `MyWordsFirstFrameTimingTest`，600 行首帧冷 3339/3809ms、热身 3007.5ms、
  空态 74.1/112.5ms；超「核心路径不得阻塞 UI」口径，按计划立 Task 36（`LazyColumn` + 宿主 hoist），本批有意不改。
- **Task 26 Step 5（门禁）**：JVM 241 类 / 1788 例 / 0 失败；connected 定向回归 30 类、唯一 395 例，详见 §3。
- **文档**：`docs/verification/ui-redesign-2026-10.md` 增「批 3 收口 Step 3 / Step 4 / Step 5」三节，
  AC-R-10/11/12 改判并补 21/22/23 行；「仍待用户目视」追加 ⑧⑨⑩（现盘 9 条）。
- 本报告。

## 3. 验证记录

**JVM 全量**（本机 Gradle 8.13，`--offline --no-daemon --no-build-cache`）：

```bash
rm -rf app/build/test-results/testDebugUnitTest
V=$(grep -oE 'gradle-[0-9]+\.[0-9]+' gradle/wrapper/gradle-wrapper.properties | head -1)
GRADLE=$(ls -1 "$HOME/.gradle/wrapper/dists/$V-bin/"*/"$V/bin/gradle.bat" | head -1)
"$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
  verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
```

- `BUILD SUCCESSFUL in 2m 10s`；XML 逐文件相加 **241 类 / 1788 例 / 0 失败 / 0 错误 / 0 跳过**
  （批 2 关账 231/1708，批 3 净 +10 类 +80 例；Step 3 曾为 241/1787，Step 4 新增 1 条品牌渐变墨腿）。
- `verifyThirdPartyNotices` → `Validated 76 third-party notice entries.`；
  `verifyAcTraceabilityMatrix` → `AC matrix covered 56 ids with evidence.`（单独 `--rerun-tasks` 留证）。
- `git diff --name-only main..HEAD | grep -c lockfile` → **0**。

**connected 定向回归**（只 `emulator-5574`；`adb install -r` 两 APK，未清数据；真机 `bf353dda` 零 install/instrument）：

- 四批 `am instrument -w -e class …`：117 OK / 72 OK / 99 OK / 107 例中 9 红；
  去重后覆盖 **30 类 / 395 例**。
- 9 红的处置：3 条 `AppScreenOverlayContainerTest…AtDoubleFontScale` 是设备 `font_scale` 未置 2.0 被行高哨兵拦下，
  置档后整类 **OK (6)**，随即回档 1.0；6 条 `AppScreenTest` 是既有顺序相关 flake（四条单跑全 `OK (1 test)`，
  整类复跑 **`OK (50 tests)`**），未改实现、未放宽判据。
- 控制者取类失误：`PhotoTranslationScreenTest` / `ReminderSettingsScreenTest` 现盘不存在，首轮 `ClassNotFoundException`
  计入教训；拍译与提醒屏本轮无新增 connected 证据（JVM 门禁与批 2 记录承担）。

**真机取证**（`bf353dda`，`wm density 480` ⇒ 360dp，App 内主题切换；截图/ dump 只入 `.git/qa-scratch/t26-step4/`，19 张）：
AC-R-10 六屏逐屏结论、AC-R-11 深色比值（16.196 / 14.587 / 7.543 / 12.262 / 10.731）、
AC-R-12 六屏节点树读数与主题分段修复复核，全部在验证文档 Step 4 节。设备已复原（density 440、旋转锁定、TalkBack 关、主题跟随系统）。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`，工作树干净。本批最近三次提交（作者身份按仓库既有 `steveliuyan <2021250388@qq.com>` 逐提交传入）：
  - `ff2df33` refactor(ui): 批 3 令牌迁移、棘轮收口与真机取证修复（Task 26 Step 3-4）——43 files，含 `MyWordsFirstFrameTimingTest` 与 `LearningRecordsRowContractTest` 两枚新测试；
  - `78d84ee` docs(ui): 批 3 收口证据表与工作报告（Task 26 Step 4-6）——2 files；
  - `93ee511` docs(ui): 修正 AC-R-21 行内命令的表格竖线转义；
  - `fb7def4` fix(ui-icons): 圆形图标补显式圆弧命令（2026-10-25，用户真机指认「学习提醒只有一半」后现场定位，
    `Clock`/`Info`/`Search`/`Camera`/`Person` 五枚在描边档只剩半圈；TDD 红样 `t27-RED-1-jvm.log`，
    门控 `241/1789/0/0/0`，真机 `bf353dda` 已装修复包并复核；细节见验证文档「2026-10-25 图标半圈修复」节）。
  更早两步为 `759126f`（Step 1 逐词朗读）与 `1afd30f`（Step 2 契约补牙）。
- **未推送 GitHub**（本地领先远端 93 个提交，远端 `origin/stage-5-ui-r2-learning-path` tip 仍为 `d73a3cb`）；合 `main` 未做。两项均需用户明确批准，推送后须 `git ls-remote` 核验远端 tip。
- 已知未入库物：`.git/qa-scratch/`（截图与 dump，按红线不入库）。

## 5. 未完成 / 遗留问题

- **AC-R-22 未通过**：12 条 §4 缺陷只关 1 条（备份文案）、部分 1 条（设置页术语）；其余 10 条未处置
  （历史页滚动、问词假按钮、词卡 MissingSetup 出口、AI 过期文案、`BackupRestoreScreen` 的「AI Profile」、
  指路不存在的路、词卡同句两义、默写纸归组、笔记双录入口、「学习工具与设置」名实不符）。
  根因是批 3 计划只把备份文案派进 Task 25。已立 **Task 38**，需用户决定本批补做还是转下一批。
- **AC-R-07 仍部分通过**：今日计划主体卡、设置族、阅读正文、词典/词书族仍缺设备档 2.0 断言（已立 **Task 40**）。
- **Task 14 裁决三自留项未兑现**：删两条切 tab 入口、调整词书与目标压进汇总卡（已立 **Task 39**）。
- **新账**：Task 27 图标缺件与入口配色（其中「圆形图标只剩半圈」已于 2026-10-25 修复并真机复核；
  余下是弱档 `StrokeOnly` 下识别特征缺失与 `Star`/`Bookmark` 同形）、Task 35 页头收编、
  Task 36 我的词 LazyColumn、Task 37 AI 副标题文案。
- **待用户目视 9 条**（①–⑤、⑦–⑩；⑧ AI 副标题、⑨ 今日计划 Hero、⑩ 阅读锁定态为批 3 新列）。
- **Step 7 推送**：待用户批准；**Step 8 真机手工试用**：真机 `bf353dda` 上已装最新 debug 包（`lastUpdateTime=2026-10-24 20:05:17`，与 19:57:38 的 `assembleDebug` 产物一致；此后唯一变动的源文件是 `androidTest` 侧的 `MyWordsFirstFrameTimingTest.kt`，不进 app 包），已用 `monkey -p … LAUNCHER` 拉起并核对 `mCurrentFocus` 落在 `MainActivity`，等待用户手工走链。

## 6. 下一任接手须知

- **第一步**：读本报告 + `docs/verification/ui-redesign-2026-10.md` 的「批 3 收口 Step 5」节，再决定
  AC-R-22 是本批补做（推荐）还是转下一批；任何补做都先写 RED 再改生产。
- **门禁**：严格按 §3 的命令（本机 Gradle 8.13，禁用 `./gradlew`）；基线 241/1788 只准增不准减。
- **connected**：只在 `emulator-5574`，`install -r` + `am instrument`；禁止 `connectedDebugAndroidTest`、禁止清数据；
  `-e class` 一律从 `androidTest` 现盘 package 行抄。`AppScreenTest` 装机后首跑有已知顺序 flake，单跑与整类复跑绿即为既有债，
  不得改实现或放宽判据。大字号类跑前置 `settings put system font_scale 2.0`、跑完回 1.0。
- **真机**：`bf353dda` 只手工试用（里程碑 `adb install -r`），绝不跑 instrument。
- **性能债**：Task 36 改造后必须复跑 `MyWordsFirstFrameTimingTest` 给出数字，判据见该任务描述。
- **关键词路径**：验证文档 `docs/verification/ui-redesign-2026-10.md`；计划 `docs/superpowers/plans/2026-10-17-ui-redesign.md`；
  审计 `docs/design/2026-10-17-content-placement-audit.md` §4。

**可复制接续提示词**：`读 docs/handoff/2026-10-24-stage-5-ui-r2-batch3-report.md 与 docs/verification/ui-redesign-2026-10.md 的批 3 收口节；先按 §5 处理 AC-R-22 余项（TDD），再跑 §3 门禁与 connected，最后请示推送与合 main。`

## 7. 用户确认过的决策

- UI 重设计按批推进，**不合 `main`**；每批推送前单独请示（沿用前几轮口径）。
- Task 22 按「一次做透」合并 A/B 两段执行（用户拍板）。
- `AppType` 六档不写 `lineHeight`（批 1 已定口径，本批未回潮）。
- AI 保留独立一级页作为能力聚合中心（批 3 Task 23 的边界依据）。
- 真机只做手工试用，instrumented 只在模拟器。
