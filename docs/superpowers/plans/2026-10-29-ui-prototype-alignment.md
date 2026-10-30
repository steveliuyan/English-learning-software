# UI 原型对齐计划（2026-10-29）

## 目标与权威源

把 Android 应用的 39 个界面与交互对齐到已冻结的 HTML 原型：

- 原型目录（只读，禁止修改）：
  `C:\Users\steveliuyan\AppData\Roaming\Open Design\namespaces\release-stable-win\data\projects\6a0aa00b-6bfc-43e7-b35f-13f7478d3147`
- 权威文件：`index.html`、`assets/app.css`、`assets/app.js`、`assets/data.js`、`assets/icons/*.png`、`assets/fonts/*.woff2`
- 冲突裁决：原型 > 本文描述；原型未画到的状态先问用户，不自行补。
- 范围外：FSRS 排期、备份、AI 请求协议、权限、依赖。只在接界面时读/写既有接口。

## 批次

| 批 | 内容 | 验收 |
|---|---|---|
| 0 | 令牌层：颜色 / 排印 / 形状 / 动效 / 字体 / 图标贴纸 / 共享组件 | JVM 契约全绿 + 共享组件样例可编译；不逐屏验收 |
| 1 | 学习路径 11 屏 + 词卡反馈/回执交互 | 逐屏对照原型截图 |
| 2 | 阅读 5 屏 + 我的 8 屏 + 其他 5 屏 | 逐屏对照 |
| 3 | AI 助学 10 屏（含未接入控件禁用态） | 逐屏对照 |
| 4 | 全量 QA：深浅两套、fontScale 2.0、TalkBack、动效减弱、真机走查 | 门禁 + 真机清单 |

## 推进决定（2026-10-29 用户拍板，覆盖上面的分批口径）

1. **不再逐批停下等验收**：剩余 28 屏（+ 3 屏的未对部分）**一次推平**，按本文「全 36 屏对账清单」的 1→36 顺序做，
   门禁与工作报告放到最后统一出（原 #14 批 1 门禁 → 现全量门禁）。中途只在**原型没画到的状态**上停下来问。
2. **原型画了、本仓没有承载点的五处，按原型新建**（用户 2026-10-29 两轮拍板）：
   独立「关于」页（`sMeAbout`：图标 + 版本 + 一句话说明 + 「说明」三行（隐私说明 / 开源许可与来源 / 备份与恢复）+
   「致谢」`card-quiet`）、「页面不可用」页（`sUnavailable`：`emptyState` + 两枚 CTA）、
   以及学习链路里核到源码才发现没有承载点的三屏——**「今日词单」（`sLearnQueue`）、「复习队列」（`sReview`）、
   「今日完成」（`sSummary`）全部新建并接进导航**。这是本轮唯一获授权的**新增页面**（五屏），
   除此之外不扩功能范围。
3. `sSummary`「今日完成」的三枚统计（`个词 / 用时 / 一次答对`）是原型 mock：做到那一屏时先核本仓有没有真数据，
   读不到的那枚**整枚不显示**（Task 12 第 6 行同口径），不编造指标；**新增「用时 / 一次答对」的采集属功能改动，未获授权，不做**。
   同理 `sReview` 的「复习方式 · 听发音，选释义」本仓没有这一能力，按「无后端就隐藏」处理。
4. 每屏仍按 AGENTS.md 的 TDD 推进：先读原型函数与 CSS 取值 → 写/改判据（红）→ 实现（绿）→ 记台账。
   connected 只在模拟器 `emulator-5574` 跑，真机 `bf353dda` 里程碑装机目视。

## 批 0 步骤

1. **红**：改写/新增 JVM 契约测试，钉住本批 token 与共享组件结构：
   - `DesignTokenContractTest`：新 `AppColorSet` 字段与取值、域色、`AppPalette` 同步。
   - `MaterialThemeAssemblyContractTest`：M3 角色新映射、`AppShapes`、无裸 hex。
   - `AppTypeContractTest`（新）：字号/字重/字距/行高/字族与 M3 派生的逐档钉值。
   - `AppMotionContractTest`（新）：160/220/300 与两条缓动。
   - `AppStickerContractTest`（新）：32 张 `sticker_*.png` 存在、尺寸 256、`res/drawable-nodpi` 路径。
   - `AppFontContractTest`（新）：Newsreader 5 个静态字重 + OFL 许可登记。
   - `SharedComponentContractTest`（扩展）：贴纸徽标 / 卡片 / 行 / 分段 / 芯片 / 开关 / 选择行 / 提示 / 空状态 / 双段进度条。
2. **绿**：按测试实现 `AppColors`、`AppPalette`、`AppType`、`AppShape`、`AppMotion`、`MintGlassTheme`、共享组件。
3. **字体**：用 `fonttools` 从原型 woff2 生成静态 TTF（400/500/550/600/650），落 `res/font/newsreader_*.ttf`；登记 SIL OFL 1.1。
4. **贴纸**：32 张 256×256 PNG 拷进 `res/drawable-nodpi/sticker_<name>.png`，代码按资源名引用，不联网。
5. **门禁**：`assembleDebug → testDebugUnitTest → compileDebugAndroidTestKotlin`（本机 Gradle 8.13，`exit ${PIPESTATUS[0]}`）。
6. **报告**：`docs/handoff/2026-10-29-ui-prototype-batch-0-report.md`，含对照表、实测对比度、未决项。

## 迁移策略

- 既有 57 个屏在批 0 不重写；保留同名兼容字段（`surfaceGlass`、`tint3`、`onMint`、`onBrand`、`errorFill`）以免半迁移屏编译失败，批 1–3 逐屏改用新 token，批 4 删除兼容字段。
- `AppType` 六档保留名字、换成原型度量；新增 `Eyebrow / Caption / Meta / BodyLg / Lemma / StatNum / Prose / ProseZh / NavLabel / StrongTitle / ArticleTitle / PlanNum` 供共享组件与新屏使用。
- M3 的 15 个排印角色由六档派生；M3 颜色角色改映到新 token，不再使用旧 `onMint / errorFill` 语义。

## 已知冲突（需在报告点名）

- 原型 `--fuzzy #A8792A` 压 `--surface #FFFFFF` 实测 3.87:1，低于正文 4.5:1；原型为权威，先照原值落地并记录，待用户裁决是否调深。
- 原型 `--ink-3` 压 `--bg` 浅套 4.39:1，略低于 4.5；`ink-3` 只用于 11–13sp 辅助文字，记录实测。
- 原型没有「设置」域色；现有 `AppDomain.Settings` 由 `--ink-3` 派生，批 1 屏迁移时按原型逐处核对。
- 原型的 AI 配置项（temperature / max_tokens / stream / 语音 / 生图参数）超出允许的请求协议；按用户裁决 B 画布，禁用未接入控件并标注「暂未接入」。

## 证据与门禁

- 每个 token 断言都从源码文本或真实 `Color` 复算，不靠注释。
- 对比度一律按 WCAG 相对亮度当场计算，结论写实测值，不写「合格」。
- 单测基线只增不减；报告 §3 对照基线写清新增/下降。

## 批 1 逐屏偏离台账

批 1 的验收是「逐屏对照原型」，凡是**故意不对齐原型**的地方都要在这里留一行，报告（#14）从这里抄，
不靠会话记忆。判据一律指向具体的测试名，不写「已测试」。

### Task 12 · 学习设置屏（原型 `#/plan-edit` + `#/card-settings`）

| # | 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 原型两屏合成**一屏**，页名取 `学习计划` | `sCardSettings` 可迁移的只有「评分之后怎么走」三枚开关，而这组开关本来就住在计划屏；拆两屏等于给一屏一主操作长出第二个页头与第二条保存路径 | `LearningSetupLayoutContractTest.the header is the prototype plan title with its save action` |
| 2 | 隐藏的节：`习惯`、`自评时先看哪一面`、`卡面上显示`、`预览` | 本仓没有对应能力，画出来就是死控件（用户口径：不编造功能） | 同文件 `the sections without a real backend stay hidden` |
| 3 | 「始终」两行改成**真开关**（原型 1 枚开关 + 2 行「始终」→ 本屏 4 枚开关）；副文案随之由「默认**一直**开启」改成「默认开启」 | 三档反馈各有真字段在驱动词卡屏（`WordCardViewModel` 的 `openDetailOn*`），写「始终」是假话；既然能关，「一直开启」就成了假话 | 同文件 `the reveal section is one card holding the three rating rows` |
| 4 | 术语「忘记」一律叫「**不认识**」 | 与词卡屏三档口径一致（批 1 Task 10 已定） | 同文件 `the third feedback tier is called unknown like the word card does` |
| 5 | `每日新增` 用四枚 chip（3/5/10/20）替换旧拖动滑杆 | 原型就是 `chip(n + ' 个')`；旧实现是 1–50 连续滑杆，档位是假精度 | 同文件 `the daily new target is the prototype chip row` |
| 6 | 预估句改成**绑真数据**（读不到剩余词量整句不显示） | 原型样例自身不自洽（4308 ÷ 4 = 1077，不是 756）；照抄就是编造指标 | `PlanEstimateTest` + 同文件 `the daily new section states the current value and a real-data estimate` |
| 7 | 走向句按真机按钮改写（「下一个单词」= 详情页 / 回执上「下一个词」）并删掉后半句 | 原型那半句承诺「标记「忘记」的词会在本轮末尾再出现一次」，与「今日计划是不可变快照、一次反馈即完成」对冲 | `PlanEstimateTest`（`revealCaptionText` 两组） |
| 8 | 通知条后半句改写为「换词书会立刻重排今日词单，调整每日量从次日生效」 | 原型那句「今日词单会立刻按新计划重排」在真机可证伪（`GetOrCreateTodayPlanUseCase` 只在同本地日 + 同活动词书复用快照；AGENTS.md：设置默认次日生效） | 同文件 `the info notice tells what saving actually does` |
| 9 | 节标题走共享 `SectionHeading`（原型 `.sec-label` + `.sec-note`） | 屏侧手搓 `AppType.Title` 就是第二套分组排印 | 同文件 `every group heading is the shared eyebrow heading and the count is the prototype's` |
| 10 | **复习上限存而不排**：滑杆落库（Room v33），`dueTarget` 生成逻辑零改动；原型那句「到期词超过上限时，先复习最久没见的」**改写成不承诺口径**「当前版本先记下这个上限，今日复习仍会排完全部到期词。」 | spec 01:28 明写 V1 不设复习硬上限，而用户不许这一轮动 FSRS 排期；照抄原型那句就是空头承诺。**用户 2026-10-29 裁决：先把文案改成不承诺**（等真接进排期再回到原型原句） | `docs/decisions/2026-10-29-plan-review-limit-preference.md`、`LearningSetupLayoutContractTest.the review limit keeps the prototype number and range`（正反两条：新句逐字在、旧承诺全文件归零）、`AppDatabaseMigrationTest.migrateV32ToV33_addsDailyReviewLimitWithBackfillWithoutLosingRows`、`BackupSnapshotBuilderTest.learning settings payload carries the stored review limit and legacy payloads still decode` |
| 11 | 「选择词书」那一节仍留在本屏（原型把它画成独立屏 `#/book-picker`） | 本屏的入口就是「我的 → 词书与每日目标」，换词书与调目标在同一条保存路径里；拆成两屏要新增一次导航与第二条保存语义。选词书**行的形状**已按原型翻正（见下面 Task 13-A 一节），独立屏的搜索 / 排序 / 词书详情仍留到 Task 15 | 同文件 `the word book list is the prototype pick radio rows`（旧的两列色块两条判据随版面作废，见 Task 13-A 第 1 行） |
| 12 | 复习上限的轨道色取**全局品牌色** `brandDeep`，不走批 5 的「深套换域色」三元式 | 原型 `assets/app.css:761` 的 `.range { accent-color: var(--brand-deep) }` 与 `:757` 的 `.switch[aria-checked='true']` 同一条品牌深绿，它不是一屏一域色的成员；`brandDeep` 两套主题各有值（浅 #1A7A63 / 深 #7FE3C1），深套压在 `surface` 上本就可见。为此 `SwitchTrackColorGuardTest` 的合法取值由两种增为第三种 | 同文件 `the review limit keeps the prototype number and range`（三条轨道断言 + 一条「屏侧不许再写 `LocalIsDark`」）与 `SwitchTrackColorGuardTest.the track site ledger matches the source in both directions` |
| 13 | 滑杆**拇指几何**未对齐：Compose M3 1.4 画成竖条 + 与轨道留缝 + 步进刻度点，原型是浏览器默认的圆点、无刻度 | 原型那根 `.range` 的 CSS 只有 `accent-color` 一句，形状是 `<input type=range>` 的浏览器默认渲染，不是设计决定；改成圆点要覆写 `thumb` / `track` 槽，会连带改到语音合成页那枚滑杆。**用户 2026-10-29 裁决：保持现状，只记台账** | 截图对照 `.git/qa-scratch/setup-slider-branddeep.png`（浅）/ `setup-slider-dark3.png`（深）vs `proto-plan-edit.png`（本机 scratch，未入库） |

### Task 13-A · 选词书行列表（原型 `#/book-picker` 的 `.pick.pick-radio`）

