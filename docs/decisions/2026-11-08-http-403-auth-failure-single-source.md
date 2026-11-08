# 决策：HTTP 403 归入「密钥被拒」，状态码→失败分类只留一处权威（N-1）

日期：2026-11-08
分支：`stage-5-ui-r2-learning-path`（不合 main）
关联：`docs/specs/02-reading-and-ai-content.md` F2-02 + AC2-04/AC2-11、`docs/specs/03-dictionary-pronunciation-and-photo-translation.md`（拍照翻译与发音复用同一失败分类）、`docs/decisions/2026-11-05-ai-outbound-confirmation-granularity-and-network-security-config.md`（探活的窄授权口径）、缺陷记录 N-1（见 `docs/handoff/2026-11-05-qa-round3-report.md` 收口补记与本决策 §1）

## 1. 背景：一条界面文案把两件事说反了

真机 `bf353dda`（2026-11-08）用配置好的 Profile「贝贝」（Endpoint `https://www.sssai.xin/v1`）走 AI 问词、长难句、文章生成三条腿，界面**一律**落在同一句话：

> 返回内容不合格，可以重新生成。

而同一套配置点「测试连接」，界面说的是另一套：「鉴权失败」。两句并存就是本缺陷的形状——一句话让你去重新生成，一句话告诉你密钥有问题，而只有后者指向唯一有效动作。

排查用了四次出站，取到的是**同一份服务端答复**（原始取证在设备侧临时区，密钥已脱敏，不落仓库）：

```
meta  method=GET  url=https://www.sssai.xin/v1/models           status=403 type=application/json; charset=utf-8 reqlen=0   bodylen=52
body  {"code":"GROUP_DELETED","message":"API Key 所属分组已删除"}
meta  method=POST url=https://www.sssai.xin/v1/chat/completions  status=403 type=application/json; charset=utf-8 reqlen=658 bodylen=52
body  {"code":"GROUP_DELETED","message":"API Key 所属分组已删除"}
```

另外两条独立证据把「服务端确实答 403」和「应用自己的解释跑偏」分开了：

- 匿名 `curl` 不带任何密钥打同一台 `/v1/models`，得到的仍是 403 + 同一段 JSON ——说明这份答复是网关的准入拒绝，不是本应用请求形态引起的。
- 「测试连接」这条腿走的是 `AiConnectionProbe.classifyStatus`，那里**已经**写了 `401, 403 -> Unauthorized`；真机当场报「鉴权失败」，与取证一致。

结论分两层。**外部**：用户那把密钥所属的计费分组已被服务方删除，配置本身失效，任何请求都不会进模型（响应体只有 52 字节、`chat/completions` 那条 reqlen=658 说明请求已发出但被网关挡在模型之前，四次出站**零生成费用**）。**内部**：应用把 403 翻译成了「内容不合格」，并顺着 `AiFailure.InvalidResponse -> UserAction.RegenerateContent` 把用户推向「重新生成」——对失效密钥重试永远不会成功，还要再吃一次逐次确认弹窗。

代码面的成因是**同一条映射抄了七遍、抄漏了一遍**。全仓状态码→失败的判点共八处（重算：`grep -rn "401 -> \|401, 403 ->" app/src/main/java/com/example/englishlearning`，当前 8 行）：

| 判点 | 现状 | 是否带密钥 |
|---|---|---|
| `ai/AiConnectionProbe.kt:104` | `401, 403 -> Unauthorized` | 是 |
| `wordqa/WordQaResponseParser.kt:26` | 只有 401/429/5xx | 是 |
| `reading/ArticleResponseParser.kt:27` | 同上 | 是 |
| `sentence/SentenceAnalysisParser.kt:39` | 同上 | 是 |
| `phototranslation/PhotoTranslationParser.kt:22` | 同上 | 是 |
| `imagegen/ImageGenerationResponseParser.kt:28` | 同上（另有 `in 200..299` 显式放行） | 是 |
| `imagegen/ImageGenerationUseCase.kt:82` | 同上（结果形态，不是抛异常） | 是 |
| `imagegen/GeneratedImageStore.kt:57` | 同上 | **否**（无密钥 CDN 下载，见 §5） |

而 `ai/AiFailure.kt:27` 的 KDoc 早就写着「401/403：Key 被拒绝」——规格里想要 403 进鉴权档，只有探活实现了它。这不是笔误级遗漏，是重复实现的必然漂移：六处带密钥的 AI 响应路径各自内联同一条规则，改一处不动六处没人拦。

## 2. 决定：状态码→`AiFailure` 只留一处权威，六处消费

新增 `app/src/main/java/com/example/englishlearning/ai/AiStatusFailure.kt`：

```kotlin
object AiStatusFailure {
    fun of(statusCode: Int): AiFailure? = when (statusCode) {
        401, 403 -> AiFailure.Unauthorized
        429 -> AiFailure.RateLimited
        in 500..599 -> AiFailure.ServerUnavailable
        else -> null // 不认识的档不猜：由调用方按原有形状处理
    }
}
```

六处带密钥的 AI 响应路径改为消费它，**保持各自的落地形态不变**：五处 `parse` 用 `AiStatusFailure.of(httpStatusCode)?.let { throw AiException(it) }` 顶掉整段内联 `when`；`ImageGenerationUseCase` 那条腿是结果形态，改成拿到非 null 即 `DrawingPromptResult.Failed(it)`。返回 `null` 而不是兜底 `InvalidResponse`，是为了不在共享层偷偷改变 3xx/400/404/405 的现有归属（§4）。

