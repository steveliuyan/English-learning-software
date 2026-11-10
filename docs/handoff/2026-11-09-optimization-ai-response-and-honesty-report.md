# AI 响应信封解析加固 + 词义详情提示诚实化（2026-11-09 优化批）

## 背景

老板要求「阅读项目并自行优化」。按 AGENTS.md 的 TDD 与分阶段约束，选了两处**低风险、可测、直接影响真实体验**的缺陷来修，其余大改列为结转。

### 优化 1：chat/completions 信封取正文加固（`ai/ChatResponseEnvelope.kt`）

原实现只认 `choices[0].message.content` 是**字符串**这一种写法，别的写法一律 `InvalidResponse`（用户看到「返回内容不合格，可以重新生成」）。真机 / 不同 OpenAI 兼容服务上存在大量**内容合格但写法不同**的响应：

| 现象 | 原判定 | 现在 |
|---|---|---|
| `content` 为 `[{type:"text",text:…}]` 数组（多模态 / 新版接口） | 畸形 | 拼接 text 片段 |
| 推理模型把正文放 `reasoning_content`、content 留空 | 畸形 | 回退取值 |
| 老式 completions 的 `choices[0].text` | 畸形 | 回退取值 |
| 围栏前后夹散文（```json … ```\n希望有帮助） | 不剥 → 畸形 | 取第一块围栏内容 |
| 正常字符串 content | 正常 | 正常（回归不变） |

同时 `reading/ArticleResponseParser.kt` 原本自留了一份**重复**的信封解包 + `stripCodeFence`，改为复用 `ChatResponseEnvelope`，消除「同一规则抄两遍、漏改一遍」的复发面。状态码映射仍走唯一权威 `AiStatusFailure.of(`（`ArticleResponseParser` 内**仍恰好一次**），满足 `AiStatusFailureContractTest`。

### 优化 2：词义详情页末提示不再对空按钮撒谎（N-2）

`ui/CardDetailScreen.kt` 是「一份 UI、五种宿主」。搜索 / 查词那一路（`AppScreen.kt` 的 `VocabularyDetail` 分支）传 `onAskAi = null`，`AskAiButton` 不渲染；但页末那句「长难句分析、AI 问词都从这里进入，围绕这一个词继续追问。」过去**无条件**渲染，等于把用户引向一个点不到的入口。改为把 `InlineNotice` 收进 `onAskAi?.let` 守卫——**提示与入口同生同灭**。

## 变更清单

main：
- `ai/ChatResponseEnvelope.kt`：`extract` 支持 数组 content / `reasoning_content` / legacy `text`；`stripCodeFence` 支持围栏前后带散文、取第一块围栏。
- `reading/ArticleResponseParser.kt`：删掉本文件的 `stripCodeFence` 与信封解包，复用 `ChatResponseEnvelope`；保留单次 `AiStatusFailure.of(`。
- `ui/CardDetailScreen.kt`：页末 `InlineNotice` + `AskAiButton` 一起收进 `onAskAi?.let {}`。

test（先写后实现，全绿）：
- 新增 `ai/ChatResponseEnvelopeTest.kt`（13 例：字符串 / 数组 / 非文字片段跳过 / reasoning 回退 / content 优先 / legacy text / 空白判畸形 / 非 JSON / 无 choices / 围栏四种）。
- 新增 `ui/CardDetailNoticeHonestyTest.kt`（钉住提示文案必须落在 `onAskAi?.let` 守卫内、且与 `AskAiButton` 同块）。
- 既有 `CardDetailPrototypeContractTest`（文案恰出现一次、位于按钮之前）、各 Parser 测试、`AiStatusFailureContractTest` 全部保持通过。

## 验证

- `:app:compileDebugKotlin --offline --no-daemon` → BUILD SUCCESSFUL
- `:app:testDebugUnitTest --offline --no-daemon`（全量 JVM 单测）→ BUILD SUCCESSFUL
- 未跑 instrumented / `:app:assembleDebug`（本轮仅 JVM 可见改动，无资源 / manifest 变更）。

## 结转（本轮未做）

- 给搜索 / 查词详情真正接上 AI 问词入口：需在该分支补 `WordAiQaScreen` 渲染与 `overlayOpen` / `BackHandler` 串联，风险高于本轮范围，先以诚实文案止血。
- 「长难句分析」在词义详情内实际没有落地按钮（全仓 `长难句` 无第二处），提示里保留它是为了对齐原型契约；若日后补按钮，需同步在 `CardDetailNoticeHonestyTest` 加「按钮确实渲染」的断言。
- 无 git 可执行文件，未提交 / 推送。
