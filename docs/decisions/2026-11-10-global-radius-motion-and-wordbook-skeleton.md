# 2026-11-10 全站圆角统一 · 折叠动效收口 · 词书内容包首帧骨架

用户当批要求（m04281）：「整体风格以及页面风格要统一，圆角 / R 角 / 颜色饱和度对齐一下，太杂也不好；
动画也要做——所有展开、切换、弹窗、确认，以及加载词书的那个，要有预加载，就是骨架。」

本批落地的是**不涉及品牌视觉取值**的那部分，放在既有裁定之内：

## 1. 圆角：全站只剩具名档（含新增两枚图形档）

- 屏侧裸 `RoundedCornerShape(` 归零。最后三处历史债清空：
  - `ProfileScreen.kt` 统计格可点面 `16.dp` → `AppShape.Button`（14dp）。这处裁角没有底色，
    只裁 ripple，视觉上本就不可见，落到按钮档零视觉变化。
  - `CheckInScreen.kt` 本周完成度柱 `6.dp` → `AppShape.GraphicBar`（6dp）。
  - `TodayPlanScreen.kt` 图例 8dp 方点 `2.dp` → `AppShape.GraphicDot`（2dp）。
- **为什么新增 `GraphicBar` / `GraphicDot` 而不是硬套容器五档**：五档是为「盒子装内容」定的；
  拿 10dp 去画 8dp 的方点会画成圆点，拿 14dp 去画 4dp 的细柱端会画成胶囊端——那都是没有原型依据的
  视觉改动。图形因此单列两档（见 `AppShape` 的 KDoc），屏侧仍然不许写数值。
- `ContainerContractTest` 的 `rawRadiusAllowance` 豁免表**整张删除**：一律 0，不再有「记账例外」的口袋；
  同时把 `ProfileScreen.kt` / `CheckInScreen.kt` 并入逐屏清单（这两屏此前不在表里，那两处裸圆角无人守）。
- 卡片维持 20dp（`2026-11-09-card-radius-keep-20dp-b3-closeout.md` 不变）；
  `AppShape.Icon`(12dp) / `AppShape.Inline`(9dp) 属 `IconBadgeContrastContractTest` 钉住的迁移档，本批不动。

## 2. 折叠：展开/收起一律走 `AnimatedVisibility`，chevron 走旋转

- 七处折叠体（许可证台账、阅读历史、恢复异常详情、拍照翻译「历史」「模型」、阅读「生成前参数」、
  语音合成「试听」）从裸 `if (cond) { … }` 换成
  `AnimatedVisibility(visible = cond, enter = expandVertically(tween(AppMotion.Normal, EasingOut)), exit = shrinkVertically(tween(AppMotion.Normal, EasingIn)))`。
  时长与缓动只走 `AppMotion` token（`2026-11-06-motion-foundation-and-system-reduced-motion.md`：
  跟随系统 `animator_duration_scale`，不做应用内开关、不做自研换算）。
- `EntryRow` 的 chevron 从「换字形（ChevronDown / ChevronRight）」改成「`ChevronRight` 原地旋转 90°」
  （`animateFloatAsState`，`AppMotion.Normal`）。理由：换字形会在一帧内跳成另一个形状，与它下面的展开体
  不是同一次动效；改旋转后两端共用一段时长，展开与收起才是同一段动效的两端。
  `SharedComponentGeometryContractTest` 的 `size = 16.dp, layer = AppGlyphLayer.StrokeOnly` 锚仍成立。
- 因形状改变而同步改锚的两条契约：`SpeechSettingsLayoutContractTest`（`if (previewExpanded)` →
  `visible = previewExpanded`）、`ReadingHomeLayoutContractTest`（`if (generationParamsExpanded)` →
  `visible = generationParamsExpanded`）。判的仍是「哪一段活在这一层展开体里」，不是它住在哪种语法壳里。

## 3. 词书内容包首帧：骨架替掉假状态

- `WordBookTransferScreen` 首帧（`state == null && viewModel != null`）此前把两枚 `EntryRow` 画成禁用态、
  副标题写「正在处理，请稍候…」——**那是拿假状态顶替等待**。现在画 `SkeletonList(2 行, 56dp, tag word_book_transfer_loading)`。
- `viewModel == null`（生产组装缺口）仍走真禁用行 + 「这台设备暂时读不到词书目录」：那不是等待，是缺件。
  导入/导出**动作进行中**仍走禁用行 + 「正在处理，请稍候…」（`2026-11-07-loading-skeleton-shimmer-prefetch.md`
  决定 3 的三分类：等数据 → 骨架；动作进行中 → 回执 + 真话文案）。
- 判据：`LocalSubpageSkeletonContractTest` 新增一条 loading 单元（锚 `if (state == null && viewModel != null) {`
  → `} else {`）+ 稳定锚 `word_book_transfer_loading` + 行数读常量 `WORD_BOOK_TRANSFER_PLACEHOLDER_ROWS`。
- 转圈台账（`PrimaryTabSkeletonContractTest.SPINNER_LEDGER`）不动：剩下 6 处全是「动作回执 / 网络 AI 等待」的豁免。

## 未做（待用户拍板）

- **域色与中性色的饱和度 / 明度归一**：7 枚域色 base 档 S 在 6~71%、L 在 61~74%，deep 档 S 32~77%、
  L 29~42%，确实不齐；但改 hex 会撞 `DesignTokenContractTest`（按 hex 钉值 + WCAG 比值当场复算），
  且这是品牌视觉取值，属用户拍板项，本批不动。
- 周柱 / 图例小点的图形档取值同样等原型依据（本批只把它们挪进具名档，取值不变）。

## 验证

- `:app:testDebugUnitTest` 2617 条全绿；`:app:assembleDebug` 通过。
- 真机 connected：`EntryRowTest / CheckInScreenTest / ProfileScreenTest / LicensesScreenTest /
  ReadingHistoryScreenTest`（33 条）+ `PhotoTranslationScreenTest / ReadingAccessScreenTest /
  SpeechSettingsScreenTest / RecoveryScreenTest / TodayPlanScreenTest`（76 条）全绿。
