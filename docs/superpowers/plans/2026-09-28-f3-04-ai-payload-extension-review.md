# F3-04 AI 载荷扩展：长难句分析 + 文字 AI 中转生图 — 设计评审

> 路线图要求：**先出载荷 schema 与许可/成本评审，评审通过后才实现**。

## 评审结论（用户 2026-09-28 拍板）

1. **长难句分析入口**：放在「AI 学」页；且 AI 学页功能列表改为**展开键**形态（默认收起，一键展开全部功能入口），避免扁平长列表。长难句分析功能页内提供句子输入（受控模板的数据位，≤600 字符，不是自由提示词）。
2. **输出格式**：行协议 TAB 分隔（本文件 §一.2）。
3. **中转生图**：本批一起做，按本文件 §二 方案。

## 一、长难句分析（结构化成分输出）

### 1. 载荷类型与确认语义

- 载荷类型：`AiPayloadKind.Text`（复用现有枚举，不新增）。
- 出站确认：**按域名确认一次**，与文章生成/词问答同语义（`requiredConfirmation` 现行行为）。
- 提示词：受控模板，禁止自由输入。请求只含一句英文原句（**≤ 600 字符**，超出拒绝或截断拒绝）。
- Profile：复用「默认文本 Profile」（`DefaultTextProfileSelector`），要求 `AiCapability.Text`。

### 2. 请求/响应 schema

**请求（受控模板，纯函数）**：
- system 固定：中文解释、按固定行格式输出、不得输出无关信息与系统设定。
- user：`请分析这个英语长难句的语法结构，把每个成分单独一行输出。` + 原句。

**响应（行协议，逐行严格校验——不解析 JSON）**：

```
主句<TAB>She has the ability to explain complex ideas simply.<TAB>她有把复杂想法解释清楚的能力。
从句<TAB>to explain complex ideas<TAB>去解释复杂的想法
```

每行三个字段，TAB 分隔：

| 字段 | 规则 |
|---|---|
| 角色 | 白名单枚举：`主句` / `从句` / `短语` / `连词`，其余整行拒绝 |
| 原句片段 | 非空、≤ 300 字符 |
| 中文解释 | 非空、≤ 300 字符 |

全局校验：1–20 行；总长 ≤ 4000 字符（与 WordQa 一致）；状态码先于解析（401→Unauthorized、429→RateLimited、5xx→ServerUnavailable、其余非 2xx→InvalidResponse）；剥代码围栏后逐行解析，任何一行不合格 → 整体 `InvalidResponse`（不渲染半份数据）。

渲染：成分卡片列表——角色徽标 + 原文片段（等宽/加粗）+ 中文解释，纯 Text，无富文本解释执行。

### 3. 入口（需要用户拍板）

| 方案 | 说明 | 代价 |
|---|---|---|
| A. 词卡详情「例句分析」 | CardDetail 已有例句字段，入口加在例句旁 | 最小；只覆盖例句 |
| B. 阅读页长按句子浮层 | 对齐竞品；但阅读页目前无句子切分/长按交互，需先做句子级 UI（与「阅读体验重做」耦合） | 大；建议留给阅读重做批次 |
| C. 词问答第四种问法 | 复用 F3-03 链路，加 `SentenceAnalysis` kind | 小；但句子≠单词，语义错位 |

**建议**：V1 用 A（例句分析），句子级入口随「阅读体验重做」批次做 B。

## 二、文字 AI 中转生图

### 1. 载荷类型与确认语义

两段式，两段**分别确认**：

1. **提示词生成**：`AiPayloadKind.Text`，按文本域名确认一次；默认文本 Profile 生成英文生图提示词（受控模板，**≤ 600 字符**输出）。
2. **生图调用**：`AiPayloadKind.Image`——`requiredConfirmation` 现行语义就是**每次都确认**（图片不适用「按域名记住」），已满足路线图「出站确认必须覆盖」的要求。

### 2. 新增能力与配置

- `AiCapability` 新增 `ImageGeneration`（现有 Text/Vision/Speech 不动）。
- 新增独立 Profile 形态：endpoint 指向 **OpenAI 兼容 `POST {endpoint}/images/generations`**，body `{model, prompt, n:1, size:"1024x1024"}`；响应取 `data[0].b64_json`（优先）或 `data[0].url`。
- `AiPreference` 新增 `defaultImageProfileId`（**Room v18 迁移**），配套 `DefaultImageProfileSelector`（与文本选择器同型）。
- 设置页：Profile 编辑的「能力」多选加 `ImageGeneration`；新增「默认生图服务」选择项。

