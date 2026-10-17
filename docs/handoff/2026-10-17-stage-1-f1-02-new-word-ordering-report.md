# 2026-10-17 新词交错顺序 + 词书选取页渐进发布 工作报告

分支 `stage-1-f1-02-new-word-ordering`（基线 main `045c316`）。

## 1. 本次目标

用户原话：「你还要改一下学习的方法，因为我看他背单词就像单词书一样，要把全部 A 的单词全部学完才能到 B 的单词」，
并要求「在网上搜一下什么样的背单词方法是最有效、得到大家公认的学习方法」；同一批里还要解决
「打开选取词书会卡一下，什么都没有，才会突然蹦出来那些词书」的预加载体验。
用户随后拍板：顺序模型走**确定性乱序 + 相似词隔离**，排期上**顺序批先落地再开 UI 轮**，深色模式并入 UI 轮顺带做。

## 2. 完成了什么

生产侧（5 文件）：

- `learning/NewWordOrdering.kt`（新增）：以词条前 3 字母为相似性单位，组内按 `cardId` 的 64 位 FNV-1a
  哈希定序，用「剩余最多的组先出、同组冷却 `D-1` 个位置」的确定性贪心展开，`D = floor(n / M)`。
  纯内容函数：无时钟、无随机种子、无 per-user 状态，跨进程/跨设备逐位可复现；剔除已学词后剩原顺序的子序列。
- `learning/StoredPlanCardSource.kt`：新词取词改走交错顺序（原先直接按词书存储顺序 `take(limit)`）。
- `ui/LearningSetupViewModel.kt`：册列表读到即先发布（含名称、等级、词量），进度逐册解析后回填、
  每算完一册发布一次；重进页面保留仍在本版列表里的已算进度；三处过期任务落笔前显式 `isActive` 收手
  （整份发布前、逐册发布前、`finally` 收尾）；单册读不出来只让那一本降级，不钉住整页加载态。
- `ui/AppScreen.kt`：加载首帧骨架（3 行、与真卡同圆角同底色、零文案零数字）、三态进度播报
  （已学 N／进度计算中／暂时读不到进度）、无数据不画进度条也不报「未学」、
  册自带词量与进度解耦（`book.totalWords`）、`progressLoading` 刻意不给默认值。
- `androidTest/ui/WordBookSwipeRowTest.kt`：两处调用点补 `progressLoading = false`（参数去掉默认值后必须显式传）。

文档侧：

- Spec `docs/specs/01-vocabulary-learning-and-review.md`：F1-01 首帧与渐进发布口径、F1-02 交错顺序口径，
  新增 AC1-23～AC1-27。
- 决策 `docs/decisions/2026-10-17-new-word-ordering-and-setup-preload.md`：研究依据与链接、五个候选方案的取舍、
  明确不做（不加字母序开关、不做词频分层、相似判定只看前 3 字母）、已知局限（短词条整串做前缀 → 
  `go`/`goal` 这类包含对不保证隔开；十册各含 6–27 个短词条为实测）。
- 计划 `docs/superpowers/plans/2026-10-17-word-ordering-and-setup-preload.md`。
- 验收 `docs/verification/f1-02-word-ordering-and-setup-preload-2026-10.md`（门禁原文、RED 取证、契约匹配面局限、
  TODO-USER、交下来的账）。
- 矩阵 `docs/verification/ac-traceability-matrix.md`：AC1-23～AC1-27 五行证据补到用例方法名逐字一致，
  含新契约测试与金值用例。

测试新增（4 文件，本批相对 main +28 例）：`NewWordOrderingTest`、`StoredPlanCardSourceTest`、
`LearningSetupViewModelTest`、`WordBookCardProgressContractTest`（新增文件）。

## 3. 验证记录

- 五任务门禁（顺序不可颠倒，Kokoro 边界测试读 APK 产物）：
  `gradle.bat --offline --no-daemon --no-build-cache :app:assembleDebug :app:testDebugUnitTest
  :app:compileDebugAndroidTestKotlin verifyAcTraceabilityMatrix verifyThirdPartyNotices` → BUILD SUCCESSFUL；
  `AC matrix covered 56 ids with evidence.`、`Validated 73 third-party notice entries.`
