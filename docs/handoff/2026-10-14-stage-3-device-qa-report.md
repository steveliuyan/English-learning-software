# 2026-10-14 Stage 3 / F3-03 拍照翻译 真机走查报告（设备 QA 分对话）

分支：`stage-3-f3-03-photo-ocr`（走查基点 tip `29ab7f4`）。任务书：`docs/handoff/2026-10-14-f3-03-device-qa-task.md`。设备：小米 Mi 10S（M2102J2SC），Android 13（API 33），**带 GMS**（`com.google.android.gms` 在位）。被测 APK：`app-debug.apk` versionName 0.0.1 / versionCode 1，targetSdk 36，debug 构建。

## 1. 本次目标

F3-03 拍照翻译真机走查 + T2 抓包核验：v28→v29 迁移 connected 测试、B 组功能全走查、AC3-04 无 GMS 降级行为与 T2（ML Kit 外联）核验，产出报告与缺陷清单，不改代码不合分支。

## 2. 完成了什么

- A 组：`AppDatabasePhotoTranslationMigrationTest` 在真机 bf353dda 跑通（见 §3）。
- B 组：拍照翻译 8 项走查完成 7 项，B5（保存/删除/备份侧）经用户裁决跳过（§3）。
- C 组：AC3-04 真机降级行为核验完成；T2 抓包按任务书优先级推进到方案 b 变体（mitmproxy + 用户 CA）并取得**应用级 TLS 隧道级证据**，内容级解密部分完成（§4）。
- 测试数据清理：测试图片（文件 + media store 行）、QAFake AI Profile、`/sdcard/ui.xml` 均已删除；设备代理已撤、GMS 已恢复启用。
- 本报告。

## 3. 验证记录

### A 组：v28→v29 迁移（真机）

命令（第三次尝试通过；前两次为环境问题，见 §6）：

```
gradle.bat :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.core.storage.AppDatabasePhotoTranslationMigrationTest \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=3067 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=3067
```

结果：**BUILD SUCCESSFUL in 30s**；XML 报告（`app/build/outputs/androidTest-results/connected/debug/TEST-M2102J2SC - 13-_app-.xml`）`tests="1" failures="0" errors="0" skipped="0"`。测试用独立库名 `migration-photo-translation-test`（`AppDatabasePhotoTranslationMigrationTest.kt:88`），生产库 `english-learning.db`（`AppModule.kt:105`）不受影响；真机学习数据未被触碰。**验收记录 §6.2「迁移未在设备执行」就此关闭。**

### B 组：功能走查（每项：步骤 / 预期 / 实际）

| # | 项 | 实际结果 | 结论 |
|---|---|---|---|
| B1 | 相机权限 | 撤销 CAMERA 后点「拍照」弹系统权限框；拒绝→页面提示「相机权限被拒绝。可改用相册选图，或点下方按钮重新授权。」；选「拒绝且不再询问」后再点不弹框、不崩溃、提示仍在 | ✅（但提示文案与实际不符，见缺陷 D-1） |
| B2 | 相册 Photo Picker | 点「从相册选图」弹系统 Photo Picker（「此应用只能访问您选择的照片」），未请求存储权限 | ✅ |
| B3 | 识别/低置信/编辑 | 本地生成英文样张（Pillow，1200×900，Arial 64）推入设备；ML Kit 识别 5 行文本全部出现且可编辑（实测删除字符成功）；「tonmorrow」误识别未触发低置信标注，与阈值 0.5 逻辑一致（`PhotoTranslationViewModel.kt:392-399`、`MlKitTextRecognizerClient.kt:41`）；后续样张（截屏类图）触发「以下行识别把握较低：第 1、3、4、5、10 行」标注 | ✅ |
| B4 | 确认门 | 未配置 AI：点翻译→「还没有默认的 AI 服务…」卡片，零出网（按钮下方，需滚动可见）。配置 QAFake（`https://qa-fake.invalid.example`，假 key）后：弹「确认发送请求」——域名 `qa-fake.invalid.example`、明示「识别文本与密钥将发送」「图片不会发送」、有「确认发送/取消」；**每次发送前都重弹**（连续确认两次后第三次仍弹，符合 AC3-06）；拒绝后 logcat 零 socket/DNS 活动；确认后 DNS 解析失败→「网络不可用，检查网络后重试。」错误卡独立显示，OCR 文本保留可编辑 | ✅ |
| B5 | 保存/删除/备份 | **经用户裁决跳过**（需要一次真实成功翻译；假域名无法达成，任务书禁止上传用户数据）。JVM 侧证据见验收记录 §3，真机端到端仍空缺 | ⏸ 未验证 |
| B6 | 离线模型卡 | 「下载模型（约 4.1 MB）」→「下载中…」→「已安装（4113088 字节）」；网络抖动时「下载失败：timeout。检查网络后重试。」可重试成功；「删除」→回「下载模型」态 | ✅ |
| B7 | 退出清场 | 退出重进后无 OCR 文本、无历史记录残留、无崩溃；模型状态正常保留 | ✅ |
| B8 | 深色模式 / TalkBack | 深色模式：`cmd uimode night yes` 后页面仍为浅色（无深色主题适配，见缺陷 D-2）；TalkBack：「拍照」「从相册选图」「返回 AI 学」有 contentDescription；模型卡「下载/删除」按钮与识别文本编辑器无独立语音标注（文字本身可读）；TalkBack 实机走读被 MIUI 无障碍套件权限弹窗链阻塞，未完成 | ⚠️ 部分 |

