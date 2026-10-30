# Task 13-A 交接简报 · 选词书改原型 pick-radio 行列表

日期：2026-10-29 · 分支：`stage-5-ui-r2-learning-path` · 状态：**13-A 已完成**（收口记录见文末，未提交、未推送）

用户已拍板（2026-10-29，三问三答）：

1. 选词书版面 → **改原型 `.pick.pick-radio` 通栏行列表**，翻掉批 5 spec §5 的「2 列色块卡」；
2. 复习上限滑杆的拇指几何 → **保持 M3 现状**，只记台账（不覆写 `thumb` / `track`）；
3. 「到期词超过上限时，先复习最久没见的。」→ **改成不承诺口径**，已落地（见下「本轮已完成」）。

## 原型权威源（只读，逐字抄，别凭描述）

目录：`C:\Users\steveliuyan\AppData\Roaming\Open Design\namespaces\release-stable-win\data\projects\6a0aa00b-6bfc-43e7-b35f-13f7478d3147`

- `assets/app.js:587-606` = `sBookPicker()`（独立选书屏）；`assets/app.js:1467-1487` = `sOnboarding2()` 的「选择词书」节。
- **两处都是 `.pick.pick-radio` 行列表**，原型没有任何**词书**网格画法：`assets/app.css` 里
  `grid-template-columns` **0 命中**，`app.js` 里 5 命中但全不是词书（`:834-835` 是 AI 主页「还在打磨」的
  `1fr 1fr` 卡片格，`:452`、`:1259`、`:1309` 是三列统计格——别把「原型没有网格」当依据用，词书这一处才是本任务的范围）；
  `两列` css/js 各 0 命中，`色块` 只在 `app.js:1001` 命中一次、是 AI 记忆法选项「几何色块」，与词书无关（以上均实跑 grep）。
- 行结构（`app.js:595-599`）：`.pick-mark`（`icon('check', 14)`）+ `.row-body`（`.row-title` = 书名
  + `.row-sub` = `b.words + ' 词' + (b.learnt ? ' · 已学 ' + b.learnt : ' · 未开始')`）
  + `b.learnt` 时才有 `.row-value` = 「使用中」。`aria-checked` = 是否 `S.bookSel`。
- 屏其余部分：页头 `barTop(title:'选择词书', action: .bar-action「导入」)`；
  meta「内置词书随 App 打包，导入的词书放在本机，不上传。」；
  notice（info + shield）「换词书后，旧词书的进度会保留在本机，随时可以换回来。」；
  `.foot` 主按钮「选用这本书」。**本仓没有导入能力**，「导入」与「选用这本书」按台账「无后端就隐藏」口径处理
  （本屏的保存路径就是「学习计划」那一条）。
- CSS（`assets/app.css:764-793`）：`.pick` 与 `.row` 几何**完全同**——`padding: var(--s3) 0`（12dp 上下、横向 0）、
  `gap: var(--s3)`（12dp）、`border-top: 1px solid var(--line-soft)`、`:first-child { border-top: 0 }`、透明底。
  `.pick-mark`：22×22、`--r` 复选 7px / `.pick-radio` 50%、`border: 1.5px solid var(--line)`、底 `var(--surface)`、
  `color: transparent`；`[aria-checked=true]` → 底与边都 `var(--brand-deep)`、`color: #fff`（勾随选中出现）。
  `.row-value`（`app.css:544`）= `--fs-meta` 13px / `--ink-3`。

## 现成可复用的共享件（批 0 已建，零调用点）

`app/src/main/java/com/example/englishlearning/ui/components/PickRow.kt` 的 `PickRow(label, checked, onCheckedChange, modifier, radio, subtitle, enabled)`
——标记 22dp / 1.5dp 边 / 选中 brand-deep + 白勾 14dp / 上下 12dp / 间隙 12dp / `heightIn(min = 48.dp)` 全部已按原型建好，
**生产侧一个调用点都没有**，就是留给这一屏的。

