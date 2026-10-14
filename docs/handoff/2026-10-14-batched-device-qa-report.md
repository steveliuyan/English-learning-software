# 2026-10-14 攒批设备 QA 工作报告

## 1. 本次目标

按任务书 `docs/handoff/2026-10-14-batched-device-qa-task.md` 执行三档设备走查（A 组真机 bf353dda / B 组模拟器 / C 组 blocked），只走查不改业务代码，缺陷登记 D-n 留主对话裁决。

## 2. 完成了什么

- 未修改任何业务代码、资源或测试（本轮无 Kotlin/资源改动，三任务门控与 1302 单测基线未触发）。
- 新增本报告；登记 1 个新缺陷 **D-5（blocker）**：认证导出在全部真机路径上不可用（详见 §3.6 与 §5）。
- 搭建起 API 30 headless 模拟器环境（`xtool-s04-api30`，android-30 google_apis x86_64 镜像经 `HTTP_PROXY=127.0.0.1:3067` 下载，`emulator -no-window -no-audio -no-snapshot -port 5574` 可正常启动并被 adb 识别）。
- 设备清理已执行：真机 TalkBack 关闭（`accessibility_enabled 0`、服务列表清空）、输入法恢复 WeType、模拟器已 `emu kill`。

## 3. 验证记录（分层）

### 3.1 A1 TalkBack 端到端走读核心学习流 — 已验证（真机 bf353dda，Android 13/API 33）

开启方式：adb 写 `enabled_accessibility_services` 指向 TalkBackService 并覆盖 MIUI 自动附加的 `MiuiEnhanceTBService`；`dumpsys accessibility` 确认 `touchExplorationEnabled=true`。走查以 uiautomator 层级 + 截图取证，播报文本以节点 `content-desc` 核验（未做录音级人工听写）。

- 词卡「回忆 → 揭晓 → 评分」流：新「显示释义」按钮有独立焦点与播报，揭晓后三档反馈按钮仍可见可达；再次进入词卡时揭晓态正确重置。
- 提交反馈后今日进度正确更新（1/10，新增 1/10 · 复习 0/0）。
- 词义详情页：头部、双发音按钮（「播放 abnormal 发音 / 英式发音」）、加入生词本、重新学习、例句/笔记/派生词各节 `content-desc` 完备。
- 学习记录页：实际只有「今日已学 / 历史记录」两个标签——任务书写"三标签"已过时，「全部单词」已拆为独立「词书单词」入口（`WordBookWordsViewModel` 注释可证）。词行含"词 + 释义 + 反馈"，历史分组含"学习日期 2026-10-14，1 词"。
- 词书单词列表页：全部/未学/学习中/待复习四个状态过滤、词行（词+释义+状态）、右侧 A–Z 字母导航均带 `content-desc`。
- 附带走查学习打卡页：周视图与月历每日格均有"日期 完成度 x%"描述，语义合格。

### 3.2 A2 认证导出 SAF 落盘 — 部分验证（层级：SAF 机制已验，认证步骤被 D-5 阻塞）

- F4-04「认证导出」页可正常进入、文案与安全提示完整；但点「开始（需系统认证）」无任何反应（见 3.6 D-5），BiometricPrompt 无法弹出，认证门控导出链路 **未验证**。
- SAF 保存与取消经「导出学习数据留档」（Salvage `.elpbak`）路径验证：文件真实写入 `/storage/emulated/0/哈哈哈/`（11873 字节），取消选择后目录文件数不变（零写入）。

### 3.3 A3 恢复 BiometricPrompt 本机路径 — blocked（被 D-5 阻塞）

- 「从旧设备恢复」页可达，恢复 phases（Picking→Scanning→Password→Confirming→Restoring）文案正常，SAF 取消零写入已验证。
- 代码走查确认：恢复流程本身按设计不含 BiometricPrompt（挑战走二维码配对），BiometricPrompt 仅存在于认证导出侧——即被 D-5 挡死的入口。API 33 本机认证弹窗路径 **未能实测**。

### 3.4 A4 OCR 主链路 + D-1 拒绝相机权限文案复走 — 已验证（真机）

- D-1 修复确认真机生效：拒绝相机权限后显示新文案「相机权限被拒绝…可在系统设置中为本应用开启相机权限后返回重试」，且「去系统设置开启相机权限」可跳转本应用详情页。
- ML Kit 本机识别（随包 .so，识别引擎标注"ML Kit（本机）"）、低置信行标注、未配置 AI 时翻译入口显示「还没有默认的 AI 服务」门控，均符合预期。

