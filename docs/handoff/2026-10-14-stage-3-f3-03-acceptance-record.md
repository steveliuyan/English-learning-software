# 2026-10-14 Stage 3 / F3-03 拍照 OCR 与翻译 验收记录

分支：`stage-3-f3-03-photo-ocr`。许可核验与选型裁决见 `docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md`（方案 A 已获用户批准），第三方台账已登记。范围依据：`docs/specs/03-dictionary-pronunciation-and-photo-translation.md` F3-03 条目 + AC3-04/05/06，任务书 `docs/handoff/2026-10-13-f3-03-photo-ocr-task.md` §2 六项增量。

## 1. 验收门禁

| 门禁 | 命令 | 结果 |
|---|---|---|
| 构建 | `:app:assembleDebug` | PASS |
| 单测 | `:app:testDebugUnitTest` | PASS：1296 用例、0 失败、0 跳过（F3-03 分支基点 `b3343ac` 基线 1213 → UI 增量 1278 → 本增量 +18：ViewModel 7、历史仓储 8、备份快照/管线 3） |
| androidTest 编译 | `:app:compileDebugAndroidTestKotlin` | PASS（v28→v29 迁移用例可编译，未在真机执行） |
| detekt/ktlint | 非门禁 | 存量债照旧（1500+ 条），未当回归处理 |

## 2. AC3-04 → 测试证据

**AC3-04：无 GMS 时自动尝试 Tesseract；两个 OCR Provider 不可用时，说明 OCR 不可用而不是翻译失败。**

| 环节 | 证据 | 状态 |
|---|---|---|
| ML Kit 主路径优先 | `OcrProviderChainTest.uses mlkit first when it succeeds and never calls tesseract` | JVM 已验证 |
| ML Kit 不可用 → 自动降级 Tesseract | 同文件 `falls back to tesseract when mlkit reports unavailable` | JVM 已验证 |
| 识别失败仍尝试备用引擎 | 同文件 `mlkit recognition failure still tries tesseract and reports the working engine` | JVM 已验证 |
| 双双不可用 = 固定「OCR 不可用」类别 | 同文件 `both providers unavailable yields the fixed OcrUnavailable category, not a recognition failure`、`an empty provider chain is OcrUnavailable` | JVM 已验证 |
| 「不可用」与「识别失败」不互相污染 | 同文件 `all attempted providers failing yields RecognitionFailed with every reason preserved`、`unavailable after a failed attempt stays a recognition failure category, not unavailable` | JVM 已验证 |
| OCR 失败 ≠ 翻译失败（页面/状态机层） | `PhotoTranslationViewModelTest.bothProvidersUnavailableIsAnOcrIssueNeverATranslationFailure`、`recognitionFailureKeepsItsOwnCategoryAndReasons`；翻译侧 `networkAndTimeoutFailuresSurfaceAsAiFailuresNotOcrFailures`（UseCase） | JVM 已验证 |
| 无 GMS 真机上 ML Kit 实际返回不可用、Tesseract 可装载并识别 | 需真机（bf353dda）相机走查 + 离线模型下载实测 | **未验证** |

结论：AC3-04 的代码路径与错误类别分离已由 7 条链测试 + 3 条 UI/用例层测试覆盖；**「无 GMS 设备真实降级行为」尚未在真机核验，不得据此标记 AC3-04 已验证**（攒批走查项）。

## 3. AC3-05 → 测试证据

**AC3-05：未经确认不上传图像或 OCR 文本；用户未保存时，完成/取消/失败后不残留原图于历史、媒体仓或备份。**