缺三样，需要**加性扩展**（默认值保证零调用点不受影响）：

1. `trailing: String?` + `trailingTag: String?` —— 原型 `.row-value`「使用中」（`AppType.Meta` + `colors.text3`）；
2. 顶部分隔线：`PickRow` 的 KDoc 已声明「分隔线由宿主用 `hairlineTop` 决定」，调用方在传入的 `modifier` 上挂
   `hairlineTop(if (topDivider) colors.outlineSoft else null)` 即可，**不必改组件**；
3. **删除入口**（下面单列一节，这是本任务真正的难点）。

## 必须先解决：删除词书的入口往哪放

- 现状：删除只挂在色块的**长按** + `customActions`「删除词书」上（`AppScreen.kt:2687-2708`），
  两步确认红线在宿主 `ConfirmDestructiveDialog`；内置册 `deletable = false`，连长按与自定义动作都不挂。
- `WordBookWordsScreen.kt` 里 **0 处「删除」**（已实跑 grep），所以删掉长按就等于砍掉唯一删除路径——不可接受。
- 原型 `.pick` 行**没有画任何删除 affordance**。这属于「原型里没画到的状态」，按用户口径要么沿用既有长按（不加原型没有的控件），
  要么先问。建议：**沿用长按**，把它做进 `PickRow`。
- 技术约束：`PickRow` 现在用 `Modifier.selectable(...)`（`SharedComponentContractTest.the pick row mirrors the prototype mark and row metrics`
  第 204 行**字面要求 `selectable(` 在**）。长按要同节点共存就得换 `combinedClickable(role = Role.RadioButton/Checkbox, onClickLabel, onLongClickLabel, onLongClick, onClick)`
  + `semantics { selected = checked; contentDescription = …; stateDescription = …; customActions = … }`
  ——**radio 语义靠 `role` + `selected` 复刻，不丢**。改的时候那条契约断言必须同步改判据，并在 KDoc 写明为什么不再是 `selectable`
  （「同一节点还要挂第二动作」），别悄悄放宽。
- 语义同节点这条是终审 A 的 I-A2 裁过的（父节点属性不下发给子节点）：手势、`contentDescription`、`stateDescription`、
  `customActions`、`testTag` 必须落在**同一个** modifier 链上，别拆到外层。

## 待裁决 / 建议按下面口径直接做并记台账

| 点 | 原型 | 建议 |
|---|---|---|
| 「使用中」给谁 | 给 `b.learnt > 0` 的册（mock 数据里恰好等于在用那本） | 给**当前活动词书**（`selectedId`）；「已学 N」已在 `.row-sub` 里，用进度当「使用中」会误报 |
| 词量文案 | `4308 词` | 现色块写「共 4308 词」→ 按原型去掉「共」，改成「4308 词 · 已学 3」/「4308 词 · 未开始」 |
| 等级行（CET-4 / 博士） | 行里没有 | 原型 `.pick` 只有 title + sub → **不再单列**；若舍不得，只能并进 sub 并记偏离 |
| 进度条 `LinearProgressIndicator` | 行里没有 | 撤（通栏行放进度条会把行高顶起来，原型没有这一档） |
| 「进度计算中」/「暂时读不到进度」 | 原型没有降级态 | **保留**，替换 `.row-sub` 里「· 已学 M」那一段（本地优先：降级要如实呈现，绝不写「已学 0」） |
| 首帧骨架 | 无 | 从 3 行 × 2 列色块骨架改成 **3 行通栏行骨架**，tag 前缀随之改名（见下） |

## 要迁的测试面（39 处引用 / 7 个文件，实跑 `grep -rn "wordbook_tile\|WORD_BOOK_TILE\|WordBookTile"` 得）