| # | 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | **批 5 spec §5 那句「选词书 2 列色块卡（域色 tint + `Book` + 书名 + 词数）」作废**，改回原型的通栏单选行（22dp 圆标记 + 书名 + `N 词 · 已学 M` + 可选「使用中」，行间一条 `line-soft`、首行无线） | 原型全篇没有任何**词书**网格画法：`app.js:587-606`（`sBookPicker`）与 `app.js:1467-1487`（`sOnboarding2`）用的是同一个 `.pick.pick-radio`，`app.css` 里 `grid-template-columns` 0 命中（`app.js` 另有 5 处内联网格——AI 主页卡片格与三列统计格，没有一处是词书，所以「原型没有网格」不能当依据用）。**用户 2026-10-29 裁决：以原型为准，spec 那句话翻掉**，作废指针就地落笔见 `docs/decisions/2026-10-29-word-book-picker-rows-supersede-tiles.md` | `LearningSetupLayoutContractTest.the word book list is the prototype pick radio rows`（两列机械形状 `chunked(` / `weight(1f)` / `WORD_BOOK_TILE_*` / `wordbook_tile_` 等四个旧锚前缀逐条钉成 0）+ connected `WordbookPickerHeaderTest.the_picker_lays_word_books_out_as_full_width_stacked_rows`（通栏 + 缝只 1dp + 线只在非首行，正反两条腿） |
| 2 | 删除入口 = **行上长按**（原型 `.pick` 没画任何删除控件，也没画滑删） | 本仓只有这一条删除路径，撤掉等于砍掉功能；补一枚原型没有的按钮又违反「不补原型里没画的状态」。**用户 2026-10-29 裁决：沿用长按**。长按同时登记成 `customActions`「删除词书」，读屏用户不必做长按也有第二条路；两步确认链（`pendingDelete` → `irreversible = true` 确认框 → 才落库）不变 | `LearningSetupLayoutContractTest.the delete entry rides the row long press and keeps the confirmation chain` + connected `WordBookPickRowTest.deleteWordBookCustomActionIsRegisteredOnTheClickableNode`（I-A2：手势 / `contentDescription` / `stateDescription` / `customActions` 同一节点）、`tap_selects_while_long_press_only_asks_for_confirmation`、`the_bundled_book_has_no_long_press_delete_entry`（内置册连长按都不登记） |
| 3 | 色块的**等级行**（CET-4 / 自定义）与**进度条**（`LinearProgressIndicator` + 域色 tint + `IllustratedIcon`）撤下 | 原型 `.pick` 只有 title + sub + value 三档，没有第四行，也没有条内进度；通栏行里塞一条 bar 会把行高顶起来。「已学 N」的文字已经承担同一件事，等级在词书详情屏与导入流程里仍念得到 | `LearningSetupLayoutContractTest.the pick row is the shared radio row without a tile`（`appDomain(` / `IllustratedIcon(` / `LinearProgressIndicator` / `animateColorAsState` / `.background(` / `border(` 归零）+ connected `WordBookPickRowTest.the_row_subtitle_is_the_prototype_words_and_learned_sentence`（`CET-4` / `自定义` 字样必须不在屏上） |
| 4 | 「使用中」绑**已生效的活动册**（宿主角取 `state.savedWordBookId`），不按原型那句 `b.learnt > 0` | 原型 mock 里「有进度」与「被选中」恰好同一本（`data.js:10-17`），照抄判据在真数据上会把学过但没在用的册念成使用中；绑屏上的草稿选中位则会在用户还没保存时就把标签搬到别的册上。三种绑法里只有活动册这一种说的是实话 | `the pick row is the shared radio row without a tile`（钉住 `activeBookId = state.savedWordBookId`）+ connected `WordBookPickRowTest.the_in_use_label_stays_on_the_active_book`（故意让草稿选中 ≠ 活动册，并钉标签落在哪一行的边界里） |
| 5 | 进度降级三档标签（「未开始」/「进度计算中」/「暂时读不到进度」）替换原型「· 已学 M」那一段 | 原型只画了「有进度」与「没进度」两态；本地优先口径要求降级如实呈现，`已学 0` 与「还没算出来」是两回事，合并写就是说谎 | `LearningSetupLayoutContractTest.the row subtitle carries the word count and the three progress states` + `WordBookCardProgressContractTest.the three progress states stay separately labelled` + connected `WordBookPickRowTest.the_progress_states_say_which_one_is_which` / `the_progress_labels_switch_when_loading_is_over` |
| 6 | 词量去掉批 5 那句「**共** N 词」的「共」，回到原型 `b.words + ' 词'` | 「共」是色块时代自己加的修饰，原型没有；词量仍取册元数据（`book.totalWords`），不挂 progress | `the row subtitle carries the word count and the three progress states`（`共 ` 归零）+ `WordBookCardProgressContractTest.word count comes from the book and never from progress`（旧「共」形状按近 miss 判违规） |
| 7 | 首帧骨架从「3 行 × 2 列 6 格」回到 **3 通栏行**，线与真实行同序（首行无线、其余行顶边一条 1dp） | 骨架必须与回填后的行同形状同线序，否则回填那一刻线从别处冒出来（F1-01 那一族的口径） | `the word book list is the prototype pick radio rows`（`wordbook_row_skeleton_` + `WORD_BOOK_PLACEHOLDER_ROWS`）+ connected `WordbookPickerHeaderTest.first_frame_reserves_full_width_placeholder_rows_at_the_row_minimum_height`（行高、通栏、缝宽全读生产真值 `WordBookRowMinHeight`，R-13.1 / m-4） |
| 8 | 选中态语义判据的**站点换锚**：`AppScreen.kt` 的色块 → `components/PickRow.kt` | 行的形状搬到批 0 的共享组件（本仓第一处真实调用点），判据跟着搬；规则本身一字未松，仍要求 `stateDescription = if (checked) "已选" else "未选"` 的双值分支，近 miss（常量播报、注释里的播报、`if (selected)` 走样）照样判违规 | `AccessibilityStateSemanticsContractTest.pick rows announce selected state` / `pick row selected state rule rejects near misses and accepts fixed shape`、`SharedComponentContractTest.the pick row mirrors the prototype mark and row metrics` |

（Task 9–11 的偏离在各自屏复检时补进本表，与批 1 报告同源。）

### Task 16 · 今日词单屏（原型 `#/learn-queue`，`sLearnQueue` `app.js:357-390`）

**新建屏**（用户 2026-10-29 拍板「三屏全部新建并接进导航」）：`ui/TodayWordListScreen.kt` + 宿主四件套接线
（状态位 `showTodayWordList` / `overlayOpen` 一项 / `BackHandler` 一条 / 挂载分支一支），
入口是今日学习软卡裸行组首位 `today_plan_open_word_list`。
数据面走扩 `TodayPlanUiState.Ready`（`newQueue` / `dueQueue` / `doneCardIds`，三枚都带默认值），
不新增 ViewModel、不给 `AppScreen` 加可空形参（`AppScreenProductionWiringTest` 的口径）。

| # | 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 吸底 `.foot` 由原型的**渐变底**（`app.css:1031`）改「`colors.background` 实底 + 顶边 `hairlineTop(colors.outline)`」 | 本仓吸底槽统一是这一形（`SetupSaveBar` 同形），且这一族由双向台账管着：自铺底色的槽必须补顶线，否则 2× 字号下长词单断在槽沿会被读成「内容被截断」。渐变底还会让吸底槽与主 CTA 两处渐变同屏——强调面在本轮只许一处 | `StickyFooterHairlineGuardTest.the sticky footer ledger covers every bottom slot in both directions`（本屏已登记为自铺底色那一档）+ `TodayWordListLayoutContractTest.the sticky foot carries the one primary action and nothing else`（`Button(` 归零、锚点恰一处） |
| 2 | 原型 `.notice` 那句（`app.js:385`「标记『忘记』的词会在本轮末尾再出现一次…」）**整条不搬** | 三个分句要么由三枚真开关驱动（写死即假话），要么与 AGENTS.md「今日计划是首次进入时生成的不可变快照」直接对冲。同一句话的动态版本已经住在学习计划屏（`PlanEstimate.kt` 的 `revealCaptionText`，Task 12 台账第 3 行同口径） | `TodayWordListLayoutContractTest.the prototype notice line stays off this screen`（`InlineNotice(` 与「本轮末尾」双归零） |
| 3 | 到期词里**今日已完成**的那几行标「已学」，不跟原型一律标「到期」 | 快照里那一项已记为完成，再说「到期」是假话。逐 id 判定而不是按计数：`newDone/dueDone` 只说「做了几件」，说不出**哪一件**做完，而词单是逐行摆的 | `TodayWordListScreenTest.rows_carry_the_gloss_tier_and_per_card_status`（已学 2 / 待学 1 / 到期 1，故意让两个队列各含一枚已完成卡）+ `TodayPlanViewModelTest.load exposes today's queue words in snapshot order`（`doneCardIds` 逐 id 暴露） |
| 4 | 原型 `wordRow` 行前那枚 44dp 词级贴纸**留空**（原型取自 `D.words[key].icon`） | 本仓 `WordCard` 没有词级图标字段；拿域徽标填进去就是把「这个域」说成「这个词的贴纸」 | `TodayWordListLayoutContractTest.word rows go through the shared component and carry the prototype status words`（`leading =` 归零 + 私有 `*Row*` 函数归零） |
| 5 | 词头不跟原型的 `serif` 18px，走共享 `WordRow` 的 `AppType.Body`（15sp） | 共享份是全仓词行的唯一真值（棘轮族 4），为这一屏另开一档就是留第二份版式；`GlobalVocabularySearchScreen` 同情形在册 | `SharedComponentUniquenessRatchetTest.each_family_offender_set_equals_its_seed_exactly`（本屏调 `WordRow(` 即合规，seed 不因新建屏漂移） |
| 6 | 某一节 0 个词就**整节不渲染**；今日做完就**吸底条整条不出现** | 两条都是原型没画到的状态（原型是写死数据，两节永远有内容、永远有下一个词）。按「原型没画到的状态先问」的口径，这里取「不画原型没画的那一态」，不留一枚「0 个词」的空标题，也不留一枚写着「开始第 5 个词」却没有第 5 个词的按钮 | `TodayWordListScreenTest.an_empty_section_is_not_rendered_at_all` / `the_footer_disappears_once_today_is_fully_done`（并钉「已学」4 枚、「到期」0 枚） |
| 7 | 三枚 chip 的计数取**队列长度**，而今日学习页那条入口行副标题取**计划目标数** | 两个数各说各的真话：入口行回答「今天排了多少」（与强卡同一权威 = 计划目标），词单屏回答「这一页列了几行」（= 内容源真返回的卡数）。混用会在词书包被换掉时当场对不上 | `TodayWordListScreenTest.header_meta_and_chips_carry_the_prototype_copy`（钉「全部 4 / 新学 2 / 复习 2」按队列算）；今日学习那侧由 `TodayPlanBareRowContractTest` 钉入口行存在与锚点 |
| 8 | 「按遗忘曲线排序」这句 `.sec-note` 保留 | 本仓**是真话**：复习队列就是快照 `dueCardIds`，其 DAO 排序是 `ORDER BY nextReviewAtEpochMillis ASC, cardId ASC`，屏侧不再重排（动手前核过 `InternalLearningEventDao`） | `TodayWordListLayoutContractTest.the two sections carry the prototype headings and the forgetting-curve note` + `TodayPlanViewModelTest.load exposes today's queue words in snapshot order`（顺序即快照序） |
| 9 | 行点开走共享词义详情载体（`selectedSearchCard` + 新增 `DetailSource.TODAY_QUEUE`），「下一个」沿本屏呈现序（新学段 + 复习段）推进 | 不为这一屏另起第六套详情栈；详情屏本轮已对过（Task 11），接线只加一种来源枚举 | 入口开屏 / 底部导航让位 / 返回键回今日学习：`AppScreenTest.theWordListEntryOpensTheTodayWordListAndBackReturnsToTheLearningTab`；行的点按回调（收到的是哪一张卡）：`TodayWordListScreenTest.tapping_a_row_opens_that_word_and_the_footer_starts_the_next_one`。**登记未覆盖项**：详情里「下一个」沿 `newQueue + dueQueue` 推进这一支本轮**没有锚**（需要 harness 带内容源，`placeholderCardPlan()` 不带），补锚归批 1 收口那一轮 |

### Task 17 · 复习队列屏（原型 `#/learn-review`，`sReview` `app.js:472-498`）

**新建屏**（用户 2026-10-29 拍板「三屏全部新建并接进导航」）：`ui/ReviewQueueScreen.kt` + 宿主四件套接线
（状态位 `showReviewQueue` / `overlayOpen` 一项 / `BackHandler` 一条 / 挂载分支一支），
入口是今日学习软卡裸行组**第二位** `today_plan_open_review_queue`（原型 `app.js:343` 的
`rowItem({sticker:'clock', …, to:'learn-review'})`，裸行从三条变四条）。
数据面只吃 Task 16 扩好的 `TodayPlanUiState.Ready`（`dueQueue` / `dueTarget`），不新增 ViewModel、
不给 `AppScreen` 加可空形参；「开始复习」沿用今日学习那枚同名 CTA 的目的分支（`showLearning`），
行点开沿用 `selectedSearchCard` + `DetailSource.TODAY_QUEUE`。

