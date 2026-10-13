# 2026-10-14 F3-03 拍照 OCR 与翻译（历史保存 + 备份接入）工作报告

## 1. 本次目标

完成 `docs/handoff/2026-10-13-f3-03-photo-ocr-task.md` §2 第 6 项，原话：**「历史保存 + 备份接入：仅用户明确保存才写原图/OCR 文本/译文；保存资源进备份可选范围（v2/v3 导出与恢复管线复用，不破坏 `BackupExportMustNotIncludeCredentialsTest` 哨兵）。UI/设置接线 + 验收记录（AC3-04/05/06 逐条映射）。」** 至此 F3-03 六项增量全部落地。

## 2. 完成了什么

**历史保存（`7a4677a`）**

- `phototranslation/PhotoTranslationHistory.kt`：`SavedPhotoTranslation` 模型 + `PhotoTranslationHistoryRepository` 端口（save/listRecent/delete/storeImage/removeImage，全部返回 `Result`）。
- `core/storage/entity/PhotoTranslationRecordEntity.kt` + `dao/InternalPhotoTranslationDao.kt`：表 `photo_translation_records`（`profileId`+`createdAtEpochMillis` 复合索引），插入 REPLACE、列表按 profile 倒序限 100、删除按 profileId 收窄（跨 profile 0 行）。
- `core/storage/AppDatabase.kt`：实体注册、`version = 29`、`MIGRATION_28_29`（含索引），`app/schemas/…/29.json` 已导出；`dao/InternalAssetDao.kt` 增 `deleteById`。
- `phototranslation/RoomPhotoTranslationHistoryRepository.kt`：`storeImage` 先在内存算 SHA-256 → `PrivateMediaStore.writeVerified` 临时写/校验/原子发布到 `filesDir/assets` → 校验通过才登记 `asset_records`；删除时先取 `imageAssetId` 再删行、清 DAO 记录与私有文件；`engine` 只写 `MlKit`/`Tesseract`/`None`，读回未知字面量解码为 null。
- `ui/PhotoTranslationViewModel.kt`：新增 `history`/`recordIds`/`clock` 注入；`captureBytes` 只存内存并在 `reset()` 清空；`saveCurrentTranslation`（无译文/已保存不动作，图片写失败整笔中止，记录写失败回滚图片）、`refreshHistory`、`deleteSaved`；状态新增 `history`/`lastSavedRecordId`/`saveIssue`。
- `di/AppModule.kt`：补 `providePrivateMediaStore`、`providePhotoTranslationHistoryRepository`（复用既有 `FileOps`/`ClockProvider`/`@Named("io")` 绑定，无新依赖）。
- `ui/PhotoTranslationScreen.kt` + `ui/AppScreen.kt`：「保存到本机」按钮与失败提示、最近保存列表（含原图/仅文本、「刚刚保存」标注、逐条删除）、隐私脚注改为「只有你点『保存到本机』才会留下记录」；`LaunchedEffect` 按 profile 刷新历史。

**备份接入（`c485bd2`）**

- 导出白名单四处：`BackupSourceRows.photoTranslations` + `BackupSnapshotBuilder.record("photo-translation-records", …)` + `PhotoTranslationRecordDto`（只含 schemaVersion 与逻辑字段，不含设备路径）+ `BackupRecordTypes.all` 路径许可 + `InternalBackupSourceDao.allPhotoTranslations`（SELECT-only）+ `RoomBackupRowReader`。
- 恢复链路：`RestoreRecordCodec` 解码 + 引用门（`profileId` 必须存在于 profiles 桶，否则 `EntityReferenceInvalid`）+ `RestoreEntityDecoder` 反解 + `InternalBackupRestoreDao.insertPhotoTranslations`（ABORT）+ `RoomRestoreDataWriter` 写入次序（历史行先于资源行）。
- 原图复用既有媒体通道：保存的图片就是普通 `asset_records` 行，`includeMediaAssets` 下随 `resources/<assetId>` 导出、恢复时重建于 `filesDir/assets`，**没有新增媒体白名单分支**。
- 测试收紧：`BackupExportMustNotIncludeCredentialsTest`（允许行类型 + 新实体、哨兵键与串扫描照旧）、`BackupSnapshotBuilderTest`（记录数 16→17、精确 key 集、无隐私路径/无 `assets/` 断言、无图记录 `imageAssetId:null`+`engine:"None"`、exclude-media 仍保留文本）、`BackupRestorePipelineTest`（recordCount 8→9、缺 profile 引用负例）、`BackupSourceReadOnlyContractTest`（17 条 SELECT、表集合含新表）、6 处 fixture 补 `photoTranslations`。

