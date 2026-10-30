# 决策：选词书改原型通栏单选行，批 5「2 列色块卡」作废（2026-10-29）

## 背景

批 5 把「学习设置 / 建档」链路里的选词书做成了**两列色块卡**：域色 tint + `Book` 字形 + 书名 + 词数，
`chunked(2)` + 行内 `weight(1f)`，删除走色块长按。这句范围来自两份当时的权威文件：

- `docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md:222`
  「启动/建档 | 三屏内完成…；选词书改 **2 列色块卡**（域色 tint + `Book` 字形 + 书名 + 词数，不引封面图）」；
- `docs/decisions/2026-11-02-batch-5-routing-and-picker-rulings.md` §3（滑删 → 长按、`LazyVerticalGrid` → `chunked(2)` 的裁定与留因）。

批 1 的验收口径是**逐屏对照原型**（`docs/superpowers/plans/2026-10-29-ui-prototype-alignment.md`），
Task 13-A 复检「学习计划」屏的选词书那一节时，原型里翻不出这一画法：

- 选词书在原型里出现两处，都是同一份 `.pick.pick-radio` 行列表：`assets/app.js:587-606`（`sBookPicker()`，
  标记在 `:595`）与 `assets/app.js:1467-1487`（`sOnboarding2()` 的「选择词书」节，标记在 `:1475`）；
- `assets/app.css` 里 `grid-template-columns` **0 命中**；`.pick` 的几何（`app.css:764-793`）与 `.rows > .row`
  （`app.css:509-525`）**完全同**——`padding: var(--s3) 0`、`gap: var(--s3)`、`border-top: 1px solid var(--line-soft)`、
  `:first-child { border-top: 0 }`、透明底；`.pick-mark` 22×22、`.pick-radio` 把它收成 50%（`app.css:792`）、
  选中时底与边都 `var(--brand-deep)`、勾 `#fff`（`app.css:791`）；`.row-value`「使用中」= `--fs-meta` / `--ink-3`（`app.css:544`）。
- 需要说清一件事，别让下一条改判拿它当依据：**原型不是没有网格**，`app.js` 内联样式里有 5 处
  `grid-template-columns`（`:834-835` 是 AI 主页「还在打磨」的 `1fr 1fr` 卡片格，`:452`、`:1259`、`:1309` 是三列统计格），
  但**没有一处画的是词书**；「两列」在 `app.css` 与 `app.js` 各 0 命中，「色块」只在 `app.js:1001` 命中一次，
  是 AI 记忆法选项「几何色块」，与词书无关。

用户的裁决口径是「遇到原型和文字描述冲突，以原型文件为准」，2026-10-29 就此三问三答的第一问拍板：
**选词书版面改原型 `.pick.pick-radio` 通栏行列表，翻掉批 5 那句「2 列色块卡」**。

## 候选方案

| 方案 | 内容 | 否决 / 采纳理由 |
|---|---|---|
| A | 保留两列色块，只在报告里注明「原型没这一画法」 | 直接违反用户「以原型为准」的口径；且色块那套（域色 tint + 插画 + 行内进度条）本来就没有原型依据，是批 5 自己起的。**否决** |
| B | **翻成通栏单选行**：复用批 0 已建、生产侧零调用点的共享件 `PickRow`，删除入口沿用行长按 | 版面逐值抄原型；组件不新造第二套；删除路径不丢（`WordBookWordsScreen.kt` 里 0 处「删除」，去掉长按等于砍掉唯一删除口）。**采纳（本决策）** |
| C | 顺带把选词书拆成独立屏（原型 `#/book-picker` 的搜索 / 排序 / 词书详情 / 「导入」） | 超出本轮范围：本屏的保存路径只有「学习计划」那一条，拆屏要新增导航与第二条保存语义；「导入」在本仓没有能力。**否决，独立屏留到 Task 15 逐屏对** |

## 决定