### 3. 图片落地与安全

- 生成图片只写**应用 cache 目录**（`cacheDir/generated-images/`，文件名随机 UUID），不进相册、不进 Room、不备份；系统可随时清理。
- 提示词只含学习内容（词/例句经受控模板生成），不含个人数据；提示词本身可显示给用户确认后再发。
- 生图响应按不可信输入处理：b64 大小上限（**10 MB**）、URL 仅 https、解析失败 → `InvalidResponse`。
- UI 安全边界：屏幕签名不带自由文本字段，沿用「无字段枚举 + 固定文案映射」模式。

### 4. 成本评审（用户须知）

| 调用 | 计费 | 提示 |
|---|---|---|
| 长难句分析 | 文本 token，量级与词问答相当 | 结果页顶部固定文案「AI 调用可能产生费用」 |
| 提示词生成 | 文本 token，极小 | 同上 |
| 生图调用 | **按张计费，通常是文本的数十倍** | 确认弹窗文案明确「生成一张图片，可能产生较高费用」 |

失败不自动重试（重试=重复扣费）；失败态给「重新生成」按钮由用户显式触发。

## 三、不做范围

- 不做图片编辑、图生图、上传图片。
- 不做生图历史持久化（cache 即弃）。
- 不做第三方非 OpenAI 兼容生图协议（MiMo 生图走 OpenAI 兼容则天然支持）。
- 不做句子级长按交互（阅读体验重做批次）。

## 四、任务拆解（按 TDD 执行，入口=AI 学页展开键）

