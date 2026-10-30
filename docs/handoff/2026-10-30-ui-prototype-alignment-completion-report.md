# 2026-10-30 UI 原型对齐收尾 工作报告

## 1. 本次目标

按用户给定顺序把「UI 原型对齐」剩下的六项一屏一屏推平：
sMeRecords → sMeSettings → sMeReminder → sMeLicenses/sMeData → 批 2 余下五屏 → 批 3 AI 助学七屏
（含未接入控件禁用态），全部完成后跑全量门禁、写报告，**推送前先问用户**。

## 2. 完成了什么

### 2.1 六项收尾（对应提交）

| # | 内容 | 提交 |
|---|---|---|
| 1 | sMeRecords 学习记录对齐原型（本周七格刻度 + 逐日细条 + 页尾提示） | `c6ba954` |
| 2 | sMeSettings 学习提醒行补原型副标题（AC-R-21 的「4 组 10 行」未增删） | `d1e7b8e` |
| 3 | sMeReminder 提醒与发音两屏补原型副标题与只读「重复」行 | `6332283` |
| 4 | sMeLicenses/sMeData 补「用途」副标题与页尾提示条 | `da3b058` |
| 5 | 批 2 余下五屏：启动品牌帧 / 建档·姓名 / 建档·选词书 / 错误屏按 `AppError` 分叉 / **新建** `UnavailableScreen.kt` | `dcac0f2` |
| 6 | 批 3 AI 助学七屏按原型对齐（提示条 / 分组 / 禁用态） | `ecbc136` |

### 2.2 批 3 七屏逐屏（本次会话完成）

| 屏 | 做了什么 | 按证据不搬的项 | 提交 |
|---|---|---|---|
| #17 `sAiHome` | 页末补「拍照翻译的图片只在本机识别，不上传；AI 生图会先把中文描述转成英文提示词。」Info 提示（`ai_learning_privacy_notice`） | 两列卡片格 /「即将可用」chip / 页头密钥按钮：现盘骨架是批 4 版式重做与 spec §3.5 钉住的 hero + 分组裸行；状态说法本仓已有 `featureStatusBadge` | `ecbc136` |
| #18 `sAiSentence` | 「切分结果」`SectionHeading`（trailing = 真实段数）+ 单卡逐段 `BareRow`（`topDivider` / 无 chevron / 3 行）；输入标签「英文句子」+ field-hint，计数器保留 | 「继续追问」三枚 chip：本仓分析是一次性请求，没有多轮后端 | `ecbc136` |
| #19 `sAiPhoto` | 识别框与「翻译成中文」之间补「先校对文字再翻译…」Info 提示（`photo_translation_proofread_notice`） | 4:3 静态取景框 + 单枚「打开相机」：本仓挂真实 CameraX + 拍照/相册两入口 + 权限提示 | `ecbc136` |
| #20 `sAiWordQa` | **反向钉住**：自由输入框（`围绕这个词提问`）与「已发送」保持为 0；词头 + 三枚固定问法保留 | 原型的对话流输入框：本仓只收 `WordQaKind` 固定问法，补上等于把提示词注入口开到界面 | `ecbc136` |
| #21 `sAiImage` | 「英文提示词」「生成结果」两枚 `SectionHeading`（各随内容出现）+ 主题框 field-hint | 原型页头卡与常驻「去配置生图服务」：与共享页头重复；配置出口由 `NotConfiguredCard` 在需要时提供 | `ecbc136` |
| #22 `sAiProfiles` | 页末补离线承诺 Info 提示（`ai_profiles_offline_notice`） | 列表行六件形状不改成卡片：批 4 已按 spec §4.3 改裸行 + 动作带，信息一件不少 | `ecbc136` |
| #23 `sAiProfileEdit` | 第 4 枚软卡「未接入的选项」（`ai_profile_unsupported_card`）：流式 / 音色细化 / 生图参数 / 连接测试四枚 `EntryRow(enabled = false)`，尾值统一「暂未接入」、无 chevron | 滑块 / 开关 / chip / 参考图等具体控件不复刻：超出 V1 请求白名单，且连接测试缺探活端点（裁决 B） | `ecbc136` |

### 2.3 文档与判据

- 新增 `app/src/test/java/com/example/englishlearning/ui/Batch3AiScreensContractTest.kt`（7 条，一一对应 #17–#23；
  含 #20 的反向钉与 #23 的禁用配方钉）。
- `AiProfileLayoutContractTest` **期望值变化（非放宽）**：编辑器 `SoftCard(` 3 → 4、`softCardBodies` 组数 3 → 4，
  测试名改为 `…into three soft cards plus the unsupported fourth`；13 个可写控件的组归属断言一字未减，
  并新增两条反向断言（四枚 `unsupported` tag 在 groups[3]；13 个真控件 tag 不许混进 groups[3]）。
- 计划文档新增「Task 22-I」台账（14 行，均带判据），对账表 #17–#23 翻 ✅，
  小计按文档命令实跑重算：**✅ 30 屏 · 🟡 6 屏 · ⬜ 0 屏**。

## 3. 验证记录

### 3.1 TDD

- 批 3 新契约先跑红：`7 tests completed, 6 failed`（仅 #20 的反向钉当时已成立）。
- 实现后：`Batch3AiScreensContractTest` + `AiProfileLayoutContractTest` 全绿。