| # | 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 页头**没有动作位**：本屏 `AppScreenHeader` 不传 `action`，屏上也不出现「调整」 | 原型给 `sLearnQueue` 画了 `.bar-action「调整」`，给 `sReview` 的 `barTop({title:'复习队列'})` 只画标题 + 返回（`app.js:475`）。照抄另一屏的动作位就是给本屏加原型没有的出口 | `ReviewQueueLayoutContractTest.the header is the prototype bar top with no action slot`（`action = {` 与 `← 返回` 归零；判据用 `\baction = \{` 而不是 `action\s*=`，否则会把 `WordRow` 合法的 `actions = {` 一起算成违规）+ connected `ReviewQueueScreenTest.header_and_strong_card_carry_the_prototype_copy`（屏上无「调整」、返回键恰一次回调） |
| 2 | 强卡只留 `.strong-label` + `.strong-title`，**三块原型写了的东西整块不搬**：`.strong-meta`「最久的一个已经 9 天没见了…」、到期词行尾那格「9 天前」、`.meter` 那根 70% 的条与 `.strong-foot`「按遗忘曲线，今天复习过的词 6 天后才会再来。」 | 前两件要逐卡读上次复习时间，本屏只吃已备好的计划快照，不为两句 mock 新开一条仓库→界面的读数通路（AGENTS.md「UI 之外的逻辑保持原样」）；`.meter` 原型没写它量的是什么，照抄就是挂一枚来历不明的进度条；`.strong-foot` 把 FSRS 的**逐卡**下次间隔说成全局 6 天。同口径先例：批 1 Task 8 打卡页「完成度环整块删除」 | `ReviewQueueLayoutContractTest.the strong card carries the label and the queue count only` + `the three mock metric blocks stay off the strong card`（「最久」「没见」「才会再来」「8 分钟」四串归零，`LinearProgressIndicator(` / `PlanProgressBar(` / `ProgressRing(` / `ThinProgress(` 四种进度件归零） |
| 3 | 「复习方式」`card-quiet` 里只留**本仓真有**的那一行「看英文，想中文 / 默认方式」，「听发音，选释义 · 练听力时用」整行不搬；留下那行是**说明不是控件**（不给 `onClick`、`selectable`、radio 或 switch） | 本仓词卡流程只有「看英文，想中文 + 三档反馈」这一种模式，没有播放音频选释义的能力——用户 2026-10-29 拍板「无能力就隐藏」。原型这一行本身也只画了一行文字加一枚 `.dot` 选中点（`app.css:646`），没有可点态 | `ReviewQueueLayoutContractTest.the review mode section keeps only the mode this app can do`（听发音 / 练听力归零、`.dot` 走 `colors.brandDeep`）+ connected `the_review_mode_row_is_a_label_not_a_control`（带 `review_queue_mode_row` 的节点自身**不许**有 click 动作，且点它不透传任何词） |
| 4 | 吸底 `.foot` 由原型的**渐变底**改「`colors.background` 实底 + 顶边 `hairlineTop(colors.outline)`」，且 0 个到期词时**整条不出现**、「到期词」整节也不出现 | 与 Task 16 第 1、6 行同一口径：本仓吸底槽统一这一形，且由双向台账管着（自铺底色必须补顶线）；「0 个」那一态原型没画（写死永远 2 个到期词），不留一枚写着「开始复习」却无词可复习的按钮，也不自创「今天没有到期的词」那种文案 | `StickyFooterHairlineGuardTest`（本屏以 `align:BottomCenter` 登记为自铺底色那一档，台账 12 处 / 15 枚认领符号）+ `ReviewQueueLayoutContractTest.the sticky foot carries the one primary action and nothing else` + connected `the_sticky_foot_starts_review_exactly_once` / `an_empty_queue_hides_the_section_and_the_footer` |
| 5 | 强卡那个数取 `state.dueQueue.size`（**列出来几行**），而今日学习入口行那条取 `state.dueTarget`（**排了几件**） | 与 Task 16 第 7 行同一口径，两个数各说各的真话；本屏点进去就是这些行，卡上却说计划目标数，词书包换掉时当场对不上 | `ReviewQueueLayoutContractTest.the strong card carries the label and the queue count only`（`state.dueQueue.size`）+ `TodayPlanBareRowContractTest.the learning home keeps only four bare rows`（入口行的 `dueTarget`）+ connected `header_and_strong_card_carry_the_prototype_copy` |
| 6 | 到期词行前的 44dp 词级贴纸**留空**；词头不跟原型 `serif` 18px，走共享 `WordRow` 的 `AppType.Body`（15sp） | 与 Task 16 第 4、5 行同源：本仓 `WordCard` 没有词级图标字段，拿域徽标填进去就是把「这个域」说成「这个词的贴纸」；共享 `WordRow` 是全仓词行的唯一真值，为这一屏另开一档就是留第二份版式 | `ReviewQueueLayoutContractTest.the due section lists the queue through the shared word row`（私有 `*Row*` 函数归零、`status =` 归零、分线只由行号给）+ `SharedComponentUniquenessRatchetTest`（本屏调 `WordRow(` 即合规）+ `WordListFormatTest`（副文案 `wordListGloss(` 本屏恰一处、不许自己读 `partOfSpeech`） |
| 7 | 导航接线的挂载分支排在词单分支**之前**，`BackHandler` 的 `enabled` 守卫同时排除两枚覆盖层 | 本仓没有路由表：「挂载更早 = z 序更高」与「后声明的 `BackHandler` 优先」必须配成一对，否则返回键会把 z 序最高的那层留下。两条路由互斥（点一条才置位一条），守卫写成互斥而不是叠优先级 | connected `AppScreenTest.theReviewQueueEntryOpensTheReviewQueueAndBackReturnsToTheLearningTab`（开屏 → 底部导航让位 → 返回键回今日学习；注入按键异步派发，覆盖层拆除用 `waitUntil` 等，不能紧跟 `pressSystemBack()` 直接断言） |

**本轮附带收口（批 0 遗留的三处设备侧陈旧期望，非 Task 17 引入）**：
`TodayPlanScreenTest` 与 `ThemeModeSwitchTest` 里有三枚像素期望写的是批 0 换令牌**之前**的版面，
一直挂红；本轮按「原型与文件冲突时以原型为准」的口径把期望值搬回现令牌，判据强度不降：
 - `the_four_bare_rows_…`（原 `the_three_bare_rows_…`）：软档卡底从「中性白 `#FFFFFF`」改成原型
   `.card-soft { background: var(--tint) }` 的 `tint`（浅 `#E8F2EA`，`app.css:369-374`；`39e826a` 换的），
   「域色不铺成色块」那层立法由 `the_primary_cta_keeps_the_only_brand_gradient` 的反向禁色继续承担；
 - `assertCardFillBands` 的三点取样：旧点位「上沿居中」正落在原型
   `.strong-card::after` 那枚合法装饰光晕的亮核里（`app.css:392-402`），实测取到合成色 (197,228,214)、
   离期望底 38.0（阈值 18.0）被判成「底色漏画」；三点全部搬到光晕盘外（左内边带上段 / 下段 / 右内边带中段），
   并把三点读数一次性报出；期望字面量同时从抄录值（`#E4EFE9` / `#1F2B26`，欧氏距离 12.0 / 17.3，
   靠阈值 18 勉强绿）改成 token 逐值（`AppColors.kt:72` / `:76`）；
 - `ThemeModeSwitchTest` 的深色容器判据：期望档从 `AppColorDark.surface` 改成 `AppColorDark.tint`
   （深套实测众数 1845796 = `#1C2A24`，与 token 逐通道差 0），容差仍 2，
   相邻档候选集换成能透出的三档填充 `background`(18) / `surface`(11) / `surface2`(6)（`tint` 升为期望值本身、
   `outlineSoft` 是描边档不是底），换套判别力 204 档、离墨 205 / 142 档，三条自检一件没少。

### Task 18 · 今日完成屏（原型 `#/learn-summary`，`sSummary` `app.js:440-469`）

**新建屏**（用户 2026-10-29 拍板「三屏全部新建并接进导航」的第三枚）：`ui/TodaySummaryScreen.kt`
+ 宿主 `AppScreen.kt` 的 `showLearning` 分支按 `WordCardUiState.AllDone` 分派挂载。
与前两枚新建屏的**结构差别**：本屏没有入口行，原型自己的入口就是流程末段
（`app.js:1688` 末卡 `toast` 后 `go('learn-summary')` / `:1701` 「下一张」在末张时 `go('learn-summary')`），
所以**不新增导航状态位**：`overlayOpen` 与 `BackHandler` 都沿用 `showLearning` 那一条，
三个出口（页头 ← / 页头「完成」/ 吸底「回到今日」）在原型里同为 `learn-home`，这里同接一条 `exitLearning`
（回今日学习**且重算计划**，F1-04）。数据面只吃已备好的三份真数：
`AllDone.completedCount`、`TodayPlanUiState.Ready.localDateLabel`、`CheckInUiState.Ready.today.completedReadingCount`；
不新增 ViewModel、不给 `AppScreen` 加可空形参、不开新的仓库→界面读数通路。

