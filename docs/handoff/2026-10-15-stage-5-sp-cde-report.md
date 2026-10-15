# 2026-10-15 阶段 5 SP-C/D/E（许可页·发布构建·QA 攒批）工作报告

## 1. 本次目标

在分支 `stage-5-sp-cde-licenses-release-and-accessibility`（自 main tip `285a4c4` 切出）执行
`docs/superpowers/plans/2026-10-15-stage-5-sp-cde-licenses-release-and-accessibility.md` 的 Task 1–9：
SP-D（F5-04 应用内「关于与开源许可」页+台账终审+v0.0.1 发布说明）、SP-E（F5-01 release 构建类型+
可选签名+APK 密钥扫描入门禁）、SP-C（F5-03/AC5-03 走查攒批成第三轮设备 QA 任务书），矩阵 AC5 回填并收口推送。
用户指令「第一个问题，我选的是，第二个允许」= SP-B 已 FF 合 main（`285a4c4`）+ 放行本任务全计划。

## 2. 完成了什么

- **Task 1**（`cd94294`）：决策 `docs/decisions/2026-10-15-sp-cde-licenses-page-release-build-and-qa-batch.md`
  （§1 台账单一事实源进 assets；§2 R8 缓开+再启用条件；§3 签名 local.properties 四键可选、缺省未签名如实登记；
  §4 apk 扫描先红后绿；§5 SP-C 移交设备轮、矩阵 AC5-03 如实「部分覆盖」；§6 AC5-01 口径）；
  Spec 05 F5-01 第三条同步修订（验签诚实性）；计划文档入库。
- **Task 2**（`ce7baa7`）：`notices.NoticesCatalog`（`parseThirdPartyNotices` 纯解析器）+ JVM 3 用例
  （preamble 跳过/真台账 73 条八字段全非空/畸形行不崩）。RED=unresolved reference 观察后转绿。
- **Task 3**（`964f23f`）：`notices.NoticesPrivacyStatement`（5 条隐私声明纯数据）+ JVM 2 用例（五关键词+条数钉死+
  反向禁「可查看」）；`LedgerAssetSyncTest`（真 APK 资产跑 `verifyThirdPartyNotices` 同函数，钉「设备所见=门禁所验」）；
  `app/build.gradle.kts` 增 `syncThirdPartyNoticesAsset` Copy → assets 接线。RED=资产缺失 FileNotFoundException 观察。
- **Task 4+5**（`2f1d9d1`）：`ui.LicensesScreen`（条目计数/隐私声明/点击展开五字段不含许可证原文/诚实不可用态/返回语义）
  + `SettingsScreen` 真实入口 `settings_open_licenses`（禁假入口注释）+ `AppScreen` 全屏覆盖层读 assets 台账接线；
  androidTest `LicensesScreenTest`（2 用例）+ `SettingsLicensesEntryTest`。
- **Task 6**（`a41aaf5`）：台账终审抓出真缺项——用户自配 AI 服务/小米 MiMo/Talkify 式在线 TTS 上游无条目
  （spec 03 L54 硬性要求启用前登记）→ 补第 73 条 `user-configured-ai-endpoints`；
  `docs/verification/release-notes-v0.0.1.md`（五要点逐条+「卸载/清数据前未导出可能无法恢复」警示+门禁命令清单+TODO-USER 边界）。
- **Task 7**（`9493f73`）：release buildType（R8 本轮关、`signingConfigs.releaseLocal` 读 local.properties 四键，
  缺省未签名+配置期如实 WARN「验签 TODO-USER」）；`scripts/apk-secret-scan.sh`（文件名禁入+assets 内容模式+
  未签名单仅 WARN）+ 自测五连（python zipfile 造包，本机无 zip 命令）；root `verifyReleaseArtifact`
  （assembleRelease 真产物扫描）并入 `checkReleaseReadiness`。
  附带修复：`secret-scan.sh` 豁免路径扩到 `scripts/apk-secret-scan*`（与头注释「自测文件自身豁免」一致）、
  计划文档 Task 7 的字面样例 key 改描述式（曾致跟踪文件自扫命中，RED 的真实回归证据）。
- **Task 8**（`419f757`）：`docs/handoff/2026-10-15-qa-round3-task.md`——AC5-03 六链 TalkBack+词卡揭晓焦点遗留、
  F5-03 矩阵（动态字体最大档/深色 D-2 复测/小屏 320dp/新许可页朗读）、TODO-USER 攒批（B3、bf353dda 六项复测、
  飞行模式手工六条、B4/B5/T2、AppScreenTest 8 flaky 归因）、纪律条款（install -r+am instrument/锁机/隐私红线/ls-remote 核验）。
- **Task 9**（本次提交）：矩阵「阶段 5 AC」节新增 AC5-03/04/05 三行（AC5-03 如实标「待设备轮执行，未验证前不得升级」）。

## 3. 验证记录

- 门禁（本机 Gradle 8.13，`:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest checkReleaseReadiness`，
  `--no-daemon --no-build-cache`）：**BUILD SUCCESSFUL**；单测 **1396** 用例 0 失败 0 跳过（基线 1391 → +5：
  NoticesCatalogTest 3 + NoticesPrivacyStatementTest 2，只增不减成立）。
