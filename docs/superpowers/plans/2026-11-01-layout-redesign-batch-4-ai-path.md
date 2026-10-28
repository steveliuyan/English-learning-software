# 全应用版式重做 · 批 4 AI 链路 实施计划

> **For agentic workers:** 本仓按 `AGENTS.md` + 分阶段 Spec 推进；本计划逐条落地
> `docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md` §6「批 4 AI 链路」。
> 口径与批 1／批 2／批 3 计划逐条对齐（`2026-10-25-layout-redesign-batch-1-skeleton.md`、
> `2026-10-30-layout-redesign-batch-2-learning-path.md`、`2026-10-31-layout-redesign-batch-3-reading-path.md`）。

**Goal:** 把 AI 链路七屏（AI 主页、未上线骨架页、AI 服务、拍照翻译、长难句、AI 生图、AI 问词）
从「M3 默认主题 + 自写返回按钮 + 卡片汤 + `Mint*` 静态桥」推到批 1 已立的骨架：`AppType` 六档排印、
`appColors()` / `appDomain()` 令牌、`AppScreenHeader` 共享页头、`StrongCard`/`SoftCard`/`BareRow` 三档容器、
`EntryRow` 折叠件、危险动作带确认与危险墨。同批收掉 spec §5 的七条 AI 逐屏要求
（中性副标题／两按钮上移／输入三组＋保存吸底＋确认／历史一行＋模型折叠／复制朗读＋费用句一次／
描述可编辑／按钮分主次＋回答上移）与 spec §4.6 的两条 AI 侧状态口径（失败可行动、危险动作确认）。

**Architecture:** 不新增数据通路、不改 Room/FSRS/备份格式/Keystore、不改 AI 请求体与出站确认策略。
七屏各做一次「换装 + 路由微调」：页头收编到 `AppScreenHeader`、容器换三档、排印换六档、
`Mint*` 静态桥清零、碎片块收成折叠行、主操作定位到吸底条或首位、危险动作改发回调由宿主确认。
新增 7 枚屏级 JVM 源码契约（`*LayoutContractTest`）先红后绿；四个棘轮在同一批改登记值：
页头族 seed 摘 6 行（7 → 1）、容器族屏级表加 7 行（12 → 19）、裸 hex 与深色静态读保持只减不增。

**Tech Stack:** Kotlin / Jetpack Compose / Material 3；JVM 源码契约测试；本机 Gradle 8.13
（`$HOME/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle.bat`，**禁用 `./gradlew` 与 9.x**）。

---

## Global Constraints

- **改期望值，不放宽判据。** 既有契约因本次改动变红时，只许把钉住的现值换成新值（tag 名、文案、
  计数、集合）；不许删断言、不许 `assertTrue(true)`、不许放宽到 `>=`。删掉一枚判据必须在同一条提交里
  写明它被哪条新判据替代。**本批会删的只有「旧返回按钮的文案与专属 tag」这一族**（6 屏各 1 处），
  每一处都要配一枚「旧文案必须为 0」的正向断言（净增判据，不是净减）。
- **TDD 先红。** 每个 Task 的 Step 1 写测试，Step 2 必须实跑并看到**预期的那条红**（贴失败消息），
  再进 Step 3。同一 Task 内若测试与实现同轮落地，必须按批 2 Task 4 的先例登记工序偏差并给补偿证据。
