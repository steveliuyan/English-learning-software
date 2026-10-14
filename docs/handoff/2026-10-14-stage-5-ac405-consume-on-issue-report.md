# 2026-10-14 AC4-05 签发即消费台账 工作报告

## 1. 本次目标

按用户拍板补实现 AC4-05「已使用配对凭据被拒」：消费台账「产包成功即消费」（同一挑战码第二次签名请求被拒），关闭 negative-test-audit 缺口 1b。

## 2. 完成了什么

- TDD 先红后绿（RED：新断言引用未实现符号，`compileDebugUnitTestKotlin` 编译失败留证；GREEN：最小实现转绿）。
- 新增端口与实现：`backup/pairing/ConsumedChallengeLedger.kt`（接口只收 nonce）；`backup/infrastructure/RoomConsumedChallengeLedger.kt`（落库仅 SHA-256 hex，异常如实上抛）。
- 生产接线：`ui/BackupProtectedExportViewModel.kt` `exportProtected` 重写——台账命中先拒签、`Prepared` 后登记、读/写失败 fail-closed（拒签/作废包）、`challengeConsumed` 同步拒绝二次导出且不动已产出待写包；`onPairingRevealed` 零改动（AC4-08 重显相容）。
- 域模型：`MigrationChallengeIssuer` 增 `consumeActiveChallenge()/isChallengeConsumed()` 与 `Consumed(nonceHex)` 台账事件（消费保持 Active）。
- Room v29→v30：`MigrationPairingLedgerEntity` + `InternalMigrationPairingLedgerDao`（INSERT IGNORE 幂等）+ `AppDatabase.MIGRATION_29_30` 挂装；schema `30.json` 由 KSP 生成并入库。
- DI：`di/AppModule.kt` 增 `provideConsumedChallengeLedger`（F4-04 区段）。
- 导出防泄漏契约：`BackupSourceReadOnlyContractTest.forbiddenTables` 与 `BackupExportMustNotIncludeCredentialsTest.forbiddenRowTypes` 增台账表/实体（台账不入备份包，新设备空台账起步为刻意设计）。
- 测试：`BackupProtectedExportViewModelTest` 新增 6 用例（二次导出被拒且首包可落盘 / 产包即登记 / 失败不消费可重试 / 跨会话已消费先拒签 / 登记失败作废包 / 消费后仍可重显），删除与新语义矛盾的旧用例「starting a new export invalidates the previous pending package」；`MigrationChallengeIssuerTest` 新增 3 用例；androidTest 新增 `RoomConsumedChallengeLedgerTest`（3 用例）与 `AppDatabaseMigrationTest.migrateV29ToV30_...`。
- 文档：决策 `docs/decisions/2026-10-14-pairing-challenge-consume-on-export.md`（消费点选择、fail-closed、AC4-08 相容、固有残留「消费不撤销已产包」）；`docs/verification/ac-traceability-matrix.md` AC4-05 行补证据；`docs/verification/negative-test-audit.md` 缺口 1b 改「已修」。

## 3. 验证记录

- RED：门禁 `compileDebugUnitTestKotlin` 4 条 `e:` Unresolved reference + 1 次断言参数序错（kotlin.test 布尔在前）——均留证后修复。
- GREEN：`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache` BUILD SUCCESSFUL；`testDebugUnitTest` **1332 用例 / 0 失败**（XML 计数，基线 1324 −1 删除 +9 新增，只增不减成立；9 个新用例名已逐个在结果 XML 确认执行）。
- `scripts/secret-scan.sh` exit 0（新文件与文档零密钥/挑战明文）；`verifyAcTraceabilityMatrix` 51 ids 有证据。
- 红线核验：`BackupCryptoPolicy.kt` / `MigrationChallenge.kt`(含 `MigrationChallengePolicy`) / `AndroidMigrationKey.kt` `git status` 零改动。
- Room `30.json` 存在且 `migration_pairing_ledger` DDL 为 `nonceHash`+`consumedAtEpochMillis` 两列。
- detekt/ktlint：按 2026-09-19 决策门禁口径为单测全绿，未运行静态分析（存量约 1580 条为债，非本次回归）。

## 4. Git 状态

- 分支 `stage-5-ac405-consume-on-issue`（自 main tip `fa659f4cf8b218f7f38d432ec0bdfccccc2895ed`），本工作连同报告一起提交；推送后远端 tip 以 `ls-remote` 核验值为准（见项目记忆 roadmap 行），未核验不宣称备份完成。
- 工作区在提交前 `git status` 复核：仅本功能 18 个文件（源码 8、测试 6、schema 1、文档 3），无临时产物、无密钥类文件混入。

## 5. 未完成 / 遗留问题

- 两条新 androidTest（`RoomConsumedChallengeLedgerTest`、`migrateV29ToV30_...`）本轮**只编译通过，未在设备实跑**——归 QA 第三轮攒批复验（用 `adb install -r` + `am instrument`，禁 `connectedDebugAndroidTest`）。
- F4-01 按日历史统计补实现（任务 #33）未开工：现状盘点 → Spec 核对（收藏/错词 UI 粒度取舍）→ `docs/superpowers/plans/2026-10-14-f4-01-daily-history-and-collections.md` → 开分支 TDD；已知必改项：`deleteWordBook` 物理删 `learning_events` 违反「删词书保留历史」。
- 固有残留：消费不撤销已产出包（TTL 内新设备重选同包仍可恢复），协议层无法堵死，已如实写入决策文档。
- SP-B（性能与可靠性）、SP-C/D/E、QA 第三轮攒批未启动；本分支尚未合回 main。

## 6. 下一任接手须知

- 第一步：读 `docs/specs/04-history-reminders-backup-and-security.md` F4-01 节 + `docs/verification/negative-test-audit.md` 缺口 1a，做现状盘点并写 F4-01 实现计划（writing-plans skill），**先计划后编码**。
- 门禁命令与坑沿用：本机 `gradle-8.13` 路径（哈希目录变了用 glob 重发现）、管道必须 `; exit ${PIPESTATUS[0]}`、`testDebugUnitTest` 前必须 `assembleDebug`（Kokoro 边界测试读 APK）、`kotlin.test` 断言布尔在前。
- 单测基线现为 **1332**，只增不减。
- 台账语义改动前必读决策 `2026-10-14-pairing-challenge-consume-on-export.md`：消费点=产包成功、fail-closed、AC4-08 重显不受消费阻断，三者皆是锚。
- 分支合并建议：F4-01 完成（或用户要求先行）时把本分支合回 main 并跑三门禁。

## 7. 用户确认过的决策

- 「F4-01 缺口」=补实现 F4-01（按 Spec 已载范围新开分支 TDD）。
- 「凭据台账」=补「签发即消费」实现；本会话按已呈报口径实现为**产包成功即消费**（失败重试不烧码）。
- 两者完成后 **SP-B 性能与可靠性**先于 SP-C/D/E。
- 同意加载 `android-repo-session-handoff` skill 并按其流水线收工（本报告即产物）。
