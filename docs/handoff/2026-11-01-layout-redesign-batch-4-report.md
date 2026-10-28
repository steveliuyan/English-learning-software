# 2026-11-01 版式重做 批 4（AI 链路 7 屏）交付报告

计划文档：`docs/superpowers/plans/2026-11-01-layout-redesign-batch-4-ai-path.md`
规范文档：`docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md`
范围：`AiLearningScreen` / `AiFeatureScreen` / `AiProfileSettingsScreen` / `PhotoTranslationScreen` /
`SentenceAnalysisScreen` / `ImageStudioScreen` / `WordAiQaScreen`（7 屏 2824 行，全量重排）

## 1. 本次目标

把 AI 链路七屏从「M3 默认主题 + 自写返回按钮 + 卡片汤 + `Mint*` 静态桥」推到批 1 已立的骨架：
`AppType` 六档排印、`appColors()` / `appDomain()` 令牌、`AppScreenHeader` 共享页头、
`StrongCard`/`SoftCard`/`BareRow` 三档容器、危险动作带确认。同批收掉 spec §5 的七条 AI 逐屏要求：

- AI 主页：副标题改中性事实句（不承诺「生成」）、「查看词表」去掉渐变套卡改直达裸行。
- 骨架页：两枚按钮上移到进度卡之后，页末补一句轻量说明。
- AI 服务：输入按 3 组、保存吸底、清空密钥与删除配置加确认。
- 拍照翻译：常驻 5 块收成「历史」+「离线模型」两枚折叠行，历史删除加确认。
- 长难句：结果区补「复制译文」/「朗读原文」，费用句只讲一处。
- AI 生图：画面描述可编辑后再生成（spec §5 原话），生成键吸底。
- AI 问词：问法分主次、回答上移、笔记区下沉。

约束（全程未松）：只改版式与信息架构，**不动** Room / FSRS / 备份 / Keystore / AI 请求体与出站确认策略；
改期望值不放宽判据（删旧锚必配「旧锚为 0」正向断言）；TDD 先红并贴失败读数；`fontScale = 2f` 不退化；
一屏一个主操作；真机只 `install -r` + 手看，connected 只在 `emulator-5574` 用 `am instrument`。

## 2. 完成了什么

| Task | 屏 | 契约测试 | 实测状态 |
|---|---|---|---|
| 1 | AI 主页 `AiLearningScreen.kt` | `AiLearningLayoutContractTest`（7） | ✅ 6 红 1 绿（第 6 条是护栏，设计上先绿）→ 绿；1913 tests |
| 2 | 未上线骨架页 `AiFeatureScreen.kt` | `AiFeatureLayoutContractTest`（7） | ✅ 红 6/7（第 7 条先绿）→ 绿；1920 tests |
| 3 | AI 服务 `AiProfileSettingsScreen.kt` | `AiProfileLayoutContractTest`（**8**） | ✅ → 绿；1928 tests（多出的第 8 条见 §4-6） |
| 4 | 拍照翻译 `PhotoTranslationScreen.kt` | `PhotoTranslationLayoutContractTest`（7） | ✅ → 绿；1935 tests；新建 connected 类 8 条 |
| 5 | 长难句 `SentenceAnalysisScreen.kt` | `SentenceAnalysisLayoutContractTest`（7） | ✅ 先红 7/7 → 绿；1942 tests |
| 7 | AI 问词 `WordAiQaScreen.kt` | `WordQaLayoutContractTest`（7） | ✅ 先红 7/7 → 绿；1949 tests |
| 6 | AI 生图 `ImageStudioScreen.kt` | `ImageStudioLayoutContractTest`（7） | ✅ 先红 7/7 → 绿；1958 tests（VM 增参照准） |
| 8 | 全量门禁 + connected + 大字号 + 真机目视 + 报告 | — | ✅ 见 §3 |

> **Task 6 / Task 7 的执行顺序**：Task 6 实际开工晚于 Task 7，条数轨迹是
> `1906（批 3 收口）→ 1913 → 1920 → 1928 → 1935 → 1942 → 1949（T7）→ 1958（T6）`，
> 计划文件内 Task 6 段的读数大于 Task 7 段属执行顺序所致，已在计划该段写明。