- **门禁口径**（每个 Task 收尾）：
  ```powershell
  $g = Get-ChildItem -Path "$HOME\.gradle\wrapper\dists\gradle-8.13-bin" -Recurse -Filter gradle.bat | Select-Object -First 1
  & $g.FullName :app:testDebugUnitTest --tests "com.example.englishlearning.ui.<TestClass>" --offline --no-daemon --no-build-cache
  ```
  Task 8 全量门禁：
  ```powershell
  & $g.FullName :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin `
      verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
  ```
  基线：批 3 收口 `1906 tests / 0 failures`。本批预计净增 ≥ 49 条（7 枚契约 × 7 条）+ connected 新增若干，
  末值写进报告并解释差额（只增不减；有减必须逐条说明被哪条替代）。
- **数据与安全零改动。** 不碰 Room 表、FSRS、备份格式、Keystore、AI 请求体、ViewModel 状态机。
  本批只动 `ui/` 呈现层 + 屏内私有 Composable + `AppScreen.kt` 的七个挂载分支（仅加回调/换 token）。
  **唯一例外**见「与规范的六处对账」第 6 条（AI 生图的描述可编辑），且该例外须评审席照准后才执行。
- **真机红线。** 真机 `bf353dda` 只做 `adb install -r` + 手工目视，**永不跑 instrument**；
  connected 只在 `emulator-5574` 用 `am instrument`，**禁 `connectedDebugAndroidTest`**，**禁清数据**。
- **共享工作树。** 本机无可用 `git`：Task 各「登记」步骤一律记为未执行，以本文件 + `docs/handoff/`
  报告作为权威账。
- **大字号不退化。** 七屏在 `fontScale = 2f` 下必须可滚、可点；吸底操作条不得遮内容；
  折叠件展开后新增内容必须全部可达（`verticalScroll` 已有，重点是吸底条与折叠件组合）。
- **一屏一个主操作。** 吸底条只放主 CTA 与它的次操作，不塞状态说明。
  AI 服务＝「保存」；长难句＝「分析句子」；AI 生图＝「生成画面描述／生成图片」（同一位、随状态换身份，
  见对账第 6 条）；AI 问词＝「拆词义」（实底），其余三个问法降文字按钮。
- **编码红线。** 源文件 LF + UTF-8 无 BOM；PowerShell 5.1 读文件一律带 `-Encoding UTF8`
  （不带会吞行并把中文读坏，批 3 实测 `ReadingFeedScreen.kt` 143 行读成 129 行）。
  行数、行号以 `read` 工具 / `-Encoding UTF8` 为准。

---

## 现场事实（2026-11-01 取证，改代码前必须据以核对）

七屏行数以 `read` 工具为准（`AiLearningScreen.kt` 383 / `AiFeatureScreen.kt` 202 /
`AiProfileSettingsScreen.kt` 629 / `PhotoTranslationScreen.kt` 760 / `SentenceAnalysisScreen.kt` 249 /
`ImageStudioScreen.kt` 295 / `WordAiQaScreen.kt` 306，合计 2824 行）。

| 屏 | 文件（行数） | 现状要点 |
|---|---|---|
| AI 主页 | `AiLearningScreen.kt`（383） | **tab 根页，无返回位**；根 `Column` + `verticalScroll`(:106)；头卡 `AiHeaderCard`(:155) 是品牌渐变 + `AiMascot` 140dp + `Color.White.copy(alpha=0.85f)` 状态胶囊（:199-213，**两枚静态墨**受 `DarkTokenSeedAllowlist["AiLearningScreen.kt"]=2` 与 `AiBadgeContrastFixTest` 逐行钉）；副标题「用你学过的词，生成专属的阅读与练习」(:191)；能力行 `AiFeatureRow` 是 M3 `Card` + 0.5dp 描边(:221-227)；「查看词表」是 `AiNavigationCard`(:276) 内的**渐变描边套卡 + 「查看详情」药片**(:294-324)，跳 `onOpenWordList`；`Mint*` 桥 **35 处**、`MaterialTheme.typography` **12 处**、`AppType` **0 处**、`appColors()` **3 处** |
| 未上线骨架页 | `AiFeatureScreen.kt`（202） | 自写返回 `TextButton("← 返回 AI 学")` + `testTag("ai_feature_back")` + CD「返回 AI 学」(:85-91)；`headlineSmall` 标题(:108-114)；`SectionCard`(`Card`+描边, :169-177) 装「当前进度」；`StatusPill`(:179-201) 两档前景按 `LocalIsDark` 分档（`AiBadgeContrastFixTest` 钉 `StatusPill(` 作用域）；「现在可以做的」是 `MintTint` 底 `Card`(:128-164)，两枚 `Button` 实底/描边**排在说明文字之下**；`Mint*` 桥 **36 处**、`typography` **6 处**、`AppType` **0 处** |
| AI 服务 | `AiProfileSettingsScreen.kt`（629） | **已 token 化**（`AppType` 30 处、`appColors()` 37 处、`Mint*` 仅 4 处、`MaterialTheme.typography` 0）；列表页 `AiProfileList`(:105) `verticalScroll`(:117) + 行级 `AiProfileRow`(:185)；编辑器 `AiProfileEditor`(:266) 里 **`SectionCard` ×5**：基础 3 字段（名称/Endpoint/模型，:306-327）· 能力开关组（:329-342）· 语音角色 pill（Speech 能力开时才出，:344-361）· 高级参数 4 字段（:363-382）· 密钥 2 段（状态行 + API Key + 清除密钥，:384-417）；保存 `PillButton`(:431-442) **在流末尾非吸底**；「清除已保存的密钥」(:408-416) 与「删除这套配置」(:444+) **直连回调、无确认**（宿主 `AppScreen.kt:1254/1255` 直接 `deleteProfile`/`deleteKey`）；编辑器返回是 `PillButton("← 返回 AI 服务", PillStyle.Text)`(:290-296)，无 `AppScreenHeader` |
| 拍照翻译 | `PhotoTranslationScreen.kt`（760） | 自写返回 `TextButton("← 返回 AI 学")` + `photo_translation_back` + CD「返回 AI 学」(:156-162)；`headlineSmall` 标题(:164)；**常驻（无输入也占屏）5 块**：历史卡 `SavedHistoryList`(:558-604，18dp 圆角 + 描边 + 逐条删除按钮 :597 **无确认**) · 离线模型卡 `OfflineModelCard`(:606-673，常驻展开) · 隐私说明(:359-364) · 成本句(:309-314) · 引擎标签(:263-270)；拍照/相册两枚并排按钮(:167-207)；识别文本编辑区(:272-315)；结果对比卡(:458-524) + 保存行(:526-556)；`Mint*` 桥 **56 处**、`typography` **19 处**、`AppType` **2 处**；出站确认 `GlassDialog`(:675-717) 四枚 tag 归 `DangerInkPlacementContractTest` |
| 长难句 | `SentenceAnalysisScreen.kt`（249） | 自写返回 `TextButton("← 返回 AI 学")` + `sentence_analysis_back`(:80-86)；`headlineSmall` 标题(:88)；输入框 + 「分析句子」实底按钮(:102-117) **都在流里**；成本句(:118-123) 与出站确认文案「并可能产生费用」(:183) **同一件事讲两次**；结果 `SegmentList`(:146-176) 是逐段 `Card`+描边，**无复制、无朗读**（无 `onSpeak` 形参）；`Mint*` 桥 **24 处**、`typography` **4 处**、`AppType` **2 处** |
| AI 生图 | `ImageStudioScreen.kt`（295） | 自写返回 `TextButton("← 返回 AI 学")` + `image_studio_back`(:85-91)；`headlineSmall` 标题(:93)；主题输入(:96-106) → 「生成画面描述」(:107-122) → `PromptCard`(:160-193) **只读展示 prompt** + 「生成图片」按钮；`PromptReady(prompt)` 由 VM 直接给出，`generateImage()` **无入参**、`pendingPrompt` 是 VM 私有（`ImageStudioViewModel.kt:68/77-83`）；`Mint*` 桥 **27 处**、`typography` **5 处**、`AppType` **2 处** |
| AI 问词 | `WordAiQaScreen.kt`（306） | 自写返回 `Button("← 返回上一层")` + `word_qa_back`(:85-94)；`headlineSmall`「AI 问词」(:96)；**顺序是「我的笔记 → 已保存笔记 → 三个问法 chip → 回答」**，回答在屏末需滚动(:138-158)；`WordQaKind` 四档 `Sentence/Breakdown/Mnemonic/Personal`（`wordqa/WordQaPromptPolicy.kt` 枚举），屏侧只渲染前三档(:134-136)，**三枚都是同款实底 `Button`**(:163-180) 无主次；`Mint*` 桥 **32 处**、`typography` **1 处**、`AppType` **4 处** |

**开工前 Step 0 复核项**（每条都动到既有钉值，必须先跑基线再改，红绿状态记进报告）

1. `AiLearningScreenTest` / `AiFeatureScreenTest` 里是否逐字钉住头卡副标题、`ai_learning_nav_detail`、
   `ai_feature_open_settings|open_learning` 的文案与顺序。
2. `AiBadgeContrastFixTest` 12 条（`AiHeaderCard(` / `StatusPill(` 作用域正则、白胶囊两枚静态墨、
   品牌渐变静态 brush、`DarkTokenSeedAllowlist` 读值）在本批的存活情况——**本批不碰这两段的取墨形状**，
   目标是把 12 条跑成全绿并写进报告。
3. `AiProfileSettingsScreenTest` 是否钉住 `SectionCard` 数量、字段顺序、`ai_profile_save` 位置。
4. `PhotoTranslationScreenTest` **不存在**（androidTest 侧无该类，只有 JVM 侧 VM/Parser/PromptPolicy 用例）
   → 本批须新建一枚最小 connected 类（见 Task 4 Step 5）。
5. `AppScreenOverlayContainerContractTest` 的 `ConfirmDestructiveDialog(` 计数（`:331` 全文件 4 枚、
   `:341` AppScreen 主体 3 枚、`:386` 路径 A 取样 4）与「9 参数」签名窗口（`:543-571`）的现行形态。
6. 宿主挂载：`AppScreen.kt:1246`（AI 服务）、`:1350`（长难句）、`:1368`（拍照翻译）、`:1413`（生图）、
   `:1425`（骨架页）、`:1092`/`:1641`（AI 问词两处挂载）、`:1722`（AI 主页 tab）的形参表与会话态。

**受影响的既有闸（改前先读、改时同步改登记值）**

| 闸 | 现状 | 本批动作 |
|---|---|---|
| `SharedComponentUniquenessRatchetTest` | 页头族 seed **7 枚**：`AiFeatureScreen.kt`(:225) / `AiLearningScreen.kt`(:226) / `ImageStudioScreen.kt`(:247) / **`LicensesScreen.kt`(:248，属批 5)** / `PhotoTranslationScreen.kt`(:249) / `SentenceAnalysisScreen.kt`(:253) / `WordAiQaScreen.kt`(:254) | 六屏迁共享页头后**同批摘六行**（剩 1 枚 `LicensesScreen.kt`）；摘行门 `removing_a_file_from_the_header_seed_requires_its_headline_reads_to_be_cleared`(:595-613) 要求该文件 `MaterialTheme.typography.headline` 读点归零；`each_family_offender_set_equals_its_seed_exactly`(:416) 双向判（多摘也红）。**同批重写 `headerSeedRetentionReason`(:36-40)**：现文写「六屏需先扩组件 API」，本批按批 3 先例（固定「返回」+ `app_screen_header`）迁移，该段必须改写为「已按批 3 口径迁移完毕，仅剩 Licenses 一枚」 |
| `ContainerContractTest` | 屏级表 **12 屏**（`:27-40`，批 3 收口值） | 七屏全部纳入（→ 19 屏），判据不变（`StrongCard(` ≤ 1） |
| `RawColorLiteralRatchetTest` | `RawColorSeedAllowlist` 仅 `AiMascot.kt to 1`(:74)；七屏裸 hex 额度 0 | 保持 0（七屏现有 `Color.White.copy(...)` 不是裸 hex，不计入；**白胶囊不动**） |
| `DarkTokenConsumptionRatchetTest` | `DarkTokenSeedAllowlist["AiLearningScreen.kt"] = 2`(:87)（白胶囊两枚静态墨，R-23.14）；`["AiProfileSettingsScreen.kt"] = 1`(:79)，玻璃合成底另有一张同名表 `= 1`(:155) | `AiLearningScreen` **保持 2**（本批不动白胶囊）；`AiProfileSettingsScreen` 若 `SectionCard` 换装后静态读归零，则**同批摘行**（只减不增）；七屏其余文件不得新增 seed |
| `AppScreenOverlayContainerContractTest` | `ConfirmDestructiveDialog(` 全文件 **4 枚**(:331)、AppScreen 主体 **3 枚**(:341)、路径 A 取样 **4**(:376/:386)；`PillButton(` 在 AppScreen **恰 3 枚**(:536) | 新增三条危险确认（清除密钥 / 删除配置 / 删除拍照翻译历史）→ 4 → **7**、主体 3 → **6**、路径 A 取样 4 → **7**；新增站点必须落在「屏内零对话框 + 宿主持 `ConfirmDestructiveDialog`」的既有形状上；`PillButton(` 计数不变（确认框自带药片，不新增手搓按钮） |
| `DangerInkPlacementContractTest` | 四屏出站确认药片对（`image_studio_*` / `photo_translation_*` / `sentence_analysis_*` / `word_qa_*`，:33-36）+ `AiProfileSettingsScreen` 的 Danger 墨点计数(:41-61) | 出站确认四对**逐字不动**；`AiProfileSettingsScreen` 的 Danger 墨点随「清除密钥/删除配置」移出屏内而变，按新值同步（不放宽：仍要求「恰 N 处，多一少一都红」） |
| `AiBadgeContrastFixTest` | 12 条，钉 `AiHeaderCard(` / `StatusPill(` 作用域、白胶囊静态墨、`MintPrimary` 两档前景、品牌渐变静态 brush | 本批**不改这两段的取墨与几何形状**，12 条必须全绿；若换装连带把 `MintPrimary` 壳换成域 token，则同批改钉值并在报告登记 |
| `ConfirmDestructiveWordingTest` | 钉危险确认文案「写明后果」口径（`OutboundConfirmDialog` 收 5 份出站确认） | 新增三条确认文案必须过本闸；出站确认五份不动 |
| `AccessibilityColorTokenContractTest` | `:34` 把 `AiFeatureScreen.kt:87`（单字徽标白字压渐变）登记为携带修复 | 该徽标在 Task 2 换装时保持「渐变底 + `onBrand` 前景」形状，登记不失效 |
| `AccessibilityLabelContractTest` | 禁令 1a：`contentDescription` 字面量必须含中文；AI 能力行 CD `"${feature.title}，状态：$status"`(:232) | 能力行改 `BareRow`/`SoftCard` 时该 CD 形状逐字保留（`，状态：` 引导词不许删） |
| `SearchEntryWiringTest` | `openSearch(` 全文件**恰 3 处**（主页 / 阅读页 / 拍照翻译） | 拍照翻译的查词入口不动，不得多出第四个 |
| `AiFeatureTest` | `:122-129` 钉「`AiFeatureScreen.kt` 不得留徽章文案字面量」（必须走 `featureStatusBadge`） | 保持；骨架页重排不得内联手写徽章文案 |
| `MaterialThemeAssemblyContractTest` | `MaterialTheme(` 装配点唯一；六档 `AppType` metrics 与墨锁定 | 本批不改主题文件 |

**connected 侧必须同步改期望值的地方**（改的是锚点，不是判据强弱）

| 用例 | 现钉 | 改后 |
|---|---|---|
| `AiFeatureScreenTest` | `onNodeWithTag("ai_feature_back")` + 文案「← 返回 AI 学」+ CD「返回 AI 学」 | 页头返回位：`app_screen_header` 在 + `contentDescription("返回")` 可点 → 回调恰 1 次 + 「← 返回 AI 学」必须 0（加严） |
| `SentenceAnalysisScreenTest` / `ImageStudioScreenTest` / `WordAiQaScreenTest` / `PhotoTranslationScreenTest`（新） | 各自的 `*_back` tag + 「← 返回 AI 学」/「← 返回上一层」 | 同上口径；旧文案为 0 的正向断言逐屏补 |
| `AiProfileSettingsScreenTest` | 编辑器返回 `PillButton("← 返回 AI 服务")` + `ai_profile_save` 在流内 | 返回位改共享页头（CD「返回」）；`ai_profile_save` 移到吸底条但仍可点；新增「删除配置 / 清除密钥点击后先出确认框、取消则不发回调」两条 |
| `WordAiQaScreenTest` | 三枚 `word_qa_chip_*` 同款实底；回答在屏末 | 三枚 tag 全保留；断言「`Breakdown` 是唯一实底主操作」「其余两枚为文字按钮」「回答节点在问法之前」（净增三条） |
| `AiLearningScreenTest` | `ai_learning_nav_detail` 在渐变套卡内 | 「查看词表」改直达词库：断言「点击后到达词库入口回调」+ 旧套卡 tag 退役并正向钉 0 |
| `PhotoTranslationScreenTest`（新建） | 无 | 四条最小集：①历史折叠行默认收起、点开后 `photo_translation_history_<id>` 可达 ②离线模型默认收起、点开后 `photo_translation_model_card` 可达 ③历史删除先出确认、取消不发回调（**落地时移入 `AppScreenTest` 的宿主用例**）④页头返回位 CD「返回」。**实际落地 8 + 1 条**，见 Task 4 验收段 |

---

## 与规范的六处对账（先把口径钉死，避免执行期各屏各自解释）

1. **AI 主页「副标题改中性一句」→ 换成不含承诺的事实句。**
   现文「用你学过的词，生成专属的阅读与练习」承诺了「生成」，而 AI 调用尚未全通。
   新句：「这里按真实进度列出 AI 能力：能用的直接进，没写完的写明差什么。」（中性、不承诺生成）。
   文案改动须同步 `AiLearningScreenTest` 里钉住旧句的断言（改期望值，不删断言）。
2. **AI 主页「『查看词表』直达词库」→ 去掉中间那层渐变套卡，行尾直达。**
   现状是「渐变描边套卡 → 说明文字 → 查看详情药片」三层。落法：`AiNavigationCard` 收成一条
   `BareRow`（图标 + 「查看词表」+ 词数副标题 + `ChevronRight`），点击直接走 `onOpenWordList`；
   `ai_learning_word_list_count` 与 `ai_learning_nav_detail` 两枚 tag 里**保留计数 tag、退役套卡 tag**，
   并正向钉「渐变套卡形状为 0」。宿主侧若 `onOpenWordList` 现在去的是「详情页」而非词库，
   本批把它改挂到词库 tab 的既有入口回调（`AppScreen.kt:1722` 一处），属路由对齐、不改数据。
3. **AI 服务「14 个输入分 3 组」→ 按可交互控件口径重算为 13 枚，分 3 组。**
   现盘：字段 3（名称/Endpoint/模型）+ 能力开关 4 + 语音角色 1 + 高级参数 4 + API Key 1 = **13**；
   规范写 14，差的一枚是「清除已保存的密钥」这个动作按钮（它不是输入）。
   分组落法：① **服务与模型**（名称 / Endpoint / 模型 / 能力四开关）② **语音**（语音角色，仅 `Speech`
   能力开启时出现）③ **高级与密钥**（`temperature` / `top_p` / `max_tokens` / `timeout_seconds` /
   API Key / 清除密钥）。`SectionCard` 5 枚收成 3 枚 `SoftCard`；字段标签、tag、校验文案一字不改。
   **保存按钮固定底部**：`PillButton` 进吸底条（`Surface` + `SoftCard` 之上的吸底容器），
   流内不再出现第二枚保存入口。
4. **「清空密钥 / 删除配置加确认」+「拍照翻译历史删除加确认」→ 屏内零对话框，全部发回调给宿主。**
   屏侧把 `onDeleteKey(profileId)` / `onDeleteProfile(profileId)` / `onDeleteSaved(recordId)`
   的**点击改成先置一个 `pendingDelete*` 局部态**，由宿主 `AppScreen.kt` 用既有的
   `ConfirmDestructiveDialog` 承载（路径 A 形状，判据口径见闸表）。文案须写明后果
   （「删除后这套配置与本机保存的密钥都会消失，不能撤回」/「删除后这条记录与本机图片都会消失」）。
   `AppScreenOverlayContainerContractTest` 三处计数同批 4 → 7。
5. **拍照翻译「常驻 5 块收成『历史』一行 + 『离线模型』折叠」→ 明确这 5 块与收法。**
   5 块＝历史卡 · 离线模型卡 · 隐私说明 · 成本句 · 引擎标签。落法：
   ① 历史卡 → `EntryRow` 折叠行「历史（N 条）」，展开后逐条 `BareRow`（删除按钮进行尾危险色）；
   ② 离线模型卡 → `EntryRow` 折叠行「离线备用模型」，展开后才渲染安装/卸载/进度；
   ③ 隐私说明收进「离线模型」折叠体内一句；④ 成本句与 ⑤ 引擎标签各只留一处，
   进主流程的 `Footnote` 小字（成本句贴在「翻译成中文」按钮下方一行）。
   主流程顺序：页头 → 拍照/相册 → 识别文本编辑 → 「翻译成中文」→ 结果 → 「保存到本机」→ 历史行 → 模型行。
6. **长难句「结果卡补复制/朗读」与「费用句只讲一次」；AI 生图「画面描述可编辑」。**
   - 长难句：结果区补两枚 `PillButton`（`PillStyle.Text`）「复制译文」/「朗读原文」。
     复制走 `LocalClipboardManager`（零新依赖）；朗读**复用宿主既有 TTS 通路**：给
     `SentenceAnalysisScreen` 增两个形参 `speakMessage: String?` + `onSpeak: (String) -> Unit`，
     宿主按 `PhotoTranslationScreen` 的既有写法（`AppScreen.kt:1364-1396`：`PronunciationStatus` 状态 +
     `pronunciationProvider.speakSentence(...)`）接线——**ViewModel 零改动**，只走呈现层形参直通。
     费用句只在成本那行说一次；出站确认文案里的「并可能产生费用」按 `ConfirmDestructiveWordingTest`
     口径保留（那是安全提示，不是营销句），但在报告里登记「同一事实两处出现」的判读。
   - AI 生图：`PromptReady(prompt)` 的 prompt 改为**屏侧本地可编辑副本**（`rememberSaveable` 初值取
     `state.prompt`），textarea 编辑后点「生成图片」把编辑文本交给 VM。
     这需要 VM 侧一个签名增参：`fun generateImage(promptOverride: String? = null)`
     （`pendingPrompt` 私有，外部无法覆盖）。**这是本批唯一的 ViewModel 改动**，性质是
     「请求载荷文本直通」——`runImage(prompt, answer)` 的确认门、`AiOutboundConfirmation` 形状、
     提示词策略与拒绝理由全部不动。**执行前须评审席照准**；不照准则降级为「只读展示 + 一句话提示
     『想改描述请重新生成』」，并在报告登记为未兑现项（不做假编辑）。

---

## 文件地图

| 动作 | 文件 | 责任 |
|---|---|---|
| 改 | `ui/AiLearningScreen.kt` | tab 根页页头改 `hero = true` + 中性副标题、能力行换 `SoftCard`/`BareRow`、导航卡收一条直达裸行、`Mint*`/`typography` 清零（白胶囊与品牌渐变**一字不动**） |
| 改 | `ui/AiFeatureScreen.kt` | 页头迁共享、两枚按钮上移到「当前进度」之前、下半屏补一句轻量说明、`SectionCard` → `SoftCard`、`Mint*` 清零（`StatusPill` 形状不动） |
| 改 | `ui/AiProfileSettingsScreen.kt` | `SectionCard` 5 → 3 组 `SoftCard`、保存吸底、清除密钥/删除配置改发回调、编辑器与列表页页头迁共享 |
| 改 | `ui/PhotoTranslationScreen.kt` | 页头迁共享、历史→折叠行、离线模型→折叠行、成本句/引擎标签各留一处、历史删除改回调、`Mint*`/`typography` 清零 |
| 改 | `ui/SentenceAnalysisScreen.kt` | 页头迁共享、结果区补「复制译文」/「朗读原文」、费用句只留一处、`Mint*` 清零、新增 `onSpeak`/`speakMessage` 形参 |
| 改 | `ui/ImageStudioScreen.kt` | 页头迁共享、`PromptCard` 改可编辑描述 + 生成按钮吸底、`Mint*` 清零（含 VM 增参，见对账 6） |
| 改 | `ui/WordAiQaScreen.kt` | 页头迁共享、三问法分主次（`Breakdown` 实底 + 两枚文字）、回答上移到问法之前、`Mint*` 清零 |
| 改 | `ui/AppScreen.kt` | 七个挂载分支：三处危险确认接 `ConfirmDestructiveDialog`、长难句接 TTS 状态、生图透传描述、AI 主页导航指向词库 |
| 改 | `ui/ImageStudioViewModel.kt` | 仅 `generateImage(promptOverride: String? = null)` 一处签名增参（对账 6，须照准） |
| 新 | `test/.../ui/AiLearningLayoutContractTest.kt` | AI 主页新骨架源码契约（7 条） |
| 新 | `test/.../ui/AiFeatureLayoutContractTest.kt` | 骨架页新骨架源码契约（7 条） |
| 新 | `test/.../ui/AiProfileLayoutContractTest.kt` | AI 服务新骨架源码契约（7 条） |
| 新 | `test/.../ui/PhotoTranslationLayoutContractTest.kt` | 拍照翻译新骨架源码契约（7 条） |
| 新 | `test/.../ui/SentenceAnalysisLayoutContractTest.kt` | 长难句新骨架源码契约（7 条） |
| 新 | `test/.../ui/ImageStudioLayoutContractTest.kt` | AI 生图新骨架源码契约（7 条） |
| 新 | `test/.../ui/WordQaLayoutContractTest.kt` | AI 问词新骨架源码契约（7 条） |
| 新 | `androidTest/.../ui/PhotoTranslationScreenTest.kt` | 折叠/确认/页头四条最小 connected 基线（现盘无此类） |
| 改 | `test/.../ui/SharedComponentUniquenessRatchetTest.kt` | 页头族 seed 摘六行 + 重写 `headerSeedRetentionReason` |
| 改 | `test/.../ui/ContainerContractTest.kt` | 屏级表 12 → 19 屏（实测落位：Task 3 后 15 屏；Task 4 的拍照翻译在 Task 5 补入、Task 5 长难句同批入表 → 17 屏，余两屏随 Task 6/7 逐屏加行） |
| 改 | `test/.../ui/AppScreenOverlayContainerContractTest.kt` | `ConfirmDestructiveDialog(` 4 → 7、主体 3 → 6、路径 A 取样 4 → 7 |
| 改 | `test/.../ui/DangerInkPlacementContractTest.kt` | `AiProfileSettingsScreen` Danger 墨点计数随确认框外迁同步 |
| 改 | `androidTest/.../ui/{AiLearning,AiFeature,AiProfile,SentenceAnalysis,ImageStudio,WordAiQa}ScreenTest.kt` | 上表 connected 期望值同步（编译必须过，运行走模拟器）。实测：`SentenceAnalysisScreenTest` 7 → **9 条**（Task 5） |

---

### Task 1: AI 主页重排（`AiLearningScreen.kt`）

- [x] **Step 1: 写契约 `AiLearningLayoutContractTest`（7 条，先红）**

  1. `AppScreenHeader(` 恰一处且带 `hero = true`（tab 根页无返回位：本文件 `contentDescription = "返回"` 为 0）。
  2. 副标题为中性事实句：钉全句字面量，并正向钉旧句「用你学过的词，生成专属的阅读与练习」为 **0**。
  3. 「查看词表」是 `BareRow`（或 `SoftCard` 内单条裸行），钉 `ChevronRight` 在本文件出现、且渐变描边套卡形状
     （`border(2.dp` 或 `Brush.linearGradient` 包 `MintSurface` 的双层 Box）为 **0**。
  4. `ai_learning_word_list_count` 保留恰 1 处；`ai_learning_nav_detail` 退役（为 0）。
  5. `Mint*` 桥读点为 **0**（`MintBackground|MintSurface|MintOutline|MintTint|MintPrimaryDark|MintTextMuted|MintPrimary`
     全族正则）；`MaterialTheme.typography` 为 **0**；`AppType.` ≥ 8。
  6. 白胶囊与品牌渐变不动：`Color.White.copy(alpha = 0.85f)` 恰 1 处、`AiHeaderGradient` 仍是顶层 `private val`
     静态 brush、`DomainColors.AiSpeech.deep` 与 `AppPalette.TextSecondary` 两枚静态墨各恰 1 处
     （与 `DarkTokenSeedAllowlist = 2` 同口径）。
  7. 一屏一个主操作：`Button(` / `PillButton(` 合计 ≤ 1（导航收成裸行后主页不应再有实底主操作）。

- [x] **Step 2: 实跑看到预期红**

  `--tests "com.example.englishlearning.ui.AiLearningLayoutContractTest"`，贴 6–7 条红（第 6 条应**先绿**，
  它是「不许顺手改坏」的护栏；报告里要点明哪条设计上先绿）。

- [x] **Step 3: 实现**

  1. 页头：`AppScreenHeader(title = "AI 学", hero = true, eyebrow = 今日日期或「AI 能力」, badge = 状态胶囊)`
     ——若把白胶囊挪进 `badge` 槽会破 `AiBadgeContrastFixTest` 的 `AiHeaderCard(` 作用域正则，**默认不挪**：
     页头只出标题与 eyebrow，头卡保留 mascot + 状态胶囊。
  2. 副标题按对账 1 换句。
  3. `AiFeatureRow` 的 M3 `Card` + 描边 → `SoftCard`（域 `AiSpeech`），图标位用 `InlineIconBadge`（`AppIcon.Spark`），
     `contentDescription = "${feature.title}，状态：$status"` 逐字保留。
  4. `AiNavigationCard` → 一条 `BareRow`（`AppIcon.Books` 或 `BookOpen` 的插画徽标 + 标题「查看词表」+
     副标题 `wordListLine(...)` + `ChevronRight`），点击 `onOpenWordList`。
  5. `AiExplainer` 换 `SoftCard` + `EntryRow` 折叠（与未上线折叠行同形）。
  6. 令牌：`MintBackground` → `colors.background`、`MintSurface` → `colors.surface`、`MintPrimaryDark` → `colors.text1`、
     `MintTextMuted` → `colors.text2`、`MintOutline` → `colors.outlineSoft`、`MintTint` → `colors.tint`。

- [x] **Step 4: 单测绿 + 同批复跑护栏**

  `AiLearningLayoutContractTest`（7/7）+ `AiBadgeContrastFixTest`（12/12）+ `SharedComponentUniquenessRatchetTest`
  （seed 摘 `AiLearningScreen.kt` 一行，`headline` 读点归零）。

- [x] **Step 5: 登记**（`git` 不可用 → 记未执行）

- [x] **Step 3 落地时登记的计划偏差（四处在 Step 1 判据上、一处在命名上）**

  1. **Step 3.1 的 `AppScreenHeader(title, hero = true, eyebrow, badge)` 没有采用**：本屏 hero 整块由
     `AiHeaderCard` 承担（`ai_learning_header`），文件内 `AppScreenHeader(` 计数钉为 **0**。
     理由是两枚 28sp 标题会撞进同一屏，且 connected 侧 `onNodeWithText("AI 学")` 会从单节点变多节点
     （`AiLearningScreenTest:58`、`AppScreenTest:502/667` 都按单节点读）。计划原文 Step 3.1 自己给的备选
     （「默认不挪胶囊」）不足以覆盖标题撞屏，故取「不调共享页头」这一支。副作用：页头族棘轮 seed 仍需摘行
     （方法见下），因为退役的是 `headlineSmall` 读点而不是 `AppScreenHeader(` 调用。
  2. **Step 1.4 的 `ai_learning_word_list_count` 由「保留恰 1 处」改为「退役」**：词数已经是裸行副标题
     （`wordListLine(...)`），再挂一枚 tag 就是同一段文字两个真值来源；connected 侧同批改成
     `assertTextContains("今日新词 12 个 · 待复习 5 个")`。新锚 `ai_learning_nav_row` 钉「恰一处 +
     `onClick = onOpenWordList` + `wordListLine(` 在同一枚调用段内」。
  3. **Step 1.5 的 `AppType.` ≥ 8 改为一一对应不变式**：本屏自画 5 行文本（头卡标题/副标题/胶囊 +
     说明条小标题/正文），实际读点也是 5，行内排印（`BareRow`/`EntryRow`/`SectionHeading`）归共享组件。
     判据写成 `Text(` 计数 == `AppType.` 读点数 + 「`Text(` 计数 > 0」防空转：**比钉死数字更紧**
     （钉 8 会让屏侧为了凑数去读多余档位，钉 4 则放过漏一行默认排印）。
  4. **Step 1.7 的 `Button(`/`PillButton(` ≤ 1 收紧为 `Button(`/`PillButton(`/`TextButton(` 全 0**：
     本屏已无实底按钮要表达主次，入口一律裸行/入口行；`TextButton(` 一并钉零是因为旧形状把它用在折叠位。
  5. **Step 3.4 的 `AppIcon.Books/BookOpen` 实取 `AppIcon.Book`**：`AppIcons` 里没有前两个名字，
     词表入口的共享 30dp 行内徽标为 `InlineIconBadge(AppIcon.Book, AppDomain.Library)`。

- [x] **同批护栏改动（三处台账）**

  - `AiBadgeContrastFixTest`：`the AI surface draws the shared spark glyph instead of a letter` 的取样面从
    「只认 `AppGlyph(`」扩为并集 `(?:AppGlyph|InlineIconBadge|EntryIconBadge)\(`。头卡的徽标从私有 38dp 盒
    + `AppGlyph(Spark)` 换成共享 `InlineIconBadge(Spark)` 后，旧取样面会把「换掉」误判成「删掉」。
    取样单元仍是**同一枚调用**（`AppIcon.Spark` 必须在那次调用的实参里），并补了一条反空转断言
    （单字当图标的 `Text(feature.glyph, …)` 不得被判成合规出口）。
  - `SharedComponentUniquenessRatchetTest`：页头族 seed 摘掉 `"AiLearningScreen.kt"`，
    `headerSeedRetentionReason` 追加一句说明（批 4 Task 1 起本理由不再覆盖该屏：头卡标题换
    `AppType.Headline`，`headline` 读点归零）。
  - `ContainerContractTest`：屏级表加 `"AiLearningScreen.kt"`（强卡 0 / 软卡 4 / 裸行 1，hero 是渐变头卡
    不落 `StrongCard`）。

---

### Task 2: 未上线骨架页重排（`AiFeatureScreen.kt`）

- [x] **Step 1: 写契约 `AiFeatureLayoutContractTest`（7 条，先红）**

  1. `AppScreenHeader(` 恰一处、带 `onBack`；旧返回（`TextButton(` 内「← 返回 AI 学」）为 **0**；
     正向钉 `contentDescription = "返回"` 恰 1 处。
  2. 顺序：`AppScreenHeader(` < `ai_feature_status_card` < 第一枚按钮（两按钮上移）；
     且「现在可以做的」说明文字**排在两枚按钮之后**（现盘相反）。
  3. 主次：`ai_feature_open_settings` 是实底主操作（`PillStyle.Primary` 或 `Button(` + 域色），
     `ai_feature_open_learning` 是描边/文字次操作；两者 `heightIn(min = 48.dp)` 保留。
  4. 下半屏补一句轻量说明：钉子串「##LIGHT_NOTE##」占位（实现时写定句，例如「这个功能上线前，
     上面两件事随时可用；其余能力点开只会看到进度。」），并钉它排在按钮之后。
  5. `Mint*` 读点为 0、`MaterialTheme.typography` 为 0、`AppType.` ≥ 6。
  6. `StatusPill` 形状不动：`featureStatusBadge(implemented)` 恰 1 处调用、徽章文案字面量为 0（与 `AiFeatureTest:122` 同口径）、
     `if (implemented && !LocalIsDark.current) colors.onMint else if (implemented) colors.onBrand` 三档链逐字保留。
  7. 徽标：`AppGlyph(icon = AppIcon.Spark, tint = colors.onBrand` 恰 1 处（`AccessibilityColorTokenContractTest:34`
     登记的携带修复不许退化）。

  **落地时的口径收紧（同批登记，不放宽）**：
  - 第 1 条把「旧返回/旧标题锚」写成退役清单（`TextButton(`、`ai_feature_back`、`ai_feature_title`、
    屏侧再写一份 `contentDescription = "返回"` 均为 0）；
  - 第 3 条把「域色」落成逐字 `containerColor = appDomain(AppDomain.AiSpeech).base` + 次操作
    `containerColor = colors.surface` + `border(1.dp, colors.outlineSoft, AppShape.Button)`，
    并加 `containerColor = appDomain(` 全屏恰 1 处（两枚实底就是两个主操作）；
  - 第 5 条不是「≥ 6」而是一一对应：`Text(` 计数 == `AppType.` 读点数（同 Task 1 第 5 条），
    外加 `fontWeight =` 为 0；
  - 第 6 条把徽章容器也钉字形：`background(if (implemented) appDomain(AppDomain.AiSpeech).base else colors.tint, AppShape.Pill)`。

- [x] **Step 2: 实跑看到预期红** —— `7 tests completed, 6 failed`：第 1/2/3/4/5 条与第 6 条（徽章容器字形）红，
  第 7 条（渐变徽标 / 静态渐变）绿。**与计划的偏差**：计划预期「6/7 先绿」，实跑是「7 先绿」——
  第 6 条我把「徽章容器换 `appDomain()`/`colors.tint`」也折进同一条，它在 RED 阶段必然红；
  分档墨链那半条（`colors.onMint`/`onBrand`/`text1`）仍是逐字保留、改前改后一字未动。

- [x] **Step 3: 实现**（`AiFeatureScreen.kt` 202 → 200 行）

  1. 页头 `AppScreenHeader(title = feature.title, subtitle = feature.summary, onBack = onBack, badge = { AiFeatureBadge() })`；
     旧「自写 TextButton 返回 + 44dp 徽标 Row + 标题」三件整体退役，徽标搬进 `badge` 槽（形状与 on-brand 墨不动）。
  2. `SectionCard`（`Card` + 描边）→ `SoftCard`（`ai_feature_status_card`）；「当前进度」行 + `StatusPill` +
     `ai_feature_status` 全部保留。
  3. 两枚按钮上移到进度卡之后（页级，不再裹在说明卡里）：主操作实底 `appDomain(AppDomain.AiSpeech).base`、
     次操作 `colors.surface` + `outlineSoft` 描边，两枚 `AppShape.Button` / `heightIn(min = 48.dp)` /
     `contentDescription` 全部保留。
  4. 「现在可以做的」块整块退役，页末换一句 `AppType.Footnote` 说明（`ai_feature_light_note`）。
  5. 令牌换装：`Mint*` 36 → 0、`MaterialTheme.typography` 12 → 0、`fontWeight =` 0、`AppType.` 0 → **6**
     （6 处 `Text(` 一一取档）；顶层 `AiFeatureGradient` 仍 `private val` 静态读 `AppleMint*`。
  6. 两枚按钮文案的前景显式补色（`ContainerInkPairingContractTest` 的「同段已迁中性容器」段要求）：
     主操作 `if (!LocalIsDark.current) colors.onMint else colors.onBrand`、次操作 `colors.text1`，
     与各自 `contentColor` 同值（不新增第二个判据源）。

- [x] **Step 4: 单测绿 + 护栏**

  `AiFeatureLayoutContractTest` **7/7**、`AiFeatureTest` 全绿、`AiBadgeContrastFixTest` 12/12、
  `SharedComponentUniquenessRatchetTest`（已摘 `AiFeatureScreen.kt` 行 + 扩写 `headerSeedRetentionReason`）、
  `ContainerContractTest`（已加屏级行）、`ContainerInkPairingContractTest` 全绿；
  全量 JVM **1920 tests / 0 failures**。connected（`emulator-5574`，`xtool-s04-api30`）：
  `AiFeatureScreenTest` **8/8**、`AppScreenTest` **50/50**（含 `openingAnAiFeatureHidesTheBottomBarAndBackReturnsToTheAiTab`）。

- [x] **Step 5: 登记**（`git` 不在 PATH → 提交未执行；以下为同批偏差与观察）

  | # | 项 | 说明 |
  | --- | --- | --- |
  | 1 | 第 6 条 RED 阶段也红 | 徽章容器换 `appDomain()`/`colors.tint` 折进同一条判据，与计划「6 先绿」不符；分档墨链不动 |
  | 2 | 次操作也用 `Button(` | 计划写「描边/文字次操作」二选一，实取 `Button(` + `surface` 容器 + 1dp `outlineSoft` 描边：`ai_feature_open_learning` 的 connected 侧按 tag + `contentDescription` 点，换 `TextButton` 会丢 48dp 触控盒与点击面 |
  | 3 | 页末说明句 | 计划给的「##LIGHT_NOTE##」占位落成「这个功能上线前，上面两件事随时可用；其余能力点开只会看到进度。」——
    不含任何「即将上线」承诺；**文案待评审席追认**（与 Task 1 的 `newSubtitle` 同批） |
  | 4 | 两枚按钮文案显式补色 | `ContainerInkPairingContractTest` 在同段（`val colors = appColors()` + `.surface` 读点）里要求每处 `AppType` 排印点写明 `color =`；显式补色与 `contentColor` 同值 |
  | 5 | `AiBadgeContrastFixTest.mintPrimaryButtonCall` 放宽容器别名 | 正则从只认 `MintPrimary` 扩到 `MintPrimary` 或 `appDomain(AppDomain.AiSpeech).base`（同一值的两种拼法），
    并容许 `contentColor` 之后的尾随逗号；**前景分档与两套亮度复算的判据一字未松** |
  | 6 | 已知顺序 flake 复核 | 单跑 `AppScreenTest#openingAnAiFeature...` 与 `#systemBackFromANonLearningTab...` 各自失败
    （`ai_learning_screen` / `today_plan_summary` 取样不到，后者与本批无关），整类跑 **50/50 全绿**——
    与 `docs/handoff/2026-10-15-*` 登记的「8 处 back/返回 顺序 flaky」同形，非本批回归 |


---

### Task 3: AI 服务重排（`AiProfileSettingsScreen.kt`）

- [x] **Step 1: 写契约 `AiProfileLayoutContractTest`（7 条，先红）**

  1. `AppScreenHeader(` 在编辑器与列表页各恰一处（合计 2）；旧「← 返回 AI 服务」`PillButton` 为 **0**。
  2. 分组：`SoftCard(` 恰 3 处（编辑器内），`SectionCard(` 为 **0**。
  3. 字段守恒：13 个输入/控件 tag 逐一在册（`ai_profile_name` / `ai_profile_endpoint` / `ai_profile_model` /
     `ai_profile_capability_{text,vision,speech,image}` / `ai_profile_voice` / `ai_profile_temperature` /
     `ai_profile_top_p` / `ai_profile_max_tokens` / `ai_profile_timeout_seconds` / `ai_profile_key_input`），
     且组归属符合对账 3（服务与模型 4 项 + 能力 4 项 / 语音 2 项 / 高级与密钥 6 项）。
  4. 保存吸底：`ai_profile_save` 在吸底容器内（钉「`ai_profile_save` 之前 800 字符窗口内出现 `aligned(Alignment.BottomCenter)` 或
     吸底 Box/`Surface`」），且全文件 `ai_profile_save` 恰 1 处。
  5. 危险动作改回调：`onDeleteProfile(` / `onDeleteKey(` 不在点击处直调（钉子串「点击处必须先置 `pendingDelete」），
     正向钉 `AlertDialog(` 与 `GlassDialog(` 在该文件为 **0**。
  6. `Mint*` 读点保持 ≤ 现值（4），新增为 0；`MaterialTheme.typography` 仍为 0。
  7. 列表页行：`ai_profile_item_*` 与两枚默认标记 tag 全保留；行容器改 `BareRow`（`Card(` 在该文件为 0）。

- [x] **Step 2: 实跑看到预期红**

- [x] **Step 3: 实现**

  1. 编辑器：5 枚 `SectionCard` → 3 枚 `SoftCard`（对账 3 的分组），组内标题用 `AppType.Title`、
     说明用 `AppType.Footnote`，字段顺序与 tag 一字不动。
  2. 保存 `PillButton` 移入吸底条；页脚「删除这套配置」也进吸底条次位（或留在页末危险区，二选一后写死并在报告登记）。
  3. `onDeleteKey` / `onDeleteProfile` 的点击改为 `pendingDelete = { ... }` 局部态 + 向外发 `onRequestDeleteKey(id)` /
     `onRequestDeleteProfile(id)`；宿主接 `ConfirmDestructiveDialog`。
  4. 列表页与编辑器页头都换 `AppScreenHeader`（编辑器标题「新增 AI 配置」/「编辑 AI 配置」）。
  5. `AiProfileRow` 的卡 → `BareRow`。

- [x] **Step 4: 单测绿 + 护栏**

  `AiProfileLayoutContractTest` 7/7 + `DangerInkPlacementContractTest`（Danger 墨点新值）+ `ContainerInkPairingContractTest`
  （`:255` 对 `AiProfileSettingsScreen.kt` 的配对判据）+ `DarkTokenConsumptionRatchetTest`（若 SectionCard 归零则摘行）。

- [x] **Step 5: 宿主确认框**（`AppScreen.kt:1246` 分支 + `AppScreenOverlayContainerContractTest` 计数 4 → 6 的一部分）

- [x] **Step 6: 登记**（`git` 不在 PATH → 未执行）

- [x] **验收（2026-11-01 实跑）**
  - 全量 JVM：`tests=1928 failures=0 skipped=0`（262 XML）——`AiProfileLayoutContractTest` 8/8、
    `ContainerContractTest` 14→15、`DarkTokenConsumptionRatchetTest` 两处台账摘行、
    `GlassPanelInkPairingContractTest` `migratedPanels` 3→2 均绿。
  - `:app:compileDebugAndroidTestKotlin` 0 error。
  - 模拟器 connected（`emulator-5574`）：`AiProfileSettingsScreenTest` 24/24、`GlassPanelThemeInkTest` 8/8、
    `ThemeModeSwitchTest` 4/4、`AppScreenTest` 50/50、`AiFeatureScreenTest`+`AiLearningScreenTest`+`SpeechSettingsScreenTest` 39/39。
  - androidTest 同步的四处期望值：`setScreen` 12 形参（`onRequestDeleteProfile`/`onRequestDeleteKey`）、
    `GlassPanelThemeInkTest` ② 三例容器改不透明 `surface`、`ThemeModeSwitchTest` 裸行压 `AppColorDark.background`、
    `AppScreenTest:590` 返回位改 `onNodeWithContentDescription("返回")`。
  - 已知非本批引入的**单方法连测噪声**：`AppScreenTest` 里 `returningFromAiProfiles*` / `aiProfilesOpenedFromSpeech*`
    等方法单跑（`class=…#method`）会红、整类跑 50/50 绿（此前已登记，与本批改动无关）。

---

### Task 4: 拍照翻译重排（`PhotoTranslationScreen.kt`）

- [x] **Step 1: 写契约 `PhotoTranslationLayoutContractTest`（7 条，先红）**

  落地版（实施时按实际形制收口，比计划稿更严，登记于 Step 6 偏差表）：

  1. 页头：`AppScreenHeader(` 恰 1 处、`contentDescription = "返回"` 恰 1 处；`TextButton(` 归零、
     `photo_translation_back` 归零、旧「← 返回 AI 学」归零；`onRequestDeleteSaved(` 恰 1 处。
  2. 两枚折叠行：`EntryRow(` 恰 2 处；`expanded = historyExpanded` / `expanded = modelExpanded` 各恰 1 处；
     `var historyExpanded by rememberSaveable { mutableStateOf(false) }` 与 `modelExpanded` 同形各恰 1 处
     （折叠态必须默认收起）；`SavedHistoryList(` / `OfflineModelCard(` 的调用点必须紧跟 `if (...Expanded)`。
  3. 常驻内容：引擎标签 / 成本句 / 隐私句各恰 1 处；`photo_translation_history_card` / `Card(` /
     `CardDefaults` / `SectionCard(` 全归零；`SoftCard(` ≥ 2。
  4. 历史删除改回调：`onDeleteSaved(` 归零、`onRequestDeleteSaved(` 恰 1、`AlertDialog(` 与
     `ConfirmDestructiveDialog(` 在该文件归零、`GlassDialog(` 恰 1（出站确认）。
  5. 令牌：`Mint*` 读点 ≤ 2、`MaterialTheme.typography` / `FontWeight` / 裸 `Button(` / `OutlinedButton(` /
     `TextButton(` 归零；`OutlinedTextField(` 恰 1。
  6. 31 枚 tag 各恰 1 处（含插值 tag `photo_translation_history_${record.recordId}` /
     `..._delete_${record.recordId}` / `photo_translation_word_$index`）。
  7. 宿主：`AppScreen.kt` 里 `onDeleteSaved =` 归零、`onRequestDeleteSaved =` 恰 1、
     `pendingPhotoTranslationDelete = recordId` 恰 1，且存在 `pendingPhotoTranslationDelete?.let`。

- [x] **Step 2: 实跑看到预期红**

- [x] **Step 3: 实现**

  1. 页头 `AppScreenHeader(title = "拍照翻译", subtitle = 一句中性说明, onBack = onBack)`（旧「← 返回 AI 学」药片下线）。
  2. 5 块收成「历史一行 + 离线模型折叠」：两枚 `EntryRow`（`AppIcon.Clock` / `AppIcon.Download`，
     `EntryIconTone.Tint`）各带 `expanded` 三态，默认收起；隐私说明搬进模型展开体。
  3. 成本句只在翻译按钮之后留一处；引擎标签全局只留一处。
  4. 历史逐条：`SoftCard` 内三行文本 + `PillButton(style = Text)` 删除，只发 `onRequestDeleteSaved`。
  5. 令牌换装：`Mint*` 清零、`MaterialTheme.typography` / `FontWeight` 全换 `AppType`；容器只走
     `SoftCard`/`EntryRow`，按钮一律 `PillButton`；宿主 `AppScreen` 接第三枚 `ConfirmDestructiveDialog`。

- [x] **Step 4: 单测绿 + 护栏**

  `PhotoTranslationLayoutContractTest` 7/7；连带护栏 `PhotoTranslationPermissionContractTest`、
  `AppScreenOverlayContainerContractTest`、`SharedComponentUniquenessRatchetTest`、
  `GlassPanelInkPairingContractTest`、`DangerInkPlacementContractTest`、`NotConfiguredCardContractTest`、
  `ConfirmDestructiveWordingTest` 全绿。

- [x] **Step 5: 新建 `PhotoTranslationScreenTest`（connected，模拟器）**

  8 条（比最小集多 4 条，见 Step 6 偏差表），`emulator-5574` 实跑 **8/8 绿**。计划稿最小集第 ③ 条
  「历史删除先出确认、取消不发回调」是**宿主**行为，屏侧证不到，故另在 `AppScreenTest` 补一条
  `photoTranslationHistoryDeleteGoesThroughTheHostDestructiveDoor`（真实宿主 + 真实
  `PhotoTranslationViewModel` + 记录型历史仓库：弹框 → 取消零写入 → 再弹 → 确认才调仓库），
  单方法跑绿、整类 **51/51**（原 50 + 本 1）。

- [x] **Step 6: 登记**（`git` 不在 PATH → 未执行；偏差登记见下）

- [x] **验收（2026-11-01 实跑）**
  - 全量 JVM：`tests=1935 failures=0 skipped=0`（263 XML）——较 Task 3 基线 1928 增加 7 条，即本 Task 新增契约。
  - `:app:compileDebugAndroidTestKotlin` 0 error；`PhotoTranslationScreenTest` **8/8**（`emulator-5574`）；
    `AppScreenTest` **51/51**（新增 `photoTranslationHistoryDeleteGoesThroughTheHostDestructiveDoor`）。
  - connected 连带：`AppScreenTest` + `AppScreenOverlayContainerTest` + `EntryRowTest` + `AppScreenHeaderTest` +
    `NotConfiguredCardTest` + `OutboundConfirmDialogTest` + `AiFeatureScreenTest` + `AiLearningScreenTest` 共 98 例，
    97 绿；唯一红为已知**设备前置**用例
    `hostedWordBookDeletePanelStaysFullyReadableAndTappableAtDoubleFontScale`（须先
    `adb shell settings put system font_scale 2.0`）——补置后单跑 1/1 绿，随后已恢复 `1.0`。
  - **偏差登记（3 条，均不放宽判据）**：
    1. 版式契约由计划稿的「前 400 字符窗口」改成**逐字调用点判定**（`if (historyExpanded)` 紧跟
       `SavedHistoryList(` 调用），比窗口更严；且把「两枚折叠态 `rememberSaveable { mutableStateOf(false) }`
       恰 2 处」改为**逐个折叠变量逐字各 1 处**——前者会被屏内 `showCamera` 的同形声明顶成 3 而误红，
       改后反而把「谁才是折叠态」钉死。
    2. `DangerInkPlacementContractTest` 的 AiSpeech 计数取样从**全文件**收敛到**出站确认框区间**
       （`outboundDialogSpan`，从取消 tag 到确认 tag 两侧各一窗）：本屏入口行徽标同样属 `AiSpeech` 域，
       全文件计数会被顶到 4 以上；区间内仍必须恰 2 处（多一处 = 冒第三枚按钮，少一处 = 某枚丢了域色），
       并已加「两枚 tag 必须存在且后者在前者之后」的响亮断言防窗口切空。
    3. `PhotoTranslationScreenTest` 由最小 4 条扩到 **8 条**：加「两枚折叠行默认收起且展开体不组合」
       「展开只影响自己那一块」「成本句/引擎标签各恰 1 枚」「未配置卡出口接 `onOpenAiSettings`」。
      另外该屏此前在 `androidTest` 里**零覆盖**，这次是它的第一份真机语义树证据。
      计划稿最小集 ③「删除先出确认、取消不发回调」属宿主行为，改在 `AppScreenTest` 补一条真实宿主用例
      承担（`photoTranslationHistoryDeleteGoesThroughTheHostDestructiveDoor`）；为此 `readyAppScreen`
      增一枚 `photoTranslationViewModel` 形参（androidTest 夹具，生产签名不动），历史仓库假件的
      `listRecent` 不按 profile 过滤——资料 id 由 `createProfile()` 现场生成，跨 profile 保密语义另由
      `RoomPhotoTranslationHistoryRepositoryTest` 与 `PhotoTranslationViewModelTest` 承担。
  - 已知噪声（非本批引入）：`AppScreenTest` 的 `returningFromAiProfiles*` 单方法跑会红、整类跑绿。

---

### Task 5: 长难句重排（`SentenceAnalysisScreen.kt`）

- [x] **Step 1: 写契约 `SentenceAnalysisLayoutContractTest`（7 条，先红）** ——已落地（`app/src/test/.../ui/SentenceAnalysisLayoutContractTest.kt`），判据如下：

  1. `AppScreenHeader(` 恰一处；旧「← 返回 AI 学」为 0；`MaterialTheme.typography` 为 0。
  2. 结果区补两枚操作：`sentence_analysis_copy` 与 `sentence_analysis_speak` 各恰 1 处，且都在
     `sentence_analysis_result` 之后（实际取法见偏差 1）。
  3. 复制走 `LocalClipboardManager`（`clipboardManager.setText(` 恰 1 处），不新增依赖。
  4. 朗读走宿主形参：函数签名含 `speakMessage: String?` 与 `onSpeak: (String) -> Unit`，
     且屏内**不直调** `pronunciationProvider` / `TextToSpeech`（两枚正则均为 0）。
  5. 费用只讲一处：`sentence_analysis_cost_hint` 恰 1 处，且屏内「并可能产生费用」字面量 ≤ 1（判读见偏差 2）。
  6. 主操作：`sentence_analysis_analyze` 是唯一实底主操作（`Button(` = 0、`PillButton(` 恰 5、
     `PillStyle.Primary` 恰 2、`PillStyle.Secondary` 恰 1、`PillStyle.Text` 恰 2）。
  7. `Mint*` 读点为 0、`AppType.` ≥ 8；出站确认三枚 tag 全保留。

- [x] **Step 2: 实跑看到预期红** ——`7 tests completed, 7 failed`（全红），逐条落在页头/结果区/剪贴板/宿主朗读/成本句/主操作/token 七处。

- [x] **Step 3: 实现**

  1. 页头换共享；原 `headlineSmall` 标题退役（标题进页头，说明句进 `subtitle`）。
  2. `SegmentList` 的行 `Card` → `SoftCard`；结果区末尾加「复制译文」/「朗读原文」两枚 `PillStyle.Text`。
     复制落点是各段中文解释按行拼接（不把原文混进译文）；朗读落点是各段英文按空格接回整句。
  3. 宿主接线（`AppScreen.kt` 长难句分支）：按拍照翻译的既有写法加 `sentenceSpeakMessage` +
     `sentenceSpeakRequest` + `sentenceSpeakJob` 三件套，`onSpeak` 走
     `pronunciationProvider.speakSentence` 并带代次校验（先发的那次结果不许覆盖后一次的反馈）；
     `onBack` 与退出同批 `cancel()` 那个 job。
  4. 令牌换装：`Mint*` 读点清零（页面底色 `appColors().background`、强调墨 `appDomain(AppDomain.AiSpeech).deep`），
     排印全走 `AppType`；`FailureCard` 也从描边 `Card` 换 `SoftCard`。

- [x] **Step 4: 单测绿 + 护栏**（`ConfirmDestructiveWordingTest`、`DangerInkPlacementContractTest` 的
  `sentence_analysis_*` 一对不动）——8 类定点 56 例全绿；护栏另动两处（偏差 3/5）。

- [x] **Step 5: 登记**（`git` 不在 PATH → 未执行；偏差登记见下方「验收」段）

- [x] **验收（2026-11-01 实跑）**
  - 全量 JVM：`tests=1942 failures=0 errors=0 skipped=0`（264 XML）——较 Task 4 基线 1935 增加 7 条，
    即本 Task 新增契约；XML 数 +1（本文件）。
  - `:app:compileDebugAndroidTestKotlin` 0 error；`SentenceAnalysisScreenTest` **9/9**（`emulator-5574`）；
    `AppScreenTest` **51/51**（返回位改按内容描述点，用例数不变）。
  - 定点 8 类（`SentenceAnalysisLayoutContractTest` / `ContainerContractTest` / `DangerInkPlacementContractTest` /
    `ConfirmDestructiveWordingTest` / `NotConfiguredCardContractTest` / `SharedComponentUniquenessRatchetTest` /
    `GlassPanelInkPairingContractTest` / `AppScreenOverlayContainerContractTest`）56 例全绿。
  - **偏差登记（5 条，均不放宽判据）**：
    1. 计划稿第 2 条的「`SegmentList(` 调用点之后 400 字符窗口」改成**两重判定**：两枚 tag 必须同时
       ① 落在 `SegmentList(` 的花括号体内 ② 位置在 `sentence_analysis_result` 之后。窗口切法会随
       「卡片体加长」漂移（加一行就滑出窗外而误红），花括号体判的是「长在结果区里」，比窗口更严。
    2. **费用判读**：常驻成本句保留「并可能产生费用」，出站确认句改为「你的句子与该服务的密钥将发送给
       $host。」（删掉重复的费用半句）。代价与理由：确认框打开时成本句仍常驻在屏上，用户在点「分析句子」
       前已看到费用披露；确认框的职责收窄成「发给谁、发什么」。`ConfirmDestructiveWordingTest` 未钉这屏
       的出站文案（它只钉破坏性确认），故改动无判据冲突。
    3. `SentenceAnalysisScreenTest` 由 7 条扩到 **9 条**：新增「结果区两枚操作在位 + 朗读上报整句
       `Birds fly. birds`」「宿主 `speakMessage` 反馈可见」；`idleBlankInput…` 里把
       `sentence_analysis_back` 改成「页头唯一返回位」断言（旧 tag、旧文案各 `assertDoesNotExist`）。
       `AppScreenTest` 里同一锚点改按 `onNodeWithContentDescription("返回")` 点，用例数与断言强度不变。
    4. **顺带补 Task 4 漏项**：`ContainerContractTest` 的屏级表漏了 `PhotoTranslationScreen.kt`
       （批 4 口径是「七屏逐屏并入」），本 Task 一并补上，与 `SentenceAnalysisScreen.kt` 同批入表
       （15 → 17 屏）。
    5. `SharedComponentUniquenessRatchetTest` 页头 seed 摘 `"SentenceAnalysisScreen.kt"`（本体改调共享
       `AppScreenHeader(`、`MaterialTheme.typography` 读点归零，摘行理由已写进 seed 注释）。

---

### Task 6: AI 生图重排（`ImageStudioScreen.kt`）

- [x] **Step 1: 写契约 `ImageStudioLayoutContractTest`（7 条，先红）** ——已落地（`app/src/test/.../ui/ImageStudioLayoutContractTest.kt`）：

  1. `AppScreenHeader(` 恰一处；旧「← 返回 AI 学」为 0；`MaterialTheme.typography` 为 0。
  2. 描述可编辑：`image_studio_prompt` 载体是 `OutlinedTextField(`（或 `BasicTextField`），
     且其 `value` 来自屏内 `rememberSaveable` 的 `editedPrompt`（钉「`image_studio_prompt` 之后 200 字符内出现 `onValueChange`」）。
  3. 生成按钮吸底：`image_studio_generate_image` 在吸底容器内（`aligned(Alignment.BottomCenter)` 或吸底 `Surface`
     在 800 字符窗口内），且全文件恰 1 处。
  4. 主操作随状态换身份：`image_studio_generate_prompt` 与 `image_studio_generate_image` 同一槽位
     （钉两者渲染点所在的同一 `when (state)` 分支结构），文案不出现第三枚「生成」按钮。
  5. 出站确认四枚 tag 全保留（`image_studio_outbound_confirmation_text` / 两枚药片）。
  6. 主题输入与计数保留：`image_studio_subject_input`、`DrawingPromptPolicy.maxSubjectLength` 各恰 1 处。
  7. `Mint*` 读点为 0、`AppType.` ≥ 10；`image_studio_cost_hint` 与 `image_studio_image_cost_hint` 各 1 处。

- [x] **Step 2: 实跑看到预期红** ——`7 tests completed, 7 failed`（全红），失败读数与预期吻合：`AppScreenHeader` 0 处、
  `MaterialTheme.typography` 3 处、`Button(` 3 处、`Card(`/`CardDefaults` 各 2 处、`image_studio_prompt` 是只读
  `Text`（取不到输入框宿主）、`image_studio_generate_image` 窗口内无 `BottomCenter`。

- [x] **Step 3: 实现**

  1. 页头换共享（`AppScreenHeader(title = "AI 生图", onBack)`，`subtitle` 一句讲「描述可以改」）。
  2. `PromptCard` → `SoftCard` 内 `OutlinedTextField`（可编辑描述），本地态 `editedPrompt` 初值 `state.prompt`；
     「生成图片」把 `editedPrompt` 传给 VM。
  3. VM 增参 `generateImage(promptOverride: String? = null)`（对账 6，已照准）；屏侧形参 `onGenerateImage: (String) -> Unit`。
  4. 令牌换装（`Mint*` 归零、排印全走 `AppType`、吸底条改不透明 `Surface` + `ActionPill`）。

- [x] **Step 4: 单测绿 + 护栏**（`ImageStudioScreenTest` 现有断言改期望值；
  `DangerInkPlacementContractTest` 的 `image_studio_*` 一对不动）——契约 7/7 + `ImageStudioViewModelTest` 21/21
  绿；`ImageStudioScreenTest` 11 → **12** 条（见偏差 4）；护栏两处未动。

- [x] **Step 5: 登记**（`git` 不在 PATH → 未执行；偏差登记见下方「验收」段）

- [x] **验收（2026-11-01 实跑）**
  - **执行顺序说明（避免与本文件 Task 7 段读数看起来矛盾）**：本 Task 的实际开工晚于 Task 7，
    条数轨迹因此是 `1942（Task 5）→ 1949（Task 7）→ 1958（本 Task）`，**不是**「Task 6 在 Task 7 之前」。
    本段读数大于 Task 7 段属执行顺序所致，非笔误。
  - 全量 JVM：`xml=266 tests=1958 failures=0 errors=0 skipped=0`——较 Task 7 读数 1949 增加 9 条
    （`ImageStudioLayoutContractTest` 7 条 + `ImageStudioViewModelTest` 出站体 2 条）。
  - `:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin`、`verifyThirdPartyNotices`、
    `verifyAcTraceabilityMatrix` 全过。
  - 模拟器 `emulator-5574`：`ImageStudioScreenTest` **12/12**、`AppScreenTest` **51/51**（`am instrument`，未用 `connectedDebugAndroidTest`）。
  - **偏差登记（6 条，均不放宽判据）**：
    1. **契约第 2 条换了取窗口的读法**（计划稿写「`image_studio_prompt` 之后 200 字符内出现 `onValueChange`」）：
       tag 必然长在 `.testTag("…")` 这个**嵌套调用**里，按「anchor 之前最近的 `(`」取窗口只会取到
       `("image_studio_prompt")`——对任何合法 Compose 写法都判红，等于把判据写成不可满足。
       改按符号取宿主：`inputFieldCallCarrying` 以 `OutlinedTextField(`/`BasicTextField(` 为锚找最近一发、
       再用 `parenEnd` 括号配对确认 anchor 真落在这一发实参里。窗口内三条要求（可编辑输入类名 /
       `onValueChange` / `value = editedPrompt`）一字未减，另加「`editedPrompt` 由 `rememberSaveable` 承载」。
    2. **契约第 6 条把「`DrawingPromptPolicy.maxSubjectLength` 恰 1 处」改成恰 3 处**：上限常量在三处各自必要
       （`take()` 截断保护 / 输入框标签里的上限提示 / 现行 `n/100` 计数），少一处就是上限失效、提示丢失或计数消失。
       计划稿的「1」是笔误，本条按功能实需钉成 3——是收紧不是放宽。
    3. **出站确认框口径补严**：除四枚 tag 外另钉 `GlassDialog(` 恰 1 枚、`Card(`/`CardDefaults` 为 0、
       `SoftCard(` ≥ 1、六枚状态锚（`drafting`/`generating`/`result`/`result_unreadable`/`not_configured`/`failure`）各 1 处。
    4. `ImageStudioScreenTest` 由 11 条扩到 **12 条**：新增
       `theDescriptionIsEditableAndTheEditedWordsAreWhatGoOut`——断言描述框里就是模型稿 → `performTextReplacement`
       改成手写句 → 点「生成图片」→ 宿主 `onGenerateImage` 收到的正是**改过的那句**（spec §5「可编辑后再生成」
       这句话的唯一活证；顺带把「吸底键在大字号下要 `performScrollTo` 才点得到」写成显式步骤）。
       同屏 `AppScreenTest` 的返回锚点从退役 tag `image_studio_back` 改成
       `onNode(hasContentDescription("返回") and hasAnyAncestor(hasTestTag("image_studio_screen")))`。
    5. `ContainerContractTest` 屏级表增 `"ImageStudioScreen.kt"`（17 → 18 屏；「七屏逐屏并入」口径）。
    6. `SharedComponentUniquenessRatchetTest` 页头 seed 摘 `"ImageStudioScreen.kt"`
       （本体改调共享 `AppScreenHeader(`、`MaterialTheme.typography` 读点归零，摘行理由写进 seed 注释）。
  - **对账 6 执行结果**：`generateImage(promptOverride: String? = null)` 已照准落地，`promptOverride` 在非空时覆写
    `pendingPrompt` 后再组请求体；`ImageStudioViewModelTest` 两条出站体断言各钉「出站体里的 prompt 就是改过的那句」
    与「未传参时回落到模型稿」——越出呈现层的一处就此闭环。

---

### Task 7: AI 问词重排（`WordAiQaScreen.kt`）

- [x] **Step 1: 写契约 `WordQaLayoutContractTest`（7 条，先红）** ——已落地（`app/src/test/.../ui/WordQaLayoutContractTest.kt`），七条与计划稿一一对应：

  1. `AppScreenHeader(` 恰一处；旧「← 返回上一层」为 0；`Button(` 为 0；`MaterialTheme.typography` 为 0。
  2. 回答上移：`when (state)` 的首个渲染点位置 **<** `askKinds.forEach` 位置；`word_qa_answer` 与
     `word_qa_answer_kind` 均落在 `when (state)` 体内。
  3. 主次分明：`word_qa_chip_Breakdown` 那一枚 `PillStyle.Primary`，`Sentence`/`Mnemonic` 取 `PillStyle.Text`
     （`PillStyle.Primary` 全文件恰 2 处：首选问法 + 出站确认主键）；`WordQaKind.Personal` 仍被过滤（`askKinds` 常量）。
  4. 笔记区下沉：`word_qa_personal_note` 与 `word_qa_save_personal_note` 各 1 处，且位置**在 `askKinds.forEach` 之后**。
  5. 已保存笔记换 `BareRow(`；`Card(` 与 `CardDefaults` 在本文件为 0。
  6. 出站确认三枚 tag 全保留；`word_qa_save_failed` 仍包 `InlineNotice(`（`InlineNoticeKind.Error`）。
  7. `Mint*` 读点为 0、`AppType.` ≥ 8；`contentDescription = "AI 回答"` 逐字保留。

- [x] **Step 2: 实跑看到预期红** ——`7 tests completed, 7 failed`（全红），逐条落在页头/回答位置/主次档/笔记序/裸行/
  确认与失败条/token 七处（红值：`AppScreenHeader` 0 处、`Mint*` 32 个读点、`Button(` 4 处、`Card(` 3 处、
  回答位置 8343 > 首枚 chip 7300）。

- [x] **Step 3: 实现**

  1. 页头换共享 `AppScreenHeader(title = "AI 问词", onBack)`；旧 `Button` 形态返回与 `headlineSmall` 自绘标题退役。
  2. 单词说明不塞 `subtitle`（页头 `subtitle` 无标签槽），改为紧随页头的一行 `AppType.Footnote` + `word_qa_lemma`（偏差 1）。
  3. 屏体序（最终形态）：页头 → `word_qa_lemma` → `when (state)`（含 `AnsweredContent` 里的「保存为笔记」）
     → 三枚 `QuestionChip` → 「我的笔记」`SoftCard` → 「已保存笔记」`SavedNoteList`（偏差 2）。
  4. `askKinds` 提为文件顶层常量 `listOf(WordQaKind.Breakdown) + entries.filter { it != Personal && it != Breakdown }`：
     「拆词义」天然第一枚，`Personal` 照旧不出药片。
  5. `QuestionChip` 两档：`Breakdown` → `PillStyle.Primary`，其余 `PillStyle.Text`，`heightIn(min = 54.dp)` 保留。
  6. `SavedNoteList` 改 `BareRow(title 两行上限, topDivider, tag 形状不变)`；`FailureCard` 由描边 `Card` 换 `SoftCard`。
  7. 令牌换装：`Mint*` 读点清零（底色 `appColors().background`、强调墨 `appDomain(AppDomain.AiSpeech).deep`），
     排印全走 `AppType`。数据通路 / tag / 校验文案 / 出站确认三要素一字未动。

- [x] **Step 4: 单测绿 + 护栏**（`WordAiQaScreenTest` 现有断言改期望值；
  `DangerInkPlacementContractTest` 的 `word_qa_*` 一对不动）——契约 7/7 绿，`DangerInkPlacementContractTest` 未动且绿。

- [x] **Step 5: 登记**（`git` 不在 PATH → 未执行；偏差登记见下方「验收」段）

- [x] **验收（2026-11-01 实跑）**
  - 全量 JVM：`tests=1949 failures=0 errors=0 skipped=0`（265 XML）——较 Task 5 基线 1942 增加 7 条，即本 Task 新增契约；XML 数 +1（本文件）。
  - `:app:compileDebugAndroidTestKotlin` 0 error；`WordAiQaScreenTest` **10/10**（原 8 + 新增 2，`emulator-5574`，
    `am instrument`，未用 `connectedDebugAndroidTest`）；`AppScreenTest` **51/51**（用例数不变）。
  - **偏差登记（4 条，均不放宽判据）**：
    1. **单词行不进页头 `subtitle`**：共享 `AppScreenHeader` 的 `subtitle` 是纯 `String`、没有标签槽，
       把 `word_qa_lemma` 塞进去必须改共享组件（越出「只动呈现层」）。改为页头下一行独立 `Text` 承载，
       tag 不变；契约第 1 条仍钉「`AppScreenHeader(` 恰一处」。
    2. **问法 chip 排到回答之下**（计划稿 Step 3.2 写的是「页头 → 三问法 → 回答」）：与 spec §5「回答上移」
       及本 Task 契约第 2 条冲突，按契约判据落成「回答在上、问法在下」，并同步把「我的笔记」压到两者之后
       （计划稿原本就是「笔记区下沉」）。计划稿该行措辞按本条更正，判据未改。
    3. `WordAiQaScreenTest` 由 8 条扩到 **10 条**：新增 `answerSitsAboveTheQuestionChipsAndBreakdownLeadsThem`
       （按 `boundsInRoot.top` 钉「回答与问法标签在 chip 之上」「Breakdown 是 chip 列第一枚」）与
       `savedNotesAreListedAndTheLemmaLineSurvives`（裸行 tag + `word_qa_lemma` 在位）；
       `idleShowsTheFixedQuestionChipsAndBack` 里 `word_qa_back` 改成「页头唯一返回位」断言
       （旧 tag、旧文案各 `assertDoesNotExist`，再按 `contentDescription = "返回"` 点）。
    4. **`AppScreenTest` 的返回锚点必须限定在覆盖层内**：问词覆盖层是叠在词义详情之上的同构页，
       两者都渲染共享页头 → 同一棵树上有**两枚**「返回」，裸 `onNodeWithContentDescription("返回")`
       会因「找到 2 枚」而红。改成
       `onNode(hasContentDescription("返回") and hasAnyAncestor(hasTestTag("word_qa_screen")))`——
       判据没放宽，反而收紧到「必须恰是覆盖层里那一枚」。
  - 护栏另动一处：`SharedComponentUniquenessRatchetTest` 页头 seed 摘 `"WordAiQaScreen.kt"`
    （本体改调共享 `AppScreenHeader(`、`MaterialTheme.typography` 读点归零，摘行理由含「单词未塞进 subtitle」已写进 seed 注释）。

---

### Task 8: 全量门禁 + 模拟器 connected + 真机目视 + 报告

- [x] **Step 1: 全量 JVM 门禁** ——**过**

  ```powershell
  & $g.FullName :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin `
      verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
  ```
  基线 1906 / 0 → 期望 ≥ 1955（+49 = 7 契约 × 7）；**实测 `xml=266 tests=1958 failures=0 errors=0 skipped=0`**
  （+52 = 七枚新契约 **50** 条（7 / 7 / 8 / 7 / 7 / 7 / 7）+ `ImageStudioViewModelTest` 出站体 **2** 条；逐 Task 读数
  1913 → 1920 → 1928 → 1935 → 1942 → 1949 → 1958，与计划稿「七契约 × 7」的差额全由 AI 服务契约多出的第 8 条与 VM 两条解释），
  `assembleDebug` / `compileDebugAndroidTestKotlin` / `verifyThirdPartyNotices` / `verifyAcTraceabilityMatrix` 同批全过。

- [x] **Step 2: 模拟器 connected（只在 `emulator-5574`，`am instrument` 逐类）** ——十类**逐类全绿**（`font_scale` 1.0）：

  | 类 | 结果 | 类 | 结果 |
  |---|---|---|---|
  | `AiLearningScreenTest` | 18/18 | `ImageStudioScreenTest` | 12/12 |
  | `AiFeatureScreenTest` | 8/8 | `WordAiQaScreenTest` | 10/10 |
  | `AiProfileSettingsScreenTest` | 24/24 | `AppScreenTest` | 51/51 |
  | `PhotoTranslationScreenTest` | 8/8 | `WordCardScreenTest` | 18/18 |
  | `SentenceAnalysisScreenTest` | 9/9 | `TodayPlanScreenTest` | 24/24 |

  十类合计 182 例，0 失败。全程 `am instrument`，**未用 `connectedDebugAndroidTest`**。

- [x] **Step 3: 大字号专项** ——`font_scale 2.0` 下十类逐类重跑，**暴露两处真红、均已修**：

  1. `SentenceAnalysisScreenTest.analyzedOffersCopyAndSpeakAndSpeakReportsTheWholeSentence`：2.0 下朗读药片落到首屏外，
     点击静默落空 → 上报值恒为 `null`（期望 `Birds fly. birds`）。修法：点前 `performScrollTo()`（**不改判据**，
     只是把「滚到能点」补成显式步骤）。
  2. `AppScreenTest.wordBankSystemBackClosesAnOpenWordDetailBeforeLeavingTheBank`：2.0 下词行变高，
     行心落点压到行尾「标记为已掌握」药片上 → 详情没开、断言红。修法：点击改按行左 20% 宽度
     （`performTouchInput { click(Offset((width * 0.2f).coerceAtLeast(1f), centerY)) }`），仍点同一枚 `my_words_wordbook_row_0`。

  修后：五屏断言逐条绿（① AI 服务三组弱卡 + 吸底保存不遮末组 ② 拍照翻译折叠行展开后全部可达
  ③ 长难句复制/朗读可点 ④ 生图描述框可改 + 吸底键不遮输入 ⑤ 问词主操作与回答不裁切）。
  `settings put system font_scale 1.0` 已恢复（模拟器与真机均为 1.0）。
  **已知抖动（未改实现）**：2.0 全类跑时 `AppScreenTest` 第 349 行（`pressSystemBack()` 后 `card_detail_screen`
  仍存在）红过一次；单跑 3 连绿、整类重跑绿 → 判为 `sendKeyDownUpSync(KEYCODE_BACK)` 与 `createComposeRule`
  的时序抖动，记录在案。

- [x] **Step 4: 真机目视（只 `adb install -r` + 手看，永不 instrument）** ——`bf353dda`（M2102J2SC）已装
  `app-debug.apk` 并逐屏手看（截图存 `%TEMP%\opencode\dev_*.png`）：
  AI 主页副标题「随身陪练，AI 开口说话」中性、三行能力无卡片汤、「查看词表」一条裸行 ✅；
  骨架页两枚按钮在首屏 ✅；AI 服务三组 + 吸底保存 + 两处确认 ✅；拍照翻译进屏即见主流程、历史与模型各一行 ✅；
  长难句复制/朗读可用 ✅；生图描述可改、生成键吸底 ✅；词库合并页四段、词卡详情、词卡翻面 ✅。
  **未覆盖**：AI 问词覆盖层（入口只在「词卡详情 / 文章详情」两条路由上；本机文章未解锁，
  词卡复习的评级键因 `state.submitting` 卡住点不动——与版式无关的数据/会话态）。该屏版式由
  `WordAiQaScreenTest` 10/10 在模拟器覆盖，已在报告「残余风险」里登记为待复看项。

- [x] **Step 5: 报告** ——`docs/handoff/2026-11-01-layout-redesign-batch-4-report.md` 已落盘（277 行，
  UTF-8 无 BOM / LF；含改期望值清单 14 条、六处对账执行结果、三处规范性让步、seed 摘六行理由、
  `ConfirmDestructiveDialog` 4 → 7 逐站点账、净增判据统计（JVM +52 / connected +14 两个差额都逐条解释）、
  未执行项、遗留议题 6 条）。

- [x] **Step 6: 登记**（`git` 不在 PATH → 未执行；偏差与实测读数全部写进本文件各 Task 验收段与报告）

- **收口补漏（2026-11-01，Task 8 复核时发现）**：`ContainerContractTest` 屏级表在 Task 7 只加了注释没加行，
  表里**漏了 `"WordAiQaScreen.kt"`**（实际 18 行，计划对账口径是 19 屏）。已补该行并写清容器账
  （3 枚 `SoftCard` + 1 枚 `BareRow` + 零强卡 + 零 M3 `Card(`，旧 `FailureCard` 的 `Card` 随 Task 7 下线）；
  `ContainerContractTest` 5/5 绿。**判据是加强不是放宽**——此前这屏的强卡口径**根本没被任何断言看着**。
  到此七屏全部在表内（12 → **19** 屏，与计划稿一致）。

---

## Self-Review

**规范覆盖对账（spec §5 逐屏表 AI 七行 + §4.6 状态口径）**

| 规范要求 | 落点 | 判据 |
|---|---|---|
| AI 主页副标题改中性一句 | Task 1 Step 3.2 | `AiLearningLayoutContractTest` 第 2 条（含旧句为 0） |
| 「查看词表」直达词库 | Task 1 Step 3.4 + 宿主路由 | 第 3/4 条 |
| 骨架页两按钮上移 | Task 2 Step 3.3 | `AiFeatureLayoutContractTest` 第 2 条 |
| 骨架页下半屏补一句轻量说明 | Task 2 Step 3.4 | 第 4 条 |
| AI 服务输入分 3 组 | Task 3 Step 3.1 | `AiProfileLayoutContractTest` 第 2/3 条 |
| AI 服务保存固定底部 | Task 3 Step 3.2 | 第 4 条 |
| 清空密钥/删除配置加确认 | Task 3 Step 3.3 + Step 5 | 第 5 条 + 宿主计数 4 → 7 |
| 拍照翻译常驻 5 块收成两行 | Task 4 Step 3.2 | `PhotoTranslationLayoutContractTest` 第 2/3/4 条 |
| 拍照翻译历史删除加确认 | Task 4 Step 3.3 | 第 6 条 + 宿主计数 |
| 长难句结果卡补复制/朗读 | Task 5 Step 3.2-3.3 | `SentenceAnalysisLayoutContractTest` 第 2/3/4 条 |
| 长难句费用句只讲一次 | Task 5 Step 3 | 第 5 条 |
| AI 生图画面描述可编辑后再生成 | Task 6 Step 3.2 | `ImageStudioLayoutContractTest` 第 2 条 |
| AI 问词 4 枚按钮分主次 | Task 7 Step 3.3 | `WordQaLayoutContractTest` 第 3 条 |
| AI 问词回答上移 | Task 7 Step 3.2 | 第 2 条 |
| §4.6 失败态可行动 | 七屏均已有 `NotConfiguredCard` / `FailureCard`（批 3 前身已接），本批只换装不新建 | 各屏契约「旧私有失败卡形状为 0」 |
| §4.6 危险动作带确认 | 对账 4 | 宿主 `ConfirmDestructiveDialog` 4 → 7 |

**与规范字面的三处让步（须在报告里显式登记）**
1. 「14 个输入」按可交互控件口径为 13（差的是「清除已保存的密钥」这个动作按钮），分组仍为 3 组（对账 3）。
2. AI 问词「4 枚按钮」：`WordQaKind` 四档里 `Personal` 已被屏侧 filter 掉（笔记走独立输入区），
   本批按「3 个问法 + 1 个保存笔记」讲主次，不改枚举（改枚举＝改请求策略）。
3. AI 生图「描述可编辑」需 VM 增一个形参（对账 6），这是本批唯一越出「只动呈现层」的一处；
   不照准则降级为「只读 + 提示重新生成」并登记为未兑现项。

**风险与对冲**

| 风险 | 对冲 |
|---|---|
| 七屏 2824 行，`AiProfileSettingsScreen` 单文件 629 行易改坏 | 每屏契约 7 条逐条钉，Task 3 拆「分组 → 吸底 → 确认」三小步，每步后单跑本契约 + `DangerInkPlacementContractTest` |
| 页头族 seed 少摘/多摘 | 摘行与「headline 读点归零」同一次跑验证；`each_family_offender_set_equals_its_seed_exactly` 双向判 |
| 白胶囊两枚静态墨被「顺手迁移」 | `AiLearningLayoutContractTest` 第 6 条 + `AiBadgeContrastFixTest` 双重钉；`DarkTokenSeedAllowlist` 不动即红 |
| 三处新确认框把 `AppScreenOverlayContainerContractTest` 判据打散 | 计数与路径 A 取样同批改（4 → 7 / 3 → 6 / 4 → 7），并把三条文案纳入 `ConfirmDestructiveWordingTest` |
| 长难句朗读接线触发 TTS 状态机回归 | 逐字照拍照翻译既有形状（代次校验 + `PronunciationStatus` 回写），只加形参不改 VM |
| 拍照翻译无边基线（现无 connected 类） | Task 4 Step 5 新建 connected 类，先红后绿（落地 8 条，比最小集 4 条多出折叠/单例/出口四条） |

**判据净增**：新 7 枚契约 49 条 + connected 新增「旧返回文案为 0」「主次分明」「回答在问法之上」「折叠默认收起」
约 12 条，删除判据 0 条（只退役旧返回 tag，每处配一条「必须为 0」）。

---

## 执行状态

| Task | 状态 | 备注 |
|---|---|---|
| 计划落盘 | ✅ 2026-11-01 | 本文件 |
| Task 1 AI 主页 | ✅ 2026-11-01 | 契约 7/7 绿；JVM 全量 1913/0；connected `AiLearningScreenTest` 18/18 + `AiMascotTest` 7/7（`emulator-5574`）。偏差见 Task 1 Step 3 末尾登记 |
| Task 2 未上线骨架页 | ✅ 2026-11-01 | 契约 7/7 绿；全量 JVM **1920/0**；connected `AiFeatureScreenTest` 8/8 + `AppScreenTest` 50/50（`emulator-5574`）。偏差 6 条见 Task 2 Step 5 登记 |
| Task 3 AI 服务 | ✅ 2026-11-01 | 契约 8/8 绿；全量 JVM **1928/0**；connected `AiProfileSettingsScreenTest` 24/24 + `GlassPanelThemeInkTest` 8/8 + `ThemeModeSwitchTest` 4/4 + `AppScreenTest` 50/50 + AI 三屏 39/39（`emulator-5574`）。宿主确认框 4 → 6 落地；详见 Task 3 末尾验收登记 |
| Task 4 拍照翻译 | ✅ 2026-11-01 | 契约 7/7 绿；全量 JVM **1935/0**（263 XML）；connected 新建 `PhotoTranslationScreenTest` **8/8** + `AppScreenTest` **51/51**（新增宿主确认门用例），连带 7 类 98 例 97 绿（余 1 例为已知设备前置，补置 `font_scale` 后单跑绿）；宿主确认框 6 → **7**、`AppScreenOverlayContainerContractTest` 8 → 9 枚浮层；偏差 3 条见 Task 4 末尾验收登记 |
| Task 5 长难句 | ✅ 2026-11-01 | 契约 7/7 绿；全量 JVM **1942/0**（264 XML）；connected `SentenceAnalysisScreenTest` **9/9**（原 7 + 复制/朗读/朗读反馈 2 条）+ `AppScreenTest` **51/51**；8 类定点 56 例全绿；朗读走宿主形参（屏内零 TTS、零 `pronunciationProvider`），费用收成一处；偏差 5 条见 Task 5 末尾验收登记（含顺带补上 Task 4 漏的 `ContainerContractTest` 拍照翻译入表） |
| Task 6 AI 生图 | ✅ 2026-11-01 | 契约 7/7 绿；全量 JVM **1958/0**（266 XML）；`assembleDebug` + `compileDebugAndroidTestKotlin` + `verifyThirdPartyNotices` + `verifyAcTraceabilityMatrix` 全过；模拟器 `emulator-5574` `ImageStudioScreenTest` **12/12**（原 11 + 「改过描述后回传」）+ `AppScreenTest` **51/51**；**对账 6 的 VM 增参照准落地**（`generateImage(promptOverride)` + 2 条出站体断言）；偏差 6 条见 Task 6 末尾验收登记 |
| Task 7 AI 问词 | ✅ 2026-11-01 | 契约 7/7 绿；全量 JVM **1949/0**（265 XML）；模拟器 `emulator-5574` `WordAiQaScreenTest` **10/10**（原 8 + 位置序/裸行 2 条）+ `AppScreenTest` **51/51**；回答上移、`Breakdown` 唯一实底首选、笔记区下沉；`Card(`/`Button(`/`MaterialTheme.typography`/`Mint*` 归零；偏差 4 条见 Task 7 末尾验收登记（含「覆盖层与底层详情各有一枚『返回』，锚点须限定在覆盖层内」）；**Task 8 复核补漏**：`ContainerContractTest` 屏级表漏了本屏，已补行（18 → 19 屏） |
| Task 8 门禁/目视/报告 | ✅ 2026-11-01 | 全量 JVM **1958/0**（266 XML）+ 四项 verify 全过；模拟器十类 **182 例逐类全绿**；`font_scale 2.0` 十类重跑暴露两处真红（长难句朗读键在首屏外、词行点击落点压到行尾药片），均已修且判据不放宽；真机 `bf353dda` 十二屏目视完成、AI 问词覆盖层待复看（路由不可达）；报告 `docs/handoff/2026-11-01-layout-redesign-batch-4-report.md` 已落盘。**收口后回填（2026-10-31 用户评审三条，见报告 §10）**：全量 JVM 现值 **1961/0**（266 XML，+3 条为三条反馈新增判据）；底导两档改为画同一枚插画（未选中只 `alpha(0.5f)`）、`StrongCard` 浅套改 `tint2` 底 + `text1` 墨（深套不动）；真机 `bf353dda` 浅套取证 `docs/design/app-today-plan-strongcard-light-20261031.png`、`docs/design/app-profile-statcard-bottombar-light-20261031.png`；裁决 `docs/decisions/2026-10-31-ui-review-three-rulings.md` |

**开工前须评审席照准的两件事**：① 对账 6 的 `ImageStudioViewModel.generateImage(promptOverride)` 增参
（唯一越出「只动呈现层」的一处）；② 对账 1 的 AI 主页中性副标题定稿文案。
未照准也可开工 Task 1–5、7；Task 6 的描述可编辑那一小步降级为「只读 + 提示重新生成」。
③（新增，Task 2 落地后追认）骨架页页末说明句「这个功能上线前，上面两件事随时可用；其余能力点开只会看到进度。」
——spec §5 只给了方位没给文案，本批把它落成一句；要改词请连改 `AiFeatureLayoutContractTest.lightNote` 与屏侧同一行（两处一字不差）。


