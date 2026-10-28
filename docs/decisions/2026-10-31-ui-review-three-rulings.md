# 决策记录 · 2026-10-31 用户评审的三条 UI 裁决（底导同图 / 强档卡浅套 / 反向禁色怎么取样）

## 背景

批 4 收口（`docs/handoff/2026-11-01-layout-redesign-batch-4-report.md`）之后，用户在真机
`bf353dda`（M2102J2SC）上看过整套页面，给出三条意见：

1. **底导未选中态还是旧矢量线框**，与选中态那枚插画不是同一个东西——「看着像两个 App」。
2. 浅色模式下「我的 → 已学单词连续天数」那块卡**底色过深**，压得整页发闷。
3. 浅色模式下学习页「四级词汇 · 开始学习」那块卡同样过深，**喧宾夺主**：卡片底比主 CTA 还重。

第 2、3 条是同一件事的两个落点（同一枚 `StrongCard`），第 1 条是图标族的最后一处不一致。
本文钉的是「这三条怎么改、改到哪儿为止、判据怎么钉」，不改数据层、不改 FSRS/AI 请求策略、
**也没有改任何一个 token 取值**——改的是「谁消费哪个 token」。

## 候选方案

**议题一 · 底导两档想图形一致，怎么做到「同一枚插画」又不让未选中态抢注意力。**

- A. 未选中态继续用矢量线框（现状），只把选中态插画换成同一母版的线框版 —— 否。
  线框版与位图插画不是同一笔形（母版是三阶明暗 + 高光，线框抽掉了中间层），
  在 30dp 上差异被放大，用户看到的就是「两个图标」。
- B. 两档都画**同一枚位图插画**，差别只留两件事：选中态有 `tint` 药片底、未选中态整枚淡到
  `alpha = 0.5f` —— **采纳**（裁决 1）。

**议题二 · 强档卡（`StrongCard`）浅套的底与墨。**

现状浅套 = `strongSurface #1F4739`（深墨实底）+ `onStrong #E9F2EC`（浅墨）。
这在学习页/我的页都是**页内面积最大的那块**，于是深墨实底成了视觉第一落点，
把真正的第一落点（主 CTA 与那枚 44sp 的数字）压下去了。

- A. 只把学习页那张卡单独调浅（屏内覆盖 `background(...)`）—— 否。同一个组件在「我的」页
  同样过深，用户两条意见指的就是同一个形状；屏内覆盖会造出第二套配方，
  下一个屏用 `StrongCard` 时还要再判一次「该深该浅」。
- B. **在组件内按套分叉**：浅套 `tint2 #E4EFE9` 底 + `text1 #1F2B26` 墨；
  深套一字不动（`strongSurface #22332C` + `onStrong #E9F2EC`）—— **采纳**（裁决 2）。
- C. 直接改 `strongSurface` token 的浅套取值 —— 否。`strongSurface` 还有**非卡片**消费点
  （`AppScreen.kt` 的 Button/Switch、`ArticleReadingScreen.kt`、`ArticleImportScreen.kt`），
  改 token 会把这些一起改掉，超出「这块卡太重」的范围。

**议题三 · 「旧深墨底不许回流」这条反向判据怎么量。**

第一条落地版本写的是「在 `today_plan_summary` 整节点内扫，`#1F4739` 不许出现」。真机判红，
报「最近距离 6.16（阈值 18.0）」。逐像素取证（临时探针，用完即删）后确认：

- 命中像素在卡片**底部居中**、颜色 `(25,72,58)` 一档，是主 CTA 药片上**字形边缘**的合成色；
- 该 CTA 的墨是 `onBrand #04241C`，压在薄荷渐变上，抗锯齿混出的中间色与 `#1F4739`
  欧氏距离只有 6.2 —— 落在 18.0 阈值内。

- A. 放大阈值到能容下这枚合成色 —— 否。阈值一放大，真正的旧深墨实底（距离 0）就更容易漏过；
  放宽判据正是本仓明令禁止的动作。
- B. 反向判据改成**取样点**：只在「只由底色构成」的三点取色（上沿居中距顶 5dp、左右内边带
  垂直居中距边 5dp），三点都必须等于新底色、且都不许接近旧底色 —— **采纳**（裁决 3）。

**议题四 · 几何判据用哪个矩形。**

`reading_is_after_primary_learning_action` 用 `boundsInRoot` 比先后。真机判红，
取证发现两个节点都是 `Rect(0,0,0,0)`：`boundsInRoot` 是**裁剪后**矩形，而测试宿主窗口
在该机上是 `2250×946`px（≈818×344dp，即横屏窗口），整屏比它高，两块都在视口外被裁成零。

- A. 判据改成「两者都有非零高度」再加宽 —— 否，绕开而不是修正。
- B. 改用 `positionInRoot` + `size`（布局位置，不裁剪）算「前块下沿 / 后块上沿」，
  仍要求**严格** `>` —— **采纳**（裁决 4）。

## 决定

1. **底导两档同一枚插画**。`InlineIconBadge` 增开关 `backdrop: Boolean = true`
   （`val pillBackdrop = if (backdrop) Modifier.background(appColors().tint) else Modifier`）；
   `AppBottomBar.TabSlot` 两档都调 `InlineIconBadge(icon = tab.glyph, domain = AppDomain.AiSpeech,
   backdrop = selected)`，未选中态只多一层 `Modifier.alpha(TabGlyphIdleAlpha)`，
   `private const val TabGlyphIdleAlpha = 0.5f`。`AppBottomBar.kt` 内 `AppGlyph(`/`AppGlyphLayer`
   的读点与 import 归零（兜底腿仍在 `InlineIconBadge` 里，只有插画缺失时才走矢量）。