### 屏级变化摘要

- **AI 主页**：hero 整块仍由 `AiHeaderCard` 承担（`AppScreenHeader(` 计数钉为 **0**，理由见 §5-附）；
  副标题换中性句「这里按真实进度列出 AI 能力：能用的直接进，没写完的写明差什么。」
  能力行 M3 `Card` + 描边 → `SoftCard` + `InlineIconBadge(AppIcon.Spark)`，
  `contentDescription = "${feature.title}，状态：$status"` 逐字保留；`AiNavigationCard` 的三层
  渐变套卡 → 一条 `BareRow`（`ai_learning_nav_row` + `wordListLine(...)` + `ChevronRight`）直调 `onOpenWordList`；
  `Mint*` 35 → **0**、`MaterialTheme.typography` 12 → **0**、`AppType.` 0 → 5（与 `Text(` 一一对应）、
  `Button(`/`PillButton(`/`TextButton(` 全 **0**；白胶囊两枚静态墨与品牌渐变静态 brush **一字未动**
  （`DarkTokenSeedAllowlist["AiLearningScreen.kt"]` 保持 2，`AiBadgeContrastFixTest` 12/12）。
- **未上线骨架页**：页头 `AppScreenHeader(feature.title, feature.summary, onBack, badge = { AiFeatureBadge() })`，
  旧「自写 `TextButton` 返回 + 44dp 徽标行 + `headlineSmall` 标题」三件整体退役；`SectionCard` → `SoftCard`
  （`ai_feature_status_card`）；两枚按钮上移到进度卡之后（主操作实底 `appDomain(AiSpeech).base`、
  次操作 `surface` + 1dp `outlineSoft` 描边，`heightIn(min = 48.dp)` 保留）；「现在可以做的」整块退役，
  页末换一句 `AppType.Footnote`（`ai_feature_light_note`）；`Mint*` 36 → 0、`typography` 12 → 0、
  `fontWeight =` 0、`AppType.` 0 → 6；文件 202 → 200 行。
- **AI 服务**：编辑器 `SectionCard` 5 → **3 枚 `SoftCard`**（① 服务与模型 ② 语音 ③ 高级与密钥），
  13 枚控件 tag 与字段顺序、校验文案一字不动；保存 `PillButton` 入吸底条（流内无第二枚保存入口）；
  「清除已保存的密钥」「删除这套配置」改 `pendingDelete*` 局部态 + 向宿主发
  `onRequestDeleteKey` / `onRequestDeleteProfile`，由 `AppScreen` 的 `ConfirmDestructiveDialog` 承载；
  编辑器与列表页页头都换共享；`AiProfileRow` 卡 → `BareRow`（该文件 `Card(` = 0）。
  台账同批摘两行（`DarkTokenConsumptionRatchetTest` 的 `AiProfileSettingsScreen.kt` 两处）、
  `GlassPanelInkPairingContractTest.migratedPanels` 3 → 2。`Mint*` 是七屏里唯一**未清零**的一屏
  （契约第 6 条钉「≤ 现值 4 且不新增」）。
- **拍照翻译**：页头共享（旧「← 返回 AI 学」药片下线）；常驻 5 块 → 「历史（N 条）」+「离线备用模型」
  两枚 `EntryRow` 折叠行（`expanded` 均默认 `false`，展开体不组合；隐私句搬进模型展开体），
  成本句只贴在「翻译成中文」下一行、引擎标签全局一处；历史逐条 `SoftCard` 内三行 + `PillStyle.Text`
  删除，只发 `onRequestDeleteSaved`，宿主持第三枚确认框；`Mint*` 56 → 0、`typography` 19 → 0、
  `FontWeight` / 裸 `Button(` / `OutlinedButton(` / `TextButton(` 全 0、`OutlinedTextField(` 恰 1；
  该屏此前在 `androidTest` 侧**零覆盖**，本次是它第一份真机语义树证据。
