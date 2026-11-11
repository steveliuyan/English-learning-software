# 发布产物体积读数与 QA 第 3 轮（深色 / 大字号）走查 — 2026-11-11

> 本文件是 F5-01 / F5-04 与 AC5-03 的设备侧证据之一。所有数字均为本地实测，命令已写在正文，可复算。

## 1. release 产物实测（首次构建）

```bash
GRADLE="C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat"
"$GRADLE" :app:assembleRelease --no-daemon      # BUILD SUCCESSFUL in 4m 35s（54 tasks: 28 executed / 3 cached / 23 up-to-date）
"$GRADLE" checkReleaseReadiness --no-daemon     # BUILD SUCCESSFUL in 1m 7s
```

| 产物 | 字节 | MiB | 说明 |
|---|---|---|---|
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 337,521,268 | 321.9 | 748 个 zip 条目；**未签名**（本地无 keystore，见 release notes TODO-USER） |
| `app/build/outputs/apk/debug/app-debug.apk` | 353,370,878 | 337.0 | 对照用 |

### 1.1 体积构成（按 zip 中央目录的压缩后字节统计）

| 分类 | 压缩后 MiB | 备注 |
|---|---|---|
| `lib/` | **192.8** | 40 个条目，4 个 ABI：arm64-v8a / armeabi-v7a / x86 / x86_64 |
| `assets/kokoro-production` | **97.5** | `kokoro-v1.0.int8.onnx` 92,361,271 B → 63.05 MB（deflate）；`voices-v1.0.bin` 28,214,398 B → 25.93 MB |
| `assets/wordbooks` | 7.5 | 原始 45.9 MiB（十册 `book.json`，含 45,290 条例句），JSON 压缩率约 6:1 |
| `classes*.dex`（5 个） | 16.4 | 4.2 + 4.0 + 3.5 + 2.6 + 2.1 |
| `res/` | 3.8 | |
| `assets/mlkit-google-ocr-models` | 1.2 | |
| 其余（arsc / META-INF / properties 等） | < 3 | |

**结论：体积的 90% 不是词书资产**——`lib/` + Kokoro 模型占 290 MiB。词书（含本轮新增例句）只占 7.5 MiB，压缩比很高。

### 1.2 单个 ABI 的原生库成本（`lib/<abi>/`，均为 stored 未压缩）

| ABI | `libonnxruntime.so` | 说明 |
|---|---|---|
| x86_64 | 38,473,056 B | 仅模拟器需要 |
| x86 | 38,442,284 B | 仅模拟器需要 |
| arm64-v8a | 32,120,992 B | 真机主力 |
| armeabi-v7a | 22,727,712 B | 32 位真机 |

**可回收空间**：只发 arm64-v8a + armeabi-v7a 时，单靠 ABI 拆分即可减约 **77 MiB**（两个 x86 库）。见决策 `2026-11-11-release-artifact-size-and-abi-strategy.md`。

## 2. 发布门禁：AC 追踪矩阵曾红，已修复

首次跑 `checkReleaseReadiness` 在 `verifyAcTraceabilityMatrix` 处 **FAILED**，缺 13 个 AC 编号：

```
AC1-28 AC1-29 AC1-30 AC1-31 AC1-32 AC1-33
AC3-07 AC3-08 AC3-09 AC3-10 AC3-11 AC3-12 AC3-13
```

原因：F1-08（例句烘焙）与 F3-04/F3-05（原位覆盖 / 悬浮窗屏幕翻译）新增的验收标准只写进了 specs 01 与 03，`docs/verification/ac-traceability-matrix.md` 未同步。

处置：按现有测试与产线用例补齐 13 行（矩阵 70 → 83 行），证据逐条指向：

