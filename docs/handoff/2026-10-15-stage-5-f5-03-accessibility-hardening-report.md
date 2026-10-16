# 2026-10-15 阶段 5 F5-03 无障碍与发音播控硬化 工作报告（Task 12 收口）

## 1. 本次目标

Task 12 收口：跑剩余门禁、把矩阵 AC5-03 升级为「代码侧完成」口径、新建 F5-03 验收记录与工作报告、增补 QA 第三轮设备清单并提交（**不推送**，推送由 controller 执行；不改动任何生产码/测试码）。

## 2. 完成了什么

- 改写 `docs/verification/ac-traceability-matrix.md` AC5-03 行为「代码侧完成」明细：语义批五个 JVM 契约类、四控接口与 UI、触控/动态字体批提交哈希、connected 21 类 266 例结果、字母栏 7/8 达成 + Rail 显式豁免口径；设备走查红线**按原意保留并收紧**（矩阵原文「待设备轮执行，未验证前不得升级为「已验证」」→ 现文「待设备轮执行，未完成不得宣称 AC5-03 全绿」；限制句位置与语义不变，非逐字照抄）；改后 `verifyAcTraceabilityMatrix` 复跑仍绿。
- 新建 `docs/verification/f5-03-accessibility-hardening-2026-10.md`（§0–§10 主节 + §1.1/§6.1–6.3/§7.1–7.3 二级节；节数会随补记变化，不复述计数）：三禁令契约、四控语义与限制、liveRegion 三容器与 `spacedBy` 代价、Canvas/固定高保留清单、普查盲区账、connected 全清单逐类命令与末行、门禁三条命令原文与末行、设备侧 TODO-USER、交下来的账。
- 本工作报告（按 `docs/handoff/REPORT-TEMPLATE.md` 七节）。
- 增补 `docs/handoff/2026-10-15-qa-round3-task.md` §3：把 F5-03 攒下的设备项追加进去（只增不改原有条目）。
- 生产码 / 测试码零改动；改动面仅上述四个文档文件。

## 3. 验证记录

### 3.1 JVM 单测增量对账（1396 → 1467 → 1471，只增不减）

| 任务 | 基线变化 | 说明 |
|---|---|---|
| 计划写下 | 1396 | 起点 |
| T1 | 1396 → 1403 | 禁令1 契约类（+类内用例，加固后 1403） |
| T2 | 1403 → 1408 | 禁令2 契约 + 低对比修 |
| T3 | 1408 → 1414 | 禁令3 契约 +6 |
| T4 | 1414 → 1421 | Switch 契约 +7 |
| T5 | 1421 → 1433 | 正向存在性契约 +12 |
| T6 | 维持 1433 | liveRegion/焦点/customActions 走 connected（3→4 例），JVM +0 |
| T7 | 1433 → 1445 → 1450 | 域层四控 Router/Kokoro/Status +5（净，1450 完成） |
| T8 | 1450 → 1465 → 1467 | 语速偏好/Room v32/UI 接线 +17 |
| T9 | 1467/0 | 复评强制 `--rerun` 重跑 =1467/0（+0，新增走 connected） |
| T10 | 1467/0 | 48dp 全在 connected 与 main 布局（+0） |
| T11 | 1467/0 | 动态字体新增 connected 护栏（+0） |
| T12（本次） | 1467/0 | 无 JVM 改动；既有红修复只触 androidTest |
| 终审批 1 | 1467/0 | I-A1/I-A2 两条护栏走 connected（+0） |
| 终审批 2 | 1467 → **1471** | 新增 JVM 源码闸 `PronunciationIdleFallbackContractTest` +4（含「目录缺失必报错」「空扫必报错」「must-flag/must-not-flag」合成反例）；I-1/M-5/M-7 走 connected 与文档（+0） |

**本次门禁**：`:app:assembleDebug :app:testDebugUnitTest --no-daemon --no-build-cache` → `BUILD SUCCESSFUL in 16s`（exit 0，无 FAILED/`e:`）。python 逐份累加 `testDebugUnitTest/*.xml`：203 suites，**tests=1467 / failures=0 / errors=0 / skipped=0**，收口那次 XML maxMtime=2026-10-16T22:31:53（任务 UP-TO-DATE，取持久化 XML 计数，1467 基线成立；该指针后续被更晚的全量跑重写，totals 不变，终审复核记为 23:18）。矩阵 + 许可门禁 `BUILD SUCCESSFUL in 12s`；`secret-scan exit=0`。三条命令原文与末行详见验收记录 §8。

