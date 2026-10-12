# 2026-10-13 Stage 4 / F4-03 备份导出 验收记录

分支：`stage-4-f4-03-backup-export`。范围裁决见 `docs/decisions/2026-10-13-f4-03-unprotected-export-scope.md`（本阶段导出为**无认证 Salvage 包**，manifest 状态固定 `not-protected / not-supported / none`，不构成恢复协议批准）。

## 1. 验收门禁

| 门禁 | 命令 | 结果 |
|---|---|---|
| 构建 | `:app:assembleDebug` | PASS |
| 单测 | `:app:testDebugUnitTest` | PASS：1097 用例、0 失败、0 错误（基线 1088 + 本步新增 9） |
| androidTest 编译 | `:app:compileDebugAndroidTestKotlin`（同阶段 2 口径） | PASS（需先修复继承自 F4-02 的依赖锁冲突，见第 5 节） |
| detekt/ktlint | `:app:detekt` / `:app:ktlintMainSourceSetCheck` | 非门禁；全库基线债 1705 条（detekt），增量见第 5 节 |

## 2. 数据覆盖表（AppDatabase v28，24 实体）

**导出（16 个 record 类型，白名单 `BackupRecordTypes.all`，逐类型版本化序列化器）：**

| 实体 | record 类型 | 说明 |
|---|---|---|
| LocalProfileEntity | profiles | |
| LearningProfileEntity | learning-profiles | |
| LearningSettingsEntity | learning-settings | |
| TodayPlanEntity | today-plans | |
| TodayPlanTaskEntity | today-plan-tasks | 复合主键 → sha256 稳定 id，原始 id 保留在 payload |
| LearningEventEntity | learning-events | 原始事件保留（AGENTS：算法升级不影响历史） |
| CardReviewStateEntity | card-review-states | 可派生但按 Spec 保留导出 |
| WordBookEntity | word-books | |
| VocabularyEntryEntity | vocabulary-entries | 复合主键 → sha256 稳定 id |
| WordBookProgressMigrationAuditEntity | migration-audits | 复合主键 → sha256 稳定 id |
| ArticleEntity | articles | |
| ReadingCompletionEntity | reading-completions | |
| ReadingPreferenceEntity | reading-preferences | |
| VocabularySearchHistoryEntity | search-history | 复合主键 → sha256 稳定 id |
| WordAiNoteEntity | word-ai-notes | |
| AssetRecordEntity | asset-records + resources/ | 仅导出记录清单时也可选；音频字节经预验证 |

**排除（在库但不导出，含理由）：**

| 实体 | 理由 |
|---|---|
| AiProfileEntity / AiPreferenceEntity | 设备侧 AI 服务绑定；恢复后要求重新配置并重新填 Key（AGENTS 备份条款） |
| KeyAliasEntity | Keystore 别名，绝不进导出包 |
| SpeechPreferenceEntity | 系统 TTS 数据，明确不备份 |
| ReminderPreferenceEntity | 设备本地设置，恢复后重建提醒（F4-04 流程） |
| SchemaMetaEntity | Room 内部元数据 |
| VocabularySearchIndexEntity / VocabularySearchGramEntity | 派生索引，恢复后重建（Spec：恢复后重建索引） |
| AssetRecordEntity.relativePath 字段 | 设备本地路径，导出 payload 中剥离，仅保留 id/size/sha256 |

**未实现（Spec F4-03 列出但当前无存储实体，不造假空记录）：**

| Spec 项 | 状态 |
|---|---|
| 收藏 | **未实现**：库中无收藏表；待功能落地后按其真实 schema 加入白名单 |
| 用户主动保存的拍照翻译记录 | **未实现**：OCR/拍照翻译记录表不存在；同上 |

## 3. Spec-04 F4-03 四要点 → 测试证据

