# 决策：词书 `ipa` 列修正与跨册例句差异化

- 日期：2026-11-11
- 触发：用户真机走查反馈（`cet6:hear` 卡面音标显示成 `/- /`，词性显示成 `/hɪə(r)/`），以及用户要求「同一个词在不同册用不同例句」。
- 决策人：项目负责人；执行：本轮产线回合。

## 一、`ipa` / `senses[].pos` 两列串位

**事实**：45,290 张随包卡片里 304 张的 `ipa` 是占位串 `/- /`；其中 300 张把真音标错放进了 `senses[0].pos`；另有 2 张（`toefl/empathy`、`toefl/employment`）词性被斜杠包成 `/n./`。

**决定 1：缺失就是空串，不是占位符。** UI 三个位置（`CardDetailScreen`、`GlobalVocabularySearchScreen`、`VocabularyRelearnScreen`）本来就用 `isNotBlank()` 判定隐藏整块，数据侧只要保证「没有音标 = `""`」，界面上就不会出现一根孤零零的短横。**没有**新增 UI 分支，也没有为数据缺陷加展示层兜底。

**决定 2：一次性归一 + 双向棘轮。** 已有的十册由 `tools/wordbooks/normalize_ipa.py` 幂等重写（只动 `ipa` 与 `senses[*].pos` 两列），未来的 Excel 重导由 `excel_to_wordbook.split_pronunciation()` 拦在入口。两侧都有测试：

- `tools/wordbooks/tests/test_normalize_ipa.py`（9 例）、`test_excel_to_wordbook.py` 的 `SplitPronunciationTest`（4 例）；
- Kotlin 资产棘轮 `WordBookIpaFieldHonestyTest`（4 例）：无占位串、无斜杠包裹的词性/音标串位、非空音标必须是 `/.../` 形态、卡片总数仍是 45,290。

**决定 3：异音词不改。** `/kləʊz/ v.; /kləʊs/ adj.` 这类多读音是合法数据，棘轮只校验「以 `/` 起头且闭合」，不追求单一形态。

**实测结果**：304 张占位卡清零（298 张恢复真音标、6 张本就没有音标故留空）、2 张斜杠词性还原为 `n.`、0 张仍有串位。

## 二、跨册例句差异化（只实现了能力，尚未跑产线）

**事实（本轮实测）**：十册共 45,290 张卡，其中 **10,760 张**卡片与「同一个词在另一册的那张卡」用的是完全相同的例句，涉及 **3,305 个词**；分册计数 cet4 1,285 / cet6 1,207 / doctoral 1,338 / graduate 1,310 / ielts 1,324 / senior 979 / toefl 1,341 / kaoyan 1,395 / junior 416 / primary 165。首跑 F1-08 报告里写的「跨册重复属预期，同一用户只装一册看不到」——本轮用户明确要求去掉这个将就。

**决定 4：只补生成撞句的卡，不全量重跑。** 全量重跑要 45,290 次请求（约 4 小时、且答案与现有 34,530 张不撞句的卡无关），而撞句集只有 10,760 张。`generate_examples.py --dedupe-cross-book` 的选择集正是这批卡。

**决定 5：补生成的提示词版本单独记 2。** 排除版提示（`USER_TEMPLATE_EXCLUSION`）会把其他册现用的句子列进提示并要求换场景/换主语/换句式；因此 `--dedupe-cross-book` 写出的 sidecar 条目记 `promptVersion: 2`，与首跑的 `1` 区分，合并时用新的 `--data-version-tag examples-YYYYMMDD-dedupe`。这是对「改提示词必须全量重跑」规则的**有意偏斜**，理由与范围如上：被改写的只有撞句卡，未撞句的卡一个字节都不动。

**执行前提（尚未满足）**：`generate_examples.py` 需要十册 Excel 原件（`--excel-dir`）与本地网关三件套（`--base-url` / `--model` / `--api-key`）。本轮机器上两项都不在：仓库内没有 `.xlsx`，`WORD_EXAMPLES_*` 环境变量为空，`http://127.0.0.1:7863/v1/models` 返回 `invalid_api_key`（服务在、钥匙不在）。

**跑法（拿到前提后一条命令）**：

```bash
python tools/wordbooks/generate_examples.py --dedupe-cross-book \
  --excel-dir <十册 xlsx 目录> --base-url http://127.0.0.1:7863/v1 \
  --model <模型名> --api-key <密钥> --concurrency 32
python tools/wordbooks/apply_examples.py --data-version-tag examples-YYYYMMDD-dedupe
```

## 三、不做什么

- 不为数据缺陷加 UI 兜底（音标缺失由 `isNotBlank()` 天然隐藏）。
- 不改 R8/压缩策略（见 2026-11-11 产物体积决策）。
- 不引入 `pytest` 依赖：仓库的 python 测试一律 `unittest`，本轮的测试也照此写。
