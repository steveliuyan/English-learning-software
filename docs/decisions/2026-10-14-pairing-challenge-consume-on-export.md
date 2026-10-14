# 2026-10-14 决策：配对挑战码「产包即消费」台账（AC4-05 缺口 1b 关闭）

## 背景

SP-A 负向测试审计（`docs/verification/negative-test-audit.md` 缺口 1b）：阶段 4 Spec AC4-05
明确列举「使用……**已使用**……的配对凭据」必须导入失败，但实现没有 used-nonce 消费台账——
旧设备在 30 分钟 TTL 内可以用同一挑战码反复签发新包，「一次性挑战」语义只在旧设备状态机的
「单活跃挑战」层面成立，跨导出请求不成立。用户拍板：补实现「签发即消费」，不裁剪 AC4-05。

## 候选方案

- A. 接受现状，写文档说明「TTL + 包↔挑战绑定 + 旧设备认证门控」已收敛风险。
  **拒绝。** AC4-05 是已载验收标准，「已使用被拒」无实现即缺口，文档化不构成关闭。
- B. 签发即消费（挑战发出的瞬间标记 consumed）。
  **拒绝。** 会烧掉 D-9/AC4-08 的重试与重显语义：签名因认证窗口过期失败、密码形态错误等
  「未产包」路径下挑战码本该仍可再次认证后使用；签发即标记会让导出失败变成死路，
  与既有验收记录「认证失败保留重新发起导出的认证入口」直接冲突。
- C. **产包成功即消费**：`BackupExportResult.Prepared` 拿到签好的字节后、进入待写状态前，
  先把 nonce 登记进消费台账；此后同一 nonce 的任何签名请求一律拒绝。导出失败不消费，
  重试链路原样保留。 **采纳。**

## 决定与语义

1. **消费点 = 产包成功**。台账只回答一个问题：这个 nonce 是否已经产出过包。
   未产出过（失败/取消/作废）的挑战码在 TTL 内仍可经再次认证重签；已产出的不可再签。
2. **双层拒绝**。VM 内 `challengeConsumed` 标记同步拒绝本会话的二次导出，且不触碰
   已产出的待写包（首次成功产包后仍可正常落盘）；Room 持久台账 `migration_pairing_ledger`
   是纵深防御：导出协程启动前先查持久记录，命中即拒签并如实报错，堵住 VM 之外未来
   可能出现的新签发路径。
3. **fail-closed**。台账读失败 → 拒绝签发（「无法核验配对码使用状态，已停止本次导出」）；
   登记失败 → 已产包作废（待写字节清空、不进入 Done），提示重新发起导出。一次性保证
   无法成立时宁可不出包，不出「可能可重放」的包。
4. **台账只存 nonce 的 SHA-256 摘要**（列 `nonceHash`，64 位十六进制小写）。secret 是
   QR-only 机密，连 nonce 明文都不落账；台账本身泄露不构成任何配对能力。这比
   `2026-10-13-f4-04-crypto-and-pairing-design.md` §2.3 预留的发放台账字段集（N、pub 指纹、
   boundDigest、状态）更保守——审计字段属 YAGNI，AC4-05 只需消费事实。
5. **台账不入备份包**。`migration_pairing_ledger` 已加入导出禁列表
   （`BackupSourceReadOnlyContractTest.forbiddenTables`、
   `BackupExportMustNotIncludeCredentialsTest.forbiddenRowTypes`），新设备恢复后从空台账
   起步是刻意设计：台账防的是旧设备重复签发包，不是新设备重复恢复包；恢复防重放由
   boundDigest 绑定校验与 TTL 承担（见「固有残留」）。恢复数据提交只搬运白名单表，
   新表天然不随包进入。
6. **AC4-08 相容**。消费 ≠ 作废显示：已消费的配对码仍可在重显认证后查看（供新设备在
   TTL 内完成对已产出待写包的恢复）。锚定用例
   `a consumed pairing code can still be revealed for the pending package`。
7. **域模型与生产双轨**。`MigrationChallengeIssuer`（纯 JVM 语义锚）新增
   `consumeActiveChallenge()/isChallengeConsumed()`，事件台账记 `Consumed(nonceHex)`；
   生产路径是 `BackupProtectedExportViewModel` + `ConsumedChallengeLedger` 端口
   （Room 实现 `RoomConsumedChallengeLedger`）。两者语义一致：消费保持 Active 阶段，
   消费事件不落 secret。

## 固有残留（如实记录）

**消费不撤销已产出的包。** 旧设备成功产包后，若该包文件与配对 QR（截图转发）同时落入
攻击者之手，且仍在 30 分钟 TTL 内、且包↔挑战 boundDigest 匹配，新设备仍可完成恢复——
这是协议的固有局限（恢复侧无法反向查询旧设备台账，两台设备之间没有在线通道），
与 10-13 决策文档 §2.3「单次使用由旧设备发放语义 + expiresAt 共同封顶」一致。
本决策把可堵的半边堵死：**同一凭据不可再签出新包**；不可堵的半边（已签出包在窗口内
可被恢复）依赖 TTL 自然过期与旧设备认证门控。

## Room 迁移

v29→v30 只加表不改表：`CREATE TABLE IF NOT EXISTS migration_pairing_ledger
(nonceHash TEXT NOT NULL PRIMARY KEY, consumedAtEpochMillis INTEGER NOT NULL)`。
`INSERT OR IGNORE` 给出幂等登记。schema `30.json` 由 KSP 导出入库，
`AppDatabaseMigrationTest.migrateV29ToV30_...` 用 MigrationTestHelper 校验列序逐字一致、
既有行零触碰。

## 验证证据

- `ui.BackupProtectedExportViewModelTest`：二次导出被拒且首包可落盘 / 产包即登记 /
  失败不消费可重试 / 跨会话已消费先拒签 / 登记失败作废包 / 消费后仍可重显（6 用例）。
- `pairing.MigrationChallengeIssuerTest`：消费记录事件且不泄密 / 无活跃挑战消费为空操作 /
  重新签发是新 nonce（3 用例）。
- androidTest `RoomConsumedChallengeLedgerTest`：真实 Room 回读 / 幂等 / 落库仅摘要。
- androidTest `AppDatabaseMigrationTest.migrateV29ToV30_...`：迁移与列序。
- `backup.BackupSourceReadOnlyContractTest`、`BackupExportMustNotIncludeCredentialsTest`：
  台账表禁入导出包。