- JVM：`LearningSetupLayoutContractTest`（`the swipe list becomes a two column tile grid of word books`、
  `the tile takes domain colour and the book illustration without cover art`、`the four progress literals move into the tile with the container`、
  `the delete entry moves to a long press and keeps the confirmation chain`，以及取窗器 `picker` / `tile`）、
  `AccessibilityStateSemanticsContractTest`、`WordBookCardProgressContractTest`、`SharedComponentContractTest`（若换掉 `selectable`）。
- connected：`WordBookTileTest.kt`（整份）、`WordbookPickerHeaderTest.kt`、`AppScreenOverlayContainerTest.kt`。
- 生产侧要删/改的常量与件：`WORD_BOOK_TILE_COLUMNS`、`WordBookTileShape` / `WordBookTileMinHeight` / `WordBookTileGap`、
  `WordBookTile`、`WordBookTileSkeleton`、`wordbook_tile_*` / `wordbook_total_*` / `wordbook_learned_*` / `wordbook_progress_*` 这些 tag。
  connected 侧读生产真值的断言（R-13.1 / m-4 口径）必须一起换，别留悬空常量。
- 台账：批 1 偏离台账（`docs/superpowers/plans/2026-10-29-ui-prototype-alignment.md`）第 11 行现在写的是
  「两列色块留在本屏」，改完必须重写成「按原型 pick-radio 行列表，spec §5 的两列色块作废」，并补上表那几条偏离。

## 本轮已完成（不用重做）

- Task 12 收口：复习上限滑杆轨道色改 `colors.brandDeep` + `inactiveTrackColor = colors.outline`（原型 `app.css:761`
  `.range { accent-color: var(--brand-deep) }`），`SwitchTrackColorGuardTest` 合法取值由两种增为三种（新增 `brandAccentForm`），
  AppScreen.kt 从 `darkAwareFiles` 移出并改钉 2 枚 brandDeep 站点；`LocalIsDark` 导入随之删掉。
- 复习上限 caption 改成「当前版本先记下这个上限，今日复习仍会排完全部到期词。」，正反两条判据
  （新句在窗口内 + 旧承诺在剥注释的整份 `AppScreen.kt` 归零）；决策文档 `docs/decisions/2026-10-29-plan-review-limit-preference.md`
  §影响与遗留已记裁决；台账第 10、13 行同步。
- 门禁：`:app:testDebugUnitTest` 全绿（284 份结果 / 2119 条 / 0 失败，mtime 全新鲜）、`:app:assembleDebug`、
  `:app:compileDebugAndroidTestKotlin` 均通过；深浅两套真机截图见 `.git/qa-scratch/setup-slider-branddeep.png`
  与 `setup-slider-dark3.png`（本机 scratch，未入库）。

## 门禁命令（本机 Gradle 8.13，别用 `./gradlew`）

```bash
"$HOME/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat" \
  :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin \
  --offline --no-daemon --no-build-cache 2>&1 | tail -20; exit ${PIPESTATUS[0]}
```

connected 侧按 `android-connected-qa-loop` 的口径跑（`adb -s emulator-5574 install -r` + `am instrument`；
真机 `bf353dda` 只手工试用，不跑 instrumented）。

## 13-A 收口记录（2026-10-29）

状态：功能、测试、台账、决策全部落地，**未提交、未推送**（提交与推送在批 1 门禁 #14 那一站，需另行授权）。

### 生产侧改了什么

- `ui/components/PickRow.kt` —— **加性扩展** 6 个带默认值的形参：`trailing` / `trailingTag` /
  `contentDescription` / `onClickLabel` / `secondaryActionLabel` / `onSecondaryAction`。有第二动作时手势槽走
  `combinedClickable(role = Role.RadioButton, onClickLabel = "选择词书", onLongClickLabel = "删除词书", ...)`，
  否则仍是 `selectable(...)`；`combinedClickable` 不自行下发 `Selected`，所以组件补
  `semantics { selected = checked; stateDescription = 已选/未选; contentDescription; customActions }`，
  手势与语义仍在**同一枚节点**（I-A2）。批 0 那一支零调用点，默认值保证不受影响。