**文档**

- 新增 `docs/handoff/2026-10-14-stage-3-f3-03-acceptance-record.md`：门禁结果 + AC3-04/05/06 逐条证据映射 + Spec F3-03 八条约束落点 + 红线自检 + 未完成边界。
- 本工作报告。

## 3. 验证记录

TDD 全程 compile-failure→GREEN：先写/改 15+3 条断言，RED 记录为「`Unresolved reference 'saveCurrentTranslation'`、`No parameter with name 'history'`」等编译失败，随后最小实现转绿。

| 命令 | 结果 |
|---|---|
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest` | **1296 用例、0 失败、0 错误、0 跳过**（基线 1213 → UI 增量 1278 → 本增量 +18） |
| `:app:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL（迁移用例仅编译，未在设备执行） |
| detekt/ktlint | 非门禁，存量债未处理 |

Gradle 全程 `--offline`，本地分发 `C:/Users/steveliuyan/.gradle/.../gradle-8.13/bin/gradle.bat`（`gradlew` 离线不可用）。

## 4. Git 状态

- 分支 `stage-3-f3-03-photo-ocr`，基点 `b3343ac`（main tip）。
- 本会话提交：`7a4677a`（历史保存）、`c485bd2`（备份接入）、`40f4b78`（验收记录），以及本报告提交。
- 已推送并通过 `ls-remote` 核验：`refs/heads/stage-3-f3-03-photo-ocr` 与本地 HEAD 一致（核验时 tip 为 `40f4b78`；本报告提交随后一并推送）。远程 `origin = https://github.com/steveliuyan/English-learning-software.git`。
- 工作树干净（除构建产物），无未提交遗留。

## 5. 未完成 / 遗留问题

1. **AC3-04 真机降级未核验**（不得标已验证）：无 GMS 设备上 ML Kit 的不可用信号、Tesseract 按需下载与识别效果 → 攒批走查（bf353dda）。
2. **v28→v29 迁移未在设备跑**：`AppDatabasePhotoTranslationMigrationTest` 需在真机/模拟器执行 `runMigrationsAndValidate`。
3. **换机端到端**：恢复后「保存的图片可再次打开」属真机项。
4. **走查攒批**（沿用既有约定）：拍照翻译真机相机/权限走查 + F4 系列遗留走查项。
5. **待用户拍板**：是否现在把 `stage-3-f3-03-photo-ocr` 合入 main（合入前建议先做一轮真机走查），还是先继续 F3 其他项/阶段收尾。
6. **提醒**：会话早前在聊天里粘贴过 GitHub PAT，只存于 Windows 凭据管理器、未写入任何文件或日志；建议轮换。

## 6. 下一任接手须知

- 第一步：读 `docs/handoff/2026-10-13-f3-03-photo-ocr-task.md`（§2/§3 红线/§4 门禁）+ `docs/handoff/2026-10-14-stage-3-f3-03-acceptance-record.md`（§6 未完成清单）。
- 备份白名单四处 + 恢复五处是「加一张表」的固定套路，漏一处会在 `BackupSourceReadOnlyContractTest`/`BackupPackageValidatorTest`/管线测试里炸，不必怕漏。
- 哨兵测试是**反射比对行类型集合**：新增实体必须同步进 `allowedRowTypes`，且导出 DTO 字段名不得含 `relativePath`/`password`/`endpoint` 等禁键。
- androidTest 用 JUnit4（`org.junit.Assert`），`kotlin.test` 不在其 classpath。
- git 网络命令需带代理与身份 `-c` 参数（见环境备忘），不要改仓库全局配置。
- 真机执行迁移测试命令（有设备时）：`gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.core.storage.AppDatabasePhotoTranslationMigrationTest --offline`。

## 7. 用户确认过的决策

- F3-03 OCR 供应商许可方案 **A**（ML Kit bundled + Tesseract4Android），已记录于 `docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md` 并登记第三方台账。
- PAT 认证走 Windows 凭据管理器 + 代理参数方案（用户选择「a」），弹窗分步指导偏好已入用户记忆。
- detekt/ktlint 非验收门禁（存量债不当回归）。
- 真机端到端验证「攒批」处理，不逐条打断开发流。