| 环节 | 证据 |
|---|---|
| 确认前零字节出网 | `PhotoTranslationUseCaseTest.firstCallDemandsConfirmationBeforeAnyByteLeaves`、`confirmationIsNeverRememberedAcrossCalls`（每次调用重新确认）；`PhotoTranslationViewModelTest.translateFirstDemandsConfirmationAndDenialSendsNoBytes`；`PhotoTranslationPromptPolicyTest` 4 用例（域名/内容类别/无默认放行） |
| 未配置 AI 不发起请求 | `noDefaultProfileIsNotConfiguredWithoutAnyNetworkCall`、`missingKeyIsNotConfiguredWithoutAnyNetworkCall`；页面 `notConfiguredAiIsItsOwnCategory` |
| 超大 OCR 文本本地拒发 | `oversizedOcrTextIsRejectedLocallyWithoutNetwork` |
| 采集件只在临时区、用完即清 | `TemporaryImageWorkspaceTest` 9 用例：`useCapture cleans up after a successful recognition run` / `…when the caller cancels by throwing` / `…when the recognition engine crashes` / `…when the coroutine is cancelled`、`oversized capture is rejected and leaves no file behind`、`purgeAll sweeps crash residue including foreign files` |
| 页面退出/重置清场 | `PhotoTranslationViewModelTest.resetAndLeavingTheScreenPurgeEveryCapture` |
| 未保存 → 历史零写入 | `explicitSaveStoresImageTextAndTranslation` 是唯一写入路径；`savingWithoutACaptureKeepsNoImage`、`failedImageStorageAbortsTheSave`（图片写入失败整笔中止，不静默落无图记录）、`saveIsNotPossibleBeforeTranslationSucceeds`；`saveFailureReportsTheIssueAndKeepsStateUsable`（记录写入失败回滚已发布图片） |
| 未保存 → 媒体仓零残留 | `RoomPhotoTranslationHistoryRepositoryTest.storeImage publishes…`（只在 `writeVerified` 摘要校验通过后才登记 `asset_records`）+ `verification failure propagates as IntegrityMismatch with no DAO write`；采集件从不进入 `filesDir/assets` |
| 未保存 → 备份零包含 | 备份媒体导出只遍历 `asset_records`；未保存时表内无行，`includeMediaAssets` 自然导不出。文本历史同理由 `BackupSourceRows.photoTranslations` 只读该表。哨兵 `BackupExportMustNotIncludeCredentialsTest` 允许行类型加入新实体后仍要求整包字节不含 credential/password/keystore/relativePath 等键与 4 个哨兵串 |
| 备份数据路径只读 | `BackupSourceReadOnlyContractTest`：17 条 SELECT、表集合含 `photo_translation_records`，6 个生产文件禁写标记 |
| 「图片默认不保存」贯穿失败路径 | 相机 `ImageCapture` 字节直进状态机不落盘（UI 增量 `ae51a51`），`captureBytes` 仅存内存且 `reset()` 清空；页面固定文案「识别结果与图片默认不保存，只有你点「保存到本机」才会留下记录」 |
| 删除不跨 profile | `RoomPhotoTranslationHistoryRepositoryTest.delete cascades asset row and file`（DAO DELETE 按 profileId 收窄）+ `delete without image leaves asset layer untouched`、`removeImage drops row and file` |

## 4. AC3-06 → 测试证据

**AC3-06：用户编辑 OCR 文本后，AI 翻译只接收编辑后的文本；失败后文本仍可复制与重试。**

| 环节 | 证据 |
|---|---|
| 只送编辑后文本 | `PhotoTranslationViewModelTest.editedTextIsWhatGetsTranslated`（断言请求体载荷等于编辑后的字符串）；`confirmedTranslationShowsSourceAndTarget` |
| 低置信可标注、可编辑 | `lowConfidenceLinesAreFlaggedForTheEditor`；OCR 结果携带逐行置信度且坐标不入 AI 请求 |
| 失败后保留可编辑文本 + 重试 | `translationFailureKeepsEditableOcrTextAndAllowsRetry`（失败后 `editableText` 与重试入口仍在、译文被清空而非留半成品） |
| 不可信模型输出整份拒收 | `blankOrDangerousModelOutputIsRejectedAsInvalidResponse`；`httpErrorStatusesMapToTheirActionableFailures`；`userCancellationPropagatesInsteadOfFakingFailure` |
| 编辑后文本仍可无图保存 | `editedTextWithoutRecognitionIsStillSavableAndImageless`（`engine` 记 `None`、`imageAssetId` 为 null） |
| 复制/朗读/点词查义 | UI 增量 `af778b1`：复制、朗读原文（Playing 反馈契约）、点词走索引全量查词；由 `PronunciationFeedbackWiringTest`、`SearchEntryWiringTest` 的第三入口断言锁定 |

