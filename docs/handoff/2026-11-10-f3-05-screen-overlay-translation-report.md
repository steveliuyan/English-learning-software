# 2026-11-10 F3-05 悬浮窗屏幕翻译 工作报告

## 1. 本次目标

把 F3-05「悬浮窗屏幕翻译」（MediaProjection 截屏 + 本机 OCR + 悬浮窗原位覆盖译文）从内核接到真机上可跑：
manifest 权限与服务声明、拍照翻译页里的入口段 UI、宿主（AppScreen / MainActivity）接线、首次发送前的应用内确认闭环，
并在真机上走查一条会话的完整生命周期。用户 m06826 追加要求：**写交接文档 + 更新规范手册（AGENTS.md）**。

## 2. 完成了什么

### 2.1 平台与内核
- `app/src/main/AndroidManifest.xml`：新增 `SYSTEM_ALERT_WINDOW`（用户手动去系统设置开，应用不主动弹窗要权限）、`FOREGROUND_SERVICE`、
  `FOREGROUND_SERVICE_MEDIA_PROJECTION` 三条权限；声明 `ScreenTranslationService`（`foregroundServiceType="mediaProjection"`、`exported="false"`）。
  注释写清「一次会话一次授权」「帧不落盘、不上传」「没有声明任何无障碍服务权限」。原文件是 CRLF，已统一成 LF。
- `screentranslation/ScreenTranslationService.kt`：
  - 单次模式会话一开就自动截一帧翻译（Manual 触发，不被 SingleShotMode/节流闸口挡掉），覆盖层留到用户按停止，不自动 finish；
  - **真机走查发现的缺陷修复**：OCR 读不出字（`OcrFailed`）、整屏没有英文块、块数超上限这三种「这一帧没翻开」的情况
    从「结束会话」改为 `reportNotice(...)`——只把话放到界面、**会话继续**。理由：用户的正常动线是「在自己中文界面点开始 → 切到要翻的页面」，
    第一帧本来就没有英文；按旧逻辑连续模式一开就死。真正没法自己好的情况（无悬浮窗权限、截屏授权被拒/被回收、本机没有 OCR、
    没有可用 AI 服务、网络失败）仍然结束会话。
- `screentranslation/ScreenTranslationController.kt`：确认台账 `confirmedTextHosts` 的清空从 `beginSession` 挪到 `endSession`
  （F3-05 的确认闭环天生要「确认完再重启一次会话」，在 beginSession 清空会造成「确认→重启→再确认」死循环）；
  新增 `dismissPendingConfirmation()`。
- `screentranslation/ScreenTranslationModels.kt`：新增 `ScreenTranslationEvent.ConfirmationDismissed`（Stopped 故意保留待确认域名，只有明确放弃才清）。
- `screentranslation/ScreenTranslationOverlayView.kt`：`scaledDensity` 弃用改为 `TypedValue.applyDimension(COMPLEX_UNIT_SP, ...)`。
- `screentranslation/ScreenTranslationControlBar.kt`：裸 hex 改成 `Color.argb(...)` 十进制三通道（棘轮测试要求 `ui/` 之外零裸 hex）。
- 新增 `screentranslation/ScreenTranslationCopy.kt`：`noticeText(notice)` / `costLine(sentRequests, modelName)` / `phaseLabel(phase, mode)` 三条纯函数，
  文案口径：「本机识别不可用」绝不说成「翻译失败」；成本句不写金额（单价随供应商变），只写已发请求数 + 模型名 + 「按 AI 服务实际计费」。

### 2.2 UI 与接线
- 新增 `ui/ScreenTranslationEntry.kt`：`ScreenTranslationSection(...)`——拍照翻译页里的入口段。
  `EntryRow(icon = AppIcon.Translate, title = "悬浮窗屏幕翻译")` 折叠 + `SoftCard` 面板；权限判定 `Settings.canDrawOverlays` +
  `ON_RESUME` 重查；「去系统设置开启」（`ACTION_MANAGE_OVERLAY_PERMISSION`）；「单次翻译当前屏」/「连续翻译」两枚按钮
  → `ActivityResultContracts.StartActivityForResult` 取 MediaProjection 授权 → `ContextCompat.startForegroundService(startIntent(..., once))`；
  运行中显示「停止屏幕翻译」；成本句与两类提示（会话级错误 / 启动期提示）。
  出站确认复用共享件 `OutboundConfirmDialog`（三段自有 tag `screen_translation_outbound_confirmation_text` / `screen_translation_confirm_outbound` / `screen_translation_decline_outbound`），
  确认后 `controller.confirmTextHost(host)` 并**重新申请一次截屏授权**再启动会话（令牌一次性）。
