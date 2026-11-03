# 2026-11-04 AI 服务页 · Vision 假开关退役 + 连接测试改真探活 工作报告

## 1. 本次目标

用户拍板的两件事（原话摘要）：① 摘掉「AI 服务」页那枚没人读的 Vision 假开关，摘之前按数据兼容判定
枚名是删是留并写进决策文档；② 把「连接测试」从 `UnsupportedOption` 占位换成真探活——按 OpenAI 兼容惯例
发一次最小 `GET /models`，报「可达 / 鉴权失败 / 超时」，硬约束是带 Key 出站先过同一套域名披露确认、
超时取该 Profile 的 `timeout_seconds`、不得阻塞 UI、Endpoint 先过 `AiEndpointPolicy`、探活会真的出网
所以不许拿它当截图或取证手段。

## 2. 完成了什么

**域层（新增）**

- `ai/AiConnectionProbe.kt`：`probe(profile, apiKey, confirmedTextHost): ConnectionProbeResult`，
  编排固定为「Key 非空 → Endpoint 白名单 → 出站确认 → 构造 GET → transport → 状态分类」；
  七档结果全部是**无字段** `data object`（延续 `AiFailure` 的泄露防线：结果载体不带自由文本，
  界面文案只能取常量），`NeedsConfirmation(host, payloadKind)` 是唯一带字段的成员且只带域名。
  Key 缓冲在 `finally` 里 `fill('\u0000')`，即用即清。
- `ai/net/AiChatRequestBuilder.kt`：把 `joinEndpoint` 里的「`validateEndpoint` + 拒 query/fragment +
  `trimEnd('/')`」收成内部 `baseEndpoint()`，新增 `modelsUrl(endpoint)` 复用它——
  「路径由应用决定」这条约束从此只有一个出处。
- `di/AppModule.kt`：`provideAiConnectionProbe(transport)`。

**ViewModel（`ui/AiProfileSettingsViewModel.kt`）**

- `AiConnectionOutcome`（七档 → 常量文案）+ `AiConnectionUiState`（`Idle`/`Testing`/
  `AwaitingConfirmation(host)`/`Reported(outcome)`），挂进 `AiProfileEditorUiState.connection`。
- `testConnection()`：先跑既有 `check(draft, providerKind)`，不合法就地返回 `ConfigurationInvalid` 不出网；
  取密钥走「草稿里新填的优先，否则只有已保存的 profile 才去 Keystore 读」，
  **新建草稿没填 Key 时绝不碰 SecretStore**；两者皆无 → `MissingKey`。
- 在飞请求归一枚 `connectionJob`：改草稿即清结果、关编辑页与再点一次都取消前一次（结构化并发，
  不是「取消后再看一眼标志位」）；`closeEditor` 同时清掉本次编辑会话的已确认域名。
- 确认答复 `confirmOutbound(confirmed)`：同意才把 host 记进会话，拒绝直接回 `Idle` 且零请求。

**屏侧（`ui/AiProfileSettingsScreen.kt` + `ui/AppScreen.kt`）**

- Vision 那枚 `CapabilityToggle` 下线（能力开关剩三枚，逐枚点名枚名、不遍历 `entries`）；
  `capabilitiesLabel` 里的「读图」映射**保留**——`when` 必须穷尽，列表页对老配置仍如实显示它声明过的能力。
- 第 4 张卡的「连接测试」禁用占位删除（`UnsupportedOption` 4→3），第三组「API Key」之后加真入口：
  `PillButton(tag = ai_profile_connection_test)` + 结果行（`Text` + `AppType.Footnote` + `text2`，
  `tag = ai_profile_connection_result`），`Testing` 中禁用并显示进行中文案。
- 待确认态由本屏承载全仓唯一的 `OutboundConfirmDialog`（`AiPayloadKind.Text` + 本屏 `note`），
  tag 走同族口径 `ai_profile_outbound_confirmation_text` / `ai_profile_confirm_outbound` /
  `ai_profile_decline_outbound`；「取消」与「点蒙层」都回 `false`，没有默认放行。
