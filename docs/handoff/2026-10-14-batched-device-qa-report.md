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
- 提交：本报告一件文档提交；推送状态与远端核验见报告末尾接续提示词前的收尾说明。
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
