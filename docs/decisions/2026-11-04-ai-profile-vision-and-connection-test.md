# 2026-11-04 AI 服务页：Vision 假开关退役与「连接测试」改真探活

范围来源：UI 重设计轮 AI 缺口批（`docs/handoff/2026-11-03-week-view-single-source-report.md` §5 挂账
「AI 缺口批：每次弹确认 / Vision 空转 / 生图不落盘 / 缺连接测试与 nsc」）。用户 2026-11-03 拍板本批只做
①②两项（摘 Vision 假开关、连接测试改真探活），其余三项结转。

前置规格：`docs/specs/02-reading-and-ai-content.md` F2-02、`docs/specs/03-…photo-translation.md` §发音/§拍照翻译。
产品约束出处：AGENTS.md「AI 与隐私安全」「本地优先」；PRD `docs/superpowers/specs/2026-09-17-…-prd.md:127`
（「设置页掩码显示，支持连接测试、替换和删除」——本条是「连接测试」的需求依据，现状是占位禁用行）。

## 1. 证据（动手前现跑，可复算）

- **没有任何请求构造读 Vision**（全仓 grep，剥注释前的原文）：
  `grep -ro "AiCapability\.Vision" --include=*.kt app/src/main | wc -l` = **5**，分布在
  `ai/domain/AiProvider.kt:10`（枚举定义）、`ui/AiProfileSettingsScreen.kt:357/359/361`（那枚假开关）、
  `ui/AiProfileSettingsScreen.kt:740`（能力中文名映射）。
  消费侧的读点只有三处且都不含 Vision：`ai/DefaultTextProfileSelector.kt:30`（Text）、
  `ai/DefaultImageProfileSelector.kt:35`（ImageGeneration）、
  `ai/net/TtsRequestBuilder.kt:30` 与 `ai/net/MiMoTtsRequestBuilder.kt:41`（Speech）。
  ⇒ 「读图」能力声明写进库后，全仓没有一个调用方检查它。它就是盘点表说的「Vision 空转」。
- **能力以枚举名字符串持久化，解析用 `valueOf`**：`ai/RoomAiProfileRepository.kt:53` 写入
  `capabilities.joinToString(",") { it.name }`；`:70` 读回
  `capabilities.split(",").filter { it.isNotBlank() }.map { AiCapability.valueOf(it) }`。
  `valueOf` 对未知枚名抛 `IllegalArgumentException`，被 `:43` 的 `catch (_: Exception)` 折叠成
  `Result.failure(StorageUnavailable)` ⇒ **整张 AI Profile 表读不出来**，而不只是丢一枚能力。
  失败面覆盖：AI 服务列表页（`AiProfileListUiState.Unavailable`）、默认文本/生图解析、语音供应商候选、
  以及所有依赖默认 Profile 的功能（文章、词问答、长难句、拍照翻译）。
- **库里确实可能存过 Vision**：`app/src/androidTest/…/ai/RoomAiProfileRepositoryTest.kt:63` 就以
  `setOf(AiCapability.Text, AiCapability.Vision)` 往返写入；这枚假开关在批 3（2026-10-29 裁决 B 的版面
  重做）里就已经是可点控件，用户点过就会留下 `…,Vision` 这一枚 token。
- **数据库无破坏性迁移兜底**：`core/storage/AppDatabase.kt:85` `version = 33`，
  `:214` 建表语句里 `capabilities` 是裸 `TEXT NOT NULL`，没有 CHECK 约束、没有值域迁移。
  枚名一旦从 enum 里删掉，老行只能靠迁移脚本改写数据才不炸。
- **备份不含 AI Profile**：`grep -rn "ai_profiles\|AiProfileEntity" app/src/main/java/com/example/englishlearning/backup`
  结果为空 ⇒ 兼容风险只剩本机 Room，不用管导出包跨设备。

## 2. 裁定一：`AiCapability.Vision` 枚举成员**保留**，只摘 UI 开关

**结论：保留常量。** 三条理由：

1. §1 第 2、3 条给出的是「删一枚常量 = 让整表不可读」的实打实故障面，且本仓没有为它写迁移的预算；
   为清理一枚死开关而引入数据损坏，收益与代价不成比例。
2. 保留不产生任何用户可见的假承诺：摘掉开关后，界面上不再有「读图」这个可点项；
   列表页的能力汇总（`capabilitiesLabel`）对老配置仍如实显示「读图」——那是库里真实存在过的声明，
   显示它比静默吞掉更可追溯。
3. 语义上 `Vision` 仍是 OpenAI 兼容协议里一个真实存在的模态位；将来若把拍照翻译改成真发图片，
   它是有主的锚点，不必重新造名（重新造名才会出现两个 token 指同一件事）。

**保留必须带闸**，否则「保留」会被下一任当「遗留可清理」再次动摇，或被当成开关的复活许可：

- 新闸（JVM）：`AiCapability` 枚名集合必须逐字含 `Text/Vision/Speech/ImageGeneration`，
  并显式断言 `AiCapability.valueOf("Vision")` 可用（数据兼容锚：老行还能解析）。