- **长难句**：页头共享（标题进页头、说明句进 `subtitle`，`headlineSmall` 退役）；`SegmentList` 行 `Card`
  → `SoftCard`；结果区补「复制译文」（`LocalClipboardManager`，零新依赖）与「朗读原文」
  （宿主形参 `speakMessage` / `onSpeak`，屏内 `pronunciationProvider` 与 `TextToSpeech` 均 **0**，
  宿主带代次校验，先发的那次不许覆盖后一次的反馈）；费用句只在成本行讲一次，出站确认收窄为
  「你的句子与该服务的密钥将发送给 $host。」；`Mint*` 24 → 0；主操作分布
  `PillStyle.Primary` 2 / `Secondary` 1 / `Text` 2。
- **AI 生图**：页头共享；只读 `PromptCard` → `SoftCard` 内 `OutlinedTextField`（`image_studio_prompt`），
  本地态 `editedPrompt` 由 `rememberSaveable` 承载、初值 `state.prompt`；「生成图片」把**改过的那句**
  交给 VM（VM 增参 `generateImage(promptOverride: String? = null)`，非空即覆写 `pendingPrompt` 再组请求体）；
  「生成画面描述／生成图片」同一槽位随状态换身份，生成键吸底；`Mint*` 27 → 0、`typography` 5 → 0；
  出站确认四枚 tag 全保留，另钉 `GlassDialog(` 恰 1、`Card(`/`CardDefaults` 0、六枚状态锚各 1 处。
- **AI 问词**：页头共享；屏体序改为「页头 → `word_qa_lemma` 一行 → `when(state)`（回答 + 保存为笔记）
  → 三枚问法 chip → 「我的笔记」`SoftCard` → 「已保存笔记」`SavedNoteList`」；`askKinds` 提为文件顶层常量
  （`Breakdown` 天然第一枚，`WordQaKind.Personal` 照旧不出药片）；`Breakdown` 取 `PillStyle.Primary`、
  其余两枚 `PillStyle.Text`（`PillStyle.Primary` 全文件恰 2 处）；已保存笔记改 `BareRow`，
  `FailureCard` 描边 `Card` → `SoftCard`，该文件 `Card(` / `Button(` / `MaterialTheme.typography` / `Mint*` 全 **0**。

## 3. 验证记录

### 3.1 全量门禁（Step 1）

```
& $g :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin `
    verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
```

结果：**BUILD SUCCESSFUL**

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest` | **1958 tests / 0 failures / 0 errors / 0 skipped**（266 个 suite xml 聚合实测） |
| `:app:assembleDebug` | 通过 |
| `:app:compileDebugAndroidTestKotlin` | 通过 |
| `verifyThirdPartyNotices` | `Validated 76 third-party notice entries.`（与批 3 一致，本批未引入依赖） |
| `verifyAcTraceabilityMatrix` | `AC matrix covered 56 ids with evidence.`（与批 3 一致） |

条数轨迹（每步都是实跑数）：

| 里程碑 | tests | 增量 |
|---|---|---|
| 批 3 收口 | 1906 | — |
| Task 1 AI 主页 | 1913 | +7 |
| Task 2 骨架页 | 1920 | +7 |
| Task 3 AI 服务 | 1928 | +8 |
| Task 4 拍照翻译 | 1935 | +7 |
| Task 5 长难句 | 1942 | +7 |
| Task 7 AI 问词（先于 Task 6 开工） | 1949 | +7 |
| Task 6 AI 生图 | 1958 | +9（7 契约 + 2 条 VM 出站体） |

XML 数 259 → 266（每枚新契约一枚 suite）。

### 3.2 模拟器 connected（只 `emulator-5574`，`am instrument` 逐类跑）

十类**逐类全绿**（`font_scale` 1.0，**未使用** `connectedDebugAndroidTest`）：

| 类 | 结果 | 类 | 结果 |
|---|---|---|---|
| `AiLearningScreenTest` | 18/18 | `ImageStudioScreenTest` | 12/12 |
| `AiFeatureScreenTest` | 8/8 | `WordAiQaScreenTest` | 10/10 |
| `AiProfileSettingsScreenTest` | 24/24 | `AppScreenTest` | 51/51 |
| `PhotoTranslationScreenTest` | 8/8 | `WordCardScreenTest`（回归） | 18/18 |
| `SentenceAnalysisScreenTest` | 9/9 | `TodayPlanScreenTest`（回归） | 24/24 |

