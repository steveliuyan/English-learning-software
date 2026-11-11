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

## 5. 追加：ABI 拆分落地（用户「可以」后同棒完成）

- 配置：`app/build.gradle.kts` 新增 `splits.abi`，由 Gradle 属性 `abiSplits` 驱动，**默认 false**（默认产物形状、既有 QA 口令、模拟器 instrumented 全部不变）。
- 契约测试：`verification.ReleaseAbiSplitContractTest`（3 例，`splits ship only the two arm ABIs` / `abi splits are gated by the abiSplits gradle property` / `release still does not enable r8 or resource shrinking`）—— 3 tests / 0 failures。
- 门禁：`verifyReleaseArtifact` 改成「扫目录里每个 APK」，拆分产物逐份 secret-scan 干净（arm64 185,930,228 B / armv7 170,205,460 B）。
- 读数：arm64-v8a **177.3 MiB**、armeabi-v7a **162.3 MiB**，相对 universal 321.9 MiB 分别 **−45% / −50%**（比预估「省 77 MiB」更好，因为不再需要把四份原生库合进一个包）。
- 决策与实测已回写：`docs/decisions/2026-11-11-release-artifact-size-and-abi-strategy.md` §实施结果、`docs/verification/2026-11-11-release-build-size-and-qa3.md` §5、`docs/verification/release-notes-v0.0.1.md`。

仍未做：R8/资源压缩（决策决定 3，单开一轮）、发布签名（无 keystore）、词书 `ipa` 列映射修复（304 卡）、跨册例句差异化重跑（4,335 组）。

## 6. 追加：音标列修正 + 跨册例句去重能力（同棒完成）

- **音标列串位已修**：304 张占位卡清零（298 恢复真音标、6 张源表无注音留空串）、2 张斜杠词性还原、0 张串位。数据侧 `tools/wordbooks/normalize_ipa.py`（幂等，只动 `ipa`/`pos` 两列），产线侧 `excel_to_wordbook.split_pronunciation()`，资产侧棘轮 `WordBookIpaFieldHonestyTest`（4 例）。
- **解析器放宽**：`WordBookPackage.validateCard` 不再要求 `ipa` 非空白，只拒超长——否则「无注音」卡会被判 `InvalidCard`（改动后首轮 4 条测试红过，已定位并修）。
- **跨册例句去重**：新增 `generate_examples.py --dedupe-cross-book`（只选撞句卡 + 排除提示 + `promptVersion: 2`），7 例新测试。实测 10,760 张 / 3,305 词跨册撞句。
- **门禁**：单测 366 suites / 2,713 tests / 0 fail / 0 skip；python 侧 normalize 9 + ETL 7 + generator 14 全绿。
- **未跑**：补生成需要十册 `.xlsx` 原件与本地网关 `--api-key`（本机都没有）。

## 7. R8 已开（收尾）

- `app/build.gradle.kts`：`isMinifyEnabled`/`isShrinkResources` 由属性 `r8` 驱动，**默认 true**，`-Pr8=false` 回退。
- `app/proguard-rules.pro`：只留反射 / JNI / 按名实例化三类入口（ONNX Runtime、Tesseract、BouncyCastle、Tink、ML Kit、kotlinx.serialization），逐节写理由；无全局 `-ignorewarnings`。
- 契约测试 `ReleaseR8ContractTest`（4 例）钉住开关默认值、optimize 档、keep 条目与「每节必须有理由」；`ReleaseAbiSplitContractTest` 的第 3 例从「R8 必须关」改成「R8 与 ABI 是两个独立开关」。
- 体积：universal 310.7 MiB（−11.2）、arm64 166.1（−11.2）、v7a 151.1（−11.2）。
- 冒烟：debug 签名装真机，冷启动 567ms、无崩溃、首页与查词页正常。
- **遗留**：底部四 tab 的逐屏回归、OCR/TTS 真机链路未验；签名仍 TODO-USER。
