# 2026-11-11 F1-08 例句离线烘焙进随包词书 工作报告

## 1. 本次目标

用户原话：「全部」——十册词书 45,290 张卡，每张都要有英文例句（连带中文译文），且例句要像原生数据一样长在词书里（不是运行时生成+缓存）。

## 2. 完成了什么

- Spec：`docs/specs/01-vocabulary-learning-and-review.md` F1-08 重写为「例句的离线烘焙与随包分发」，AC1-29…AC1-33 全量重写；`docs/specs/02-reading-and-ai-content.md` F2-07 改为「例句不再属于运行时 AI 消费方」并删除 AC2-16。
- 决策：`docs/decisions/2026-11-11-card-examples-baked-into-bundled-wordbooks.md`（A 案离线烘焙；B 案运行时生成+缓存作废；C 案两者都要，否）。
- 计划：`docs/superpowers/plans/2026-11-11-f1-08-example-baking.md`（11 步全部完成，含实跑记录）。
- 产线（零依赖 python，`tools/wordbooks/`）：`example_validation.py`（解析 + 六条内容规则）、`generate_examples.py`（一卡一请求、sidecar 原子落盘、断点续跑、dry-run 报价）、`apply_examples.py`（只改 example/exampleZh、幂等、metadata 版本标记），各配用例。
- App 侧：新增 `app/src/test/java/com/example/englishlearning/wordbook/WordBookExampleCoverageTest.kt`——资产级棘轮，未烘焙时自动跳过、烘焙后变硬断言（覆盖率 100% / 成对 / 逐条同规则）。
- 资产：十册 `book.json` 写入 45,290 对例句译文，`metadata.json` 的 dataVersion 全部升到 `v1-xlsx-20261003+examples-20261111`。
- 台账：`docs/third-party-notices.md` 新增 `ai-generated-card-examples` 条目。
- 其它：`.gitignore` 补 `__pycache__/` / `*.pyc`。

## 3. 验证记录

- python 产线用例 40 条全绿（`python -m unittest discover -s tools/wordbooks/tests`）。
- `:app:testDebugUnitTest` 2,706 条用例 0 失败 0 跳过（含资产级棘轮的两条：一条真跑了 45,290 张卡的逐条校验，一条是规则空白分支）。
- `:app:compileDebugAndroidTestKotlin` 与 `:app:assembleDebug` 均 BUILD SUCCESSFUL。
- 真机（M2102J2SC / MIUI）走查：选「小学英语词汇」3 词/天，三张卡（the / foreign / stay）卡面都显示烘焙例句；the 与 stay 的详情页「例句」模块同时显示英文句与译文；走完 3 张到「今日完成」，crash log 为空。
- 体积：十册 book.json 合计 48.1MB（预算上限 60MB）。
- 质量读数：册内重复句 0–1%；跨册重复（同一个词在十册里各出现一次，如 hear / overcome / build）属预期，同一用户只装一册看不到。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`；本次提交含资产（十册 book.json + metadata.json，+6.5MB）、产线脚本、sidecar 留档（`tools/wordbooks/examples/`，14.7MB）、三份文档、Kotlin 棘轮测试。

## 5. 未完成 / 遗留问题

- ~~许可判定待用户拍板~~ **已闭合**：负责人 2026-11-11 确认这是本项目自部署的本地模型（`http://127.0.0.1:7863/v1`），不含第三方服务条款，随包分发无外部许可障碍。台账条目已按此结论定稿。
- 未做 release 构建（本次只验 debug）。
- 跨册重复句未去重：同一个词在十册里各有一张卡，拿到的是同一句（如 hear）。若要求「同一个词在不同册也用不同句」，需再跑一轮带排除提示的补生成。

## 6. 下一任接手须知

- 重跑/补生成：`python tools/wordbooks/generate_examples.py --excel-dir <十册 xlsx 目录> --base-url ... --model ... --api-key ... --concurrency 32`（并发别超 32，64 会触发网关 503 风暴；已完成的词条自动跳过）。
- 合并：`python tools/wordbooks/apply_examples.py --data-version-tag examples-YYYYMMDD`；同一天重跑幂等。
- 换模型/改提示词必须重跑全量并换 `--data-version-tag`，否则资产与台账版本对不上。
- 八条例外词条（`vapo(u)r` / `wag(g)on` / `rumo(u)r` / `cliché` / `exposé` / `axe/ax` / `microsope` 录入笔误）是靠匹配规则容错通过的，规则写在 Spec F1-08，python 与 Kotlin 两侧必须同步改。

## 7. 用户确认过的决策

- 覆盖范围 = 「全部」45,290 张卡，不是抽样或分层。
- 例句走离线烘焙随包（A 案），运行时零请求零缓存零出站。
- 例句许可：产线用本地自部署模型，负责人确认无第三方条款障碍，可随包分发（台账 `ai-generated-card-examples` 已定稿）。
- 产线端点用用户给的本地网关三件套；该端点只用于产线，不改变 App「仅 HTTPS 公网 Endpoint」的约束。