| # | 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 原型 `.card` 统计格是 `grid-template-columns:1fr 1fr 1fr` 三列，本屏**只有一枚**（`个词`）并占满整行 | 三枚里只有 `个词` 有真数；照抄三列再挖两个空位，等于在收尾屏上留两块「这里本来有数字」的伤口 | `TodaySummaryLayoutContractTest.the stat card carries only the count this app actually knows`（`AppCard(` 恰一处、`private fun *Cell*` 归零）+ connected `TodaySummaryScreenTest.header_hero_and_stat_carry_the_real_numbers` |
| 2 | `用时 6:42` 与 `一次答对 82%` **两枚整枚不显示**，也不换成「耗时 / 正确率」这类同义改写 | 用户 2026-10-29 拍板「读不到的那枚整枚不显示，不编造指标；新增采集属功能改动、未获授权」。本仓 `LearningEvent` 只存逐卡 `ReviewFeedback`，既没有单轮用时也没有一次答对率 | 同上（`用时` / `一次答对` / `6:42` / `82` 四串在剥注释的屏源码里归零）+ connected `the_mock_metrics_from_the_prototype_stay_off_the_screen`（渲染侧同样取不到这些节点——源码侧正则挡不住「挪到别处渲染」，这条补那一面） |
| 3 | `section('这一轮的反馈')` **整节不搬**（`认识 / 模糊 / 忘记` 三行计数与「下次间隔 6 天 / 明天再来 / 今天晚些时候再问一次」三句副案） | 计数要按档聚合今天的事件日志，是一条新的读数通路（AGENTS.md「UI 之外的逻辑保持原样，只在必要时接上界面」，Task 17 台账第 2 行同口径）；三句副案又把 FSRS 的**逐卡**下次间隔写死，且与 `V1ReviewScheduler` 的真实档位（Again 10 分钟 / Hard 1 天 / Good 3 天）以及「提交一次有效反馈即记完成、本轮不重排」直接对冲。先例：Task 8 打卡页完成度环、Task 17 的 `.strong-foot` | `TodaySummaryLayoutContractTest.the per-round feedback section stays off the screen`（`QuietCard(` 归零、四句 mock 文案归零、四种进度件归零）+ connected 同上一条的 token 遍历 |
| 4 | `还差一步` 只留**本仓真有**的那一行「读今天的文章」；第二行「8 分钟后再看 1 个词 / 忘记的词会在最佳时点回来」整行不搬，留下那行的副案用今日学习同目的地行已在说的「去读今天生成的短文」，不搬原型 `4 段 · 覆盖 4 个今日词` | 本仓「忘记」的词不会在本轮再出现（今日计划是首次进入时生成的不可变快照），那枚 `1` 也没有备好的数——与 Task 16 台账删掉的那句 `.notice` 是同一条对冲；段数与覆盖词在文章还没生成时说不出 | `TodaySummaryLayoutContractTest.the next step section keeps only the row this app can hand over`（`后再看` / `最佳时点` / `4 段` / `覆盖` 归零、`SectionHeading(` 与 `BareRow(` 各恰一处）+ connected `the_reading_row_reports_the_actual_read_state` / `the_reading_row_reports_unread_when_today_is_not_read` |
| 5 | 行尾 `value:'未读'` 绑**真实阅读读数**；行前导取共享 `InlineIconBadge`（域徽标）而不是原型 `rowItem({sticker:'newspaper'})` 那枚 44dp 贴纸 | 本仓有「今日文章是否读过」的数（`completedReadingCount`），所以那一格不是装饰；行首统一 `InlineIconBadge` 是批 1 已定的口径（今日学习同目的地行、复习队列默认模式行都是它），为这一屏另立一种行首就是留第二份版式 | `TodaySummaryLayoutContractTest.the next step section keeps only the row this app can hand over`（`AppIcon.Newspaper` 恰一处）+ `SharedComponentUniquenessRatchetTest` |
| 6 | `.eyebrow` 那格日期在计划快照未就绪时**整行不进组合**（宿主传 `null`），不留空串、不写「今天」占位 | 原型该格是 mock 的 `D.today.planDate`；本仓没有日期时摆一个空壳，等于让读屏用户在一个并不存在的日期上听到一句编出来的话 | `TodaySummaryLayoutContractTest.the hero is the 64dp star badge with the date eyebrow and the serif line`（`AppType.Eyebrow` 恰一处）+ connected `a_missing_plan_date_hides_the_eyebrow_row_only` |
| 7 | hero 那句 `.serif` 26px 与统计数字共用 `AppType.StatNum` 一档（行高 1.1，原型 hero 内联写的是 `line-height:1.2`）；64dp `badge-lg` 走 `StickerSize.Large` | 批 0 的六档锁不给这一屏开第七档字号，26px serif 唯一同度量的档就是 `StatNum`（`app.css:696`）；行高差 0.1 属 token 近似，不为此加参数 | `TodaySummaryLayoutContractTest.the hero is the 64dp star badge with the date eyebrow and the serif line`（`AppType.StatNum` 恰两处 = hero 主句 + 统计数字；`StickerBadge(` 恰一处）+ `DesignTokenContractTest` + connected `header_hero_and_stat_carry_the_real_numbers`（贴纸高度下界） |
| 8 | 吸底 `.foot` 由原型的**渐变底**改「`colors.background` 实底 + 顶边 `hairlineTop(colors.outline)`」；且本屏那条**不随剩余量收条** | 与 Task 16 第 1、6 行、Task 17 第 4 行同一形态、同一双向台账口径（自铺底色必须补顶线）；收条条件是「本轮总有回到今日这一件事可做」，不存在词单/复习队列那种「0 个就不该有按钮」的形态 | `StickyFooterHairlineGuardTest`（本屏以 `align:BottomCenter` 登记为自铺底色那一档，台账 13 处 / 16 枚认领符号）+ `TodaySummaryLayoutContractTest.the sticky foot carries the one primary action and nothing else`（裸 `Button(` 归零） |
| 9 | `WordCardScreen` 的 `AllDone` 分支**只剩穷尽性、不画版面**：原先那三行（`今天的词卡都提交完了` / `共完成 x / y 张` / `复习… 新增…`）与「返回今日计划」一并撤掉 | 同一条状态两处渲染就是 Task 10 台账点过名的重复画面；原型在末卡推进后画的是收尾屏，不是词卡屏里的一行字。分支保留是为了 `when (state)` 穷尽，给未来的接线留编译期空档而不是留一份静默版面 | connected `WordCardScreenTest.allDoneIsNoLongerRenderedByTheCardScreen`（`word_card_all_done` / `word_card_completion_breakdown` / `返回今日计划` / `today_summary_screen` 在本屏全部取不到，且 `onBackToPlan` 零回调）+ `TodaySummaryLayoutContractTest`（收尾版面只此一处） |
| 10 | 导航接线不给 `AppScreen` 加状态位：`AllDone` 到达即在同一 `Box` 里把词卡屏换成收尾屏 | 加一枚 `showSummary` 就要同时改 `overlayOpen` 聚合与 `BackHandler` 优先级两表，而原型三个出口与系统返回键行为**完全同构**（都回 learn-home 并重算计划）；靠状态分派则一处都不必改。换屏时词卡屏离开组合，F5-03 的退出停播守卫照常把停播恰好委托给宿主一次 | `TodaySummaryLayoutContractTest.the host mounts it on the AllDone state without a new navigation bit`（源码侧钉「不分派到别处、也不新置位」：`showTodaySummary|showSummary` 归零、`BackHandler(enabled = showLearning)` 仍恰一条、三个出口同一条 `exitLearning`）+ connected `AppScreenTest.finishingTheLastCardHandsTheScreenToTheTodaySummaryAndDoneReturnsToThePlan`（回执仍在词卡屏 → 推进后收尾屏在场且词卡屏不在场 → 真实日期落在屏上 → `完成` 回今日学习）+ `AppScreenTest.systemBackOnTheTodaySummaryExitsTheLearningFlowTheSameWay`（返回键同一条退出通路） |
| 11 | 收尾屏不接发音回调、不碰播放器（本屏源码里 `Pronunciation` / `speakWord` / `stopPlayback` / `onSpeak` / `Tts` 五串归零） | 停播只有一条既有通路：词卡屏那条退出守卫把停播恰好委托给宿主一次。收尾屏若长出第二枚发音入口或自己调 provider，就出现「同一轮音频两处都能控制」，`AppScreenTest` 钉的 `stopCalls` 恰一次随之失真 | `TodaySummaryLayoutContractTest.the summary screen never touches the pronunciation player` + `AppScreenTest.leavingLearningStopsTheStillPlayingAudioAtTheProvider`（`stopCalls` 恰一次未被本屏改动） |

### Task 19 · 今日学习空态（原型 `#/learn-empty`，`sLearnEmpty` `app.js:501-518`）

**不是第六枚新建屏**：原型把这一态画成「学习」这个 tab 的另一份 `.scr`（页头同名「今日学习」、
底部 `nav('learn-home')` 仍停在第一位，路由表 `:1608` `'learn-empty'`、屏索引 `:1625`「今日已学完」），
与 `sLearnHome` 不共存。本仓对应物是 `TodayPlanScreen.kt` 的 Ready 分支里「排了任务且每一颗都提交完」
（`todayAllDone`）那一态——计划「推进决定」第 2 条只授权五枚新建屏，这一枚不在名单里，
所以宿主 `AppScreen.kt` 不改挂载点、不新增长导航位。

| # | 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 空态用批 0 的共享 `EmptyState`（本仓第一处**真实调用点**），不在此屏另铺 84dp 贴纸 + 标题 + 说明 | 原型 `.empty` 那一族度量（84dp/r26/内描边 1dp 5% 墨、`.empty-title` `--fs-body` 600、`.empty-body` `--fs-caption` `--ink-3`、`gap:--s3`、`padding:--s7 --s4`）批 0 已逐字落进共享件；屏侧再复刻一份就是同一块版面的第二实现 | `TodayPlanEmptyStateContractTest.the all done state is a state of this screen rendered with the shared empty state`（`EmptyState(` 恰一处、`Sticker.Star`、标题逐字、`todayAllDone` 判定恰一枚且在 `when (state)` 之前、宿主 `TodayPlanScreen(` 恰一处 + `showLearnEmpty|showTodayEmpty|LearnEmptyScreen` 归零）+ `SharedComponentUniquenessRatchetTest` |
| 2 | `section('明天')` **整节不搬**（「明天新学 4 个词，加上今天模糊的 2 个，约 6 个词；文章会在 0 点后生成」） | 四个数/句没有一处读得到：明日份额要么把今天的 `newTarget` 当明天的（AGENTS.md「设置默认从次日生效」，今天改过即假话）、要么新开一条次日预生成读数；「今天模糊的 2 个」要按档聚合今日 `LearningEvent`（与 Task 18 台账第 3 行同一理由）；「约 6 个」是派生数；「0 点后生成」在本仓不成立（今日文章解锁后按需生成）。用户 2026-10-29 拍板「读不到的那枚整枚不显示，不编造指标」 | `TodayPlanEmptyStateContractTest.the mock tomorrow section stays off the screen`（`明天` / `约 6 个词` / `0 点后生成` / `模糊的` / `QuietCard(` 五串在剥注释源码里归零） |
| 3 | 页头按原型 `.head-plain` **撤掉齿轮**、副标题只留「连续打卡 N 天」（`streakDays == 0` 时连副标题也不给） | 全原型 `.head-plain` 只两处（`app.js:503` 本屏、`:1257` 「我的」根页），共同点是「这一屏不办事、只交代状态」——是画法不是漏画；「今日目标 N 个词」在学完那一态已是既成事实，留在副标题就是同一句话的第二遍。设置仍从「我的」tab 进，本态的两枚 CTA 才是这一屏要的出口 | `TodayPlanEmptyStateContractTest.the plain head drops the gear and keeps only the streak in the subtitle`（`if (!todayAllDone) SettingsAction(` 恰一处、`今日目标` 全文件恰一处 = 未学完那一档）+ connected `all_done_state_keeps_only_the_streak_in_the_plain_head` / `all_done_state_without_streak_hides_the_subtitle` / `not_done_state_still_shows_the_gear_and_the_goal` |
| 4 | 说明句的数量分句取**计划快照的目标数**（`state.newTarget` / `state.dueTarget`）且某档为 0 时整句不拼；后半句「想多学可以调整每日新增，或者读一篇今天生成的文章。」逐字 | 原型那两句里写着 mock 的「新学 4 个、复习 2 个」，本仓的权威是快照目标数（与未学完那一支的入口行、强卡图例同一权威）；「新学 0 个都已完成」是把「今天没排新词」说成「排了 0 个并且学完了」，故该分句整段不拼 | `TodayPlanEmptyStateContractTest.the body sentence names only the counts the plan snapshot actually has`（`新学 ${state.newTarget} 个` / `复习 ${state.dueTarget} 个` / 两条 `if (… > 0)` / mock 数 `新学 4 个`·`复习 2 个` 归零）+ connected `all_done_state_swaps_in_the_prototype_empty_state_with_two_exits`（整句逐字） |
| 5 | 两枚出口逐字取原型 260dp 网格：primary「调整每日新增」→ `onOpenSetup`、secondary「去读今日文章」→ `onOpenReading`；`widthIn(max = 260.dp)` + 8dp 间距 + 8dp 上边 | 原型 `app.js:509` 那枚 grid 就是 `width:100%; max-width:260px; gap:--s2; margin-top:--s2`；两个目的地都是本仓既有通路，不新开读数 | `TodayPlanEmptyStateContractTest.the empty state offers exactly the two exits the prototype draws`（`PillButton(` 恰两枚、裸 `Button(` 归零、两枚文案与回调、`PillStyle.Secondary`、两枚锚、260dp / 8dp / `padding(top = 8.dp)`）+ connected 套用同上一条 |
| 6 | `.btn-primary` 的原型底是 `--strong` 深墨，本仓主出口走品牌渐变（`PillStyle.Primary` 默认域） | 与 `sLearnHome` 主 CTA 同一取法；批 0 的 `pillButtonInk` 纯表里 `--strong` 没有对应域（`strongSurface` 不等于任何 `AppDomain` 深色），为这一屏单开一档就破了「强调面只许一处」。本态只许**一枚**渐变（次出口是 `PillStyle.Secondary` 的 surface 底 + 描边） | `TodayPlanEmptyStateContractTest.the empty state adds no color read of its own`（空态那一支 `appColors()` / `colors.(tint|surface|brand|onStrong)` 归零——墨色由 `PillButton` 纯表与 `EmptyState` 承载）+ connected `all_done_state_swaps_in_the_prototype_empty_state_with_two_exits` 末两条（`today_plan_adjust_goal` 有渐变端点、`today_plan_read_article` 无） |
| 7 | 「复习生词本」/`onReviewWords` 随四态主 CTA **一起下线**（主 CTA 收成两态：开始学习 / 开始复习） | 原型的空态只有两枚出口，四态主 CTA 里「做完了才出现」的两枚（去读今日文章 / 复习生词本）本就没有画在学完那一屏上。生词段不是断头路：词库页自己的分段控件（`WordBankScreen` 的四格 tab）是全站出口，「我的」页词库格开的是同一页 | `TodayPlanEmptyStateContractTest.the empty state offers exactly the two exits the prototype draws`（`复习生词本` / `onReviewWords` 本屏归零）+ `MyWordsScreenContractTest`（宿主 `onReviewWords` 归零、`wordBankSection = WordBankSection.New` 写入端归零）+ `TodayPlanBareRowContractTest.the main cta keeps one anchor across the two states`（Ready 体 `PillButton(` 恰 4 = 两支各一对） |
| 8 | 空态那一支**不渲染**强档卡 / 进度条 / 权威数字 / 「接下来」节标题 / 四条裸行，也不渲染卡外 `.caption` 注脚 | 原型那一屏的 `.scr-body` 只有 `emptyState` 与 `section('明天')` 两块；同屏再摆一条「今日已完成 · 连续 N 天」注脚是同一件事的第二遍（连续天数已由页头说） | `TodayPlanEmptyStateContractTest.the empty branch draws no plan card no progress bar and no bare rows`（该支 `StrongCard(`/`SoftCard(`/`BareRow(`/`PlanProgressBar`/`SectionHeading(`/`AppType.PlanNum`/两种进度件归零；反向：未学完那一支 `StrongCard(` 1 + `BareRow(` 4 照旧）+ connected `all_done_state_swaps_in_the_prototype_empty_state_with_two_exits`（`today_plan_summary` / `today_plan_plan_bar` / `today_plan_total_progress` / `today_plan_secondary_card` / `today_plan_start_learning` 全取不到） |

**连带改动的既有判据（都是「期望值随原型改」，没有一条放宽）**：
`MyWordsScreenContractTest` 的 `reviewWiring == 1` 换成「`onReviewWords` 与 `wordBankSection = WordBankSection.New` 写入端归零」
（第 7 行的反向钉法）；`TodayPlanBareRowContractTest` / `TodayPlanNumberAuthorityTest` 里 Ready 体 `PillButton(` 2 → 4
（未学完一支的主 CTA + 「计划」次按钮、空态一支的两枚出口，两支互斥同屏只渲染一对）；标签名册删
`today_plan_plan_footnote`、增 `today_plan_all_done_state` / `today_plan_adjust_goal` / `today_plan_read_article`。
connected 侧：`cta_sends_the_user_to_todays_article_when_the_plan_is_done` / `cta_closes_the_loop_on_wordbook_review`
两条随「主 CTA 不再有第四态」换形为 `all_done_state_swaps_in_the_prototype_empty_state_with_two_exits`
（换态 + 两枚出口各落哪儿）；`completed_plan_shows_the_streak` / `completed_plan_without_streak_omits_the_counter`
换成页头副标题断言；新增 `all_done_state_keeps_a_closed_clickable_set_and_no_gear`（空态可点闭合集合 = 查词 1 + 出口 2 = 3）。
`AppScreenTest.createProfile()` 的落地断言从 `today_plan_summary` 改成屏根 `today_plan_screen`：
`readingFixture()` 这类「当日新词已学完」的夹具现在正落在空态，拿强档卡当「建完档了」的凭据会把这批用例误判成失败。