**终审修复轮复跑（2026-10-17，I-A1/I-A2 生产码 + 2 条 connected 护栏落地后）**：`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache` → `BUILD SUCCESSFUL in 1m 26s`；python 累加 = **203 suites / 1467 / 0 / 0 / 0**（JVM 基线只增不减成立，新增两条走 connected 故 +0），XML maxMtime=2026-10-17T00:29:22；`verifyAcTraceabilityMatrix verifyThirdPartyNotices --rerun-tasks` → `AC matrix covered 51 ids with evidence.` + `BUILD SUCCESSFUL`（exit 0）。受影响 connected 类逐类实跑（`emulator-5574`）：`SpeechSettingsScreenTest` `OK (13 tests)`、`WordBookSwipeRowTest` `OK (2 tests)`、`AppScreenTest` 双跑 `OK (45 tests)`×2（back/导航族 flaky 未复现，基线对照法，未放宽任何断言）。

**终审批 2 + 窄复评复跑（2026-10-17，`fc68cbf`/`aac801d` 落地后，controller 独立跑）**：`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin verifyAcTraceabilityMatrix --no-daemon --no-build-cache --rerun-tasks` → `AC matrix covered 51 ids with evidence.` + `BUILD SUCCESSFUL in 3m 48s`；python 逐份累加 = **204 suites / tests 1471 / failures 0 / errors 0 / skipped 0**，XML maxMtime=2026-10-17T01:00:46；`bash scripts/secret-scan.sh` exit 0。受影响 connected 类逐类实跑（`emulator-5574`，`adb install -r` + `am instrument`）：`LearningRecordsScreenTest` `OK (4 tests)`（I-1 先红「读不到选中态描述」后绿）、`AppScreenTest` `OK (45 tests)`。**窄复评**（区间 `df881e9..48fb073`，7 提交 / 121KB，报告 `.superpowers/sdd/…/final-fix-recheck-report.md`）＝ Critical 0 / Important 0 / Minor 2，裁定「终审 7 条 Important + M-1/M-5/M-7 全部关闭、无需回实现轮」；六条红线（滑动删除两步确认、五处 `else` 不落 `Idle`、删除行零断言削弱、零密钥、改动面仅限点名文件、「代码侧完成」未升格）逐条未破；决策 §4 两处编辑独立核实为**加严**。两条 Minor 已就地改：报告 §2 的节数计数改为「不复述会漂移的计数」、connected 类名与站点号由「按序对读」改为**逐站配对**（验证文档 §5 与矩阵 AC5-03 行）。

### 3.2 connected 全清单（21 类 / 266 例，全绿）

`emulator-5574`（AVD `xtool-s04-api30`，sdk 30），逐类 `adb install -r` + `am instrument`（**未用 `connectedDebugAndroidTest`；真机 `bf353dda` 零 install/instrument**）。18 类首跑即绿；`AppScreenTest` 双跑 `OK (45 tests)`×2 flaky 未复现；`TodayPlanScreenTest`/`ReadingAccessScreenTest`/`WordAiQaScreenTest` 三处既有红（N-4「未滚先点」，BASE A/B 证明系既有债务、Test 文件 `main..HEAD` 零改动）经补 `performScrollTo()`（只改测试、断言未放宽，提交 `908eeed`）转绿。AC5-03 触控证据链 6 个尺寸护栏类均实跑绿。逐类命令与末行原文见验收记录 §7；证据文件 `.superpowers/sdd/2026-10-15-stage-5-accessibility-hardening/task-12-connected-evidence.md`。

## 4. Git 状态

- 分支：`stage-5-f5-03-accessibility-hardening`（基线 main tip `4b3f1a6`）。
- 提交链（`main..HEAD`，`a67dd67` 起）关键里程碑：
  - 语义/契约批：`a67dd67`（决策+计划）、`1dc49e4`+`f488dda`（T1）、`339d3da`+`c710c38`（T2）、`f2d8875`（T3）、`a51333f`（T4）、`02c4ba7`（T5）、`0567b2a`+`803ed97`（T6）
  - 四控/语速批：`3aa125a`→`d7a8c8e`→`7589416`→`71ab095`（T7）、`a054c3e`→`3c4dcd7`→`fdbf4b4`（T8）、`46ae7e9`→`a87b817`→`022a3d2`→`84cac40`→`0c014cb`（T9）
  - 触控/字体批：`42a43ae`→`1ad0cdd`→`d62c36b`（T10）、`a422039`→`83bac4e`→`26440b1`→`c3b3097`→`b25f9be`（T11）
  - 收口前置：`908eeed`（connected 三处既有红测试侧修复）
  - 收口批（Task 12）：`258e2ca`（矩阵 AC5-03 升级 + 新建验证文档 + 本报告 + QA 任务书增补，父 `908eeed`）→ `df881e9`（§4 远端 tip 补填）