- Task 1：✅ AI 学页展开键重构 + `AiFeature` 新增 `SENTENCE_ANALYSIS` / `IMAGE_STUDIO`（`AiFeatureTest` 4→6 全绿）。androidTest：`feature_rows_start_collapsed_and_expand_on_tap`（默认收起 → 展开全部 → 收起消失）+ 既有用例先展开；AiLearningScreenTest 12/12、AiFeatureScreenTest 全绿、AppScreenTest 受影响 2 条绿。变异（默认展开）→ RED → 恢复复绿。Explainer「现在的状态」文案同步更新。
- Task 2：✅ `SentenceAnalysisPromptPolicy`（600 字符上限、空白拒绝、固定 system、行协议指令）+ `SentenceAnalysisParser`（角色白名单、TAB 三字段、字段 ≤300、1–20 行、总长 ≤4000、状态码先于解析、剥围栏）。JVM 14 例全绿。抽共享 `ai/ChatResponseEnvelope`（信封提取+剥围栏），`WordQaResponseParser` 重构复用且原测试全绿。变异（未知角色回退 Phrase）→ RED → 恢复复绿。
- Task 3：✅ `SentenceAnalysisUseCase`（编排同 WordQa：默认文本 Profile → 按域名确认 → 请求 → 解析；Key 清零；Cancelled 重抛）JVM 10 例；`SentenceAnalysisViewModel`（Idle→Analyzing→Analyzed/确认/失败/未配置；reset）JVM 8 例 + AppModule provider；`SentenceAnalysisScreen`（输入框 600 上限、分析按钮、成分卡片、确认弹层、失败/未配置常量文案）真机 7 例；AppScreen 路由 SENTENCE_ANALYSIS → 真实屏幕（骨架页只留给未实现功能），AiFeature.implemented 翻 true + `AiFeatureTest` 改期望实现集合 + `AiFeatureScreenTest` 过滤未实现，AppScreen 端到端接线测试 1 例。变异：NetworkUnavailable→Timeout RED、忽略拒绝 RED、断开路由 RED（各验证后恢复）。全量：JVM 绿、真机 292 例全绿（283+9 新增）、DB 三件套前后逐文件一致（快照 `outputs/verification-sentence-analysis-20260928/`）。
- Task 4：✅ `AiCapability.ImageGeneration`（标签「生图」，Vision 改「读图」消歧义）+ Room v18 `ai_preferences.defaultImageProfileId`（迁移测试实证：已有行 NULL、可写回）+ `DefaultImageProfileSelector`/`DefaultImageProfileResolver`（JVM 7 例：能力+密钥+存在性；清除失效选择**只清自己字段**，文本/生图默认互不陪葬）+ 设置页（行内「设为生图默认/生图默认」徽标、能力编辑自动出现生图项）；设置 VM 5 例（set/delete 读写改写保持另一字段；删除配置只清指向自己的默认）；顺带修复 `setDefaultTextProfile`/删除清除逻辑整表覆盖会抹掉另一默认的缺陷。变异：清除时存空偏好 RED（2 例）、迁移列名错 RED。全量：JVM 绿、真机 295 例全绿、DB 三件套本批前后逐文件一致（快照 `outputs/verification-image-generation-20260928/`）。
- Task 5：✅ `DrawingPromptPolicy`（JVM 6 例：固定 system、user 含主题/「英文」/「600」、确定同输出、空白/超长拒绝）+ `ImageGenerationResponseParser`（JVM 8 例：b64 优先、https url、http 拒绝、空 data/坏 JSON/空对象 InvalidResponse、b64 ≤10MB≈13981012 字符、状态码先于解析）+ `DrawingPromptUseCase`（JVM 11 例：Text 按域名一次确认、Draft ≤600 剥围栏、Key 清零、Cancelled 重抛）+ `ImageGenerationRequestBuilder`（JVM 5 例：`{base}/images/generations`、body 恰四字段、Key 只进 Authorization、query/fragment 拒绝）+ `ImageGenerationUseCase`（JVM 11 例：**每次**确认且核对 host+载荷类型、错域/文本答复/拒绝不放行、b64/url 结果、失败映射、Key 清零）+ `GeneratedImageWriter`（JVM 4 例：b64 严格解码、UUID 文件名、PNG/JPG 魔数扩展名、非法 b64 失败不落盘）+ AppModule 接线（`DefaultImageProfileResolver` provider、`@Named("imageAi")` 生图专用 transport `maxResponseBytes=16MB`——文本传输 512KB 上限会堵死 b64 响应，真机假红预防）。变异：确认只核对 confirmed 不核对 host/载荷类型 RED、扩展名恒 png RED（各恢复复绿）；解析器超长 b64 放行 RED、提示词模板去 600 约束 RED（各恢复复绿）。用例实现期真实 RED：DrawingPromptUseCase 漏状态码映射（401→InvalidResponse 而非 Unauthorized），补 401/429/5xx 先于解析后复绿。全量 JVM 648 例 0 失败（2026-09-28）。
- Task 6：✅ `GeneratedImageStore`（JVM 8 例：b64 直接落盘不出网、url 经二进制通道取回落盘且**不带密钥**、字节用完清零、InsecureUrl/HTTP/传输失败映射、取消重抛）+ `ImageStudioViewModel`（JVM 11 例：两段式确认——文本按域一次/图片每次、拒绝不产生任何字节、b64 与 url 两条路都落到 cache、解码失败→InvalidResponse、reset 忘记已确认域名）+ `ImageStudioScreen`（真机 11 例：主题输入 100 上限、两段按钮、两种确认弹层文案（生图明确「按张计费」）、结果渲染与缓存文件不可读回退、未配置/失败常量文案）+ AppScreen 路由与进入 reset（接线测试 1 例，夹具出站即 error 证明确认前零字节）+ `AiFeature.IMAGE_STUDIO` 翻 implemented=true（`AiFeatureTest` 期望集合同步）。变异：卡片文本断言暴露 Card 未合并子语义（`.semantics(mergeDescendants = true)`，顺带改善朗读）→ 修复后绿；输入上限放宽 RED、路由不接 VM 状态 RED（各恢复复绿）。全量：JVM 667 例 0 失败、真机 307 例全绿（295+12）、DB 三件套逐字节一致（快照 `outputs/verification-image-studio-20260928/`）。**待用户用自己的 Key 做一次真实出图验收**（假 transport 只证明参数传递与安全路径）。
- **F3-04 全部闭合**（Task 1~6）。
- **真机走查后追加修复（2026-09-28）**：`MainActivity` 从未传入 `wordQaViewModel` / `sentenceAnalysisViewModel` / `imageStudioViewModel`，真机上三个功能（词卡「问 AI」、长难句分析、AI 生图）点按钮均毫无反应——instrumented 测试自己注入 VM，绕过了生产组装点，所以全绿。修复后再加 JVM 守卫测试 `AppScreenProductionWiringTest`（解析 AppScreen 签名的 `= null` 参数，断言 MainActivity 全部传入；先 RED 精确点名三个缺失参数，再 GREEN）。

每个 Task 遵循：RED（行为性失败）→ 最小 GREEN → 变异验证 → 恢复 → 真机 `install -r -t` + `am instrument` 定向，DB 三件套前后比对。