### Task 15 · 词书详情（原型 `#/book-detail`，`sBookDetail` `app.js:608-640`）

**新建屏**（用户 2026-10-29 追问后拍板「按原型新建这一屏」——超出当初五屏授权，是本轮第六枚新增页）：
`ui/BookDetailScreen.kt`。落点：**词库页「词书」段的默认面**，与既有 `WordBookWordsScreen`（词单浏览）
构成「详情 ↔ 词单」两态；「查看完整词单」进词单浏览，壳那枚返回键**按态分派**（词单里退回详情、
详情里退出词库页）。两态开关住在壳层 `AppScreen`（`rememberSaveable(state.profile.id, showWordBank)`）：
词卡详情是 `else if` 的相邻分支，开关写在词库分支内部会在详情打开时被遗忘（真机实测：退回时词单跳回详情）。
页头归 `WordBankScreen` 壳，本屏只出正文（与生词 / 错词 / 收藏三段同一口径，不叠第二份返回位）。

| # | 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | `.strong-foot`「按每天 4 个新词，还剩 N 天；改计划可以更快」**整句不搬** | 「这本书每天学几个」本仓没有读数：每日新增写在**当日计划快照**里且改设置次日生效；写死 4 就是编造指标（Task 18 台账第 2、3 行同口径） | `BookDetailScreenContractTest.the facts section carries only counts and rows this app can actually read`（`按每天` / `还剩 ` / `改计划可以更快` 归零） |
| 2 | 「这本书的情况」不搬原型四行**名目**：只留 `已学 / 未学 / 学习中 / 待复习` | `已掌握` 这一档本仓没有（只有 `WordBookRecordStatus` 的未学 / 学习中 / 待复习）；「词书版本」（`2026.11` / `book.json · 随 App 更新`）的词书元数据没传到界面 → 整行不画（读不到的那枚整枚不显示） | 同一条判据（`已掌握` / `词书版本` / `book.json` / `2026.11` 归零）+ connected `facts_show_the_counts_this_app_can_read` / `unknown_counts_disappear_instead_of_showing_a_fake_zero` |
| 3 | 「操作」只剩**两行**：查看完整词单 / 更换词书；`导出学习进度` 与 `删除这本书的进度` **整行不搬** | 本仓只有整包导入/导出词书（`.wbpack`）与整库备份，**没有**按书导出进度、也没有按书删进度这两项能力；新增写入通路属功能改动、未获授权 | `BookDetailScreenContractTest.the action group keeps only the two rows this app can perform`（`BareRow(` 恰 2、两枚入口文案与回调、`导出学习进度` / `删除这本书的进度` / `book_detail_delete` 归零）+ connected `the_two_action_rows_open_the_word_list_and_the_picker`（可点闭合集合恰 2） |
| 4 | 页头 `more` 溢出菜单（`dlg delete-book`）不搬 | 它唯一的内容就是第 3 条那枚删进度的危险动作；没有第 3 条就没有这枚菜单 | 同上（`more` 归零） |
| 5 | 进度环百分比取 `AppType.StatNum`（26sp serif） | 原型 `.ring-num` 是 `serif 21px`，六档锁里没有 21sp serif 档；取同族最近的 `StatNum`，登记 token 近似（Task 18 台账第 7 行同一取法） | `BookDetailScreenContractTest.the summary card mirrors the prototype ring label title and meta`（`AppType.StatNum` 恰一处、`AppType.StrongTitle` = `.strong-title` 27sp 逐字）+ connected `summary_carries_the_book_name_the_learned_count_and_the_percentage` |
| 6 | 「学习中 / 待复习」两枚计数只在词单真读出来时出现（`null` 整行不画） | 词单未加载 / 读取失败时摆「0」等于把「不知道」说成「没有」 | `BookDetailScreenContractTest`（`if (learningCount != null)` / `if (dueCount != null)`）+ connected `unknown_counts_disappear_instead_of_showing_a_fake_zero` / `without_progress_the_learned_rows_stay_off` |

**连带改动的既有判据（期望值随原型改，未放宽）**：`AppScreenTest` 四条走词库页的用例
（`wordBookReturnsToTheLetterAndScrollPositionUsedToOpenWordDetail` / `wordBookNewBrowsingSessionStartsAtAAfterLeavingW`
/ `wordBookSystemBackRetainsTheNonzeroScrollOffsetWithinW` / `wordBankEntryOpensTheFourSegmentPageAndSystemBackReturnsToTheTodayPlan`）
在打开词库后补一步「查看完整词单」，其中第一条按「两次返回」表达按态分派（词单 → 详情 → 关页），
断言对象（字母定位、W 组内偏移、A 组新会话）原样保留。

### Task 21-A · 外刊选读（原型 `#/read-feed`，`sReadFeed` `app.js:779-795`）

本屏早前（批 3 Task 1）已换过骨架：共享页头 + `BareRow` 列表 + 六态文案 + 两枚失败态可重试。
本轮只补原型画到、本屏还缺的 `section('来源说明', .card-quiet)`，并把三处读不到的登记成偏离。

| # | 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 条目副案 `section · length · words 词` **不搬** | `FeedItem`（`reading/ArticleFeedParser.kt`）只有 `title / articleUrl / publishedAtEpochMillis / summary`；补栏目/篇幅/词数要么改数据形状（撞「数据层零改动」），要么按摘要长度糊字数（编造） | `ReadingFeedScreenContractTest.the source notice only states what the feed actually knows`（`section` / `.length` / `words 词` 归零） |
| 2 | `.meta`「来自已登记的公开学习类来源，正文不含广告。」**不搬** | 「不含广告」是对来源页面的断言，本仓只读到条目元数据、无法证实；换成本屏真有的来源署名（`sourceDisplayName`） | 同一条判据（`正文不含广告` 归零、`\$\{feed\.sourceDisplayName\}` 必在） |
| 3 | 「VOA Learning English 的内容属公共领域」**不写死** | 来源可配置，写死 VOA 换成别的来源即假话；来源说明只写「按其来源许可仅供学习使用」+ 指向既有的「开源许可与来源」页 | 同一条判据（`VOA` / `公共领域` 归零）+ `the feed keeps the shared header and one quiet source notice`（`SectionHeading(text = "来源说明")` / `QuietCard(` 各恰一处） |

connected `ReadingFeedScreenTest` 9/9 原样通过（新增那一节是列表尾部第 N+1 个条目，不改变任何既有锚与可点集合的断言）。

### Task 21-B · 阅读历史（原型 `#/read-history`，`sReadHistory` `app.js:796-813`）

本屏已有共享页头、懒列表、**同组多版本折叠**（F4-01 决策 §8：单版直开、多版展开逐版「重读 / 删除此版」）。
折叠是产品决策不是漏实现，形状不动；本轮补两处原型画到、本屏还缺的：

| # | 改动 / 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 补**日期分节**：两层 `groupBy`（先 `localDate`、再 `versionGroupKey`），节标题走共享 `SectionHeading(text = date)`；单版行副标题从「日期」改成「第 v 版」 | 原型按 `h.date` 分节；日期塞进每行副标题时，同一天的多行重复同一句话 | `ReadingHistoryScreenContractTest.history is sectioned by date like the prototype`（`groupBy { it.localDate }`、`SectionHeading(text = date)` 恰一处、`subtitle = article.localDate` 归零） |
| 2 | 补尾部提示「同一天可以重新生成文章；旧版本不会被覆盖，都在这份列表里。」（锚 `reading_history_notice`） | 这句在本仓**成立**（旧版本确实不被覆盖、都留在列表里），逐字搬 | `the tail note is carried because it is true here` |
| 3 | 右尾「已读 / 未读」**不搬** | `Article` 没有阅读态字段；`isRead` / `readAt` 全仓不存在，按「有译文就算读过」推断就是编造 | `the read state stays off because nothing records it`（`已读` / `未读` / `readAt` / `isRead` 归零） |

connected `ReadingHistoryScreenTest` **8/8 原样通过**（分节标题是新增节点、单版行锚未动，版本组展开行的
「第 2 版 · 2026-09-22」逐字未变）。

### Task 21-C · 今日阅读（原型 `#/read-home`，`sReadHome` `app.js:702-737`）— 本轮只对了「今日文章卡」一块

本屏是阅读链路的根页，当前形态仍是旧一版（`Mint*` 令牌、M3 `Button` 家族、「更多」组），
逐块对齐需要分次做。本轮只补原型今日文章卡上**读得到**的两枚 chip，其余三块按清单留给后续：

| # | 改动 / 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 今日文章卡补一行「覆盖 N 词 · 第 v 版」（锚 `reading_today_meta`），取 `Article.coveredLemmas.size` 与 `Article.version` | 原型 `app.js:717-719` 的两枚 chip 是「覆盖 4 词」「第 1 版」；本仓这两枚都是真字段，不是 mock | `ReadingHomeContractTest.the today card carries the covered word count and the version from the article itself` |
| 2 | 第三枚 chip「未读 / 已读」**不画** | `Article` 没有阅读态字段（与 Task 21-B 第 3 行同一条口径） | `ReadingHomeContractTest.the read state chip stays off because nothing records it`（`已读` / `未读` 归零） |
| 3 | `section('继续')` 的「双语对照」独立入口**不搬** | 本仓的对照态是 `ArticleReadingScreen` 内部切换，没有独立路由；另开一条属功能改动 | 记在本条，下一轮同屏对齐时再判 |
| 4 | `section('本周阅读')` **已补**（`SectionHeading(text = "本周阅读")` + `QuietCard`，卡内一行「近 7 天：生成 N 篇 · 共 M 词」） | 参照同类开源项目（LWT / LinguaCafe）：阅读侧只报**派生统计**、不设周目标；两枚数都由本地历史派生（窗口 `[today-6, today]`，基准日 `todayLocalDate` 由宿主注入，屏内不读钟；字数按 `englishText` 空白切分）。「目标是每周 5 篇，还差 2 篇」是原型 mock、本仓无周目标配置 → **不写** | `ReadingHomeContractTest.the weekly section only reports derived facts and carries no mock target`（`SectionHeading`/`QuietCard` 各一、`todayLocalDate.minusDays(6)`、两句统计字面量、`目标是每周`/`还差` 归零） |

connected `ReadingAccessScreenTest` **18/18 原样通过**；既有 `ReadingHomeLayoutContractTest` 亦绿。

### Task 21-D · 阅读正文 / 双语对照（原型 `sReadArticle` `app.js:738-761`、`sReadTranslate` `app.js:762-778`）— 本轮只对了元信息行

两屏在本仓是**同一支 `ArticleReadingScreen` 的两种呈现**（`article_translation_toggle` 切换），
与同类开源项目（LWT / LinguaCafe：翻译在阅读器内切换，无独立「双语页」）一致，不新开页面。

| # | 改动 / 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 标题下补一行「约 N 词 · 第 v 版」（锚 `article_meta`） | 词数按 `englishText` 空白切分真算、版本取 `Article.version`；原型 `.article-meta` 里的「约 214 词 · 5 分钟」换成这两枚真数 | `ArticleReadingScreenContractTest.the article meta reports only the word count and the version` |
| 2 | 「5 分钟」**不搬** | 本仓没有阅读速度口径，按固定词/分钟换算就是编造 | `the fabricated reading time stays off the screen`（`5 分钟` / `214 词` 归零） |
| 3 | `.article-meta` 里的 `source` **不重复** | 本屏来源块已单独成块（`article_source_name` / `article_attribution`），元信息行再写一遍是同一条信息第二遍 | 记在本条 |
| 4 | 其余仍缺（下一轮）：正文 `.prose` 字号/行距的逐值对齐、底部「双语 / 读完了」两枚并列出口与原型 `.foot` 的形态差、对照态顶部 `bar-action`「只看中文」、对照态末尾 warning notice | 这几处要么牵动既有出口接线，要么需要先核 `ArticleReadingScreen` 的滚动/吸底策略，单独一轮做 | 同上 |

connected `ArticleReadingScreenTest` **25/25 原样通过**。

### Task 22-A · 我的（原型 `#/me-home`，`sMeHome` `app.js:1255-1282`）

本页已有：`hero` 页头（一级 tab 无返回位）、唯一一枚强档卡装三格统计（三格各是深链）、学习记录行、
设置九行由宿主 `settingsContent` 注入。本轮补副标题、记两条偏离：

