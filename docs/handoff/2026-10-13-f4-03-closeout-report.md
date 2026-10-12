# 2026-10-13 F4-03 备份导出（接真实数据 + SAF 入口）工作报告

## 1. 本次目标

按 `docs/handoff/2026-10-13-f4-03-backup-export-task.md`，把已有备份容器格式接到真实用户数据上并开放 SAF 导出入口（无认证 Salvage 包，范围经用户裁决）。

## 2. 完成了什么

- **决策/文档**：`docs/decisions/2026-10-13-f4-03-unprotected-export-scope.md`（先落）；`docs/superpowers/specs/2026-10-11-logical-backup-export-design.md` 状态行同步；本阶段验收记录 `docs/handoff/2026-10-13-stage-4-f4-03-acceptance-record.md`（含 24 实体数据覆盖表）。
- **格式 v2**（提交 cc3ff32）：`BackupPackageFormat`（CURRENT_VERSION=2、16 类型白名单、路径契约、scope 派生、三个诚实状态字段）、`BackupPackageValidator`（未知版本 fail-closed、伪造签名/加密声明拒绝、限额/摘要/结构全套防御）、`ZipBackupPackageWriter.write(entries, createdAtEpochMillis)`。
- **快照组装层**（提交 a1febe3）：`BackupSnapshot`（逐实体版本化 DTO、稳定 id：安全 id 原样、复合/不安全 id 走 sha256，无反射、字节级确定性）、`BackupExport`（`BackupExportCoordinator` + 固定失败类别）、`InternalBackupSourceDao`（16 条 SELECT-only）+ `RoomBackupRowReader`、`FileOpsBackupMediaGateway`（size+SHA-256 预验证，失败关闭）、`ZipPackagePackager`。
- **UI 接线（本次会话）**：`BackupExportViewModel`（两段式：prepare 内存生成 → 展示范围/数量/大小估算与确定性 `.elpbak` 文件名 → SAF 选中才落盘；取消零写入；固定错误文案；范围变更使陈旧待写包失效）、`BackupDestinationWriter` 端口 + `ContentResolverBackupDestinationWriter`（直写 SAF URI，无临时文件，IO 线程）、`BackupExportScreen`（诚实 disclosure、范围列表、音频开关、降级模式与 testTag）、`SettingsScreen` 新增"数据备份"入口、`AppScreen` overlay 接线（nullable VM + rememberSaveable + BackHandler）、`MainActivity` 传入 `backupExportViewModel`、`AppModule` 新增 6 个 DI provider。
- **测试**：新增 `BackupExportViewModelTest`（9 用例，TDD RED→GREEN）；本阶段累计新增/重写 12 个备份相关测试类约 90 用例（明细见验收记录第 3 节）。
- **构建修复（范围外、必要）**：`app/gradle.lockfile` androidTest 类路径依赖锁冲突重同步（F4-02 引入的继承缺陷，见验收记录第 5 节；无版本变更、无新依赖）。

## 3. 验证记录

- TDD：`BackupExportViewModelTest` 先 RED（缺 `BackupExportViewModel`/`BackupDestinationWriter`/`suggestedFileName` 编译失败）后 GREEN。
- `:app:assembleDebug` PASS → `:app:testDebugUnitTest` **1097 用例全绿、0 失败 0 错误**（基线 1088 + 9）。
- `:app:compileDebugAndroidTestKotlin` PASS（修复锁冲突后）。干净 HEAD 复测证明该冲突与 F4-03 代码无关。
- detekt 全库 1705 条基线债；F4-03 新文件贡献约 32 条（MaxLineLength 为主 + `buildRecords` 复杂度 18、2 MagicNumber、1 Compose FunctionNaming），按既有库风格保留，UI 重设计阶段清理；ktlint 为存量债，未引入新规则类别。
- 未验证：真机 SAF 往返、真机 AI 音频目录一致性（见验收记录第 6 节）。

## 4. Git 状态

- 分支 `stage-4-f4-03-backup-export`；前两步已推送：cc3ff32（范围裁决 + 格式 v2）、a1febe3（快照组装 + 只读源 + 媒体网关）。
- 本次会话改动（UI 接线 + DI + 锁修复 + 验收记录 + 本报告）随最新提交推送，哈希见 `git log` 顶部；推送后 `git status` 应干净。
- 无遗留未提交文件。

## 5. 未完成 / 遗留问题

- **收藏、拍照翻译记录导出**：库中无对应表，Spec F4-03 第 1 点标记**部分通过**；功能落地后加入 record 白名单（路径契约与校验器已就绪，只需 DTO + 类型）。
- **真机走查**：设置页 → 导出确认页 → SAF 保存 → 文件落盘 → 取消路径，需设备执行（JVM 层已全部覆盖）。
- **AC4-03（恢复）不可由本包满足**：包为 `not-protected`，仅供数据留档；认证恢复到 F4-04。
- detekt 基线债与 UI 重设计一并处理（既有约定）。

## 6. 下一任接手须知

- 下一任务是 **F4-04**（配对认证、备份密码 KDF/AEAD、隔离临时区导入、替换恢复 + 回滚点）：先读 Spec-04 与本决策文档；v2 manifest 已为 `signature/authentication/encryption` 预留占位字段，恢复侧要新增真实状态取值并同步校验器（当前校验器**拒绝**一切非 `not-protected/not-supported/none` 的声明——实施 F4-04 时按新格式版本或状态枚举扩展，勿静默放宽）。
- 读取源约定：任何新增实体要进备份，须同时更新 `BackupRecordTypes`、`BackupSourceRows`、`InternalBackupSourceDao`、`BackupSnapshotBuilder` 与三个契约测试（白名单集合断言会先红）。
- 环境坑照旧：Gradle 用本地 8.13 路径；Kokoro 边界测试要求先 `assembleDebug`；提交需显式 `-c user.name/-c user.email`；git 直推 GitHub 可用（代理 127.0.0.1:3067 配置在仓库）。
- 依赖锁：若 androidTest 再现 `concurrent-futures-ktx` strictly 冲突，用 `:app:dependencies --configuration debugAndroidTestCompileClasspath --write-locks` 重同步即可（本次已修复过一次）。

## 7. 用户确认过的决策

- F4-03 导出定位为**无认证数据 Salvage 包**（用户在任务书两选项中裁决选 2）：manifest 状态如实 `not-protected`，UI 必须写"当前包不可用于认证恢复，F4-04 完成后才支持恢复"，错误只允许固定类别。
- 排除 AI 配置表（Endpoint/模型绑定属设备侧配置，恢复后重新填写）、提醒设置、系统 TTS、派生搜索索引。
- UI 仅接线级，不做视觉打磨（UI 重设计统一在功能完成后）。