- `ui/AppScreen.kt` —— `WordBookPicker` 从两列色块改成 `Column(Modifier.fillMaxWidth().selectableGroup())` +
  `forEachIndexed` 逐册 `WordBookPickRow`（`PickRow(radio = true)`，tag `wordbook_pick_$id`，
  分隔线 `.then(hairlineTop(if (topDivider) colors.outlineSoft else null, startIndent = 0.dp))`）；
  `subtitle` = 「N 词 · 已学 M / 未开始 / 进度计算中 / 暂时读不到进度」（去掉旧色块的「共」），
  `trailing`「使用中」绑 `activeBookId`（= `state.savedWordBookId`，`AppScreen.kt:2268`），不绑进度；
  等级行、行内 `LinearProgressIndicator`、域色 tint、`Book` 插画全部撤下；
  骨架 `WORD_BOOK_PLACEHOLDER_ROWS = 3` 行通栏（tag `wordbook_row_skeleton_$index`，`WordBookRowMinHeight = 48.dp`）。
  旧容器 `WordBookTile(` / `WordBookTileSkeleton(` 与 `WORD_BOOK_TILE_*`、`wordbook_tile_*`、`wordbook_total_*`、
  `wordbook_learned_*`、`wordbook_progress_*` 一并下线（判据逐条钉 0）。
- `ui/components/Skeleton.kt` —— 只改 KDoc 里的测试名指涉；`ListSkeleton` 生产侧 0 调用点是**本任务之前**就悬空的，
  本轮未清理，记为遗留。

### 测试迁移图

| 文件 | 处理 |
|---|---|
| `test/.../LearningSetupLayoutContractTest.kt` | 4 条旧判据翻成 4 条新判据：`the word book list is the prototype pick radio rows`、`the pick row is the shared radio row without a tile`、`the row subtitle carries the word count and the three progress states`、`the delete entry rides the row long press and keeps the confirmation chain`；两列时代的机械形状（`chunked(` 等 9 个符号 + 6 个旧容器签名）逐条钉 0，`LazyVerticalGrid(` 与 `SwipeToDismissBox` / `wordbook_swipe_` 继续钉 0（不随版面改判） |
| `test/.../SharedComponentContractTest.kt` | `the pick row mirrors the prototype mark and row metrics` 里「字面 `selectable(` 在」那一条拆成两条：无第二动作仍 `selectable(`（:205）、有第二动作用 `combinedClickable(`（:209），判据不下调 |
| `test/.../AccessibilityStateSemanticsContractTest.kt` | 选中态判据从色块锚换到组件锚 `PickRow.kt`（`pick rows announce selected state`、`pick row selected state rule rejects near misses and accepts fixed shape`） |
| `test/.../WordBookCardProgressContractTest.kt` | 四条进度字面（`已学 N` / `未开始` / `进度计算中` / `暂时读不到进度`）随 `.row-sub` 换锚，`共 N 词` 反过来钉为 0 |
| `androidTest/.../WordBookTileTest.kt` | **整份删除** |
| `androidTest/.../WordBookPickRowTest.kt` | **新增 9 条**：红色底不回潮 / `customActions`「删除词书」同节点（含 CD、`OnLongClick` 非空、`StateDescription` 未选）/ 点选与长按只请求确认 / 内置册不挂长按 / TalkBack 选中态（`assertIsSelected`、`Role.RadioButton`、brandDeep 像素计数 150 与 40 两条腿）/ 副文案原句型 / 进度三档各说各的 / 加载完成后切档 / 「使用中」留在活动册 |
| `androidTest/.../WordbookPickerHeaderTest.kt` | ②`first_frame_reserves_full_width_placeholder_rows_at_the_row_minimum_height` 与 ③`the_picker_lays_word_books_out_as_full_width_stacked_rows` 重写：通栏 + 行高读生产常量 + 首行与列表顶边齐平 + 相邻行缝恰 1dp + 非首行顶边有线（正反两条腿）；① 页头那条未动 |
| `androidTest/.../AppScreenOverlayContainerTest.kt` | 两处 `wordbook_tile_my-pack` → `wordbook_pick_my-pack`（:171、:487），KDoc 里 `WordBookSwipeRowTest` → `WordBookPickRowTest` |

