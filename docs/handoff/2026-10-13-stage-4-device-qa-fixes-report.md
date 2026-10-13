# 2026-10-13 真机 QA 修复轮 工作报告（stage-4-device-qa-fixes）

## 1. 本次目标

用户真机（Redmi K40）反馈三项缺陷，经确认"都修，单独走一轮"：
1) 词卡发音点击缓慢甚至无反应，希望有缓存；2) 词书单词"我的笔记"删不掉；3) 笔记应弹窗管理，不应内嵌在词卡页。

## 2. 完成了什么

**笔记删除全链路（缺陷 2）**
- `InternalWordAiNoteDao`：新增 `deleteOne(profileId, noteId)`，按 profileId 收窄，跨 profile 删除为 0 行空操作。
- `WordAiNoteRepository`：接口与 Room 实现新增 `delete(...): Result<Unit>`。
- `WordAiQaViewModel`：新增 `deleteNote(noteId)`——落库成功且未换卡才从可见列表移除，失败保留原列表。

**笔记展示栏（缺陷 3，两轮迭代最终形态）**
- 用户第一轮拍板"入口按钮 + 弹窗"；真机复测后第二轮澄清：例句框下方要有「我的笔记」展示栏，直接看到笔记内容，添加/删除仍收在弹窗。
- `CardDetailScreen`：在「例句」小节下方新增 `DetailSection("我的笔记", tag=card_detail_notes_section)`——有笔记逐条展示正文（`card_detail_note_display_<id>`），无笔记给引导文案，统一一个「添加 / 管理」按钮（`card_detail_notes_manage`）打开 `PersonalNotesDialog` 完成新增/逐条删除。词卡顶部旧的 `card_detail_notes_entry` 入口 pill 与 `notesEntryLabel` 一并下线（避免两个入口）。
- 管理弹窗（`PersonalNotesDialog`）与删除链路沿用第一轮实现，未改动。

**发音提速（缺陷 1，四个根因分别处置）**
- 新增 `SynthesizedAudioCache` / `FileSynthesizedAudioCache`：按「口音+文本」SHA-256 缓存合成 WAV 于私有 filesDir，64MB 预算按最旧逐出；读失败=未命中、写失败=静默，绝不打断播放。合成产物可再生、非用户内容：不进备份、不参与完整性台账。
- `KokoroPronunciationProvider`：缓存命中直接播放（完全跳过 prepare/推理）；DI 已在 `AppModule` 注入文件缓存。
- 新增 `KokoroAssetMaterializer` 替换旧 `AssetMaterializer`：已存在且大小匹配的资产直接复用（不再每次 prepare 全量重拷+readBytes 内存哈希 134MB）；缺失/截断才重拷，流式 SHA-256 校验，失败闭锁不留半截文件与 .tmp。
- 新增 `StartupPronunciationWarmer` + `EnglishLearningApplication` 接线：启动即后台 prepare()（物化+建 ONNX 会话），失败静默、点击路径自行兜底。
- `AppScreen` 反馈修复：查词详情 onSpeak/onSpeakBritish 启动前把状态置 Idle 改为 Playing；学习记录详情两处补上立即 Playing——所有点击发音路径先给"正在播放"。

**文档/规格**：无 Spec 范围变化（均为既有 F1/F3 验收标准的缺陷修复）。

## 3. 验证记录

全程 TDD（RED→GREEN），最终 JVM 单测 **1118**（基线 1097 + 21）：
- `WordAiQaViewModelTest` +2（删除、删除失败保留列表）。
- `KokoroPronunciationProviderTest` +5（命中免推理、forceRegenerate 绕过、读异常=未命中、写失败不碍播放、零字节不落缓存）；`FileSynthesizedAudioCacheTest` +4（读写回环、目录不可用静默、预算逐出、tmp 清理）。
- `KokoroAssetMaterializerTest` +4（缺失补拷校验、大小匹配零读取复用、截断重取、摘要不符闭锁）。
- `StartupPronunciationWarmerTest` +4（成功/失败 Result/异常静默 + Application 接线源码契约）；`PronunciationFeedbackWiringTest` +2（9 个点击 handler 全部先 Playing、launch 前禁止 Idle——修复前先 RED 后 GREEN）。
- 第二轮展示栏改造删除了 `CardDetailTextTest` 的 `notesEntryLabel` 文案测试（随入口按钮下线），净增 21。
- androidTest 同步（编译门禁 + 真机走查用）：`RoomWordAiNoteRepositoryTest` +2（只删自己的笔记、跨 profile 删不到）、`CardDetailScreenTest` +2（`notesAreDisplayedBelowTheExampleAndManagedInADialog`：笔记正文在例句框下方直接可见、弹窗内删除回调；`emptyNotesSectionStillInvitesTheFirstNoteThroughTheDialog`）、`AppScreenTest` fake 补 delete。

门禁（顺序执行，全绿）：
- `:app:assembleDebug` ✅（含 Kokoro 生产边界测试所需的 APK 前置）
- `:app:testDebugUnitTest` ✅ **1118 用例，0 失败**
- `:app:compileDebugAndroidTestKotlin` ✅
- detekt：1705 → 1710（+5，为 `CardDetailScreen` 等既有函数的 LongMethod/参数清单同类风格存量；按项目记录，detekt/ktlint 非验收门禁，不作为回归处理）。

## 4. Git 状态

- 分支 `stage-4-device-qa-fixes`（自 F4-03 终点 a16069b 切出），提交见本仓库 `git log`，已推送 GitHub（代理 127.0.0.1:3067）。
- 无未提交的遗留源码文件。

## 5. 未完成 / 遗留问题

- **发音复测：已通过**。用户真机确认"单词读没问题了"（缓存 + 幂等物化 + 预热 + Playing 反馈生效）。
- **笔记展示栏待复测**：本轮把笔记改为"例句框下方内联展示 + 弹窗管理"，需用户在 K40 复测：打开词能直接看到笔记、点「添加 / 管理」进弹窗能加/删。删除/弹窗功能第一轮已复测链路，本轮仅调展示位置与入口形态。
- **F4-03 §6 设备走查仍开放**：备份导出 SAF 保存 .elpbak + 取消零写入的真机走查尚未执行。
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
- 笔记交互第一轮选"入口按钮 + 弹窗管理"；真机复测发音通过后，第二轮澄清为"展示栏 + 弹窗管理"：例句框下方直接展示笔记内容，添加/删除仍在弹窗，顶部入口按钮取消（AskUserQuestion 2026-10-13，最终形态以第二轮为准）。
- （沿袭）卸载旧签名安装 debug 版已获批准；数据库备份在 `C:/Users/steveliuyan/englishlearning-device-backup-2026-10-13/`。
