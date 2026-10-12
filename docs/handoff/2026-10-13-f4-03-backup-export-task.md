# 会话任务书：F4-03 备份导出接真实数据

**分支：** `stage-4-f4-03-backup-export`（已从 `stage-4-f4-02-reminders` 创建）
**日期：** 2026-10-13
**前置阅读（按顺序，全部必读）：**

1. `AGENTS.md`
2. `docs/handoff/2026-10-12-session-briefing.md`（环境坑与规则）
3. `docs/specs/04-history-reminders-backup-and-security.md` 的 **F4-03 节**（本任务唯一范围）
4. `docs/superpowers/specs/2026-10-11-logical-backup-export-design.md`（包格式设计与其"未签名不得称可恢复备份"门槛，见下方"范围裁决"）
5. `docs/handoff/2026-10-13-f4-02-reminders-report.md`（上一会话交接）
6. `docs/handoff/REPORT-TEMPLATE.md`（结束时写报告）

## 任务目标

备份容器格式、writer、validator 已有质量不错的地基（仅合成数据、无生产入口）。本任务把它**接到真实用户数据并开放 SAF 导出入口**，同时补齐导出前知情披露。不是重写容器格式。

## 已核实的现状（2026-10-13 盘点）

**已实现（不要重复造）：**

- `backup/domain/BackupPackageFormat.kt`：v1 格式、路径白名单（`manifest.json`、`records/profile/<id>.json`、`resources/<asset-id>`）、大小/条目限制
- `backup/domain/BackupPackageValidator.kt`：流式校验 + ZIP 中央目录结构核验 + 逐条 SHA-256 + 规范化 payload 摘要，fail-closed；另有 `readEntries` 读取
- `backup/infrastructure/ZipBackupPackageWriter.kt`：确定性排序 ZIP 写入，写后自校验
- 测试：`BackupPackageValidatorTest`、`ZipBackupPackageWriterTest`、`BackupExportMustNotIncludeCredentialsTest`（哨兵凭据排除）
- `core/export/LogicalSnapshot.kt`：**仅占位**——只有 profileId/displayName/createdAt，不含任何真实学习数据
- `backup/domain/BackupProvider.kt`：**占位接口**，无生产调用方
- UI 无任何备份入口；SettingsScreen 现有的"导出词书"是词书包（.wbpack），与用户数据备份无关，不要混淆

**确认的缺口（本任务要做的）：**

1. **范围裁决先行**：设计文档第 8 行写明"签名能力接入前不向用户开放正式备份导出、不把未签名文件称为备份"。用户已批准本任务（2026-10-13，主会话决定），但必须**先写 `docs/decisions/2026-10-13-f4-03-unprotected-export-scope.md`** 记录裁决：F4-03 导出为"无认证数据 Salvage 包"，manifest 签名/加密状态如实标 `not-protected`，UI 文案明确"当前包不可用于认证恢复，F4-04 完成后才支持恢复"；设计文档状态行同步更新。
2. **数据覆盖表**：编码前对照 AppDatabase v28 全部实体列覆盖表——学习档案（LearningProfile/TodayPlan/TodayPlanTask/LearningEvent/CardReviewState/LearningSettings/WordBook 进度/ReadingCompletion）、文章（ArticleEntity）、查询历史（VocabularySearchHistory）、词书自定义条目（VocabularyEntry/WordAiNote 视数据敏感性决定）、可选 AI 音频（AssetRecord 白名单类）。**收藏、拍照翻译保存记录目前不存在**（无实体），按设计文档要求标"未实现/不适用"，不得生成空记录冒充已备份。明确排除：AiProfile 的 Key 材料、KeyAlias、Keystore、SpeechPreference 中的系统 TTS 数据、临时图片、日志、ReminderPreference（设备本地状态，可标不适用）、SchemaMeta。
3. **快照组装层**（新，如 `backup/domain/BackupSnapshotSource`）：每类实体一个版本化 serializer + 稳定 ID，显式白名单，禁止反射或"导出所有 DAO 行"；同一快照+同一时间戳生成字节级相同的包（属性测试锚定）。
4. **路径契约与 manifest 扩展**：路径白名单从 `records/profile/` 扩为 `records/<stable-type>/`（类型名白名单，writer/validator 同步）；manifest 增加创建时间、包含范围清单、签名状态（`not-protected`）、迁移认证元数据与可选加密元数据占位字段。**做 v1→v2 格式版本升级**（v1 schema 不可变、未知版本 fail-closed 已有测试，保持）；`BackupPackageFormat.CURRENT_VERSION = 2`。
5. **媒体资源预验证**：AI 音频导出前核对 asset ID、大小、SHA-256，不一致整个导出失败，不静默跳过（现有 `MediaManifestItem` 已定义结构）。
6. **UI 接线**：SettingsScreen 加"备份导出"入口 → 导出确认页（范围列表、大小估算、"不包含 API Key"说明、"无账户下请自行妥善保存备份"风险提示）→ SAF `CreateDocument`（.elpbak 或类似固定扩展名，文件名确定性）→ 成功/失败只展示固定错误类别（沿用 validator 的 sealed 失败类型，不携带原始 message/路径）。ViewModel + AppScreen overlay 接线，参照 F4-02 ReminderSettings 的模式；取消不写任何文件。
7. **写入安全**：导出流程零写入正式 Room 数据（源码契约测试或 JVM 断言锚定"只读 DAO"）；失败清理临时产物。
8. **验收记录**：新建 `docs/handoff/2026-MM-DD-stage-4-f4-03-acceptance-record.md`，把 Spec 04 F4-03 的 4 个要点逐条映射测试证据；AC4-03 若因"收藏/拍照翻译范围未实现"不能全过，如实标注部分通过，不得宣称完整通过。

## 工作规则（违反即返工）

- TDD：先写 RED 测试再实现；每步保持可编译
- 验收门禁：`:app:assembleDebug` **先跑**，再 `:app:testDebugUnitTest` 全绿（当前基线 1041）；detekt/ktlint 只报增量不当门禁
- Gradle 用本机发行版路径（见简报）；提交身份 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`；Git 已配代理直接 push
- **范围红线**：不实现 F4-04（配对认证、二维码/迁移码、挑战响应、备份密码、KDF/AEAD 加密、导入解包、恢复、替换本机数据、回滚点）；不复制 `english-learning.db`/WAL/SHM 任何字节；不新增依赖除非确有必要（有必要则走 `--write-locks` 三份锁 + `docs/third-party-notices.md` 登记）；不加云同步/账户
- 文案诚实性：任何界面/manifest/日志不得把未签名包称为"已认证备份"或"可恢复"；错误与日志不泄露实体内容、路径、凭据
- UI 只做接线级实现，不做视觉精修（UI 重设计整体延后）

## 交付判定

1. `docs/decisions/2026-10-13-f4-03-unprotected-export-scope.md` 落档
2. 数据覆盖表（放验收记录里）+ 快照组装 + v2 格式 + SAF 导出全链有测试锚定
3. 全量 JVM 绿 + androidTest 编译通过（同阶段 2 口径）
4. 验收记录 + 按 REPORT-TEMPLATE 写 `docs/handoff/2026-MM-DD-f4-03-closeout-report.md`
5. 提交推送（本地提交必须保留；推送失败要报告原因）

## 明确排除

F4-04 全部（认证、配对、加密、恢复、替换数据、回滚）；F4-01 历史统计新增；F3-03 OCR；"收藏"与"拍照翻译记录"的数据实现（只做导出侧的"未实现/不适用"标注）；真机 SAF 走查（记录为待办，与既有走查攒批一起做）。