1. **容器**：`WordBookPicker` 渲染 `Column(Modifier.fillMaxWidth().selectableGroup())`，逐册
   `wordBooks.forEachIndexed { index, book -> key(book.id) { WordBookPickRow(...) } }`。行序即列表序，
   不切行、不均分；`selectableGroup()` 让 TalkBack 在册与册之间滑动画像「这是一组单选」。
2. **行本体**：`WordBookPickRow` 调共享 `PickRow(radio = true, ...)`，`label` = 书名，
   `subtitle` = `"${book.totalWords} 词 · $learned"`（原型 `b.words + ' 词' + (b.learnt ? ' · 已学 ' + b.learnt : ' · 未开始')`，
   去掉旧色块那句「共」），`trailing` = 活动册那行的「使用中」（`AppType.Meta` + `colors.text3`，tag
   `wordbook_active_$id`），分隔线由宿主挂 `.then(hairlineTop(if (topDivider) colors.outlineSoft else null, startIndent = 0.dp))`，
   tag `wordbook_pick_$id`。
3. **「使用中」绑活动词书**（`activeBookId` ← `state.savedWordBookId`），不绑「有没有进度」。原型写的是
   `b.learnt > 0` 才挂 `.row-value`，那是 mock 数据里恰好等于在用那本的**巧合**；真机上「已学 N」已在一行 `.row-sub`
   里出现过一次，拿进度当「使用中」会把每一本学过的册子都标成在用。
4. **删除入口仍在长按**，做进 `PickRow` 的**加性扩展**（默认值保证批 0 那一支零调用点不受影响）：
   `trailing` / `trailingTag` / `contentDescription` / `onClickLabel` / `secondaryActionLabel` / `onSecondaryAction`。
   有第二动作时手势槽用 `combinedClickable(role = Role.RadioButton, onClickLabel = "选择词书", onLongClickLabel = "删除词书", ...)`，
   否则保持 `selectable(...)`；`combinedClickable` 不自行下发 `Selected`，因此 `PickRow` 补
   `semantics { selected = checked; stateDescription = if (checked) "已选" else "未选"; customActions = … }`。
   **手势、`contentDescription`、`stateDescription`、`customActions`、`testTag` 落在同一个节点上**（终审 A 的 I-A2 裁过：
   父节点属性不下发给子节点）。两步确认红线未动：`state.pendingDelete` → 宿主 `ConfirmDestructiveDialog`
   （`irreversible = true`、`confirmLabel = "删除"`）。内置册 `deletable = false`，长按与 `customActions` 都不挂。
5. **原型没画的降级态保留**（本地优先：读不到就如实说，绝不写「已学 0」）：进度在算 → 「进度计算中」；
   读不到 → 「暂时读不到进度」；`learned == 0` → 「未开始」。这三档替换的只是 `.row-sub` 里「· 已学 M」那一段，
   词数那半句照旧。
6. **撤下的件**：等级行（CET-4 / 博士）、行内 `LinearProgressIndicator`、域色 tint、`Book` 插画、封面图。
   原型 `.pick` 只有 title + sub + 可选 value，通栏行放进度条会把行高顶到原型没有的那一档。
7. **首帧骨架**同形：`repeat(WORD_BOOK_PLACEHOLDER_ROWS)`（生产常量，值 3）铺通栏行骨架，
   tag `wordbook_row_skeleton_$index`，最小高度读生产 `WordBookRowMinHeight = 48.dp`，
   `hairlineTop` 挂在 `testTag` **外侧**（原因见「影响与遗留」的语义裁切一条）。
8. **旧两列的机械形状逐条钉成 0**，不是只钉「现在长得像行」：`chunked(`、`WORD_BOOK_TILE_COLUMNS`、
   `WordBookTileGap` / `WordBookTileMinHeight` / `WordBookTileShape`、`wordbook_tile_`、`wordbook_total_`、
   `wordbook_learned_`、`wordbook_progress_`、以及两列与单列时代的容器函数名
   （`WordBookTile(`、`WordBookTileSkeleton(`、`WordBookList(`、`WordBookListDivider(`、`SwipableWordBookCard(`、`WordBookCard(`）。
   **`LazyVerticalGrid(` 继续为 0**——这一条不随版面改判：根已是 `Column(verticalScroll)`，
   嵌懒网格会被测成无限高约束并抛 `Vertically scrollable component was measured with an infinity maximum height constraints`；
   词书是十位数以内的固定集合，本就不需要懒加载。**`SwipeToDismissBox` / `wordbook_swipe_` 同样继续为 0**。