十类合计 **182 例，0 失败**。另：`AiBadgeContrastFixTest` 12/12、`AiMascotTest` 7/7、
`GlassPanelThemeInkTest` 8/8、`ThemeModeSwitchTest` 4/4、`EntryRowTest` / `AppScreenHeaderTest` /
`NotConfiguredCardTest` / `OutboundConfirmDialogTest` 同批全绿。

### 3.3 大字号专项（Step 3）

`settings put system font_scale 2.0` → 十类逐类重跑，**暴露两处真红、均已修且判据未放宽**：

| # | 用例 | 2.0 下的真红 | 修法 |
|---|---|---|---|
| 1 | `SentenceAnalysisScreenTest.analyzedOffersCopyAndSpeakAndSpeakReportsTheWholeSentence` | 朗读药片落到首屏外，点击静默落空 → 上报值恒为 `null`（期望 `Birds fly. birds`） | 点前 `performScrollTo()`（把「滚到能点」补成显式步骤） |
| 2 | `AppScreenTest.wordBankSystemBackClosesAnOpenWordDetailBeforeLeavingTheBank` | 词行变高，行心落点压到行尾「标记为已掌握」药片 → 详情没开 | 点击改按行左 20% 宽度 `performTouchInput { click(Offset((width * 0.2f).coerceAtLeast(1f), centerY)) }`，仍点同一枚 `my_words_wordbook_row_0` |

修后五屏断言逐条绿（① AI 服务三组弱卡 + 吸底保存不遮末组 ② 拍照翻译折叠行展开后全部可达
③ 长难句复制/朗读可点 ④ 生图描述框可改 + 吸底键不遮输入 ⑤ 问词主操作与回答不裁切）。
`font_scale` 已恢复 1.0（模拟器与真机均已复核）。

**已知抖动（未改实现）**：2.0 全类跑时 `AppScreenTest` 第 349 行（`pressSystemBack()` 后
`card_detail_screen` 仍存在）红过一次；单跑 3 连绿、整类重跑绿 → 判为 `sendKeyDownUpSync(KEYCODE_BACK)`
与 `createComposeRule` 的时序抖动，与本批改动无关（同形抖动见 `docs/handoff/2026-10-15-*`）。

### 3.4 真机目视（Step 4，`bf353dda` / M2102J2SC，只 `adb install -r` + 手看，未 instrument）

| 屏 | 结论 |
|---|---|
| AI 主页（Task 1） | 副标题中性、三行能力无卡片汤、「查看词表」一条裸行带 `ChevronRight` ✅ |
| 骨架页（Task 2） | 两枚按钮均在首屏，页末一句小字说明 ✅ |
| AI 服务（Task 3） | 三组弱卡、保存吸底常显、清空密钥与删除配置各出确认框 ✅ |
| 拍照翻译（Task 4） | 进屏即见主流程；历史与离线模型各一行、默认收起；成本句一行 ✅ |
| 长难句（Task 5） | 复制/朗读可用，费用句只一处 ✅ |
| AI 生图（Task 6） | 描述框可改、生成键吸底不遮输入 ✅ |
| AI 问词（Task 7） | **未覆盖**（见下） |
| 词库合并页 / 词卡详情 / 词卡翻面（回归） | 版式无回归 ✅ |

**未覆盖**：AI 问词覆盖层。入口只在「词卡详情 / 文章详情」两条路由上——本机文章未解锁；
词卡复习的评级键被 `state.submitting` 卡住点不动（数据/会话态，与版式无关）；另 `AppScreen.kt:723`
该分支传给 `CardDetailScreen` 的 `onAskAi = null`，词卡详情里的「问 AI」入口本就不出。
该屏版式由 `WordAiQaScreenTest` **10/10** 在模拟器覆盖，真机像素证据登记为**待复看项**（§9-1）。
截图存 `%TEMP%\opencode\dev_qa_*.png`（18 张）。

## 4. 改期望值清单（旧值 → 新值 + 依据）

> 只登记**判据本身变了**的地方；新增判据见 §7。本机无 `git`（不在 PATH），无法出 diff，
> 依据取各测试文件内的同批留档注释（每条改动都写在被改断言上方）与计划文件各 Task 验收段。

