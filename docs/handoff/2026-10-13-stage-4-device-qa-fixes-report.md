# 2026-10-13 真机 QA 修复轮 工作报告（stage-4-device-qa-fixes）

## 1. 本次目标

用户真机（Redmi K40）反馈三项缺陷，经确认"都修，单独走一轮"：
1) 词卡发音点击缓慢甚至无反应，希望有缓存；2) 词书单词"我的笔记"删不掉；3) 笔记应弹窗管理，不应内嵌在词卡页。

## 2. 完成了什么

**笔记删除全链路（缺陷 2）**
- `InternalWordAiNoteDao`：新增 `deleteOne(profileId, noteId)`，按 profileId 收窄，跨 profile 删除为 0 行空操作。
- `WordAiNoteRepository`：接口与 Room 实现新增 `delete(...): Result<Unit>`。
- `WordAiQaViewModel`：新增 `deleteNote(noteId)`——落库成功且未换卡才从可见列表移除，失败保留原列表。

**笔记弹窗化（缺陷 3）**
- `CardDetailScreen`：详情页内嵌笔记区替换为单行入口按钮「我的笔记（N）」（testTag `card_detail_notes_entry`），点击查看/删除/新增都在 `PersonalNotesDialog` 内完成；签名新增 `onDeleteNote: ((String) -> Unit)?`。
- `AppScreen`：两处传笔记的详情调用接线 `onDeleteNote = { wordQaViewModel?.deleteNote(it) }`。

**发音提速（缺陷 1，四个根因分别处置）**
- 新增 `SynthesizedAudioCache` / `FileSynthesizedAudioCache`：按「口音+文本」SHA-256 缓存合成 WAV 于私有 filesDir，64MB 预算按最旧逐出；读失败=未命中、写失败=静默，绝不打断播放。合成产物可再生、非用户内容：不进备份、不参与完整性台账。
- `KokoroPronunciationProvider`：缓存命中直接播放（完全跳过 prepare/推理）；DI 已在 `AppModule` 注入文件缓存。
- 新增 `KokoroAssetMaterializer` 替换旧 `AssetMaterializer`：已存在且大小匹配的资产直接复用（不再每次 prepare 全量重拷+readBytes 内存哈希 134MB）；缺失/截断才重拷，流式 SHA-256 校验，失败闭锁不留半截文件与 .tmp。
- 新增 `StartupPronunciationWarmer` + `EnglishLearningApplication` 接线：启动即后台 prepare()（物化+建 ONNX 会话），失败静默、点击路径自行兜底。
- `AppScreen` 反馈修复：查词详情 onSpeak/onSpeakBritish 启动前把状态置 Idle 改为 Playing；学习记录详情两处补上立即 Playing——所有点击发音路径先给"正在播放"。

**文档/规格**：无 Spec 范围变化（均为既有 F1/F3 验收标准的缺陷修复）。

## 3. 验证记录

全程 TDD（RED→GREEN），新增 22 个单测：
- `WordAiQaViewModelTest` +2（删除、删除失败保留列表）；`CardDetailTextTest` +1（入口计数文案）。
- `KokoroPronunciationProviderTest` +5（命中免推理、forceRegenerate 绕过、读异常=未命中、写失败不碍播放、零字节不落缓存）；`FileSynthesizedAudioCacheTest` +4（读写回环、目录不可用静默、预算逐出、tmp 清理）。
- `KokoroAssetMaterializerTest` +4（缺失补拷校验、大小匹配零读取复用、截断重取、摘要不符闭锁）。
- `StartupPronunciationWarmerTest` +4（成功/失败 Result/异常静默 + Application 接线源码契约）；`PronunciationFeedbackWiringTest` +2（9 个点击 handler 全部先 Playing、launch 前禁止 Idle——修复前先 RED 后 GREEN）。
- androidTest 同步：`RoomWordAiNoteRepositoryTest` +2（只删自己的笔记、跨 profile 删不到）、`CardDetailScreenTest` +2（笔记收进弹窗、空弹窗引导文案）、`AppScreenTest` fake 补 delete。

门禁（顺序执行，全绿）：
- `:app:assembleDebug` ✅（含 Kokoro 生产边界测试所需的 APK 前置）
- `:app:testDebugUnitTest` ✅ **1119 用例，0 失败**（基线 1097 + 22）
- `:app:compileDebugAndroidTestKotlin` ✅
- detekt：1705 → 1709（+4，为新 lambda 参数带来的既有函数 LongParameterList/LongMethod 类风格存量与刻意的 fail-closed 宽捕获；按项目记录，detekt/ktlint 非验收门禁，不作为回归处理）。

## 4. Git 状态

- 分支 `stage-4-device-qa-fixes`（自 F4-03 终点 a16069b 切出），提交见本仓库 `git log`，已推送 GitHub（代理 127.0.0.1:3067）。
- 无未提交的遗留源码文件。

## 5. 未完成 / 遗留问题

- **真机复测未完成**：APK 已可安装（`app/build/outputs/apk/debug/app-debug.apk`），待用户在 K40 上复测：点词发音首击反馈、重复点击是否秒播、笔记删除、笔记弹窗。
- **F4-03 §6 设备走查仍开放**：备份导出 SAF 保存 .elpbak + 取消零写入的真机走查尚未执行（本应随后进行）。
- 已知取舍（有意为之，勿当 bug 修）：
  1) 缓存文件复用只校验大小不重算哈希（私有 cacheDir，损坏会在 ONNX 建会话时显式失败并触发重拷路径外的 Failed）；
  2) 连续点击不同词仍会取消上一请求并释放 runtime（provider 有 mutex 串行化，缓存与预热已大幅缓解；彻底改造 runtime 生命周期属后续优化）；
  3) 合成音频缓存不进备份（可再生产物，非用户内容）。

## 6. 下一任接手须知

- 第一步：拿用户真机复测结论（发音速度/重复点击、笔记删除、弹窗交互）。复测通过→回主线合并，然后开 F4-04 认证与恢复（任务书惯例：先读 briefing + spec 04）。
- 环境坑不变：Gradle 用本地 8.13 路径；git 需代理与显式身份；Kokoro 边界测试前先 assembleDebug。
- 相关文档：`docs/handoff/2026-10-13-f4-03-closeout-report.md`（上一轮）、本文件。

## 7. 用户确认过的决策

- 三项缺陷"都修，单独走一轮"：JVM 测试全绿后重装真机复测，再进行 F4-04（AskUserQuestion 2026-10-13）。
- 笔记交互选"入口按钮 + 弹窗管理"：详情页只留一行「我的笔记（N）」，查看/删除/添加收进弹窗（AskUserQuestion 2026-10-13）。
- （沿袭）卸载旧签名安装 debug 版已获批准；数据库备份在 `C:/Users/steveliuyan/englishlearning-device-backup-2026-10-13/`。
