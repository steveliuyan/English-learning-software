# 决策：AI 文本出站的确认粒度按屏规定 + `network_security_config` 落地（2026-11-05）

## 1. 背景

用户在 2026-11-05 的选项题里先拍了「按 Spec 改实现」（文本出站按域名确认一次），我按这条口径去改之前，
把现树的相关约束全部核了一遍，发现**三份文档互相冲突**，必须请他重定一次哪一屏享有什么语义。他随后
选定「**只放宽高频两屏**」。本文件把两次拍板、核到的实况、以及顺带查出的一笔真红记在同一处。

### 1.1 冲突面（全部为现树实测，非推断）

| 出处 | 原文口径 |
|---|---|
| `AGENTS.md`「AI 与隐私安全」 | 首次调用第三方 Endpoint 前展示域名及「文本/图片和 Key 将发送给该服务」的确认。图片发送每次都需明确确认。 |
| `docs/specs/02-reading-and-ai-content.md` F2-02 | 调用前显示域名和数据出站确认；**文本能力调用首次确认**，图片能力每次确认。 |
| `docs/specs/03-dictionary-pronunciation-and-photo-translation.md` 拍照翻译节 | 发送图片或文本到第三方 AI 前**每次**显示目标域名、发送内容类别并要求明确确认。 |
| `docs/decisions/2026-09-24-article-generation-and-display.md` §四 | 用户 2026-09-24 拍板的**有意偏差**：文章生成不设每日次数上限，「只给逐次提示与逐次确认」当成本护栏，「换一篇」同样重新确认。 |

### 1.2 实况（grep + 读码核实）

判定逻辑本身**早就支持「按域名一次」**：`ai/AiOutboundConfirmation.kt` 的 `requiredConfirmation(profile, kind, confirmedTextHost)`
把 `confirmedTextHost` 与当前 endpoint 的 host 相等视为 `Satisfied`。缺的是**调用方没有把确认记住**：

- `ui/SentenceAnalysisViewModel.kt:50`、`ui/WordAiQaViewModel.kt:161`、`ui/ReadingAccessViewModel.kt:137`、
  `ui/PhotoTranslationViewModel.kt:165` 四条腿的新请求都传 `confirmedTextHost = null` ⇒ 每一次新请求都重新问。
  确认答复只在「弹框 → `confirmOutbound()` → 立刻重试那一次」的回合里活着（`confirmedTextHost = pending.host`）。
- 全仓只有 `ui/ImageStudioViewModel.kt:69` 有 `private var confirmedTextHost: String?` 字段，所以生图的
  **提示词（文本载荷）**那一步是真的按域名记一次；它的**出图（图片载荷）**每次问（`ImageGenerationUseCase.kt:154`
  传 `confirmedTextHost = null`，符合 AGENTS.md 对图片的要求）。
- `ui/AiProfileSettingsViewModel.kt:181` 有一条**有意**的注释：「每次进入编辑都是一次新的出站会话：上一轮对某个
  域名的确认不许顺延到这一轮」——连接探活的确认至今不顺延。
- `ui/AiFeature.kt:108` 长难句卡的依赖行写着「出站确认：按域名确认一次，与文章生成同语义」：**前半句与实况不符**
  （实况每次问），后半句也不符（文章生成是逐次护栏）。同一文件 `:122` 拍照翻译写「文本每次发送前逐次确认」是**与实况一致的**。

⇒ 所以这不是一处 bug，而是**同一个仓里两种语义并存**，且用户面文案按其中一种在承诺。

## 2. 决定

### D-1 确认粒度按屏规定（不再追求四屏统一）

| 出站路径 | 载荷 | 确认后语义 | 依据 |
|---|---|---|---|
| 查词 AI 问答 | 文本 | **按域名一次**（进程内、跨屏共享） | 用户 2026-11-05 拍板；AGENTS.md 与 Spec 02 的「首次确认」 |
| 长难句分析 | 文本 | **按域名一次**（同上，与查词共享同一份台账） | 同上 |
| 每日文章生成 / 换一篇 | 文本 | **保持逐次确认** | 用户 2026-09-24 §四 的有意偏差：逐次确认顶替「每日次数上限」当成本护栏，本轮**不撤护栏** |
| 拍照翻译送 OCR 文本 | 文本 | **保持逐次确认** | Spec 03 第 39 行的「每次」严于 Spec 02；AGENTS.md 规定冲突时取较严格一侧 |
| 生图·提示词 | 文本 | 按域名一次（现状即如此，不动） | Spec 02 |
| 生图·出图 | 图片 | 每次确认（不动） | AGENTS.md「图片发送每次都需明确确认」 |
| 连接探活 `GET /models` | 文本 | 每次进入编辑页重新问（不动） | `AiProfileSettingsViewModel:181` 的有意语义；且见 D-2 末段 |