| # | 改动 / 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 副标题改成原型 `.head-sub`「连续打卡 N 天 · 累计学过 N 个词」（`streakDays` / `learnedWordCount` 真值） | 旧副标题「API Key 只保存在本机加密区。」不是页头身份行：隐私口径属于设置页与备份说明，挂在名字下面既不回答问题也不构成信息 | `ProfilePageHomeContractTest.the header subtitle reads the two real facts the page receives` + connected `ProfileScreenTest`（`连续打卡 5 天 · 累计学过 42 个词`） |
| 2 | 「我的词汇」三行（收藏 / 生词本 / 易错词）**不搬** | 本仓三者是词库页的四格分段（收藏 / 生词 / 错词），再在本页挂三条同一落点的行就是同一条通路的第二份入口（与 Task 19 第 7 行同口径） | `ProfilePageHomeContractTest.the vocabulary group stays on the word bank page instead of duplicating entries` |
| 3 | 中间那格标名仍用「已学词」，不改「掌握词数」 | 本仓没有「掌握」口径（`learnedWordCount` 是已学词数），改名会把「学过」说成「掌握」 | `the three stat cells keep their own anchors and honest labels`（`掌握词数` 归零） |

connected `ProfileScreenTest` 3/3 通过（副标题期望值随本条改）。

### Task 22-B · 关于（原型 `#/me-about`，`sMeAbout` `app.js:1366-1384`）

**新建屏**（用户 2026-10-29 拍板的五枚之一）：`ui/AboutScreen.kt`。骨架逐段对原型：
宿主页头「关于」→ 应用块（`Sticker.Books` 56dp + 应用名 + 「版本 x · 单机优先」）→ 一句 `.meta`
→ `section('说明')` 三行 → `section('致谢', .card-quiet)`。

| # | 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 版本号取 `versionName` 形参（宿主给构建信息），不写死 `2026.11` | 原型是 mock 版本；写死立刻过期 | `AboutScreenContractTest.the version and the license count are never invented`（`2026.11` 归零、`版本 ${'$'}versionName · 单机优先` 必在） |
| 2 | 「N 项第三方组件」的 N 只在 `licenseCount != null` 时拼 | 数目由许可清单解析结果给（`LicensesScreen` 同源），读不到就退成不带数字的短语；`6 项` 不写死 | 同一条判据（`if (licenseCount != null)`、`6 项` 归零） |
| 3 | 「致谢」只留本仓成立的两句（图标自制 / 字体 Newsreader·OFL） | 原型「示例文章由本地模型提供」与本仓不符（文章由用户配置的 AI 服务按需生成） | `the thanks section states only what this project can stand behind`（`本地模型` 归零、`Newsreader` 必在） |
| 4 | 「隐私说明」是**只读行**（`onClick = null`），不假装是入口 | 原型那一行的动作是 `toast`；本仓隐私口径分散在设置页与备份说明里，没有单独一页可跳 | `the three explanation rows keep their capability`（三枚锚各一、另两行接既有许可页/备份页） |

**接线（用户 2026-10-29 拍板「设置页加第 10 行」后完成）**：`SettingsGroups` 台账登记
`settings_open_about`（「外观与关于」组，排在许可行之前），**AC-R-21 的行数期望值由 9 → 10**
（台账 KDoc、`SettingsNoDeveloperJargonTest` 的行数/标题表/标题枚数、`SettingsScreenTest` 的行数与
可点闭合常量 11→12 / 13→14 同步改；判据形状不变，只是行数）。宿主：`showAbout` 状态位 + `overlayOpen`
+ `BackHandler(enabled = showAbout)` + 渲染分支；`versionName` 走 `BuildConfig.VERSION_NAME`
（`buildFeatures.buildConfig` 打开），`licenseCount` 暂传 `null`（行文案退成不带数字的短语，
许可条数注入是下一步小事）。JVM 2181 条全绿；connected `SettingsScreenTest` 18/18、
`ProfileScreenTest` 3/3、`BackupEntryUnificationTest` 全绿。

### Task 22-C · 我的词汇（原型 `#/me-words`，`sMeWords` `app.js:1285-1303`）— 对应关系裁决（未动版面）

**裁决（本仓形状与原型不同，先定对应关系再改版面）**：
原型是一页「我的词汇」+ 一枚三段 `seg-solid`（收藏 / 生词本 / 易错，标签带计数）+ 每段一句 caption
+ 该段词行 + 尾部 notice。本仓的等价物**已经存在**：
- 三分类 = `WordBankScreen` 的「生词 / 错词 / 收藏」三格（`WordBankSection` 四格里的三格，第一格是词书）；
- 各段的面 = `MyWordsScreen` 以 `showHeader = false` 注入的三段（它自己不再自带页头与分段）。

因此：**不新建「我的词汇」页**；原型的三个 tab ↔ 本仓三格的映射是
`收藏 → WordBankSection.Favorite`、`生词本 → WordBankSection.New`、`易错 → WordBankSection.Wrong`。
待做（下一轮按 TDD 推进，每条各配判据）：
1. 三格标签补计数（`收藏 N` / `生词 N` / `错词 N`）——计数由各段 UiState 的列表长度给，读不到就不带数字；
2. 段内 caption 三句（收藏不排复习 / 生词明日前排 / 错词按答错次数，答对两次移出）——先核这三条**规则是否与本仓一致**，
   不一致的按「读不到/不成立就不写」处理（这三句是原型的产品口径，不是本仓现有规则）；
3. 尾部 notice「词义详情里可以加入生词本或取消收藏」——本仓词义详情确有这两枚能力时再搬；
4. 页头搜索动作（原型 `barTop` 的 search `icon-btn`）——本仓查词入口在今日学习常驻查词条，是否在这页再开口待裁决。

### Task 22-D · 学习记录（原型 `#/me-records`，`sMeRecords` `app.js:1304-1327`）

本屏原为「今日已学 / 历史记录」双页签、`Mint*` 令牌、`TabRow` 下逐日分组头 + `WordRow` 裸行。
本轮**保留双页签与既有行族**（connected `LearningRecordsScreenTest` 的可点闭合集与页签语义不动），
整屏迁到 `appColors()/AppType`，并按原型补「本周」与页尾两件可读的东西。

| # | 改动 / 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 页尾补提示条「记录按天聚合，只保存在本机；卸载 App 前记得导出备份。」（共享 `InlineNotice`，Info） | 这句在本仓**逐字成立**（反馈先落本地、逐日聚合、无云同步），原型 `:1323` 的 `notice(…,'info')` 原样搬 | `LearningRecordsWeekContractTest`（源码无 Mint/裸字号）+ connected `LearningRecordsScreenTest`（本批新增 `weekBlockRendersTheTwoRealStatsAndNeverTheMockedDuration` 顺带断言页尾在场） |
| 2 | 「历史记录」逐日行改成原型 `.row-flat` 形状：日期 + 「册名 · 新学 x/y · 复习 x/y · 文章态」副案 + 4dp 细条 + 右尾「N 词」 | 两枚数都来自既有字段：`group.count`（当天反馈词条数）、`DailyLearningHistory` 的 `newDone/newTarget/dueDone/dueTarget/articleGenerated/articleRead`；4dp 细条对原型 `.meter-thin` | `LearningRecordsWeekContractTest.the week strip and the two real stats are drawn, the mocked duration is not`（`LinearProgressIndicator(` 恰 1、`learning_records_day_meter_` 在册） |
| 3 | 补「本周」七格刻度（`learningWeek` 派生）+ 两枚真统计「本周词数 / 打卡天数」 | 原型 `weekRow()` + `.card` 三格；本仓有 7 天详展与派生口径（`learningWeek` 纯函数在 JVM 侧钉死窗口与三态） | `LearningRecordsWeekContractTest`（纯函数 5 条 + 源码「本周」「/ 7 天」「本周词数」「打卡天数」各恰一处、`learning_records_week`/`learning_records_stats` 各 1） |
| 4 | 原型第三格「19 分 · 日均用时」**整枚不画** | 本仓没有任何「单轮用时 / 日累计时长」采集，写出来就是编造指标（用户 2026-10-29 拍板「读不到的那枚整枚不显示」，Task 18 台账第 2 行同口径） | `LearningRecordsWeekContractTest`（`日均用时` 归零）+ connected 新增用例（`日均用时` 取不到节点） |
| 5 | 「本周」区块**只在「历史记录」页签渲染**（原型是单页顶部常驻） | `learningWeek` 吃的 `historyStats` 按既有契约**惰性加载**（`LearningRecordsViewModelTest.first load is lazy…` 钉首屏不预取），默认页签没有 7 天数据；要在默认页签也画周条就得改成首屏预取统计——数据层改动且直接破那条既有判据。故周条随其数据住进历史页签（读不到就不画） | `LearningRecordsViewModelTest`（首屏 `historyRangeCalls == 0` 照旧）+ `LearningRecordsWeekContractTest`（`isEmpty` 时不画） |
| 6 | 统计格从原型 `1fr 1fr 1fr` 三列改**两列占满**（`AppCard` 里两枚 `weight(1f)`） | 第三格（日均用时）整枚不画后，留三列会在收尾处挖一个空位；两格各占一半对齐原型 `.stat` 居中 | `LearningRecordsWeekContractTest`（`AppCard(` 在册、`\bCard\(` 归零、`日均用时` 归零）+ connected 新增用例 |
| 7 | 「今天」那格刻度用 **1.5dp 内描边**（原型 `.tick[data-today]` 是 `outline + offset:2` 的外描边） | Compose 无「圆角外扩描边」等价物；取同色同粗的内描边，登记为 token 近似（不为此引第二套形状） | connected 新增用例（七格与今天格渲染） |
| 8 | 整屏令牌迁移：`MintBackground/MintPrimary/MintPrimaryDark/MintTextMuted` 全清零；错误态改用共享 `InlineNotice`（Error + 重试动作） | 旧 `Mint*` 静态桥在批 2 逐屏退役；错误/回执条统一走共享件（提示条族棘轮） | `LearningRecordsWeekContractTest.the screen reads tokens and keeps the shared components`（`Mint*`/`MaterialTheme.typography`/`fontSize` 归零） |

**连带改动**：`LearningRecordsUiState` 新增 `todayLocalDate: LocalDate?`（由既有 `ClockProvider` 在 `load(...)` 取一次，
不新开时钟读点），屏内不读钟；`load()` 只在状态里带基准日，**不预取** `historyRange`（`LearningRecordsViewModelTest` 5/5 原样绿）。

### Task 22-E · 设置（原型 `#/me-settings`，`sMeSettings` `app.js:1328-1344`）

本屏已是原型「四组十行」的骨架（`SettingsGroups` 台账，AC-R-21）。本轮**只补一行副标题**，不增删任何行。

| # | 改动 / 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 「学习提醒」行副标题补「只在当天没完成计划时提醒 · 默认关闭（非精确、每天最多一次）」 | 原型 `:1332` 开启提醒行的 row-sub 就是「只在当天没完成计划时提醒」；本仓 `ReminderSchedulingPolicy` 当日计划完成即取消当日提醒，同一行为，不是空话 | `SettingsPrototypeAlignmentContractTest.the reminder row carries the prototype subtitle without gaining a row` |
| 2 | 原型「背词」组的「词卡显示」行**不搬** | 本仓没有「首面语言 / 认识也看释义」的偏好项（全仓零 `cardFirst` / `revealKnown` 读点），画出来是死控件；且会把 10 行变 11 行、直接破 AC-R-21 | 同判据（`词卡显示` 归零） |
| 3 | 原型页末 notice「这一页只放全局开关…」与 `caption` **不抄** | 本屏的 4 组 10 行**正是**提醒 / 词书 / 备份 / 许可的入口，照抄当场说假话 | 同判据（`这一页只放全局开关` 归零） |
| 4 | 外观分段 / 四组十行形状一格不动 | AC-R-21 台账锁死；本批只改文案 | 同判据（`SettingsGroups.all` 4 组、10 行）+ `SettingsNoDeveloperJargonTest` 5/5 原样绿 |

### Task 22-F · 提醒与发音（原型 `#/me-reminder`，`sMeReminder` `app.js:1345-1365`）

原型合成一屏「提醒与发音」，本仓是两屏（设置页「学习提醒」与「语音朗读」两条独立二级路由，各带自己的
ViewModel 与加载态）。**保持两屏**（用户口径），只把原型上缺的段落补到对应屏。

