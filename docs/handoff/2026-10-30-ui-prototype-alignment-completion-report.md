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
| 7 | #9 选择词书补原型「换词书后旧进度保留」提示 | `dfdc647` |
| 8 | #13/#14 阅读正文三档排印逐值 + 页脚双出口 + 只看中文 | `f94e57b` |
| 9 | #12 每日阅读迁出旧 Mint/M3 家族（动作归 PillButton 三档）+ 页头副标题 | `a008d23` |
| 10 | #24 我的页：关于入口经设置内容到达（补宿主行为判据）、第三格裁决 | `cc25f19` |
| 11 | #25 我的词汇：计数/说明/页尾提示核实 + 四处偏离登记 | `2089e94` |

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

### 2.2.1 六屏收尾（同日后半段，本轮会话完成）

| # | 屏 | 做了什么 | 按证据不搬 / 期望值变化 | 提交 |
|---|---|---|---|---|
| 9 | `sBookPicker` | 「换词书后旧进度保留」Info 提示（`book_picker_progress_notice`） | 页头「导入」/ `.foot`「选用这本书」/ `.meta` 三件不搬（已有真入口 / 单条保存语义 / 无导入能力）；原型搜索与排序源码里不存在 | `dfdc647` |
| 13/14 | `sReadArticle` / `sReadTranslate` | 正文 `AppType.Prose`（17.5 衬线 / 30）与 `ArticleTitle`（30）逐值；页脚「双语 + 完成阅读」两枚并列；「只看中文」落模式行并隐藏英文侧；译文提醒按 `ArticleSource` 分三支 | `.prose` 逐段交错不搬（数据只有两块字符串 + 绝对偏移高亮）；译文块容器撤掉（原型无容器） | `f94e57b` |
| 12 | `sReadHome` | 整屏迁到 `appColors()` + 共享件：动作归 `PillButton` 三档（主 1 / 次 4 / 文字 1）、承载面归 `AppCard`/`SoftCard`、圆角归 `AppShape`；补原型副标题；主色配平迁进 `pillButtonInk` 纯表 | `onBrand`/`onMint`/`MintPrimary` 屏内计数 1→0（**期望值变化，非放宽**：配平定义迁进共享纯表，三枚 token 级对比度断言一字未动）；日期眉标不搬 | `a008d23` |
| 24 | `sMeHome` | 「关于与许可」经注入了设置内容的「外观与关于」组到达，补宿主级行为判据；第三格保持「文章」、页头 eyebrow 不搬（反向钉住） | 「我的词汇」三行继续不搬（词库四格是唯一出口）；页面无生产源码改动 | `cc25f19` |
| 25 | `sMeWords` | 核完：分段计数、收藏段说明、页尾提示条、页头搜索裁决均已在 22-C 落地；重核另两条说明仍不成立（新词顺序只由词书内容定；错词按下次复习时间排、答对一次即移出） | 分段几何（原型 pill + 描边 vs 本仓三档容器语言）与「错过 N 次」登记为偏离；无生产源码改动 | `2089e94` |

### 2.3 文档与判据

- 新增 `app/src/test/java/com/example/englishlearning/ui/Batch3AiScreensContractTest.kt`（7 条，一一对应 #17–#23；
  含 #20 的反向钉与 #23 的禁用配方钉）。
- `AiProfileLayoutContractTest` **期望值变化（非放宽）**：编辑器 `SoftCard(` 3 → 4、`softCardBodies` 组数 3 → 4，
  测试名改为 `…into three soft cards plus the unsupported fourth`；13 个可写控件的组归属断言一字未减，
  并新增两条反向断言（四枚 `unsupported` tag 在 groups[3]；13 个真控件 tag 不许混进 groups[3]）。
- 计划文档新增「Task 22-I」台账（14 行，均带判据），对账表 #17–#23 翻 ✅。
- 同日后半段续加台账：22-J（#9，5 行）、22-K（#13/#14，9 行）、22-L（#12，9 行）、
  22-M（#24，5 行）、22-N（#25，8 行）；对账表 #9/#12/#13/#14/#24/#25 全部翻 ✅。