| Spec 要点 | 结论 | 证据 |
|---|---|---|
| 导出范围（档案/词书进度/事件/计划/文章/收藏/查询历史 + 可选 AI 音频、拍照翻译记录） | **部分通过** | `BackupSnapshotBuilderTest`（8 用例：逐类型键集、复合 id、asset 剥离 relativePath、字节级确定性）、`BackupExportCoordinatorTest`（7 用例）。收藏与拍照翻译记录**未实现**（第 2 节），如实标注；音频为可选项（UI 开关 + `audio opt out skips the media gateway entirely`）。 |
| 不导出 API Key / Keystore 密钥 / 系统 TTS / 临时图片 / 调试日志 | **通过** | `BackupExportMustNotIncludeCredentialsTest`（3 用例：源行类型 == 16 允许实体白名单且禁止 8 类敏感实体；包字节内禁止密钥字段名与哨兵值扫描）、`BackupSourceReadOnlyContractTest`（3 用例：导出链路无写操作、DAO 全部 16 条 SELECT-only、无反射）。 |
| 逻辑快照自描述包：版本、manifest、实体数据、资源索引、单文件与整体 SHA-256、签名/认证/加密元数据；禁止复制数据库文件 | **通过（签名状态如实为 not-protected）** | `BackupPackageValidatorTest`（39 用例：v2 manifest 全字段、伪造 `signed`/`aes-gcm` 声明被拒、未知版本 fail-closed、路径白名单/穿越/重复/炸弹限额、逐条目与整体 SHA-256）、`ZipBackupPackageWriterTest`（9 用例：确定性排序、createdAt 变化则字节不同）、`BackupExportCoordinatorTest` 双次导出字节相同；实现全程不接触 `.db/WAL/SHM` 文件字节（只读 DAO + 契约测试）。 |
| 导出前显示范围、大小估算、未包含 API Key 说明、"无账户下请自行妥善保存备份"风险提示 | **通过** | `BackupExportViewModelTest`（9 用例：disclosure 含"不可用于认证恢复"与"不包含 API Key"；prepare→summary/确定性 `.elpbak` 文件名；取消零写入；固定错误类别）。`BackupExportScreen` 提供范围列表、大小估算、风险提示与开关（接线级 UI）。 |

**诚实边界**：本包不可用于认证恢复（F4-04 未实施），UI 与 manifest 均如实声明；"签名元数据"以 `signatureStatus=not-protected` 占位存在，非真实签名。

## 4. 零写入与清理

- 源读取经 `InternalBackupSourceDao`（16 条 `SELECT *`），无 insert/update/delete/execSQL（`BackupSourceReadOnlyContractTest` 源码扫描守护）。
- 备份包仅在内存生成，SAF 选中位置后才落盘；取消/失败不产生任何文件或残留临时数据（`ContentResolverBackupDestinationWriter` 直写 SAF URI，无临时文件）；VM 在范围变更/成功/取消后清空待写字节（`changing scope after a prepared package invalidates the stale pending bytes`）。

## 5. 静态检查增量与环境修复

- detekt 全库 1705 条（基线债，门禁外）。本阶段新增文件贡献约 32 条：以 `MaxLineLength` 为主，另有 `BackupSnapshot.buildRecords` 圈复杂度 18>15、2 条 `MagicNumber`、1 条 Compose `FunctionNaming`（该规则对全部既有 Composable 同样触发）。保留理由：与库内既有风格一致，UI 重设计阶段统一清理。
- ktlint main 为存量债（AppModule.kt 832 条等），新文件未引入新规则类别。
- **继承缺陷修复（F4-02 引入，非 F4-03 功能范围）**：`app/gradle.lockfile` 中 `concurrent-futures-ktx` 在 app 运行时锁 1.1.0（WorkManager）而 androidTest 类路径锁 1.2.0（androidx.test:core 1.7.0），AGP 一致性解析冲突导致 `compileDebugAndroidTestKotlin` 在干净 HEAD 即失败（已用 `git stash` 验证）。修复方式：`--write-locks` 仅重同步 androidTest 类路径归属（无任何版本变更/新依赖），修复后编译通过。

## 6. 未验证项

- 真机 SAF 导出往返（instrumented 测试未新增；需设备执行）。
- 真实设备上 AI 音频目录与 `AndroidFileOps(filesDir/assets)` 根的一致性抽查（网关逻辑已由 JVM 测试覆盖，生产 DI 组合待真机验证）。
