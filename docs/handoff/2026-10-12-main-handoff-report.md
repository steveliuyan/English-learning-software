# 2026-10-12 主会话工作报告（阶段收尾 + 发音按钮改版 + 阶段 3 准备）

> 按 `docs/handoff/REPORT-TEMPLATE.md` 撰写。本报告写给在 **main/其他分支** 上工作的新会话：
> 请先读 `AGENTS.md` 与 `docs/handoff/2026-10-12-session-briefing.md`，再读本报告。

## 1. 本次目标

收尾未提交的 Kokoro/备份工作 → 阶段 1 验收关账 → 阶段 2 收口（F2-04/05/06）→ 发音按钮历史遗留清理 → 准备阶段 3（F3-01）任务书与分支。

## 2. 完成了什么（全部在 `stage-2-f2-closeout` / `stage-3-f3-01-dictionary` 分支上）

| 提交 | 内容 | 分支 |
|---|---|---|
| `68b82b5` | **Kokoro 端侧发音生产化**：单词发音直达 Kokoro（ONNX 端侧合成），句子发音保留 MiMo→系统 TTS 回退；旧 OpenAI 偏好规范化；生产清单/许可/phonemizer 审计入库，**模型二进制（~129MB）在 .gitignore**，恢复方式见 `docs/decisions/2026-10-06-kokoro-word-pronunciation.md` | stage-1-f1-05 |
| （独立提交） | **逻辑备份包**：BackupPackageFormat/Validator + 确定性 ZIP writer + JVM 回环测试，未接用户数据 | stage-1-f1-05 |
| `3b4176c` | **阶段 1 关账**：补 AC1-06 切换词书回归测试；验收记录 `2026-10-12-stage-1-acceptance-record.md`（AC1-01~19 逐条映射） | stage-1-f1-05 |
| `39bd6d5` | **阶段 2 收口**：补 3 个 displayMode Room 持久化测试；AC2-01~10 逐条映射记录 `2026-10-12-stage-2-acceptance-record.md` | stage-2-f2-closeout |
| `c2c2cef` | **发音按钮改版**：删除 AI 时代遗留的"重新生成美式/英式"按钮（Kokoro 实时合成后无语义）及 AppScreen 4 处调用点；喇叭改为 Material volume_up 比例自绘图标 + 美/英口音标注 | stage-2-f2-closeout |
| `d5c60d8` | **F3-01 任务书**：`2026-10-12-f3-01-closeout-task.md`，简报已指向阶段 3 | stage-3-f3-01-dictionary |

## 3. 验证记录

- 全量 JVM 门禁：**1026 用例全绿**（每笔提交前均验证，先 `:app:assembleDebug`）
- 新增测试编译通过：3 个 displayMode androidTest（待真机执行）+ regenerate 移除断言
- detekt/ktlint：按 `docs/decisions/2026-09-19` 决策不作门禁，本批改动仅测试文件与 UI 局部，无新增 JVM 增量报告义务

## 4. Git 状态

- 分支链：`stage-1-f1-05-fsrs-scheduling`（阶段 1 关账）→ `stage-2-f2-closeout`（阶段 2 收口 + 发音 UI）→ `stage-3-f3-01-dictionary`（当前，只有任务书提交）
- **全部已推送 GitHub**（`github.com/steveliuyan/English-learning-software`，Git 已配代理 127.0.0.1:3067）
- 若 main 分支上需要这些工作，请先从 `stage-2-f2-closeout` 或 `stage-3-f3-01-dictionary` 合并；不要凭空重做
- 工作树干净；`.workbuddy/` 已 gitignore，勿提交

## 5. 未完成 / 遗留问题

1. **真机人工走查**（合并一次 session 即可）：阶段 1 两项（学习记录 DB 只读 MD5 对照、详情页开关走查）+ 阶段 2 两项（三来源标识截图、3 个 displayMode 用例执行）+ 发音按钮改版后的真机目检
2. **F3-01 收口未开始**：任务书已备好，见下节
3. detekt 1580 条先存债治理工作包仍未排期

## 6. 下一任接手须知

- **下一个开发任务**：F3-01 离线查词收口。切 `stage-3-f3-01-dictionary`，读任务书 `docs/handoff/2026-10-12-f3-01-closeout-task.md` 后开工。主体实现已存在，只补 AC 映射、阅读位置保留验证、发音代次校验检查和验收记录
- 之后的候选顺序：F4-02 提醒 → F3-03 拍照 OCR → 备份接真实数据
- 环境坑（Gradle 本地路径、Kokoro 测试需先 assembleDebug、提交身份、代理）全部在简报第三节
- UI 约定：功能期不做视觉精修，只允许 theme/token 级修正；全部功能完成后统一做一轮 UI 重设计

## 7. 用户确认过的决策

- Kokoro 模型二进制不入 Git（.gitignore），清单/许可文件入库
- 阶段 1/2 的真机走查项不阻塞后续阶段开发
- 发音按钮："重新生成"直接删除而非改样式；喇叭用自绘 Material 风格 + 美/英标注（不引 material-icons 库）
- 功能优先、UI 后置；交接流程：读简报 → 干活 → 按 REPORT-TEMPLATE 写报告 → 推送
