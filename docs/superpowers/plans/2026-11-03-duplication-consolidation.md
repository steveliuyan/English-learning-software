# 2026-11-03 重复项整合批次实施计划

- **决策：** `docs/decisions/2026-11-03-duplication-consolidation.md`
- **盘点证据：** `docs/design/2026-11-03-duplication-audit.md`（U-1..U-14，行号为该轮 `grep` 实测，动文件后会漂，按重算命令现算）
- **纪律：** 先红后绿；字面量闸同批改；门禁 = 本机 Gradle 8.13
  `:app:assembleDebug` → `:app:testDebugUnitTest` → `:app:compileDebugAndroidTestKotlin`
  （`--offline --no-daemon --no-build-cache`，禁 `./gradlew`）；connected 用 `adb install -r` + `am instrument`（禁 `connectedDebugAndroidTest`）；
  不合 `main`、不推送。

## 步骤

1. **U-4 指引地图**：新增命名闸用例（`设置 · AI` 在 `app/src/main/java` 命中归零 + 用户可见文案不得再出现）
   ⇒ 先跑红 ⇒ 改 17 处文案 + 4 处注释 ⇒ 绿；同时改 5 条 connected 字面量断言，
   并把 `openAiProfilesOrSettings` 的 `showSettings = true` 兜底改为落「我的」hub（形状判据一起钉）。
2. **U-5 AI 服务命名**：闸（`AI 服务与密钥` 归零，`AI 服务配置` 可及名保留）⇒ 改 3 处 + 1 处说明 + `SpeechSettingsViewModelTest:388`。
3. **U-1/U-2 对话框收口**：先加「四屏不再出现同名私写对话框定义」的族形状判据（红）
   ⇒ 共享件加正文覆写入参 + 四屏改调 ⇒ 绿；正文一律 `outboundConfirmBody(host, kind)`，
   `SentenceAnalysis` 那份漏字随之消失。
4. **U-3 策略单一权威**：UI 侧 `PayloadKind` / `mustAskEveryTime` 改为基于 `AiPayloadKind` / `requiredConfirmation`
   （委托而非平行再算），`OutboundConfirmDialogTest` / `ConfirmDestructiveWordingTest` 随之改测。
5. **U-7 提醒**：死行 `重复 / 周一到周日 / 每天` 删除的 tag 判据 + 规则句在该屏只出现一次的计数判据 ⇒ 改两屏。
6. **U-10 学习计划双保存**：判据「`SetupHeaderSave` 挂载点归零、`SetupSaveBar` 恰一枚」⇒ 摘页头那枚。
7. **U-11 / U-12 / U-8 命名对齐**：落地页标题随 hub 行；hub 行名「关于与许可」→「关于」；
   词组三名对齐分段 tab（收藏 / 生词 / 错词）。各自先改闸再改文案。
8. **U-14 文档澄清**：`docs/decisions/2026-11-03-today-article-direct-open-and-checkin-name.md:89` 与
   批 6 报告 §7.5 补限定语（「暂不加」= 卡内额外入口；页头齿轮是批 5 设计）。
9. **门禁 + connected + 真机复装 + 报告**：按 `docs/handoff/REPORT-TEMPLATE.md` 写
   `docs/handoff/2026-11-03-duplication-consolidation-report.md`，JVM 汇总遍历
   `app/build/test-results/testDebugUnitTest/*.xml` 累加并在报告前重算；带 git 身份提交。

## 不做

U-9（周视图版面）、U-13（拆「提醒与发音」中转页）、盘点表 §3 六条该保留的多入口。

## 续做（收口批之后，用户 2026-11-01 选定的下一项）

10. **U-6 备份警告措辞收源**：范围、四套原句、单一真源形状与判据见决策 §8——
    先改本文档与决策（已完成），再写 `LocalDataNoticeSingleSourceTest`（RED），后落生产；
    门禁仍走 `assembleDebug → testDebugUnitTest → compileDebugAndroidTestKotlin`，connected 只在 `emulator-5574` 复装。
