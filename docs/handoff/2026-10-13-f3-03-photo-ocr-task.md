# 2026-10-13 F3-03 拍照 OCR 与翻译 任务书

分支：`stage-3-f3-03-photo-ocr`（自 main `6c93fc7`）。执行人：分对话。

## 0. 接手必读（按序）

1. `AGENTS.md`（强制产品约束：OCR 只负责本地识别与坐标；图片默认不保存；上传 AI 前必须确认）
2. `docs/specs/03-dictionary-pronunciation-and-photo-translation.md`（F3-03 条目 + AC3-04/05/06）
3. `docs/handoff/2026-10-13-f4-04-merge-closeout-report.md`（当前基线 1213 用例全绿）
4. 既有 AI 请求链：`docs/specs/02-reading-and-ai-content.md` 相关实现（Profile 绑定、HTTPS/域名确认、不可信输出渲染）

## 1. 硬前置：许可核验（未通过不得引入任何依赖）

第一个提交只能是许可核验决策文档 `docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md`：

- **ML Kit**（主路径）：逐 artifact 核验 `com.google.mlkit:text-recognition-*`（区分 bundled/on-device 与 unbundled/GMS 依赖）、Play Services 依赖的许可与再分发条款、英文模型数据条款。无 GMS 设备行为必须实测口径写清。
- **Tesseract 降级**：核验引擎（leptonica/tesseract 5.x）与维护模式（`tess-two` 已归档，评估 `ksvguyen/AndroidTesseract` 或自封装 JNI）、`eng.traineddata` 训练数据许可（CC 系条款与打包合规）。
- 任一许可不过 → 停下向用户报告替代方案，不得先写码后补许可。
- 通过后在 `docs/third-party-notices.md` 按 catalog alias 登记八字段条目（`ThirdPartyNoticesTest` 锚定）。**决策文档需用户批准后才继续实现**（同 F4-04 流程）。

## 2. 范围与提交切分（获批后）

1. 许可核验决策文档 + 台账（如已获批）。
2. OCR 域层端口：`OcrProvider`（返回文本块 + 坐标 + 置信度）、`OcrResult` 模型、Provider 链选择（ML Kit 可用 → 主路径；检测不可用 → Tesseract；双双不可用 → 固定「OCR 不可用」类别，**不得报成翻译失败**，AC3-04）。纯逻辑 + fake provider 测试。
3. 设备层：ML Kit 实现、Tesseract 实现（模型资产首次使用按需下载：展示大小与许可、可删除）。
4. 图片采集与临时区：相机（CAMERA 权限，已有先例）+ 相册（按版本最小化照片权限）；图片只进短期临时目录；**取消/失败/退出必须清理**（AC3-05 生命周期测试：每个出口断言零残留）。
5. 确认门 + 翻译页：OCR 文本低置信标识、可编辑；发送前每次显示目标域名与内容类别（图片 vs 文本）明确确认，复用 AI Profile 绑定；翻译走既有受控请求链；原文/译文对照、复制、选词查义、发音；失败保留可编辑 OCR 文本 + 重试，不留半成品译文（AC3-06）。
6. 历史保存 + 备份接入：仅用户明确保存才写原图/OCR 文本/译文；保存资源进备份可选范围（v2/v3 导出与恢复管线复用，不破坏 `BackupExportMustNotIncludeCredentialsTest` 哨兵）。UI/设置接线 + 验收记录（AC3-04/05/06 逐条映射）。

## 3. 红线（违一条即停）

- 未经确认不上传图像或 OCR 文本；「图片默认不保存」贯穿所有失败路径。
- OCR ≠ 翻译：文案、错误类别、日志严格分离。
- 新依赖必须三任务一次性 `--write-locks`（assembleDebug + testDebugUnitTest + compileDebugAndroidTestKotlin）。
- AppScreen 新可选参数必须同时接 MainActivity（`AppScreenProductionWiringTest` 强制）。
- AI 输出/OCR 文本均为不可信输入：无脚本富文本渲染、大小/长度校验。

## 4. 门禁与环境

- 门禁：`:app:assembleDebug → :app:testDebugUnitTest → :app:compileDebugAndroidTestKotlin`，基线 1213 用例只增不减；detekt/ktlint 非门禁。
- 推送：`-c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067`；挂起时 `taskkill //F //IM git.exe` 重试；提交带 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`。
- Gradle：`C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`。
- 真机（bf353dda）可协助相机/权限走查，但端到端验证仍按攒批约定。

## 5. 交付

每步独立提交推送 GitHub；完成后按 `docs/handoff/REPORT-TEMPLATE.md` 写工作报告 + 验收记录，并向主对话提交裁决请示（如有设计分歧）。