| # | 文件 | 旧值 | 新值 | 依据 |
|---|---|---|---|---|
| 1 | `SharedComponentUniquenessRatchetTest.headerSeed` | 页头族 seed 7 枚（含六屏 AI 屏） | 摘 6 行，**仅剩 `LicensesScreen.kt`** | 六屏 headline/`contentDescription = ` 读点归零，逐屏由各自契约第 1 条钉住（摘行门 `removing_a_file_from_the_header_seed_requires_its_headline_reads_to_be_cleared` 同一次跑通过） |
| 2 | `SharedComponentUniquenessRatchetTest.headerSeedRetentionReason` | 「六屏需先扩组件 API」 | 改写为「批 4 已按批 3 口径迁移完毕，仅剩 Licenses 一枚」+ 逐屏摘行理由（含 `AiLearningScreen` 不调共享页头的原因、`WordAiQa` 单词行未塞 `subtitle`） | 理由文本与 seed 必须同步，否则留档撒谎 |
| 3 | `ContainerContractTest.screens` | 12 屏 | **19 屏**（七屏逐屏并入；Task 3 后 15、Task 5 后 17、Task 6 后 18、Task 8 复核补 `WordAiQa` 到 19） | 本批七屏纳入容器三档判据；三条几何判据与三档各恰一份定义**逐字未动** |
| 4 | `AppScreenOverlayContainerContractTest` | `ConfirmDestructiveDialog(` 全文件 4 / 主体 3 / 路径 A 取样 4；浮层 8 枚 | **7 / 6 / 7**；浮层 **9 枚** | 新增三条危险确认（清空密钥 / 删除配置 / 删除拍照翻译历史）；`PillButton(` 在 AppScreen 仍恰 3 枚（确认框自带药片，未新增手搓按钮） |
| 5 | `DarkTokenConsumptionRatchetTest` | `AiProfileSettingsScreen.kt` 两处台账各 1 | 两处**摘行** | `SectionCard` 换装后静态读归零（只减不增）；`AiLearningScreen.kt` 保持 **2**（白胶囊不动，R-23.14） |
| 6 | `AiProfileLayoutContractTest` | 计划稿 7 条 | **8 条** | 落地时把「列表页行容器改 `BareRow` + `Card(` 为 0」独立成第 8 条（原与第 2 条分组判据合写会漏掉列表页） |
| 7 | `GlassPanelInkPairingContractTest.migratedPanels` | 3 | **2** | AI 服务编辑器面板从 `GlassDialog` 家族迁出，墨色对改由 `AiProfileLayoutContractTest` 承担 |
| 8 | `DangerInkPlacementContractTest`（AiSpeech 取样面） | 全文件计数 | **收敛到出站确认框区间** `outboundDialogSpan`（取消 tag 到确认 tag 两侧各一窗），区间内仍须恰 2 处 + 加「两枚 tag 存在且后者在前者之后」防切空 | 拍照翻译入口行徽标同属 `AiSpeech` 域，全文件计数会被顶到 4 以上；区间判更严 |
| 9 | `DangerInkPlacementContractTest`（AiProfileSettingsScreen 墨点） | 旧计数 | 按「清除密钥/删除配置移出屏内」后的新值同步 | 仍要求「恰 N 处，多一少一都红」，未放宽 |
| 10 | `AiBadgeContrastFixTest`（两处） | ① 取样面只认 `AppGlyph(` ② `mintPrimaryButtonCall` 只认 `MintPrimary` | ① 并集 `(?:AppGlyph\|InlineIconBadge\|EntryIconBadge)\(`（同一枚调用内 `AppIcon.Spark` 必须在实参里 + 反空转断言） ② 扩到 `MintPrimary` 或 `appDomain(AppDomain.AiSpeech).base` | 头卡徽标换共享 `InlineIconBadge` 后旧取样面会把「换掉」误判成「删掉」；两套拼法是同一个值。**前景分档与两套亮度复算一字未松** |
| 11 | `AiLearningScreenTest`（词表入口） | `ai_learning_nav_detail` 在渐变套卡内 + `ai_learning_word_list_count` | 套卡 tag 退役并正向钉 0，新锚 `ai_learning_nav_row`（恰 1 处 + `onClick = onOpenWordList` + `wordListLine(` 同段），词数改 `assertTextContains("今日新词 12 个 · 待复习 5 个")` | 「查看词表」直达词库；同一段文字不留两个真值来源 |
| 12 | 六屏 connected 返回锚点（`AiFeature` / `SentenceAnalysis` / `ImageStudio` / `WordAiQa` / `PhotoTranslation`新 / `AiProfile`） | 各自 `*_back` tag + 「← 返回 AI 学」/「← 返回上一层」/`PillButton("← 返回 AI 服务")` | `onNodeWithContentDescription("返回")`（覆盖层内另加 `hasAnyAncestor(hasTestTag("word_qa_screen")))` 限定）+ 「旧文案必须为 0」正向断言 | 共享页头返回位固定「返回」+ `app_screen_header`；问词是叠在词义详情之上的同构页，裸锚会撞「找到 2 枚」 |
| 13 | `AiProfileSettingsScreenTest` | 保存键在流内；无确认框用例 | 保存键在吸底条仍可点；新增「删除配置 / 清除密钥点击先出确认框、取消不发回调」；夹具 `setScreen` 增 `onRequestDeleteProfile` / `onRequestDeleteKey` 形参（12 形参） | 危险动作外迁到宿主 |
| 14 | `AppScreenTest` 词行点击落点 | 行心 `click(center)` | 行左 20% 宽度（见 §3.3） | 2.0 下词行变高、行心压到行尾药片；仍点同一枚 tag |