### C 组：AC3-04 与 T2

**C1 设备 GMS 状态**：带 GMS（Mi 10S 出厂内置）。

**C2 无 GMS 模拟（`pm disable-user --user 0 com.google.android.gms` + force-stop，gms 进程数 0）**：
- 应用启动、导航、拍照翻译页正常，无崩溃。
- OCR 实测仍走 **ML Kit（本机）** 识别成功（logcat 显示 `tflite_model_pooled_runner` 加载模型）。
- **根因（依赖树证实）**：`com.google.mlkit:text-recognition:16.0.1` 同时带入 `play-services-mlkit-text-recognition:19.0.1`（GMS 路径）与 **`text-recognition-bundled-common:17.0.0`（bundled 路径）**——bundled AAR 内含 11MB `libmlkit_google_ocr_pipeline.so`，tflite OCR 引擎直接打进 APK，不需要 GMS 进程。因此在本机，「无 GMS」场景 ML Kit 依然可用，**不会**触发降级 Tesseract，也不会出现不可用信号。
- 真机上 **`OcrChainOutcome.OcrUnavailable` 不可构造**：bundled ML Kit 无法卸载或禁用（so 在 APK 内）；Tesseract 侧通过移走/损坏 `eng.traineddata` 验证了「Tesseract 不可用（`Unavailable`，模型缺失时状态回「下载模型」）+ ML Kit 正常」的路径；「双双不可用」只能由 JVM 测试覆盖（`OcrProviderChainTest` 已有 5 条）。
- AC3-04 结论：**降级链在真机的可达部分已验证**（ML Kit 可用则直接用、Tesseract 缺模型时回退链不中断、OCR 失败与翻译失败文案分离）；「无 GMS 自动落 Tesseract」在本机因 bundled 实现而**不可达**，该分支真机验证以 bundled 依赖事实 + JVM 覆盖为限。

**C3 T2 抓包（按任务书优先级）**：
- a. `tcpdump`：设备无 root、无 tcpdump 二进制 → **不可行**。
- b. mitmproxy：本机装 mitmproxy 12.2.3，`mitmdump --listen-host 0.0.0.0 --listen-port 8888`；设备全局代理指向 192.168.1.76:8888；mitm CA 经「设置→加密与凭据→安装证书→CA 证书」装入用户凭据（需设备 PIN，用户协助输入）。Manifest 无 `networkSecurityConfig`、targetSdk 36，用户 CA 默认不被信任——但**实测本 app（及系统组件）的 CONNECT 隧道全部可见**，且翻译请求 `CONNECT qa-fake.invalid.example:443` 由 app 发起并被 mitmproxy 捕获（DNS 失败 502，符合预期）。Tesseract 模型下载走 HTTPS 直连，在抓包窗口内对应 `raw.githubusercontent.com` 连接（下载成功时）。
- c. `dumpsys netstats` 间接证据（uid=10384，bucketDuration=7200）：
  - **OCR 全程零出网**：ML Kit 识别前后 `uid=10384` 全部 bucket（tag 0x0 与 0xffffff82）**字节级一致**（diff 为空）；mitmproxy 在 OCR 时间窗内无来自设备的新连接。
  - **确认门拒绝零出网**：拒绝前后 netstats 双采样一致；mitmproxy 中 `qa-fake.invalid.example` 连接总数恒为 1（即先前「确认」那一次），拒绝后无新连接。
  - **翻译确认后真实出网**：仅 1 条 `CONNECT qa-fake.invalid.example:443`，与确认动作一一对应。
- **ML Kit 外联观察**：设备上出现过 `CONNECT play.googleapis.com:443`（502，client 断开），但时间点与 OCR/翻译操作不相关（OCR 窗口 netstats 零增量），判断为系统级（Play 服务）而非本 app OCR 触发。**bundled ML Kit 在识别全程未产生任何可观测外联**——与决策文档 §5 T2「验证 ToS 第 3 条 metrics 描述」的关注点一致：本机未观测到 ML Kit 因识别而联系 Google 服务器。
- **T2 结论：部分核验（隧道级 + 流量级），未达内容级解密**。原因：MIUI 用户 CA 安装流程中证书内容未被确认落入 app 信任链（未出现系统「证书已安装」确认态，且 `cacerts-added` 无 shell 读取权限无法复核），TLS 内容解密未成功。**剩余方法**：① 在「设置→信任的凭据→用户」页人工确认证书在列后重测；② 或改用 Magisk 模块把 CA 装入系统 store（需 root）；③ 或用 Android 模拟器（可写 `/system`）完整解密。隐私脚注文案「ML Kit 可能向 Google 发送与图片、文字内容无关的性能指标」与实测不冲突（未观测到 ≠ 不会发生，披露保留是正确的）。