探活（`AiConnectionProbe.classifyStatus`）只借它的**鉴权档**：`2xx -> Reachable`；`AiStatusFailure.of(code) == AiFailure.Unauthorized -> Unauthorized`；其余仍 `ServiceError`。这里刻意不整体复用——AC2-11 要求探活把「服务返回错误状态」与「密钥被拒绝」分开说，429/5xx 在探活语义里属于前者，在响应路径里属于可重试档，两边共享的只有「401/403 都是密钥被拒」这一条。

## 3. 为什么不是「六处各补一支 403」

最小改动当然是六处各加一行。否决它，理由有三：

1. 本缺陷的**成因就是重复实现**。补完六支等于把同一份规则重新钉在六个地方，下一次规格变化（比如 402 支付失败、451 法律屏蔽）还要靠人记得改全，且没有任何测试能在漏改时报红。
2. 全仓已有同口径的先例：音频缓存元数据（`docs/decisions/2026-11-05-*` 音频台账）、发音关联实体、`ChatResponseEnvelope` 都是「先抽唯一权威，再用源码扫描契约闸钉住不许长出第二份」。本批沿用同一手法，不新造规则。
3. 有契约闸之后，「漏改」从运行时体验问题变成编译期/测试期问题：任何站点重新内联 `401`/`403` 判据当场红。

代价：多一个文件与一处间接。可接受——这六处本来就互不知道对方存在，抽层后它们第一次共享同一条语义。

## 4. 本批不做的分档，如实登记

`AiStatusFailure.of` 只覆盖既有三档。以下现状**一字不改**，本决策不宣称已修：

- **3xx 重定向、400、404、405** 仍落 `AiFailure.InvalidResponse`。它们确实也是「重试不会好」，但把它们分档需要新的失败成员与对应文案、按钮动作，属产品口径扩展，不是这个缺陷的收口；且真机证据只证到 403，没证到这些档在真实网关上的表现。
- **发音两 Provider**（`language/infrastructure/OpenAiCompatiblePronunciationProvider.kt:68`、`MiMoPronunciationProvider.kt:128`）把**任何** HTTP 错误折成 `PronunciationResult.Failed()`。`PronunciationResult` 本身没有失败分类位，接进 `AiStatusFailure` 要先给发音加失败语义——那是「云端发音坏了为什么不告诉我」的另一个缺陷，不在 N-1 范围内。
- **外刊抓取**（`reading/FetchArticleUseCase.kt:116`）非 2xx 一律 `SourceUnreachable`：那条腿不带密钥、目标是白名单新闻站，鉴权档在那里没有意义。

## 5. `GeneratedImageStore` 为什么必须留在映射之外

它是生图成功后**无密钥**下载 CDN 返回的图片 URL（`AudioHttpResult`，请求头里没有 `Authorization`）。那里的 401/403 描述的是「那个图片链接不可取」，不是「你的密钥被拒」；把它接进共享映射，等于在密钥明明有效时引导用户去换密钥——正是本决策要消灭的那类误导。

处理方式：保留其私有 `failureOf`，在文件里写明这条**有意例外**的理由，并让契约闸的扫描面**排除**该文件（排除项写进测试常量，不是靠正则碰不到）。

## 6. 判据与契约闸

- **RED 先行**：六处各加一枚 403 用例，期望 `AiFailure.Unauthorized`，用例体就用真机取证的那段 `GROUP_DELETED` JSON——保证「修的是这次真机撞到的东西」，不是凭空造的边界。改码之前必须先看它们红，且红在「落 InvalidResponse」这一句上。
- **映射单测**：`AiStatusFailure` 逐档穷举（401/403 同档、429、500/503、200/3xx/400/404/405/428 归 null），并钉住「鉴权档不得因文案变化而漂」。
- **契约闸**（源码扫描，唯一权威不许长出第二份）：`main` 源码里出现 `401 ->` 或 `401, 403 ->` 这类内联鉴权判据的文件，只允许 `AiStatusFailure.kt` 与 `AiConnectionProbe.kt`（后者只借鉴权档，`Grep -rn` 现算 8 行 → 收口后应变 2 行）。按本仓既有口径，扫描面剥注释后判、路径常量 + 文件数守卫 + 反空转夹具（合成一段含 `401 ->` 的假源码必须被判出），避免 `walkTopDown()` 打空路径静默放行。
- **探活语义不破**：既有 `AC2-11` 那批用例（429/5xx → 「服务返回错误状态」而非「密钥被拒绝」）必须全绿，用来证明 §2 的「只借鉴权档」不是嘴上说说。
- 门禁按仓内口径：`:app:assembleDebug` → `:app:testDebugUnitTest --rerun-tasks` → `:app:compileDebugAndroidTestKotlin`，JVM 单测是验收线（detekt/ktlint 存量债不当回归）。设备侧只需一次里程碑装机（`bf353dda` `install -r`），密钥修复前后的对照目视由用户执行——**本批代码修的是「怎么说」，用户侧「能用」要等他在 `www.sssai.xin` 重新生成密钥并回「我的 → AI 服务 · 贝贝」重填**。模型名 `gpt-6.1-sol` 是否有效在此之前仍不可验。