- **远端备份口径（终审 C 的 IM-1 订正）**：本节**不再写死任何「当前 HEAD」哈希**——把文档自身所在提交的哈希写进正文会在落笔那一刻就被下一笔提交推翻（旧文本记 `258e2ca` 为 HEAD 并宣称「ls-remote 与 `rev-parse HEAD` 逐字符相同」，而写下时 HEAD 已是 `df881e9`，自相矛盾）。**唯一口径**：`git rev-parse HEAD` 与 `git ls-remote origin stage-5-f5-03-accessibility-hardening` 逐字符相同即异地备份成立，由 controller 在每次推送后核验并只更新「最近核验值」。
- **最近核验值**：`48fb073028c13e7fb3a0ad0936f300c2bb4306f9` == 当时 `git rev-parse HEAD`（2026-10-17 由 controller 推送后 `ls-remote` 逐字符核验。**提交数请现算** `git rev-list --count main..HEAD`，本文件不复述会漂移的计数。**本值天然落后**——写完这行后又会有文档提交落笔，所以它只作历史核验记录，不作「当前远端 tip」读；判定异地备份的唯一口径永远是推完后 `git rev-parse HEAD` == `git ls-remote origin <branch>`）。
- **终审与修复轮**：整分支终审共四份报告（三片 A 生产码 / B 测试 / C 文档 + 一份独立整分支报告），合计 **Critical 0 / Important 7 / Minor 20**。Important 全部由**两枚修复批**关闭：批 1（`f9e4abd` + `11a71ef`）落 I-A1（语速播报当前档位）+ I-A2（customActions 移到与 `clickable` 同节点）与文档面 IM-1…IM-5；批 2（`fc68cbf` `aac801d` `9e55533` `112428e` `48fb073`）落 I-1（学习记录 Tab `stateDescription`）+ M-1（新 JVM 源码闸锁死五处播控 `else` 不落 `Idle`）+ M-5/M-7。修复后**一次窄复评**覆盖两批 diff（`df881e9..48fb073`）＝ **Critical 0 / Important 0 / Minor 2**，裁定「终审发现全部关闭、无需回实现轮」；两条 Minor（节数计数漂移、connected 类名与站点号按序对读会错配）已由本节与验证文档 §5、矩阵 AC5-03 行就地改掉。修复提交逐条见矩阵 AC5-03 行与 `docs/verification/f5-03-accessibility-hardening-2026-10.md` §5/§7.3/§8；**推送一律由 controller 执行**。
- 未提交遗留文件：无（本阶段台账目录 `.superpowers/sdd/2026-10-15-stage-5-accessibility-hardening/` 命中 `.gitignore:10`，本区间 `git log --diff-filter=A` 对其零新增；注意仓库内仍有 6 个更早阶段**已跟踪**的 `.superpowers/**` 文件，gitignore 不取消既有跟踪，故不能笼统说「`.superpowers/` 都被忽略」）。

## 5. 未完成 / 遗留问题