- **小计按文档命令实跑重算：✅ 36 屏 · 🟡 0 屏 · ⬜ 0 屏**（`sed | grep | awk | sort | uniq -c` 实得 36 ✅）。

## 3. 验证记录

### 3.1 TDD

- 批 3 新契约先跑红：`7 tests completed, 6 failed`（仅 #20 的反向钉当时已成立）。
- 实现后：`Batch3AiScreensContractTest` + `AiProfileLayoutContractTest` 全绿。

### 3.2 全量门禁

`gradle --offline assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin` → **BUILD SUCCESSFUL**（61 tasks）。
JVM 结果按 XML 聚合实跑：**tests 2212 · failures 0 · errors 0 · skipped 0**（批 3 收口时）。

同日后半段（#9/#12/#13/#14/#24/#25）收口后**重跑整条门禁**：`BUILD SUCCESSFUL`；
JVM 按同一脚本聚合：**tests 2221 · failures 0 · errors 0 · skipped 0**（+9 条：22-K/22-L/22-M 新增契约）。

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
| `ReadingAccessScreenTest`（#12 收口后） | OK (18 tests) |
| `AppScreenTest`（#12 收口后，宿主挂载） | OK (57 tests) |
| `AppScreenTest#theAboutRowOnTheProfilePageOpensTheAboutScreenAndComesBack`（#24 新增） | OK (1 test) |
| `WordBankScreenTest`（#25） | OK (4 tests) |
| `MyWordsScreenTest`（#25） | OK (17 tests) |

`AppScreenOverlayContainerTest` 那 3 条失败是**环境前置缺失**（断言信息原文：「fontScale=2.0 未落进对话框窗口…
connected 侧需先把设备 font_scale 置 2.0，护栏不许在 scale 1.0 下空转」），不是本次改动回归。

## 4. Git 状态

- 分支：`stage-5-ui-r2-learning-path`
- 最近提交：`2089e94`（#25）、`cc25f19`（#24）、`a008d23`（#12）、`f94e57b`（#13/#14）、
  `dfdc647`（#9），此前 `ecbc136`（批 3）、`dcac0f2`、`da3b058`、`6332283`、`d1e7b8e`、`c6ba954`
- **尚未推送**：按用户要求，推送前先请示。
- 工作树：本报告提交后应为干净。

## 5. 未完成 / 遗留问题

逐屏对账已 **36/36 ✅**（🟡 与 ⬜ 均清零，小计按计划文档命令实跑）。仍未做的是**批 4 全量 QA**——
按计划「批次」表要整体重跑深浅两套、`fontScale 2.0`、TalkBack、动效减弱与真机 `bf353dda` 目视，
本轮只做了逐屏级验证（各屏 connected 套件 + JVM 契约）。

登记在案、明确不搬的项（理由见各台账行，不是遗漏）：

- #12：「继续」组双语对照独立入口（对照态在阅读器内切换）、日期眉标；
- #13/#14：`.prose` 逐段交错（数据无段落结构）、译文块容器；
- #24：「我的词汇」三行（词库四格是唯一出口）、原型「收藏」第三格、页头 eyebrow；
- #25：生词本 / 易错两段说明（本仓不成立）、「错过 N 次」（无次数列）、页头搜索（查词已有两处入口）；
- #25 分段几何：原型 `.seg` 是 pill + 1dp 描边，本仓取三档容器语言（词库 13/10、设置主题行 14/11）；
  要对齐须两处一起改，属跨屏改动，单独立项。

另有两项非阻塞遗留：

- 「关于」页 `licenseCount` 仍传 `null`（行文案退成不带数字的短语），可从 `LicensesScreen` 同一份 markdown 注入；
- 批 3 的 #18 把原型三档（角色 / 英文 / 中文）映射到 `BareRow` 的 leading / title / subtitle，
  而 `BareRow` 的 subtitle 上限是 2 行，长解释会省略；改它会牵动共享件几何契约，须单独立项。

## 6. 下一任接手须知

1. 先读本报告 + 计划文档的「Task 22-I ~ 22-N」台账与对账表（36/36 ✅）；下一步是批 4 全量 QA，
   不是继续逐屏对齐。
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