选「只放宽高频两屏」的理由：这两屏是**一次输入换一次回答**的高频动作（查一个词、拆一句），逐次弹框的打断成本最高、
单次成本最低；文章生成与生图是低频高成本动作（生图按张计费，通常是文本调用的数十倍），弹窗本身就是唯一的成本刹车。

### D-2 会话级确认台账：`ai/AiOutboundGrantStore`

- Hilt `@Singleton`，内部一个 `MutableSet<String>` 存**已确认的 host**，加锁读写，**不写盘**。
- API 收成三个动作：`isTextConfirmed(host)`、`confirmText(host)`、`clear()`（`clear()` 给「清除全部学习数据/退出」这类
  重置路径留口子，本批若接不上就不接，不硬造调用点）。
- **键是 host，不是 profileId**：披露对象本来就是域名（`AGENTS.md` 原话「展示域名」），换 Profile 但域名相同 ⇒ 用户已知的
  事实没变；域名一变 ⇒ 一定重新问。这把 `AiOutboundConfirmation.kt:43` 那段「刻意收成 `String?` 而不是布尔」的理由原样继承。
- **进程级而非账号级**：应用被杀 ⇒ 台账自然清空，下次照样问。不做持久化是因为「把授权写进磁盘」等于让一次确认
  永久有效，那是比 D-1 更宽的隐私变更，得另拍。
- **两屏共享同一份**：在长难句确认过 `api.example.com`，查词页同一域名就不再弹。这是「按域名一次」的字面语义；
  反面方案（每屏一份）会退化成「同一个域名一分钟内被问两次」，比现状更差。
- **探活不接这份台账**（保持 `AiProfileSettingsViewModel:181` 的现状）。理由不是省事：探活确认框的 `note` 明说
  「只发一次模型列表查询，不带词书、文章或任何学习内容」，用户点「确认」时授权的范围就是这一条查询；
  让它顺手豁免后续**带学习内容**的真实请求，是把一次窄授权当成宽授权用。反向也不豁免：内容请求确认过之后，
  探活仍按本页语义在自己的会话里问。2026-11-04 决策 §6.1 拒绝新增第三档 `AiPayloadKind`，这条判断在**调用方**做，
  不需要动枚举。

### D-3 `network_security_config`

新增 `app/src/main/res/xml/network_security_config.xml` 并在 `<application>` 上挂 `android:networkSecurityConfig`：

- `<base-config cleartextTrafficPermitted="false">` + `<trust-anchors><certificates src="system" /></trust-anchors>`：
  只信系统根，用户安装/企业安装的根证书与自签名证书一律拒——这是 `AGENTS.md`「拒绝……自签名证书」那条在配置层的落地。
- `<debug-overrides><trust-anchors><certificates src="user" /></trust-anchors></debug-overrides>`：**只在 debuggable
  构建生效**，给设备 QA 留内容级抓包口子。`docs/handoff/2026-10-14-stage-3-device-qa-report.md` §3「C 组：AC3-04 与 T2」
  记的实测就是：装了 mitm 用户 CA，本 app 的 `CONNECT` 隧道全部可见，但结论停在**「部分核验（隧道级 + 流量级），未达
  内容级解密」**——这条 debug 开口补的是「app 侧不信任用户根」那半边前提。
  如实标注它买不全：同一份报告还写了 MIUI 上「证书内容未被确认落入用户信任链（`cacerts-added` 无 shell 权限复核）」，
  所以取证前仍须人工在「设置→信任的凭据→用户」确认证书在列，或改用可写 `/system` 的模拟器。
- 如实登记价值边界，别夸成「新增防护」：targetSdk 36 下「不信用户 CA」与「默认禁明文」都是 **Android 7（API 24）起的
  平台默认**（同一份 QA 报告也写了「Manifest 无 `networkSecurityConfig`、用户 CA 默认不被信任」）。本条真正买到的是
  三件：**把要求钉成可测契约**（今后谁加 `<base-config cleartextTrafficPermitted="true">` 或把用户 CA 漏进 release 语义
  都会红）、**给 debug 一个受控开口**（现在 QA 要临时抓包只能手改 manifest 再改回来）、**让评审能一眼看到边界**。
