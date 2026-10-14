# 2026-10-14 F3-03 拍照 OCR 合并收口报告（主对话）

分支：`stage-3-f3-03-photo-ocr` 已快进合入 main（无 force），main tip `2275fc4`，已推送 GitHub（ls-remote 复核一致）；分支已验证为 main 祖先后本地+远端删除。仓库现剩 main + archive/checkin-line-132f507 tag。

## 1. 本轮裁决与执行（对用户请示的结论）

1. **真机走查结果采纳**：QA 报告 `docs/handoff/2026-10-14-stage-3-device-qa-report.md` 为 AC3-04/05/06 真机口径权威记录；关键事实（bundled `text-recognition-bundled-common:17.0.0` 内含约 11MB `libmlkit_google_ocr_pipeline.so`，禁用 GMS 后 ML Kit 照常本机识别，「自动降级 Tesseract」分支真机不可达、`OcrUnavailable` 由 JVM 5 条链测试覆盖）已按要求补入决策文档新增 **§5.1 真机核验补记**（含 T2 状态：(a)(c)(d) 按口径关闭，(b) 隧道级+流量级已证、内容级解密留待补做）。
2. **D-1 修复（提交 2275fc4，TDD 先红后绿）**：`PhotoTranslationScreen` 相机拒绝文案不再承诺不存在的「点下方按钮重新授权」，改为如实指向系统设置，并在提示下方新增真实跳转按钮（`ACTION_APPLICATION_DETAILS_SETTINGS`，testTag `photo_translation_permission_settings`）；由新源码契约测试 `PhotoTranslationPermissionContractTest`（2 用例）钉死「文案↔入口」一致性，防止再次脱节。
3. **D-2（全局无深色主题）**：确认归入既定的 UI 重设计轮（阶段 5 合并处理），不在功能期单修。
4. **D-3/D-4（信息级）**：不修、不排期——D-3 保持固定类别文案（诚实：DNS 失败对用户就是网络不可用）；D-4 已有重试路径，若后续真机反馈恶化再议超时值。两项记录在案即可。
5. **B5 端到端（保存→历史→删除→备份）真机验证**：维持用户裁决跳过，等用户配好真实 AI Profile 后与 F4-04 攒批走查合并补做。

## 2. 验证记录

- 三任务门禁 `:app:assembleDebug → :app:testDebugUnitTest → :app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL；**1298 用例、0 失败 0 错误**（分对话基线 1296 + D-1 契约测试 2，XML 汇总核验）。
- D-1 红→绿：契约测试先失败于「点下方按钮」存在且无 settings 入口，实现后转绿。
- v28→v29 迁移 connected 测试真机通过（QA 报告 §3，验收记录 §6.2 设备项就此关闭）。
- detekt/ktlint 非门禁，未跑增量。

## 3. F3-03 交付概貌（分对话 15 提交 + QA 轮 2 提交 + 本轮 1 提交）

许可核验决策（方案 A 获批）→ 台账登记 → OCR 域层端口与降级链（AC3-04）→ 设备层 ML Kit/Tesseract4Android + 固定摘要按需模型 → 临时区零残留生命周期（AC3-05）→ 拍照翻译页（逐次确认门、低置信标注、编辑、对照/复制/查义/发音，AC3-06）→ 显式保存历史 + v2/v3 备份管线接入。验收记录：`docs/handoff/2026-10-14-stage-3-f3-03-acceptance-record.md`；工作报告：`docs/handoff/2026-10-14-stage-3-f3-03-photo-ocr-report.md`。

## 4. 遗留与下一步

- **真机走查攒批（待派）**：F4-04 清单（双设备扫码配对、替换恢复端到端、BiometricPrompt 26–29、恢复失败逐字节比对、认证导出页 SAF 保存）+ F3-03 补做项（B5 端到端、T2 内容级解密、TalkBack 干净环境走读）。
- 设备环境提醒：bf353dda 的 mitmproxy 用户 CA 由用户手动删除；MIUI u2000 会自动卸载 adb 安装的应用、WeType 拦截 `adb shell input text`（QA 报告 §6.5）。
- 阶段 0–4 功能面至此全部合入 main。建议下一步开 **阶段 5（发布质量与合规）**，与 UI 重设计（含 D-2 深色主题）、detekt 基线债、Kokoro 合规文档合并规划。
