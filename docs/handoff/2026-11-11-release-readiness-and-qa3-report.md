# 交接：发布收口第 1 棒 — release 产物体积、AC 矩阵补齐、QA3 设备走查（2026-11-11）

用户令「全部都做」，本棒落了四件事中的前三件的一半 + 第四件的前置分析。全部本地提交，**未推送、不合 main**。

## 1. 做了什么

### 1.1 release 产物首次构建 + 体积读数（F5-01 / F5-04）

- `:app:assembleRelease` → **BUILD SUCCESSFUL in 4m 35s**，产出 `app-release-unsigned.apk` = **337,521,268 B（321.9 MiB）**。
- 构成实测：`lib/` **192.8 MiB**（4 个 ABI 的 `libonnxruntime.so`）、`assets/kokoro-production` **97.5 MiB**、`assets/wordbooks` 仅 **7.5 MiB**（原始 45.9 MiB）、dex 合计 16.4 MiB。
- **结论**：体积问题不在「词书 + 45,290 条例句」，而在推理运行时与模型。详见 `docs/verification/2026-11-11-release-build-size-and-qa3.md`。
- 决策：`docs/decisions/2026-11-11-release-artifact-size-and-abi-strategy.md` —— 先做 ABI 拆分（只发 arm64-v8a + armeabi-v7a，可省约 77 MiB），R8/资源压缩单开一轮。

### 1.2 修好一条发布阻断：AC 追踪矩阵缺 13 行

`checkReleaseReadiness` 第一次跑就 **红在 `verifyAcTraceabilityMatrix`**，缺 AC1-28…AC1-33、AC3-07…AC3-13（F1-08 与 F3-04/F3-05 新增的验收标准没进矩阵）。已按现有测试与产线用例补齐 13 行（70 → 83 行），证据逐条指到 `WordBookExampleCoverageTest` / `WordBookContentHonestyTest` / `tools/wordbooks/tests/*` / `BlockTranslationParserTest` / `OverlayTextFittingTest` / `ScreenTranslationSessionTest` 等。

修好后：`AC matrix covered 75 ids with evidence.` + `checkReleaseReadiness` **BUILD SUCCESSFUL in 1m 7s**（notices + 矩阵 + 单测 + secret-scan + release 产物扫描全绿）。

### 1.3 QA 第 3 轮：深色 + 1.3 字号设备走查（AC5-03 局部）

真机 `bf353dda`，只 `adb install -r` 装 debug APK 手工试用，零 instrumented。证据 `docs/handoff/evidence/qa3-2026-11-11/`：

- `01-home-dark-font13.png` 学习首页（今日已学完态）无裁切无重叠；
- `02-search-hear-dark-font13.png` 查词 overlay 命中 30 条、按册分组；
- `03-card-detail-dark-font13.png` **词义详情里 F1-08 的「例句」模块在深色 + 1.3 字号下正常显示英文例句 + 中文译文**（AC1-29 详情侧）。

收工已还原设备：`font_scale=1.0`、`night no`。

## 2. 走查/分析发现（下一轮处置）

1. **词书数据缺陷：304 张卡的 `ipa` 是占位串 `/- /`**，其中 300 张把真音标错放进了 `senses[].pos`（例：`cet6:hear` → `ipa:"/- /"`、`senses[0].pos:"/hɪə(r)/"`）。用户可见：查词结果与详情页渲染出 `/- /`。判定：**ETL（`tools/wordbooks/excel_to_wordbook.py`）列映射问题**，不是展示层。修复需回源核对 Excel 列序 + 重跑合并，属独立小批，**本棒未动**。
2. **跨册例句重复（用户提过的 4,330 组）— 已量化**：45,290 张卡里唯一例句文本 38,770 条；**同文本重复组 4,335 组，涉及 10,855 张卡、3,331 个不同单词**；其中「同一个词在它出现的所有册里例句完全相同」的有 **132 个词**（hear / eager / lease / barbecue …）。要消除需带排除提示重跑约 1 万张卡的产线 + 全量重建资产（约 1 个产线回合 + 复核），本棒未做。
3. TalkBack 六链语音、飞行模式六条、真机冷启动性能复测仍属人工任务书（`docs/handoff/2026-10-15-qa-round3-task.md`）。
4. 在册小债照旧：页面转场 `pumpUntil` 偶发红（等用户裁定）、若干过期行号指针、两枚裸子串探针、`AppOverlayRouteResolver.kt:11` KDoc。

## 3. 门禁读数（本棒）

- `:app:assembleRelease` BUILD SUCCESSFUL in 4m 35s
- `:app:assembleDebug`（QA 用）BUILD SUCCESSFUL in 17s
- `verifyAcTraceabilityMatrix` green（75 ids）
- `checkReleaseReadiness` BUILD SUCCESSFUL in 1m 7s
- 单测未单独 --rerun-tasks 重跑：`checkReleaseReadiness` 内 `:app:testDebugUnitTest` 为 up-to-date 通过。**下棒若要把单测当验收读数，需 clean 重跑并现算 XML 计数。**

## 4. 下一步建议

1. ABI 拆分落地（`bundle`/splits + `verifyReleaseArtifact` 改指向拆分产物 + 装机口径改写），预估单机省 77 MiB。
2. 词书 `ipa` 列映射修复（304 卡，含 300 卡音标错位）。
3. 跨册例句差异化重跑（4,335 组 / 10,855 卡）。
4. 发布签名（需用户提供 keystore）。