- JVM 单测按 `app/build/test-results/testDebugUnitTest/*.xml` 现算（非 UP-TO-DATE 取证）：
  **206 suite / 1499 例，0 失败 / 0 错误 / 0 跳过** = main 基线 1471 + 本批 28，只增不减。
- RED→GREEN 逐条留痕（详见验收记录 §4 与 SDD 工作区 `task-3-report.md`）：
  过期整份发布 `expected: <30> but was: <10>`、过期进度回填 `expected: <[kaoyan]> but was: <[cet4, kaoyan]>`、
  抹空进度 `expected: <[cet4, cet6]> but was: <[]>` 与 `expected: <[cet4]> but was: <[]>`、
  词量契约 `word count comes from the book and never from progress()` 首跑即红。
- 判别力变异复验三处：交错序退回字母序 → 2 条用例红；`newCardIds` 退回字母序 → 2 条用例红；
  `FNV_OFFSET_BASIS` 改一 bit → **只有**金值用例红（随后复原，工作树无残留）。
- 评审链：Task 2/Task 3 各两轮窄复评 + 整分支终审（无 Critical）+ 终审批一轮修复（`2fe4c16`）后
  窄复评 7/7 ADDRESSED、无新增 Critical/Important。复评方独立用 python 复刻哈希与贪心逐位复现金值，
  并重新解析十册 `book.json` 核对文档里每一个 `n/M/D` 与短词计数。
- detekt/ktlint：**本批未跑**（依 `docs/decisions/2026-09-19-static-analysis-and-test-baseline.md` 口径，
  门禁是单测全绿；存量债约 1580 条属历史债，非本批回归）。
- connected/instrumentation：**本批未执行**，只保证 `compileDebugAndroidTestKotlin` 编译通过。
  设备侧取证归 QA 第三轮与 UI 轮；真机只做手工试用（用户规则：真机不跑 instrumented 测试）。
- 未取证项（如实登记）：词书页首帧观感、新词顺序手感、fontScale/TalkBack 下的三态播报——
  骨架仅在 JVM 侧编译验证，本仓库无 JVM Compose 测试基建，UI 口径由源码契约测试兜住。

## 4. Git 状态

- 分支 `stage-1-f1-02-new-word-ordering`，功能与文档提交链：
  `96e868a` 决策/Spec/矩阵 → `bd25a04` 口径修订 → `bde900e` 交错序实现与接线 → `5e93fd8`/`da4bf25` Task2 复审批 →
  `9e86457` 词书页渐进发布 → `ec472bd`/`190d223` Task3 评审批与复审批 → `2fe4c16` 终审批修复批 →
  `116cb9d` 验收记录 → 本报告及其收口提交（即推送后 HEAD）。
- 已推送 GitHub 并核验：本报告的收口提交即推送后 HEAD，
  `git ls-remote origin stage-1-f1-02-new-word-ordering` 与 `git rev-parse HEAD` 逐字一致才算备份完成
  （具体哈希在会话回复中同步给出，不写进文档以免自漂移）。
- 远端此前已核验的功能末端：验收记录提交 `116cb9d`（推送后 `ls-remote` 与本地 HEAD 一致）。
- `main` 仍为 `045c316`，**本分支未合入 main**，等用户拍板。
- 工作树干净（`.superpowers/sdd/…` 工作区与 `.worktrees/` 均在 ignore 内，不入库）。
  报告与验收记录同批入库，无未提交的遗留文件。

## 5. 未完成 / 遗留问题

- **待用户拍板**：是否把本分支 FF 合入 `main`（9 个功能提交 + 1 个文档提交，`main` 未动，可快进）。
- **待用户装机试用**：里程碑 debug 包（`app/build/outputs/apk/debug/app-debug.apk` 已产出）
  装真机 `bf353dda` 手工确认顺序观感与词书页首帧；只 `adb install -r`，不跑 instrumented。