- **设备 TalkBack 走查未完成**：AC5-03 只到「代码侧完成」，六链、四控真出声、系统 TTS「只有停止」可理解性、真机 1.25x→1.0x 降速、滑动删除 customAction 可达性、揭晓焦点播报均归 QA 第三轮；**未完成不得宣称 AC5-03 全绿**。
- **动态字体顶档取证缺口**：connected 护栏只覆盖 `fontScale=2.0`；`3.0` 档与真机 MIUI 档未取证（几何推演 HEAD 代码在 3.0 档会让护栏变红）。含字徽标 `AiFeatureScreen:84 size(44.dp)`、`SettingsScreen:395 size(32.dp)` 顶档破版归重设计轮 + 设备走查。
- **深色 / 硬编码色 token 化**、**`ClickableText` 逐词读屏**、**对比度 `AiSpeech.deep≈4.1:1` 临界** → UI 重设计轮（决策显式移交）。
- **字母栏真 48dp**：Rail 显式豁免（现命中区 ≈24×13dp，纵向未铺满整槽），48dp 目标 = 轨道可滚动/分页/减常驻数的信息架构级改动 → UI 重设计轮（先改 Spec）。
- **liveRegion `spacedBy` 幽灵间距**（常驻容器空态多给一份间距）→ UI 重设计轮版面重排。
- **契约 helper 未抽公共 fixture**：为不碰 T6 既有类，每个源码扫描契约自带一份 `stripComments` + 扫描守卫，终审批 2 新增的 `PronunciationIdleFallbackContractTest` 是同款副本（份数请现算：`grep -rln 'fun .*stripComments' app/src/test`）。终审裁定「登记、不改实现」（M-2）→ 归下一轮测试面清理批；风险是守卫形状在各副本间漂移。
- **L-T11-4 / D-8｜Favorites / WrongWords 零设备取证**：两屏无 connected UI 测试类，`weight(1f)` 头部只有编译级保证（纯静态改动）。**同一缺口也覆盖 48dp 触控站点 8**（`FavoritesScreen.kt:78` / `WrongWordsScreen.kt:70` 的 `.defaultMinSize(48.dp)` 无 connected 断言，`task-10-report.md` §站点8）→ 与「7/8 达成」的计数一并归 UI 重设计轮 + QA 第三轮。
- **L-T11-1**：`fontScale=2.0` CheckIn 标题被 `weight(1f)` 压到 78.48dp 换 4 行（可达不美观），结构性修法归 UI 重设计轮。
- **L4 / I-1 no-op**：详情页 liveRegion 容器「删容器必红」RED 留痕未跑，收口=静态闭合；3 处 `fillMaxWidth().horizontalScroll()` 为版面 no-op（护栏不钉存在性，不得写「头部溢出已由 T11 收口」）；`LicensesScreen.kt:62` 裸 `horizontalScroll` 禁止统一成带 `fillMaxWidth` 形状（D-4/Ruling T11-B）。
- **L8**：`WordCardPronunciationControlsTest.kt:26-29` 过期注释（不影响断言）。
- **L9 / D-9**：喇叭药片 `minimumInteractiveComponentSize` 加在 padding 内侧、随节点放大到 ≈60dp → 词卡/音标行打架与头部大字号溢出风险，归 QA 第三轮设备取证（验证文档 §9/§10；派发 §F 要求报告 §5 含 L7/L8/L9 三条，本条为补记）。
- **已知债 M-B1 / M-B2 / M-A2（终审后修复轮登记，本轮不改实现）**：契约正则只锁精确空格与同行形状（`+=`/换行赋值/压缩空格可逃逸）、`stripComments` 的 `//.*` 吞同行 URL 后半段（禁令 1/2/3 三类同款缺陷）→ 验证文档 §1.1「契约测试匹配面局限」；`exitLearning` 与阅读页三条收口只清 UI 三元组、不 `stopPlayback()`（BASE 既有行为）→ QA 第三轮设备清单 + 播控重设计轮候选。
- **AppScreenTest flaky**：已知 back/导航族 8 flaky，基线对照法判定、不改断言，本轮双跑未触发。
- **合 main 请示**：是否 FF 合 main 听用户拍板（惯例：验收后合）；里程碑真机安装（`adb -s bf353dda install -r app-debug.apk`）由 controller 执行、装完提示用户尽快试用（MIUI 会自动卸载 adb 装的应用）。

## 6. 下一任接手须知

- 接手第一步：controller 按 `android-repo-session-handoff` §4.3/§4.4 推送本分支并用 `ls-remote` 核验远端 tip==本地 HEAD，把结果补进本报告 §4；随后按用户拍板决定是否 FF 合 main。
- 环境坑：
  - Gradle 用本机路径 `C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`；管道尾必须 `; exit ${PIPESTATUS[0]}`、`grep` 加 `-a`，否则构建失败会被误报成功。
  - `testDebugUnitTest` 无输入变化时 UP-TO-DATE、不重写 XML；真实计数用 **python** 逐份累加 `app/build/test-results/testDebugUnitTest/*.xml`（本机 node 不在 `~/.qoder/runtime/node.exe`）。
  - connected 一律 `ANDROID_SERIAL=emulator-5574`（计划原文 `emulator-5576` 当前没起）；**禁 `connectedDebugAndroidTest`（清数据）**；真机 `bf353dda` **只装不测**。
  - `AppScreenTest` back/导航族 flaky：基线对照法，不改断言。
- 相关文档：验收记录 `docs/verification/f5-03-accessibility-hardening-2026-10.md`；决策 `docs/decisions/2026-10-15-f5-03-accessibility-hardening-semantics.md`；QA 第三轮 `docs/handoff/2026-10-15-qa-round3-task.md`；证据/台账 `.superpowers/sdd/2026-10-15-stage-5-accessibility-hardening/`。

## 7. 用户确认过的决策

- 深色模式与硬编码色 token 化、`ClickableText` 逐词语义、字母栏真 48dp 归接下来的 UI 视觉重设计轮（策略：先功能后美化）。
- `PronunciationCacheKey` 不纳入本地变速 rate（澄清而非偏离）。
- Room `speech_preferences` 新列走驼峰 `speechRate`（照 autoSpeak 先例，非蛇形）。
- 播控失败保持原状态、系统 TTS 下只呈现「停止」（决策 §2 裁决）。
- 里程碑式真机安装（用户长期指示「有结果后可以安装的真机上」），由 controller 执行、只装 debug 包、不跑 instrument。
- 是否 FF 合 main、推送由用户/controller 拍板（本代理只提交不推送）。