- 新增 `ui/ScreenTranslationViewModel.kt`（`@HiltViewModel`）：把 controller 的 snapshot 交给 Compose，三个动作
  `confirmOutbound` / `declineOutbound` / `stop`。
- `ui/PhotoTranslationScreen.kt`：新增 4 个**无默认值**形参（`screenTranslation` 快照 + 三个回调），在离线模型折叠之后插入一行
  `ScreenTranslationSection(...)`。屏内不调 `hiltViewModel()`（androidTest 用 `createComposeRule` 无 Hilt 直接渲染该屏）。
- `ui/AppScreen.kt`：新增 `screenTranslationViewModel: ScreenTranslationViewModel? = null`，拍照翻译分支解包成快照 + 四个回调。
- `MainActivity.kt`：`hiltViewModel<ScreenTranslationViewModel>()`。

### 2.3 测试
- 新增 JVM 单测：`screentranslation/ScreenTranslationCopyTest.kt`（8 类错误文案都非空且可读；「本机识别不可用」不说成翻译失败；
  成本句含次数与模型名、模型缺失说「未配置模型」；阶段文案三档）、`screentranslation/ScreenTranslationControllerTest.kt`
  （确认台账跨会话保留、endSession 清空、ConfirmationDismissed 只清待确认不动台账、Stopped 保留待确认域名）。
- `screentranslation/ScreenTranslationSessionTest.kt` 补一条：`OcrFailed` 这类「没读出字」不结束会话。
- 修既有契约测试的漂移：`ui/ScreenTransitionContractTest.kt` 的 `MOUNT_CONDITION_EXEMPTIONS` 行号 596–600 → 602–606（AppScreen.kt 前面加了形参）。
- 修 **androidTest 基线破损**（F3-04 遗留，`assembleDebug` 不编译 androidTest 所以一直没暴露）：
  `PhotoTranslationScreenTest` / `RequestInProgressEvidenceTest` 缺 onOverlay* 五个新形参；
  `AppScreenTest` 的 `PhotoTranslationViewModel` 夹具缺 F3-04 新增的 `translateBlocks`。
- 规格：`docs/specs/03-dictionary-pronunciation-and-photo-translation.md` 的 F3-05 段补一条「『这一屏没有可翻的英文』不是会话级错误」。

## 3. 验证记录