| # | 改动 / 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 提醒页「开启提醒」行补副标题「只在当天没完成计划时提醒」，标题从 `Label` 升到 `Body` | 原型 `:1332-1333` 的 row-title/row-sub；本仓 `ReminderSchedulingPolicy` 当日计划已完成即取消当日提醒，同一行为 | `ReminderSpeechPrototypeAlignmentContractTest.the reminder screen gains the prototype subtitle and the read only repeat row` + connected `ReminderSettingsScreenTest` 3/3 |
| 2 | 补只读「重复 / 周一到周日 / 每天」行（共享 `BareRow`，`onClick = null`、无 chevron） | 原型 `:1334` 画了这一行；本仓没有按星期配置提醒的能力（固定每天最多一次），所以它是说明不是控件，不给 `onClick`（同 Task 17 台账第 3 行「说明不是控件」） | 同判据（`"重复"` / `周一到周日` / `"每天"` 各恰 1）+ `\bSwitch\(` 仍恰 1 |
| 3 | 提醒时间沿用真实 `state.time`，**不写死原型 mock 的 20:30** | 20:30 是原型样例值；本仓时间由仓储读出、经 `TimePicker` 回写 | connected `ReminderSettingsScreenTest.enablingGoesThroughTheHostPermissionHookAndTimeConfirmWritesThePickedValue`（写入的是刚选的 20:00 真值） |
| 4 | 发音页自动发音行副标题补「用本机离线合成，不联网」，且**只在当前供应商是系统 TTS 时渲染** | 原型 `:1360` 那句只对系统朗读成立；小米 MiMo 走已保存的 AI 服务（经网络），无条件写就是假话 | `ReminderSpeechPrototypeAlignmentContractTest.the speech screen explains that the switch is offline only for system tts`（`PronunciationEngine.SystemTts` 条件在册） |
| 5 | 原型页头「保存」、口音「英式 / 美式」、语速「0.9 倍」、试听「delicate」四处**不搬** | 保存是 `data-act="toast"` 的 mock（本仓改完即落库，一枚只弹提示的键就是假入口）；`SpeechPreference` 无 `accent` 字段；0.9 不是 `SpeechRateSteps` 合法档（0.75/1.0/1.25/1.5）；试听卡接受用户输入 | 同判据（`口音` / `0.9 倍` 归零）+ `SpeechSettingsScreenTest` 13/13 原样绿 |
| 6 | 保持两屏，不合成原型的一屏 | 两条独立二级路由各带 ViewModel 与加载态；合成需要合并导航与状态，属导航改动且用户口径是「保持两屏」 | connected `ReminderSettingsScreenTest` 3/3 + `SpeechSettingsScreenTest` 13/13 |

### Task 22-G · 开源许可 / 数据与备份（原型 `sMeLicenses` `app.js:1385-1401`、`sMeData` `:1402-1423`）

两屏都已有共享页头 + 三档容器 + `BareRow` 行；本轮只补原型里**本仓读得到**的内容，并把读不到的两处登记为偏离。

| # | 改动 / 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 许可条目副标题从「许可证 · v版本」补成「许可证 · v版本 · 用途」 | 原型 `:1395` 的行副是「version · use」；本仓台账 `用途` 字段真实存在（`docs/third-party-notices.md`），不是编造。空字段整段不拼 | `LicensesLayoutContractTest.rows carry version and usage and the page closes with a true tail notice` |
| 2 | 许可页页尾加提示条「点开任意一条，可以查看用途、数据流与 NOTICE 位置。」（共享 `InlineNotice`，Info） | 原型那句「完整许可证文本在『设置 → 关于 → 开源许可』里可以逐条查看」在**本页**是自我指涉（本页就是那一页）；改成这一页真实提供的动作 | 同判据（适配句恰 1、原型自我指涉句归零） |
| 3 | 许可条目右尾保留「展开 / 收起」而不是原型的许可证名 | 本仓这一行可以原地展开五枚字段，「展开 / 收起」是唯一可见的展开线索；许可证已进副标题，不丢信息 | 同判据 + 既有 `AccessibilityStateSemanticsContractTest` 的展开态播报不动 |
| 4 | 原型 meta「以下组件均以其许可证原文使用，完整文本随安装包提供」**不搬** | 全仓没有逐条许可证原文的打包证据（`assets/` 只有 `kokoro-production/notices` 一组），照抄就是空头承诺 | 同判据（`完整文本随安装包提供` 归零） |
| 5 | 许可页页名保持「开源许可与隐私说明」（原型「开源许可」）；隐私说明卡保留 | spec §5 要求**入口名与页名统一**（设置入口就是这句），改名会与台账入口分叉；隐私说明是本仓 spec 要求与既有内容 | 既有 `LicensesLayoutContractTest.the screen wears the shared header under the unified page name` 原样绿 |
| 6 | 备份页页尾加提示条「所有数据默认只在本机；卸载 App 会一并删除，请先导出备份。」（原型 `:1421`） | 本地优先 + 无云同步 + 卸载随应用数据删除，这句在本仓**逐字成立** | `BackupHubLayoutContractTest.the page closes with the local only notice and invents no storage numbers` |
| 7 | 原型顶部那张「本机数据 14.2 MB / 最近备份 3 天前」**整张不画** | 本仓没有「本机占用空间」的读数，也没有「最近一次备份时间」的记录；两格都是 mock（用户 2026-10-29 拍板「读不到的那枚整枚不显示」） | 同判据（`14.2 MB` / `最近备份` / `3 天前` 归零） |
| 8 | 备份页页名保持「备份与恢复」（原型「数据与备份」）；入口文案与四行结构（留档导出 / 认证导出 / 恢复 / 删除）不动 | 页名与设置入口同名（统一口径）；四枚行文案是 `BackupEntryUnificationTest` 钉住的唯一真值，本仓把「从文件恢复」与「配对另一台设备」拆成认证导出 / 认证恢复两条真实通路，原型的「清除全部学习数据」与本仓「删除全部学习数据」同义 | 既有 `BackupHubLayoutContractTest` 6 条 + connected `BackupEntryUnificationTest` 7/7 原样绿 |

**连带改动的既有判据（期望值变化，非放宽）**：`BackupHubLayoutContractTest` 的 `InlineNotice(` 计数 **1 → 2**。
理由写进测试注释：原型本页末有一条**常驻**安全提示条，与条件回执是两件事，两条都走共享 `InlineNotice`（没有第二套提示形状）；
「回执只有一枚」的实质仍由 `deleteAllLearningDataNotice?.let` 条件渲染 + 「第一枚 `InlineNotice(` 必须排在危险区之前」
两条断言守住（页尾那条在危险区之后）。

### Task 22-H · 启动 / 建档两屏 / 错误 / 页面不可用（原型 `sSplash` `app.js:1424`、`sOnboarding1` `:1442`、`sOnboarding2` `:1467`、`sError` `:1493`、`sUnavailable` `:1511`）

批 2 余下五屏。共同口径：原型画了且本仓承载得住的照搬；承载不住的一枚不补，理由逐条落表。

| # | 改动 / 偏离 | 理由 | 判据 |
|---|---|---|---|
| 1 | 启动屏（`BrandLaunchSurface`）补原型品牌帧：88dp 圆角 logo、`AppType.ArticleTitle`（30sp 衬线）标题「英语学习」、副标题「背词 · 阅读 · AI 助学」、120×4 不确定进度条、「正在打开本机词库…」 | 原型 `:1424-1438`；这一帧是数据库探针慢 / 首启建档前的 Loading 面，旧形态只有一枚 48dp logo | `Batch2EntryScreensContractTest.the splash shows the prototype brand frame but never fakes a skip route` + connected `AppScreenTest` 57/57 |
| 2 | 启动屏底色**沿用品牌渐变**（与 `launch/BrandLaunchActivity` 同源，`AppleMintGradient` 恰 1 处） | 原型是页底；但系统启动 Activity 刚用同一组三段渐变画过 1.2 秒，这里换白底会连闪两帧。两帧同底是启动连续性，不是第二套皮肤 | 同判据（`AppleMintGradient` 计数 1；墨一律 `onBrand`） |
| 3 | 原型进度条的 62% **不搬**，改不确定态；「开始建档」「跳过，直接进入」两枚**不画** | 62% 是画稿静态演示值，本仓不知道真实加载进度；两枚按钮在本时机没有路由（首启强制建档、无跳过），画出来就是假入口 | 同判据（`progress = 0.62f` / 两枚文案归零）+ `LinearProgressIndicator(` 恰 1 |
| 4 | 建档·姓名补眉题「第 1 步 / 共 2 步」、原型副标题「只为把每天的量定得刚好，随时可以改。」、字段标签「怎么称呼你」、主键可见文案「下一步 · 选词书」 | 原型 `:1444-1463`；副标题解释「为什么要问名字」，主键说清下一步真是选词书。**可及名仍是「创建资料」**，`AppScreenTest` 的取样锚不动 | `Batch2EntryScreensContractTest.the name step frames itself as step one and keeps the connected anchors` + `LearningSetupLayoutContractTest` 建档条原样绿 |
| 5 | 建档·姓名的「每天有多少时间」「你的目标」两节**不搬** | 本仓没有时长档 / 学习目标偏好字段，原型的 chip 与 `.pick` 只接 toast | 同判据（四枚文案归零） |
| 6 | `sOnboarding2` 与「学习计划」并屏：眉题「第 2 步」、`词卡显示` 分段、「建档完成后…1 篇文章」三样**不搬** | 并屏后同一屏还要从设置反复打开（步骤框只在首启成立）；`cardFirst` 全仓零字段；文章按需生成，「建档就发一篇」为假 | `Batch2EntryScreensContractTest.the book step stays one editable screen and drops what it cannot say`（`篇文章` 归零）+ 既有 `AppScreenTest.profileCreation…`（落到「学习计划」） |
| 7 | 错误屏按 `AppError` 分叉：`InvalidProfileName` 保留「无法创建资料，请检查姓名 / 返回重新填写」；其余落 `EmptyState(Sticker.Warning)`「词库没能打开」+ 一枚真「重试」；静态 Mint 桥清零 | 旧形态把**任何**失败都说成姓名问题——数据库打不开时用户会去改名字。失败类型本该决定文案（`AppUiState.Error` 一直带着它，宿主却没传） | `Batch2EntryScreensContractTest.the error screen branches on the real failure and never invents a diagnostic` + connected `AppScreenTest.errorScreenNamesTheRealFailureAndNeverShowsAMockedDiagnosticCode` |
| 8 | 原型错误码「`LC-DB-03` · 记录时间 07:5x」与「从备份恢复」**不搬** | `AppError` 不携带可展示诊断码（编一个就是假数据）；回滚归恢复态（`NeedsRecovery` 分支已有完整导出/恢复），在普通错误屏再挂一枚会绕过恢复流程的前置校验 | 同判据（两枚归零） |
| 9 | **新建** `UnavailableScreen.kt`（共享页头「页面不可用」+ `EmptyState(Sticker.Info)` + 260dp 两枚真出口），作为「未知 AI 功能 key」的兜底；`overlayOpen` 与返回键改按 `selectedFeatureKey` 判定 | 原型 `sUnavailable`（用户 2026-10-29 拍板授权的五枚新屏之一）。`selectedFeatureKey` 是 `rememberSaveable` 的字符串，枚举改名/删项后旧 key 仍会恢复；旧链条没有分支，覆盖层会静默消失 | `Batch2EntryScreensContractTest.the unavailable page is a real screen with two honest exits` + `…is mounted as the unknown feature fallback` + connected `UnavailableScreenTest` 2/2 |

**连带改动的既有判据（期望值变化，非放宽）**：`AppScreenOverlayContainerContractTest` 的
`AppScreen.kt` 内 `PillButton(` 计数 **3 → 4**。理由写进测试注释：错误屏的「重试」是第 4 处真动作、
走同一枚共享件；四枚落点（导出取消 1 + 迁移二选一 2 + 错误屏重试 1）全部点名，判据「不许手搓按钮、
不许长出未登记的第 5 枚」不变。

## 全 36 屏对账清单（2026-10-29 复核）

原型 `assets/app.js` 的屏渲染函数共 **36 个**（`grep -oE "(const|function) s[A-Z][A-Za-z0-9]*"` 实跑得 36 条唯一名）；
应用侧顶层 `*Screen(` 在 2026-10-29 复核时是 **37 个**，Task 16 新建 `TodayWordListScreen`、
Task 17 新建 `ReviewQueueScreen`、Task 18 新建 `TodaySummaryScreen` 后为 **40 个**（2026-10-29 重跑同一条命令实得）
（重算：`grep -rhoE "^(internal )?fun [A-Za-z0-9]+Screen\(" app/src/main/java/com/example/englishlearning/ui/*.kt | sed -E 's/.*fun ([A-Za-z0-9]+Screen)\(/\1/' | sort -u | wc -l`）。
两边**不是一一对应**，所以这张表既是进度，也是范围对账。

状态图例：✅ = 本轮按原型逐屏对过并有判据；🟡 = 同屏只对了其中一块，其余未对；⬜ = 未逐屏对照，
只吃到批 0 的令牌层（颜色 / 排印 / 形状 / 动效 / 共享组件），版面没按原型重排过。

