# 2026-11-05 AI 缺口批第 2 批 · 三处验证缺口（现状：一已判定、二已处置、三待你目视）

来源：`docs/handoff/2026-11-05-ai-outbound-granularity-and-network-baseline-report.md` §3、§5。
口径不变：门禁是 `:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` 三连 +
`verifyAcTraceabilityMatrix` + `scripts/secret-scan.sh`；detekt/ktlint 不作闸；截图与日志一律落 `.git/qa-scratch/` 不入库。

## 待办 1 · AppScreenTest 那条 5 秒超时 —— 已判定：已知 flake，不改代码

首趟整类 `Tests run: 64, Failures: 1`，红在 `startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan`
的 `waitUntil(5_000) { today_plan_summary 节点非空 }`（`AppScreenTest.kt:2797`）。

按「先取证复现率」执行：同一 APK、不清数据，连跑三趟整类，末行分别是
`OK (64 tests)` / `OK (64 tests)` / `OK (64 tests)`（`.git/qa-scratch/conn-appscreen-{1,2,3}.log`）。
加上此前的「首趟 1 红 + 重跑全绿 + 单跑 `OK (1)`」，观测到 **5 趟里 1 次红，且只红在这一条 5 秒等待上**。

结论登记为已知 flake（学习路径 `pressSystemBack()` → `today_plan_summary` 的时序，与本批 AI/nsc 改动无交集），
**代码一字未动**。再出现时的处置顺序保持不变：抓 `fetchSemanticsNodeTree()` 现场 → 只允许
「等待时长提成具名常量并写依据」或「用例自带 setup 隔离」两种修法，**禁止**删断言或放宽判定。

## 待办 2 · wordbook 那三个类 —— 已处置：批量出网工具不再冒充门禁用例

读源码才发现的关键事实（原先的报告与待办都把它当「设备前置条件红」处理，判错了性质）：
`WordBookPackTest`、`WordCardFieldsProbeTest`、`WordMeaningProbeTest` 的类 KDoc 自己写着
**「真机 run → `adb pull`」「打包阶段的工作，不是运行时功能」「逐词发真实 AI 请求」「产 TSV/JSONL 供人工核质量」**。
它们是操作者工具，不是测试：整册打包会打出几百次付费请求，`Tests run: 2, Failures: 2` 这种红从来不是在报被测行为。

所以既不是「配服务跑通」（那是花真钱跑一次打包），也不是简单 `assumeTrue`（会把三个类的性质继续藏住），而是：

- 新增 `app/src/androidTest/.../wordbook/OperatorToolGate.kt`：`assumeOperatorToolRequested("类名")`，
  默认跳过，跳过理由一次说清三件事——它是批量出网工具、**跳过 ≠ 通过**、显式点名方式是 `-e operatorTool true`；
- 四个 `@Test` 的第一行各加这一句（**原有两条前置断言一字未删**，显式点名后仍会红，那是给操作者的真信号）；
- 三个类的 KDoc 各加一行「门禁里默认跳过」的性质说明；
- 新增 JVM 契约 `app/src/test/.../wordbook/OperatorToolGateContractTest.kt`：判据是「类里出现真实传输
  （`UrlConnectionAiHttpTransport(` / `UrlConnectionAudioHttpTransport(`）＝工具类，每个 `@Test` 第一行必须装闸」——
  第四个工具类长出来时同样跑不掉；另有合成 must-flag（没装闸的合成样本必须被抓住、装了闸的必须放过）
  与「闸的理由不许删掉 `-e` 点名方式和『跳过 ≠ 通过』」两条诚实面护栏。

设备级取证（`emulator-5554`，**只装了新的 androidTest APK**，未发任何真实 AI 请求）：

- 默认跑（不带 flag）：`WordBookPackTest OK (2 tests)`、`WordCardFieldsProbeTest OK (1 test)`、
  `WordMeaningProbeTest OK (1 test)`，且 `Time: 0.03`、输出里没有执行进度点 ⇒ 三条全部**零执行跳过**（日志 `.git/qa-scratch/r5-skip-*.log`）。
- opt-in 分支也验了，但只挑那条**不联网**的用例单跑（其 KDoc 写明「不联网、不读数据库、不碰密钥」）：
  `-e operatorTool true -e class ...WordBookPackTest#reassemblesBookJsonFromAlreadyPackedCards`
  ⇒ `Tests run: 1, Failures: 1`，红在原有的「至少要有已落盘的词条可汇总」
  ——证明闸真的会放行、而响断言没被削弱（`.git/qa-scratch/r5-optin-readonly.log`）。

**遗留**：`WordBookPackTest.kt:306` 那条 `confirmedTextHosts = setOf(textHost)`（生图提示词腿）仍只有编译级证据
（`:app:compileDebugAndroidTestKotlin` rc=0）——跑它就要发几百次付费请求，不在门禁里做；
行为面证据在 JVM 侧（`imagegen.DrawingPromptUseCaseTest` 等）。真要放量打包时由操作者在真机带
`-e operatorTool true` 显式点名，届时这条才升级为设备级证据。

## 待办 3 · 真机四项目视 —— 仍待你来验（只有你的眼睛能做）

已完成的只有冒烟：装包 Success、`.launch.BrandLaunchActivity` 拉起后 `mCurrentFocus` 到 `MainActivity`、
logcat 无 nsc 解析错误与崩溃。未做的四项：

1. 长难句首次输入 ⇒ 出域名确认框；
2. 同一域名本次使用内第二次提问 ⇒ 不再弹；
3. 查词页 AI 问答同域名 ⇒ 也不弹（两屏共享一份台账的直接证据）；
4. 拍照翻译 / 生图出图 / 文章「换一篇」⇒ **每一次都还弹**（放宽没有渗漏）。

分层执行，别一上来就烧钱：

- **零出站可完成**：第 1、4 项。弹框都在出站之前，点「取消」即零字节
  （`AiConnectionProbeTest#unconfirmedHostStopsBeforeAnyByteLeaves` 与两屏用例钉的就是这个语义）。
- **必须发一次真实请求才能验**：第 2、3 项（要先有一次成功确认入账，才谈得上「第二次不问」）。
  若不愿在真机发付费请求，就在报告里把这两项标成「JVM 台账用例已证 + 真机未验」，别写成已确认。
- 取证规则：截图/日志落 `.git/qa-scratch/`；`adb pull` 前加 `MSYS_NO_PATHCONV=1`；
  真机只手工试用、**禁止 instrument**；MIUI 会自动卸载，跑前先
  `adb -s bf353dda shell pm list packages | grep english` 确认包还在。

## 复算与后续

本轮改动后的全量门禁读数用现算，别引用文档里的死值：
```
python .git/qa-scratch/tally.py .git/qa-scratch/counts.txt   # 第二个参数是写出读数的目标文件
bash scripts/secret-scan.sh
```
待办 1、2 已随本轮收口；待办 3 留给下一位（或你）在真机上顺手做完即可，无需代码改动。