- `AppScreen` 把 `onTestConnection` / `onConfirmOutbound` 两环接到 VM。

**判据闸（新增 + 同批改）**

- 新增 `ai/AiConnectionProbeTest.kt`、`ai/AiCapabilityCompatContractTest.kt`、`ui/AiVisionToggleRetirementTest.kt`，
  `ai/net/AiChatRequestBuilderTest.kt` 补 `modelsUrl` 一组。
- `ui/AiProfileSettingsViewModelTest.kt` 扩 16 枚探活编排用例（含「未确认时零字节出网」「拒绝后零请求」
  「结果态不占任何密钥槽位」「超时与连不上不混为一谈」「迟到的回答被丢弃」）。
- 六处既有闸 + 两处计划外调用点全部同批改，逐条对账写在
  `docs/superpowers/plans/2026-11-04-ai-profile-vision-and-connection-test.md` §5。

**文档**

- 决策：`docs/decisions/2026-11-04-ai-profile-vision-and-connection-test.md`（§1 证据、§2 留枚名删 UI 的
  数据兼容裁定、§3 探活设计表、§4 探草稿而非探已存配置、§5 控件落位与 tag、§6 明确不做）。
- 计划：`docs/superpowers/plans/2026-11-04-ai-profile-vision-and-connection-test.md`（含 §5 执行对账）。
- Spec：`docs/specs/02-reading-and-ai-content.md` F2-02 行改写 + AC2-11/12/13。

## 3. 验证记录

RED→GREEN 逐轮日志都在 `.git/qa-scratch/`（不入库）：`r4-red-vm.log`、`r4-red-ui.log`（改屏前 5 枚判据红）、
`r4-green1..6.log`。

- 定向判据批（`AiProfileLayoutContractTest` / `Batch3AiScreensContractTest` / `AiVisionToggleRetirementTest` /
  `OutboundDialogFamilyTest` / `AiRequestPolicyTest`）：`classes=5 tests=43 failures=0 errors=0 skipped=0`。
- 全量离线门禁（固定顺序）：
  `gradle.bat --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest`
  → `BUILD SUCCESSFUL`（`.git/qa-scratch/r4-gate-full4.log`）。
- 读数重算（**第一个实参是输出文件，不是日志**）：
  `python .git/qa-scratch/tally.py .git/qa-scratch/r4-gate-full4-tally.txt`
  → `classes=311 tests=2302 failures=0 errors=0 skipped=0`（上一批基线 308 类 / 2263 例，本批 +3 类 / +39 例）。
- connected 只在 `emulator-5554`，`adb install -r` 双 APK + `am instrument`：
  `AiProfileSettingsScreenTest` + `RoomAiProfileRepositoryTest` + `GlassPanelThemeInkTest` → `OK (38 tests)`
  （`.git/qa-scratch/r4-inst-ai3.log`）；`AppScreenTest` → `OK (64 tests)`（`.git/qa-scratch/r4-inst-appscreen2.log`）。
- 真机 `bf353dda` 只做零出站链路（`adb install -r` + 手工试用，未跑 instrument）：
  我的 → AI 服务 → 编辑配置 → 能力行只剩「文本 / 语音 / 生图」三枚 → 「测试连接」在 API Key 下方 →
  点它弹出共享确认框，正文含域名 `www.sssai.xin` + 密钥披露 + 「并可能产生费用」+ 本屏那句探活说明 →
  点「取消」对话框消失、无结果行、零出站。截图与 UI dump 落
  `.git/qa-scratch/r4-device-ai.png` / `r4-device-confirm.png` / `r4-d12-texts.txt`。