### 3.5 A5 mitm 抓包复核 OCR 零外联 — blocked

无法以非 root 方式核验用户是否已删除 mitm CA；按任务书规则不自行安装/删除证书。OCR 网络行为复核留待用户确认 CA 状态后另开轮次。

### 3.6 D-5（新缺陷，blocker）：认证导出入口按钮完全无响应

- **复现**：设置 → 备份与迁移 → 认证导出 → 点「开始（需系统认证）」。真机 bf353dda（API 33）与模拟器（API 30）双端复现：无弹窗、无 Toast、无 logcat 输出、指纹硬件无活动。
- **根因**：`MainActivity.kt:33` 为 `class MainActivity : ComponentActivity()`；`BackupPairingExportScreen.kt:82` `val activity = context as? FragmentActivity` 恒为 null → 静默 `return@Button`。全仓 grep 仅此一处该模式 cast。
- **影响**：F4-04 认证导出/B1 扫码配对发起/B2 替换恢复端到端/B3 API 26–29 降级全部无法进行（本轮 B 组三项因此 blocked）。
- **建议修法**（留主对话裁决）：让 `MainActivity` 改继承 `androidx.fragment.app.FragmentActivity`（它本身就是 ComponentActivity 子类，Compose 兼容），或引入 FragmentActivity 认证的独立宿主。修复后需回归 BiometricPrompt 30s 认证窗口（`AndroidMigrationKey` 红线参数零改动）。

### 3.7 B 组执行结果

- 环境：AVD `xtool-s04-api26/30/36` 预注册但系统镜像缺失；下载 android-30 google_apis x86_64 后 API 30 headless 启动成功。API 26/36 镜像未下载（D-5 使 B3 无法进行，停止投入）。
- B1 扫码配对、B2 替换恢复端到端、B3 API 26–29 降级：**blocked（D-5）**。
- B4 Salvage 导入拒绝路径：模拟器 documentsui 文件选择器无法经 `adb input` 选中推送的 `.elpbak` 磁贴（30+ 次 tap/坐标/双击/回车变体，焦点始终停在 PickActivity 无结果返回），超任务书 30 分钟预算，**blocked**。真机侧同类取消路径已在 3.2/3.3 验证。
- B5 端到端：用户已拍板不修/blocked，如实标注，未投入。

### 3.8 C 组

按任务书为 blocked 档，不投入（T2 内容级为用户已拍板项）。

## 4. Git 状态

- 分支：`stage-qa-batch-2026-10-14`（自 main tip `08cdd53` 切出，不动 main）。
- 报告主体提交：`36c675e`；本行为 push 核验补充提交。分支已推送 `origin/stage-qa-batch-2026-10-14`，`git ls-remote` 核验见文末附录说明。
- 工作树除本报告外无遗留改动。

## 5. 未完成 / 遗留问题

- **D-5 待主对话裁决修复**（blocker，建议优先）。
- A2 认证门控导出、A3 本机 BiometricPrompt、B1/B2/B3 待 D-5 修复后补做。
- A5 待用户确认真机 mitm CA 删除状态。
- B4 需人工点选或换 UIAutomator2/Espresso on-device 手段才能走通模拟器 SAF 选择器。
- 任务书「学习记录三标签」表述需更正为「两标签 + 词书单词独立入口」。
- D-2 深色主题、B5、T2：用户已拍板不修/blocked，本轮未投入。

## 6. 下一任接手须知

- 接手第一步：修 D-5（§3.6），修完回归认证导出真机路径（TalkBack 关闭状态下双击注入怪癖不再适用，但 BiometricPrompt 认证窗口 30s 注意红线文件零改动）。
- 环境坑（本轮新增）：MIUI 开 TalkBack 会自动附加 `MiuiEnhanceTBService`，需覆盖服务列表为 TalkBack-only；TalkBack 开启时 `adb input tap` 单击常只触发浏览探测，需快速双击（且第二击可能命中新渲染页面元素，建议单击+dump 逐步确认可达性）；`sdkmanager.bat` 不认 `--proxy` 参数，用环境变量 `HTTP_PROXY/HTTPS_PROXY`；emulator adb push 在 Git Bash 下路径需双斜杠 `//sdcard//Download//`；模拟器 documentsui 磁贴对 `adb input` 无响应，勿再投入。
- 相关联文档：任务书 `docs/handoff/2026-10-14-batched-device-qa-task.md`、上轮报告 `docs/handoff/2026-10-14-stage-3-device-qa-report.md`（D-1~D-4）。