### 门禁实跑

- JVM：`:app:testDebugUnitTest` **284 份结果 / 2119 条 / 0 失败 / 0 错误 / 0 跳过**（全量重跑于 14:48，284 份 XML
  mtime 全新鲜）；`:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin` 同批 BUILD SUCCESSFUL（54s）。
- **一个坑，记下来**：`--tests` 过滤跑完会把 `app/build/test-results/testDebugUnitTest/` 只剩过滤中那几份 XML
  （实跑：4 份），基线计数当场失真。用过滤跑单类**之后**要引用全量数字，必须先不带过滤重跑一次再数。
- connected（emulator-5574，`install -r` 两枚 APK）：
  fontScale **1.0** 档 `WordBookPickRowTest` + `WordbookPickerHeaderTest` = **OK (12 tests)**，38.3s；
  fontScale **2.0** 档再加 `AppScreenOverlayContainerTest` = **OK (18 tests)**，46.1s；跑完已把设备
  `system/font_scale` 回写 1.0 并读回校验。
- **一条必须留给下一个人的 Compose 几何事实**：`hairlineTop` = `drawBehind` 画 1dp 线 + `padding(top = 1.dp)`，
  而 `PaddingModifier.semanticsBounds` 会裁掉**内距内侧**节点的语义矩形——所以带线的行，`boundsInRoot` 顶边比
  画出的线低 1dp。相邻两行语义矩形之间那条 1dp 缝**就是**那条线，首行不画线故与容器顶边齐平。骨架没有内层语义节点，
  `hairlineTop` 必须挂在 `testTag` **外侧**，否则骨架行框比真实行**多出** 1dp，「同形状」那条判据会自己骗自己
  （首跑红因即在此，另附「缝 = 0」的旧口径作废、颜色判据由三通道差改成带 token 可判别性自检的方向性通道和：
  浅套 `background` 724 与 `outlineSoft` 717 实测只差 7）。

### 台账与决策

- 批 1 偏离台账（`docs/superpowers/plans/2026-10-29-ui-prototype-alignment.md`）第 11 行已改写为
  「按原型 pick-radio 行列表，spec §5 两列色块作废」，并新增「Task 13-A · 选词书行列表」8 行偏离表。
- 新决策文档 `docs/decisions/2026-10-29-word-book-picker-rows-supersede-tiles.md`（背景 / 三方案 / 八条决定 / 影响与遗留）。
- 作废指针两处就地落笔：`docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md:222`（划掉「2 列色块卡」）与
  `docs/decisions/2026-11-02-batch-5-routing-and-picker-rulings.md` §3 段首（写明只有版面作废，
  「长按删除」与「不引 `LazyVerticalGrid`」两条结论继续有效）。历史报告（批 5 report / 批 5 plan）不改写。

### 还剩什么

- #15 词书详情屏（原型 `sBookDetail`：强卡 + `ring(done, total, 86)` + 「这本书的情况」裸行 + 「操作」行含
  「删除这本书的进度」）——那屏今天**没有**删除入口，属「原型画了本仓没有」，动手前先核能力再问。
- #14 批 1 门禁与工作报告（`docs/handoff/REPORT-TEMPLATE.md`），提交与推送需另行授权。
- 遗留小项：`ListSkeleton` 零调用点；`CardDetailScreen.DetailSection` → `AppType.Eyebrow`（backlog #11）。