2. **`StrongCard` 按套分叉**：浅套 `fill = tint2` / `ink = text1`，深套 `fill = strongSurface` /
   `ink = onStrong`，`CompositionLocalProvider(LocalContentColor provides ink)` 供给卡内容。
   卡片内容一律改成读 `LocalContentColor.current`（`TodayPlanScreen` 15 处含 `ThinProgress` 的
   `track`/`ink`；`ProfileScreen.ProfileStatCell` 含 `copy(alpha = 0.72f)`），
   屏内不再写死 `onStrong`。**不新增第三档容器**、`SoftCard`/`BareRow` 不动。
3. **反向禁色只判「底」**：`assertCardFillBands` 三点取样（上沿居中 / 左右内边带），
   三点都必须落新底色、且都不许落旧底色，阈值仍用全局 18.0；整节点扫色的
   `assertNoRenderedColor` 继续留给「软档卡里不许出现品牌渐变端点色」这类**真正整卡为底**的场景。
4. **几何先后用 `positionInRoot` + `size`**，`boundsInRoot` 只在节点完整可见时才可用。

## 判据与实测

**新底色这一对是加强不是放松**：`text1 #1F2B26` 压 `tint2 #E4EFE9` = **12.45:1**，
高于旧配对 `onStrong` 压 `strongSurface` 的 **9.10:1**（深套仍 11.65:1，一字未动）。
契约继续要求 ≥ 4.5，并新增「旧深墨底不许在卡底取样点出现」这一条正向对偶。

JVM 侧（全部先红后绿，改的是期望值/锚点，没有删断言、没有 `assertTrue(true)`、没有放宽成 `>=`）：

| 契约 | 动的部分 |
|---|---|
| `AppBottomBarContractTest` | `tabBadgeAnchor` / `tabBackdropAnchor` / `tabIdleAlphaAnchor` / `tabIdleAlphaPin` / `bareGlyphAnchor` 重钉 + 反空转 |
| `AppBottomBarContrastTest` | 语汇从「未选中走矢量」改成「徽标兜底腿」，新增 `backdrop = selected` 断言 |
| `AppIllustrationContractTest` | 新增 `backdrop: Boolean = true` 与 `if (backdrop) Modifier.background(appColors().tint) else Modifier` 两条锚 |
| `SharedComponentGeometryContractTest` / `IconBadgeContrastContractTest` | 弱档配方取样窗口收窄到 `substringBefore("fun InlineIconBadge(")`（禁 lerp 仍按全文件数） |
| `ContainerContractTest` | 浅套浅薄荷底 / 深套仍深且前景 `onStrong` |
| `ProfilePageContractTest` | 三枚统计格的墨取自容器，不再钉 `onStrong` |
| `TodayPlanNumberAuthorityTest` | `every_card_ink_comes_from_the_container` |
| `MaterialThemeAssemblyContractTest` | `contentInkOverrides` 登记：`StrongCard` 是唯一 `LocalContentColor` 覆盖点 |

读数：全量 JVM `files=266 / tests=1961 / failures=0 / errors=0 / skipped=0`；
`:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin`、`verifyThirdPartyNotices`、
`verifyAcTraceabilityMatrix` 全过。

真机 `bf353dda`（`am instrument`，**未用** `connectedDebugAndroidTest`）：

| 类 | 结果 | 说明 |
|---|---|---|
| `AppBottomBarTest` | 9/9 | 新增「两档同一枚插画同位」+「未选中是选中态暗化孪生」（不透明像素平均亮度差 > 8.0） |
| `TodayPlanScreenTest` | 24/24 | 重写浅套强档卡用例（底色 `#E4EFE9`、墨 `#1F2B26`、对比 ≥4.5、卡底取样点禁旧深墨）；先后序用例改 `positionInRoot` |
| `ProfileScreenTest` | 3/3 | 统计格内容色随容器 |
| `ThemeModeSwitchTest` | 4/4 | 深套容器链路口径未变，DST 注释同步到「浅套不再用 `strongSurface`」 |
| `ui` 包全量 | 全绿 | 首次全跑 498 例里 29 条是横屏假红（见下「环境侧记录」）；锁竖屏后按失败清单重跑 11 类 **147/147** + 5 类 **57/57**，另合并重跑 4 类 **40/40**、`font_scale 2.0` 2 类 **10/10** |

## 环境侧记录（不是代码问题，但会造出假红）

- **真机曾处于横屏**（`accelerometer_rotation = 1` + 设备平放），宿主窗口只有 ≈344dp 高，
  整卷比窗口高的屏里所有「视口外的节点」都被裁成零矩形，一次 `ui` 包全跑因此出
  29 条假红（`AppScreenTest` / `ArticleReadingScreenTest` / `WordsScreen*` / `ReadingAccessScreenTest` 等）。
  已把 `accelerometer_rotation` 置 `0`、`user_rotation` 置 `0` 锁竖屏，复跑全绿。
  **后续任何人看到「大半屏用例突然判红且消息里带 `Rect(0,0,0,0)`」，先查旋转。**
- `font_scale` 1.0 / 2.0 两档分别在位跑过，跑完已恢复 1.0。

## 未做的事

- 不新增第三档容器、不改任何 token 取值、不动品牌渐变与 28 枚插画资源。
- `git` 不在 PATH，本文与各 Task 的「登记」步骤一律记为未执行，本文件 + `docs/handoff/`
  报告是权威账。