**Task 1 / Task 2 未删除任何既有断言**：两屏的 connected 文件内没有「旧→新」改值留档以外的删减，
旧锚逐条配「必须为 0」正向断言（净增，不是净减）。

## 5. 页头族 seed 摘六行的理由

摘行不是「补一行 `AppScreenHeader(` 就准删」：`each_family_offender_set_equals_its_seed_exactly` 双向判，
摘行必须与「该文件 `MaterialTheme.typography.headline` 读点归零」在同一次跑里同时成立。

| 文件 | 摘行理由 |
|---|---|
| `AiLearningScreen.kt`（T1） | 退役的是 `headlineSmall` 读点而非页头调用：hero 整块仍由 `AiHeaderCard` 承担，标题换 `AppType.Headline` 后 `headline` 读点为 0 |
| `AiFeatureScreen.kt`（T2） | 自写 `TextButton("← 返回 AI 学")` + `headlineSmall` 标题 → 共享页头（徽标搬进 `badge` 槽）；读点归零 |
| `PhotoTranslationScreen.kt`（T4） | 自写返回药片 + `headlineSmall` → `AppScreenHeader(拍照翻译, onBack)`；`Mint*` 56 → 0、`typography` 19 → 0 |
| `SentenceAnalysisScreen.kt`（T5） | 自写返回 + `headlineSmall` → 共享页头（标题进页头、说明句进 `subtitle`）；读点归零 |
| `ImageStudioScreen.kt`（T6） | 自写返回 + `headlineSmall` → `AppScreenHeader(AI 生图, onBack, subtitle = "描述可以改")`；读点归零 |
| `WordAiQaScreen.kt`（T7） | 自写 `Button("← 返回上一层")` + `headlineSmall` → 共享页头；单词说明改页头下一行独立 `Text`（未塞 `subtitle`，理由见 §6-附）；读点归零 |

`LicensesScreen.kt`（`‹` 方向字形）**仍留档**：它的 headline 读点不是页头，换 `AppScreenHeader` 会改字形/语义，属批 5。

> **附：AI 主页为什么是 0 处 `AppScreenHeader(`** —— 计划稿 Step 3.1 给的 `AppScreenHeader(hero = true, badge = …)`
> 未被采用：本屏 hero 已由 `AiHeaderCard`（mascot + 状态胶囊）承担，再加一枚 28sp 标题会两枚大标题撞进同一屏，
> 且 connected 侧 `onNodeWithText("AI 学")` 会从单节点变多节点（`AiLearningScreenTest:58`、`AppScreenTest:502/667`）。
> 故本屏`AppScreenHeader(` 计数**钉为 0**（是判据，不是遗漏）。