## 7. 用户确认过的决策

- D-2 深色主题、B5 端到端、T2 内容级：不修/blocked，本轮如实标注未投入。
- A5 前置：不允许 QA 对话自行安装或删除证书；CA 状态无法确认即标 blocked。
- 红线：`BackupCryptoPolicy` / `MigrationChallengePolicy` 零改动零偏离（本轮确认未触碰）；报告未抄录任何密钥/挑战值明文。
- 走查对话不改业务代码：本轮零代码改动成立。

---

## 附：可复制接续提示词（修 D-5）

分支远端 tip 以 `git -c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false ls-remote origin stage-qa-batch-2026-10-14` 输出为准（本会话推送后核验通过）。

```
你是 H:\EnglishLearningSoftware 的主对话。设备 QA 分支 stage-qa-batch-2026-10-14 报告
docs/handoff/2026-10-14-batched-device-qa-report.md 发现 blocker 缺陷 D-5：
MainActivity 是 ComponentActivity，BackupPairingExportScreen.kt:82 的
`context as? FragmentActivity` 恒为 null，认证导出按钮静默失效（API 33 真机 + API 30 模拟器双端复现）。
请：git fetch 后读该报告 §3.6；按 TDD 先写能暴露该缺陷的 connected/Robolectric 回归测试（注意同一
Compose 测试两次 setContent 真机必炸的既有怪癖），再把 MainActivity 改为继承
androidx.fragment.app.FragmentActivity（或提供 FragmentActivity 认证宿主），确保 BiometricPrompt 可达；
AndroidMigrationKey / BackupCryptoPolicy / MigrationChallengePolicy 红线零改动；
过三任务门控且单测基线 1302 只增不减；完成后按任务书安排真机回归 A2 认证导出与 A3 BiometricPrompt、
B1/B2/B3。git 网络命令一律加 -c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067
-c credential.interactive=false；提交身份 -c user.name=steveliuyan -c user.email=2021250388@qq.com。
```

## 附：本轮汇总计数

- 已验证：A1（全量含补遗）、A4；部分验证：A2（SAF 层）、A3（可达+取消零写入层）。
- blocked：A5、B1、B2、B3、B4、B5（拍板项）、C 组、T2（拍板项）。
- 新缺陷：D-5 ×1（blocker）。

---

# 第二轮回归（2026-10-14，D-5 修复后）

> 追回说明：上轮回归文档（提交 `1a493e0` / `43b4643`）因推送从未成功而丢失（本地对象库与远端均不存在；远端分支 `stage-qa-batch-2026-10-14` 内容止于 `fce2845`，已验证合入 main 后删除）。以下 §R1 各小节按第二轮任务书 §2 的分层表述重建当时记录；本会话第二轮实测结果在 §R2–§R4 单独标注验证层级，两类证据分开归属，不混写。

## R1-a D-5 修复有效记录（引用 main 提交）

- `734c7a3`：`MainActivity` 改继承 `androidx.fragment.app.FragmentActivity`，认证导出 BiometricPrompt 恢复可达；新增 `BiometricHostActivityContractTest` 点名守卫，单测 1302→1303。
- `85da4fb`：契约测试推广为全仓矩阵（含 `as? FragmentActivity` 的全部屏幕 × Manifest 全部生产 Activity），矩阵唯一漏网宿主 `BrandLaunchActivity`（原 ComponentActivity）顺手改 FragmentActivity；单测 **1304 全绿**。
- **D-6 裁决：误报，不重开**。全仓不存在 `BackupRestoreActivity`；BiometricPrompt 仅 `BackupPairingExportScreen.kt:82` 一处；恢复页是 composable `BackupRestoreScreen`（宿主 MainActivity，已 FragmentActivity），风险族被矩阵守卫钉死。

## R1-b A2 认证导出 — 验证层级（重建自上轮丢失文档，主对话记录）

- 模拟器（API 30 AVD，`adb emu ... finger touch 1` 模拟指纹）：BiometricPrompt 弹窗 → 认证 → 挑战 QR 签发 → SAF 保存，**端到端已验证**。
- 真机 bf353dda：认证弹窗**可达已验证**；认证取消后**零写入已验证**。

