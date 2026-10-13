# 2026-10-14 F3-03 拍照翻译真机走查 + T2 抓包 任务书（设备 QA 分对话）

主对话裁决（2026-10-14，用户拍板）：F3-03 六项增量已完成推送（分支 `stage-3-f3-03-photo-ocr`，tip `b06feda`，1296 单测全绿），**先真机走查、通过后再合 main**。本任务书即走查范围与验收要求。

## 背景与必读

1. `AGENTS.md`（红线与产品约束）。
2. `docs/handoff/2026-10-13-f3-03-photo-ocr-task.md`（§3 红线、§4 门禁）。
3. `docs/handoff/2026-10-14-stage-3-f3-03-acceptance-record.md`（§2/§6：哪些项明确「未验证」，本走查就是补这些）。
4. `docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md` §5（T2 抓包核验的具体内容）。
5. `docs/handoff/2026-10-14-stage-3-f3-03-photo-ocr-report.md` §6（接手须知）。

## 环境要点

- 真机 ID `bf353dda`（`adb devices` 先核验；无设备连接立即停下向用户说明，不得跳过）。
- Gradle 用本地分发 `C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`，始终 `--offline`；`gradlew` 离线不可用。
- Bash 管道会吞退出码：每条命令结尾 `2>&1 | grep -E … ; exit ${PIPESTATUS[0]}`。
- git 网络命令一律带 `-c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false`，不修改仓库/全局 git 配置；提交身份 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`。
- androidTest 是 JUnit4（`org.junit.Assert`），`kotlin.test` 不在 androidTest classpath。
- App 安装：`gradle.bat :app:installDebug --offline`。

## 走查清单（每项记录：步骤 / 预期 / 实际 / 截图或日志路径）

### A. v28→v29 迁移（自动化，最优先）

```
gradle.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.core.storage.AppDatabasePhotoTranslationMigrationTest --offline
```

通过后可顺带跑整个 connected 套件（时间允许时）。失败则保留完整 logcat/gradle 输出。

### B. 拍照翻译功能走查

1. AI 学 → 拍照翻译入口可见可达；首次进入相机 CAMERA 权限弹窗；拒绝后不反复弹窗、给出可行提示。
2. 相册入口走 Photo Picker（不应请求存储权限）。
3. 对准一段英文拍照：识别文本出现、低置信行有标注、文本可编辑。
4. 点翻译：弹出域名 + 内容类别确认；**拒绝后不得出网**；确认后出现原文/译文对照；复制、朗读原文、点词查义各试一次。
5. 「保存到本机」：出现「已保存」与历史列表（含原图标记）；删除一条后列表消失且不再出现在备份导出预览。
6. Tesseract 离线模型卡：下载进度可看、完成态可识别（可切飞行模式 + 强制 Tesseract 路径验证离线识别，见 C 组）；删除后回「未安装」。
7. 页面隐私脚注文案与实际行为一致（未点保存时退出，不留任何记录——可从「备份导出不含新记录、历史列表为空」侧面验证）。
8. 退出页面/重置后无崩溃；深色模式与 TalkBack 过一遍关键按钮（基础导航即可）。

### C. AC3-04 降级与 T2 抓包（核心）

1. 记录设备是否带 GMS（`adb shell pm list packages | grep com.google.android.gms`）。
2. 无 GMS 或禁用 GMS 时：ML Kit 初始化的实际行为（不可用信号/崩溃/静默失败）→ 是否自动落到 Tesseract（先装好离线模型）→ 双双不可用（如未装模型且禁 GMS）时页面是否显示「OCR 不可用」类别而非翻译失败。
3. 抓包核验 ML Kit bundled 外联（决策文档 §5 T2）：可行方案按优先级——
   a. `adb shell tcpdump`（需 root/特定 ROM，不行就跳过）；
   b. mitmproxy + 设备代理 + 用户 CA（Android 7+ 用户 CA 对 app 默认不信任，**debug 构建可用 `networkSecurityConfig` 已信任用户 CA 则可行；先确认 manifest**）；
   c. 退路：`adb shell dumpsys netstats` / 连接计数在飞行模式开关前后对比 + `adb logcat` 观察 ML Kit 下载器行为，如实记录为「间接证据」。
4. 同时核验：翻译确认弹窗拒绝后网络零请求（抓包/连接计数佐证）。
5. 抓包结果按「外联域名清单 + 字节数量级 + 结论」记录；**做不到完整抓包时，明确写「T2 未完整核验，剩余方法与阻塞点」**，不得谎称通过。

### D. 产出与提交

1. 报告写到 `docs/handoff/2026-10-14-stage-3-device-qa-report.md`，按 `docs/handoff/REPORT-TEMPLATE.md` 七节结构；AC3-04/05/06 每条给「真机已验证 / 仍未验证 + 原因」的明确结论。
2. 发现缺陷：在报告列「缺陷清单（复现步骤 + 期望/实际）」，**不要自行改代码**（修复走主对话裁决）。
3. 报告提交推送到 `stage-3-f3-03-photo-ocr` 分支（只加报告与截图/日志引用，不混入代码改动）；推送后 `ls-remote` 核验。
4. 若过程中设备/环境阻塞超过两轮尝试，停止并写阻塞报告，把需要用户手动做的事列清楚（如信任抓包证书、第二台设备）。

## 红线提醒

- 不得上传任何用户图片或 OCR 文本到任何外部服务做测试；测试图片用自摄样张或本地生成。
- 测试产生的保存记录走查完删除，不留测试数据在设备。
- 不要动 `main`、不要合分支、不要改版本号或构建配置。
