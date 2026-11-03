# AI 缺口批 · 第 1 批：Vision 假开关退役 + 连接测试改真探活

- **日期：** 2026-11-04
- **依据：** `docs/decisions/2026-11-04-ai-profile-vision-and-connection-test.md`（四条裁定 + 判据闸清单 + 明确不做）
- **规格：** `docs/specs/02-reading-and-ai-content.md` F2-02（已按本批改写能力枚举行、新增探活条目与 AC2-11/12/13）
- **前置：** `docs/handoff/2026-11-03-week-view-single-source-report.md`（U-1…U-14 全部收口；AI 缺口批挂账）
- **分支：** `stage-5-ui-r2-learning-path`（UI 重设计轮，**不合 main**）
- **用户拍板：** 本批只做「摘 Vision 假开关」与「连接测试改真探活」两件事；其余 AI 缺口项（每次弹确认、
  生图不落盘、nsc）结转。**推送逐批请示**，未得「推送」不发网络动作。

## 验收门禁

1. 本机解压版 Gradle 8.13（`find "$HOME/.gradle/wrapper/dists/gradle-8.13-bin" -name gradle.bat` 取路径）：
   `gradle.bat --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
   一条命令跑完，管道后必带 `; exit ${PIPESTATUS[0]}`。
2. 单测读数**自己重算**，不引用上一批基线：`python .git/qa-scratch/tally.py <输出文件>`
   —— 注意第一个实参是**写出读数的目标文件**（脚本内 `open(sys.argv[1],'w')`），它自己从
   `app/build/test-results/testDebugUnitTest/*.xml` 取数；把 gate 日志路径传给它会把日志覆盖成一行读数
   （本轮实测踩过，`.git/qa-scratch/r4-gate-full2.log` 因此只剩 54 字节）。验收口径是单测全绿，
   detekt/ktlint 存量债不当回归。
3. connected 只在 `emulator-5554`：`adb install -r` 双 APK + `am instrument` 跑受影响类
   （`AiProfileSettingsScreenTest`、`GlassPanelThemeInkTest`、`RoomAiProfileRepositoryTest`、`AppScreenTest`）。
   **禁用** `connectedDebugAndroidTest`（清设备数据）。
4. 真机 `bf353dda` 只 `adb install -r` + 手工走查，且**只做零出站链路**（点探活 → 出域名确认 → 取消）；
   确认框不点「确认发送」——探活会真的带 Key 出网。
5. 取证一律落 `.git/qa-scratch/`，不入库；中文验证文本写 UTF-8 文件后用 Read 看；`adb pull` 带 `MSYS_NO_PATHCONV=1`。
6. 报告按 `docs/handoff/REPORT-TEMPLATE.md` 七节；提交带 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`；
   文档内不写会随自身提交漂移的计数，改写成可复算命令。

## 步骤

### 1. 探活用例（域层，TDD 先红）

- 新建 `app/src/main/java/com/example/englishlearning/ai/AiConnectionProbe.kt`：
  `probe(profile: AiProfile, apiKey: CharArray, confirmedTextHost: String?): ConnectionProbeResult`
  （落地名用 `ConnectionProbeResult`，非计划初稿的 `…Outcome`），
  编排顺序固定为「Endpoint 白名单 → 出站确认 → 构造 GET 请求 → transport → 状态分类」，
  Key 在 `finally` 清零（照 `SentenceAnalysisUseCase` 的既有形状）。
- 结果用无字段 sealed 成员（延续 `AiFailure` 的泄露防线：成员不得携带自由文本），
  用户可见文案在界面侧按枚举取常量。
- `AiChatRequestBuilder` 抽出 base 校验复用：现有 `joinEndpoint` 的「`validateEndpoint` + 拒 query/fragment +
  `trimEnd('/')`」那半段收成一个内部函数，`joinEndpoint` = base + `/chat/completions`、
  新增 `modelsUrl(endpoint)` = base + `/models`。**路径由应用决定**这条约束只留一个出处。
- JVM 用例（新文件 `ai/AiConnectionProbeTest.kt`，自带私有 `RecordingTransport`，与同域各 UseCase 测试同形）：
  未确认 ⇒ `NeedsConfirmation` 且 transport 零调用；确认后 2xx ⇒ `Reachable`；401/403 ⇒ `Unauthorized`；
  `TimedOut` ⇒ `Timeout`；`NetworkUnavailable` ⇒ `NetworkUnavailable`；404/429/5xx ⇒ `ServiceError`；
  请求形状三枚断言（URL 以 `/models` 结尾、`headers` 键集合恰 `Authorization`、`body` 为空、
  `timeoutSeconds` 等于 profile 的值）；Key 缓冲即用即清；密钥不进 URL 也不进 body。
- URL 契约并进同一文件（或 `ai/net/AiChatRequestBuilderTest.kt` 既有条目旁）：
  `modelsUrl` 继承 http/环回/私网/userInfo/query/fragment 全部拒绝，尾部斜杠归一。

### 2. Vision 数据兼容与假开关归零（闸先行）

- 新闸 `ai/AiCapabilityCompatContractTest.kt`：枚名集合逐字为 `Text/Vision/Speech/ImageGeneration`、
  `AiCapability.valueOf("Vision")` 可用，并点名理由（老行解析）。
- 新闸 `ui/AiVisionToggleRetirementTest.kt`（源码契约，独立小文件而非并进版式契约）三枚用例：
  ① 屏上 `capability = AiCapability.…` 逐字恰 `Text/Speech/ImageGeneration` 三枚，且
  `AiCapability.Vision in draft.capabilities` = 0、tag `"ai_profile_capability_vision"` = 0；
  ② 「读图」中文名映射仍恰 1 处（`when` 必须穷尽，摘开关不等于摘历史转述）；
  ③ `ai/` 目录对 `Vision` 的限定名读点 = 0 处，**同一条用例内**再钉住枚名定义位
  （`enum class AiCapability { Text, Vision,` 恰 1 处）——只断「零读点」会在枚名被删光时假绿，
  而那正是砸老配置 `valueOf` 的那一刀。
- 现跑证据（改前）：`AiProfileSettingsScreen.kt` 里 `CapabilityToggle(` 调用恰 4 枚、
  `capability = AiCapability.Vision` 恰 1 枚 —— 两条都要先红后绿。

### 3. ViewModel 编排

- `AiProfileSettingsViewModel` 构造注入 `AiConnectionProbe`；`AiProfileEditorUiState` 增
  `connection: AiConnectionTestState`（`Idle`/`Testing`/`AwaitingConfirmation(host)`/`Result(outcome)`）。
- `testConnection()`：先用既有 `check(draft, providerKind)` 走一遍（不合法即 `ConfigurationInvalid`，
  不发网络）；密钥按「`draft.pendingKey` 非空优先，否则 `profileId != null` 时读已存密钥」取，两者皆无 ⇒ `MissingKey`。
- `confirmOutbound(confirmed)` / `dismissOutbound()`：确认后把 host 记进编辑会话的 `confirmedTextHost`
  （换 Endpoint 自动失效，由 `requiredConfirmation` 保证），并重发探活。
- 在飞请求归一枚 Job：`closeEditor()` 与新一次 `testConnection()` 各取消前一次；结果只落枚举，不落原文。
- JVM 用例扩 `ui/AiProfileSettingsViewModelTest.kt`：不阻塞（`Testing` 态可继续改草稿）、
  取消即无结果、新建与编辑两条取密钥路径、`pendingKey` 不进任何 UI 状态字段。

### 4. 屏侧

- 摘 Vision 那枚 `CapabilityToggle`（原 `:356-364`，行号随本批改动漂移，锚点名 `ai_profile_capability_vision`）。
- 原 `:500-505` 的 `UnsupportedOption("连接测试")` 从第 4 张卡删除；在第 3 张卡「API Key」区之后加真控件：
  `PillButton`（`testTag = "ai_profile_connection_test"`，
  实际可用性谓词 `state.connection != Testing && !state.saving`——保存中同样不该再点一次）+
  结果行（`testTag = "ai_profile_connection_result"`，取房内先例 `Text(style = AppType.Footnote, color = text2)`
  而非 `InlineNotice`：它是一行读数而不是需要处置的告警，且排印档必须显式，理由见
  `OutboundDialogFamilyTest` ⑥ 同款口径）。`Testing` 态显示进行中文案，`Idle` / `AwaitingConfirmation`
  不占位（待确认时屏幕上说的是确认框，不是结果行）。
- 编辑会话内承载 `OutboundConfirmDialog(endpointHost, AiPayloadKind.Text, …)`，
  `note` 为「连接测试只发一次模型列表查询，不带词书、文章或任何学习内容。」；屏级 tag 走
  `ai_profile_outbound_confirmation_text` / `ai_profile_confirm_outbound` / `ai_profile_decline_outbound`
  这一族既有命名口径（与另外四枚宿主逐字同族，已按此落地）。
- 摘 `:740` 的顾虑：`label` 映射保留（`when` 必须穷尽），列表页对老配置仍显示「读图」。
- `UnsupportedOption` 的 KDoc 与组注释随之从「四枚禁用行」改为「三枚」，并注明第 4 枚卡只装禁用行。

### 5. 既有闸同批改（否则整体红）· 已执行对账

清单里最后一行是**跑门禁才补上的**：`compileDebugAndroidTestKotlin` 用编译错误把漏改的调用点亮出来，
比任何人工回忆都可靠——计划写的六处不是全集。

| 文件 | 改法（实际执行） |
|---|---|
| `app/src/test/…/ui/AiProfileLayoutContractTest.kt` | ③ 组清单重排：第一组摘 `ai_profile_capability_vision`（7→6）、第三组加 `ai_profile_connection_test` + `ai_profile_connection_result`（5→6）、第四组摘占位 tag（4→3），可写控件总数仍 13；新增 ③′「探活不许长成死按钮」把入口形参 → 编辑页形参 → `onClick` → VM 方法 → 共享确认框 → `AppScreen` 接线逐环点名。两处判据自身的坑也在这条上暴露并修好：形参在 `bodyOf` 的窗口**外面**（改用新增的 `paramsOf`），`onConfirmOutbound =` 是全仓共用形参名（整文件 6 处），宿主侧只能按 `aiProfileViewModel?.testConnection()` / `?.confirmOutbound(` 数本屏那一次 |
| `app/src/test/…/ui/Batch3AiScreensContractTest.kt` | 未接入枚数 4→3：tag 清单、`UnsupportedOption(` 计数、占位 tag 归零、`ai_profile_connection_test` 恰 1 |
| `app/src/androidTest/…/ui/AiProfileSettingsScreenTest.kt` | 能力多选取样改用 `image` / `text` 两枚真开关；夹具 `setScreen` 与新形参默认值一并补两枚回线；`editorState()` 加 `connection` 形参；新增三枚 connected（点击回数、结果文案不回显 Key、待确认框读域名并答复）。出站确认那枚原本想在一局里跑完「确认 + 取消」两条答复，`ComposeTestRule` 一回合只许一次 `setContent`（第二次直接抛 `has already set content`），据此**拆成两枚用例** |
| `app/src/androidTest/…/ui/glass/GlassPanelThemeInkTest.kt` | 未选中档取样 tag `_vision` → `_image`（取样窗口与四条断言一字未动）；`setEditor` 夹具补两枚空回线并写明「取像素不出网」 |
| `app/src/test/…/ai/AiRequestPolicyTest.kt` | 计划原写「`Vision` 作参数保留」，**按用户拍板改为 `ImageGeneration`**：这一枚验的是「同一域名文本已确认，图片仍每次问」，声明 `ImageGeneration` 比声明一枚 UI 已退役的假开关更贴近真实配置；枚名解析与向后兼容另有 `AiCapabilityCompatContractTest` 守 |
| `app/src/test/…/ui/OutboundDialogFamilyTest.kt` | `privateScreens` 加 `AiProfileSettingsScreen.kt`（第五枚宿主），共享件「恰一处调用」覆盖到连接探活；类 KDoc 里那段「本轮实测」的旧快照改写为历史口径 + 可复算命令（`grep -rn "OutboundConfirmDialog(" app/src/main/java --include=*.kt`） |
| `app/src/androidTest/…/ui/ThemeModeSwitchTest.kt`（计划外） | 列表页渲染夹具补两枚空回线——`onTestConnection` / `onConfirmOutbound` 是必填形参（③′ 不许默认值），漏一处就编译不过 |
| `app/src/androidTest/…/ui/AppScreenTest.kt`（计划外） | `AiProfileSettingsViewModel(...)` 第 5 枚实参补 `AiConnectionProbe(炸掉的 transport)`：这一族用例不探活，transport 一旦被调用就 `error(...)`，把「渲染测试不许出网」写进夹具 |

### 6. 收尾

- 三门禁 + `tally.py` 重算 → connected（`emulator-5554`）→ 真机零出站走查 →
  `docs/handoff/2026-11-04-ai-profile-vision-and-connection-test-report.md` → 本地提交（不推送）。

## 明确不做

见决策 §6：不加第三档 `AiPayloadKind`、不让拍照翻译 gate `Vision`、不做模型下拉自动填充、
不动其余三项 AI 缺口、不新增权限或依赖。