- `verifyThirdPartyNotices`：Validated 73 entries；`verifyAcTraceabilityMatrix`：51 ids 证据非空；
  `verifyReleaseArtifact`：app-release-unsigned.apk scanned clean（未签名如实输出）；
  `scripts/secret-scan.sh` exit 0；`scripts/secret-scan-selftest.sh` 8 PASS；`scripts/apk-secret-scan-selftest.sh` SELFTEST OK
  （五坏包全命中、净包 0、unsigned 命名 WARN 仍 0、缺文件 exit 2）。
- connected 实跑（emulator-5576，`adb install -r` 双 APK + `am instrument`，数据零清空）：
  `LedgerAssetSyncTest` **OK (1 test)**；`LicensesScreenTest` **OK (2 tests)**；`SettingsLicensesEntryTest` **OK (1 test)**。
- TDD 偏离记录（如实）：Task 4 的 `LicensesScreenTest` RED 未单独跑观察——实现与测试同轮落盘，其「红」与 Task 5
  的 `onOpenLicenses` 编译失败合并观察（后者已确证 unresolved）；Task 2/3/7 的 RED 均单独观察（unresolved×2、
  FileNotFoundException、扫描器不存在 exit 1 通道）。
- 无新增第三方依赖 → lockfile 未动。

## 4. Git 状态

- 分支：`stage-5-sp-cde-licenses-release-and-accessibility`，提交链
  `cd94294 → ce7baa7 → 964f23f → 2f1d9d1 → a41aaf5 → 9493f73 → 419f757 →`（本报告+矩阵的收口提交，即推送后 HEAD）。
- 基线：main `285a4c4`（SP-B 已 FF 合入并 ls-remote 核验）。
- 远端：本收口提交后立即经代理 127.0.0.1:3067 推送本分支，`ls-remote origin` 核验值=本地 HEAD 哈希（一致才算备份完成；
  推送与核验结果在会话回复中同步给出）。
- 工作区：收口提交后干净；未混入密钥、`local.properties`、QA 临时产物（secret-scan exit 0 佐证）。
- 拍板补记（2026-10-15 用户「可以按你推荐的来」）：本分支 **FF 合入 main**；合入并 ls-remote 核验后删除本地+远端的
  `stage-5-sp-b-performance-and-reliability`、`stage-5-sp-cde-licenses-release-and-accessibility` 与本地三个已并入 main 的
  `fix/*` 分支。QA 第三轮直接在 main 上按任务书走查。

## 5. 未完成 / 遗留问题

- **验签 TODO-USER**：发布 keystore 由用户场外提供后配 local.properties 四键；在那之前 release 产物未签名，门禁如实 WARN。
- **AC5-03 设备走查未执行**：矩阵与本报告均标「部分覆盖」；第三轮 QA 任务书已备好待分对话执行。
- **R8 minify 技术债**：本轮关闭（决策 §2），再启用条件=专项逐库 keep 规则+真机验证。
- bf353dda 六项性能复测、飞行模式手工六条、B3/B4/B5/T2、AppScreenTest 8 flaky 归因——全部移交 `2026-10-15-qa-round3-task.md`。
- UI 重设计轮（用户已登记的未来工作）：本轮仅沿用既有域色组件，无新硬编码样式扩张。
- detekt/ktlint 存量债不属门禁（2026-09-19 基线裁决）。

## 6. 下一任接手须知

- 本分支是否 FF 合 main 听用户拍板（惯例：验收后合）；合后再派 QA 轮三分对话，任务书自包含（先读它+spec 05+`android-connected-qa-loop`）。
- 许可页数据流红线：`docs/third-party-notices.md` 是唯一台账源（构建期 Copy 进 assets，运行时纯解析渲染）；
  改台账必须过 `verifyThirdPartyNotices`+`LedgerAssetSyncTest`，禁止在 UI 里硬编码台账内容。
- release 签名四键（RELEASE_STORE_FILE/RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD）绝不入库、不进报告；
  缺省未签名路径必须保持「如实 WARN 不谎称验签」。
- 扫描模式改动须同步两个 selftest 并先红后绿；`secret-scan-allowlist` 行内豁免仅用于确需字面样例的测试文件。
- 矩阵校验器正则 `AC[0-4]-\d{2}` 使 AC5 行天然不被自动校验，「阶段 5 AC」节口径=人工追踪、每行证据非空。

## 7. 用户确认过的决策

- 「第一个问题，我选的是，第二个允许」= ①SP-B FF 合 main（已执行 `285a4c4`）；②放行 SP-C/D/E 全计划（本对话内联执行 Task 1–9）。
- 决策细节均先落 `docs/decisions/2026-10-15-sp-cde-licenses-page-release-build-and-qa-batch.md` 再实施，无临场扩权：
  未新增账号/云同步/订阅/广告/远程代码执行；API Key 未入库入日志入报告；台账第 73 条只登记「用户自配服务」事实与条款归属。
- 验签/R8/设备走查均选择「如实登记 TODO，不伪造通过」口径（决策 §2/§3/§5）。