| 命令 | 结果 |
|---|---|
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL（新增 2 个测试类 + 1 条 reducer 用例全绿；其间修掉 4 条红：文案断言「再」→「重新」、ControlBar 裸 hex、ScreenTransition 行号两条） |
| `:app:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL（先修 F3-04 遗留 + 本轮新形参；一度把实参误插到 `setScreen(` / `SentenceAnalysisScreen(` / `ImageStudioScreen(` 调用上，已删净重插） |
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| `adb install -r app\build\outputs\apk\debug\app-debug.apk`（bf353dda = M2102J2SC） | `Success` |
| `:app:connectedDebugAndroidTest -P...class=...PhotoTranslationScreenTest` | `Finished 9 tests on M2102J2SC`，BUILD SUCCESSFUL |

### 真机走查（2026-11-10，设备 bf353dda）
1. AI 助学 → 拍照翻译 → 折叠段「悬浮窗屏幕翻译」渲染正常：隐私句、权限提示、两枚按钮、成本句（「本次会话已发 0 次请求 · 模型 未配置模型 · 按 AI 服务实际计费」）齐全；
2. 未开悬浮窗权限时出现「还需要『显示在其他应用上层』权限…」+「去系统设置开启」；`appops set SYSTEM_ALERT_WINDOW allow` 后提示消失；
3. 点「连续翻译」→ 系统截屏授权弹窗（系统原生文案）→「立即开始」→ 段内变成「会话进行中 · 连续」+ 出现「停止屏幕翻译」，前台服务通知建立；
4. **中文界面这一帧没读出字**：面板显示「这一屏没读出文字：换一屏、或把要翻的内容放大后重试。」而**会话仍在运行**（本轮修复的关键证据，旧代码此处会话已结束）；
5. 切到浏览器里一页英文后：会话走到了 OCR → 块规划 → 出站，最后显示「还没有可用的 AI 服务：先在 AI 设置里配置一个文本模型，再回来开始。」（本机没有配置任何 AI Profile，属预期）；
6. 全程 logcat 无 FATAL / 无 ANR。

**未能在真机上验到的**：悬浮窗真实绘制译文、出站确认框的真实弹出。原因是本机没有可用的 AI 文本 Profile
（没有 endpoint/Key），一帧走不到 `Translated` / `NeedsConfirmation` 分支。这两条目前只有 JVM 单测与共享件契约测试覆盖。

## 4. Git 状态

- 分支：`stage-5-ui-r2-learning-path`（跟踪 `origin/stage-5-ui-r2-learning-path`）。
- **本轮没有提交**：工作区里同时躺着 F3-04 与 F3-05 的改动（约 20 个已跟踪文件被改 + 十余个新增文件/目录），
  是否一起提交、拆成两个提交、以及是否推送 GitHub 需要用户拍板（AGENTS.md 要求推送前确认分支与认证）。
- 未被 Git 跟踪的本地产物：`app/src/main/java/com/example/englishlearning/screentranslation/`、`phototranslation/` 新文件、
  `ui/ScreenTranslationEntry.kt`、`ui/ScreenTranslationViewModel.kt` 等（都是本轮/F3-04 的源码，需随提交纳入）。

## 5. 未完成 / 遗留问题

1. （F3-05）真机上「悬浮窗真的画出译文」与「首次发送前的应用内确认框」还差一次带可用 AI Profile 的走查；
2. （F3-05）连续模式的成本保护：目前只有 3 秒节流 + 指纹去重 + 单屏块数上限；若用户配置了很贵的模型，是否需要「本会话请求数上限」待用户决定；
3. 例句 AI 生成（方案 A）代码未写，spec 01/02 已改；
4. 保存计划按钮色 `#2F6B54` 待用户确认（m06156）；
5. 书封改造待用户认可（m06151）；
6. m06193：继续学习配色 / 长按进详情 / 显示释义后模糊；
7. stale git proxy `http://127.0.0.1:3067` 是否清理待定（m06148）。

## 6. 下一任接手须知

- **先读**：本报告 → `AGENTS.md` → `docs/specs/03-dictionary-pronunciation-and-photo-translation.md` 的 F3-05 段 →
  `docs/decisions/2026-11-10-in-place-text-replacement-and-screen-overlay-translation.md`。
- **跑什么**：`cmd /c gradlew.bat testDebugUnitTest`（全绿才动别的）、`compileDebugAndroidTestKotlin`（`assembleDebug` **不**编译它，
  F3-04 的形参漂移就是这么漏掉的）、`assembleDebug` + `adb install -r`。
- **坑**：
  - `AppScreen.kt` / `MainActivity.kt` / `AppScreenTest.kt` 都是 **CRLF**，拼锚点要按文件实际换行；
  - `AppScreen.kt` 里 `onOpenAiSettings = {...}` + `)` 这段出现三次（长难句 / 拍照翻译 / AI 生图），锚点必须带该调用点独有的行；
  - `ScreenTransitionContractTest` 把 `AppScreen.kt` 的 `val overlayOpen =` 行号钉死（现在 602–606），在它之前增删行就要重算；
  - `ui/` 之外不允许裸 hex（用 `Color.argb(十进制...)`）；
  - 屏内绝不能调 `hiltViewModel()`：`PhotoTranslationScreenTest` 用 `createComposeRule` 无 Hilt 直接渲染；
  - 真机 `am start -n .../.MainActivity` 会被拒（未导出），用 `monkey -p <pkg> -c android.intent.category.LAUNCHER 1` 拉起。

## 7. 用户确认过的决策

- 每次会话都要一次 MediaProjection 授权、不假装长期免授权（沿用 `docs/decisions/2026-11-10-...` 的决策）；
- 确认台账跨会话重启保留、只在 endSession 清空（本轮实现取舍，理由见 §2.1）；
- 「这一屏没读出字」不停会话（真机走查直接驱动，已写进 spec 03）。