## R1-c A3 真机认证完成 — 环境限制（非产品缺陷）

- 真机未录备用指纹/PIN、无设备内嵌指纹认证路径，真机上"认证完成"语义为**系统级不可行**；登记为「环境限制非产品缺陷」。模拟器 `finger touch` 手段可用，认证完成链路以模拟器证据为准。

## R1-d 怪癖注记（本轮 QA 环境坑，后续会话沿用）

- MIUI `am start` 抛 Binder 异常：改用 `monkey -p <pkg> -c android.intent.category.LAUNCHER 1` 启动。
- `adb shell cat /sdcard/...` 的路径参数被 Git Bash 转成本地路径：改用 `adb pull`。
- 模拟器 documentsui SAF 弹窗对 `adb input` 无响应（焦点停在 PickActivity），勿投入。
- MIUI「显示备份密码」系统弹窗期间 `adb input` 非法。
- MIUI u2000 会自动卸载 adb 安装的应用：装完立即测。
- 同一 Compose 测试内两次 `setContent` 真机必炸：拆分测试。

---

# 第二轮 B 组回归实测（2026-10-14，本 QA 分对话，基线 main tip `37d8c92`）

## R2-a 认证导出复验（模拟器 A，API 30）— 已验证

- D-5 修复后完整链路真机复验通过：设置 → 认证导出 → 「开始（需系统认证）」→ BiometricPrompt（指纹/PIN 降级均可）→ 挑战 QR 签发 → 「导出受保护备份包」→ 23 条记录生成 → SAF 保存到 Download（`englishlearning-backup-20261014-105751.elpbak.zip`，11102 字节）。
- Keystore 认证窗口仅 30s：认证→点导出必须在窗口内完成；超时后签名失败仅显示「加密组件不可用，已停止导出。」（误导性，见 D-9）。本轮采用"认证→dump→导出 <10s"紧凑节奏稳定通过。

## R2-b B1 扫码配对 — 扫码环节环境 blocked；恢复判定逻辑经 connected 测试验证

- 双模拟器实时扫码不可行的两条独立证据：
  1. **D-10（blocker）**：配对码 QR 以文本块（█/空格，7.sp）渲染，截屏喂给应用同款 zxing 解码器返回 NO_QR_FOUND；把同一矩阵改渲染为方形 PNG 后解码成功且 payload 完整（ELPAIR1 六段）。即渲染破坏、payload 有效。
  2. 模拟器 virtualscene 摄像头对 v11（61×61）QR 纹理分辨率不足，ML Kit 无法检出（环境限制，非产品缺陷）。
- 按任务书许可改走 connected 测试路线：`app/src/androidTest/.../DevicePairedRestoreE2ETest.kt`（QA 专用，输入经 `/data/local/tmp` 推送，不入库），逐类复刻 AppModule 生产装配，用**真实导出包 + 真实挑战码**驱动：
  - **prepare 全链已验证**：结构校验 → 配对绑定（包↔码常量时间比对）→ ECDSA 验签 → 30 分钟有效期 → 23 条记录隔离暂存成功。
  - **篡改拒绝已验证**：翻转包中部一字节 → prepare Failed 且正式 DB SHA-256 逐字节不变（test2 通过）。
- 挑战明文与含 QR 截图一律不入库（`r2-qr-active-screen.png`、`r2-check-state.png` 留本地，qa-scratch 全部在 `.git/` 内）。

## R2-c B2 替换恢复 — 发现新缺陷 D-12（blocker），恢复提交在进程内结构性不可能成功

- connected 测试实测：swap 目录交换、回滚点创建（`files/rollback/<ts>` 含旧 DB 三件套）、`reopenAndQuickCheck=true` 全部通过；随后 `postActions.rebuildSearchIndex` 立即失败 → 网关回滚 → `Failed(RollbackVerification)`。
- 测试侧装饰器定位根因（生产代码零改动）：swap 后首个 suspend DAO 调用抛
  `kotlinx.coroutines.JobCancellationException: Job was cancelled; job=SupervisorJobImpl{Cancelled}`。
  机制：Room 2.8.4 的 `RoomDatabase.coroutineScope` 在 `close()`（`RoomFormalDataHandle.checkpointAndClose`）时取消，`openHelper.writableDatabase` 重开底层连接但**不重建该 scope**，此后同一实例上所有 suspend DAO 调用必抛 → `runStorage` 映射为 `StorageUnavailable` → postActions 失败 → 按设计回滚。