## 5. Spec 逐条约束 → 实现位置

| F3-03 约束 | 实现 |
|---|---|
| 相机请求 CAMERA；相册按系统版本最小化权限 | 相机 `ImageCapture` + CAMERA 权限流程；相册用 Photo Picker（免存储权限），见 `PhotoTranslationScreen.kt`（`af778b1`） |
| OCR 主 ML Kit、降级 Tesseract；只返回文本/坐标/置信度，OCR 不称翻译 | `OcrProvider`/`OcrResult`/`OcrProviderChain`（`ad90a73`）+ 设备层（`d7902f1`）；页面文案与错误类别全程区分「识别」「翻译」 |
| 低置信标识、OCR 文本可编辑 | `PhotoTranslationScreen` 逐行标注 + 编辑框；VM 状态机 |
| 图片与 OCR 文本默认只在短期临时区，取消/失败/退出清理 | `TemporaryImageWorkspace`（`ae51a51`），purge 契约见 §3 |
| 确认文本后才发起翻译；每次发送显示域名与内容类别并要求确认 | `PhotoTranslationPromptPolicy`/`UseCase`（`2f78698`）+ 每次弹确认（`af778b1`）；Key 仅在请求期存在、`finally` 清零 |
| 原文/译文对照、复制、选词查义、发音；失败不留半成品译文 | `TranslationCompareCard` + 复制/朗读/点词查义 |
| 明确保存后才写原图/OCR 文本/译文；保存资源进备份可选范围 | 本增量（`7a4677a`、`c485bd2`）：`photo_translation_records` v29 + `PrivateMediaStore.writeVerified` 发布 + `asset_records`；备份 v2/v3 四处白名单接线 + 恢复引用门 |

## 6. 未完成与边界（不得声称已验证）

1. **AC3-04 真机降级未核验**：无 GMS 设备上 ML Kit 的实际不可用信号、Tesseract 模型按需下载与识别效果，需在 bf353dda 相机走查时一并验证（攒批约定）。
2. **v28→v29 迁移未在设备执行**：`AppDatabasePhotoTranslationMigrationTest` 只通过编译门禁，`runMigrationsAndValidate` 尚未在真机/模拟器运行。
3. **恢复后原图可读性**：JVM 管线断言覆盖到暂存与资源摘要校验，「换机后保存的图片可再次打开」属真机端到端。
4. **ML Kit 遥测披露（L2）**：页面脚注已落（`af778b1`），Play Console 数据安全表声明属阶段 5 发布清单。
5. **detekt/ktlint**：非门禁，本增量未处理存量债。

## 7. 红线自检

- 未经确认不上传图像或 OCR 文本：确认门在用例发出请求之前且每次重弹， denial 路径断言零网络调用。✅
- 「图片默认不保存」贯穿所有失败路径：采集不落盘、临时区四种退出路径清理、保存失败回滚、备份只认已登记 `asset_records`。✅
- OCR ≠ 翻译：错误类别、页面文案、日志分类三者分离，由链测试与 VM 测试双向锁定。✅
- AI 输出与 OCR 文本视为不可信输入：整份拒收空白/超长/含脚本输出，`photo_translation_records` 的 `engine` 只存固定枚举名。✅
- AppScreen 新可选参数同时接 MainActivity：本增量未新增 AppScreen 可选参数，`AppScreenProductionWiringTest` 保持绿。✅
- 无新依赖，故未触发 `--write-locks` 三任务约定。✅