| # | 原型屏（`app.js` 行号） | 页名 | 应用侧落点 | 状态 | 还差什么 |
|---|---|---|---|---|---|
| 1 | `sLearnHome`（:302） | 今日学习 | `TodayPlanScreen.kt` | ✅ | Task 9 已对（页头 / 强卡 / 图例 / 双 CTA） |
| 2 | `sLearnQueue`（:357） | 今日词单 | **批 1 Task 16 新建** `TodayWordListScreen.kt`，入口在今日学习软卡裸行组首位（`today_plan_open_word_list`，原型 `app.js:342`） | ✅ | 已对（页头 + `.meta` + 三枚 chip + 两节 `WordRow` + `.foot`）；9 条偏离与 1 项未覆盖登记在「Task 16」台账 |
| 3 | `sSession`（:393） | 学新词（词卡） | `WordCardScreen.kt` | ✅ | Task 10 已对（三档反馈与回执） |
| 4 | `sReview`（:472） | 复习队列 | **批 1 Task 17 新建** `ReviewQueueScreen.kt`，入口在今日学习软卡裸行组第二位（`today_plan_open_review_queue`，原型 `app.js:343`） | ✅ | 已对（页头无动作位 + 强卡两行 + 「到期词」`WordRow` 节 + 「复习方式」`card-quiet` 一行 + `.foot`「开始复习」）；7 条偏离登记在「Task 17」台账，含「听发音，选释义」按「无能力就隐藏」整行不搬、三块 mock 指标整块不搬 |
| 5 | `sSummary`（:440） | 今日完成 | **批 1 Task 18 新建** `TodaySummaryScreen.kt`，无入口行——由宿主在 `showLearning` 分支按 `WordCardUiState.AllDone` 挂载（原型 `app.js:1688` / `:1701` 的末卡推进 `go('learn-summary')`） | ✅ | 已对（`barTop` + 「完成」动作位、64dp `badge-lg` + `star` + 日期 `.eyebrow` + `serif` 26px 主句、`.card` 统计格只留 `个词`、`section('还差一步')` 一行、`.foot`「回到今日」）；6 条偏离登记在「Task 18」台账，含 `用时 / 一次答对` 两枚与 `section('这一轮的反馈')` 整节按「读不到就整枚不显示」不搬 |
| 6 | `sLearnEmpty`（:501） | 今日学习·空态 | `TodayPlanScreen.kt` 空态（Ready 分支 `todayAllDone` 那一支，**不新增屏**） | ✅ | Task 19 已对（`EmptyState(star, 今天的词都学完了, …)` + 两枚 260dp 出口「调整每日新增」/「去读今日文章」+ `.head-plain` 撤齿轮）；`section('明天')` 整节不搬，8 条偏离登记在「Task 19」台账 |
| 7 | `sWordDetail`（:519） | 词义详情 | `CardDetailScreen.kt` | ✅ | Task 11 已对 |
| 8 | `sPlanEdit`（:562） | 学习计划 | `AppScreen.kt` 的 `LearningSetupScreen` | ✅ | Task 12 已对（含与 `sCardSettings` 合成一屏的裁决，台账第 1 行） |
| 9 | `sBookPicker`（:588） | 选择词书 | `AppScreen.kt` 的 `WordBookPicker` | 🟡 | **行形状已翻正**（Task 13-A）；独立屏那一份（页头「导入」`bar-action`、搜索、排序、`.foot`「选用这本书」、notice）未对 → Task 15 |
| 10 | `sBookDetail`（:608） | 词书 | **批 1 Task 15 新建** `BookDetailScreen.kt`（词库页「词书」段的默认面；「查看完整词单」切到该段的 `WordBookWordsScreen` 浏览态） | ✅ | Task 15 已对（强卡 `ring(86)` + 正在使用 + 书名 + 已学 x/y、「这本书的情况」四行取真数、「操作」两行）；6 条偏离登记在「Task 15」台账，含按书导出 / 按书删进度两项能力缺失整行不搬、页头 more 菜单不搬 |
| 11 | `sCardSettings`（:645） | 词卡显示 | 并入 `LearningSetupScreen` | ✅ | Task 12（合成一屏，台账第 1 行留因） |
| 12 | `sReadHome`（:702） | 每日阅读 | `ReadingAccessScreen.kt` | 🟡 | Task 21-C 已对：今日文章卡（覆盖 N 词 · 第 v 版）+「本周阅读」派生统计卡（近 7 天篇数/字数，无周目标）；仍缺：「继续」组双语对照独立入口（按「阅读器内切换」不搬，见 21-C 第 3 行）、旧 `Mint*`/M3 Button 家族的版面重排 |
| 13 | `sReadArticle`（:738） | 阅读 | `ArticleReadingScreen.kt` | 🟡 | Task 21-D 已对元信息行（「约 N 词 · 第 v 版」真数，「5 分钟」不搬）；仍缺 `.prose` 字号/行距逐值、底部两枚并列出口的形态对齐 |
| 14 | `sReadTranslate`（:762） | 双语对照 | `ArticleReadingScreen.kt` 对照态 | 🟡 | Task 21-D：确认「对照态 = 同一支屏内切换」（与 LWT/LinguaCafe 一致，不新开页）；仍缺 `bar-action`「只看中文」与末尾 warning notice、逐段英中的字号/间距对齐 |
| 15 | `sReadFeed`（:779） | 外刊选读 | `ReadingFeedScreen.kt` | ✅ | Task 21-A 已对（页头 + `BareRow` 列表 + 尾部「来源说明」`QuietCard`）；3 条偏离登记在「Task 21-A」台账（条目栏目/篇幅/词数、`.meta`「不含广告」、VOA 公共领域三处读不到或不成立） |
| 16 | `sReadHistory`（:796） | 阅读历史 | `ReadingHistoryScreen.kt` | ✅ | Task 21-B 已对（按日期分节 + 逐版行 + 尾部提示；多版本折叠是 F4-01 决策 §8 的产品形状，保留）；3 条登记在「Task 21-B」台账，含「已读 / 未读」无字段整枚不搬 |
| 17 | `sAiHome`（:814） | AI 助学 | `AiLearningScreen.kt` | ⬜ | 批 3（含未接入控件禁用态） |
| 18 | `sAiSentence`（:841） | 长难句分析 | `SentenceAnalysisScreen.kt` | ⬜ | 批 3 |
| 19 | `sAiPhoto`（:865） | 拍照翻译 | `PhotoTranslationScreen.kt` | ⬜ | 批 3 |
| 20 | `sAiWordQa`（:894） | AI 问词 | `WordAiQaScreen.kt` | ⬜ | 批 3 |
| 21 | `sAiImage`（:929） | AI 生图 | `ImageStudioScreen.kt` | ⬜ | 批 3 |
| 22 | `sAiProfiles`（:952） | AI 服务 | `AiProfileSettingsScreen.kt` | ⬜ | 批 3 |
| 23 | `sAiProfileEdit`（:1194） | AI 服务编辑 | `AiProfileSettingsScreen.kt` 编辑态 | ⬜ | 批 3 |
| 24 | `sMeHome`（:1255） | 我的 | `ProfileScreen.kt` | 🟡 | Task 22-A 已对：`.head-sub`「连续打卡 N 天 · 累计学过 N 个词」+ 三格统计（三枚深链）；「我的词汇」三行按「词库页四格分段是唯一出口」不搬；「应用」组的「关于与许可」待关于页新建后接线 |
| 25 | `sMeWords`（:1285） | 我的词汇 | `WordBankScreen.kt` 的三格（生词/错词/收藏）+ `MyWordsScreen.kt` 各段 | 🟡 | Task 22-C 已落**对应关系裁决**（不新建页：原型三 tab ↔ 词库页三格）；待做：分段计数、段内 caption（需先核三条规则是否与本仓一致）、尾部 notice、页头搜索动作裁决 |
| 26 | `sMeRecords`（:1304） | 学习记录 | `LearningRecordsScreen.kt` | ✅ | Task 22-D 已对：页尾提示条逐字搬、「历史记录」逐日行补文章态 + 4dp 细条 + 右尾「N 词」、「本周」七格刻度 + 两枚真统计（本周词数 / 打卡天数）；「19 分 · 日均用时」整枚不画（无采集），「本周」区块随惰性统计住进历史页签；8 条登记在「Task 22-D」台账 |
| 27 | `sMeSettings`（:1328） | 设置 | `SettingsScreen.kt` | ✅ | Task 22-E 已对：「学习提醒」行补原型副标题；「词卡显示」行与页末 notice 不搬（无偏好项 / 与 4 组 10 行矛盾）；4 组 10 行形状不动，4 条登记在「Task 22-E」台账 |
| 28 | `sMeReminder`（:1345） | 提醒与发音 | `ReminderSettingsScreen.kt` + `SpeechSettingsScreen.kt` | ✅ | Task 22-F 已对：提醒页开启行副标题 + 只读「重复」行；发音页「用本机离线合成，不联网」按系统 TTS 条件渲染；保存 / 口音 / 0.9 倍 / 试听 delicate 四处不搬；保持两屏，6 条登记在「Task 22-F」台账 |
| 29 | `sMeAbout`（:1366） | 关于 | **批 2 Task 22-B 新建** `AboutScreen.kt`；入口 = 设置「外观与关于」组「关于」行（第 10 行，用户拍板） | ✅ | 应用块 / 说明三行 / 致谢；版本取 `BuildConfig.VERSION_NAME`、致谢只留成立的两句；AC-R-21 行数 9→10；JVM 4 条 + connected 全绿；`licenseCount` 注入待补（行文案已能不带数字） |
| 30 | `sMeLicenses`（:1385） | 开源许可 | `LicensesScreen.kt` | ✅ | Task 22-G 已对：条目副标题补用途、页尾提示条（原型自我指涉句改写为真实动作）、meta「完整文本随安装包提供」不搬（无打包证据）；5 条登记在「Task 22-G」台账 |
| 31 | `sMeData`（:1402） | 数据与备份 | `BackupHubScreen.kt` | ✅ | Task 22-G 已对：页尾提示条逐字搬；顶部「本机数据 14.2 MB / 最近备份 3 天前」整张不画（无采集）；页名与四行入口文案保持既有唯一真值；3 条登记在「Task 22-G」台账 |
| 32 | `sSplash`（:1424） | 启动 | `AppScreen.kt` 的 `BrandLaunchSurface` 分支 | ✅ | Task 22-H 已对：88dp logo + 30sp 衬线标题 + 副标题 + 不确定进度条 + 「正在打开本机词库…」；底色沿用品牌渐变（启动两帧不换底）；62% 假进度与两枚无路由按钮不搬；3 条登记在「Task 22-H」台账 |
| 33 | `sOnboarding1`（:1442） | 建档·姓名 | `ProfileCreationScreen`（在 `AppScreen.kt`） | ✅ | Task 22-H 已对：眉题「第 1 步 / 共 2 步」+ 原型副标题 + 「怎么称呼你」+ 「下一步 · 选词书」；「每天有多少时间」「你的目标」不搬（无偏好字段）；可及名「创建资料」保留；2 条登记在「Task 22-H」台账 |
| 34 | `sOnboarding2`（:1467） | 建档·选词书与目标 | `AppScreen.kt` 建档分支（`LearningSetupScreen`） | ✅ | Task 22-H 已对（**并屏裁决**）：选词书行列表与每日新增档位已对；眉题「第 2 步」不搬（同一屏还用于后续编辑）、`词卡显示` 无字段、`建档完成后…1 篇文章` 为假（按需生成）；1 条登记在「Task 22-H」台账 |
| 35 | `sError`（:1493） | 词卡没打开 | `ErrorScreen`（在 `AppScreen.kt`） | ✅ | Task 22-H 已对：按 `AppError` 分叉（姓名错误保留旧文案 / 其余「词库没能打开」+ 真重试）；`LC-DB-03` 与「从备份恢复」不搬（无诊断码 / 回滚归恢复态）；2 条登记在「Task 22-H」台账 |
| 36 | `sUnavailable`（:1511） | 页面不可用 | **批 2 Task 22-H 新建** `UnavailableScreen.kt`；宿主 = 未知 AI 功能 key 的兜底分支 | ✅ | Task 22-H 已对：原型文案逐字 + 260dp 两枚真出口；`overlayOpen` 与返回键改按 key 判定；JVM 2 条 + connected 2/2；1 条登记在「Task 22-H」台账 |

**小计（Task 22-H 完成后重算，按本表状态列逐行数，命令见本节末）**：✅ 23 屏 · 🟡 6 屏 · ⬜ 7 屏。
复核时点是「✅ 5 · 🟡 2 · ⬜ 29」，其中 **#2 今日词单 / #4 复习队列 / #5 今日完成 三屏本仓没有承载点**，
是逐屏核到源码才发现的，见各行「待裁决」——要做得先新建屏与导航，属产品范围变化；
用户 2026-10-29 拍板「三屏全部新建」，**#2 已于 Task 16 建屏并接线、#4 已于 Task 17 建屏并接线、
#5 已于 Task 18 建屏并接线**（本表随之翻正，「待裁决」那一行的旧措辞已按拍板改掉）。
重算命令：`sed -n '<表起>,<表止>p' docs/superpowers/plans/2026-10-29-ui-prototype-alignment.md | grep -E "^\| [0-9]+ \|" | awk -F'|' '{print $6}' | sort | uniq -c`。
也就是说**到 2026-10-29 复核为止，36 屏里真正逐屏对过的只有 5 屏**，
其余只共享批 0 换过的令牌层——这就是「还有很多 UI 没按新原型改」的确切范围。

同批已下、但表里仍标「先确认」的两格（**不必再问，按此推进**）：#29 关于页 → 用户 2026-10-29 拍板
**按原型新建独立「关于」页**；#36 页面不可用 → 同批拍板**按原型新建「页面不可用」页**。
两屏分别归 Task 22（我的链路）与 Task 23（启动 / 建档 / 错误 / 不可用）执行。

**反向差集（应用有、原型没画）**：`AiFeatureScreen`、`ArticleImportScreen`、`BackupExportScreen`、
`BackupPairingExportScreen`、`BackupRestoreScreen`、`GlobalVocabularySearchScreen`、`RecoveryScreen`、
`VocabularyRelearnScreen`、`WorksheetSettingsScreen`、`WorksheetPreviewScreen`，以及词卡内层的错词 / 收藏视图。
这些屏在原型里没有对应画板，按「原型没画到的状态先问用户」的口径处理：不自行发明版面，也不许顺手改。