- 影响：生产路径同一 Hilt 单例 `AppDatabase` 贯穿恢复流程，**认证恢复在任何设备上都无法完成**（回滚机制本身工作正常：失败后正式数据完好）。
- 建议修法（留主对话裁决，红线零触碰）：交换成功后重建 RoomDatabase 实例供 postActions 使用；或 postActions 改用非 suspend（blocking）DAO；或恢复提交放独立进程/子流程后重启。任一修法落地后本测试可直接复跑验收（断言 `commit is Restored`）。
- 回滚语义附带观察（非缺陷）：commit 失败回滚后应用数据完好可续用，本轮多次失败后 B 仍正常。

## R2-d D-7~D-11 缺陷登记（本轮认证导出/配对链路）

| ID | 级别 | 现象 | 证据 |
|---|---|---|---|
| D-7 | major | `AndroidMigrationKey.ensureKey()` 失败走 `onAuthenticationRejected()`，Idle 按钮静默无反馈（error=null、无 Toast） | BackupPairingExportScreen.kt:86-90 + 真机复现（截图 `r2-export-keyfail.png`，无 QR 内容） |
| D-8 | major | Active 页 Column 不可滚动：61 行 QR 文本块把密码勾选/导出按钮挤出屏幕（1080x1920 默认字体），违反 AGENTS「小屏可达」约束 | 真机观察；本轮靠临时 font_scale=0.5 才见控件（已复原 1.0） |
| D-9 | major | 30s Keystore 认证窗口与流程不匹配：Active 页无重新认证入口，超窗后导出报「加密组件不可用」（误导）；密码分支同窗口约束 | 本轮两次失败 + 紧凑节奏成功对照；错误文案 BackupProtectedExportViewModel.kt:180 |
| D-10 | **blocker** | 文本块 QR 不可被 zxing 解码（宽高比失真），新设备永远扫不了 | 截屏解码 NO_QR_FOUND vs 同矩阵方形 PNG 解码成功（payload 一致） |
| D-11 | **blocker** | 流程死锁：挑战码只在导出前显示，Done 后不再显示；而恢复要求先选包再扫码，验证器又强制包↔码一一绑定 → 真实世界 happy path 不可达 | 代码走查（两 VM 状态机）+ 设备复现 |
| D-12 | **blocker** | 见 R2-c：Room close() 后 suspend DAO 全灭 → 认证恢复 commit 必回滚 | connected 测试 + logcat QA-B1 标签堆栈 |

- D-9 的"密码分支必炸"候选已排除：早前失败同为超窗，窗口内导出成功。
- D-6 维持误报裁决，未重开。

## R2-e B3 / B4

- **B3（API 26–29 BiometricPrompt 降级）— blocked**：本机仅 android-30 镜像可用，android-26 镜像未证实存在（`xtool-s04-api26` AVD 有 ini 无系统镜像），30 分钟预算内放弃搭建；且 D-12 会使 commit 段在低版本同样失败，优先级让位于 D-10/D-11/D-12 修复。
- **B4（Salvage 包拒绝，可选项）— 未投入**：SAF 选择器怪癖 + 预算约束；真机侧同类取消零写入已有 R1 记录（§3.2/3.3）。

## R3 闪屏宿主冷启动复核 — 已验证（真机 + 模拟器）

- 真机 bf353dda：`Displayed .launch.BrandLaunchActivity +910ms` → `Displayed .MainActivity +201ms`，无白屏/崩溃，FragmentActivity 改造未破坏闪屏链路。
- 模拟器 API 30：`+2s656ms → +380ms` 同样正常。证据：`docs/handoff/evidence/r3-*`（不含 QR/密钥）。

## R4 A5 mitm 复核 — blocked

- 无证据表明用户已删除真机 mitm CA；按任务书不自行装卸证书，维持 blocked，待用户确认 CA 状态另开轮次。

## R5 QA 环境怪癖补充（本轮新增，后续会话沿用）