- 新闸（JVM 源码契约）：`AiProfileSettingsScreen.kt` 剥注释后 `capability = AiCapability.Vision` = 0、
  `AiCapability.Vision in draft.capabilities` = 0、`CapabilityToggle(` 调用恰 3 枚——
  「假开关不许长回来」。
- 改判闸（connected）：`AiProfileSettingsScreenTest.editor_capability_toggle_reports_the_added_and_removed_capability`
  的取样对象从 `vision` 换成 `image`（判据体不变：仍是「点一次加上、再点一次移除」的多选真值）。
- 像素取样闸（connected）：`GlassPanelThemeInkTest.the_dark_capability_toggle_container_…` 的
  未选中档取样 tag 从 `ai_profile_capability_vision` 换成 `ai_profile_capability_image`
  （该用例的 `capabilities = {Text, Speech}` 组合下，image 与 vision 同样是未选中档，取样语义等价；
  取样窗口与四条断言一字不动）。

**明确不做**：不删 `:740` 的「读图」中文名映射（删了 `when` 不再穷尽，等于逼下任把枚举一起删掉）；
不把 `valueOf` 改成容错解析（未知 token 忽略）——那是「配置写坏了却假装读得懂」的行为变化，
属数据完整性口径，要单开决策；本批只把 Vision 这一枚**已知**枚名钉死在闸里。

## 3. 裁定二：「连接测试」从禁用占位行改成真探活

现状：`ui/AiProfileSettingsScreen.kt:500-505` 是 `UnsupportedOption`，tag `ai_profile_unsupported_connection_test`，
副标题「V1 还没有探活端点」——与 PRD:127 的承诺相反。

**协议形状**：按 OpenAI 兼容惯例发一次最小请求 `GET {endpoint}/models`。
不建会话、不带模型名、不带消息体，因此不产生内容计费，也不写任何本地记录。

| 面 | 口径 | 依据 |
|---|---|---|
| URL | 复用 `AiChatRequestBuilder` 的 base 校验（HTTPS 公网 + 拒 userInfo/query/fragment + 拒环回/私网），路径改为 `/models` | 同一套规则只留一个出处，避免 `chat/completions` 与 `models` 两套拼接漂移；AGENTS.md「Endpoint 先过 AiEndpointPolicy」 |
| 方法 | `GET`，body 必须为空 | `ai/net/AiHttpTransport.kt:6` 声明只支持 POST/GET；`UrlConnectionAiHttpTransport.kt:36` 对带 body 的 GET 直接 `check` 失败 |
| Header | 只有 `Authorization: Bearer <key>` | 白名单落实：GET 无 body，`Content-Type` 属于多余声明；两枚头也不给（`AiRequestPolicy` 的「拒绝任意 Header」口径） |
| 超时 | 取该 Profile/草稿的 `timeout_seconds` | 用户设定就是这条链路的等待上限；`UrlConnectionAiHttpTransport.kt:30-31` 用它设 connect/read 超时 |
| 出站确认 | 带 Key 出站前先过 `requiredConfirmation(profile, AiPayloadKind.Text, confirmedTextHost)` | AGENTS.md「首次调用第三方 Endpoint 前展示域名及『文本/图片和 Key 将发送给该服务』的确认」；确认未给 ⇒ **零字节出站** |
| 不阻塞 UI | `viewModelScope` 异步 + `testing` 态禁用按钮 | AGENTS.md「核心路径不得阻塞 UI；网络请求可取消、超时并映射为可行动错误」 |
| 结果文案 | 常量，不插值 Endpoint / Key / 响应体 | `AiFailure` 家族的既有泄露防线（`ai/AiFailure.kt:1-9`：成员一律无字段 data object） |

**状态口径**：用户要的是「可达 / 鉴权失败 / 超时」三态，实现必须同时接住真实可能遇到的其余分支
（否则「网络坏了」会被说成「超时」，或把没配密钥说成服务坏了）：

- `Reachable` —— 2xx：「连接可达，服务返回了模型列表」
- `Unauthorized` —— 401/403：「鉴权失败，密钥被这个服务拒绝」
- `Timeout` —— `AiHttpResult.TimedOut`：「等待超时，该服务在设定的超时秒数内没有响应」
- `NetworkUnavailable` —— DNS/连接被拒/`AiHttpResult.NetworkUnavailable`：「网络不可达，检查网络或服务地址后重试」
- `ServiceError` —— 其余非 2xx（404/405/429/5xx 等）：「服务返回了错误状态，这个地址可能不是 OpenAI 兼容端点」
  （404 是用户填错 base 最常见的形状，归到「鉴权失败」或「网络不可达」都是撒谎）
- `MissingKey` —— 草稿没有可发出的密钥（既没输入新 Key，也没有已存 Key）：「还没有密钥，先填 API Key 再测试」
- `ConfigurationInvalid` —— Endpoint 或未通过白名单的高级参数：「地址或高级参数不合法，先改正再测试」
- `NeedsConfirmation` —— 该域名尚未确认：界面弹共享 `OutboundConfirmDialog`，取消即零出站

`AiHttpResult.Cancelled` 不当失败处理（沿用既有口径：用户离开就是离开，不得说成网络坏了）。