- 本批**不改**传输层：`ai/net/UrlConnectionAiHttpTransport.kt:29` 已 `instanceFollowRedirects = false`，
  「重定向到私网/环回」这条在代码层已经封住；`AiEndpointPolicy`/`validateEndpoint` 继续做请求前校验。

### D-4 顺带修一笔真红（上一批欠的账）

`gradle.bat --offline --no-daemon verifyAcTraceabilityMatrix` 本会话实跑 **FAILED**：
「矩阵缺少行: AC2-11」「AC2-12」「AC2-13」——2026-11-04 那批往 `docs/specs/02` 加了三条 AC，
但同批只跑了 `assembleDebug / testDebugUnitTest / compileDebugAndroidTestKotlin`，没跑这条 root 校验任务，
所以发布门禁 `checkReleaseReadiness` 现在是红的。本批必须把这三行连同新增编号一起回填。
**教训**：往 specs 加 AC 编号，同一批就得跑 `verifyAcTraceabilityMatrix`，别只信单测三元组。

### D-5 Spec 与矩阵的落点（先改文档，再动代码）

- `docs/specs/00-foundation-and-architecture.md`：F0-02 加一条网络信任基线；新增 `AC0-06`（release 语义拒用户根/自签名与明文，
  debug 构建可经 `debug-overrides` 用用户 CA 抓包）。
- `docs/specs/02-reading-and-ai-content.md`：F2-02 那条「文本能力调用首次确认，图片能力每次确认」改成按 D-1 的表如实分屏；
  新增 `AC2-14`（高频两屏的同域名第二次请求零弹框、换域名重新问、文章/拍照/生图逐次腿不得被放宽）。
- `docs/specs/03-…photo-translation.md`：不动「每次」这条（它是更严的一侧），只在原句后补一句指明它与 Spec 02 的
  一般规则构成有意从严。
- `docs/verification/ac-traceability-matrix.md`：回填 AC2-11 / AC2-12 / AC2-13（本批补证据行）+ AC0-06 / AC2-14 新行。

### D-6 用户面文案改实话

- `ui/AiFeature.kt` 三张未上线卡（词文串学、AI 短文填词、单词串讲）的第一条依赖都写着
  「AI 网关：网络客户端与 INTERNET 权限」——网关（`ai/net/UrlConnectionAiHttpTransport`）、`INTERNET` 权限
  （manifest 第 5 行）、AI 配置页、出站确认全都早已在位，**把已完成的东西列为阻塞项是假实话**；逐条换成该功能真正还缺的东西。
- 长难句卡的「出站确认：按域名确认一次，**与文章生成同语义**」→ 本批实现后前半句为真，后半句为假，改成指向查词问答。
- 拍照翻译卡与阅读/生图页的「每次确认」文案**保持原样**（与实况一致，且 D-1 不放宽它们）。

## 3. 明确不做

1. 不给文章生成加每日调用次数上限（2026-09-24 §四 那笔「后续补上限」的账仍挂着，属功能批次，需另拍）。
2. 不放宽拍照翻译、生图出图、探活任何一条腿；不改 `AiPayloadKind` 枚举。
3. 不把确认台账写进 Room / DataStore / 备份包，也不进日志。
4. 不引入 OkHttp/NetworkSecurityPolicy 之类新依赖；不新增网络权限。
5. 不动传输层与 `AiEndpointPolicy` 的校验逻辑（重定向已封）。

## 4. 影响

- **代码面**：`ai/` 新增一个纯内存台账类 + DI provider；`SentenceAnalysisViewModel`、`WordAiQaViewModel` 两条腿接台账；
  四个 `*UseCase` 的 `confirmedTextHost` 参数形状**不改**（仍是 `String?`），所以域层判定与既有用例的语义不受影响，
  放宽只发生在这两个 ViewModel 的「新请求传什么」这一点上。
- **测试面**：新增台账用例（含「换域名必重新问」「跨屏共享」「进程内 clear」）、两屏 VM 各一组（第二次不弹、
  拒绝答复不落账、图片腿不许被顺带放宽）、`network_security_config` 的 XML+Manifest 双源契约测试。
- **构建面**：release 包多一个 XML 资源，行为等价于平台默认；debug 包获得用户 CA 信任（QA 用）。
- **取证面**：connected 在 `emulator-5554` 实跑；真机只做手工试用——**这两屏的请求会真的出网并计费，
  不得拿它当截图手段**（同 2026-11-04 决策对探活的约束）。
- **用户面**：查词与长难句同一域名一天里只问一次；文章/拍照/生图仍是每次问，且卡片文案与实际行为对齐。
