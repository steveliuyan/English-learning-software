# 会话交接简报（新会话必读）

**日期：** 2026-10-12
**用途：** 任何新开会的 AI 助手，开工前先完整阅读本文件，再按 AGENTS.md 流程工作。

## 一、项目是什么

Android 英语背词应用：本地优先词书学习、FSRS 间隔复习、每日 AI 语境短文。Kotlin + Jetpack Compose + Room + Hilt，面向四六级/考研人群。**必读 `AGENTS.md`**，里面有强制产品约束（本地优先、AI 安全、备份规则等）和分阶段 Spec 路线图（`docs/specs/00~05`）。

## 二、当前进度

- 阶段 0（工程基线）✅ 完成
- 阶段 1（背词与复习）✅ JVM 层验收完成（2026-10-12，1026 用例全绿，逐 AC 映射见 `docs/handoff/2026-10-12-stage-1-acceptance-record.md`）；剩两项真机人工走查不阻塞阶段 2
- 阶段 2+ 前置条件已满足，可在新分支开发
- 其他已交付（随阶段 1 一起提交）：Kokoro 端侧发音生产化（`68b82b5`，模型二进制不入库）、逻辑备份包格式（`c2560f7`，未接用户数据）
- 决策文档 `docs/decisions/` 记录了关键取舍，**改代码前先扫一眼**，尤其 `2026-09-19-static-analysis-and-test-baseline.md`

## 三、关键工作规则

1. **TDD 强制**：先写测试（RED）→ 最小实现（GREEN）→ 聚焦回归。用 superpowers skill 里的流程。
2. **验收门禁是单测**，不是 detekt/ktlint：`gradlew :app:testDebugUnitTest`（1025 用例基线，不允许新增失败类别）。detekt/ktlint 基线即失败（先存技术债），只要求报告增量，见上面 2026-09-19 决策。
3. **跑测试注意**：直接 `gradlew` 会联网下载 Gradle 失败，用本机发行版：
   `C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`
4. **Kokoro 边界测试需要先 assembleDebug**（它检查 APK 产物），所以完整验证 = 先 `:app:assembleDebug` 再 `:app:testDebugUnitTest`。
5. **提交身份**：仓库没有全局 git config，提交时用 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`。
6. **推送**：remote 已指向 `github.com/steveliuyan/English-learning-software`，Git 已配代理 `127.0.0.1:3067`（系统代理，必须走它才能连 GitHub）。每完成一个工作包必须提交并推送。
7. **文档同步**：范围变化先改 Spec 再写代码；新决策写 `docs/decisions/YYYY-MM-DD-<topic>.md`；实现计划写 `docs/superpowers/plans/`。
8. **工作树现状**：`.workbuddy/` 是工具私有数据，不要提交、不要动。

## 四、下一阶段候选方向

- **当前任务：阶段 2 收口（F2-04/05/06），任务书 `docs/handoff/2026-10-12-stage-2-closeout-task.md`，分支 `stage-2-f2-closeout`**
- 之后的候选顺序：F3-01 离线词典 → F4-02 提醒 → F3-03 拍照 OCR → 备份接真实数据（阶段 4）
- 阶段 1 有两项真机人工走查待做（见 `2026-10-12-stage-1-acceptance-record.md`），不阻塞开发

## 五、用户偏好

- 功能优先，**后期会做一轮 UI 重设计**：现在不要精修视觉，但 Compose 代码要组件化、样式走 theme，禁止硬编码颜色/间距
- 大决策先问用户（如"模型文件要不要入库"）；普通实现细节自己判断
- 沟通用中文；回答务实，先结论后细节