## 4. AC3-04 / 05 / 06 真机结论

| 条目 | 结论 | 说明 |
|---|---|---|
| AC3-04 | **真机已验证（可达部分）** | 无 GMS 模拟下 ML Kit（bundled）正常识别；Tesseract 模型缺失→`Unavailable` 不阻断链路；`OcrUnavailable` 真机不可构造（bundled so 在 APK 内），由 JVM 5 条链测试覆盖。「自动落 Tesseract」分支因 bundled 依赖事实在本机不可达——建议在决策文档补一句依赖树证据 |
| AC3-05 | **部分验证** | 确认前/拒绝后零出网（mitmproxy + netstats 双证据）✅；「未保存→历史/媒体/备份零残留」的端到端（B5）未真机验证（需真实翻译成功，用户裁决跳过） |
| AC3-06 | **真机已验证** | 每次发送前确认门重弹；只送编辑后文本（确认框文案+确认后载荷域名正确）；失败后文本保留可编辑、错误类别独立 |

## 5. 缺陷清单（交主对话裁决修复，本对话未改代码）

| ID | 严重度 | 复现 | 期望 / 实际 |
|---|---|---|---|
| D-1 | 低 | 相机权限选「拒绝且不再询问」后进入拍照翻译页 | 提示「…或点下方按钮重新授权。」但页面**没有**重新授权按钮（`PhotoTranslationScreen.kt:119` 文案承诺了不存在的入口；点「拍照」只会静默失败再提示）。建议：文案改为「去系统设置开启相机权限」并提供跳转 `ACTION_APPLICATION_DETAILS_SETTINGS`，或删掉「点下方按钮」半句 |
| D-2 | 中（全局债，非 F3-03 特有） | `adb shell cmd uimode night yes` | AGENTS.md 要求支持深色模式；实际全局无深色主题（`MintPalette` 单调色板、无 `isSystemInDarkTheme`），拍照翻译页在深色模式下仍渲染浅色。已约定功能完成后做一轮 UI 重设计（见项目记忆），此项归入该轮 |
| D-3 | 信息 | 连续多次「确认发送」→DNS 失败 | 失败文案统一为「网络不可用，检查网络后重试。」，DNS 解析失败与真断网未区分（`UrlConnectionAiHttpTransport.kt:54-55` 把所有 IOException 折叠为 NetworkUnavailable）。可用性问题，非缺陷级 |
| D-4 | 信息 | 模型下载偶发「下载失败：timeout」（3 次尝试 2 失败 1 成功） | GitHub raw 直连在用户网络下不稳定（15s 超时偏紧）。可考虑提高超时或失败文案给出「检查网络后重试」之外的指引。有重试路径，不阻塞 |

## 6. 未完成 / 遗留问题

1. **B5 端到端（保存→历史→删除→备份导出侧）真机验证仍空缺**——需要一套真实可用 AI Profile 才能走通；下次走查（或用户配置好 AI 后）补做。
2. **T2 内容级解密未完成**：剩余方法见 §4-C3。若走模拟器路线需另约环境。
3. **TalkBack 实机走读未完成**：被 MIUI 无障碍套件权限弹窗链阻塞（与 app 无关）；已用 uiautomator 树抽查 contentDescription 覆盖作为部分证据。建议后续在干净环境（或关闭 MIUI 增强服务）重走。
4. **设备环境恢复项（需用户手动）**：mitmproxy 用户 CA 仍在设备用户凭据里，建议在「设置→信任的凭据→用户」删除；设备代理已撤、GMS 已启用，无需处理。
5. 真机 QA 期间发现的 MIUI 怪癖（供后续会话）：系统清理组件（u2000）会在 adb install 后自动卸载应用（events log `pm_critical_info: Uninstall ... from u2000`），connected 测试/手动安装后需确认包仍在；WeType 输入法拦截 `adb shell input text`，需先 `ime set` 切搜狗。

## 7. 用户确认过的决策

1. **B5 跳过**：保存/删除/备份侧真机验证因需要真实 AI 服务而跳过，报告如实记录「未验证」（2026-10-14 对话中拍板）。
2. **T2 用户提供设备 PIN**：用于完成 mitmproxy 用户 CA 安装的设备凭据验证（仅用于本次走查，PIN 未写入任何文件）。

## 8. Git 状态

- 分支 `stage-3-f3-03-photo-ocr`，本报告以新提交追加（仅报告文件，无代码改动）；推送后以 `git ls-remote` 核验。
- 测试期间产生的设备端数据均已清理（§2）；仓库工作区除本报告外无其他改动。