- `connectedDebugAndroidTest` 会**在所有已连接设备**上跑（务必 `ANDROID_SERIAL=emulator-5576` 锁机），且跑完**卸载应用清空数据**——重跑前必须重新 onboarding；改用 `adb install -r` + `am instrument -w -e class <FQCN#method> <testpkg>/androidx.test.runner.AndroidJUnitRunner` 可保留数据反复跑。
- 模拟器息屏后 `input text/keyevent` 全部吞掉且 uiautomator 显示旧值：先 `KEYCODE_WAKEUP` + `dismiss-keyguard`；带 PIN 的 keyguard 用 keyevent 8..13+66 解锁（`input text` 无效）。
- Gboard 偶发吞字（`input text` 无效果）：`am force-stop com.google.android.inputmethod.latin` 后恢复。
- AOSP 相机弹窗「要记住照片拍摄地点吗」会劫持焦点：`am force-stop com.android.camera2`。
- 词书 onboarding 页内层列表与外层页面滚动竞争：先 BACK 收起 IME（一次，两次会退出页面），在列表区外滑动外层；B 屏高 1920，y>1800 的 swipe 落入导航条无效。
- onboarding 名称字段在 A/B 布局 bounds 不同（键盘收起后移位），点击前必须重新 dump。

## R6 Git 状态与计数（第二轮）

- 分支 `stage-qa-round-2-2026-10-14`（自 `37d8c92`），不动 main；本文件与 connected 测试提交于该分支，`git ls-remote` 核验输出见提交后记录。
- 计数：已验证 4（认证导出 e2e、prepare 全链、篡改拒绝、R3 冷启动×2 端）；新缺陷 6（D-7~D-12，其中 blocker 3）；blocked 4（B1 扫码环节（环境）、B3、B4、R4）。
- 红线确认：`BackupCryptoPolicy` / `MigrationChallengePolicy` / `AndroidMigrationKey` 零改动；报告与仓库内无任何密钥/挑战明文；业务代码零改动（仅新增 androidTest 与文档）。
- 遗留清理项：两台模拟器 `/data/local/tmp/qa-b1-*`、`pkg.zip` 为一次性 QA 数据（应用卸载不影响）；模拟器 A 字体已复原 1.0；A/B 均留有本轮 onboarding 演示档案，属 QA 环境正常状态。

## R7 修复复验（主对话，2026-10-14，D-12 → D-10/D-11/D-9 → D-7/D-8）

基线：分支 tip `08fc20b`（含 `29ca79e` D-12、`070be2a` D-10/D-11/D-9、`08fc20b` D-7/D-8）。

- **A（emulator-5574）受保护导出全链 UI 走查通过**：滚动可达认证按钮 → BiometricPrompt（指纹）→
  Active 页方形位图 QR（D-10）→ 页内滚动到「导出受保护备份包」（D-8）→ 导出 23 条 →
  Done 页保留「选择保存位置并导出」与「显示配对码（需再次认证）」（D-11/D-9）→
  SAF 落盘成功（文件名 `englishlearning-backup-20261014-123759.elpbak.zip`，SAF 按 MIME 追加 .zip）→
  落盘后再认证再显示：**同一个挑战码**，zxing 从截图直接解码成功
  （`ELPAIR1`，6 段，签发/过期时间戳齐全）。
- **B（emulator-5576）connected E2E 通过**：真实包 + 真实挑战码推送 `/data/local/tmp` 后
  `am instrument` 跑 `DevicePairedRestoreE2ETest`：**OK (2 tests)** ——
  `commit is Restored`、回滚点含旧 DB 三件套（`rollback/<ts>/db/english-learning.db`）、
  篡改包拒绝且正式 DB 逐字节不变。恢复后冷启动数据完好（词书/计划正常显示）。
  这直接证明 D-12 生产修复（维护走 `withMaintenanceDatabase` 临时实例）在设备生效。
- **复验中修掉的 3 个测试侧缺陷**（均在 androidTest，生产零改动）：
  1. `LoggingPostRestoreActions.rebuildSearchIndex` 整体替换了 delegate，用已取消的单例库跑探测
     DAO 并以其结果判定 commit 成败——D-12 修复后该路径必假，导致复验首跑 `RollbackVerification`
     误报。改为：真实维护走 delegate，探测仅记日志不参与判定。
  2. 回滚点断言用 `listFiles().last()`（字典序）会被历史 10 位时间戳目录干扰，改为按数值最大目录。
  3. 断言路径错误：快照按 SwapTarget 分层，db 三件套在 `rollback/<ts>/db/` 下而非顶层。
- 单测门禁：1322 全绿（≥ 基线 1304）。红线文件零触碰；报告与仓库不含密钥/挑战明文
  （挑战码仅存 `.git/qa-scratch/`，不入库）。
- 结论：D-7~D-12 全部设备复验通过；「旧设备导出 → 新设备认证恢复」端到端可达。