## 6. 六处对账的落地结果

| # | 对账项 | 落地结果 |
|---|---|---|
| 1 | AI 主页中性副标题 | ✅ 落地为「这里按真实进度列出 AI 能力：能用的直接进，没写完的写明差什么。」；契约第 2 条钉全句 + 旧句为 0 |
| 2 | 「查看词表」直达词库 | ✅ 渐变套卡退役，`ai_learning_nav_row` 一条裸行直调 `onOpenWordList`；套卡形状为 0 已正向钉 |
| 3 | 14 个输入分 3 组 | ✅ 按可交互控件口径为 **13** 枚（差的「清除已保存的密钥」是动作按钮），分 3 组 `SoftCard` |
| 4 | 三处危险确认 | ✅ 屏内零对话框，全发回调由宿主 `ConfirmDestructiveDialog` 承载；宿主计数 4 → **7**、主体 3 → 6、路径 A 4 → 7；三条文案过 `ConfirmDestructiveWordingTest` |
| 5 | 拍照翻译 5 块收两行 | ✅ 「历史（N 条）」+「离线备用模型」两枚 `EntryRow`（默认收起），隐私句进展开体，成本句/引擎标签各一处 |
| 6 | 生图描述可编辑（唯一越出呈现层） | ✅ **已照准并落地**：`generateImage(promptOverride: String? = null)`，`ImageStudioViewModelTest` +2 条钉「出站体里的 prompt 就是改过的那句」与「未传参回落到模型稿」；确认门、`AiOutboundConfirmation` 形状、提示词策略零改动 |

### 三处规范性让步（须显式登记）

1. **AI 问词「4 枚按钮」**：`WordQaKind` 四档里 `Personal` 已被屏侧 filter 掉（笔记走独立输入区），
   本批按「3 个问法 + 1 个保存笔记」讲主次，**不改枚举**（改枚举＝改请求策略）。
2. **AI 问词「单词说明」不进页头 `subtitle`**：共享 `AppScreenHeader` 的 `subtitle` 是纯 `String`、无标签槽，
   塞进去必须改共享组件（越出「只动呈现层」）。改页头下一行独立 `Text` 承载，tag 不变。
3. **长难句「费用句只讲一次」的判读**：常驻成本句保留「并可能产生费用」，出站确认句收窄为
   「你的句子与该服务的密钥将发送给 $host。」——同一事实在屏上仍出现两次（成本行 + 确认框），
   但确认框职责改为「发给谁、发什么」，成本披露在点按钮之前已常驻可见。若审核席要求字面「只出现一次」，
   需改的是常驻成本句（会削弱披露时点），本批取「保留披露、收窄确认文案」。

## 7. 净增判据统计

| 来源 | 条数 | 说明 |
|---|---|---|
| 七枚新契约 | **+50** | `AiLearning` 7、`AiFeature` 7、`AiProfile` **8**、`PhotoTranslation` 7、`SentenceAnalysis` 7、`ImageStudio` 7、`WordQa` 7 |
| `ImageStudioViewModelTest` | **+2** | 出站体「改过的句子」/「未传参回落」各 1 |
| **JVM 合计** | **1906 → 1958（+52）** | 与「50 + 2」完全吻合 |
| connected 新增 | **+14** | 新建 `PhotoTranslationScreenTest` 8、`SentenceAnalysisScreenTest` 7→9（+2）、`ImageStudioScreenTest` 11→12（+1）、`WordAiQaScreenTest` 8→10（+2）、`AppScreenTest` 50→51（+1） |
| 删除判据 | **0** | 无 `assertTrue(true)`、无放宽到 `>=`、无删断言；退役的六枚旧返回锚各配一条「旧文案/旧 tag 必须为 0」 |

## 8. 未执行项

- **git 提交**：Task 1–8 的「登记」步骤全部登记为未执行——本机 `git` 不在 PATH，无法暂存/提交/出 diff。
  全文以计划文件 + 本报告为权威账。
- **AI 问词覆盖层的真机目视**：见 §3.4「未覆盖」。