## 4. 裁定三：测的是「屏上这份草稿」，不是已保存的配置

编辑页存在两类用户，必须都覆盖：新建（库里还没有这行）与编辑（想改地址或换 Key 后再试）。
因此探活读的是**当前草稿**，密钥按「新输入的优先」取：

- `draft.pendingKey` 非空 ⇒ 用它（用户正在试的就是这份新密钥；它只进 `Authorization` 头，
  不进状态、不进日志、不落库）。
- 否则 `editor.profileId != null` ⇒ 走 `AiProfileSecretUseCase.loadKey` 读已存密钥（读后即清零）。
- 两者都没有 ⇒ `MissingKey`，不出站。

代价与处置：探活用的 `AiProfile` 是按草稿临时构造的（不落库、不写任何仓储），
因此 `providerKind` 与 `voice` 从编辑会话状态原样带回，避免 MiMo 预设被探活重置成 OpenAI 兼容
（这条坑是 `AiProfileEditorUiState.providerKind` 注释里已经登记过的真机实证）。

**探活不是取证手段**：它会真的带着 Key 出网。批内所有自动化判据一律走假 transport
（`app/src/test/…` 各 UseCase 的既有 `RecordingTransport` 形状）；connected 不接网络；
真机走查只做「点按钮 → 弹域名确认 → 取消」这条零出站链路，不代为确认出站。

## 5. 裁定四：控件落第三组「高级与密钥」，第 4 张禁用卡剩三行

- 真探活的输入是 **Endpoint + 高级参数 + 密钥**，PRD:127 也把「连接测试」与 Key 的
  掩码显示/替换/删除并列 ⇒ 放在第三组「API Key」输入框与「清除已保存的密钥」之间。
- 第 4 张卡「未接入的选项」摘掉 `ai_profile_unsupported_connection_test`，剩
  流式返回 / 音色细化 / 生图参数三枚（`UnsupportedOption(` 调用 4→3，裁决 B 的口径不受影响）。
- 因此可写控件总数**仍是 13**（摘一枚能力开关 + 加一枚探活动作），但组成变化：
  第一组 7→6，第三组 5→6。`AiProfileLayoutContractTest` 的「13 个控件守恒」标题保留，
  列表按新组成改判；控件 tag 用 `ai_profile_connection_test`，结果显示区用 `ai_profile_connection_result`。
- 出站确认框由本屏承载（`OutboundConfirmDialog`），与 `WordAiQaScreen`/`SentenceAnalysisScreen`/
  `PhotoTranslationScreen`/`ImageStudioScreen`/`ReadingAccessScreen` 五处既有宿主同口径；
  `AiProfileLayoutContractTest` ⑤「屏内零对话框」的清单只管**危险动作**那三枚
  （`AlertDialog(` / `GlassDialog(` / `ConfirmDestructiveDialog(`），本批一字不动——
  出站确认不是毁数据的动作。作为代价，`OutboundDialogFamilyTest` 的共享件调用点在册清单加本屏，
  「0 = 那屏的出站确认消失」这条守卫也覆盖它。

## 6. 明确不做（本批范围外，结转 AI 缺口批其余项）

1. **不加第三档 `AiPayloadKind`**（如 `Probe`）。理由：确认正文要说全「域名 + 密钥 + 计费」三件事，
   现有 Text 档措辞是「你的文本内容与该服务的密钥将发送给 X，并可能产生费用」，对探活而言
   多说了「文本内容」而少说了「不计费」——**多说**的方向是保守侧（不会让用户低估外发面），
   而新增档位要同时改 `outboundConfirmBody` 的两格配方、`requiredConfirmation` 的分派与
   `OutboundDialogFamilyTest` 的枚数闸（U-1/U-3 刚把这一族收成一处）。
   处置：探活的确认框走本屏 `note` 追加一句实话——探活只发一次模型列表请求、不发送学习内容。
2. **不让拍照翻译开始 gate Vision**。`PhotoTranslationUseCase` 现在发的是 OCR 文本（屏侧 note 已写明
   「图片不会发送」），把 Vision 突然变成硬前置会让所有存量配置直接不可用——那是产品行为变化，
   要另开决策与 Spec 改动。
3. 不做 `GET /models` 的模型下拉/自动填模型名（`docs/superpowers/specs/2026-09-23-ai-learning-tab-design.md:263`
   规划过，属功能扩展，本批只做「能不能通」。
4. 不动「每次弹确认」「生图不落盘」「nsc」三项挂账。
5. 不新增网络权限、不引入新依赖（沿用零依赖的 `UrlConnectionAiHttpTransport`）。

## 7. Spec 与计划的落点

- `docs/specs/02-reading-and-ai-content.md` F2-02：能力枚举行改为「文本 / 语音 / 生图」，
  `vision` 标注为历史兼容解析位（无 UI、无请求消费方）；新增连接探活条目与其验收标准。
- 实现计划：`docs/superpowers/plans/2026-11-04-ai-profile-vision-and-connection-test.md`。
- 门禁口径沿用：验收看 `:app:testDebugUnitTest` 全绿，detekt/ktlint 存量债不当回归。
