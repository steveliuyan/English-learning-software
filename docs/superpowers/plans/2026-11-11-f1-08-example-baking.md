# F1-08 例句离线烘焙进随包词书（实施计划）

- 日期：2026-11-11
- 决策：`docs/decisions/2026-11-11-card-examples-baked-into-bundled-wordbooks.md`（A 案：离线烘焙，运行时零请求）
- 规格：`docs/specs/01-vocabulary-learning-and-review.md` F1-08 / AC1-28…AC1-33（已按新范围重写）；
  `docs/specs/02-reading-and-ai-content.md` F2-07（改为「不再属于运行时 AI 消费方」），AC2-16 删除。

## 范围

45,290 张卡（十册全量，用户口径「全部」）每张写一句合格英文例句 + 中文译文，直接进 `app/src/main/assets/wordbooks/<id>/book.json`。
App 代码零改动：`WordBookPackageParser` 早已把 `example`/`exampleZh` 映射进 `WordCard`，卡面与详情页自动显示。

## 步骤与状态

| # | 步骤 | 状态 |
|---|---|---|
| 1 | 改 Spec（F1-08 重写、AC1-29…33 重写、F2-07 改向、删 AC2-16） | 完成 |
| 2 | 决策文档 `2026-11-11-card-examples-baked-into-bundled-wordbooks.md` | 完成 |
| 3 | `tools/wordbooks/example_validation.py`（解析 + 校验） | 完成，10 条用例 |
| 4 | `tools/wordbooks/generate_examples.py`（一卡一请求、sidecar、断点续跑、dry-run 报价） | 完成，7 条用例 |
| 5 | `tools/wordbooks/apply_examples.py`（只改两字段、幂等、metadata 版本标记） | 完成，4 条用例 |
| 6 | 词条集合核对（Excel 十册 vs 随包资产） | 完成：45,290 张，逐册 0 增 0 减 |
| 7 | `WordBookExampleCoverageTest`（资产级棘轮，未烘焙时自动跳过） | 完成，全量单测绿 |
| 8 | 实跑产线生成 45,290 条（需端点三件套） | 完成：45,290 / 45,290，sidecar 零失败 |
| 9 | 合并进资产 + `--data-version-tag examples-YYYYMMDD` + 体积复核（≤60MB） | 完成：十册全量合并，48.1MB |
| 10 | 第三方台账登记「例句为 AI 生成文本」+ 服务条款允许随包分发的判定 | 台账条目已登记；条款判定待确认（本地网关 `/v1` 服务的归属方） |
| 11 | `testDebugUnitTest` + `compileDebugAndroidTestKotlin` + `assembleDebug` + 真机走查卡面/详情页例句 | 完成：三连全绿；真机（M2102J2SC）三张卡的卡面与详情页都显示烘焙例句 + 译文，无崩溃 |

## 运行方式

```
# 1) 报价与覆盖率（不发请求，随时可跑）
python tools/wordbooks/generate_examples.py --excel-dir <十册 xlsx 目录> --dry-run

# 2) 实跑（每 N 条落盘；被杀后重跑自动跳过已完成）
python tools/wordbooks/generate_examples.py --excel-dir <目录> --base-url <...> --model <...> \
    --api-key <...> --concurrency 32

# 3) 合并 + 打版本标记
python tools/wordbooks/apply_examples.py --data-version-tag examples-20261111
```

## 已完成的一次现场读数（2026-11-11，dry-run）

十册 45,290 条待生成；按每请求 250 输入 + 70 输出 token、gpt-4o-mini 参考价（$0.15/$0.60 每百万）
估算约 $2.28（其中 doctoral-english 8,537 条最多，toefl 7,438、ielts 5,215）。
`book.json` 现状 41.6MB，预计合并后 50–53MB（预算上限 60MB）。

## 风险

- **不可逆**：模型输出一旦随包，换模型必须重跑产线发新版；产线 sidecar 必须入库留档（可重放 sidecar 而不必重花钱）。
- **许可**：例句是 AI 生成文本，必须先确认所用服务条款允许把输出随包分发，否则不发布该包。
- **逐字节安全**：`apply_examples.py` 会先验证 `book.json` 是标准 `json.dumps(indent=2, ensure_ascii=False)` 格式（实测十册全部可逐字节 round-trip），不满足就拒改。

## 实跑记录（2026-11-11）

- 端点：本地网关 `http://127.0.0.1:7863/v1`，模型 `cn:deepseek-v4.1-flash`。并发用 32：实测 64 并发触发网关 503 风暴（192 条里 153 条失败），32 稳定在 7–17 条/秒。
- 全程 3 小时左右跑完（多次因会话窗口重启，靠 sidecar 续跑，未重复请求已完成的词条）。
- 结果：45,290 / 45,290 张卡全部合格，sidecar `failures` 为空。合并后十册 `book.json` 合计 48.1MB（预算 60MB），`dataVersion` 全部为 `v1-xlsx-20261003+examples-20261111`。
- 8 个特殊词条（`vapo(u)r` / `wag(g)on` / `rumo(u)r` / `cliché` / `exposé` / `axe/ax` / `microsope` 的录入笔误）第一次全部被判「例句里没有这个词」；为此把匹配规则扩到「括注可选字母 / 斜杠并列 / 重音字母 / 编辑距离 1 的笔误容错」，Spec F1-08 已同步改写，python 与 Kotlin 两侧同口径，各补了用例，重跑 8 条全部通过。
- 质量读数：册内重复句 0–1%（唯一明显重复都发生在「同一个词出现在不同册」之间，如 hear / overcome / build，属同一词条跨册复用，界面上单册使用时看不到）。
- 计费：本地网关，无实际支出；dry-run 的 $2.28 只是 gpt-4o-mini 参考价口径。