## 9. 遗留议题

1. **`WordAiQaScreen` 真机目视待复看**：需先解锁一篇文章（或让词卡复习进入可评级态）才能在真机走到
   「问 AI」入口；另 `AppScreen.kt:723` 给 `CardDetailScreen` 传 `onAskAi = null`，词卡详情这条路走不通。
   这是**入口可达性**问题（数据/会话态 + 路由），不是版式问题，本批不动；如需真机像素证据，应另开一条
   「AI 问词入口可达性」任务（会动会话态/入口，越出本批边界）。
2. **`AppScreenTest` 的 back-key 时序抖动**：`pressSystemBack()` 与 `createComposeRule` 的组合在
   `font_scale 2.0` 全类跑时偶发（§3.3），单跑与整类重跑均绿。已登记，未改实现——若下批再现，
   应考虑在夹具里等待 `onNodeWithTag(...).assertExists()` 到稳定态，而不是加 `Thread.sleep`。
3. **`AppScreenTest` 的「单方法连测噪声」**（非本批引入）：`returningFromAiProfiles*` /
   `aiProfilesOpenedFromSpeech*` 以 `class=…#method` 单跑会红、整类跑绿。与本批改动无关，仍在案。
4. **`Mint*` 静态桥的最后一屏**：`AiProfileSettingsScreen.kt` 仍保留 4 枚 `Mint*` 读点（契约钉「≤ 4 且不新增」），
   是七屏里唯一没清零的一屏；清零需要先把这 4 处的语义映射到 `appColors()/appDomain()` 且不破
   `AiBadgeContrastFixTest` 的 `MintPrimary` 墨链，建议单开一条小任务。
5. **白胶囊两枚静态墨仍占 `DarkTokenSeedAllowlist = 2`**：本批按约束「不动白胶囊」保持原状，
   `AiBadgeContrastFixTest` 12/12 全绿。要摘掉这条 seed 需重做头卡胶囊取墨，属视觉口径变更。
6. **hero 页头形态缺口**：AI 主页证明「hero 大标题 + 状态胶囊」塞不进现有 `AppScreenHeader`（无 `hero` 语义的
   双标题会撞屏）。若后续要让 tab 根页也统一走共享页头，应先给组件补一个 hero 档，而不是在屏侧继续自绘。

## 10. 收口后补记（2026-10-31 用户评审三条）

真机目视后用户提了三条（底导未选中态仍是旧矢量线框 / 浅色模式「已学单词连续天数」卡底过深 /
学习页「四级词汇 · 开始学习」卡底过深），已按组件级修好并补齐判据：

- `InlineIconBadge` 增 `backdrop` 开关，底导两档画同一枚插画、未选中只 `alpha(0.5f)`；
- `StrongCard` 浅套改 `tint2` 底 + `text1` 墨（深套不动），屏内一律读 `LocalContentColor.current`；
- 「旧深墨底不许回流」的反向判据改成卡底三点取样，先后序判据改用 `positionInRoot`，
  并把「宿主窗口横屏会造成整屏裁零假红」写进决策文。

裁决、判据清单与真机读数见 `docs/decisions/2026-10-31-ui-review-three-rulings.md`；
全量 JVM 现值 `266 XML / 1961 tests / 0 failures`（本批收口读数 1958，差额 3 条为这三条反馈新增的判据）。

真机 `bf353dda` 浅套取证（`am force-stop` + `monkey` 冷启，系统 `font_scale 1.0`、竖屏锁定）：

| 截图 | 取证点 |
|---|---|
| `docs/design/app-today-plan-strongcard-light-20261031.png` | 学习页「四级英语词汇」强档卡已是 `tint2` 浅薄荷底 + `text1` 墨，底比主 CTA 轻；底导四格同一枚插画（选中带药片底、未选中半透明） |
| `docs/design/app-profile-statcard-bottombar-light-20261031.png` | 「我的」页三格统计卡同一配方（已学词 / 连续天数 / 文章），卡内正文墨随容器；底导「我的」选中态与另三格同族 |

深套未取新截图：`StrongCard` 深套分支代码一字未动，`ThemeModeSwitchTest` 4/4 真机已覆盖。