- AC1-28 / AC1-33：`ui.WordBookExampleCoverageTest#everyBundledCardCarriesAQualifiedExamplePair`、`ui.WordBookContentHonestyTest#theShippedPlaceholderExampleBecomesNoExampleAtAll`
- AC1-30：`tools/wordbooks/tests/test_example_validation.py`（26 例）
- AC1-31 / AC1-32：`tools/wordbooks/tests/test_generate_examples.py`（resume / flush / 不重复请求）、`test_apply_examples.py#test_only_example_fields_change`、`#test_merge_is_idempotent`
- AC3-07 ~ AC3-11：`OcrBlockContractTest`、`OverlayBlockPlannerTest`、`BlockTranslationParserTest`、`OverlayTextFittingTest`、`PhotoTranslationViewModelTest`
- AC3-12 / AC3-13：`ScreenCaptureGateTest`、`ScreenCaptureFingerprintTest`、`ScreenTranslationSessionTest`、androidTest `ScreenTranslationOverlayDeviceTest`

修复后：`AC matrix covered 75 ids with evidence.`，`checkReleaseReadiness` 全绿（notices + 矩阵 + 单测 + secret-scan + release 产物扫描）。

## 3. QA 第 3 轮（设备侧，深色 + 大字号）

设备 `bf353dda`（M2102J2SC / Android 13），**只装 debug APK 手工试用，零 instrumented**（沿用仓库红线）。设置：`cmd uimode night yes` + `settings put system font_scale 1.3`（收工已还原 `font_scale=1.0` / `night no`）。

证据：`docs/handoff/evidence/qa3-2026-11-11/`

| 截图 | 走查点 |
|---|---|
| `01-home-dark-font13.png` | 学习首页（今日已学完态）：标题「今日学习」、连续打卡、搜索入口、完成态文案与两枚次入口，深色 + 1.3 字号下无裁切、无重叠 |
| `02-search-hear-dark-font13.png` | 全局查词 overlay：输入 `hear` 命中 30 条、按册分组、来源标注可读 |
| `03-card-detail-dark-font13.png` | 词义详情（F1-08 新增内容路径）：**「例句」模块显示英文例句 + 中文译文**，深色下对比度正常，1.3 字号下例句折行不溢出 |

**本条对 AC1-29 的意义**：详情页半边在深色 + 大字号下实测成立（1.0 浅色真机证据见 `2026-11-11-f1-08-example-baking-report.md`）。词卡正面（揭晓前）本次未取到——设备上今日计划已全部完成，无法再进卡面；该项仍以既有 1.0 字号真机截图为准。

### 3.1 走查发现（待下一轮处置）

- **音标缺失时显示 `/- /`**：查词结果与详情页里「六级英语词汇」的 `hear` 词条音标渲染为 `/- /`（详情页另有一处正常 `/hɪə(r)/`）。属展示层降级串外露，用户可见噪音。建议单开一枚小修：音标缺失时整块隐藏而不是画占位短横。

## 4. 仍未自动化的部分（诚实边界）

- TalkBack 六链语音播报、飞行模式手工六条、真机冷启动性能复测：仍按 `docs/handoff/2026-10-15-qa-round3-task.md` 由人工执行，本轮未覆盖。
- release 签名：本地无 keystore，产物为 unsigned（`verifyReleaseArtifact` 已注「signature check pending release keystore」）。

## 5. ABI 拆分实测（`-PabiSplits=true`）

```bash
"$GRADLE" :app:assembleRelease -PabiSplits=true --no-daemon    # BUILD SUCCESSFUL in 1m 42s
"$GRADLE" verifyReleaseArtifact -PabiSplits=true --no-daemon   # 两份产物 scan clean
"$GRADLE" :app:testDebugUnitTest --tests "*ReleaseAbiSplitContractTest*"   # 3 tests / 0 failures
```

| 产物 | 字节 | MiB |
|---|---|---|
| `app-arm64-v8a-release-unsigned.apk` | 185,930,228 | 177.3 |
| `app-armeabi-v7a-release-unsigned.apk` | 170,205,460 | 162.3 |
| universal（对照，默认构建） | 337,521,268 | 321.9 |

结论：真机 arm64 单机体积从 321.9 MiB 降到 **177.3 MiB（−45%）**，armerabi-v7a 到 162.3 MiB。默认构建不受影响（`abiSplits` 默认 false），既有 QA 安装口令保持原样。