- 交错顺序的设备侧证据缺口：间距性质属 JVM 保证，真机演示数据未自动化覆盖（验收记录 §6）。
- 短词条（<3 字母）包含对不保证隔开的局限已在决策文档登记，V1 不改（要改需换成前缀树并重做可证明性）。
- 转 UI 重设计轮的账：`AppScreen.kt` 两处裸 `Color(0xFF…)` 字面量（`:2223`、`:2336`）、
  骨架与真卡高度不同导致回填长高（是否按册预留高度属版面决策）、
  以及 F5-03 移交的字母 Rail 真 48dp／大字号头部挤压／喇叭药片放大／颜色 token 化 + 深色模式。
- `da4bf25` 的提交标题 `docs(learning)+test:` 与仓库 `type(scope): 中文` 口径略偏，不改写历史。

## 6. 下一任接手须知

- 接手第一步：读本文件 + `docs/verification/f1-02-word-ordering-and-setup-preload-2026-10.md`，
  然后按用户裁决走「合 main」或「UI 重设计 brainstorming」。UI 轮方向已定：薄荷玻璃保留，
  要解决的是页面不统一、功能页没做设计、入口拥挤要分类、图标（喇叭等）要审查、设置条目用彩色图标，
  允许重排入口与分组（4 tab 骨架不动），矢量图标 + 彩色容器 + 少量自绘插画（≤5 张，须写决策记录并登记许可结论），
  本轮顺带做深色模式。
- 硬约束（不得回潮）：薄荷浅底与品牌渐变不得用白字，前景走 `AppPalette.OnMintPrimary #174C44`（≈4.6:1）。
- **哈希基准是契约不是实现细节**：`NewWordOrdering.FNV_OFFSET_BASIS`、前缀截断规则、平局判序三处任一改动
  都会重写用户在一册里的剩余路径；金值用例变红时，先写决策记录再改期望值，禁止"顺手整理"。
- 环境坑（本仓库实测）：Gradle 用本机 `~/.gradle/wrapper/dists/gradle-8.13-bin/.../bin/gradle.bat`
  （版本从 wrapper 发现并强制 `-bin`，禁止取最新目录）；管道必须 `; exit ${PIPESTATUS[0]}`；
  计数一律按 `app/build/test-results/testDebugUnitTest/*.xml` 现算；
  git 走全局代理且推送带 `-c credential.interactive=false`；提交显式带身份参数；禁 `git add -A`。
- 契约测试写法坑（本批踩过）：KDoc/行注释里出现「星号紧跟斜杠」那对字符会提前闭合块注释导致语法错；
  `stripComments` 对字符串字面量不可靠，规则锁精确书写形状，重排版要有意识地同步改。
- 测试设计教训：**门要放在「两次读盘之后、发布之前」**才造得出过期窗口；把门放在读盘本身里面，
  过期任务会读到新数据、测试虚假变绿。`books.toList()` 快照只是防御，不是判别力来源。
- 设备规则：connected/androidTest 绝禁 `connectedDebugAndroidTest`（清空学习数据），只用
  `adb install -r` + `am instrument`，且只在 `emulator-5574`；真机 `bf353dda` 只手工试用。
- 相关路径：`docs/specs/01-vocabulary-learning-and-review.md`、
  `docs/decisions/2026-10-17-new-word-ordering-and-setup-preload.md`、
  `docs/superpowers/plans/2026-10-17-word-ordering-and-setup-preload.md`、
  `docs/handoff/2026-10-15-qa-round3-task.md`（QA 第三轮任务书，随时可派）。

## 7. 用户确认过的决策

- 背词顺序模型：确定性乱序 + 相似词隔离（否决纯随机、否决词频优先本轮、否决字母序开关）。
- 排期：顺序批（含词书页预加载体验）先落地，再开 UI 重设计轮；深色模式并入 UI 轮顺带做。
- 研究先行：先在公网检索公认有效的背词方法作为依据（已在决策文档「依据」节带链接登记）。
- UI 轮已定：允许重排入口与分组（4 tab 骨架不动）；图标走矢量 + 彩色容器 + 少量自绘插画（≤5 张）。
- 工作方式：继续用子代理按任务执行；有结果后把 debug 包装到真机试用。
- 集成：合 main 由用户拍板（本批未擅自合并）；此前 F5-03 分支的裁决是「FF 合 main + 删本地与远端分支」，
  可作为本次请示的先例，但不等于本次已获同意。