### 3.2 全量门禁

`gradle --offline assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin` → **BUILD SUCCESSFUL**（61 tasks）。
JVM 结果按 XML 聚合实跑：**tests 2212 · failures 0 · errors 0 · skipped 0**。

### 3.3 connected（模拟器 `emulator-5574`，`am instrument -w -e class …`）

本次会话实跑输出：

| 套件 | 结果 |
|---|---|
| `LearningRecordsScreenTest` | OK (5 tests) |
| `SettingsScreenTest` | OK (18 tests) |
| `ReminderSettingsScreenTest` | OK (3 tests) |
| `SpeechSettingsScreenTest` | OK (13 tests) |
| `LicensesScreenTest` | OK (3 tests) |
| `BackupEntryUnificationTest` | OK (7 tests) |
| `AppScreenTest` | OK (57 tests) |
| `UnavailableScreenTest` | OK (2 tests) |
| `AppScreenOverlayContainerTest` | 先 3 条失败 → 按须先置 `font_scale 2.0` 后重跑 **OK (6 tests)**，已复原 1.0（实测 `settings get system font_scale` = 1.0） |
| `AiLearningScreenTest` | OK (18 tests) |
| `SentenceAnalysisScreenTest` | OK (9 tests) |
| `PhotoTranslationScreenTest` | OK (8 tests) |
| `WordAiQaScreenTest` | OK (10 tests) |
| `ImageStudioScreenTest` | OK (12 tests) |
| `AiProfileSettingsScreenTest` | OK (24 tests) |

`AppScreenOverlayContainerTest` 那 3 条失败是**环境前置缺失**（断言信息原文：「fontScale=2.0 未落进对话框窗口…
connected 侧需先把设备 font_scale 置 2.0，护栏不许在 scale 1.0 下空转」），不是本次改动回归。

## 4. Git 状态

- 分支：`stage-5-ui-r2-learning-path`
- 最近提交：`ecbc136`（批 3）、`dcac0f2`、`da3b058`、`6332283`、`d1e7b8e`、`c6ba954`
- **尚未推送**：按用户要求，推送前先请示。
- 工作树：本报告提交后应为干净。

## 5. 未完成 / 遗留问题

对账表里 🟡 仍有 6 屏（⬜ 已清零）：

| # | 屏 | 还差什么 |
|---|---|---|
| 9 | `sBookPicker` 选择词书 | 独立屏那一份（页头「导入」、搜索、排序、`.foot`「选用这本书」、notice）未对 |
| 12 | `sReadHome` 每日阅读 | 「继续」组双语对照独立入口（按「阅读器内切换」不搬，待用户再次确认）、旧 `Mint*`/M3 Button 家族的版面重排 |
| 13 | `sReadArticle` 阅读 | `.prose` 字号/行距逐值、底部两枚并列出口形态 |
| 14 | `sReadTranslate` 双语对照 | `bar-action`「只看中文」、末尾 warning notice、逐段英中字号/间距 |
| 24 | `sMeHome` 我的 | 「我的词汇」三行按「词库页四格分段是唯一出口」不搬；「应用」组「关于与许可」已新建关于页，接线待核 |
| 25 | `sMeWords` 我的词汇 | 分段计数、段内 caption、尾部 notice、页头搜索动作裁决 |

另：批 3 的 #18 单卡逐段把原型的三档（角色 / 英文 / 中文）映射到 `BareRow` 的 leading / title / subtitle；
`BareRow` 的 subtitle 上限是 2 行，长解释会省略。本轮未改共享件（改它会牵动几何契约），如后续需要完整展示，须单独立项。

## 6. 下一任接手须知

1. 先读本报告 + 计划文档的「Task 22-I」台账与对账表；🟡 6 屏按上表顺序推进。
2. 环境：Gradle 用 `C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/…/gradle.bat` + `--offline`；
   管道 `; exit ${PIPESTATUS[0]}`；输出过 `iconv -c -f utf-8 -t utf-8`。
3. connected 走模拟器 `emulator-5574`：`assembleDebug assembleDebugAndroidTest` → `adb install -r` 两个 APK →
   `am instrument -w -e class …`；不要用 `connectedDebugAndroidTest`，不要碰真机 `bf353dda`。
4. 双倍字号用例先 `settings put system font_scale 2.0`，跑完改回 `1.0`（`AppScreenOverlayContainerTest` 会当场校验设备缩放）。
5. Kotlin 字符串里写 `${...}` 字面量用 `${'$'}`（`\$` 非法）。
6. 源码契约测试的读法是「剥注释后的文本 + 花括号配对」，不锚行号；改动屏时优先沿用 `uiScreenSource` / `uiComponentSource`。

## 7. 用户确认过的决策

- 逐屏推进、不合 main、按批提交；每屏一行汇报（做了什么 / 哪条按证据不搬 / 实跑输出 / 提交号）。
- 既有判据不放宽，只允许改期望值并写明「为什么是期望值变化而非放宽」。
- 「读不到就整枚不显示、不编造指标」。
- 批次 3 的未接入控件按裁决 B：**画完整版面、禁用、标「暂未接入」**。
- 推送前先问用户（本文写作时**未推送**）。