- detekt/ktlint：未跑（验收口径是单测全绿；存量债 1500+ 条不当回归）。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`；本批提交见 `git log --oneline -1`（提交前基线 `73dc3a6`）。
- 领先远端枚数用可复算命令读，不写死：
  `git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD`
- **未推送**：用户口径是「推送等我发话」，本轮没有收到「推送」。
- 工作树干净；`.git/qa-scratch/` 下的日志、截图、dump 与 `tally.py` 全部不入库。

## 5. 未完成 / 遗留问题

- 探活只回答「能不能通」：`Reachable` 不等于该 Profile 能出好文章，也不校验模型名是否存在
  （决策 §6.3 明确不做模型下拉/自动填名）。
- AI 缺口批其余四项按决策 §6.4 结转：图片每次都问的现状、生图结果不落盘、
  `network_security_config` 缺位、以及「是否把每次确认改成按域名一次」的产品口径。
- `AiCapability.Vision` 仍是**枚举里的历史声明**：UI 无入口、`ai/` 无消费方。哪天真要接读图请求，
  必须先回决策 §2 重开裁定（`AiVisionToggleRetirementTest` ③ 会在有人加消费方时判红）。
- 用户目视待办（UI 重设计轮累计）：本批新增的「测试连接」药片 + 结果行 + 确认框在真机上的观感
  需要目视；其余仍挂在前几份报告的目视清单里。

## 6. 下一任接手须知

1. 读序：`AGENTS.md` → 本报告 → `docs/decisions/2026-11-04-ai-profile-vision-and-connection-test.md` →
   计划 §5 的执行对账 → `.superpowers/sdd/2026-10-17-ui-redesign/progress.md`。
2. 门禁口径与环境坑（Gradle 本机路径、`PIPESTATUS`、代理/凭据、`adb pull` 的 `MSYS_NO_PATHCONV=1`、
   控制台 GBK 乱码要先写 UTF-8 文件再读）统一记在 `android-repo-session-handoff` 与
   `android-connected-qa-loop` 两条 skill 里，本批新增三条：
   - `tally.py` 的 `sys.argv[1]` 是**写出读数的目标文件**，把 gate 日志路径传给它会把日志覆盖成一行读数
     （本轮踩过，`r4-gate-full2.log` 因此只剩 54 字节）。
   - `ComposeTestRule` 一回合一内容：想在一条用例里跑「确认」和「取消」两条答复，第二次 `setContent`
     直接抛 `has already set content`，必须拆成两条。
   - 源码契约的窗口要选对：`bodyOf` 从函数体的 `{` 起裁，**形参表在窗口外**（本次补了 `paramsOf`）；
     `onConfirmOutbound =` 是全仓共用形参名（`AppScreen.kt` 整文件 6 处），宿主侧只能按
     `aiProfileViewModel?.…(` 数本屏那一次。
3. 探活会真的带 Key 出网：任何「为了截图/取证」而点它的做法都被禁止，真机链路停在确认框。
4. 关联文档：`docs/specs/02-reading-and-ai-content.md`（F2-02 + AC2-11/12/13）、
   `docs/decisions/2026-11-03-duplication-consolidation.md`（U-1 出站确认归一，本批把第五枚宿主登记进去）。

## 7. 用户确认过的决策

- Vision：**留枚名、删 UI 入口**（删常量会让老行 `valueOf("Vision")` 抛异常并连带砸整张 Profile 表）。
- 探活形状：一次 `GET /models`；结果至少分「可达 / 鉴权失败 / 超时」，实现按七档分（另含连不上、
  服务异常、缺 Key、配置不完整、待确认）；超时取该 Profile 的 `timeout_seconds`；带 Key 出站先过
  `AiOutboundConfirmation` 同一套域名披露（Text 档 = 每域名一次）；不阻塞 UI；Endpoint 先过 `AiEndpointPolicy`。
- 本批范围就这两件事，其余 AI 缺口结转；UI 重设计轮**不合 main**，推送逐批请示。
- 可写控件总数保持 13（摘一枚能力开关 + 加一枚探活动作），组成变化为第一组 7→6、第三组 5→6、第四组 4→3。
