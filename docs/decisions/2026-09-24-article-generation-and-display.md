# 文章生成与展示：四组「候选方案 vs 决定」

## 背景

F2-03（`docs/superpowers/plans/2026-09-24-f2-03-article-generation-and-reading.md`）首次让应用
对外发起真实 AI 请求：出站经用户确认、响应经解析校验、结果落库并渲染。本记录涵盖实现中
四组「候选方案 vs 决定」，以及真机验收（Task 9）发现的三个集成缺口的处置。

## 一、HTTP 实现：零依赖 JDK 还是引框架

- **候选 B：引入 OkHttp + MockWebServer**。API 现代、测试生态成熟，但要为此引入两个
  依赖链；本项目一贯不为便利引依赖（词书、PDF、密钥存储均零依赖落地）。
- **决定：`HttpURLConnection` + 测试用 JDK 内置 `HttpServer`**。传输端口
  (`AiHttpTransport`) 只暴露 `send(request): AiHttpResult`，所有失败折叠成 sealed 结果的
  一个成员而**不抛异常**——调用方的 `when` 穷尽性就是全部错误路径的清单。配套三个安全细节：
  1. `instanceFollowRedirects = false`：跟随重定向会把「公网域名 → 私网地址」这条被
     `validateEndpoint` 挡掉的路重新打开；
  2. 响应体 512KB 上限用**逐块读累计**判定（`readCapped`），绝不先 `readBytes()` 再量长度——
     那已经把整个响应体吃进内存，上限失去意义；
  3. `CancellationException` 的 catch 必须排在 `IOException` 之前（它是其子类）：用户离开
     页面不等于网络坏了，两者不能折叠成同一个结果。

## 二、高亮来源：信模型坐标还是系统重派生

- **候选 A：提示词要求模型返回高亮/坐标，照单渲染**。省事，但模型的坐标无人校验：
  错位、幻觉、甚至被提示注入引导去「高亮」任意文本，都没有第二道防线。
- **决定：解析器忽略模型自愿提供的任何坐标**（`ArticleResponseParser` 有专门的
  `ignoresAnyCoordinatesTheModelVolunteers` 用例），高亮一律由
  `ArticleHighlightPolicy` 从**落库正文 + lemma 列表**重新派生。真机验收（Task 9 Step 5
  第 5 项）确认：正文高亮（obvious / ability / climate / generous）与
  `coveredLemmas` 一一对应、未覆盖词 chips（feature / reduce / achieve / benefit）
  与今日计划互斥。

## 三、高亮持久化：落库坐标还是落 lemma 列表

- **候选 A：把派生出的高亮区间存进库**。渲染快，但区间是「正文第 N 版」的衍生品——
  正文一变（换一篇出新版本）区间全部作废，还引入「坐标与正文不同步」的脏状态。
- **决定：库里只存 `coveredLemmas`，渲染时重算**。隔天重读历史文章，派生输入
  （正文 + lemma 列表）不变，高亮必然还对得上；真机验收第 6 项（退出重进）与
  第 7 项（换一篇后 v1 行原样保留）分别验证了这两点。

## 四、成本护栏：设上限还是只提示（有意偏差）

- **候选 A：每日生成次数上限 + 历史容量上限**。更安全，但需要配额表、日界判定、
  超限文案，且「多少算多」没有依据。
- **决定（用户 2026-09-24 拍板，记为有意偏差）：本轮不设上限，只给逐次提示与逐次确认**
  ——生成按钮旁常驻「每次生成都会真实调用你配置的 AI 服务并可能产生费用」，
  每次出站前弹域名确认框，「换一篇」同样重新确认（真机走查第 1/2/7 项取证）。
  **后续补上限不需要改领域模型**：次数上限可以放在用例层查当日本地日期的已有行数，
  容量上限只是清理策略——`articles` 表已有 `localDate` 与 `generatedAtEpochMillis`，
  不缺判定所需字段。

## 真机集成缺口（Task 9 走查发现并当场修复）

1. **设备级配置选择语义**：`GenerateArticleUseCase` 原按学习 `profileId` 查
   `AiProfile`，但 `AiProfile.profileId` 是配置自身的主键，与学习档案无关——
   用户录好配置仍报「还没有配置」。改为遍历设备全部配置、选**第一套存有密钥的**
   （V1 策略；「默认 Profile 选择」落地后替换）。
2. **API 34+ 方法在 Android 13 崩溃**：`LocalDate.ofInstant(Instant, ZoneId)` 是
   API 34+，真机直接 `NoSuchMethodError`（JVM 测试抓不到，桌面 JDK 17+ 有该方法）。
   改 `instant.atZone(zoneId).toLocalDate()`。
3. **阅读栏不可滚动**：Ready 态内容变多后高于屏幕，根 Column 没有
   `verticalScroll`，「查看本地阅读历史」按钮被折叠在屏外不可达。TDD 修复：
   先加 `performScrollToNode` 失败测试（RED：`no parent layout with a Scroll
   SemanticsAction`），再给根 Column 加 `verticalScroll(rememberScrollState())`
   （GREEN），变异测试证明断言可红（移除滚动修饰符 → 同一错误复现）。

## 影响

- 网络能力与出站确认同时生效：`INTERNET` 权限只此一项，未顺手加
  `ACCESS_NETWORK_STATE`；确认框展示真实域名（真机取证 `www.bb-api.com`）。
- **已知限制**：默认 `timeoutSeconds=30` 对文章生成偏紧（LLM 生成 300 词 + 译文
  常超 30 秒，真机两次「换一篇」超时即是实例）。该值本就是用户可配项（5–120），
  验收中由用户调成 90 后成功；是否上调默认值留待后续，不阻塞本特性。
- `GET /models` 模型列表与「测试连接」仍未做（F2-02 残留）。
- 端侧 TTS/OCR、真实词典数据、生成次数与历史容量上限：见计划「不在本轮范围」。