## 影响与遗留

- **两份权威文件就地留作废指针**（本决策不改写历史报告，报告是当时事实的记录）：
  `docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md:222` 与
  `docs/decisions/2026-11-02-batch-5-routing-and-picker-rulings.md` §3 各加一行指向本文。
  批 5 报告（`docs/handoff/2026-11-02-layout-redesign-batch-5-report.md`）与批 5 计划里
  「2 列色块卡」的叙述保持原样，按指针读。
- **批 1 偏离台账**：第 11 行由「两列色块留在本屏」改写为「按原型 pick-radio 行列表」，并新增
  「Task 13-A · 选词书行列表」一节 8 行偏离（作废依据、长按删除、撤下的件、「使用中」绑活动册、
  进度三档、去掉「共」、骨架、选中态判据换锚）。
- **判据换锚**（判据不下调）：`LearningSetupLayoutContractTest` 新增
  `the word book list is the prototype pick radio rows`、`the pick row is the shared radio row without a tile`、
  `the row subtitle carries the word count and the three progress states`、
  `the delete entry rides the row long press and keeps the confirmation chain`；
  `SharedComponentContractTest.the pick row mirrors the prototype mark and row metrics` 里那条「字面要求 `selectable(` 在」
  随之改成「有第二动作时用 `combinedClickable`，且 `selectable(` 仍在（无删除入口那一支）」；
  `AccessibilityStateSemanticsContractTest` 的选中态判据从色块锚换到 `PickRow.kt` 组件锚
  （`pick rows announce selected state`、`pick row selected state rule rejects near misses and accepts fixed shape`）。
- **connected 侧**：`WordBookTileTest.kt` 整份删除，由 `WordBookPickRowTest.kt`（9 条）接替；
  `WordbookPickerHeaderTest.kt` 的首帧骨架与版面两条重写；`AppScreenOverlayContainerTest.kt`
  两处 `wordbook_tile_my-pack` → `wordbook_pick_my-pack`。
- **一条 Compose 几何事实，写下来免得下一个人重新踩**：`hairlineTop` = `drawBehind` 画 1dp 线 + `padding(top = 1.dp)`，
  而 `PaddingModifier.semanticsBounds` 会把**内距内侧**节点的语义矩形裁掉——所以带分隔线的 `PickRow`，
  它的 `boundsInRoot` 顶边比画出来的线低 1dp。相邻两行语义矩形之间的那条 1dp 缝**就是**那条线；
  首行不画线，与容器顶边齐平。骨架那一支没有内层语义节点，`hairlineTop` 必须挂在 `testTag` 外侧，
  否则骨架量到的行框比真实行**多出** 1dp，「骨架与真实行同形状」这条判据会自己骗自己。
  connected 判据据此从「缝 = 0」改写成「缝恰 = 1dp + 首行与列表顶边齐平」两条腿，颜色那条也从「三通道差」
  改成带 token 可判别性自检的方向性通道和判据（浅套 `background=0xFFF1F4EF` 与 `outlineSoft=0xFFEEF2ED`
  实测只差 7，三通道逐通道比会误报）。
- **遗留**：`Skeleton.kt` 的 `ListSkeleton` 生产侧 0 调用点（本任务前就悬空，本轮未清理，留给组件盘点批次）。
  独立选书屏（原型 `#/book-picker` 的搜索 / 排序 / 词书详情 / 「导入」/「选用这本书」）不在本轮，归 Task 15；
  本仓没有导入能力，届时按「无后端就隐藏」口径处理。
